package com.teletracker.tasks.db

import com.teletracker.common.db.SyncDbProvider
import com.teletracker.common.db.model.{
  Certification,
  CertificationType,
  Certifications
}
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.tmdb.CertificationListResponse
import com.teletracker.tasks.{TeletrackerTask, TeletrackerTaskApp}
import javax.inject.Inject
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object SeedCertifications extends TeletrackerTaskApp[CertificationSeeder]

class CertificationSeeder @Inject()(
  tmdbClient: TmdbClient,
  provider: SyncDbProvider,
  certifications: Certifications)
    extends TeletrackerTask {
  import certifications.driver.api._

  def run(args: Map[String, Option[Any]]) = {
    val movieCerts = Await.result(
      tmdbClient
        .makeRequest[CertificationListResponse]("certification/movie/list"),
      Duration.Inf
    )

    Await.result(provider.getDB.run(certifications.query.delete), Duration.Inf)

    val inserts = movieCerts.certifications.flatMap {
      case (region, certs) =>
        certs.map(cert => {
          val certModel = Certification(
            None,
            CertificationType.Movie,
            region,
            cert.certification,
            cert.meaning,
            cert.order
          )
          certifications.query += certModel
        })
    }

    val movieCertInserts = provider.getDB.run(
      DBIO.sequence(inserts)
    )

    Await.result(movieCertInserts, Duration.Inf)

    val tvCerts = Await.result(
      tmdbClient
        .makeRequest[CertificationListResponse]("certification/tv/list"),
      Duration.Inf
    )

    val tvInserts = tvCerts.certifications.flatMap {
      case (region, certs) =>
        certs.map(cert => {
          val certModel = Certification(
            None,
            CertificationType.Tv,
            region,
            cert.certification,
            cert.meaning,
            cert.order
          )
          certifications.query += certModel
        })
    }

    val tvCertInserts = provider.getDB.run(
      DBIO.sequence(tvInserts)
    )

    Await.result(tvCertInserts, Duration.Inf)
  }

}
