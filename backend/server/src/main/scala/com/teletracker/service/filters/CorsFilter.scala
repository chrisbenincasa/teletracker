package com.teletracker.service.filters

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.filter.Cors

object CorsFilter {
  final val instance = new Cors.HttpFilter(
    Cors.Policy(
      allowsOrigin = _ => Some("*"),
      allowsMethods =
        _ => Some(Seq("HEAD", "GET", "PUT", "POST", "DELETE", "OPTIONS")),
      allowsHeaders = _ =>
        Some(
          Seq(
            "Origin",
            "X-Requested-With",
            "Content-Type",
            "Accept",
            "Authorization"
          )
        ),
      supportsCredentials = true,
      maxAge = Some(1.day)
    )
  )
}
