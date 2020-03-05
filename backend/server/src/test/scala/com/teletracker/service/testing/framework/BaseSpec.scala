package com.teletracker.service.testing.framework

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.google.inject.util.{Modules => GuiceModules}
import com.google.inject.{Binder, Guice, Module}
import com.teletracker.service.TeletrackerServer
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.inject.{Modules}
import com.twitter.finatra.http.EmbeddedHttpServer
import com.typesafe.config.ConfigFactory
import org.scalatest.{Assertions, BeforeAndAfterAll, FlatSpec, Inside}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

trait BaseSpec
    extends FlatSpec
    with Assertions
    with Inside
    with BeforeAndAfterAll {
  implicit val execCtx = scala.concurrent.ExecutionContext.Implicits.global

  implicit class RichFuture[T](f: Future[T]) {
    def await() = Await.result(f, Duration.Inf)
  }

  def startDb = true

  lazy val modules = {
    val overrides = Seq(
      new Module {
        import net.ceedubs.ficus.Ficus._
        import net.ceedubs.ficus.readers.ArbitraryTypeReader._

        override def configure(binder: Binder): Unit = {
          val loader =
            ConfigFactory
              .defaultOverrides()
              .withFallback(ConfigFactory.parseResources("test.conf"))
              .withFallback(ConfigFactory.defaultApplication())
              .resolve()

          val loaded = loader.as[TeletrackerConfig]("teletracker")
          binder.bind(classOf[TeletrackerConfig]).toInstance(loaded)
        }
      }
    )

    Seq(GuiceModules.`override`(Modules(): _*).`with`(overrides: _*))
  }

  lazy val injector = Guice.createInjector(modules: _*)
}

trait BaseSpecWithServer extends BaseSpec {
  lazy val server = new EmbeddedHttpServer(new TeletrackerServer(modules))
  lazy val serializer =
    server.injector.instance[ObjectMapper with ScalaObjectMapper]

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    server.start()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    server.close()
  }
}
