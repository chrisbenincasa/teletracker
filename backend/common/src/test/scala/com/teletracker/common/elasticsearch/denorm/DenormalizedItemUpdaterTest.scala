package com.teletracker.common.elasticsearch.denorm

import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.xcontent.{XContentHelper, XContentType}
import org.scalatest.flatspec.AnyFlatSpec

class DenormalizedItemUpdaterTest extends AnyFlatSpec {
  import com.teletracker.common.testing.scalacheck.ArbitraryEsItem._

  it should "build a query" in {
    val item = arbEsItem.arbitrary.sample.get
    println(item)

    val query = DenormalizedItemUpdater.getUpdateUserItemsQuery(
      item,
      "user_items"
    )

    val reference = XContentHelper.toXContent(query, XContentType.JSON, true)
    println(
      new String(BytesReference.toBytes(reference))
    )
  }
}
