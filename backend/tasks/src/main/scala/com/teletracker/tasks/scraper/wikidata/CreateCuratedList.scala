package com.teletracker.tasks.scraper.wikidata

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.ItemLookup
import com.teletracker.common.elasticsearch.model.EsExternalId
import com.teletracker.common.model.wikidata.{
  Entity,
  EntityOperations,
  KnownWikibaseIds,
  TimeDataValue,
  WikibaseEntityIdDataValue,
  WikibaseProperties
}
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.SourceRetriever
import io.circe.generic.JsonCodec
import javax.inject.Inject
import java.net.URI
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.time.OffsetDateTimeUtils
import java.time.{OffsetDateTime, OffsetTime, ZoneId}
import scala.collection.immutable.TreeMap
import scala.concurrent.{ExecutionContext, Future}

class CreateCuratedList @Inject()(
  sourceRetriever: SourceRetriever,
  itemLookup: ItemLookup
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

    val triplets = x
      .map(_.entity)
      .flatMap(entity => {
        val publicationDates = EntityOperations
          .extractValues(
            entity,
            WikibaseProperties.PublicationDate
          )
          .getOrElse(Nil)
        val dates = publicationDates
          .flatMap(_.datavalue)
          .collectFirst {
            case TimeDataValue(time, precision) =>
              OffsetDateTime.parse(time, OffsetDateTimeUtils.SignedFormatter)
          }

        val title = EntityOperations.extractTitle(entity)
        entity.imdbId match {
          case None =>
            println(s"Entity id = ${entity.id} has no imdb id")
          case Some(_) =>
        }

        for {
          t <- title
          d <- dates
          i <- entity.imdbId
        } yield {
          (t, d, i)
        }
      })
      .sortBy(_._2)
      .map {
        case (str, time, imdbId) => imdbId -> (str, time)
      }

    val sortedByImdbId = new TreeMap[String, (String, OffsetDateTime)]() ++ triplets

    itemLookup
      .lookupItemsByExternalIds(
        sortedByImdbId.keys
          .map(key => (ExternalSource.Imdb, key, ItemType.Movie))
          .toList
      )
      .map(results => {
        sortedByImdbId
          .flatMap {
            case (imdbId, _) =>
              results.get(
                EsExternalId(ExternalSource.Imdb, imdbId) -> ItemType.Movie
              ) match {
                case Some(value) => Some(value)
                case None =>
                  println(s"No result for id = ${imdbId}")
                  None
              }
          }
          .toList
          .sortBy(
            _.usReleaseDateOrFallback
              .map(_.atTime(OffsetTime.now()))
          )
          .foreach(item => {
            println(
              s"${item.title.get.head} - ${item.usReleaseDateOrFallback.get}"
            )
          })
      })
      .await()
  }
}

@JsonCodec
case class WikibaseEntityByImdbId(
  imdbId: String,
  entity: Entity)
