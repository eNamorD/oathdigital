package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport._

private[frontend] object DevelopmentRenderer {
 def controls(ui: ServerUiView): dom.Element = {
   import ui._
   val bar = element("div", "debug-toolbar")
   bar.appendChild(text(
     "span",
     "debug-label",
     s"Development-only active-player view: $currentPlayerId"
   ))
   val input =
     dom.document.createElement("input").asInstanceOf[dom.html.Input]
   input.value = currentGameId
   input.setAttribute("aria-label", "Existing game ID")
   bar.appendChild(input)
   displayedProjection.toVector.flatMap(_.players).foreach { player =>
     val selector = button(player.displayName,
       s"player-selector ${PlayerColorCss.of(player.color)}")
     selector.setAttribute("aria-pressed",
       (player.playerId == currentPlayerId).toString)
     selector.setAttribute("data-player-id", player.playerId)
     selector.onclick = _ => loadSession(input.value, player.playerId)
     bar.appendChild(selector)
   }
   val load = button("Load existing game", "load-game")
   load.onclick = _ => loadSession(input.value, currentPlayerId)
   bar.appendChild(load)
   sessionCoordinator.connectionState match {
     case ServerConnectionState.Disconnected(_) =>
       val retry = button("Reconnect", "reconnectSession")
       retry.setAttribute(
         "aria-label",
         "Reconnect and fetch authoritative current state"
       )
       retry.onclick = _ => reconnectSession()
       bar.appendChild(retry)
     case _ => ()
   }
   val fresh = button("New game", "restart")
   fresh.setAttribute(
     "aria-label",
     "Return to the start page to create another game"
   )
   fresh.onclick = _ => createGame()
   bar.appendChild(fresh)
   bar
 }

 def rawEventLog(rawEvents: Vector[RawEvent]): dom.Element = {
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

}
