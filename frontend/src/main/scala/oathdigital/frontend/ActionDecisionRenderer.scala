package oathdigital.frontend
import oathdigital.protocol.GameIntent => GameCommand; import org.scalajs.dom
import ServerUiSupport._
private[frontend] object ActionDecisionRenderer:
 def status(value: GameProjection, presentation: ViewerPresentation): dom.Element =
   val node = element("div", "status")
   presentation.procedureStatus match
     case Some(message) => node.textContent = message
     case None => presentation.waitingForPlayerId match
     case Some(playerId) =>
       node.appendChild(dom.document.createTextNode("Waiting for "))
       node.appendChild(playerReference(value, playerId))
     case None if value.walkerDecision.nonEmpty =>
       // A parked walker Decide belonging to this viewer projects
       // waitingForPlayerId = None (see the ServerUiSupport.viewerPresentation
       // comment), so this is genuinely this viewer's own turn. Falling
       // through to the generic phase text or the activeParticipantId
       // fallback below would show the wrong or no turn indicator at all.
       node.textContent = value.walkerDecision.flatMap(_.query).flatMap(_.heading)
         .getOrElse("Your decision.")
     // The Game Log names the phase and the controls below say what can be
     // done, so the Act phase prints no instruction of its own.
     case None if value.phase == "act-action-selection" => ()
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
   if showActActionControls(value, presentation) then
     val groups = new ActionSections
     if value.legalControls.contains("beginSearch") then
       val search = button("Search", "act-action search-action")
       search.disabled = !canControl || !presentation.showGameplayControls
       search.onclick = _ => submitCommand(
         GameCommand.StartWalker("search"))
       groups.appendKind("search", search)
     if value.legalControls.contains("beginRecover") then
       val recover = button("Recover (1 Supply)", "act-action recover-action")
       recover.disabled = !canControl
       recover.onclick = _ => submitCommand(GameCommand.StartWalker("recover"))
       groups.appendKind("recover", recover)
     if value.legalControls.contains("beginForge") then
       val forge = button("Forge (1 Supply)", "act-action forge-action")
       forge.disabled = !canControl
       forge.onclick = _ => submitCommand(
         GameCommand.StartWalker("forge"))
       groups.appendKind("forge", forge)
     if value.legalControls.contains("beginTravel") then
       val travel = button("Travel", "act-action travel-action")
       travel.disabled = !canControl
       travel.onclick = _ => submitCommand(
         GameCommand.StartWalker("travel"))
       groups.appendKind("travel", travel)
     EconomyControls.render(value, canControl, groups, submitCommand)
     BannerControls.render(value, canControl, groups, submitCommand)
     NegotiationControls.render(value, canControl, groups, submitCommand)
     CampaignControls.render(value, canControl, groups, submitCommand)
     value.minorActions.foreach { minor =>
       if facedownAdviserLaunchCount(minor) == 1 then
         val play = button("Play facedown adviser", "minor-adviser-launch")
         play.disabled = !canControl
         play.onclick = _ => submitCommand(
           GameCommand.StartWalker("play-facedown-adviser"))
         groups.appendKind("facedown-adviser", play)
     }
     // The legacy minor actions are walker procedures (walker-driven game
     // loop design, slice 3): a button starts one, and the walk asks the
     // rest. Slice 4's Act menu replaces these buttons.
     Vector(
       ("peekSiteRelics", "Peek at relics at your site", "minor-peek-relics",
         "peek-site-relics"),
       ("revealOwnedRelic", "Reveal a relic", "minor-reveal-relic",
         "reveal-owned-relic"),
       ("moveWarbands", "Move warbands", "minor-move-warband",
         "move-warbands"))
       .filter((control, _, _, _) => value.legalControls.contains(control))
       .foreach { (_, label, style, action) =>
         val start = button(label, style)
         start.disabled = !canControl
         start.onclick = _ => submitCommand(GameCommand.StartWalker(action))
         groups.appendKind(action, start)
       }
     PhasePowerButtons.appendTo(value, canControl, groups, submitCommand)
     groups.appendTo(panel)
     if value.legalControls.contains("beginRest") then
       // What Rest returns before the track's ceiling takes its cut, so
       // the number doubles as the Supply this Act may still spend for
       // free. A band that returns nothing says nothing.
       val regained = value.restSupplyGain.filter(_ > 0)
         .fold("")(gain => s" (+$gain Supply)")
       val rest = button(s"End Act and Rest$regained", "rest-action")
       rest.disabled = !canControl
       rest.onclick = _ => submitCommand(GameCommand.BeginRest)
       panel.appendChild(rest)
   ParkedDecision.render(value, presentation, routed, canControl, panel, drafts,
     controls)
   panel
