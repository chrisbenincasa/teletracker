package com.teletracker.common.elasticsearch

import com.teletracker.common.db.Bookmark
import com.teletracker.common.elasticsearch.model.{
  EsItem,
  EsPerson,
  EsPotentialMatchItem,
  EsUserItem
}

trait ElasticsearchPagedResponse[T] {
  def items: List[T]
  def totalHits: Long
  def bookmark: Option[Bookmark] = None

  def withBookmark(bookmark: Option[Bookmark]): ElasticsearchPagedResponse[T]
}

object ElasticsearchItemsResponse {
  val empty = ElasticsearchItemsResponse(Nil, 0, None)
}

case class ElasticsearchItemsResponse(
  items: List[EsItem],
  totalHits: Long,
  override val bookmark: Option[Bookmark] = None)
    extends ElasticsearchPagedResponse[EsItem] {
  override def withBookmark(
    bookmark: Option[Bookmark]
  ): ElasticsearchItemsResponse =
    this.copy(bookmark = bookmark)
}

object ElasticsearchPeopleResponse {
  val empty = ElasticsearchPeopleResponse(Nil, 0, None)
}

case class ElasticsearchPeopleResponse(
  items: List[EsPerson],
  totalHits: Long,
  override val bookmark: Option[Bookmark] = None)
    extends ElasticsearchPagedResponse[EsPerson] {
  override def withBookmark(
    bookmark: Option[Bookmark]
  ): ElasticsearchPeopleResponse =
    this.copy(bookmark = bookmark)
}

case class ElasticsearchUserItemsResponse(
  items: List[EsUserItem],
  totalHits: Long,
  override val bookmark: Option[Bookmark] = None)
    extends ElasticsearchPagedResponse[EsUserItem] {
  override def withBookmark(
    bookmark: Option[Bookmark]
  ): ElasticsearchUserItemsResponse =
    this.copy(bookmark = bookmark)
}

case class EsPotentialMatchResponse(
  items: List[EsPotentialMatchItem],
  totalHits: Long,
  override val bookmark: Option[Bookmark] = None)
    extends ElasticsearchPagedResponse[EsPotentialMatchItem] {
  override def withBookmark(
    bookmark: Option[Bookmark]
  ): EsPotentialMatchResponse =
    this.copy(bookmark = bookmark)
}
