package oathdigital.frontend

import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object ServerModeUi {
  private val bootstrap = FirstGameBootstrap(
    Vector(
      BootstrapPlayer("red-exile", "red-lineage", "red"),
      BootstrapPlayer("blue-exile", "blue-lineage", "blue"),
      BootstrapPlayer("yellow-exile", "yellow-lineage", "yellow")
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
    var rawEvents = Vector.empty[RawEvent]
    var rawHistorySequence = Option.empty[Long]

    def render(): Unit = {
      while (mount.lastChild != null) mount.removeChild(mount.lastChild)
      mount.appendChild(text("div", "eyebrow",
        "Server mode · JVM-authoritative persisted stream"))
      mount.appendChild(text("h1", "", "Oath Digital game"))
      coordinator.connectionState match {
        case ServerConnectionState.Disconnected(_) =>
          mount.appendChild(text(
            "div",
            "status error disconnected",
            "Disconnected. Reconnect to fetch the authoritative current " +
              "state before issuing another command."
          ))
        case ServerConnectionState.Connecting if projection.nonEmpty =>
          mount.appendChild(text(
            "div",
            "status",
            "Reconnecting to server…"
          ))
        case _ => ()
      }
      failure.foreach(error =>
        mount.appendChild(text("div", "status error", error.message)))
      projection match {
        case None if failure.isEmpty =>
          mount.appendChild(text("div", "status", "Loading server projection…"))
        case None => ()
        case Some(value) =>
          val presentation = viewerPresentation(value, selectedPlayer)
          mount.appendChild(actionsPanel(value, presentation))
          mount.appendChild(players(value))
          mount.appendChild(world(value, presentation))
          mount.appendChild(playerBoards(value))
      }
      mount.appendChild(controls())
      if (projection.nonEmpty) mount.appendChild(rawEventLog())
    }

    def store(
        request: ServerRequestIdentity,
        value: GameProjection,
        notice: Option[GameClientFailure]
    ): Unit =
      coordinator.route(request, value, notice).foreach {
        case ProjectionRoute.Display(displayed, retainedNotice) =>
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

    def submit(command: GameCommand): Unit =
      projection.foreach { current =>
        val request = coordinator.capture
        client
          .submit(gameId, selectedPlayer, current.nextSequence, command)
          .foreach {
            case Left(stale: GameClientFailure.StalePosition)
                if coordinator.accepts(request) =>
              boardSelectionState = None
              boardFormationState = None
              campaignPlacementState = None
              failure = Some(stale)
              client.load(gameId, selectedPlayer).foreach {
                refreshed => accept(request, refreshed, Some(stale))
              }
            case other => accept(request, other)
          }
      }

    def handleBoardSelection(result: BoardSelectionResult): Unit = result match {
      case BoardSelectionResult.Updated(state) =>
        boardSelectionState = Some(state)
        render()
      case BoardSelectionResult.Submit(action, targets) =>
        val force = projection.toVector.flatMap(_.playerBoards)
          .find(_.playerId == selectedPlayer).map(_.warbands).getOrElse(0)
        commandForSelection(action, targets, selectedPlayer, force).foreach { command =>
          boardSelectionState = None
          submit(command)
        }
      case BoardSelectionResult.Form(state) =>
        boardFormationState = Some(state)
        render()
    }

    def controls(): dom.Element = {
      val bar = element("div", "debug-toolbar")
      bar.appendChild(text(
        "span",
        "debug-label",
        s"Development-only active-player view: $selectedPlayer"
      ))
      val input =
        dom.document.createElement("input").asInstanceOf[dom.html.Input]
      input.value = gameId
      input.setAttribute("aria-label", "Existing game ID")
      bar.appendChild(input)
      projection.toVector.flatMap(_.players).foreach { player =>
        val selector = button(player.displayName,
          s"player-selector ${player.color.cssClass}")
        selector.setAttribute("aria-pressed",
          (player.playerId == selectedPlayer).toString)
        selector.setAttribute("data-player-id", player.playerId)
        selector.onclick = _ => loadExisting(input.value, player.playerId)
        bar.appendChild(selector)
      }
      val load = button("Load existing game", "load-game")
      load.onclick = _ => loadExisting(input.value, selectedPlayer)
      bar.appendChild(load)
      coordinator.connectionState match {
        case ServerConnectionState.Disconnected(_) =>
          val retry = button("Reconnect", "reconnect")
          retry.setAttribute(
            "aria-label",
            "Reconnect and fetch authoritative current state"
          )
          retry.onclick = _ => reconnect()
          bar.appendChild(retry)
        case _ => ()
      }
      val fresh = button("New persisted test game", "restart")
      fresh.setAttribute(
        "aria-label",
        "Create and bootstrap a fresh persisted development game"
      )
      fresh.onclick = _ => newGame()
      bar.appendChild(fresh)
      bar
    }

    def rawEventLog(): dom.Element = {
      val panel = element("section", "panel raw-event-log")
      panel.appendChild(text("h2", "", "Raw authoritative event log"))
      panel.appendChild(text("p", "warning",
        "Development only. Raw authoritative events may reveal hidden outcomes."))
      if (rawEvents.isEmpty) panel.appendChild(text("p", "empty-state", "No events."))
      val list = element("ol", "events")
      rawEvents.foreach { event =>
        val item = element("li", "raw-event")
        item.appendChild(text("strong", "", s"${event.sequence} · ${event.discriminator}"))
        item.appendChild(text("pre", "raw-payload", event.rawPayload))
        list.appendChild(item)
      }
      panel.appendChild(list)
      panel
    }

    def status(value: GameProjection): dom.Element = {
      val node = element("div", "status")
      val presentation = viewerPresentation(value, selectedPlayer)
      presentation.waitingForPlayerId match {
        case Some(playerId) =>
          node.appendChild(dom.document.createTextNode("Waiting for "))
          node.appendChild(playerReference(value, playerId))
        case None if value.phase == "act-action-selection" =>
          node.textContent = "Act phase — choose your first normal action."
        case None if value.phase == "wake" =>
          node.textContent = "Wake phase — take available wealth or end Wake."
        case None if value.phase == "rest" =>
          node.textContent = "Rest phase — finish Rest when ready."
        case None if value.phase == "game-over" =>
          node.textContent = value.oathkeeper.flatMap(_.winnerPlayerId)
            .fold("Game over.") { winner =>
              val reason = value.oathkeeper.flatMap(_.winnerVictoryKind)
                .map(_.replace('-', ' ')).getOrElse("winner")
              s"Game over — $winner wins ($reason)."
            }
        case None if value.phase == "search-decision" =>
          node.textContent = "Act phase — resolve your Search."
        case None if value.phase == "awaiting-adviser" =>
          node.textContent = "Setup — choose your starting adviser."
        case None if value.phase == "awaiting-pawn" =>
          node.textContent = "Setup — choose your pawn's starting site."
        case None =>
          node.appendChild(dom.document.createTextNode(
            s"${value.phase}; active participant: "
          ))
          value.activeParticipantId match {
            case Some(playerId) =>
              node.appendChild(playerReference(value, playerId))
            case None => node.appendChild(dom.document.createTextNode("none"))
          }
        }
      node
    }

    def actionsPanel(
        value: GameProjection,
        presentation: ViewerPresentation
    ): dom.Element = {
      val panel = element("section", "panel wake-actions")
      panel.appendChild(text("h2", "", "Available actions"))
      panel.appendChild(status(value))
      value.oathkeeper.foreach { oath =>
        val holder = oath.holderPlayerId.getOrElse("unheld")
        val limiter = if (oath.usurperLimited) " · Usurper locked until round 4" else ""
        val winner = oath.winnerPlayerId.fold("")(id => s" · Winner: $id")
        panel.appendChild(text("p", "oathkeeper-status",
          s"Oath of Supremacy · ${oath.side.capitalize}: $holder$limiter$winner"))
      }
      value.activePlayerResources.foreach { resources =>
        panel.appendChild(text(
          "p",
          "resources",
          s"Favor ${resources.favor} · Secrets ${resources.faceUpSecrets} " +
            s"face up / ${resources.faceDownSecrets} face down · " +
            s"Supply ${resources.supply}"
        ))
      }
      value.currentSiteResources.foreach { resources =>
        panel.appendChild(text(
          "p",
          "site-resources",
          s"${siteLabel(value, resources.siteId)} loose wealth: " +
            s"${resources.favor} favor · ${resources.secrets} secrets"
        ))
      }
      if (value.phase == "wake" && presentation.showGameplayControls) {
        takeWealthActions(value, selectedPlayer).foreach { action =>
          val control = button(action.label, "wake-action")
          control.disabled = !controlsAvailable
          control.onclick = _ => submit(action.command)
          panel.appendChild(control)
        }

        val end = button("End Wake", "wake-action")
        end.disabled = !controlsAvailable ||
          !value.legalControls.contains("endWake")
        end.onclick = _ => submit(GameCommand.EndWake(selectedPlayer))
        panel.appendChild(end)
      }
      if (!value.actionSelectionOpen && presentation.showGameplayControls) {
        boardSelectionState.flatMap(_.activeAction).foreach { action =>
          panel.appendChild(text("p", "selection-instruction", action.prompt))
          panel.appendChild(text("p", "selection-cardinality",
            cardinalityInstruction(action)))
        }
      }
      if (showActActionControls(value, presentation)) {
        val selection = boardSelectionState.flatMap(_.activeAction)
        if (boardFormationState.nonEmpty) {
          val formation = boardFormationState.get
          val targetLabel = formation.targets.map { target =>
            formation.action.candidates.find(_.target == target)
              .fold(target.stableKey)(_.label)
          }.mkString(", ")
          panel.appendChild(text("h2", "", "Form Campaign force"))
          panel.appendChild(text("p", "campaign-formation-target",
            s"Target: $targetLabel"))
          val summary = text("p", "campaign-formation-summary",
            campaignFormationSummary(formation))
          summary.setAttribute("aria-live", "polite")
          panel.appendChild(summary)
          val decrease = button("Decrease committed force", "campaign-force-decrease")
          decrease.setAttribute("aria-label", campaignForceAdjustmentLabel(increase = false))
          decrease.disabled = !controlsAvailable || formation.force <= formation.minimumForce
          decrease.onclick = _ => {
            boardFormationState = boardFormationState.map(_.decrement); render()
          }
          panel.appendChild(decrease)
          (formation.minimumForce to formation.maximumForce).foreach { count =>
            val choice = button(count.toString, "campaign-force-choice")
            choice.setAttribute("aria-label", campaignForceChoiceLabel(count))
            choice.setAttribute("aria-pressed", (formation.force == count).toString)
            choice.disabled = !controlsAvailable
            choice.onclick = _ => {
              boardFormationState = boardFormationState.map(_.choose(count)); render()
            }
            panel.appendChild(choice)
          }
          val increase = button("Increase committed force", "campaign-force-increase")
          increase.setAttribute("aria-label", campaignForceAdjustmentLabel(increase = true))
          increase.disabled = !controlsAvailable || formation.force >= formation.maximumForce
          increase.onclick = _ => {
            boardFormationState = boardFormationState.map(_.increment); render()
          }
          panel.appendChild(increase)
          val confirm = button("Confirm Campaign", "campaign-force-confirm")
          confirm.disabled = !controlsAvailable
          confirm.onclick = _ => {
            boardFormationState = None
            boardSelectionState = None
            commandForFormation(formation, selectedPlayer).foreach(submit)
          }
          panel.appendChild(confirm)
          val back = button("Back to target selection", "campaign-force-back")
          back.onclick = _ => { boardFormationState = None; render() }
          panel.appendChild(back)
          val cancel = button("Cancel Campaign", "campaign-force-cancel")
          cancel.onclick = _ => {
            boardFormationState = None
            boardSelectionState = boardSelectionState.map(_.cancel)
            render()
          }
          panel.appendChild(cancel)
        } else if (selection.nonEmpty) {
          val action = selection.get
          panel.appendChild(text("p", "selection-instruction", action.prompt))
          panel.appendChild(text("p", "selection-cardinality",
            cardinalityInstruction(action)))
          if (!action.autoActivate) {
            val cancel = button("Cancel", "cancel-board-selection")
            cancel.onclick = _ => {
              boardSelectionState = boardSelectionState.map(_.cancel)
              render()
            }
            panel.appendChild(cancel)
          }
          if (action.maximum > 1) {
            val confirm = button("Confirm selection", "confirm-board-selection")
            confirm.disabled = !controlsAvailable ||
              !boardSelectionState.exists(_.canConfirm)
            confirm.onclick = _ => boardSelectionState.flatMap(_.confirmResult)
              .foreach(handleBoardSelection)
            panel.appendChild(confirm)
          }
          action.candidates.foreach { candidate =>
            val choose = button(candidateButtonLabel(candidate),
              "board-target-control raid-target-control")
            choose.setAttribute("data-target-ref", candidate.target.stableKey)
            choose.setAttribute("aria-pressed", boardSelectionState.exists(
              _.selected(candidate.target)).toString)
            choose.disabled = !controlsAvailable
            choose.onclick = _ => boardSelectionState.foreach(state =>
              handleBoardSelection(state.choose(candidate.target)))
            panel.appendChild(choose)
          }
        } else {
          value.legalSearchSources.foreach { source =>
            val label = source.kind match {
              case "world" => s"Search world deck (${source.supplyCost} Supply)"
              case _ => s"Search ${source.region.getOrElse("regional")} discard " +
                s"(${source.supplyCost} Supply)"
            }
            val search = button(label, "act-action search-action")
            search.disabled = !controlsAvailable || !presentation.showGameplayControls
            search.onclick = _ => submit(GameCommand.BeginSearch(
              selectedPlayer, source.kind, source.region))
            panel.appendChild(search)
          }
          if (value.legalControls.contains("beginRecover")) {
            val recover = button("Recover (1 Supply)", "act-action recover-action")
            recover.disabled = !controlsAvailable
            recover.onclick = _ => submit(GameCommand.BeginRecover(selectedPlayer))
            panel.appendChild(recover)
          }
          if (value.legalControls.contains("beginForge")) {
            val forge = button("Forge (1 Supply)", "act-action forge-action")
            forge.disabled = !controlsAvailable
            forge.onclick = _ => submit(GameCommand.BeginForge(selectedPlayer))
            panel.appendChild(forge)
          }
          if (value.legalControls.contains("placeBannerResource")) {
            value.banners.filter(_.holderPlayerId.contains(selectedPlayer)).foreach { banner =>
              val label = dom.document.createElement("label").asInstanceOf[dom.html.Label]
              label.textContent = s"Add to ${actionLabel(banner.banner)} "
              val amount = dom.document.createElement("input").asInstanceOf[dom.html.Input]
              amount.`type` = "number"; amount.min = "1"; amount.value = "1"
              amount.setAttribute("aria-label", s"Resources to add to ${actionLabel(banner.banner)}")
              label.appendChild(amount); panel.appendChild(label)
              val place = button("Place resources (0 Supply)", "banner-place-resource")
              place.disabled = !controlsAvailable
              place.onclick = _ => submit(GameCommand.PlaceBannerResource(
                selectedPlayer, banner.banner, amount.value.toInt))
              panel.appendChild(place)
            }
          }
          value.minorActions.foreach { minor =>
            if (minor.advisers.nonEmpty) {
              panel.appendChild(text("h2", "", "Facedown advisers"))
              minor.advisers.foreach { adviser =>
                adviser.placements.foreach { placement =>
                  val label = placement.kind match {
                    case "discard" => s"Discard ${adviser.card.name}"
                    case "play-adviser" => s"Play ${adviser.card.name} as adviser"
                    case "play-site" if placement.replacement.nonEmpty =>
                      s"Play ${adviser.card.name}; discard ${placement.replacement.get.name}"
                    case _ => s"Play ${adviser.card.name} at your site"
                  }
                  val control = button(label, s"minor-adviser-${placement.kind}")
                  control.disabled = !controlsAvailable
                  control.onclick = _ => minorAdviserCommand(
                    adviser, placement, selectedPlayer).foreach(submit)
                  panel.appendChild(control)
                }
              }
            }
            if (minor.canPeekSiteRelics) {
              val peek = button("Peek at relics at your site", "minor-peek-relics")
              peek.disabled = !controlsAvailable
              peek.onclick = _ => submit(GameCommand.PeekSiteRelics(selectedPlayer))
              panel.appendChild(peek)
            }
            minor.facedownRelics.foreach { relic =>
              val reveal = button(s"Reveal ${relic.name}", "minor-reveal-relic")
              reveal.disabled = !controlsAvailable
              reveal.onclick = _ => submit(GameCommand.RevealOwnedRelic(
                selectedPlayer, relic.cardId))
              panel.appendChild(reveal)
            }
            Vector(true -> minor.maxBoardToSite, false -> minor.maxSiteToBoard)
              .filter(_._2 > 0).foreach { case (toSite, maximum) =>
                val label = dom.document.createElement("label").asInstanceOf[dom.html.Label]
                label.textContent = if (toSite) "Warbands board to site "
                  else "Warbands site to board "
                val amount = dom.document.createElement("input").asInstanceOf[dom.html.Input]
                amount.`type` = "number"; amount.min = "1"; amount.max = maximum.toString
                amount.value = "1"; amount.setAttribute("aria-label", label.textContent)
                label.appendChild(amount); panel.appendChild(label)
                val move = button("Move warbands", "minor-move-warband")
                move.disabled = !controlsAvailable
                move.onclick = _ => submit(GameCommand.MoveWarbands(
                  selectedPlayer, toSite, amount.value.toInt))
                panel.appendChild(move)
              }
          }
          value.boardTargetActions.filterNot(_.autoActivate).foreach { action =>
            val control = button(actionLabel(action.actionKind),
              s"act-action target-action action-${action.actionKind}")
            control.disabled = !controlsAvailable ||
              !presentation.showGameplayControls ||
              (action.candidates.isEmpty && action.minimum > 0)
            control.onclick = _ => {
              if (action.minimum == 0 && action.maximum == 0)
                commandForSelection(action, Vector.empty, selectedPlayer).foreach(submit)
              else {
                boardSelectionState = boardSelectionState.map(
                  _.activate(action.actionKind))
                render()
              }
            }
            panel.appendChild(control)
          }
          panel.appendChild(text("p", "informational",
            "Other normal action families are not yet implemented."))
          if (value.legalControls.contains("beginRest")) {
            val rest = button("End Act and Rest", "rest-action")
            rest.disabled = !controlsAvailable
            rest.onclick = _ => submit(GameCommand.BeginRest(selectedPlayer))
            panel.appendChild(rest)
          }
        }
      }
      value.recover.filter(_ => presentation.showGameplayControls).foreach { recover =>
        panel.appendChild(text("h2", "", "Recover"))
        panel.appendChild(text("p", "recover-results",
          s"Dice: ${recover.dice.mkString(", ")} · ${recover.shields}/${recover.difficulty} shields · " +
            s"${recover.supplySpent} Supply spent · ${recover.supplyRemaining} remaining"))
        if (recover.canAddDice) {
          val add = button("Spend 1 Supply for two dice", "recover-add")
          add.disabled = !controlsAvailable
          add.onclick = _ => submit(GameCommand.AddRecoverDice(
            selectedPlayer, recover.decisionId))
          panel.appendChild(add)
        }
        if (recover.canStop) {
          val stop = button("Stop Recover", "recover-stop")
          stop.disabled = !controlsAvailable
          stop.onclick = _ => submit(GameCommand.StopRecover(
            selectedPlayer, recover.decisionId))
          panel.appendChild(stop)
        }
      }
      value.forge.filter(_ => presentation.showGameplayControls).foreach { forge =>
        panel.appendChild(text("h2", "", "Forge a relic"))
        panel.appendChild(text("p", "forge-instruction",
          s"Assign ${forge.favor} favor and ${forge.secrets} secrets, one resource per denizen."))
        val confirm = button("Complete Forge", "forge-complete")
        forge.targets.zipWithIndex.foreach { case (target, index) =>
          val label = dom.document.createElement("label").asInstanceOf[dom.html.Label]
          label.textContent = target.label + " "
          val select = dom.document.createElement("select").asInstanceOf[dom.html.Select]
          select.setAttribute("aria-label", s"Resource for ${target.label}")
          Vector("favor", "secret").foreach { resource =>
            val option = dom.document.createElement("option").asInstanceOf[dom.html.Option]
            option.value = resource; option.text = resource.capitalize
            option.selected = forgeAssignmentState.exists(
              _.assignments(index) == resource)
            select.appendChild(option)
          }
          select.onchange = _ => {
            forgeAssignmentState = forgeAssignmentState.map(
              _.choose(index, select.value))
            confirm.disabled = !controlsAvailable ||
              !forgeAssignmentState.exists(_.canConfirm)
          }
          label.appendChild(select); panel.appendChild(label)
        }
        confirm.disabled = !controlsAvailable ||
          !forgeAssignmentState.exists(_.canConfirm)
        confirm.onclick = _ => forgeAssignmentState.flatMap(
          _.command(selectedPlayer)).foreach(submit)
        panel.appendChild(confirm)
      }
      value.challenge.filter(_ => presentation.showGameplayControls).foreach { challenge =>
        panel.appendChild(text("h2", "", s"Challenge ${actionLabel(challenge.banner)}"))
        challengeSiteCommands(challenge, selectedPlayer).foreach { command =>
          val site = command.siteId
          val choose = button(s"Place secret at $site", "challenge-secret-site")
          choose.disabled = !controlsAvailable
          choose.onclick = _ => submit(command)
          panel.appendChild(choose)
        }
        if (challenge.legalSecretSiteIds.isEmpty) {
          val label = dom.document.createElement("label").asInstanceOf[dom.html.Label]
          label.textContent = "Resources to place "
          val amount = dom.document.createElement("input").asInstanceOf[dom.html.Input]
          amount.`type` = "number"; amount.min = challenge.minimumPlacement.toString
          amount.max = challenge.maximumPlacement.toString
          amount.value = challenge.minimumPlacement.toString
          amount.setAttribute("aria-label", "Banner replacement resources")
          label.appendChild(amount); panel.appendChild(label)
          val complete = button("Take banner", "challenge-complete")
          complete.disabled = !controlsAvailable || challenge.minimumPlacement > challenge.maximumPlacement
          complete.onclick = _ => completeChallengeCommand(challenge,
            selectedPlayer, amount.value.toInt).foreach(submit)
          panel.appendChild(complete)
        }
      }
      value.negotiation match {
        case Some(deal) if presentation.showGameplayControls =>
          panel.appendChild(text("h2", "", "Negotiation"))
          panel.appendChild(text("p", "negotiation-status",
            deal.participantPlayerIds.map(id => s"$id: ${if (deal.acceptedPlayerIds.contains(id))
              "accepted" else "reviewing"}").mkString(" · ")))
          deal.transfers.foreach(t => panel.appendChild(text("p", "negotiation-transfer",
            s"${t.authorPlayerId} gives ${t.recipientPlayerId}: ${t.favor} favor, " +
              s"${t.relicCount} relic(s)")))
          deal.disclosures.foreach(d => panel.appendChild(text("p", "negotiation-disclosure",
            s"${d.authorPlayerId} promises ${d.recipientPlayerId} a ${d.kind} disclosure")))
          val favors = scala.collection.mutable.ArrayBuffer.empty[(String, dom.html.Input)]
          val relics = scala.collection.mutable.ArrayBuffer.empty[(String, String, dom.html.Input)]
          val disclosures = scala.collection.mutable.ArrayBuffer.empty[
            (String, String, CardDetails, dom.html.Input)]
          deal.participantPlayerIds.filterNot(_ == selectedPlayer).foreach { recipient =>
            panel.appendChild(text("h3", "", s"Your terms for $recipient"))
            val favor = dom.document.createElement("input").asInstanceOf[dom.html.Input]
            favor.`type` = "number"; favor.min = "0"; favor.max = deal.editableFavor.toString
            favor.value = deal.transfers.find(t => t.authorPlayerId == selectedPlayer &&
              t.recipientPlayerId == recipient).map(_.favor).getOrElse(0).toString
            favor.setAttribute("aria-label", s"Favor offered to $recipient")
            panel.appendChild(favor); favors += recipient -> favor
            deal.editableRelics.foreach { relic =>
              val check = dom.document.createElement("input").asInstanceOf[dom.html.Input]
              check.`type` = "checkbox"; check.setAttribute("aria-label",
                s"Offer ${relic.name} to $recipient")
              check.checked = negotiationRelicChecked(
                deal, selectedPlayer, recipient, relic.cardId)
              check.onchange = _ => if (check.checked) relics.foreach {
                case (otherRecipient, otherRelic, otherCheck)
                    if negotiationRelicCompetes(otherRecipient, otherRelic,
                      recipient, relic.cardId) =>
                  otherCheck.checked = false
                case _ => ()
              }
              panel.appendChild(check); panel.appendChild(text("span", "", s" ${relic.name} "))
              relics += ((recipient, relic.cardId, check))
            }
            (deal.editableAdvisers.map("adviser" -> _) ++
                deal.editableRelics.map("held-relic" -> _) ++
                deal.editableSiteRelics.map("site-relic" -> _)).foreach { case (kind, card) =>
              val check = dom.document.createElement("input").asInstanceOf[dom.html.Input]
              check.`type` = "checkbox"; check.setAttribute("aria-label",
                s"Promise $kind disclosure of ${card.name} to $recipient")
              check.checked = negotiationDisclosureChecked(
                deal, selectedPlayer, recipient, kind, card.cardId)
              panel.appendChild(check); panel.appendChild(text("span", "", s" Show ${card.name} "))
              disclosures += ((recipient, kind, card, check))
            }
          }
          val save = button("Save Deal Changes", "negotiation-save")
          save.disabled = !controlsAvailable
          save.onclick = _ => {
            val terms = NegotiationTermsInput(favors.map { case (recipient, input) =>
              NegotiationTransferInput(recipient, input.value.toInt,
                relics.collect { case (`recipient`, relic, check) if check.checked => relic }.toVector)
            }.toVector, disclosures.collect { case (recipient, kind, card, check)
                if check.checked => NegotiationDisclosureInput(recipient, kind,
                  Option.when(kind != "site-relic")(selectedPlayer),
                  Option.when(kind == "site-relic")(deal.siteId),
                  Option.when(kind == "adviser")(card.cardKind), card.cardId)
            }.toVector)
            submit(GameCommand.ReplaceNegotiationTerms(selectedPlayer, deal.decisionId, terms))
          }
          panel.appendChild(save)
          val accept = button("Accept Current Deal", "negotiation-accept")
          accept.disabled = !controlsAvailable ||
            !value.legalControls.contains("acceptNegotiation")
          accept.onclick = _ => submit(GameCommand.AcceptNegotiation(
            selectedPlayer, deal.decisionId)); panel.appendChild(accept)
          val decline = button("End/Decline", "negotiation-decline")
          decline.disabled = !controlsAvailable
          decline.onclick = _ => submit(GameCommand.DeclineNegotiation(
            selectedPlayer, deal.decisionId)); panel.appendChild(decline)
        case None if value.negotiationWaiting =>
          panel.appendChild(text("p", "informational", "Waiting for the negotiation to finish."))
        case _ => ()
      }
      value.campaign.filter(_ => presentation.showGameplayControls).foreach { campaign =>
        panel.appendChild(text("h2", "", "Campaign"))
        if (!campaign.plansFinished) {
          panel.appendChild(text("p", "campaign-instruction",
            s"Choose ${campaign.planSide} battle plans in order, then finish."))
          if (campaign.selectedPlans.nonEmpty) panel.appendChild(text("p",
            "campaign-selected-plans", campaignSelectedPlansLabel(
              campaign.selectedPlans)))
          campaign.planChoices.foreach { choice =>
            val choose = button(campaignPlanButtonLabel(choice), "campaign-plan")
            choose.title = choice.mechanicalResult
            choose.disabled = !controlsAvailable
            choose.onclick = _ => submit(GameCommand.ChooseCampaignPlan(
              selectedPlayer, campaign.decisionId, choice))
            panel.appendChild(choose)
          }
          val rollsNow = campaign.planSide == "defender" || campaign.defenderKind == "bandits"
          val finishLabel = if (!rollsNow) "Finish attacker plans"
            else if (campaign.selectedPlans.isEmpty) "Roll without battle plans"
            else "Finish plans and roll"
          val finish = button(finishLabel, "campaign-finish-plans")
          finish.disabled = !controlsAvailable
          finish.onclick = _ => submit(GameCommand.FinishCampaignPlans(
            selectedPlayer, campaign.decisionId))
          panel.appendChild(finish)
        } else {
          panel.appendChild(text("p", "campaign-results",
            s"Attack dice: ${campaign.attackDice.mkString(", ")} · " +
              s"${campaign.attack} attack · ${campaign.skullLosses} skull losses"))
        if (campaign.sacrificed.isEmpty) {
          panel.appendChild(text("p", "campaign-instruction",
            "Choose surviving warbands to sacrifice for +1 attack each."))
          (0 to campaign.maxSacrifice).foreach { count =>
            val choose = button(s"Sacrifice $count", "campaign-sacrifice")
            choose.disabled = !controlsAvailable
            choose.onclick = _ => submit(GameCommand.ChooseCampaignSacrifice(
              selectedPlayer, campaign.decisionId, count))
            panel.appendChild(choose)
          }
        } else {
          val outcome = if (campaign.victorious.contains(true)) "Victory" else "Defeat"
          panel.appendChild(text("p", "campaign-defense",
            s"Defense dice: ${campaign.defenseDice.mkString(", ")} · " +
              s"${campaign.defense.getOrElse(0)} defense · $outcome"))
          if (campaign.victorious.contains(true)) {
            val placement = campaignPlacementState.getOrElse(
              CampaignPlacementState.reconcile(None,
                BoardSelectionContext(value.gameId, selectedPlayer,
                  value.nextSequence), Some(campaign)).get)
            panel.appendChild(text("p", "campaign-instruction",
              "Allocate surviving warbands among conquered sites."))
            placement.targets.foreach { target =>
              val row = element("div", "campaign-placement-row")
              row.appendChild(text("span", "campaign-placement-site",
                target.label))
              val decrease = button(s"Remove one from ${target.label}",
                "campaign-placement-decrease")
              decrease.disabled = !controlsAvailable ||
                placement.count(target.siteId) == 0
              decrease.onclick = _ => {
                campaignPlacementState = campaignPlacementState.map(
                  _.decrement(target.siteId)); render()
              }
              row.appendChild(decrease)
              row.appendChild(text("span", "campaign-placement-count",
                placement.count(target.siteId).toString))
              val increase = button(s"Add one to ${target.label}",
                "campaign-placement-increase")
              increase.disabled = !controlsAvailable || placement.remaining == 0
              increase.onclick = _ => {
                campaignPlacementState = campaignPlacementState.map(
                  _.increment(target.siteId)); render()
              }
              row.appendChild(increase)
              panel.appendChild(row)
            }
            panel.appendChild(text("p", "campaign-placement-summary",
              s"Placed: ${placement.total} · Remaining: ${placement.remaining}"))
            val confirm = button("Confirm placement",
              "campaign-placement-confirm")
            confirm.disabled = !controlsAvailable
            confirm.onclick = _ => {
              campaignPlacementState = None
              submit(GameCommand.PlaceCampaignForce(selectedPlayer,
                campaign.decisionId, placement.allocations))
            }
            panel.appendChild(confirm)
            val back = button("Back", "campaign-placement-back")
            back.disabled = !controlsAvailable || placement.total == 0
            back.onclick = _ => {
              campaignPlacementState = campaignPlacementState.map(_.reset)
              render()
            }
            panel.appendChild(back)
          }
        }
        }
      }
      value.campaignRaidRelocation.filter(decision =>
        decision.actorPlayerId == selectedPlayer &&
          presentation.showGameplayControls).foreach { decision =>
        panel.appendChild(text("h2", "", "Relocate defender pawn"))
        panel.appendChild(text("p", "campaign-instruction",
          "Choose another legal site for the defender pawn."))
        raidRelocationCommands(decision, selectedPlayer).foreach { command =>
          val site = command.destinationSiteId
          val choose = button(siteLabel(value, site), "campaign-raid-relocation")
          choose.disabled = !controlsAvailable
          choose.onclick = _ => submit(command)
          panel.appendChild(choose)
        }
      }
      value.oathkeeperRecipient.filter(decision =>
        decision.actorPlayerId == selectedPlayer).foreach { decision =>
        panel.appendChild(text("h2", "", "Choose the Oathkeeper"))
        panel.appendChild(text("p", "campaign-instruction",
          "Choose which tied leader receives the Oathkeeper title."))
        decision.candidatePlayerIds.foreach { candidate =>
          val label = value.players.find(_.playerId == candidate)
            .map(_.displayName).getOrElse(candidate)
          val choose = button(label, "oathkeeper-recipient-choice")
          choose.disabled = !controlsAvailable
          choose.onclick = _ => submit(GameCommand.ChooseOathkeeperRecipient(
            selectedPlayer, decision.decisionId, candidate))
          panel.appendChild(choose)
        }
      }
      if (value.phase == "rest" && presentation.showGameplayControls) {
        panel.appendChild(text("p", "informational",
          "Finish Rest to return card resources, reveal secrets, refresh " +
            "Supply, and wake the next player."))
        val finish = button("Finish Rest", "rest-action")
        finish.disabled = !controlsAvailable ||
          !value.legalControls.contains("finishRest")
        finish.onclick = _ => submit(GameCommand.FinishRest(selectedPlayer))
        panel.appendChild(finish)
      }
      value.pendingCardDecision.filter(_ => presentation.showGameplayControls)
        .foreach(decision => panel.appendChild(cardDecision(value, decision)))
      panel
    }

    def cardDecision(
        value: GameProjection,
        decision: PendingCardDecision
    ): dom.Element = {
      val shell = element("section", "card-decision")
      shell.setAttribute("aria-labelledby", "card-decision-title")
      shell.setAttribute("data-decision-kind", decision.kind)
      shell.appendChild(text("h2", "", decision.prompt))
      shell.lastChild.asInstanceOf[dom.Element].id = "card-decision-title"
      decision.instructions.foreach(instruction =>
        shell.appendChild(text("p", "decision-instruction", instruction)))
      val state = cardDecisionState.filter(_.decisionId == decision.decisionId)
        .getOrElse(CardDecisionState.initial(decision))

      def update(next: CardDecisionState): Unit = {
        cardDecisionState = Some(next)
        render()
      }
      def cardNode(card: CardDetails, zone: String): dom.Element = {
        val node = element("article", "decision-card")
        node.setAttribute("tabindex", "0")
        node.setAttribute("draggable", "true")
        node.setAttribute("data-card-id", card.cardId)
        node.setAttribute("aria-label", card.name)
        node.appendChild(text("strong", "card-name", card.name))
        node.appendChild(cardDetailsPopover(card))
        val move = if (zone == "keep") button("Move to Discard", "move-discard")
          else button("Move to Keep", "move-keep")
        move.onclick = _ => if (zone == "keep") update(state.moveToDiscard(card.cardId))
          else update(state.moveToKeep(card.cardId))
        node.appendChild(move)
        node.addEventListener("dragstart", (event: dom.Event) =>
          event.asInstanceOf[dom.DragEvent].dataTransfer
            .setData("text/plain", card.cardId))
        if (zone == "discard") {
          val left = button("Move Left", "move-left")
          left.disabled = state.discard.headOption.contains(card)
          left.onclick = _ => update(state.move(card.cardId, -1))
          val right = button("Move Right", "move-right")
          right.disabled = state.discard.lastOption.contains(card)
          right.onclick = _ => update(state.move(card.cardId, 1))
          node.appendChild(left); node.appendChild(right)
          node.addEventListener("dragover", (event: dom.Event) => event.preventDefault())
          node.addEventListener("drop", (event: dom.Event) => {
            event.preventDefault()
            event.stopPropagation()
            update(dropBeforeDiscard(state,
              event.asInstanceOf[dom.DragEvent].dataTransfer.getData("text/plain"),
              card.cardId))
          })
        }
        node
      }

      def arrangementZones(showDiscardOrder: Boolean): dom.Element = {
        val zones = element("div", "decision-zones")
        val keep = element("section", "decision-zone keep-zone")
        keep.appendChild(text("h3", "", "Keep"))
        state.keep.foreach(card => keep.appendChild(cardNode(card, "keep")))
        keep.addEventListener("dragover", (event: dom.Event) => event.preventDefault())
        keep.addEventListener("drop", (event: dom.Event) => {
          event.preventDefault()
          update(dropOnKeep(state, event.asInstanceOf[dom.DragEvent]
            .dataTransfer.getData("text/plain")))
        })
        val discard = element("section", "decision-zone discard-zone")
        discard.appendChild(text("h3", "", "Discard"))
        if (showDiscardOrder) discard.appendChild(text("p", "discard-order",
          "Remaining cards are discarded from left to right."))
        state.discard.foreach(card => discard.appendChild(cardNode(card, "discard")))
        discard.addEventListener("dragover", (event: dom.Event) => event.preventDefault())
        discard.addEventListener("drop", (event: dom.Event) => {
          event.preventDefault()
          update(dropOnDiscard(state, event.asInstanceOf[dom.DragEvent]
            .dataTransfer.getData("text/plain")))
        })
        zones.appendChild(keep)
        zones.appendChild(discard)
        zones
      }

      if (decision.kind == "recover-relic") {
        decision.cards.foreach { card =>
          val choose = button(s"Take ${card.name} facedown", "resolution-choice")
          choose.disabled = !controlsAvailable
          choose.onclick = _ => submit(GameCommand.ResolveCardDecision(
            selectedPlayer, decision.decisionId,
            DecisionResolution.TakeFacedownRelic(card.cardId)))
          shell.appendChild(choose)
        }
      } else if (decision.kind == "starting-adviser") {
        shell.appendChild(arrangementZones(showDiscardOrder = false))
        val confirm = button("Confirm adviser", "decision-confirm")
        confirm.disabled = !state.arrangementValid(decision.cards) || !controlsAvailable
        confirm.onclick = _ => state.keep.headOption.foreach(card => submit(
          GameCommand.ResolveCardDecision(selectedPlayer, decision.decisionId,
            DecisionResolution.StartingAdviser(card.cardId))))
        shell.appendChild(confirm)
      } else state.stage match {
        case CardDecisionStage.Arrange =>
          shell.appendChild(arrangementZones(showDiscardOrder = true))
          val confirm = button("Confirm arrangement", "decision-confirm")
          confirm.disabled = !state.arrangementValid(decision.cards)
          confirm.onclick = _ => update(state.copy(stage = CardDecisionStage.Resolve))
          shell.appendChild(confirm)
        case CardDecisionStage.Resolve =>
          val kept = state.keep.head
          shell.appendChild(text("h3", "", s"Resolve ${kept.name}"))
          decision.resolutionsByCard.getOrElse(kept.cardId, Vector.empty).foreach { resolution =>
            val label = resolution.kind match {
              case "discard" => "Discard"
              case "site" => "Play at site"
              case "adviser" if resolution.orientation.contains("face-down") => "Play facedown"
              case "adviser" => "Play faceup"
              case other => other
            }
            val choose = button(label, "resolution-choice")
            choose.setAttribute("aria-pressed", state.selectedResolution.contains(resolution).toString)
            choose.onclick = _ => update(state.chooseResolution(resolution))
            shell.appendChild(choose)
          }
          state.selectedResolution.filter(_.replacementRequired).foreach { resolution =>
            val select = dom.document.createElement("select").asInstanceOf[dom.html.Select]
            select.setAttribute("aria-label", "Card to replace")
            val placeholder = dom.document.createElement("option").asInstanceOf[dom.html.Option]
            placeholder.value = ""; placeholder.text = "Choose a card to replace"
            select.appendChild(placeholder)
            resolution.replacementTargets.foreach { card =>
              val option = dom.document.createElement("option").asInstanceOf[dom.html.Option]
              option.value = card.cardId; option.text = card.name; select.appendChild(option)
            }
            select.onchange = _ => update(state.chooseReplacement(select.value))
            shell.appendChild(select)
          }
          val back = button("Back", "decision-back")
          back.onclick = _ => update(state.copy(stage = CardDecisionStage.Arrange,
            selectedResolution = None, selectedReplacement = None))
          val confirmRow = element("div", "decision-final-row")
          confirmRow.appendChild(back)
          val confirm = button("Final confirm", "decision-confirm")
          confirm.disabled = !state.resolutionValid || !controlsAvailable
          confirm.onclick = _ => for {
            resolution <- state.selectedResolution
          } submit(GameCommand.ResolveCardDecision(selectedPlayer, decision.decisionId,
            DecisionResolution.Search(kept, state.discard,
              resolution.kind match {
                case "adviser" if resolution.orientation.contains("face-up") => "adviser-face-up"
                case "adviser" => "adviser-face-down"
                case other => other
              }, resolution.orientation, state.selectedReplacement)))
          confirmRow.appendChild(confirm)
          shell.appendChild(confirmRow)
      }
      shell
    }

    def players(value: GameProjection): dom.Element = {
      val panel = element("section", "panel")
      panel.appendChild(text("h2", "", "Exile players"))
      val list = element("ul", "participants")
      value.players.foreach { player =>
        val item = dom.document.createElement("li")
        val reference = playerReference(value, player.playerId)
        item.appendChild(reference)
        item.appendChild(dom.document.createTextNode(s" · role: ${player.role}"))
        value.pawnLocations.find(_.playerId == player.playerId).foreach(pawn =>
          item.appendChild(dom.document.createTextNode(
            s" · pawn at ${siteLabel(value, pawn.siteId)}"
          )))
        list.appendChild(item)
      }
      panel.appendChild(list)
      panel
    }

    def playerBoards(value: GameProjection): dom.Element = {
      val panel = element("section", "panel player-boards")
      panel.appendChild(text("h2", "", "Player boards"))
      if (value.playerBoards.isEmpty)
        panel.appendChild(text("p", "empty-state", "Player boards are not available yet."))
      value.playerBoards.foreach { board =>
        val section = element("section", "player-board")
        val heading = element("h3", "")
        heading.appendChild(playerReference(value, board.playerId))
        section.appendChild(heading)
        section.appendChild(text("p", "resources",
          s"Warbands ${board.warbands} · Favor ${board.favor} · Secrets " +
            s"${board.faceUpSecrets} face up / ${board.faceDownSecrets} face down · " +
            s"Supply ${board.supply}"))
        val advisers = element("div", "board-cards advisers")
        advisers.appendChild(text("strong", "", "Advisers"))
        board.advisers.foreach { card =>
          advisers.appendChild(boardCardTarget(card,
            BoardTargetRef.PlayerAdviser(board.playerId, card.cardId)))
        }
        section.appendChild(advisers)
        val relics = element("div", "board-cards relics")
        relics.appendChild(text("strong", "", "Relics"))
        board.relics.foreach { card =>
          relics.appendChild(boardCardTarget(card,
            BoardTargetRef.PlayerRelic(board.playerId, card.cardId)))
        }
        section.appendChild(relics)
        board.revealedVision.foreach(card => {
          section.appendChild(text("strong", "", "Revealed Vision"))
          section.appendChild(cardDetailsPopover(card))
        })
        panel.appendChild(section)
      }
      panel
    }

    def boardCardTarget(card: CardDetails, target: BoardTargetRef): dom.Element = {
      val candidate = boardSelectionState.flatMap(_.activeAction.flatMap(
        _.candidates.find(_.target == target)))
      val shell = element("span", cardTargetClasses(candidate.nonEmpty,
        boardSelectionState.exists(_.selected(target))))
      shell.setAttribute("data-target-ref", target.stableKey)
      shell.appendChild(cardDetailsPopover(card))
      candidate.foreach { value =>
        val choose = button(candidateButtonLabel(value), "board-target-control")
        choose.setAttribute("data-target-ref", target.stableKey)
        choose.setAttribute("aria-pressed",
          boardSelectionState.exists(_.selected(target)).toString)
        choose.onclick = _ => boardSelectionState.foreach(state =>
          handleBoardSelection(state.choose(target)))
        shell.appendChild(choose)
      }
      shell
    }

    def world(
        value: GameProjection,
        presentation: ViewerPresentation
    ): dom.Element = {
      val panel = element("section", "panel world")
      panel.setAttribute("aria-label", "The World")
      panel.appendChild(text("h2", "", "The World"))
      panel.appendChild(pileDisplay("World deck", value.worldDeckCount,
        value.worldDeckTopCardKind))
      val regions = element("div", "regions")
      value.world.foreach { region =>
        val section = element("section", "region")
        val name = region.regionId match {
          case "cradle" => "Cradle"
          case "provinces" => "Provinces"
          case "hinterland" => "Hinterland"
          case other => other
        }
        section.setAttribute("aria-label", name)
        section.appendChild(text("h3", "region-label", name))
        section.appendChild(pileDisplay("Discard", region.discardCount,
          region.discardTopCardKind))
        val sites = element("div", "sites")
        region.sites.foreach { site =>
          val siteTarget = BoardTargetRef.Site(site.siteId)
          val candidate = boardSelectionState.flatMap(
            _.activeAction.flatMap(_.candidates.find(_.target == siteTarget)))
          val isSelected = boardSelectionState.exists(_.selected(siteTarget))
          val control = element("article", siteTargetClasses(candidate.nonEmpty,
            isSelected))
          control.setAttribute("aria-label", site.label)
          control.setAttribute("data-target-ref", siteTarget.stableKey)
          val heading = element("div", "site-heading")
          heading.appendChild(VisualDomRenderer.render(
            SiteCardPresentation.from(site).siteVisual,
            "site-visual"
          ))
          heading.appendChild(text("span", "site-name", site.label))
          control.appendChild(heading)
          candidate.foreach { value =>
            val choose = button(candidateButtonLabel(value), "board-target-control")
            choose.setAttribute("data-target-ref", siteTarget.stableKey)
            choose.setAttribute("aria-pressed", isSelected.toString)
            choose.disabled = !controlsAvailable || !presentation.showGameplayControls
            choose.onclick = _ => boardSelectionState.foreach(state =>
              handleBoardSelection(state.choose(siteTarget)))
            control.appendChild(choose)
          }
          val pawns = element("div", "site-pawns")
          value.pawnLocations.filter(_.siteId == site.siteId).foreach { pawn =>
            val marker = element("span", "pawn")
            marker.appendChild(dom.document.createTextNode("● "))
            marker.appendChild(playerReference(value, pawn.playerId))
            pawns.appendChild(marker)
          }
          if (pawns.childNodes.length > 0) control.appendChild(pawns)
          control.appendChild(siteDetails(site, boardSelectionState,
            target => boardSelectionState.foreach(state =>
              handleBoardSelection(state.choose(target)))))
          sites.appendChild(control)
        }
        section.appendChild(sites)
        regions.appendChild(section)
      }
      panel.appendChild(regions)
      panel
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

  private def siteLabel(value: GameProjection, siteId: String): String =
    value.world.flatMap(_.sites).find(_.siteId == siteId)
      .fold(siteId)(_.label)

  private[frontend] def negotiationRelicChecked(deal: NegotiationState,
      author: String, recipient: String, relicId: String): Boolean =
    deal.transfers.exists(t => t.authorPlayerId == author &&
      t.recipientPlayerId == recipient && t.relics.exists(_.cardId == relicId))

  private[frontend] def negotiationDisclosureChecked(deal: NegotiationState,
      author: String, recipient: String, kind: String, cardId: String): Boolean =
    deal.disclosures.exists(d => d.authorPlayerId == author &&
      d.recipientPlayerId == recipient && d.kind == kind &&
      d.card.exists(_.cardId == cardId))

  private[frontend] def negotiationRelicCompetes(currentRecipient: String,
      currentRelic: String, selectedRecipient: String, selectedRelic: String): Boolean =
    currentRecipient != selectedRecipient && currentRelic == selectedRelic

  private[frontend] def siteDetails(site: GameSite,
      selection: Option[BoardTargetSelectionState] = None,
      chooseTarget: BoardTargetRef => Unit = _ => ()): dom.Element = {
    val presentation = SiteCardPresentation.from(site)
    val details = element("div", "site-details")
    val properties = element("dl", "site-properties")
    presentation.metrics.foreach { metric =>
      val item = element("div", "site-property")
      item.appendChild(text("dt", "", metric.label))
      item.appendChild(text("dd", "", metric.value.toString))
      properties.appendChild(item)
    }
    details.appendChild(properties)
    site.forces.foreach { forces =>
      val row = text("p", s"site-forces ${forceCssClass(forces)}",
        forceText(forces))
      row.setAttribute("data-ruler-kind", forces.rulerKind)
      forces.rulerPlayerId.foreach(row.setAttribute("data-ruler-player-id", _))
      details.appendChild(row)
    }
    if (site.powers.nonEmpty) {
      val powers = element("ul", "site-powers")
      site.powers.foreach { power =>
        val item = element("li", "site-power")
        item.textContent = power.description.fold(power.label)(description =>
          s"${power.label}: $description")
        powers.appendChild(item)
      }
      details.appendChild(powers)
    }

    val denizens = element("div", "site-denizens")
    denizens.appendChild(text("strong", "", "Denizens: "))
    if (site.denizens.isEmpty)
      denizens.appendChild(dom.document.createTextNode(presentation.denizenEmpty))
    else site.denizens.foreach { denizen =>
      val target = BoardTargetRef.SiteCard(site.siteId,
        denizen.details.fold("denizen")(_.cardKind), denizen.denizenId)
      val candidate = selection.flatMap(_.activeAction.flatMap(
        _.candidates.find(_.target == target)))
      val shell = element("span", cardTargetClasses(candidate.nonEmpty,
        selection.exists(_.selected(target))))
      shell.setAttribute("data-target-ref", target.stableKey)
      val card = denizen.details.fold[dom.Element](VisualDomRenderer.render(
        presentation.denizenVisuals.find(_._1 == denizen.denizenId).get._2,
        "site-card"))(cardDetailsPopover)
      card.setAttribute("data-denizen-id", denizen.denizenId)
      shell.appendChild(card)
      candidate.foreach { value =>
        val choose = button(candidateButtonLabel(value), "board-target-control")
        choose.setAttribute("data-target-ref", target.stableKey)
        choose.setAttribute("aria-pressed",
          selection.exists(_.selected(target)).toString)
        choose.onclick = _ => chooseTarget(target)
        shell.appendChild(choose)
      }
      denizens.appendChild(shell)
    }
    (site.denizens.size until site.denizenCapacity).foreach { _ =>
      val slot = text("span", "empty-denizen-slot", "◇")
      slot.setAttribute("role", "img")
      slot.setAttribute("aria-label", "Empty denizen slot")
      denizens.appendChild(slot)
    }
    details.appendChild(denizens)

    val relics = element("div", "site-relics")
    relics.appendChild(text("strong", "", "Relics: "))
    if (site.relics.facedownCount == 0)
      relics.appendChild(dom.document.createTextNode("None"))
    (0 until site.relics.facedownCount).foreach { _ =>
      val relic = text("span", "facedown-relic", "▣")
      relic.setAttribute("role", "img")
      relic.setAttribute("aria-label", "Facedown relic")
      relics.appendChild(relic)
    }
    details.appendChild(relics)
    details
  }

  private[frontend] def forceText(forces: SiteForces): String =
    s"${forces.label} x${forces.count}"

  private[frontend] def forceCssClass(forces: SiteForces): String =
    s"force-${forces.colorToken}"

  private[frontend] def dropOnKeep(
      state: CardDecisionState,
      cardId: String
  ): CardDecisionState = state.moveToKeep(cardId)

  private[frontend] def dropOnDiscard(
      state: CardDecisionState,
      cardId: String
  ): CardDecisionState =
    if (state.keep.exists(_.cardId == cardId)) state.moveToDiscard(cardId)
    else state.arrangeDrop(cardId, None)

  private[frontend] def dropBeforeDiscard(
      state: CardDecisionState,
      cardId: String,
      beforeCardId: String
  ): CardDecisionState = {
    val inDiscard = if (state.keep.exists(_.cardId == cardId))
      state.moveToDiscard(cardId) else state
    inDiscard.arrangeDrop(cardId, Some(beforeCardId))
  }

  private[frontend] def cardDetailsPopover(card: CardDetails): dom.Element = {
    val node = element("button", "card-detail")
    node.setAttribute("type", "button")
    node.setAttribute("aria-label", card.name)
    node.setAttribute("data-card-id", card.cardId)
    node.appendChild(text("span", "card-summary", card.name))
    val details = element("span", "card-popover")
    details.setAttribute("role", "tooltip")
    val metadata = Vector(card.suit.map(value => s"Suit: $value"),
      card.restrictions.map(value => s"Restrictions: $value"),
      card.orientation.map(value => s"Orientation: $value"),
      card.side.map(value => s"Side: $value"),
      Option.when(card.favor > 0)(s"Favor: ${card.favor}"),
      Option.when(card.secrets > 0)(s"Secrets: ${card.secrets}"),
      card.relicValue.map(value => s"Relic value: $value"),
      card.defense.map(value => s"Defense: $value"),
      card.rulesText.map(value => s"Rules: $value")).flatten
    metadata.zipWithIndex.foreach { case (value, index) =>
      details.appendChild(text("span", "card-property", value))
      if (index < metadata.size - 1)
        details.appendChild(dom.document.createElement("br"))
    }
    node.appendChild(details)
    node
  }

  private[frontend] def pileDisplay(
      label: String,
      count: Int,
      topCardKind: Option[String]
  ): dom.Element = {
    val pile = element("div", "pile-display")
    pile.appendChild(text("span", "pile-label", s"$label:"))
    val css = pileCardClasses(count)
    val symbol = pileSymbol(count, topCardKind)
    val back = text("span", css, if (symbol.isEmpty) "\u00a0" else symbol)
    back.setAttribute("role", "img")
    back.setAttribute("aria-label", if (count == 0) "Empty pile"
      else topCardKind match {
        case Some("denizen") => "Denizen card on top"
        case Some("vision") => "Vision card on top"
        case _ => "Facedown card; type hidden"
      })
    pile.appendChild(back)
    pile.appendChild(text("span", "pile-count", s"x$count"))
    pile
  }

  private[frontend] def pileSymbol(count: Int, topCardKind: Option[String]): String =
    if (count == 0) "" else topCardKind match {
      case Some("denizen") => "D"
      case Some("vision") => "V"
      case _ => ""
    }

  private[frontend] def pileCardClasses(count: Int): String =
    if (count == 0) "pile-card pile-empty" else "pile-card pile-back"

  private[frontend] final case class TakeWealthAction(
      label: String,
      command: GameCommand.TakeWealth
  )

  private[frontend] final case class ViewerPresentation(
      showGameplayControls: Boolean,
      waitingForPlayerId: Option[String],
      waitingForDisplayName: Option[String]
  )

  private[frontend] def viewerPresentation(
      value: GameProjection,
      playerId: String
  ): ViewerPresentation = {
    val controllingPlayer = value.campaign.filter(!_.plansFinished)
      .flatMap(_.decisionOwnerPlayerId).orElse(value.activeParticipantId)
    controllingPlayer match {
      case Some(activePlayerId) if activePlayerId != playerId =>
        ViewerPresentation(
          showGameplayControls = false,
          waitingForPlayerId = Some(activePlayerId),
          waitingForDisplayName = Some(playerDisplayName(value, activePlayerId))
        )
      case _ => ViewerPresentation(
        showGameplayControls = true,
        waitingForPlayerId = None,
        waitingForDisplayName = None
      )
    }
  }

  private[frontend] def showActActionControls(
      value: GameProjection,
      presentation: ViewerPresentation
  ): Boolean = value.actionSelectionOpen && presentation.showGameplayControls

  private[frontend] def actionLabel(kind: String): String = kind match {
    case "travel" => "Travel"
    case "campaign-conquest" => "Campaign"
    case "campaign-raid" => "Raid"
    case "challenge" => "Challenge"
    case "peoples-favor" => "People's Favor"
    case "darkest-secret" => "Darkest Secret"
    case "muster" => "Muster"
    case "trade-favor" => "Trade for favor"
    case "trade-secret" => "Trade for secrets"
    case "reveal-vision" => "Reveal Vision"
    case "play-conspiracy" => "Play Conspiracy"
    case "conspiracy-secret-site" => "Place Darkest Secret"
    case other => other
  }

  private[frontend] def cardinalityInstruction(action: BoardTargetAction): String =
    if (action.maximum == 0) "No target is available; confirm to play this action."
    else if (action.maximum == 1) "Choose one target. Selection submits immediately."
    else s"Choose ${action.minimum} to ${action.maximum} targets, then confirm."

  private[frontend] def candidateButtonLabel(candidate: BoardTargetCandidate): String =
    (candidate.label +: candidate.details).mkString(" · ")

  private[frontend] def campaignPlanButtonLabel(choice: CampaignPlanChoice): String = {
    val cost = Vector(
      Option.when(choice.favorCost > 0)(s"${choice.favorCost} Favor"),
      Option.when(choice.secretCost > 0)(s"Place ${choice.secretCost} Secret")
    ).flatten.mkString(", ")
    if (cost.isEmpty) choice.label else s"${choice.label} ($cost)"
  }

  private[frontend] def campaignSelectedPlansLabel(
      plans: Vector[CampaignPlanChoice]): String =
    plans.zipWithIndex.map { case (plan, index) =>
      s"${index + 1}. ${plan.label}"
    }.mkString("Selected: ", " · ", "")

  private[frontend] def campaignFormationSummary(
      formation: BoardTargetFormationState): String =
    s"Committed force: ${formation.force}. Board warbands remaining: " +
      s"${formation.remainingWarbands}. Attack dice before plans: " +
      s"${formation.attackDiceBeforePlans}. Cost: ${formation.supplyCost} Supply."

  private[frontend] def campaignForceChoiceLabel(force: Int): String =
    s"Commit $force warbands"

  private[frontend] def campaignForceAdjustmentLabel(increase: Boolean): String =
    if (increase) "Increase committed force" else "Decrease committed force"

  private[frontend] def siteTargetClasses(candidate: Boolean,
      selected: Boolean): String =
    Vector("site", if (candidate) "board-target" else "site-readonly",
      if (selected) "board-target-selected" else "").filter(_.nonEmpty).mkString(" ")

  private[frontend] def cardTargetClasses(candidate: Boolean,
      selected: Boolean): String =
    Vector("site-card-target", if (candidate) "board-target" else "",
      if (selected) "board-target-selected" else "").filter(_.nonEmpty).mkString(" ")

  private[frontend] def commandForSelection(action: BoardTargetAction,
      targets: Vector[BoardTargetRef], playerId: String,
      attackDiceCount: Int = 0): Option[GameCommand] =
    (action.actionKind, targets) match {
      case ("place-pawn", Vector(BoardTargetRef.Site(site))) =>
        Some(GameCommand.PlacePawn(playerId, site))
      case ("travel", Vector(BoardTargetRef.Site(site))) =>
        Some(GameCommand.Travel(playerId, site))
      case ("campaign-conquest", sites) if sites.nonEmpty &&
          sites.forall(_.isInstanceOf[BoardTargetRef.Site]) =>
        Some(GameCommand.CampaignConquest(playerId, sites.collect {
          case BoardTargetRef.Site(site) => site
        }, attackDiceCount))
      case ("campaign-raid", targets) if targets.nonEmpty &&
          targets.head.isInstanceOf[BoardTargetRef.PlayerPawn] &&
          targets.forall {
            case _: BoardTargetRef.PlayerPawn | _: BoardTargetRef.PlayerRelic |
                _: BoardTargetRef.PlayerBanner => true
            case _ => false
          } => Some(GameCommand.CampaignRaid(playerId, targets, attackDiceCount))
      case ("challenge", Vector(BoardTargetRef.PlayerBanner(_, banner))) =>
        Some(GameCommand.BeginChallenge(playerId, banner))
      case ("negotiation", players) if players.nonEmpty &&
          players.forall(_.isInstanceOf[BoardTargetRef.Player]) =>
        Some(GameCommand.BeginNegotiation(playerId, players.collect {
          case BoardTargetRef.Player(id) => id
        }))
      case ("reveal-vision", Vector(BoardTargetRef.PlayerAdviser(owner, vision)))
          if owner == playerId =>
        Some(GameCommand.RevealVision(playerId, vision))
      case ("play-conspiracy", Vector(BoardTargetRef.PlayerRelic(owner, relic))) =>
        relic.toIntOption.map(slot => GameCommand.PlayConspiracy(playerId,
          Some(ConspiracyTarget.RelicSlot(owner, slot))))
      case ("play-conspiracy", Vector(BoardTargetRef.PlayerBanner(owner, banner))) =>
        Some(GameCommand.PlayConspiracy(playerId,
          Some(ConspiracyTarget.Banner(owner, banner))))
      case ("play-conspiracy", Vector()) if action.minimum == 0 &&
          action.maximum == 0 =>
        Some(GameCommand.PlayConspiracy(playerId, None))
      case ("conspiracy-secret-site", Vector(BoardTargetRef.Site(site))) =>
        action.decisionId.map(GameCommand.ChooseConspiracySecretSite(
          playerId, _, site))
      case ("muster", Vector(BoardTargetRef.SiteCard(_, kind, id))) =>
        Some(GameCommand.Muster(playerId, EconomyTarget(kind, id)))
      case ("trade-favor", Vector(BoardTargetRef.SiteCard(_, kind, id))) =>
        Some(GameCommand.Trade(playerId, EconomyTarget(kind, id), "favor"))
      case ("trade-secret", Vector(BoardTargetRef.SiteCard(_, kind, id))) =>
        Some(GameCommand.Trade(playerId, EconomyTarget(kind, id), "secret"))
      case _ => None
    }

  private[frontend] def commandForFormation(formation: BoardTargetFormationState,
      playerId: String): Option[GameCommand] =
    (formation.action.actionKind, formation.targets) match {
      case ("campaign-conquest", sites) if sites.nonEmpty &&
          sites.forall(_.isInstanceOf[BoardTargetRef.Site]) =>
        Some(GameCommand.CampaignConquest(playerId, sites.collect {
          case BoardTargetRef.Site(site) => site
        }, formation.force))
      case ("campaign-raid", targets) if targets.nonEmpty &&
          targets.head.isInstanceOf[BoardTargetRef.PlayerPawn] &&
          targets.forall {
            case _: BoardTargetRef.PlayerPawn | _: BoardTargetRef.PlayerRelic |
                _: BoardTargetRef.PlayerBanner => true
            case _ => false
          } => Some(GameCommand.CampaignRaid(playerId, targets, formation.force))
      case _ => None
    }

  private[frontend] def raidRelocationCommands(
      decision: CampaignRaidRelocation,
      playerId: String): Vector[GameCommand.RelocateCampaignRaidPawn] =
    if (decision.actorPlayerId != playerId) Vector.empty
    else decision.legalSiteIds.map(site => GameCommand.RelocateCampaignRaidPawn(
      playerId, decision.decisionId, site))

  private[frontend] def challengeSiteCommands(decision: ChallengeState,
      playerId: String): Vector[GameCommand.ChooseChallengeSecretSite] =
    if (decision.actorPlayerId != playerId) Vector.empty
    else decision.legalSecretSiteIds.map(site =>
      GameCommand.ChooseChallengeSecretSite(playerId, decision.decisionId, site))

  private[frontend] def completeChallengeCommand(decision: ChallengeState,
      playerId: String, amount: Int): Option[GameCommand.CompleteChallenge] =
    Option.when(decision.actorPlayerId == playerId &&
      decision.legalSecretSiteIds.isEmpty && amount >= decision.minimumPlacement &&
      amount <= decision.maximumPlacement)(GameCommand.CompleteChallenge(
        playerId, decision.decisionId, amount))

  private[frontend] def takeWealthActions(
      value: GameProjection,
      playerId: String
  ): Vector[TakeWealthAction] =
    if (value.phase != "wake" ||
        !viewerPresentation(value, playerId).showGameplayControls) Vector.empty
    else Vector(
      "takeFavor" -> TakeWealthAction(
        "Take Wealth: 1 favor",
        GameCommand.TakeWealth(playerId, "favor")
      ),
      "takeSecret" -> TakeWealthAction(
        "Take Wealth: 1 secret",
        GameCommand.TakeWealth(playerId, "secret")
      )
    ).collect {
      case (legalControl, action)
          if value.legalControls.contains(legalControl) => action
    }

  private[frontend] def playerReference(
      value: GameProjection,
      playerId: String
  ): dom.Element = {
    val player = value.players.find(_.playerId == playerId)
    val node = text(
      "span",
      s"player-ref ${player.fold[PlayerColorToken](PlayerColorToken.Neutral)(_.color).cssClass}",
      player.fold(playerId)(_.displayName)
    )
    node.setAttribute("data-player-id", playerId)
    node
  }

  private[frontend] def minorAdviserCommand(adviser: MinorAdviser,
      placement: MinorAdviserPlacement, playerId: String): Option[GameCommand] =
    placement.kind match {
      case "discard" => Some(GameCommand.DiscardFacedownAdviser(
        playerId, adviser.card))
      case "play-adviser" => Some(GameCommand.PlayFacedownAdviser(
        playerId, adviser.card, "adviser-face-up"))
      case "play-site" => Some(GameCommand.PlayFacedownAdviser(
        playerId, adviser.card, "site", placement.replacement))
      case _ => None
    }

  private def playerDisplayName(
      value: GameProjection,
      playerId: String
  ): String =
    value.players.find(_.playerId == playerId)
      .fold(playerId)(_.displayName)

  private def freshGameId(): String =
    s"manual-${js.Date.now().toLong}-${(js.Math.random() * 1000000).toInt}"

  private def queryParameter(name: String): Option[String] =
    dom.window.location.search.stripPrefix("?").split("&").toVector
      .flatMap { pair =>
        pair.split("=", 2).toVector match {
          case Vector(key, value) if key == name =>
            Some(js.URIUtils.decodeURIComponent(value))
          case _ => None
        }
      }.headOption.filter(_.nonEmpty)

  private def updateUrl(gameId: String, playerId: String): Unit =
    dom.window.history.replaceState(
      null,
      "",
      s"/?mode=server&gameId=${js.URIUtils.encodeURIComponent(gameId)}" +
        s"&playerId=${js.URIUtils.encodeURIComponent(playerId)}"
    )

  private def button(label: String, className: String): dom.html.Button = {
    val node =
      dom.document.createElement("button").asInstanceOf[dom.html.Button]
    node.className = className
    node.textContent = label
    node
  }

  private def text(tag: String, className: String, value: String): dom.Element = {
    val node = element(tag, className)
    node.textContent = value
    node
  }

  private def element(tag: String, className: String): dom.Element = {
    val node = dom.document.createElement(tag)
    if (className.nonEmpty) node.setAttribute("class", className)
    node
  }
}
