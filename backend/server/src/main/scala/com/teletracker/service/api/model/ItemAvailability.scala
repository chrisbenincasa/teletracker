package com.teletracker.service.api.model

import com.teletracker.common.db.model.PresentationType
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import java.time.LocalDate

@JsonCodec
case class ItemAvailability(
  networkId: Int,
  networkName: String,
  offers: List[ItemAvailabilityOffer])

@JsonCodec
case class ItemAvailabilityOffer(
  region: String,
  startDate: Option[LocalDate],
  endDate: Option[LocalDate],
  offerType: String,
  cost: Option[Double],
  currency: Option[String],
  presentationType: Option[PresentationType],
  links: Option[ItemAvailabilityOfferLinks],
  numSeasonsAvailable: Option[Int])

@JsonCodec
case class ItemAvailabilityOfferLinks(web: Option[String])
