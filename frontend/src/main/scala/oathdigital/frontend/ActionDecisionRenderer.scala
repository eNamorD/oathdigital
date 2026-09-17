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
     case None if value.phase == "search-decision" =>
       node.textContent = "Act phase — resolve your Search."
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
    } else if (currentBoardFormation.nonEmpty) {
       val formation = currentBoardFormation.get
       val targetLabel = formation.targets.map { target =>
         formation.action.candidates.find(_.target == target)
           .fold(target.stableKey)(_.label)
       }.mkString(", ")
       panel.appendChild(text("h2", "", "Form Campaign force"))
       panel.appendChild(text("p", "campaign-formation-target",
         s"Target: $targetLabel"))
       val summary = text("p", "campaign-formation-summary",
         campaignFormationSummary(formation))
       summary.setAttribute("aria-live", "polite")
       panel.appendChild(summary)
       val decrease = button("Decrease committed force", "campaign-force-decrease")
       decrease.setAttribute("aria-label", campaignForceAdjustmentLabel(increase = false))
       decrease.disabled = !canControl || formation.force <= formation.minimumForce
       decrease.onclick = _ => {
         currentBoardFormation = currentBoardFormation.map(_.decrement); rerender()
       }
       panel.appendChild(decrease)
       (formation.minimumForce to formation.maximumForce).foreach { count =>
         val choice = button(count.toString, "campaign-force-choice")
         choice.setAttribute("aria-label", campaignForceChoiceLabel(count))
         choice.setAttribute("aria-pressed", (formation.force == count).toString)
         choice.disabled = !canControl
         choice.onclick = _ => {
           currentBoardFormation = currentBoardFormation.map(_.choose(count)); rerender()
         }
         panel.appendChild(choice)
       }
       val increase = button("Increase committed force", "campaign-force-increase")
       increase.setAttribute("aria-label", campaignForceAdjustmentLabel(increase = true))
       increase.disabled = !canControl || formation.force >= formation.maximumForce
       increase.onclick = _ => {
         currentBoardFormation = currentBoardFormation.map(_.increment); rerender()
       }
       panel.appendChild(increase)
       val confirm = button("Confirm Campaign", "campaign-force-confirm")
       confirm.disabled = !canControl
       confirm.onclick = _ => {
         currentBoardFormation = None
         currentBoardSelection = None
         commandForFormation(formation, currentPlayerId).foreach(submitTargetCommand)
       }
       panel.appendChild(confirm)
       val back = button("Back to target selection", "campaign-force-back")
       back.onclick = _ => { currentBoardFormation = None; rerender() }
       panel.appendChild(back)
       val cancel = button("Cancel Campaign", "campaign-force-cancel")
       cancel.onclick = _ => cancelTargetAction()
       panel.appendChild(cancel)
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
         confirm.onclick = _ => currentBoardSelection.flatMap(_.confirmResult)
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
       if (value.legalControls.contains("placeBannerResource")) {
         value.banners.filter(_.holderPlayerId.contains(currentPlayerId)).foreach { banner =>
           val label = dom.document.createElement("label").asInstanceOf[dom.html.Label]
           label.textContent = s"Add to ${actionLabel(banner.banner)} "
           val amount = dom.document.createElement("input").asInstanceOf[dom.html.Input]
           amount.`type` = "number"; amount.min = "1"; amount.value = "1"
           amount.setAttribute("aria-label", s"Resources to add to ${actionLabel(banner.banner)}")
           label.appendChild(amount); groups.appendKind("place-banner-resource", label)
           val place = button("Place resources (0 Supply)", "banner-place-resource")
           place.disabled = !canControl
           place.onclick = _ => submitCommand(GameCommand.PlaceBannerResource(
             banner.banner, amount.value.toInt))
           groups.appendKind("place-banner-resource", place)
         }
       }
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
   WalkerPanelSupport.renderWaitingNotice(value, panel)
   value.challenge.filter(_ => presentation.showGameplayControls).foreach { challenge =>
     panel.appendChild(text("h2", "", s"Challenge ${actionLabel(challenge.banner)}"))
     challengeSiteCommands(challenge, currentPlayerId).foreach { command =>
       val site = command.siteId
       val choose = button(s"Place secret at $site", "challenge-secret-site")
       choose.disabled = !canControl
       choose.onclick = _ => submitCommand(command)
       panel.appendChild(choose)
     }
     if (challenge.legalSecretSiteIds.isEmpty) {
       val label = dom.document.createElement("label").asInstanceOf[dom.html.Label]
       label.textContent = "Resources to place "
       val amount = dom.document.createElement("input").asInstanceOf[dom.html.Input]
       amount.`type` = "number"; amount.min = challenge.minimumPlacement.toString
       amount.max = challenge.maximumPlacement.toString
       amount.value = challenge.minimumPlacement.toString
       amount.setAttribute("aria-label", "Banner replacement resources")
       label.appendChild(amount); panel.appendChild(label)
       val complete = button("Take banner", "challenge-complete")
       complete.disabled = !canControl || challenge.minimumPlacement > challenge.maximumPlacement
       complete.onclick = _ => completeChallengeCommand(challenge,
         currentPlayerId, amount.value.toInt).foreach(submitCommand)
       panel.appendChild(complete)
     }
   }
   value.negotiation match {
     case Some(deal) if showNegotiationControls(value, presentation) =>
       panel.appendChild(text("h2", "", "Negotiation"))
       panel.appendChild(text("p", "negotiation-status",
         deal.participantPlayerIds.map(id => s"$id: ${if (deal.acceptedPlayerIds.contains(id))
           "accepted" else "reviewing"}").mkString(" · ")))
       deal.transfers.foreach(t => panel.appendChild(text("p", "negotiation-transfer",
         s"${t.authorPlayerId} gives ${t.recipientPlayerId}: ${t.favor} favor, " +
           s"${t.relicCount} relic(s)")))
       deal.disclosures.foreach(d => panel.appendChild(text("p", "negotiation-disclosure",
         s"${d.authorPlayerId} promises ${d.recipientPlayerId} a ${d.kind} disclosure")))
       val favors = scala.collection.mutable.ArrayBuffer.empty[(String, dom.html.Input)]
       val relics = scala.collection.mutable.ArrayBuffer.empty[(String, String, dom.html.Input)]
       val disclosures = scala.collection.mutable.ArrayBuffer.empty[
         (String, NegotiationDisclosureOffer, dom.html.Input)]
       deal.participantPlayerIds.filterNot(_ == currentPlayerId).foreach { recipient =>
         panel.appendChild(text("h3", "", s"Your terms for $recipient"))
         val favor = dom.document.createElement("input").asInstanceOf[dom.html.Input]
         favor.`type` = "number"; favor.min = "0"; favor.max = deal.editableFavor.toString
         favor.value = deal.transfers.find(t => t.authorPlayerId == currentPlayerId &&
           t.recipientPlayerId == recipient).map(_.favor).getOrElse(0).toString
         favor.setAttribute("aria-label", s"Favor offered to $recipient")
         panel.appendChild(favor); favors += recipient -> favor
         deal.editableRelics.foreach { relic =>
           val check = dom.document.createElement("input").asInstanceOf[dom.html.Input]
           check.`type` = "checkbox"; check.setAttribute("aria-label",
             s"Offer ${relic.name} to $recipient")
           check.checked = negotiationRelicChecked(
             deal, currentPlayerId, recipient, relic.cardId)
           check.onchange = _ => if (check.checked) relics.foreach {
             case (otherRecipient, otherRelic, otherCheck)
                 if negotiationRelicCompetes(otherRecipient, otherRelic,
                   recipient, relic.cardId) =>
               otherCheck.checked = false
             case _ => ()
           }
           panel.appendChild(check); panel.appendChild(text("span", "", s" ${relic.name} "))
           relics += ((recipient, relic.cardId, check))
         }
         negotiationDisclosureOffers(deal).foreach { offer =>
           val check = dom.document.createElement("input").asInstanceOf[dom.html.Input]
           check.`type` = "checkbox"; check.setAttribute("aria-label",
             s"Promise ${offer.kind} disclosure of ${offer.card.name} to $recipient")
           check.checked = negotiationDisclosureChecked(
             deal, currentPlayerId, recipient, offer.kind, offer.card.cardId)
           panel.appendChild(check)
           panel.appendChild(text("span", "", s" Show ${offer.card.name} "))
           disclosures += ((recipient, offer, check))
         }
       }
       val save = button("Save Deal Changes", "negotiation-save")
       save.disabled = !canControl ||
         !value.legalControls.contains("replaceNegotiationTerms")
       save.onclick = _ => {
         val terms = NegotiationTermsInput(favors.map { case (recipient, input) =>
           NegotiationTransferInput(recipient, input.value.toInt,
             relics.collect { case (`recipient`, relic, check) if check.checked => relic }.toVector)
         }.toVector, disclosures.collect { case (recipient, offer, check)
             if check.checked => NegotiationDisclosureInput(recipient, offer.kind,
               Option.when(offer.kind != "site-relic")(currentPlayerId),
               offer.siteId,
               Option.when(offer.kind == "adviser")(offer.card.cardKind),
               offer.card.cardId)
         }.toVector)
         submitCommand(GameCommand.ReplaceNegotiationTerms(deal.decisionId,
           protocolNegotiationTerms(terms)))
       }
       panel.appendChild(save)
       val accept = button("Accept Current Deal", "negotiation-accept")
       accept.disabled = !canControl ||
         !value.legalControls.contains("acceptNegotiation")
       accept.onclick = _ => submitCommand(GameCommand.AcceptNegotiation(
         deal.decisionId)); panel.appendChild(accept)
       val decline = button("End/Decline", "negotiation-decline")
       decline.disabled = !canControl ||
         !value.legalControls.contains("declineNegotiation")
       decline.onclick = _ => submitCommand(GameCommand.DeclineNegotiation(
         deal.decisionId)); panel.appendChild(decline)
     case _ => ()
   }
   value.campaign.filter(_ => presentation.showGameplayControls).foreach { campaign =>
     panel.appendChild(text("h2", "", "Campaign"))
     if (!campaign.plansFinished) {
       panel.appendChild(text("p", "campaign-instruction",
         s"Choose ${campaign.planSide} battle plans in order, then finish."))
       if (campaign.selectedPlans.nonEmpty) panel.appendChild(text("p",
         "campaign-selected-plans", campaignSelectedPlansLabel(
           campaign.selectedPlans)))
       campaign.planChoices.foreach { choice =>
         val choose = button(campaignPlanButtonLabel(choice), "campaign-plan")
         choose.title = choice.mechanicalResult
         choose.disabled = !canControl
         choose.onclick = _ => submitCommand(GameCommand.ChooseCampaignPlan(
           campaign.decisionId, protocolCampaignPlan(choice)))
         panel.appendChild(choose)
       }
       val rollsNow = campaign.planSide == "defender" || campaign.defenderKind == "bandits"
       val finishLabel = if (!rollsNow) "Finish attacker plans"
         else if (campaign.selectedPlans.isEmpty) "Roll without battle plans"
         else "Finish plans and roll"
       val finish = button(finishLabel, "campaign-finish-plans")
       finish.disabled = !canControl
       finish.onclick = _ => submitCommand(GameCommand.FinishCampaignPlans(
         campaign.decisionId))
       panel.appendChild(finish)
     } else {
       panel.appendChild(text("p", "campaign-results",
         s"Attack dice: ${campaign.attackDice.mkString(", ")} · " +
           s"${campaign.attack} attack · ${campaign.skullLosses} skull losses"))
     if (campaign.sacrificed.isEmpty) {
       panel.appendChild(text("p", "campaign-instruction",
         "Choose surviving warbands to sacrifice for +1 attack each."))
       (0 to campaign.maxSacrifice).foreach { count =>
         val choose = button(s"Sacrifice $count", "campaign-sacrifice")
         choose.disabled = !canControl
         choose.onclick = _ => submitCommand(GameCommand.ChooseCampaignSacrifice(
           campaign.decisionId, count))
         panel.appendChild(choose)
       }
     } else {
       val outcome = if (campaign.victorious.contains(true)) "Victory" else "Defeat"
       panel.appendChild(text("p", "campaign-defense",
         s"Defense dice: ${campaign.defenseDice.mkString(", ")} · " +
           s"${campaign.defense.getOrElse(0)} defense · $outcome"))
       if (campaign.victorious.contains(true)) {
         val placement = currentCampaignPlacement.getOrElse(
           CampaignPlacementState.reconcile(None,
             BoardSelectionContext(value.gameId, currentPlayerId,
               value.nextSequence), Some(campaign)).get)
         panel.appendChild(text("p", "campaign-instruction",
           "Allocate surviving warbands among conquered sites."))
         placement.targets.foreach { target =>
           val row = element("div", "campaign-placement-row")
           row.appendChild(text("span", "campaign-placement-site",
             target.label))
           val decrease = button(s"Remove one from ${target.label}",
             "campaign-placement-decrease")
           decrease.disabled = !canControl ||
             placement.count(target.siteId) == 0
           decrease.onclick = _ => {
             currentCampaignPlacement = currentCampaignPlacement.map(
               _.decrement(target.siteId)); rerender()
           }
           row.appendChild(decrease)
           row.appendChild(text("span", "campaign-placement-count",
             placement.count(target.siteId).toString))
           val increase = button(s"Add one to ${target.label}",
             "campaign-placement-increase")
           increase.disabled = !canControl || placement.remaining == 0
           increase.onclick = _ => {
             currentCampaignPlacement = currentCampaignPlacement.map(
               _.increment(target.siteId)); rerender()
           }
           row.appendChild(increase)
           panel.appendChild(row)
         }
         panel.appendChild(text("p", "campaign-placement-summary",
           s"Placed: ${placement.total} · Remaining: ${placement.remaining}"))
         val confirm = button("Confirm placement",
           "campaign-placement-confirm")
         confirm.disabled = !canControl
         confirm.onclick = _ => {
           currentCampaignPlacement = None
           submitCommand(GameCommand.PlaceCampaignForce(campaign.decisionId,
             placement.allocations.map(v => CampaignForceAllocation(v.siteId, v.count))))
         }
         panel.appendChild(confirm)
         val back = button("Back", "campaign-placement-back")
         back.disabled = !canControl || placement.total == 0
         back.onclick = _ => {
           currentCampaignPlacement = currentCampaignPlacement.map(_.reset)
           rerender()
         }
         panel.appendChild(back)
       }
     }
     }
   }
   value.campaignRaidRelocation.filter(decision =>
     decision.actorPlayerId == currentPlayerId &&
       presentation.showGameplayControls).foreach { decision =>
     panel.appendChild(text("h2", "", "Relocate defender pawn"))
     panel.appendChild(text("p", "campaign-instruction",
       "Choose another legal site for the defender pawn."))
     raidRelocationCommands(decision, currentPlayerId).foreach { command =>
       val site = command.destinationSiteId
       val choose = button(siteLabel(value, site), "campaign-raid-relocation")
       choose.disabled = !canControl
       choose.onclick = _ => submitCommand(command)
       panel.appendChild(choose)
     }
   }
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
   if (decision.kind != "starting-adviser") decision.instructions.foreach(instruction =>
     shell.appendChild(text("p", "decision-instruction", instruction)))
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
     if (zone == "discard") {
       val left = button("‹", "move-left")
       left.setAttribute("aria-label", s"Move ${card.name} left")
       left.setAttribute("title", s"Move ${card.name} left")
       left.disabled = state.discard.headOption.contains(card)
       left.onclick = _ => update(state.move(card.cardId, -1))
       val right = button("›", "move-right")
       right.setAttribute("aria-label", s"Move ${card.name} right")
       right.setAttribute("title", s"Move ${card.name} right")
       right.disabled = state.discard.lastOption.contains(card)
       right.onclick = _ => update(state.move(card.cardId, 1))
       node.appendChild(left); node.appendChild(right)
       node.addEventListener("dragover", (event: dom.Event) => event.preventDefault())
       node.addEventListener("drop", (event: dom.Event) => {
         event.preventDefault()
         event.stopPropagation()
         update(dropBeforeDiscard(state,
           event.asInstanceOf[dom.DragEvent].dataTransfer.getData("text/plain"),
           card.cardId))
       })
     }
     node
   }
   def arrangementZones(showDiscardOrder: Boolean): dom.Element = {
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
     if (showDiscardOrder || decision.kind == "starting-adviser")
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
   if (decision.kind == "starting-adviser") {
     shell.appendChild(arrangementZones(showDiscardOrder = false))
     val confirm = button("Confirm adviser", "decision-confirm")
     confirm.disabled = !state.arrangementValid(decision.cards) || !canControl
     confirm.onclick = _ => state.keep.headOption.foreach(card => submitCommand(
       GameCommand.ResolveCardDecision(decision.decisionId,
         DecisionResolution.StartingAdviser(card.cardId))))
     shell.appendChild(confirm)
   } else state.stage match {
     case CardDecisionStage.Arrange =>
       shell.appendChild(arrangementZones(showDiscardOrder = true))
       val confirm = button("Confirm arrangement", "decision-confirm")
       confirm.disabled = !state.arrangementValid(decision.cards)
       confirm.onclick = _ => update(state.copy(stage = CardDecisionStage.Resolve))
       shell.appendChild(confirm)
     case CardDecisionStage.Resolve =>
       val kept = state.keep.head
       shell.appendChild(text("h3", "", s"Resolve ${kept.name}"))
       decision.resolutionsByCard.getOrElse(kept.cardId, Vector.empty).foreach { resolution =>
         val label = resolution.kind match {
           case "discard" => "Discard"
           case "site" => "Play at site"
           case "adviser" if resolution.orientation.contains("face-down") => "Play facedown"
           case "adviser" => "Play faceup"
           case other => other
         }
         val choose = button(label, "resolution-choice")
         choose.setAttribute("aria-pressed", state.selectedResolution.contains(resolution).toString)
         choose.onclick = _ => update(state.chooseResolution(resolution))
         shell.appendChild(choose)
       }
       state.selectedResolution.filter(_.replacementRequired).foreach { resolution =>
         val select = dom.document.createElement("select").asInstanceOf[dom.html.Select]
         select.setAttribute("aria-label", "Card to replace")
         val placeholder = dom.document.createElement("option").asInstanceOf[dom.html.Option]
         placeholder.value = ""; placeholder.text = "Choose a card to replace"
         select.appendChild(placeholder)
         resolution.replacementTargets.foreach { card =>
           val option = dom.document.createElement("option").asInstanceOf[dom.html.Option]
           option.value = card.cardId; option.text = card.name; select.appendChild(option)
         }
         select.onchange = _ => update(state.chooseReplacement(select.value))
         shell.appendChild(select)
       }
       val back = button("Back", "decision-back")
       back.onclick = _ => update(state.copy(stage = CardDecisionStage.Arrange,
         selectedResolution = None, selectedReplacement = None))
       val confirmRow = element("div", "decision-final-row")
       confirmRow.appendChild(back)
       val confirm = button("Final confirm", "decision-confirm")
       confirm.disabled = !state.resolutionValid || !canControl
       confirm.onclick = _ => state.selectedResolution.foreach { resolution =>
         val placementKind = resolution.kind match {
             case "adviser" if resolution.orientation.contains("face-up") => "adviser-face-up"
             case "adviser" => "adviser-face-down"
             case other => other
         }
         submitCommand(GameCommand.ResolveCardDecision(decision.decisionId,
           DecisionResolution.Search(protocolWorldCard(kept),
             state.discard.map(protocolWorldCard), Placement(placementKind,
               state.selectedReplacement.map(card =>
                 CardRef(card.cardKind, card.cardId))))))
       }
       confirmRow.appendChild(confirm)
       shell.appendChild(confirmRow)
   }
   shell
 }
}
