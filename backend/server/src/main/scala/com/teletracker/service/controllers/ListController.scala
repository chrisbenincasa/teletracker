package com.teletracker.service.controllers

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.db.{
  Bookmark,
  DefaultForListType,
  Recent,
  SortMode
}
import com.teletracker.common.elasticsearch.BinaryOperator
import com.teletracker.common.elasticsearch.model.PeopleCreditsFilter
import com.teletracker.common.model.{DataResponse, Paging}
import com.teletracker.common.util.time.LocalDateUtils
import com.teletracker.common.util.{
  HasFieldsFilter,
  IdOrSlug,
  ListFilterParser,
  OpenDateRange
}
import com.teletracker.service.api.{
  ItemApi,
  ItemSearchRequest,
  ListsApi,
  UsersApi
}
import com.teletracker.service.auth.CurrentAuthenticatedUser
import com.teletracker.service.controllers.annotations.{
  ItemReleaseYear,
  RatingRange
}
import com.teletracker.service.controllers.params.RangeParser
import com.twitter.finagle.http.{ParamMap, Request}
import com.twitter.finatra.request.{QueryParam, RouteParam}
import javax.inject.{Inject, Provider}
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
      usersApi.getUserList(
        IdOrSlug(req.listId),
        req.authenticatedUserId,
        mustBePublic = true,
        includeItems = false
      )
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
        .getUserListAndItems(
          IdOrSlug(req.listId),
          req.authenticatedUserId,
          makeSearchRequest(req.request.params, req),
          sort,
          bookmark,
          req.limit
        )
        .map {
          case None => response.notFound

          case Some((list, bookmark)) =>
            response.ok
              .contentTypeJson()
              .body(
                DataResponse.forDataResponse(
                  DataResponse(
                    list
                  ).withPaging(Paging(bookmark.map(_.encode)))
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
      ParamExtractor.extractOptSeqParam(params, "cast")
    val crew =
      ParamExtractor.extractOptSeqParam(params, "crew")

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
                  req.cast.toSeq,
                  req.crew.toSeq,
                  BinaryOperator.And
                )
              )
            else None,
          imdbRating = req.imdbRating.flatMap(RangeParser.parseRatingString)
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
  @QueryParam(commaSeparatedList = true) cast: Set[String] = Set.empty,
  @QueryParam(commaSeparatedList = true) crew: Set[String] = Set.empty,
  @QueryParam @RatingRange(min = 0.0d, max = 10.0d) imdbRating: Option[String],
  @QueryParam isDynamic: Option[Boolean], // Hint as to whether the list is dynamic or not
  @QueryParam sort: Option[String],
  @QueryParam desc: Option[Boolean],
  @QueryParam bookmark: Option[String],
  @QueryParam limit: Int = 10,
  request: Request)
    extends HasFieldsFilter
    with InjectedRequest
