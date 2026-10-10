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
     // done, so Wake and the Act phase print no instruction of their own.
     case None if value.phase == "act-action-selection" || value.phase == "wake" => ()
     case None if value.phase == "rest" =>
       node.textContent = "Rest phase — finish Rest when ready."
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
   // The oath and the usurper limit describe the table, so they are drawn
   // with the board; the acting player's resources are on their own board
   // already, and a site's loose wealth is drawn on the site.
   if value.phase == "wake" && presentation.showGameplayControls then
     takeWealthActions(value, presentation.playerId).foreach { action =>
       val control = button(action.label, "wake-action")
       control.disabled = !canControl
       control.onclick = _ => submitCommand(action.command)
       panel.appendChild(control)
     }
     PhasePowerButtons.render(value, canControl, panel, submitCommand)
     val end = button("End Wake", "wake-action")
     end.disabled = !canControl ||
       !value.legalControls.contains("endWake")
     end.onclick = _ => submitCommand(GameCommand.EndWake)
     panel.appendChild(end)
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
       if minor.canPeekSiteRelics then
         val peek = button("Peek at relics at your site", "minor-peek-relics")
         peek.disabled = !canControl
         peek.onclick = _ => submitCommand(GameCommand.PeekSiteRelics)
         groups.appendKind("peek-site-relics", peek)
       minor.facedownRelics.foreach { relic =>
         val reveal = button(s"Reveal ${relic.name}", "minor-reveal-relic")
         reveal.disabled = !canControl
         reveal.onclick = _ => submitCommand(GameCommand.RevealOwnedRelic(relic.cardId))
         groups.appendKind("reveal-owned-relic", reveal)
       }
       Vector(true -> minor.maxBoardToSite, false -> minor.maxSiteToBoard)
         .filter(_._2 > 0).foreach { case (toSite, maximum) =>
           val label = dom.document.createElement("label").asInstanceOf[dom.html.Label]
           label.textContent = if toSite then "Warbands board to site "
             else "Warbands site to board "
           val amount = dom.document.createElement("input").asInstanceOf[dom.html.Input]
           amount.`type` = "number"; amount.min = "1"; amount.max = maximum.toString
           amount.value = "1"; amount.setAttribute("aria-label", label.textContent)
           label.appendChild(amount)
           val move = button("Move warbands", "minor-move-warband")
           move.disabled = !canControl
           move.onclick = _ => submitCommand(GameCommand.MoveWarbands(
             toSite, amount.value.toInt))
           // The count and its button are one option, so they share a row.
           val option = dom.document.createElement("span")
           option.appendChild(label); option.appendChild(move)
           groups.appendKind("move-warbands", option)
         }
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
   if value.phase == "rest" && presentation.showGameplayControls then
     PhasePowerButtons.render(value, canControl, panel, submitCommand)
     if PhasePowerButtons.showsFinishRest(value) then
       panel.appendChild(text("p", "informational",
         "Finish Rest to return card resources, reveal secrets, refresh " +
           "Supply, and wake the next player."))
       val finish = button("Finish Rest", "rest-action finish-rest")
       finish.disabled = !canControl
       finish.onclick = _ => submitCommand(GameCommand.FinishRest)
       panel.appendChild(finish)
   panel
