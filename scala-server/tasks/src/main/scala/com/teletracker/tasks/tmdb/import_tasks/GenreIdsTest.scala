package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.db.SyncDbProvider
import com.teletracker.common.db.access.{GenresDbAccess, ThingsDbAccess}
import com.teletracker.common.db.model.{
  ExternalSource,
  GenreReferences,
  Genres,
  ThingType
}
import com.teletracker.common.model.tmdb.Genre
import com.teletracker.tasks.TeletrackerTask
import javax.inject.Inject
import com.teletracker.common.util.Futures._
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future}

class GenreIdsTest @Inject()(
  dbProvider: SyncDbProvider,
  genres: Genres,
  genreReferences: GenreReferences,
  genresDbAccess: GenresDbAccess,
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTask {
  private val logger = LoggerFactory.getLogger(getClass)

  override def run(args: Args): Unit = {
    import dbProvider.driver.api._
    val limit = args.valueOrDefault("limit", -1)

    import io.circe.optics.JsonPath._

    val genresJson = root.themoviedb.movie.genres.as[List[Genre]]

//    var offset = 0
//    val limit = 1000
//
//    do {
//      val res = dbProvider.getDB
//        .run {
//          sqlu"""
//      update things set genres = subquery.genres
//          from (select movie_genres.id, array_agg(gr.genre_id) as genres from
//                  (select id, jsonb_array_elements(metadata->'themoviedb'->'movie'->'genres')->>'id' as tmdb_genre_id from things where type = 'movie' and id not in ('166669cf-aa93-4bf6-a36e-fd43b8060fdf', '856a4801-868a-410f-9c9f-30e41f218d11', 'eea7801c-0aa3-48db-91fe-34e66155ac2e', 'efe5cb65-2686-40c7-b1cb-e01cf165e185') order by id asc offset $offset limit $limit)
//              as movie_genres, genre_references gr
//              where gr.external_id = tmdb_genre_id
//              group by movie_genres.id) as subquery
//          where things.id = subquery.id;
//    """
//        }
//        .await()
//
//      println(s"updated $res rows")
//
//      offset += limit
//    } while (offset < 500000)

//    val genres = genresDbAccess.findAllGenres().await()
    val references =
      genresDbAccess.findAllGenreReferences(ExternalSource.TheMovieDb).await()

    val byTmdbId = references.map(ref => ref.externalId -> ref.genreId).toMap

    thingsDbAccess
      .loopThroughAllThings(
        limit = limit,
        thingType = Some(ThingType.Movie)
      )(things => {
        things.map(_.id).foreach(println)
        Future.unit
      })
      .await()

//    thingsDbAccess
//      .loopThroughAllThings(limit = limit, thingType = Some(ThingType.Movie))(
//        things => {
//          val futs = things.map(thing => {
//            thing.metadata
//              .flatMap(genresJson.getOption)
//              .map(genres => {
//                logger.info(s"Updating ${thing.id}")
//                val ids = genres.map(_.id.toString).flatMap(byTmdbId.get)
//                thingsDbAccess.updateGenresForThing(thing.id, ids)
//              })
//              .getOrElse(Future.successful(0))
//          })
//
//          Future.sequence(futs).map(_ => {})
//        }
//      )
//      .await()
  }
}
