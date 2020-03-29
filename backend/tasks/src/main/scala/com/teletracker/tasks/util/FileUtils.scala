package com.teletracker.tasks.util

import javax.inject.Inject
import java.net.URI

class FileUtils @Inject()(sourceRetriever: SourceRetriever) {
  def readAllLinesToSet(loc: URI): Set[String] = {
    sourceRetriever.getSourceStream(loc).foldLeft(Set.empty[String]) {
      case (set, src) =>
        try {
          set ++ src.getLines()
        } finally {
          src.close()
        }
    }
  }
}
