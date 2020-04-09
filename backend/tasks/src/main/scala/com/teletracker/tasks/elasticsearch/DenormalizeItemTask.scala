package com.teletracker.tasks.elasticsearch

import com.teletracker.common.tasks.{model, TeletrackerTask}
import com.teletracker.common.elasticsearch.denorm.DenormalizedItemUpdater
import com.teletracker.common.elasticsearch._
import com.teletracker.common.tasks.model.DenormalizeItemTaskArgs
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.{AsyncStream, IdOrSlug}
import io.circe.Encoder
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class DenormalizeItemTask @Inject()(
  itemLookup: ItemLookup,
  personLookup: PersonLookup,
  personUpdater: PersonUpdater,
  denormalizedItemUpdater: DenormalizedItemUpdater
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTask {
  override type TypedArgs = DenormalizeItemTaskArgs

  implicit override protected def typedArgsEncoder
    : Encoder[DenormalizeItemTaskArgs] =
    io.circe.generic.semiauto.deriveEncoder

  override def preparseArgs(args: Args): DenormalizeItemTaskArgs =
    model.DenormalizeItemTaskArgs(
      itemId = args.valueOrThrow[UUID]("itemId"),
      creditsChanged = args.valueOrThrow[Boolean]("creditsChanged"),
      crewChanged = args.valueOrThrow[Boolean]("crewChanged"),
      dryRun = args.valueOrDefault("dryRun", true)
    )

  override protected def runInternal(_args: Args): Unit = {
    val args = preparseArgs(_args)

    itemLookup
      .lookupItem(Left(args.itemId), None, materializeJoins = false)
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

          peopleUpdateFut

        // Denorm details to list tracking indexes

        case None =>
          Future.failed(
            new IllegalAccessException(
              s"Could not find item with id = ${args.itemId}"
            )
          )
      }
      .await()

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
            .info(s"Updating person = ${person.id}, new credit: ${newCredit}")
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
