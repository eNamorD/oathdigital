package oathdigital.frontend

import org.scalajs.dom
import oathdigital.engine.RecordedEvent
import oathdigital.setup.{SetupEvent, SetupState}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object Main {
  def main(args: Array[String]): Unit = {
    val mount = Option(dom.document.getElementById("app")).getOrElse {
      throw new IllegalStateException("missing #app mount point")
    }
    if (dom.window.location.port != "8000") {
      ServerModeUi.start(mount)
      return
    }
    val adapter = new HrfDomAdapter(mount)
    val client = LocalDebugSetupClient.demo()
    var currentProjection = Option.empty[SetupProjection]
    var lastFailure = Option.empty[SetupClientFailure]

    def render(): Unit = adapter.replace { root =>
      root.appendChild(textElement(
        "div",
        "eyebrow",
        "Local debug client · browser-memory authority"
      ))
      root.appendChild(textElement("h1", "", "Oath Digital setup"))
      root.appendChild(textElement(
        "p",
        "lede",
        "A minimal Scala.js view over Oath's existing authoritative-domain-" +
          "event setup engine. Artwork is deliberately replaced by " +
          "semantic cards."
      ))

      currentProjection match {
        case None =>
          root.appendChild(textElement(
            "div",
            "status",
            lastFailure.fold("Loading projection…")(failure =>
              s"Client error: ${failure.message}"
            )
          ))
        case Some(projection) =>
          val view = SetupViewModel.from(projection)
          root.appendChild(debugToolbar(() => {
            client.restartDebug().foreach { result =>
              result match {
                case Right(restarted) =>
                  currentProjection = Some(restarted.projection)
                  lastFailure = None
                case Left(failure) => lastFailure = Some(failure)
              }
              render()
            }
          }))
          root.appendChild(status(projection, lastFailure))
          root.appendChild(participantPanel(view))
          root.appendChild(boardPanel(
            client,
            projection,
            view,
            result => handleSubmit(client, result, (refreshed, failure) => {
              refreshed.foreach(value => currentProjection = Some(value))
              lastFailure = failure
              render()
            })
          ))
          root.appendChild(eventPanel(projection.visibleEvents, projection))
      }
    }

    render()
    client.load().foreach { loaded =>
      currentProjection = loaded.toOption
      lastFailure = loaded.left.toOption
      render()
    }
  }

  private def handleSubmit(
      client: SetupClient,
      result: Either[SetupClientFailure, AcceptedSetupUpdate],
      finish: (Option[SetupProjection], Option[SetupClientFailure]) => Unit
  ): Unit =
    result match {
      case Right(update) => finish(Some(update.projection), None)
      case Left(conflict: SetupClientFailure.ExpectedPositionConflict) =>
        client.refresh().foreach {
          case Right(refreshed) => finish(Some(refreshed), Some(conflict))
          case Left(failure) => finish(None, Some(failure))
        }
      case Left(failure) => finish(None, Some(failure))
    }

  private def debugToolbar(restart: () => Unit): dom.Element = {
    val toolbar = element("div", "debug-toolbar")
    toolbar.appendChild(textElement(
      "span",
      "debug-label",
      "Debug tools — not available in production"
    ))
    val button =
      dom.document.createElement("button").asInstanceOf[dom.html.Button]
    button.className = "restart"
    button.textContent = "Restart debug stream"
    button.setAttribute(
      "aria-label",
      "Restart local debug stream from designated initial state"
    )
    button.onclick = _ => restart()
    toolbar.appendChild(button)
    toolbar
  }

  private def status(
      projection: SetupProjection,
      failure: Option[SetupClientFailure]
  ): dom.Element = {
    val node = element("div", "status")
    failure match {
      case Some(error) =>
        node.textContent = s"Client error: ${error.message}"
      case None =>
        projection.activePlayer match {
          case Some(player) =>
            node.appendChild(playerReference(
              SetupViewModel.player(projection, player)
            ))
            node.appendChild(dom.document.createTextNode(
              ": choose any highlighted site for your pawn."
            ))
          case None =>
            node.textContent =
              "Setup complete. Displayed state is accepted-event derived."
        }
    }
    node
  }

  private def participantPanel(view: SetupViewModel): dom.Element = {
    val panel = element("section", "panel")
    panel.appendChild(textElement("h2", "", "Ordered participants"))
    val list = element("ol", "participants")
    view.players.foreach { player =>
      val item = dom.document.createElement("li")
      item.appendChild(playerReference(PlayerDisplay(
        player.id,
        player.text,
        player.color
      )))
      if (player.placed)
        item.appendChild(dom.document.createTextNode(" · placed"))
      if (player.active) item.classList.add("participant-active")
      list.appendChild(item)
    }
    panel.appendChild(list)
    panel
  }

  private def boardPanel(
      client: SetupClient,
      projection: SetupProjection,
      view: SetupViewModel,
      finish: Either[SetupClientFailure, AcceptedSetupUpdate] => Unit
  ): dom.Element = {
    val panel = element("section", "panel world")
    val heading = textElement("h2", "", view.worldTitle)
    heading.id = "world-title"
    panel.setAttribute("aria-labelledby", "world-title")
    panel.appendChild(heading)
    val columns = element("div", "regions")
    view.regions.foreach { regionView =>
      val region = element("section", "region")
      region.setAttribute("aria-label", regionView.name)
      region.appendChild(textElement("h3", "region-label", regionView.name))
      val sites = element("div", "sites")
      regionView.sites.foreach(site =>
        sites.appendChild(siteButton(client, projection, view, site, finish))
      )
      region.appendChild(sites)
      columns.appendChild(region)
    }
    panel.appendChild(columns)
    panel
  }

  private def siteButton(
      client: SetupClient,
      projection: SetupProjection,
      view: SetupViewModel,
      site: SiteDisplay,
      finish: Either[SetupClientFailure, AcceptedSetupUpdate] => Unit
  ): dom.html.Button = {
    val button =
      dom.document.createElement("button").asInstanceOf[dom.html.Button]
    button.className = "site"
    button.disabled = !projection.legalPlacements.contains(site.id)
    val pawns = view.placements.filter(_.siteId == site.id)
    button.appendChild(textElement("span", "site-name", site.label))
    pawns.foreach { pawn =>
      val marker = element("span", "pawn")
      marker.appendChild(dom.document.createTextNode("● "))
      marker.appendChild(playerReference(
        SetupViewModel.player(projection, pawn.playerId)
      ))
      button.appendChild(marker)
    }
    button.onclick = _ => {
      client
        .submit(
          projection.nextSequence,
          SetupClientCommand.PlacePawn(site.id)
        )
        .foreach(finish)
    }
    button
  }

  private def eventPanel(
      events: Vector[RecordedEvent[SetupEvent]],
      projection: SetupProjection
  ): dom.Element = {
    val panel = element("section", "panel")
    panel.setAttribute("style", "margin-top: 18px")
    panel.appendChild(textElement("h2", "", "Accepted event stream"))
    val list = element("ol", "events")
    events.foreach { record =>
      val item = dom.document.createElement("li")
      record.event match {
        case _: SetupEvent.SetupStarted =>
          item.textContent = "SetupStarted(participants, catalog, 8 sites)"
        case SetupEvent.PawnPlaced(player, site) =>
          item.appendChild(dom.document.createTextNode("PawnPlaced("))
          item.appendChild(playerReference(
            SetupViewModel.player(projection, player)
          ))
          item.appendChild(dom.document.createTextNode(s", ${site.value})"))
        case SetupEvent.SetupCompleted =>
          item.textContent = "SetupCompleted"
      }
      list.appendChild(item)
    }
    panel.appendChild(list)
    val replay = element("p", "replay")
    replay.textContent =
      s"Accepted-event projection: ${stateName(projection.state)}"
    panel.appendChild(replay)
    panel
  }

  private def stateName(state: SetupState): String =
    state match {
      case SetupState.NotStarted => "NotStarted"
      case value: SetupState.InProgress =>
        s"InProgress (${value.pawnPlacements.size}/${value.participants.size} pawns)"
      case value: SetupState.Completed =>
        s"Completed (${value.pawnPlacements.size} pawns)"
    }

  private def playerReference(player: PlayerDisplay): dom.Element = {
    val node = textElement(
      "span",
      s"player-ref ${player.color.cssClass}",
      player.label
    )
    node.setAttribute("data-player-id", player.id.value)
    node
  }

  private def element(tag: String, className: String): dom.Element = {
    val node = dom.document.createElement(tag)
    if (className.nonEmpty) node.setAttribute("class", className)
    node
  }

  private def textElement(
      tag: String,
      className: String,
      text: String
  ): dom.Element = {
    val node = element(tag, className)
    node.textContent = text
    node
  }
}
