package com.chrisbenincasa.services.teletracker.testing

import com.chrisbenincasa.services.teletracker.auth.jwt.JwtVendor
import com.chrisbenincasa.services.teletracker.controllers.AddUserEventRequest
import com.chrisbenincasa.services.teletracker.db.UsersDbAccess
import com.chrisbenincasa.services.teletracker.db.model.Event
import com.chrisbenincasa.services.teletracker.model.DataResponse
import com.chrisbenincasa.services.teletracker.testing.framework.BaseSpecWithServer
import com.chrisbenincasa.services.teletracker.util.json.circe._
import io.circe.parser._
import io.circe.generic.auto._
import java.sql.Timestamp

class EventsSpec extends BaseSpecWithServer {
  "Events" should "should create and read events for a user" in {
    val access = injector.getInstance(classOf[UsersDbAccess])
    val userId = access.newUser("Christian", "chrisbenincasa", "test@test.com", "password").await()
    val jwt = injector.getInstance(classOf[JwtVendor]).vend("test@test.com")

    server.httpPost(
      "/api/v1/users/self/events",
      serializer.writeValueAsString(AddUserEventRequest("self", Event(None, "Watched", "show", "123", None, userId, new Timestamp(System.currentTimeMillis())), null)),
      headers = Map("Authorization" -> s"Bearer $jwt")
    )

    val eventsResponse = server.httpGet(
      "/api/v1/users/self/events",
      headers = Map("Authorization" -> s"Bearer $jwt")
    )

    val parsedResponse = parse(eventsResponse.contentString).flatMap(_.as[DataResponse[List[Event]]]).right.get

    assert(parsedResponse.data.length === 1)
  }
}
