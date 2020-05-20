package com.teletracker.common.config.sources

import com.teletracker.common.config.core.sources.{
  AppName,
  Source,
  SourceFactory
}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{
  GetObjectRequest,
  NoSuchKeyException
}
import scala.util.Try
import scala.util.control.NonFatal

case class Bucket(value: String) extends AnyVal

case class S3SourceConfig(
  bucket: String,
  key_prefix: String,
  global_config_name: String)

class S3SourceFactory(s3: S3Client) extends SourceFactory {
  def this() = {
    this(S3Client.create())
  }

  override def source(
    config: Config,
    appName: AppName
  ): Source = {
    val sourceConfig = config.as[S3SourceConfig]("config")

    new S3Source(s3, appName, sourceConfig)
  }
}

class S3Source(
  s3: S3Client,
  val appName: AppName,
  config: S3SourceConfig)
    extends Source {
  protected val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  private lazy val appOverrideFile =
    s"${config.key_prefix}/${appName.value}.conf"
  private lazy val globalOverrideFile =
    s"${config.key_prefix}/${config.global_config_name}.conf"

  private lazy val bucket = Bucket(config.bucket)

  override def description: String =
    s"S3 source for ${appName.value}. Bucket: ${bucket.value}, key: ${appOverrideFile}"

  override def contents(): Option[String] = {
    val overridenConf =
      readFromS3(bucket, appOverrideFile).flatMap { appConf =>
        readFromS3(bucket, globalOverrideFile).map { globalConf =>
          Some(
            appConf.getOrElse("") + System.lineSeparator() + globalConf
              .getOrElse("")
          )
        }
      }

    overridenConf.getOrElse(None)
  }

  private def readFromS3(
    bucket: Bucket,
    key: String
  ): Try[Option[String]] = {
    Try {
      val data = s3
        .getObjectAsBytes(
          GetObjectRequest.builder().bucket(bucket.value).key(key).build()
        )
        .asUtf8String()

      if (data.trim.isEmpty) {
        None
      } else {
        Some(data)
      }
    }.recover {
      case ex: NoSuchKeyException =>
        // if it doesn't exist, there's no data so we should remove it

        logger.debug(
          s"S3 source for ${appName} does not exist (s3://${bucket.value}/${key}), returning empty"
        )

        None
      case t @ NonFatal(ex) =>
        logger.warn("Unable to reach remote override", ex)

        throw t
    }
  }
}
