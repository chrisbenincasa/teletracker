package com.teletracker.tasks.model

import com.teletracker.tasks.tmdb.export_tasks.TmdbDumpFileRow
import io.circe.generic.JsonCodec

@JsonCodec
case class MovieDumpFileRow(
  adult: Boolean,
  id: Int,
  original_title: String,
  popularity: Double,
  video: Boolean)
    extends TmdbDumpFileRow

@JsonCodec
case class TvShowDumpFileRow(
  id: Int,
  original_name: String,
  popularity: Double)
    extends TmdbDumpFileRow

@JsonCodec
case class PersonDumpFileRow(
  adult: Boolean,
  id: Int,
  name: String,
  popularity: Double)
    extends TmdbDumpFileRow

@JsonCodec
case class GenericTmdbDumpFileRow(
  id: Int,
  popularity: Double)
