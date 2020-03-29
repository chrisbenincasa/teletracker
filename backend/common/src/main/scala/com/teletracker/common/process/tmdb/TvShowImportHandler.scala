package com.teletracker.common.process.tmdb

import cats.implicits._
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch._
import com.teletracker.common.elasticsearch.denorm.ItemCreditsDenormalizationHelper
import com.teletracker.common.model.ToEsItem
import com.teletracker.common.model.tmdb.TvShow
import com.teletracker.common.process.tmdb.TvShowImportHandler.{
  TvShowImportHandlerArgs,
  TvShowImportResult
}
import com.teletracker.common.pubsub.TaskScheduler
import com.teletracker.common.tasks.TaskMessageHelper
import com.teletracker.common.tasks.model.{
  DenormalizeItemTaskArgs,
  TeletrackerTaskIdentifier
}
import com.teletracker.common.util.{GenreCache, Slug}
import javax.inject.Inject
import org.slf4j.LoggerFactory
import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success
import scala.util.control.NonFatal

object TvShowImportHandler {
  case class TvShowImportHandlerArgs(dryRun: Boolean)

  case class TvShowImportResult(
    itemId: UUID,
    inserted: Boolean,
    updated: Boolean,
    castNeedsDenorm: Boolean,
    crewNeedsDenorm: Boolean,
    itemChanged: Boolean)
}

class TvShowImportHandler @Inject()(
  genreCache: GenreCache,
  itemSearch: ItemLookup,
  itemUpdater: ItemUpdater,
  personLookup: PersonLookup,
  creditsDenormalization: ItemCreditsDenormalizationHelper,
  taskScheduler: TaskScheduler
)(implicit executor: ExecutionContext) {

  import com.teletracker.common.util.Shows._
  import diffson._
  import diffson.circe._
  import diffson.jsonpatch.lcsdiff.remembering._
  import diffson.lcs._
  import io.circe._
  import io.circe.syntax._

  implicit private val lcs = new Patience[Json]

  private val logger = LoggerFactory.getLogger(getClass)

  implicit private def toEsItem: ToEsItem[TvShow] =
    ToEsItem.forTmdbShow

  def handleItem(
    args: TvShowImportHandlerArgs,
    item: TvShow
  ): Future[TvShowImportResult] = {
    itemSearch
      .lookupItemByExternalId(
        ExternalSource.TheMovieDb,
        item.id.toString,
        ItemType.Show
      )
      .flatMap {
        case Some(value) =>
          updateExistingShow(item, value, args)

        case None =>
          insertNewShow(item, args)
      }
      .andThen {
        case Success(result)
            if !args.dryRun && (result.inserted || result.updated) =>
          logger.debug(
            s"Scheduling denormalization task item id = ${result.itemId}"
          )

          taskScheduler.schedule(
            TaskMessageHelper.forTaskArgs(
              TeletrackerTaskIdentifier.DENORMALIZE_ITEM_TASK,
              DenormalizeItemTaskArgs.apply(
                itemId = result.itemId,
                creditsChanged = result.castNeedsDenorm,
                crewChanged = result.crewNeedsDenorm,
                dryRun = args.dryRun
              ),
              tags = None
            )
          )
      }
      .recover {
        case NonFatal(e) =>
          logger.warn(e.getMessage)
          throw e
      }
  }

  private def updateExistingShow(
    item: TvShow,
    existingShow: EsItem,
    args: TvShowImportHandlerArgs
  ): Future[TvShowImportResult] = {
    val castCrewFut = lookupCastAndCrew(item)
    val recsFut = buildRecommendations(item)
    val updateFut = for {
      castAndCrew <- castCrewFut
      recommendations <- recsFut
    } yield {
      val images =
        toEsItem
          .esItemImages(item)
          .foldLeft(existingShow.imagesGrouped)((acc, image) => {
            val externalSource =
              ExternalSource.fromString(image.provider_shortname)
            acc.updated((externalSource, image.image_type), image)
          })
          .values

      val newRatings = toEsItem
        .esItemRating(item)
        .map(
          rating =>
            existingShow.ratingsGrouped
              .updated(ExternalSource.TheMovieDb, rating)
        )
        .getOrElse(existingShow.ratingsGrouped)
        .values
        .toList

      val extractedExternalIds = List(
        toEsItem.esExternalId(item),
        item.external_ids
          .flatMap(_.tvdb_id)
          .map(
            tvDbId => EsExternalId(ExternalSource.TvDb, tvDbId.toString)
          ),
        item.external_ids
          .flatMap(_.imdb_id)
          .map(imdb => EsExternalId(ExternalSource.Imdb, imdb))
      ).flatten

      val newExternalSources = extractedExternalIds
        .foldLeft(existingShow.externalIdsGrouped)(
          (acc, id) =>
            acc.updated(ExternalSource.fromString(id.provider), id.id)
        )
        .toList
        .map(Function.tupled(EsExternalId.apply))

      val cast = buildCredits(item, castAndCrew)
      val castNeedsDenorm =
        creditsDenormalization.castNeedsDenormalization(cast, existingShow.cast)

      val crew = buildCrew(item, castAndCrew)
      val crewNeedsDenorm =
        creditsDenormalization.crewNeedsDenormalization(crew, existingShow.crew)

      val partialUpdates = existingShow.copy(
        alternative_titles = item.alternative_titles
          .map(_.results)
          .nested
          .map(
            altTitle =>
              EsItemAlternativeTitle(
                country_code = altTitle.iso_3166_1,
                title = altTitle.title,
                `type` = altTitle.`type`
              )
          )
          .value
          .orElse(existingShow.alternative_titles),
        adult = existingShow.adult,
        cast = cast,
        crew = crew,
        images = Some(images.toList),
        last_updated = Some(OffsetDateTime.now().toInstant.toEpochMilli),
        external_ids = Some(newExternalSources),
        original_title = item.original_name.orElse(existingShow.original_title),
        overview = item.overview.orElse(existingShow.overview),
        popularity = item.popularity.orElse(existingShow.popularity),
        ratings = Some(newRatings),
        recommendations =
          if (recommendations.nonEmpty) Some(recommendations)
          else existingShow.recommendations,
        release_date = item.first_air_date
          .filter(_.nonEmpty)
          .map(LocalDate.parse(_))
          .orElse(existingShow.release_date),
        release_dates = Some(
          item.content_ratings
            .map(_.results.map(rating => {
              EsItemReleaseDate(
                rating.iso_3166_1,
                None,
                Some(rating.rating)
              )
            }))
            .getOrElse(Nil)
        ),
        slug =
          if (existingShow.slug.isEmpty)
            item.releaseYear.map(Slug(item.name, _))
          else existingShow.slug
      )

      if (args.dryRun) {
        logger.info(
          s"Would've updated id = ${existingShow.id}:\n${diff(existingShow.asJson, partialUpdates.asJson).asJson.spaces2}"
        )
        Future.successful {
          TvShowImportResult(
            itemId = existingShow.id,
            inserted = false,
            updated = false,
            castNeedsDenorm = castNeedsDenorm,
            crewNeedsDenorm = crewNeedsDenorm,
            itemChanged = partialUpdates != existingShow
          )
        }
      } else {
        itemUpdater
          .update(partialUpdates)
          .map(_ => {
            TvShowImportResult(
              itemId = existingShow.id,
              inserted = false,
              updated = true,
              castNeedsDenorm = castNeedsDenorm,
              crewNeedsDenorm = crewNeedsDenorm,
              itemChanged = partialUpdates != existingShow
            )
          })
      }
    }

    updateFut.flatMap(identity)
  }

  private def insertNewShow(
    item: TvShow,
    args: TvShowImportHandlerArgs
  ): Future[TvShowImportResult] = {
    val castCrewFut = lookupCastAndCrew(item)
    val recsFut = buildRecommendations(item)

    val updateFut = for {
      genres <- genreCache.getReferenceMap()
      castAndCrew <- castCrewFut
      recommendations <- recsFut
    } yield {
      val tmdbGenreIds =
        item.genre_ids.orElse(item.genres.map(_.map(_.id)))

      val itemGenres = tmdbGenreIds
        .getOrElse(Nil)
        .flatMap(
          id => genres.get(ExternalSource.TheMovieDb, id.toString)
        )

      val cast = buildCredits(item, castAndCrew)
      val crew = buildCrew(item, castAndCrew)

      val esItem = EsItem(
        alternative_titles = item.alternative_titles
          .map(_.results)
          .nested
          .map(
            altTitle =>
              EsItemAlternativeTitle(
                country_code = altTitle.iso_3166_1,
                title = altTitle.title,
                `type` = altTitle.`type`
              )
          )
          .value,
        adult = None,
        availability = None,
        cast = cast,
        crew = crew,
        external_ids = Some(
          List(
            toEsItem.esExternalId(item),
            item.external_ids
              .flatMap(_.tvdb_id)
              .map(
                tvDbId => EsExternalId(ExternalSource.TvDb, tvDbId.toString)
              ),
            item.external_ids
              .flatMap(_.imdb_id)
              .map(imdb => EsExternalId(ExternalSource.Imdb, imdb))
          ).flatten
        ),
        genres = Some(
          itemGenres
            .map(genre => {
              EsGenre(
                id = genre.id,
                name = genre.name
              )
            })
        ),
        id = UUID.randomUUID(),
        images = Some(toEsItem.esItemImages(item)),
        last_updated = Some(OffsetDateTime.now().toInstant.toEpochMilli),
        original_title = item.original_name,
        overview = item.overview,
        popularity = item.popularity,
        ratings = Some(toEsItem.esItemRating(item).toList),
        recommendations = Some(recommendations),
        release_date = item.first_air_date.map(LocalDate.parse(_)),
        release_dates = Some(
          item.content_ratings
            .map(_.results.map(rating => {
              EsItemReleaseDate(
                rating.iso_3166_1,
                None,
                Some(rating.rating)
              )
            }))
            .getOrElse(Nil)
        ),
        runtime = item.episode_run_time.flatMap(_.headOption),
        slug = item.releaseYear.map(Slug(item.name, _)),
        tags = None,
        title = StringListOrString.forString(item.name),
        `type` = ItemType.Show
      )

      if (args.dryRun) {
        logger.info(
          s"Would've inserted new movie (id = ${item.id}, slug = ${esItem.slug})\n:${esItem.asJson.spaces2}"
        )
        Future.successful {
          TvShowImportResult(
            itemId = esItem.id,
            inserted = false,
            updated = false,
            castNeedsDenorm = true,
            crewNeedsDenorm = true,
            itemChanged = true
          )
        }
      } else {
        itemUpdater
          .insert(esItem)
          .map(_ => {
            TvShowImportResult(
              itemId = esItem.id,
              inserted = true,
              updated = false,
              castNeedsDenorm = true,
              crewNeedsDenorm = true,
              itemChanged = true
            )
          })
      }
    }

    updateFut.flatMap(identity)
  }

  private def lookupCastAndCrew(item: TvShow) = {
    val castIds =
      item.credits.toList.flatMap(_.cast.toList.flatMap(_.map(_.id)))
    val crewIds =
      item.credits.toList.flatMap(_.crew.toList.flatMap(_.map(_.id)))

    val allIds = (castIds ++ crewIds)
    if (allIds.nonEmpty) {
      personLookup.lookupPeopleByExternalIds(
        ExternalSource.TheMovieDb,
        (castIds ++ crewIds).distinct.map(_.toString)
      )
    } else {
      Future.successful(Map.empty[String, EsPerson])
    }
  }

  private def buildCredits(
    item: TvShow,
    matchedCastAndCrew: Map[String, EsPerson]
  ) = {
    item.credits
      .flatMap(_.cast)
      .map(_.flatMap(castMember => {
        matchedCastAndCrew
          .get(castMember.id.toString)
          .map(person => {
            EsItemCastMember(
              character = castMember.character.filter(_.nonEmpty),
              id = person.id,
              order = castMember.order.getOrElse(0),
              name = person.name.getOrElse(""),
              slug = person.slug
            )
          })
      }))
  }

  private def buildCrew(
    item: TvShow,
    matchedCastAndCrew: Map[String, EsPerson]
  ) = {
    item.credits
      .flatMap(_.crew)
      .map(_.flatMap(crewMember => {
        matchedCastAndCrew
          .get(crewMember.id.toString)
          .map(person => {
            EsItemCrewMember(
              id = person.id,
              name = person.name.getOrElse(""),
              slug = person.slug,
              order = None,
              department = crewMember.department.filter(_.nonEmpty),
              job = crewMember.job.filter(_.nonEmpty)
            )
          })
      }))
  }

  private def updateImages(
    item: TvShow,
    existingItem: EsItem
  ) = {
    toEsItem
      .esItemImages(item)
      .foldLeft(existingItem.imagesGrouped)((acc, image) => {
        val externalSource =
          ExternalSource.fromString(image.provider_shortname)
        acc.get(externalSource -> image.image_type) match {
          case Some(_) =>
            acc.updated((externalSource, image.image_type), image)
          case None => acc
        }
      })
      .values
  }

  private def buildRecommendations(item: TvShow) = {
    val allIds = item.recommendations.toList
      .flatMap(_.results)
      .map(_.id)
      .map(id => (ExternalSource.TheMovieDb, id.toString, ItemType.Show))

    itemSearch
      .lookupItemsByExternalIds(allIds)
      .map(_.map {
        case ((_, id), item) => id -> item
      })
      .map(tmdbIdToItem => {
        item.recommendations.toList
          .flatMap(_.results)
          .flatMap(show => {
            for {
              foundItem <- tmdbIdToItem.get(show.id.toString)
            } yield {
              EsItemRecommendation(
                id = foundItem.id,
                title = show.name,
                slug = foundItem.slug
              )
            }
          })
      })
  }
}
