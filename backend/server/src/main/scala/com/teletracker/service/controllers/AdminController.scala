package com.teletracker.service.controllers

import com.teletracker.common.cache.{JustWatchLocalCache, TmdbLocalCache}
import com.teletracker.common.db.model.ItemType
import com.teletracker.common.elasticsearch.ItemLookup
import com.teletracker.common.elasticsearch.scraping.{
  EsPotentialMatchItemStore,
  PotentialMatchItemSearch
}
import com.teletracker.common.model.{DataResponse, Paging}
import com.teletracker.common.model.scraping.ScrapeItemType
import com.teletracker.common.util.HasThingIdOrSlug
import com.teletracker.service.api.ItemApi
import com.teletracker.service.auth.AdminFilter
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.QueryParam
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import io.circe.syntax._

class AdminController @Inject()(
  itemLookup: ItemLookup,
  esPotentialMatchItemStore: EsPotentialMatchItemStore
)(implicit executionContext: ExecutionContext)
    extends Controller {

  // TODO put on admin server and open up admin server port on GCP
  filter[AdminFilter].get("/version") { _: Request =>
    response.ok.body(
      getClass.getClassLoader.getResourceAsStream("version_info.txt")
    )
  }

  filter[AdminFilter].prefix("/api/v1/internal") {
    prefix("/potential_matches") {
      get("/search") { req: PotentialMatchSearchRequest =>
        esPotentialMatchItemStore
          .search(
            PotentialMatchItemSearch(
              scraperType = req.scraperItemType,
              limit = req.limit
            )
          )
          .map(resp => {
            response
              .ok(
                DataResponse.forDataResponse(
                  DataResponse(
                    resp.items,
                    Some(
                      Paging(
                        resp.bookmark.map(_.encode),
                        total = Some(resp.totalHits)
                      )
                    )
                  )
                )
              )
              .contentTypeJson()
          })
      }
    }
  }

  get("/admin/finatra/things/:thingId", admin = true) { req: Request =>
    (HasThingIdOrSlug.parse(req.getParam("thingId")) match {
      case Left(id) =>
        itemLookup.lookupItemsByIds(Set(id)).map(_.get(id).flatten)
      case Right(slug) =>
        itemLookup.lookupItemBySlug(
          slug,
          ItemType.fromString(req.getParam("type")),
          None
        )
    }).map {
      case None => response.notFound
      case Some(thing) =>
        response.ok(DataResponse.complex(thing)).contentTypeJson()
    }
  }
}

case class PotentialMatchSearchRequest(
  @QueryParam scraperItemType: Option[ScrapeItemType],
  @QueryParam limit: Int = 20)

case class RefreshThingRequest(thingId: String) extends HasThingIdOrSlug
case class ScrapeTmdbRequest(
  id: Int,
  thingType: ItemType)
