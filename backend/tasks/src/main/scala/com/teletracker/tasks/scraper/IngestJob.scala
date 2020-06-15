package com.teletracker.tasks.scraper

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.dynamo.model.StoredNetwork
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
  ScrapedItem
}
import com.teletracker.common.pubsub.EsIngestItemDenormArgs
import com.teletracker.common.tasks.TeletrackerTask.{JsonableArgs, RawArgs}
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.json.circe._
import com.teletracker.common.util.{AsyncStream, NetworkCache}
import com.teletracker.tasks.TeletrackerTaskApp
import com.teletracker.tasks.scraper.IngestJobParser.ParseMode
import com.teletracker.tasks.scraper.matching.{
  CustomElasticsearchLookup,
  ElasticsearchExternalIdLookup,
  ElasticsearchLookup,
  LookupMethod
}
import com.teletracker.tasks.util.{SourceRetriever, SourceWriter}
import io.circe.Codec
import io.circe.generic.JsonCodec
import javax.inject.Inject
import org.elasticsearch.common.xcontent.{XContentHelper, XContentType}
import software.amazon.awssdk.services.s3.S3Client
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}
import java.net.URI
import java.time.{LocalDate, OffsetDateTime}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.Source
import scala.util.control.NonFatal

abstract class IngestJobApp[T <: IngestJob[_]: Manifest]
    extends TeletrackerTaskApp[T] {
  val offset = flag[Int]("offset", 0, "The offset to start at")
  val limit = flag[Int]("limit", -1, "The number of items to process")

  val inputFile = flag[File]("input", "The json file to parse")
  val titleMatchThreshold = flag[Int]("fuzzyThreshold", 15, "X")
  val dryRun = flag[Boolean]("dryRun", true, "X")
}

trait IngestJobArgsLike {
  val offset: Int
  val limit: Int
  val titleMatchThreshold: Int
  val dryRun: Boolean
}

@JsonCodec
case class IngestJobArgs(
  inputFile: URI,
  offset: Int,
  limit: Int,
  titleMatchThreshold: Int,
  dryRun: Boolean,
  parallelism: Int,
  sourceLimit: Int,
  perBatchSleepMs: Option[Int],
  enableExternalIdMatching: Boolean = true,
  reimport: Boolean = false,
  externalIdFilter: Option[String] = None)
    extends IngestJobArgsLike

abstract class IngestJob[T <: ScrapedItem](
  implicit codec: Codec[T],
  jsonableArgs: JsonableArgs[IngestJobArgs])
    extends BaseIngestJob[T, IngestJobArgs]()(
      jsonableArgs,
      scala.concurrent.ExecutionContext.Implicits.global,
      codec
    ) {

  import diffson._
  import diffson.circe._
  import diffson.jsonpatch.simplediff._
  import io.circe.syntax._

  implicit protected val execCtx =
    scala.concurrent.ExecutionContext.Implicits.global

  protected def itemLookup: ItemLookup
  protected def itemUpdater: ItemUpdater
  protected def s3: S3Client
  protected def networkCache: NetworkCache

  protected def externalSources: List[ExternalSource]

  @Inject
  private[this] var externalIdLookup: ElasticsearchExternalIdLookup.Factory = _
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
      externalSources.map(externalSource => {
        externalIdLookup.createOpt[T](
          externalSource,
          (item: T) => getExternalIds(item).get(externalSource)
        )
      })
    } else {
      Nil
    }

    new CustomElasticsearchLookup[T](
      externalIdMatchers :+ elasticsearchLookup.toMethod[T]
    )
  }

  protected def processMode(): ProcessMode =
    Parallel(args.parallelism, args.perBatchSleepMs.map(_ millis))

  protected def outputLocation: Option[URI] = None

  protected def getAdditionalOutputFiles: Seq[(File, String)] = Seq()

  override def preparseArgs(args: RawArgs): ArgsType = parseArgs(args)

  final protected def parseArgs(
    args: Map[String, Option[Any]]
  ): IngestJobArgs = {
    IngestJobArgs(
      inputFile = args.valueOrThrow[URI]("inputFile"),
      offset = args.valueOrDefault("offset", 0),
      limit = args.valueOrDefault("limit", -1),
      titleMatchThreshold = args.valueOrDefault("fuzzyThreshold", 15),
      dryRun = args.valueOrDefault("dryRun", true),
      parallelism = args.valueOrDefault("parallelism", 32),
      sourceLimit = args.valueOrDefault("sourceLimit", -1),
      perBatchSleepMs = args.value[Int]("perBatchSleepMs"),
      enableExternalIdMatching =
        args.valueOrDefault("enableExternalIdMatching", true),
      reimport = args.valueOrDefault("reimport", false),
      externalIdFilter = args.value[String]("externalIdFilter")
    )
  }

  override def runInternal(): Unit = {
    registerArtifact(potentialMatchFile)
    registerArtifact(matchChangesFile)

    val network = getNetworksOrExit()

    logger.info("Running preprocess phase")
    preprocess()

    if (args.reimport) {
      val sources = externalSources.map(_.getName)
      val matchingNetworks =
        network.filter(net => sources.contains(net.slug.value))
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

    logger.info(s"Starting ingest of ${externalSources} content")

    new SourceRetriever(s3)
      .getSourceStream(args.inputFile)
      .safeTake(args.sourceLimit)
      .foreach(processSource(_, network))

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
            createAvailabilities(
              networks,
              esItem,
              item
            )

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
                  case (uuid, (existingItem, keys)) =>
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
              ItemUpdateApplier.removeAvailabilitiesForNetworks(_, networks)
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
        externalSource <- externalSources
        (item, t) <- potentialMatches
        externalId <- t.externalId.toList
      } yield {
        val esExternalId = EsExternalId(externalSource, externalId)
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

  protected def getNetworksOrExit(): Set[StoredNetwork] = {
    val foundNetworks = networkCache
      .getAllNetworks()
      .await()
      .collect {
        case network
            if externalSources.map(_.getName).contains(network.slug.value) =>
          network
      }
      .toSet

    if (externalSources
          .map(_.getName)
          .toSet
          .diff(foundNetworks.map(_.slug.value))
          .nonEmpty) {
      throw new IllegalStateException(
        s"""Could not find all networks "${externalSources}" network from datastore"""
      )
    }

    foundNetworks
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

  protected def getExternalIds(item: T): Map[ExternalSource, String] = Map.empty

  protected def uploadResultFiles(): Unit = {
    outputLocation.foreach(uri => {
      try {
        new SourceWriter(s3).writeFile(
          uri.resolve(s"${uri.getPath}/match-items.txt"),
          matchItemsFile.toPath
        )
      } catch {
        case NonFatal(e) => logger.error("Error writing match-items file", e)
      }

      try {
        new SourceWriter(s3).writeFile(
          uri.resolve(s"${uri.getPath}/missing-items.txt"),
          missingItemsFile.toPath
        )
      } catch {
        case NonFatal(e) => logger.error("Error writing missing-items file", e)
      }

      try {
        new SourceWriter(s3).writeFile(
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
