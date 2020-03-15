package com.teletracker.tasks.general

import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs

class LoggingTask extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    logger.info(s"${taskId}: Hello")
    logger.info(s"${taskId}: Goodbye")
    logger.info(s"${taskId}: Goodnight")
  }
}
