package oathdigital.frontend
import org.scalajs.dom
import ServerUiSupport._
private[frontend] object ActionDecisionRenderer:
 def status(value: GameProjection, presentation: ViewerPresentation): dom.Element =
   val node = element("div", "status")
   presentation.waitingForPlayerId match
     case Some(playerId) =>
       node.appendChild(dom.document.createTextNode("Waiting for "))
       node.appendChild(playerReference(value, playerId))
       // The question a parked decision asks rides on the same line, so the
       // pane names the player waited on once.
       value.walkerWaiting.filter(_.playerId == playerId).flatMap(_.heading)
         .foreach(heading =>
           node.appendChild(dom.document.createTextNode(s": $heading")))
     case None if value.walkerDecision.nonEmpty =>
       // A parked walker Decide belonging to this viewer projects
       // waitingForPlayerId = None (see the ServerUiSupport.viewerPresentation
       // comment), so this is genuinely this viewer's own turn. Falling
       // through to the generic phase text or the activeParticipantId
       // fallback below would show the wrong or no turn indicator at all.
       node.textContent = value.walkerDecision.flatMap(_.query).flatMap(_.heading)
         .getOrElse("Your decision.")
     case None if value.phase == "game-over" =>
       node.textContent = value.oathkeeper.flatMap(_.winnerPlayerId)
         .fold("Game over.") { winner =>
           val reason = value.oathkeeper.flatMap(_.winnerVictoryKind)
             .map(_.replace('-', ' ')).getOrElse("winner")
           s"Game over — $winner wins ($reason)."
         }
     case None =>
       node.appendChild(dom.document.createTextNode(
         s"${value.phase}; active participant: "
       ))
       value.activeParticipantId match
         case Some(playerId) =>
           node.appendChild(playerReference(value, playerId))
         case None => node.appendChild(dom.document.createTextNode("none"))
   node
 def actionsPanel(value: GameProjection, presentation: ViewerPresentation,
     routed: ParkedDecision.Routed, canControl: Boolean,
     drafts: SessionDrafts, controls: TableControls): dom.Element =
   import controls._
   val panel = element("section", "panel wake-actions")
   panel.appendChild(text("h2", "", "Available actions"))
   panel.appendChild(status(value, presentation))
   // Before the winner banner and every phase's controls: the steps to take
   // back or restore belong to whatever the pane shows below them.
   StepControls.render(value, canControl, submitCommand).foreach(panel.appendChild)
   value.oathkeeper.flatMap(_.winnerPlayerId).foreach { winner =>
     val victory = value.oathkeeper.flatMap(_.winnerVictoryKind)
       .getOrElse("winner").replace('-', ' ')
     panel.appendChild(winnerBanner(value, winner, victory))
   }
   // Drawn cards wait in the temporary hand: starting advisers, a Search's
   // draw, a Vision in flight. A card a placement is about rides along, even
   // when it is played from the board. The projection drops every card a
   // decision offers as an option, so nothing here has to know which action
   // put them there or which decision is asking.
   if value.temporaryHandPreview.nonEmpty then
     val preview = element("section", "temporary-hand-preview")
     preview.appendChild(text("h3", "", "Cards in hand"))
     val cards = element("div", "decision-cards")
     value.temporaryHandPreview.foreach(card =>
       cards.appendChild(CardFace.render(card)))
     preview.appendChild(cards)
     panel.appendChild(preview)
   ParkedDecision.render(value, presentation, routed, canControl, panel, drafts,
     controls)
   panel
