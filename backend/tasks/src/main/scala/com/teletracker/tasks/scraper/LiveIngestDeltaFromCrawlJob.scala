//package com.teletracker.tasks.scraper
//
//import com.google.inject.Injector
//import com.teletracker.common.db.dynamo.{
//  CrawlStore,
//  CrawlerName,
//  HistoricalCrawlOutput
//}
//import com.teletracker.common.model.scraping.ScrapedItem
//import com.teletracker.common.model.scraping.netflix.NetflixScrapedCatalogItem
//import com.teletracker.common.tasks.TeletrackerTask.{JsonableArgs, RawArgs}
//import com.teletracker.common.util.Futures._
//import com.teletracker.common.tasks.{TeletrackerTask, TypedTeletrackerTask}
//import com.teletracker.tasks.scraper.hbo.IngestHboLiveCatalogDelta
//import io.circe.generic.JsonCodec
//import javax.inject.Inject
//import shapeless.ops.product.ToMap
//import shapeless.{Generic, HList, LabelledGeneric}
//import java.net.URI
//import scala.reflect.ClassTag
//
//@JsonCodec
//case class LiveIngestDeltaFromCrawlJobArgs(crawler: String)
//
//class TestLiveIngestDeltaFromCrawlJob
//    extends LiveIngestDeltaFromCrawlJob[IngestHboLiveCatalogDelta]
//
//abstract class LiveIngestDeltaFromCrawlJob[T <: LiveIngestDeltaJob[_]]
//    extends CalculatedTeletrackerTask[T, LiveIngestDeltaFromCrawlJobArgs] {
//  @Inject private var crawlStore: CrawlStore = _
//
//  override def preparseArgs(args: RawArgs): LiveIngestDeltaFromCrawlJobArgs =
//    LiveIngestDeltaFromCrawlJobArgs(
//      crawler = args.valueOrThrow[String]("crawler")
//    )
//
//  override protected def getSeedTaskArgs: RawArgs = {
//    val crawl = crawlStore.getLatestCrawl(new CrawlerName(args.crawler)).await()
//
//    crawl match {
//      case None =>
//        logger.error(s"Could not find a crawl for ${args.crawler}")
//        ???
//      case Some(value) =>
//        val outputs = value.metadata
//          .flatMap(_.outputs)
//          .map(_.fold(Map.empty[String, HistoricalCrawlOutput])(_ ++ _))
//          .getOrElse(Map.empty)
//
//        if (outputs.isEmpty) {
//          ???
//        } else {
//          val s3Output = outputs
//            .find {
//              case (loc, _) =>
//                val uri = URI.create(loc)
//                uri.getScheme == "s3"
//            }
//            .map(_._1)
//
//          s3Output match {
//            case Some(value) =>
//              val args = IngestDeltaJobArgs(
//                snapshotAfter = URI.create(value),
//                snapshotBefore = None
//              )
//
//              val x = toRawArgs(args)
//              println(x ++ rawArgs)
//              ???
//            case None =>
//              ???
//          }
//        }
//    }
//  }
//
//  private def toRawArgs[X](
//    x: X
//  )(implicit toMap: ToMap.Aux[X, Symbol, Any]
//  ): Map[String, Any] = {
//    toMap(x).map {
//      case (symbol, option) => symbol.name -> option
//    }
//  }
//}
//
//abstract class CalculatedTeletrackerTask[
//  TaskType <: TeletrackerTask,
//  SeedJobArgsType <: AnyRef
//](implicit jsonableArgs: JsonableArgs[SeedJobArgsType],
//  ct: ClassTag[TaskType])
//    extends TypedTeletrackerTask[SeedJobArgsType] {
//  @Inject private var injector: Injector = _
//
//  protected def getSeedTaskArgs: RawArgs
//
//  override protected def runInternal(): Unit = {
//    val taskArgs = getSeedTaskArgs
//    val task =
//      injector.getInstance(ct.runtimeClass.asInstanceOf[Class[TaskType]])
//    task.run(taskArgs)
//  }
//}
