package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.denorm.DenormalizedItemUpdater
import com.teletracker.common.elasticsearch.model.{
  EsUserDenormalizedItem,
  EsUserItem
}
import com.teletracker.common.elasticsearch.{ItemLookup, UserItemsScroller}
import com.teletracker.common.tasks.UntypedTeletrackerTask
import javax.inject.Inject
import io.circe.parser._
import io.circe.syntax._
import com.teletracker.common.util.Futures._
import org.elasticsearch.index.query.QueryBuilders
import scala.concurrent.ExecutionContext

class FixBrokenListItems @Inject()(
  userItemsScroller: UserItemsScroller,
  teletrackerConfig: TeletrackerConfig,
  itemLookup: ItemLookup,
  denormalizedItemUpdater: DenormalizedItemUpdater
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val allUserItems = userItemsScroller
      .start(
        QueryBuilders.matchAllQuery()
      )
      .toList
      .await()

    allUserItems
      .filter(j => {
//        val id = j.asObject.get.apply("id").get.asString.get
        rawArgs.value[String]("id").forall(_ == j.id)
      })
      .foreach(item => {
        //        val obj = item.asObject.get.toMap
        //        obj("item").asObject match {
        //          case Some(currentItem) =>
        //            currentItem.apply("item") match {
        //              case Some(esItem) =>
        //                logger.info(s"Fixing item with id = ${obj("id").asString.get}")
        //                val newOne = obj.updated("item", esItem).asJson
        //                denormalizedItemUpdater
        //                  .indexUserItem(obj("id").asString.get, newOne)
        //                  .await()
        //              case None =>
        //            }
        //          case None =>
        //        }
        if (item.item.isEmpty) {
          itemLookup
            .lookupItem(
              Left(item.item_id),
              None,
              shouldMaterializeRecommendations = false,
              shouldMateralizeCredits = false
            )
            .await() match {
            case Some(value) =>
              logger.info(
                s"Updating user item: ${item.id} with item id = ${value.rawItem.id}"
              )
              val newItem =
                item.copy(item = Some(value.rawItem.toDenormalizedUserItem))
              denormalizedItemUpdater.updateUserItem(newItem).await()
            case None =>
          }
        }
      })

//    allUserItems
//      .filter(j => {
//        val id = j.asObject.get.apply("id").get.asString.get
//        rawArgs.value[String]("id").forall(_ == id)
//      })
//      .foreach(blob => {
//        val bustedKey = blob.asObject.get
//          .filter {
//            case (_, json) =>
//              json.isString && json.asString.get == "JSON"
//          }
//          .toMap
//          .headOption
//
//        bustedKey match {
//          case Some((key, _)) =>
//            parse(key) match {
//              case Left(value) =>
//              case Right(value) =>
//                val newItem =
//                  (blob.asObject.get.toMap - key).updated("item", value)
//                val newJson = newItem.asJson
//                denormalizedItemUpdater
//                  .indexUserItem(newItem("id").asString.get, newJson)
//                  .await()
//            }
//
//          case None =>
//        }

//      bustedKey match {
//        case Some((item, _)) =>
//          decode[EsUserDenormalizedItem](item) match {
//            case Left(value) =>
//              logger.error("Could not deser denormalized item", value)
//            case Right(denormItem) =>
//              blob.as[EsUserItem] match {
//                case Left(value) =>
//                  logger.error("Could not deser usesr item", value)
//                case Right(value) =>
//                  val fixedItem = value.copy(
//                    item = Some(denormItem)
//                  )
//
//                  println(fixedItem)
//              }
//          }
//        case None =>
//          logger.warn("Could not find matching busted key")
//      }
//      })
  }
}
