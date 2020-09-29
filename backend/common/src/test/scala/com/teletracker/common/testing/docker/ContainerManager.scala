package com.teletracker.common.testing.docker

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.{
  ExposedPort,
  Frame,
  HostConfig,
  PortBinding,
  StreamType
}
import com.github.dockerjava.core.{
  DefaultDockerClientConfig,
  DockerClientConfig,
  DockerClientImpl
}
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient
import com.teletracker.common.testing.EphemeralPorts
import com.teletracker.common.util.Functions._
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

class ContainerManager(
  dockerConfig: DockerClientConfig =
    DefaultDockerClientConfig.createDefaultConfigBuilder().build()) {
  protected val logger = LoggerFactory.getLogger(getClass)

  protected val dockerHttpClient = new ZerodepDockerHttpClient.Builder()
    .dockerHost(dockerConfig.getDockerHost)
    .sslConfig(dockerConfig.getSSLConfig)
    .build()

  protected val dockerClient =
    DockerClientImpl.getInstance(dockerConfig, dockerHttpClient)

  def create(config: ContainerConfig): Container = {
    if (config.pullAlways) {
      logger.info(s"Pulling image ${config.imageName}...")
      dockerClient
        .pullImageCmd(config.imageName)
        .start()
        .awaitCompletion()
    }

    logger.info(s"Creating container: ${config.imageName}")

    val container = dockerClient
      .createContainerCmd(config.imageName)
      .applyOptional(config.port)(
        (cmd, port) =>
          cmd.withHostConfig(
            HostConfig
              .newHostConfig()
              .withPortBindings(
                PortBinding.parse(s"${EphemeralPorts.get}:$port")
              )
          )
      )
      .withEnv(config.envVars: _*)
      .exec()

    val containerId = container.getId

    logger.info(
      s"Created container: ${config.imageName} with id ${containerId}"
    )
    logger.info(
      s"Starting container: ${config.imageName} with id ${containerId}"
    )

    dockerClient.startContainerCmd(containerId).exec()

    if (config.waitForLogLine.exists(_.nonEmpty)) {
      logger.info(s"Waiting for log line: ${config.imageName}")

      val latch = new CountDownLatch(1)
      val thread = new Thread(() => {
        val stdout = new StringBuffer("")
        val stderr = new StringBuffer("")

        dockerClient
          .logContainerCmd(containerId)
          .withStdOut(true)
          .withStdErr(true)
          .withFollowStream(true)
          .withTailAll()
          .exec(
            new ResultCallback.Adapter[Frame] {
              override def onNext(frame: Frame): Unit = {
                frame.getStreamType match {
                  case StreamType.STDIN =>
                  case StreamType.STDOUT =>
                    stdout.append(new String(frame.getPayload))
                  case StreamType.STDERR =>
                    stderr.append(new String(frame.getPayload))
                  case StreamType.RAW =>
                }

                if (stdout.toString
                      .contains(config.waitForLogLine.get) || stderr.toString
                      .contains(config.waitForLogLine.get)) {
                  latch.countDown()
                  close()
                }
              }
            }
          )
      })

      thread.setDaemon(true)
      thread.start()

      val res = if (config.maxWaitForLogLine.isDefined) {
        latch.await(
          config.maxWaitForLogLine.get.toMillis,
          TimeUnit.MILLISECONDS
        )
      } else {
        Try(latch.await()) match {
          case Success(_)                       => true
          case Failure(_: InterruptedException) => false
          case Failure(ex)                      => throw ex
        }
      }

      if (!res) {
        logger.warn(
          "Log line never appeared in a timely manner, attempting to continue."
        )
      }
    }

    val portMap = config.port
      .flatMap(p => {
        dockerClient
          .inspectContainerCmd(containerId)
          .exec()
          .getNetworkSettings
          .getPorts
          .getBindings
          .asScala
          .get(ExposedPort.tcp(p))
          .flatMap(_.headOption.map(_.getHostPortSpec.toInt))
          .map(p -> _)
      })
      .map(Map(_))
      .getOrElse(Map())

    logger.info(
      s"Running container: ${config.imageName} with id ${containerId}"
    )

    new Container(containerId, portMap, config, this)
  }

  def stop(
    containerId: String,
    remove: Boolean = true
  ): Unit = {
    logger.info(s"Stopping container: ${containerId}")

    dockerClient.stopContainerCmd(containerId).exec()

    if (remove) {
      logger.info(s"Removing container: ${containerId}")
      dockerClient.removeContainerCmd(containerId)
    }
  }
}

class Container(
  val id: String,
  val portMap: Map[Int, Int],
  val config: ContainerConfig,
  private val manager: ContainerManager)
    extends AutoCloseable {
  override def close(): Unit = manager.stop(id, config.removeAfterStop)
}

case class ContainerConfig(
  imageName: String,
  pullAlways: Boolean = true,
  waitForLogLine: Option[String] = None,
  maxWaitForLogLine: Option[FiniteDuration] = None,
  port: Option[Int] = None,
  removeAfterStop: Boolean = true,
  envVars: Seq[String] = Seq())
