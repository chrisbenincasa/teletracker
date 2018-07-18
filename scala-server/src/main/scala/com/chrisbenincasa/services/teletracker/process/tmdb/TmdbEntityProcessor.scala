package com.chrisbenincasa.services.teletracker.process.tmdb

import com.chrisbenincasa.services.teletracker.db.{NetworksDbAccess, ThingsDbAccess, TvShowDbAccess, model}
import com.chrisbenincasa.services.teletracker.db.model._
import com.chrisbenincasa.services.teletracker.external.tmdb.TmdbClient
import com.chrisbenincasa.services.teletracker.model.tmdb
import com.chrisbenincasa.services.teletracker.model.tmdb._
import com.chrisbenincasa.services.teletracker.process.tmdb.TmdbEntity.{Entities, EntityIds}
import com.chrisbenincasa.services.teletracker.util.Slug
import com.chrisbenincasa.services.teletracker.util.execution.SequentialFutures
import com.chrisbenincasa.services.teletracker.util.json.circe._
import java.sql.Timestamp
import javax.inject.Inject
import org.joda.time.{DateTime, LocalDate}
import shapeless.ops.coproduct.{FilterNot, Folder, Mapper, Remove}
import shapeless.tag.@@
import shapeless.{:+:, CNil, Coproduct}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class ItemExpander @Inject()(tmdbClient: TmdbClient) {
  def expandMovie(id: String): Future[Movie] = {
    tmdbClient.makeRequest[Movie](
      s"movie/$id",
      Seq("append_to_response" -> List("release_dates", "credits", "external_ids").mkString(","))
    )
  }

  def expandTvShow(id: String): Future[TvShow] = {
    tmdbClient.makeRequest[TvShow](
      s"tv/$id",
      Seq("append_to_response" -> List("release_dates", "credits", "external_ids").mkString(","))
    )
  }

  def expandPerson(id: String): Future[Person] = {
    tmdbClient.makeRequest[Person](
      s"person/$id",
      Seq("append_to_response" -> List("combined_credits", "images", "external_ids").mkString(","))
    )
  }

  object ExpandItem extends shapeless.Poly1 {
    implicit val atMovieId: Case.Aux[String @@ MovieId, Future[Movie]] = at { m => expandMovie(m) }

    implicit val atMovie: Case.Aux[Movie, Future[Movie]] = at { m => expandMovie(m.id.toString) }

    implicit val atShow: Case.Aux[TvShow, Future[TvShow]] = at { s => expandTvShow(s.id.toString) }

    implicit val atShowId: Case.Aux[String @@ TvShowId, Future[TvShow]] = at { s => expandTvShow(s) }

    implicit val atPerson: Case.Aux[Person, Future[Person]] = at { p => expandPerson(p.id.toString) }

    implicit val atPersonId: Case.Aux[String @@ PersonId, Future[Person]] = at { p => expandPerson(p) }
  }
}

object TmdbEntity {
  type Entities = Movie :+: TvShow :+: Person :+: CNil
  type EntityIds = (String @@ MovieId) :+: (String @@ TvShowId) :+: (String @@ PersonId) :+: CNil
}

class TmdbEntityProcessor @Inject()(
  tmdbClient: TmdbClient,
  thingsDbAccess: ThingsDbAccess,
  networksDbAccess: NetworksDbAccess,
  expander: ItemExpander,
  tvShowDbAccess: TvShowDbAccess
)(implicit executionContext: ExecutionContext) {
  def processSearchResults(results: List[Movie :+: TvShow :+: CNil]): List[Future[(String, Thing)]] = {
    results.map(_.map(expander.ExpandItem)).map(_.fold(ResultProcessor))
  }

  def expandAndProcessEntity(e: Entities): Future[(String, Thing)] = {
    e.map(expander.ExpandItem).fold(ResultProcessor)
  }

  def expandAndProcessEntityId(e: EntityIds): Future[(String, Thing)] = {
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
    implicit val atMovie: Case.Aux[Movie, Future[(String, Thing)]] = at(handleMovie)

    implicit val atShow: Case.Aux[TvShow, Future[(String, Thing)]] = at(handleShow(_, false))

    implicit val atPerson: Case.Aux[Person, Future[(String, Thing)]] = at(handlePerson)

    implicit def atFutureN[N](
      implicit c: Case.Aux[N, Future[(String, Thing)]]
    ): Case.Aux[Future[N], Future[(String, Thing)]] = at { _.flatMap(c.apply(_)) }
  }

  def handleMovie(movie: Movie): Future[(String, Thing)] = {
    val genreIds = movie.genre_ids.orElse(movie.genres.map(_.map(_.id))).getOrElse(Nil).toSet
    val genresFut = thingsDbAccess.findTmdbGenres(genreIds)

    val now = DateTime.now()
    val t = Thing(None, movie.title.get, Slug(movie.title.get), ThingType.Movie, now, now, Some(ObjectMetadata.withTmdbMovie(movie)))

    val saveThingFut = thingsDbAccess.saveThing(t)

    val saveExternalIds = for {
      savedThing <- saveThingFut
      _ <- movie.external_ids.map(eids => {
        val externalId = ExternalId(None, Some(savedThing.id.get), None, Some(movie.id.toString), eids.imdb_id, None, new java.sql.Timestamp(now.getMillis))
        thingsDbAccess.upsertExternalIds(externalId).map(_ => savedThing)
      }).getOrElse(Future.successful(savedThing))
    } yield savedThing

    val saveGenres = for {
      savedThing <- saveThingFut
      genres <- genresFut
      _ <- Future.sequence(genres.map(g => {
        val ref = ThingGenre(savedThing.id.get, g.id.get)
        thingsDbAccess.saveGenreAssociation(ref)
      }))
    } yield {}

    for {
      savedThing <- saveThingFut
      _ <- saveExternalIds
      _ <- saveGenres
    } yield movie.id.toString -> savedThing
  }

  def handleShow(show: TvShow, handleSeasons: Boolean): Future[(String, Thing)] = {
    val genreIds = show.genres.getOrElse(Nil).map(_.id).toSet
    val genresFut = thingsDbAccess.findTmdbGenres(genreIds)

    val networkSlugs = show.networks.toList.flatMap(_.map(_.name)).map(Slug(_)).toSet
    val networksFut = networksDbAccess.findNetworksBySlugs(networkSlugs)

    val now = DateTime.now()
    val t = Thing(None, show.name, Slug(show.name), ThingType.Show, now, now, Some(ObjectMetadata.withTmdbShow(show)))
    val saveThingFut = thingsDbAccess.saveThing(t)

    val externalIdsFut = saveThingFut.flatMap(t => handleExternalIds(Left(t), show.external_ids, Some(show.id.toString)))

    val networkSaves = for {
      savedThing <- saveThingFut
      networks <- networksFut
      _ <- Future.sequence(networks.map(n => {
        val tn = ThingNetwork(savedThing.id.get, n.id.get)
        networksDbAccess.saveNetworkAssociation(tn)
      }))
    } yield {}

    val seasonFut = if (handleSeasons) {
      saveThingFut.flatMap(t => {
        tvShowDbAccess.findAllSeasonsForShow(t.id.get).flatMap(dbSeasons => {
          val saveFuts = show.seasons.getOrElse(Nil).map(apiSeason => {
            dbSeasons.find(_.number == apiSeason.season_number.get) match {
              case Some(s) => Future.successful(s)
              case None =>
                val m = model.TvShowSeason(None, apiSeason.season_number.get, t.id.get, apiSeason.overview, apiSeason.air_date.map(new LocalDate(_)))
                tvShowDbAccess.saveSeason(m)
            }
          })

          Future.sequence(saveFuts)
        })
      })
    } else {
      Future.successful(Nil)
    }

    for {
      savedThing <- saveThingFut
      _ <- networkSaves
      _ <- genresFut
      _ <- seasonFut
      _ <- externalIdsFut
    } yield show.id.toString -> savedThing
  }

  def handlePerson(person: Person): Future[(String, Thing)] = {
    def insertAssociations(personId: Int, thingId: Int, typ: String) = {
      thingsDbAccess.upsertPersonThing(PersonThing(personId, thingId, typ))
    }

    val now = DateTime.now()
    val t = Thing(None, person.name.get, Slug(person.name.get), ThingType.Person, now, now, Some(ObjectMetadata.withTmdbPerson(person)))

    val personSave = thingsDbAccess.saveThing(t).map(person.id.toString -> _)

    val creditsSave = person.combined_credits.map(credits => {
      for {
        savedPerson <- personSave
        cast <- SequentialFutures.serialize(credits.cast, Some(250 millis))(processResult(_).flatMap {
          case (_, thing) =>  insertAssociations(savedPerson._2.id.get, thing.id.get, "cast")
        })
        crew <- SequentialFutures.serialize(credits.crew, Some(250 millis))(processResult(_).flatMap {
          case (_, thing) =>  insertAssociations(savedPerson._2.id.get, thing.id.get, "cast")
        })
      } yield {}
    }).getOrElse(Future.successful(Nil))

    for {
      _ <- creditsSave
      p <- personSave
    } yield p
  }

  def handleExternalIds(entity: Either[Thing, model.TvShowEpisode], externalIds: Option[tmdb.ExternalIds], tmdbId: Option[String]) = {
    if (externalIds.isDefined || tmdbId.isDefined) {
      val id = tmdbId.orElse(externalIds.map(_.id.toString)).get
      thingsDbAccess.findExternalIdsByTmdbId(id).flatMap {
        case None =>
          val baseEid = model.ExternalId(None, None, None, Some(id), externalIds.flatMap(_.imdb_id), None, new Timestamp(System.currentTimeMillis()))

          val eid = entity match {
            case Left(t) => baseEid.copy(thingId = t.id)
            case Right(t) => baseEid.copy(tvEpisodeId = t.id)
          }

          thingsDbAccess.upsertExternalIds(eid).map(Some(_))

        case Some(x) => Future.successful(Some(x))
      }
    } else {
      Future.successful(None)
    }
  }
}
