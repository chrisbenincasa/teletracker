package com.chrisbenincasa.services.teletracker.testing.framework

import com.chrisbenincasa.services.teletracker.db.model._
import com.chrisbenincasa.services.teletracker.inject.DbProvider
import com.google.inject.Injector
import com.spotify.docker.client.DockerClient.AttachParameter.{LOGS, STDERR, STDOUT, STREAM}
import com.spotify.docker.client.messages._
import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import net.codingwell.scalaguice.InjectorExtensions._
import slick.jdbc.DriverDataSource
import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
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
    val portBindings = Map(
      "5432" -> List(PortBinding.of("0.0.0.0", ephemeralPort)).asJava
    ).asJava

    val hostConfig = HostConfig.builder().
      portBindings(portBindings).
      lxcConf(
        new HostConfig.LxcConfParameter {
          override def key(): String = "icc"

          override def value(): String = "false"
        }
      ).build()

    val containerConfig = ContainerConfig.builder().
      hostConfig(hostConfig).
      env("POSTGRES_USER=teletracker", "POSTGRES_PASSWORD=teletracker", "POSTGRES_DB=teletracker").
      image("postgres").
      exposedPorts("5432").
      build()

    try {
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
    container.networkSettings().ports().asScala.get(s"$p/tcp").flatMap(_.asScala.headOption).map(_.hostPort().toInt)
  }

  lazy val dataSource = {
    new DriverDataSource(
      url = s"jdbc:postgresql://localhost:${getMappedPort(5432).get}/teletracker",
      user = "teletracker",
      password = "teletracker",
      driverObject = new org.postgresql.Driver
    )
  }

  lazy val dbProvider = new DbProvider(dataSource)

  def createAllTables(injector: Injector) = {
    val provider = dbProvider
    import provider.driver.api._

    val createStatements = List(
      injector.instance[Users].query,
      injector.instance[UserCredentials].query,
      injector.instance[Events].query,
      injector.instance[Genres].query,
      injector.instance[Networks].query,
      injector.instance[NetworkReferences].query,
      injector.instance[Things].query,
      injector.instance[ThingNetworks].query,
      injector.instance[TrackedLists].query,
      injector.instance[TrackedListThings].query,
      injector.instance[TvShowEpisodes].query,
      injector.instance[TvShowSeasons].query,
      injector.instance[Availabilities].query,
      injector.instance[ExternalIds].query,
      injector.instance[GenreReferences].query,
      injector.instance[ThingGenres].query,
      injector.instance[Certifications].query,
      injector.instance[PersonThings].query
    ).map(_.schema.create)

    Await.result(
      provider.getDB.run {
        DBIO.sequence(createStatements)
      },
      30 seconds
    )
  }

  private def ephemeralPort = r.nextInt(30000) + 15000

  private def waitForLogLine(containerId: String) = {
    val lock = new CountDownLatch(1)

    val t = new Thread(new Runnable {
      override def run(): Unit = {
        var matchTimes = 0
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
            try Thread.sleep(10) catch { case _: InterruptedException => }
          } else {
            val msg = stream.next()
            val buf = msg.content()
            val bytes = new Array[Byte](buf.remaining())
            buf.get(bytes)
            logs += new String(bytes)
            if (logs.contains("database system is ready to accept connections")) {
              matchTimes += 1
            }
          }
        } while (matchTimes != 2)

        lock.countDown()
      }
    })

    t.setDaemon(true)

    t.run()

    if (!lock.await(30, TimeUnit.SECONDS)) {

    }
  }
}
