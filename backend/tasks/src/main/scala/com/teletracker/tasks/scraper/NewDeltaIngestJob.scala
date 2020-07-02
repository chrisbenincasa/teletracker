//package com.teletracker.tasks.scraper
//
//import com.teletracker.common.db.dynamo.{CrawlStore, CrawlerName}
//import com.teletracker.common.db.model.SupportedNetwork
//import com.teletracker.common.elasticsearch.{
//  AvailabilityQueryBuilder,
//  ItemsScroller
//}
//import com.teletracker.common.elasticsearch.model.EsItem
//import com.teletracker.common.model.scraping.{
//  ScrapedItem,
//  ScrapedItemAvailabilityDetails
//}
//import com.teletracker.common.model.scraping.netflix.NetflixScrapedCatalogItem
//import com.teletracker.common.tasks.TeletrackerTask.RawArgs
//import com.teletracker.common.tasks.{
//  TaskArgImplicits,
//  TypedTeletrackerTask,
//  UntypedTeletrackerTask
//}
//import com.teletracker.common.util.NetworkCache
//import com.teletracker.tasks.scraper.loaders.{
//  AvailabilityItemLoader,
//  ElasticsearchAvailabilityItemLoader
//}
//import com.teletracker.tasks.util.SourceRetriever
//import io.circe.Json.JString
//import io.circe.generic.JsonCodec
//import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json}
//import javax.inject.Inject
//import org.elasticsearch.index.query.QueryBuilders
//import org.slf4j.LoggerFactory
//import java.net.URI
//import java.util.concurrent.ConcurrentHashMap
//import scala.concurrent.{ExecutionContext, Future}
//import scala.collection.JavaConverters._
//import scala.util.Success
//
//object NewDeltaIngestJob {}
//
//@JsonCodec
//case class NewDeltaIngestJobArgs(leftItemMode: ItemLoaderMode)
//
//class NewDeltaIngestJob[
//  T <: ScrapedItem: Decoder: ScrapedItemAvailabilityDetails] @Inject()(
//  crawlAvailabilityItemLoaderFactory: CrawlAvailabilityItemLoaderFactory,
//  uriAvailabilityItemLoaderFactory: UriAvailabilityItemLoaderFactory,
//  elasticsearchAvailabilityItemLoader: ElasticsearchAvailabilityItemLoader
//)(implicit executionContext: ExecutionContext)
//    extends TypedTeletrackerTask[NewDeltaIngestJobArgs] {
//
//  override def preparseArgs(args: RawArgs): NewDeltaIngestJobArgs = {
//    NewDeltaIngestJobArgs(
//      leftItemMode =
//        ItemLoaderMode.fromString(args.valueOrThrow[String]("leftItemMode"))
//    )
//  }
//
//  override protected def runInternal(): Unit = {
//    // Load
//    args.leftItemMode match {
//      case x: DynamoItemLoaderMode.type =>
//        val z = x.parseArgs("left", rawArgs)
//        crawlAvailabilityItemLoaderFactory
//          .make[T]
//          .load(CrawlAvailabilityItemLoaderArgs(Set(), z.crawler, z.version))
//      case x: FileSourceItemLoaderMode.type =>
//        val z = x.parseArgs()
//      case ElasticsearchItemLoaderMode =>
//    }
//  }
//}
//object ItemLoaderMode {
//  implicit final val decoder: Decoder[ItemLoaderMode] = Decoder.instance(
//    c =>
//      c.value match {
//        case JString("dynamo")        => Right(DynamoItemLoaderMode)
//        case JString("file")          => Right(FileSourceItemLoaderMode)
//        case JString("elasticsearch") => Right(ElasticsearchItemLoaderMode)
//        case _                        => Left(DecodingFailure("ItemLoaderMode", c.history))
//      }
//  )
//
//  implicit final val encoder: Encoder[ItemLoaderMode] = Encoder.instance {
//    case DynamoItemLoaderMode        => Json.fromString("dynamo")
//    case FileSourceItemLoaderMode    => Json.fromString("file")
//    case ElasticsearchItemLoaderMode => Json.fromString("elasticsearch")
//  }
//
//  implicit val codec: Codec[ItemLoaderMode] =
//    Codec.from(decoder, encoder)
//
//  def fromString(str: String): ItemLoaderMode = str.toLowerCase match {
//    case "dynamo"        => DynamoItemLoaderMode
//    case "file"          => FileSourceItemLoaderMode
//    case "elasticsearch" => ElasticsearchItemLoaderMode
//    case _ =>
//      throw new IllegalArgumentException(s"Unrecognized ItemLoaderMode = $str")
//  }
//}
//sealed trait ItemLoaderMode extends TaskArgImplicits {
//  type Args
//  def parseArgs(
//    side: String,
//    args: RawArgs
//  ): Args
//}
//case object DynamoItemLoaderMode extends ItemLoaderMode {
//  case class DynamoItemLoaderModeArgs(
//    crawler: CrawlerName,
//    version: Option[Long])
//  override type Args = DynamoItemLoaderModeArgs
//  override def parseArgs(
//    side: String,
//    args: RawArgs
//  ): DynamoItemLoaderModeArgs =
//    DynamoItemLoaderModeArgs(
//      new CrawlerName(args.valueOrThrow[String](s"$side.crawler")),
//      args.value[Long](s"$side.crawlVersion")
//    )
//}
//case object FileSourceItemLoaderMode extends ItemLoaderMode {
//  case class FileSourceItemLoaderModeArgs(location: URI)
//
//  override type Args = FileSourceItemLoaderModeArgs
//
//  override def parseArgs(
//    side: String,
//    args: RawArgs
//  ): FileSourceItemLoaderModeArgs =
//    FileSourceItemLoaderModeArgs(
//      location = args.valueOrThrow[URI](s"$side.location")
//    )
//}
//case object ElasticsearchItemLoaderMode extends ItemLoaderMode {
//  override type Args = this.type
//
//  override def parseArgs(
//    side: String,
//    args: RawArgs
//  ): ElasticsearchItemLoaderMode = ???
//}
//
//// Diff scrape incoming against live
//// Diff scrape against scrape
//abstract class LiveDeltaIngestJob[
//  T <: ScrapedItem: ScrapedItemAvailabilityDetails] @Inject()() {}
