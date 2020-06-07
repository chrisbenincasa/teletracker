package com.teletracker.common.elasticsearch.denorm

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.ItemUpdater.UpdateUserTagsScript
import com.teletracker.common.elasticsearch.model._
import com.teletracker.common.elasticsearch._
import com.teletracker.common.tasks.model.DenormalizeItemTaskArgs
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.json.{IdentityFolder, IdentityJavaFolder}
import com.teletracker.common.util.{AsyncStream, IdOrSlug}
import io.circe.Json
import io.circe.syntax._
import javax.inject.Inject
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.{
  BulkByScrollResponse,
  UpdateByQueryRequest
}
import org.elasticsearch.script.{Script, ScriptType}
import org.slf4j.LoggerFactory
import java.util.UUID
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object DenormalizedItemUpdater {
  final private val UpdateDenormalizedItemScriptSource =
    """ctx._source.item = params.item"""

  final private def UpdateDenormalizedItemScript(
    item: EsUserDenormalizedItem
  ) = {
    new Script(
      ScriptType.INLINE,
      "painless",
      UpdateDenormalizedItemScriptSource,
      Map[String, Object]("item" -> itemAsMap(item)).asJava
    )
  }

  private def itemAsMap(
    item: EsUserDenormalizedItem
  ): java.util.Map[String, Any] = {
    item.asJson.asObject.get.toMap
      .mapValues(_.foldWith(IdentityJavaFolder))
      .asJava
  }
}

class DenormalizedItemUpdater @Inject()(
  teletrackerConfig: TeletrackerConfig,
  itemLookup: ItemLookup,
  elasticsearchExecutor: ElasticsearchExecutor,
  personLookup: PersonLookup,
  personUpdater: PersonUpdater
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {
  import DenormalizedItemUpdater._

  private val logger = LoggerFactory.getLogger(getClass)

  def updateUserItem(item: EsUserItem): Future[Unit] = {
    val updateDenormRequest =
      new UpdateRequest(
        teletrackerConfig.elasticsearch.user_items_index_name,
        EsUserItem.makeId(item.user_id, item.item_id)
      ).doc(item.asJson.dropNullValues.noSpaces, XContentType.JSON)
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)

    elasticsearchExecutor.update(updateDenormRequest).map(_ => {})
  }

  def updateUserItem(
    id: String,
    item: Json
  ): Future[Unit] = {
    val updateDenormRequest =
      new UpdateRequest(
        teletrackerConfig.elasticsearch.user_items_index_name,
        id
      ).doc(item.dropNullValues.noSpaces, XContentType.JSON)
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)

    elasticsearchExecutor.update(updateDenormRequest).map(_ => {})
  }

  def fullyDenormalizeItem(args: DenormalizeItemTaskArgs): Future[Unit] = {
    itemLookup
      .lookupItem(
        Left(args.itemId),
        None,
        shouldMateralizeCredits = false,
        shouldMaterializeRecommendations = false
      )
      .flatMap {
        case Some(item) =>
          // Denorm cast if necessary
          val castMembersNeedingUpdateFut = if (args.creditsChanged) {
            val castMemberIds = item.rawItem.cast.getOrElse(Nil).map(_.id)

            personLookup
              .lookupPeople(castMemberIds.map(IdOrSlug.fromUUID))
              .map(cast => {
                val foundMembers = cast.map(_.id).toSet
                val missingMembers = castMemberIds.toSet -- foundMembers

                if (missingMembers.nonEmpty) {
                  logger
                    .warn(s"Did not find the following people: ${missingMembers
                      .mkString("(", ", ", ")")}")
                }

                cast.flatMap(updateCastMember(_, item.rawItem))
              })
          } else {
            Future.successful(Nil)
          }

          // Denorm crew if necessary
          val crewMembersNeedingUpdateFut = if (args.crewChanged) {
            val crewMemberIds = item.rawItem.crew.getOrElse(Nil).map(_.id)

            personLookup
              .lookupPeople(crewMemberIds.map(IdOrSlug.fromUUID))
              .map(cast => {
                val foundMembers = cast.map(_.id).toSet
                val missingMembers = crewMemberIds.toSet -- foundMembers

                if (missingMembers.nonEmpty) {
                  logger
                    .warn(s"Did not find the following people: ${missingMembers
                      .mkString("(", ", ", ")")}")
                }

                cast.flatMap(updateCrewMember(_, item.rawItem))
              })
          } else {
            Future.successful(Nil)
          }

          val allPeopleToUpdateFut = for {
            castMembersNeedingUpdate <- castMembersNeedingUpdateFut
            crewMembersNeedingUpdate <- crewMembersNeedingUpdateFut
          } yield {
            // Combine people that are updated
            val crewUpdatesById =
              crewMembersNeedingUpdate.map(person => person.id -> person).toMap
            val combinedUpdates = castMembersNeedingUpdate.map(person => {
              if (crewUpdatesById.contains(person.id)) {
                person.copy(
                  crew_credits = crewUpdatesById(person.id).crew_credits
                )
              } else {
                person
              }
            })

            val combinedUpdateIds = combinedUpdates.map(_.id).toSet
            val crewUpdates =
              crewUpdatesById.filterKeys(!combinedUpdateIds.contains(_)).values

            combinedUpdates ++ crewUpdates
          }

          val userItemDenormFut = if (args.dryRun) {
            Future.successful {
              logger.info("Would've denoramlized to user_items.")
            }
          } else {
            updateUserItems(item.rawItem).map(_ => {})
          }

          val peopleUpdateFut = if (args.dryRun) {
            allPeopleToUpdateFut.map(people => {
              logger.info(
                s"Would've denormalized item info to:\n${people.map(_.id).mkString("\n")}"
              )
            })
          } else {
            AsyncStream
              .fromFuture(allPeopleToUpdateFut)
              .flatMap(AsyncStream.fromSeq)
              .mapConcurrent(8)(person => {
                personUpdater.update(person)
              })
              .force
          }

          for {
            _ <- peopleUpdateFut
            _ <- userItemDenormFut
          } yield {}

        // Denorm details to list tracking indexes
        case None =>
          Future.failed(
            new IllegalAccessException(
              s"Could not find item with id = ${args.itemId}"
            )
          )
      }
  }

  def updateUserItems(itemId: UUID): Future[BulkByScrollResponse] = {
    itemLookup
      .lookupItem(
        Left(itemId),
        None,
        shouldMateralizeCredits = false,
        shouldMaterializeRecommendations = false
      )
      .flatMap {
        case Some(value) =>
          updateUserItems(value.rawItem)
        case None =>
          Future.failed(
            new IllegalArgumentException(s"Item with ID = ${itemId} not found.")
          )
      }
  }

  def updateUserItems(item: EsItem): Future[BulkByScrollResponse] = {
    val matchingItems =
      QueryBuilders.termQuery("item_id", item.id.toString)

    val updateByQueryRequest = new UpdateByQueryRequest(
      teletrackerConfig.elasticsearch.user_items_index_name
    )

    updateByQueryRequest.setQuery(matchingItems)
    updateByQueryRequest.setScript(
      UpdateDenormalizedItemScript(item.toDenormalizedUserItem)
    )
    updateByQueryRequest.setConflicts("proceed")
    updateByQueryRequest.setRequestsPerSecond(25)

    elasticsearchExecutor.updateByQuery(updateByQueryRequest)
  }

  private def updateCastMember(
    person: EsPerson,
    item: EsItem
  ) = {
    item.cast
      .getOrElse(Nil)
      .find(_.id == person.id)
      .flatMap(membership => {
        val newCredit = EsPersonCastCredit(
          character = membership.character,
          id = item.id,
          title = item.original_title
            .orElse(item.title.get.headOption)
            .getOrElse(""),
          `type` = item.`type`,
          slug = item.slug
        )

        val newCredits = person.cast_credits match {
          case Some(credits) if credits.contains(newCredit) =>
            None

          case Some(credits) =>
            Some(credits.filterNot(_.id == item.id) :+ newCredit)

          case None =>
            Some(List(newCredit))
        }

        val personToUpdate =
          newCredits.map(credits => person.copy(cast_credits = Some(credits)))

        personToUpdate.foreach(person => {
          logger
            .trace(s"Updating person = ${person.id}, new credit: ${newCredit}")
        })

        personToUpdate
      })
  }

  private def updateCrewMember(
    person: EsPerson,
    item: EsItem
  ) = {
    item.crew
      .getOrElse(Nil)
      .find(_.id == person.id)
      .flatMap(membership => {
        val newCredit = EsPersonCrewCredit(
          id = item.id,
          title = item.original_title
            .orElse(item.title.get.headOption)
            .getOrElse(""),
          `type` = item.`type`,
          slug = membership.slug,
          department = membership.department,
          job = membership.job
        )

        val newCredits = person.crew_credits match {
          case Some(credits) if credits.contains(newCredit) =>
            None

          case Some(credits) =>
            Some(credits.filterNot(_.id == item.id) :+ newCredit)

          case None =>
            Some(List(newCredit))
        }

        newCredits.map(credits => person.copy(crew_credits = Some(credits)))
      })
  }
}
