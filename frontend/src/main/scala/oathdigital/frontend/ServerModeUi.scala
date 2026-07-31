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
    var selectedPlayer = "red-exile"
    var gameId = queryParameter("gameId").getOrElse(freshGameId())

    def render(): Unit = {
      while (mount.lastChild != null) mount.removeChild(mount.lastChild)
      mount.appendChild(text("div", "eyebrow",
        "Server mode · JVM-authoritative persisted stream"))
      mount.appendChild(text("h1", "", "Oath Digital first-game setup"))
      mount.appendChild(controls())
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

    def store(value: FirstGameProjection): Unit = {
      projection = Some(value)
      failure = None
      value.activeParticipantId match {
        case Some(active) if active != selectedPlayer && !value.ready =>
          selectedPlayer = active
          client.load(gameId, selectedPlayer).foreach(accept)
        case _ => render()
      }
    }

    def accept(result: Either[FirstGameClientFailure, FirstGameProjection])
        : Unit =
      result match {
        case Right(value) => store(value)
        case Left(error) =>
          failure = Some(error)
          render()
      }

    def loadExisting(id: String): Unit = {
      gameId = id.trim
      projection = None
      failure = None
      selectedPlayer = "red-exile"
      updateUrl(gameId)
      render()
      client.load(gameId, selectedPlayer).foreach(accept)
    }

    def newGame(): Unit = {
      gameId = freshGameId()
      selectedPlayer = bootstrap.firstPlayer
      projection = None
      failure = None
      updateUrl(gameId)
      render()
      client.bootstrap(gameId, selectedPlayer, bootstrap).foreach(accept)
    }

    def submit(command: FirstGameCommand): Unit =
      projection.foreach { current =>
        client
          .submit(gameId, selectedPlayer, current.nextSequence, command)
          .foreach {
            case Left(stale: FirstGameClientFailure.StalePosition) =>
              failure = Some(stale)
              client.load(gameId, selectedPlayer).foreach {
                case Right(refreshed) =>
                  projection = Some(refreshed)
                  render()
                case Left(loadFailure) =>
                  failure = Some(loadFailure)
                  render()
              }
            case other => accept(other)
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
      val load = button("Load existing game", "load-game")
      load.onclick = _ => loadExisting(input.value)
      bar.appendChild(load)
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
      val message =
        if (value.ready)
          "Ready to begin first turn."
        else
          s"${value.phase}; active participant: " +
            value.activeParticipantId.getOrElse("none")
      text("div", "status", message)
    }

    def players(value: FirstGameProjection): dom.Element = {
      val panel = element("section", "panel")
      panel.appendChild(text("h2", "", "Exile players"))
      val list = element("ul", "participants")
      value.players.foreach { player =>
        val item = dom.document.createElement("li")
        val reference = text(
          "span",
          s"player-ref ${player.color.cssClass}",
          s"${player.displayName} · role: ${player.role}"
        )
        item.appendChild(reference)
        value.pawnLocations.find(_.playerId == player.playerId).foreach(pawn =>
          item.appendChild(dom.document.createTextNode(
            s" · pawn at ${siteLabel(value, pawn.siteId)}"
          )))
        panel.appendChild(item)
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
          control.disabled = !value.legalControls.contains("placePawn")
          control.onclick = _ => submit(
            FirstGameCommand.PlacePawn(selectedPlayer, site.siteId)
          )
          value.pawnLocations.filter(_.siteId == site.siteId).foreach { pawn =>
            val player = value.players.find(_.playerId == pawn.playerId)
            control.appendChild(dom.document.createTextNode(
              s" · ● ${player.fold(pawn.playerId)(_.displayName)}"
            ))
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
      panel.appendChild(text(
        "h2",
        "",
        s"Private adviser choices for $selectedPlayer"
      ))
      if (value.privateAdviserChoices.isEmpty)
        panel.appendChild(text("p", "", "No private adviser choice is available."))
      else
        value.privateAdviserChoices.foreach { choice =>
          val control = button(choice.label, "adviser")
          control.setAttribute("data-adviser-id", choice.adviserId)
          control.disabled = !value.legalControls.contains("chooseAdviser")
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

    render()
    queryParameter("gameId") match {
      case Some(existing) => loadExisting(existing)
      case None => newGame()
    }
  }

  private def siteLabel(value: FirstGameProjection, siteId: String): String =
    value.world.flatMap(_.sites).find(_.siteId == siteId)
      .fold(siteId)(_.label)

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

  private def updateUrl(gameId: String): Unit =
    dom.window.history.replaceState(
      null,
      "",
      s"/?gameId=${js.URIUtils.encodeURIComponent(gameId)}"
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
