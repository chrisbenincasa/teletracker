package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{ExternalSource, ThingType}
import com.teletracker.common.elasticsearch._
import com.teletracker.common.model.ToEsItem
import com.teletracker.common.model.tmdb.{Movie, MovieCountryRelease}
import com.teletracker.common.util.Movies._
import com.teletracker.common.util.{GenreCache, Slug}
import com.teletracker.tasks.util.SourceRetriever
import io.circe.syntax._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

class ImportMoviesFromDump @Inject()(
  s3: S3Client,
  sourceRetriever: SourceRetriever,
  thingsDbAccess: ThingsDbAccess,
  genreCache: GenreCache,
  protected val itemSearch: ItemSearch,
  protected val itemUpdater: ItemUpdater,
  protected val personLookup: PersonLookup
)(implicit protected val executionContext: ExecutionContext)
    extends ImportTmdbDumpTask[Movie](
      s3,
      sourceRetriever,
      thingsDbAccess,
      genreCache
    )
    with ImportTmdbDumpTaskToElasticsearch[Movie] {

  import diffson._
  import diffson.circe._
  import diffson.jsonpatch.lcsdiff.remembering._

  implicit override def toEsItem: ToEsItem[Movie] =
    ToEsItem.forTmdbMovie

  override protected def handleItem(
    args: ImportTmdbDumpTaskArgs,
    item: Movie
  ): Future[Unit] = {
    itemSearch
      .lookupItemByExternalId(
        ExternalSource.TheMovieDb,
        item.id.toString,
        ThingType.Movie
      )
      .flatMap {
        case Some(value) =>
          val images =
            toEsItem
              .esItemImages(item)
              .foldLeft(value.imagesGrouped)((acc, image) => {
                val externalSource =
                  ExternalSource.fromString(image.provider_shortname)
                acc.get(externalSource -> image.image_type) match {
                  case Some(_) =>
                    acc.updated((externalSource, image.image_type), image)
                  case None => acc
                }
              })
              .values

          val newRatings = toEsItem
            .esItemRating(item)
            .map(
              rating =>
                value.ratingsGrouped.updated(ExternalSource.TheMovieDb, rating)
            )
            .getOrElse(value.ratingsGrouped)
            .values
            .toList

          val extractedExternalIds = List(
            toEsItem.esExternalId(item),
            item.external_ids
              .flatMap(_.imdb_id)
              .map(imdb => EsExternalId(ExternalSource.Imdb, imdb))
          ).flatten

          val newExternalSources = extractedExternalIds
            .foldLeft(value.externalIdsGrouped)(
              (acc, id) =>
                acc.updated(ExternalSource.fromString(id.provider), id.id)
            )
            .toList
            .map(Function.tupled(EsExternalId.apply))

          val partialUpdates = value.copy(
            adult = value.adult,
            images = Some(images.toList),
            external_ids = Some(newExternalSources),
            original_title = item.original_title.orElse(value.original_title),
            overview = item.overview.orElse(value.overview),
            popularity = item.popularity.orElse(value.popularity),
            ratings = Some(newRatings),
            release_date = item.release_date
              .filter(_.nonEmpty)
              .map(LocalDate.parse(_))
              .orElse(value.release_date)
          )

          if (args.dryRun) {
            Future.successful {
              logger.info(
                s"Would've updated id = ${value.id}:\n${diff(value.asJson, partialUpdates.asJson).asJson.spaces2}"
              )
            }
          } else {
            itemUpdater.update(partialUpdates).map(_ => {})
          }

        case None =>
          val updateFut = for {
            genres <- genreCache.get()
            castAndCrew <- {
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
          } yield {
            val tmdbGenreIds =
              item.genre_ids.orElse(item.genres.map(_.map(_.id)))

            val itemGenres = tmdbGenreIds
              .getOrElse(Nil)
              .flatMap(
                id => genres.get(ExternalSource.TheMovieDb, id.toString)
              )

            val cast = item.credits
              .flatMap(_.cast)
              .map(_.flatMap(castMember => {
                castAndCrew
                  .get(castMember.id.toString)
                  .map(person => {
                    EsItemCastMember(
                      character = castMember.character,
                      id = person.id,
                      order = castMember.order.getOrElse(0),
                      name = person.name.getOrElse(""),
                      slug = person.slug
                    )
                  })
              }))

            val crew = item.credits
              .flatMap(_.crew)
              .map(_.flatMap(crewMember => {
                castAndCrew
                  .get(crewMember.id.toString)
                  .map(person => {
                    EsItemCrewMember(
                      id = person.id,
                      name = person.name.getOrElse(""),
                      slug = person.slug,
                      order = None,
                      department = crewMember.department,
                      job = crewMember.job
                    )
                  })
              }))

            val esItem = EsItem(
              adult = None,
              availability = None,
              cast = cast,
              crew = crew,
              external_ids = Some(
                List(
                  toEsItem.esExternalId(item),
                  item.external_ids
                    .flatMap(_.imdb_id)
                    .map(imdb => EsExternalId(ExternalSource.Imdb, imdb))
                ).flatten
              ),
              genres = Some(
                itemGenres
                  .map(genre => {
                    EsGenre(
                      id = genre.id.get,
                      name = genre.name
                    )
                  })
              ),
              id = UUID.randomUUID(),
              images = Some(toEsItem.esItemImages(item)),
              original_title = item.original_title,
              overview = item.overview,
              popularity = item.popularity,
              ratings = Some(toEsItem.esItemRating(item).toList),
              recommendations = None,
              release_date =
                item.release_date.filter(_.nonEmpty).map(LocalDate.parse(_)),
              release_dates = item.release_dates.map(_.results.map(mrd => {
                val earliest = findEarliestReleaseDate(mrd.release_dates)
                EsItemReleaseDate(
                  mrd.iso_3166_1,
                  earliest.map(_._1.toLocalDate),
                  earliest.flatMap(_._2.certification)
                )
              })),
              runtime = item.runtime,
              slug = item.releaseYear.map(Slug(item.title.get, _)),
              tags = None,
              title = StringListOrString.forString(item.title.get),
              `type` = ThingType.Movie
            )

            if (args.dryRun) {
              Future.successful {
                logger.info(
                  s"Would've inserted new movie (id = ${item.id}, slug = ${esItem.slug})\n:${esItem.asJson.spaces2}"
                )
              }
            } else {
              itemUpdater.insert(esItem)
            }
          }

          updateFut.flatMap(identity).map(_ => {})
      }
      .recover {
        case NonFatal(e) =>
          logger.warn(e.getMessage)
      }
  }

  private def findEarliestReleaseDate(releases: List[MovieCountryRelease]) = {
    releases
      .flatMap(release => {
        release.release_date
          .filter(_.nonEmpty)
          .flatMap(rd => Try(OffsetDateTime.parse(rd)).toOption)
          .map(dt => dt -> release)
      })
      .sortWith {
        case ((dt1, _), (dt2, _)) => dt1.isBefore(dt2)
      }
      .headOption
  }
}
