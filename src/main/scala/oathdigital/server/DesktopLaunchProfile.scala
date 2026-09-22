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
