//package com.teletracker.common.elasticsearch.denorm
//
//import com.teletracker.common.elasticsearch.model.{EsItem, StringListOrString}
//import com.teletracker.common.util.Slug
//import org.scalacheck.{Arbitrary, Gen}
//import org.scalacheck.ScalacheckShapeless._
//import com.fortysevendeg.scalacheck.datetime.jdk8.ArbitraryJdk8._
//import org.elasticsearch.common.bytes.BytesReference
//import org.elasticsearch.common.xcontent.{
//  ToXContent,
//  XContentBuilder,
//  XContentHelper,
//  XContentType
//}
//import org.elasticsearch.common.xcontent.json.JsonXContent
//import org.scalatest.flatspec.AnyFlatSpec
//import java.util.UUID
//
//class DenormalizedItemUpdaterTest extends AnyFlatSpec {
//  it should "build a query" in {
//    implicit val stringOrListStringGen: Gen[StringListOrString] =
//      implicitly[Arbitrary[String]].arbitrary.map(StringListOrString.forString)
//    implicit val stringOrListStringArg: Arbitrary[StringListOrString] =
//      Arbitrary(stringOrListStringGen)
//
//    implicit val slugGen: Gen[Slug] =
//      implicitly[Arbitrary[String]].arbitrary.map(Slug.raw)
//    implicit val slugArb: Arbitrary[Slug] =
//      Arbitrary(slugGen)
//
//    implicit val uuidGen: Gen[UUID] = Gen.delay(UUID.randomUUID())
//    implicit val uuidArg: Arbitrary[UUID] = Arbitrary(uuidGen)
//
//    val esItemArb = implicitly[Arbitrary[EsItem]]
//    val query = DenormalizedItemUpdater.getUpdateUserItemsQuery(
//      esItemArb.arbitrary.sample.get,
//      "user_items"
//    )
//
//    val reference = XContentHelper.toXContent(query, XContentType.JSON, true)
//    println(
//      new String(BytesReference.toBytes(reference))
//    )
//  }
//}
