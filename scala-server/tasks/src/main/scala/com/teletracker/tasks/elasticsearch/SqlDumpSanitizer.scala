package com.teletracker.tasks.elasticsearch

import com.teletracker.common.db.model.{
  ObjectMetadata,
  Person,
  PersonAssociationType,
  PersonThing,
  Thing,
  ThingType
}
import com.teletracker.common.model.tmdb.{Person => TmdbPerson}
import com.teletracker.common.util.Slug
import io.circe.parser.decode
import java.time.OffsetDateTime
import java.util.UUID
import io.circe._
import io.circe.parser._
import io.circe.shapes._
import io.circe.generic.auto._
import io.circe.syntax._
import scala.util.{Failure, Success, Try}

object SqlDumpSanitizer {
  def extractPersonFromLine(
    line: String,
    idx: Option[Int]
  ): Option[Person] = {
    val Array(
      id,
      name,
      normalizedName,
      _,
      _,
      metadata,
      popularity,
      tmdbId
    ) = line.split("\t", 8)

    val sanitizedMetadata =
      sanitizeJson(metadata)

    val decoded = decode[TmdbPerson](sanitizedMetadata)

    decoded.left.foreach(err => println(s"err at line ${idx}: $err"))

    decoded
      .map(j => {
        Person(
          id = UUID.fromString(id),
          name = name,
          normalizedName = Slug.raw(normalizedName),
          metadata = Some(j.asJson),
          createdAt = OffsetDateTime.now(),
          lastUpdatedAt = OffsetDateTime.now(),
          tmdbId =
            if (tmdbId.isEmpty || tmdbId == "\\N") None
            else Some(tmdbId),
          popularity =
            if (popularity.isEmpty || popularity == "\\N") None
            else Some(popularity.toDouble)
        )
      })
      .toOption
  }

  def extractThingFromLine(
    line: String,
    idx: Option[Int]
  ) = {
    val Array(
      id,
      name,
      normalizedName,
      thingType,
      createdAt,
      lastUpdatedAt,
      metadata,
      tmdbId,
      popularity,
      genres
    ) = line.split("\t", 10)

    val sanitizedGenres =
      sanitizeSqlArray(genres)

    val sanitizedMetadata =
      sanitizeJson(metadata)

    val decoded = decode[ObjectMetadata](sanitizedMetadata)

    decoded.left.foreach(err => println(s"err at line ${idx}: $err"))

    decoded
      .map(j => {
        Thing(
          id = UUID.fromString(id),
          name = name,
          normalizedName = Slug.raw(normalizedName),
          `type` = ThingType.fromString(thingType),
          createdAt = OffsetDateTime.now(),
          lastUpdatedAt = OffsetDateTime.now(),
          metadata = Some(j),
          tmdbId =
            if (tmdbId.isEmpty || tmdbId == "\\N") None
            else Some(tmdbId),
          popularity =
            if (popularity.isEmpty || popularity == "\\N") None
            else Some(popularity.toDouble),
          genres =
            if (sanitizedGenres.isEmpty) None
            else Some(sanitizedGenres.map(_.toInt))
        )
      })
      .toOption
  }

  def extractPersonMappingFromLine(
    line: String,
    idx: Option[Int]
  ): Option[PersonThing] = {
    Try {
      val Array(
        personId,
        thingId,
        relationType,
        character,
        order,
        department,
        job
      ) = line.split("\t", 7)
      PersonThing(
        personId = UUID.fromString(personId),
        thingId = UUID.fromString(thingId),
        relationType = PersonAssociationType.fromString(relationType),
        characterName =
          if (character.isEmpty || character == "\\N") None
          else Some(character),
        order =
          if (order.isEmpty || order == "\\N") None
          else Some(order.toInt),
        department =
          if (department.isEmpty || department == "\\N") None
          else Some(department),
        job =
          if (job.isEmpty || job == "\\N") None
          else Some(job)
      )
    } match {
      case Failure(exception) =>
        println(s"line ${idx} has error ${exception.getMessage}")
        None

      case Success(value) =>
        Some(value)
    }
  }

  def sanitizeJson(string: String): String = {
    string
      .replaceAll("^\"|\"$", "")
      .replaceAll("\\\\\\\\", "\\\\")
  }

  def sanitizeSqlArray(string: String): List[String] = {
    string
      .split("=")
      .lastOption
      .map(arr => {
        arr
          .replaceAll("^\\{|\\}$", "")
          .split(",")
          .filterNot(_ == "\\N")
          .filterNot(_.isEmpty)
          .toList
      })
      .getOrElse(Nil)
  }
}
