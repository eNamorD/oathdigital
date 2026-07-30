package oathdigital.frontend

import org.scalajs.dom
import oathdigital.engine.RecordedEvent
import oathdigital.model.{PlayerId, SiteId}
import oathdigital.setup.{SetupEvent, SetupState}

object Main {
  def main(args: Array[String]): Unit = {
    val mount = Option(dom.document.getElementById("app")).getOrElse {
      throw new IllegalStateException("missing #app mount point")
    }
    val adapter = new HrfDomAdapter(mount)
    val client = LocalDebugSetupClient.demo()
    var lastFailure = Option.empty[SetupClientFailure]

    def render(): Unit = adapter.replace { root =>
      val projection = client.projection
      val view = SetupViewModel.from(projection)
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

      root.appendChild(debugToolbar(() => {
        lastFailure = client.restartDebug().left.toOption
        render()
      }))
      root.appendChild(status(projection, lastFailure))
      root.appendChild(participantPanel(view))
      root.appendChild(boardPanel(client, projection, view, failure => {
        lastFailure = failure
        render()
      }))
      root.appendChild(eventPanel(projection.acceptedEvents, projection.state))
    }

    render()
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
            node.appendChild(playerReference(player))
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
      item.appendChild(playerReference(player.id))
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
      finish: Option[SetupClientFailure] => Unit
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
      siteId: SiteId,
      finish: Option[SetupClientFailure] => Unit
  ): dom.html.Button = {
    val button =
      dom.document.createElement("button").asInstanceOf[dom.html.Button]
    button.className = "site"
    button.disabled = !projection.legalPlacements.contains(siteId)
    val definition = DemoCatalog.sites.find(_.id == siteId).get
    val pawns = view.placements.filter(_.siteId == siteId)
    button.appendChild(textElement("span", "site-name", definition.name))
    pawns.foreach { pawn =>
      val marker = element("span", "pawn")
      marker.appendChild(dom.document.createTextNode("● "))
      marker.appendChild(playerReference(pawn.playerId))
      button.appendChild(marker)
    }
    button.onclick = _ => {
      val result = client.submit(
        projection.expectedPosition,
        SetupClientCommand.PlacePawn(siteId)
      )
      finish(result.left.toOption)
    }
    button
  }

  private def eventPanel(
      events: Vector[RecordedEvent[SetupEvent]],
      state: SetupState
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
          item.appendChild(playerReference(player))
          item.appendChild(dom.document.createTextNode(s", ${site.value})"))
        case SetupEvent.SetupCompleted =>
          item.textContent = "SetupCompleted"
      }
      list.appendChild(item)
    }
    panel.appendChild(list)
    val replay = element("p", "replay")
    replay.textContent = s"Accepted-event projection: ${stateName(state)}"
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

  private def playerReference(playerId: PlayerId): dom.Element = {
    val node = textElement(
      "span",
      s"player-ref ${SetupViewModel.colorClass(playerId)}",
      playerId.value
    )
    node.setAttribute("data-player-name", playerId.value)
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
