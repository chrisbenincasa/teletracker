package com.teletracker.service.testing

import com.teletracker.service.api.UsersApi
import com.teletracker.service.controllers.EventCreate
import com.teletracker.service.db.access.UsersDbAccess
import com.teletracker.service.db.model.EventWithTarget
import com.teletracker.service.model.DataResponse
import com.teletracker.service.testing.framework.BaseSpecWithServer
import com.teletracker.service.util.json.circe._
import io.circe.generic.auto._
import io.circe.parser._

class EventsSpec extends BaseSpecWithServer {
  "Events" should "should create and read events for a user" in {
    val access = injector.getInstance(classOf[UsersApi])
    val (userId, jwt) = access
      .createUserAndToken(
        "Christian",
        "chrisbenincasa",
        "test@test.com",
        "password"
      )
      .await()

    server.httpPost(
      "/api/v1/users/self/events",
      serializer.writeValueAsString(
        Map(
          "event" -> EventCreate(
            "Watched",
            "show",
            "123",
            None,
            System.currentTimeMillis()
          )
        )
      ),
      headers = Map("Authorization" -> s"Bearer $jwt")
    )

    val eventsResponse = server.httpGet(
      "/api/v1/users/self/events",
      headers = Map("Authorization" -> s"Bearer $jwt")
    )

    val parsedResponse = parse(eventsResponse.contentString)
      .flatMap(_.as[DataResponse[List[EventWithTarget]]])
      .right
      .get

    assert(parsedResponse.data.length === 1)
  }
}
