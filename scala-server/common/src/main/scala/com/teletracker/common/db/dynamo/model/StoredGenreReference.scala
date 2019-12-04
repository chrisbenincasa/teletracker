package com.teletracker.common.db.dynamo.model

import com.teletracker.common.db.dynamo.util.syntax._
import com.teletracker.common.db.model.ExternalSource
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import scala.collection.JavaConverters._

object StoredGenreReference {
  final val Prefix = "GENRE_REFERENCE_"

  def getGenreReferenceId(
    externalSource: ExternalSource,
    externalId: String
  ) =
    s"${StoredGenreReference.Prefix}${externalSource}_$externalId"

  def parseGenreReferenceId(id: String): (String, String) = {
    val Array(externalSource, externalId) = id.stripPrefix(Prefix).split("_", 2)
    externalSource -> externalId
  }

  def getKey(
    externalSource: ExternalSource,
    externalId: String
  ) = {
    Map(
      "id" -> StoredGenreReference
        .getGenreReferenceId(externalSource, externalId)
        .toAttributeValue,
      "type" -> MetadataType.GenreReferenceType.toAttributeValue
    ).asJava
  }

  def fromRow(
    row: java.util.Map[String, AttributeValue]
  ): StoredGenreReference = {
    val rowMap = row.asScala

    require(rowMap("type").valueAs[String] == MetadataType.GenreReferenceType)

    val (externalSource, externalId) = parseGenreReferenceId(
      rowMap("id").valueAs[String]
    )

    StoredGenreReference(
      externalSource = ExternalSource.fromString(externalSource),
      externalId = externalId,
      genreId = rowMap("genreId").valueAs[Int]
    )
  }
}

case class StoredGenreReference(
  externalSource: ExternalSource,
  externalId: String,
  genreId: Int) {
  def toDynamoItem: java.util.Map[String, AttributeValue] = {
    Map(
      "id" -> StoredGenreReference
        .getGenreReferenceId(externalSource, externalId)
        .toAttributeValue,
      "type" -> MetadataType.GenreReferenceType.toAttributeValue,
      "genreId" -> genreId.toAttributeValue
    ).asJava
  }
}
