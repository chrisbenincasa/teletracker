package com.teletracker.tasks.general

import com.teletracker.common.tasks.UntypedTeletrackerTask

class LoggingTask extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    logger.info(s"${taskId}: Hello")
    logger.info(s"${taskId}: Goodbye")
    logger.info(s"${taskId}: Goodnight")
  }
}
