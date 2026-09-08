package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, _}
import ServerUiSupport._

import org.scalajs.dom
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object ServerModeUi {
  private val bootstrap = FirstGameBootstrapRequest(
    0,
    Vector(
      BootstrapParticipantRequest("red-exile", "red-lineage", "red"),
      BootstrapParticipantRequest("blue-exile", "blue-lineage", "blue"),
      BootstrapParticipantRequest("yellow-exile", "yellow-lineage", "yellow")
    ),
    "red-exile"
  )

  def start(mount: dom.Element): Unit = {
    val client = new HttpGameClient(new SameOriginJsonTransport)
    var projection = Option.empty[GameProjection]
    var failure = Option.empty[GameClientFailure]
    var selectedPlayer = queryParameter("playerId").getOrElse("red-exile")
    var gameId = queryParameter("gameId").getOrElse(freshGameId())
    val coordinator = new ServerSessionCoordinator(gameId, selectedPlayer)
    var polling = Option.empty[SnapshotPollingCoordinator]
    var boardSelectionState = Option.empty[BoardTargetSelectionState]
    var boardFormationState = Option.empty[BoardTargetFormationState]
    var campaignPlacementState = Option.empty[CampaignPlacementState]
    var forgeAssignmentState = Option.empty[ForgeAssignmentState]
    var cardDecisionState = Option.empty[CardDecisionState]
    var modifierWorkflow = Option.empty[ModifierWorkflow]
    var facedownAdviserDraft = Option.empty[FacedownAdviserDraft]
    var rawEvents = Vector.empty[RawEvent]
    var rawHistorySequence = Option.empty[Long]
    val shell = new GameTableShell(mount)

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
        val notice = text("div", "status error", error.message)
        notice.setAttribute("role", "alert")
        actionContent.appendChild(notice)
      }
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
          val pendingIds = Vector(value.recover.map(_.decisionId),
            value.forge.map(_.decisionId), value.campaign.map(_.decisionId),
            value.campaignRaidRelocation.map(_.decisionId),
            value.oathkeeperRecipient.map(_.decisionId), value.challenge.map(_.decisionId),
            value.negotiation.map(_.decisionId), value.restPower.map(_.decisionId)).flatten
          val decisionKey = Vector(selectedPlayer, value.phase,
            value.activeParticipantId.getOrElse(""),
            value.pendingCardDecision.map(_.decisionId).getOrElse(""),
            pendingIds.mkString(","), boardFormationState.isDefined.toString,
            campaignPlacementState.isDefined.toString,
            boardSelectionState.flatMap(_.activeActionKind).getOrElse(""),
            modifierWorkflow.map(_.stage.toString).getOrElse(""), prompt).mkString("|")
          (WorldBoardRenderer.players(value, ui),
            WorldBoardRenderer.world(value, presentation, ui), decisionKey)
      }
      val development = element("div", "development-content")
      development.appendChild(DevelopmentRenderer.controls(ui))
      if (projection.nonEmpty) development.appendChild(DevelopmentRenderer.rawEventLog(rawEvents))
      val attention = s"$decision|${coordinator.connectionState}|${failure.map(_.message)}"
      shell.update(gameId, attention, players, world, actionContent, development)
    }

    def store(
        request: ServerRequestIdentity,
        value: GameProjection,
        notice: Option[GameClientFailure]
    ): Unit =
      coordinator.route(request, value, notice).foreach {
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
          boardFormationState = BoardTargetFormationState.reconcile(
            boardFormationState,
            BoardSelectionContext(gameId, selectedPlayer, displayed.nextSequence),
            displayed.boardTargetActions)
          campaignPlacementState = CampaignPlacementState.reconcile(
            campaignPlacementState,
            BoardSelectionContext(gameId, selectedPlayer,
              displayed.nextSequence), displayed.campaign)
          forgeAssignmentState = ForgeAssignmentState.reconcile(
            forgeAssignmentState,
            BoardSelectionContext(gameId, selectedPlayer,
              displayed.nextSequence), displayed.forge)
          cardDecisionState = displayed.pendingCardDecision.map { decision =>
            cardDecisionState.filter(_.decisionId == decision.decisionId)
              .getOrElse(CardDecisionState.initial(decision))
          }
          projection = Some(displayed)
          failure = retainedNotice
          render()
          polling.foreach(_.resume(coordinator.capture))
          if (!rawHistorySequence.contains(displayed.nextSequence)) {
            rawHistorySequence = Some(displayed.nextSequence)
            client.loadRawEventHistory(gameId).foreach {
              case Right(events) => rawEvents = events; render()
              case Left(_) => ()
            }
          }
        case ProjectionRoute.ReloadForActivePlayer(
              displayed,
              nextRequest,
              retainedNotice
            ) =>
          boardSelectionState = None
          boardFormationState = None
          campaignPlacementState = None
          cardDecisionState = None
          modifierWorkflow = None
          facedownAdviserDraft = None
          polling.foreach(_.stop())
          projection = Some(displayed)
          failure = retainedNotice
          selectedPlayer = nextRequest.playerId
          updateUrl(gameId, selectedPlayer)
          render()
          client.load(gameId, selectedPlayer).foreach { result =>
            accept(nextRequest, result, retainedNotice)
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
          if (GameClientFailure.isTransient(error))
            polling.foreach(_.stop())
          failure = Some(error)
          render()
      }

    def loadExisting(id: String, playerId: String): Unit = {
      polling.foreach(_.stop())
      gameId = id.trim
      projection = None
      boardSelectionState = None
      boardFormationState = None
      campaignPlacementState = None
      cardDecisionState = None
      modifierWorkflow = None
      rawEvents = Vector.empty
      rawHistorySequence = None
      failure = None
      selectedPlayer = playerId.trim match {
        case "" => "red-exile"
        case value => value
      }
      val request = coordinator.switchSession(gameId, selectedPlayer)
      updateUrl(gameId, selectedPlayer)
      render()
      client.load(gameId, selectedPlayer).foreach(accept(request, _))
    }

    def newGame(): Unit = {
      polling.foreach(_.stop())
      gameId = freshGameId()
      selectedPlayer = bootstrap.firstPlayer
      projection = None
      boardSelectionState = None
      boardFormationState = None
      campaignPlacementState = None
      cardDecisionState = None
      modifierWorkflow = None
      rawEvents = Vector.empty
      rawHistorySequence = None
      failure = None
      val request = coordinator.switchSession(gameId, selectedPlayer)
      updateUrl(gameId, selectedPlayer)
      render()
      client.bootstrap(gameId, selectedPlayer, bootstrap)
        .foreach(accept(request, _))
    }

    def reconnect(): Unit = {
      polling.foreach(_.stop())
      failure = None
      val request = coordinator.reconnect()
      updateUrl(gameId, selectedPlayer)
      render()
      client.load(gameId, selectedPlayer).foreach(accept(request, _))
    }

    def poll(request: ServerRequestIdentity): Unit =
      client.load(request.gameId, request.playerId).foreach {
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
              boardFormationState = None
              campaignPlacementState = None
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
      boardFormationState = None
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
      boardFormationState = None
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
            submitTransport(command, workflow.selection.invocations)
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
          boardFormationState = None
          submitTransport(command, workflow.selection.invocations)
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
        val force = projection.toVector.flatMap(_.playerBoards)
          .find(_.playerId == selectedPlayer).map(_.warbands).getOrElse(0)
        commandForSelection(action, targets, selectedPlayer, force).foreach { command =>
          completeTargetCommand(command)
        }
      case BoardSelectionResult.Form(state) =>
        boardFormationState = Some(state)
        render()
    }

    lazy val ui: ServerUiView = new ServerUiView {
      def currentGameId = gameId
      def currentPlayerId = selectedPlayer
      def displayedProjection = projection
      def sessionCoordinator = coordinator
      def currentBoardSelection = boardSelectionState
      def currentBoardSelection_=(value: Option[BoardTargetSelectionState]) = boardSelectionState = value
      def currentBoardFormation = boardFormationState
      def currentBoardFormation_=(value: Option[BoardTargetFormationState]) = boardFormationState = value
      def currentCampaignPlacement = campaignPlacementState
      def currentCampaignPlacement_=(value: Option[CampaignPlacementState]) = campaignPlacementState = value
      def currentForgeAssignment = forgeAssignmentState
      def currentForgeAssignment_=(value: Option[ForgeAssignmentState]) = forgeAssignmentState = value
      def currentCardDecision = cardDecisionState
      def currentCardDecision_=(value: Option[CardDecisionState]) = cardDecisionState = value
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
        boardFormationState = None
        render()
      }
      def beginTargetedMajorAction(actionKind: String) =
        startTargetedFlow(actionKind)
      def backFromTargets() = modifierWorkflow.foreach { workflow =>
        facedownAdviserDraft = None
        boardSelectionState = None
        boardFormationState = None
        modifierWorkflow = workflow.backFromTargets
        render()
      }
      def cancelTargetAction() = {
        modifierWorkflow = modifierWorkflow.flatMap(_.cancel)
        facedownAdviserDraft = None
        restoreBoardTargetActions()
        boardFormationState = None
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
    queryParameter("gameId") match {
      case Some(existing) => loadExisting(existing, selectedPlayer)
      case None => newGame()
    }

    def controlsAvailable: Boolean =
      coordinator.connectionState == ServerConnectionState.Connected
  }
}
