package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.model.EsExternalId
import com.teletracker.common.elasticsearch.{ElasticsearchExecutor, ItemLookup}
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.model.GenericTmdbDumpFileRow
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import io.circe.syntax._
import com.teletracker.common.util.Futures._
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.xcontent.XContentType
import java.net.URI
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class UpdatePopularitiesDirect @Inject()(
  sourceRetriever: SourceRetriever,
  itemLookup: ItemLookup,
  elasticsearchExecutor: ElasticsearchExecutor,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  final private val scheduler = Executors.newSingleThreadScheduledExecutor()

  override protected def runInternal(args: Args): Unit = {
    val itemType = args.valueOrThrow[ItemType]("itemType")
    val loc = args.valueOrThrow[URI]("loc")
    val limit = args.valueOrThrow[Int]("limit")

    val source = sourceRetriever.getSource(loc)

    try {
      new IngestJobParser()
        .asyncStream[GenericTmdbDumpFileRow](source.getLines())
        .collect {
          case Right(value) => value
        }
        .take(limit)
        .grouped(50)
        .delayedMapF(1 second, scheduler)(batch => {
          val popularityById = batch.map(row => row.id -> row.popularity).toMap

          itemLookup
            .lookupItemsByExternalIds(
              batch
                .map(
                  row => (ExternalSource.TheMovieDb, row.id.toString, itemType)
                )
                .toList
            )
            .map(found => {
              if (found.isEmpty) {
                Future.unit
              } else {
                logger.info(
                  s"Updating ${found.size} popularities: ${found.values.map(_.id).mkString("[", ", ", "]")}"
                )

                val request = new BulkRequest(
                  teletrackerConfig.elasticsearch.items_index_name
                )

                found.mapValues(_.id).foreach {
                  case ((EsExternalId(_, id), _), uuid) =>
                    val popularity = popularityById(id.toInt)
                    request.add(
                      new UpdateRequest(
                        teletrackerConfig.elasticsearch.items_index_name,
                        uuid.toString
                      ).doc(
                        Map(
                          "popularity" -> popularity
                        ).asJson.noSpaces,
                        XContentType.JSON
                      )
                    )
                }

                elasticsearchExecutor.bulk(request).map(_ => {})
              }
            })
        })
        .force
        .await()
    } finally {}
  }
}
