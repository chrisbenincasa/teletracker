package com.teletracker.common.model.scraping

import com.teletracker.common.db.model.ItemType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

trait ScrapedItem {
  def availableDate: Option[String]
  def title: String
  def releaseYear: Option[Int]
  def category: Option[String]
  def network: String
  def status: String
  def externalId: Option[String]
  def description: Option[String]
  def url: Option[String] = None
  def numSeasonsAvailable: Option[Int] = None
  def posterImageUrl: Option[String] = None
  def actualItemId: Option[UUID] = None

  lazy val availableLocalDate: Option[LocalDate] =
    availableDate.map(LocalDate.parse(_, DateTimeFormatter.ISO_LOCAL_DATE))

  lazy val isExpiring: Boolean = status == "Expiring"

  def isMovie: Boolean
  def isTvShow: Boolean
  def thingType: Option[ItemType] = {
    if (isMovie) {
      Some(ItemType.Movie)
    } else if (isTvShow) {
      Some(ItemType.Show)
    } else {
      None
    }
  }

  def cast: Option[Seq[ScrapedCastMember]] = None
  def crew: Option[Seq[ScrapedCrewMember]] = None
}

trait ScrapedCastMember {
  def name: String
  def order: Option[Int]
  def role: Option[String]
}

trait ScrapedCrewMember {
  def name: String
  def order: Option[Int]
  def role: Option[String]
}
