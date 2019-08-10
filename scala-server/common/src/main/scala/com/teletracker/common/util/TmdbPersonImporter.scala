package com.teletracker.common.util

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.process.tmdb.TmdbEntityProcessor
import javax.inject.{Inject, Provider}
import scala.concurrent.ExecutionContext

class TmdbPersonImporter @Inject()(
  thingsDbAccess: ThingsDbAccess,
  tmdbEntityProcessor: Provider[TmdbEntityProcessor]
)(implicit executionContext: ExecutionContext)
    extends TmdbImporter(thingsDbAccess) {
  // TODO: Figure out why this breaks the compiler
//  def handlePerson(person: Person): Future[ProcessResult] = {
//    def insertAssociations(
//      personId: UUID,
//      thingId: UUID,
//      typ: String
//    ): Future[PersonThing] = {
//      thingsDbAccess.upsertPersonThing(PersonThing(personId, thingId, typ))
//    }
//
//    val now = OffsetDateTime.now()
//
//    val personSave = Promise
//      .fromTry(ThingFactory.makePerson(person))
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
//                  tmdbEntityProcessor.get().processResult(castMember).flatMap {
//                    case ProcessSuccess(_, savedCastMember) =>
//                      insertAssociations(
//                        savedPerson.id,
//                        savedCastMember.id,
//                        "cast"
//                      ).map(Some(_))
//                    case ProcessFailure(ex) => Future.successful(None)
//                  }
//                case ProcessFailure(ex) =>
//                  Future.successful(None)
//              }
//          }
//          _ <- SequentialFutures.serialize(credits.crew, Some(250 millis)) {
//            castMember =>
//              savedPerson match {
//                case ProcessSuccess(_, savedPerson) =>
//                  tmdbEntityProcessor.get().processResult(castMember).flatMap {
//                    case ProcessSuccess(_, savedCastMember) =>
//                      insertAssociations(
//                        savedPerson.id,
//                        savedCastMember.id,
//                        "crew"
//                      ).map(Some(_))
//                    case ProcessFailure(ex) => Future.successful(None)
//                  }
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
