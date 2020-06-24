package com.teletracker.common.db.dynamo.model

import com.teletracker.common.db.dynamo.util.syntax._
import com.teletracker.common.db.model.SupportedNetwork
import com.teletracker.common.util.Slug
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.util
import scala.collection.JavaConverters._
import scala.util.Try

object StoredNetwork {
  final val Prefix = "NETWORK_"

  def getNetworkId(id: Int) = s"$Prefix$id"

  def parseNetworkId(id: String): Int = {
    id.stripPrefix(Prefix).toInt
  }

  def getNetworkKey(id: Int): util.Map[String, AttributeValue] =
    Map(
      "id" -> getNetworkId(id).toAttributeValue,
      "type" -> MetadataType.NetworkType.toAttributeValue
    ).asJava

  def fromRow(row: java.util.Map[String, AttributeValue]): StoredNetwork = {
    val rowMap = row.asScala

    require(
      rowMap("type").fromAttributeValue[String] == MetadataType.NetworkType
    )

    StoredNetwork(
      id = parseNetworkId(rowMap("id").fromAttributeValue[String]),
      name = rowMap("name").fromAttributeValue[String],
      slug = Slug.raw(rowMap("slug").fromAttributeValue[String]),
      shortname = rowMap("shortname").fromAttributeValue[String],
      homepage = rowMap.get("homepage").map(_.fromAttributeValue[String]),
      origin = rowMap.get("origin").map(_.fromAttributeValue[String])
    )
  }
}

case class StoredNetwork(
  id: Int,
  name: String,
  slug: Slug,
  shortname: String,
  homepage: Option[String],
  origin: Option[String]) {

  import StoredNetwork._

  lazy val supportedNetwork: Option[SupportedNetwork] = Try(
    SupportedNetwork.fromString(slug.value)
  ).toOption

  lazy val isSupported: Boolean = supportedNetwork.isDefined

  def toDynamoItem: java.util.Map[String, AttributeValue] = {
    (Map(
      "id" -> s"${Prefix}$id".toAttributeValue,
      "type" -> MetadataType.NetworkType.toAttributeValue,
      "name" -> name.toAttributeValue,
      "slug" -> slug.value.toAttributeValue,
      "shortname" -> shortname.toAttributeValue
    ) ++ Map(
      "homepage" -> homepage.map(_.toAttributeValue),
      "origin" -> origin.map(_.toAttributeValue)
    ).collect {
      case (k, Some(v)) => k -> v
    }).asJava
  }
}
