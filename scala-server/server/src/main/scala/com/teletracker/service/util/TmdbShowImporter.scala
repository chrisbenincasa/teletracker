package com.teletracker.service.util

import com.teletracker.service.db.access.{
  NetworksDbAccess,
  ThingsDbAccess,
  TvShowDbAccess
}
import com.teletracker.service.db.model._
import com.teletracker.service.external.justwatch.JustWatchClient
import com.teletracker.service.external.tmdb.TmdbClient
import com.teletracker.service.model.justwatch.{
  JustWatchSeason,
  JustWatchShow,
  PopularItem,
  PopularItemsResponse,
  _
}
import com.teletracker.service.model.tmdb.{
  TvShow,
  TvShowSeason => TmdbTvShowSeason
}
import com.teletracker.service.process.tmdb.TmdbEntityProcessor
import com.teletracker.service.util.execution.SequentialFutures
import com.twitter.logging.Logger
import java.time.{LocalDate, OffsetDateTime}
import javax.inject.Inject
import java.time.format.DateTimeFormatter
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class TmdbShowImporter @Inject()(
  tmdbClient: TmdbClient,
  tmdbEntityProcessor: TmdbEntityProcessor,
  tvShowDbAccess: TvShowDbAccess,
  justWatchClient: JustWatchClient,
  networksDbAccess: NetworksDbAccess,
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext) {
  private val logger = Logger(getClass)

  import io.circe.generic.auto._
  import io.circe.syntax._

  def handleShows(
    shows: List[TvShow],
    pullSeasons: Boolean,
    pullAvailability: Boolean
  ) = {
    val allNetworks = networksDbAccess
      .findAllNetworks()
      .map(_.map {
        case (ref, net) => (ref.externalSource -> ref.externalId) -> net
      }.toMap)

    SequentialFutures.serialize(shows, Some(250 millis))(show => {
      val saveShowFut = tmdbEntityProcessor.handleShow(show, true)

      val seasonsFut = if (pullSeasons) {
        for {
          (_, thing) <- saveShowFut
          networksBySource <- allNetworks
          seasonsAndEpisodes <- saveSeasons(
            show,
            thing,
            networksBySource,
            pullAvailability
          )
        } yield seasonsAndEpisodes
      } else Future.unit.map(_ => Nil)

      for {
        (_, savedShow) <- saveShowFut
        seasonsAndEpisodes <- seasonsFut
        networksBySource <- allNetworks
        _ <- if (pullAvailability)
          saveAvailability(show, seasonsAndEpisodes, networksBySource)
        else Future.unit
      } yield savedShow
    })
  }

  private def saveSeasons(
    show: TvShow,
    entity: Thing,
    networksBySource: Map[(ExternalSource, String), Network],
    pullAvailability: Boolean
  ) = {
    tvShowDbAccess
      .findAllSeasonsForShow(entity.id.get)
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
                    entity.id.get,
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
                            entity.id.get,
                            s.id.get,
                            episode.name.get,
                            episode.production_code
                          )
                          tvShowDbAccess
                            .insertEpisode(newEpisode)
                            .flatMap(savedEpisode => {
                              tmdbEntityProcessor
                                .handleExternalIds(
                                  Right(savedEpisode),
                                  None,
                                  Some(episode.id.toString)
                                )
                                .map(_ => savedEpisode)
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
  ) = {
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
      val availabilitiesFut = matchJustWatchShow(show, justWatchRes.items)
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

  private def matchJustWatchShow(
    show: TvShow,
    popularItems: List[PopularItem]
  ): Option[PopularItem] = {
    popularItems.find(item => {
      val idMatch = item.scoring
        .getOrElse(Nil)
        .exists(
          s =>
            s.provider_type == "tmdb:id" && s.value.toInt.toString == show.id.toString
        )
      val nameMatch = item.title.contains(show.name)
      val originalMatch =
        show.original_name.exists(og => item.original_title.contains(og))

      idMatch || nameMatch || originalMatch
    })
  }
}
