package com.teletracker.common.logging

import ch.qos.logback.core.rolling.helper.FileNamePattern
import ch.qos.logback.core.rolling.{
  FixedWindowRollingPolicy,
  RollingFileAppender
}
import com.twitter.concurrent.NamedPoolThreadFactory
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.File
import java.util.concurrent.{Executors, TimeUnit}
import scala.util.control.NonFatal

class S3RollingAppender extends RollingFileAppender {}

object S3RollingPolicy {
  private val uploadPool = Executors.newSingleThreadExecutor(
    new NamedPoolThreadFactory("s3-log-roller-pool")
  )
}

class S3RollingPolicy extends FixedWindowRollingPolicy {
  import S3RollingPolicy._

  private var _fileNamePattern: FileNamePattern = _
  private var s3: S3Client = _
  private var bucketName: String = _
  private var keyPrefix: String = _
  private var onClose: () => Unit = _

  override def start(): Unit = {
    super.start()

    _fileNamePattern = new FileNamePattern(getFileNamePattern, this.context)
  }

  override def stop(): Unit = {
    super.stop()

    try {
      uploadFileToS3(getActiveFileName)

      uploadPool.shutdown()
      uploadPool.awaitTermination(30, TimeUnit.SECONDS)
    } catch {
      case NonFatal(e) =>
        addError("Failed uploading final log to S3", e)
        uploadPool.shutdownNow()
    }
  }

  override def rollover(): Unit = {
    super.rollover()

    val rolledFileName = _fileNamePattern.convertInt(getMinIndex)
    uploadFileToS3(rolledFileName)
  }

  protected def uploadFileToS3(name: String): Unit = {
    val file = new File(name)

    if (file.exists() || file.length() > 0) {
      addInfo(s"Uploading ${file.getName} to S3")

      val objectName = s"$getKeyPrefix/${file.getName}"

      uploadPool.submit(new Runnable {
        override def run(): Unit = {
          try {
            getS3Client().putObject(
              PutObjectRequest
                .builder()
                .bucket(getBucketName)
                .key(objectName)
                .build(),
              file.toPath
            )

            file.delete()
          } catch {
            case NonFatal(e) => e.printStackTrace()
          }
        }
      })
    }
  }

  def getS3Client() = {
    if (s3 == null) {
      s3 = S3Client.create()
    }

    s3
  }

  def setS3Client(s3Client: S3Client): Unit = {
    s3 = s3Client
  }

  def getBucketName: String = {
    bucketName
  }

  def getKeyPrefix: String = {
    keyPrefix
  }

  def getOnClose: () => Unit = {
    onClose
  }

  def setBucketName(name: String) = {
    bucketName = name
  }

  def setKeyPrefix(prefix: String) = {
    keyPrefix = prefix
  }

  def setOnClose(close: () => Unit): Unit = {
    onClose = close
  }
}
