# Bundled Runtime and Desktop Launch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a trusted-alpha host run the server without installing Java and without typing commands: download the archive for their OS, extract it, double-click **Start**, share the printed address.

**Architecture:** A desktop launch profile in Scala (`oathdigital.server`), active only when `OATH_LAUNCH=desktop`, computes an effective environment (settings file, app-data database path, detected LAN address) and hands it to the unchanged `ServerConfig.parse`; after binding it prints a banner and opens the browser. Packaging adds checked-in Start files and one launcher line that prefers a bundled `jre/`. A post-processing script turns the tested all-platform archive into per-OS archives with a `jlink` runtime, and a new release-workflow matrix job builds and smokes them on macOS, Windows, and Linux.

**Tech Stack:** Scala 2.13, Akka HTTP 10.5.3, munit, sbt-native-packager 1.11.7, Temurin 21 `jdeps`/`jlink`, POSIX `sh`, PowerShell 7, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-22-bundled-desktop-launch-design.md`

## Global Constraints

- Bundled targets exactly: `macos-arm64` (runner `macos-14`, `.tgz`), `windows-x64` (runner `windows-latest`, `.zip`), `linux-x64` (runner `ubuntu-24.04`, `.tgz`).
- Bundled archive name: `oathdigital-<version>-<target>.<ext>`; it extracts to the single directory `oathdigital-<version>/`, the same root as the all-platform archive.
- The all-platform ZIP/TGZ stays, requires Java 21, and must keep passing `scripts/smoke-packaged-distribution.sh`.
- Without `OATH_LAUNCH`, behavior is unchanged: `ServerConfig.parse` receives exactly `sys.env`, no files are written, and CLI/OCI output is unchanged (including today's bind-failure behavior). `OATH_LAUNCH` set to anything other than `desktop` is a configuration error.
- Desktop precedence, highest first: command-line flags, environment variables, settings file, desktop defaults (`OATH_HOST=0.0.0.0`, detected `OATH_PUBLIC_BASE_URL`, `OATH_DATABASE_PATH=<app-data>/data/database`, `OATH_OPEN_BROWSER=true`), existing `ServerConfig` defaults.
- Settings file: `<app-data>/oathdigital.properties`, Java properties format, keys exactly `OATH_HOST`, `OATH_PORT`, `OATH_PUBLIC_BASE_URL`, `OATH_DATABASE_PATH`, `OATH_OPEN_BROWSER`. Unknown key is an error. Template is written only if missing and never overwritten.
- App-data folders: macOS `~/Library/Application Support/OathDigital`; Windows `%LOCALAPPDATA%\OathDigital`; others `$XDG_DATA_HOME/oathdigital`, else `~/.local/share/oathdigital`. `~` is `HOME` when set and non-blank, else `user.home`.
- LAN detection: UDP "connect" to `192.0.2.1:9` probe, accepted only if private IPv4 (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`); else first up, non-loopback, private IPv4 interface; else host `127.0.0.1` and the no-LAN banner.
- Default port stays 8080; no automatic port change.
- The browser opens the public base URL, never `127.0.0.1`, unless there is no public base URL.
- Expected failures print one line prefixed `oathdigital: ` to stderr and exit with status 2, never a stack trace. Settings-template write failure and browser-open failure are warnings.
- Seat links, cookies, databases, and raw server logs never appear in published evidence; smoke failure output redacts `/s/<code>`.
- `packaging/jlink-modules.txt` is the only module list; the bundling script fails when `jdeps` reports a module missing from it.
- Release verification and bundling use Java 21. Locally: `export JAVA_HOME=/Users/roman/projects/oathdigital/.tooling/jdk-21.0.12.1+1/Contents/Home; export PATH="$JAVA_HOME/bin:$PATH"`. Plain `./sbtw test` may use the default JDK 17.
- Out of scope: Intel macOS and Linux arm64 bundled archives, code signing and notarization, installers, tray app, automatic firewall changes.
- Every commit message ends with the session's `Co-Authored-By` trailer.

## File Structure

| File | Responsibility |
| --- | --- |
| `src/main/scala/oathdigital/server/DesktopPaths.scala` | Per-OS app-data folder, settings file path, default database path |
| `src/main/scala/oathdigital/server/DesktopSettings.scala` | Settings keys, template text, load and write-if-missing |
| `src/main/scala/oathdigital/server/LanAddress.scala` | Private-IPv4 test, pure chooser, system probe and interface listing |
| `src/main/scala/oathdigital/server/DesktopLaunchProfile.scala` | `OATH_LAUNCH` switch, precedence merge, `prepare` entry point |
| `src/main/scala/oathdigital/server/DesktopConsole.scala` | Banner text, browser URL and command, bind-failure message |
| `src/main/scala/oathdigital/server/OathServer.scala` | Wiring only |
| `src/test/scala/oathdigital/server/Desktop*Suite.scala`, `LanAddressSuite.scala` | Unit tests per unit above |
| `packaging/desktop/Start Oath Digital.command`, `start-oathdigital.sh`, `Start Oath Digital.bat` | Double-click Start files |
| `packaging/jlink-modules.txt` | The bundled runtime's module list |
| `build.sbt` | Launcher define, Start file mappings, `verifyPackageMappings` assertions |
| `.gitattributes` | CRLF for `.bat` |
| `scripts/package-bundled-runtime.sh` | Tested all-platform archive to bundled archive |
| `scripts/smoke-bundled-distribution.sh`, `.ps1` | Bundled smoke for macOS/Linux and Windows |
| `scripts/verify-alpha-release.sh` | New local `bundled` mode |
| `.github/workflows/alpha-release.yml` | `bundled` matrix job; `publish` depends on it |
| `docs/operations/*.md`, `docs/ROADMAP.md` | Operator documentation and acceptance rows |

## Decisions Taken Where the Spec Was Open

- The port-in-use one-line message applies under the desktop profile only. CLI and OCI runs keep today's bind-failure behavior, per the spec's "no change to command-line or OCI behavior".
- `OATH_LAUNCH` set to a value other than `desktop` is a configuration error rather than silently ignored.
- The Windows smoke cannot stop the server gracefully from a script, so the Windows database-close check is acceptance row 22, not CI.
- macOS ad-hoc signing is added only if the Task 12 quarantine checkpoint shows the bundled `java` is blocked. Until then the script verifies the existing signature.

---

### Task 1: Desktop paths

**Files:**
- Create: `src/main/scala/oathdigital/server/DesktopPaths.scala`
- Test: `src/test/scala/oathdigital/server/DesktopPathsSuite.scala`

**Interfaces:**
- Produces:
  - `DesktopPaths.appDataDirectory(osName: String, environment: Map[String, String], fallbackHome: String): java.nio.file.Path`
  - `DesktopPaths.settingsFile(appData: Path): Path` = `appData/oathdigital.properties`
  - `DesktopPaths.databasePath(appData: Path): Path` = `appData/data/database`

- [ ] **Step 1: Write the failing test**

```scala
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./sbtw "testOnly oathdigital.server.DesktopPathsSuite"`
Expected: FAIL, compilation error `not found: value DesktopPaths`.

- [ ] **Step 3: Write minimal implementation**

```scala
package oathdigital.server

import java.nio.file.{Path, Paths}

/** Per-user locations used by the desktop launch profile. */
object DesktopPaths {
  def appDataDirectory(
      osName: String,
      environment: Map[String, String],
      fallbackHome: String
  ): Path = {
    val home = nonBlank(environment, "HOME").getOrElse(fallbackHome)
    val os = osName.toLowerCase
    if (os.startsWith("mac"))
      Paths.get(home, "Library", "Application Support", "OathDigital")
    else if (os.startsWith("windows"))
      nonBlank(environment, "LOCALAPPDATA")
        .map(Paths.get(_, "OathDigital"))
        .getOrElse(Paths.get(home, "AppData", "Local", "OathDigital"))
    else
      nonBlank(environment, "XDG_DATA_HOME")
        .map(Paths.get(_, "oathdigital"))
        .getOrElse(Paths.get(home, ".local", "share", "oathdigital"))
  }

  def settingsFile(appData: Path): Path =
    appData.resolve("oathdigital.properties")

  def databasePath(appData: Path): Path =
    appData.resolve("data").resolve("database")

  private def nonBlank(
      environment: Map[String, String],
      name: String
  ): Option[String] = environment.get(name).filter(_.trim.nonEmpty)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./sbtw "testOnly oathdigital.server.DesktopPathsSuite"`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/server/DesktopPaths.scala src/test/scala/oathdigital/server/DesktopPathsSuite.scala
git commit -m "feat: resolve per-user desktop app-data paths"
```

---

### Task 2: Desktop settings file

**Files:**
- Create: `src/main/scala/oathdigital/server/DesktopSettings.scala`
- Test: `src/test/scala/oathdigital/server/DesktopSettingsSuite.scala`

**Interfaces:**
- Produces:
  - `DesktopSettings.Keys: Vector[String]` (the five keys, in the order listed in Global Constraints)
  - `DesktopSettings.template: String`
  - `DesktopSettings.load(file: Path): Either[String, Map[String, String]]` — missing file is `Right(Map.empty)`; blank values are dropped; values are trimmed.
  - `DesktopSettings.writeTemplateIfMissing(file: Path): Option[String]` — `Some(warning)` on failure.

- [ ] **Step 1: Write the failing test**

```scala
package oathdigital.server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

class DesktopSettingsSuite extends munit.FunSuite {
  private val directory = FunFixture[Path](
    setup = _ => Files.createTempDirectory("oathdigital-settings"),
    teardown = path => deleteRecursively(path)
  )

  directory.test("a missing file has no settings") { dir =>
    assertEquals(DesktopSettings.load(dir.resolve("oathdigital.properties")), Right(Map.empty[String, String]))
  }

  directory.test("known keys load trimmed and blank values are dropped") { dir =>
    val file = write(dir, "OATH_PORT = 9000 \nOATH_HOST=\n# OATH_OPEN_BROWSER=false\nOATH_DATABASE_PATH=C:/OathData/database\n")
    assertEquals(
      DesktopSettings.load(file),
      Right(Map("OATH_PORT" -> "9000", "OATH_DATABASE_PATH" -> "C:/OathData/database"))
    )
  }

  directory.test("an unknown key is a configuration error naming the file") { dir =>
    val file = write(dir, "OATH_PORT=9000\nOATH_PROT=9001\n")
    assertEquals(DesktopSettings.load(file), Left(s"$file: unknown setting OATH_PROT"))
  }

  directory.test("an unreadable settings path is a configuration error") { dir =>
    val file = Files.createDirectory(dir.resolve("oathdigital.properties"))
    assertEquals(DesktopSettings.load(file), Left(s"$file: cannot read settings file"))
  }

  directory.test("the template comments out every key and loads as empty") { dir =>
    val file = dir.resolve("app").resolve("oathdigital.properties")
    assertEquals(DesktopSettings.writeTemplateIfMissing(file), None)
    val lines = new String(Files.readAllBytes(file), UTF_8).linesIterator.toVector
    DesktopSettings.Keys.foreach(key => assert(lines.exists(_.startsWith(s"#$key=")), key))
    assert(lines.forall(line => line.trim.isEmpty || line.startsWith("#")), lines.mkString("\n"))
    assertEquals(DesktopSettings.load(file), Right(Map.empty[String, String]))
  }

  directory.test("an existing settings file is never overwritten") { dir =>
    val file = write(dir, "OATH_PORT=9000\n")
    assertEquals(DesktopSettings.writeTemplateIfMissing(file), None)
    assertEquals(new String(Files.readAllBytes(file), UTF_8), "OATH_PORT=9000\n")
  }

  directory.test("a template that cannot be written is a warning") { dir =>
    val blocker = write(dir, "not a directory")
    val warning = DesktopSettings.writeTemplateIfMissing(blocker.resolve("oathdigital.properties"))
    assert(warning.exists(_.startsWith("could not create settings file ")), warning.toString)
  }

  private def write(dir: Path, content: String): Path =
    Files.write(dir.resolve("oathdigital.properties"), content.getBytes(UTF_8))

  private def deleteRecursively(path: Path): Unit = {
    if (Files.isDirectory(path)) {
      val children = Files.list(path)
      try children.toArray.foreach(child => deleteRecursively(child.asInstanceOf[Path]))
      finally children.close()
    }
    Files.deleteIfExists(path)
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./sbtw "testOnly oathdigital.server.DesktopSettingsSuite"`
Expected: FAIL, `not found: value DesktopSettings`.

- [ ] **Step 3: Write minimal implementation**

```scala
package oathdigital.server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.Properties

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

/** The desktop profile's optional per-user settings file. */
object DesktopSettings {
  val Keys: Vector[String] = Vector(
    "OATH_HOST",
    "OATH_PORT",
    "OATH_PUBLIC_BASE_URL",
    "OATH_DATABASE_PATH",
    "OATH_OPEN_BROWSER"
  )

  val template: String = Vector(
    "# Oath Digital settings, used when the server is started with Start Oath Digital.",
    "# Remove the leading # from a line to use it. Environment variables and",
    "# command-line options override this file. Restart the server after editing.",
    "# On Windows, write paths with forward slashes, for example C:/OathData/database.",
    "",
    "# Address to listen on. 0.0.0.0 accepts other computers on your network;",
    "# 127.0.0.1 accepts only this computer.",
    "#OATH_HOST=0.0.0.0",
    "",
    "# Port to listen on. Seat links contain the port.",
    "#OATH_PORT=8080",
    "",
    "# Address players open, if the detected one is wrong. Seat links contain it.",
    "#OATH_PUBLIC_BASE_URL=http://192.168.1.20:8080",
    "",
    "# Database file prefix. Default: the data folder next to this file.",
    "#OATH_DATABASE_PATH=/path/to/OathDigitalData/database",
    "",
    "# Set to false to stop the browser opening at startup.",
    "#OATH_OPEN_BROWSER=true",
    ""
  ).mkString("\n")

  def load(file: Path): Either[String, Map[String, String]] =
    if (!Files.exists(file)) Right(Map.empty)
    else
      try {
        val properties = new Properties()
        val reader = Files.newBufferedReader(file, UTF_8)
        try properties.load(reader)
        finally reader.close()
        val values = properties.stringPropertyNames.asScala.toVector.sorted
          .map(key => key -> properties.getProperty(key).trim)
        values.map(_._1).find(key => !Keys.contains(key)) match {
          case Some(unknown) => Left(s"$file: unknown setting $unknown")
          case None => Right(values.filter(_._2.nonEmpty).toMap)
        }
      } catch {
        case NonFatal(_) => Left(s"$file: cannot read settings file")
      }

  def writeTemplateIfMissing(file: Path): Option[String] =
    if (Files.exists(file)) None
    else
      try {
        Files.createDirectories(file.getParent)
        Files.write(
          file,
          template.getBytes(UTF_8),
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE
        )
        None
      } catch {
        case NonFatal(error) =>
          Some(s"could not create settings file $file: ${error.getMessage}")
      }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./sbtw "testOnly oathdigital.server.DesktopSettingsSuite"`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/server/DesktopSettings.scala src/test/scala/oathdigital/server/DesktopSettingsSuite.scala
git commit -m "feat: load and template the desktop settings file"
```

---

### Task 3: LAN address detection

**Files:**
- Create: `src/main/scala/oathdigital/server/LanAddress.scala`
- Test: `src/test/scala/oathdigital/server/LanAddressSuite.scala`

**Interfaces:**
- Produces:
  - `final case class NetworkAddress(interfaceName: String, up: Boolean, loopback: Boolean, address: java.net.InetAddress)`
  - `LanAddress.isPrivateIpv4(address: InetAddress): Boolean`
  - `LanAddress.choose(probe: Option[InetAddress], addresses: Seq[NetworkAddress]): Option[String]` — dotted IPv4 text
  - `LanAddress.detect(): Option[String]` — `choose(probeDefaultRoute(), systemAddresses())`

- [ ] **Step 1: Write the failing test**

```scala
package oathdigital.server

import java.net.InetAddress

class LanAddressSuite extends munit.FunSuite {
  private def ip(a: Int, b: Int, c: Int, d: Int): InetAddress =
    InetAddress.getByAddress(Array(a, b, c, d).map(_.toByte))

  private def interface(name: String, address: InetAddress, up: Boolean = true, loopback: Boolean = false) =
    NetworkAddress(name, up, loopback, address)

  test("private IPv4 ranges are recognized at their boundaries") {
    assert(LanAddress.isPrivateIpv4(ip(10, 0, 0, 1)))
    assert(LanAddress.isPrivateIpv4(ip(172, 16, 0, 1)))
    assert(LanAddress.isPrivateIpv4(ip(172, 31, 255, 254)))
    assert(LanAddress.isPrivateIpv4(ip(192, 168, 1, 20)))
    assert(!LanAddress.isPrivateIpv4(ip(172, 15, 0, 1)))
    assert(!LanAddress.isPrivateIpv4(ip(172, 32, 0, 1)))
    assert(!LanAddress.isPrivateIpv4(ip(8, 8, 8, 8)))
    assert(!LanAddress.isPrivateIpv4(ip(127, 0, 0, 1)))
    assert(!LanAddress.isPrivateIpv4(InetAddress.getByName("fd00::1")))
  }

  test("a private default-route probe wins over earlier Docker or VPN interfaces") {
    val interfaces = Seq(
      interface("docker0", ip(172, 17, 0, 1)),
      interface("utun3", ip(10, 8, 0, 2)),
      interface("en0", ip(192, 168, 1, 20))
    )
    assertEquals(LanAddress.choose(Some(ip(192, 168, 1, 20)), interfaces), Some("192.168.1.20"))
  }

  test("a public probe result is rejected in favor of the first private interface") {
    val interfaces = Seq(interface("en0", ip(192, 168, 1, 20)))
    assertEquals(LanAddress.choose(Some(ip(203, 0, 113, 7)), interfaces), Some("192.168.1.20"))
  }

  test("the fallback skips down and loopback interfaces") {
    val interfaces = Seq(
      interface("lo0", ip(10, 0, 0, 9), loopback = true),
      interface("en1", ip(10, 0, 0, 5), up = false),
      interface("en0", ip(10, 0, 0, 7))
    )
    assertEquals(LanAddress.choose(None, interfaces), Some("10.0.0.7"))
  }

  test("no private address means no LAN address") {
    assertEquals(LanAddress.choose(None, Seq(interface("en0", ip(203, 0, 113, 7)))), None)
    assertEquals(LanAddress.choose(None, Seq.empty), None)
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./sbtw "testOnly oathdigital.server.LanAddressSuite"`
Expected: FAIL, `not found: value LanAddress`.

- [ ] **Step 3: Write minimal implementation**

```scala
package oathdigital.server

import java.net.{DatagramSocket, Inet4Address, InetAddress, NetworkInterface}

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

final case class NetworkAddress(
    interfaceName: String,
    up: Boolean,
    loopback: Boolean,
    address: InetAddress
)

/** Picks the address other computers on the local network can reach. */
object LanAddress {
  // TEST-NET-1: connecting a UDP socket sends nothing, but the OS selects the
  // source address of the route it would use, which skips Docker bridges and
  // most VPN adapters.
  private val ProbeDestination = "192.0.2.1"

  def isPrivateIpv4(address: InetAddress): Boolean = address match {
    case ipv4: Inet4Address =>
      val octets = ipv4.getAddress.map(_ & 0xff)
      octets(0) == 10 ||
      (octets(0) == 172 && octets(1) >= 16 && octets(1) <= 31) ||
      (octets(0) == 192 && octets(1) == 168)
    case _ => false
  }

  def choose(
      probe: Option[InetAddress],
      addresses: Seq[NetworkAddress]
  ): Option[String] =
    probe.filter(isPrivateIpv4)
      .orElse(addresses.collectFirst {
        case NetworkAddress(_, true, false, address)
            if isPrivateIpv4(address) => address
      })
      .map(_.getHostAddress)

  def detect(): Option[String] =
    choose(probeDefaultRoute(), systemAddresses())

  private def probeDefaultRoute(): Option[InetAddress] =
    try {
      val socket = new DatagramSocket()
      try {
        socket.connect(InetAddress.getByName(ProbeDestination), 9)
        Option(socket.getLocalAddress).filterNot(_.isAnyLocalAddress)
      } finally socket.close()
    } catch {
      case NonFatal(_) => None
    }

  private def systemAddresses(): Seq[NetworkAddress] =
    try
      NetworkInterface.getNetworkInterfaces.asScala.toVector.flatMap { network =>
        val up = network.isUp
        val loopback = network.isLoopback
        network.getInetAddresses.asScala.toVector
          .map(NetworkAddress(network.getName, up, loopback, _))
      }
    catch {
      case NonFatal(_) => Vector.empty
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./sbtw "testOnly oathdigital.server.LanAddressSuite"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/server/LanAddress.scala src/test/scala/oathdigital/server/LanAddressSuite.scala
git commit -m "feat: detect the host's private LAN address"
```

---

### Task 4: Desktop launch profile

**Files:**
- Create: `src/main/scala/oathdigital/server/DesktopLaunchProfile.scala`
- Test: `src/test/scala/oathdigital/server/DesktopLaunchProfileSuite.scala`

**Interfaces:**
- Consumes: `DesktopPaths` (Task 1), `DesktopSettings` (Task 2), `ServerConfig.parse(arguments, environment, version)` (existing).
- Produces:
  - `final case class DesktopLaunch(environment: Map[String, String], settingsFile: Path, openBrowser: Boolean, lanDetectionFailed: Boolean, warnings: Vector[String])`
  - `DesktopLaunchProfile.resolve(arguments: Array[String], environment: Map[String, String], settings: Map[String, String], appData: Path, detectLanAddress: () => Option[String]): Either[String, DesktopLaunch]` — `warnings` empty.
  - `DesktopLaunchProfile.prepare(arguments: Array[String], environment: Map[String, String], osName: String, fallbackHome: String, detectLanAddress: () => Option[String]): Either[String, Option[DesktopLaunch]]` — `Right(None)` when `OATH_LAUNCH` is unset, touching no files.

- [ ] **Step 1: Write the failing test**

```scala
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./sbtw "testOnly oathdigital.server.DesktopLaunchProfileSuite"`
Expected: FAIL, `not found: type DesktopLaunch`.

- [ ] **Step 3: Write minimal implementation**

```scala
package oathdigital.server

import java.nio.file.{Files, Path, Paths}

import scala.util.control.NonFatal

final case class DesktopLaunch(
    environment: Map[String, String],
    settingsFile: Path,
    openBrowser: Boolean,
    lanDetectionFailed: Boolean,
    warnings: Vector[String]
)

/**
 * The double-click launch profile. Only active when `OATH_LAUNCH=desktop`;
 * it fills in unset values and leaves validation to `ServerConfig.parse`.
 */
object DesktopLaunchProfile {
  val LaunchVariable = "OATH_LAUNCH"
  private val Wildcard = "0.0.0.0"
  private val DefaultPort = "8080"
  private val Flags = Map(
    "OATH_HOST" -> "--host",
    "OATH_PORT" -> "--port",
    "OATH_PUBLIC_BASE_URL" -> "--public-base-url",
    "OATH_DATABASE_PATH" -> "--database-path"
  )
  private val Loopback = Set("127.0.0.1", "localhost", "::1", "[::1]")

  def prepare(
      arguments: Array[String],
      environment: Map[String, String],
      osName: String,
      fallbackHome: String,
      detectLanAddress: () => Option[String]
  ): Either[String, Option[DesktopLaunch]] =
    environment.get(LaunchVariable) match {
      case None => Right(None)
      case Some("desktop") =>
        val appData =
          DesktopPaths.appDataDirectory(osName, environment, fallbackHome)
        val settingsFile = DesktopPaths.settingsFile(appData)
        val templateWarning = DesktopSettings.writeTemplateIfMissing(settingsFile)
        for {
          settings <- DesktopSettings.load(settingsFile)
          launch <- resolve(arguments, environment, settings, appData, detectLanAddress)
          _ <- createDataFolder(launch)
        } yield Some(launch.copy(warnings = templateWarning.toVector))
      case Some(_) => Left(s"$LaunchVariable: must be desktop when set")
    }

  def resolve(
      arguments: Array[String],
      environment: Map[String, String],
      settings: Map[String, String],
      appData: Path,
      detectLanAddress: () => Option[String]
  ): Either[String, DesktopLaunch] = {
    def flagValue(key: String): Option[String] = Flags.get(key).flatMap { flag =>
      val index = arguments.lastIndexOf(flag)
      if (index >= 0 && index + 1 < arguments.length) Some(arguments(index + 1))
      else None
    }
    def higher(key: String): Option[String] =
      flagValue(key).orElse(environment.get(key)).orElse(settings.get(key))

    val openBrowser = environment.get("OATH_OPEN_BROWSER")
      .orElse(settings.get("OATH_OPEN_BROWSER"))
      .getOrElse("true").trim.toLowerCase match {
        case "true" => Right(true)
        case "false" => Right(false)
        case _ => Left("OATH_OPEN_BROWSER: must be true or false")
      }

    openBrowser.map { open =>
      val port = higher("OATH_PORT").map(_.trim).getOrElse(DefaultPort)
      val host = higher("OATH_HOST").map(_.trim).getOrElse(Wildcard)
      val (address, lanDetectionFailed) =
        if (higher("OATH_PUBLIC_BASE_URL").nonEmpty) (Map.empty[String, String], false)
        else if (host == Wildcard) detectLanAddress() match {
          case Some(lan) => (Map("OATH_PUBLIC_BASE_URL" -> s"http://$lan:$port"), false)
          case None => (Map("OATH_HOST" -> "127.0.0.1"), true)
        }
        else if (Loopback.contains(host.toLowerCase)) (Map.empty[String, String], false)
        else (Map("OATH_PUBLIC_BASE_URL" -> s"http://${bracketed(host)}:$port"), false)

      val defaults = Map(
        "OATH_HOST" -> Wildcard,
        "OATH_DATABASE_PATH" -> DesktopPaths.databasePath(appData).toString
      ).filter { case (key, _) => higher(key).isEmpty }
      val fromSettings = settings.filter { case (key, _) =>
        Flags.contains(key) && flagValue(key).isEmpty && !environment.contains(key)
      }

      DesktopLaunch(
        defaults ++ fromSettings ++ environment ++ address,
        DesktopPaths.settingsFile(appData),
        open,
        lanDetectionFailed,
        Vector.empty
      )
    }
  }

  private def createDataFolder(launch: DesktopLaunch): Either[String, Unit] =
    launch.environment.get("OATH_DATABASE_PATH") match {
      case None => Right(())
      case Some(databasePath) =>
        val folder = Paths.get(databasePath).toAbsolutePath.normalize.getParent
        try {
          Files.createDirectories(folder)
          Right(())
        } catch {
          case NonFatal(_) => Left(s"cannot create data folder $folder")
        }
    }

  private def bracketed(host: String): String =
    if (host.contains(":") && !host.startsWith("[")) s"[$host]" else host
}
```

Note: when a `--database-path` flag is present, `OATH_DATABASE_PATH` may still be in the environment; the data folder is then created for the environment value only. That is harmless, and `ServerRuntime` handles the flag's path as today.

- [ ] **Step 4: Run test to verify it passes**

Run: `./sbtw "testOnly oathdigital.server.DesktopLaunchProfileSuite"`
Expected: PASS, 15 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/server/DesktopLaunchProfile.scala src/test/scala/oathdigital/server/DesktopLaunchProfileSuite.scala
git commit -m "feat: merge desktop launch settings with defined precedence"
```

---

### Task 5: Banner, browser, and bind-failure message

**Files:**
- Create: `src/main/scala/oathdigital/server/DesktopConsole.scala`
- Test: `src/test/scala/oathdigital/server/DesktopConsoleSuite.scala`

**Interfaces:**
- Consumes: `DesktopLaunch` (Task 4), `ServerConfig` (existing).
- Produces:
  - `DesktopConsole.banner(config: ServerConfig, launch: DesktopLaunch): String` — lines joined by `System.lineSeparator`, ending with one.
  - `DesktopConsole.browserUrl(config: ServerConfig): String`
  - `DesktopConsole.browserCommand(osName: String, url: String): Vector[String]`
  - `DesktopConsole.openBrowser(osName: String, url: String, run: Vector[String] => Unit): Option[String]` — warning on failure.
  - `DesktopConsole.bindFailure(error: Throwable, port: Int, settingsFile: Path): Option[String]`

- [ ] **Step 1: Write the failing test**

```scala
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./sbtw "testOnly oathdigital.server.DesktopConsoleSuite"`
Expected: FAIL, `not found: value DesktopConsole`.

- [ ] **Step 3: Write minimal implementation**

```scala
package oathdigital.server

import java.net.BindException
import java.nio.file.Path

import scala.util.control.NonFatal

/** What the desktop profile shows and opens after the server binds. */
object DesktopConsole {
  def banner(config: ServerConfig, launch: DesktopLaunch): String = {
    val address = config.publicBaseUrl match {
      case Some(url) => s"  Players open:  $url"
      case None => s"  Only this computer can connect: http://127.0.0.1:${config.port}"
    }
    val lines =
      Vector(
        s"Oath Digital ${config.version} is running.",
        address,
        s"  Data folder:   ${config.databasePath.getParent}",
        s"  Settings:      ${launch.settingsFile}"
      ) ++
        (if (launch.lanDetectionFailed)
           Vector("No local network address found. Set OATH_PUBLIC_BASE_URL in the settings file.")
         else Vector.empty) ++
        (if (config.publicBaseUrl.nonEmpty)
           Vector("Seat links contain this address. If it changes, players need the new address.")
         else Vector.empty) :+
        "Close this window or press Ctrl-C to stop."
    lines.mkString("", System.lineSeparator, System.lineSeparator)
  }

  def browserUrl(config: ServerConfig): String =
    config.publicBaseUrl
      .map(_.toString.stripSuffix("/") + "/")
      .getOrElse(s"http://127.0.0.1:${config.port}/")

  def browserCommand(osName: String, url: String): Vector[String] = {
    val os = osName.toLowerCase
    if (os.startsWith("mac")) Vector("open", url)
    else if (os.startsWith("windows")) Vector("cmd", "/c", "start", "", url)
    else Vector("xdg-open", url)
  }

  def openBrowser(
      osName: String,
      url: String,
      run: Vector[String] => Unit
  ): Option[String] =
    try {
      run(browserCommand(osName, url))
      None
    } catch {
      case NonFatal(error) => Some(s"could not open a browser: ${error.getMessage}")
    }

  def bindFailure(
      error: Throwable,
      port: Int,
      settingsFile: Path
  ): Option[String] =
    Iterator.iterate(error)(_.getCause).takeWhile(_ != null).take(20)
      .collectFirst {
        case cause if cause.isInstanceOf[BindException] ||
            cause.getClass.getSimpleName == "BindFailedException" =>
          s"port $port is in use. Is Oath Digital already running? " +
            s"Change OATH_PORT in $settingsFile."
      }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./sbtw "testOnly oathdigital.server.DesktopConsoleSuite"`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/server/DesktopConsole.scala src/test/scala/oathdigital/server/DesktopConsoleSuite.scala
git commit -m "feat: render the desktop banner and open the browser"
```

---

### Task 6: Wire the desktop profile into server startup

**Files:**
- Modify: `src/main/scala/oathdigital/server/OathServer.scala:14-22` (configuration), `:81-100` (bind and after-bind)

**Interfaces:**
- Consumes: `DesktopLaunchProfile.prepare`, `LanAddress.detect`, `DesktopConsole.*`.
- Produces: startup behavior the smoke in Task 9 asserts: banner lines `  Players open:  <url>` or `  Only this computer can connect: <url>` on stdout after `Oath Digital server listening at`.

- [ ] **Step 1: Replace the configuration block**

Replace lines 17-22 of `OathServer.scala` with:

```scala
    val osName = sys.props.getOrElse("os.name", "")
    val launch = DesktopLaunchProfile.prepare(
      arguments,
      sys.env,
      osName,
      sys.props.getOrElse("user.home", ""),
      () => LanAddress.detect()
    ) match {
      case Left(error) =>
        System.err.println(s"oathdigital: $error")
        sys.exit(2)
      case Right(prepared) => prepared
    }
    launch.toVector.flatMap(_.warnings)
      .foreach(warning => System.err.println(s"oathdigital: warning: $warning"))
    val environment = launch.fold(sys.env)(_.environment)
    val config = ServerConfig.parse(arguments, environment, version) match {
      case Left(errors) =>
        errors.foreach(error => System.err.println(s"oathdigital: $error"))
        sys.exit(2)
      case Right(parsed) => parsed
    }
```

- [ ] **Step 2: Replace the bind-failure handler and add the after-bind output**

Replace the `catch` block in the `binding` expression and the lines after `readiness.markReady()` up to the closing `)` of the `listening at` log call with:

```scala
          catch {
            case NonFatal(error) =>
              readiness.markStopping()
              runtime.close()
              system.terminate()
              Await.result(system.whenTerminated, 30.seconds)
              launch.flatMap(desktop =>
                DesktopConsole.bindFailure(error, config.port, desktop.settingsFile)
              ) match {
                case Some(message) =>
                  System.err.println(s"oathdigital: $message")
                  sys.exit(2)
                case None => throw error
              }
          }
        readiness.markReady()
        system.log.info(
          "Oath Digital server listening at http://{}:{}/",
          config.host,
          Int.box(config.port)
        )
        launch.foreach { desktop =>
          System.out.print(DesktopConsole.banner(config, desktop))
          System.out.flush()
          if (desktop.openBrowser)
            DesktopConsole.openBrowser(
              osName,
              DesktopConsole.browserUrl(config),
              command => {
                new ProcessBuilder(command: _*)
                  .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                  .redirectError(ProcessBuilder.Redirect.DISCARD)
                  .start()
                ()
              }
            ).foreach(warning => system.log.warn(warning))
        }
```

- [ ] **Step 3: Run the full JVM suite**

Run: `./sbtw test`
Expected: PASS, all suites, including the unchanged `ServerConfigSuite`.

- [ ] **Step 4: Manually confirm desktop startup and the real bind-failure message**

Run from the repository root (development catalog path works from here):

```bash
export JAVA_HOME=/Users/roman/projects/oathdigital/.tooling/jdk-21.0.12.1+1/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./sbtw Universal/stage
scratch=$(mktemp -d)
env -i PATH=/usr/bin:/bin HOME="$scratch" OATH_LAUNCH=desktop OATH_OPEN_BROWSER=false OATH_PORT=18090 target/universal/stage/bin/oathdigital > "$scratch/first.log" 2>&1 &
first=$!
sleep 15
grep -E '^  (Players open:|Only this computer can connect:)' "$scratch/first.log"
env -i PATH=/usr/bin:/bin HOME="$scratch" OATH_LAUNCH=desktop OATH_OPEN_BROWSER=false OATH_PORT=18090 target/universal/stage/bin/oathdigital; echo "exit=$?"
kill -TERM "$first"; wait "$first"
ls "$scratch/Library/Application Support/OathDigital"
```

Expected:
- The first log shows `  Players open:  http://<private address>:18090` (or the no-LAN variant).
- The second run prints exactly one stderr line `oathdigital: port 18090 is in use. Is Oath Digital already running? Change OATH_PORT in <path>/oathdigital.properties.` and `exit=2`, with no stack trace.
- The app-data folder contains `oathdigital.properties` and `data/`.

If the second run prints a stack trace instead, Akka's exception chain differs from the Task 5 matcher. Print the chain (`Iterator.iterate(error)(_.getCause).takeWhile(_ != null).map(_.getClass.getName)`), add a failing `DesktopConsoleSuite` case that reproduces that shape, and extend `bindFailure` until it passes. Then rerun this step.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/server/OathServer.scala
git commit -m "feat: start the server under the desktop launch profile"
```

---

### Task 7: Launcher runtime selection and Start files

**Files:**
- Create: `packaging/desktop/Start Oath Digital.command`, `packaging/desktop/start-oathdigital.sh`, `packaging/desktop/Start Oath Digital.bat`
- Modify: `build.sbt:78-97` (mappings and launcher defines), `build.sbt:112-199` (`verifyPackageMappings`)
- Modify or create: `.gitattributes`

**Interfaces:**
- Produces: archive-root files `Start Oath Digital.command`, `start-oathdigital.sh`, `Start Oath Digital.bat`; launchers that use `../jre` when present. Tasks 8-9 rely on both.

- [ ] **Step 1: Add the failing `verifyPackageMappings` assertions**

In `verifyPackageMappings`, add these three entries to `requiredFiles`:

```scala
        "Start Oath Digital.command",
        "start-oathdigital.sh",
        "Start Oath Digital.bat",
```

and before `val failures =`, add:

```scala
      def launcherSource(destination: String): String = packageMappings
        .collectFirst { case (source, `destination`) => IO.read(source) }
        .getOrElse("")
      val bundledRuntimeFailures = Seq(
        if (launcherSource("bin/oathdigital").contains(
              """then bundled_jvm="$(realpath "${app_home}/../jre")"; fi"""
            )) None
        else Some("bash launcher must prefer a bundled jre/"),
        if (launcherSource("bin/oathdigital.bat").contains(
              """if exist "%APP_HOME%\jre\bin\java.exe" set "BUNDLED_JVM=%APP_HOME%\jre""""
            )) None
        else Some("batch launcher must prefer a bundled jre\\")
      ).flatten
```

and change the failures expression to:

```scala
      val failures = missingFiles.map(path => s"missing $path") ++
        missingJars ++ dockerFailures ++ bundledRuntimeFailures
```

- [ ] **Step 2: Run to verify it fails**

Run: `./sbtw verifyPackageMappings`
Expected: FAIL with `Invalid package mappings: missing Start Oath Digital.command, missing start-oathdigital.sh, missing Start Oath Digital.bat, bash launcher must prefer a bundled jre/, batch launcher must prefer a bundled jre\`.

- [ ] **Step 3: Create the Start files**

`packaging/desktop/Start Oath Digital.command` and `packaging/desktop/start-oathdigital.sh` have identical content:

```sh
#!/bin/sh
# Double-click to start Oath Digital. Close this window or press Ctrl-C to stop.
cd "$(dirname "$0")" || exit 1
OATH_LAUNCH=desktop
export OATH_LAUNCH
bin/oathdigital "$@"
status=$?
case "$status" in
  0|130|143) ;;
  *)
    printf '\nOath Digital stopped with an error (exit %s). Press Return to close this window.\n' "$status"
    read -r _
    ;;
esac
exit "$status"
```

`packaging/desktop/Start Oath Digital.bat`:

```bat
@echo off
rem Double-click to start Oath Digital. Close this window or press Ctrl-C to stop.
setlocal
cd /d "%~dp0"
set "OATH_LAUNCH=desktop"
call "bin\oathdigital.bat" %*
set "status=%ERRORLEVEL%"
if not "%status%"=="0" (
  echo.
  echo Oath Digital stopped with an error ^(exit %status%^).
  pause
)
exit /b %status%
```

Then mark the shell files executable in Git and the batch file CRLF:

```bash
chmod +x "packaging/desktop/Start Oath Digital.command" packaging/desktop/start-oathdigital.sh
printf '*.bat text eol=crlf\n' >> .gitattributes
git add --renormalize .gitattributes "packaging/desktop/Start Oath Digital.bat"
```

- [ ] **Step 4: Add the mappings and launcher defines in `build.sbt`**

After the existing `Universal / mappings ++= { ... }` block, add:

```scala
    Universal / mappings ++= Seq(
      "Start Oath Digital.command",
      "start-oathdigital.sh",
      "Start Oath Digital.bat"
    ).map(name => baseDirectory.value / "packaging/desktop" / name -> name),
```

Append to `bashScriptExtraDefines`:

```scala
      // A bundled-runtime archive ships jre/ beside bin/. The template gives
      // bundled_jvm priority over JAVA_HOME; -java-home still overrides it.
      """if [ -x "${app_home}/../jre/bin/java" ]; then bundled_jvm="$(realpath "${app_home}/../jre")"; fi"""
```

Append to `batScriptExtraDefines`:

```scala
      """if exist "%APP_HOME%\jre\bin\java.exe" set "BUNDLED_JVM=%APP_HOME%\jre""""
```

- [ ] **Step 5: Run to verify it passes, and check the archive keeps executable bits**

Run:

```bash
./sbtw verifyPackageMappings Universal/packageZipTarball Universal/packageBin
scratch=$(mktemp -d)
tar -xzf target/universal/oathdigital-0.1.0-SNAPSHOT.tgz -C "$scratch"
test -x "$scratch/oathdigital-0.1.0-SNAPSHOT/Start Oath Digital.command" && test -x "$scratch/oathdigital-0.1.0-SNAPSHOT/start-oathdigital.sh" && echo tgz-executable
unzip -Z target/universal/oathdigital-0.1.0-SNAPSHOT.zip | grep -E 'start-oathdigital.sh|Start Oath Digital'
sh scripts/smoke-packaged-distribution.sh "$scratch/oathdigital-0.1.0-SNAPSHOT" 18080
```

Expected: `verifyPackageMappings` passes; `tgz-executable`; the ZIP listing shows `-rwxr-xr-x` for the two shell files; the existing packaged smoke passes unchanged.

If the executable bits are missing from either archive, stop and report the task as BLOCKED with the `tar -tvzf` and `unzip -Z` listings. Do not continue to Task 8: a Start file that is not executable fails the whole desktop flow on macOS and Linux.

- [ ] **Step 6: Run the full JVM suite**

Run: `./sbtw test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add build.sbt .gitattributes packaging/desktop
git commit -m "feat: add double-click Start files and bundled runtime selection"
```

---

### Task 8: Bundling script and module list

**Files:**
- Create: `packaging/jlink-modules.txt`, `scripts/package-bundled-runtime.sh`

**Interfaces:**
- Consumes: an extracted all-platform archive directory `oathdigital-<version>` (Task 7 contents).
- Produces: `sh scripts/package-bundled-runtime.sh APP_DIRECTORY TARGET NEW_OUTPUT_DIRECTORY` writes `NEW_OUTPUT_DIRECTORY/oathdigital-<version>-<target>.<tgz|zip>` and prints `<sha256>  <archive>`.

- [ ] **Step 1: Create the module list**

`packaging/jlink-modules.txt`:

```
# Java modules in the bundled runtime. scripts/package-bundled-runtime.sh
# fails when jdeps reports a module missing from this list.
java.base
java.desktop
java.management
java.naming
java.sql
jdk.crypto.ec
jdk.jfr
jdk.localedata
jdk.unsupported
```

- [ ] **Step 2: Create the script**

`scripts/package-bundled-runtime.sh`:

```sh
#!/bin/sh
# Turns an extracted, tested all-platform archive into a bundled-runtime
# archive for one target. Never downloads anything; JAVA_HOME supplies jlink.

set -eu

fail() {
  printf 'bundled runtime packaging failed: %s\n' "$1" >&2
  exit 1
}

[ "$#" -eq 3 ] || fail "usage: $0 APP_DIRECTORY TARGET NEW_OUTPUT_DIRECTORY"
app_directory=$1
target=$2
output_directory=$3

case "$target" in
  macos-arm64|linux-x64) extension=tgz ;;
  windows-x64) extension=zip ;;
  *) fail "unknown target: $target" ;;
esac
[ -d "$app_directory/lib" ] && [ -f "$app_directory/bin/oathdigital" ] ||
  fail "not an extracted Oath Digital archive: $app_directory"
[ ! -e "$app_directory/jre" ] || fail "app directory already contains jre/"
root=$(basename "$app_directory")
case "$root" in
  oathdigital-*) ;;
  *) fail "app directory must be named oathdigital-<version>: $root" ;;
esac
[ -n "${JAVA_HOME:-}" ] || fail "JAVA_HOME must select a Java 21 JDK"
"$JAVA_HOME/bin/java" -version 2>&1 | grep -Eq 'version "21\.' ||
  fail "JAVA_HOME must select Java 21"
[ ! -e "$output_directory" ] || fail "output directory already exists: $output_directory"

# Windows tools need native paths; Git Bash provides cygpath.
native_path() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}
separator=:
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) separator=';' ;; esac

script_directory=$(cd "$(dirname "$0")" && pwd -P)
modules=$(grep -Ev '^[[:space:]]*(#|$)' "$script_directory/../packaging/jlink-modules.txt" |
  tr -d '\r' | paste -sd, -)

classpath=
for jar in "$app_directory"/lib/*.jar; do
  classpath="$classpath$(native_path "$jar")$separator"
done
server_jar=
for jar in "$app_directory"/lib/dev.oathdigital.*.jar; do server_jar=$jar; done
[ -f "$server_jar" ] || fail "server jar not found under $app_directory/lib"
required=$("$JAVA_HOME/bin/jdeps" --multi-release 21 --ignore-missing-deps \
  --print-module-deps -q --class-path "$classpath" "$(native_path "$server_jar")" | tr -d '\r')
missing=
for module in $(printf '%s' "$required" | tr ',' ' '); do
  case ",$modules," in
    *",$module,"*) ;;
    *) missing="$missing $module" ;;
  esac
done
[ -z "$missing" ] ||
  fail "packaging/jlink-modules.txt lacks modules reported by jdeps:$missing"

mkdir -p "$output_directory"
output=$(cd "$output_directory" && pwd -P)
work="$output/work"
mkdir "$work"
cp -R "$app_directory" "$work/$root"
"$JAVA_HOME/bin/jlink" --add-modules "$modules" --include-locales en \
  --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
  --output "$(native_path "$work/$root/jre")"
"$work/$root/jre/bin/java" -version >/dev/null 2>&1 ||
  fail "bundled runtime does not start"
if [ "$(uname -s)" = Darwin ]; then
  codesign --verify "$work/$root/jre/bin/java" ||
    fail "bundled java has an invalid code signature"
fi

archive="$root-$target.$extension"
if [ "$extension" = tgz ]; then
  tar -czf "$output/$archive" -C "$work" "$root"
else
  python=$(command -v python3 || command -v python) ||
    fail "python is required to write zip archives"
  (cd "$work" && "$python" -m zipfile -c "$(native_path "$output/$archive")" "$root")
fi
rm -rf -- "$work"
cd "$output"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$archive"
else
  shasum -a 256 "$archive"
fi
```

Then: `chmod +x scripts/package-bundled-runtime.sh`

- [ ] **Step 3: Verify the jdeps gate fails when a module is missing**

Run:

```bash
export JAVA_HOME=/Users/roman/projects/oathdigital/.tooling/jdk-21.0.12.1+1/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./sbtw Universal/packageZipTarball
scratch=$(mktemp -d)
tar -xzf target/universal/oathdigital-0.1.0-SNAPSHOT.tgz -C "$scratch"
cp packaging/jlink-modules.txt "$scratch/modules.backup"
grep -v '^java.sql$' "$scratch/modules.backup" > packaging/jlink-modules.txt
sh scripts/package-bundled-runtime.sh "$scratch/oathdigital-0.1.0-SNAPSHOT" macos-arm64 "$scratch/out-missing"; echo "exit=$?"
cp "$scratch/modules.backup" packaging/jlink-modules.txt
git diff --exit-code packaging/jlink-modules.txt
```

Expected: `bundled runtime packaging failed: packaging/jlink-modules.txt lacks modules reported by jdeps: java.sql` and `exit=1`; the final `git diff` is clean.

- [ ] **Step 4: Build a real bundled archive**

Run (same shell):

```bash
sh scripts/package-bundled-runtime.sh "$scratch/oathdigital-0.1.0-SNAPSHOT" macos-arm64 "$scratch/out"
ls -lh "$scratch/out"
tar -tzf "$scratch/out/oathdigital-0.1.0-SNAPSHOT-macos-arm64.tgz" | awk -F/ '{print $1}' | sort -u
```

Expected: one checksum line; an archive of roughly 70 MB; the only root entry is `oathdigital-0.1.0-SNAPSHOT`.

- [ ] **Step 5: Commit**

```bash
git add packaging/jlink-modules.txt scripts/package-bundled-runtime.sh
git commit -m "feat: package bundled-runtime archives with jlink"
```

---

### Task 9: Bundled smoke and local verification mode

**Files:**
- Create: `scripts/smoke-bundled-distribution.sh`, `scripts/smoke-bundled-distribution.ps1`
- Modify: `scripts/verify-alpha-release.sh` (new `bundled` case before `*)`, usage line)

**Interfaces:**
- Consumes: bundled archive from Task 8; banner lines from Task 6.
- Produces:
  - `sh scripts/smoke-bundled-distribution.sh APP_DIRECTORY PORT`
  - `pwsh scripts/smoke-bundled-distribution.ps1 -AppDirectory DIR -Port PORT`
  - `sh scripts/verify-alpha-release.sh bundled UNIVERSAL_TGZ TARGET NEW_OUTPUT_DIRECTORY` (tgz targets only)

- [ ] **Step 1: Create the POSIX smoke**

`scripts/smoke-bundled-distribution.sh`:

```sh
#!/bin/sh
# Smokes an extracted bundled-runtime archive under the desktop profile with
# no JAVA_HOME and no Java on PATH. macOS and Linux only.

set -eu

app_directory=${1:-}
port=${2:-}
log_file=
child_pid=
temporary=

fail() {
  printf 'bundled distribution smoke failed: %s\n' "$1" >&2
  if [ -n "$log_file" ] && [ -f "$log_file" ]; then
    sed 's#/s/[A-Za-z0-9_-][A-Za-z0-9_-]*#/s/[REDACTED]#g' "$log_file" >&2
  fi
  exit 1
}

cleanup() {
  if [ -n "$child_pid" ] && kill -0 "$child_pid" 2>/dev/null; then
    kill -KILL "$child_pid" 2>/dev/null || true
  fi
  [ -z "$temporary" ] || rm -rf -- "$temporary"
}
trap cleanup EXIT

case "$port" in ''|*[!0-9]*) fail "port must be an integer: $port" ;; esac
[ -x "$app_directory/jre/bin/java" ] || fail "missing bundled jre/ in $app_directory"
[ -x "$app_directory/Start Oath Digital.command" ] && [ -x "$app_directory/start-oathdigital.sh" ] ||
  fail "Start files must be present and executable"
app=$(cd "$app_directory" && pwd -P)
temporary=$(mktemp -d "${TMPDIR:-/tmp}/oathdigital-bundled.XXXXXX")
temporary=$(cd "$temporary" && pwd -P)
log_file="$temporary/server.log"
mkdir -p "$temporary/home"
case "$(uname -s)" in
  Darwin) app_data="$temporary/home/Library/Application Support/OathDigital" ;;
  *) app_data="$temporary/xdg/oathdigital" ;;
esac

env -i PATH=/usr/bin:/bin HOME="$temporary/home" XDG_DATA_HOME="$temporary/xdg" \
  OATH_LAUNCH=desktop OATH_OPEN_BROWSER=false OATH_PORT="$port" \
  "$app/bin/oathdigital" >"$log_file" 2>&1 &
child_pid=$!

url=
deadline=$(($(date +%s) + 90))
while [ -z "$url" ] && [ "$(date +%s)" -lt "$deadline" ]; do
  kill -0 "$child_pid" 2>/dev/null || fail "server exited before the banner"
  url=$(sed -n \
    -e 's/^  Players open:  \(http:[^ ]*\)$/\1/p' \
    -e 's/^  Only this computer can connect: \(http:[^ ]*\)$/\1/p' \
    "$log_file" | head -n 1)
  [ -n "$url" ] || sleep 1
done
[ -n "$url" ] || fail "banner did not appear within 90 seconds"
case "$url" in *":$port") ;; *) fail "banner address $url does not use port $port" ;; esac

for path in /health/ready / /assets/main.js; do
  curl -fsS --connect-timeout 2 --max-time 10 -o /dev/null "$url$path" ||
    fail "$url$path did not respond successfully"
done
ps -o command= -p "$child_pid" | grep -F "$app/jre/bin/java" >/dev/null ||
  fail "server is not running on the bundled runtime"
[ -f "$app_data/oathdigital.properties" ] || fail "settings file was not created"
ls "$app_data/data" 2>/dev/null | grep -q '^database\.' ||
  fail "database was not created in the app-data folder"

kill -TERM "$child_pid"
wait "$child_pid" 2>/dev/null || true
child_pid=
grep -q 'Oath Digital database closed' "$log_file" || fail "database did not close cleanly"
echo "bundled distribution smoke passed: $app"
```

Then: `chmod +x scripts/smoke-bundled-distribution.sh`

- [ ] **Step 2: Run the POSIX smoke against Task 8's archive**

Run:

```bash
scratch=$(mktemp -d)
./sbtw Universal/packageZipTarball
tar -xzf target/universal/oathdigital-0.1.0-SNAPSHOT.tgz -C "$scratch"
sh scripts/package-bundled-runtime.sh "$scratch/oathdigital-0.1.0-SNAPSHOT" macos-arm64 "$scratch/out"
mkdir "$scratch/bundled"
tar -xzf "$scratch/out/oathdigital-0.1.0-SNAPSHOT-macos-arm64.tgz" -C "$scratch/bundled"
sh scripts/smoke-bundled-distribution.sh "$scratch/bundled/oathdigital-0.1.0-SNAPSHOT" 18081
```

Expected: `bundled distribution smoke passed: <path>`.

Negative check: run the smoke against the all-platform extraction `"$scratch/oathdigital-0.1.0-SNAPSHOT"`. Expected: `bundled distribution smoke failed: missing bundled jre/ in ...`.

- [ ] **Step 3: Create the Windows smoke**

`scripts/smoke-bundled-distribution.ps1`:

```powershell
# Smokes an extracted Windows bundled-runtime archive under the desktop
# profile with no JAVA_HOME and no Java on PATH. Windows has no graceful
# console stop from a script, so the database-close check is manual
# (alpha-acceptance.md).
param(
  [Parameter(Mandatory = $true)][string]$AppDirectory,
  [Parameter(Mandatory = $true)][int]$Port
)
$ErrorActionPreference = 'Stop'
function Fail([string]$Message) { throw "bundled distribution smoke failed: $Message" }

$app = (Resolve-Path -LiteralPath $AppDirectory).Path
$bundledJava = Join-Path $app 'jre\bin\java.exe'
if (-not (Test-Path -LiteralPath $bundledJava)) { Fail "missing bundled jre\ in $app" }
if (-not (Test-Path -LiteralPath (Join-Path $app 'Start Oath Digital.bat'))) { Fail 'missing Start Oath Digital.bat' }

$temporary = Join-Path ([IO.Path]::GetTempPath()) ('oathdigital-bundled-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $temporary | Out-Null
$log = Join-Path $temporary 'server.log'
$errorLog = Join-Path $temporary 'server.err.log'
$appData = Join-Path $temporary 'local\OathDigital'

$env:LOCALAPPDATA = Join-Path $temporary 'local'
Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
$env:PATH = "$env:SystemRoot\System32;$env:SystemRoot"
$env:OATH_LAUNCH = 'desktop'
$env:OATH_OPEN_BROWSER = 'false'
$env:OATH_PORT = "$Port"

$process = Start-Process -FilePath (Join-Path $app 'bin\oathdigital.bat') -NoNewWindow -PassThru `
  -RedirectStandardOutput $log -RedirectStandardError $errorLog
try {
  $url = $null
  for ($i = 0; $i -lt 90 -and -not $url; $i++) {
    Start-Sleep -Seconds 1
    if ($process.HasExited) { Fail "server exited before the banner with code $($process.ExitCode)" }
    if (Test-Path -LiteralPath $log) {
      $match = Select-String -LiteralPath $log -Pattern '^\s+(Players open:|Only this computer can connect:)\s+(http://\S+)' |
        Select-Object -First 1
      if ($match) { $url = $match.Matches[0].Groups[2].Value }
    }
  }
  if (-not $url) { Fail 'banner did not appear within 90 seconds' }
  if (-not $url.EndsWith(":$Port")) { Fail "banner address $url does not use port $Port" }
  foreach ($path in '/health/ready', '/', '/assets/main.js') {
    $response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 -Uri "$url$path"
    if ($response.StatusCode -ne 200) { Fail "$url$path returned $($response.StatusCode)" }
  }
  $java = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object { $_.ExecutablePath -ieq $bundledJava }
  if (-not $java) { Fail 'server is not running on the bundled runtime' }
  if (-not (Test-Path -LiteralPath (Join-Path $appData 'oathdigital.properties'))) { Fail 'settings file was not created' }
  if (-not (Get-ChildItem -LiteralPath (Join-Path $appData 'data') -Filter 'database.*' -ErrorAction SilentlyContinue)) {
    Fail 'database was not created in the app-data folder'
  }
  Write-Output "bundled distribution smoke passed: $app"
}
finally {
  Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object { $_.ExecutablePath -ieq $bundledJava } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
  if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
  Remove-Item -LiteralPath $temporary -Recurse -Force -ErrorAction SilentlyContinue
}
```

This script is first executed by the Task 10 workflow run; there is no local Windows machine.

- [ ] **Step 4: Add the `bundled` mode to `scripts/verify-alpha-release.sh`**

Insert before the final `*)` case:

```sh
  bundled)
    [ "$#" -eq 4 ] || fail "usage: bundled UNIVERSAL_TGZ TARGET NEW_OUTPUT_DIRECTORY"
    case "$3" in
      macos-arm64|linux-x64) ;;
      windows-x64) fail "windows-x64 is built and smoked by the release workflow only" ;;
      *) fail "unknown target: $3" ;;
    esac
    [ -f "$2" ] || fail "missing all-platform archive: $2"
    [ ! -e "$4" ] || fail "output directory already exists"
    temporary=$(mktemp -d "${TMPDIR:-/tmp}/oathdigital-bundled-release.XXXXXX")
    trap 'rm -rf -- "$temporary"' EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM
    mkdir "$temporary/universal" "$temporary/bundled"
    tar -xzf "$2" -C "$temporary/universal"
    root=$(ls -A "$temporary/universal")
    [ "$(printf '%s\n' "$root" | wc -l | tr -d ' ')" = 1 ] || fail "archive must have exactly one versioned root"
    sh scripts/package-bundled-runtime.sh "$temporary/universal/$root" "$3" "$4"
    tar -xzf "$4/$root-$3.tgz" -C "$temporary/bundled"
    sh scripts/smoke-bundled-distribution.sh "$temporary/bundled/$root" 18081
    echo "bundled archive verification passed: $root-$3"
    ;;
```

and change the usage line in `*)` to:

```sh
  *) fail "usage: $0 {validate-tag TAG|check-tag TAG|self-test|archives TAG NEW_OUTPUT_DIRECTORY|bundled UNIVERSAL_TGZ TARGET NEW_OUTPUT_DIRECTORY}" ;;
```

- [ ] **Step 5: Run the verification mode**

Run: `sh scripts/verify-alpha-release.sh bundled target/universal/oathdigital-0.1.0-SNAPSHOT.tgz macos-arm64 "$(mktemp -d)/out"`
Expected: `bundled distribution smoke passed: ...` then `bundled archive verification passed: oathdigital-0.1.0-SNAPSHOT-macos-arm64`.

Run: `sh scripts/verify-alpha-release.sh self-test`
Expected: unchanged pass line.

- [ ] **Step 6: Commit**

```bash
git add scripts/smoke-bundled-distribution.sh scripts/smoke-bundled-distribution.ps1 scripts/verify-alpha-release.sh
git commit -m "test: smoke bundled-runtime archives without a system Java"
```

---

### Task 10: Release workflow job

**Files:**
- Modify: `.github/workflows/alpha-release.yml` (new `bundled` job after `images`; `publish.needs`; publish download and checksum steps)

**Interfaces:**
- Consumes: `archives` job outputs `commit`, `version` and artifact `release-bundle`; Task 8 and Task 9 scripts.
- Produces: artifacts `bundled-macos-arm64`, `bundled-windows-x64`, `bundled-linux-x64`; three archives attached to the prerelease and covered by `SHA256SUMS`.

- [ ] **Step 1: Add the `bundled` job**

Insert after the `images` job, reusing the exact pinned action revisions already in the file:

```yaml
  bundled:
    needs: archives
    strategy:
      fail-fast: false
      matrix:
        include:
          - runner: macos-14
            target: macos-arm64
            extension: tgz
          - runner: windows-latest
            target: windows-x64
            extension: zip
          - runner: ubuntu-24.04
            target: linux-x64
            extension: tgz
    runs-on: ${{ matrix.runner }}
    timeout-minutes: 30
    permissions:
      contents: read
    defaults:
      run:
        shell: bash
    env:
      RELEASE_VERSION: ${{ needs.archives.outputs.version }}
      TARGET: ${{ matrix.target }}
      EXTENSION: ${{ matrix.extension }}
    steps:
      - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683 # v4.2.2
        with:
          ref: ${{ needs.archives.outputs.commit }}
          persist-credentials: false
      - uses: actions/setup-java@c5195efecf7bdfc987ee8bae7a71cb8b11521c00 # v4.7.1
        with:
          distribution: temurin
          java-version: '21'
      - uses: actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093 # v4.3.0
        with:
          name: release-bundle
          path: release-bundle
      - name: Build the bundled archive from the tested all-platform archive
        run: |
          mkdir universal "extracted app"
          tar -xzf "release-bundle/oathdigital-$RELEASE_VERSION.tgz" -C universal
          sh scripts/package-bundled-runtime.sh "universal/oathdigital-$RELEASE_VERSION" "$TARGET" bundled
          archive="bundled/oathdigital-$RELEASE_VERSION-$TARGET.$EXTENSION"
          if [ "$EXTENSION" = zip ]; then
            python -m zipfile -e "$archive" "extracted app"
          else
            tar -xzf "$archive" -C "extracted app"
          fi
          [ "$(ls -A "extracted app")" = "oathdigital-$RELEASE_VERSION" ]
      - name: Smoke the extracted bundled archive
        if: runner.os != 'Windows'
        run: sh scripts/smoke-bundled-distribution.sh "extracted app/oathdigital-$RELEASE_VERSION" 18080
      - name: Smoke the extracted bundled archive
        if: runner.os == 'Windows'
        shell: pwsh
        run: ./scripts/smoke-bundled-distribution.ps1 -AppDirectory "extracted app/oathdigital-$env:RELEASE_VERSION" -Port 18080
      - uses: actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02 # v4.6.2
        with:
          name: bundled-${{ matrix.target }}
          path: bundled/oathdigital-*-${{ matrix.target }}.${{ matrix.extension }}
          if-no-files-found: error
          retention-days: 14
```

The extraction folder name contains a space on purpose: it exercises the spec's Windows risk that `BUNDLED_JVM` must work when the path contains spaces, on every runner.

- [ ] **Step 2: Make publication depend on it and attach the archives**

In `publish`, change `needs: [archives, images]` to `needs: [archives, images, bundled]`. After the `release-bundle` download step, add:

```yaml
      - uses: actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093 # v4.3.0
        with:
          pattern: bundled-*
          path: release-bundle
          merge-multiple: true
```

In the `Create prerelease without replacing existing assets` step, change the checksum line to:

```yaml
          (cd release-bundle && sha256sum amd64-evidence.txt arm64-evidence.txt manifest-evidence.txt \
            "oathdigital-$RELEASE_VERSION-macos-arm64.tgz" "oathdigital-$RELEASE_VERSION-linux-x64.tgz" \
            "oathdigital-$RELEASE_VERSION-windows-x64.zip" >> SHA256SUMS)
```

- [ ] **Step 3: Validate the workflow syntax**

Run: `python3 -c "import yaml,sys; d=yaml.safe_load(open('.github/workflows/alpha-release.yml')); print(sorted(d['jobs'])); print(d['jobs']['publish']['needs'])"`
Expected: `['archives', 'bundled', 'images', 'publish']` and `['archives', 'images', 'bundled']`.

If `actionlint` is on `PATH`, also run `actionlint .github/workflows/alpha-release.yml` and expect no findings. The first real execution happens with the repository's first `publish=false` workflow dispatch; record that as still pending in Task 11's docs.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/alpha-release.yml
git commit -m "ci: build and smoke bundled-runtime archives per OS"
```

---

### Task 11: Operator documentation and acceptance rows

**Files:**
- Modify: `docs/operations/quick-start.md` (new leading section; existing sections under "Advanced")
- Modify: `docs/operations/configuration.md` (new "Desktop launch profile" section)
- Modify: `docs/operations/releases.md` (bundled archives in "Versions and compatibility", workflow step 3, prerelease contents)
- Modify: `docs/operations/packaged-smoke-test.md` (bundled smoke)
- Modify: `docs/operations/alpha-acceptance.md` (bundled rows)
- Modify: `docs/ROADMAP.md` (Phase 5 item 2)

- [ ] **Step 1: Rewrite the start of `quick-start.md`**

Insert after the introduction paragraph, before `## Universal archive`:

```markdown
## Easiest: download for your computer

Download the archive for your computer. It includes its own Java, so nothing
else needs installing:

| Computer | Download |
| --- | --- |
| Mac with Apple silicon | `oathdigital-0.1.0-alpha.1-macos-arm64.tgz` |
| Windows (64-bit Intel or AMD) | `oathdigital-0.1.0-alpha.1-windows-x64.zip` |
| Linux (64-bit Intel or AMD) | `oathdigital-0.1.0-alpha.1-linux-x64.tgz` |

1. Extract the archive: double-click it on macOS, or right-click and choose
   **Extract All** on Windows. On Linux, extract it with your file manager.
2. Open the extracted `oathdigital-0.1.0-alpha.1` folder and start the server:
   - macOS: double-click **Start Oath Digital.command**. The first time, macOS
     says it cannot verify the file. Open **System Settings › Privacy &
     Security**, choose **Open Anyway** for it, and confirm.
   - Windows: double-click **Start Oath Digital.bat**. If SmartScreen appears,
     choose **More info › Run anyway**.
   - Linux: run `./start-oathdigital.sh` from a terminal in that folder, or use
     your file manager's "Run in terminal" action.
3. If the system asks whether to allow incoming connections, allow them on
   private networks only.
4. The window shows the address players open, for example
   `http://192.168.1.20:8080`, and your browser opens the game-creation page.
   Continue with [Create and distribute seats](#create-and-distribute-seats).
5. Keep the window open while you play. Close it, or press Ctrl-C, to stop.

Your games are stored outside the extracted folder, so you can replace the
folder with a newer version. The window shows the data folder and the settings
file. See the [desktop launch profile](configuration.md#desktop-launch-profile)
to change the port or the address, or to turn off the browser opening.

Seat links contain the address. If the host's network address changes, for
example after joining a different network, players need the new address.

## Advanced: all-platform archive and OCI image

The sections below need a terminal. Use them for other computers, servers, or
HTTPS arrangements.
```

Change `## Universal archive` to `### Universal archive`, its `###` subsections (`macOS`, `Linux`, `Windows`) to `####`, and `## OCI image` to `### OCI image`. Keep `## Create and distribute seats` and `## Before players join` at level 2. In the Universal archive paragraph, add after "They do not require sbt or Node.": "The same archives also contain the Start files, which use the installed Java 21."

- [ ] **Step 2: Add the configuration section**

Append to `docs/operations/configuration.md`:

```markdown
## Desktop launch profile

The Start files set `OATH_LAUNCH=desktop`. Without that variable, nothing in
this section applies. Any other value of `OATH_LAUNCH` is a configuration
error.

Under the desktop profile, values come from, in order of precedence:

1. Command-line options.
2. Environment variables.
3. The settings file.
4. Desktop defaults: listen on `0.0.0.0`, detect the local network address,
   store the database in the app-data folder, open the browser.
5. The defaults in the table above.

| Computer | App-data folder |
| --- | --- |
| macOS | `~/Library/Application Support/OathDigital` |
| Windows | `%LOCALAPPDATA%\OathDigital` |
| Linux and others | `$XDG_DATA_HOME/oathdigital`, else `~/.local/share/oathdigital` |

The database is `data/database` inside that folder. The settings file is
`oathdigital.properties` inside that folder. The first start writes it with
every setting commented out. Remove the leading `#` from a line to use it, and
restart the server. It accepts only `OATH_HOST`, `OATH_PORT`,
`OATH_PUBLIC_BASE_URL`, `OATH_DATABASE_PATH`, and `OATH_OPEN_BROWSER`
(`true` or `false`). On Windows, write paths with forward slashes, such as
`C:/OathData/database`.

The detected address is the private IPv4 address of the connection the
computer uses for its default route. If there is none, the server listens on
`127.0.0.1` only and says so. If the detected address is wrong, set
`OATH_PUBLIC_BASE_URL` in the settings file. The port stays at 8080 unless
you change it; if it is in use, the server stops with a message naming the
settings file.
```

- [ ] **Step 3: Update `releases.md`**

In "Versions and compatibility", after the paragraph about archive filenames, add:

```markdown
Bundled archives include a Java 21 runtime built with `jlink` from the
module list in `packaging/jlink-modules.txt`: `oathdigital-0.1.0-alpha.1-macos-arm64.tgz`,
`oathdigital-0.1.0-alpha.1-windows-x64.zip`, and
`oathdigital-0.1.0-alpha.1-linux-x64.tgz`. They extract to the same
`oathdigital-0.1.0-alpha.1` directory. They are not signed or notarized; the
[quick start](quick-start.md) describes the one-time macOS and Windows prompts.
Intel Macs and Linux arm64 use the all-platform archive or the OCI image.
```

In "Operator sequence" step 3, after "extracts and smokes both archives under Java 21,", insert "builds each bundled archive from the tested all-platform archive on macOS, Windows, and Linux runners and smokes it with no system Java,". In the paragraph starting "The GitHub prerelease contains", change "both Universal archives" to "both Universal archives, the three bundled archives". Add under "Local verification":

```markdown
To build and smoke the bundled archive for the host platform (`macos-arm64`
or `linux-x64`) from an all-platform archive:

    sh scripts/verify-alpha-release.sh bundled target/universal/oathdigital-0.1.0-alpha.1.tgz macos-arm64 /tmp/oath-bundled

The `windows-x64` archive is built and smoked only by the workflow. Its smoke
cannot stop the server gracefully, so the database-close check for Windows is
in the acceptance record.
```

- [ ] **Step 4: Update `packaged-smoke-test.md`**

Append:

```markdown
## Bundled-runtime smoke

`scripts/smoke-bundled-distribution.sh APP_DIRECTORY PORT` (macOS, Linux) and
`scripts/smoke-bundled-distribution.ps1 -AppDirectory DIR -Port PORT`
(Windows) start an extracted bundled archive with `OATH_LAUNCH=desktop`, no
`JAVA_HOME`, no Java on `PATH`, and a temporary app-data folder. They check
that the process runs from `jre/`, that the banner address answers
`/health/ready`, `/`, and `/assets/main.js`, and that the settings file and
database are in the app-data folder. The POSIX smoke also checks
`Oath Digital database closed` after SIGTERM.
```

- [ ] **Step 5: Add acceptance rows to `alpha-acceptance.md`**

Add a new section after the existing HTTPS rows, continuing the row numbering after the last existing row (read the file first; the rows currently end at 16):

```markdown
## Bundled archive checks

Run once per bundled archive (macOS arm64, Windows x64, Linux x64) that the
build ships. Use a machine without Java 21 on `PATH` where possible.

| # | Check | Result | Notes |
| --- | --- | --- | --- |
| 17 | Download the archive with a browser and extract it with the OS's own tool. | UNEXECUTED | |
| 18 | Double-click Start. Record the exact Gatekeeper or SmartScreen steps needed. | UNEXECUTED | |
| 19 | Record the firewall prompt and the choice made (private networks only). | UNEXECUTED | |
| 20 | The window shows the banner; the browser opens the game-creation page at the banner address. | UNEXECUTED | |
| 21 | Create a game on the host; a second machine joins using a seat link with the banner address and completes a turn. | UNEXECUTED | |
| 22 | Close the Start window (Windows: close the console window). The next start restores the game, and the log from the first run ends with `Oath Digital database closed`. | UNEXECUTED | |
```

- [ ] **Step 6: Update the roadmap**

In `docs/ROADMAP.md` Phase 5 item 2, after "publish a short host/player quick-start.", add: "Bundled-runtime archives for macOS arm64, Windows x64, and Linux x64 with a double-click Start are implemented; their workflow run and acceptance rows 17-22 remain unexecuted."

- [ ] **Step 7: Run the documentation checks**

Run: `python3 scripts/check-markdown-links.py && ./sbtw verifyPackageMappings`
Expected: `Markdown link check passed: <n> files`; `verifyPackageMappings` passes (the docs are bundled into `share/oathdigital`).

- [ ] **Step 8: Commit**

```bash
git add docs/operations docs/ROADMAP.md
git commit -m "docs: document bundled archives and the desktop launch profile"
```

---

### Task 12: Full gate and host acceptance checkpoint

**Files:**
- Create: `docs/testing/bundled-launch-macos-arm64-<date>.md` (only if the checkpoint runs)

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: Run the full local gate under Java 21**

```bash
export JAVA_HOME=/Users/roman/projects/oathdigital/.tooling/jdk-21.0.12.1+1/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
python3 scripts/check-architecture.py
python3 scripts/validate-component-catalog.py
python3 scripts/check-markdown-links.py
./sbtw verifyReleaseVersion test frontend/test verifyPackageMappings Universal/packageBin Universal/packageZipTarball
scratch=$(mktemp -d)
tar -xzf target/universal/oathdigital-0.1.0-SNAPSHOT.tgz -C "$scratch"
sh scripts/smoke-packaged-distribution.sh "$scratch/oathdigital-0.1.0-SNAPSHOT" 18080
sh scripts/verify-alpha-release.sh bundled target/universal/oathdigital-0.1.0-SNAPSHOT.tgz macos-arm64 "$scratch/out"
```

Expected: every command passes. Record JVM and frontend test counts.

- [ ] **Step 2: Host checkpoint with the user (macOS quarantine risk)**

Stop and ask the user to do this on their Mac; the agent cannot click through Gatekeeper:

1. The agent copies `$scratch/out/oathdigital-0.1.0-SNAPSHOT-macos-arm64.tgz` to a folder the user names, and marks it as downloaded, so macOS treats it like a browser download:
   `xattr -w com.apple.quarantine "0083;$(printf '%x' "$(date +%s)");Safari;" <archive>`
2. The user double-clicks the archive in Finder, then double-clicks **Start Oath Digital.command**, and follows any prompt.
3. The user reports: each prompt's wording, whether **Open Anyway** was needed, whether the bundled `java` then ran or was blocked, the firewall prompt, whether the browser opened the banner address.

If the bundled `java` is blocked after the Start file was allowed, add ad-hoc signing to `scripts/package-bundled-runtime.sh` in the `Darwin` branch before `codesign --verify`:

```sh
  find "$work/$root/jre" -type f \( -perm -u+x -o -name '*.dylib' \) -exec codesign --force --sign - {} +
```

rerun Task 9 Step 5 and this checkpoint, and commit with `fix: ad-hoc sign the bundled macOS runtime`.

- [ ] **Step 3: Record the checkpoint**

Write `docs/testing/bundled-launch-macos-arm64-<date>.md` with the commit, archive SHA-256, the macOS version, the exact prompts, and the result. Do not include seat links, cookies, or raw logs. Commit:

```bash
git add docs/testing
git commit -m "docs: record the macOS bundled-launch checkpoint"
```

Windows and Linux rows 17-22 remain UNEXECUTED until the workflow has produced those archives and a host has run them.
