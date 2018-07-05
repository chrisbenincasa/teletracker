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
  import usersDbAccess.provider.driver.api._

  prefix("/api/v1") {
    // Create a user
    post("/users/?") { req: CreateUserRequest =>
      usersDbAccess.newUser(req.name, req.username, req.email, req.password).map(_ => {
        // Insert into token field
        response.created(
          DataResponse(
            TokenResponse(jwtVendor.vend(req.email))
          )
        )
      })
    }

    filter[JwtAuthFilter].filter[UserSelfOnlyFilter].apply {
      get("/users/:userId") { req: GetUserByIdRequest =>
        usersDbAccess.run {
          usersDbAccess.findUserAndLists(req.request.authContext.user.id).result
        }.map(result => {
          if (result.isEmpty) {
            response.status(404)
          } else {
            val user = result.head._1
            val lists = result.map(_._2)

            DataResponse(
              user.toFull.withLists(lists.map(_.toFull).toList)
            )
          }
        })
      }

      get("/users/:userId/lists") { req: GetUserByIdRequest =>
        usersDbAccess.run {
          usersDbAccess.findUserAndLists(req.request.authContext.user.id).result
        }.map(result => {
          if (result.isEmpty) {
            response.status(404)
          } else {
            val user = result.head._1
            val lists = result.map(_._2)

            user.toFull.withLists(lists.map(_.toFull).toList)
          }
        })
      }

      get("/users/:userId/lists/:listId") { req: GetUserAndListByIdRequest =>
        usersDbAccess.run {
          usersDbAccess.findList(req.userId, req.listId).result
        }.map(result => {
          if (result.isEmpty) {
            response.status(404)
          } else {
            val list = result.head._1
            val things = result.flatMap(_._2)

            list.toFull.withThings(things.toList)
          }
        })
      }

      put("/users/:userId/lists/:listId") { req: AddThingToListRequest =>
        val listFut = if (req.listId == "default") {
          usersDbAccess.run(usersDbAccess.findDefaultListForUser(req.userId).result.headOption)
        } else {
          Promise.fromTry(Try(req.listId.toInt)).future.flatMap(listId => {
            usersDbAccess.run(usersDbAccess.findUserAndList(req.userId, listId).result).map(_.headOption.map(_._2))
          })
        }

        listFut.flatMap {
          case None => Future.successful(response.status(404))
          case Some(list) =>
            thingsDbAccess.run(thingsDbAccess.findThingById(req.itemId).result.headOption).flatMap {
              case None => Future.successful(response.status(404))
              case Some(thing) =>
                usersDbAccess.run(usersDbAccess.addThingToList(list.id.get, thing.id.get)).map(_ => response.status(204))
            }
        }
      }

      get("/users/:userId/events") { req: GetUserByIdRequest =>
        usersDbAccess.run(usersDbAccess.getUserEvents(req.userId.toInt).result).map(res => Map("data" -> res))
      }

      post("/users/:userId/events") { req: AddUserEventRequest =>
        usersDbAccess.run(usersDbAccess.addUserEvent(req.event)).map(res => Map("data" -> res))
      }
    }
  }
}

case class GetUserByIdRequest(
  @RouteParam userId: String,
  request: Request
)

case class GetUserAndListByIdRequest(
  @RouteParam userId: Int,
  @RouteParam listId: Int
)

case class AddThingToListRequest(
  @RouteParam userId: Int,
  @RouteParam listId: String,
  itemId: Int
)

case class AddUserEventRequest(
  @RouteParam userId: Int,
  event: Event // Don't use DAO here
)

case class TokenResponse(token: String)