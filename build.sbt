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

lazy val frontend = (project in file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "oathdigital-frontend",
    scalaJSUseMainModuleInitializer := true,
    Compile / mainClass := Some("oathdigital.frontend.Main"),
    Compile / unmanagedSources ++= {
      val shared = (LocalRootProject / baseDirectory).value / "src" / "main" / "scala"
      Seq(
        shared / "oathdigital" / "model" / "Identity.scala",
        shared / "oathdigital" / "model" / "Resources.scala",
        shared / "oathdigital" / "catalog" / "CatalogModel.scala",
        shared / "oathdigital" / "engine" / "Engine.scala",
        shared / "oathdigital" / "setup" / "Setup.scala"
      )
    },
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.8.0",
      "org.scalameta" %%% "munit" % "1.0.4" % Test
    ),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlint"
    )
  )
