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
