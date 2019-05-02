package com.teletracker.service.tools

import com.teletracker.service.db.model._
import com.teletracker.service.inject.{DbProvider, Modules}
import com.google.inject.Module
import com.twitter.inject.app.App
import java.io.{File, FileWriter, PrintWriter}
import scala.concurrent.ExecutionContext.Implicits.global

object GenerateDdlsMain extends GenerateDdls

class GenerateDdls extends App {
  override protected def modules: Seq[Module] = Modules()

  override protected def run(): Unit = {
    val d = injector.instance(classOf[DbProvider])
    import d.driver.api._

    val createStatements = List(
      injector.instance[Users].query,
      injector.instance[UserCredentials].query,
      injector.instance[Events].query,
      injector.instance[Genres].query,
      injector.instance[Networks].query,
      injector.instance[NetworkReferences].query,
      injector.instance[Things].query,
      injector.instance[ThingNetworks].query,
      injector.instance[TrackedLists].query,
      injector.instance[TrackedListThings].query,
      injector.instance[TvShowEpisodes].query,
      injector.instance[TvShowSeasons].query,
      injector.instance[Availabilities].query,
      injector.instance[ExternalIds].query,
      injector.instance[GenreReferences].query,
      injector.instance[ThingGenres].query,
      injector.instance[Certifications].query,
      injector.instance[PersonThings].query,
      injector.instance[Tokens].query
    ).flatMap(_.schema.createStatements).
      groupBy(_.split(" ").take(2).toList)

    val outputFile = args.head

    val f = new File(outputFile).getParentFile
    if (!f.exists()) {
      f.mkdirs()
    }

    val writer = new PrintWriter(new FileWriter(outputFile))

    createStatements.getOrElse("create" :: "table" :: Nil, Nil).map(_ + ";").foreach(writer.println)
    createStatements.getOrElse("create" :: "index" :: Nil, Nil).map(_ + ";").foreach(writer.println)
    createStatements.getOrElse("create" :: "unique" :: Nil, Nil).map(_ + ";").foreach(writer.println)
    createStatements.getOrElse("alter" :: "table" :: Nil, Nil).map(_ + ";").foreach(writer.println)
    writer.flush()

    logger.info("Complete!")
  }
}
