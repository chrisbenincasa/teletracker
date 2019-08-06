import sbt._

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
      "-Xfuture"
    )
  }

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
      "io.circe" %% "circe-parser",
      "io.circe" %% "circe-shapes"
    ).map(_ % versions.circe)
  }

  object versions {
    lazy val twitter = "19.8.0"
    lazy val jackson = "2.9.9"
    lazy val guice = "4.0"
    lazy val scalaGuice = "4.1.0"
    lazy val circe = "0.12.0-M4"
    lazy val slick = "3.2.3"
    lazy val slickPg = "0.16.2"
  }

  object Revision {
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
