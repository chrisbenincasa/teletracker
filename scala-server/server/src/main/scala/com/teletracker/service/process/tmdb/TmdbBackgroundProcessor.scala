package com.teletracker.service.process.tmdb

import com.google.inject.Provider
import com.teletracker.service.process.ProcessQueue
import com.teletracker.service.util.Futures._
import com.twitter.concurrent.NamedPoolThreadFactory
import javax.inject.Inject
import java.util.concurrent.Executors
import scala.annotation.tailrec
import scala.util.control.NonFatal

final class TmdbBackgroundProcessor @Inject()(
  queue: ProcessQueue[TmdbProcessMessage],
  handlerProvider: Provider[TmdbMessageHandler])
    extends AutoCloseable {
  final private val pool = Executors.newFixedThreadPool(
    10,
    new NamedPoolThreadFactory("TmdbBackgroundWorker", makeDaemons = true)
  )

  final private val looper = Executors.newSingleThreadExecutor()

  @volatile private var _running = false
  @volatile private var _shutdownRequested = false

  def run(): Unit = {
    if (!_running) {
      println("Starting background processor...")
      _running = true
      looper.submit(new Runnable {
        override def run(): Unit = {
          runInternal()
        }
      })
    }
  }

  @tailrec
  private def runInternal(): Unit = {
    if (_shutdownRequested) return

    try {
      queue
        .dequeue(3)
        .await()
        .foreach(message => {
          pool.submit(new Runnable {
            override def run(): Unit = {
              handlerProvider.get().handle(message).await()
              queue.ack(message.id)
            }
          })
        })
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
    }

    Thread.sleep(500)

    runInternal()
  }

  override def close(): Unit = {
    _shutdownRequested = true
    pool.shutdown()
    looper.shutdown()
  }
}
