package com.teletracker.common.db

object SortMode {
  final val PopularityType = "popularity"
  final val RecentType = "recent"
  final val AddedTimeType = "added_time"
  final val DefaultType = "default"

  def fromString(str: String): SortMode = str.toLowerCase() match {
    case PopularityType => Popularity()
    case RecentType     => Recent()
    case AddedTimeType  => AddedTime()
    case _              => DefaultForListType()
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
case class Popularity(isDesc: Boolean = true) extends SortMode {
  override def desc: Popularity = this.copy(true)
  override def asc: Popularity = this.copy(false)
  override def `type`: String = SortMode.PopularityType
}
case class Recent(isDesc: Boolean = true) extends SortMode {
  override def desc: Recent = this.copy(true)
  override def asc: Recent = this.copy(false)
  override def `type`: String = SortMode.RecentType
}
case class AddedTime(isDesc: Boolean = true) extends SortMode {
  override def desc: AddedTime = this.copy(true)
  override def asc: AddedTime = this.copy(false)
  override def `type`: String = SortMode.AddedTimeType
}
case class DefaultForListType(isDesc: Boolean = true) extends SortMode {
  override def desc: DefaultForListType = this.copy(true)
  override def asc: DefaultForListType = this.copy(false)
  override def `type`: String = SortMode.DefaultType
  def get(isDynamic: Boolean): SortMode =
    if (isDynamic) Popularity(isDesc) else AddedTime(isDesc)
}
