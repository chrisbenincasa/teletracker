import sbt._

object BuildConfig {
  object Compilation {
    lazy val scalacVersion = "2.12.8"
    
    lazy val scalacOpts = Seq(
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
      "com.twitter" %% "inject-modules" % versions.twitter % Test classifier "tests",
    )
    
    lazy val circe = Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser",
      "io.circe" %% "circe-shapes"
    ).map(_ % versions.circe)
  }
  
  object versions {
    lazy val twitter = "19.4.0"

    lazy val circe = "0.9.3"
  }
  
  object Revision {
    lazy val revision = System.getProperty("revision", "SNAPSHOT")
  }
}
