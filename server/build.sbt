import Dependencies._

lazy val server = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.chrisbenincasa",
      scalaVersion := "2.12.4",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "api",
    libraryDependencies ++= Seq(
      finatra,
      logback,
      scalaTest % Test
    )
  )
