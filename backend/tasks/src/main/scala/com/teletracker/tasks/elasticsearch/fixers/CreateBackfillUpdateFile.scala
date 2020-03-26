package com.teletracker.tasks.elasticsearch.fixers

import cats.implicits._
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.EsItemAlternativeTitle
import com.teletracker.common.model.tmdb.Movie
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.elasticsearch.FileRotator
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.SourceRetriever
import com.twitter.util.StorageUnit
import io.circe.syntax._
import io.circe.{Codec, Decoder, Json}
import javax.inject.Inject
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.util.regex.Pattern
import scala.concurrent.{ExecutionContext, Future}

abstract class CreateBackfillUpdateFile[T: Decoder](
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {

  private val NonLatin = Pattern.compile("[^\\w-\\s]")

  override protected def runInternal(args: Args): Unit = {
    val input = args.valueOrThrow[URI]("input")
    val regionString = args.valueOrDefault("region", "us-west-2")
    val offset = args.valueOrDefault[Int]("offset", 0)
    val limit = args.valueOrDefault[Int]("limit", -1)
    val perFileLimit = args.valueOrDefault[Int]("perFileLimit", -1)
    val append = args.valueOrDefault[Boolean]("append", false)
    val region = Region.of(regionString)
    val gteFilter = args.value[String]("gteFilter")
    val ltFilter = args.value[String]("ltFilter")

    val s3 = S3Client.builder().region(region).build()

    val retriever = new SourceRetriever(s3)

    val fileRotator = FileRotator.everyNBytes(
      "alternative-title-updates",
      StorageUnit.fromMegabytes(100),
      Some("alternative-titles"),
      append = append
    )

    def filter(uri: URI) = {
      lazy val sanitized = uri.getPath.stripPrefix("/")
      gteFilter.forall(f => sanitized >= f) &&
      ltFilter.forall(f => sanitized < f)
    }

    retriever
      .getSourceAsyncStream(
        input,
        filter = filter,
        offset = offset,
        limit = limit
      )
      .foreachConcurrent(1)(source => {
        Future {
          try {
            new IngestJobParser()
              .asyncStream[T](source.getLines())
              .collect {
                case Right(value) if shouldKeepItem(value) => value
              }
              .safeTake(perFileLimit)
              .foreachConcurrent(8)(value => {
                val row = makeBackfillRow(value)

                Future.successful {
                  fileRotator.writeLines(
                    Seq(row.asJson.noSpaces)
                  )
                }
              })
              .await()
          } finally {
            source.close()
          }
        }
      })
      .await()

    fileRotator.finish()
  }

  protected def shouldKeepItem(item: T): Boolean

  protected def makeBackfillRow(item: T): TmdbBackfillOutputRow

  private def sanitizeType(typ: String) =
    NonLatin.matcher(typ.toLowerCase()).replaceAll("")
}

class BackfillMovieAltTitles @Inject()(
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends CreateBackfillUpdateFile[Movie](teletrackerConfig) {
  private val countries = Set("US", "GB")

  override protected def shouldKeepItem(item: Movie): Boolean = {
    item.alternative_titles
      .exists(
        _.titles.exists(t => countries.contains(t.iso_3166_1))
      )
  }

  override protected def makeBackfillRow(item: Movie): TmdbBackfillOutputRow = {
    val titles = item.alternative_titles
      .map(_.titles)
      .nested
      .filter(t => countries.contains(t.iso_3166_1))
      .value
      .getOrElse(Nil)

    val altTitles = titles
      .map(
        alt =>
          EsItemAlternativeTitle(
            country_code = alt.iso_3166_1,
            title = alt.title,
            `type` = alt.`type`
          )
      )
      .asJson

    TmdbBackfillOutputRow(
      item.id,
      Map("alternative_titles" -> altTitles).asJson
    )
  }
}

object TmdbBackfillOutputRow {
  implicit val codec: Codec[TmdbBackfillOutputRow] =
    io.circe.generic.semiauto.deriveCodec
}
case class TmdbBackfillOutputRow(
  tmdbId: Int,
  partialJson: Json)
