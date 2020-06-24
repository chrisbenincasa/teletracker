package com.teletracker.common.db.dynamo.model

import com.teletracker.common.db.dynamo.util.syntax._
import com.teletracker.common.db.model.GenreType
import com.teletracker.common.util.Slug
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.util
import scala.collection.JavaConverters._

object StoredGenre {
  final val Prefix = "GENRE_"

  def getGenreId(id: Int) = s"$Prefix$id"

  def parseGenreId(id: String): Int = {
    id.stripPrefix(Prefix).toInt
  }

  def getKey(id: Int): util.Map[String, AttributeValue] = {
    Map(
      "id" -> getGenreId(id).toAttributeValue,
      "type" -> MetadataType.NetworkType.toAttributeValue
    ).asJava
  }

  def fromRow(row: java.util.Map[String, AttributeValue]): StoredGenre = {
    val rowMap = row.asScala

    require(rowMap("type").fromAttributeValue[String] == MetadataType.GenreType)

    StoredGenre(
      id = parseGenreId(rowMap("id").fromAttributeValue[String]),
      name = rowMap("name").fromAttributeValue[String],
      slug = Slug.raw(rowMap("slug").fromAttributeValue[String]),
      genreTypes = rowMap("genreTypes")
        .fromAttributeValue[Set[String]]
        .map(GenreType.fromString)
    )
  }
}

case class StoredGenre(
  id: Int,
  name: String,
  slug: Slug,
  genreTypes: Set[GenreType]) {

  def toDynamoItem: java.util.Map[String, AttributeValue] = {
    Map(
      "id" -> s"${StoredGenre.Prefix}$id".toAttributeValue,
      "type" -> MetadataType.GenreType.toAttributeValue,
      "name" -> name.toAttributeValue,
      "slug" -> slug.value.toAttributeValue,
      "genreTypes" -> genreTypes.map(_.toString).toAttributeValue
    ).asJava
  }
}
