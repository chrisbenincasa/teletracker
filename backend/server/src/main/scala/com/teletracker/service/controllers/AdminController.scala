package com.teletracker.service.controllers

import com.teletracker.common.aws.sqs.SqsFifoQueue
import com.teletracker.common.db.Bookmark
import com.teletracker.common.db.model.{ItemType, SupportedNetwork}
import com.teletracker.common.elasticsearch.model.EsPotentialMatchState
import com.teletracker.common.elasticsearch.scraping.{
  EsPotentialMatchItemStore,
  PotentialMatchItemSearch
}
import com.teletracker.common.elasticsearch.util.ItemUpdateApplier
import com.teletracker.common.elasticsearch.{
  ItemLookup,
  ItemLookupResponse,
  ItemUpdater
}
import com.teletracker.common.model.scraping.ScrapeCatalogType
import com.teletracker.common.model.{DataResponse, Paging}
import com.teletracker.common.pubsub.EsDenormalizeItemMessage
import com.teletracker.common.util.{HasThingIdOrSlug, NetworkCache}
import com.teletracker.service.auth.AdminFilter
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AdminController @Inject()(
  itemLookup: ItemLookup,
  esPotentialMatchItemStore: EsPotentialMatchItemStore,
  itemUpdater: ItemUpdater,
  itemDenormQueue: SqsFifoQueue[EsDenormalizeItemMessage]
)(implicit executionContext: ExecutionContext)
    extends BaseController {

  // TODO put on admin server and open up admin server port on GCP
  filter[AdminFilter].get("/version") { _: Request =>
    response.ok.body(
      getClass.getClassLoader.getResourceAsStream("version_info.txt")
    )
  }

  filter[AdminFilter].prefix("/api/v1/internal") {
    prefix("/potential_matches") {
      get("/search") { req: PotentialMatchSearchRequest =>
        val scrapeSources = if (req.networks.isEmpty) {
          None
        } else {
          val scrapeItemTypes =
            req.networks.map(SupportedNetwork.fromString).flatMap {
              case SupportedNetwork.Netflix =>
                Set(
                  ScrapeCatalogType.NetflixCatalog,
                  ScrapeCatalogType.NetflixOriginalsArriving
                )
              case SupportedNetwork.Hulu => Set(ScrapeCatalogType.HuluCatalog)
              case SupportedNetwork.Hbo =>
                Set(ScrapeCatalogType.HboCatalog, ScrapeCatalogType.HboChanges)
              case SupportedNetwork.HboMax =>
                Set(
                  ScrapeCatalogType.HboChanges,
                  ScrapeCatalogType.HboMaxCatalog
                )
              case SupportedNetwork.DisneyPlus =>
                Set(ScrapeCatalogType.DisneyPlusCatalog)
              case SupportedNetwork.AmazonPrimeVideo |
                  SupportedNetwork.AmazonVideo =>
                // TODO Support amazon filtering
                Set.empty[ScrapeCatalogType]
            }

          Some(scrapeItemTypes)
        }

        esPotentialMatchItemStore
          .search(
            PotentialMatchItemSearch(
              scraperTypes =
                scrapeSources.orElse(req.scraperItemType.map(Set(_))),
              state = req.matchState.map(EsPotentialMatchState.fromString),
              limit = req.limit,
              bookmark = req.bookmark.map(Bookmark.parse),
              sort = req.sort,
              desc = req.desc
            )
          )
          .map(resp => {
            response
              .ok(
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
              .contentTypeJson()
          })
      }

      get("/:id") { req: SpecificPotentialMatchRequest =>
        esPotentialMatchItemStore.lookup(req.id).map {
          case Some(value) =>
            response.ok(DataResponse(value)).contentTypeJson()
          case None => response.notFound
        }
      }

      put("/:id") { req: UpdatePotentialMatchRequest =>
        esPotentialMatchItemStore.lookup(req.id).flatMap {
          case Some(value) if value.availability.getOrElse(Nil).isEmpty =>
            Future.successful(
              response
                .badRequest("Potential item has no availabilities to save")
            )

          case Some(value) =>
            itemLookup
              .lookupItem(
                Left(value.potential.id),
                None,
                shouldMaterializeRecommendations = false,
                shouldMateralizeCredits = false
              )
              .flatMap {
                case Some(ItemLookupResponse(rawItem, _, _)) =>
                  esPotentialMatchItemStore
                    .updateState(req.id, req.status)
                    .flatMap(_ => {
                      req.status match {
                        case EsPotentialMatchState.Matched =>
                          val availabilities = value.availability.getOrElse(Nil)
                          val itemWithUpdates = ItemUpdateApplier
                            .applyAvailabilities(rawItem, availabilities)

                          for {
                            _ <- itemUpdater.update(itemWithUpdates)
                            _ <- itemDenormQueue.queue(
                              EsDenormalizeItemMessage(
                                itemWithUpdates.id,
                                creditsChanged = false,
                                crewChanged = false,
                                dryRun = false
                              )
                            )
                          } yield {
                            response.noContent
                          }
                        case _ => Future.successful(response.noContent)
                      }
                    })
                case None =>
                  Future.successful(response.notFound("Could not find item."))
              }
          case None =>
            Future.successful(
              response.notFound("Could not find potential match item")
            )
        }
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
        response.ok(DataResponse(thing)).contentTypeJson()
    }
  }
}

case class PotentialMatchSearchRequest(
  @QueryParam scraperItemType: Option[ScrapeCatalogType],
  @QueryParam(commaSeparatedList = true) networks: Set[String] = Set.empty,
  @QueryParam matchState: Option[String],
  @QueryParam limit: Int = 20,
  @QueryParam bookmark: Option[String],
  @QueryParam sort: Option[String],
  @QueryParam desc: Boolean = true)

case class SpecificPotentialMatchRequest(@RouteParam id: String)

case class UpdatePotentialMatchRequest(
  @RouteParam id: String,
  status: EsPotentialMatchState)

case class RefreshThingRequest(thingId: String) extends HasThingIdOrSlug
case class ScrapeTmdbRequest(
  id: Int,
  thingType: ItemType)
