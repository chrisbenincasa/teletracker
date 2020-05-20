package com.teletracker.common.model.wikidata

import org.scalatest.flatspec.AnyFlatSpec
import io.circe.parser._
import io.circe.syntax._
import java.time.OffsetDateTime

class EntityDecodersTest extends AnyFlatSpec {
  it should "work" in {
    val j =
      """
        |{
        |"id": "Q806559$04DAC272-C7E7-445E-8906-63C82E98C3F9",
        |"rank": "normal",
        |"type": "statement",
        |"mainsnak": {
        |  "datatype": "wikibase-item",
        |  "datavalue": {
        |    "type": "wikibase-entityid",
        |    "value": {
        |      "entity-type": "item",
        |      "id": "Q1860",
        |      "numeric-id": 1860
        |    }
        |  },
        |  "hash": "cc2c15fb58feeae8fe7a641c0e2ddaf3f48e59f8",
        |  "property": "P364",
        |  "snaktype": "value"
        |  }
        |}""".stripMargin

    println(decode[Claim](j))
  }

  it should "globecoordinate" in {
    val j = """
      |{
      |  "id": "q4351904$3FB44614-FD58-409C-A63A-0E2245A92947",
      |  "mainsnak": {
      |    "datatype": "globe-coordinate",
      |    "datavalue": {
      |      "type": "globecoordinate",
      |      "value": {
      |        "altitude": null,
      |        "globe": "http://www.wikidata.org/entity/Q2",
      |        "latitude": -27.858888888889,
      |        "longitude": 153.31472222222,
      |        "precision": null
      |      }
      |    },
      |    "hash": "1c36884bcd04f52887c2153054faed47db62cea3",
      |    "property": "P625",
      |    "snaktype": "value"
      |  },
      |  "rank": "normal",
      |  "type": "statement"
      |}""".stripMargin

    println(decode[Claim](j))
  }
}
