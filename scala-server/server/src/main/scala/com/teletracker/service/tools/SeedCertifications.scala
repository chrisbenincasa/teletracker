package com.teletracker.service.tools

import com.teletracker.service.db.model.{
  Certification,
  CertificationType,
  Certifications
}
import com.teletracker.service.external.tmdb.TmdbClient
import com.teletracker.service.inject.{DbProvider, Modules}
import com.teletracker.service.model.tmdb.CertificationListResponse
import com.google.inject.Module
import com.twitter.inject.app.App
import javax.inject.Inject
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

object SeedCertifications extends App {
  override protected def modules: Seq[Module] = Modules()

  override protected def run(): Unit = {
    injector.instance[CertificationSeeder].run()
  }
}

class CertificationSeeder @Inject()(
  tmdbClient: TmdbClient,
  provider: DbProvider,
  certifications: Certifications) {
  import certifications.driver.api._

  def run() = {
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
