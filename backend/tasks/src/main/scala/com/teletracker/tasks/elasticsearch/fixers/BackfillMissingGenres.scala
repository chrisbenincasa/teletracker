package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.ItemsScroller
import com.teletracker.common.process.tmdb.TmdbItemLookup
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.Futures.richFuture
import com.teletracker.common.util.GenreCache
import javax.inject.Inject
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortOrder
import scala.concurrent.{ExecutionContext, Future}

class BackfillMissingGenres @Inject()(
  itemsScroller: ItemsScroller,
  tmdbItemLookup: TmdbItemLookup,
  genreCache: GenreCache
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val query = QueryBuilders
      .boolQuery()
      .mustNot(
        QueryBuilders.nestedQuery(
          "genres",
          QueryBuilders.boolQuery().filter(QueryBuilders.existsQuery("genres")),
          ScoreMode.Avg
        )
      )
      .filter(
        QueryBuilders.rangeQuery("popularity").gte(20.0)
      )

    val genreReferences = genreCache.getReferenceMap().await()

    itemsScroller
      .start(
        new SearchSourceBuilder()
          .query(query)
          .sort("popularity", SortOrder.DESC),
        TimeValue.timeValueMinutes(5)
      )
      .take(50)
      .foreachConcurrent(8)(item => {
        val genresFut =
          item.externalIdsGrouped.get(ExternalSource.TheMovieDb) match {
            case Some(value) =>
              item.`type` match {
                case ItemType.Movie =>
                  tmdbItemLookup.expandMovie(value.toInt).map(_.genres)
                case ItemType.Show =>
                  tmdbItemLookup
                    .expandTvShow(value.toInt)
                    .map(show => {
                      show.genres
                    })
                case ItemType.Person =>
                  logger.warn(s"Unexpected person, id: ${item.id}")
                  Future.successful(None)
              }
            case None =>
              logger.warn(s"no tmdb id for id ${item.id}")
              Future.successful(None)
          }

        genresFut.map {
          case Some(value) =>
            val newGenres = value.flatMap(
              g => genreReferences.get(ExternalSource.TheMovieDb, g.id.toString)
            )
            println(
              s"${item.title}, ${item.popularity}, ${item.genres}, new genres: ${newGenres}"
            )
          case None =>
            println(s"no genres for ${item.title}, ${item.id}")
        }
      })
      .await()
  }
}
