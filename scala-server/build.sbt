import BuildConfig._

Global / cancelable := true

lazy val `teletracker-repo` = Project("teletracker-repo", file("."))
  .settings(
    version := BuildConfig.Revision.wholeVersion,
    publish := {},
    publishLocal := {},
    publishArtifact := false
  )
  .aggregate(
    server,
    common,
    consumer,
    tasks
  )

lazy val common = project
  .in(file("common"))
  .settings(
    organization := "com.teletracker",
    name := "common",
    version := BuildConfig.Revision.wholeVersion,
    // Compilation
    scalaVersion := Compilation.scalacVersion,
    scalacOptions ++= Compilation.scalacOpts,
    libraryDependencies ++= Seq(
      // Twitter
      "com.twitter" %% "util-core" % versions.twitter,
      "com.twitter" %% "inject-core" % versions.twitter,
      "com.twitter" %% "inject-app" % versions.twitter,
      "com.twitter" %% "inject-modules" % versions.twitter,
      "com.twitter" %% "inject-utils" % versions.twitter,
      "com.twitter" %% "inject-slf4j" % versions.twitter,
      // Config
      "com.iheart" %% "ficus" % "1.4.3",
      // Logging
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.google.cloud" % "google-cloud-logging-logback" % "0.102.0-alpha",
      // Monitoring
      "io.opencensus" % "opencensus-api" % "0.23.0",
      "io.opencensus" % "opencensus-impl" % "0.23.0",
      "io.opencensus" % "opencensus-exporter-stats-stackdriver" % "0.23.0",
      // Jackson
      "com.fasterxml.jackson.core" % "jackson-annotations" % versions.jackson,
      // Db
      "com.typesafe.slick" %% "slick" % "3.2.3",
      "com.typesafe.slick" %% "slick-hikaricp" % "3.2.3",
      "com.github.tminglei" %% "slick-pg" % "0.16.2",
      "com.github.tminglei" %% "slick-pg_circe-json" % "0.16.2",
      "com.github.tminglei" %% "slick-pg_joda-time" % "0.16.2",
      "org.postgresql" % "postgresql" % "42.2.2",
      // Auth
      "io.jsonwebtoken" % "jjwt" % "0.9.0",
      // Inject
      "com.google.cloud" % "google-cloud-storage" % "1.84.0",
      "com.google.cloud" % "google-cloud-pubsub" % "1.84.0",
      "com.google.inject" % "guice" % versions.guice,
      "com.google.inject.extensions" % "guice-assistedinject" % versions.guice,
      "com.google.inject.extensions" % "guice-multibindings" % versions.guice,
      "net.codingwell" %% "scala-guice" % versions.scalaGuice,
      // Misc
      // Http
      "org.http4s" %% "http4s-blaze-client" % "0.21.0-M3",
      "commons-codec" % "commons-codec" % "1.13",
      "javax.inject" % "javax.inject" % "1",
      "org.typelevel" %% "cats-core" % "1.1.0",
      "com.google.guava" % "guava" % "20.0",
      "com.lihaoyi" %% "fastparse" % "2.1.0",
      "org.apache.commons" % "commons-lang3" % "3.9",
      "org.apache.commons" % "commons-text" % "1.6",
      compilerPlugin(
        "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full
      )
    ) ++ Dependencies.circe
  )

lazy val consumer = project
  .in(file("consumer"))
  .settings(
    organization := "com.teletracker",
    name := "consumer",
    version := BuildConfig.Revision.wholeVersion,
    // Compilation
    scalaVersion := Compilation.scalacVersion,
    scalacOptions ++= Compilation.scalacOpts,
    libraryDependencies ++= Seq(
      "com.twitter" %% "inject-app" % versions.twitter
    )
  )
  .dependsOn(common)

lazy val tasks = project
  .in(file("tasks"))
  .settings(BuildConfig.commonSettings)
  .settings(BuildConfig.commonAssmeblySettings)
  .settings(
    name := "tasks",
    libraryDependencies ++= Seq(
      "org.flywaydb" % "flyway-core" % "6.0.0-beta2",
      // Google
      "com.github.scopt" %% "scopt" % "3.5.0",
      "org.scalatest" %% "scalatest" % "3.0.5" % Test
    ),
    Compile / run / mainClass := Some(
      "com.teletracker.tasks.TeletrackerTaskRunner"
    ),
    mainClass in assembly := Some(
      "com.teletracker.tasks.TeletrackerTaskRunner"
    ),
    `run-db-migrations` := runInputTask(
      Runtime,
      "com.teletracker.tasks.db.RunDatabaseMigrationMain"
    ).evaluated,
    `reset-db` := Def
      .sequential(
        `run-db-migrations`.toTask(" -action=clean"),
        `run-db-migrations`.toTask(" -action=migrate"),
        (runMain in Runtime)
          .toTask(" com.teletracker.tasks.db.RunAllSeedsMain")
      )
      .value
  )
  .dependsOn(common)

lazy val server = project
  .in(file("server"))
  .settings(BuildConfig.commonSettings)
  .settings(BuildConfig.commonAssmeblySettings)
  .settings(
    name := "server",
    libraryDependencies ++= Seq(
      // Service
      "com.twitter" %% "finagle-core" % versions.twitter,
      "com.twitter" %% "finagle-http" % versions.twitter,
      "com.twitter" %% "finatra-http" % versions.twitter,
      // Testing
      "com.spotify" % "docker-client" % "8.11.7" % Test excludeAll "com.fasterxml.jackson.core",
      "org.scalatest" %% "scalatest" % "3.0.5" % Test,
      "com.h2database" % "h2" % "1.4.193" % Test,
      compilerPlugin(
        "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full
      )
    ) ++ Dependencies.finatraTest,
    // Local running / testing
    mainClass in reStart := Some(
      "com.teletracker.service.TeletrackerServerMain"
    ),
    Revolver.enableDebugging(port = 5005, suspend = false),
    envVars in reStart := Map(
      "API_KEY" -> System.getenv("API_KEY"),
      "JWT_SECRET" -> System.getenv("JWT_SECRET")
    ),
    // Assmebly JAR
    mainClass in assembly := Some("com.teletracker.service.Teletracker"),
    // Docker
    dockerfile in docker := {
      // The assembly task generates a fat JAR file
      val artifact: File = assembly.value
      val artifactTargetPath = s"/app/bin/${artifact.name}"

      new Dockerfile {
        from(
          s"gcr.io/teletracker/base:latest"
        )
        add(baseDirectory.value / "src/docker/", "/app")
        add(baseDirectory.value / "data", "/data")
        add(artifact, artifactTargetPath)
        runRaw("chmod +x /app/main.sh")
        entryPoint("/app/main.sh")
      }
    },
    imageNames in docker := Seq(
      ImageName(
        namespace = Some("gcr.io/teletracker"),
        repository = "server",
        tag = Some("latest")
      ),
      ImageName(
        namespace = Some("gcr.io/teletracker"),
        repository = "server",
        tag = Some(version.value)
      )
    )
  )
  .enablePlugins(FlywayPlugin, DockerPlugin)
  .dependsOn(common, tasks)

lazy val `run-db-migrations` = inputKey[Unit]("generate ddl")
lazy val `reset-db` = taskKey[Unit]("reset-db")

resourceGenerators in Compile += Def.task {
  ((resourceManaged in Compile).value / "db" ** "*").get
}.taskValue

lazy val showVersion = taskKey[Unit]("Prints version")
showVersion := { println(version.value) }
