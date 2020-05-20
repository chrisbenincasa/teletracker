package com.teletracker.common.config.core.sources

import java.io.File
import org.apache.commons.io.FileUtils

/**
  * Read from a source and write the results to a file
  *
  * @param source
  * @param overrideFile
  */
class SourceWriter(
  source: Source,
  overrideFile: File) {
  protected val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def writeContents(newContent: String): Unit = {
    FileUtils.forceMkdirParent(overrideFile)

    val trimmedContent = newContent.trim

    val writeFile =
      (!overrideFile.exists() ||
        trimmedContent != FileUtils
          .readFileToString(overrideFile, "utf-8")
          .trim) &&
        trimmedContent.nonEmpty

    val deleteFile = overrideFile.exists() && trimmedContent.length == 0

    if (writeFile) {
      if (!overrideFile.exists()) {
        logger.info(
          s"Local override file ${overrideFile.getAbsolutePath} does not exist"
        )
      }

      logger.info(
        s"Remote configuration contents has changed, writing to new data to ${overrideFile.getAbsolutePath}!"
      )

      FileUtils.writeStringToFile(overrideFile, trimmedContent, "utf-8")
    } else if (deleteFile) {
      logger.info(
        s"Remote configuration contents is missing, deleting override file ${overrideFile.getAbsolutePath}!"
      )

      overrideFile.delete()
    }
  }

  def read(): Option[String] = {
    try {
      source.contents()
    } catch {
      case e: Exception =>
        logger.warn(
          s"Unable to pull remote contents for source ${source.appName.value}:${source.getClass}!",
          e
        )

        None
    }
  }
}
