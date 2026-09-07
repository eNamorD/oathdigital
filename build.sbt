ThisBuild / scalaVersion := "2.13.16"
ThisBuild / organization := "dev.oathdigital"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .settings(
    name := "oathdigital-engine",
    Compile / run := (Compile / run)
      .dependsOn(frontend / Compile / fastLinkJS).evaluated,
    Compile / runMain := (Compile / runMain)
      .dependsOn(frontend / Compile / fastLinkJS).evaluated,
    Compile / resourceGenerators += Def.task {
      val report = (frontend / Compile / fullLinkJS).value
      val linkerOutput = (frontend / Compile / fullLinkJS /
        scalaJSLinkerOutputDirectory).value
      val output = (Compile / resourceManaged).value /
        "oathdigital" / "frontend"
      val linked = linkerOutput /
        report.data.publicModules.find(_.moduleID == "main").get.jsFileName
      val files = Seq(
        linked -> (output / "main.js"),
        baseDirectory.value / "frontend" / "styles.css" ->
          (output / "styles.css"),
        baseDirectory.value / "frontend" / "production-index.html" ->
          (output / "index.html")
      )
      IO.copy(files)
      files.map(_._2)
    }.taskValue,
    Compile / unmanagedSourceDirectories += baseDirectory.value / "shared" / "src" / "main" / "scala",
    Test / unmanagedSourceDirectories += baseDirectory.value / "shared" / "src" / "test" / "scala",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "ujson" % "4.4.3",
      "com.typesafe.slick" %% "slick" % "3.5.2",
      "com.typesafe.slick" %% "slick-hikaricp" % "3.5.2",
      "org.hsqldb" % "hsqldb" % "2.7.4",
      "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5",
      "com.typesafe.akka" %% "akka-stream" % "2.8.5",
      "com.typesafe.akka" %% "akka-http" % "10.5.3",
      "ch.qos.logback" % "logback-classic" % "1.5.18",
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
        shared / "oathdigital" / "presentation" / "ViewModel.scala"
      )
    },
    Compile / unmanagedSourceDirectories +=
      (LocalRootProject / baseDirectory).value / "shared" / "src" / "main" / "scala",
    Test / unmanagedSourceDirectories +=
      (LocalRootProject / baseDirectory).value / "shared" / "src" / "test" / "scala",
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.8.0",
      "com.lihaoyi" %%% "ujson" % "4.4.3",
      "org.scalameta" %%% "munit" % "1.0.4" % Test
    ),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlint"
    )
  )
