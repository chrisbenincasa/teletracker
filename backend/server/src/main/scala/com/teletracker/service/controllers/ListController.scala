package com.teletracker.service.controllers

import com.teletracker.common.db.{Bookmark, DefaultForListType, SortMode}
import com.teletracker.common.model.{DataResponse, Paging}
import com.teletracker.common.util.{HasFieldsFilter, IdOrSlug, ListFilterParser}
import com.teletracker.service.api.{ItemApi, ListsApi, UsersApi}
import com.teletracker.service.auth.CurrentAuthenticatedUser
import com.twitter.finagle.http.Request
import com.twitter.finatra.request.{QueryParam, RouteParam}
import javax.inject.{Inject, Provider}
import scala.concurrent.ExecutionContext

class ListController @Inject()(
  usersApi: UsersApi,
  listsApi: ListsApi,
  listFilterParser: ListFilterParser,
  thingApi: ItemApi,
  currentAuthenticatedUser: Provider[Option[CurrentAuthenticatedUser]]
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
      val genres =
        ParamExtractor.extractOptSeqParam(req.request.params, "genres")
      val networks =
        ParamExtractor.extractOptSeqParam(req.request.params, "networks")
      val itemTypes =
        ParamExtractor.extractOptSeqParam(req.request.params, "itemTypes")

      val filtersFut =
        listFilterParser.parseListFilters(
          itemTypes,
          genres
        )

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

      filtersFut.flatMap(filters => {
        usersApi
          .getUserListAndItems(
            IdOrSlug(req.listId),
            req.authenticatedUserId,
            Some(filters),
            req.isDynamic,
            sort,
            bookmark,
            req.limit,
            mustBePublic = true
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
      })
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
  //  @QueryParam(commaSeparatedList = true) genres: Option[Seq[String]] = None,
  @QueryParam isDynamic: Option[Boolean], // Hint as to whether the list is dynamic or not
  @QueryParam sort: Option[String],
  @QueryParam desc: Option[Boolean],
  @QueryParam bookmark: Option[String],
  @QueryParam limit: Int = 10,
  request: Request)
    extends HasFieldsFilter
    with InjectedRequest
