package com.teletracker.tasks.scraper

import com.teletracker.tasks.scraper.model.PotentialInput
import io.circe.Codec

// Job that takes in a hand-filtered potential match file output and imports the items
abstract class IngestPotentialMatches[T <: ScrapedItem: Codec]
    extends IngestJob[PotentialInput[T]]
