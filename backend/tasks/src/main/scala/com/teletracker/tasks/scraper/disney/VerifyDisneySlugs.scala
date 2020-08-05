package com.teletracker.tasks.scraper.disney

import com.teletracker.common.model.scraping.disney.DisneyPlusCatalogItem
import com.teletracker.common.tasks.TypedTeletrackerTask
import com.teletracker.common.tasks.args.GenArgParser
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.util.{SourceRetriever, SourceUtils}
import io.circe.generic.JsonCodec
import javax.inject.Inject
import java.net.URI
import java.text.Normalizer
import java.util.regex.Pattern

@JsonCodec
@GenArgParser
case class VerifyDisneySlugsArgs(input: URI)

object VerifyDisneySlugsArgs

class VerifyDisneySlugs @Inject()(sourceUtils: SourceUtils)
    extends TypedTeletrackerTask[VerifyDisneySlugsArgs] {
  val NonLatin = Pattern.compile("[^\\w\\s-]")
  val MultipleSpaces = Pattern.compile("\\s+")

  override protected def runInternal(): Unit = {
    sourceUtils
      .readAllToList[DisneyPlusCatalogItem](args.input, false)
      .flatMap(item => {
        item.url.flatMap(url => {
          val path = URI.create(url).getPath.split("/")
          val slug = path(path.length - 2)
          val ourSlug = createSlug(item.title)

          if (slug != ourSlug) {
            Some((item.title, slug, ourSlug))
          } else {
            None
          }
        })
      })
      .foreach {
        case (title, theirSlug, ourSlug) =>
          println(s"mismatch on ${title}: ${theirSlug} != ${ourSlug}")
      }
  }

  private def createSlug(t: String) =
    Normalizer
    // Replace diacritics and non-breaking spaces
      .normalize(t, Normalizer.Form.NFD)
      .replaceAll("[\u0300-\u036f\u00a0]", "")
      // Replace characters that aren't included as a hyphenated split
      .replaceAll("[\"\'’.!–]+", "")
      // Split on characters that create hyphenated split
      .split("[\\s()@&.?$+,/:-]+")
      .map(_.trim)
      .filter(_.nonEmpty)
      // Remove non-Latin characters
      .filterNot(NonLatin.matcher(_).matches)
      // Replace N spaces with 1 space
      .map(MultipleSpaces.matcher(_).replaceAll(" "))
      .map(_.toLowerCase)
      .mkString("-")
}
