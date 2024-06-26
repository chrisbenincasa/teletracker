package com.teletracker.tasks.util

import com.twitter.util.StorageUnit
import java.io.{
  BufferedOutputStream,
  File,
  FileOutputStream,
  OutputStream,
  PrintStream
}
import java.net.URI
import java.nio.file.{Files, Paths}
import scala.compat.java8.StreamConverters._

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
    outputPath: Option[String],
    append: Boolean = false
  ): FileRotator = {
    new FileRotator(
      baseFileName,
      RotateEveryNBytes(every),
      outputPath,
      append = append
    )
  }

  def everyNLines(
    baseFileName: String,
    every: Int,
    outputPath: Option[String],
    append: Boolean = false
  ): FileRotator = {
    new FileRotator(
      baseFileName,
      RotateEveryNLines(every),
      outputPath,
      append = append
    )
  }

  def everyNLinesOrSize(
    baseFileName: String,
    lines: Int,
    size: StorageUnit,
    outputPath: Option[String],
    append: Boolean = false
  ): FileRotator = {
    new FileRotator(baseFileName, RotateEither(size, lines), outputPath, append)
  }
}

class FileRotator(
  baseFileName: String,
  rotationMethod: RotationMethod,
  outputPath: Option[String],
  append: Boolean = false) {

  if (outputPath.isDefined) {
    val f = new File(outputPath.get)
    if (!f.exists() && !f.mkdirs()) {
      throw new RuntimeException(
        s"Could not make output path: ${outputPath.get}"
      )
    }
  }

  private var (idx, currFile) = {
    val initialFile = getFile(0)

    if (append) {
      val basePath = initialFile.getAbsolutePath
        .split(File.separatorChar)
        .init
        .mkString(File.separator)
      val existingFiles = Files
        .list(Paths.get(URI.create("file://" + basePath)))
        .toScala[Stream]
        .filter(
          path =>
            path
              .getName(path.getNameCount - 1)
              .toString
              .startsWith(baseFileName)
        )
        .toList

      existingFiles match {
        case Nil => 0 -> initialFile
        case files =>
          val startingIdx = files
            .map(path => {
              path
                .getName(path.getNameCount - 1)
                .toString
                .split('.')
                .apply(1)
                .toInt
            })
            .max
          startingIdx -> getFile(startingIdx)
      }
    } else {
      0 -> initialFile
    }
  }

  private var currBytes = {
    if (append && currFile.exists()) {
      Files.size(Paths.get(currFile.toURI))
    } else {
      0L
    }
  }
  private var currLines = {
    if (append && currFile.exists()) {
      Files.lines(Paths.get(currFile.toURI)).count()
    } else {
      0L
    }
  }

  private var os = new PrintStream(
    new BufferedOutputStream(new FileOutputStream(currFile, append))
  )

  private var isClosed = false

  println(
    s"Beginning file rotation at: ${currFile.getAbsolutePath}. (bytes=${currBytes}, lines=${currLines})"
  )

  def baseUri: URI =
    new File(outputPath.getOrElse(System.getProperty("user.dir"))).toURI

  def writeLine(line: String): Unit = writeLines(Seq(line))

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
    currFile = getFile(idx)

    println(s"Rotating to $currFile")

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
        currBytes + calculateBytes(lines) > amount.bytes

      case RotateEveryNLines(amount) =>
        currLines + calculateLines(lines) > amount

      case RotateEither(amount, maxLines) =>
        (currBytes + calculateLines(lines) > amount.bytes) || (currLines + calculateBytes(
          lines
        ) > maxLines)
    }
  }

  private def getFile(idx: Int) = {
    new File(
      outputPath.getOrElse(System.getProperty("user.dir")),
      f"$baseFileName.$idx%03d.txt"
    )
  }
}

class CountingOutputStream(delegate: OutputStream) extends OutputStream {
  private var _count = 0L

  def getCount: Long = _count

  override def write(b: Int): Unit = {
    delegate.write(b)
    _count += 1
  }

  override def write(b: Array[Byte]): Unit = {
    delegate.write(b)
    _count += b.length
  }

  override def write(
    b: Array[Byte],
    off: Int,
    len: Int
  ): Unit = {
    delegate.write(b, off, len)
    _count += len
  }
}
