package oathdigital.server

import java.nio.file.Paths

class DesktopPathsSuite extends munit.FunSuite {
  test("macOS uses Application Support under HOME") {
    assertEquals(
      DesktopPaths.appDataDirectory("Mac OS X", Map("HOME" -> "/Users/alex"), "/ignored"),
      Paths.get("/Users/alex", "Library", "Application Support", "OathDigital")
    )
  }

  test("a blank or missing HOME falls back to user.home") {
    val expected = Paths.get("/home/fallback", "Library", "Application Support", "OathDigital")
    assertEquals(DesktopPaths.appDataDirectory("Mac OS X", Map.empty, "/home/fallback"), expected)
    assertEquals(DesktopPaths.appDataDirectory("Mac OS X", Map("HOME" -> " "), "/home/fallback"), expected)
  }

  test("Windows uses LOCALAPPDATA") {
    assertEquals(
      DesktopPaths.appDataDirectory("Windows 11", Map("LOCALAPPDATA" -> "/c/Users/alex/AppData/Local"), "/ignored"),
      Paths.get("/c/Users/alex/AppData/Local", "OathDigital")
    )
  }

  test("Windows without LOCALAPPDATA uses AppData/Local under the home folder") {
    assertEquals(
      DesktopPaths.appDataDirectory("Windows 11", Map.empty, "/c/Users/alex"),
      Paths.get("/c/Users/alex", "AppData", "Local", "OathDigital")
    )
  }

  test("Linux uses XDG_DATA_HOME") {
    assertEquals(
      DesktopPaths.appDataDirectory("Linux", Map("XDG_DATA_HOME" -> "/data/xdg", "HOME" -> "/home/alex"), "/ignored"),
      Paths.get("/data/xdg", "oathdigital")
    )
  }

  test("Linux without a usable XDG_DATA_HOME uses .local/share") {
    val expected = Paths.get("/home/alex", ".local", "share", "oathdigital")
    assertEquals(DesktopPaths.appDataDirectory("Linux", Map("HOME" -> "/home/alex"), "/ignored"), expected)
    assertEquals(
      DesktopPaths.appDataDirectory("Linux", Map("HOME" -> "/home/alex", "XDG_DATA_HOME" -> ""), "/ignored"),
      expected
    )
  }

  test("settings file and database path live in the app-data folder") {
    val appData = Paths.get("/data/oathdigital")
    assertEquals(DesktopPaths.settingsFile(appData), Paths.get("/data/oathdigital/oathdigital.properties"))
    assertEquals(DesktopPaths.databasePath(appData), Paths.get("/data/oathdigital/data/database"))
  }
}
