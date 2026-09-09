package oathdigital.frontend

import org.scalajs.dom

object Main {
  def main(args: Array[String]): Unit = {
    val mount = Option(dom.document.getElementById("app")).getOrElse {
      throw new IllegalStateException("missing #app mount point")
    }
    start(mount, dom.window.location.pathname,
      mount.getAttribute("data-runtime-mode") == "trusted-alpha",
      new SameOriginJsonTransport)
  }

  private[frontend] def start(mount: dom.Element, pathname: String,
      trustedAlpha: Boolean, transport: JsonTransport): Unit =
    ServerUiSupport.canonicalGameId(pathname) match {
      case Some(gameId) =>
        ServerModeUi.start(mount, new TrustedHttpGameClient(transport), Some(gameId))
      case None if trustedAlpha && pathname == "/" => TrustedHostUi.start(mount, transport)
      case None if trustedAlpha => mount.textContent = "Open your assigned seat link."
      case None => ServerModeUi.start(mount, new HttpGameClient(transport))
    }
}
