package com.teletracker.consumers

import com.google.inject.Guice
import com.teletracker.common.tasks.TeletrackerTask
import com.teletracker.consumers.inject.Modules
import com.twitter.inject.Injector
import org.reflections.Reflections
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import java.lang.reflect.Modifier
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

class InjectTest extends AnyFlatSpecLike with ScalaCheckPropertyChecks {
  it should "inject each consumer type" in {
    forAll(Table("runMode", RunMode.all: _*)) { mode =>
      val injector = Guice.createInjector(Modules(): _*)
      QueueConsumerDaemon.listenerForMode(Injector(injector), mode)
    }
  }

  it should "inject all tasks" in {
    val injector = Guice.createInjector(Modules(): _*)
    val reflections = new Reflections("com.teletracker")
    val allTasks = reflections
      .getSubTypesOf(classOf[TeletrackerTask])
      .asScala
      .filterNot(_.isInterface)
      .filterNot(clazz => Modifier.isAbstract(clazz.getModifiers))

    allTasks.foreach(clazz => {
      injector.getInstance(clazz)
    })
  }
}
