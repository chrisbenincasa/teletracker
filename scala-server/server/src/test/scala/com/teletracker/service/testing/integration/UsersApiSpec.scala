package com.teletracker.service.testing.integration

import com.teletracker.service.testing.framework.BaseSpecWithServer

class UsersApiSpec extends BaseSpecWithServer {
  "Users API" should "create a default list for a new user" in {
    //    val createResponse = server.httpPost(
    //      "/api/v1/users",
    //      serializer.writeValueAsString(
    //        CreateUserRequest("gordon@hacf.com", "gordo", "Gordon", "password")
    //      )
    //    )
    //
    //    val DataResponse(CreateUserResponse(_, token)) =
    //      parse(createResponse.contentString)
    //        .flatMap(_.as[DataResponse[CreateUserResponse]])
    //        .right
    //        .get
    //
    //    val getResponse = server.httpGet(
    //      "/api/v1/users/self",
    //      headers = Map("Authorization" -> s"Bearer $token")
    //    )
    //
    //    val parsedResponse =
    //      parse(getResponse.contentString)
    //        .flatMap(_.as[DataResponse[User]])
    //        .right
    //        .get
    //
    //    println(parsedResponse)
    //  }

    //  it should "retrieve all lists for a user" in {
    //    val (_, token) = createUser()
    //
    //    server.httpPost(
    //      "/api/v1/users/self/lists",
    //      serializer.writeValueAsString(Map("name" -> "My New List")),
    //      headers = Map("Authorization" -> s"Bearer $token")
    //    )
    //
    //    val userListsResponse = server.httpGet(
    //      "/api/v1/users/self/lists",
    //      headers = Map("Authorization" -> s"Bearer $token")
    //    )
    //
    //    parse(userListsResponse.contentString)
    //      .flatMap(_.as[DataResponse[User]]) match {
    //      case Left(err) =>
    //        fail(err)
    //      case Right(value) =>
    //        println(value)
    //    }
    //  }
    //
    //  it should "respond with a user's specific list" in {
    //    val (_, token) = createUser()
    //
    //    val createListResponse = server.httpPost(
    //      "/api/v1/users/self/lists",
    //      serializer.writeValueAsString(Map("name" -> "My New List")),
    //      headers = Map("Authorization" -> s"Bearer $token")
    //    )
    //
    //    val DataResponse(CreateListResponse(listId)) = parse(
    //      createListResponse.contentString
    //    ).flatMap(_.as[DataResponse[CreateListResponse]]).right.get
    //
    //    val listResponse = server.httpGet(
    //      s"/api/v1/users/self/lists/$listId",
    //      headers = Map("Authorization" -> s"Bearer $token")
    //    )
    //
    //    val deser = parse(listResponse.contentString)
    //      .flatMap(_.as[DataResponse[TrackedList]])
    //      .right
    //      .get
    //
    //    println(deser)
    //  }
    //
    //  it should "respond with 404 is a user's list is not found" in {
    //    val (_, token) = createUser()
    //
    //    server.httpGet(
    //      "/api/v1/users/self/lists/1000",
    //      headers = Map("Authorization" -> s"Bearer $token"),
    //      andExpect = Status.NotFound
    //    )
    //  }
    //
    //  it should "add a show to a user's list" in {
    //    val (_, token) = createUser()
    //
    //    val createListResponse = server.httpPost(
    //      "/api/v1/users/self/lists",
    //      serializer.writeValueAsString(Map("name" -> "My New List")),
    //      headers = Map("Authorization" -> s"Bearer $token")
    //    )
    //
    //    val DataResponse(CreateListResponse(listId)) = parse(
    //      createListResponse.contentString
    //    ).flatMap(_.as[DataResponse[CreateListResponse]]).right.get
    //
    //    val thing = server.injector
    //      .instance[ThingsDbAccess]
    //      .saveThing(
    //        Thing(
    //          UUID.randomUUID(),
    //          "Halt and Catch Fire",
    //          Slug("Halt and Catch Fire", 2015),
    //          ThingType.Show,
    //          OffsetDateTime.now(),
    //          OffsetDateTime.now(),
    //          None
    //        )
    //      )
    //      .await()
    //
    //    server.httpPut(
    //      s"/api/v1/users/self/lists/$listId",
    //      serializer.writeValueAsString(
    //        Map("itemId" -> thing.id)
    //      ),
    //      headers = Map("Authorization" -> s"Bearer $token")
    //    )
    //
    //    val listResponse = server.httpGet(
    //      s"/api/v1/users/self/lists/$listId",
    //      headers = Map("Authorization" -> s"Bearer $token")
    //    )
    //
    //    val deser = parse(listResponse.contentString)
    //      .flatMap(_.as[DataResponse[TrackedList]])
    //      .right
    //      .get
    //
    //    println(deser)
    //  }
    //
    //  it should "delete a user's list" in {
    //    val (_, token) = createUser()
    //
    //    val createListResponse = server.httpPost(
    //      "/api/v1/users/self/lists",
    //      serializer.writeValueAsString(Map("name" -> "My New List")),
    //      headers = Map("Authorization" -> s"Bearer $token")
    //    )
    //
    //    val DataResponse(CreateListResponse(listId)) = parse(
    //      createListResponse.contentString
    //    ).flatMap(_.as[DataResponse[CreateListResponse]]).right.get
    //
    //    val deleteListResponse = server.httpDelete(
    //      s"/api/v1/users/self/lists/$listId",
    //      headers = Map("Authorization" -> s"Bearer $token")
    //    )
    //
    //    assert(deleteListResponse.status.code == 204)
    //
    //    val getListsResponse = server.httpGet(
    //      "/api/v1/users/self/lists",
    //      headers = Map("Authorization" -> s"Bearer $token")
    //    )
    //
    //    val DataResponse(user) = parse(getListsResponse.contentString)
    //      .flatMap(_.as[DataResponse[User]])
    //      .right
    //      .get
    //
    //    assert(!user.lists.getOrElse(Nil).exists(_.id == listId))
    //  }
    //
    //  it should "merge a user's list upon deletion" in {
    //    val (_, token) = createUser()
    //
    //    val list1 = createList(token, "My List 1")
    //    val list2 = createList(token, "My List 2")
    //
    //    val thing1 = createThing(token, "Halt and Catch Fire", Set(list1, list2))
    //    val thing2 = createThing(token, "Chernobyl", Set(list1))
    //    val thing3 = createThing(token, "Veep", Set(list1))
    //
    //    deleteList(token, list1, mergeWith = Some(list2))
    //
    //    val mergedList2 = getList(token, list2)
    //
    //    assert(
    //      mergedList2.things.getOrElse(Nil).map(_.id).toSet === Set(
    //        thing1.id,
    //        thing2.id,
    //        thing3.id
    //      )
    //    )
    //  }
    //
    //  private def createUser() = {
    //    val createUserRequest = CreateUserRequest(
    //      UUID.randomUUID().toString,
    //      UUID.randomUUID().toString,
    //      "Gordon",
    //      "password"
    //    )
    //
    //    val response = server.httpPost(
    //      "/api/v1/users",
    //      serializer.writeValueAsString(createUserRequest)
    //    )
    //
    //    val DataResponse(CreateUserResponse(userId, token)) =
    //      parse(response.contentString)
    //        .flatMap(_.as[DataResponse[CreateUserResponse]])
    //        .right
    //        .get
    //
    //    userId -> token
    //  }
    //
    //  private def createList(
    //    token: String,
    //    name: String
    //  ) = {
    //    val createListResponse = server.httpPost(
    //      "/api/v1/users/self/lists",
    //      serializer.writeValueAsString(Map("name" -> name)),
    //      headers = Map("Authorization" -> s"Bearer $token")
    //    )
    //
    //    val DataResponse(CreateListResponse(listId)) = parse(
    //      createListResponse.contentString
    //    ).flatMap(_.as[DataResponse[CreateListResponse]]).right.get
    //
    //    listId
    //  }
    //
    //  private def createThing(
    //    token: String,
    //    name: String,
    //    insertToLists: Set[Int]
    //  ) = {
    //    val thing = server.injector
    //      .instance[ThingsDbAccess]
    //      .saveThing(
    //        Thing(
    //          UUID.randomUUID(),
    //          name,
    //          Slug(name, 2010),
    //          ThingType.Show,
    //          OffsetDateTime.now(),
    //          OffsetDateTime.now(),
    //          None
    //        )
    //      )
    //      .await()
    //
    //    insertToLists.foreach(listId => {
    //      server.httpPut(
    //        s"/api/v1/users/self/lists/$listId",
    //        serializer.writeValueAsString(
    //          Map("itemId" -> thing.id)
    //        ),
    //        headers = Map("Authorization" -> s"Bearer $token")
    //      )
    //    })
    //
    //    thing
    //  }
    //
    //  private def deleteList(
    //    token: String,
    //    listId: Int,
    //    mergeWith: Option[Int] = None
    //  ) = {
    //    val params = mergeWith.map(i => s"?mergeWithList=$i").getOrElse("")
    //
    //    val deleteListResponse = server.httpDelete(
    //      s"/api/v1/users/self/lists/$listId$params",
    //      headers = Map("Authorization" -> s"Bearer $token")
    //    )
    //
    //    assert(deleteListResponse.status.code == 204)
    //  }
    //
    //  private def getList(
    //    token: String,
    //    listId: Int
    //  ) = {
    //    val listResponse = server.httpGet(
    //      s"/api/v1/users/self/lists/$listId",
    //      headers = Map("Authorization" -> s"Bearer $token")
    //    )
    //
    //    parse(listResponse.contentString)
    //      .flatMap(_.as[DataResponse[TrackedList]])
    //      .right
    //      .get
    //      .data
    //  }
  }
}
