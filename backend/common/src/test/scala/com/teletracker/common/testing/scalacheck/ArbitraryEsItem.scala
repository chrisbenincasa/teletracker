package com.teletracker.common.testing.scalacheck

import com.fortysevendeg.scalacheck.datetime.Granularity
import com.fortysevendeg.scalacheck.datetime.jdk8.ArbitraryJdk8.genZonedDateTime
import com.teletracker.common.elasticsearch.model.{EsItem, StringListOrString}
import org.scalacheck.ScalacheckShapeless._
import com.fortysevendeg.scalacheck.datetime.jdk8.ArbitraryJdk8._
import com.teletracker.common.util.Slug
import org.scalacheck.{Arbitrary, Gen}
import java.time.{OffsetDateTime, ZonedDateTime}
import java.util.UUID

trait GenEsItem {
  implicit def genOffsetDateTime(
    implicit granularity: Granularity[ZonedDateTime]
  ): Gen[OffsetDateTime] =
    genZonedDateTime.map(_.toOffsetDateTime)

  implicit val stringOrListStringGen: Gen[StringListOrString] =
    Gen.alphaNumStr.map(StringListOrString.forString)

  implicit val genSlug: Gen[Slug] =
    Gen.alphaNumStr.map(Slug.raw)

  implicit val uuidGen: Gen[UUID] = Gen.delay(UUID.randomUUID())
}

object GenEsItem extends GenEsItem

object ArbitraryEsItem extends GenEsItem {
  implicit def arbOffsetDateTime(
    implicit granularity: Granularity[ZonedDateTime]
  ): Arbitrary[OffsetDateTime] = Arbitrary(genOffsetDateTime)

  implicit val arbStringOrListString: Arbitrary[StringListOrString] =
    Arbitrary(stringOrListStringGen)

  implicit val arbSlug: Arbitrary[Slug] =
    Arbitrary(genSlug)

  implicit val arbUuid: Arbitrary[UUID] = Arbitrary(uuidGen)

  val arbEsItem: Arbitrary[EsItem] = implicitly[Arbitrary[EsItem]]
}
