import scala.util.{Failure, Success}

name := "teletracker-scala-2"

organization := "com.chrisbenincasa.services"

version := s"0.1-${BuildConfig.Revision.revision}"

scalaVersion := "2.12.6"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-language:experimental.macros",
  "-unchecked",
  "-Ywarn-nullary-unit",
  "-Xfatal-warnings",
  "-Ywarn-dead-code",
  "-Xfuture"
)

//cancelable in Global := true

fork in run := true

lazy val circeVersion = "0.9.3"

libraryDependencies ++= Seq(
  // Config
  "com.iheart" %% "ficus" % "1.4.3",
  "com.github.scopt" %% "scopt" % "3.5.0",
  // Logging
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  // Service
  "com.twitter" %% "finatra-http" % "18.6.0",
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
  "io.jsonwebtoken" % "jjwt" % "0.9.0"
) ++ Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser",
  "io.circe" %% "circe-shapes"
).map(_ % circeVersion)

addCompilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full
)

mainClass in reStart := Some("com.chrisbenincasa.services.teletracker.TeletrackerServerMain")
envVars in reStart := Map(
  "API_KEY" -> System.getenv("API_KEY"),
  "JWT_SECRET" -> System.getenv("JWT_SECRET")
)

Revolver.enableDebugging(port = 5005, suspend = true)

//javaOptions in reStart := Seq("-Dlog.service.output=/dev/stdout", "-Dlog.access.output=/dev/stdout")

enablePlugins(FlywayPlugin, DockerPlugin)

mainClass in assembly := Some("com.chrisbenincasa.services.teletracker.TeletrackerServerMain")

test in assembly := {}

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
  case x => MergeStrategy.first
}

dockerfile in docker := {
  // The assembly task generates a fat JAR file
  val artifact: File = assembly.value
  val artifactTargetPath = s"/app/bin/${artifact.name}"

  new Dockerfile {
    from("openjdk:8u141-jre-slim")
    add(baseDirectory.value / "src/docker/", "/app")
    add(artifact, artifactTargetPath).
      runRaw("chmod +x /app/main.sh")
    entryPoint("/app/main.sh")
  }
}

imageNames in docker := Seq(
  ImageName(s"${organization.value}/${name.value}:latest"),
  ImageName(
    namespace = Some(organization.value),
    repository = name.value,
    tag = Some("v" + version.value)
  )
)

buildOptions in docker := BuildOptions(
  pullBaseImage = BuildOptions.Pull.Always
)

//sourceGenerators in Compile += `slick-codegen`

lazy val `generate-ddl` = taskKey[Unit]("generate ddl")
`generate-ddl` := Def.task {
  val dir = (resourceManaged in Compile).value
  val cp = (fullClasspath in Runtime).value
  val r = (runner in Compile).value
  val s = streams.value

  val outputFile = (dir / "db" / "migration" / "postgres" / "V1__create.sql").getAbsolutePath
  r.run("com.chrisbenincasa.services.teletracker.tools.GenerateDdlsMain", cp.files, Array(outputFile), s.log) match {
    case Success(_) => ()
    case Failure(e) => throw e
  }
}.value

lazy val `run-db-migrations` = inputKey[Unit]("generate ddl")
`run-db-migrations` := runInputTask(Runtime, "com.chrisbenincasa.services.teletracker.tools.RunDatabaseMigration").evaluated

resourceGenerators in Compile += Def.task {
  ((resourceManaged in Compile).value / "db"  ** "*").get
}.taskValue

// Do this in actual code.
lazy val `slick-codegen` = taskKey[Seq[File]]("gen-tables")
`slick-codegen` := Def.task {
  val dir = sourceManaged.value
  val cp = (dependencyClasspath in Compile).value
  val r = (runner in Compile).value
  val s = streams.value

  val outputDir = dir.getPath // place generated files in sbt's managed sources folder
  val url = "jdbc:postgresql://localhost/teletracker" // connection info for a pre-populated throw-away, in-memory db for this demo, which is freshly initialized on every run
  val jdbcDriver = "org.postgresql.Driver"
  val slickDriver = "slick.jdbc.PostgresProfile"
  val pkg = "com.chrisbenincasa.services.teletracker.db.model"
  r.run("slick.codegen.SourceCodeGenerator", cp.files, Array(slickDriver, jdbcDriver, url, outputDir, pkg, "teletracker", "teletracker"), s.log)
  val fname = outputDir + "/" + pkg.replaceAllLiterally(".", "/") + "/Tables.scala"
  Seq(file(fname))
}.value