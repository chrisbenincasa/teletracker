package com.teletracker.common.db

import com.teletracker.common.db.model.{
  DynamicListRules,
  ExternalSource,
  GenreType,
  OfferType,
  PersonAssociationType,
  PresentationType,
  ThingType,
  TrackedListRowOptions,
  UserPreferences,
  UserThingTagType
}
import com.teletracker.common.util.Slug
import com.teletracker.common.util.json.circe._
import io.circe.Json
import javax.inject.Inject

class DbImplicits @Inject()(val profile: CustomPostgresProfile) {
  import io.circe.syntax._
  import profile.api._

  implicit val externalSourceMapper = MappedColumnType
    .base[ExternalSource, String](_.getName, ExternalSource.fromString)
  implicit val offerTypeMapper =
    MappedColumnType.base[OfferType, String](_.getName, OfferType.fromString)
  implicit val presentationTypeMapper = MappedColumnType
    .base[PresentationType, String](_.getName, PresentationType.fromString)
  implicit val genreTypeMapper =
    MappedColumnType.base[GenreType, String](_.getName, GenreType.fromString)
  implicit val genreListTypeMapper =
    MappedColumnType.base[List[GenreType], List[String]](
      _.map(_.getName),
      _.map(GenreType.fromString)
    )
  implicit val thingTypeMapper =
    MappedColumnType.base[ThingType, String](_.getName, ThingType.fromString)
  implicit val actionTypeMapper = MappedColumnType
    .base[UserThingTagType, String](_.getName, UserThingTagType.fromString)
  implicit val slugTypeMapper =
    MappedColumnType.base[Slug, String](_.value, Slug.raw)
  implicit val personAssociationTypeMapper =
    MappedColumnType.base[PersonAssociationType, String](
      _.toString,
      PersonAssociationType.fromString
    )

  implicit val userPrefsToJson = MappedColumnType
    .base[UserPreferences, Json](_.asJson, _.as[UserPreferences].right.get)
  implicit val tagRulesToJson = MappedColumnType
    .base[DynamicListRules, Json](_.asJson, _.as[DynamicListRules].right.get)
  implicit val listConfigurationToJson = MappedColumnType
    .base[TrackedListRowOptions, Json](
      _.asJson,
      _.as[TrackedListRowOptions].right.get
    )
}
