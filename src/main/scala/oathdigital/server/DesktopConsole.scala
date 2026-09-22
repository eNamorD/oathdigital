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
