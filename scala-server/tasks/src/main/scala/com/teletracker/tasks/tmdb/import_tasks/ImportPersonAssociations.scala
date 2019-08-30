package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{
  PersonAssociationType,
  PersonThing,
  ThingRaw
}
import com.teletracker.common.model.tmdb.CastMember
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.tasks.TeletrackerTask
import javax.inject.Inject
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future}

class ImportPersonAssociations @Inject()(
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTask {
  private val logger = LoggerFactory.getLogger(getClass)

  import io.circe.optics.JsonPath._

  private val movieCast =
    root.themoviedb.movie.credits.cast.as[Option[List[CastMember]]]
  private val showCast =
    root.themoviedb.show.credits.cast.as[Option[List[CastMember]]]

  private val movieCrew =
    root.themoviedb.movie.credits.crew.as[Option[List[CastMember]]]
  private val showCrew =
    root.themoviedb.show.credits.crew.as[Option[List[CastMember]]]

  private val personIdCache = new ConcurrentHashMap[String, UUID]()

  override def run(args: Args): Unit = {
    val offset = args.valueOrDefault[Int]("offset", 0)
    val limit = args.valueOrDefault("limit", -1)
    val specificThingId = args.value[UUID]("thingId")

    if (specificThingId.isDefined) {
      thingsDbAccess
        .findThingByIdRaw(specificThingId.get)
        .flatMap {
          case None =>
            Future.failed(
              new IllegalArgumentException(
                s"Could not find thing with id = ${specificThingId.get}"
              )
            )
          case Some(thing) => handleSingleThing(thing)
        }
        .await()
    } else {
      thingsDbAccess
        .loopThroughAllThings(offset, limit = limit) { things =>
          SequentialFutures
            .serialize(things)(handleSingleThing)
            .map(_ => {})
        }
        .await()
    }
  }

  private def handleSingleThing(thing: ThingRaw): Future[Unit] = {
    logger.info(s"Handling thing id = ${thing.id}")

    thing.metadata
      .flatMap(meta => {
        movieCast
          .getOption(meta)
          .orElse(showCast.getOption(meta))
          .flatten
          .map(
            processCastMembers(
              thing.id,
              _,
              PersonAssociationType.Cast
            )
          )
          .map(_.map(_ => {}))
      })
      .getOrElse(Future.unit)
  }

  private def processCastMembers(
    thingId: UUID,
    castMembers: List[CastMember],
    relationType: PersonAssociationType
  ): Future[List[PersonThing]] = {
    val personTmdbIds = castMembers.map(_.id.toString)
    val doesntContain =
      personTmdbIds.filterNot(personIdCache.contains).toSet

    logger.info(s"Found ${castMembers.size} members for thing ID = $thingId")

    thingsDbAccess
      .findPeopleByTmdbIds(doesntContain)
      .flatMap(ids => {
        ids.foreach {
          case (tmdbId, id) => personIdCache.put(tmdbId, id)
        }

        val relations = castMembers.flatMap(member => {
          Option(personIdCache.get(member.id.toString)).map(personId => {
            PersonThing(
              personId,
              thingId,
              relationType,
              member.character.orElse(member.character_name)
            )
          })
        })

        Future.sequence(relations.map(thingsDbAccess.upsertPersonThing))
      })
  }
}
