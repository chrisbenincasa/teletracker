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
  title: Option[String],
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

case class JustWatchShow(
  id: Int,
  title: Option[String],
  full_path: Option[String],
  poster: Option[String],
  backdrops: Option[List[JustWatchBackdrop]],
  short_description: Option[String],
  original_release_year: Option[Int],
  tmdb_popularity: Option[Double],
  object_type: Option[String],
  original_title: Option[String],
  all_titles: Option[List[String]],
  offers: Option[List[Offer]],
  scoring: Option[List[Scoring]],
  genre_ids: Option[List[Int]],
  seasons: Option[List[JustWatchSeason]],
  age_certification: Option[String],
  max_season_number: Option[Int]
)

case class JustWatchSeason(
  id: Int,
  title: Option[String],
  full_path: Option[String],
  poster: Option[String],
  season_number: Option[Int],
  short_description: Option[String],
  original_release_year: Option[Int],
  tmdb_popularity: Option[Double],
  object_type: Option[String],
  original_title: Option[String],
  all_titles: Option[List[String]],
  offers: Option[List[Offer]],
  scoring: Option[List[Scoring]],
  show_id: Option[Int],
  show_title: Option[String],
  max_episode_number: Option[Int],
  episodes: Option[List[JustWatchEpisode]]
)

case class JustWatchEpisode(
  id: Int,
  title: Option[String],
  poster: Option[String],
  short_description: Option[String],
  object_type: Option[String],
  offers: Option[List[Offer]],
  season_number: Option[Int],
  episode_number: Option[Int],
  runtime: Option[Int],
  show_title: Option[String]
)

case class JustWatchBackdrop(
  backdrop_url: String
)
