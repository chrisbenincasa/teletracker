package com.teletracker.tasks.elasticsearch

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch._
import com.teletracker.common.elasticsearch.denorm.DenormalizedItemUpdater
import com.teletracker.common.elasticsearch.model.{
  EsItem,
  EsItemCastMember,
  EsItemCrewMember,
  EsPerson
}
import com.teletracker.common.model.tmdb.{
  CastMember,
  MovieCredits,
  TvShowCredits
}
import com.teletracker.common.process.tmdb.ItemExpander
import com.teletracker.common.tasks.model.DenormalizePersonTaskArgs
import com.teletracker.common.tasks.{model, TeletrackerTask}
import com.teletracker.common.util.AsyncStream
import com.teletracker.common.util.Futures._
import io.circe.Encoder
import javax.inject.Inject
import java.util.UUID
import java.util.concurrent.Executors
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class DenormalizePersonTask @Inject()(
  itemLookup: ItemLookup,
  personLookup: PersonLookup,
  personUpdater: PersonUpdater,
  denormalizedItemUpdater: DenormalizedItemUpdater,
  itemExpander: ItemExpander,
  itemUpdater: ItemUpdater
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTask {
  override type TypedArgs = DenormalizePersonTaskArgs

  override def retryable: Boolean = true

  implicit override protected def typedArgsEncoder
    : Encoder[DenormalizePersonTaskArgs] =
    io.circe.generic.semiauto.deriveEncoder

  override def preparseArgs(args: Args): DenormalizePersonTaskArgs =
    model.DenormalizePersonTaskArgs(
      personId = args.valueOrThrow[UUID]("personId"),
      dryRun = args.valueOrDefault("dryRun", true)
    )

  private def getIdsToLookup(
    itemIds: Set[UUID],
    itemsById: Map[UUID, Option[EsItem]]
  ) = {
    val foundItems = itemsById.collect {
      case (id, Some(item)) if itemIds.contains(id) => item
    }
    val missingItems = itemIds -- foundItems.map(_.id).toSet

    if (missingItems.nonEmpty) {
      logger
        .warn(s"Did not find the following items: ${missingItems
          .mkString("(", ", ", ")")}")
    }

    foundItems
  }

  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  override protected def runInternal(_args: Args): Unit = {
    val args = preparseArgs(_args)

    val futStream = personLookup
      .lookupPerson(
        Left(args.personId),
        materializeCredits = false,
        creditsLimit = None
      )
      .flatMap {
        case Some((person, _)) =>
          val castItemIds = person.cast_credits.getOrElse(Nil).map(_.id)
          val crewItemIds = person.crew_credits.getOrElse(Nil).map(_.id)

          itemLookup
            .lookupItemsByIds((castItemIds ++ crewItemIds).toSet)
            .map(itemsById => {
              val castItems = getIdsToLookup(castItemIds.toSet, itemsById)
              val crewItems = getIdsToLookup(crewItemIds.toSet, itemsById)

              (castItems ++ crewItems).groupBy(_.id).mapValues(_.head).values
            })
            .map(items => {
              AsyncStream
                .fromSeq(items.toSeq)
                .delayedMapF(250 millis, scheduler)(item => {
                  val fetchFut = item.externalIdsGrouped
                    .get(ExternalSource.TheMovieDb)
                    .map(_.toInt)
                    .map(id => {
                      item.`type` match {
                        case ItemType.Movie =>
                          logger.info(
                            s"Looking up movie (${item.original_title}) in tmdb"
                          )
                          itemExpander
                            .expandMovie(id)
                            .map(
                              _.credits
                                .flatMap(handleRawTvCredits(person, item, _))
                            )
                        case ItemType.Show =>
                          logger.info(
                            s"Looking up show (${item.original_title}) in tmdb"
                          )
                          itemExpander
                            .expandTvShow(id)
                            .map(
                              _.credits
                                .flatMap(handleRawTvCredits(person, item, _))
                            )
                        case ItemType.Person =>
                          throw new IllegalStateException()
                      }
                    })
                    .getOrElse(Future.successful(None))

                  fetchFut.flatMap {
                    case Some(item) if args.dryRun =>
                      Future.successful {
                        logger.info(
                          s"Would've updated item ${item.id} with credit for person ${person.id}"
                        )
                      }
                    case Some(item) =>
                      logger.info(
                        s"Updating item ${item.id} with credit for person ${person.id}"
                      )
                      itemUpdater.update(item).map(_ => {})
                    case None => Future.unit
                  }
                })
            })
        case None =>
          throw new IllegalArgumentException(
            s"Could not find person with id = ${args.personId}"
          )
      }

    AsyncStream.fromFuture(futStream).flatten.force.await()
  }

  private def handleRawTvCredits(
    person: EsPerson,
    item: EsItem,
    credits: MovieCredits
  ) = {
    person.externalIdsGrouped
      .get(ExternalSource.TheMovieDb)
      .map(_.toInt)
      .map(personId => {
        val castMember = credits.cast.getOrElse(Nil).find(_.id == personId)
        updateCastAndCrew(person, item, castMember)
      })
  }

  private def handleRawTvCredits(
    person: EsPerson,
    item: EsItem,
    credits: TvShowCredits
  ) = {
    person.externalIdsGrouped
      .get(ExternalSource.TheMovieDb)
      .map(_.toInt)
      .map(personId => {
        val castMember = credits.cast.getOrElse(Nil).find(_.id == personId)
        updateCastAndCrew(person, item, castMember)
      })
  }

  private def updateCastAndCrew(
    person: EsPerson,
    item: EsItem,
    rawCastMember: Option[CastMember]
  ) = {
    val newCast =
      person.cast_credits.getOrElse(Nil).find(_.id == item.id) match {
        case Some(member) =>
          val newCastMember = EsItemCastMember(
            character = member.character,
            id = person.id,
            order = rawCastMember
              .flatMap(_.order)
              .getOrElse(item.cast.getOrElse(Nil).size),
            name = person.name.getOrElse(""),
            slug = person.slug
          )

          item.cast match {
            case Some(existingCast) =>
              Some(
                existingCast.filterNot(_.id == person.id) :+ newCastMember
              )
            case None =>
              Some(List(newCastMember))
          }
        case None => item.cast
      }

    val newCrew =
      person.crew_credits.getOrElse(Nil).find(_.id == item.id) match {
        case Some(member) =>
          val newCrewMember = EsItemCrewMember(
            department = member.department,
            id = person.id,
            name = person.name.getOrElse(""),
            slug = person.slug,
            job = member.job,
            order = None
          )

          item.crew match {
            case Some(existingCrew) =>
              Some(
                existingCrew.filterNot(_.id == person.id) :+ newCrewMember
              )
            case None =>
              Some(List(newCrewMember))
          }
        case None => item.crew
      }

    item.copy(
      cast = newCast,
      crew = newCrew
    )
  }
}
