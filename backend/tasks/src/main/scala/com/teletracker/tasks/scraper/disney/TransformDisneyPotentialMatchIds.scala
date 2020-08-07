package com.teletracker.tasks.scraper.disney

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.PotentialMatchScroller
import com.teletracker.common.elasticsearch.model.{
  EsExternalId,
  EsPotentialMatchItem
}
import com.teletracker.common.elasticsearch.model.EsPotentialMatchItem.EsPotentialMatchItemId
import com.teletracker.common.elasticsearch.scraping.EsPotentialMatchItemStore
import com.teletracker.common.model.scraping.ScrapeCatalogType
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.Futures.richFuture
import io.circe.syntax._
import javax.inject.Inject
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.index.query.QueryBuilders
import scala.concurrent.{ExecutionContext, Future}

class TransformDisneyPotentialMatchIds @Inject()(
  potentialMatchScroller: PotentialMatchScroller,
  esPotentialMatchItemStore: EsPotentialMatchItemStore
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val dryRun = rawArgs.dryRun
    val limit = rawArgs.limit

    potentialMatchScroller
      .start(
        QueryBuilders
          .nestedQuery(
            "scraped",
            QueryBuilders
              .termQuery(
                "scraped.type",
                ScrapeCatalogType.DisneyPlusCatalog.toString
              ),
            ScoreMode.Avg
          )
      )
      .safeTake(limit)
      .foreachF(item => {
        if (item.scraped.item.url.isDefined) {
          item.id match {
            case EsPotentialMatchItemId((itemId, externalId)) =>
              val newId =
                item.scraped.item.url.get.split("/").takeRight(2).mkString("_")
              val newItem = item.copy(
                id = EsPotentialMatchItem
                  .id(itemId, EsExternalId(ExternalSource.DisneyPlus, newId))
              )

              if (!dryRun) {
                logger.info(s"Replacing: ${item.id} with ${newId}.")
                esPotentialMatchItemStore
                  .index(newItem)
                  .flatMap(_ => {
                    logger.info(s"Deleting old id ${item.id}")
                    esPotentialMatchItemStore.delete(item.id).map(_ => {})
                  })
              } else {
                logger
                  .info(s"DRY RUN: Would've indexed: ${newItem.asJson.spaces2}")
                Future.unit
              }

            case _ =>
              logger.warn(s"Could not parse ID: ${item.id}")
              Future.unit
          }
        } else {
          logger.warn(s"Item did not have url: ${item.id}")
          Future.unit
        }
      })
      .await()
  }
}
