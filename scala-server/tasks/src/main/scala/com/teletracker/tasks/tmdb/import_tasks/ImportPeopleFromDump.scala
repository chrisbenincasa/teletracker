package com.teletracker.tasks.tmdb.import_tasks

import com.google.cloud.storage.Storage
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{
  ExternalSource,
  PersonAssociationType,
  PersonThing,
  ThingLike
}
import com.teletracker.common.model.tmdb.Person
import com.teletracker.common.process.tmdb.TmdbSynchronousProcessor
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ImportPeopleFromDump @Inject()(
  storage: Storage,
  thingsDbAccess: ThingsDbAccess,
  tmdbSynchronousProcessor: TmdbSynchronousProcessor
)(implicit executionContext: ExecutionContext)
    extends ImportTmdbDumpTask[Person](storage, thingsDbAccess) {

  override protected def extraWork(
    thingLike: ThingLike,
    entity: Person
  ): Future[Unit] = {
    entity.combined_credits
      .map(credits => {
        credits.cast.map(_.id.toString) ++ credits.crew.map(_.id.toString)
      })
      .map(_.toSet)
      .map(
        thingsDbAccess
          .findThingsByExternalIds(ExternalSource.TheMovieDb, _, None)
      )
      .map(_.flatMap(things => {
        val thingByExternalId = things.collect {
          case (externalId, thing) if externalId.tmdbId.isDefined =>
            externalId.tmdbId.get -> thing
        }.toMap

        val cast = entity.combined_credits
          .map(_.cast)
          .map(
            _.flatMap(
              credit =>
                thingByExternalId
                  .get(credit.id.toString)
                  .map(
                    thing =>
                      saveAssociations(
                        thingLike.id,
                        thing.id,
                        PersonAssociationType.Cast,
                        credit.character
                      )
                  )
            )
          )
          .map(Future.sequence(_))
          .map(_.map(_ => {}))
          .getOrElse(Future.unit)

        val crew = entity.combined_credits
          .map(_.crew)
          .map(
            _.flatMap(
              credit =>
                thingByExternalId
                  .get(credit.id.toString)
                  .map(
                    thing =>
                      saveAssociations(
                        thingLike.id,
                        thing.id,
                        PersonAssociationType.Crew,
                        credit.character
                      )
                  )
            )
          )
          .map(Future.sequence(_))
          .map(_.map(_ => {}))
          .getOrElse(Future.unit)

        (for {
          _ <- cast
          _ <- crew
        } yield {}).recover {
          case NonFatal(e) =>
            logger.error("Hit error during estra work", e)
        }
      }))
      .getOrElse(Future.unit)

  }

  private def saveAssociations(
    personId: UUID,
    thingId: UUID,
    personAssociationType: PersonAssociationType,
    character: Option[String]
  ): Future[PersonThing] = {
    thingsDbAccess.upsertPersonThing(
      PersonThing(personId, thingId, personAssociationType, character)
    )
  }
}
