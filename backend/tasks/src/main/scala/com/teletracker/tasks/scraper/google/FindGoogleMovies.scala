package com.teletracker.tasks.scraper.google

import com.teletracker.common.http.{HttpClient, HttpClientOptions}
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.AsyncStream
import com.teletracker.common.util.Futures._
import javax.inject.Inject
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.{GZIPInputStream, ZipException}
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import scala.xml.XML

class FindGoogleMovies @Inject()(
  httpFactory: HttpClient.Factory
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  private val client =
    httpFactory.create(
      "play.google.com",
      HttpClientOptions.default.copy(poolSize = 16)
    )

  override protected def runInternal(): Unit = {
    val fmt = "/sitemaps/sitemaps-index-%d.xml"
    val counter = new AtomicLong()
    AsyncStream
      .fromSeq(0 to 9)
      .flatMapF(i => {
        client
          .get(fmt.format(i))
          .map(response => {
            val xml = XML.loadString(response.content)

            xml \\ "sitemapindex" \\ "sitemap" \\ "loc" map (_.text)
          })
          .map(_.sorted.distinct)
      })
      .foreachConcurrent(8)(url => {
        client
          .getBytes(url.split("/").takeRight(2).mkString("/"))
          .map(response => {
            val bytes = response.content

            val xml =
              try {
                XML.load(new GZIPInputStream(new ByteArrayInputStream(bytes)))
              } catch {
                case e: ZipException =>
                  XML.load(new ByteArrayInputStream(bytes))
              }

            val urls = xml \\ "urlset" \\ "url" \\ "loc" map (_.text)

            val matching = urls.filter(_.contains("/movies/"))

            if (matching.nonEmpty) {
              counter.addAndGet(matching.size)
            }

            println(s"Counter at: ${counter.get()}")
          })
          .recover {
            case NonFatal(e) =>
              logger.error(s"Couldn't fetch $url", e)
          }
          .map(_ => {})
      })
      .await()

    println(s"Found a total of ${counter.get()} movies")
  }
}
