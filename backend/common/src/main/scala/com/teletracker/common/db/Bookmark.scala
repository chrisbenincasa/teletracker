package com.teletracker.common.db

import io.circe.Codec
import java.util.Base64
import io.circe.generic.semiauto._
import io.circe._
import io.circe.parser._
import io.circe.syntax._

object Bookmark {
  implicit val bookmarkCodec: Codec[Bookmark] = deriveCodec

  def parse(bookmark: String): Bookmark = {
    val decoded = new String(Base64.getDecoder.decode(bookmark))

    decode[Bookmark](decoded) match {
      case Left(value)  => throw value
      case Right(value) => value
    }
  }

  def encode(bookmark: Bookmark): String =
    base64Encoder.encodeToString(bookmark.asJson.noSpaces.getBytes())

  def apply(
    sortMode: SortMode,
    value: String,
    refinement: Option[String]
  ): Bookmark = {
    Bookmark(sortMode.`type`, sortMode.isDesc, value, refinement)
  }

  def unapply(
    arg: Bookmark
  ): Option[(String, Boolean, String, Option[String])] = {
    Some(arg.sortType, arg.desc, arg.value, arg.valueRefinement)
  }

  final private val base64Encoder = Base64.getEncoder
}

case class Bookmark(
  sortType: String,
  desc: Boolean,
  value: String,
  valueRefinement: Option[String]) {

  def encode: String = {
    Bookmark.encode(this)
  }

  def sortMode: SortMode = {
    SortMode.fromString(sortType).direction(desc)
  }
}
