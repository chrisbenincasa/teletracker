package com.teletracker.common.elasticsearch

import com.teletracker.common.db.Bookmark

object ElasticsearchItemsResponse {
  val empty = ElasticsearchItemsResponse(Nil, 0, None)
}

case class ElasticsearchItemsResponse(
  items: List[EsItem],
  totalHits: Long,
  bookmark: Option[Bookmark] = None) {
  def withBookmark(bookmark: Option[Bookmark]): ElasticsearchItemsResponse =
    this.copy(bookmark = bookmark)
}

object ElasticsearchPeopleResponse {
  val empty = ElasticsearchPeopleResponse(Nil, 0, None)
}

case class ElasticsearchPeopleResponse(
  items: List[EsPerson],
  totalHits: Long,
  bookmark: Option[Bookmark] = None) {
  def withBookmark(bookmark: Option[Bookmark]): ElasticsearchPeopleResponse =
    this.copy(bookmark = bookmark)
}

case class ElasticsearchUserItemsResponse(
  items: List[EsUserItem],
  totalHits: Long,
  bookmark: Option[Bookmark] = None) {
  def withBookmark(bookmark: Option[Bookmark]): ElasticsearchUserItemsResponse =
    this.copy(bookmark = bookmark)
}
