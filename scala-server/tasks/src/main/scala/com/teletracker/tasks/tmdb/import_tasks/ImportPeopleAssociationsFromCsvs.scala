package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.db.model.{PersonAssociationType, PersonThing}
import com.teletracker.common.model.tmdb.{CastMember, CrewMember}
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

/*
  Queries used to generate inputs:

  To import the output:
  \copy person_things from 'movie_crew_mappings.tsv';
  \copy person_things from 'show_crew_mappings.tsv';
 */
class ImportPeopleAssociationsFromCsvs extends TeletrackerTaskWithDefaultArgs {
  override def runInternal(args: Args): Unit = {
    val peopleInput = args.value[URI]("peopleMapping").get
    val thingInput = args.value[URI]("thingMapping").get
    val offset = args.valueOrDefault[Int]("offset", 0)
    val limit = args.valueOrDefault("limit", -1)
    val memberType = args.valueOrDefault("memberType", "crew")
    val separator = args.valueOrDefault("separator", "tab")

    val actualsep = if (separator == "tab") '\t' else separator.head

    val peopleByTmdbId = loadPersonMappings(peopleInput, actualsep)

    val src = Source.fromURI(thingInput)

    val f = new File("output.tsv")
    val os = new PrintStream(new BufferedOutputStream(new FileOutputStream(f)))

    try {
      src
        .getLines()
        .drop(offset)
        .safeTake(limit)
        .foreach(line => {
          val Array(id, castJson) = line.split(actualsep.toString, 2)
          val thingId = UUID.fromString(id)
          val sanitizedCastJson =
            castJson.replaceAll("^\"|\"$", "").replaceAll("\"\"", "\"")

          parse(sanitizedCastJson)
            .foreach(json => {
              if (memberType == "crew") {
                val crew = json
                  .as[List[CrewMember]]
                crew
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
                              PersonAssociationType.Crew,
                              None,
                              None,
                              member.department,
                              member.job
                            )

                            set += (personId -> thingId)

                            synchronized(os.println(personThingToCsv(pt)))
                          }
                        })
                    })
                  })
              } else if (memberType == "cast") {
                json
                  .as[List[CastMember]]
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
                              member.order,
                              None,
                              None
                            )

                            set += (personId -> thingId)

                            synchronized(os.println(personThingToCsv(pt)))
                          }
                        })
                    })
                  })
              } else {
                throw new IllegalArgumentException
              }
            })
        })
    } finally {
      src.close()
    }
  }

  private def loadPersonMappings(
    input: URI,
    separator: Char
  ) = {
    val src = Source.fromURI(input)

    try {
      src.getLines().foldLeft(Map[String, UUID]()) {
        case (acc, line) =>
          val Array(id, tmdbId) = line.split(separator.toString, 2)
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
      personThing.characterName.getOrElse("\\N"),
      personThing.order.getOrElse("\\N"),
      personThing.department.getOrElse("\\N"),
      personThing.job.getOrElse("\\N")
    ).map(_.toString)
      .map(
        _.replaceAllLiterally("\\\"", "'")
          .replaceAllLiterally("\"", "\"\"")
      )
//      .map("\"" + _ + "\"")
      .mkString("\t")
  }
}
