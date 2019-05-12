import BuildConfig._

lazy val `teletracker-repo` = Project("teletracker-repo", file(".")).
  settings(
    publish := {},
    publishLocal := {},
    publishArtifact := false
  ).aggregate(
    server
  )

lazy val server = project.in(file("server")).
  settings(
    organization := "com.teletracker",
    name := "teletracker",
    version := s"0.1-${BuildConfig.Revision.revision}",

    // Compilation
    scalaVersion := Compilation.scalacVersion,
    scalacOptions ++= Compilation.scalacOpts,

    libraryDependencies ++= Seq(
      // Config
      "com.iheart" %% "ficus" % "1.4.3",
      "com.github.scopt" %% "scopt" % "3.5.0",

      // Logging
      "ch.qos.logback" % "logback-classic" % "1.2.3",

      // Service
      "com.twitter" %% "finagle-core" % versions.twitter,
      "com.twitter" %% "finagle-http" % versions.twitter,
      "com.twitter" %% "finatra-http" % versions.twitter,

      // Db
      "com.typesafe.slick" %% "slick" % "3.2.3",
      "com.typesafe.slick" %% "slick-codegen" % "3.2.3",
      "com.github.tminglei" %% "slick-pg" % "0.16.2",
      "com.github.tminglei" %% "slick-pg_circe-json" % "0.16.2",
      "com.github.tminglei" %% "slick-pg_joda-time" % "0.16.2",
      "org.postgresql" % "postgresql" % "42.2.2",
      "org.flywaydb" % "flyway-core" % "5.1.3",
      "com.h2database" % "h2" % "1.4.193",

      // Auth
      "io.jsonwebtoken" % "jjwt" % "0.9.0",

      // Misc
      "org.typelevel" %% "cats-core" % "1.1.0",
      "com.google.guava" % "guava" % "20.0",
      "com.lihaoyi" %% "fastparse" % "2.1.0",

      // Testing
      "com.spotify" % "docker-client" % "8.11.7" % Test excludeAll "com.fasterxml.jackson.core",
      "org.scalatest" %% "scalatest" % "3.0.5" % Test,

      compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
    ) ++ Dependencies.finatraTest ++ Dependencies.circe,

    // Testing
    Global / concurrentRestrictions := Seq(Tags.limit(Tags.Test, 1)),
    Test / fork := true,

    // Local running / testing
    mainClass in reStart := Some("com.teletracker.service.TeletrackerServerMain"),
    Revolver.enableDebugging(port = 5005, suspend = false),
    envVars in reStart := Map(
      "API_KEY" -> System.getenv("API_KEY"),
      "JWT_SECRET" -> System.getenv("JWT_SECRET")
    ),

    // Assmebly JAR
    mainClass in assembly := Some("com.teletracker.service.Teletracker"),
    test in assembly := {},
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _*) =>
        xs map {_.toLowerCase} match {
          case (x :: Nil) if Seq("manifest.mf", "index.list", "dependencies") contains x =>
            MergeStrategy.discard
          case ps @ (x :: _) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") || ps.last.endsWith(".rsa") =>
            MergeStrategy.discard
          case "maven" :: _ =>
            MergeStrategy.discard
          case "plexus" :: _ =>
            MergeStrategy.discard
          case "services" :: _ =>
            MergeStrategy.filterDistinctLines
          case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) | ("spring.tooling" :: Nil) =>
            MergeStrategy.filterDistinctLines
          case _ => MergeStrategy.first
        }

      case PathList(ps @ _*) if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) =>
        MergeStrategy.rename

      case PathList(ps @ _*) if Assembly.isSystemJunkFile(ps.last) =>
        MergeStrategy.discard

      case _ => MergeStrategy.first
    },

    // Docker
    dockerfile in docker := {
      // The assembly task generates a fat JAR file
      val artifact: File = assembly.value
      val artifactTargetPath = s"/app/bin/${artifact.name}"

      new Dockerfile {
        from("openjdk:8u141-jre-slim")
        add(baseDirectory.value / "src/docker/", "/app")
        add(baseDirectory.value / "data", "/data")
        add(artifact, artifactTargetPath)
        runRaw("chmod +x /app/main.sh")
        entryPoint("/app/main.sh")
      }
    },
    imageNames in docker := Seq(
      ImageName(s"chrisbenincasa/${name.value}:latest"),
      ImageName(
          namespace = Some("chrisbenincasa"),
        repository = name.value,
        tag = Some("v" + version.value)
      )
    ),
    buildOptions in docker := BuildOptions(
      pullBaseImage = BuildOptions.Pull.Always
    ),

    `run-db-migrations` := runInputTask(Runtime, "com.teletracker.service.tools.RunDatabaseMigrationMain").evaluated,

    `reset-db` := Def.sequential(
      `run-db-migrations`.toTask(" -action=clean"),
      `run-db-migrations`.toTask(" -action=migrate"),
      (runMain in Runtime).toTask(" com.teletracker.service.tools.RunAllSeedsMain")
    ).value
  ).
  enablePlugins(FlywayPlugin, DockerPlugin)

lazy val `run-db-migrations` = inputKey[Unit]("generate ddl")
lazy val `reset-db` = taskKey[Unit]("reset-db")

resourceGenerators in Compile += Def.task {
  ((resourceManaged in Compile).value / "db"  ** "*").get
}.taskValue

lazy val showVersion = taskKey[Unit]("Prints version")
showVersion := { println(version.value) }
