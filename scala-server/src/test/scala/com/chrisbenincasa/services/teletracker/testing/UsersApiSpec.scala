package com.chrisbenincasa.services.teletracker.testing

import com.chrisbenincasa.services.teletracker.controllers.{CreateListResponse, CreateUserRequest, CreateUserResponse}
import com.chrisbenincasa.services.teletracker.db.ThingsDbAccess
import com.chrisbenincasa.services.teletracker.db.model.{Thing, ThingType, TrackedList, User}
import com.chrisbenincasa.services.teletracker.model.DataResponse
import com.chrisbenincasa.services.teletracker.testing.framework.BaseSpecWithServer
import com.chrisbenincasa.services.teletracker.util.Slug
import com.chrisbenincasa.services.teletracker.util.json.circe._
import com.fasterxml.jackson.core.`type`.TypeReference
import java.lang.reflect.{ParameterizedType, Type}
import io.circe.parser._
import io.circe.generic.auto._
import java.util.UUID
import org.joda.time.DateTime
import scala.reflect.Manifest

class UsersApiSpec extends BaseSpecWithServer {
  "Users API" should "create a default list for a new user" in {
    val createResponse = server.httpPost(
      "/api/v1/users",
      serializer.writeValueAsString(CreateUserRequest("gordon@hacf.com", "gordo", "Gordon", "password"))
    )

    val DataResponse(CreateUserResponse(_, token)) =
      parse(createResponse.contentString).flatMap(_.as[DataResponse[CreateUserResponse]]).right.get

    val getResponse = server.httpGet(
      "/api/v1/users/self",
      headers = Map("Authorization" -> s"Bearer $token")
    )

    val parsedResponse =
      parse(getResponse.contentString).flatMap(_.as[DataResponse[User]]).right.get

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

    val deser = parse(userListsResponse.contentString).flatMap(_.as[DataResponse[User]]).right.get

    println(deser)
  }

  it should "respond with a user's specific list" in {
    val (_, token) = createUser()

    val createListResponse = server.httpPost(
      "/api/v1/users/self/lists",
      serializer.writeValueAsString(Map("name" -> "My New List")),
      headers = Map("Authorization" -> s"Bearer $token")
    )

    val DataResponse(CreateListResponse(listId)) = parse(createListResponse.contentString).flatMap(_.as[DataResponse[CreateListResponse]]).right.get

    val listResponse = server.httpGet(
      s"/api/v1/users/self/lists/$listId",
      headers = Map("Authorization" -> s"Bearer $token")
    )

    val deser = parse(listResponse.contentString).flatMap(_.as[DataResponse[TrackedList]]).right.get

    println(deser)
  }

  it should "respond with 404 is a user's list is not found" in {
    val (_, token) = createUser()

    val noListResponse = server.httpGet(
      "/api/v1/users/self/lists/1000",
      headers = Map("Authorization" -> s"Bearer $token")
    )

    assert(noListResponse.statusCode === 404)
  }

  it should "add a show to a user's list" in {
    val (_, token) = createUser()

    val createListResponse = server.httpPost(
      "/api/v1/users/self/lists",
      serializer.writeValueAsString(Map("name" -> "My New List")),
      headers = Map("Authorization" -> s"Bearer $token")
    )

    val DataResponse(CreateListResponse(listId)) = parse(createListResponse.contentString).flatMap(_.as[DataResponse[CreateListResponse]]).right.get

    val thing = server.injector.instance[ThingsDbAccess].saveThing(
      Thing(None, "Halt and Catch Fire", Slug("Halt and Catch Fire"), ThingType.Show, DateTime.now(), DateTime.now(), None)
    ).await()

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

    val deser = parse(listResponse.contentString).flatMap(_.as[DataResponse[TrackedList]]).right.get

    println(deser)
  }

  private def createUser() = {
    val createUserRequest = CreateUserRequest(UUID.randomUUID().toString, UUID.randomUUID().toString, "Gordon", "password")

    val response = server.httpPost(
      "/api/v1/users",
      serializer.writeValueAsString(createUserRequest)
    )

    val DataResponse(CreateUserResponse(userId, token)) =
      parse(response.contentString).flatMap(_.as[DataResponse[CreateUserResponse]]).right.get

    userId -> token
  }

  def typeRefFromManifest[A: Manifest]: TypeReference[A] = {
    new TypeReference[A] {
      override def getType: Type = {
        if (manifest[A].typeArguments.isEmpty) {
          manifest[A].runtimeClass
        } else {
          new ParameterizedType {
            override def getActualTypeArguments: Array[Type] = manifest.typeArguments.map(_.runtimeClass).toArray

            override def getRawType: Type = manifest.runtimeClass

            override def getOwnerType: Type = manifest.runtimeClass
          }
        }
      }
    }
  }
}
