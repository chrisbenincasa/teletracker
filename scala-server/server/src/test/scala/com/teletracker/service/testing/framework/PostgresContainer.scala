package com.teletracker.service.testing.framework

import com.google.inject.Injector
import com.spotify.docker.client.DockerClient.AttachParameter.{
  LOGS,
  STDERR,
  STDOUT,
  STREAM
}
import com.spotify.docker.client.messages._
import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import com.teletracker.common.db.model._
import com.teletracker.common.inject.SyncDbProvider
import javax.sql.DataSource
import net.codingwell.scalaguice.InjectorExtensions._
import org.flywaydb.core.Flyway
import slick.jdbc.DriverDataSource
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Random

object PostgresContainer {
  private val r = new Random()
}

class PostgresContainer(
  val client: DockerClient = DefaultDockerClient.fromEnv().build()
)(implicit executionContext: ExecutionContext) {
  import PostgresContainer._

  var container: ContainerInfo = _

  def initialize() = {
    val target = ephemeralPort

    val portBindings = Map(
      "5432" -> List(PortBinding.of("0.0.0.0", target)).asJava
    ).asJava

    val hostConfig = HostConfig
      .builder()
      .portBindings(portBindings)
      .lxcConf(
        new HostConfig.LxcConfParameter {
          override def key(): String = "icc"

          override def value(): String = "false"
        }
      )
      .build()

    val containerConfig = ContainerConfig
      .builder()
      .hostConfig(hostConfig)
      .env(
        "POSTGRES_USER=teletracker",
        "POSTGRES_PASSWORD=teletracker",
        "POSTGRES_DB=teletracker"
      )
      .image("postgres:10.4")
      .exposedPorts("5432")
      .build()

    try {
      client.pull("postgres:10.4")
      client.inspectImage("postgres:10.4")
    } catch {
      case _: Exception =>
        client.pull("postgres:10.4")
    }

    val createdContainer = client.createContainer(containerConfig)

    client.startContainer(createdContainer.id())

    waitForLogLine(createdContainer.id())

    val containerInfo = client.inspectContainer(createdContainer.id())

    container = containerInfo
  }

  def shutdown() = {
    if (container ne null) {
      client.killContainer(container.id())
    }
  }

  def getMappedPort(p: Int): Option[Int] = {
    container
      .networkSettings()
      .ports()
      .asScala
      .get(s"$p/tcp")
      .flatMap(_.asScala.headOption)
      .map(_.hostPort().toInt)
  }

  lazy val dataSource = {
    new DriverDataSource(
      url =
        s"jdbc:postgresql://localhost:${getMappedPort(5432).get}/teletracker",
      user = "teletracker",
      password = "teletracker",
      driverObject = new org.postgresql.Driver
    )
  }

  lazy val dbProvider = new SyncDbProvider(dataSource)

  def createAllTables(injector: Injector) = {
    val flyway = Flyway
      .configure()
      .dataSource(dataSource)
      .locations("classpath:db/migration/postgres")
      .load()
    flyway.migrate()
  }

  private def ephemeralPort = r.nextInt(30000) + 15000

  private def waitForLogLine(containerId: String) = {
    val lock = new CountDownLatch(1)

    val t = new Thread(new Runnable {
      override def run(): Unit = {
        var matched = false
        var logs = ""
        val stream = try {
          client.attachContainer(containerId, LOGS, STDERR, STDOUT, STREAM)
        } catch {
          case e: Exception =>
            e.printStackTrace()
            return
        }

        do {
          if (!stream.hasNext) {
            try Thread.sleep(10)
            catch { case _: InterruptedException => }
          } else {
            val msg = stream.next()
            val buf = msg.content()
            val bytes = new Array[Byte](buf.remaining())
            buf.get(bytes)
            val newLogs = new String(bytes)
            logs += newLogs
            if ("database system is ready to accept connections".r
                  .findAllIn(logs)
                  .size == 2) {
              matched = true
            }
          }
        } while (!matched)

        lock.countDown()
      }
    })

    t.setDaemon(true)

    t.run()

    if (!lock.await(30, TimeUnit.SECONDS)) {
      println("Timeout reached, attempting to connect to container anyway")
    }
  }
}