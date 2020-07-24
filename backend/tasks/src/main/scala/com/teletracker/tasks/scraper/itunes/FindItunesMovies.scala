package com.teletracker.tasks.scraper.itunes

import com.teletracker.common.http.{HttpClient, HttpClientOptions}
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.AsyncStream
import com.teletracker.common.util.Futures._
import javax.inject.Inject
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import scala.concurrent.ExecutionContext
import scala.xml.XML

class FindItunesMovies @Inject()(
  httpFactory: HttpClient.Factory
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  private val client =
    httpFactory.create("sitemaps.itunes.apple.com", HttpClientOptions.default)

  override protected def runInternal(): Unit = {
    val path = rawArgs.valueOrThrow[String]("path")

    val urls = client
      .get(path)
      .map(response => {
        val xml = XML.loadString(response.content)

        xml \\ "sitemapindex" \\ "sitemap" \\ "loc" map (_.text)
      })
      .await()
      .sorted
      .distinct

    println(s"Checking ${urls.size} urls")

    AsyncStream
      .fromSeq(urls)
      .foreachConcurrent(32)(url => {
        println(s"Fetching ${url}")
        client
          .getBytes(url.split("/").last)
          .map(response => {
            val bytes = response.content

            val xml =
              XML.load(new GZIPInputStream(new ByteArrayInputStream(bytes)))

            val urls = xml \\ "urlset" \\ "url" \\ "loc" map (_.text)

            val matching = urls.filter(_.contains("/movies/"))

            if (matching.nonEmpty) {
              println(s"Found ${matching} at url ${url}")
            }
          })
      })
      .await()
  }
}
