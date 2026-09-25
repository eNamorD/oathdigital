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

    def render(): Unit =
      val view: TableView = session
      val projection = view.viewedProjection
      val failure = view.shownFailure
      val selectedPlayer = view.viewedPlayerId
      val drafts = view.viewedDrafts
      val connection = view.viewedConnection
      val actionContent = element("div", "action-content")
      connection match
        case ServerConnectionState.Disconnected(_) =>
          actionContent.appendChild(text(
            "div",
            "status error disconnected",
            "Disconnected. Reconnect to fetch the authoritative current " +
              "state before issuing another command."
          ))
          val retry = button("Reconnect", "reconnectSession")
          retry.onclick = _ => session.reconnectSession()
          actionContent.appendChild(retry)
        case ServerConnectionState.Connecting if projection.nonEmpty =>
          actionContent.appendChild(text(
            "div",
            "status",
            "Reconnecting to server…"
          ))
        case _ => ()
      failure.foreach { error =>
        val notice = text("div", "status error",
          if TableSession.needsSeatLink(view.trusted, error) then
            "Open your assigned seat link to restore access to this game."
          else error.message)
        notice.setAttribute("role", "alert")
        actionContent.appendChild(notice)
      }
      if view.trusted && selectedPlayer.nonEmpty then
        actionContent.appendChild(text("p", "seat-identity", "Your seat: " +
          projection.fold(selectedPlayer)(playerDisplayName(_, selectedPlayer))))
      val (players, world, decision) = projection match
        case None if failure.isEmpty =>
          actionContent.appendChild(text("div", "status", "Loading game…"))
          (text("p", "empty-state", "Loading players…"),
            text("p", "empty-state", "Loading world…"), "loading")
        case None =>
          (text("p", "empty-state", "Players unavailable."),
            text("p", "empty-state", "World unavailable."), "unavailable")
        case Some(value) =>
          val presentation = viewerPresentation(value, selectedPlayer)
          // Routed once per render: both panes read the same answer.
          val routed = ParkedDecision.route(value, presentation)
          val pane = ActionDecisionRenderer.actionsPanel(value, presentation,
            routed, view.controlsAvailable, drafts, ui)
          actionContent.appendChild(pane.element)
          val decisionKey = Vector(selectedPlayer, value.phase,
            value.activeParticipantId.getOrElse(""),
            value.pendingCardDecision.map(_.decisionId).getOrElse(""),
            value.walkerDecision.map(_.decisionId).getOrElse(""),
            drafts.boardTargets.flatMap(_.activeActionKind).getOrElse(""),
            drafts.modifiers.map(_.stage.toString).getOrElse(""),
            pane.prompt).mkString("|")
          (WorldBoardRenderer.players(value, selectedPlayer),
            WorldBoardRenderer.world(value, routed.surface.collect {
              case board: ParkedDecision.Surface.Board => board
            }, view.controlsAvailable, drafts, ui), decisionKey)
      val development = element("div", "development-content")
      if !view.trusted then
        development.appendChild(DevelopmentRenderer.controls(session))
        if projection.nonEmpty then
          development.appendChild(DevelopmentRenderer.rawEventLog(view.viewedRawEvents))
      val attention = s"$decision|$connection|${failure.map(_.message)}"
      shell.update(view.viewedGameId, attention, players, world, actionContent, development)

    lazy val session: TableSession = new TableSession(client, trusted,
      initialGame, initialPlayer, new BrowserPollClock,
      new BrowserNavigation(startOver), () => render())
    lazy val ui: ActionControls = new ModifierFlow(session)

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
