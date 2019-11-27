package com.teletracker.tasks.elasticsearch

import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import com.teletracker.common.util.Lists._
import com.twitter.util.StorageUnit
import io.circe._
import io.circe.syntax._
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.UUID
import scala.io.Source
import scala.util.{Failure, Success, Try}

class GenerateItemCreditUpdates @Inject()(sourceRetriever: SourceRetriever)
    extends TeletrackerTaskWithDefaultArgs {
  private val logger = LoggerFactory.getLogger(getClass)

  private def scriptSource(field: String) =
    s"""
      |if (ctx._source.$field == null) {
      |  ctx._source.$field = [params.member]
      |} else if (ctx._source.$field.find(c -> c.id.equals(params.member.id)) == null) {
      |  ctx._source.$field.add(params.member)
      |}
      |""".stripMargin.replaceAll("\n", "")

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
    val personByName = args.valueOrThrow[URI]("personDetailsById")
    val offset = args.valueOrDefault[Int]("offset", 0)
    val limit = args.valueOrDefault("limit", -1)
    val memberType = args.valueOrDefault("memberType", "crew")

    val personDetailsById = loadPeopleMap(
      sourceRetriever.getSource(personByName)
    )

    val personMappingSrc = sourceRetriever.getSource(personMappingUri)
    val writer =
      new FileRotator(
        s"${memberType}_update",
        RotateEveryNLines(10000),
        Some(System.getProperty("user.dir") + "/out/credit_updates")
      )

    try {
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
            .groupBy(_.thingId)
            .map {
              case (thingId, associations) =>
                val objects = associations
                  .filter(
                    mapping => personDetailsById.isDefinedAt(mapping.personId)
                  )
                  .map(personMapping => {
                    Map(
                      "id" -> Some(personMapping.personId.asJson),
                      "name" -> Some(
                        personDetailsById(personMapping.personId)._1.asJson
                      ),
                      "slug" -> Some(
                        personDetailsById(personMapping.personId)._2.asJson
                      ),
                      "order" -> personMapping.order.map(_.asJson),
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
                        "_id" -> thingId.toString,
                        "_index" -> "items"
                      )
                    ).asJson.noSpaces,
                    Map(
                      "script" -> Map(
                        "source" -> loopScriptSource(memberType).asJson,
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
    } finally {
      personMappingSrc.close()
    }

    writer.finish()
  }

  private def loadPeopleMap(source: Source) = {
    try {
      source
        .getLines()
        .flatMap(line => {
          Try {
            val Array(
              id,
              name,
              slug
            ) = line.split("\t", 3)

            UUID.fromString(id) -> (name, slug)
          } match {
            case Failure(exception) => None
            case Success(value)     => Some(value)
          }
        })
        .toMap
    } finally {
      source.close()
    }

  }
}
