package com.teletracker.common.model

import com.teletracker.common.elasticsearch.model.{
  EsExternalId,
  EsItemImage,
  EsItemRating,
  EsItemVideo
}
import com.teletracker.common.model.instances.{
  TmdbMovieToEsItem,
  TmdbPersonToEsItem,
  TmdbShowToEsItem
}
import com.teletracker.common.model.tmdb.{Movie, Person, TvShow}

trait ToEsItem[T] {
  def esItemRating(t: T): Option[EsItemRating]
  def esItemImages(t: T): List[EsItemImage]
  def esExternalIds(t: T): List[EsExternalId]
  def esItemVideos(t: T): List[EsItemVideo]
}

object ToEsItem {
  implicit val forTmdbMovie: ToEsItem[Movie] = TmdbMovieToEsItem
  implicit val forTmdbShow: ToEsItem[TvShow] = TmdbShowToEsItem
  implicit val forTmdbPerson: ToEsItem[Person] = TmdbPersonToEsItem
}
