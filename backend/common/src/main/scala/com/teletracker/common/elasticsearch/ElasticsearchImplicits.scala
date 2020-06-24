package com.teletracker.common.elasticsearch

import io.circe.Encoder
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.xcontent.XContentType

trait ElasticsearchImplicits {
  implicit def toRichIndexRequest(
    indexRequest: IndexRequest
  ): RichIndexRequest = new RichIndexRequest(indexRequest)

  implicit def toRichUpdateRequest(
    updateRequest: UpdateRequest
  ): RichUpdateRequest = new RichUpdateRequest(updateRequest)
}

final class RichIndexRequest(val indexRequest: IndexRequest) extends AnyVal {
  import io.circe.syntax._
  import com.teletracker.common.util.Functions._

  def jsonSource[T: Encoder](
    source: T,
    removeNulls: Boolean = false
  ): IndexRequest =
    indexRequest.source(
      source.asJson.applyIf(removeNulls)(_.deepDropNullValues).noSpaces,
      XContentType.JSON
    )
}

final class RichUpdateRequest(val updateRequest: UpdateRequest) extends AnyVal {
  import io.circe.syntax._
  import com.teletracker.common.util.Functions._

  def jsonDoc[T: Encoder](
    source: T,
    removeNulls: Boolean = false
  ): UpdateRequest =
    updateRequest.doc(
      source.asJson.applyIf(removeNulls)(_.deepDropNullValues).noSpaces,
      XContentType.JSON
    )
}
