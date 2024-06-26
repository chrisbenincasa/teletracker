package com.teletracker.common.db.dynamo.model

import com.teletracker.common.db.dynamo.util.syntax._
import com.teletracker.common.db.model.ExternalSource
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import scala.collection.JavaConverters._

object StoredNetworkReference {
  final val Prefix = "NETWORK_REFERENCE_"

  def getNetworkReferenceId(
    externalSource: ExternalSource,
    externalId: String
  ) =
    s"${StoredNetworkReference.Prefix}${externalSource}_$externalId"

  def parseNetworkReferenceId(id: String): (String, String) = {
    val Array(externalSource, externalId) = id.stripPrefix(Prefix).split("_", 2)
    externalSource -> externalId
  }

  def getKey(
    externalSource: ExternalSource,
    externalId: String
  ) = {
    Map(
      "id" -> StoredNetworkReference
        .getNetworkReferenceId(externalSource, externalId)
        .toAttributeValue,
      "type" -> MetadataType.NetworkReferenceType.toAttributeValue
    ).asJava
  }

  def fromRow(
    row: java.util.Map[String, AttributeValue]
  ): StoredNetworkReference = {
    val rowMap = row.asScala

    require(
      rowMap("type")
        .fromAttributeValue[String] == MetadataType.NetworkReferenceType
    )

    val (externalSource, externalId) = parseNetworkReferenceId(
      rowMap("id").fromAttributeValue[String]
    )

    StoredNetworkReference(
      externalSource = ExternalSource.fromString(externalSource),
      externalId = externalId,
      networkId = rowMap("networkId").fromAttributeValue[Int]
    )
  }
}

case class StoredNetworkReference(
  externalSource: ExternalSource,
  externalId: String,
  networkId: Int) {
  def toDynamoItem: java.util.Map[String, AttributeValue] = {
    Map(
      "id" -> StoredNetworkReference
        .getNetworkReferenceId(externalSource, externalId)
        .toAttributeValue,
      "type" -> MetadataType.NetworkReferenceType.toAttributeValue,
      "networkId" -> networkId.toAttributeValue
    ).asJava
  }
}
