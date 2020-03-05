package com.teletracker.tasks.scraper.model

import io.circe.generic.JsonCodec

@JsonCodec
case class WhatsOnNetflixCatalogItem(
  title: String,
  `type`: String,
  titlereleased: String,
  image_landscape: String,
  image_portrait: String,
  rating: String,
  quality: String,
  actors: String,
  director: String,
  category: String,
  imdb: String,
  runtime: String,
  netflixid: String,
  date_released: String,
  description: String)
