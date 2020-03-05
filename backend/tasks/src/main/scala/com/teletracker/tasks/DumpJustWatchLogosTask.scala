package com.teletracker.tasks

import com.teletracker.common.http.{HttpClient, HttpClientOptions}
import com.teletracker.common.model.justwatch.Provider
import javax.inject.Inject
import java.io.{File, FileOutputStream}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object DumpJustWatchLogosTask extends TeletrackerTaskApp[DumpJustWatchLogosTask]

class DumpJustWatchLogosTask @Inject()(httpClientFactory: HttpClient.Factory)
    extends TeletrackerTaskWithDefaultArgs {

  override def runInternal(args: Args): Unit = {
    import io.circe.generic.auto._
    import io.circe.parser._

    lazy val imagesClient = httpClientFactory.create(
      "images.justwatch.com",
      HttpClientOptions.withTls
    )

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

          imagesClient
            .getBytes(path)
            .map(response => {
              val f = new File(outputDirBase + s"/${provider.slug}/icon.jpg")
              f.getParentFile.mkdirs()
              val fos = new FileOutputStream(f)
              fos.write(response.content)
              fos.flush()
              fos.close()
            })
        })

        Await.result(Future.sequence(saves), Duration.Inf)
    }
  }
}
