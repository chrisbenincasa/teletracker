package com.teletracker.common.db

import com.teletracker.common.db.dynamo.model.StoredUserList
import com.teletracker.common.db.model.ExternalSource

object SortMode {
  final val SearchScoreType = "search_score"
  final val PopularityType = "popularity"
  final val RecentType = "recent"
  final val AddedTimeType = "added_time"
  final val RatingType = "rating"
  final val DefaultType = "default"
  final val Delimiter = "|"

  def fromString(str: String): SortMode = str.toLowerCase() match {
    case PopularityType => Popularity()
    case RecentType     => Recent()
    case AddedTimeType  => AddedTime()
    case x if x.startsWith(RatingType) =>
      val Array(_, source) = x.split(s"\\${Delimiter}", 2)
      Rating(isDesc = true, ExternalSource.fromString(source.toLowerCase))

    case _ => DefaultForListType()
  }
}
sealed trait SortMode {
  def isDesc: Boolean
  def desc: SortMode
  def asc: SortMode
  def direction(isDesc: Boolean): SortMode =
    if (isDesc) this.desc else this.asc
  def `type`: String
}
final case class SearchScore(isDesc: Boolean = true) extends SortMode {
  override def desc: SearchScore = this.copy(true)
  override def asc: SearchScore = this.copy(false)
  override def `type`: String = SortMode.SearchScoreType
}
final case class Popularity(isDesc: Boolean = true) extends SortMode {
  override def desc: Popularity = this.copy(true)
  override def asc: Popularity = this.copy(false)
  override def `type`: String = SortMode.PopularityType
}
final case class Recent(isDesc: Boolean = true) extends SortMode {
  override def desc: Recent = this.copy(true)
  override def asc: Recent = this.copy(false)
  override def `type`: String = SortMode.RecentType
}
final case class AddedTime(isDesc: Boolean = true) extends SortMode {
  override def desc: AddedTime = this.copy(true)
  override def asc: AddedTime = this.copy(false)
  override def `type`: String = SortMode.AddedTimeType
}
final case class Rating(
  isDesc: Boolean = true,
  source: ExternalSource)
    extends SortMode {
  override def desc: SortMode = this.copy(true)
  override def asc: SortMode = this.copy(false)
  override def `type`: String =
    s"${SortMode.RatingType}${SortMode.Delimiter}${source}"
}
final case class DefaultForListType(isDesc: Boolean = true) extends SortMode {
  override def desc: DefaultForListType = this.copy(true)
  override def asc: DefaultForListType = this.copy(false)
  override def `type`: String = SortMode.DefaultType
  def get(
    isDynamic: Boolean,
    listOwnerUserId: String
  ): SortMode =
    if (isDynamic) {
      Popularity(isDesc)
    } else if (listOwnerUserId == StoredUserList.PublicUserId) {
      Recent(isDesc)
    } else {
      AddedTime(isDesc)
    }
}
