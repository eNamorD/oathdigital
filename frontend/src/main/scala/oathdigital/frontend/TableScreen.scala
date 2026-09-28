package oathdigital.frontend

import ServerUiSupport._

/** What the table screen reads: the displayed position, the failure to
  * show, the draft set, and who is looking. Read-only. The names repeat
  * neither the session's private state nor `SessionControls`' or
  * `FlowHost`'s members, so one class implements all three traits with no
  * name answering two questions.
  */
private[frontend] trait TableView:
  def trusted: Boolean
  def viewedGameId: String
  def viewedPlayerId: String
  def viewedProjection: Option[GameProjection]
  def shownFailure: Option[GameClientFailure]
  def viewedDrafts: SessionDrafts
  def viewedConnection: ServerConnectionState
  def controlsAvailable: Boolean
  def viewedRawEvents: Vector[RawEvent]
  /** The game log as fetched for this seat, oldest first. */
  def viewedLog: Vector[oathdigital.protocol.projection.LogEntryWire]
  def clientOutOfDate: Boolean

/** The table: the action pane, the players, the world and the development
  * pane, drawn from the session's view. Every `render` replaces them all;
  * `GameTableShell` keeps focus and scroll across the replacement.
  */
private[frontend] final class TableScreen(
    shell: GameTableShell,
    view: TableView,
    controls: ActionControls,
    session: SessionControls):
  def render(): Unit =
    val projection = view.viewedProjection
    val failure = view.shownFailure
    val selectedPlayer = view.viewedPlayerId
    val drafts = view.viewedDrafts
    val connection = view.viewedConnection
    val actionContent = element("div", "action-content")
    val disconnected = connection match
      case ServerConnectionState.Disconnected(_) =>
        val notice = text("div", "status error disconnected",
          "Connection lost. Reconnect to see the current table before acting.")
        notice.setAttribute("role", "alert")
        actionContent.appendChild(notice)
        val retry = button("Reconnect", "reconnectSession")
        retry.onclick = _ => session.reconnectSession()
        actionContent.appendChild(retry)
        true
      case ServerConnectionState.Connecting if projection.nonEmpty =>
        actionContent.appendChild(text(
          "div",
          "status",
          "Reconnecting to server…"
        ))
        false
      case _ => false
    // The failure that cut the connection is the notice above: it names the
    // recovery, where the transport error names a URL. Any other failure
    // still shows, since the notice says nothing about it.
    failure.filterNot(error =>
      disconnected && GameClientFailure.isTransient(error)).foreach { error =>
      val notice = text("div", "status error",
        if view.clientOutOfDate then
          "This table is running a newer version of the game than this " +
            "page. Reload to continue."
        else if TableSession.needsSeatLink(view.trusted, error) then
          "Open your assigned seat link to restore access to this game."
        else error.message)
      notice.setAttribute("role", "alert")
      actionContent.appendChild(notice)
      if view.clientOutOfDate then
        val reload = button("Reload", "reloadClient")
        reload.onclick = _ => session.reloadClient()
        actionContent.appendChild(reload)
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
          routed, view.controlsAvailable, drafts, controls)
        actionContent.appendChild(pane.element)
        val decisionKey = Vector(selectedPlayer, value.phase,
          value.activeParticipantId.getOrElse(""),
          value.walkerDecision.map(_.decisionId).getOrElse(""),
          drafts.boardTargets.flatMap(_.activeActionKind).getOrElse(""),
          drafts.modifiers.map(_.stage.toString).getOrElse(""),
          pane.prompt).mkString("|")
        (WorldBoardRenderer.players(value, selectedPlayer),
          WorldBoardRenderer.world(value, routed.surface.collect {
            case board: ParkedDecision.Surface.Board => board
          }, view.controlsAvailable, drafts, controls), decisionKey)
    val development = element("div", "development-content")
    if !view.trusted then
      development.appendChild(DevelopmentRenderer.controls(session))
      if projection.nonEmpty then
        development.appendChild(DevelopmentRenderer.rawEventLog(view.viewedRawEvents))
    val attention = s"$decision|$connection|${failure.map(_.message)}"
    shell.update(view.viewedGameId, attention, players, world, actionContent, development)
    shell.showLog(view.viewedGameId, view.viewedPlayerId,
      view.viewedLog, projection.fold(Map.empty[String, String])(value =>
        value.players.map(player =>
          player.playerId -> PlayerColorCss.of(player.color)).toMap))
