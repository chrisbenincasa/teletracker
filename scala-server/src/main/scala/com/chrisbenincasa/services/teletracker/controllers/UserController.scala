package com.chrisbenincasa.services.teletracker.controllers

import com.chrisbenincasa.services.teletracker.auth.RequestContext._
import com.chrisbenincasa.services.teletracker.auth.jwt.JwtVendor
import com.chrisbenincasa.services.teletracker.auth.{JwtAuthFilter, UserSelfOnlyFilter}
import com.chrisbenincasa.services.teletracker.db.model.Event
import com.chrisbenincasa.services.teletracker.db.{ThingsDbAccess, UsersDbAccess}
import com.chrisbenincasa.services.teletracker.model.DataResponse
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.RouteParam
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

class UserController @Inject()(
  usersDbAccess: UsersDbAccess,
  thingsDbAccess: ThingsDbAccess,
  jwtVendor: JwtVendor
)(implicit executionContext: ExecutionContext) extends Controller {
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
            response.status(404)
          } else {
            val user = result.head._1
            val lists = result.collect {
              case (_, Some(list), thingOpt) => (list, thingOpt)
            }.groupBy(_._1).map {
              case (list, matches) => list.toFull.withThings(matches.flatMap(_._2).toList)
            }

            DataResponse(
              user.toFull.withLists(lists.toList)
            )
          }
        })
      }

      get("/:userId/lists") { req: GetUserByIdRequest =>
        usersDbAccess.findUserAndLists(req.request.authContext.user.id).map(result => {
          if (result.isEmpty) {
            response.status(404)
          } else {
            val user = result.head._1
            val lists = result.collect {
              case (_, Some(list), thingOpt) => (list, thingOpt)
            }.groupBy(_._1).map {
              case (list, matches) => list.toFull.withThings(matches.flatMap(_._2).toList)
            }

            DataResponse(
              user.toFull.withLists(lists.toList)
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
        usersDbAccess.findList(req.request.authContext.user.id, req.listId).map(result => {
          println(s"found $result")
          if (result.isEmpty) {
            response.status(404)
          } else {
            val list = result.head._1
            val things = result.flatMap(_._2)

            DataResponse(
              list.toFull.withThings(things.map(_.asPartial).toList)
            )
          }
        })
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
          case None => Future.successful(response.status(404))
          case Some(list) =>
            thingsDbAccess.findThingById(req.itemId).flatMap {
              case None => Future.successful(response.status(404))
              case Some(thing) =>
                usersDbAccess.addThingToList(list.id.get, thing.id.get).
                  map(_ => response.status(204))
            }
        }
      }

      put("/:userId/lists") { req: AddThingToListsRequest =>
        usersDbAccess.findListsForUser(req.request.authContext.user.id).flatMap(lists => {
          val listIds = lists.flatMap(_.id).toSet
          val (validListIds, _) = req.listIds.partition(listIds(_))

          if (validListIds.isEmpty) {
            Future.successful(response.status(404))
          } else {
            thingsDbAccess.findThingById(req.itemId).flatMap {
              case None => Future.successful(response.status(404))
              case Some(thing) =>
                val futs = validListIds.map(listId => {
                  usersDbAccess.addThingToList(listId, thing.id.get)
                })

                Future.sequence(futs).map(_ => response.status(204))
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
            Future.successful(response.status(404))
          } else {
            thingsDbAccess.findThingById(req.thingId).flatMap {
              case None => Future.successful(response.status(404))
              case Some(thing) =>
                val futs = validAdds.map(listId => {
                  usersDbAccess.addThingToList(listId, thing.id.get)
                })

                val removeFuts = usersDbAccess.removeThingFromLists(validRemoves.toSet, thing.id.get)

                Future.sequence(futs :+ removeFuts).map(_ => response.status(204))
            }
          }
        })
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

case class GetUserByIdRequest(
  @RouteParam userId: String,
  request: Request
)

case class GetUserAndListByIdRequest(
  @RouteParam userId: String,
  @RouteParam listId: Int,
  request: Request
)

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