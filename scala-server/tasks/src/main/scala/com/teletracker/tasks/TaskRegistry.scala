package com.teletracker.tasks

import com.teletracker.tasks.scraper._
import com.teletracker.tasks.tmdb.import_tasks._

object TaskRegistry {
  val TasksToClass: Map[String, Class[_ <: TeletrackerTask]] = List(
    classOf[IngestNetflixOriginalsArrivals],
    classOf[IngestHuluChanges],
    classOf[IngestHboChanges],
    classOf[IngestUnogsNetflixCatalog],
    classOf[IngestUnogsNetflixExpiring],
    classOf[NetflixCatalogDeltaIngestJob],
    classOf[ImportMoviesFromDump],
    classOf[ImportPeopleAssociationsFromCsvs],
    classOf[ImportPeopleFromDump],
    classOf[ImportPersonAssociations],
    classOf[ImportTvShowsFromDump],
    classOf[RemoteTask],
    classOf[NoopTeletrackerTask],
    classOf[TimeoutTask]
  ).map(clazz => clazz.getSimpleName -> clazz).toMap
}
