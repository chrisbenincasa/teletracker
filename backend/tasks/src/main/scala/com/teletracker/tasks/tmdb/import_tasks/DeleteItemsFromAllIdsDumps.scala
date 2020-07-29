package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.elasticsearch.lookups.ElasticsearchExternalIdMappingStore
import com.teletracker.common.elasticsearch.model.EsExternalId
import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.tasks.TypedTeletrackerTask
import com.teletracker.common.tasks.args.GenArgParser
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.model.{BaseTaskArgs, GenericTmdbDumpFileRow}
import com.teletracker.tasks.util.{FileUtils, SourceRetriever}
import io.circe.generic.JsonCodec
import javax.inject.Inject
import java.net.URI
import scala.concurrent.{ExecutionContext, Future}

@JsonCodec
@GenArgParser
case class DeleteItemsFromAllIdsDumpsArgs(
  snapshotBeforeLocation: URI,
  snapshotAfterLocation: URI,
  override val dryRun: Boolean,
  override val sleepBetweenWriteMs: Option[Long],
  itemType: ItemType)
    extends BaseTaskArgs

object DeleteItemsFromAllIdsDumpsArgs

class DeleteItemsFromAllIdsDumps @Inject()(
  fileUtils: FileUtils,
  externalIdMappingStore: ElasticsearchExternalIdMappingStore
)(implicit executionContext: ExecutionContext)
    extends TypedTeletrackerTask[DeleteItemsFromAllIdsDumpsArgs] {

  override protected def runInternal(): Unit = {
    val allBeforeIds =
      fileUtils.readAllLinesToUniqueIdSet[GenericTmdbDumpFileRow, Int](
        args.snapshotBeforeLocation,
        row => Some(row.id),
        consultSourceCache = false
      )

    val allAfterIds =
      fileUtils.readAllLinesToUniqueIdSet[GenericTmdbDumpFileRow, Int](
        args.snapshotAfterLocation,
        row => Some(row.id),
        consultSourceCache = false
      )

    // These ids were deleted between the two dates
    val missingIds = allBeforeIds -- allAfterIds

    externalIdMappingStore
      .getItemIdsForExternalIds(
        missingIds.map(id => EsExternalId.tmdb(id) -> args.itemType)
      )
      .flatMap(foundMappings => {
        logger.info(s"Found ${foundMappings.size} ids to delete.")
        if (args.dryRun) {
          Future.successful {
            foundMappings.foreach {
              case ((id, typ), uuid) =>
                logger
                  .info(s"Would've deleted id = ${uuid}, externalId = ${id}")
            }
          }
        } else {
          Future.unit
        }
      })
      .await()
  }
}
