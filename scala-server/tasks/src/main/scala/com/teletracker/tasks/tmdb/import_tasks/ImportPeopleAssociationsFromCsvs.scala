package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.db.model.{PersonAssociationType, PersonThing}
import com.teletracker.common.model.tmdb.CastMember
import com.teletracker.tasks.{TeletrackerTask, TeletrackerTaskWithDefaultArgs}
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}
import java.net.URI
import java.util.UUID
import scala.io.Source
import com.teletracker.common.util.Lists._
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import scala.collection.mutable

class ImportPeopleAssociationsFromCsvs extends TeletrackerTaskWithDefaultArgs {
  override def runInternal(args: Args): Unit = {
    val peopleInput = args.value[URI]("peopleMapping").get
    val thingInput = args.value[URI]("thingMapping").get
    val offset = args.valueOrDefault[Int]("offset", 0)
    val limit = args.valueOrDefault("limit", -1)

    val peopleByTmdbId = loadPersonMappings(peopleInput)

    val src = Source.fromURI(thingInput)

    val f = new File("output.csv")
    val os = new PrintStream(new BufferedOutputStream(new FileOutputStream(f)))

    try {
      src
        .getLines()
        .drop(offset)
        .safeTake(limit)
        .foreach(line => {
          val Array(id, castJson) = line.split(",", 2)
          val thingId = UUID.fromString(id)
          val sanitizedCastJson =
            castJson.replaceAll("^\"|\"$", "").replaceAll("\"\"", "\"")

          parse(sanitizedCastJson)
            .flatMap(_.as[List[CastMember]])
            .foreach(members => {
              val set = mutable.Set[(UUID, UUID)]()
              members.foreach(member => {
                peopleByTmdbId
                  .get(member.id.toString)
                  .foreach(personId => {
                    if (!set.contains(personId -> thingId)) {
                      val pt = PersonThing(
                        personId,
                        thingId,
                        PersonAssociationType.Cast,
                        member.character.orElse(member.character_name),
                        member.order
                      )

                      set += (personId -> thingId)

                      synchronized(os.println(personThingToCsv(pt)))
                    }
                  })
              })
            })
        })
    } finally {
      src.close()
    }
  }

  private def loadPersonMappings(input: URI) = {
    val src = Source.fromURI(input)

    try {
      src.getLines().foldLeft(Map[String, UUID]()) {
        case (acc, line) =>
          val Array(id, tmdbId) = line.split(",", 2)
          acc.updated(tmdbId.toString, UUID.fromString(id))
      }
    } finally {
      src.close()
    }
  }

  private def personThingToCsv(personThing: PersonThing) = {
    List(
      personThing.personId,
      personThing.thingId,
      personThing.relationType,
      personThing.characterName.getOrElse(""),
      personThing.order.getOrElse("")
    ).map(_.toString)
      .map(
        _.replaceAllLiterally("\\\"", "'")
          .replaceAllLiterally("\"", "\"\"")
      )
      .map("\"" + _ + "\"")
      .mkString(",")
  }
}
