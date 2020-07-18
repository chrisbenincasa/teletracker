package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.model.EsExternalId
import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.tasks.TypedTeletrackerTask
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.model.GenericTmdbDumpFileRow
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.SourceRetriever
import io.circe.generic.JsonCodec
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.util.UUID

// s3://teletracker-data-us-west-2/scrape-results/tmdb/2020-07-14/movie_ids_sorted-2020-07-14.json.gz

@JsonCodec
case class DeleteItemsArgs(
  dumpInput: URI,
  scrapeInput: URI,
  itemType: ItemType)

class DeleteItemsViaDump @Inject()(sourceRetriever: SourceRetriever)
    extends TypedTeletrackerTask[DeleteItemsArgs] {
  override def preparseArgs(args: RawArgs): DeleteItemsArgs = DeleteItemsArgs(
    dumpInput = args.valueOrThrow[URI]("dumpInput"),
    scrapeInput = args.valueOrThrow[URI]("scrapeInput"),
    itemType = args.valueOrThrow[ItemType]("itemType")
  )

  override protected def runInternal(): Unit = {
    val ids = sourceRetriever
      .getSourceStream(args.dumpInput)
      .flatMap(source => {
        try {
          new IngestJobParser()
            .stream[IdDumpInput](source.getLines())
            .collect {
              case Right(value) if value.`type` == args.itemType =>
                value.external_ids.collectFirst {
                  case EsExternalId(provider, id)
                      if ExternalSource
                        .fromString(provider) == ExternalSource.TheMovieDb =>
                    id.toInt -> value.id
                }
            }
            .flatten
            .toSet
        } finally {
          source.close()
        }
      })
      .toMap

    val allKnownIds = sourceRetriever
      .getSourceStream(args.scrapeInput)
      .flatMap(source => {
        try {
          new IngestJobParser()
            .stream[GenericTmdbDumpFileRow](source.getLines())
            .collect {
              case Right(value) => value.id
            }
            .toSet
        } finally {
          source.close()
        }
      })

    val missingIds = ids.keySet -- allKnownIds
    logger.info(s"Found ${missingIds.size} missing ${args.itemType} ids.")

    missingIds.foreach(id => {
      ids
        .get(id)
        .foreach(uuid => {
          println(
            s"https://qa.teletracker.tv/${args.itemType}s/${uuid}, https://themoviedb.org/movie/${id}"
          )
        })
    })
  }
}

@JsonCodec
case class IdDumpInput(
  `type`: ItemType,
  external_ids: List[EsExternalId],
  id: UUID)
