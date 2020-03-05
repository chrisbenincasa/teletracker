package com.teletracker.service.controllers

import com.twitter.finagle.http.ParamMap

object ParamExtractor {
  def extractOptSeqParam(
    params: ParamMap,
    param: String
  ): Option[Seq[String]] = {
    val hasParam = params.isDefinedAt(param)
    val extracted = params
      .get(param)
      .map(_.split(",").filterNot(_.isEmpty).toSeq)

    (hasParam, extracted) match {
      case (true, genres @ Some(_)) => genres
      case (true, None)             => Some(Seq())
      case (false, _)               => None
    }
  }
}
