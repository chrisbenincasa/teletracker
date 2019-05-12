package com.teletracker.service.process.tmdb

import com.teletracker.service.cache.{JustWatchLocalCache, TmdbLocalCache}
import com.teletracker.service.db.model._
import com.teletracker.service.db.{NetworksDbAccess, ThingFactory, ThingsDbAccess, TvShowDbAccess, model}
import com.teletracker.service.external.justwatch.JustWatchClient
import com.teletracker.service.external.tmdb.TmdbClient
import com.teletracker.service.model.justwatch.{PopularItem, PopularItemsResponse, PopularSearchRequest}
import com.teletracker.service.model.tmdb
import com.teletracker.service.model.tmdb._
import com.teletracker.service.process.tmdb.TmdbEntity.{Entities, EntityIds}
import com.teletracker.service.util.execution.SequentialFutures
import com.teletracker.service.util.{NetworkCache, Slug, TmdbMovieImporter}
import com.teletracker.service.util.json.circe._
import java.sql.Timestamp
import javax.inject.Inject
import org.joda.time.{DateTime, LocalDate}
import shapeless.ops.coproduct.{Folder, Mapper}
import shapeless.tag.@@
import shapeless.{:+:, CNil, Coproduct}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class ItemExpander @Inject()(
  tmdbClient: TmdbClient,
  cache: TmdbLocalCache
)(implicit executionContext: ExecutionContext) {
  def expandMovie(id: String): Future[Movie] = {
    cache.getOrSetEntity(ThingType.Movie, id, {
      tmdbClient.makeRequest[Movie](
        s"movie/$id",
        Seq("append_to_response" -> List("release_dates", "credits", "external_ids").mkString(","))
      )
    })
  }

  def expandTvShow(id: String): Future[TvShow] = {
    cache.getOrSetEntity(ThingType.Show, id, {
      tmdbClient.makeRequest[TvShow](
        s"tv/$id",
        Seq("append_to_response" -> List("release_dates", "credits", "external_ids").mkString(","))
      )
    })
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
  tvShowDbAccess: TvShowDbAccess,
  networkCache: NetworkCache,
  justWatchClient: JustWatchClient,
  cache: TmdbLocalCache,
  justWatchLocalCache: JustWatchLocalCache
)(implicit executionContext: ExecutionContext) {
  def processSearchResults(results: List[Movie :+: TvShow :+: Person :+: CNil]): List[Future[(String, Thing)]] = {
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

    implicit val atShow: Case.Aux[TvShow, Future[(String, Thing)]] = at(handleShow(_, handleSeasons = false))

    implicit val atPerson: Case.Aux[Person, Future[(String, Thing)]] = at(handlePerson)

    implicit def atFutureN[N](
      implicit c: Case.Aux[N, Future[(String, Thing)]]
    ): Case.Aux[Future[N], Future[(String, Thing)]] = at { _.flatMap(c.apply(_)) }
  }

  def handleMovie(movie: Movie): Future[(String, Thing)] = {
    val genreIds = movie.genre_ids.orElse(movie.genres.map(_.map(_.id))).getOrElse(Nil).toSet
    val genresFut = thingsDbAccess.findTmdbGenres(genreIds)

    val now = DateTime.now()
    val t = ThingFactory.makeThing(movie)

    val saveThingFut = thingsDbAccess.saveThing(t, Some(ExternalSource.TheMovieDb -> movie.id.toString))

    val availability = handleMovieAvailability(movie, saveThingFut)

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
      _ <- availability
    } yield movie.id.toString -> savedThing
  }

  private def handleMovieAvailability(movie: Movie, processedMovieFut: Future[Thing]) = {
    import io.circe.generic.auto._
    import io.circe.syntax._

    val query = PopularSearchRequest(1, 10, movie.title.get, List("movie"))
    val justWatchResFut = justWatchLocalCache.getOrSet(query, {
      justWatchClient.makeRequest[PopularItemsResponse]("/content/titles/en_US/popular", Seq("body" -> query.asJson.noSpaces))
    })

    (for {
      justWatchRes <- justWatchResFut
      networksBySource <- networkCache.get()
      thing <- processedMovieFut
    } yield {
      val matchingMovie = matchJustWatchMovie(movie, justWatchRes.items)

      val availabilities = matchingMovie.collect {
        case matchedItem if matchedItem.offers.exists(_.nonEmpty) =>
          for {
            offer <- matchedItem.offers.get.distinct
            provider <- networksBySource.get(ExternalSource.JustWatch -> offer.provider_id.toString).toList
          } yield {
            val offerType = Try(offer.monetization_type.map(OfferType.fromJustWatchType)).toOption.flatten
            val presentationType = Try(offer.presentation_type.map(PresentationType.fromJustWatchType)).toOption.flatten

            Availability(
              id = None,
              isAvailable = true,
              region = offer.country,
              numSeasons = None,
              startDate = offer.date_created.map(new DateTime(_)),
              endDate = None,
              offerType = offerType,
              cost = offer.retail_price.map(BigDecimal.decimal),
              currency = offer.currency,
              thingId = thing.id,
              tvShowEpisodeId = None,
              networkId = provider.id,
              presentationType = presentationType
            )
          }
      }.getOrElse(Nil)

      thingsDbAccess.saveAvailabilities(availabilities).map(_ => thing)
    }).flatMap(identity)
  }

  private def matchJustWatchMovie(movie: Movie, popularItems: List[PopularItem]): Option[PopularItem] = {
    popularItems.find(item => {
      val idMatch = item.scoring.getOrElse(Nil).exists(s => s.provider_type == "tmdb:id" && s.value.toInt.toString == movie.id.toString)
      val nameMatch = item.title.exists(movie.title.contains)
      val originalMatch = movie.original_title.exists(item.original_title.contains)
      val yearMatch = item.original_release_year.exists(year => {
        movie.release_date.filter(_.nonEmpty).map(new LocalDate(_).getYear).contains(year)
      })

      idMatch || (nameMatch && yearMatch) || (originalMatch && yearMatch)
    })
  }

  def handleShow(show: TvShow, handleSeasons: Boolean): Future[(String, Thing)] = {
    val genreIds = show.genres.getOrElse(Nil).map(_.id).toSet
    val genresFut = thingsDbAccess.findTmdbGenres(genreIds)

    val networkSlugs = show.networks.toList.flatMap(_.map(_.name)).map(Slug(_)).toSet
    val networksFut = networksDbAccess.findNetworksBySlugs(networkSlugs)

    val now = DateTime.now()
    val t = Thing(None, show.name, Slug(show.name), ThingType.Show, now, now, Some(ObjectMetadata.withTmdbShow(show)))
    val saveThingFut = thingsDbAccess.saveThing(t, Some(ExternalSource.TheMovieDb, show.id.toString))

    val externalIdsFut = saveThingFut.flatMap(t => handleExternalIds(Left(t), show.external_ids, Some(show.id.toString)))

    val networkSaves = for {
      savedThing <- saveThingFut
      networks <- networksFut
      _ <- Future.sequence(networks.map(n => {
        val tn = ThingNetwork(savedThing.id.get, n.id.get)
        networksDbAccess.saveNetworkAssociation(tn)
      }))
    } yield {}

    val availability = handleShowAvailability(show, saveThingFut)

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
      _ <- availability
    } yield show.id.toString -> savedThing
  }

  private def handleShowAvailability(show: TvShow, processedShowFut: Future[Thing]): Future[Thing] = {
    import io.circe.generic.auto._
    import io.circe.syntax._

    val query = PopularSearchRequest(1, 10, show.name, List("show"))
    val justWatchResFut = justWatchLocalCache.getOrSet(query, {
      justWatchClient.makeRequest[PopularItemsResponse]("/content/titles/en_US/popular", Seq("body" -> query.asJson.noSpaces))
    })

    (for {
      justWatchRes <- justWatchResFut
      networksBySource <- networkCache.get()
      thing <- processedShowFut
    } yield {
      val matchingShow = matchJustWatchShow(show, justWatchRes.items)

      val availabilities = matchingShow.collect {
        case matchedItem if matchedItem.offers.exists(_.nonEmpty) =>
          for {
            offer <- matchedItem.offers.get.distinct
            provider <- networksBySource.get(ExternalSource.JustWatch -> offer.provider_id.toString).toList
          } yield {
            val offerType = Try(offer.monetization_type.map(OfferType.fromJustWatchType)).toOption.flatten
            val presentationType = Try(offer.presentation_type.map(PresentationType.fromJustWatchType)).toOption.flatten

            Availability(
              id = None,
              isAvailable = true,
              region = offer.country,
              numSeasons = None,
              startDate = offer.date_created.map(new DateTime(_)),
              endDate = None,
              offerType = offerType,
              cost = offer.retail_price.map(BigDecimal.decimal),
              currency = offer.currency,
              thingId = thing.id,
              tvShowEpisodeId = None,
              networkId = provider.id,
              presentationType = presentationType
            )
          }
      }.getOrElse(Nil)

      thingsDbAccess.saveAvailabilities(availabilities).map(_ => thing)
    }).flatMap(identity)
  }

  private def matchJustWatchShow(show: TvShow, popularItems: List[PopularItem]): Option[PopularItem] = {
    popularItems.find(item => {
      val idMatch = item.scoring.getOrElse(Nil).exists(s => s.provider_type == "tmdb:id" && s.value.toInt.toString == show.id.toString)
      val nameMatch = item.title.exists(show.name.equalsIgnoreCase)
      val originalMatch = show.original_name.exists(item.original_title.contains)
      val yearMatch = item.original_release_year.exists(year => {
        show.first_air_date.filter(_.nonEmpty).map(new LocalDate(_).getYear).contains(year)
      })

      idMatch || (nameMatch && yearMatch) || (originalMatch && yearMatch)
    })
  }

  def handlePerson(person: Person): Future[(String, Thing)] = {
    def insertAssociations(personId: Int, thingId: Int, typ: String) = {
      thingsDbAccess.upsertPersonThing(PersonThing(personId, thingId, typ))
    }

    val now = DateTime.now()
    val t = Thing(None, person.name.get, Slug(person.name.get), ThingType.Person, now, now, Some(ObjectMetadata.withTmdbPerson(person)))

    val personSave = thingsDbAccess.saveThing(t, Some(ExternalSource.TheMovieDb -> person.id.toString)).map(person.id.toString -> _)

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

  def handleExternalIds(
    entity: Either[Thing, model.TvShowEpisode],
    externalIds: Option[tmdb.ExternalIds],
    tmdbId: Option[String]
  ): Future[Option[ExternalId]] = {
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
