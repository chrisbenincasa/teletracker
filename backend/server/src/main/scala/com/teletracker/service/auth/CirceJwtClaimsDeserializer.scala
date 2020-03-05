package com.teletracker.service.auth

import io.circe.{Json, JsonNumber, JsonObject}
import io.jsonwebtoken.io.Deserializer
import scala.collection.JavaConverters._

object CirceJwtClaimsDeserializer
    extends Deserializer[java.util.Map[String, _]] {
  override def deserialize(bytes: Array[Byte]): java.util.Map[String, _] = {
    val rawMap = io.circe.parser
      .decode[Map[String, Json]](new String(bytes))
      .right
      .get

    rawMap
      .map {
        case (key, value) => key -> value.foldWith(IdentityFolder)
      }
      .asInstanceOf[Map[String, Any]]
      .asJava
  }

  private object IdentityFolder extends Json.Folder[Any] {
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
}
