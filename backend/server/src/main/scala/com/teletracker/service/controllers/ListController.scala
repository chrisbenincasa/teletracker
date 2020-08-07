package com.teletracker.service.controllers

import cats.instances.all._
import com.teletracker.common.db.model.{ItemType, OfferType}
import com.teletracker.common.db.{
  Bookmark,
  DefaultForListType,
  Recent,
  SortMode
}
import com.teletracker.common.elasticsearch.BinaryOperator
import com.teletracker.common.elasticsearch.model.PeopleCreditsFilter
import com.teletracker.common.model.{DataResponse, Paging}
import com.teletracker.common.util.Monoidal._
import com.teletracker.common.util.time.LocalDateUtils
import com.teletracker.common.util.{
  HasFieldsFilter,
  IdOrSlug,
  ListFilterParser,
  OpenDateRange
}
import com.teletracker.service.api.{
  AvailabilityFilters,
  ItemSearchRequest,
  ListsApi,
  UsersApi
}
import com.teletracker.service.controllers.annotations.{
  ItemReleaseYear,
  RatingRange
}
import com.teletracker.service.controllers.params.RangeParser
import com.twitter.finagle.http.{ParamMap, Request}
import com.twitter.finatra.request.{QueryParam, RouteParam}
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.Try

class ListController @Inject()(
  usersApi: UsersApi,
  listsApi: ListsApi,
  listFilterParser: ListFilterParser
)(implicit executionContext: ExecutionContext)
    extends TeletrackerController(listsApi) {

  prefix("/api/v2/lists") {
    get("/:listId") { req: GetListRequest =>
      usersApi
        .getUserList(
          IdOrSlug(req.listId),
          req.authenticatedUserId,
          mustBePublic = true,
          includeItems = false
        )
        .map {
          case Some(value) =>
            response.okCirceJsonResponse(DataResponse(value))
          case None => response.notFound
        }
    }

    get("/:listId/items") { req: GetListItemsRequest =>
      val (bookmark, sort) = if (req.bookmark.isDefined) {
        val b = Bookmark.parse(req.bookmark.get)
        Some(b) -> b.sortMode
      } else {
        val desc = req.desc.getOrElse(true)
        val sort = req.sort
          .map(SortMode.fromString)
          .getOrElse(DefaultForListType())
          .direction(desc)

        None -> sort
      }

      usersApi
        .getListItems(
          IdOrSlug(req.listId),
          req.authenticatedUserId,
          makeSearchRequest(req.request.params, req),
          sort,
          bookmark,
          req.limit
        )
        .map {
          case None => response.notFound

          case Some((items, total, bookmark)) =>
            response.okCirceJsonResponse(
              DataResponse(
                items
              ).withPaging(
                Paging(bookmark.map(_.encode), total = Some(total))
              )
            )
        }
    }
  }

  private def makeSearchRequest(
    params: ParamMap,
    req: GetListItemsRequest
  ) = {
    val genres =
      ParamExtractor.extractOptSeqParam(params, "genres")
    val networks =
      ParamExtractor.extractOptSeqParam(params, "networks")
    val itemTypes =
      ParamExtractor.extractOptSeqParam(params, "itemTypes")
    val cast =
      ParamExtractor.extractOptSeqParam(params, "castIncludes")
    val crew =
      ParamExtractor.extractOptSeqParam(params, "crewIncludes")

    val filterOverridesEmpty =
      genres.isEmpty &&
        networks.isEmpty &&
        itemTypes.isEmpty &&
        req.sort.isEmpty &&
        req.minReleaseYear.isEmpty &&
        req.maxReleaseYear.isEmpty &&
        req.imdbRating.isEmpty &&
        cast.isEmpty &&
        crew.isEmpty

    if (filterOverridesEmpty) {
      None
    } else {
      Some(
        ItemSearchRequest(
          genres = genres.map(_.toSet),
          networks = networks.map(_.toSet),
          itemTypes = itemTypes.map(
            _.flatMap(t => Try(ItemType.fromString(t)).toOption).toSet
          ),
          allNetworks = networks
            .map(_.toSet)
            .filter(_.nonEmpty)
            .map(_ == Set(ItemSearchRequest.All)),
          sortMode = req.sort.map(SortMode.fromString).getOrElse(Recent()),
          limit = req.limit,
          bookmark = req.bookmark.map(Bookmark.parse),
          releaseYear = Some(
            OpenDateRange(
              req.minReleaseYear.map(LocalDateUtils.localDateAtYear),
              req.maxReleaseYear.map(LocalDateUtils.localDateAtYear)
            )
          ),
          peopleCredits =
            if (cast.exists(_.nonEmpty) || crew.exists(_.nonEmpty))
              Some(
                PeopleCreditsFilter(
                  req.castIncludes.toSeq,
                  req.crewIncludes.toSeq,
                  BinaryOperator.And
                )
              )
            else None,
          imdbRating = req.imdbRating.flatMap(RangeParser.parseRatingString),
          availabilityFilters = Some(
            AvailabilityFilters(
              offerTypes = req.offerTypes
                .flatMap(ot => Try(OfferType.fromString(ot)).toOption)
                .ifEmptyOption,
              presentationTypes = None
            )
          )
        )
      )
    }
  }
}

case class GetListRequest(
  @RouteParam listId: String,
  @QueryParam includeItems: Boolean = false,
  request: Request)
    extends InjectedRequest

private case class GetListItemsRequest(
  @RouteParam listId: String,
  @QueryParam fields: Option[String],
  @QueryParam(commaSeparatedList = true) itemTypes: Seq[String] = Seq(),
  @QueryParam(commaSeparatedList = true) networks: Set[String] = Set.empty,
  @QueryParam(commaSeparatedList = true) genres: Set[String] = Set.empty,
  @QueryParam @ItemReleaseYear minReleaseYear: Option[Int],
  @QueryParam @ItemReleaseYear maxReleaseYear: Option[Int],
  @QueryParam(commaSeparatedList = true) castIncludes: Set[String] = Set.empty,
  @QueryParam(commaSeparatedList = true) crewIncludes: Set[String] = Set.empty,
  @QueryParam @RatingRange(min = 0.0d, max = 10.0d) imdbRating: Option[String],
  @QueryParam isDynamic: Option[Boolean], // Hint as to whether the list is dynamic or not
  @QueryParam sort: Option[String],
  @QueryParam desc: Option[Boolean],
  @QueryParam bookmark: Option[String],
  @QueryParam limit: Int = 10,
  @QueryParam(commaSeparatedList = true)
  offerTypes: Set[String] = Set(),
  request: Request)
    extends HasFieldsFilter
    with InjectedRequest
