package com.teletracker.common.util

import com.teletracker.common.db.dynamo.MetadataDbAccess
import com.teletracker.common.db.dynamo.model.StoredGenre
import com.teletracker.common.db.model._
import com.teletracker.common.util.Implicits._
import com.twitter.cache.ConcurrentMapCache
import com.twitter.util.{Future => TFuture}
import javax.inject.{Inject, Singleton}
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GenreCache @Inject()(
  metadataDbAccess: MetadataDbAccess
)(implicit executionContext: ExecutionContext) {

  private val cache = new ConcurrentMapCache[String, List[StoredGenre]](
    new ConcurrentHashMap[String, TFuture[List[StoredGenre]]]()
  )

  private val allReferenceCache =
    new ConcurrentMapCache[String, Map[(ExternalSource, String), StoredGenre]](
      new ConcurrentHashMap[String, TFuture[
        Map[(ExternalSource, String), StoredGenre]
      ]]()
    )

  final private val referenceCache =
    new ConcurrentMapCache[(ExternalSource, String), StoredGenre](
      new ConcurrentHashMap[(ExternalSource, String), TFuture[StoredGenre]]()
    )

  def get(): Future[List[StoredGenre]] = {
    cache.getOrElseUpdate("GENRES") {
      val p = com.twitter.util.Promise[List[StoredGenre]]()
      val sFut = metadataDbAccess.getAllGenres()

      sFut.onComplete(p.update(_))

      p
    }
  }

  def getById(): Future[Map[Int, StoredGenre]] = {
    get().map(genres => genres.map(g => g.id -> g).toMap)
  }

  def getReferenceMap(): Future[Map[(ExternalSource, String), StoredGenre]] = {
    allReferenceCache.getOrElseUpdate("GENRES") {
      val p =
        com.twitter.util.Promise[Map[(ExternalSource, String), StoredGenre]]()

      val referenceFut = metadataDbAccess.getAllGenreReferences()
      val genresFut = get()

      for {
        references <- referenceFut
        genres <- genresFut
      } yield {
        val genreMap = references
          .flatMap(reference => {
            genres
              .find(_.id == reference.genreId)
              .map(
                genre =>
                  (reference.externalSource, reference.externalId) -> genre
              )
          })
          .toMap

        p.setValue(genreMap)
      }

      p
    }
  }

  def getByReference(
    externalSource: ExternalSource,
    externalId: String
  ): Future[StoredGenre] = {
    referenceCache.getOrElseUpdate(externalSource -> externalId) {
      val p = com.twitter.util.Promise[StoredGenre]()
      val sFut =
        metadataDbAccess.getGenreByReference(externalSource, externalId)

      sFut.foreach {
        case None =>
          throw new IllegalArgumentException(
            s"No network for external $externalSource, $externalId"
          )
        case Some((_, genre)) => p.setValue(genre)
      }

      p
    }
  }
}
