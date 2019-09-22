package com.teletracker.tasks.util

import com.google.cloud.storage.{BlobId, Storage}
import javax.inject.Inject
import java.net.URI
import scala.io.Source

class SourceRetriever @Inject()(storage: Storage) {
  def getSource(uri: URI): Source = {
    uri.getScheme match {
      case "gs" =>
        Source.fromBytes(
          storage
            .get(BlobId.of(uri.getHost, uri.getPath.stripPrefix("/")))
            .getContent()
        )
      case "file" =>
        Source.fromFile(uri)
      case _ =>
        throw new IllegalArgumentException(
          s"Unsupposed file scheme: ${uri.getScheme}"
        )
    }
  }
}
