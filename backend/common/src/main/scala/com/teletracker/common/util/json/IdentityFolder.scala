package com.teletracker.common.util.json

import io.circe.{Json, JsonNumber, JsonObject}
import scala.collection.JavaConverters._

object IdentityFolder extends Json.Folder[Any] {
  override def onNull: Any = null

  override def onBoolean(value: Boolean): Any = value

  override def onNumber(value: JsonNumber): Any =
    new java.lang.Double(value.toDouble)

  override def onString(value: String): Any = value

  override def onArray(value: Vector[Json]): Any =
    value.map(_.foldWith(IdentityFolder)).toList

  override def onObject(value: JsonObject): Any =
    value.toMap.map {
      case (key, value) => key -> value.foldWith(IdentityFolder)
    }
}

object IdentityJavaFolder extends Json.Folder[Any] {
  override def onNull: Any = null

  override def onBoolean(value: Boolean): Any = value

  override def onNumber(value: JsonNumber): Any =
    new java.lang.Double(value.toDouble)

  override def onString(value: String): Any = value

  override def onArray(value: Vector[Json]): Any =
    value.map(_.foldWith(IdentityJavaFolder)).toList.asJavaCollection

  override def onObject(value: JsonObject): Any =
    value.toMap.map {
      case (key, value) => key -> value.foldWith(IdentityJavaFolder)
    }.asJava
}
