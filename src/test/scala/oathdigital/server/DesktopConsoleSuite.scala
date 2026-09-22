package oathdigital.server

import java.net.{InetAddress, ServerSocket}
import java.nio.file.Paths

// Same simple name as Akka's bind failure, which the matcher recognizes by name.
private final class BindFailedException extends RuntimeException("Bind failed")

class DesktopConsoleSuite extends munit.FunSuite {
  private val settingsFile = Paths.get("/data/oathdigital/oathdigital.properties")
  private val nl = System.lineSeparator

  private def config(environment: Map[String, String]): ServerConfig =
    ServerConfig.parse(
      Array.empty,
      environment ++ Map("OATH_MODE" -> "trusted-alpha", "OATH_DATABASE_PATH" -> "/data/oathdigital/data/database"),
      "0.1.0-alpha.1"
    ).fold(errors => fail(errors.mkString("; ")), identity)

  private def launch(lanDetectionFailed: Boolean) =
    DesktopLaunch(Map.empty, settingsFile, openBrowser = true, lanDetectionFailed, Vector.empty)

  test("the LAN banner names the player address, data folder and settings file") {
    val lanConfig = config(Map("OATH_HOST" -> "0.0.0.0", "OATH_PUBLIC_BASE_URL" -> "http://192.168.1.20:8080"))
    assertEquals(
      DesktopConsole.banner(lanConfig, launch(lanDetectionFailed = false)),
      Vector(
        "Oath Digital 0.1.0-alpha.1 is running.",
        "  Players open:  http://192.168.1.20:8080",
        s"  Data folder:   ${Paths.get("/data/oathdigital/data")}",
        s"  Settings:      $settingsFile",
        "Seat links contain this address. If it changes, players need the new address.",
        "Close this window or press Ctrl-C to stop."
      ).mkString("", nl, nl)
    )
  }

  test("the no-LAN banner says only this computer can connect") {
    val localConfig = config(Map("OATH_HOST" -> "127.0.0.1"))
    assertEquals(
      DesktopConsole.banner(localConfig, launch(lanDetectionFailed = true)),
      Vector(
        "Oath Digital 0.1.0-alpha.1 is running.",
        "  Only this computer can connect: http://127.0.0.1:8080",
        s"  Data folder:   ${Paths.get("/data/oathdigital/data")}",
        s"  Settings:      $settingsFile",
        "No local network address found. Set OATH_PUBLIC_BASE_URL in the settings file.",
        "Close this window or press Ctrl-C to stop."
      ).mkString("", nl, nl)
    )
  }

  test("the browser opens the public base URL, else loopback") {
    val lanConfig = config(Map("OATH_HOST" -> "0.0.0.0", "OATH_PUBLIC_BASE_URL" -> "http://192.168.1.20:8080"))
    assertEquals(DesktopConsole.browserUrl(lanConfig), "http://192.168.1.20:8080/")
    assertEquals(DesktopConsole.browserUrl(config(Map("OATH_HOST" -> "127.0.0.1"))), "http://127.0.0.1:8080/")
  }

  test("the browser command follows the operating system") {
    val url = "http://192.168.1.20:8080/"
    assertEquals(DesktopConsole.browserCommand("Mac OS X", url), Vector("open", url))
    assertEquals(DesktopConsole.browserCommand("Windows 11", url), Vector("cmd", "/c", "start", "", url))
    assertEquals(DesktopConsole.browserCommand("Linux", url), Vector("xdg-open", url))
  }

  test("a browser that cannot be opened is a warning") {
    var ran = Vector.empty[String]
    assertEquals(DesktopConsole.openBrowser("Linux", "http://x/", command => ran = command), None)
    assertEquals(ran, Vector("xdg-open", "http://x/"))
    assertEquals(
      DesktopConsole.openBrowser("Linux", "http://x/", _ => throw new java.io.IOException("no xdg-open")),
      Some("could not open a browser: no xdg-open")
    )
  }

  test("a real port conflict produces the in-use message") {
    val held = new ServerSocket(0, 1, InetAddress.getLoopbackAddress)
    try {
      val error = intercept[java.net.BindException] {
        new ServerSocket(held.getLocalPort, 1, InetAddress.getLoopbackAddress).close()
      }
      assertEquals(
        DesktopConsole.bindFailure(new RuntimeException("bind failed", error), 8080, settingsFile),
        Some(s"port 8080 is in use. Is Oath Digital already running? Change OATH_PORT in $settingsFile.")
      )
    } finally held.close()
  }

  test("Akka's BindFailedException is recognized by name") {
    assert(DesktopConsole.bindFailure(new BindFailedException, 8080, settingsFile).nonEmpty)
    assertEquals(DesktopConsole.bindFailure(new IllegalStateException("other"), 8080, settingsFile), None)
  }
}
