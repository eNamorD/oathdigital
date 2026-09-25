package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, _}
import ServerUiSupport._

import org.scalajs.dom
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object ServerModeUi:
  def start(mount: dom.Element,
      client: GameClient = new HttpGameClient(new SameOriginJsonTransport),
      trustedGameId: Option[String] = None,
      startOver: () => Unit = () => dom.window.location.assign(Main.DevelopmentStartUrl)): Unit =
    val fixedSeat = trustedGameId.nonEmpty
    var projection = Option.empty[GameProjection]
    var failure = Option.empty[GameClientFailure]
    var selectedPlayer = if fixedSeat then "" else queryParameter("playerId").getOrElse("red-exile")
    var gameId = trustedGameId.getOrElse(queryParameter("gameId").getOrElse(freshGameId()))
    val coordinator = new ServerSessionCoordinator(gameId, selectedPlayer)
    var polling = Option.empty[SnapshotPollingCoordinator]
    var drafts = SessionDrafts.empty
    var rawEvents = Vector.empty[RawEvent]
    var rawHistorySequence = Option.empty[Long]
    val shell = new GameTableShell(mount, !fixedSeat)

    def updateSessionUrl(): Unit = if !fixedSeat then updateUrl(gameId, selectedPlayer)

    def recovery(error: GameClientFailure): Boolean = error match
      case GameClientFailure.HttpFailure(401 | 403, _, _) if fixedSeat => true
      case _ => false

    def invalidTrustedViewer(value: GameProjection): Boolean =
      fixedSeat && !value.viewerPlayerId.exists(player =>
        value.players.exists(_.playerId == player) &&
          (selectedPlayer.isEmpty || selectedPlayer == player))

    def render(): Unit =
      val actionContent = element("div", "action-content")
      coordinator.connectionState match
        case ServerConnectionState.Disconnected(_) =>
          actionContent.appendChild(text(
            "div",
            "status error disconnected",
            "Disconnected. Reconnect to fetch the authoritative current " +
              "state before issuing another command."
          ))
          val retry = button("Reconnect", "reconnectSession")
          retry.onclick = _ => reconnect()
          actionContent.appendChild(retry)
        case ServerConnectionState.Connecting if projection.nonEmpty =>
          actionContent.appendChild(text(
            "div",
            "status",
            "Reconnecting to server…"
          ))
        case _ => ()
      failure.foreach { error =>
        val notice = text("div", "status error", if recovery(error) then
          "Open your assigned seat link to restore access to this game." else error.message)
        notice.setAttribute("role", "alert")
        actionContent.appendChild(notice)
      }
      if fixedSeat && selectedPlayer.nonEmpty then
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
            routed, controlsAvailable, drafts, ui)
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
            }, controlsAvailable, drafts, ui), decisionKey)
      val development = element("div", "development-content")
      if !fixedSeat then
        development.appendChild(DevelopmentRenderer.controls(session))
        if projection.nonEmpty then
          development.appendChild(DevelopmentRenderer.rawEventLog(rawEvents))
      val attention = s"$decision|${coordinator.connectionState}|${failure.map(_.message)}"
      shell.update(gameId, attention, players, world, actionContent, development)

    def store(
        request: ServerRequestIdentity,
        value: GameProjection,
        notice: Option[GameClientFailure]
    ): Unit =
      val routed = if !fixedSeat then coordinator.route(request, value, notice)
      else value.viewerPlayerId match
        case Some(player) if !invalidTrustedViewer(value) =>
          if selectedPlayer.isEmpty then
            selectedPlayer = player
            coordinator.switchSession(gameId, selectedPlayer)
          coordinator.recordSnapshotSuccess(coordinator.capture)
          Some(ProjectionRoute.Display(value, notice))
        case _ =>
          polling.foreach(_.stop())
          coordinator.switchSession(gameId, selectedPlayer)
          projection = None
          failure = Some(GameClientFailure.HttpFailure(403, "seat-changed",
            "Open your assigned seat link."))
          render()
          None
      routed.foreach:
        case ProjectionRoute.Display(displayed, retainedNotice) =>
          drafts = drafts.reconcile(
            BoardSelectionContext(gameId, selectedPlayer, displayed.nextSequence),
            displayed)
          projection = Some(displayed)
          failure = retainedNotice
          render()
          polling.foreach(_.resume(coordinator.capture))
          if !fixedSeat && !rawHistorySequence.contains(displayed.nextSequence) then
            rawHistorySequence = Some(displayed.nextSequence)
            client match
              case development: HttpGameClient => development.loadRawEventHistory(gameId).foreach:
                case Right(events) => rawEvents = events; render()
                case Left(_) => ()
              case _ => ()
        case ProjectionRoute.ReloadForActivePlayer(
              displayed,
              nextRequest,
              retainedNotice
            ) =>
          drafts = SessionDrafts.empty
          polling.foreach(_.stop())
          projection = Some(displayed)
          failure = retainedNotice
          selectedPlayer = nextRequest.playerId
          updateSessionUrl()
          render()
          client.load(gameId, selectedPlayer).foreach { result =>
            accept(nextRequest, result, retainedNotice)
          }

    def accept(
        request: ServerRequestIdentity,
        result: Either[GameClientFailure, GameProjection],
        notice: Option[GameClientFailure] = None
    ): Unit =
      if coordinator.accepts(request) then result match
        case Right(value) => store(request, value, notice)
        case Left(error) =>
          coordinator.recordFailure(request, error)
          if GameClientFailure.isTransient(error) || recovery(error) then
            polling.foreach(_.stop())
          if recovery(error) then
            coordinator.switchSession(gameId, selectedPlayer)
            projection = None
          failure = Some(error)
          render()

    def loadExisting(id: String, playerId: String): Unit =
      if fixedSeat then return
      polling.foreach(_.stop())
      gameId = id.trim
      projection = None
      drafts = SessionDrafts.empty
      rawEvents = Vector.empty
      rawHistorySequence = None
      failure = None
      selectedPlayer = playerId.trim match
        case "" => "red-exile"
        case value => value
      val request = coordinator.switchSession(gameId, selectedPlayer)
      updateSessionUrl()
      render()
      client.load(gameId, selectedPlayer).foreach(accept(request, _))

    def newGame(): Unit =
      if fixedSeat then return
      polling.foreach(_.stop())
      dom.window.history.replaceState(null, "", Main.DevelopmentStartUrl)
      startOver()

    def reconnect(): Unit =
      polling.foreach(_.stop())
      failure = None
      val request = coordinator.reconnect()
      updateSessionUrl()
      render()
      client.load(gameId, selectedPlayer).foreach(accept(request, _))

    def poll(request: ServerRequestIdentity): Unit =
      client.load(request.gameId, request.playerId).foreach:
        case Right(snapshot) if invalidTrustedViewer(snapshot) =>
          val accepted = polling.exists(_.complete(request, continuePolling = false))
          if accepted then accept(request, Right(snapshot))
        case Right(snapshot) =>
          val advances = projection.forall(current =>
            coordinator.snapshotAdvances(
              request,
              current.nextSequence,
              snapshot.nextSequence
            )
          )
          if advances then
            val accepted = polling.exists(
              _.complete(request, continuePolling = false)
            )
            if accepted then accept(request, Right(snapshot))
          else
            val accepted = polling.exists(
              _.complete(request, continuePolling = true)
            )
            if accepted then coordinator.recordSnapshotSuccess(request)
        case Left(error) =>
          val transient = GameClientFailure.isTransient(error)
          val accepted = polling.exists(
            _.complete(request, continuePolling = !transient)
          )
          if accepted then accept(request, Left(error))

    def submitTransport(command: GameCommand,
        modifiers: Vector[ModifierInvocation]): Unit =
      projection.foreach { current =>
        val request = coordinator.capture
        client
          .submit(gameId, selectedPlayer, current.nextSequence, command, modifiers)
          .foreach:
            case Left(stale: GameClientFailure.StalePosition)
                if coordinator.accepts(request) =>
              drafts = SessionDrafts.empty
              failure = Some(stale)
              client.load(gameId, selectedPlayer).foreach:
                refreshed => accept(request, refreshed, Some(stale))
            case other => accept(request, other)
      }

    lazy val session: SessionControls = new SessionControls:
      def currentGameId = gameId
      def currentPlayerId = selectedPlayer
      def displayedProjection = projection
      def connectionState = coordinator.connectionState
      def loadSession(id: String, playerId: String) = loadExisting(id, playerId)
      def reconnectSession() = reconnect()
      def createGame() = newGame()

    lazy val host: FlowHost = new FlowHost:
      def displayedProjection = projection
      def currentGameId = gameId
      def currentPlayerId = selectedPlayer
      def currentDrafts = drafts
      def replaceDrafts(value: SessionDrafts) = drafts = value
      def redraw() = render()
      def fail(error: GameClientFailure) = { failure = Some(error); render() }
      def preview(request: MajorActionPreviewRequest) =
        client.preview(gameId, selectedPlayer, request)
      def send(command: GameCommand, modifiers: Vector[ModifierInvocation]) =
        submitTransport(command, modifiers)

    lazy val ui: ActionControls = new ModifierFlow(host)

    polling = Some(new SnapshotPollingCoordinator(
      new BrowserPollClock,
      poll
    ))
    dom.document.addEventListener(
      "visibilitychange",
      (_: dom.Event) =>
        polling.foreach(_.visibilityChanged(dom.document.hidden))
    )
    polling.foreach(_.visibilityChanged(dom.document.hidden))

    render()
    if fixedSeat then client.load(gameId, selectedPlayer).foreach(accept(coordinator.capture, _))
    else queryParameter("gameId") match
      case Some(existing) => loadExisting(existing, selectedPlayer)
      case None => newGame()

    def controlsAvailable: Boolean =
      coordinator.connectionState == ServerConnectionState.Connected
