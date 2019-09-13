package com.teletracker.tasks.elasticsearch

import com.twitter.util.StorageUnit
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}

class FileRotator(
  baseFileName: String,
  rotateEvery: StorageUnit,
  outputPath: Option[String]) {

  private val maxBytes = rotateEvery.bytes
  private var idx = 0
  private var currFile =
    new File(
      outputPath.getOrElse(System.getProperty("user.dir")),
      s"$baseFileName.$idx.txt"
    )
  private var os = new PrintStream(
    new BufferedOutputStream(new FileOutputStream(currFile))
  )
  private var currTotal = 0L
  private var isClosed = false

  def writeLines(lines: Seq[String]): Unit = synchronized {
    if (isClosed)
      throw new IllegalStateException("Tried to write after closing!!")

    // If the total plus a new line would make us go over, start a new file
    val total = (lines.mkString("") + System.lineSeparator()).getBytes().length

    if (currTotal + total > maxBytes) {
      // Add newline at end of file and rorate
      os.println()
      rotate()
    }

    currTotal += total

    lines.foreach(os.println)
  }

  def finish(): Unit = synchronized {
    os.flush()
    os.close()
    isClosed = true
  }

  private def rotate(): Unit = {
    os.flush()
    os.close()

    currTotal = 0L
    idx += 1
    currFile = new File(
      outputPath.getOrElse(System.getProperty("user.dir")),
      s"$baseFileName.$idx.txt"
    )
    os = new PrintStream(
      new BufferedOutputStream(new FileOutputStream(currFile))
    )
  }
}
