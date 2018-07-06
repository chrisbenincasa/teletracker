package com.chrisbenincasa.services.teletracker.model.justwatch

case class Provider(
  id: Int,
  profile_id: Int,
  technical_name: String,
  short_name: String,
  clear_name: String,
  has_global_account: Boolean,
  can_create_title: Boolean,
  data: ProviderData,
  monetization_types: List[String],
  icon_url: String,
  slug: String
)

case class PopularSearchRequest(
  page: Int,
  pageSize: Int,
  query: String,
  contentTypes: List[String]
)

case class ProviderData(
  public_info: Boolean
)

case class PopularItemsResponse(
  page: Int,
  page_size: Int,
  total_pages: Int,
  total_results: Int,
  items: List[PopularItem]
)

case class PopularItem(
  id: Int,
  title: String,
  full_path: String,
  poster: Option[String],
  short_description: Option[String],
  original_release_year: Option[Int],
  tmdb_popularity: Option[Double],
  offers: Option[List[Offer]],
  object_type: Option[String],
  original_title: Option[String],
  scoring: Option[List[Scoring]],
  runtime: Option[Int],
  age_certification: Option[String]
)

case class Offer(
  monetization_type: Option[String],
  provider_id: Int,
  retail_price: Option[Double],
  currency: Option[String],
  presentation_type: Option[String],
  date_created: Option[String],
  country: Option[String],
  urls: Map[String, String], // Fill in later
  last_change_retail_price: Option[Double],
  last_change_difference: Option[Double],
  last_change_percent: Option[Double],
  last_change_date: Option[String],
  last_change_date_provider_id: Option[String]
)

case class Scoring(
  provider_type: String,
  value: Double
)