package com.teletracker.tasks

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
    implicit val thingyCodec = deriveCodec[Thingy]

    val queue = new PriorityQueue[Thingy](100, new Comparator[Thingy] {
      override def compare(
        o1: Thingy,
        o2: Thingy
      ): Int = if (o1.popularity > o2.popularity) 1 else -1
    })
    val file = args.value[File]("input").get

    val source = Source
      .fromFile(file)

    source
      .getLines()
      .toStream
      .map(decode[Thingy](_).right.get)
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

case class Thingy(
  adult: Boolean,
  id: Int,
  original_title: String,
  popularity: Double,
  video: Boolean)
