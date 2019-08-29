package com.teletracker.tasks.tmdb

import com.teletracker.tasks.tmdb.export_tasks.MovieDumpFileRow
import com.teletracker.tasks.{TeletrackerTask, TeletrackerTaskApp}
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser._
import java.io.File
import java.util.{Comparator, PriorityQueue}
import scala.collection.JavaConverters._
import scala.io.Source

object MostPopularItemsApp extends TeletrackerTaskApp[MostPopularItems] {
  val file = flag[File]("input", "The offset to start at")
}

class MostPopularItems extends TeletrackerTask {
  override def run(args: Args): Unit = {
    implicit val thingyCodec = deriveCodec[MovieDumpFileRow]

    val queue = new PriorityQueue[MovieDumpFileRow](
      100,
      new Comparator[MovieDumpFileRow] {
        override def compare(
          o1: MovieDumpFileRow,
          o2: MovieDumpFileRow
        ): Int = if (o1.popularity > o2.popularity) 1 else -1
      }
    )
    val file = args.value[File]("input").get

    val source = Source
      .fromFile(file)

    source
      .getLines()
      .toStream
      .map(decode[MovieDumpFileRow](_).right.get)
      .foreach(thingy => {
        queue.offer(thingy)
        if (queue.size() > 100) {
          queue.poll()
        }
      })

    source.close()

    queue.asScala.toList.sortBy(-_.popularity).foreach(println)
  }
}
