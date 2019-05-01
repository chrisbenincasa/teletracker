package com.chrisbenincasa.services.teletracker.util

import com.chrisbenincasa.services.teletracker.db.{NetworksDbAccess, ThingsDbAccess}
import com.chrisbenincasa.services.teletracker.db.model._
import com.chrisbenincasa.services.teletracker.external.justwatch.JustWatchClient
import com.chrisbenincasa.services.teletracker.external.tmdb.TmdbClient
import com.chrisbenincasa.services.teletracker.model.justwatch.{PopularItem, PopularItemsResponse, PopularSearchRequest}
import com.chrisbenincasa.services.teletracker.model.tmdb.{Movie, MovieId}
import com.chrisbenincasa.services.teletracker.process.tmdb.TmdbEntity.EntityIds
import com.chrisbenincasa.services.teletracker.process.tmdb.TmdbEntityProcessor
import com.chrisbenincasa.services.teletracker.util.execution.SequentialFutures
import com.twitter.finagle.param.HighResTimer
import com.twitter.logging.Logger
import com.twitter.util.{Duration, Timer}
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import org.joda.time.DateTime
import shapeless.{Coproduct, tag}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

class TmdbMovieImporter @Inject()(
  thingsDbAccess: ThingsDbAccess,
  networksDbAccess: NetworksDbAccess,
  tmdbClient: TmdbClient,
  justWatchClient: JustWatchClient,
  tmdbEntityProcessor: TmdbEntityProcessor,
  availabilities: Availabilities
)(implicit executionContext: ExecutionContext) {
  private val logger = Logger()

  import io.circe.generic.auto._
  import io.circe.syntax._

  def handleMovies(movies: List[Movie]): Future[List[Thing]] = {
    val allNetworks = networksDbAccess.findAllNetworks().map(_.map {
      case (ref, net) => (ref.externalSource -> ref.externalId) -> net
    }.toMap)

    SequentialFutures.serialize(movies)(movie => {
      logger.info(s"Processing movie id = ${movie.id}")

      val query = PopularSearchRequest(1, 10, movie.title.get, List("movie"))
      val justWatchResFut = justWatchClient.makeRequest[PopularItemsResponse]("/content/titles/en_US/popular", Seq("body" -> query.asJson.noSpaces))

      val movieId = Coproduct[EntityIds](tag[MovieId](movie.id.toString))
      val processedMovieFut = tmdbEntityProcessor.expandAndProcessEntityId(movieId)

      val save = for {
        justWatchRes <- justWatchResFut
        networksBySource <- allNetworks
        (_, thing) <- processedMovieFut
      } yield {
        val availabilities = matchJustWatchMovie(movie, justWatchRes.items).collect {
          case matchedItem if matchedItem.offers.exists(_.nonEmpty) =>
            for {
              offer <- matchedItem.offers.get.distinct
              provider <- networksBySource.get(ExternalSource.JustWatch -> offer.provider_id.toString).toList
            } yield {
              val offerType = Try(offer.monetization_type.map(OfferType.fromJustWatchType)).toOption.flatten
              val presentationType = Try(offer.presentation_type.map(PresentationType.fromJustWatchType)).toOption.flatten

              Availability(
                None,
                true,
                offer.country,
                None,
                offer.date_created.map(new DateTime(_)),
                None,
                offerType,
                offer.retail_price.map(BigDecimal.decimal),
                offer.currency,
                thing.id,
                None,
                provider.id,
                presentationType
              )
            }
        }.getOrElse(Nil)

        thingsDbAccess.saveAvailabilities(availabilities).map(_ => thing)
      }

      save.flatMap(identity).flatMap(x => {
        val p = Promise[Thing]()
        HighResTimer.Default.doLater(Duration(250, TimeUnit.MILLISECONDS))(p.success(x))
        p.future
      })
    })
  }

  private def matchJustWatchMovie(movie: Movie, popularItems: List[PopularItem]): Option[PopularItem] = {
    popularItems.find(item => {
      val idMatch = item.scoring.getOrElse(Nil).exists(s => s.provider_type == "tmdb:id" && s.value.toInt.toString == movie.id.toString)
      val nameMatch = item.title.exists(movie.title.contains)
      val originalMatch = movie.original_title.exists(item.original_title.contains)

      idMatch || nameMatch || originalMatch
    })
  }
}
