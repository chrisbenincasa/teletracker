package com.teletracker.service.testing

import com.chrisbenincasa.services.teletracker.auth.jwt.JwtVendor
import com.chrisbenincasa.services.teletracker.controllers.EventCreate
import com.chrisbenincasa.services.teletracker.db.UsersDbAccess
import com.chrisbenincasa.services.teletracker.db.model.{Event, EventWithTarget}
import com.chrisbenincasa.services.teletracker.model.DataResponse
import com.chrisbenincasa.services.teletracker.testing.framework.BaseSpecWithServer
import com.chrisbenincasa.services.teletracker.util.json.circe._
import io.circe.parser._
import io.circe.generic.auto._

class EventsSpec extends BaseSpecWithServer {
  "Events" should "should create and read events for a user" in {
    val access = injector.getInstance(classOf[UsersDbAccess])
    val (userId, jwt) = access.createUserAndToken("Christian", "chrisbenincasa", "test@test.com", "password").await()

    server.httpPost(
      "/api/v1/users/self/events",
      serializer.writeValueAsString(Map("event" -> EventCreate("Watched", "show", "123", None, System.currentTimeMillis()))),
      headers = Map("Authorization" -> s"Bearer $jwt")
    )

    val eventsResponse = server.httpGet(
      "/api/v1/users/self/events",
      headers = Map("Authorization" -> s"Bearer $jwt")
    )

    val parsedResponse = parse(eventsResponse.contentString).flatMap(_.as[DataResponse[List[EventWithTarget]]]).right.get

    assert(parsedResponse.data.length === 1)
  }
}
