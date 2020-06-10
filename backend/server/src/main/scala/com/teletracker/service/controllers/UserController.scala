package com.teletracker.service.controllers

import com.teletracker.common.api.model.{TrackedListOptions, TrackedListRules}
import com.teletracker.common.db.model._
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
  IdOrSlug,
  ListFilterParser
}
import com.teletracker.service.api.model.{UserList, UserListRules}
import com.teletracker.service.api.{ItemApi, ListsApi, UsersApi}
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
  listFilterParser: ListFilterParser,
  thingApi: ItemApi
)(implicit executionContext: ExecutionContext)
    extends TeletrackerController(listsApi)
    with CanParseFieldFilter {

  prefix("/api/v2/users") {
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
        def returnLists(lists: Seq[UserList]) =
          response.ok
            .contentTypeJson()
            .body(
              DataResponse.complex(
                lists.sortBy(list => (list.createdAt, list.legacyId))
              )
            )

        usersApi
          .getUserLists(req.authenticatedUserId.get)
          .flatMap(result => {
            if (result.isEmpty) {
              usersApi
                .createDefaultListsForUser(req.authenticatedUserId.get)
                .flatMap(_ => {
                  usersApi
                    .getUserLists(
                      req.authenticatedUserId.get
                    )
                    .map(returnLists)
                })
            } else {
              Future.successful(returnLists(result))
            }
          })
      }

      post("/:userId/lists") { req: Request =>
        parse(req.contentString).flatMap(_.as[CreateUserListRequest]) match {
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

      put("/:userId/lists/:listId") { req: UpdateListRequest =>
        withList(req.authenticatedUserId.get, req.listId) { list =>
          listsApi
            .updateList(
              list.id,
              req.authenticatedUserId.get,
              req.name,
              req.rules,
              req.options
            )
            .transformWith {
              case Success(updateResult) =>
                listsApi
                  .handleListUpdateResult(
                    req.authenticatedUserId.get,
                    list.id,
                    updateResult
                  )
                  .map(_ => {
                    response.ok(
                      DataResponse.complex(
                        UpdateListResponse(
                          updateResult.optionsChanged || updateResult.rulesChanged
                        )
                      )
                    )
                  })
              case Failure(ex: IllegalArgumentException) =>
                Future.successful(response.notFound)
              case Failure(NonFatal(ex)) =>
                Future.successful(response.internalServerError)
            }
        }
      }

      put("/:userId/lists/:listId/things") { req: AddThingToListRequest =>
        withList(req.authenticatedUserId.get, req.listId) { list =>
          listsApi
            .addThingToList(
              req.authenticatedUserId.get,
              list.id,
              UUID.fromString(req.thingId)
            )
            .map(updateResponse => {

              if (updateResponse.error) {
                response.internalServerError
              } else {
                response.noContent
              }
            })
        }
      }

      put("/:userId/lists") { req: AddThingToListsRequest =>
        listsApi
          .findUserLists(
            req.authenticatedUserId.get
          )
          .flatMap(lists => {
            val listIds = lists.map(_.id).toSet
            val (validListIds, _) = req.listIds.partition(listIds(_))

            if (validListIds.isEmpty) {
              Future.successful(response.notFound)
            } else {
              if (req.idOrSlug.isRight && req.thingType.isEmpty) {
                throw new IllegalArgumentException("")
              }

              thingApi
                .getThingViaSearch(
                  req.authenticatedUserId,
                  req.idOrSlug,
                  req.thingType,
                  materializeRecommendations = false
                )
                .flatMap {
                  case None => Future.successful(response.notFound)
                  case Some(item) =>
                    val futs = validListIds.map(listId => {
                      listsApi.addThingToList(
                        req.authenticatedUserId.get,
                        listId,
                        item.id
                      )
                    })

                    Future.sequence(futs).map(_ => response.noContent)
                }
            }
          })
      }

      delete("/:userId/lists/:listId") { req: DeleteListRequest =>
        withList(req.authenticatedUserId.get, req.listId) { list =>
          listsApi
            .deleteList(
              req.authenticatedUserId.get,
              list.id,
              req.mergeWithList.map(UUID.fromString)
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

      put("/:userId/things/:thingId/lists") { req: ManageShowListsRequest =>
        listsApi
          .findUserLists(req.authenticatedUserId.get)
          .flatMap(lists => {
            val listIds = lists.map(_.id).toSet
            val validAdds = req.addToLists.filter(listIds(_))
            val validRemoves = req.removeFromLists.filter(listIds(_))

            if (validAdds.isEmpty && validRemoves.isEmpty) {
              Future.successful(response.notFound)
            } else {
              val futs = validAdds.map(listId => {
                listsApi.addThingToList(
                  req.authenticatedUserId.get,
                  listId,
                  UUID.fromString(req.thingId)
                )
              })

              val removeFuts = validRemoves.map(listId => {
                listsApi.removeThingFromList(
                  req.authenticatedUserId.get,
                  listId,
                  UUID.fromString(req.thingId)
                )
              })

              Future
                .sequence(futs ++ removeFuts)
                .map(_ => response.noContent)
            }
          })
      }

      put("/:userId/things/:thingId/actions") {
        req: UpdateUserThingActionRequest =>
          Try(UserThingTagType.fromString(req.action)) match {
            case Success(tagType) =>
              if (tagType.typeRequiresValue() && req.value.isEmpty) {
                Future.successful(response.badRequest)
              } else {
                if (req.idOrSlug.isRight && req.thingType.isEmpty) {
                  throw new IllegalArgumentException("")
                }

                val newOrUpdatedTag = thingApi.addTagToThing(
                  req.request.authenticatedUserId.get,
                  req.idOrSlug,
                  req.thingType,
                  tagType,
                  req.value
                )

                // TODO: Handle errors
                newOrUpdatedTag.foreach(
                  _.foreach(Function.tupled(usersApi.handleTagChange))
                )

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
              thingApi
                .removeTagFromThing(
                  req.request.authenticatedUserId.get,
                  req.idOrSlug,
                  req.thingType,
                  action
                )
                .map(_ => response.noContent)

            case Failure(_) =>
              Future.successful(
                response
                  .badRequest(new IllegalActionTypeError(req.actionType))
                  .contentTypeJson()
              )
          }
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

case class ListFilters(itemTypes: Option[Set[ItemType]])

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

@JsonCodec
case class CreateListRequest(
  name: String,
  thingIds: Option[List[UUID]],
  rules: Option[TrackedListRules])

@JsonCodec
case class CreateUserListRequest(
  name: String,
  thingIds: Option[List[UUID]],
  rules: Option[UserListRules])

case class CreateListResponse(id: UUID)

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
  @RouteParam listId: String,
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
  thingType: Option[ItemType], // Required if thingId is a slug...
  listIds: List[UUID],
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
  addToLists: List[UUID],
  removeFromLists: List[UUID],
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
//  networkSubscriptions: Option[List[Network]],
  userPreferences: Option[UserPreferences])

case class UpdateUserThingActionRequest(
  @RouteParam userId: String,
  @RouteParam thingId: String,
  thingType: Option[ItemType],
  action: String,
  value: Option[Double],
  request: Request)
    extends HasThingIdOrSlug

case class DeleteUserThingActionRequest(
  @RouteParam userId: String,
  @RouteParam thingId: String,
  @RouteParam actionType: String,
  thingType: Option[ItemType],
  request: Request)
    extends HasThingIdOrSlug
