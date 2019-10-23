package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.model.Thingable
import com.teletracker.common.model.tmdb.Person
import com.teletracker.common.util.Lists._
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.util.SourceRetriever
import io.circe.parser.parse
import javax.inject.Inject
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.s3.S3Client
import java.io.{BufferedWriter, File, FileOutputStream, PrintWriter}
import java.net.URI
import scala.concurrent.ExecutionContext

class CreatePersonImportCsv @Inject()(
  thingsDbAccess: ThingsDbAccess,
  storage: S3Client,
  sourceRetriever: SourceRetriever
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {

  private val logger = LoggerFactory.getLogger(getClass)

  override def runInternal(args: Args): Unit = {
    val file = args.value[URI]("input").get
    val offset = args.valueOrDefault("offset", 0)
    val limit = args.valueOrDefault("limit", -1)

    val output = new File(f"dump.csv")
    val writer = new BufferedWriter(
      new PrintWriter(new FileOutputStream(output))
    )

    val thingLike = implicitly[Thingable[Person]]

    sourceRetriever
      .getSourceStream(file)
      .drop(offset)
      .safeTake(limit)
      .flatMap(source => {
        for {
          line <- source.getLines()
          sanitizedLine <- sanitizeLine(line)
          personTry = parse(sanitizedLine).flatMap(_.as[Person])
        } yield {
          personTry.toTry
            .flatMap(thingLike.toThing)
            .map(t => {
              List(
                t.id.toString,
                t.name,
                t.normalizedName.toString,
                t.createdAt.toString,
                t.lastUpdatedAt.toString,
                t.metadata
                  .map(
                    _.noSpaces
                      .replaceAllLiterally("\\\"", "'")
                      .replaceAllLiterally("\"", "\"\"")
//                      .replaceAllLiterally("|", "\"")
                  )
                  .getOrElse(""),
                t.popularity.map(_.toString).getOrElse(""),
                t.tmdbId.getOrElse("")
              ).map(s => "\"" + s + "\"").mkString(",")
            })
            .recover {
              case t =>
                logger.error("Problem", t)
                throw t
            }
            .toOption
        }
      })
      .flatten
      .foreach(line => {
        writer.write(line)
        writer.newLine()
      })

    writer.flush()
    writer.close()
  }

  private def sanitizeLine(line: String): List[String] = {
    if (line.contains("}{")) {
      val left :: right :: Nil = line.split("}\\{", 2).toList
      (left + "}") :: ("{" + right) :: Nil
    } else {
      List(line)
    }
  }
}
