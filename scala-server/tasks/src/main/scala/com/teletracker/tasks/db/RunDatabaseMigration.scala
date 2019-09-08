package com.teletracker.tasks.db

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.inject.SyncPath
import com.teletracker.tasks.TeletrackerTask
import javax.inject.Inject
import javax.sql.DataSource
import org.flywaydb.core.Flyway

class RunDatabaseMigration @Inject()(
  teletrackerConfig: TeletrackerConfig,
  @SyncPath dataSource: DataSource)
    extends TeletrackerTask {
  override def run(args: Args): Unit = {
    val action = args.valueOrDefault("action", "info")
    val loc = args.valueOrDefault(
      "loc",
      Option(System.getProperty("java.io.tmpdir")).getOrElse("/tmp")
    )
    val color = args.valueOrDefault("color", true)

    def withColor(
      colorCode: String,
      text: String
    ): String = {
      val code = if (color) colorCode else ""
      val reset = if (color) Console.RESET else ""

      s"$code$text$reset"
    }

    val path = teletrackerConfig.db.driver match {
//      case _: org.h2.Driver         => "h2"
      case _: org.postgresql.Driver => "postgres"
      case x =>
        throw new IllegalArgumentException(
          s"Unsupported datasource class: ${x.getClass.getSimpleName}"
        )
    }

    println(s"Looking in location = ${loc}")

    val flyway = Flyway
      .configure()
      .locations(s"classpath:db/migration/$path", s"filesystem:${loc}")
      .dataSource(dataSource)
      .load()

    println(withColor(Console.BLUE, s"About to run action: ${action}"))

    action match {
      case "migrate" => flyway.migrate()
      case "info"    => flyway.info()
      case "clean"   => flyway.clean()
      case "repair"  => flyway.repair()
      case _ =>
        throw new IllegalArgumentException(
          s"Unsupported action ${action}"
        )
    }
  }
}
