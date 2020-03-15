package com.teletracker.consumers.config

case class ConsumerConfig(
  max_tmdb_concurrent_jobs: Int,
  max_regular_concurrent_jobs: Int,
  logging: ConsumerLoggingConfig)

case class ConsumerLoggingConfig(output_to_console: Boolean)
