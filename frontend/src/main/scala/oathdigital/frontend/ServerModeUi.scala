package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, _}
import ServerUiSupport._

import org.scalajs.dom
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object ServerModeUi {
  def start(mount: dom.Element,
      client: GameClient = new HttpGameClient(new SameOriginJsonTransport),
      trustedGameId: Option[String] = None,
      startOver: () => Unit = () => dom.window.location.assign(Main.DevelopmentStartUrl)): Unit = {
    val fixedSeat = trustedGameId.nonEmpty
    var projection = Option.empty[GameProjection]
    var failure = Option.empty[GameClientFailure]
    var selectedPlayer = if (fixedSeat) "" else queryParameter("playerId").getOrElse("red-exile")
    var gameId = trustedGameId.getOrElse(queryParameter("gameId").getOrElse(freshGameId()))
    val coordinator = new ServerSessionCoordinator(gameId, selectedPlayer)
    var polling = Option.empty[SnapshotPollingCoordinator]
    var boardSelectionState = Option.empty[BoardTargetSelectionState]
    var walkerPartitionDraft = Option.empty[WalkerPartitionDraft]
    var walkerDistributeDraft = Option.empty[WalkerDistributeDraft]
    var walkerSelectionDraft = Option.empty[WalkerSelectionDraft]
    var modifierWorkflow = Option.empty[ModifierWorkflow]
    var facedownAdviserDraft = Option.empty[FacedownAdviserDraft]
    var rawEvents = Vector.empty[RawEvent]
    var rawHistorySequence = Option.empty[Long]
    val shell = new GameTableShell(mount, !fixedSeat)

    def updateSessionUrl(): Unit = if (!fixedSeat) updateUrl(gameId, selectedPlayer)

    def recovery(error: GameClientFailure): Boolean = error match {
      case GameClientFailure.HttpFailure(401 | 403, _, _) if fixedSeat => true
      case _ => false
    }

    def invalidTrustedViewer(value: GameProjection): Boolean =
      fixedSeat && !value.viewerPlayerId.exists(player =>
        value.players.exists(_.playerId == player) &&
          (selectedPlayer.isEmpty || selectedPlayer == player))

    def render(): Unit = {
      val actionContent = element("div", "action-content")
      coordinator.connectionState match {
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
      }
      failure.foreach { error =>
        val notice = text("div", "status error", if (recovery(error))
          "Open your assigned seat link to restore access to this game." else error.message)
        notice.setAttribute("role", "alert")
        actionContent.appendChild(notice)
      }
      if (fixedSeat && selectedPlayer.nonEmpty)
        actionContent.appendChild(text("p", "seat-identity", "Your seat: " +
          projection.fold(selectedPlayer)(playerDisplayName(_, selectedPlayer))))
      val (players, world, decision) = projection match {
        case None if failure.isEmpty =>
          actionContent.appendChild(text("div", "status", "Loading game…"))
          (text("p", "empty-state", "Loading players…"),
            text("p", "empty-state", "Loading world…"), "loading")
        case None =>
          (text("p", "empty-state", "Players unavailable."),
            text("p", "empty-state", "World unavailable."), "unavailable")
        case Some(value) =>
          val presentation = viewerPresentation(value, selectedPlayer)
          actionContent.appendChild(ActionDecisionRenderer.actionsPanel(value, presentation, ui))
          val prompt = Option(actionContent.querySelector(
            "#card-decision-title,.selection-instruction,.modifier-confirm,.resolution-choice"))
            .map(_.textContent).getOrElse("")
          val decisionKey = Vector(selectedPlayer, value.phase,
            value.activeParticipantId.getOrElse(""),
            value.pendingCardDecision.map(_.decisionId).getOrElse(""),
            value.walkerDecision.map(_.decisionId).getOrElse(""),
            boardSelectionState.flatMap(_.activeActionKind).getOrElse(""),
            modifierWorkflow.map(_.stage.toString).getOrElse(""), prompt).mkString("|")
          (WorldBoardRenderer.players(value, ui),
            WorldBoardRenderer.world(value, presentation, ui), decisionKey)
      }
      val development = element("div", "development-content")
      if (!fixedSeat) {
        development.appendChild(DevelopmentRenderer.controls(ui))
        if (projection.nonEmpty) development.appendChild(DevelopmentRenderer.rawEventLog(rawEvents))
      }
      val attention = s"$decision|${coordinator.connectionState}|${failure.map(_.message)}"
      shell.update(gameId, attention, players, world, actionContent, development)
    }

    def store(
        request: ServerRequestIdentity,
        value: GameProjection,
        notice: Option[GameClientFailure]
    ): Unit = {
      val routed = if (!fixedSeat) coordinator.route(request, value, notice)
      else value.viewerPlayerId match {
        case Some(player) if !invalidTrustedViewer(value) =>
          if (selectedPlayer.isEmpty) {
            selectedPlayer = player
            coordinator.switchSession(gameId, selectedPlayer)
          }
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
      }
      routed.foreach {
        case ProjectionRoute.Display(displayed, retainedNotice) =>
          modifierWorkflow = ModifierWorkflow.reconcile(modifierWorkflow,
            gameId, selectedPlayer, displayed.nextSequence)
          facedownAdviserDraft = FacedownAdviserDraft.reconcile(facedownAdviserDraft,
            BoardSelectionContext(gameId, selectedPlayer, displayed.nextSequence),
            displayed.minorActions)
          boardSelectionState = Some(BoardTargetSelectionState.reconcile(
            boardSelectionState,
            BoardSelectionContext(gameId, selectedPlayer, displayed.nextSequence),
            displayed.boardTargetActions))
          walkerPartitionDraft = WalkerPartitionDraft.reconcile(
            walkerPartitionDraft,
            BoardSelectionContext(gameId, selectedPlayer,
              displayed.nextSequence), displayed.walkerDecision)
          walkerDistributeDraft = WalkerDistributeDraft.reconcile(
            walkerDistributeDraft,
            BoardSelectionContext(gameId, selectedPlayer,
              displayed.nextSequence), displayed.walkerDecision)
          walkerSelectionDraft = WalkerSelectionDraft.reconcile(
            walkerSelectionDraft,
            BoardSelectionContext(gameId, selectedPlayer,
              displayed.nextSequence), displayed.walkerDecision)
          projection = Some(displayed)
          failure = retainedNotice
          render()
          polling.foreach(_.resume(coordinator.capture))
          if (!fixedSeat && !rawHistorySequence.contains(displayed.nextSequence)) {
            rawHistorySequence = Some(displayed.nextSequence)
            client match {
              case development: HttpGameClient => development.loadRawEventHistory(gameId).foreach {
                case Right(events) => rawEvents = events; render()
                case Left(_) => ()
              }
              case _ => ()
            }
          }
        case ProjectionRoute.ReloadForActivePlayer(
              displayed,
              nextRequest,
              retainedNotice
            ) =>
          boardSelectionState = None
          modifierWorkflow = None
          facedownAdviserDraft = None
          polling.foreach(_.stop())
          projection = Some(displayed)
          failure = retainedNotice
          selectedPlayer = nextRequest.playerId
          updateSessionUrl()
          render()
          client.load(gameId, selectedPlayer).foreach { result =>
            accept(nextRequest, result, retainedNotice)
          }
      }
    }

    def accept(
        request: ServerRequestIdentity,
        result: Either[GameClientFailure, GameProjection],
        notice: Option[GameClientFailure] = None
    ): Unit =
      if (coordinator.accepts(request)) result match {
        case Right(value) => store(request, value, notice)
        case Left(error) =>
          coordinator.recordFailure(request, error)
          if (GameClientFailure.isTransient(error) || recovery(error))
            polling.foreach(_.stop())
          if (recovery(error)) {
            coordinator.switchSession(gameId, selectedPlayer)
            projection = None
          }
          failure = Some(error)
          render()
      }

    def loadExisting(id: String, playerId: String): Unit = {
      if (fixedSeat) return
      polling.foreach(_.stop())
      gameId = id.trim
      projection = None
      boardSelectionState = None
      modifierWorkflow = None
      rawEvents = Vector.empty
      rawHistorySequence = None
      failure = None
      selectedPlayer = playerId.trim match {
        case "" => "red-exile"
        case value => value
      }
      val request = coordinator.switchSession(gameId, selectedPlayer)
      updateSessionUrl()
      render()
      client.load(gameId, selectedPlayer).foreach(accept(request, _))
    }

    def newGame(): Unit = {
      if (fixedSeat) return
      polling.foreach(_.stop())
      startOver()
    }

    def reconnect(): Unit = {
      polling.foreach(_.stop())
      failure = None
      val request = coordinator.reconnect()
      updateSessionUrl()
      render()
      client.load(gameId, selectedPlayer).foreach(accept(request, _))
    }

    def poll(request: ServerRequestIdentity): Unit =
      client.load(request.gameId, request.playerId).foreach {
        case Right(snapshot) if invalidTrustedViewer(snapshot) =>
          val accepted = polling.exists(_.complete(request, continuePolling = false))
          if (accepted) accept(request, Right(snapshot))
        case Right(snapshot) =>
          val advances = projection.forall(current =>
            coordinator.snapshotAdvances(
              request,
              current.nextSequence,
              snapshot.nextSequence
            )
          )
          if (advances) {
            val accepted = polling.exists(
              _.complete(request, continuePolling = false)
            )
            if (accepted) accept(request, Right(snapshot))
          } else {
            val accepted = polling.exists(
              _.complete(request, continuePolling = true)
            )
            if (accepted) coordinator.recordSnapshotSuccess(request)
          }
        case Left(error) =>
          val transient = GameClientFailure.isTransient(error)
          val accepted = polling.exists(
            _.complete(request, continuePolling = !transient)
          )
          if (accepted) accept(request, Left(error))
      }

    def submitTransport(command: GameCommand,
        modifiers: Vector[ModifierInvocation] = Vector.empty): Unit =
      projection.foreach { current =>
        val request = coordinator.capture
        client
          .submit(gameId, selectedPlayer, current.nextSequence, command, modifiers)
          .foreach {
            case Left(stale: GameClientFailure.StalePosition)
                if coordinator.accepts(request) =>
              boardSelectionState = None
              modifierWorkflow = None
              failure = Some(stale)
              client.load(gameId, selectedPlayer).foreach {
                refreshed => accept(request, refreshed, Some(stale))
              }
            case other => accept(request, other)
          }
      }

    def submit(command: GameCommand): Unit = ModifierWorkflow.action(command) match {
      case None => submitTransport(command)
      case Some((action, parameters)) => projection.foreach { current =>
        val request = MajorActionPreviewRequest(current.nextSequence, action, parameters)
        client.preview(gameId, selectedPlayer, request).foreach {
          case Right(response) if response.modifiers.isEmpty =>
            submitTransport(command)
          case Right(response) =>
            val context = ModifierSelectionContext(gameId, selectedPlayer,
              current.nextSequence, action)
            val fingerprint = s"${response.nextSequence}:${response.action}:" +
              response.modifiers.map(m => s"${m.sourceKey}/${m.handlerId}").mkString("|")
            modifierWorkflow = Some(ModifierWorkflow(Some(command), None,
              parameters, response,
              ModifierSelectionState.reconcile(modifierWorkflow.map(_.selection),
                context, response.modifiers, fingerprint),
              ModifierWorkflowStage.Ordering))
            render()
          case Left(error) => failure = Some(error); render()
        }
      }
    }

    def activatePreviewTargets(workflow: ModifierWorkflow,
        response: MajorActionPreviewResponse): Unit = for {
      current <- projection
      actionKind <- workflow.actionKind
    } {
      val context = BoardSelectionContext(gameId, selectedPlayer,
        current.nextSequence)
      if (actionKind == "play-facedown-adviser") {
        facedownAdviserDraft = current.minorActions.flatMap(
          FacedownAdviserDraft.initial(context, _))
        boardSelectionState = None
      } else ModifierWorkflow.targetAction(actionKind, response,
        current.boardTargetActions).foreach { action =>
        boardSelectionState = Some(BoardTargetSelectionState.reconcile(None,
          context, Vector(action)).activate(actionKind))
      }
      modifierWorkflow = Some(workflow.showTargets(response))
      render()
    }

    def startTargetedFlow(actionKind: String): Unit = for {
      current <- projection
      (action, parameters) <- ModifierWorkflow.targeted(actionKind)
    } {
      modifierWorkflow = None
      facedownAdviserDraft = None
      boardSelectionState = boardSelectionState.map(_.cancel)
      val request = MajorActionPreviewRequest(current.nextSequence, action, parameters)
      client.preview(gameId, selectedPlayer, request).foreach {
        case Right(response) =>
          val context = ModifierSelectionContext(gameId, selectedPlayer,
            current.nextSequence, action)
          val fingerprint = s"${response.nextSequence}:${response.action}:" +
            response.modifiers.map(m => s"${m.sourceKey}/${m.handlerId}").mkString("|")
          val workflow = ModifierWorkflow(None, Some(actionKind), parameters,
            response, ModifierSelectionState.reconcile(None, context,
              response.modifiers, fingerprint),
            if (response.modifiers.nonEmpty) ModifierWorkflowStage.Ordering
            else ModifierWorkflowStage.Targets)
          if (workflow.ordering) { modifierWorkflow = Some(workflow); render() }
          else activatePreviewTargets(workflow, response)
        case Left(error) => failure = Some(error); render()
      }
    }

    def confirmModifierSelection(): Unit = modifierWorkflow.foreach { workflow =>
      val request = MajorActionPreviewRequest(workflow.selection.context.sequence,
        workflow.selection.context.action, workflow.baseParameters,
        workflow.selection.invocations)
      client.preview(gameId, selectedPlayer, request).foreach {
        case Right(response) => workflow.command match {
          case Some(command) =>
            modifierWorkflow = None
            val (submitted, modifiers) = ModifierWorkflow.submission(command,
              workflow.selection.invocations)
            submitTransport(submitted, modifiers)
          case None => activatePreviewTargets(workflow, response)
        }
        case Left(error) =>
          modifierWorkflow = None
          facedownAdviserDraft = None
          failure = Some(error)
          render()
      }
    }

    def completeTargetCommand(command: GameCommand): Unit =
      modifierWorkflow.filter(_.stage == ModifierWorkflowStage.Targets) match {
        case Some(workflow) =>
          modifierWorkflow = None
          facedownAdviserDraft = None
          boardSelectionState = None
          val (submitted, modifiers) = ModifierWorkflow.submission(command,
            workflow.selection.invocations)
          submitTransport(submitted, modifiers)
        case None => submit(command)
      }

    def restoreBoardTargetActions(): Unit =
      boardSelectionState = projection.map { current =>
        BoardTargetSelectionState.restore(
          BoardSelectionContext(gameId, selectedPlayer, current.nextSequence),
          current.boardTargetActions)
      }

    def handleBoardSelection(result: BoardSelectionResult): Unit = result match {
      case BoardSelectionResult.Updated(state) =>
        boardSelectionState = Some(state)
        render()
      case BoardSelectionResult.Submit(action, targets) =>
        commandForSelection(action, targets, selectedPlayer).foreach { command =>
          completeTargetCommand(command)
        }
    }

    lazy val ui: ServerUiView = new ServerUiView {
      def currentGameId = gameId
      def currentPlayerId = selectedPlayer
      def displayedProjection = projection
      def sessionCoordinator = coordinator
      def currentBoardSelection = boardSelectionState
      def currentBoardSelection_=(value: Option[BoardTargetSelectionState]) = boardSelectionState = value
      def currentWalkerPartition = walkerPartitionDraft
      def currentWalkerPartition_=(value: Option[WalkerPartitionDraft]) = walkerPartitionDraft = value
      def currentWalkerDistribution = walkerDistributeDraft
      def currentWalkerDistribution_=(value: Option[WalkerDistributeDraft]) = walkerDistributeDraft = value
      def currentWalkerSelection = walkerSelectionDraft
      def currentWalkerSelection_=(value: Option[WalkerSelectionDraft]) = walkerSelectionDraft = value
      def currentModifierWorkflow = modifierWorkflow
      def currentFacedownAdviserDraft = facedownAdviserDraft
      def chooseFacedownAdviser(cardId: String) = {
        facedownAdviserDraft = facedownAdviserDraft.map(_.choose(cardId)); render()
      }
      def toggleModifier(value: PreviewModifier) = {
        modifierWorkflow = modifierWorkflow.map(workflow => workflow.copy(
          selection = workflow.selection.toggle(value))); render()
      }
      def moveModifier(value: PreviewModifier, delta: Int) = {
        modifierWorkflow = modifierWorkflow.map(workflow => workflow.copy(selection =
          if (delta < 0) workflow.selection.moveEarlier(value)
          else workflow.selection.moveLater(value))); render()
      }
      def confirmModifiers() = confirmModifierSelection()
      def backFromModifiers() = { modifierWorkflow = None; render() }
      def cancelModifiers() = {
        modifierWorkflow = None
        facedownAdviserDraft = None
        restoreBoardTargetActions()
        render()
      }
      def beginTargetedMajorAction(actionKind: String) =
        startTargetedFlow(actionKind)
      def backFromTargets() = modifierWorkflow.foreach { workflow =>
        facedownAdviserDraft = None
        boardSelectionState = None
        modifierWorkflow = workflow.backFromTargets
        render()
      }
      def cancelTargetAction() = {
        modifierWorkflow = modifierWorkflow.flatMap(_.cancel)
        facedownAdviserDraft = None
        restoreBoardTargetActions()
        render()
      }
      def submitTargetCommand(command: GameCommand) =
        completeTargetCommand(command)
      def canControl = controlsAvailable
      def rerender() = render()
      def submitCommand(command: GameCommand) = submit(command)
      def handleSelection(result: BoardSelectionResult) = handleBoardSelection(result)
      def loadSession(id: String, playerId: String) = loadExisting(id, playerId)
      def reconnectSession() = reconnect()
      def createGame() = newGame()
    }

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
    if (fixedSeat) client.load(gameId, selectedPlayer).foreach(accept(coordinator.capture, _))
    else queryParameter("gameId") match {
      case Some(existing) => loadExisting(existing, selectedPlayer)
      case None => newGame()
    }

    def controlsAvailable: Boolean =
      coordinator.connectionState == ServerConnectionState.Connected
  }
}
