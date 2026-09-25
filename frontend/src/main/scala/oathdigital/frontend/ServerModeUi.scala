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
        modifiers: Vector[ModifierInvocation] = Vector.empty): Unit =
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

    def submit(command: GameCommand): Unit = ModifierWorkflow.action(command) match
      case None => submitTransport(command)
      case Some((action, parameters)) => projection.foreach { current =>
        val request = MajorActionPreviewRequest(current.nextSequence, action, parameters)
        client.preview(gameId, selectedPlayer, request).foreach:
          case Right(response) if response.modifiers.isEmpty =>
            submitTransport(command)
          case Right(response) =>
            drafts = drafts.copy(modifiers = Some(ModifierWorkflow.fromPreview(
              Some(command), None, parameters, response,
              drafts.modifiers.map(_.selection),
              ModifierSelectionContext(gameId, selectedPlayer,
                current.nextSequence, action))))
            render()
          case Left(error) => failure = Some(error); render()
      }

    def activatePreviewTargets(workflow: ModifierWorkflow,
        response: MajorActionPreviewResponse): Unit = for
      current <- projection
      context <- drafts.context
      actionKind <- workflow.actionKind
    do
      val entered = if actionKind == "play-facedown-adviser" then
        drafts.copy(facedownAdviser = current.minorActions.flatMap(
          FacedownAdviserDraft.initial(context, _)), boardTargets = None)
      else ModifierWorkflow.targetAction(actionKind, response,
        current.boardTargetActions).fold(drafts)(action =>
        drafts.copy(boardTargets = Some(BoardTargetSelectionState.reconcile(None,
          context, Vector(action)).activate(actionKind))))
      drafts = entered.copy(modifiers = Some(workflow.showTargets(response)))
      render()

    def startTargetedFlow(actionKind: String): Unit = for
      current <- projection
      (action, parameters) <- ModifierWorkflow.targeted(actionKind)
    do
      drafts = drafts.leave(FlowExit.Restarted)
      val request = MajorActionPreviewRequest(current.nextSequence, action, parameters)
      client.preview(gameId, selectedPlayer, request).foreach:
        case Right(response) =>
          val workflow = ModifierWorkflow.fromPreview(None, Some(actionKind),
            parameters, response, drafts.modifiers.map(_.selection),
            ModifierSelectionContext(gameId, selectedPlayer,
              current.nextSequence, action))
          if workflow.ordering then { drafts = drafts.copy(modifiers = Some(workflow)); render() }
          else activatePreviewTargets(workflow, response)
        case Left(error) => failure = Some(error); render()

    def confirmModifierSelection(): Unit = drafts.modifiers.foreach { workflow =>
      val request = MajorActionPreviewRequest(workflow.selection.context.sequence,
        workflow.selection.context.action, workflow.baseParameters,
        workflow.selection.invocations)
      client.preview(gameId, selectedPlayer, request).foreach:
        case Right(response) => workflow.command match
          case Some(command) =>
            drafts = drafts.leave(FlowExit.OrderingLeft)
            val (submitted, modifiers) = ModifierWorkflow.submission(command,
              workflow.selection.invocations)
            submitTransport(submitted, modifiers)
          case None => activatePreviewTargets(workflow, response)
        case Left(error) =>
          drafts = drafts.leave(FlowExit.Failed)
          failure = Some(error)
          render()
    }

    def completeTargetCommand(command: GameCommand): Unit =
      drafts.modifiers.filter(_.stage == ModifierWorkflowStage.Targets) match
        case Some(workflow) =>
          drafts = drafts.leave(FlowExit.Completed)
          val (submitted, modifiers) = ModifierWorkflow.submission(command,
            workflow.selection.invocations)
          submitTransport(submitted, modifiers)
        case None => submit(command)

    def restoredTargets: Option[BoardTargetSelectionState] = for
      current <- projection
      context <- drafts.context
    yield BoardTargetSelectionState.restore(context, current.boardTargetActions)

    def handleBoardSelection(result: BoardSelectionResult): Unit = result match
      case BoardSelectionResult.Updated(state) => ui.stage(Draft.BoardTargets(state))
      case BoardSelectionResult.Submit(action, targets) =>
        commandForSelection(action, targets, selectedPlayer).foreach { command =>
          completeTargetCommand(command)
        }

    lazy val session: SessionControls = new SessionControls:
      def currentGameId = gameId
      def currentPlayerId = selectedPlayer
      def displayedProjection = projection
      def connectionState = coordinator.connectionState
      def loadSession(id: String, playerId: String) = loadExisting(id, playerId)
      def reconnectSession() = reconnect()
      def createGame() = newGame()

    lazy val ui: ActionControls = new ActionControls:
      def stage(draft: Draft) =
        drafts = drafts.staged(draft)
        render()
      def chooseFacedownAdviser(cardId: String) =
        drafts = drafts.copy(facedownAdviser =
          drafts.facedownAdviser.map(_.choose(cardId)))
        render()
      def toggleModifier(value: PreviewModifier) =
        drafts = drafts.copy(modifiers = drafts.modifiers.map(workflow =>
          workflow.copy(selection = workflow.selection.toggle(value))))
        render()
      def moveModifier(value: PreviewModifier, delta: Int) =
        drafts = drafts.copy(modifiers = drafts.modifiers.map(workflow =>
          workflow.copy(selection =
            if delta < 0 then workflow.selection.moveEarlier(value)
            else workflow.selection.moveLater(value))))
        render()
      def confirmModifiers() = confirmModifierSelection()
      def backFromModifiers() =
        drafts = drafts.leave(FlowExit.OrderingLeft)
        render()
      def cancelModifiers() =
        drafts = drafts.leave(FlowExit.Cancelled(restoredTargets))
        render()
      def beginTargetedMajorAction(actionKind: String) =
        startTargetedFlow(actionKind)
      // Guarded as before: nothing happens when no flow is in flight.
      def backFromTargets() = drafts.modifiers.foreach { _ =>
        drafts = drafts.leave(FlowExit.TargetsLeft)
        render()
      }
      def cancelTargetAction() =
        drafts = drafts.leave(FlowExit.Cancelled(restoredTargets))
        render()
      def submitTargetCommand(command: GameCommand) =
        completeTargetCommand(command)
      def submitCommand(command: GameCommand) = submit(command)
      def handleSelection(result: BoardSelectionResult) = handleBoardSelection(result)

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
