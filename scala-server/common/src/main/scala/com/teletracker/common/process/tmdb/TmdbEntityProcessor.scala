package com.teletracker.common.process.tmdb

import com.google.common.cache.Cache
import com.teletracker.common.cache.{JustWatchLocalCache, TmdbLocalCache}
import com.teletracker.common.db.access.{
  NetworksDbAccess,
  ThingsDbAccess,
  TvShowDbAccess
}
import com.teletracker.common.db.model
import com.teletracker.common.db.model._
import com.teletracker.common.external.justwatch.JustWatchClient
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.inject.RecentlyProcessedCollections
import com.teletracker.common.model.justwatch.PopularItem
import com.teletracker.common.model.tmdb
import com.teletracker.common.model.tmdb.{Person => TmdbPerson, _}
import com.teletracker.common.process.ProcessQueue
import com.teletracker.common.process.tmdb.TmdbEntity.Entities
import com.teletracker.common.process.tmdb.TmdbProcessMessage.ProcessMovie
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.common.util.{
  NetworkCache,
  TmdbMovieImporter,
  TmdbShowImporter
}
import com.teletracker.common.util.json.circe._
import javax.inject.Inject
import shapeless.ops.coproduct.{Folder, Mapper}
import shapeless.tag.@@
import shapeless.{:+:, tag, CNil, Coproduct}
import java.sql.Timestamp
import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

object TmdbEntity {
  type Entities = Movie :+: TvShow :+: TmdbPerson :+: CNil
  type Ids =
    (Int @@ MovieId) :+: (Int @@ TvShowId) :+: (Int @@ PersonId) :+: CNil
}

object TmdbEntityProcessor {
  sealed trait ProcessResult
  case class ProcessSuccess(
    tmdbId: String,
    savedThing: ThingLike)
      extends ProcessResult
  case class ProcessFailure(error: Throwable) extends ProcessResult
}

class TmdbEntityProcessor @Inject()(
  tmdbClient: TmdbClient,
  thingsDbAccess: ThingsDbAccess,
  networksDbAccess: NetworksDbAccess,
  expander: ItemExpander,
  tvShowDbAccess: TvShowDbAccess,
  networkCache: NetworkCache,
  justWatchClient: JustWatchClient,
  cache: TmdbLocalCache,
  justWatchLocalCache: JustWatchLocalCache,
  processQueue: ProcessQueue[TmdbProcessMessage],
  movieImporter: TmdbMovieImporter,
  showImporter: TmdbShowImporter,
  tmdbSynchronousProcessor: TmdbSynchronousProcessor,
  @RecentlyProcessedCollections recentlyProcessedCollections: Cache[
    Integer,
    java.lang.Boolean
  ]
)(implicit executionContext: ExecutionContext) {
  import TmdbEntityProcessor._

  def processSearchResults(
    results: List[Movie :+: TvShow :+: CNil]
  ): List[Future[ProcessResult]] = {
    results.map(_.map(expander.ExpandItem)).map(_.fold(ResultProcessor))
  }

  def expandAndProcessEntity(e: Entities): Future[ProcessResult] = {
    e.map(expander.ExpandItem).fold(ResultProcessor)
  }

  def expandAndProcessEntityId(e: TmdbEntity.Ids): Future[ProcessResult] = {
    e.map(expander.ExpandItem).fold(ResultProcessor)
  }

  def processResults[X <: Coproduct, M <: Coproduct, F](
    results: List[X]
  )(implicit m: Mapper.Aux[expander.ExpandItem.type, X, M],
    f: Folder.Aux[ResultProcessor.type, M, F]
  ): List[F] = {
    results.map(m.apply).map(f.apply)
  }

  def processResult[X <: Coproduct, M <: Coproduct, F](
    result: X
  )(implicit m: Mapper.Aux[expander.ExpandItem.type, X, M],
    f: Folder.Aux[ResultProcessor.type, M, F]
  ): F = {
    f(m(result))
  }

  /**
    * Polymorphic function that operates on model types from TMDb search results
    */
  object ResultProcessor extends shapeless.Poly1 {
    implicit val atMovie: Case.Aux[Movie, Future[ProcessResult]] = at(
      movieImporter.handleMovie
    )

    implicit val atShow: Case.Aux[TvShow, Future[ProcessResult]] = at(
      handleShow(_, handleSeasons = false)
    )

    implicit val atPerson: Case.Aux[TmdbPerson, Future[ProcessResult]] = at(
      handlePerson
    )

    implicit def atFutureN[N](
      implicit c: Case.Aux[N, Future[ProcessResult]]
    ): Case.Aux[Future[N], Future[ProcessResult]] = at {
      _.flatMap(c.apply(_))
    }
  }

  def handleMovie(movie: Movie): Future[ProcessResult] = {
    movieImporter.handleMovie(movie)
  }

  private def matchJustWatchMovie(
    movie: Movie,
    popularItems: List[PopularItem]
  ): Option[PopularItem] = {
    popularItems.find(item => {
      val idMatch = item.scoring
        .getOrElse(Nil)
        .exists(
          s =>
            s.provider_type == "tmdb:id" && s.value.toInt.toString == movie.id.toString
        )
      val nameMatch = item.title.exists(movie.title.contains)
      val originalMatch =
        movie.original_title.exists(item.original_title.contains)
      val yearMatch = item.original_release_year.exists(year => {
        movie.release_date
          .filter(_.nonEmpty)
          .map(LocalDate.parse(_).getYear)
          .contains(year)
      })

      idMatch || (nameMatch && yearMatch) || (originalMatch && yearMatch)
    })
  }

  def handleShow(
    show: TvShow,
    handleSeasons: Boolean
  ): Future[ProcessResult] = {
    showImporter.handleShow(show, handleSeasons)
  }

  def handlePerson(person: TmdbPerson): Future[ProcessResult] = {
    def insertAssociations(
      personId: UUID,
      thingId: UUID,
      typ: PersonAssociationType,
      character: Option[String]
    ): Future[PersonThing] = {
      thingsDbAccess.upsertPersonThing(
        PersonThing(personId, thingId, typ, character)
      )
    }

    val now = OffsetDateTime.now()

    val personSave = Promise
      .fromTry(ThingFactory.makeThing(person))
      .future
      .flatMap(thing => {
        thingsDbAccess
          .saveThing(
            thing,
            Some(ExternalSource.TheMovieDb -> person.id.toString)
          )
          .map(ProcessSuccess(person.id.toString, _))
      })
      .recover {
        case NonFatal(e) => ProcessFailure(e)
      }

    val creditsSave = person.combined_credits
      .map(credits => {
        val castById = credits.cast.map(c => c.id.toString -> c).toMap

        val castFut =
          tmdbSynchronousProcessor.processPersonCredits(credits.cast)
        val crewFut =
          tmdbSynchronousProcessor.processPersonCredits(credits.crew)

        for {
          savedPerson <- personSave
          savedCastMemberships <- castFut
          savedCrewMemberships <- crewFut
        } yield {
          savedPerson match {
            case ProcessSuccess(_, savedPerson) =>
              val castInserts = savedCastMemberships.map {
                case (id, thing) =>
                  val characterName = castById.get(id).flatMap(_.character)
                  () =>
                    insertAssociations(
                      savedPerson.id,
                      thing.id,
                      PersonAssociationType.Cast,
                      characterName
                    ).map(Some(_)).recover {
                      case NonFatal(ex) => None
                    }
              }

              val crewInserts = savedCrewMemberships.map {
                case (_, thing) =>
                  () =>
                    insertAssociations(
                      savedPerson.id,
                      thing.id,
                      PersonAssociationType.Crew,
                      None
                    ).map(Some(_)).recover {
                      case NonFatal(ex) => None
                    }
              }

              SequentialFutures
                .batchedIterator((castInserts ++ crewInserts).iterator, 8)(
                  batch => {
                    Future.sequence(batch.map(_.apply()))
                  }
                )
                .map(_.flatten)

            case ProcessFailure(ex) =>
              // TODO: Log
              Future.successful(Nil)
          }
        }
      })
      .getOrElse(Future.successful(Nil))

    for {
      _ <- creditsSave
      p <- personSave
    } yield p
  }

  def handleExternalIds(
    entity: Either[Thing, model.TvShowEpisode],
    externalIds: Option[tmdb.ExternalIds],
    tmdbId: Option[String]
  ): Future[Option[ExternalId]] = {
    if (externalIds.isDefined || tmdbId.isDefined) {
      val id = tmdbId.orElse(externalIds.map(_.id.toString)).get
      thingsDbAccess.findExternalIdsByTmdbId(id).flatMap {
        case None =>
          val baseEid = model.ExternalId(
            None,
            None,
            None,
            Some(id),
            externalIds.flatMap(_.imdb_id),
            None,
            new Timestamp(System.currentTimeMillis())
          )

          val eid = entity match {
            case Left(t)  => baseEid.copy(thingId = Some(t.id))
            case Right(t) => baseEid.copy(tvEpisodeId = t.id)
          }

          thingsDbAccess.upsertExternalIds(eid).map(Some(_))

        case Some(x) => Future.successful(Some(x))
      }
    } else {
      Future.successful(None)
    }
  }

  def processCollection(
    thingId: UUID,
    collection: BelongsToCollection
  ): Future[Unit] = {
    var notFound = false
    recentlyProcessedCollections.synchronized {
      recentlyProcessedCollections.get(collection.id, () => {
        notFound = true
        true
      })
    }

    if (notFound) {
      recentlyProcessedCollections.put(collection.id, true)

      val dbCollectionFut =
        thingsDbAccess.findCollectionByTmdbId(collection.id.toString)

      val fullCollectionFut = tmdbClient
        .getCollection(collection.id)

      val completeFut = for {
        dbCollection <- dbCollectionFut
        fullCollection <- fullCollectionFut
      } yield {
        dbCollection match {
          case Some(foundDbCollection) =>
            val updatedCollection = foundDbCollection.copy(
              name = fullCollection.name,
              overview = fullCollection.overview
            )
            thingsDbAccess
              .updateCollection(
                updatedCollection
              )
              .map(_ => updatedCollection)

          case None =>
            thingsDbAccess.insertCollection(
              model.Collection(
                id = -1,
                name = fullCollection.name,
                fullCollection.overview,
                Some(fullCollection.id.toString)
              )
            )
        }

        Future.sequence(
          fullCollection.parts
            .map(
              part =>
                TmdbProcessMessage.make(ProcessMovie(tag[MovieId](part.id)))
            )
            .map(processQueue.enqueue)
        )
      }

      completeFut.flatMap(identity).map(_ => {})
    } else {
      Future.unit
    }
  }
}
