package com.teletracker.service.controllers

import com.teletracker.common.db.{Bookmark, Popularity, Recent, SortMode}
import com.teletracker.common.db.access.{ThingsDbAccess, UserThingDetails}
import com.teletracker.common.db.model.{Network, ThingType}
import com.teletracker.common.elasticsearch
import com.teletracker.common.elasticsearch.{
  BinaryOperator,
  ItemSearch,
  PeopleCreditSearch,
  PersonCreditSearch
}
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.{DataResponse, Paging}
import com.teletracker.common.util.{
  CanParseFieldFilter,
  Field,
  NetworkCache,
  OpenDateRange
}
import com.teletracker.common.util.json.circe._
import com.teletracker.service.api
import com.teletracker.service.api.model.Item
import com.teletracker.service.api.{
  ItemSearchRequest,
  PeopleCreditsFilter,
  ThingApi
}
import com.teletracker.service.controllers.TeletrackerController._
import com.teletracker.service.controllers.annotations.ItemReleaseYear
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.QueryParam
import com.twitter.finatra.validation.{
  Max,
  MethodValidation,
  Min,
  ValidationResult
}
import javax.inject.Inject
import java.time.LocalDate
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class PopularItemsController @Inject()(
  tmdbClient: TmdbClient,
  thingsDbAccess: ThingsDbAccess,
  networkCache: NetworkCache,
  popularItemSearch: ItemSearch,
  thingApi: ThingApi
)(implicit executionContext: ExecutionContext)
    extends Controller
    with CanParseFieldFilter {
  private val defaultFields = List(Field("id"))

  prefix("/api/v1") {
    get("/popular") { req: GetItemsRequest =>
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
            Paging(bookmark.map(_.encode))
          )
        )
      }
    }
  }

  prefix("/api/v2") {
    get("/explore") { req: GetItemsRequest =>
      val searchRequest = makeSearchRequest(req)
      executeRequest(req.authenticatedUserId, searchRequest)
    }

    get("/popular") { req: GetItemsRequest =>
      val searchRequest = makeSearchRequest(req).copy(sortMode = Popularity())
      executeRequest(req.authenticatedUserId, searchRequest)
    }
  }

  private def executeRequest(
    userId: Option[String],
    searchRequest: ItemSearchRequest
  ) = {
    thingApi
      .search(
        searchRequest
      )
      .map(popularItems => {
        val items =
          popularItems.items
            .map(_.scopeToUser(userId))
            .map(Item.fromEsItem(_, Nil))

        DataResponse.forDataResponse(
          DataResponse(items).withPaging(
            Paging(popularItems.bookmark.map(_.encode))
          )
        )
      })
  }

  private def makeSearchRequest(req: GetItemsRequest) = {
    api.ItemSearchRequest(
      genres = Some(req.genres.map(_.toString)).filter(_.nonEmpty),
      networks = Some(req.networks).filter(_.nonEmpty),
      itemTypes = Some(
        req.itemTypes.flatMap(t => Try(ThingType.fromString(t)).toOption)
      ),
      sortMode = req.sort.map(SortMode.fromString).getOrElse(Recent()),
      limit = req.limit,
      bookmark = req.bookmark.map(Bookmark.parse),
      releaseYear = Some(
        OpenDateRange(
          req.minReleaseYear.map(localDateAtYear),
          req.maxReleaseYear.map(localDateAtYear)
        )
      ),
      peopleCredits =
        if (req.cast.nonEmpty || req.crew.nonEmpty)
          Some(
            api.PeopleCreditsFilter(
              req.cast.toSeq,
              req.crew.toSeq,
              BinaryOperator.And
            )
          )
        else None
    )
  }

  private def localDateAtYear(year: Int): LocalDate = LocalDate.of(year, 1, 1)
}

object GetItemsRequest {
  final val DefaultLimit = 10
  final val MaxYear = LocalDate.now().getYear + 5
}

case class GetItemsRequest(
  @QueryParam(commaSeparatedList = true) itemTypes: Set[String] =
    Set(ThingType.Movie, ThingType.Show).map(_.toString),
  @QueryParam bookmark: Option[String],
  @QueryParam sort: Option[String],
  @Min(0) @Max(50) @QueryParam limit: Int = GetItemsRequest.DefaultLimit,
  @QueryParam(commaSeparatedList = true) networks: Set[String] = Set.empty,
  @QueryParam fields: Option[String] = None,
  @QueryParam(commaSeparatedList = true) genres: Set[Int] = Set.empty,
  @QueryParam @ItemReleaseYear minReleaseYear: Option[Int],
  @QueryParam @ItemReleaseYear maxReleaseYear: Option[Int],
  @QueryParam(commaSeparatedList = true) cast: Set[String] = Set.empty,
  @QueryParam(commaSeparatedList = true) crew: Set[String] = Set.empty,
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
