package com.teletracker.common.db.dynamo.model

import com.teletracker.common.db.dynamo.util.syntax._
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import scala.collection.JavaConverters._
import java.util.UUID

object StoredListAlias {
  def primaryKey(alias: String) = {
    Map("alias" -> alias.toAttributeValue).asJava
  }
}

case class StoredListAlias(
  alias: String,
  id: UUID) {
  def toDynamoItem: java.util.Map[String, AttributeValue] =
    Map("alias" -> alias.toAttributeValue, "id" -> id.toAttributeValue).asJava
}
