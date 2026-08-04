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
          mount.appendChild(status(value))
          mount.appendChild(players(value))
          mount.appendChild(world(value))
          mount.appendChild(advisers(value))
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
      if (value.ready) node.textContent = "Ready to begin first turn."
      else {
        node.appendChild(dom.document.createTextNode(
          s"${value.phase}; active participant: "
        ))
        value.activeParticipantId match {
          case Some(playerId) => node.appendChild(playerReference(value, playerId))
          case None => node.appendChild(dom.document.createTextNode("none"))
        }
      }
      node
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

    def world(value: FirstGameProjection): dom.Element = {
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
          val control = button(site.label, "site")
          control.disabled = !controlsAvailable ||
            !value.legalControls.contains("placePawn")
          control.onclick = _ => submit(
            FirstGameCommand.PlacePawn(selectedPlayer, site.siteId)
          )
          value.pawnLocations.filter(_.siteId == site.siteId).foreach { pawn =>
            control.appendChild(dom.document.createTextNode(" · ● "))
            control.appendChild(playerReference(value, pawn.playerId))
          }
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
