package com.teletracker.service.controllers

import com.teletracker.common.db.Bookmark
import com.teletracker.common.db.access.{ThingsDbAccess, UserThingDetails}
import com.teletracker.common.db.model.{Network, ThingType}
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.{DataResponse, Paging}
import com.teletracker.common.util.{CanParseFieldFilter, Field, NetworkCache}
import com.teletracker.common.util.json.circe._
import com.teletracker.service.controllers.TeletrackerController._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.QueryParam
import com.twitter.finatra.validation.{MethodValidation, ValidationResult}
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class PopularItemsController @Inject()(
  tmdbClient: TmdbClient,
  thingsDbAccess: ThingsDbAccess,
  networkCache: NetworkCache
)(implicit executionContext: ExecutionContext)
    extends Controller
    with CanParseFieldFilter {
  private val defaultFields = List(Field("id"))

  prefix("/api/v1") {
    get("/popular") { req: GetPopularItemsRequest =>
      val parsedFields = parseFieldsOrNone(req.fields)

      val networksFut = if (req.networks.nonEmpty) {
        networkCache
          .get()
          .map(networks => {
            networks.values
              .filter(network => req.networks.contains(network.slug.value))
              .toSet
          })
      } else {
        Future.successful(Set.empty[Network])
      }

      for {
        networks <- networksFut
        (popularItems, bookmark) <- thingsDbAccess.getMostPopularItems(
          req.itemTypes.flatMap(t => Try(ThingType.fromString(t)).toOption),
          networks,
          req.bookmark.map(Bookmark.parse),
          req.limit
        )
        thingIds = popularItems.map(_.id)

        thingUserDetails <- req.request.authenticatedUserId
          .map(
            thingsDbAccess
              .getThingsUserDetails(_, thingIds.toSet)
          )
          .getOrElse(Future.successful(Map.empty[UUID, UserThingDetails]))
      } yield {
        val itemsWithMeta = popularItems.map(thing => {
          val meta = thingUserDetails
            .getOrElse(thing.id, UserThingDetails.empty)
          thing
            .selectFields(parsedFields, defaultFields)
            .toPartial
            .withUserMetadata(meta)
        })

        DataResponse.forDataResponse(
          DataResponse(itemsWithMeta).withPaging(
            Paging(bookmark.map(_.asString))
          )
        )
      }
    }
  }
}

case class GetPopularItemsRequest(
  @QueryParam(commaSeparatedList = true) itemTypes: Set[String] =
    Set(ThingType.Movie, ThingType.Show).map(_.toString),
  @QueryParam bookmark: Option[String],
  @QueryParam limit: Int = 10,
  @QueryParam(commaSeparatedList = true) networks: Set[String] = Set.empty,
  @QueryParam fields: Option[String] = None,
  request: Request)
    extends InjectedRequest {

  @MethodValidation
  def validateBookmark: ValidationResult = {
    bookmark
      .map(b => {
        ValidationResult.validate(
          Try(Bookmark.parse(b)).isSuccess,
          s"Invalid bookmark with format: $b"
        )
      })
      .getOrElse(ValidationResult.Valid())
  }
}
