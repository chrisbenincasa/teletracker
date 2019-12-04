package com.teletracker.tasks.db

import com.teletracker.common.db.access.{GenresDbAccess, NetworksDbAccess}
import com.teletracker.common.db.dynamo.MetadataDbAccess
import com.teletracker.common.db.dynamo.model.{StoredGenre, StoredNetwork}
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class MigrateGenresToDynamo @Inject()(
  genresDbAccess: GenresDbAccess,
  metadataDbAccess: MetadataDbAccess
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val genreId = args.value[Int]("genreId")

    val genresByReference = genresDbAccess.findAllGenres().await()

    val genres = genresByReference
      .map(_._2)
      .groupBy(_.id.get)
      .values
      .flatMap(_.headOption)

    genres
      .filter(genre => genreId.forall(_ == genre.id.get))
      .foreach(genre => {
        val storedGenre = StoredGenre(
          id = genre.id.get,
          name = genre.name,
          slug = genre.slug,
          genreTypes = genre.`type`.toSet
        )

        metadataDbAccess.saveGenre(storedGenre).await()
      })
  }
}
