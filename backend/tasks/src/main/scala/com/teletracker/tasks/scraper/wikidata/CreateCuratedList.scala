package com.teletracker.tasks.scraper.wikidata

import com.teletracker.common.model.wikidata.{
  Entity,
  EntityOperations,
  TimeDataValue,
  WikibaseEntityIdDataValue
}
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.SourceRetriever
import io.circe.generic.JsonCodec
import javax.inject.Inject
import java.net.URI
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.time.OffsetDateTimeUtils
import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}

class CreateCuratedList @Inject()(
  sourceRetriever: SourceRetriever
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val input = args.valueOrThrow[URI]("input")
    val property = args.valueOrThrow[String]("property")
    val expectedValue = args.valueOrThrow[String]("value")

    val x = sourceRetriever
      .getSourceAsyncStream(input)
      .mapConcurrent(16)(source => {
        Future {
          try {
            new IngestJobParser()
              .stream[WikibaseEntityByImdbId](source.getLines())
              .flatMap {
                case Left(value) =>
                  logger.error("Couldnt parse line", value)
                  None
                case Right(value) => Some(value)
              }
              .filter(row => {
//                row.entity.claims.get(property).foreach(println)
                row.entity.claims
                  .getOrElse(property, Nil)
                  .exists(
                    claim => {
                      claim.mainsnak.datavalue match {
                        case Some(WikibaseEntityIdDataValue(value, _)) =>
                          value.id == expectedValue
                        case _ => false
                      }
                    }
                  )
              })
              .toList
          } finally {
            source.close()
          }
        }
      })
      .foldLeft(List.empty[WikibaseEntityByImdbId])(_ ++ _)
      .await()

    x.map(_.entity)
      .map(entity => {
        val dates = EntityOperations
          .extractValues(entity, "P577")
          .getOrElse(Nil)
          .flatMap(_.datavalue)
          .collectFirst {
            case TimeDataValue(time, precision) =>
              OffsetDateTime.parse(time, OffsetDateTimeUtils.SignedFormatter)
          }

        val title = EntityOperations.extractTitle(entity)
        title -> dates
      })
      .sortBy(_._2)
      .foreach(println)
  }
}

@JsonCodec
case class WikibaseEntityByImdbId(
  imdbId: String,
  entity: Entity)
