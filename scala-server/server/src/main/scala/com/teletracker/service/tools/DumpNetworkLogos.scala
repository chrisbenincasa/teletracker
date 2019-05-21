package com.teletracker.service.tools

import com.teletracker.service.external.tmdb.TmdbClient
import com.teletracker.service.inject.Modules
import com.teletracker.service.model.tmdb.Network
import com.teletracker.service.util.Implicits._
import com.teletracker.service.util.execution.SequentialFutures
import com.google.inject.Module
import com.twitter.finagle.Http
import com.twitter.finagle.http.{Request, Response}
import java.io.{File, FileOutputStream}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object DumpNetworkLogos extends com.twitter.inject.app.App {
  override protected def modules: Seq[Module] = Modules()

  override protected def run(): Unit = {
    import io.circe.generic.auto._
    import io.circe.parser._

    lazy val imagesClient =
      Http.client.withTls("image.tmdb.org").newService("image.tmdb.org:443")

    val logoSizes = List(
      "w45",
      "w92",
      "w154",
      "w185",
      "w300",
      "w500",
      "original"
    )

    val client = injector.instance[TmdbClient]

    val lines = scala.io.Source
      .fromFile(
        new File(
          System.getProperty("user.dir") + "/data/tv_network_ids_06_26_2018.txt"
        )
      )
      .getLines()

    val parsed = lines
      .map(line => {
        parse(line.trim).flatMap(_.as[NetworkLine])
      })
      .drop(90)
      .toList

    val outputDirBase = System.getProperty("user.dir") + "/data/logos"
    val outputDir = new File(outputDirBase)
    if (!outputDir.exists()) {
      outputDir.mkdir()
    }

    val finish = SequentialFutures.serialize(parsed, Some(250 millis)) {
      case Left(error) =>
        println(s"Got error: $error")
        Future.unit
      case Right(line) =>
        client
          .makeRequest[Network](
            s"/network/${line.id}",
            Seq("append_to_response" -> "images")
          )
          .flatMap(network => {
            val logos = network.images.flatMap(_.logos).getOrElse(Nil)
            println(
              s"Found ${logos.size} logos for network ${network.name.get}"
            )
            val requests = for {
              (logo, idx) <- logos.zipWithIndex
              if logo.file_path.isDefined
              size <- logoSizes
            } yield {
              val r = Request(s"/t/p/$size/${logo.file_path.get}")
              val typ = logo.file_path.get.split("\\.").last
              () =>
                (imagesClient(r): Future[Response]).map(response => {
                  val f = new File(
                    outputDirBase + s"/${network.name.get}/${network.name.get}-${idx}_${size}.$typ"
                  )
                  f.getParentFile.mkdirs()
                  val bb = new Array[Byte](response.content.length)
                  response.content.write(bb, 0)
                  val fos = new FileOutputStream(f)
                  fos.write(bb)
                  fos.flush()
                  fos.close()
                })
            }

            SequentialFutures.batchedIterator[() => Future[Unit], Unit](
              requests.iterator,
              5,
              _ ++ _
            )(batch => {
              Future.sequence(batch.map(f => f().recover { case _ => }))
            })
          })
          .recover { case _ => }
    }

    Await.result(finish, Duration.Inf)
  }
}

case class NetworkLine(
  id: Int,
  name: String)
