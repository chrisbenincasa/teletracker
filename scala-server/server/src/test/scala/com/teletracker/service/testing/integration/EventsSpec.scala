package com.teletracker.service.testing.integration

import com.teletracker.service.testing.framework.BaseSpecWithServer

class EventsSpec extends BaseSpecWithServer {
//  "Events" should "should create and read events for a user" in {
//    val access = injector.getInstance(classOf[UsersApi])
//    val (userId, jwt) = access
//      .createUserAndToken(
//        "Christian",
//        "chrisbenincasa",
//        "test@test.com",
//        "password"
//      )
//      .await()
//
//    server.httpPost(
//      "/api/v1/users/self/events",
//      serializer.writeValueAsString(
//        Map(
//          "event" -> EventCreate(
//            "Watched",
//            "show",
//            "123",
//            None,
//            System.currentTimeMillis()
//          )
//        )
//      ),
//      headers = Map("Authorization" -> s"Bearer $jwt")
//    )
//
//    val eventsResponse = server.httpGet(
//      "/api/v1/users/self/events",
//      headers = Map("Authorization" -> s"Bearer $jwt")
//    )
//
//    val parsedResponse = parse(eventsResponse.contentString)
//      .flatMap(_.as[DataResponse[List[EventWithTarget]]])
//      .right
//      .get
//
//    assert(parsedResponse.data.length === 1)
//  }
}
