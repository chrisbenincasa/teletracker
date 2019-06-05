package com.teletracker.service.tools

import com.teletracker.service.db.access.ThingsDbAccess
import com.teletracker.service.util.Futures._
import diffson._
import diffson.lcs._
import diffson.circe._
import diffson.jsonpatch._
import diffson.jsonpatch.lcsdiff._
import io.circe.Json

object ThingDiffer extends TeletrackerJob {
  override protected def runInternal(): Unit = {
    import dbProvider.driver.api._

//    val name = getDupes.await().head
//
//    val thingsDb = injector.instance[ThingsDbAccess]
//
//    val things = thingsDb.findThingsByNormalizedName(name).await()
//
//    val jsons = things.flatMap(_.metadata)
//    implicit val lcs = new Patience[Json]
//
//    val diffResult = diff(jsons.head, jsons(1))
//
//    println(diffResult)

    val res = sql"""
          INSERT INTO "external_ids" (thing_id, tmdb_id) VALUES (2, 1)
            ON CONFLICT ON CONSTRAINT unique_external_ids_thing_tmdb DO UPDATE SET tmdb_id=EXCLUDED.tmdb_id
            RETURNING (thing_id);
        """.as[Int]

    println(dbProvider.getDB.run(res).await())
  }

  private def getDupes = {
    import dbProvider.driver.api._

    val result = sql"""
          select normalized_name from things group by normalized_name having count(*) > 1 order by normalized_name asc;
    """.as[String]

    dbProvider.getDB.run {
      result
    }
  }
}
