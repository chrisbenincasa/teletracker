package com.teletracker.common.util

import com.fasterxml.jackson.annotation.{JsonCreator, JsonValue}
import java.text.Normalizer
import java.text.Normalizer.Form
import java.util.{Locale, Objects}
import java.util.regex.Pattern
import scala.util.{Success, Try}

object Slug {
  final val Separator = "-"

  private val NonLatin = Pattern.compile("[^\\w-]")
  private val Whitespace = Pattern.compile("[\\s]")
  private val NormalizerFunc = Seq[String => String](
    _.replaceAll("&", "and"),
    _.replaceAll("-", ""),
    _.replaceAll("\\s{2,}", " "),
    Whitespace.matcher(_).replaceAll("-"),
    _.replaceAll("--", "-"), // This shouldn't happen... but we'll check
    Normalizer.normalize(_, Form.NFD),
    NonLatin.matcher(_).replaceAll(""),
    _.toLowerCase(Locale.ENGLISH)
  ).reduce(_.andThen(_))

  def apply(
    input: String,
    year: Int
  ): Slug = {
    val slug = NormalizerFunc(input)
    raw(s"$slug$Separator$year")
  }

  def apply(
    input: String,
    year: Option[Int]
  ): Slug = {
    year.map(apply(input, _)).getOrElse(forString(input))
  }

  def forString(input: String): Slug = new Slug(NormalizerFunc(input))

  def raw(input: String): Slug = new Slug(input)

  def unapply(arg: Slug): Option[(String, Option[Int])] = {
    val slugString = arg.toString
    val idx = slugString.lastIndexOf(Separator)
    if (idx > -1) {
      val (left, right) = slugString.splitAt(idx)
      Some(
        Try(right.stripPrefix(Separator).toInt)
          .map(year => left -> Some(year))
          .getOrElse(slugString -> None)
      )
    } else {
      Some(slugString -> None)
    }
  }

  def findNext(
    targetSlug: Slug,
    existingSlugs: List[Slug]
  ): Try[Slug] = {
    if (existingSlugs.isEmpty) {
      Success(targetSlug)
    } else {
      Try {
        val slugIndexes = existingSlugs
          .map(slug => {
            if (!slug.value.startsWith(targetSlug.value)) {
              throw new IllegalArgumentException(
                s"Given slug (${slug}) does not contain prefix ${targetSlug}"
              )
            }

            slug.value.stripPrefix(targetSlug.value).stripPrefix(Slug.Separator)
          })
          .flatMap {
            case ""  => Some(0)
            case str => Try(str.toInt).toOption
          }

        slugIndexes match {
          case Nil => targetSlug
          case xs =>
            val nextSlugIndex = xs.max + 1
            targetSlug.addSuffix(nextSlugIndex.toString)
        }

      }
    }
  }
}

//@JsonCreator
final class Slug(val value: String) extends AnyVal {
  @JsonValue
  override def toString: String = value

  def addSuffix(suffix: String) = new Slug(value + Slug.Separator + suffix)
}
