package com.teletracker.tasks.model

import com.teletracker.common.elasticsearch.EsPerson
import io.circe.generic.JsonCodec

/**
  * A single row for an [[EsPerson]] in a dump file generated by elasticdump
  */
@JsonCodec
case class EsPersonDumpRow(_source: EsPerson)
