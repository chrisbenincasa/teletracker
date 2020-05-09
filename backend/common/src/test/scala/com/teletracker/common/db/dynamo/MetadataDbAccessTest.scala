package com.teletracker.common.db.dynamo

import com.google.inject.Guice
import com.teletracker.common.inject.Modules
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Futures._
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.flatspec.AnyFlatSpec

class MetadataDbAccessTest extends AnyFlatSpec {
  it should "work" in {
    val injector = Guice.createInjector(Modules(): _*)
    val access = injector.getInstance(classOf[MetadataDbAccess])

//    val list = access
//      .getAllNetworks()
//      .await()
//
//    println(list)

    println(access.getAllGenres().await())
  }
}
