ThisBuild / scalaVersion := "2.13.16"
ThisBuild / organization := "dev.oathdigital"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .settings(
    name := "oathdigital-engine",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "ujson" % "4.4.3",
      "org.scalameta" %% "munit" % "1.0.4" % Test
    ),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlint"
    )
  )
