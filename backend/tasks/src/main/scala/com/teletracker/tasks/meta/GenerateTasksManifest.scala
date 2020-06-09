package com.teletracker.tasks.meta

import com.teletracker.common.tasks.{TeletrackerTask, UntypedTeletrackerTask}
import org.reflections.Reflections
import scala.collection.JavaConverters._

class GenerateTasksManifest extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val reflections = new Reflections("com.teletracker.tasks")

    val subtypes = reflections.getSubTypesOf(classOf[TeletrackerTask])

    subtypes.asScala.filterNot(_.isInterface).foreach(println)
  }
}
