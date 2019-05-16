package com.teletracker.service.controllers

import com.teletracker.service.auth.RequestContext._
import com.teletracker.service.auth.jwt.JwtVendor
import com.teletracker.service.auth.{JwtAuthFilter, UserSelfOnlyFilter}
import com.teletracker.service.controllers.utils.CanParseFieldFilter
import com.teletracker.service.db.model.{Event, ThingType, User, UserThingTagType}
import com.teletracker.service.db.{ThingsDbAccess, UsersDbAccess}
import com.teletracker.service.model.{DataResponse, IllegalActionTypeError}
import com.teletracker.service.util.HasFieldsFilter
import com.teletracker.service.util.json.circe._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import io.circe.generic.JsonCodec
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}
import io.circe.syntax._
import io.circe.parser._

class UserController @Inject()(
  usersDbAccess: UsersDbAccess,
  thingsDbAccess: ThingsDbAccess,
  jwtVendor: JwtVendor
)(implicit executionContext: ExecutionContext) extends Controller with CanParseFieldFilter {
  prefix("/api/v1/users") {
    // Create a user
    post("/?") { req: CreateUserRequest =>
      for {
        (userId, token) <- usersDbAccess.createUserAndToken(req.name, req.username, req.email, req.password)
      } yield {
        DataResponse(
          CreateUserResponse(userId, token)
        )
      }
    }

    filter[JwtAuthFilter].filter[UserSelfOnlyFilter].apply {
      get("/:userId") { req: GetUserByIdRequest =>
        usersDbAccess.findUserAndLists(req.request.authContext.user.id).map(result => {
          if (result.isEmpty) {
            response.notFound
          } else {
            DataResponse.complex(result.get)
          }
        })
      }

      put("/:userId") { request: Request =>
        decode[UpdateUserRequest](request.contentString) match {
          case Right(UpdateUserRequest(updatedUser)) =>
            usersDbAccess.updateUser(updatedUser).flatMap(_ => {
              usersDbAccess.findUserAndLists(request.authContext.user.id).map(result => {
                if (result.isEmpty) {
                  response.notFound
                } else {
                  DataResponse.complex(result.get)
                }
              })
            })

          case Left(err) => throw err
        }
      }

      get("/:userId/lists") { req: GetUserListsRequest =>
        val selectFields = parseFieldsOrNone(req.fields)
        usersDbAccess.findUserAndLists(req.request.authContext.user.id, selectFields).map(result => {
          if (result.isEmpty) {
            response.notFound
          } else {
            response.ok.contentTypeJson().body(
              DataResponse.complex(
                result.get
              )
            )
          }
        })
      }

      post("/:userId/lists") { req: CreateListRequest =>
        usersDbAccess.insertList(req.request.authContext.user.id, req.name).map(newList => {
          DataResponse(
            CreateListResponse(newList.id.get)
          )
        })
      }

      get("/:userId/lists/:listId") { req: GetUserAndListByIdRequest =>
        val selectFields = parseFieldsOrNone(req.fields)
        val filters = ListFilters(
          if (req.itemTypes.nonEmpty) {
            Some(req.itemTypes.flatMap(typ => Try(ThingType.fromString(typ)).toOption).toSet)
          } else {
            None
          }
        )

        usersDbAccess.findList(
          req.request.authContext.user.id,
          req.listId,
          selectFields,
          Some(filters),
          req.isDynamic
        ).map {
          case None => response.notFound

          case Some((list, things)) =>
            response.ok.contentTypeJson().body(
              DataResponse.complex(
                list.toFull.withThings(things.map(_.asPartial).toList)
              ))
        }
      }

      put("/:userId/lists/:listId") { req: AddThingToListRequest =>
        val listFut = if (req.listId == "default") {
          usersDbAccess.findDefaultListForUser(req.request.authContext.user.id)
        } else {
          Promise.fromTry(Try(req.listId.toInt)).future.flatMap(listId => {
            usersDbAccess.findUserAndList(req.request.authContext.user.id, listId).map(_.headOption.map(_._2))
          })
        }

        listFut.flatMap {
          case None => Future.successful(response.notFound)
          case Some(list) =>
            thingsDbAccess.findThingById(req.itemId).flatMap {
              case None => Future.successful(response.notFound)
              case Some(thing) =>
                usersDbAccess.addThingToList(list.id.get, thing.id.get).
                  map(_ => response.noContent)
            }
        }
      }

      put("/:userId/lists") { req: AddThingToListsRequest =>
        usersDbAccess.findListsForUser(req.request.authContext.user.id).flatMap(lists => {
          val listIds = lists.flatMap(_.id).toSet
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
        usersDbAccess.findListsForUser(req.request.authContext.user.id).flatMap(lists => {
          val listIds = lists.flatMap(_.id).toSet
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

                val removeFuts = usersDbAccess.removeThingFromLists(validRemoves.toSet, thing.id.get)

                Future.sequence(futs :+ removeFuts).map(_ => response.noContent)
            }
          }
        })
      }

      put("/:userId/things/:thingId/actions") { req: UpdateUserThingActionRequest =>
        Try(UserThingTagType.fromString(req.action)) match {
          case Success(action) =>
            if (action.typeRequiresValue() && req.value.isEmpty) {
              Future.successful(response.badRequest)
            } else {
              usersDbAccess.
                insertOrUpdateAction(req.request.authContext.user.id, req.thingId, action, req.value).
                map(_ => response.noContent)
            }

          case Failure(_) =>
            Future.successful(response.badRequest(new IllegalActionTypeError(req.action)).contentTypeJson())
        }
      }

      delete("/:userId/things/:thingId/actions/:actionType") { req: DeleteUserThingActionRequest =>
        Try(UserThingTagType.fromString(req.actionType)) match {
          case Success(action) =>
            usersDbAccess.removeAction(req.request.authContext.user.id, req.thingId, action).map(_ => {
              response.noContent
            })

          case Failure(_) =>
            Future.successful(response.badRequest(new IllegalActionTypeError(req.actionType)).contentTypeJson())
        }
      }

      get("/:userId/events") { req: GetUserByIdRequest =>
        usersDbAccess.getUserEvents(req.request.authContext.user.id).map(DataResponse(_))
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

        usersDbAccess.addUserEvent(dao).map(DataResponse(_)).map(response.created(_))
      }
    }
  }
}

case class ListFilters(
  itemTypes: Option[Set[ThingType]]
)

case class GetUserByIdRequest(
  @RouteParam userId: String,
  request: Request
)

case class GetUserListsRequest(
  @RouteParam userId: String,
  @QueryParam fields: Option[String],
  request: Request
) extends HasFieldsFilter

case class GetUserAndListByIdRequest(
  @RouteParam userId: String,
  @RouteParam listId: Int,
  @QueryParam fields: Option[String],
  @QueryParam(commaSeparatedList = true) itemTypes: Seq[String] = Seq(),
  @QueryParam isDynamic: Option[Boolean], // Hint as to whether the list is dynamic or not
  request: Request
) extends HasFieldsFilter

case class CreateListRequest(
  @RouteParam userId: String,
  request: Request,
  name: String
)

case class CreateListResponse(id: Int)

case class AddThingToListRequest(
  @RouteParam userId: String,
  @RouteParam listId: String,
  itemId: Int,
  request: Request,
)

case class AddThingToListsRequest(
  @RouteParam userId: String,
  itemId: Int,
  listIds: List[Int],
  request: Request,
)


case class AddUserEventRequest(
  @RouteParam userId: String,
  event: EventCreate,
  request: Request
)

case class ManageShowListsRequest(
  @RouteParam userId: String,
  @RouteParam thingId: Int,
  addToLists: List[Int],
  removeFromLists: List[Int],
  request: Request
)

case class EventCreate(
  `type`: String,
  targetEntityType: String,
  targetEntityId: String,
  details: Option[String],
  timestamp: Long
)

case class CreateUserResponse(
  userId: Int,
  token: String
)

@JsonCodec
case class UpdateUserRequest(
  user: User
)

case class UpdateUserThingActionRequest(
  @RouteParam userId: String,
  @RouteParam thingId: Int,
  action: String,
  value: Option[Double],
  request: Request
)

case class DeleteUserThingActionRequest(
  @RouteParam userId: String,
  @RouteParam thingId: Int,
  @RouteParam actionType: String,
  request: Request
)