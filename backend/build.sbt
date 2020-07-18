import BuildConfig._

scalafixDependencies in ThisBuild +=
  "org.scalatest" %% "autofix" % "3.1.0.0"

addCompilerPlugin(scalafixSemanticdb)

Global / cancelable := true

lazy val `teletracker` = Project("teletracker", file("."))
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
  .settings(BuildConfig.commonSettings)
  .settings(
    name := "common",
    // Compilation
    libraryDependencies ++= Seq(
      // Twitter
      "com.twitter" %% "util-core" % versions.twitter,
      "com.twitter" %% "inject-core" % versions.twitter,
      "com.twitter" %% "inject-app" % versions.twitter exclude (
        "org.slf4j",
        "jcl-over-slf4j"
      ),
      "com.twitter" %% "inject-modules" % versions.twitter,
      "com.twitter" %% "inject-utils" % versions.twitter,
      "com.twitter" %% "inject-slf4j" % versions.twitter,
      // Config
      "com.iheart" %% "ficus" % "1.4.3",
      // Logging
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "ch.qos.logback" % "logback-core" % "1.2.3",
      // Elasticsearch
      "org.elasticsearch.client" % "elasticsearch-rest-high-level-client" % "7.4.0",
      // Monitoring
      "io.dropwizard.metrics" % "metrics-core" % "3.1.0",
      // Jackson
      "com.fasterxml.jackson.core" % "jackson-annotations" % versions.jackson,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % versions.jackson,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % versions.jackson,
      // Auth
      "io.jsonwebtoken" % "jjwt-api" % "0.10.7",
      "io.jsonwebtoken" % "jjwt-impl" % "0.10.7",
      "com.auth0" % "jwks-rsa" % "0.9.0" excludeAll (ExclusionRule(
        organization = "com.fasterxml.jackson.core"
      )),
      // AWS
      "software.amazon.awssdk" % "kms" % "2.9.24",
      "software.amazon.awssdk" % "s3" % "2.9.24",
      "software.amazon.awssdk" % "sqs" % "2.9.24",
      "software.amazon.awssdk" % "ssm" % "2.9.24",
      "software.amazon.awssdk" % "dynamodb" % "2.9.24",
      // Inject
      "com.google.inject" % "guice" % versions.guice,
      "com.google.inject.extensions" % "guice-assistedinject" % versions.guice,
      "com.google.inject.extensions" % "guice-multibindings" % versions.guice,
      "net.codingwell" %% "scala-guice" % versions.scalaGuice,
      // Misc
      // Http
      "org.http4s" %% "http4s-blaze-client" % "0.21.3",
      "org.http4s" %% "http4s-circe" % "0.21.3",
      "commons-codec" % "commons-codec" % "1.13",
      "javax.inject" % "javax.inject" % "1",
      "org.typelevel" %% "cats-core" % "1.1.0",
      "com.google.guava" % "guava" % "20.0",
      "com.lihaoyi" %% "fastparse" % "2.1.0",
      "org.apache.commons" % "commons-lang3" % "3.9",
      "org.apache.commons" % "commons-text" % "1.6",
      "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0",
      "org.gnieh" %% "diffson-circe" % "4.0.0",
      compilerPlugin(
        "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full
      ),
      "com.lihaoyi" %% "pprint" % "0.5.6" % Test
    ) ++ Dependencies.circe
  )

lazy val consumer = project
  .in(file("consumer"))
  .settings(BuildConfig.commonSettings)
  .settings(BuildConfig.commonAssmeblySettings)
  .settings(
    organization := "com.teletracker",
    name := "consumer",
    version := BuildConfig.Revision.wholeVersion,
    // Compilation
    scalaVersion := Compilation.scalacVersion,
    scalacOptions ++= Compilation.scalacOpts,
    libraryDependencies ++= Seq(
      "org.codehaus.janino" % "janino" % "3.1.2"
    ),
    mainClass in assembly := Some(
      "com.teletracker.consumers.QueueConsumerDaemon"
    ),
    Compile / run / mainClass := Some(
      "com.teletracker.consumers.QueueConsumerDaemon"
    ),
    envVars in reStart := Map(
      "TMDB_API_KEY" -> System.getenv("TMDB_API_KEY"),
      "JWT_SECRET" -> System.getenv("JWT_SECRET"),
      "LOCALCERTS_PATH" -> System.getenv("LOCALCERTS_PATH"),
      "AWS_EXECUTION_ENV" -> Option(System.getenv("AWS_EXECUTION_ENV"))
        .getOrElse("")
    ),
    javaOptions in reStart ++= Seq(
      "-Dregular_logging=true",
      "-Dlog.level=DEBUG"
    ),
//    Revolver.enableDebugging(port = 5005, suspend = false),
    dockerfile in docker := {
      // The assembly task generates a fat JAR file
      val artifact: File = assembly.value
      val artifactTargetPath = s"/app/bin/${artifact.name}"

      new Dockerfile {
        from(
          s"302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/base:latest"
        )
        add(baseDirectory.value / "src/docker/", "/app")
        add(artifact, artifactTargetPath)
        runRaw("chmod +x /app/main.sh")
        entryPoint("/app/main.sh")
      }
    },
    imageNames in docker := Seq(
      ImageName(
        namespace =
          Some("302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker"),
        repository = "consumer",
        tag = Some("latest")
      ),
      ImageName(
        namespace =
          Some("302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker"),
        repository = "consumer",
        tag = Some(version.value)
      )
    )
  )
  .dependsOn(common, tasks)
  .enablePlugins(DockerPlugin)

lazy val tasks = project
  .in(file("tasks"))
  .settings(BuildConfig.commonSettings)
  .settings(BuildConfig.commonAssmeblySettings)
  .settings(
    name := "tasks",
    libraryDependencies ++= Seq(
      "software.amazon.awssdk" % "lambda" % "2.9.24",
      "software.amazon.awssdk" % "arns" % "2.9.24",
      "org.xerial" % "sqlite-jdbc" % "3.30.1",
      "org.reflections" % "reflections" % "0.9.12",
      compilerPlugin(
        "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full
      )
    ),
    Compile / resourceGenerators += Def.task {
      TaskManifestGenerator.generate()
    },
    Compile / mainClass := Some(
      "com.teletracker.tasks.TeletrackerTaskRunner"
    ),
    mainClass in assembly := Some(
      "com.teletracker.tasks.TeletrackerTaskRunner"
    ),
    Compile / run / fork := true,
    Compile / run / javaOptions ++= Seq(
      "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5007",
      "-Dlog.level=DEBUG"
    ),
    connectInput in run := true,
    // Docker
    dockerfile in docker := {
      // The assembly task generates a fat JAR file
      val artifact: File = assembly.value
      val artifactTargetPath = s"/app/bin/${artifact.name}"

      new Dockerfile {
        from(
          s"302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/base:latest"
        )
        add(baseDirectory.value / "src/docker/", "/app")
        add(artifact, artifactTargetPath)
        runRaw("chmod +x /app/main.sh")
        entryPoint("/app/main.sh")
      }
    },
    imageNames in docker := Seq(
      ImageName(
        namespace =
          Some("302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker"),
        repository = "tasks",
        tag = Some("latest")
      ),
      ImageName(
        namespace =
          Some("302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker"),
        repository = "tasks",
        tag = Some(version.value)
      )
    )
  )
  .enablePlugins(DockerPlugin)
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
      "com.twitter" %% "inject-request-scope" % versions.twitter,
      // Testing
      "com.spotify" % "docker-client" % "8.11.7" % Test excludeAll "com.fasterxml.jackson.core",
      "com.h2database" % "h2" % "1.4.193" % Test,
      compilerPlugin(
        "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full
      )
    ) ++ Dependencies.finatraTest,
    // Local running / testing
    mainClass in reStart := Some(
      "com.teletracker.service.TeletrackerServerMain"
    ),
    reStartArgs ++= Seq("-https.port=:3002"),
    Revolver.enableDebugging(port = 5005, suspend = false),
    envVars in reStart := Map(
      "TMDB_API_KEY" -> System.getenv("TMDB_API_KEY"),
      "JWT_SECRET" -> System.getenv("JWT_SECRET"),
      "LOCALCERTS_PATH" -> System.getenv("LOCALCERTS_PATH")
    ),
    // Assmebly JAR
    mainClass in assembly := Some(
      "com.teletracker.service.TeletrackerServerMain"
    ),
    // Docker
    dockerfile in docker := {
      // The assembly task generates a fat JAR file
      val artifact: File = assembly.value
      val artifactTargetPath = s"/app/bin/${artifact.name}"

      new Dockerfile {
        from(
          s"302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/base:latest"
        )
        add(baseDirectory.value / "src/docker/", "/app")
        add(artifact, artifactTargetPath)
        runRaw("chmod +x /app/main.sh")
        entryPoint("/app/main.sh")
      }
    },
    imageNames in docker := Seq(
      ImageName(
        namespace =
          Some("302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker"),
        repository = "server",
        tag = Some("latest")
      ),
      ImageName(
        namespace =
          Some("302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker"),
        repository = "server",
        tag = Some(version.value)
      )
    )
  )
  .enablePlugins(DockerPlugin)
  .dependsOn(common, tasks)

lazy val `run-db-migrations` = inputKey[Unit]("generate ddl")
lazy val `reset-db` = taskKey[Unit]("reset-db")

resourceGenerators in Compile += Def.task {
  ((resourceManaged in Compile).value / "db" ** "*").get
}.taskValue

lazy val showVersion = taskKey[Unit]("Prints version")
showVersion := { println(version.value) }
