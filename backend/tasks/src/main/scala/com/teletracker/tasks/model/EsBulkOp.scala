package com.teletracker.tasks.model

import io.circe.generic.JsonCodec
import io.circe.syntax._
import java.util.UUID

trait EsBulkOp {
  def typ: String
  def lines: Seq[String]
}

@JsonCodec
case class EsBulkIndexRaw(index: EsBulkIndexDoc)

@JsonCodec
case class EsBulkIndexDoc(
  _index: String,
  _id: String)

@JsonCodec
case class EsBulkUpdateRaw(update: EsBulkIndexDoc)

case class EsBulkIndex(
  index: String,
  id: UUID,
  doc: String)
    extends EsBulkOp {
  override def typ: String = "index"

  override def lines: Seq[String] = Seq(
    Map(typ -> Map("_index" -> index, "_id" -> id.toString).asJson).asJson.noSpaces,
    doc
  )
}

@JsonCodec
case class EsBulkUpdate(
  index: String,
  id: UUID,
  update: String)
    extends EsBulkOp {
  override def typ: String = "update"

  override def lines: Seq[String] = Seq(
    Map(typ -> Map("_index" -> index, "_id" -> id.toString).asJson).asJson.noSpaces,
    update
  )
}

@JsonCodec
case class EsBulkDelete(
  index: String,
  id: UUID)
    extends EsBulkOp {
  override def typ: String = "delete"

  override def lines: Seq[String] =
    Seq(
      Map(typ -> Map("_index" -> index, "_id" -> id.toString).asJson).asJson.noSpaces
    )
}
