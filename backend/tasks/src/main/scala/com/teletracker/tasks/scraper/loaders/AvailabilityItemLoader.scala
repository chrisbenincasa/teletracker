package com.teletracker.tasks.scraper.loaders

import com.teletracker.common.db.model.SupportedNetwork
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

trait AvailabilityItemLoaderArgs {
  def enableCaching: Boolean = true
  def supportedNetworks: Set[SupportedNetwork]
}

abstract class AvailabilityItemLoader[T, Args <: AvailabilityItemLoaderArgs](
  implicit executionContext: ExecutionContext) {
  private var loadedOnce = false
  private lazy val cache = ConcurrentHashMap.newKeySet[T]

  def load(args: Args): Future[List[T]] = synchronized {
    if (loadedOnce && args.enableCaching) {
      Future.successful(getCache)
    } else {
      loadImpl(args).andThen {
        case Success(value) if args.enableCaching =>
          setCache(value)
          loadedOnce = true
      }
    }
  }

  protected def loadImpl(args: Args): Future[List[T]]

  protected def getCache: List[T] = cache.asScala.toList

  protected def setCache(items: List[T]): Unit =
    cache.addAll(items.asJavaCollection)

  protected def markAsLoadedOnce(): Unit = synchronized(loadedOnce = true)
}
