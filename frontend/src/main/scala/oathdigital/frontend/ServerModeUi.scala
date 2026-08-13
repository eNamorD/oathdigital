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
    var travelSelectionOpen = false
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
          travelSelectionOpen = false
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
          travelSelectionOpen = false
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
      travelSelectionOpen = false
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
      travelSelectionOpen = false
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
              failure = Some(stale)
              client.load(gameId, selectedPlayer).foreach {
                refreshed => accept(request, refreshed, Some(stale))
              }
            case other => accept(request, other)
          }
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
      if (showActActionControls(value, presentation)) {
        if (travelSelectionOpen) {
          panel.appendChild(text("p", "informational",
            "Choose a destination site."))
          val cancel = button("Cancel Travel", "cancel-travel")
          cancel.onclick = _ => {
            travelSelectionOpen = false
            render()
          }
          panel.appendChild(cancel)
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
          val travel = button("Travel", "act-action travel-action")
          travel.disabled = !controlsAvailable ||
            value.legalTravelDestinations.isEmpty ||
            !presentation.showGameplayControls
          travel.onclick = _ => {
            travelSelectionOpen = true
            render()
          }
          panel.appendChild(travel)
          value.legalMusters.foreach { option =>
            val control = button(
              s"Muster ${option.label} (+${option.warbandsGained} warbands)",
              "act-action muster-action")
            control.disabled = !controlsAvailable || !presentation.showGameplayControls
            control.onclick = _ => submit(GameCommand.Muster(
              selectedPlayer, option.target))
            panel.appendChild(control)
          }
          value.legalTrades.foreach { option =>
            val control = button(
              s"Trade ${option.label} for ${option.gained} ${option.resource}",
              "act-action trade-action")
            control.disabled = !controlsAvailable || !presentation.showGameplayControls
            control.onclick = _ => submit(GameCommand.Trade(
              selectedPlayer, option.target, option.resource))
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
        board.advisers.foreach(card => advisers.appendChild(cardDetailsPopover(card)))
        section.appendChild(advisers)
        val relics = element("div", "board-cards relics")
        relics.appendChild(text("strong", "", "Relics"))
        board.relics.foreach(card => relics.appendChild(cardDetailsPopover(card)))
        section.appendChild(relics)
        board.revealedVision.foreach(card => {
          section.appendChild(text("strong", "", "Revealed Vision"))
          section.appendChild(cardDetailsPopover(card))
        })
        panel.appendChild(section)
      }
      panel
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
          val control: dom.Element =
            if (siteCardsActionable(
              value,
              presentation,
              controlsAvailable,
              travelSelectionOpen,
              site.siteId
            )) {
              val buttonControl = button("", "site")
              travelCost(value, site.siteId) match {
                case Some(cost) if travelSelectionOpen =>
                  buttonControl.setAttribute("aria-label",
                    s"${site.label}: Travel for $cost Supply")
                  buttonControl.appendChild(text(
                    "span", "travel-cost", s"$cost Supply"))
                  buttonControl.onclick = _ => submit(
                    GameCommand.Travel(selectedPlayer, site.siteId))
                case _ =>
                  buttonControl.setAttribute("aria-label",
                    s"${site.label}: place pawn")
                  buttonControl.onclick = _ => submit(
                    GameCommand.PlacePawn(selectedPlayer, site.siteId))
              }
              buttonControl
            } else {
              val readonly = element("article", "site site-readonly")
              readonly.setAttribute("aria-label", site.label)
              readonly
            }
          val heading = element("div", "site-heading")
          heading.appendChild(VisualDomRenderer.render(
            SiteCardPresentation.from(site).siteVisual,
            "site-visual"
          ))
          heading.appendChild(text("span", "site-name", site.label))
          control.appendChild(heading)
          val pawns = element("div", "site-pawns")
          value.pawnLocations.filter(_.siteId == site.siteId).foreach { pawn =>
            val marker = element("span", "pawn")
            marker.appendChild(dom.document.createTextNode("● "))
            marker.appendChild(playerReference(value, pawn.playerId))
            pawns.appendChild(marker)
          }
          if (pawns.childNodes.length > 0) control.appendChild(pawns)
          control.appendChild(siteDetails(site))
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

  private[frontend] def siteDetails(site: GameSite): dom.Element = {
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
      val card = denizen.details.fold[dom.Element](
        VisualDomRenderer.render(
          presentation.denizenVisuals.find(_._1 == denizen.denizenId).get._2,
          "site-card"))(cardDetailsPopover)
      card.setAttribute("data-denizen-id", denizen.denizenId)
      denizens.appendChild(card)
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
  ): ViewerPresentation =
    value.activeParticipantId match {
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

  private[frontend] def showActActionControls(
      value: GameProjection,
      presentation: ViewerPresentation
  ): Boolean = value.actionSelectionOpen && presentation.showGameplayControls

  private[frontend] def siteCardsActionable(
      value: GameProjection,
      presentation: ViewerPresentation,
      controlsAvailable: Boolean = true,
      travelSelectionOpen: Boolean = false,
      siteId: String = ""
  ): Boolean =
    controlsAvailable && presentation.showGameplayControls &&
      (value.legalControls.contains("placePawn") ||
        (travelSelectionOpen && travelCost(value, siteId).nonEmpty))

  private[frontend] def travelCost(
      value: GameProjection,
      siteId: String
  ): Option[Int] = value.legalTravelDestinations
    .find(_.siteId == siteId).map(_.supplyCost)

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

  private def playerDisplayName(
      value: GameProjection,
      playerId: String
  ): String =
    value.players.find(_.playerId == playerId)
      .fold(playerId)(_.displayName)

  private def freshGameId(): String =
    s"manual-${js.Date.now().toLong}-${(js.Math.random() * 1000000).toInt}"

  private def queryParameter(name: String): Option[String] =
    FrontendMode.queryParameter(dom.window.location.search, name)

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
