package oathdigital.frontend

import org.scalajs.dom
import oathdigital.presentation.VisualInstruction
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
    val client = new HttpFirstGameClient(new SameOriginJsonTransport)
    var projection = Option.empty[FirstGameProjection]
    var failure = Option.empty[FirstGameClientFailure]
    var selectedPlayer = queryParameter("playerId").getOrElse("red-exile")
    var gameId = queryParameter("gameId").getOrElse(freshGameId())
    val coordinator = new ServerSessionCoordinator(gameId, selectedPlayer)
    var polling = Option.empty[SnapshotPollingCoordinator]

    def render(): Unit = {
      while (mount.lastChild != null) mount.removeChild(mount.lastChild)
      mount.appendChild(text("div", "eyebrow",
        "Server mode · JVM-authoritative persisted stream"))
      mount.appendChild(text("h1", "", "Oath Digital first-game setup"))
      mount.appendChild(controls())
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
            "Reconnecting to authoritative state…"
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
          mount.appendChild(status(value))
          mount.appendChild(players(value))
          mount.appendChild(world(value, presentation))
          if (presentation.showGameplayControls)
            mount.appendChild(advisers(value))
          mount.appendChild(wakeActions(value, presentation))
      }
    }

    def store(
        request: ServerRequestIdentity,
        value: FirstGameProjection,
        notice: Option[FirstGameClientFailure]
    ): Unit =
      coordinator.route(request, value, notice).foreach {
        case ProjectionRoute.Display(displayed, retainedNotice) =>
          projection = Some(displayed)
          failure = retainedNotice
          render()
          polling.foreach(_.resume(coordinator.capture))
        case ProjectionRoute.ReloadForActivePlayer(
              displayed,
              nextRequest,
              retainedNotice
            ) =>
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
        result: Either[FirstGameClientFailure, FirstGameProjection],
        notice: Option[FirstGameClientFailure] = None
    ): Unit =
      if (coordinator.accepts(request)) result match {
        case Right(value) => store(request, value, notice)
        case Left(error) =>
          coordinator.recordFailure(request, error)
          if (FirstGameClientFailure.isTransient(error))
            polling.foreach(_.stop())
          failure = Some(error)
          render()
      }

    def loadExisting(id: String, playerId: String): Unit = {
      polling.foreach(_.stop())
      gameId = id.trim
      projection = None
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
          val transient = FirstGameClientFailure.isTransient(error)
          val accepted = polling.exists(
            _.complete(request, continuePolling = !transient)
          )
          if (accepted) accept(request, Left(error))
      }

    def submit(command: FirstGameCommand): Unit =
      projection.foreach { current =>
        val request = coordinator.capture
        client
          .submit(gameId, selectedPlayer, current.nextSequence, command)
          .foreach {
            case Left(stale: FirstGameClientFailure.StalePosition)
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
      val playerInput =
        dom.document.createElement("input").asInstanceOf[dom.html.Input]
      playerInput.value = selectedPlayer
      playerInput.setAttribute("aria-label", "Selected player ID")
      bar.appendChild(playerInput)
      val load = button("Load existing game", "load-game")
      load.onclick = _ => loadExisting(input.value, playerInput.value)
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

    def status(value: FirstGameProjection): dom.Element = {
      val node = element("div", "status")
      val presentation = viewerPresentation(value, selectedPlayer)
      presentation.waitingForPlayerId match {
        case Some(playerId) =>
          node.appendChild(dom.document.createTextNode("Waiting for "))
          node.appendChild(playerReference(value, playerId))
        case None if value.phase == "act-action-selection" =>
          node.textContent = "Act phase — choose your first normal action."
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

    def wakeActions(
        value: FirstGameProjection,
        presentation: ViewerPresentation
    ): dom.Element = {
      val panel = element("section", "panel wake-actions")
      panel.appendChild(text("h2", "", "Wake actions"))
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
        end.onclick = _ => submit(FirstGameCommand.EndWake(selectedPlayer))
        panel.appendChild(end)
      }
      if (value.actionSelectionOpen) {
        panel.appendChild(text(
          "p",
          "informational",
          "Normal action families (not yet implemented):"
        ))
        val list = element("ul", "action-families")
        value.actionFamilies.foreach(action =>
          list.appendChild(text("li", "", action)))
        panel.appendChild(list)
      }
      panel
    }

    def players(value: FirstGameProjection): dom.Element = {
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

    def world(
        value: FirstGameProjection,
        presentation: ViewerPresentation
    ): dom.Element = {
      val panel = element("section", "panel world")
      panel.setAttribute("aria-label", "The World")
      panel.appendChild(text("h2", "", "The World"))
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
        val sites = element("div", "sites")
        region.sites.foreach { site =>
          val control: dom.Element =
            if (presentation.showGameplayControls) {
              val buttonControl = button("", "site")
              buttonControl.disabled = !controlsAvailable ||
                !value.legalControls.contains("placePawn")
              buttonControl.setAttribute(
                "aria-label",
                s"${site.label}: place pawn"
              )
              buttonControl.onclick = _ => submit(
                FirstGameCommand.PlacePawn(selectedPlayer, site.siteId)
              )
              buttonControl
            } else {
              val readonly = element("article", "site site-readonly")
              readonly.setAttribute("aria-label", site.label)
              readonly
            }
          val heading = element("div", "site-heading")
          heading.appendChild(visualFallback(
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

    def advisers(value: FirstGameProjection): dom.Element = {
      val panel = element("section", "panel adviser-panel")
      val heading = element("h2", "")
      heading.appendChild(dom.document.createTextNode(
        "Private adviser choices for "
      ))
      heading.appendChild(playerReference(value, selectedPlayer))
      panel.appendChild(heading)
      if (value.privateAdviserChoices.isEmpty)
        panel.appendChild(text("p", "", "No private adviser choice is available."))
      else
        value.privateAdviserChoices.foreach { choice =>
          val control = button(choice.label, "adviser")
          control.setAttribute("data-adviser-id", choice.adviserId)
          control.disabled = !controlsAvailable ||
            !value.legalControls.contains("chooseAdviser")
          control.onclick = _ => submit(
            FirstGameCommand.ChooseAdviser(
              selectedPlayer,
              choice.adviserId
            )
          )
          panel.appendChild(control)
        }
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

  private def siteLabel(value: FirstGameProjection, siteId: String): String =
    value.world.flatMap(_.sites).find(_.siteId == siteId)
      .fold(siteId)(_.label)

  private[frontend] def siteDetails(site: FirstGameSite): dom.Element = {
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

    val denizens = element("div", "site-denizens")
    denizens.appendChild(text("strong", "", "Denizens: "))
    if (site.denizens.isEmpty)
      denizens.appendChild(dom.document.createTextNode(presentation.denizenEmpty))
    else presentation.denizenVisuals.foreach { case (denizenId, visual) =>
      val card = visualFallback(visual, "site-card")
      card.setAttribute("data-denizen-id", denizenId)
      denizens.appendChild(card)
    }
    details.appendChild(denizens)

    val relics = element("div", "site-relics")
    relics.appendChild(text("strong", "", "Relics: "))
    relics.appendChild(dom.document.createTextNode(
      presentation.relicSummary
    ))
    details.appendChild(relics)
    details
  }

  private def visualFallback(
      instruction: VisualInstruction,
      className: String
  ): dom.Element = instruction match {
    case VisualInstruction.Placeholder(symbol, label, accessibleLabel) =>
      val node = element("span", s"$className visual-fallback")
      node.setAttribute("role", "img")
      node.setAttribute("aria-label", accessibleLabel.value)
      node.setAttribute("data-fallback-text", label)
      node.textContent = symbol
      node
    case VisualInstruction.Image(reference, accessibleLabel) =>
      val image = dom.document.createElement("img")
        .asInstanceOf[dom.html.Image]
      image.className = className
      image.src = reference.value
      image.alt = accessibleLabel.value
      image
  }

  private[frontend] final case class TakeWealthAction(
      label: String,
      command: FirstGameCommand.TakeWealth
  )

  private[frontend] final case class ViewerPresentation(
      showGameplayControls: Boolean,
      waitingForPlayerId: Option[String],
      waitingForDisplayName: Option[String]
  )

  private[frontend] def viewerPresentation(
      value: FirstGameProjection,
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

  private[frontend] def takeWealthActions(
      value: FirstGameProjection,
      playerId: String
  ): Vector[TakeWealthAction] =
    if (value.phase != "wake" ||
        !viewerPresentation(value, playerId).showGameplayControls) Vector.empty
    else Vector(
      "takeFavor" -> TakeWealthAction(
        "Take Wealth: 1 favor",
        FirstGameCommand.TakeWealth(playerId, "favor")
      ),
      "takeSecret" -> TakeWealthAction(
        "Take Wealth: 1 secret",
        FirstGameCommand.TakeWealth(playerId, "secret")
      )
    ).collect {
      case (legalControl, action)
          if value.legalControls.contains(legalControl) => action
    }

  private[frontend] def playerReference(
      value: FirstGameProjection,
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
      value: FirstGameProjection,
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
