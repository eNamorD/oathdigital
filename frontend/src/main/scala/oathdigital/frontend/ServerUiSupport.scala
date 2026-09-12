package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, _}
import org.scalajs.dom
import scala.scalajs.js

private[frontend] trait ServerUiView {
  def currentGameId: String
  def currentPlayerId: String
  def displayedProjection: Option[GameProjection]
  def sessionCoordinator: ServerSessionCoordinator
  def currentBoardSelection: Option[BoardTargetSelectionState]
  def currentBoardSelection_=(value: Option[BoardTargetSelectionState]): Unit
  def currentBoardFormation: Option[BoardTargetFormationState]
  def currentBoardFormation_=(value: Option[BoardTargetFormationState]): Unit
  def currentCampaignPlacement: Option[CampaignPlacementState]
  def currentCampaignPlacement_=(value: Option[CampaignPlacementState]): Unit
  def currentWalkerPartition: Option[WalkerPartitionDraft]
  def currentWalkerPartition_=(value: Option[WalkerPartitionDraft]): Unit
  def currentCardDecision: Option[CardDecisionState]
  def currentCardDecision_=(value: Option[CardDecisionState]): Unit
  def currentModifierWorkflow: Option[ModifierWorkflow]
  def currentFacedownAdviserDraft: Option[FacedownAdviserDraft]
  def chooseFacedownAdviser(cardId: String): Unit
  def toggleModifier(value: PreviewModifier): Unit
  def moveModifier(value: PreviewModifier, delta: Int): Unit
  def confirmModifiers(): Unit
  def backFromModifiers(): Unit
  def cancelModifiers(): Unit
  def beginTargetedMajorAction(actionKind: String): Unit
  def backFromTargets(): Unit
  def cancelTargetAction(): Unit
  def submitTargetCommand(command: GameCommand): Unit
  def canControl: Boolean
  def rerender(): Unit
  def submitCommand(command: GameCommand): Unit
  def handleSelection(result: BoardSelectionResult): Unit
  def loadSession(gameId: String, playerId: String): Unit
  def reconnectSession(): Unit
  def createGame(): Unit
}
private[frontend] object ServerUiSupport {
  private[frontend] def secretSummaryLabel(available: Int, total: Int,
      facedown: Int, committed: Int): String =
    s"$available available of $total owned; $facedown facedown and $committed committed"

  private[frontend] def siteLabel(value: GameProjection, siteId: String): String =
    value.world.flatMap(_.sites).find(_.siteId == siteId)
      .fold(siteId)(_.label)

  private[frontend] def negotiationRelicChecked(deal: NegotiationState,
      author: String, recipient: String, relicId: String): Boolean =
    deal.transfers.exists(t => t.authorPlayerId == author &&
      t.recipientPlayerId == recipient && t.relics.exists(_.cardId == relicId))

  private[frontend] def negotiationDisclosureChecked(deal: NegotiationState,
      author: String, recipient: String, kind: String, cardId: String): Boolean =
    deal.disclosures.exists(d => d.authorPlayerId == author &&
      d.recipientPlayerId == recipient && d.kind == kind &&
      d.card.exists(_.cardId == cardId))

  private[frontend] def negotiationRelicCompetes(currentRecipient: String,
      currentRelic: String, selectedRecipient: String, selectedRelic: String): Boolean =
    currentRecipient != selectedRecipient && currentRelic == selectedRelic

  private[frontend] final case class NegotiationDisclosureOffer(
      kind: String,
      card: CardDetails,
      siteId: Option[String])

  /** Disclosure options the engine can accept: only information the author can
    * currently inspect (facedown advisers/relics, known site relics with the
    * site that holds them). */
  private[frontend] def negotiationDisclosureOffers(
      deal: NegotiationState): Vector[NegotiationDisclosureOffer] =
    deal.editableAdvisers.map(card =>
      NegotiationDisclosureOffer("adviser", card, None)) ++
      deal.editableRelics.filter(_.orientation.contains("face-down"))
        .map(card => NegotiationDisclosureOffer("held-relic", card, None)) ++
      deal.editableSiteRelics.map(entry => NegotiationDisclosureOffer(
        "site-relic", entry.card, Some(entry.siteId)))

  private[frontend] def siteDetails(site: GameSite,
      selection: Option[BoardTargetSelectionState] = None,
      chooseTarget: BoardTargetRef => Unit = _ => ()): dom.Element = {
    val presentation = SiteCardPresentation.from(site)
    val details = element("div", "site-details")
    val properties = element("dl", "site-properties")
    presentation.metrics.foreach { metric =>
      val item = element("div", "site-property")
      item.appendChild(text("dt", "", metric.label))
      item.appendChild(text("dd", "", metric.value.toString))
      properties.appendChild(item)
    }
    details.appendChild(properties)
    site.forces.foreach { forces =>
      val row = text("p", s"site-forces ${forceCssClass(forces)}",
        forceText(forces))
      row.setAttribute("data-ruler-kind", forces.rulerKind)
      forces.rulerPlayerId.foreach(row.setAttribute("data-ruler-player-id", _))
      details.appendChild(row)
    }
    if (site.powers.nonEmpty) {
      val powers = element("ul", "site-powers")
      site.powers.foreach { power =>
        val item = element("li", "site-power")
        item.textContent = power.description.fold(power.label)(description =>
          s"${power.label}: $description")
        powers.appendChild(item)
      }
      details.appendChild(powers)
    }

    val denizens = element("div", "site-denizens")
    denizens.appendChild(text("strong", "", "Denizens: "))
    if (site.denizens.isEmpty)
      denizens.appendChild(dom.document.createTextNode(presentation.denizenEmpty))
    else site.denizens.foreach { denizen =>
      val target = BoardTargetRef.SiteCard(site.siteId,
        denizen.details.fold("denizen")(_.cardKind), denizen.denizenId)
      val candidate = selection.flatMap(_.activeAction.flatMap(
        _.candidates.find(_.target == target)))
      val shell = element("span", cardTargetClasses(candidate.nonEmpty,
        selection.exists(_.selected(target))))
      shell.setAttribute("data-target-ref", target.stableKey)
      val card = denizen.details.fold[dom.Element](VisualDomRenderer.render(
        presentation.denizenVisuals.find(_._1 == denizen.denizenId).get._2,
        "site-card"))(cardDetailsPopover)
      card.setAttribute("data-denizen-id", denizen.denizenId)
      candidate.foreach { value =>
        card.classList.add("board-target")
        card.setAttribute("aria-pressed",
          selection.exists(_.selected(target)).toString)
        card.setAttribute("title", candidateButtonLabel(value))
        card.addEventListener("click", (event: dom.Event) => {
          event.stopPropagation(); chooseTarget(target)
        })
        card.addEventListener("keydown", (event: dom.Event) => {
          val key = event.asInstanceOf[dom.KeyboardEvent].key
          if (key == "Enter" || key == " ") {
            event.preventDefault(); event.stopPropagation(); chooseTarget(target)
          }
        })
      }
      shell.appendChild(card)
      candidate.flatMap(candidateDetailBadge).foreach(shell.appendChild)
      denizens.appendChild(shell)
    }
    (site.denizens.size until site.denizenCapacity).foreach { _ =>
      val slot = text("span", "empty-denizen-slot", "◇")
      slot.setAttribute("role", "img")
      slot.setAttribute("aria-label", "Empty denizen slot")
      denizens.appendChild(slot)
    }
    details.appendChild(denizens)

    val relics = element("div", "site-relics")
    relics.appendChild(text("strong", "", "Relics: "))
    if (site.relics.facedownCount == 0)
      relics.appendChild(dom.document.createTextNode("None"))
    presentation.peekedRelics.foreach(value =>
      relics.appendChild(peekedRelic(value.card)))
    (0 until presentation.unknownRelicCount).foreach { _ =>
      val relic = text("span", "facedown-relic", "▣")
      relic.setAttribute("role", "img")
      relic.setAttribute("aria-label", "Facedown relic")
      relics.appendChild(relic)
    }
    details.appendChild(relics)
    details
  }

  private[frontend] def forceText(forces: SiteForces): String =
    s"${forces.label} x${forces.count}"

  private[frontend] def forceCssClass(forces: SiteForces): String =
    s"force-${forces.colorToken}"

  private[frontend] def dropOnKeep(
      state: CardDecisionState,
      cardId: String
  ): CardDecisionState = state.moveToKeep(cardId)

  private[frontend] def dropOnDiscard(
      state: CardDecisionState,
      cardId: String
  ): CardDecisionState =
    if (state.keep.exists(_.cardId == cardId)) state.moveToDiscard(cardId)
    else state.arrangeDrop(cardId, None)

  private[frontend] def dropBeforeDiscard(
      state: CardDecisionState,
      cardId: String,
      beforeCardId: String
  ): CardDecisionState = {
    val inDiscard = if (state.keep.exists(_.cardId == cardId))
      state.moveToDiscard(cardId) else state
    inDiscard.arrangeDrop(cardId, Some(beforeCardId))
  }

  private[frontend] final case class CardDecisionZoneHelpers(
      keep: String, discard: String)

  private[frontend] def cardDecisionZoneHelpers(
      decision: PendingCardDecision): CardDecisionZoneHelpers =
    if (decision.kind == "starting-adviser") CardDecisionZoneHelpers(
      "Move exactly one adviser to Keep.",
      "The remaining candidates are discarded in order.")
    else CardDecisionZoneHelpers(
      "Move the card you want to resolve to Keep.",
      "The remaining cards are discarded in order.")

  private[frontend] def cardDetailsPopover(card: CardDetails): dom.Element = {
    val node = element("button", "card-detail")
    node.setAttribute("type", "button")
    node.setAttribute("aria-label", card.name)
    node.setAttribute("data-card-id", card.cardId)
    node.appendChild(text("span", "card-summary", card.name))
    val details = element("span", "card-popover")
    details.setAttribute("role", "tooltip")
    val metadata = Vector(card.suit.map(value => s"Suit: $value"),
      card.restrictions.map(value => s"Restrictions: $value"),
      card.orientation.map(value => s"Orientation: $value"),
      card.side.map(value => s"Side: $value"),
      Option.when(card.favor > 0)(s"Favor: ${card.favor}"),
      Option.when(card.secrets > 0)(s"Secrets: ${card.secrets}"),
      card.relicValue.map(value => s"Relic value: $value"),
      card.defense.map(value => s"Defense: $value"),
      card.rulesText.map(value => s"Rules: $value")).flatten
    metadata.zipWithIndex.foreach { case (value, index) =>
      details.appendChild(text("span", "card-property", value))
      if (index < metadata.size - 1)
        details.appendChild(dom.document.createElement("br"))
    }
    node.appendChild(details)
    node
  }

  private[frontend] def peekedRelic(card: CardDetails): dom.Element = {
    val shell = element("span", "peeked-relic")
    val back = text("span", "facedown-relic peeked-relic-back", "▣")
    back.setAttribute("aria-hidden", "true")
    val reveal = cardDetailsPopover(card)
    reveal.classList.add("peeked-relic-reveal")
    reveal.setAttribute("aria-label", s"Peek at ${card.name}")
    reveal.asInstanceOf[dom.html.Button].onmouseup = _ =>
      reveal.asInstanceOf[dom.html.Button].blur()
    reveal.addEventListener("touchend", (_: dom.Event) =>
      reveal.asInstanceOf[dom.html.Button].blur())
    shell.appendChild(back); shell.appendChild(reveal); shell
  }

  private[frontend] def pileDisplay(
      label: String,
      count: Int,
      topCardKind: Option[String]
  ): dom.Element = {
    val pile = element("div", "pile-display")
    pile.appendChild(text("span", "pile-label", s"$label:"))
    val css = pileCardClasses(count)
    val symbol = pileSymbol(count, topCardKind)
    val back = text("span", css, if (symbol.isEmpty) "\u00a0" else symbol)
    back.setAttribute("role", "img")
    back.setAttribute("aria-label", if (count == 0) "Empty pile"
      else topCardKind match {
        case Some("denizen") => "Denizen card on top"
        case Some("vision") => "Vision card on top"
        case _ => "Facedown card; type hidden"
      })
    pile.appendChild(back)
    pile.appendChild(text("span", "pile-count", s"x$count"))
    pile
  }

  private[frontend] def pileSymbol(count: Int, topCardKind: Option[String]): String =
    if (count == 0) "" else topCardKind match {
      case Some("denizen") => "D"
      case Some("vision") => "V"
      case _ => ""
    }

  private[frontend] def pileCardClasses(count: Int): String =
    if (count == 0) "pile-card pile-empty" else "pile-card pile-back"

  private[frontend] final case class TakeWealthAction(
      label: String,
      command: GameCommand.TakeWealth
  )

  private[frontend] final case class ViewerPresentation(
      showGameplayControls: Boolean,
      waitingForPlayerId: Option[String],
      waitingForDisplayName: Option[String],
      procedureStatus: Option[String] = None
  )

  private[frontend] def viewerPresentation(
      value: GameProjection,
      playerId: String
  ): ViewerPresentation = {
    if (value.oathkeeper.exists(_.winnerPlayerId.nonEmpty))
      return ViewerPresentation(showGameplayControls = false,
        waitingForPlayerId = None, waitingForDisplayName = None)
    if (value.restPower.exists(_.decisionOwnerPlayerId == playerId))
      return ViewerPresentation(showGameplayControls = true,
        waitingForPlayerId = None, waitingForDisplayName = None,
        procedureStatus = Some("Choose whether to use the Rest power."))
    if (value.restPowerWaiting)
      return ViewerPresentation(showGameplayControls = false,
        waitingForPlayerId = None, waitingForDisplayName = None,
        procedureStatus = Some("Waiting for a Rest power decision."))
    if (value.negotiation.exists(_.participantPlayerIds.contains(playerId)))
      return ViewerPresentation(showGameplayControls = true,
        waitingForPlayerId = None, waitingForDisplayName = None,
        procedureStatus = Some("Negotiation in progress."))
    if (value.negotiationWaiting)
      return ViewerPresentation(showGameplayControls = false,
        waitingForPlayerId = None, waitingForDisplayName = None,
        procedureStatus = Some("Waiting for the negotiation to finish."))
    val controllingPlayer = value.campaign.filter(!_.plansFinished)
      .flatMap(_.decisionOwnerPlayerId).orElse(value.activeParticipantId)
    controllingPlayer match {
      case Some(activePlayerId) if activePlayerId != playerId =>
        ViewerPresentation(
          showGameplayControls = false,
          waitingForPlayerId = Some(activePlayerId),
          waitingForDisplayName = Some(playerDisplayName(value, activePlayerId))
        )
      case _ => ViewerPresentation(
        showGameplayControls = true,
        waitingForPlayerId = None,
        waitingForDisplayName = None
      )
    }
  }

  private[frontend] def showNegotiationControls(
      value: GameProjection,
      presentation: ViewerPresentation
  ): Boolean = value.negotiation.nonEmpty && presentation.showGameplayControls

  private[frontend] def showActActionControls(
      value: GameProjection,
      presentation: ViewerPresentation
  ): Boolean = value.actionSelectionOpen && presentation.showGameplayControls

  private[frontend] def actionLabel(kind: String): String = kind match {
    case "travel" => "Travel"
    case "campaign-conquest" => "Campaign"
    case "campaign-raid" => "Raid"
    case "challenge" => "Challenge"
    case "peoples-favor" => "People's Favor"
    case "darkest-secret" => "Darkest Secret"
    case "muster" => "Muster"
    case "trade-favor" => "Trade for favor"
    case "trade-secret" => "Trade for secrets"
    case "reveal-vision" => "Reveal Vision"
    case "play-conspiracy" => "Play Conspiracy"
    case other => other
  }

  private[frontend] def actionCategory(kind: String): String = kind match {
    case "search" | "travel" | "campaign-conquest" | "campaign-raid" |
        "muster" | "trade-favor" | "trade-secret" | "recover" | "forge" |
        "challenge" => "major"
    case "negotiation" | "reveal-vision" | "play-conspiracy" |
        "place-banner-resource" | "facedown-adviser" | "peek-site-relics" |
        "reveal-owned-relic" | "move-warbands" => "minor"
    case _ => "powers"
  }

  private[frontend] val majorFamilyOrder: Vector[String] = Vector(
    "search", "travel", "campaign", "muster", "trade", "forge", "recover", "challenge")

  private[frontend] def actionFamily(kind: String): String = kind match {
    case "campaign-conquest" | "campaign-raid" => "campaign"
    case "trade-favor" | "trade-secret" => "trade"
    case other => other
  }

  private[frontend] val actionCategoryOrder: Vector[(String, String)] =
    Vector("major" -> "Major actions", "minor" -> "Minor actions", "powers" -> "Powers")

  private[frontend] final class ActionSections {
    private val contents = scala.collection.mutable.Map.empty[String,
      scala.collection.mutable.ArrayBuffer[(String, dom.Node)]]
    def appendKind(kind: String, node: dom.Node): Unit = contents
      .getOrElseUpdate(actionCategory(kind), scala.collection.mutable.ArrayBuffer.empty)
      .append(actionFamily(kind) -> node)
    def appendTo(panel: dom.Element): Unit = actionCategoryOrder.foreach { case (key, heading) =>
      contents.get(key).filter(_.nonEmpty).foreach { nodes =>
        val section = element("section", s"available-action-group action-group-$key")
        section.appendChild(text("h3", "action-group-heading", heading))
        val order = if (key == "major") majorFamilyOrder else nodes.map(_._1).distinct.toVector
        order.foreach(family => nodes.filter(_._1 == family).foreach(entry =>
          section.appendChild(entry._2)))
        panel.appendChild(section)
      }
    }
  }

  private[frontend] def cardinalityInstruction(action: BoardTargetAction): String =
    if (action.maximum == 0) "No target is available; confirm to play this action."
    else if (action.maximum == 1) "Choose one target. Selection submits immediately."
    else s"Choose ${action.minimum} to ${action.maximum} targets, then confirm."

  private[frontend] def candidateButtonLabel(candidate: BoardTargetCandidate): String =
    (candidate.label +: candidate.details).mkString(" · ")

  private[frontend] def candidateDetailText(
      candidate: BoardTargetCandidate): Option[String] =
    Option.when(candidate.details.nonEmpty)(candidate.details.mkString(" · "))

  private[frontend] def candidateDetailBadgeTexts(
      candidate: BoardTargetCandidate): Vector[String] =
    candidateDetailText(candidate).toVector

  private[frontend] def candidateForTarget(
      selection: Option[BoardTargetSelectionState],
      target: BoardTargetRef): Option[BoardTargetCandidate] =
    selection.flatMap(_.activeAction.flatMap(_.candidates.find(_.target == target)))

  private[frontend] def candidateDetailBadge(
      candidate: BoardTargetCandidate): Option[dom.Element] =
    candidateDetailText(candidate).map(value => text("span", "target-detail-badge", value))

  private[frontend] def winnerColorClass(value: GameProjection,
      playerId: String): String = value.players.find(_.playerId == playerId)
    .fold[PlayerColorToken](PlayerColorToken.Neutral)(p =>
      PlayerColorToken.fromKey(p.colorToken)).cssClass

  private[frontend] def winnerBanner(value: GameProjection,
      winner: String, victory: String): dom.Element = {
    val banner = element("div", s"victory-banner ${winnerColorClass(value, winner)}")
    banner.setAttribute("role", "status")
    banner.appendChild(playerReference(value, winner))
    banner.appendChild(dom.document.createTextNode(s" wins — $victory victory"))
    banner
  }

  private[frontend] def campaignPlanButtonLabel(choice: CampaignPlanChoice): String = {
    val cost = Vector(
      Option.when(choice.favorCost > 0)(s"${choice.favorCost} Favor"),
      Option.when(choice.secretCost > 0)(s"Place ${choice.secretCost} Secret")
    ).flatten.mkString(", ")
    if (cost.isEmpty) choice.label else s"${choice.label} ($cost)"
  }

  private[frontend] def campaignSelectedPlansLabel(
      plans: Vector[CampaignPlanChoice]): String =
    plans.zipWithIndex.map { case (plan, index) =>
      s"${index + 1}. ${plan.label}"
    }.mkString("Selected: ", " · ", "")

  private[frontend] def campaignFormationSummary(
      formation: BoardTargetFormationState): String =
    s"Committed force: ${formation.force}. Board warbands remaining: " +
      s"${formation.remainingWarbands}. Attack dice before plans: " +
      s"${formation.attackDiceBeforePlans}. Cost: ${formation.supplyCost} Supply."

  private[frontend] def campaignForceChoiceLabel(force: Int): String =
    s"Commit $force warbands"

  private[frontend] def campaignForceAdjustmentLabel(increase: Boolean): String =
    if (increase) "Increase committed force" else "Decrease committed force"

  private[frontend] def siteTargetClasses(candidate: Boolean,
      selected: Boolean): String =
    Vector("site", if (candidate) "board-target" else "site-readonly",
      if (selected) "board-target-selected" else "").filter(_.nonEmpty).mkString(" ")

  private[frontend] def cardTargetClasses(candidate: Boolean,
      selected: Boolean): String =
    Vector("site-card-target", if (candidate) "board-target" else "",
      if (selected) "board-target-selected" else "").filter(_.nonEmpty).mkString(" ")

  private[frontend] def commandForSelection(action: BoardTargetAction,
      targets: Vector[BoardTargetRef], playerId: String,
      attackDiceCount: Int = 0): Option[GameCommand] =
    (action.actionKind, targets) match {
      case ("place-pawn", Vector(BoardTargetRef.Site(site))) =>
        Some(GameCommand.PlacePawn(site))
      case ("travel", Vector(BoardTargetRef.Site(site))) =>
        Some(GameCommand.Travel(site))
      case ("campaign-conquest", sites) if sites.nonEmpty &&
          sites.forall(_.isInstanceOf[BoardTargetRef.Site]) =>
        Some(GameCommand.BeginCampaignConquest(sites.collect {
          case BoardTargetRef.Site(site) => site
        }, attackDiceCount))
      case ("campaign-raid", targets) if targets.nonEmpty &&
          targets.head.isInstanceOf[BoardTargetRef.PlayerPawn] &&
          targets.forall {
            case _: BoardTargetRef.PlayerPawn | _: BoardTargetRef.PlayerRelic |
                _: BoardTargetRef.PlayerBanner => true
            case _ => false
          } => Some(GameCommand.BeginCampaignRaid(targets.map(protocolRaidTarget), attackDiceCount))
      case ("challenge", Vector(BoardTargetRef.PlayerBanner(_, banner))) =>
        Some(GameCommand.BeginChallenge(banner))
      case ("negotiation", players) if players.nonEmpty &&
          players.forall(_.isInstanceOf[BoardTargetRef.Player]) =>
        Some(GameCommand.BeginNegotiation(players.collect {
          case BoardTargetRef.Player(id) => id
        }))
      case ("reveal-vision", Vector(BoardTargetRef.PlayerAdviser(owner, vision)))
          if owner == playerId =>
        Some(GameCommand.RevealVision(vision))
      case ("play-conspiracy", Vector(BoardTargetRef.PlayerRelic(owner, relic))) =>
        relic.toIntOption.map(slot => GameCommand.PlayConspiracy(
          Some(ConspiracyTarget.RelicSlot(owner, slot))))
      case ("play-conspiracy", Vector(BoardTargetRef.PlayerBanner(owner, banner))) =>
        Some(GameCommand.PlayConspiracy(
          Some(ConspiracyTarget.Banner(owner, banner))))
      case ("play-conspiracy", Vector()) if action.minimum == 0 &&
          action.maximum == 0 =>
        Some(GameCommand.PlayConspiracy(None))
      case ("muster", Vector(BoardTargetRef.SiteCard(_, kind, id))) =>
        Some(GameCommand.Muster(oathdigital.protocol.EconomyTarget(kind, id)))
      case ("trade-favor", Vector(BoardTargetRef.SiteCard(_, kind, id))) =>
        Some(GameCommand.Trade(oathdigital.protocol.EconomyTarget(kind, id), "favor"))
      case ("trade-secret", Vector(BoardTargetRef.SiteCard(_, kind, id))) =>
        Some(GameCommand.Trade(oathdigital.protocol.EconomyTarget(kind, id), "secret"))
      case _ => None
    }

  private[frontend] def commandForFormation(formation: BoardTargetFormationState,
      playerId: String): Option[GameCommand] =
    (formation.action.actionKind, formation.targets) match {
      case ("campaign-conquest", sites) if sites.nonEmpty &&
          sites.forall(_.isInstanceOf[BoardTargetRef.Site]) =>
        Some(GameCommand.BeginCampaignConquest(sites.collect {
          case BoardTargetRef.Site(site) => site
        }, formation.force))
      case ("campaign-raid", targets) if targets.nonEmpty &&
          targets.head.isInstanceOf[BoardTargetRef.PlayerPawn] &&
          targets.forall {
            case _: BoardTargetRef.PlayerPawn | _: BoardTargetRef.PlayerRelic |
                _: BoardTargetRef.PlayerBanner => true
            case _ => false
          } => Some(GameCommand.BeginCampaignRaid(targets.map(protocolRaidTarget), formation.force))
      case _ => None
    }

  private[frontend] def raidRelocationCommands(
      decision: CampaignRaidRelocation,
      playerId: String): Vector[GameCommand.RelocateCampaignRaidPawn] =
    if (decision.actorPlayerId != playerId) Vector.empty
    else decision.legalSiteIds.map(site => GameCommand.RelocateCampaignRaidPawn(
      decision.decisionId, site))

  private[frontend] def challengeSiteCommands(decision: ChallengeState,
      playerId: String): Vector[GameCommand.ChooseChallengeSecretSite] =
    if (decision.actorPlayerId != playerId) Vector.empty
    else decision.legalSecretSiteIds.map(site =>
      GameCommand.ChooseChallengeSecretSite(decision.decisionId, site))

  private[frontend] def completeChallengeCommand(decision: ChallengeState,
      playerId: String, amount: Int): Option[GameCommand.CompleteChallenge] =
    Option.when(decision.actorPlayerId == playerId &&
      decision.legalSecretSiteIds.isEmpty && amount >= decision.minimumPlacement &&
      amount <= decision.maximumPlacement)(GameCommand.CompleteChallenge(
        decision.decisionId, amount))

  private[frontend] def takeWealthActions(
      value: GameProjection,
      playerId: String
  ): Vector[TakeWealthAction] =
    if (value.phase != "wake" ||
        !viewerPresentation(value, playerId).showGameplayControls) Vector.empty
    else Vector(
      "takeFavor" -> TakeWealthAction(
        "Take Wealth: 1 favor",
        GameCommand.TakeWealth("favor")
      ),
      "takeSecret" -> TakeWealthAction(
        "Take Wealth: 1 secret",
        GameCommand.TakeWealth("secret")
      )
    ).collect {
      case (legalControl, action)
          if value.legalControls.contains(legalControl) => action
    }

  private[frontend] def playerReference(
      value: GameProjection,
      playerId: String
  ): dom.Element = {
    val player = value.players.find(_.playerId == playerId)
    val node = text(
      "span",
      s"player-ref ${player.fold[PlayerColorToken](PlayerColorToken.Neutral)(p =>
        PlayerColorToken.fromKey(p.colorToken)).cssClass}",
      player.fold(playerId)(_.displayName)
    )
    node.setAttribute("data-player-id", playerId)
    node
  }

  private[frontend] def facedownAdviserLaunchCount(minor: MinorActionsState): Int =
    if (minor.advisers.exists(_.placements.nonEmpty)) 1 else 0

  private[frontend] def protocolWorldCard(card: CardDetails): WorldCard =
    WorldCard(card.cardKind, card.cardId)

  private[frontend] def protocolRaidTarget(target: BoardTargetRef): CampaignRaidTarget = target match {
    case BoardTargetRef.PlayerPawn(player) => CampaignRaidTarget.Pawn(player)
    case BoardTargetRef.PlayerRelic(player, relic) => CampaignRaidTarget.Relic(player, relic)
    case BoardTargetRef.PlayerBanner(player, banner) => CampaignRaidTarget.Banner(player, banner)
    case other => throw new IllegalArgumentException(
      s"unsupported Campaign Raid target ${other.stableKey}")
  }

  private[frontend] def protocolCampaignPlan(choice: CampaignPlanChoice): CampaignPlanSource =
    choice.kind match {
      case "adviser" => CampaignPlanSource.Adviser(choice.playerId.get, choice.cardId.get)
      case "site-card" => CampaignPlanSource.SiteCard(choice.siteId.get, choice.cardId.get)
      case "relic" => CampaignPlanSource.Relic(choice.playerId.get, choice.cardId.get)
      case "title" => CampaignPlanSource.Title(choice.playerId.get)
      case other => throw new IllegalArgumentException(s"unknown Campaign plan '$other'")
    }

  private[frontend] def protocolNegotiationTerms(value: NegotiationTermsInput): NegotiationTerms =
    NegotiationTerms(
      value.transfers.map(v => NegotiationTransfer(
        v.recipientPlayerId, v.favor, v.relicIds)),
      value.disclosures.map { disclosure =>
        val information: NegotiationInformation = disclosure.kind match {
          case "adviser" => NegotiationInformation.Adviser(
            disclosure.ownerPlayerId.get,
            WorldCard(disclosure.cardKind.get, disclosure.cardId))
          case "held-relic" => NegotiationInformation.HeldRelic(
            disclosure.ownerPlayerId.get, disclosure.cardId)
          case "site-relic" => NegotiationInformation.SiteRelic(
            disclosure.siteId.get, disclosure.cardId)
        }
        NegotiationDisclosure(disclosure.recipientPlayerId, information)
      })

  private[frontend] def playerDisplayName(
      value: GameProjection,
      playerId: String
  ): String =
    value.players.find(_.playerId == playerId)
      .fold(playerId)(_.displayName)

  private[frontend] def freshGameId(): String =
    s"manual-${js.Date.now().toLong}-${(js.Math.random() * 1000000).toInt}"

  private[frontend] def queryParameter(name: String): Option[String] =
    dom.window.location.search.stripPrefix("?").split("&").toVector
      .flatMap { pair =>
        pair.split("=", 2).toVector match {
          case Vector(key, value) if key == name =>
            Some(js.URIUtils.decodeURIComponent(value))
          case _ => None
        }
      }.headOption.filter(_.nonEmpty)

  private[frontend] def updateUrl(gameId: String, playerId: String): Unit =
    dom.window.history.replaceState(
      null,
      "",
      s"/?mode=server&gameId=${js.URIUtils.encodeURIComponent(gameId)}" +
        s"&playerId=${js.URIUtils.encodeURIComponent(playerId)}"
    )

  private[frontend] def button(label: String, className: String): dom.html.Button = {
    val node =
      dom.document.createElement("button").asInstanceOf[dom.html.Button]
    node.className = className
    node.textContent = label
    node
  }

  private[frontend] def text(tag: String, className: String, value: String): dom.Element = {
    val node = element(tag, className)
    node.textContent = value
    node
  }

  private[frontend] def element(tag: String, className: String): dom.Element = {
    val node = dom.document.createElement(tag)
    if (className.nonEmpty) node.setAttribute("class", className)
    node
  }
}
