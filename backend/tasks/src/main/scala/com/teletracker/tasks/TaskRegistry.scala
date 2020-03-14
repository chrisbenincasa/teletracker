package com.teletracker.tasks

import com.teletracker.common.tasks.TeletrackerTask
import com.teletracker.common.tasks.model.TeletrackerTaskIdentifier
import com.teletracker.tasks.elasticsearch.{
  DenormalizeItemTask,
  DenormalizePersonTask
}
import com.teletracker.tasks.scraper._
import com.teletracker.tasks.scraper.hbo.IngestHboChanges
import com.teletracker.tasks.scraper.hulu.IngestHuluChanges
import com.teletracker.tasks.scraper.netflix.{
  IngestNetflixCatalog,
  IngestNetflixOriginalsArrivals,
  IngestUnogsNetflixExpiring,
  NetflixCatalogDeltaIngestJob
}
import com.teletracker.tasks.tmdb.export_tasks.{
  MovieChangesDumpTask,
  PersonChangesDumpTask,
  TvChangesDumpTask
}
import com.teletracker.tasks.tmdb.import_tasks._

object TaskRegistry {
  def taskForTaskType(
    taskIdentifier: TeletrackerTaskIdentifier
  ): Class[_ <: TeletrackerTask] = {
    taskIdentifier match {
      case TeletrackerTaskIdentifier.DENORMALIZE_ITEM_TASK =>
        classOf[DenormalizeItemTask]
      case TeletrackerTaskIdentifier.DENORMALIZE_PERSON_TASK =>
        classOf[DenormalizePersonTask]
    }
  }

  val TasksToClass: Map[String, Class[_ <: TeletrackerTask]] = List(
    classOf[IngestNetflixOriginalsArrivals],
    classOf[IngestHuluChanges],
    classOf[IngestHboChanges],
    classOf[IngestNetflixCatalog],
    classOf[IngestUnogsNetflixExpiring],
    classOf[NetflixCatalogDeltaIngestJob],
    classOf[ImportMoviesFromDump],
    classOf[ImportPeopleAssociationsFromCsvs],
    classOf[ImportPeopleFromDump],
    classOf[ImportTvShowsFromDump],
    classOf[RemoteTask],
    classOf[NoopTeletrackerTask],
    classOf[TimeoutTask],
    classOf[MovieChangesDumpTask],
    classOf[TvChangesDumpTask],
    classOf[PersonChangesDumpTask]
  ).map(clazz => clazz.getSimpleName -> clazz).toMap
}
