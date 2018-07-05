package com.chrisbenincasa.services.teletracker.process.tmdb

import com.chrisbenincasa.services.teletracker.db.ThingsDbAccess
import com.chrisbenincasa.services.teletracker.db.model._
import com.chrisbenincasa.services.teletracker.external.tmdb.TmdbClient
import com.chrisbenincasa.services.teletracker.model.tmdb._
import com.chrisbenincasa.services.teletracker.process.tmdb.TmdbEntity.{Entities, EntityIds}
import com.chrisbenincasa.services.teletracker.util.Slug
import javax.inject.Inject
import org.joda.time.DateTime
import shapeless.tag.@@
import shapeless.{:+:, CNil, Coproduct}
import scala.concurrent.{ExecutionContext, Future}

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

  object ExpandItem extends shapeless.Poly1 {
    implicit val atMovieId: Case.Aux[String @@ MovieId, Future[Movie]] = at { m => expandMovie(m) }

    implicit val atMovie: Case.Aux[Movie, Future[Movie]] = at { m => expandMovie(m.id.toString) }

    implicit val atShow: Case.Aux[TvShow, Future[TvShow]] = at { s => expandTvShow(s.id.toString) }

    implicit val atShowId: Case.Aux[String @@ TvShowId, Future[TvShow]] = at { s => expandTvShow(s) }

    implicit val atPerson: Case.Aux[Person, Future[Person]] = at { p => ??? }

    implicit val atPersonId: Case.Aux[String @@ PersonId, Future[Person]] = at { p => ??? }
  }
}

object TmdbEntity {
  type Entities = Movie :+: TvShow :+: Person :+: CNil
  type EntityIds = (String @@ MovieId) :+: (String @@ TvShowId) :+: (String @@ PersonId) :+: CNil
}

class TmdbEntityProcessor @Inject()(
  tmdbClient: TmdbClient,
  thingsDbAccess: ThingsDbAccess,
  expander: ItemExpander
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

  /**
   * Polymorphic function that operates on model types from TMDb search results
   */
  object ResultProcessor extends shapeless.Poly1 {
    implicit val atMovie: Case.Aux[Movie, Future[(String, Thing)]] = at(handleMovie)

    implicit val atShow: Case.Aux[TvShow, Future[(String, Thing)]] = at(handleShow)

    implicit val atPerson: Case.Aux[Person, Future[(String, Thing)]] = at { p =>
      ???
    }

    implicit def atFutureN[N](
      implicit c: Case.Aux[N, Future[(String, Thing)]]
    ): Case.Aux[Future[N], Future[(String, Thing)]] = at { _.flatMap(c.apply(_)) }
  }

  private def handleMovie(movie: Movie): Future[(String, Thing)] = {
    val genreIds = movie.genre_ids.orElse(movie.genres.map(_.map(_.id))).getOrElse(Nil).toSet
    val genresFut = thingsDbAccess.findTmdbGenres(genreIds)

    val now = DateTime.now()
    val t = Thing(None, movie.title.get, Slug(movie.title.get), "movie", now, now, Some(ObjectMetadata.withTmdbMovie(movie)))

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

  private def handleShow(show: TvShow): Future[(String, Thing)] = {
    val genreIds = show.genres.getOrElse(Nil).map(_.id).toSet
    val genresFut = thingsDbAccess.findTmdbGenres(genreIds)

    val networkSlugs = show.networks.toList.flatMap(_.map(_.name)).map(Slug(_)).toSet
    val networksFut = thingsDbAccess.findNetworksBySlugs(networkSlugs)

    val now = DateTime.now()
    val t = Thing(None, show.name, Slug(show.name), "show", now, now, Some(ObjectMetadata.withTmdbShow(show)))
    val saveThingFut = thingsDbAccess.saveThing(t)

    val networkSaves = for {
      savedThing <- saveThingFut
      networks <- networksFut
      _ <- Future.sequence(networks.map(n => {
        val tn = ThingNetwork(savedThing.id.get, n.id.get)
        thingsDbAccess.saveNetworkAssociation(tn)
      }))
    } yield {}

    for {
      savedThing <- saveThingFut
      _ <- networkSaves
      _ <- genresFut
    } yield show.id.toString -> savedThing
  }
}
