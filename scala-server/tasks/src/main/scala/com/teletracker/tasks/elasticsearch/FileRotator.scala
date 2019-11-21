package com.teletracker.tasks.elasticsearch

import com.twitter.util.StorageUnit
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}

sealed trait RotationMethod
case class RotateEveryNBytes(amount: StorageUnit) extends RotationMethod
case class RotateEveryNLines(amount: Int) extends RotationMethod
case class RotateEither(
  amount: StorageUnit,
  lines: Int)
    extends RotationMethod

object FileRotator {
  def everyNBytes(
    baseFileName: String,
    every: StorageUnit,
    outputPath: Option[String]
  ): FileRotator = {
    new FileRotator(baseFileName, RotateEveryNBytes(every), outputPath)
  }
}

class FileRotator(
  baseFileName: String,
  rotationMethod: RotationMethod,
  outputPath: Option[String]) {

  if (outputPath.isDefined) {
    val f = new File(outputPath.get)
    if (!f.exists() && !f.mkdirs()) {
      throw new RuntimeException(
        s"Could not make output path: ${outputPath.get}"
      )
    }
  }

  private var currBytes = 0L
  private var currLines = 0L

  private var idx = 0
  private var currFile =
    new File(
      outputPath.getOrElse(System.getProperty("user.dir")),
      s"$baseFileName.$idx.txt"
    )
  private var os = new PrintStream(
    new BufferedOutputStream(new FileOutputStream(currFile))
  )

  private var isClosed = false

  def writeLines(lines: Seq[String]): Unit = synchronized {
    if (isClosed)
      throw new IllegalStateException("Tried to write after closing!!")

    if (exceedsLimit(lines)) {
      // Add newline at end of file and rotate
      os.println()
      rotate()
    }

    updateCurrentCounts(lines)

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

    currLines = 0L
    currBytes = 0L
    idx += 1
    currFile = new File(
      outputPath.getOrElse(System.getProperty("user.dir")),
      s"$baseFileName.$idx.txt"
    )
    os = new PrintStream(
      new BufferedOutputStream(new FileOutputStream(currFile))
    )
  }

  private def calculateBytes(lines: Seq[String]) = {
    (lines.mkString("\n") + System.lineSeparator()).getBytes().length
  }

  private def calculateLines(lines: Seq[String]) = {
    lines.size
  }

  private def calculate(lines: Seq[String]) = {
    rotationMethod match {
      // If the total plus a new line would make us go over, start a new file
      case RotateEveryNBytes(_) =>
        calculateBytes(lines)

      case RotateEveryNLines(_) =>
        calculateLines(lines)

      case RotateEither(amount, maxLines) =>
        ???
    }
  }

  private def updateCurrentCounts(lines: Seq[String]) = {
    rotationMethod match {
      case RotateEveryNBytes(_) =>
        currBytes += calculateBytes(lines)

      case RotateEveryNLines(amount) =>
        currLines += calculateLines(lines)

      case RotateEither(amount, maxLines) =>
        currBytes += calculateBytes(lines)
        currLines += calculateLines(lines)
    }
  }

  private def exceedsLimit(lines: Seq[String]) = {
    rotationMethod match {
      case RotateEveryNBytes(amount) =>
        currBytes + calculate(lines) > amount.bytes

      case RotateEveryNLines(amount) =>
        currLines + calculate(lines) > amount

      case RotateEither(amount, maxLines) =>
        (currBytes + calculate(lines) > amount.bytes) || (currLines + calculate(
          lines
        ) > maxLines)
    }
  }

}
