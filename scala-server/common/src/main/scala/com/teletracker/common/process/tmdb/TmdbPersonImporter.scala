package com.teletracker.common.process.tmdb

import com.google.inject.assistedinject.Assisted
import com.teletracker.common.db.BaseDbProvider
import com.teletracker.common.db.access.ThingsDbAccess
import javax.inject.{Inject, Provider}
import scala.concurrent.ExecutionContext

class TmdbPersonImporter @Inject()(
  thingsDbAccess: ThingsDbAccess,
  tmdbEntityProcessor: Provider[TmdbEntityProcessor],
  tmdbPersonCreditProcessor: TmdbPersonCreditProcessor
)(implicit executionContext: ExecutionContext)
    extends TmdbImporter(thingsDbAccess) {
  // TODO: Figure out why this breaks the compiler
//  def handlePerson(person: Person): Future[ProcessResult] = {
//    def insertAssociations(
//      personId: UUID,
//      thingId: UUID,
//      typ: PersonAssociationType,
//      character: Option[String],
//      order: Option[Int]
//    ): Future[PersonThing] = {
//      thingsDbAccess.upsertPersonThing(
//        model.PersonThing(personId, thingId, typ, character, order)
//      )
//    }
//
//    val personSave: Future[ProcessResult] = Promise
//      .fromTry(ThingFactory.makeThing(person))
//      .future
//      .flatMap(thing => {
//        thingsDbAccess
//          .saveThing(
//            thing,
//            Some(ExternalSource.TheMovieDb -> person.id.toString)
//          )
//          .map(ProcessSuccess(person.id.toString, _))
//      })
//      .recover {
//        case NonFatal(e) => ProcessFailure(e)
//      }
//
//    val creditsSave = person.combined_credits
//      .map(credits => {
//        for {
//          // TODO: Push to queue
//          savedPerson <- personSave
//          _ <- SequentialFutures.serialize(credits.cast, Some(250 millis)) {
//            castMember =>
//              savedPerson match {
//                case ProcessSuccess(_, savedPerson) =>
//                  tmdbPersonCreditProcessor
//                    .processPersonCredits(castMember :: Nil)
//                    .flatMap {
//                      case ProcessSuccess(_, savedCastMember) =>
//                        insertAssociations(
//                          savedPerson.id,
//                          savedCastMember.id,
//                          PersonAssociationType.Cast,
//                          castMember.character,
//                          None
//                        ).map(Some(_))
//                      case ProcessFailure(ex) => Future.successful(None)
//                    }
//                case ProcessFailure(ex) =>
//                  Future.successful(None)
//              }
//          }
//          _ <- SequentialFutures.serialize(credits.crew, Some(250 millis)) {
//            castMember =>
//              savedPerson match {
//                case ProcessSuccess(_, savedPerson) =>
//                  tmdbPersonCreditProcessor
//                    .processPersonCredits(castMember :: Nil)
//                    .flatMap {
//                      case ProcessSuccess(_, savedCastMember) =>
//                        insertAssociations(
//                          savedPerson.id,
//                          savedCastMember.id,
//                          PersonAssociationType.Crew,
//                          None,
//                          None
//                        ).map(Some(_))
//                      case ProcessFailure(ex) => Future.successful(None)
//                    }
//                case ProcessFailure(ex) =>
//                  Future.successful(None)
//              }
//          }
//        } yield {}
//      })
//      .getOrElse(Future.successful(Nil))
//
//    for {
//      _ <- creditsSave
//      p <- personSave
//    } yield p
//  }
}
