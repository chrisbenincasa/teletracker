package com.teletracker.tasks.scraper

import com.teletracker.common.util.AsyncStream
import com.teletracker.tasks.scraper.IngestJobParser.{
  AllJson,
  JsonPerLine,
  ParseMode
}
//import io.circe.{Decoder, ParsingFailure}
import io.circe.parser.{parse => parseJson, decode}
import io.circe._
import io.circe.syntax._
import cats.syntax.all._

object IngestJobParser {
  sealed trait ParseMode
  case object JsonPerLine extends ParseMode
  case object AllJson extends ParseMode
}

class IngestJobParser {
  def parse[T](
    lines: Iterator[String],
    parseMode: ParseMode
  )(implicit decoder: Decoder[T]
  ): Either[Exception, List[T]] = {
    parseMode match {
      case AllJson =>
        parseJson(lines.mkString(""))
          .flatMap(_.as[List[T]])
          .leftMap(x => new Exception(x.show))
      case JsonPerLine =>
        lines
          .flatMap(sanitizeLine)
          .zipWithIndex
          .filter(_._1.nonEmpty)
          .map { case (in, idx) => in.trim -> idx }
          .map {
            case (in, idx) =>
              parseJson(in).left.map(failure => {
                new ParsingFailure(s"$failure, $idx: $in", failure)
              })
          }
          .map(_.flatMap(_.as[T]))
          .foldLeft(
            Right(Nil): Either[Exception, List[T]]
          ) {
            case (_, e @ Left(_)) =>
              e.asInstanceOf[Either[Exception, List[T]]]
            case (e @ Left(_), _)       => e
            case (Right(acc), Right(n)) => Right(acc :+ n)
          }
    }
  }

  def stream[T](
    lines: Iterator[String]
  )(implicit decoder: Decoder[T]
  ): Stream[Either[Exception, T]] = {
    lines
      .flatMap(sanitizeLine)
      .toStream
      .zipWithIndex
      .filter(_._1.nonEmpty)
      .map { case (in, idx) => in.trim -> idx }
      .map {
        case (in, idx) =>
          decode[T](in).left.map(failure => {
            new RuntimeException(s"$failure, $idx: $in", failure)
          })
      }
  }

  def asyncStream[T](
    lines: Iterator[String]
  )(implicit decoder: Decoder[T]
  ): AsyncStream[Either[Exception, T]] =
    AsyncStream.fromStream(stream[T](lines))

  private def sanitizeLine(line: String): List[String] = {
    if (line.contains("}{")) {
      val left :: right :: Nil = line.split("}\\{", 2).toList
      (left + "}") :: ("{" + right) :: Nil
    } else {
      List(line)
    }
  }
}
