package com.teletracker.service.api

import com.teletracker.common.db.dynamo.model.{StoredGenre, StoredNetwork}
import com.teletracker.common.db.model.{
  ItemType,
  PersonAssociationType,
  UserThingTagType
}
import com.teletracker.common.db.{Bookmark, SortMode}
import com.teletracker.common.elasticsearch._
import com.teletracker.common.elasticsearch.model._
import com.teletracker.common.util._
import com.teletracker.service.api.model.Item
import javax.inject.Inject
import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ItemApi @Inject()(
  genreCache: GenreCache,
  networkCache: NetworkCache,
  itemSearch: ItemSearch,
  itemLookup: ItemLookup,
  itemUpdater: ItemUpdater,
  personLookup: PersonLookup
)(implicit executionContext: ExecutionContext) {
  def getThingViaSearch(
    userId: Option[String],
    idOrSlug: String,
    thingType: Option[ItemType],
    materializeRecommendations: Boolean
  ): Future[Option[Item]] = {
    getThingViaSearch(
      userId,
      HasThingIdOrSlug.parse(idOrSlug),
      thingType,
      materializeRecommendations
    )
  }

  def getThingViaSearch(
    userId: Option[String],
    idOrSlug: Either[UUID, Slug],
    thingType: Option[ItemType],
    materializeRecommendations: Boolean
  ): Future[Option[Item]] = {
    itemLookup
      .lookupItem(
        idOrSlug,
        thingType,
        shouldMaterializeRecommendations = materializeRecommendations
      )
      .map(_.collect {
        case ItemLookupResponse(item, cast, recs) =>
          Item.fromEsItem(
            item.scopeToUser(userId),
            recs.items
              .map(_.scopeToUser(userId))
              .map(Item.fromEsItem(_, Nil, Map.empty)),
            cast.items.map(i => i.id -> i).toMap
          )
      })
  }

  def addTagToThing[T](
    userId: String,
    idOrSlug: Either[UUID, Slug],
    thingType: Option[ItemType],
    tag: UserThingTagType,
    value: Option[T]
  )(implicit esItemTaggable: EsItemTaggable[T]
  ): Future[Option[(UUID, EsItemTag)]] = {
    val esTag = EsItemTag.userScoped(userId, tag, value, Some(Instant.now()))
    (idOrSlug match {
      case Left(itemId) =>
        val userTag = esItemTaggable.makeUserItemTag(userId, itemId, tag, value)
        Future.successful(Some(itemId -> userTag))

      case Right(slug) =>
        getThingViaSearch(
          Some(userId),
          Right(slug),
          thingType,
          materializeRecommendations = false
        ).map {
          case None => None
          case Some(item) =>
            val userTag =
              esItemTaggable.makeUserItemTag(userId, item.id, tag, value)

            Some(item.id -> userTag)
        }
    }).flatMap {
      case None => Future.successful(None)
      case Some((itemId, userTag)) =>
        itemUpdater
          .upsertItemTag(itemId, esTag, Some(userId -> userTag))
          .map(_ => Some(itemId -> esTag))
    }
  }

  def removeTagFromThing(
    userId: String,
    idOrSlug: Either[UUID, Slug],
    thingType: Option[ItemType],
    tag: UserThingTagType
  ): Future[Option[(UUID, EsItemTag)]] = {
    val esTag =
      EsItemTag.userScoped[String](userId, tag, None, Some(Instant.now()))
    // Create a no value tag because we only need the unique key to know how to delete it
    val userTag = EsUserItemTag.noValue(tag)

    idOrSlug match {
      case Left(value) =>
        itemUpdater
          .removeItemTag(value, esTag, Some(userId -> userTag))
          .map(_ => Some(value -> esTag))

      case Right(value) =>
        getThingViaSearch(
          Some(userId),
          Right(value),
          thingType,
          materializeRecommendations = false
        ).flatMap {
          case None => Future.successful(None)
          case Some(value) =>
            itemUpdater
              .removeItemTag(value.id, esTag, Some(userId -> userTag))
              .map(_ => Some(value.id -> esTag))
        }
    }
  }

  def getPersonViaSearch(
    idOrSlug: String,
    materializeCredits: Boolean,
    creditsLimit: Int
  ): Future[Option[(EsPerson, ElasticsearchItemsResponse)]] = {
    personLookup.lookupPerson(
      HasThingIdOrSlug.parse(idOrSlug),
      materializeCredits,
      Some(creditsLimit)
    )
  }

  def getPeopleViaSearch(idOrSlugs: List[String]): Future[List[EsPerson]] = {
    personLookup
      .lookupPeople(idOrSlugs.map(HasThingIdOrSlug.parseIdOrSlug))
      .map(people => {
        people.groupBy(_.id).mapValues(_.head).values.toList
      })
  }

  def getPersonCredits(
    idOrSlug: String,
    request: PersonCreditsRequest
  ): Future[ElasticsearchItemsResponse] = {
    search(
      ItemSearchRequest(
        genres = request.genres,
        networks = request.networks,
        itemTypes = request.itemTypes,
        sortMode = request.sortMode,
        limit = request.limit,
        bookmark = request.bookmark,
        releaseYear = request.releaseYear,
        peopleCredits = Some(
          PeopleCreditsFilter(
            if (request.creditTypes
                  .forall(_.contains(PersonAssociationType.Cast))) Seq(idOrSlug)
            else Seq(),
            if (request.creditTypes
                  .forall(_.contains(PersonAssociationType.Crew))) Seq(idOrSlug)
            else Seq(),
            BinaryOperator.Or
          )
        ),
        imdbRating = None
      )
    )
  }

  def fullTextSearch(
    query: String,
    request: ItemSearchRequest
  ): Future[ElasticsearchItemsResponse] = {
    toItemSearchParams(request)
      .map(_.copy(titleSearch = Some(query)))
      .flatMap(itemSearch.fullTextSearch)
  }

  def fullTextSearchPeople(
    query: String,
    request: ItemSearchRequest
  ): Future[ElasticsearchPeopleResponse] = {
    toItemSearchParams(request)
      .map(_.copy(titleSearch = Some(query)))
      .flatMap(personLookup.fullTextSearch)
  }

  def search(request: ItemSearchRequest): Future[ElasticsearchItemsResponse] = {
    toItemSearchParams(request).flatMap(itemSearch.searchItems)
  }

  def toItemSearchParams(
    request: ItemSearchRequest
  ): Future[ItemSearchParams] = {
    val genresFut = resolveGenres(request)
    val networksFut = resolveNetworks(request)

    for {
      filterGenres <- genresFut
      filterNetworks <- networksFut
    } yield {
      ItemSearchParams(
        genres = Some(filterGenres).filter(_.nonEmpty),
        networks = Some(filterNetworks).filter(_.nonEmpty),
        itemTypes = request.itemTypes,
        releaseYear = request.releaseYear,
        peopleCredits = request.peopleCredits.flatMap(_.toPeopleCreditSearch),
        imdbRating = request.imdbRating,
        titleSearch = None,
        sortMode = request.sortMode,
        limit = request.limit,
        bookmark = request.bookmark,
        forList = None
      )
    }
  }

  private def resolveGenres(itemSearchRequest: ItemSearchRequest) = {
    if (itemSearchRequest.genres.exists(_.nonEmpty)) {
      genreCache
        .get()
        .map(cachedGenres => {
          itemSearchRequest.genres.get.map(HasGenreIdOrSlug.parse).flatMap {
            case Left(id)    => cachedGenres.find(_.id == id)
            case Right(slug) => cachedGenres.find(_.slug == slug)
          }
        })
    } else {
      Future.successful(Set.empty[StoredGenre])
    }
  }

  private def resolveNetworks(itemSearchRequest: ItemSearchRequest) = {
    if (itemSearchRequest.networks.exists(_.nonEmpty)) {
      networkCache
        .getAllNetworks()
        .map(cachedNetworks => {
          cachedNetworks
            .filter(
              network =>
                itemSearchRequest.networks.get.contains(network.slug.value)
            )
            .toSet
        })
    } else {
      Future.successful(Set.empty[StoredNetwork])
    }
  }
}

case class ItemSearchRequest(
  genres: Option[Set[String]],
  networks: Option[Set[String]],
  itemTypes: Option[Set[ItemType]],
  sortMode: SortMode,
  limit: Int,
  bookmark: Option[Bookmark],
  releaseYear: Option[OpenDateRange],
  peopleCredits: Option[PeopleCreditsFilter],
  imdbRating: Option[ClosedNumericRange[Double]])

case class PersonCreditsRequest(
  genres: Option[Set[String]],
  networks: Option[Set[String]],
  itemTypes: Option[Set[ItemType]],
  creditTypes: Option[Set[PersonAssociationType]],
  sortMode: SortMode,
  limit: Int,
  bookmark: Option[Bookmark],
  releaseYear: Option[OpenDateRange])
