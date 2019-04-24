package com.chrisbenincasa.services.teletracker.util

import io.circe.Json

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

object FieldSelector {
  private def objectWasEmpty(j: Json) = j.isObject && j.asObject.exists(_.isEmpty)
  private def arrayOfObjectsWasEmpty(j: Json) = j.isArray && j.asArray.get.forall(objectWasEmpty)

  @tailrec
  private def _filter(
    j: List[(String, Json)],
    fieldMap: Map[String, Field],
    acc: List[(String, Json)] = Nil
  ): Json = {
    j match {
      case (key, value) :: xs if fieldMap.contains(key) =>
        val subfields = fieldMap(key).subfields.getOrElse(Nil)
        val ret = filter(value, subfields)

        // Include whole object if we specify it without fields
        if (objectWasEmpty(ret) || arrayOfObjectsWasEmpty(ret)) {
          _filter(xs, fieldMap, (key, value) :: acc)
        } else {
          _filter(xs, fieldMap, (key, ret) :: acc)
        }

      case _ :: xs => _filter(xs, fieldMap, acc)

      case Nil => Json.obj(acc: _*)
    }
  }

  @tailrec
  private def handleArray(arr: List[Json], fieldMap: Map[String, Field], acc: List[Json] = Nil): List[Json] = {
    arr match {
      case Nil => acc

      case j :: xs =>
        val newV = filter(j, fieldMap)
        handleArray(xs, fieldMap, newV :: acc)
    }
  }

  def filter(j: Json, fieldMap: Map[String, Field]): Json = {
    if (j.isObject) {
      _filter(j.asObject.get.toList, fieldMap)
    } else if (j.isArray) {
      val newArr = handleArray(j.asArray.get.toList, fieldMap)
      Json.arr(newArr: _*)
    } else {
      j
    }
  }

  def filter(j: Json, fields: List[Field]): Json = {
    filter(j, fields.map(f => f.name -> f).toMap)
  }
}

object Field {
  import fastparse.{parse => fparse, _}, NoWhitespace._

  object Parser {
    private def space[_: P]: P[Unit] =
      P(CharsWhileIn(" \r\n", 0))

    private def strChars[_: P]: P[Field] =
      P((CharsWhileIn("0-9a-zA-Z\\-_").! ~~ subfield.?).map(Function.tupled(Field.apply)))

    private def subfield[_: P]: P[List[Field]] =
      P("{" ~ commaSep ~ space ~ "}")

    private def commaSep[_: P]: P[List[Field]] =
      P(strChars.rep(sep = space ~ ",", min = 1).map(_.toList))

    def apply[_: P]: P[List[Field]] = commaSep
  }

  def parse(s: String): Try[List[Field]] = {
    fparse(s, Parser(_)) match {
      case Parsed.Success(v, _) => Success(v)
      case fail @ Parsed.Failure(_, _, extra) =>
        val trace = extra.trace()
        Failure(new Throwable(fail.msg + "\n" + trace.aggregateMsg))
    }
  }
}

case class Field(
  name: String,
  subfields: Option[List[Field]] = None
)

trait HasFieldsFilter {
  def fields: Option[String]
}