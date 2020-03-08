package com.teletracker.common.db.dynamo

import com.google.inject.Guice
import com.teletracker.common.inject.Modules
import org.scalatest.FlatSpec
import java.util.UUID
import com.teletracker.common.util.Futures._
import scala.concurrent.ExecutionContext.Implicits.global

class ListsDbAccessTest extends FlatSpec {
  "The test" should "work" in {
    val injector = Guice.createInjector(Modules(): _*)
    val access = injector.getInstance(classOf[ListsDbAccess])

//    val list = access
//      .getList(
//        "0d544a5e-c32b-4af0-8c47-88c34cdd0152",
//        UUID.fromString("fade838b-32fb-43f0-8ca3-2e5ccd7b5040")
//      )
//      .await()
//
//    println(list)

    val list = access
      .getDefaultList(
        "0d544a5e-c32b-4af0-8c47-88c34cdd0152"
      )
      .await()

    println(list)
  }
}
