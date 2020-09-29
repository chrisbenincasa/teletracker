package com.teletracker.tasks.util

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{
  CompressionType,
  ExpressionType,
  InputSerialization,
  JSONInput,
  JSONOutput,
  JSONType,
  OutputSerialization,
  SelectObjectContentEvent,
  SelectObjectContentEventVisitor,
  SelectObjectContentRequest
}
import com.teletracker.common.inject.NamedFixedThreadPoolFactory
import com.teletracker.common.util.S3Uri
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import java.io.{File, FileOutputStream}
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import scala.concurrent.{Future, Promise}

class S3Selector(
  client: AmazonS3,
  val maxOutstanding: Int = 5) {
  private val logger = LoggerFactory.getLogger(getClass)

  private lazy val blockingPool =
    NamedFixedThreadPoolFactory.create(maxOutstanding, "s3-selector")

  def select(
    bucket: String,
    key: String,
    query: String,
    outputDir: Path
  ): Future[File] = {
    Future {
      val latch = new CountDownLatch(1)
      val request = generateSelectRequest(bucket, key, query)
      val result = client.selectObjectContent(request)

      val inputStream = result.getPayload.getRecordsInputStream(
        new SelectObjectContentEventVisitor {
          override def visit(
            event: SelectObjectContentEvent.StatsEvent
          ): Unit = {
            logger.debug(
              s"Transferring s3://${bucket}/${key}: ${event.getDetails.getBytesProcessed} bytes processed so far"
            )
          }

          override def visit(event: SelectObjectContentEvent.EndEvent): Unit = {
            logger.debug(s"Done transferring for: ${bucket}/${key}")
            latch.countDown()
          }
        }
      )

      val outputFilePath = key.replaceAll("/", "_")

      logger.info(
        s"Outputting s3://${bucket}/${key} to ${outputDir.toAbsolutePath}/${outputFilePath}"
      )

      val outputFile = new File(outputDir.toFile, outputFilePath)
      val fos = new FileOutputStream(outputFile)

      IOUtils.copy(inputStream, fos)

      latch.countDown()
      fos.close()

      outputFile
    }(blockingPool.asExecutionContext)
  }

  private def generateSelectRequest(
    bucket: String,
    key: String,
    query: String
  ) = {
    val req = new SelectObjectContentRequest()
    req.setBucketName(bucket)
    req.setKey(key)
    req.setExpression(query)
    req.setExpressionType(ExpressionType.SQL)

    val input = new InputSerialization
    input.setCompressionType(CompressionType.NONE)
    input.setJson(new JSONInput().withType(JSONType.LINES))
    req.setInputSerialization(input)

    val output = new OutputSerialization
    output.setJson(new JSONOutput())
    req.setOutputSerialization(output)

    req
  }
}
