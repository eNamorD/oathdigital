import com.typesafe.sbt.packager.docker.ExecCmd

ThisBuild / scalaVersion := "2.13.16"
ThisBuild / organization := "dev.oathdigital"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val verifyPackageMappings = taskKey[Unit](
  "Verify that every distribution contains its launchers and runtime files"
)

lazy val root = (project in file("."))
  .enablePlugins(JavaServerAppPackaging, DockerPlugin)
  .settings(
    name := "oathdigital-engine",
    maintainer := "Oath Digital",
    Compile / mainClass := Some("oathdigital.server.OathServer"),
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
    ),
    Universal / packageName := "oathdigital",
    executableScriptName := "oathdigital",
    Universal / mappings ++= Seq(
      baseDirectory.value /
        "docs/catalog/new-foundations-component-catalog.json" ->
        "share/oathdigital/new-foundations-component-catalog.json",
      baseDirectory.value / "docs/operations/configuration.md" ->
        "share/oathdigital/configuration.md"
    ),
    Universal / javaOptions += "-Dfile.encoding=UTF-8",
    bashScriptExtraDefines ++= Seq(
      """if [ -z "${OATH_MODE+x}" ]; then OATH_MODE=trusted-alpha; fi""",
      "export OATH_MODE",
      """if [ -z "${OATH_CATALOG_PATH+x}" ]; then OATH_CATALOG_PATH="${app_home}/../share/oathdigital/new-foundations-component-catalog.json"; fi""",
      "export OATH_CATALOG_PATH"
    ),
    batScriptExtraDefines ++= Seq(
      "if not defined OATH_MODE set \"OATH_MODE=trusted-alpha\"",
      "if not defined OATH_CATALOG_PATH set \"OATH_CATALOG_PATH=%~dp0..\\share\\oathdigital\\new-foundations-component-catalog.json\""
    ),
    Docker / packageName := "oathdigital",
    Docker / dockerExposedPorts := Seq(8080),
    dockerExposedPorts := (Docker / dockerExposedPorts).value,
    Docker / daemonUser := "oathdigital",
    Docker / daemonUserUid := Some("10001"),
    Docker / dockerBaseImage := "eclipse-temurin:21-jre",
    dockerBaseImage := (Docker / dockerBaseImage).value,
    Docker / dockerCommands := {
      val commands = (Docker / dockerCommands).value
      val finalUserIndex = commands.lastIndexWhere(
        _.makeContent.trim == "USER 10001:0"
      )
      if (finalUserIndex < 0)
        sys.error("Docker commands are missing final USER 10001:0")
      commands.patch(
        finalUserIndex,
        Seq(
          ExecCmd("RUN", "mkdir", "-p", "/var/lib/oathdigital"),
          ExecCmd("RUN", "chown", "10001:0", "/var/lib/oathdigital")
        ),
        0
      )
    },
    verifyPackageMappings := {
      val packageMappings = (Universal / mappings).value
      val destinations = packageMappings.map(_._2)
      val serverJar = (Compile / packageBin).value.getCanonicalFile
      val requiredFiles = Seq(
        "bin/oathdigital",
        "bin/oathdigital.bat",
        "share/oathdigital/new-foundations-component-catalog.json",
        "share/oathdigital/configuration.md"
      )
      val missingFiles = requiredFiles.filterNot(destinations.contains)
      val serverJarMapped = packageMappings.exists { case (source, path) =>
        source.getCanonicalFile == serverJar &&
          path.startsWith("lib/") && path.endsWith(".jar")
      }
      val dependencyJarCount = packageMappings.count { case (source, path) =>
        source.getCanonicalFile != serverJar &&
          path.startsWith("lib/") && path.endsWith(".jar")
      }
      val missingJars =
        Seq(
          if (!serverJarMapped) Some("missing server jar under lib/")
          else None,
          if (dependencyJarCount == 0)
            Some("missing dependency jars under lib/")
          else None
        ).flatten
      val dockerCommandLines = (Docker / dockerCommands).value
        .map(_.makeContent.trim)
      val createDataDirectory =
        """RUN ["mkdir", "-p", "/var/lib/oathdigital"]"""
      val ownDataDirectory =
        """RUN ["chown", "10001:0", "/var/lib/oathdigital"]"""
      val finalUser = "USER 10001:0"
      val createIndex = dockerCommandLines.indexOf(createDataDirectory)
      val ownIndex = dockerCommandLines.indexOf(ownDataDirectory)
      val finalUserIndex = dockerCommandLines.lastIndexOf(finalUser)
      val dockerFailures =
        if (createIndex >= 0 && ownIndex > createIndex &&
            finalUserIndex > ownIndex)
          Seq.empty
        else Seq(
          "Docker image must create and own /var/lib/oathdigital " +
            "before switching to USER 10001:0"
        )
      val failures = missingFiles.map(path => s"missing $path") ++
        missingJars ++ dockerFailures
      if (failures.nonEmpty)
        sys.error("Invalid package mappings: " + failures.mkString(", "))
    },
    Universal / packageBin := {
      val archive = (Universal / packageBin)
        .dependsOn(verifyPackageMappings).value
      val versioned = archive.getParentFile /
        s"${(Universal / packageName).value}-${version.value}.zip"
      IO.move(archive, versioned)
      versioned
    },
    Universal / packageZipTarball := {
      val archive = (Universal / packageZipTarball)
        .dependsOn(verifyPackageMappings).value
      val versioned = archive.getParentFile /
        s"${(Universal / packageName).value}-${version.value}.tgz"
      IO.move(archive, versioned)
      versioned
    },
    Docker / stage := (Docker / stage)
      .dependsOn(verifyPackageMappings).value
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
