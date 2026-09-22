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
