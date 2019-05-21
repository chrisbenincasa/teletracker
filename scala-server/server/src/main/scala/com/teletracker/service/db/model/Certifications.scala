package com.teletracker.service.db.model

import javax.inject.Inject
import slick.jdbc.JdbcProfile

case class Certification(
  id: Option[Int],
  `type`: CertificationType,
  iso_3166_1: String,
  certification: String,
  description: String,
  order: Int)

class Certifications @Inject()(val driver: JdbcProfile) {
  import driver.api._

  object Implicits {
    implicit val certificationTypeMapping = MappedColumnType
      .base[CertificationType, String](_.getName, CertificationType.fromString)
  }

  import Implicits._

  class CertificationsTable(tag: Tag)
      extends Table[Certification](tag, "certifications") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def `type` = column[CertificationType]("type")
    def iso_3166_1 = column[String]("iso_3166_1")
    def certification = column[String]("certification")
    def description = column[String]("homepage")
    def order = column[Int]("order")

    def uniqCert =
      index("uniq_cert_idx", (iso_3166_1, `type`, certification, order), true)

    override def * =
      (
        id.?,
        `type`,
        iso_3166_1,
        certification,
        description,
        order
      ) <> (Certification.tupled, Certification.unapply)
  }

  val query = TableQuery[CertificationsTable]
}
