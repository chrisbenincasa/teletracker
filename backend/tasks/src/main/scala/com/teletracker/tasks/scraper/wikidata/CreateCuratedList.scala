package com.teletracker.tasks.scraper.wikidata

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.dynamo.model.StoredUserList
import com.teletracker.common.db.model.{
  ExternalSource,
  ItemType,
  UserThingTagType
}
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.elasticsearch.model.{
  EsExternalId,
  EsItemTag,
  EsUserItem,
  EsUserItemTag
}
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
import io.circe.syntax._
import javax.inject.Inject
import java.net.URI
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.time.OffsetDateTimeUtils
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.xcontent.XContentType
import java.time.{Instant, OffsetDateTime, OffsetTime, ZoneId}
import java.util.UUID
import scala.collection.immutable.TreeMap
import scala.concurrent.{ExecutionContext, Future}

class CreateCuratedList @Inject()(
  sourceRetriever: SourceRetriever,
  itemLookup: ItemLookup,
  elasticsearchExecutor: ElasticsearchExecutor,
  itemUpdater: ItemUpdater,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val input = args.valueOrThrow[URI]("input")
    val property = args.valueOrThrow[String]("property")
    val expectedValue = args.valueOrThrow[String]("value")
    val listId = args.valueOrThrow[UUID]("listId")

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

    val items = itemLookup
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
      })
      .await()

    val userItemTagBulkUpdate = items
      .map(item => {
        EsUserItem(
          id = EsUserItem.makeId(StoredUserList.PublicUserId, item.id),
          item_id = item.id,
          user_id = StoredUserList.PublicUserId,
          tags = List(EsUserItemTag.belongsToList(listId)),
          item = Some(item.toDenormalizedUserItem)
        )
      })
      .foldLeft(new BulkRequest())(
        (bulkReq, userItem) =>
          bulkReq.add(
            new UpdateRequest(
              teletrackerConfig.elasticsearch.user_items_index_name,
              userItem.id
            ).doc(userItem.asJson.noSpaces, XContentType.JSON)
              .upsert(userItem.asJson.noSpaces, XContentType.JSON)
          )
      )

    elasticsearchExecutor.bulk(userItemTagBulkUpdate).await()

    val now = Instant.now()

    val itemTag = EsItemTag.userScopedString(
      StoredUserList.PublicUserId,
      UserThingTagType.TrackedInList,
      Some(listId.toString),
      Some(now)
    )

    itemUpdater.upsertItemTags(items.map(_.id).toSet, itemTag).await()
  }
}

@JsonCodec
case class WikibaseEntityByImdbId(
  imdbId: String,
  entity: Entity)
