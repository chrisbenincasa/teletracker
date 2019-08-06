package com.teletracker.service.tools

import com.teletracker.common.inject.Modules
import com.teletracker.common.model.justwatch.Provider
import com.teletracker.common.util.Implicits._
import com.google.inject.Module
import com.twitter.finagle.Http
import com.twitter.finagle.http.{Request, Response}
import io.circe.parser.parse
import java.io.{File, FileOutputStream}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object DumpJustWatchLogos extends com.twitter.inject.app.App {
  override protected def modules: Seq[Module] = Modules()

  override protected def run(): Unit = {
    import io.circe.generic.auto._
    import io.circe.parser._

    lazy val imagesClient = Http.client
      .withTls("images.justwatch.com")
      .newService("images.justwatch.com:443")

    val lines = scala.io.Source
      .fromFile(
        new File(System.getProperty("user.dir") + "/data/providers.json")
      )
      .getLines()
      .mkString("")
      .trim

    val outputDirBase = System.getProperty("user.dir") + "/data/logos"
    val outputDir = new File(outputDirBase)
    if (!outputDir.exists()) {
      outputDir.mkdir()
    }

    parse(lines).flatMap(_.as[List[Provider]]) match {
      case Left(err) =>
        println(err)
        sys.exit(1)
      case Right(providers) =>
        val saves = providers.map(provider => {
          val path = provider.icon_url
            .replaceAllLiterally("{profile}", "s100") + s"/${provider.slug}"
          val r = Request(path)
          (imagesClient(r): Future[Response]).map(response => {
            val f = new File(outputDirBase + s"/${provider.slug}/icon.jpg")
            f.getParentFile.mkdirs()
            val bb = new Array[Byte](response.content.length)
            response.content.write(bb, 0)
            val fos = new FileOutputStream(f)
            fos.write(bb)
            fos.flush()
            fos.close()
          })
        })

        Await.result(Future.sequence(saves), Duration.Inf)
    }
  }
}
