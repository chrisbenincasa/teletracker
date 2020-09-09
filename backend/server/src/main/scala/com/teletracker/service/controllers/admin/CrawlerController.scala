package com.teletracker.service.controllers.admin

import com.teletracker.common.db.dynamo.{CrawlStore, CrawlerName}
import com.teletracker.common.model.DataResponse
import com.teletracker.service.auth.AdminFilter
import com.teletracker.service.controllers.BaseController
import com.twitter.finatra.request.QueryParam
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class CrawlerController @Inject()(
  crawlStore: CrawlStore
)(implicit executionContext: ExecutionContext)
    extends BaseController {
  filter[AdminFilter].prefix("/api/v1/internal") {
    prefix("/crawls") {
      get("/:crawlerName") { req: GetCrawlsRequest =>
        crawlStore
          .getAllCrawls(new CrawlerName(req.crawlerName), None)
          .map(crawls => response.okCirceJsonResponse(DataResponse(crawls)))
      }

      put("/close") { req: CloseCrawlRequest =>
        crawlStore
          .closeCrawl(
            new CrawlerName(req.crawlerName),
            req.version,
            req.totalItemsCrawled
          )
          .map(_ => {
            response.noContent
          })
      }
    }
  }
}

case class GetCrawlsRequest(@QueryParam crawlerName: String)

case class CloseCrawlRequest(
  crawlerName: String,
  version: Long,
  totalItemsCrawled: Option[Int])
