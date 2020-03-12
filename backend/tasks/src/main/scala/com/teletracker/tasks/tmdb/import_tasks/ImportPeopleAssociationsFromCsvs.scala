package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.db.model.{
  PersonAssociationType,
  PersonThing,
  ThingType
}
import com.teletracker.common.model.tmdb.{CastMember, CrewMember}
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}
import java.net.URI
import java.util.UUID
import scala.io.Source
import com.teletracker.common.util.Lists._
import com.teletracker.tasks.elasticsearch.SqlDumpSanitizer
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import scala.collection.mutable

/*
  Queries used to generate inputs:

  To generate the inputs for these jobs:
  \copy (select * from things) to '/Users/christianbenincasa/Desktop/things_dump_full.tsv';

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
        .zipWithIndex
        .foreach {
          case (line, idx) =>
            val thingOpt = SqlDumpSanitizer
              .extractThingFromLine(line, Some(idx))

            thingOpt.foreach(thing => {
              val crew = thing.metadata.flatMap(meta => {
                thing.`type` match {
                  case ThingType.Movie =>
                    meta.tmdbMovie.flatMap(_.credits.flatMap(_.crew))
                  case ThingType.Show =>
                    meta.tmdbShow.flatMap(_.credits.flatMap(_.crew))
                  case ThingType.Person => None
                }
              })

              val cast = thing.metadata.flatMap(meta => {
                thing.`type` match {
                  case ThingType.Movie =>
                    meta.tmdbMovie.flatMap(_.credits.flatMap(_.cast))
                  case ThingType.Show =>
                    meta.tmdbShow.flatMap(_.credits.flatMap(_.cast))
                  case ThingType.Person => None
                }
              })

              memberType match {
                case "crew" =>
                  crew
                    .foreach(members => {
                      val set = mutable.Set[(UUID, UUID)]()
                      members.foreach(member => {
                        peopleByTmdbId
                          .get(member.id.toString)
                          .foreach(personId => {
                            if (!set.contains(personId -> thing.id)) {
                              val pt = PersonThing(
                                personId,
                                thing.id,
                                PersonAssociationType.Crew,
                                None,
                                None,
                                member.department,
                                member.job
                              )

                              set += (personId -> thing.id)

                              synchronized(os.println(personThingToCsv(pt)))
                            }
                          })
                      })
                    })

                case "cast" =>
                  cast.foreach(members => {
                    val set = mutable.Set[(UUID, UUID)]()
                    members.foreach(member => {
                      peopleByTmdbId
                        .get(member.id.toString)
                        .foreach(personId => {
                          if (!set.contains(personId -> thing.id)) {
                            val pt = PersonThing(
                              personId,
                              thing.id,
                              PersonAssociationType.Cast,
                              member.character.orElse(member.character_name),
                              member.order,
                              None,
                              None
                            )

                            set += (personId -> thing.id)

                            synchronized(os.println(personThingToCsv(pt)))
                          }
                        })
                    })
                  })

                case _ => throw new IllegalArgumentException
              }
            })
        }
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
      personThing.characterName.map(_.trim).filter(_.nonEmpty).getOrElse("\\N"),
      personThing.order.getOrElse("\\N"),
      personThing.department.map(_.trim).filter(_.nonEmpty).getOrElse("\\N"),
      personThing.job.map(_.trim).filter(_.nonEmpty).getOrElse("\\N")
    ).map(_.toString)
      .map(
        _.replaceAllLiterally("\\\"", "'")
          .replaceAllLiterally("\"", "\"\"")
      )
//      .map("\"" + _ + "\"")
      .mkString("\t")
  }
}
