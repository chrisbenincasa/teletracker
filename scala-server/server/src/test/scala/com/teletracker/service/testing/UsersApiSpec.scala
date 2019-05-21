package com.teletracker.service.testing

import com.teletracker.service.controllers.{
  CreateListResponse,
  CreateUserRequest,
  CreateUserResponse
}
import com.teletracker.service.db.ThingsDbAccess
import com.teletracker.service.db.model.{Thing, ThingType, TrackedList, User}
import com.teletracker.service.model.DataResponse
import com.teletracker.service.testing.framework.BaseSpecWithServer
import com.teletracker.service.util.Slug
import com.teletracker.service.util.json.circe._
import com.twitter.finagle.http.Status
import io.circe.generic.auto._
import io.circe.parser._
import org.joda.time.DateTime
import java.util.UUID

class UsersApiSpec extends BaseSpecWithServer {
  "Users API" should "create a default list for a new user" in {
    val createResponse = server.httpPost(
      "/api/v1/users",
      serializer.writeValueAsString(
        CreateUserRequest("gordon@hacf.com", "gordo", "Gordon", "password")
      )
    )

    val DataResponse(CreateUserResponse(_, token)) =
      parse(createResponse.contentString)
        .flatMap(_.as[DataResponse[CreateUserResponse]])
        .right
        .get

    val getResponse = server.httpGet(
      "/api/v1/users/self",
      headers = Map("Authorization" -> s"Bearer $token")
    )

    val parsedResponse =
      parse(getResponse.contentString)
        .flatMap(_.as[DataResponse[User]])
        .right
        .get

    println(parsedResponse)
  }

  it should "retrieve all lists for a user" in {
    val (_, token) = createUser()

    server.httpPost(
      "/api/v1/users/self/lists",
      serializer.writeValueAsString(Map("name" -> "My New List")),
      headers = Map("Authorization" -> s"Bearer $token")
    )

    val userListsResponse = server.httpGet(
      "/api/v1/users/self/lists",
      headers = Map("Authorization" -> s"Bearer $token")
    )

    parse(userListsResponse.contentString)
      .flatMap(_.as[DataResponse[User]]) match {
      case Left(err) =>
        fail(err)
      case Right(value) =>
        println(value)
    }
  }

  it should "respond with a user's specific list" in {
    val (_, token) = createUser()

    val createListResponse = server.httpPost(
      "/api/v1/users/self/lists",
      serializer.writeValueAsString(Map("name" -> "My New List")),
      headers = Map("Authorization" -> s"Bearer $token")
    )

    val DataResponse(CreateListResponse(listId)) = parse(
      createListResponse.contentString
    ).flatMap(_.as[DataResponse[CreateListResponse]]).right.get

    val listResponse = server.httpGet(
      s"/api/v1/users/self/lists/$listId",
      headers = Map("Authorization" -> s"Bearer $token")
    )

    val deser = parse(listResponse.contentString)
      .flatMap(_.as[DataResponse[TrackedList]])
      .right
      .get

    println(deser)
  }

  it should "respond with 404 is a user's list is not found" in {
    val (_, token) = createUser()

    server.httpGet(
      "/api/v1/users/self/lists/1000",
      headers = Map("Authorization" -> s"Bearer $token"),
      andExpect = Status.NotFound
    )
  }

  it should "add a show to a user's list" in {
    val (_, token) = createUser()

    val createListResponse = server.httpPost(
      "/api/v1/users/self/lists",
      serializer.writeValueAsString(Map("name" -> "My New List")),
      headers = Map("Authorization" -> s"Bearer $token")
    )

    val DataResponse(CreateListResponse(listId)) = parse(
      createListResponse.contentString
    ).flatMap(_.as[DataResponse[CreateListResponse]]).right.get

    val thing = server.injector
      .instance[ThingsDbAccess]
      .saveThing(
        Thing(
          None,
          "Halt and Catch Fire",
          Slug("Halt and Catch Fire"),
          ThingType.Show,
          DateTime.now(),
          DateTime.now(),
          None
        )
      )
      .await()

    server.httpPut(
      s"/api/v1/users/self/lists/$listId",
      serializer.writeValueAsString(
        Map("itemId" -> thing.id.get)
      ),
      headers = Map("Authorization" -> s"Bearer $token")
    )

    val listResponse = server.httpGet(
      s"/api/v1/users/self/lists/$listId",
      headers = Map("Authorization" -> s"Bearer $token")
    )

    val deser = parse(listResponse.contentString)
      .flatMap(_.as[DataResponse[TrackedList]])
      .right
      .get

    println(deser)
  }

  private def createUser() = {
    val createUserRequest = CreateUserRequest(
      UUID.randomUUID().toString,
      UUID.randomUUID().toString,
      "Gordon",
      "password"
    )

    val response = server.httpPost(
      "/api/v1/users",
      serializer.writeValueAsString(createUserRequest)
    )

    val DataResponse(CreateUserResponse(userId, token)) =
      parse(response.contentString)
        .flatMap(_.as[DataResponse[CreateUserResponse]])
        .right
        .get

    userId -> token
  }
}
