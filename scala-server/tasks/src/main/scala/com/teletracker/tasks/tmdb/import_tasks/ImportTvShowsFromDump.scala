package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{ExternalSource, ThingType}
import com.teletracker.common.elasticsearch._
import com.teletracker.common.model.ToEsItem
import com.teletracker.common.model.tmdb.TvShow
import com.teletracker.common.util.RequireLite.requireLite
import com.teletracker.common.util.{GenreCache, Slug}
import javax.inject.Inject
import java.time.LocalDate
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try
import com.teletracker.common.util.GenreCache
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import scala.concurrent.ExecutionContext

class ImportTvShowsFromDump @Inject()(
  s3: S3Client,
  sourceRetriever: SourceRetriever,
  thingsDbAccess: ThingsDbAccess,
  genreCache: GenreCache,
  protected val itemSearch: ItemSearch,
  protected val itemUpdater: ItemUpdater,
  protected val personLookup: PersonLookup
)(implicit protected val executionContext: ExecutionContext)
    extends ImportTmdbDumpTask[TvShow](
      s3,
      sourceRetriever,
      thingsDbAccess,
      genreCache
    )
    with ImportTmdbDumpTaskToElasticsearch[TvShow] {

  import io.circe.syntax._
  import com.teletracker.common.util.Shows._
  import diffson._
  import diffson.circe._
  import diffson.jsonpatch._
  import diffson.jsonpatch.lcsdiff.remembering._

  implicit override def toEsItem: ToEsItem[TvShow] =
    ToEsItem.forTmdbShow

  override protected def handleItem(
    args: ImportTmdbDumpTaskArgs,
    item: TvShow
  ): Future[Unit] = {
    Promise
      .fromTry {
        Try {
          val releaseYear = item.releaseYear

          requireLite(
            releaseYear.isDefined,
            s"Attempted to get release year from show = ${item.id} but couldn't: (original field = ${item.first_air_date})"
          )
        }
      }
      .future
      .flatMap(_ => {
        itemSearch.lookupItemByExternalId(
          ExternalSource.TheMovieDb,
          item.id.toString,
          ThingType.Show
        )
      })
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

          val partialUpdates = value.copy(
            adult = value.adult,
            images = Some(images.toList),
            original_title = item.original_name.orElse(value.original_title),
            overview = item.overview.orElse(value.overview),
            popularity = item.popularity.orElse(value.popularity),
            ratings = Some(newRatings),
            release_date = item.first_air_date
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
              external_ids = Some(toEsItem.esExternalId(item).toList),
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
              original_title = item.original_name,
              overview = item.overview,
              popularity = item.popularity,
              ratings = Some(toEsItem.esItemRating(item).toList),
              recommendations = None,
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
              slug = Slug(item.name, item.releaseYear.get),
              tags = None,
              title = StringListOrString.forString(item.name),
              `type` = ThingType.Show
            )

            if (args.dryRun) {
              Future.successful {
                logger.info(
                  s"Would've inserted new show (id = ${item.show.id}, slug = ${esItem.slug})\n:${esItem.asJson.spaces2}"
                )
              }
            } else {
              itemUpdater.insert(esItem)
            }
          }

          updateFut.flatMap(identity).map(_ => {})
      }
  }
}
