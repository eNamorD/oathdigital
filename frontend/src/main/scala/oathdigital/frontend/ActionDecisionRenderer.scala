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
     case None if value.phase == "awaiting-adviser" =>
       node.textContent = "Setup — choose your starting adviser."
     case None if value.phase == "awaiting-pawn" =>
       node.textContent = "Setup — choose your pawn's starting site."
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
   if (value.privateAdviserPreview.nonEmpty) {
     val preview = element("section", "adviser-preview")
     preview.appendChild(text("h3", "", "Your adviser options"))
     preview.appendChild(text("p", "decision-instruction",
       "Preview only. Place your pawn, then choose one to keep."))
     value.privateAdviserPreview.foreach(card => preview.appendChild(cardDetailsPopover(card)))
     panel.appendChild(preview)
   }
   value.oathkeeper.foreach { oath =>
     val holder = oath.holderPlayerId.getOrElse("unheld")
     val limiter = if (oath.usurperLimited) " · Usurper locked until round 4" else ""
     val winner = oath.winnerPlayerId.fold("")(id => s" · Winner: $id")
     panel.appendChild(text("p", "oathkeeper-status",
       s"Oath of Supremacy · ${oath.side.capitalize}: $holder$limiter$winner"))
   }
   value.activePlayerResources.foreach { resources =>
     val summary = text(
       "p",
       "resources",
       s"Favor ${resources.favor} · Secrets ${resources.faceUpSecrets}/${resources.totalSecrets} · " +
         s"Supply ${resources.supply}"
     )
     summary.setAttribute("title", secretSummaryLabel(resources.faceUpSecrets,
       resources.totalSecrets, resources.faceDownSecrets, resources.committedSecrets))
     summary.setAttribute("aria-label", summary.textContent + ". " +
       summary.getAttribute("title"))
     panel.appendChild(summary)
   }
   value.currentSiteResources.foreach { resources =>
     panel.appendChild(text(
       "p",
       "site-resources",
       s"${siteLabel(value, resources.siteId)} loose wealth: " +
         s"${resources.favor} favor · ${resources.secrets} secrets"
     ))
   }
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
       if (action.maximum > 1 || action.explicitConfirm) {
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
             label.appendChild(amount); groups.appendKind("move-warbands", label)
             val move = button("Move warbands", "minor-move-warband")
             move.disabled = !canControl
             move.onclick = _ => submitCommand(GameCommand.MoveWarbands(
               toSite, amount.value.toInt))
             groups.appendKind("move-warbands", move)
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
       groups.appendTo(panel)
       panel.appendChild(text("p", "informational",
         "Other normal action families are not yet implemented."))
       PhasePowerButtons.render(value, canControl, panel, submitCommand)
       if (value.legalControls.contains("beginRest")) {
         val rest = button("End Act and Rest", "rest-action")
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
   value.pendingCardDecision.filter(_ => presentation.showGameplayControls)
     .foreach(decision => panel.appendChild(cardDecision(value, decision, ui)))
   panel
 }
 def cardDecision(
     value: GameProjection,
     decision: PendingCardDecision,
     ui: ServerUiView
 ): dom.Element = {
   import ui._
   val shell = element("section", "card-decision")
   shell.setAttribute("aria-labelledby", "card-decision-title")
   shell.setAttribute("data-decision-kind", decision.kind); shell.appendChild(text("h2", "", decision.prompt))
   shell.lastChild.asInstanceOf[dom.Element].id = "card-decision-title"
   val state = currentCardDecision.filter(_.decisionId == decision.decisionId)
     .getOrElse(CardDecisionState.initial(decision))
   def update(next: CardDecisionState): Unit = {
     currentCardDecision = Some(next)
     rerender()
   }
   def cardNode(card: CardDetails, zone: String): dom.Element = {
     val node = element("article", "decision-card")
     node.setAttribute("tabindex", "0")
     node.setAttribute("draggable", "true")
     node.setAttribute("data-card-id", card.cardId); node.setAttribute("aria-label", card.name)
     node.appendChild(cardDetailsPopover(card))
     val moveLabel = if (zone == "keep") s"Discard ${card.name}" else s"Keep ${card.name}"
     val move = if (zone == "keep") button("→", "move-discard")
       else button("←", "move-keep")
     move.setAttribute("aria-label", moveLabel); move.setAttribute("title", moveLabel)
     move.onclick = _ => if (zone == "keep") update(state.moveToDiscard(card.cardId))
       else update(state.moveToKeep(card.cardId))
     node.appendChild(move)
     node.addEventListener("dragstart", (event: dom.Event) =>
       event.asInstanceOf[dom.DragEvent].dataTransfer
         .setData("text/plain", card.cardId))
     node
   }
   def arrangementZones(): dom.Element = {
     val helpers = cardDecisionZoneHelpers(decision)
     val zones = element("div", "decision-zones")
     val keep = element("section", "decision-zone keep-zone")
     keep.appendChild(text("h3", "", "Keep")); keep.appendChild(text("p", "decision-zone-helper", helpers.keep))
     state.keep.foreach(card => keep.appendChild(cardNode(card, "keep")))
     keep.addEventListener("dragover", (event: dom.Event) => event.preventDefault())
     keep.addEventListener("drop", (event: dom.Event) => {
       event.preventDefault()
       update(dropOnKeep(state, event.asInstanceOf[dom.DragEvent]
         .dataTransfer.getData("text/plain")))
     })
     val discard = element("section", "decision-zone discard-zone"); val discardHeading = element("div", "decision-zone-heading")
     discardHeading.appendChild(text("h3", "", "Discard"))
     discardHeading.appendChild(text("p", "decision-zone-helper", helpers.discard))
     discard.appendChild(discardHeading)
     state.discard.foreach(card => discard.appendChild(cardNode(card, "discard")))
     discard.addEventListener("dragover", (event: dom.Event) => event.preventDefault())
     discard.addEventListener("drop", (event: dom.Event) => {
       event.preventDefault()
       update(dropOnDiscard(state, event.asInstanceOf[dom.DragEvent]
         .dataTransfer.getData("text/plain")))
     })
     zones.appendChild(keep); zones.appendChild(discard)
     zones
   }
   shell.appendChild(arrangementZones())
   val confirm = button("Confirm adviser", "decision-confirm")
   confirm.disabled = !state.arrangementValid(decision.cards) || !canControl
   confirm.onclick = _ => state.keep.headOption.foreach(card => submitCommand(
     GameCommand.ResolveCardDecision(decision.decisionId,
       DecisionResolution.StartingAdviser(card.cardId))))
   shell.appendChild(confirm)
   shell
 }
}
