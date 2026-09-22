package oathdigital.server

import java.net.URI
import java.nio.file.{Files, Path, Paths}

class DesktopLaunchProfileSuite extends munit.FunSuite {
  private val appData = Paths.get("/data/oathdigital")
  private val lan: () => Option[String] = () => Some("192.168.1.20")
  private val noDetection: () => Option[String] =
    () => fail("LAN detection must not run")

  private def resolve(
      arguments: Array[String] = Array.empty,
      environment: Map[String, String] = Map.empty,
      settings: Map[String, String] = Map.empty,
      detect: () => Option[String] = lan
  ): DesktopLaunch =
    DesktopLaunchProfile.resolve(arguments, environment, settings, appData, detect)
      .fold(error => fail(error), identity)

  private def parse(arguments: Array[String], launch: DesktopLaunch): ServerConfig =
    ServerConfig.parse(arguments, launch.environment + ("OATH_MODE" -> "trusted-alpha"), "0.1.0-alpha.1")
      .fold(errors => fail(errors.mkString("; ")), identity)

  test("desktop defaults bind all interfaces with the detected address") {
    val launch = resolve()
    assertEquals(launch.environment.get("OATH_HOST"), Some("0.0.0.0"))
    assertEquals(launch.environment.get("OATH_PUBLIC_BASE_URL"), Some("http://192.168.1.20:8080"))
    assertEquals(launch.environment.get("OATH_DATABASE_PATH"), Some(appData.resolve("data/database").toString))
    assertEquals(launch.settingsFile, appData.resolve("oathdigital.properties"))
    assert(launch.openBrowser)
    assert(!launch.lanDetectionFailed)
    val config = parse(Array.empty, launch)
    assertEquals(config.publicBaseUrl, Some(new URI("http://192.168.1.20:8080")))
  }

  test("the settings file overrides desktop defaults") {
    val launch = resolve(settings = Map("OATH_PORT" -> "9000"))
    assertEquals(launch.environment.get("OATH_PORT"), Some("9000"))
    assertEquals(launch.environment.get("OATH_PUBLIC_BASE_URL"), Some("http://192.168.1.20:9000"))
  }

  test("environment variables override the settings file") {
    val launch = resolve(environment = Map("OATH_PORT" -> "9100"), settings = Map("OATH_PORT" -> "9000"))
    assertEquals(launch.environment.get("OATH_PORT"), Some("9100"))
    assertEquals(launch.environment.get("OATH_PUBLIC_BASE_URL"), Some("http://192.168.1.20:9100"))
  }

  test("command-line flags override environment variables") {
    val arguments = Array("--port", "9200")
    val launch = resolve(arguments = arguments, environment = Map("OATH_PORT" -> "9100"))
    val config = parse(arguments, launch)
    assertEquals(config.port, 9200)
    assertEquals(config.publicBaseUrl, Some(new URI("http://192.168.1.20:9200")))
  }

  test("a flag alone counts as set for the database path") {
    val arguments = Array("--database-path", "/elsewhere/database")
    val launch = resolve(arguments = arguments)
    assertEquals(launch.environment.get("OATH_DATABASE_PATH"), None)
    assertEquals(parse(arguments, launch).databasePath, Paths.get("/elsewhere/database"))
  }

  test("an explicit public base URL skips detection") {
    val launch = resolve(settings = Map("OATH_PUBLIC_BASE_URL" -> "https://oath.example.test"), detect = noDetection)
    assertEquals(launch.environment.get("OATH_PUBLIC_BASE_URL"), Some("https://oath.example.test"))
  }

  test("a loopback host skips detection and sets no public base URL") {
    val launch = resolve(environment = Map("OATH_HOST" -> "127.0.0.1"), detect = noDetection)
    assertEquals(launch.environment.get("OATH_PUBLIC_BASE_URL"), None)
    assert(!launch.lanDetectionFailed)
    assertEquals(parse(Array.empty, launch).publicBaseUrl, None)
  }

  test("a specific non-loopback host becomes the public base URL") {
    val launch = resolve(settings = Map("OATH_HOST" -> "192.168.5.5"), detect = noDetection)
    assertEquals(launch.environment.get("OATH_PUBLIC_BASE_URL"), Some("http://192.168.5.5:8080"))
  }

  test("no LAN address falls back to this computer only") {
    val launch = resolve(detect = () => None)
    assertEquals(launch.environment.get("OATH_HOST"), Some("127.0.0.1"))
    assertEquals(launch.environment.get("OATH_PUBLIC_BASE_URL"), None)
    assert(launch.lanDetectionFailed)
    assertEquals(parse(Array.empty, launch).host, "127.0.0.1")
  }

  test("browser opening follows settings and environment precedence") {
    assert(!resolve(settings = Map("OATH_OPEN_BROWSER" -> "false")).openBrowser)
    assert(resolve(environment = Map("OATH_OPEN_BROWSER" -> "TRUE"), settings = Map("OATH_OPEN_BROWSER" -> "false")).openBrowser)
    assertEquals(
      DesktopLaunchProfile.resolve(Array.empty, Map("OATH_OPEN_BROWSER" -> "yes"), Map.empty, appData, lan),
      Left("OATH_OPEN_BROWSER: must be true or false")
    )
  }

  test("unrelated environment variables pass through") {
    val launch = resolve(environment = Map("OATH_CATALOG_PATH" -> "/catalog.json", "PATH" -> "/usr/bin"))
    assertEquals(launch.environment.get("OATH_CATALOG_PATH"), Some("/catalog.json"))
    assertEquals(launch.environment.get("PATH"), Some("/usr/bin"))
  }

  test("prepare leaves the environment untouched and writes nothing without OATH_LAUNCH") {
    val home = Files.createTempDirectory("oathdigital-home")
    val environment = Map("HOME" -> home.toString, "OATH_PORT" -> "9300")
    assertEquals(
      DesktopLaunchProfile.prepare(Array.empty, environment, "Linux", home.toString, noDetection),
      Right(None)
    )
    assert(!Files.exists(home.resolve(".local")))
    Files.delete(home)
  }

  test("prepare rejects an unknown launch profile") {
    assertEquals(
      DesktopLaunchProfile.prepare(Array.empty, Map("OATH_LAUNCH" -> "tray"), "Linux", "/tmp", noDetection),
      Left("OATH_LAUNCH: must be desktop when set")
    )
  }

  test("prepare reads settings and creates the data folder") {
    val home = Files.createTempDirectory("oathdigital-home")
    val environment = Map("HOME" -> home.toString, "OATH_LAUNCH" -> "desktop")
    val appData = home.resolve(".local/share/oathdigital")
    Files.createDirectories(appData)
    Files.write(appData.resolve("oathdigital.properties"), "OATH_PORT=9400\n".getBytes("UTF-8"))
    val launch = DesktopLaunchProfile.prepare(Array.empty, environment, "Linux", home.toString, lan)
      .fold(error => fail(error), _.getOrElse(fail("expected a desktop launch")))
    assertEquals(launch.environment.get("OATH_PORT"), Some("9400"))
    assert(Files.isDirectory(appData.resolve("data")))
    assertEquals(launch.warnings, Vector.empty)
  }

  test("prepare reports an unknown settings key") {
    val home = Files.createTempDirectory("oathdigital-home")
    val appData = home.resolve(".local/share/oathdigital")
    Files.createDirectories(appData)
    val file = Files.write(appData.resolve("oathdigital.properties"), "OATH_PROT=1\n".getBytes("UTF-8"))
    assertEquals(
      DesktopLaunchProfile.prepare(Array.empty, Map("HOME" -> home.toString, "OATH_LAUNCH" -> "desktop"), "Linux", home.toString, lan),
      Left(s"$file: unknown setting OATH_PROT")
    )
  }
}
