package com.teletracker.tasks.scraper

import com.teletracker.tasks.scraper.IngestJobParser.{
  AllJson,
  JsonPerLine,
  ParseMode
}
import io.circe.{Decoder, ParsingFailure}
import io.circe.parser.{parse => parseJson}

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
        parseJson(lines.mkString("")).flatMap(_.as[List[T]])
      case JsonPerLine =>
        lines.zipWithIndex
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
}
