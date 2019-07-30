package com.teletracker.service.controllers

import com.teletracker.service.api.UserApi
import com.teletracker.service.auth.RequestContext._
import com.teletracker.service.auth.jwt.JwtVendor
import com.teletracker.service.auth.{JwtAuthFilter, UserSelfOnlyFilter}
import com.teletracker.service.controllers.utils.{
  CanParseFieldFilter,
  CanParseListFilters
}
import com.teletracker.service.db.model.{
  Event,
  ThingType,
  User,
  UserThingTagType
}
import com.teletracker.service.db.{ThingsDbAccess, UsersDbAccess}
import com.teletracker.service.model.{DataResponse, IllegalActionTypeError}
import com.teletracker.service.util.HasFieldsFilter
import com.teletracker.service.util.json.circe._
import com.twitter.finagle.http.Request
import com.twitter.finatra.request.{QueryParam, RouteParam}
import io.circe.generic.JsonCodec
import io.circe.parser._
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class UserController @Inject()(
  userApi: UserApi,
  usersDbAccess: UsersDbAccess,
  thingsDbAccess: ThingsDbAccess,
  jwtVendor: JwtVendor
)(implicit executionContext: ExecutionContext)
    extends TeletrackerController(usersDbAccess)
    with CanParseFieldFilter
    with CanParseListFilters {
  prefix("/api/v1/users") {
    // Create a user
    post("/?") { req: CreateUserRequest =>
      for {
        (userId, token) <- usersDbAccess.createUserAndToken(
          req.name,
          req.username,
          req.email,
          req.password
        )
      } yield {
        DataResponse(
          CreateUserResponse(userId, token)
        )
      }
    }

    filter[JwtAuthFilter].filter[UserSelfOnlyFilter].apply {
      get("/:userId") { request: GetUserByIdRequest =>
        getUserOrNotFound(request.user.id)
      }

      put("/:userId") { request: Request =>
        decode[UpdateUserRequest](request.contentString) match {
          case Right(UpdateUserRequest(updatedUser)) =>
            userApi
              .updateUser(updatedUser)
              .flatMap(_ => {
                getUserOrNotFound(request.user.id)
              })

          case Left(err) => throw err
        }
      }

      get("/:userId/lists") { req: GetUserListsRequest =>
        usersDbAccess
          .findListsForUser(req.user.id, req.includeThings)
          .map(result => {
            if (result.isEmpty) {
              response.notFound
            } else {
              response.ok
                .contentTypeJson()
                .body(
                  DataResponse.complex(
                    result
                  )
                )
            }
          })
      }

      post("/:userId/lists") { req: CreateListRequest =>
        usersDbAccess
          .insertList(req.request.authContext.user.id, req.name)
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
            req.user.id,
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
        withList(req.user.id, req.listId) { list =>
          req.mergeWithList
            .map(listId => {
              usersDbAccess
                .getList(
                  req.request.authContext.user.id,
                  listId.toInt
                )
            })
            .getOrElse(Future.successful(None))
            .flatMap {
              case Some(list) if list.isDynamic =>
                Future.successful(response.badRequest)

              case None if req.mergeWithList.nonEmpty =>
                Future.successful(response.notFound)

              case _ =>
                usersDbAccess
                  .deleteList(
                    req.request.authContext.user.id,
                    list.id,
                    req.mergeWithList.map(_.toInt)
                  )
                  .map {
                    case true  => response.noContent
                    case false => response.notFound
                  }
            }
        }
      }

      put("/:userId/lists/:listId") { req: UpdateListRequest =>
        withList(req.user.id, req.listId) { list =>
          usersDbAccess.updateList(req.user.id, list.id, req.name).map {
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
            req.user.id,
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
        withList(req.user.id, req.listId) { list =>
          thingsDbAccess.findThingById(req.itemId).flatMap {
            case None => Future.successful(response.notFound)
            case Some(thing) =>
              usersDbAccess
                .addThingToList(list.id, thing.id.get)
                .map(_ => response.noContent)
          }
        }
      }

      put("/:userId/lists") { req: AddThingToListsRequest =>
        usersDbAccess
          .findListsForUser(
            req.request.authContext.user.id,
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
                    usersDbAccess.addThingToList(listId, thing.id.get)
                  })

                  Future.sequence(futs).map(_ => response.noContent)
              }
            }
          })
      }

      put("/:userId/things/:thingId/lists") { req: ManageShowListsRequest =>
        usersDbAccess
          .findListsForUser(req.user.id, includeThings = false)
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
                    usersDbAccess.addThingToList(listId, thing.id.get)
                  })

                  val removeFuts = usersDbAccess
                    .removeThingFromLists(validRemoves.toSet, thing.id.get)

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
                    req.request.authContext.user.id,
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
                  req.request.authContext.user.id,
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
          .getUserEvents(req.request.authContext.user.id)
          .map(DataResponse(_))
      }

      post("/:userId/events") { req: AddUserEventRequest =>
        val dao = Event(
          None,
          req.event.`type`,
          req.event.targetEntityType,
          req.event.targetEntityId,
          req.event.details,
          req.request.authContext.user.id,
          new java.sql.Timestamp(req.event.timestamp)
        )

        usersDbAccess
          .addUserEvent(dao)
          .map(DataResponse(_))
          .map(response.created(_))
      }
    }
  }

  private def getUserOrNotFound(userId: Int) = {
    userApi.getUser(userId).map {
      case None =>
        response.notFound

      case Some(user) =>
        DataResponse.complex(user)
    }
  }
}

trait InjectedRequest {
  def request: Request
}

case class ListFilters(itemTypes: Option[Set[ThingType]])

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
  itemId: Int,
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
  itemId: Int,
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
  @RouteParam thingId: Int,
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

@JsonCodec
case class UpdateUserRequest(user: User)

case class UpdateUserThingActionRequest(
  @RouteParam userId: String,
  @RouteParam thingId: Int,
  action: String,
  value: Option[Double],
  request: Request)

case class DeleteUserThingActionRequest(
  @RouteParam userId: String,
  @RouteParam thingId: Int,
  @RouteParam actionType: String,
  request: Request)
