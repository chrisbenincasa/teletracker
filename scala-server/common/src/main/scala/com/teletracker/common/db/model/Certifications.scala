package com.teletracker.common.db.model

case class Certification(
  id: Option[Int],
  `type`: CertificationType,
  iso_3166_1: String,
  certification: String,
  description: String,
  order: Int)
