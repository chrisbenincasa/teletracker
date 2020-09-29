package com.teletracker.common.util

import java.net.URI

object S3Uri {
  def apply(
    bucket: String,
    path: String
  ): URI = URI.create(s"s3://$bucket/${path.stripPrefix("/")}")

  def unapply(arg: URI): Option[(String, String)] = {
    if (arg.getScheme == "s3") {
      Some(arg.getHost -> arg.getPath.stripPrefix("/"))
    } else {
      None
    }
  }
}
