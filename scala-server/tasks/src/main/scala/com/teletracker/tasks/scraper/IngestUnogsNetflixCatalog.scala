package com.teletracker.tasks.scraper

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model._
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.process.tmdb.TmdbEntityProcessor
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.json.circe._
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.tasks.scraper.IngestJobParser.{JsonPerLine, ParseMode}
import com.teletracker.tasks.scraper.matching.{ElasticsearchLookup, MatchMode}
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.util.UUID
import scala.concurrent.Future

class IngestUnogsNetflixCatalog @Inject()(
  protected val tmdbClient: TmdbClient,
  protected val tmdbProcessor: TmdbEntityProcessor,
  protected val thingsDb: ThingsDbAccess,
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemSearch: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  protected val elasticsearchExecutor: ElasticsearchExecutor,
  elasticsearchLookup: ElasticsearchLookup)
    extends IngestJob[UnogsNetflixCatalogItem]
    with IngestJobWithElasticsearch[UnogsNetflixCatalogItem]
    with ElasticsearchFallbackMatcher[UnogsNetflixCatalogItem] {
  override protected def networkNames: Set[String] = Set("netflix")

  override protected def parseMode: ParseMode = JsonPerLine

  override protected def processMode(args: IngestJobArgs): ProcessMode =
    Parallel(32)

  override protected def matchMode: MatchMode =
    elasticsearchLookup

  override protected def createAvailabilities(
    networks: Set[Network],
    itemId: UUID,
    title: String,
    scrapeItem: UnogsNetflixCatalogItem
  ): Future[Seq[Availability]] = {
    SequentialFutures
      .serialize(networks.toSeq)(
        network => {
          thingsDb
            .findAvailability(itemId, network.id.get)
            .flatMap {
              case Seq() =>
                Future.successful {

                  presentationTypes.toSeq.map(pres => {
                    Availability(
                      None,
                      isAvailable = true, // TODO compare to last dump and see what is missing
                      region = Some("US"),
                      numSeasons = None,
                      startDate = None,
                      endDate = None,
                      offerType = Some(OfferType.Subscription),
                      cost = None,
                      currency = None,
                      thingId = Some(itemId),
                      tvShowEpisodeId = None,
                      networkId = Some(network.id.get),
                      presentationType = Some(pres)
                    )
                  })
                }

//                thingsDb.insertAvailabilities(avs)

              case availabilities =>
                // TODO(christian) - find missing presentation types
                Future.successful {

                  availabilities.map(
                    _.copy(
                      isAvailable = true,
                      numSeasons = None,
                      startDate = None,
                      endDate = None
                    )
                  )
                }

//                thingsDb.saveAvailabilities(newAvs).map(_ => newAvs)
            }
        }
      )
      .map(_.flatten)
  }
}

@JsonCodec
case class UnogsNetflixCatalogItem(
  availableDate: Option[String],
  title: String,
  releaseYear: Option[Int],
  network: String,
  `type`: ThingType,
  externalId: Option[String])
    extends ScrapedItem {
  val status = "Available"

  override def category: String = ""

  override def isMovie: Boolean = `type` == ThingType.Movie

  override def isTvShow: Boolean = `type` == ThingType.Show
}
