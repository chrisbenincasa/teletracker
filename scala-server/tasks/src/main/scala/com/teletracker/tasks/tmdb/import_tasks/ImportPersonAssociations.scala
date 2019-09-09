package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.db.access.AsyncThingsDbAccess
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
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ImportPersonAssociations @Inject()(
  thingsDbAccess: AsyncThingsDbAccess
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
    val pageSize = args.valueOrDefault("pageSize", 100)
    val specificThingId = args.value[UUID]("thingId")
    val mode = args.valueOrDefault("mode", "insert")
    val parallelism = args.valueOrDefault("parallelism", 8)

    val f = new File("output.csv")
    val os = new PrintStream(new BufferedOutputStream(new FileOutputStream(f)))

    val procFunc = if (mode == "insert") {
      handleError(handleSingleThing)
    } else if (mode == "csv") {
      handleError(handleSingleThingCsv(_)(synchronized(os.println)))
    } else {
      throw new IllegalArgumentException
    }

    val processed = new AtomicInteger(0)

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
          case Some(thing) => procFunc(thing)
        }
        .await()
    } else {
      thingsDbAccess
        .loopThroughAllThings(offset, pageSize, limit = limit) { things =>
          SequentialFutures.batchedIterator(
            things.iterator,
            parallelism
          )(batch => {
            Future
              .sequence(batch.map(procFunc))
              .map(_ => {
                val total = processed.addAndGet(batch.size)
                if (total % 10000 == 0) {
                  logger.info(s"Processed ${total} items so far")
                }
              })
          })
        }
        .await()
    }

    os.close()
  }

  private def handleError(
    fn: ThingRaw => Future[Unit]
  ): ThingRaw => Future[Unit] = { thing =>
    {
      fn(thing).recover {
        case NonFatal(e) =>
          logger.error(s"Error while processing thing ID = ${thing.id}", e)
      }
    }
  }

  private def handleSingleThingCsv(
    thing: ThingRaw
  )(
    append: String => Unit
  ): Future[Unit] = {
    logger.debug(s"Handling thing id = ${thing.id}")

    extractCastMembers(thing)
      .map(members => {
        extractCastMembers(
          thing.id,
          members,
          PersonAssociationType.Cast
        ).map(personThings => {
          personThings.map(personThingToCsv).foreach(append)
        })
      })
      .getOrElse(Future.unit)
  }

  private def personThingToCsv(personThing: PersonThing) = {
    List(
      personThing.personId,
      personThing.thingId,
      personThing.relationType,
      personThing.characterName.getOrElse(""),
      personThing.order.getOrElse("")
    ).map(_.toString)
      .map(
        _.replaceAllLiterally("\\\"", "'")
          .replaceAllLiterally("\"", "\"\"")
      )
      .map("\"" + _ + "\"")
      .mkString(",")
  }

  private def handleSingleThing(thing: ThingRaw): Future[Unit] = {
    logger.debug(s"Handling thing id = ${thing.id}")

    extractCastMembers(thing)
      .map(members => {
        extractCastMembers(
          thing.id,
          members,
          PersonAssociationType.Cast
        ).flatMap(personThings => {
            Future
              .sequence(personThings.map(thingsDbAccess.upsertPersonThing))
          })
          .map(_ => {})
      })
      .getOrElse(Future.unit)
  }

  private def extractCastMembers(thing: ThingRaw) = {
    thing.metadata
      .flatMap(meta => {
        movieCast
          .getOption(meta)
          .orElse(showCast.getOption(meta))
          .flatten
      })
  }

  private def extractCastMembers(
    thingId: UUID,
    castMembers: List[CastMember],
    relationType: PersonAssociationType
  ): Future[List[PersonThing]] = {
    val personTmdbIds = castMembers.map(_.id.toString)
    val doesntContain =
      personTmdbIds.filterNot(personIdCache.contains).toSet

    logger.debug(s"Found ${castMembers.size} members for thing ID = $thingId")

    thingsDbAccess
      .findPeopleIdsByTmdbIds(doesntContain)
      .map(ids => {
        ids.foreach {
          case (tmdbId, id) => personIdCache.put(tmdbId, id)
        }

        castMembers.flatMap(member => {
          Option(personIdCache.get(member.id.toString)).map(personId => {
            PersonThing(
              personId,
              thingId,
              relationType,
              member.character.orElse(member.character_name),
              member.order
            )
          })
        })
      })
  }
}
