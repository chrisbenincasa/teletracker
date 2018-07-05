package com.chrisbenincasa.services.teletracker.tools

import com.chrisbenincasa.services.teletracker.config.TeletrackerConfig
import com.chrisbenincasa.services.teletracker.inject.Modules
import com.google.inject.Module
import com.twitter.inject.app.App
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import scala.concurrent.ExecutionContext.Implicits.global

object RunDatabaseMigration extends App {
  override protected def modules: Seq[Module] = Modules()

  override protected def run(): Unit = {
    val runConfig = new RunDatabaseMigrationParser().parse(args, RunDatabaseMigrationConfig()).getOrElse(throw new IllegalArgumentException)

    def withColor(colorCode: String, text: String): String = {
      val code = if (runConfig.color) colorCode else ""
      val reset = if (runConfig.color) Console.RESET else ""

      s"$code$text$reset"
    }

    val config = injector.instance[TeletrackerConfig]
    val dataSource = injector.instance[DataSource]

    val flyway = new Flyway()

    val path = config.db.driver match {
      case _: org.h2.Driver => "h2"
      case _: org.postgresql.Driver => "postgres"
      case x => throw new IllegalArgumentException(s"Unsupported datasource class: ${x.getClass.getSimpleName}")
    }

    flyway.setLocations(s"classpath:db/migration/$path")
    flyway.setDataSource(dataSource)

    println(withColor(Console.BLUE, s"About to run action: ${runConfig.action}"))

    runConfig.action match {
      case "migrate" => flyway.migrate()
      case "info" => flyway.info()
      case "clean" => flyway.clean()
      case _ => throw new IllegalArgumentException(s"Unsupported action ${runConfig.action}")
    }
  }
}

private[tools] case class RunDatabaseMigrationConfig(color: Boolean = true, action: String = "migrate")

private[tools] class RunDatabaseMigrationParser extends scopt.OptionParser[RunDatabaseMigrationConfig]("RunDatabaseMigration") {
  case class Cmd(action: String, description: String = "")
  val info = Cmd("info", "Print information about the current database migration")
  val migrate = Cmd("migrate", "Run migration steps for the database")
  val clean = Cmd("clean")

  help("help")

  opt[Unit]("no-color").action((_, c) => c.copy(color = false))

  Seq(info, migrate, clean).foreach(command => {
    cmd(command.action).action((_, c) => c.copy(action = command.action)).text(command.description)
  })

}