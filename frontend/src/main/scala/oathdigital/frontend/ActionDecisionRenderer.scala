package oathdigital.frontend
import oathdigital.protocol.{GameIntent => GameCommand, _}; import org.scalajs.dom
import ServerUiSupport._
private[frontend] object ActionDecisionRenderer {
 def status(value: GameProjection, ui: ServerUiView): dom.Element = {
   import ui._
   val node = element("div", "status")
   val presentation = viewerPresentation(value, currentPlayerId)
   presentation.procedureStatus match {
     case Some(message) => node.textContent = message
     case None => presentation.waitingForPlayerId match {
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
     case None if value.phase == "act-action-selection" =>
       node.textContent = "Act phase — choose your first normal action."
     case None if value.phase == "wake" =>
       node.textContent = "Wake phase — take available wealth or end Wake."
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
       value.activeParticipantId match {
         case Some(playerId) =>
           node.appendChild(playerReference(value, playerId))
         case None => node.appendChild(dom.document.createTextNode("none"))
       }
     }
   }
   node
 }
 def actionsPanel(value: GameProjection, presentation: ViewerPresentation,
     ui: ServerUiView): dom.Element = {
   import ui._
   val panel = element("section", "panel wake-actions")
   panel.appendChild(text("h2", "", "Available actions"))
   panel.appendChild(status(value, ui))
   value.oathkeeper.flatMap(_.winnerPlayerId).foreach { winner =>
     val victory = value.oathkeeper.flatMap(_.winnerVictoryKind)
       .getOrElse("winner").replace('-', ' ')
     panel.appendChild(winnerBanner(value, winner, victory))
   }
   // Drawn cards wait in the temporary hand: starting advisers, a Search's
   // draw, a Vision in flight. The projection drops the preview once a
   // decision offers the same cards, so nothing here has to know which
   // action put them there.
   if (value.temporaryHandPreview.nonEmpty) {
     val preview = element("section", "temporary-hand-preview")
     preview.appendChild(text("h3", "", "Cards in hand"))
     val cards = element("div", "decision-cards")
     value.temporaryHandPreview.foreach(card =>
       cards.appendChild(CardFace.render(card)))
     preview.appendChild(cards)
     panel.appendChild(preview)
   }
   // The oath and the usurper limit describe the table, so they are drawn
   // with the board; the acting player's resources are on their own board
   // already, and a site's loose wealth is drawn on the site.
   if (value.phase == "wake" && presentation.showGameplayControls) {
     takeWealthActions(value, currentPlayerId).foreach { action =>
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
   }
   if (!value.actionSelectionOpen && presentation.showGameplayControls) {
     currentBoardSelection.flatMap(_.activeAction).foreach { action =>
       panel.appendChild(text("p", "selection-instruction", action.prompt))
       panel.appendChild(text("p", "selection-cardinality",
         cardinalityInstruction(action)))
     }
   }
   if (showActActionControls(value, presentation)) {
     val selection = currentBoardSelection.flatMap(_.activeAction)
     if (currentModifierWorkflow.exists(_.ordering)) {
       val workflow = currentModifierWorkflow.get
       panel.appendChild(text("h2", "", s"Order ${actionLabel(workflow.preview.action)} modifiers"))
       panel.appendChild(text("p", "modifier-instruction", "Choose optional modifiers in " +
         "resolution order. Numbered badges show that order."))
       workflow.selection.candidates.foreach { modifier =>
         val row = element("div", "modifier-option")
         val ordinal = workflow.selection.ordinal(modifier)
         val choose = button(ordinal.fold(modifier.description)(n =>
           s"$n. ${modifier.description}"), "modifier-toggle")
         choose.setAttribute("aria-pressed", ordinal.nonEmpty.toString); choose
           .setAttribute("data-source-key", modifier.sourceKey)
         // The card, then what it modifies. The description stays as the
         // control's accessible name so a reader who cannot see the face
         // still hears which power this is.
         modifier.card.foreach { card =>
           choose.setAttribute("aria-label", ordinal.fold(modifier.description)(
             n => s"$n. ${modifier.description}"))
           choose.textContent = ""
           ordinal.foreach(n => choose.appendChild(
             text("span", "modifier-ordinal-prefix", s"$n.")))
           choose.appendChild(CardFace.render(card))
           choose.appendChild(text("span", "modifier-modifies",
             s"${actionLabel(modifier.modifies.getOrElse(workflow.preview.action))} Modifier"))
         }
         choose.onclick = _ => toggleModifier(modifier)
         choose.onkeydown = event => event.key match {
           case "Enter" | " " => event.preventDefault(); toggleModifier(modifier)
           case "ArrowUp" => event.preventDefault(); moveModifier(modifier, -1)
           case "ArrowDown" => event.preventDefault(); moveModifier(modifier, 1)
           case _ => ()
         }
         row.appendChild(choose)
         ordinal.foreach { number =>
           val badge = text("span", "modifier-ordinal", number.toString); badge
             .setAttribute("aria-label", s"Resolution order $number"); row.appendChild(badge)
           val earlier = button("Earlier", "modifier-earlier")
           earlier.disabled = number == 1; earlier.onclick = _ =>
             moveModifier(modifier, -1); row.appendChild(earlier)
           val later = button("Later", "modifier-later")
           later.disabled = number == workflow.selection.selected.size
           later.onclick = _ => moveModifier(modifier, 1); row.appendChild(later)
         }
         panel.appendChild(row)
       }
       val confirm = button("Confirm modifier order", "modifier-confirm")
       confirm.disabled = !canControl; confirm.onclick = _ => confirmModifiers(); panel.appendChild(confirm)
       val back = button("Back", "modifier-back"); back.onclick = _ => backFromModifiers()
       panel.appendChild(back)
       val cancel = button("Cancel action", "modifier-cancel"); cancel.onclick = _ =>
         cancelModifiers(); panel.appendChild(cancel)
    } else if (currentFacedownAdviserDraft.nonEmpty) {
      panel.appendChild(FacedownAdviserRenderer.render(
        currentFacedownAdviserDraft.get, ui))
     } else if (selection.nonEmpty) {
       val action = selection.get
       panel.appendChild(text("p", "selection-instruction", action.prompt))
       panel.appendChild(text("p", "selection-cardinality",
         cardinalityInstruction(action)))
       if (!action.autoActivate) {
         val cancel = button("Cancel", "cancel-board-selection")
         cancel.onclick = _ => cancelTargetAction()
         panel.appendChild(cancel)
         currentModifierWorkflow.foreach { workflow =>
           val back = button(if (workflow.hadModifierStage) "Back to modifiers"
             else "Back to actions", "back-board-selection")
           back.onclick = _ => backFromTargets(); panel.appendChild(back)
         }
       }
       if (action.explicitConfirm) {
         val confirm = button("Confirm selection", "confirm-board-selection")
         confirm.disabled = !canControl ||
           !currentBoardSelection.exists(_.canConfirm)
         confirm.onclick = _ => currentBoardSelection.flatMap(_.confirm)
           .foreach(handleSelection)
         panel.appendChild(confirm)
       }
     } else {
       val groups = new ActionSections; value.legalSearchSources.foreach { source =>
         val label = source.kind match {
           case "world" => s"Search world deck (${source.supplyCost} Supply)"
           case _ => s"Search ${source.region.getOrElse("regional")} discard " +
             s"(${source.supplyCost} Supply)"
         }
         val search = button(label, "act-action search-action")
         search.disabled = !canControl || !presentation.showGameplayControls
         val key = source.kind match {
           case "world" => "search:world"
           case _ => s"search:regional-discard:${source.region.getOrElse("")}"
         }
         search.onclick = _ => submitCommand(GameCommand.StartWalker(
           "search", Vector.empty, Vector(WalkerStartArgWire("button", key))))
         groups.appendKind("search", search)
       }
       if (value.legalControls.contains("beginRecover")) {
         val recover = button("Recover (1 Supply)", "act-action recover-action")
         recover.disabled = !canControl
         recover.onclick = _ => submitCommand(GameCommand.StartWalker("recover", Vector.empty))
         groups.appendKind("recover", recover)
       }
       if (value.legalControls.contains("beginForge")) {
         val forge = button("Forge (1 Supply)", "act-action forge-action")
         forge.disabled = !canControl
         forge.onclick = _ => submitCommand(
           GameCommand.StartWalker("forge", Vector.empty))
         groups.appendKind("forge", forge)
       }
       EconomyControls.render(value, canControl, groups, submitCommand)
       BannerControls.render(value, canControl, groups, submitCommand)
       NegotiationControls.render(value, canControl, groups, submitCommand)
       CampaignControls.render(value, canControl, groups, submitCommand)
       value.minorActions.foreach { minor =>
         if (facedownAdviserLaunchCount(minor) == 1) {
           val play = button("Play facedown adviser", "minor-adviser-launch")
           play.disabled = !canControl
           play.onclick = _ => beginTargetedMajorAction("play-facedown-adviser")
           groups.appendKind("facedown-adviser", play)
         }
         if (minor.canPeekSiteRelics) {
           val peek = button("Peek at relics at your site", "minor-peek-relics")
           peek.disabled = !canControl
           peek.onclick = _ => submitCommand(GameCommand.PeekSiteRelics)
           groups.appendKind("peek-site-relics", peek)
         }
         minor.facedownRelics.foreach { relic =>
           val reveal = button(s"Reveal ${relic.name}", "minor-reveal-relic")
           reveal.disabled = !canControl
           reveal.onclick = _ => submitCommand(GameCommand.RevealOwnedRelic(relic.cardId))
           groups.appendKind("reveal-owned-relic", reveal)
         }
         Vector(true -> minor.maxBoardToSite, false -> minor.maxSiteToBoard)
           .filter(_._2 > 0).foreach { case (toSite, maximum) =>
             val label = dom.document.createElement("label").asInstanceOf[dom.html.Label]
             label.textContent = if (toSite) "Warbands board to site "
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
       value.boardTargetActions.filterNot(_.autoActivate).foreach { action =>
         val control = button(actionLabel(action.actionKind),
           s"act-action target-action action-${action.actionKind}")
         control.disabled = !canControl ||
           !presentation.showGameplayControls ||
           (action.candidates.isEmpty && action.minimum > 0)
         control.onclick = _ => {
           if (action.minimum == 0 && action.maximum == 0)
             commandForSelection(action, Vector.empty, currentPlayerId).foreach(submitCommand)
           else {
             if (ModifierWorkflow.targeted(action.actionKind).nonEmpty)
               beginTargetedMajorAction(action.actionKind)
             else {
               currentBoardSelection = currentBoardSelection.map(
                 _.activate(action.actionKind))
               rerender()
             }
           }
         }
         groups.appendKind(action.actionKind, control)
       }
       PhasePowerButtons.appendTo(value, canControl, groups, submitCommand)
       groups.appendTo(panel)
       panel.appendChild(text("p", "informational",
         "Other normal action families are not yet implemented."))
       if (value.legalControls.contains("beginRest")) {
         // What Rest returns before the track's ceiling takes its cut, so
         // the number doubles as the Supply this Act may still spend for
         // free. A band that returns nothing says nothing.
         val regained = value.restSupplyGain.filter(_ > 0)
           .fold("")(gain => s" (+$gain Supply)")
         val rest = button(s"End Act and Rest$regained", "rest-action")
         rest.disabled = !canControl
         rest.onclick = _ => submitCommand(GameCommand.BeginRest)
         panel.appendChild(rest)
       }
     }
   }
   WalkerPanelSupport.renderRecoverPanel(value, presentation, canControl,
     panel, ui)
   WalkerPanelSupport.renderChooseOnePanel(value, presentation, canControl,
     panel, ui)
   WalkerPanelSupport.renderPartitionPanel(value, presentation, canControl,
     panel, ui)
   DistributePanelRenderer.render(value, presentation, canControl, panel, ui)
   NegotiationDealPanel.render(value, presentation, canControl, panel, ui)
   WalkerSelectionPanels.render(value, presentation, canControl, panel, ui)
   WalkerPanelSupport.renderWaitingNotice(value, panel)
   CampaignResultPanel.render(value, panel)
   if (value.phase == "rest" && presentation.showGameplayControls) {
     PhasePowerButtons.render(value, canControl, panel, submitCommand)
     if (PhasePowerButtons.showsFinishRest(value)) {
       panel.appendChild(text("p", "informational",
         "Finish Rest to return card resources, reveal secrets, refresh " +
           "Supply, and wake the next player."))
       val finish = button("Finish Rest", "rest-action finish-rest")
       finish.disabled = !canControl
       finish.onclick = _ => submitCommand(GameCommand.FinishRest)
       panel.appendChild(finish)
     }
   }
   panel
 }
}
