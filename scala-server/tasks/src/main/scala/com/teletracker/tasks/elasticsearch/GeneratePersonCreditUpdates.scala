package com.teletracker.tasks.elasticsearch

import com.teletracker.common.db.model.ThingType
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.Slug
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.util.SourceRetriever
import io.circe.syntax._
import javax.inject.Inject
import java.net.URI
import java.util.UUID
import scala.io.Source

class GeneratePersonCreditUpdates @Inject()(sourceRetriever: SourceRetriever)
    extends TeletrackerTaskWithDefaultArgs {

  private def loopScriptSource(field: String) =
    s"""
       |for (member in params.members) {
       |  if (ctx._source.$field == null) {
       |    ctx._source.$field = [member]
       |  } else if (ctx._source.$field.find(c -> c.id.equals(member.id)) == null) {
       |    ctx._source.$field.add(member)
       |  }
       |}
       |""".stripMargin.replaceAll("\n", "")

  override protected def runInternal(args: Args): Unit = {
    val personMappingUri = args.valueOrThrow[URI]("personMappingInput")
    val itemDetailsUri = args.valueOrThrow[URI]("itemDetailsInput")
    val offset = args.valueOrDefault[Int]("offset", 0)
    val limit = args.valueOrDefault("limit", -1)
    val memberType = args.valueOrDefault("memberType", "crew")

    val fieldName = if (memberType == "crew") {
      "crew_credits"
    } else if (memberType == "cast") {
      "cast_credits"
    } else {
      throw new IllegalArgumentException("Unexpected memberType: " + memberType)
    }

    val itemDetailsById = loadItemMap(
      sourceRetriever.getSource(itemDetailsUri)
    )

    val personMappingSrc = sourceRetriever.getSource(personMappingUri)
    val writer =
      new FileRotator(
        s"person_${memberType}_update",
        RotateEveryNLines(10000),
        Some(System.getProperty("user.dir") + "/out/credit_updates")
      )

    personMappingSrc
      .getLines()
      .zipWithIndex
      .drop(offset)
      .safeTake(limit)
      .grouped(10000)
      .foreach(group => {
        group
          .flatMap {
            case (line, idx) =>
              SqlDumpSanitizer.extractPersonMappingFromLine(line, Some(idx))
          }
          .groupBy(_.personId)
          .map {
            case (personId, associations) =>
              val objects = associations
                .filter(
                  mapping => itemDetailsById.isDefinedAt(mapping.thingId)
                )
                .map(personMapping => {
                  val (title, slug, thingType) =
                    itemDetailsById(personMapping.thingId)
                  Map(
                    "id" -> Some(personMapping.thingId.asJson),
                    "title" -> Some(
                      title.asJson
                    ),
                    "slug" -> Some(
                      slug.asJson
                    ),
                    "type" -> Some(thingType.toString.asJson),
                    "character" -> personMapping.characterName.map(_.asJson),
                    "department" -> personMapping.department.map(_.asJson),
                    "job" -> personMapping.job.map(_.asJson)
                  ).collect {
                    case (key, Some(value)) => key -> value
                  }
                })

              writer.writeLines(
                Seq(
                  Map(
                    "update" -> Map(
                      "_id" -> personId.toString,
                      "_index" -> "people"
                    )
                  ).asJson.noSpaces,
                  Map(
                    "script" -> Map(
                      "source" -> loopScriptSource(fieldName).asJson,
                      "lang" -> "painless".asJson,
                      "params" -> Map(
                        "members" -> objects.asJson
                      ).asJson
                    ).asJson
                  ).asJson.noSpaces
                )
              )
          }
      })

    writer.finish()
  }

  private def loadItemMap(
    source: Source
  ): Map[UUID, (String, Slug, ThingType)] = {
    try {
      source
        .getLines()
        .zipWithIndex
        .flatMap {
          case (line, idx) =>
            SqlDumpSanitizer
              .extractThingFromLine(line, Some(idx))
              .map(thing => {
                thing.id -> (thing.name, thing.normalizedName, thing.`type`)
              })
        }
        .toMap
    } finally {
      source.close()
    }
  }
}
