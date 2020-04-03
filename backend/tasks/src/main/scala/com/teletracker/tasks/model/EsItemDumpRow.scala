package com.teletracker.tasks.model

import com.teletracker.common.elasticsearch.EsItem
import io.circe.generic.JsonCodec

/**
  * A single row for an [[EsItem]] in a dump file generated by elasticdump
  */
@JsonCodec
case class EsItemDumpRow(_source: EsItem)