package com.teletracker.tasks.scraper

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.dynamo.{CrawlStore, CrawlerName}
import com.teletracker.common.db.model._
import com.teletracker.common.elasticsearch.async.EsIngestQueue
import com.teletracker.common.elasticsearch.async.EsIngestQueue.AsyncItemUpdateRequest
import com.teletracker.common.elasticsearch.model._
import com.teletracker.common.elasticsearch.scraping.EsPotentialMatchItemStore
import com.teletracker.common.elasticsearch.util.ItemUpdateApplier
import com.teletracker.common.elasticsearch.{
  AvailabilityQueryHelper,
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.model.scraping.{
  MatchResult,
  PartialEsItem,
  ScrapedItem,
  ScrapedItemAvailabilityDetails
}
import com.teletracker.common.pubsub.EsIngestItemDenormArgs
import com.teletracker.common.tasks.TeletrackerTask.JsonableArgs
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.{AsyncStream, NetworkCache}
import com.teletracker.tasks.model.{
  BaseTaskArgs,
  PagingTaskArgs,
  ParallelismTaskArgs
}
import com.teletracker.tasks.scraper.matching._
import com.teletracker.tasks.util.{SourceRetriever, SourceWriter}
import io.circe.Codec
import javax.inject.Inject
import org.elasticsearch.common.xcontent.{XContentHelper, XContentType}
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}
import java.net.URI
import java.time.{LocalDate, OffsetDateTime}
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.control.NonFatal

trait IngestJobArgsLike
    extends BaseTaskArgs
    with PagingTaskArgs
    with ParallelismTaskArgs {

  def uploadArtifacts: Boolean = false
}

abstract class IngestJob[T <: ScrapedItem: ScrapedItemAvailabilityDetails](
  networkCache: NetworkCache
)(implicit codec: Codec[T],
  jsonableArgs: JsonableArgs[IngestJobArgs],
  executionContext: ExecutionContext)
    extends BaseIngestJob[T, IngestJobArgs](networkCache) {

  import ScrapedItemAvailabilityDetails.syntax._
  import diffson._
  import diffson.circe._
  import diffson.jsonpatch.simplediff._
  import io.circe.syntax._

  protected def itemLookup: ItemLookup
  protected def itemUpdater: ItemUpdater
  protected def crawlerName: CrawlerName

  @Inject
  private[this] var sourceRetriever: SourceRetriever = _
  @Inject
  private[this] var sourceWriter: SourceWriter = _
  @Inject
  private[this] var externalIdLookup: ElasticsearchExternalIdLookup.Factory = _
  @Inject
  private[this] var internalIdLookup: InternalIdLookupMethod.Factory = _
  @Inject
  private[this] var elasticsearchLookup: ElasticsearchLookup = _
  @Inject
  private[this] var availabilityQueryHelper: AvailabilityQueryHelper = _
  @Inject
  private[this] var itemUpdateQueue: EsIngestQueue = _
  @Inject
  private[this] var esPotentialMatchItemStore: EsPotentialMatchItemStore = _
  @Inject
  protected var elasticsearchExecutor: ElasticsearchExecutor = _
  @Inject
  protected var teletrackerConfig: TeletrackerConfig = _
  @Inject
  protected var crawlStore: CrawlStore = _

  protected val matchChangesFile = new File(
    s"${today}_${getClass.getSimpleName}-change-matches.json"
  )

  protected val matchChangesWriter = new PrintStream(
    new BufferedOutputStream(
      new FileOutputStream(
        matchChangesFile
      )
    )
  )

  protected def lookupMethod(): LookupMethod[T] = {
    val externalIdMatchers = if (args.enableExternalIdMatching) {
      List(externalIdLookup.create[T])
    } else {
      Nil
    }

    new CustomElasticsearchLookup[T](
      internalIdLookup.create[T] +:
        externalIdMatchers :+
        elasticsearchLookup.create[T]
    )
  }

  protected def outputLocation: Option[URI] = None

  protected def getAdditionalOutputFiles: Seq[(File, String)] = Seq()

  override def runInternal(): Unit = {
    registerArtifact(Artifact(potentialMatchesWriter, potentialMatchFile))
    registerArtifact(Artifact(matchChangesWriter, matchChangesFile))

    val networks = getNetworksOrExit()

    logger.info("Running preprocess phase")
    preprocess()

    if (args.reimport) {
      val sources = supportedNetworks
      val matchingNetworks =
        networks
          .filter(
            net => net.supportedNetwork.exists(sources.contains)
          )

      if (!args.dryRun) {
        logger.info("Reimport mode. Deleting existing availability.")
        availabilityQueryHelper.deleteAvailabilityForNetworks(matchingNetworks)
      } else {
        val query =
          availabilityQueryHelper.getDeleteAvailabilityForNetworksQuery(
            matchingNetworks
          )
        val jsonQuery =
          XContentHelper.toXContent(query, XContentType.JSON, true)
        logger.info(
          s"Would've deleted all availability for networks: ${sources}: ${jsonQuery.utf8ToString()}"
        )
      }
    }

    logger.info(s"Starting ingest of ${supportedNetworks} content")

    if (args.inputFile.isDefined) {
      sourceRetriever
        .getSourceStream(args.inputFile.get)
        .safeTake(args.sourceLimit)
        .foreach(processSource(_, networks))
    } else {
      val crawlerFut = if (args.crawlerVersion.isDefined) {
        crawlStore.getCrawlAtVersion(crawlerName, args.crawlerVersion.get).map {
          case Some(value) => value
          case None =>
            throw new IllegalArgumentException(
              s"No crawl found at version: ${args.crawlerVersion.get}"
            )
        }
      } else {
        crawlStore.getLatestCrawl(crawlerName).map {
          case Some(value) => value
          case None =>
            throw new IllegalArgumentException(
              "No completed crawl found for crawler"
            )
        }
      }

      val crawl = crawlerFut.await()

      crawl.getOutputWithScheme("s3") match {
        case Some((uri, _)) =>
          logger.info(s"Pulliing ${uri} for crawl import")
          sourceRetriever
            .getSourceStream(uri)
            .safeTake(args.sourceLimit)
            .foreach(processSource(_, networks))
        case None =>
          throw new RuntimeException(
            s"Crawl at version ${crawl.version} had no S3 output. Only S3 output is supported by this job."
          )
      }
    }

    matchChangesWriter.flush()
    matchChangesWriter.close()
  }

  protected def preprocess(): Unit = {}

  private def processSource(
    source: Source,
    networks: Set[StoredNetwork]
  ): Unit = {
    try {
      new IngestJobParser()
        .asyncStream[T](source.getLines())
        .flatMapOption {
          case Left(value) =>
            logger.warn("Could not parse line", value)
            None
          case Right(value) =>
            Some(value).filter(item => {
              args.externalIdFilter match {
                case Some(value) if item.externalId.isDefined =>
                  value == item.externalId.get
                case Some(_) => false
                case None    => true
              }
            })
        }
        .throughApply(processAll(_, networks))
        .force
        .await()
    } catch {
      case NonFatal(e) =>
        throw e
    } finally {
      source.close()
      missingItemsWriter.flush()
      missingItemsWriter.close()

      matchingItemsWriter.flush()
      matchingItemsWriter.close()

      uploadResultFiles()
    }
  }

  override protected def handleMatchResults(
    results: List[MatchResult[T]],
    networks: Set[StoredNetwork],
    args: IngestJobArgs
  ): Future[Unit] = {
    detectExternalIdChanges(results, networks).flatMap(_ => {
      val itemsWithNewAvailability = results.flatMap {
        case MatchResult(item, esItem) =>
          val availabilities =
            createAvailabilities(networks, esItem, item)

          val updateItemFunc = (ItemUpdateApplier
            .applyAvailabilities(_, availabilities))
            .andThen(
              esItem =>
                ItemUpdateApplier.applyExternalIds(esItem, getExternalIds(item))
            )
            .andThen(_.copy(last_updated = Some(System.currentTimeMillis())))

          val newItem = updateItemFunc(esItem)

          val availabilitiesEqual = newItem.availability
            .getOrElse(Nil)
            .toSet == esItem.availability
            .getOrElse(Nil)
            .toSet

          val externalIdsEqual = newItem.externalIdsGrouped == esItem.externalIdsGrouped

          if (availabilitiesEqual && externalIdsEqual) {
            None
          } else {
            if (args.dryRun && esItem != newItem) {
              logger.info(
                s"Would've updated id = ${newItem.id}:\n ${diff(esItem.asJson, newItem.asJson).asJson.spaces2}"
              )
            }

            if (esItem != newItem) {
              Some(newItem)
            } else {
              None
            }
          }
      }

      if (!args.dryRun) {
        val requests = itemsWithNewAvailability.map(item => {
          AsyncItemUpdateRequest(
            id = item.id,
            itemType = item.`type`,
            doc = item.asJson,
            denorm = Some(
              EsIngestItemDenormArgs(
                needsDenorm = true,
                cast = false,
                crew = false
              )
            )
          )
        })

        itemUpdateQueue.queueItemUpdates(requests).map(_ => {})
      } else {
        Future.successful {
          logger.info(
            s"Would've saved ${itemsWithNewAvailability.size} items with availability: ${}"
          )
        }
      }
    })
  }

  private def detectExternalIdChanges(
    results: List[MatchResult[T]],
    networks: Set[StoredNetwork]
  ): Future[Unit] = {
    AsyncStream
      .fromSeq(results)
      .grouped(25)
      .flatMapF(batch => {
        val esItemById = batch.map(_.esItem).map(item => item.id -> item).toMap
        val scrapeItemByEsItemId =
          batch.map(i => i.esItem.id -> i.scrapedItem).toMap
        val esItemIdByExternalId = batch.flatMap {
          case MatchResult(scrapedItem, esItem) =>
            getExternalIds(scrapedItem).map {
              case (source, str) =>
                (EsExternalId(source, str), esItem.`type`) -> esItem.id
            }
        }.toMap
        val keys = batch.flatMap {
          case MatchResult(scrapedItem, esItem) =>
            getExternalIds(scrapedItem).map {
              case (source, str) => (source, str, esItem.`type`)
            }
        }

        // Lookup by external IDs to see if there are existing items we've matched previously
        // that don't match the current results
        itemLookup
          .lookupItemsByExternalIds(keys.toList)
          .map(results => {
            results.toSeq
              .flatMap {
                case (key, existingItem) =>
                  esItemIdByExternalId.get(key) match {
                    case Some(value) if value != existingItem.id =>
                      logger.info(
                        s"Found external id mismatch for ${key}: existing = ${value}, new = ${existingItem.id}"
                      )

                      Some(existingItem -> key)
                    case _ => None
                  }
              }
              .groupBy {
                case (item, _) => item.id
              }
              .map {
                case (uuid, values) =>
                  uuid -> (values.head._1, values.map(_._2))
              }
              .withEffect(grouped => {
                grouped.foreach {
                  case (_, (existingItem, keys)) =>
                    val newMatchItemId = esItemIdByExternalId(keys.head)
                    val newItem = esItemById(newMatchItemId)
                    writeMatchChange(
                      scrapeItemByEsItemId(newMatchItemId),
                      existingItem,
                      newItem
                    )
                }
              })
              .toSeq
          })
      })
      .map {
        case (_, (item, keys)) =>
          val idsToRemove = keys.map(_._1).toSet
          val updatedItem = item
            .through(
              ItemUpdateApplier
                .removeAvailabilitiesForNetworks(_, networks)
            )
            .through(i => {
              i.copy(
                external_ids = Some(
                  i.external_ids.getOrElse(Nil).filterNot(idsToRemove.contains)
                )
              )
            })

          if (args.dryRun) {
            logger.info(
              s"Would've updated incorrect item (${item.id}):\n${diff(item.asJson, updatedItem.asJson).asJson.spaces2}"
            )
          }

          updatedItem
      }
      .map(item => {
        AsyncItemUpdateRequest(
          id = item.id,
          itemType = item.`type`,
          doc = item.asJson,
          denorm = Some(
            EsIngestItemDenormArgs(
              needsDenorm = true,
              cast = false,
              crew = false
            )
          )
        )
      })
      .grouped(10)
      .mapF(
        group =>
          if (args.dryRun) {
            Future.unit
          } else {
            itemUpdateQueue.queueItemUpdates(group.toList).map(_ => {})
          }
      )
      .force
  }

  protected def writeMatchChange(
    item: T,
    original: EsItem,
    newMatch: EsItem
  ): Unit = {
    matchChangesWriter.println(
      Map(
        "scraped" -> item.asJson,
        "original" -> PartialEsItem.forEsItem(original).asJson,
        "new" -> PartialEsItem.forEsItem(newMatch).asJson
      ).asJson.noSpaces
    )
  }

  override protected def writePotentialMatches(
    potentialMatches: Iterable[(EsItem, T)]
  ): Unit = {
    import UpdateableEsItem.syntax._

    super.writePotentialMatches(potentialMatches)

    val writePotentialMatchesToEs = rawArgs.valueOrDefault(
      "writePotentialMatchesToEs",
      false
    )

    if (!args.dryRun || writePotentialMatchesToEs) {
      val networks = getNetworksOrExit()

      val items = for {
        supportedNetwork <- supportedNetworks.toList
        (item, t) <- potentialMatches
        externalId <- t.externalId.toList
      } yield {
        val esExternalId =
          EsExternalId(supportedNetwork.getExternalSource, externalId)
        val now = OffsetDateTime.now()
        val insert = EsPotentialMatchItem(
          id = EsPotentialMatchItem.id(item.id, esExternalId),
          created_at = now,
          state = EsPotentialMatchState.Unmatched,
          last_updated_at = now,
          last_state_change = now,
          potential = PartialEsItem.forEsItem(item),
          scraped = EsGenericScrapedItem(
            scrapeItemType,
            EsScrapedItem.fromAnyScrapedItem(t),
            t.asJson
          ),
          availability = Some(createAvailabilities(networks, item, t).toList)
        )

        val update: EsPotentialMatchItemUpdateView = insert.toUpdateable

        update.copy(state = None, last_state_change = None) -> insert
      }

      esPotentialMatchItemStore
        .upsertBatchWithFallback[EsPotentialMatchItemUpdateView](items)
        .recover {
          case NonFatal(e) =>
            logger.warn(
              "Failed while attempting to write potential matches to ES.",
              e
            )
        }
        .await()
    }
  }

  protected def isAvailable(
    item: T,
    today: LocalDate
  ): Boolean = {
    val start =
      if (item.isExpiring) None else item.availableLocalDate
    val end =
      if (item.isExpiring) item.availableLocalDate else None

    (start.isEmpty && end.isEmpty) ||
    start.exists(_.isBefore(today)) ||
    end.exists(_.isAfter(today))
  }

  protected def getExternalIds(item: T): Map[ExternalSource, String] =
    item.externalIds

  protected def uploadResultFiles(): Unit = {
    outputLocation.foreach(uri => {
      try {
        sourceWriter.writeFile(
          uri.resolve(s"${uri.getPath}/match-items.txt"),
          matchItemsFile.toPath
        )
      } catch {
        case NonFatal(e) => logger.error("Error writing match-items file", e)
      }

      try {
        sourceWriter.writeFile(
          uri.resolve(s"${uri.getPath}/missing-items.txt"),
          missingItemsFile.toPath
        )
      } catch {
        case NonFatal(e) => logger.error("Error writing missing-items file", e)
      }

      try {
        sourceWriter.writeFile(
          uri.resolve(s"${uri.getPath}/potential-matches.txt"),
          potentialMatchFile.toPath
        )
      } catch {
        case NonFatal(e) =>
          logger.error("Error writing potential-matches file", e)
      }
    })
  }
}
