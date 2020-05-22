package com.teletracker.consumers.config

import com.teletracker.common.config.core.api.ConfigWithPath

object ConsumerConfig extends ConfigWithPath {
  override type ConfigType = ConsumerConfig
  override val path: String = "teletracker.consumer"
}

case class ConsumerConfig(
  max_tmdb_concurrent_jobs: Int,
  max_regular_concurrent_jobs: Int,
  logging: ConsumerLoggingConfig,
  es_ingest_worker: ThroughputWorkerConfig,
  es_item_denorm_worker: BatchWorkerConfig,
  es_person_denorm_worker: BatchWorkerConfig)

case class ConsumerLoggingConfig(output_to_console: Boolean)

case class ThroughputWorkerConfig(max_outstanding: Int)

case class BatchWorkerConfig(batch_size: Int)
