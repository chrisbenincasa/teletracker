package com.teletracker.common.db.model

import com.teletracker.common.util.{Field, FieldSelector, Slug}
import io.circe.Json
import java.time.OffsetDateTime
import io.circe._
import io.circe.shapes._
import io.circe.generic.auto._
import io.circe.generic.semiauto._
import io.circe.syntax._
import java.util.UUID

sealed trait ThingLike {
  def id: UUID
  def name: String
  def normalizedName: Slug
  def `type`: ThingType
  def createdAt: OffsetDateTime
  def lastUpdatedAt: OffsetDateTime
  def metadata: Option[Json]
  def tmdbId: Option[String]
  def popularity: Option[Double]

  type This <: ThingLike
  def self: This

  def withMetadata(json: Option[Json]): This
  def withGenres(genres: Set[Int]): This

  def selectFields(
    fieldsOpt: Option[List[Field]],
    defaultFields: List[Field]
  ): This = {
    fieldsOpt
      .map(fields => {
        metadata match {
          case Some(metadata) =>
            withMetadata(
              json = Some(
                FieldSelector
                  .filter(metadata, fields ::: defaultFields)
              )
            )

          case None => self
        }
      })
      .getOrElse(self)
  }
}

case class Person(
  id: UUID,
  name: String,
  normalizedName: Slug,
  createdAt: OffsetDateTime,
  lastUpdatedAt: OffsetDateTime,
  metadata: Option[Json],
  tmdbId: Option[String],
  popularity: Option[Double])
    extends ThingLike {

  override type This = Person

  override def self: Person = this

  override def withMetadata(json: Option[Json]): Person = copy(metadata = json)

  override def withGenres(genres: Set[Int]): Person = this

  override def `type`: ThingType = ThingType.Person
}

case class ThingRaw(
  id: UUID,
  name: String,
  normalizedName: Slug,
  `type`: ThingType,
  createdAt: OffsetDateTime,
  lastUpdatedAt: OffsetDateTime,
  metadata: Option[Json],
  tmdbId: Option[String] = None,
  popularity: Option[Double],
  genres: Option[List[Int]])
    extends ThingLike {

  override type This = ThingRaw

  override def self: ThingRaw = this

  override def withMetadata(json: Option[Json]): ThingRaw =
    copy(metadata = json)

  override def withGenres(genres: Set[Int]): ThingRaw =
    copy(genres = if (genres.isEmpty) None else Some(genres.toList))

  def toPartial: PartialThing = {
    PartialThing(
      id,
      Some(name),
      Some(normalizedName),
      Some(`type`),
      Some(createdAt),
      Some(lastUpdatedAt),
      popularity,
      metadata,
      genreIds = genres.map(_.toSet)
    )
  }
}
