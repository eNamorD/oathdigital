ThisBuild / scalaVersion := "2.13.16"
ThisBuild / organization := "dev.oathdigital"
ThisBuild / version := ReleaseVersion.resolve(sys.env.get("OATH_RELEASE_VERSION"))

lazy val verifyReleaseVersion = taskKey[Unit]("Check release version validation")

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
      "org.scalameta" %% "munit" % "1.0.4" % Test,
      "org.scalameta" %% "munit-scalacheck" % "1.0.0" % Test
    ),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlint"
    ),
    // Ratchet: pinned at the baseline measured when scoverage was adopted
    // (stmt 84.11%). Raise it as coverage improves; the goal is 100% with
    // justified $COVERAGE-OFF$ exemptions. Enforced by `coverageReport`.
    coverageMinimumStmtTotal := 84.0,
    coverageFailOnMinimum := true,
    Universal / packageName := s"oathdigital-${version.value}",
    verifyReleaseVersion := {
      assert(ReleaseVersion.resolve(None) == "0.1.0-SNAPSHOT")
      Seq("0.1.0-alpha.1", "1.2.3-beta.0", "10.20.30-rc.12").foreach { value =>
        assert(ReleaseVersion.resolve(Some(value)) == value)
      }
      Seq("", "1.2.3", "v1.2.3-alpha.1", "01.2.3-alpha.1",
        "1.2.3-alpha.01", "1.2.3-SNAPSHOT", "1.2.3-alpha.1\n",
        "1.2.3-alpha.1+build", "../alpha").foreach { value =>
        assert(scala.util.Try(ReleaseVersion.resolve(Some(value))).isFailure,
          s"accepted invalid release version: $value")
      }
    },
    executableScriptName := "oathdigital",
    Universal / mappings ++= {
      val operations = ((baseDirectory.value / "docs/operations") ** "*.md")
        .get
        .map(file => file -> s"share/oathdigital/${file.getName}")
      (baseDirectory.value /
        "docs/catalog/new-foundations-component-catalog.json" ->
        "share/oathdigital/new-foundations-component-catalog.json") +:
        operations
    },
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
    Docker / dockerEnvVars := Map(
      "OATH_HOST" -> "0.0.0.0",
      "OATH_DATABASE_PATH" -> "/var/lib/oathdigital/database"
    ),
    dockerEnvVars := (Docker / dockerEnvVars).value,
    Docker / dockerExposedVolumes := Seq("/var/lib/oathdigital"),
    dockerExposedVolumes := (Docker / dockerExposedVolumes).value,
    verifyPackageMappings := {
      val packageMappings = (Universal / mappings).value
      val destinations = packageMappings.map(_._2)
      val serverJar = (Compile / packageBin).value.getCanonicalFile
      val requiredFiles = Seq(
        "bin/oathdigital",
        "bin/oathdigital.bat",
        "share/oathdigital/new-foundations-component-catalog.json",
        "share/oathdigital/configuration.md",
        "share/oathdigital/packaged-smoke-test.md",
        "share/oathdigital/phase-5-follow-ups.md",
        "share/oathdigital/quick-start.md",
        "share/oathdigital/data-policy.md",
        "share/oathdigital/network-and-browser.md",
        "share/oathdigital/alpha-acceptance.md",
        "share/oathdigital/releases.md"
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
      val dataDirectory = "/var/lib/oathdigital"
      val createDataDirectory =
        """RUN ["mkdir", "-p", "/var/lib/oathdigital"]"""
      val ownDataDirectory =
        """RUN ["chown", "-R", "oathdigital:root", "/var/lib/oathdigital"]"""
      val declareDataVolume = """VOLUME ["/var/lib/oathdigital"]"""
      val finalUser = "USER 10001:0"
      val createIndex = dockerCommandLines.indexOf(createDataDirectory)
      val ownIndex = dockerCommandLines.indexOf(ownDataDirectory)
      val volumeIndex = dockerCommandLines.indexOf(declareDataVolume)
      val finalUserIndex = dockerCommandLines.lastIndexOf(finalUser)
      val orderingFailures =
        if (createIndex >= 0 && ownIndex > createIndex &&
            volumeIndex > ownIndex && finalUserIndex > volumeIndex)
          Seq.empty
        else Seq(
          "Docker image must create, own, and declare a volume for " +
            "/var/lib/oathdigital before switching to USER 10001:0"
        )
      // The prepared data directory is useless unless the image also points
      // the server at it, so pin both halves: the configured database path
      // must live under the directory the image creates, and the generated
      // Dockerfile must actually carry that ENV declaration.
      val configuredDatabasePath = (Docker / dockerEnvVars).value
        .getOrElse("OATH_DATABASE_PATH", "")
      val environmentLines = dockerCommandLines
        .filter(_.startsWith("ENV"))
        .map(_.replace("\"", ""))
      val databasePathFailures =
        if (!configuredDatabasePath.startsWith(dataDirectory + "/"))
          Seq(
            s"Docker OATH_DATABASE_PATH '$configuredDatabasePath' must live " +
              s"under the prepared data directory $dataDirectory"
          )
        else if (!environmentLines.exists(
              _.contains(s"OATH_DATABASE_PATH=$configuredDatabasePath")
            ))
          Seq(
            "Docker image must declare ENV " +
              s"OATH_DATABASE_PATH=$configuredDatabasePath"
          )
        else Seq.empty
      val hostFailures =
        if (environmentLines.exists(_.contains("OATH_HOST=0.0.0.0")))
          Seq.empty
        else Seq("Docker image must declare ENV OATH_HOST=0.0.0.0")
      val dockerFailures =
        orderingFailures ++ databasePathFailures ++ hostFailures
      val failures = missingFiles.map(path => s"missing $path") ++
        missingJars ++ dockerFailures
      if (failures.nonEmpty)
        sys.error("Invalid package mappings: " + failures.mkString(", "))
    },
    Universal / packageBin := (Universal / packageBin)
      .dependsOn(verifyPackageMappings).value,
    Universal / packageZipTarball := (Universal / packageZipTarball)
      .dependsOn(verifyPackageMappings).value,
    Docker / stage := (Docker / stage)
      .dependsOn(verifyPackageMappings).value
  )

lazy val frontend = (project in file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "oathdigital-frontend",
    // scoverage instruments JVM code only; `coverage` would otherwise switch
    // it on for this Scala.js project too.
    coverageEnabled := false,
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
    // Frontend tests run in jsdom, not bare Node, so a renderer suite can
    // drive the DOM the panels actually build: create the controls, click an
    // accessible move button, read a confirm button's disabled state, and
    // capture the command a click submits. `dom.document` is a val captured
    // when scalajs-dom's package object initializes, so the document has to
    // be real before any test touches it -- a hand-rolled double would
    // depend on suite ordering. The `jsdom` package is pinned in the repo
    // root's `package.json`; CI runs `npm ci` before `frontend/test`.
    Test / jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv(),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlint"
    )
  )

addCommandAlias(
  "smokeUniversal",
  ";Universal/stage;verifyPackageMappings"
)
addCommandAlias(
  "smokeContainer",
  ";Docker/publishLocal;verifyPackageMappings"
)
addCommandAlias(
  "buildAlphaArtifacts",
  ";test;frontend/test;Universal/packageBin;Universal/packageZipTarball"
)
