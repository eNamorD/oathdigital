package oathdigital.frontend

import org.scalajs.dom
import oathdigital.engine.RecordedEvent
import oathdigital.model.SiteId
import oathdigital.setup.{SetupEvent, SetupState}

object Main {
  def main(args: Array[String]): Unit = {
    val mount = Option(dom.document.getElementById("app")).getOrElse {
      throw new IllegalStateException("missing #app mount point")
    }
    val adapter = new HrfDomAdapter(mount)
    val session = SetupSession.demo()

    def render(): Unit = adapter.replace { root =>
      root.innerHTML =
        """<div class="eyebrow">N2 · HRF UI reuse feasibility</div>
          |<h1>Oath Digital setup</h1>
          |<p class="lede">A minimal Scala.js view over Oath's existing
          |authoritative-domain-event setup engine. Artwork is deliberately
          |replaced by semantic cards.</p>""".stripMargin

      root.appendChild(status(session))
      val grid = element("div", "grid")
      grid.appendChild(participantPanel(session))
      grid.appendChild(boardPanel(session, () => render()))
      root.appendChild(grid)
      root.appendChild(eventPanel(session.events, session.replayedState))
    }

    render()
  }

  private def status(session: SetupSession): dom.Element = {
    val node = element("div", "status")
    val message = session.activePlayer match {
      case Some(player) =>
        s"${player.value}: choose any highlighted site for your pawn."
      case None => "Setup complete. The replayed state matches the live state."
    }
    node.textContent = message
    node
  }

  private def participantPanel(session: SetupSession): dom.Element = {
    val panel = element("section", "panel")
    panel.innerHTML = "<h2>Ordered participants</h2>"
    val list = element("ol", "participants")
    val placed = placements(session.state).map(_.playerId).toSet
    session.participants.foreach { participant =>
      val item = dom.document.createElement("li")
      val suffix = if (placed.contains(participant.playerId)) " · placed" else ""
      item.textContent =
        s"${participant.playerId.value} / ${participant.lineageId.value}$suffix"
      if (session.activePlayer.contains(participant.playerId))
        item.setAttribute("class", "participant-active")
      list.appendChild(item)
    }
    panel.appendChild(list)
    panel
  }

  private def boardPanel(
      session: SetupSession,
      rerender: () => Unit
  ): dom.Element = {
    val panel = element("section", "panel")
    panel.innerHTML = "<h2>Eight-site setup layout</h2>"
    val grouped = Vector(
      "Cradle" -> session.orderedSites.take(2),
      "Provinces" -> session.orderedSites.slice(2, 5),
      "Hinterland" -> session.orderedSites.slice(5, 8)
    )
    grouped.foreach { case (name, sites) =>
      val region = element("div", "region")
      val label = element("div", "region-label")
      label.textContent = name
      region.appendChild(label)
      val row = element("div", "sites")
      sites.foreach(site => row.appendChild(siteButton(session, site, rerender)))
      region.appendChild(row)
      panel.appendChild(region)
    }
    panel
  }

  private def siteButton(
      session: SetupSession,
      siteId: SiteId,
      rerender: () => Unit
  ): dom.html.Button = {
    val button =
      dom.document.createElement("button").asInstanceOf[dom.html.Button]
    button.className = "site"
    button.disabled = !session.legalPlacements.contains(siteId)
    val definition = DemoCatalog.sites.find(_.id == siteId).get
    val pawns = placements(session.state).filter(_.siteId == siteId)
    button.innerHTML =
      s"""<span class="site-name">${definition.metadata.name}</span>""" +
        pawns
          .map(p => s"""<span class="pawn">● ${p.playerId.value}</span>""")
          .mkString
    button.onclick = _ => {
      session.place(siteId)
      rerender()
    }
    button
  }

  private def eventPanel(
      events: Vector[RecordedEvent[SetupEvent]],
      replayed: Either[String, SetupState]
  ): dom.Element = {
    val panel = element("section", "panel")
    panel.setAttribute("style", "margin-top: 18px")
    panel.innerHTML = "<h2>Authoritative event stream</h2>"
    val list = element("ol", "events")
    events.foreach { record =>
      val item = dom.document.createElement("li")
      item.textContent = eventText(record.event)
      list.appendChild(item)
    }
    panel.appendChild(list)
    val replay = element("p", "replay")
    replay.textContent = replayed.fold(
      error => s"Replay failed: $error",
      state => s"Browser-memory replay: ${stateName(state)}"
    )
    panel.appendChild(replay)
    panel
  }

  private def placements(state: SetupState) =
    state match {
      case value: SetupState.InProgress => value.pawnPlacements
      case value: SetupState.Completed => value.pawnPlacements
      case SetupState.NotStarted => Vector.empty
    }

  private def eventText(event: SetupEvent): String =
    event match {
      case _: SetupEvent.SetupStarted => "SetupStarted(participants, catalog, 8 sites)"
      case SetupEvent.PawnPlaced(player, site) =>
        s"PawnPlaced(${player.value}, ${site.value})"
      case SetupEvent.SetupCompleted => "SetupCompleted"
    }

  private def stateName(state: SetupState): String =
    state match {
      case SetupState.NotStarted => "NotStarted"
      case value: SetupState.InProgress =>
        s"InProgress (${value.pawnPlacements.size}/${value.participants.size} pawns)"
      case value: SetupState.Completed =>
        s"Completed (${value.pawnPlacements.size} pawns)"
    }

  private def element(tag: String, className: String): dom.Element = {
    val node = dom.document.createElement(tag)
    node.setAttribute("class", className)
    node
  }
}
