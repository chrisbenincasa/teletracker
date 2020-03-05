package com.teletracker.tasks.db

import com.teletracker.common.db.dynamo.MetadataDbAccess
import com.teletracker.common.db.dynamo.model.StoredGenre
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.db.legacy_model.Genre
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import java.net.URI
import scala.concurrent.ExecutionContext

class MigrateGenresToDynamo @Inject()(
  metadataDbAccess: MetadataDbAccess,
  sourceRetriever: SourceRetriever
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val genreId = args.value[Int]("genreId")
    val input = args.valueOrThrow[URI]("input")

    sourceRetriever
      .getSource(input)
      .getLines()
      .map(Genre.fromLine(_))
      .filter(genre => genreId.forall(_ == genre.id))
      .foreach(genre => {
        val storedGenre = StoredGenre(
          id = genre.id,
          name = genre.name,
          slug = genre.slug,
          genreTypes = genre.`type`.toSet
        )

        metadataDbAccess.saveGenre(storedGenre).await()
      })
  }
}
