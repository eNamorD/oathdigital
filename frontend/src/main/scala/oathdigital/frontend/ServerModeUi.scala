package oathdigital.frontend

import ServerUiSupport._

import org.scalajs.dom

object ServerModeUi:
  def start(mount: dom.Element,
      client: GameClient = new HttpGameClient(new SameOriginJsonTransport),
      trustedGameId: Option[String] = None,
      startOver: () => Unit = () => dom.window.location.assign(Main.DevelopmentStartUrl)): Unit =
    val trusted = trustedGameId.nonEmpty
    val initialPlayer = if trusted then "" else queryParameter("playerId").getOrElse("red-exile")
    val initialGame = trustedGameId.getOrElse(queryParameter("gameId").getOrElse(freshGameId()))
    val shell = new GameTableShell(mount, !trusted)

    lazy val session: TableSession = new TableSession(client, trusted,
      initialGame, initialPlayer, new BrowserPollClock,
      new BrowserNavigation(startOver), () => screen.render())
    lazy val flow: ActionControls = new ModifierFlow(session)
    lazy val screen: TableScreen = new TableScreen(shell, session, flow, session)

    dom.document.addEventListener(
      "visibilitychange",
      (_: dom.Event) => session.visibilityChanged(dom.document.hidden)
    )
    session.visibilityChanged(dom.document.hidden)
    session.open(queryParameter("gameId"))

/** The browser's address bar and start page, for the table session. */
private[frontend] final class BrowserNavigation(leave: () => Unit) extends Navigation:
  def showSession(gameId: String, playerId: String): Unit = updateUrl(gameId, playerId)
  def startOver(): Unit =
    dom.window.history.replaceState(null, "", Main.DevelopmentStartUrl)
    leave()
