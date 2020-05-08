package com.teletracker.service.api.model

import com.teletracker.common.db.model.{ItemType, PersonAssociationType}
import com.teletracker.common.elasticsearch.model.{
  EsDenormalizedItem,
  EsExternalId,
  EsItemImage,
  EsPerson,
  EsPersonCrewCredit
}
import com.teletracker.common.elasticsearch.ElasticsearchItemsResponse
import com.teletracker.common.model.Paging
import com.teletracker.common.util.Slug
import com.teletracker.common.util.json.circe._
import io.circe.{Codec, Json}
import io.circe.generic.JsonCodec
import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID

@JsonCodec
case class EnrichedPerson(
  id: UUID,
  name: String,
  normalizedName: Slug,
  createdAt: OffsetDateTime,
  lastUpdatedAt: OffsetDateTime,
  metadata: Option[Json],
  tmdbId: Option[String],
  popularity: Option[Double],
  credits: Option[List[PersonCredit]]) {

  def withCredits(credits: List[PersonCredit]): EnrichedPerson = {
    this.copy(credits = Some(credits))
  }
}

@JsonCodec
case class PersonCredit(
  id: UUID,
  name: String,
  normalizedName: Slug,
  tmdbId: Option[String],
  popularity: Option[Double],
  `type`: ItemType,
  associationType: PersonAssociationType,
  characterName: Option[String],
  releaseDate: Option[LocalDate],
  posterPath: Option[String],
  genreIds: Set[Int])

object Person {
  import io.circe.generic.semiauto._

  implicit val codec: Codec[Person] = deriveCodec

  def fromEsPerson(
    esPerson: EsPerson,
    materializedCastCredits: Option[ElasticsearchItemsResponse]
  ): Person = {
    val castCreditsList = materializedCastCredits match {
      // Retain the specified sort
      case Some(castCredits) =>
        val creditsById = esPerson.cast_credits
          .getOrElse(Nil)
          .map(credit => credit.id -> credit)
          .toMap
        val itemsNotPresent = creditsById.keySet -- castCredits.items.map(_.id)

        val sortedCredits = for {
          item <- castCredits.items
          credit <- creditsById.get(item.id).toList
        } yield {
          PersonCastMember(
            character = credit.character,
            id = credit.id,
            title = credit.title,
            slug = credit.slug,
            item = Some(Item.fromEsItem(item, Nil, Map.empty))
          )
        }

        sortedCredits

      case None =>
        esPerson.cast_credits
          .getOrElse(Nil)
          .map(credit => {
            PersonCastMember(
              character = credit.character,
              id = credit.id,
              title = credit.title,
              slug = credit.slug,
              item = None
            )
          })
    }

    val castCredits = esPerson.cast_credits.map(castCredits => {
      PagedResponse(
        data = castCreditsList,
        paging = materializedCastCredits
          .flatMap(_.bookmark)
          .map(bm => Paging(Some(bm.encode)))
      )
    })

    Person(
      adult = esPerson.adult,
      biography = esPerson.biography,
      birthday = esPerson.birthday,
      cast_credits = castCredits,
      crew_credits = esPerson.crew_credits,
      external_ids = esPerson.external_ids,
      deathday = esPerson.deathday,
      homepage = esPerson.homepage,
      id = esPerson.id,
      images = esPerson.images,
      name = esPerson.name,
      place_of_birth = esPerson.place_of_birth,
      popularity = esPerson.popularity,
      slug = esPerson.slug,
      known_for = esPerson.known_for
    )
  }
}

case class Person(
  adult: Option[Boolean],
  biography: Option[String],
  birthday: Option[LocalDate],
  cast_credits: Option[PagedResponse[PersonCastMember]],
  crew_credits: Option[List[EsPersonCrewCredit]],
  external_ids: Option[List[EsExternalId]],
  deathday: Option[LocalDate],
  homepage: Option[String],
  id: UUID,
  images: Option[List[EsItemImage]],
  name: Option[String],
  place_of_birth: Option[String],
  popularity: Option[Double],
  slug: Option[Slug],
  known_for: Option[List[EsDenormalizedItem]])

@JsonCodec
case class PersonCastMember(
  character: Option[String],
  id: UUID,
  title: String,
  slug: Option[Slug],
  item: Option[Item])
