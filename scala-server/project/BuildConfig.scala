import sbt._
import sbt.Keys._
import sbtassembly.AssemblyPlugin.autoImport._

object BuildConfig {
  object Compilation {
    lazy val scalacVersion = "2.12.9"

    lazy val scalacOpts = Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
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
      "-Xfuture",
      "-Ypartial-unification"
    )
  }

  lazy val commonSettings = Seq(
    organization := "com.teletracker",
    version := BuildConfig.Revision.wholeVersion,
    // Compilation
    scalaVersion := Compilation.scalacVersion,
    scalacOptions ++= Compilation.scalacOpts,
    Global / concurrentRestrictions := Seq(Tags.limit(Tags.Test, 1)),
    Test / fork := true,
    Compile / resourceGenerators += Def.task {
      VersionFileGenerate.generate(
        resourceManaged.in(Compile).value,
        version.in(Compile).value
      )
    }
  )

  lazy val commonAssmeblySettings = Seq(
    test in assembly := {},
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _*) =>
        xs map { _.toLowerCase } match {
          case (x :: Nil)
              if Seq("manifest.mf", "index.list", "dependencies") contains x =>
            MergeStrategy.discard
          case ps @ (x :: _)
              if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") || ps.last
                .endsWith(".rsa") =>
            MergeStrategy.discard
          case "maven" :: _ =>
            MergeStrategy.discard
          case "plexus" :: _ =>
            MergeStrategy.discard
          case "services" :: _ =>
            MergeStrategy.filterDistinctLines
          case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) |
              ("spring.tooling" :: Nil) =>
            MergeStrategy.filterDistinctLines
          case _ => MergeStrategy.first
        }

      case PathList(ps @ _*)
          if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) =>
        MergeStrategy.rename

      case PathList(ps @ _*) if Assembly.isSystemJunkFile(ps.last) =>
        MergeStrategy.discard

      case _ => MergeStrategy.first
    }
  )

  object Dependencies {
    lazy val finatraTest = Seq(
      "com.twitter" %% "inject-server" % versions.twitter % Test,
      "com.twitter" %% "inject-app" % versions.twitter % Test,
      "com.twitter" %% "inject-core" % versions.twitter % Test,
      "com.twitter" %% "inject-modules" % versions.twitter % Test,
      "com.twitter" %% "finatra-http" % versions.twitter % Test classifier "tests",
      "com.twitter" %% "inject-server" % versions.twitter % Test classifier "tests",
      "com.twitter" %% "inject-app" % versions.twitter % Test classifier "tests",
      "com.twitter" %% "inject-core" % versions.twitter % Test classifier "tests",
      "com.twitter" %% "inject-modules" % versions.twitter % Test classifier "tests"
    )

    lazy val circe = Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-generic-extras",
      "io.circe" %% "circe-parser",
      "io.circe" %% "circe-shapes",
      "io.circe" %% "circe-optics"
    ).map(_ % versions.circe)
  }

  object versions {
    lazy val twitter = "19.8.0"
    lazy val jackson = "2.9.9"
    lazy val guice = "4.0"
    lazy val scalaGuice = "4.1.0"
    lazy val circe = "0.12.0-RC2"
    lazy val slick = "3.2.3"
    lazy val slickPg = "0.16.2"
  }

  object Revision {
    lazy val wholeVersion = System.getProperty("version", "0.1-SNAPSHOT")
    lazy val revision = System.getProperty("revision", "SNAPSHOT")
    lazy val baseImageVersion = {
      val v = System.getProperty(
        "base_image_version",
        "sha256:e586ccd0786a55490f5bb18ad90bb2d26e6fc3df2c37e94a6144d9323fc5c7e8"
      )

      if (v.startsWith("sha256")) {
        s"@${v}"
      } else {
        s":${v}"
      }
    }

  }
}
