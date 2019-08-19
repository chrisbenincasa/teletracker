package com.teletracker.service.controllers

import com.teletracker.service.api.{ListsApi, UsersApi}
import com.teletracker.service.auth.RequestContext._
import com.teletracker.common.auth.jwt.JwtVendor
import com.teletracker.service.auth.{JwtAuthFilter, UserSelfOnlyFilter}
import com.teletracker.common.db.access.{ThingsDbAccess, UsersDbAccess}
import com.teletracker.common.db.model.{
  Event,
  Network,
  ThingType,
  TrackedList,
  UserPreferences,
  UserThingTagType
}
import com.teletracker.common.model.{DataResponse, IllegalActionTypeError}
import com.teletracker.common.util.{
  CanParseFieldFilter,
  CanParseListFilters,
  HasFieldsFilter
}
import com.teletracker.common.util.json.circe._
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
  jwtVendor: JwtVendor
)(implicit executionContext: ExecutionContext)
    extends TeletrackerController(usersDbAccess)
    with CanParseFieldFilter
    with CanParseListFilters {
  prefix("/api/v1/users") {
    // Create a user
    post("/?") { req: RegisterUserRequest =>
      usersApi.registerUser(req.userId).map(_ => response.ok)
    }

    filter[JwtAuthFilter].filter[UserSelfOnlyFilter].apply {
      get("/:userId") { request: GetUserByIdRequest =>
        getUserOrNotFound(request.authenticatedUserId)
      }

      put("/:userId") { request: UpdateUserRequest =>
        decode[UpdateUserRequestPayload](request.request.contentString) match {
          case Right(updateUserRequest) =>
            usersApi
              .updateUser(
                request.request.authenticatedUserId,
                updateUserRequest
              )
              .flatMap(_ => {
                getUserOrNotFound(request.request.authenticatedUserId)
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
          .findListsForUser(req.authenticatedUserId, req.includeThings)
          .flatMap(result => {
            if (result.isEmpty) {
              usersApi
                .createDefaultListsForUser(req.authenticatedUserId)
                .flatMap(_ => {
                  usersDbAccess
                    .findListsForUser(
                      req.authenticatedUserId,
                      req.includeThings
                    )
                    .map(returnLists)
                })
            } else {
              Future.successful(returnLists(result))
            }
          })
      }

      post("/:userId/lists") { req: CreateListRequest =>
        usersDbAccess
          .insertList(req.authenticatedUserId, req.name)
          .map(newList => {
            DataResponse(
              CreateListResponse(newList.id)
            )
          })
      }

      get("/:userId/lists/:listId") { req: GetUserAndListByIdRequest =>
        val selectFields = parseFieldsOrNone(req.fields)
        val filters = parseListFilters(req.itemTypes)

        usersDbAccess
          .findList(
            req.authenticatedUserId,
            req.listId,
            includeMetadata = true,
            selectFields,
            Some(filters),
            req.isDynamic
          )
          .map {
            case None => response.notFound

            case Some(trackedList) =>
              response.ok
                .contentTypeJson()
                .body(
                  DataResponse.complex(
                    trackedList
                  )
                )
          }
      }

      delete("/:userId/lists/:listId") { req: DeleteListRequest =>
        withList(req.authenticatedUserId, req.listId) { list =>
          listsApi
            .deleteList(
              req.authenticatedUserId,
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
        withList(req.authenticatedUserId, req.listId) { list =>
          usersDbAccess
            .updateList(req.authenticatedUserId, list.id, req.name)
            .map {
              case 0 => response.notFound
              case _ => response.noContent
            }
        }
      }

      get("/:userId/lists/:listId/things") { req: GetListThingsRequest =>
        val selectFields = parseFieldsOrNone(req.fields)
        val filters = parseListFilters(req.itemTypes)

        usersDbAccess
          .findList(
            req.authenticatedUserId,
            req.listId,
            includeMetadata = true,
            selectFields,
            Some(filters),
            req.isDynamic
          )
          .map {
            case None => response.notFound

            case Some(trackedList) =>
              response.ok
                .contentTypeJson()
                .body(
                  DataResponse.complex(
                    trackedList.things.getOrElse(Nil)
                  )
                )
          }
      }

      put("/:userId/lists/:listId/things") { req: AddThingToListRequest =>
        withList(req.authenticatedUserId, req.listId) { list =>
          thingsDbAccess.findThingById(req.itemId).flatMap {
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
            req.authenticatedUserId,
            includeThings = false
          )
          .flatMap(lists => {
            val listIds = lists.map(_.id).toSet
            val (validListIds, _) = req.listIds.partition(listIds(_))

            if (validListIds.isEmpty) {
              Future.successful(response.notFound)
            } else {
              thingsDbAccess.findThingById(req.itemId).flatMap {
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
          .findListsForUser(req.authenticatedUserId, includeThings = false)
          .flatMap(lists => {
            val listIds = lists.map(_.id).toSet
            val validAdds = req.addToLists.filter(listIds(_))
            val validRemoves = req.removeFromLists.filter(listIds(_))

            if (validAdds.isEmpty && validRemoves.isEmpty) {
              Future.successful(response.notFound)
            } else {
              thingsDbAccess.findThingById(req.thingId).flatMap {
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
                usersDbAccess
                  .insertOrUpdateAction(
                    req.request.authenticatedUserId,
                    req.thingId,
                    action,
                    req.value
                  )
                  .map(_ => response.noContent)
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
                  req.request.authenticatedUserId,
                  req.thingId,
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
          .getUserEvents(req.authenticatedUserId)
          .map(DataResponse(_))
      }

      post("/:userId/events") { req: AddUserEventRequest =>
        val dao = Event(
          None,
          req.event.`type`,
          req.event.targetEntityType,
          req.event.targetEntityId,
          req.event.details,
          req.authenticatedUserId,
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
  @QueryParam isDynamic: Option[Boolean], // Hint as to whether the list is dynamic or not
  request: Request)
    extends HasFieldsFilter
    with InjectedRequest

case class CreateListRequest(
  @RouteParam userId: String,
  request: Request,
  name: String)
    extends InjectedRequest

case class CreateListResponse(id: Int)

case class UpdateListRequest(
  @RouteParam userId: String,
  @RouteParam listId: String,
  name: String,
  request: Request)
    extends InjectedRequest

case class GetListThingsRequest(
  @RouteParam userId: String,
  @RouteParam listId: Int,
  @QueryParam fields: Option[String],
  @QueryParam(commaSeparatedList = true) itemTypes: Seq[String] = Seq(),
  @QueryParam isDynamic: Option[Boolean], // Hint as to whether the list is dynamic or not
  request: Request)
    extends InjectedRequest

case class AddThingToListRequest(
  @RouteParam userId: String,
  @RouteParam listId: String,
  itemId: UUID,
  request: Request)
    extends InjectedRequest

case class DeleteListRequest(
  @RouteParam userId: String,
  @RouteParam listId: String,
  @RouteParam mergeWithList: Option[String],
  request: Request)
    extends InjectedRequest

case class AddThingToListsRequest(
  @RouteParam userId: String,
  itemId: UUID,
  listIds: List[Int],
  request: Request)
    extends InjectedRequest

case class AddUserEventRequest(
  @RouteParam userId: String,
  event: EventCreate,
  request: Request)
    extends InjectedRequest

case class ManageShowListsRequest(
  @RouteParam userId: String,
  @RouteParam thingId: UUID,
  addToLists: List[Int],
  removeFromLists: List[Int],
  request: Request)
    extends InjectedRequest

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
  userPreferences: Option[UserPreferences]
)

case class UpdateUserThingActionRequest(
  @RouteParam userId: String,
  @RouteParam thingId: UUID,
  action: String,
  value: Option[Double],
  request: Request)

case class DeleteUserThingActionRequest(
  @RouteParam userId: String,
  @RouteParam thingId: UUID,
  @RouteParam actionType: String,
  request: Request)
