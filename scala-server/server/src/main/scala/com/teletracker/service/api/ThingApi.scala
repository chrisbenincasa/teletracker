package com.teletracker.service.api

import com.teletracker.common.db.access.GenresDbAccess
import com.teletracker.common.db.dynamo.model.{StoredGenre, StoredNetwork}
import com.teletracker.common.db.model.{
  PartialThing,
  PersonAssociationType,
  ThingType,
  UserThingTagType
}
import com.teletracker.common.db.{Bookmark, Popularity, SortMode}
import com.teletracker.common.elasticsearch._
import com.teletracker.common.util._
import com.teletracker.service.api.model.Item
import javax.inject.Inject
import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ThingApi @Inject()(
  genresDbAccess: GenresDbAccess,
  genreCache: GenreCache,
  networkCache: NetworkCache,
  popularItemSearch: ItemSearch,
  itemLookup: ItemLookup,
  itemUpdater: ItemUpdater,
  personLookup: PersonLookup
)(implicit executionContext: ExecutionContext) {
  def getThingViaSearch(
    userId: Option[String],
    idOrSlug: String,
    thingType: Option[ThingType]
  ): Future[Option[Item]] = {
    getThingViaSearch(userId, HasThingIdOrSlug.parse(idOrSlug), thingType)
  }

  def getThingViaSearch(
    userId: Option[String],
    idOrSlug: Either[UUID, Slug],
    thingType: Option[ThingType]
  ): Future[Option[Item]] = {
    itemLookup
      .lookupItem(idOrSlug, thingType)
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

  def addTagToThing(
    userId: String,
    idOrSlug: Either[UUID, Slug],
    thingType: Option[ThingType],
    tag: UserThingTagType,
    value: Option[Double]
  ): Future[Option[(UUID, EsItemTag)]] = {
    val esTag = EsItemTag.userScoped(userId, tag, value, Some(Instant.now()))
    val userTag = EsUserItemTag.noValue(tag)
    idOrSlug match {
      case Left(value) =>
        itemUpdater
          .upsertItemTag(value, esTag, Some(userId -> userTag))
          .map(_ => Some(value -> esTag))

      case Right(value) =>
        getThingViaSearch(Some(userId), Right(value), thingType).flatMap {
          case None => Future.successful(None)
          case Some(value) =>
            itemUpdater
              .upsertItemTag(value.id, esTag, Some(userId -> userTag))
              .map(_ => Some(value.id -> esTag))
        }
    }
  }

  def removeTagFromThing(
    userId: String,
    idOrSlug: Either[UUID, Slug],
    thingType: Option[ThingType],
    tag: UserThingTagType
  ): Future[Option[(UUID, EsItemTag)]] = {
    val esTag = EsItemTag.userScoped(userId, tag, None, Some(Instant.now()))
    val userTag = EsUserItemTag.noValue(tag)

    idOrSlug match {
      case Left(value) =>
        itemUpdater
          .upsertItemTag(value, esTag, Some(userId -> userTag))
          .map(_ => Some(value -> esTag))

      case Right(value) =>
        getThingViaSearch(Some(userId), Right(value), thingType).flatMap {
          case None => Future.successful(None)
          case Some(value) =>
            itemUpdater
              .upsertItemTag(value.id, esTag, Some(userId -> userTag))
              .map(_ => Some(value.id -> esTag))
        }
    }
  }

  def getPersonViaSearch(
    userId: Option[String],
    idOrSlug: String
  ): Future[Option[(EsPerson, ElasticsearchItemsResponse)]] = {
    personLookup.lookupPerson(HasThingIdOrSlug.parse(idOrSlug))
  }

  def getPeopleViaSearch(
    userId: Option[String],
    idOrSlugs: List[String]
  ): Future[List[EsPerson]] = {
    personLookup
      .lookupPeople(idOrSlugs.map(HasThingIdOrSlug.parseIdOrSlug))
      .map(people => {
        people.groupBy(_.id).mapValues(_.head).values.toList
      })
  }

  def getPersonCredits(
    userId: Option[String],
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
        )
      )
    )
  }

  def getPopularByGenre(
    genreIdOrSlug: String,
    thingType: Option[ThingType],
    limit: Int = 20,
    bookmark: Option[Bookmark]
  ): Future[Option[(Seq[PartialThing], Option[Bookmark])]] = {
    genreCache
      .get()
      .flatMap(genres => {
        val genre = HasGenreIdOrSlug.parse(genreIdOrSlug) match {
          case Left(id)    => genres.find(_.id == id)
          case Right(slug) => genres.find(_.slug == slug)
        }

        // TODO: Fill in user deets
        genre.map(g => {
          genresDbAccess
            .findMostPopularThingsForGenre(g.id, thingType, limit, bookmark)
            .map(_.map(_.toPartial))
            .map(popularThings => {
              val nextBookmark = popularThings.lastOption.map(last => {
                val refinement = bookmark
                  .filter(_.value == last.popularity.getOrElse(0.0).toString)
                  .map(_ => last.id.toString)

                Bookmark(
                  Popularity(),
                  last.popularity.getOrElse(0.0).toString,
                  refinement
                )
              })

              popularThings -> nextBookmark
            })
            .map(Some(_))
        })
      }.getOrElse(Future.successful(None)))
  }

  def search(request: ItemSearchRequest): Future[ElasticsearchItemsResponse] = {
    val genresFut = if (request.genres.exists(_.nonEmpty)) {
      genreCache
        .get()
        .map(cachedGenres => {
          request.genres.get.map(HasGenreIdOrSlug.parse).flatMap {
            case Left(id)    => cachedGenres.find(_.id == id)
            case Right(slug) => cachedGenres.find(_.slug == slug)
          }
        })
    } else {
      Future.successful(Set.empty[StoredGenre])
    }

    val networksFut = if (request.networks.exists(_.nonEmpty)) {
      networkCache
        .getAllNetworks()
        .map(cachedNetworks => {
          cachedNetworks
            .filter(
              network => request.networks.get.contains(network.slug.value)
            )
            .toSet
        })
    } else {
      Future.successful(Set.empty[StoredNetwork])
    }

    val peopleCreditFilter = if (request.peopleCredits.exists(_.nonEmpty)) {
      val cast = request.peopleCredits.get.cast
        .map(HasThingIdOrSlug.parseIdOrSlug)
        .map(PersonCreditSearch(_, PersonAssociationType.Cast))
      val crew = request.peopleCredits.get.crew
        .map(HasThingIdOrSlug.parseIdOrSlug)
        .map(PersonCreditSearch(_, PersonAssociationType.Crew))

      Some(
        PeopleCreditSearch(
          cast ++ crew,
          request.peopleCredits.get.operator
        )
      )
    } else {
      None
    }

    for {
      filterGenres <- genresFut
      filterNetworks <- networksFut
      result <- popularItemSearch
        .searchItems(
          Some(filterGenres).filter(_.nonEmpty),
          Some(filterNetworks).filter(_.nonEmpty),
          request.itemTypes,
          request.sortMode,
          request.limit,
          request.bookmark,
          request.releaseYear,
          peopleCreditFilter
        )
    } yield {
      result
    }
  }
}

case class PeopleCreditsFilter(
  cast: Seq[String],
  crew: Seq[String],
  operator: BinaryOperator) {
  def nonEmpty: Boolean = cast.nonEmpty || crew.nonEmpty
}

case class ItemSearchRequest(
  genres: Option[Set[String]],
  networks: Option[Set[String]],
  itemTypes: Option[Set[ThingType]],
  sortMode: SortMode,
  limit: Int,
  bookmark: Option[Bookmark],
  releaseYear: Option[OpenDateRange],
  peopleCredits: Option[PeopleCreditsFilter])

case class PersonCreditsRequest(
  genres: Option[Set[String]],
  networks: Option[Set[String]],
  itemTypes: Option[Set[ThingType]],
  creditTypes: Option[Set[PersonAssociationType]],
  sortMode: SortMode,
  limit: Int,
  bookmark: Option[Bookmark],
  releaseYear: Option[OpenDateRange])
