package oathdigital.frontend

import org.scalajs.dom

object Main {
  /** The development start page; `ServerModeUi` returns here to create another game. */
  private[frontend] val DevelopmentStartUrl = "/?mode=server"

  def main(args: Array[String]): Unit = {
    val mount = Option(dom.document.getElementById("app")).getOrElse {
      throw new IllegalStateException("missing #app mount point")
    }
    start(mount, dom.window.location.pathname,
      mount.getAttribute("data-runtime-mode") == "trusted-alpha",
      new SameOriginJsonTransport)
  }

  private[frontend] def start(mount: dom.Element, pathname: String,
      trustedAlpha: Boolean, transport: JsonTransport,
      navigate: String => Unit = url => dom.window.location.assign(url)): Unit =
    ServerUiSupport.canonicalGameId(pathname) match {
      case Some(gameId) =>
        ServerModeUi.start(mount, new TrustedHttpGameClient(transport), Some(gameId))
      case None if trustedAlpha && pathname == "/" => TrustedHostUi.start(mount, transport)
      case None if trustedAlpha => mount.textContent = "Open your assigned seat link."
      case None if ServerUiSupport.queryParameter("gameId").isEmpty =>
        // Development games are created exactly like trusted ones, then opened
        // in the development table as the first listed player.
        TrustedHostUi.start(mount, transport, Some(created =>
          navigate(ServerUiSupport.sessionUrl(created.gameId, created.seats.head.playerId))))
      case None => ServerModeUi.start(mount, new HttpGameClient(transport),
        startOver = () => navigate(DevelopmentStartUrl))
    }
}
