package com.teletracker.tasks.tmdb

import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.http.{HttpClient, HttpClientOptions}
import com.teletracker.common.model.tmdb.Network
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.tasks.TeletrackerTask
import javax.inject.Inject
import java.io.{File, FileOutputStream}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class DumpNetworkLogos @Inject()(
  httpClientFactory: HttpClient.Factory,
  tmdbClient: TmdbClient)
    extends TeletrackerTask {

  override def run(args: Args): Unit = {
    import io.circe.generic.auto._
    import io.circe.parser._

    lazy val imagesClient =
      httpClientFactory.create(
        "image.tmdb.org",
        HttpClientOptions.withTls
      )

    val logoSizes = List(
      "w45",
      "w92",
      "w154",
      "w185",
      "w300",
      "w500",
      "original"
    )

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
        tmdbClient
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
              val typ = logo.file_path.get.split("\\.").last
              () =>
                imagesClient
                  .getBytes(s"/t/p/$size/${logo.file_path.get}")
                  .map(response => {
                    val f = new File(
                      outputDirBase + s"/${network.name.get}/${network.name.get}-${idx}_${size}.$typ"
                    )
                    val fos = new FileOutputStream(f)
                    fos.write(response.content)
                    fos.flush()
                    fos.close()
                  })
            }

            SequentialFutures.batchedIteratorAccum[() => Future[Unit], Unit](
              requests.iterator,
              5
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
