package com.teletracker.common.process

import com.teletracker.common.process.tmdb.TmdbProcessMessage
import com.twitter.concurrent.NamedPoolThreadFactory
import javax.inject.Inject
import java.util.concurrent.{
  ConcurrentHashMap,
  ConcurrentLinkedQueue,
  Executors,
  TimeUnit
}
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait ProcessQueue[M <: Message] {
  def enqueue(message: M): Future[Unit]
  def dequeue(n: Int): Future[List[M]]
  def ack(ids: Set[String]): Future[Unit]
  def ack(id: String): Future[Unit] = ack(Set(id))
}

trait ProcessQueueConsumer[Message] {}

trait Message {
  def id: String
}

final case class WrappedMessage[Contents <: Message](
  enqueuedTime: Long,
  timeout: FiniteDuration = 30 seconds,
  attempts: Int = 1,
  retries: Int = 5,
  contents: Contents)

final class InMemoryFifoProcessQueue[M <: Message] @Inject()(
)(implicit executionContext: ExecutionContext)
    extends ProcessQueue[M]
    with ProcessQueueConsumer[M]
    with AutoCloseable {

  final private val queue =
    new ConcurrentLinkedQueue[WrappedMessage[M]]()

  final private val reaperPool = Executors.newSingleThreadScheduledExecutor(
    new NamedPoolThreadFactory("ProcessQueueReaper", makeDaemons = true)
  )

  reaperPool.schedule(
    new Runnable {
      override def run(): Unit = {
        val now = System.currentTimeMillis()
        val expired = inFlight
          .keySet()
          .asScala
          .filter(message => {
            val timePassed = (now - message.enqueuedTime).millis
            timePassed.toMillis > message.timeout.toMillis
          })

        expired.foreach(message => {
          if (message.attempts < message.retries) {
            queue.offer(message.copy(attempts = message.attempts + 1))
          }

          inFlight.remove(message)
        })
      }
    },
    1,
    TimeUnit.SECONDS
  )

  final private val inFlight =
    new ConcurrentHashMap[WrappedMessage[M], Boolean]()

  final private val acked = new ConcurrentHashMap[String, Boolean]()

  override def enqueue(message: M): Future[Unit] = {
    Future.successful {
      queue.offer(
        WrappedMessage(System.currentTimeMillis(), contents = message)
      )
    }
  }

  override def dequeue(n: Int): Future[List[M]] = {
    require(n >= 0)
    if (n == 0) {
      Future.successful(Nil)
    } else {
      synchronized {
        Future.successful {
          val items = (0 until n).toList.flatMap(_ => Option(queue.poll()))
          val unackedItems = items
          unackedItems
            .filter(item => {
              if (acked.contains(item.contents.id)) {
                acked.remove(item.contents.id)
                false
              } else {
                true
              }
            })

          unackedItems.foreach(inFlight.putIfAbsent(_, true))

          unackedItems.map(_.contents)
        }
      }
    }
  }

  override def ack(ids: Set[String]): Future[Unit] = {
    Future.successful(acked.putAll(ids.map(_ -> true).toMap.asJava))
  }

  override def close(): Unit = {
    reaperPool.shutdown()
  }
}
