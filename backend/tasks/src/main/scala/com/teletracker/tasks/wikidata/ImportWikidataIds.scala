package com.teletracker.tasks.wikidata

import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.{EsExternalId, ItemLookup}
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import com.teletracker.tasks.elasticsearch.FileRotator
import com.teletracker.tasks.util.SourceRetriever
import com.twitter.util.StorageUnit
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Codec, Json}
import javax.inject.Inject
import org.elasticsearch.script.{Script, ScriptType}
import java.net.URI
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class ImportWikidataIds @Inject()(
  sourceRetriever: SourceRetriever,
  itemSearch: ItemLookup
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {

  final private val UpdateExternalIdsScriptSource =
    """
      |if (ctx._source.external_ids == null) {
      |   ctx._source.external_ids = [params.external_id]
      |} else {
      |   ctx._source.external_ids.removeIf(id -> id.startsWith(params.external_source));
      |   ctx._source.external_ids.add(params.external_id)
      |}
      |""".stripMargin

  final private def UpdateTagsScript(tag: EsExternalId) = {
    new Script(
      ScriptType.INLINE,
      "painless",
      UpdateExternalIdsScriptSource,
      Map[String, Object](
        "external_id" -> tag.toString,
        "external_source" -> tag.provider
      ).asJava
    )
  }

  implicit val rowCodec: Codec[Row] = io.circe.generic.semiauto.deriveCodec[Row]

  override def runInternal(args: Args): Unit = {
    val input = args.value[URI]("input").get
    val offset = args.valueOrDefault("offset", 0)
    val limit = args.valueOrDefault("limit", -1)
    val perFileLimit = args.valueOrDefault("perFileLimit", -1)

    val rotator =
      FileRotator.everyNBytes(
        "wikidata_import",
        StorageUnit.fromMegabytes(90),
        None
      )

    sourceRetriever
      .getSourceStream(input)
      .drop(offset)
      .safeTake(limit)
      .foreach(source => {

        var count = 0
        source
          .getLines()
          .safeTake(perFileLimit)
          .grouped(50)
          .foreach(batch => {
            count += batch.size

            val validRows = batch
              .map(decode[Row])
              .flatMap(_.right.toOption)
              .toList

            val wikiIdByExternalId =
              validRows.groupBy(_.tmdb_id).mapValues(_.head.id)

            val idByExternalId = itemSearch
              .lookupItemsByExternalIds(
                validRows.map(
                  i => (ExternalSource.TheMovieDb, i.tmdb_id, ItemType.Movie)
                )
              )
              .await()
              .mapValues(_.id)

            for {
              ((_, externalId), itemId) <- idByExternalId
              wikiId <- wikiIdByExternalId.get(externalId).toList
            } {
              val scriptObj = Map(
                "source" -> Json.fromString(UpdateExternalIdsScriptSource),
                "lang" -> Json.fromString("painless"),
                "params" -> Map(
                  "external_id" -> EsExternalId(
                    ExternalSource.Wikidata.toString,
                    wikiId
                  ).toString,
                  "external_source" -> ExternalSource.Wikidata.toString
                ).asJson
              ).asJson

              rotator.writeLines(
                Seq(
                  Map(
                    "update" -> Map(
                      "_id" -> itemId.toString,
                      "_index" -> "items"
                    )
                  ).asJson.noSpaces,
                  Map(
                    "script" -> scriptObj
                  ).asJson.noSpaces
                )
              )
            }
          })

        println(s"Processed ${count} lines")
      })

    rotator.finish()
  }
}

case class Row(
  tmdb_id: String,
  id: String)
