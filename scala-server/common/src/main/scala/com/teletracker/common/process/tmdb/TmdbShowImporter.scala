package com.teletracker.common.process.tmdb

import com.teletracker.common.cache.JustWatchLocalCache
import com.teletracker.common.db.access.{
  AsyncThingsDbAccess,
  NetworksDbAccess,
  TvShowDbAccess
}
import com.teletracker.common.db.model
import com.teletracker.common.db.model._
import com.teletracker.common.external.justwatch.JustWatchClient
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.justwatch.{
  JustWatchSeason,
  JustWatchShow,
  PopularItem,
  PopularItemsResponse,
  _
}
import com.teletracker.common.model.tmdb.{
  TvShow,
  TvShowSeason => TmdbTvShowSeason
}
import com.teletracker.common.process.ProcessQueue
import com.teletracker.common.process.tmdb.TmdbEntityProcessor.{
  ProcessFailure,
  ProcessResult,
  ProcessSuccess
}
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.common.util.{GenreCache, NetworkCache, Slug}
import com.twitter.logging.Logger
import javax.inject.Inject
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, OffsetDateTime, ZoneOffset}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try
import scala.util.control.NonFatal

class TmdbShowImporter @Inject()(
  tmdbClient: TmdbClient,
  tvShowDbAccess: TvShowDbAccess,
  justWatchClient: JustWatchClient,
  networksDbAccess: NetworksDbAccess,
  thingsDbAccess: AsyncThingsDbAccess,
  justWatchLocalCache: JustWatchLocalCache,
  processQueue: ProcessQueue[TmdbProcessMessage],
  networkCache: NetworkCache,
  genreCache: GenreCache
)(implicit executionContext: ExecutionContext)
    extends TmdbImporter(thingsDbAccess) {
  private val logger = Logger(getClass)

  import io.circe.generic.auto._
  import io.circe.syntax._

  def handleShows(
    shows: List[TvShow],
    pullSeasons: Boolean,
    pullAvailability: Boolean
  ): Future[List[ProcessResult]] = {
    SequentialFutures.serialize(shows, Some(250 millis))(handleShow(_, true))
//    val allNetworks = networksDbAccess
//      .findAllNetworks()
//      .map(_.map {
//        case (ref, net) => (ref.externalSource -> ref.externalId) -> net
//      }.toMap)
//
//    SequentialFutures
//      .serialize(shows, Some(250 millis))(show => {
//        val saveShowFut = handleShow(show, true)
//
//        val seasonsFut = if (pullSeasons) {
//          saveShowFut.flatMap {
//            case ProcessSuccess(_, thing) =>
//              for {
//                networksBySource <- allNetworks
//                seasonsAndEpisodes <- saveSeasons(
//                  show,
//                  thing,
//                  networksBySource,
//                  pullAvailability
//                )
//              } yield seasonsAndEpisodes
//
//            case ProcessFailure(error) =>
//              Future.successful(Nil)
//          }
//        } else Future.unit.map(_ => Nil)
//
//        saveShowFut.flatMap {
//          case ProcessSuccess(_, savedShow) =>
//            for {
//              seasonsAndEpisodes <- seasonsFut
//              networksBySource <- allNetworks
//              _ <- if (pullAvailability)
//                saveAvailability(show, seasonsAndEpisodes, networksBySource)
//              else Future.unit
//            } yield Some(savedShow)
//
//          case ProcessFailure(ex) =>
//            Future.successful(None)
//        }
//      })
//      .map(_.flatten)
  }

  def saveShow(show: TvShow) = {
    val genreIds =
      show.genre_ids.orElse(show.genres.map(_.map(_.id))).getOrElse(Nil).toSet
    val genresFut = genreCache.get()

    val t = ThingFactory.makeThing(show)

    (for {
      thing <- Promise
        .fromTry(t)
        .future
      genres <- genresFut
    } yield {
      val matchedGenres = genreIds.toList
        .flatMap(id => {
          genres.get(ExternalSource.TheMovieDb -> id.toString)
        })
        .flatMap(_.id)

      thingsDbAccess.saveThingRaw(
        thing.copy(genres = Some(matchedGenres)),
        Some(ExternalSource.TheMovieDb -> show.id.toString)
      )
    }).flatMap(identity)
  }

  def handleShow(
    show: TvShow,
    handleSeasons: Boolean
  ): Future[ProcessResult] = {
    val networkSlugs =
      show.networks.toList.flatMap(_.map(_.name)).map(Slug.forString).toSet
    val networksFut = networksDbAccess.findNetworksBySlugs(networkSlugs)

    val saveThingFut = saveShow(show)

    val externalIdsFut = saveThingFut.flatMap(
      t => handleExternalIds(Left(t), show.external_ids, Some(show.id.toString))
    )

    val networkSaves = for {
      savedThing <- saveThingFut
      networks <- networksFut
      _ <- Future.sequence(networks.map(n => {
        val tn = ThingNetwork(savedThing.id, n.id.get)
        networksDbAccess.saveNetworkAssociation(tn)
      }))
    } yield {}

    val availability = handleShowAvailability(show, saveThingFut)

    val seasonFut = if (handleSeasons) {
      saveThingFut.flatMap(t => {
        tvShowDbAccess
          .findAllSeasonsForShow(t.id)
          .flatMap(dbSeasons => {
            val saveFuts = show.seasons
              .getOrElse(Nil)
              .map(apiSeason => {
                dbSeasons.find(_.number == apiSeason.season_number.get) match {
                  case Some(s) => Future.successful(s)
                  case None =>
                    val m = model.TvShowSeason(
                      None,
                      apiSeason.season_number.get,
                      t.id,
                      apiSeason.overview,
                      apiSeason.air_date.map(LocalDate.parse(_))
                    )
                    tvShowDbAccess.saveSeason(m)
                }
              })

            Future.sequence(saveFuts)
          })
      })
    } else {
      Future.successful(Nil)
    }

    val result = for {
      savedThing <- saveThingFut
      _ <- networkSaves
      _ <- seasonFut
      _ <- externalIdsFut
      _ <- availability
    } yield ProcessSuccess(show.id.toString, savedThing)

    result.recover {
      case NonFatal(e) => ProcessFailure(e)
    }
  }

  private def handleShowAvailability(
    show: TvShow,
    processedShowFut: Future[ThingRaw]
  ): Future[ThingRaw] = {
    import io.circe.generic.auto._
    import io.circe.syntax._

    val query = PopularSearchRequest(1, 10, show.name, List("show"))
    val justWatchResFut = justWatchLocalCache.getOrSet(query, {
      justWatchClient.makeRequest[PopularItemsResponse](
        "/content/titles/en_US/popular",
        Seq("body" -> query.asJson.noSpaces)
      )
    })

    (for {
      justWatchRes <- justWatchResFut
      networksBySource <- networkCache.get()
      thing <- processedShowFut
    } yield {
      val matchingShow = matchJustWatchItem(show, justWatchRes.items)

      val availabilities = matchingShow
        .collect {
          case matchedItem if matchedItem.offers.exists(_.nonEmpty) =>
            for {
              offer <- matchedItem.offers.get.distinct
              provider <- networksBySource
                .get(ExternalSource.JustWatch -> offer.provider_id.toString)
                .toList
            } yield {
              val offerType = Try(
                offer.monetization_type.map(OfferType.fromJustWatchType)
              ).toOption.flatten
              val presentationType = Try(
                offer.presentation_type.map(PresentationType.fromJustWatchType)
              ).toOption.flatten

              Availability(
                id = None,
                isAvailable = true,
                region = offer.country,
                numSeasons = None,
                startDate = offer.date_created.map(
                  LocalDate
                    .parse(_, DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay()
                    .atOffset(ZoneOffset.UTC)
                ),
                endDate = None,
                offerType = offerType,
                cost = offer.retail_price.map(BigDecimal.decimal),
                currency = offer.currency,
                thingId = Some(thing.id),
                tvShowEpisodeId = None,
                networkId = provider.id,
                presentationType = presentationType
              )
            }
        }
        .getOrElse(Nil)

      thingsDbAccess.saveAvailabilities(availabilities).map(_ => thing)
    }).flatMap(identity)
  }

  private def saveSeasons(
    show: TvShow,
    entity: Thing,
    networksBySource: Map[(ExternalSource, String), Network],
    pullAvailability: Boolean
  ): Future[List[(TvShowSeason, List[TvShowEpisode])]] = {
    tvShowDbAccess
      .findAllSeasonsForShow(entity.id)
      .flatMap(existingSeasons => {
        SequentialFutures
          .serialize(show.seasons.getOrElse(Nil), Some(250 millis))(season => {
            logger.info(s"Handling season ID = ${season.id}")

            val seasonFut = tmdbClient.makeRequest[TmdbTvShowSeason](
              s"/tv/${show.id}/season/${season.season_number.get}",
              Seq("append_to_response" -> Seq("images", "videos").mkString(","))
            )

            seasonFut.flatMap(foundSeason => {
              logger.info(s"Found season for show = ${show.name}")

              val seasonSaveFut = existingSeasons
                .find(_.number == foundSeason.season_number.get) match {
                case None =>
                  logger.info("Saving TV show season")
                  val newSeason = TvShowSeason(
                    None,
                    foundSeason.season_number.get,
                    entity.id,
                    foundSeason.overview,
                    foundSeason.air_date.map(LocalDate.parse(_))
                  )

                  tvShowDbAccess.insertSeason(newSeason)

                case Some(s) => Future.successful(s)
              }

              val episodeSaves = foundSeason.episodes
                .getOrElse(Nil)
                .map(episode => {
                  tvShowDbAccess
                    .findEpisodeByExternalId(
                      ExternalSource.TheMovieDb,
                      episode.id.toString
                    )
                    .flatMap {
                      case None =>
                        seasonSaveFut.flatMap(s => {
                          val newEpisode = TvShowEpisode(
                            None,
                            episode.episode_number.get,
                            entity.id,
                            s.id.get,
                            episode.name.get,
                            episode.production_code
                          )
                          tvShowDbAccess
                            .insertEpisode(newEpisode)
                            .flatMap(savedEpisode => {
                              handleExternalIds(
                                Right(savedEpisode),
                                None,
                                Some(episode.id.toString)
                              ).map(_ => savedEpisode)
                            })
                        })

                      case Some(x) => Future.successful(x)
                    }
                })

              for {
                savedSeason <- seasonSaveFut
                savedEpisodes <- Future.sequence(episodeSaves)
              } yield (savedSeason, savedEpisodes)
            })
          })
      })
  }

  private def saveAvailability(
    show: TvShow,
    tvShowSeasonsAndEpisodes: List[(TvShowSeason, List[TvShowEpisode])],
    networksBySource: Map[(ExternalSource, String), Network]
  ): Future[Unit] = {
    val query = PopularSearchRequest(1, 10, show.name, List("show"))
    val justWatchResFut = justWatchClient.makeRequest[PopularItemsResponse](
      "/content/titles/en_US/popular",
      Seq("body" -> query.asJson.noSpaces)
    )

    val episodesByNumber = tvShowSeasonsAndEpisodes.flatMap {
      case (season, episodes) =>
        episodes.map(ep => (season.number, ep.number) -> ep)
    }.toMap

    justWatchResFut.flatMap(justWatchRes => {
      val availabilitiesFut = matchJustWatchItem(show, justWatchRes.items)
        .map {
          case matchedItem =>
            for {
              jwShow <- justWatchClient.makeRequest[JustWatchShow](
                s"/content/titles/show/${matchedItem.id}/locale/en_US"
              )
              jwPartialSeasons = jwShow.seasons.getOrElse(Nil)
              jwSeasonFuts = jwPartialSeasons.map(jwSeason => {
                justWatchClient.makeRequest[JustWatchSeason](
                  s"/content/titles/show_season/${jwSeason.id}/locale/en_US"
                )
              })
              jwSeasons <- Future.sequence(jwSeasonFuts)
            } yield {
              for {
                jwSeason <- jwSeasons
                jwEpisode <- jwSeason.episodes.getOrElse(Nil)
                jwOffer <- jwEpisode.offers.getOrElse(Nil)
                provider <- networksBySource
                  .get(ExternalSource.JustWatch -> jwOffer.provider_id.toString)
                  .toList
              } yield {
                val episodeId = episodesByNumber
                  .get(
                    jwSeason.season_number.get -> jwEpisode.episode_number.get
                  )
                  .flatMap(_.id)
                val offerType = Try(
                  jwOffer.monetization_type.map(OfferType.fromJustWatchType)
                ).toOption.flatten
                val presentationType = Try(
                  jwOffer.presentation_type
                    .map(PresentationType.fromJustWatchType)
                ).toOption.flatten

                Availability(
                  None,
                  true,
                  jwOffer.country,
                  None,
                  jwOffer.date_created.map(
                    OffsetDateTime.parse(_, DateTimeFormatter.ISO_LOCAL_DATE)
                  ),
                  None,
                  offerType,
                  jwOffer.retail_price.map(BigDecimal.decimal),
                  jwOffer.currency,
                  None,
                  episodeId,
                  provider.id,
                  presentationType
                )
              }
            }
        }
        .getOrElse(Future.successful(Nil))

      availabilitiesFut.flatMap(availabilities => {
        if (availabilities.isEmpty) {
          Future.unit
        } else {
          thingsDbAccess.saveAvailabilities(availabilities).map(_ => {})
        }
      })
    })
  }
}
