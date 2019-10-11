package com.teletracker.service.controllers

import com.teletracker.common.api.model.{
  TrackedList,
  TrackedListOptions,
  TrackedListRules
}
import com.teletracker.common.auth.jwt.JwtVendor
import com.teletracker.common.db.access.{
  ListQueryResult,
  ThingsDbAccess,
  UsersDbAccess
}
import com.teletracker.common.db.model._
import com.teletracker.common.db.{Bookmark, DefaultForListType, SortMode}
import com.teletracker.common.model.{
  DataResponse,
  IllegalActionTypeError,
  Paging
}
import com.teletracker.common.util.json.circe._
import com.teletracker.common.util.{
  CanParseFieldFilter,
  HasFieldsFilter,
  HasThingIdOrSlug,
  ListFilterParser
}
import com.teletracker.service.api.{ListsApi, UsersApi}
import com.teletracker.service.auth.{AuthRequiredFilter, UserSelfOnlyFilter}
import com.twitter.finagle.http.Request
import com.twitter.finatra.request.{QueryParam, RouteParam}
import io.circe.generic.JsonCodec
import io.circe.parser._
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class UserController @Inject()(
  usersApi: UsersApi,
  listsApi: ListsApi,
  usersDbAccess: UsersDbAccess,
  thingsDbAccess: ThingsDbAccess,
  jwtVendor: JwtVendor,
  listFilterParser: ListFilterParser
)(implicit executionContext: ExecutionContext)
    extends TeletrackerController(usersDbAccess)
    with CanParseFieldFilter {
  prefix("/api/v1/users") {
    // Create a user
    post("/?") { req: RegisterUserRequest =>
      usersApi.registerUser(req.userId).map(_ => response.ok)
    }

    filter[AuthRequiredFilter].filter[UserSelfOnlyFilter] {
      get("/:userId") { request: GetUserByIdRequest =>
        getUserOrNotFound(request.authenticatedUserId.get)
      }

      put("/:userId") { request: UpdateUserRequest =>
        decode[UpdateUserRequestPayload](request.request.contentString) match {
          case Right(updateUserRequest) =>
            usersApi
              .updateUser(
                request.request.authenticatedUserId.get,
                updateUserRequest
              )
              .flatMap(_ => {
                getUserOrNotFound(request.request.authenticatedUserId.get)
              })

          case Left(err) => throw err
        }
      }

      get("/:userId/lists") { req: GetUserListsRequest =>
        def returnLists(lists: Seq[TrackedList]) =
          response.ok
            .contentTypeJson()
            .body(
              DataResponse.complex(
                lists
              )
            )

        usersDbAccess
          .findListsForUser(req.authenticatedUserId.get, req.includeThings)
          .flatMap(result => {
            if (result.isEmpty) {
              usersApi
                .createDefaultListsForUser(req.authenticatedUserId.get)
                .flatMap(_ => {
                  usersDbAccess
                    .findListsForUser(
                      req.authenticatedUserId.get,
                      req.includeThings
                    )
                    .map(returnLists)
                })
            } else {
              Future.successful(returnLists(result))
            }
          })
      }

      post("/:userId/lists") { req: Request =>
        parse(req.contentString).flatMap(_.as[CreateListRequest]) match {
          case Left(err) =>
            throw err

          case Right(listCreateRequest) =>
            require(
              !(listCreateRequest.thingIds.isDefined && listCreateRequest.rules.isDefined),
              "Cannot specify both thingIds and rules when creating a list"
            )

            listsApi
              .createList(
                req.authenticatedUserId.get,
                listCreateRequest.name,
                listCreateRequest.thingIds,
                listCreateRequest.rules
              )
              .map(newList => {
                DataResponse(
                  CreateListResponse(newList.id)
                )
              })
        }

      }

      get("/:userId/lists/:listId") { req: GetUserAndListByIdRequest =>
        val selectFields = parseFieldsOrNone(req.fields)
        val filtersFut =
          listFilterParser.parseListFilters(req.itemTypes, req.genres)

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

        logger.info(s"Retrieving list with bookmark: ${bookmark}")

        filtersFut.flatMap(filters => {
          usersDbAccess
            .findList(
              req.authenticatedUserId.get,
              req.listId,
              includeMetadata = true,
              selectFields,
              Some(filters),
              req.isDynamic,
              sort,
              bookmark,
              req.limit
            )
            .map {
              case ListQueryResult(None, _) => response.notFound

              case ListQueryResult(Some(trackedList), bookmark) =>
                response.ok
                  .contentTypeJson()
                  .body(
                    DataResponse.forDataResponse(
                      DataResponse(trackedList)
                        .withPaging(Paging(bookmark.map(_.asString)))
                    )
                  )
            }
        })
      }

      delete("/:userId/lists/:listId") { req: DeleteListRequest =>
        withList(req.authenticatedUserId.get, req.listId) { list =>
          listsApi
            .deleteList(
              req.authenticatedUserId.get,
              list.id,
              req.mergeWithList.map(_.toInt)
            )
            .recover {
              case _: IllegalArgumentException =>
                response.badRequest
              case NonFatal(_) => response.internalServerError
            }
            .map {
              case true  => response.noContent
              case false => response.notFound
            }
        }
      }

      put("/:userId/lists/:listId") { req: UpdateListRequest =>
        withList(req.authenticatedUserId.get, req.listId) { list =>
          for {
            updateResult <- usersDbAccess
              .updateList(
                req.authenticatedUserId.get,
                list.id,
                req.name,
                req.rules,
                req.options
              )
            _ <- updateResult
              .map(
                listsApi
                  .handleListUpdateResult(
                    req.authenticatedUserId.get,
                    list.id,
                    _
                  )
              )
              .getOrElse(Future.unit)
          } yield {
            updateResult match {
              case None => response.notFound
              case Some(result) =>
                response.ok(
                  DataResponse.complex(
                    UpdateListResponse(
                      result.optionsChanged || result.rulesChanged
                    )
                  )
                )
            }
          }
        }
      }

      get("/:userId/lists/:listId/things") { req: GetListThingsRequest =>
        val selectFields = parseFieldsOrNone(req.fields)
        val filtersFut =
          listFilterParser.parseListFilters(req.itemTypes, req.genres)

        val desc = req.desc.getOrElse(true)
        val sort = req.sort
          .map(SortMode.fromString)
          .getOrElse(DefaultForListType())
          .direction(desc)

        filtersFut.flatMap(filters => {
          usersDbAccess
            .findList(
              req.authenticatedUserId.get,
              req.listId,
              includeMetadata = true,
              selectFields,
              Some(filters),
              req.isDynamic,
              sort
            )
            .map {
              case ListQueryResult(None, _) => response.notFound

              case ListQueryResult(Some(trackedList), bookmark) =>
                response.ok
                  .contentTypeJson()
                  .body(
                    DataResponse.complex(
                      trackedList.things.getOrElse(Nil)
                    )
                  )
            }
        })
      }

      put("/:userId/lists/:listId/things") { req: AddThingToListRequest =>
        withList(req.authenticatedUserId.get, req.listId) { list =>
          thingsDbAccess.findThingByIdOrSlug(req.idOrSlug).flatMap {
            case None => Future.successful(response.notFound)
            case Some(thing) =>
              usersDbAccess
                .addThingToList(list.id, thing.id)
                .map(_ => response.noContent)
          }
        }
      }

      put("/:userId/lists") { req: AddThingToListsRequest =>
        usersDbAccess
          .findListsForUser(
            req.authenticatedUserId.get,
            includeThings = false
          )
          .flatMap(lists => {
            val listIds = lists.map(_.id).toSet
            val (validListIds, _) = req.listIds.partition(listIds(_))

            if (validListIds.isEmpty) {
              Future.successful(response.notFound)
            } else {
              thingsDbAccess.findThingByIdOrSlug(req.idOrSlug).flatMap {
                case None => Future.successful(response.notFound)
                case Some(thing) =>
                  val futs = validListIds.map(listId => {
                    usersDbAccess.addThingToList(listId, thing.id)
                  })

                  Future.sequence(futs).map(_ => response.noContent)
              }
            }
          })
      }

      put("/:userId/things/:thingId/lists") { req: ManageShowListsRequest =>
        usersDbAccess
          .findListsForUser(req.authenticatedUserId.get, includeThings = false)
          .flatMap(lists => {
            val listIds = lists.map(_.id).toSet
            val validAdds = req.addToLists.filter(listIds(_))
            val validRemoves = req.removeFromLists.filter(listIds(_))

            if (validAdds.isEmpty && validRemoves.isEmpty) {
              Future.successful(response.notFound)
            } else {
              thingsDbAccess.findThingByIdOrSlug(req.idOrSlug).flatMap {
                case None => Future.successful(response.notFound)
                case Some(thing) =>
                  val futs = validAdds.map(listId => {
                    usersDbAccess.addThingToList(listId, thing.id)
                  })

                  val removeFuts = usersDbAccess
                    .removeThingFromLists(validRemoves.toSet, thing.id)

                  Future
                    .sequence(futs :+ removeFuts)
                    .map(_ => response.noContent)
              }
            }
          })
      }

      put("/:userId/things/:thingId/actions") {
        req: UpdateUserThingActionRequest =>
          Try(UserThingTagType.fromString(req.action)) match {
            case Success(action) =>
              if (action.typeRequiresValue() && req.value.isEmpty) {
                Future.successful(response.badRequest)
              } else {
                val newOrUpdatedTag = usersDbAccess
                  .insertOrUpdateAction(
                    req.request.authenticatedUserId.get,
                    req.idOrSlug,
                    action,
                    req.value
                  )

                newOrUpdatedTag.foreach(_.foreach(usersApi.handleTagChange))

                newOrUpdatedTag.map(_ => response.noContent)
              }

            case Failure(_) =>
              Future.successful(
                response
                  .badRequest(new IllegalActionTypeError(req.action))
                  .contentTypeJson()
              )
          }
      }

      delete("/:userId/things/:thingId/actions/:actionType") {
        req: DeleteUserThingActionRequest =>
          Try(UserThingTagType.fromString(req.actionType)) match {
            case Success(action) =>
              usersDbAccess
                .removeAction(
                  req.request.authenticatedUserId.get,
                  req.idOrSlug,
                  action
                )
                .map(_ => {
                  response.noContent
                })

            case Failure(_) =>
              Future.successful(
                response
                  .badRequest(new IllegalActionTypeError(req.actionType))
                  .contentTypeJson()
              )
          }
      }

      get("/:userId/events") { req: GetUserByIdRequest =>
        usersDbAccess
          .getUserEvents(req.authenticatedUserId.get)
          .map(DataResponse(_))
      }

      post("/:userId/events") { req: AddUserEventRequest =>
        val dao = Event(
          None,
          req.event.`type`,
          req.event.targetEntityType,
          req.event.targetEntityId,
          req.event.details,
          req.authenticatedUserId.get,
          new java.sql.Timestamp(req.event.timestamp)
        )

        usersDbAccess
          .addUserEvent(dao)
          .map(DataResponse(_))
          .map(response.created(_))
      }
    }
  }

  private def getUserOrNotFound(userId: String): Future[String] = {
    usersApi.getUser(userId).map(DataResponse.complex(_))
  }
}

trait InjectedRequest {
  def request: Request
}

case class ListFilters(itemTypes: Option[Set[ThingType]])

case class RegisterUserRequest(userId: String)

case class GetUserByIdRequest(
  @RouteParam userId: String,
  request: Request)
    extends InjectedRequest

case class GetUserListsRequest(
  @RouteParam userId: String,
  @QueryParam fields: Option[String],
  @QueryParam includeThings: Boolean = false,
  request: Request)
    extends HasFieldsFilter
    with InjectedRequest

case class GetUserAndListByIdRequest(
  @RouteParam userId: String,
  @RouteParam listId: Int,
  @QueryParam fields: Option[String],
  @QueryParam(commaSeparatedList = true) itemTypes: Seq[String] = Seq(),
  @QueryParam(commaSeparatedList = true) genres: Seq[String] = Seq(),
  @QueryParam isDynamic: Option[Boolean], // Hint as to whether the list is dynamic or not
  @QueryParam sort: Option[String],
  @QueryParam desc: Option[Boolean],
  @QueryParam bookmark: Option[String],
  @QueryParam limit: Int = 10,
  request: Request)
    extends HasFieldsFilter
    with InjectedRequest

@JsonCodec
case class CreateListRequest(
  name: String,
  thingIds: Option[List[UUID]],
  rules: Option[TrackedListRules])

case class CreateListResponse(id: Int)

case class UpdateListRequest(
  @RouteParam userId: String,
  @RouteParam listId: String,
  name: Option[String],
  rules: Option[TrackedListRules],
  options: Option[TrackedListOptions],
  request: Request)
    extends InjectedRequest

@JsonCodec
case class UpdateListResponse(requiresRefresh: Boolean)

case class GetListThingsRequest(
  @RouteParam userId: String,
  @RouteParam listId: Int,
  @QueryParam fields: Option[String],
  @QueryParam(commaSeparatedList = true) itemTypes: Seq[String] = Seq(),
  @QueryParam(commaSeparatedList = true) genres: Seq[String] = Seq(),
  @QueryParam isDynamic: Option[Boolean], // Hint as to whether the list is dynamic or not
  @QueryParam sort: Option[String],
  @QueryParam desc: Option[Boolean],
  request: Request)
    extends InjectedRequest

case class AddThingToListRequest(
  @RouteParam userId: String,
  @RouteParam listId: String,
  thingId: String,
  request: Request)
    extends InjectedRequest
    with HasThingIdOrSlug

case class DeleteListRequest(
  @RouteParam userId: String,
  @RouteParam listId: String,
  @RouteParam mergeWithList: Option[String],
  request: Request)
    extends InjectedRequest

case class AddThingToListsRequest(
  @RouteParam userId: String,
  thingId: String,
  listIds: List[Int],
  request: Request)
    extends InjectedRequest
    with HasThingIdOrSlug

case class AddUserEventRequest(
  @RouteParam userId: String,
  event: EventCreate,
  request: Request)
    extends InjectedRequest

case class ManageShowListsRequest(
  @RouteParam userId: String,
  @RouteParam thingId: String,
  addToLists: List[Int],
  removeFromLists: List[Int],
  request: Request)
    extends InjectedRequest
    with HasThingIdOrSlug

case class EventCreate(
  `type`: String,
  targetEntityType: String,
  targetEntityId: String,
  details: Option[String],
  timestamp: Long)

case class CreateUserResponse(
  userId: Int,
  token: String)

case class UpdateUserRequest(
  @RouteParam userId: String,
  request: Request)

@JsonCodec
case class UpdateUserRequestPayload(
  networkSubscriptions: Option[List[Network]],
  userPreferences: Option[UserPreferences])

case class UpdateUserThingActionRequest(
  @RouteParam userId: String,
  @RouteParam thingId: String,
  action: String,
  value: Option[Double],
  request: Request)
    extends HasThingIdOrSlug

case class DeleteUserThingActionRequest(
  @RouteParam userId: String,
  @RouteParam thingId: String,
  @RouteParam actionType: String,
  request: Request)
    extends HasThingIdOrSlug
