package com.chrisbenincasa.services.teletracker.controllers

import com.chrisbenincasa.services.teletracker.db.ThingsDbAccess
import com.chrisbenincasa.services.teletracker.model.DataResponse
import com.chrisbenincasa.services.teletracker.util.json.circe._
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.RouteParam
import javax.inject.Inject
import io.circe.generic.auto._
import scala.concurrent.ExecutionContext

class PeopleController @Inject()(
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext) extends Controller {
  prefix("/api/v1") {
    get("/person/:personId") { req: GetPersonRequest =>
      thingsDbAccess.findPersonById(req.personId).map(DataResponse.complex(_))
    }
  }
}

case class GetPersonRequest(
  @RouteParam personId: Int
)
