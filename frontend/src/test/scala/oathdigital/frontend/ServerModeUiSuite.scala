package oathdigital.frontend

import munit.FunSuite
import oathdigital.presentation._
import oathdigital.protocol.{DecisionAnswerWire, DecisionPlacementWire}

class ServerModeUiSuite extends FunSuite {
  test("secret summaries lead with available over total and explain unavailable tokens") {
    assertEquals(ServerUiSupport.secretSummaryLabel(1, 1, 0, 0),
      "1 available of 1 owned; 0 facedown and 0 committed")
    assertEquals(ServerUiSupport.secretSummaryLabel(0, 1, 0, 1),
      "0 available of 1 owned; 0 facedown and 1 committed")
    assertEquals(ServerUiSupport.secretSummaryLabel(0, 1, 1, 0),
      "0 available of 1 owned; 1 facedown and 0 committed")
    assertEquals(ServerUiSupport.secretSummaryLabel(1, 2, 0, 1),
      "1 available of 2 owned; 0 facedown and 1 committed")
  }
  test("the Recover roll outcome summary shows the target before any roll " +
      "and the accumulated dice and score after") {
    assertEquals(WalkerPanelSupport.rollOutcomeSummary(
      WalkerRollOutcomeState(Vector.empty, 0, 4)),
      "Need 4 shields to succeed.")
    assertEquals(WalkerPanelSupport.rollOutcomeSummary(
      WalkerRollOutcomeState(Vector("blank", "blank"), 0, 4)),
      "Rolled blank, blank -- 0 shields so far (need 4).")
    assertEquals(WalkerPanelSupport.rollOutcomeSummary(
      WalkerRollOutcomeState(Vector("two-shields", "doubler"), 4, 4)),
      "Rolled two-shields, doubler -- 4 shields so far (need 4).")
  }

  test("Negotiation editor restores only authored relic and disclosure selections") {
    val relic = CardDetails("R1", "relic", "Public Relic")
    val adviser = CardDetails("D1", "denizen", "Hidden Adviser")
    val deal = NegotiationState("deal", "red", "S1", Vector("red", "blue", "yellow"),
      Vector.empty, Vector(NegotiationTransferState("red", "blue", 0, 1,
        Vector(relic))), Vector(NegotiationDisclosureState("red", "yellow",
        "adviser", Some(adviser))), 3, Vector(relic), Vector(adviser), Vector.empty)
    assert(ServerUiSupport.negotiationRelicChecked(deal, "red", "blue", "R1"))
    assert(!ServerUiSupport.negotiationRelicChecked(deal, "red", "yellow", "R1"))
    assert(ServerUiSupport.negotiationDisclosureChecked(
      deal, "red", "yellow", "adviser", "D1"))
    assert(!ServerUiSupport.negotiationDisclosureChecked(
      deal, "blue", "yellow", "adviser", "D1"))
    assert(ServerUiSupport.negotiationRelicCompetes("blue", "R1", "yellow", "R1"))
    assert(!ServerUiSupport.negotiationRelicCompetes("blue", "R1", "blue", "R1"))
    assert(!ServerUiSupport.negotiationRelicCompetes("blue", "R2", "yellow", "R1"))
  }
  test("Negotiation editor offers disclosures only for inspectable information") {
    val faceUpRelic = CardDetails("R1", "relic", "Public Relic",
      orientation = Some("face-up"))
    val facedownRelic = CardDetails("R2", "relic", "Secret Relic",
      orientation = Some("face-down"))
    val adviser = CardDetails("D1", "denizen", "Hidden Adviser")
    val siteRelic = CardDetails("R3", "relic", "Bone Dice")
    val deal = NegotiationState("deal", "red", "S1", Vector("red", "blue"),
      Vector.empty, Vector.empty, Vector.empty, 3,
      Vector(faceUpRelic, facedownRelic), Vector(adviser),
      Vector(NegotiationSiteRelicState("site:broken-peaks", siteRelic)))
    val offers = ServerUiSupport.negotiationDisclosureOffers(deal)
    assertEquals(offers.map(o => (o.kind, o.card.cardId, o.siteId)),
      Vector(
        ("adviser", "D1", None),
        ("held-relic", "R2", None),
        ("site-relic", "R3", Some("site:broken-peaks"))))
  }

  /** Task 5: Forge is driven end to end through the shared two-zone
    * interaction. The sections carrying their own labels and minima, the
    * denizen options carrying their own references, and the answer is
    * assembled by generic code -- nothing below states Forge's printed
    * cost, and nothing names a resource.
    */
  private val forgeQuery = DecisionQueryState("partition",
    Vector("1", "2", "3").map(id =>
      DecisionOptionState("denizen", s"denizen:$id", s"Denizen $id")),
    Vector(DecisionSectionState("pay-favor", "Pay Favor", 2),
      DecisionSectionState("pay-secret", "Pay Secret", 1)))

  private val forgeParked = WalkerDecisionState("forge", "forge-9", "decide",
    query = Some(forgeQuery))

  private def forgeItem(index: Int): String =
    WalkerPartitionDraft.itemId(forgeQuery.options(index))

  test("Forge is answered by moving projected options between projected " +
      "sections") {
    val context = BoardSelectionContext("game", "red", 9)
    val initial = WalkerPartitionDraft.reconcile(None, context,
      Some(forgeParked)).get
    // The opening draft fills each section to its projected minimum, in
    // declared order.
    assertEquals(initial.optionsIn("pay-favor").map(_.label),
      Vector("Denizen 1", "Denizen 2"))
    assertEquals(initial.optionsIn("pay-secret").map(_.label),
      Vector("Denizen 3"))
    assert(initial.canConfirm)
    // A confirmed draft answers the decision as one placement per offered
    // option, naming the option's own kind and id.
    assertEquals(initial.command("red"), Some(GameCommand.ResolveWalker(
      "red", "forge-9", DecisionAnswerWire.PartitionWire(
        Vector("pay-favor", "pay-favor", "pay-secret").zipWithIndex.map {
          case (sectionKey, index) =>
            val option = forgeQuery.options(index)
            DecisionPlacementWire(option.kind, option.id, sectionKey) }))))
    // Dragging the third option into the favor zone leaves the secret zone
    // below its projected minimum, so confirmation is refused.
    val invalid = initial.move(forgeItem(2), "pay-favor")
    assert(!invalid.canConfirm)
    assertEquals(invalid.command("red"), None)
    val repaired = invalid.move(forgeItem(0), "pay-secret")
    assert(repaired.canConfirm)
    assertEquals(repaired.optionsIn("pay-favor").map(_.label),
      Vector("Denizen 2", "Denizen 3"))
    assertEquals(repaired.optionsIn("pay-secret").map(_.label),
      Vector("Denizen 1"))
    // A section the query never declared is ignored rather than recorded.
    assertEquals(repaired.move(forgeItem(0), "pay-nothing"), repaired)
  }

  test("a Forge draft is dropped whenever the question changes") {
    val context = BoardSelectionContext("game", "red", 9)
    val initial = WalkerPartitionDraft.reconcile(None, context,
      Some(forgeParked)).get
    val moved = initial.move(forgeItem(2), "pay-favor")
      .move(forgeItem(0), "pay-secret")
    assertEquals(WalkerPartitionDraft.reconcile(Some(moved), context,
      Some(forgeParked)), Some(moved))
    assertEquals(WalkerPartitionDraft.reconcile(Some(moved),
      context.copy(sequence = 10), Some(forgeParked)).get.partition,
      initial.partition)
    assertEquals(WalkerPartitionDraft.reconcile(Some(moved), context,
      Some(forgeParked.copy(decisionId = "forge-new"))).get.partition,
      initial.partition)
    // A power that changes the option set asks a different question, so the
    // draft assembled against the old one is dropped.
    assertEquals(WalkerPartitionDraft.reconcile(Some(moved), context,
      Some(forgeParked.copy(query = Some(forgeQuery.copy(
        options = forgeQuery.options.drop(1)))))).get
        .optionsIn("pay-favor").map(_.label),
      Vector("Denizen 2", "Denizen 3"))
    assertEquals(WalkerPartitionDraft.reconcile(Some(moved), context, None),
      None)
  }

  test("a parked walker decision that is not a partition drives no draft") {
    val context = BoardSelectionContext("game", "red", 9)
    // A choose-one park, and a park whose query was suppressed because an
    // option could not be presented: neither is an answerable partition.
    assertEquals(WalkerPartitionDraft.reconcile(None, context, Some(
      WalkerDecisionState("recover", "recover.choice", "decide",
        query = Some(DecisionQueryState("choose-one", Vector(
          DecisionOptionState("button", "stop", "Stop"))))))), None)
    assertEquals(WalkerPartitionDraft.reconcile(None, context,
      Some(forgeParked.copy(query = None))), None)
  }
  test("selection actions map only authorized single target shapes to commands") {
    val placeholderCandidates = Vector("a", "b", "c", "d").map(id =>
      BoardTargetCandidate(BoardTargetRef.Site(id), id, Vector.empty))
    def action(kind: String) = BoardTargetAction(kind, "Choose", 1, 1,
      false, placeholderCandidates)
    assertEquals(ServerUiSupport.commandForSelection(action("travel"),
      Vector(BoardTargetRef.Site("site:b")), "red"),
      Some(GameCommand.Travel("red", "site:b")))
    assertEquals(ServerUiSupport.commandForSelection(action("campaign-conquest"),
      Vector(BoardTargetRef.Site("site:b")), "red", 4),
      Some(GameCommand.CampaignConquest("red", "site:b", 4)))
    assertEquals(ServerUiSupport.commandForSelection(action("muster"), Vector(
      BoardTargetRef.SiteCard("site", "edifice", "E26")), "red"),
      Some(GameCommand.Muster("red", EconomyTarget("edifice", "E26"))))
    assertEquals(ServerUiSupport.commandForSelection(action("trade-favor"), Vector(
      BoardTargetRef.SiteCard("site", "denizen", "D1")), "red"),
      Some(GameCommand.Trade("red", EconomyTarget("denizen", "D1"), "favor")))
    assertEquals(ServerUiSupport.commandForSelection(action("trade-secret"), Vector(
      BoardTargetRef.SiteCard("site", "denizen", "D1")), "red"),
      Some(GameCommand.Trade("red", EconomyTarget("denizen", "D1"), "secret")))
    assertEquals(ServerUiSupport.commandForSelection(action("travel"), Vector(
      BoardTargetRef.PlayerRelic("red", "R1")), "red"), None)
    assertEquals(ServerUiSupport.commandForSelection(action("campaign-conquest"),
      Vector(BoardTargetRef.Site("site:a"), BoardTargetRef.Site("site:b")),
      "red", 4), Some(GameCommand.CampaignConquest("red",
        Vector("site:a", "site:b"), 4)))
    assertEquals(ServerUiSupport.commandForSelection(action("campaign-conquest"),
      Vector(BoardTargetRef.Site("site:b")), "red", 0),
      Some(GameCommand.CampaignConquest("red", "site:b", 0)))
    val raidTargets = Vector[BoardTargetRef](
      BoardTargetRef.PlayerPawn("blue"),
      BoardTargetRef.PlayerRelic("blue", "R03"),
      BoardTargetRef.PlayerBanner("blue", "peoples-favor"))
    assertEquals(ServerUiSupport.commandForSelection(action("campaign-raid"),
      raidTargets, "red", 2),
      Some(GameCommand.CampaignRaid("red", raidTargets, 2)))
    assertEquals(ServerUiSupport.commandForSelection(action("campaign-raid"),
      raidTargets.tail, "red", 2), None)
    assertEquals(ServerUiSupport.commandForSelection(action("challenge"), Vector(
      BoardTargetRef.PlayerBanner("shared-bank", "peoples-favor")), "red"),
      Some(GameCommand.BeginChallenge("red", "peoples-favor")))
    val negotiation = BoardTargetAction("negotiation", "Choose negotiators", 1, 2,
      false, Vector("blue", "yellow").map(id => BoardTargetCandidate(
        BoardTargetRef.Player(id), id, Vector.empty)))
    assertEquals(ServerUiSupport.commandForSelection(negotiation, Vector(
      BoardTargetRef.Player("blue"), BoardTargetRef.Player("yellow")), "red"),
      Some(GameCommand.BeginNegotiation("red", Vector("blue", "yellow"))))

    val reveal = action("reveal-vision")
    assertEquals(ServerUiSupport.commandForSelection(reveal, Vector(
      BoardTargetRef.PlayerAdviser("red", "vision-conquest")), "red"),
      Some(GameCommand.RevealVision("red", "vision-conquest")))
    assertEquals(ServerUiSupport.commandForSelection(reveal, Vector(
      BoardTargetRef.PlayerAdviser("blue", "vision-conquest")), "red"), None)
    val conspiracy = action("play-conspiracy")
    assertEquals(ServerUiSupport.commandForSelection(conspiracy, Vector(
      BoardTargetRef.PlayerRelic("blue", "1")), "red"),
      Some(GameCommand.PlayConspiracy("red",
        Some(ConspiracyTarget.RelicSlot("blue", 1)))))
    assertEquals(ServerUiSupport.commandForSelection(conspiracy, Vector(
      BoardTargetRef.PlayerBanner("blue", "darkest-secret")), "red"),
      Some(GameCommand.PlayConspiracy("red",
        Some(ConspiracyTarget.Banner("blue", "darkest-secret")))))
    assertEquals(ServerUiSupport.commandForSelection(conspiracy, Vector(
      BoardTargetRef.PlayerRelic("blue", "hidden-id")), "red"), None)
    val noTarget = conspiracy.copy(minimum = 0, maximum = 0)
    assertEquals(ServerUiSupport.commandForSelection(noTarget, Vector.empty, "red"),
      Some(GameCommand.PlayConspiracy("red", None)))
  }

  test("Challenge controls render only owner-authorized site or replacement commands") {
    val sites = ChallengeState("challenge-9", "red", "darkest-secret", None,
      3, Vector("site:a", "site:b"), 4, 6)
    assertEquals(ServerUiSupport.challengeSiteCommands(sites, "red"), Vector(
      GameCommand.ChooseChallengeSecretSite("red", "challenge-9", "site:a"),
      GameCommand.ChooseChallengeSecretSite("red", "challenge-9", "site:b")))
    assertEquals(ServerUiSupport.challengeSiteCommands(sites, "blue"), Vector.empty)
    assertEquals(ServerUiSupport.completeChallengeCommand(sites, "red", 4), None)
    val replacement = sites.copy(banner = "peoples-favor",
      legalSecretSiteIds = Vector.empty)
    assertEquals(ServerUiSupport.completeChallengeCommand(replacement, "red", 4),
      Some(GameCommand.CompleteChallenge("red", "challenge-9", 4)))
    assertEquals(ServerUiSupport.completeChallengeCommand(replacement, "blue", 4), None)
    assertEquals(ServerUiSupport.completeChallengeCommand(replacement, "red", 3), None)
    assertEquals(ServerUiSupport.actionLabel("challenge"), "Challenge")
    assertEquals(ServerUiSupport.actionLabel("peoples-favor"), "People's Favor")
  }

  test("facedown adviser draft supports zero one and multiple choices and only faceup outcomes") {
    val context = BoardSelectionContext("game", "red", 7)
    val card = CardDetails("D1", "denizen", "The Adviser")
    val other = CardDetails("D2", "denizen", "The Other Adviser")
    val replacement = CardDetails("D3", "denizen", "Old Denizen")
    val placements = Vector(MinorAdviserPlacement("discard"),
      MinorAdviserPlacement("play-adviser"),
      MinorAdviserPlacement("play-site", Some(replacement)))
    def minor(advisers: Vector[MinorAdviser]) = MinorActionsState(advisers,
      canPeekSiteRelics = false, Vector.empty, Some("site:a"), 0, 0)
    assertEquals(FacedownAdviserDraft.initial(context, minor(Vector.empty)), None)
    assertEquals(ServerUiSupport.facedownAdviserLaunchCount(minor(Vector.empty)), 0)
    val one = FacedownAdviserDraft.initial(context,
      minor(Vector(MinorAdviser(card, placements)))).get
    assertEquals(one.selectedCardId, Some("D1"))
    assertEquals(ServerUiSupport.facedownAdviserLaunchCount(
      minor(Vector(MinorAdviser(card, placements)))), 1)
    assertEquals(one.command, Some(oathdigital.protocol.GameIntent.StartWalker(
      "play-facedown-adviser", Vector.empty,
      Vector(oathdigital.protocol.WalkerStartArgWire("denizen", "D1")))))
    val many = FacedownAdviserDraft.initial(context, minor(Vector(
      MinorAdviser(card, placements), MinorAdviser(other, placements)))).get
    assertEquals(ServerUiSupport.facedownAdviserLaunchCount(minor(Vector(
      MinorAdviser(card, placements), MinorAdviser(other, placements)))), 1)
    assertEquals(many.selected, None)
    assertEquals(many.choose("D2").selected.map(_.card.cardId), Some("D2"))
    assertEquals(FacedownAdviserDraft.reconcile(Some(many.choose("D2")),
      context, Some(minor(Vector(MinorAdviser(card, placements),
        MinorAdviser(other, placements))))).flatMap(_.selected).map(_.card.cardId),
      Some("D2"))
    assertEquals(FacedownAdviserDraft.reconcile(Some(many.choose("D2")),
      context.copy(sequence = 8), Some(minor(Vector(
        MinorAdviser(card, placements), MinorAdviser(other, placements))))), None)
  }

  test("selection copy exposes details and non-color cardinality instructions") {
    val single = BoardTargetAction("travel", "Travel", 1, 1, false,
      Vector(BoardTargetCandidate(BoardTargetRef.Site("a"), "A", Vector.empty)))
    val multi = single.copy(actionKind = "campaign", maximum = 3,
      candidates = Vector("a", "b", "c").map(id => BoardTargetCandidate(
        BoardTargetRef.Site(id), id, Vector.empty)))
    assert(ServerUiSupport.cardinalityInstruction(single).contains("immediately"))
    assertEquals(ServerUiSupport.cardinalityInstruction(multi),
      "Choose 1 to 3 targets, then confirm.")
    assertEquals(ServerUiSupport.candidateButtonLabel(BoardTargetCandidate(
      BoardTargetRef.Site("b"), "Site B", Vector("2 Supply"))),
      "Site B · 2 Supply")
    assertEquals(ServerUiSupport.candidateButtonLabel(BoardTargetCandidate(
      BoardTargetRef.Site("b"), "Site B",
      Vector("2 Supply", "Commit all 4 board warbands"))),
      "Site B · 2 Supply · Commit all 4 board warbands")
    assertEquals(ServerUiSupport.actionLabel("trade-secret"), "Trade for secrets")
    assertEquals(ServerUiSupport.actionLabel("reveal-vision"), "Reveal Vision")
    assertEquals(ServerUiSupport.actionLabel("play-conspiracy"), "Play Conspiracy")
    assertEquals(ServerUiSupport.cardinalityInstruction(single.copy(
      actionKind = "play-conspiracy", minimum = 0, maximum = 0)),
      "No target is available; confirm to play this action.")
  }

  test("Campaign uses the generic board-target action label") {
    assertEquals(ServerUiSupport.actionLabel("campaign-conquest"), "Campaign")
    assertEquals(ServerUiSupport.actionLabel("campaign-raid"), "Raid")
    val skip = CampaignPlanChoice("skip", None, None, None, None,
      "Use no battle plan", None, 0, 0, "Roll normally")
    val outriders = CampaignPlanChoice("adviser", Some("source"), Some("red"),
      None, Some("143"), "Outriders", Some("denizen.outriders"), 0, 0,
      "Ignore all attack-roll skull losses")
    assertEquals(ServerUiSupport.campaignPlanButtonLabel(skip), "Use no battle plan")
    assertEquals(ServerUiSupport.campaignPlanButtonLabel(outriders), "Outriders")
    val brass = CampaignPlanChoice("relic", Some("relic:red:R25"), Some("red"),
      None, Some("R25"), "Brass Army", Some("relic.brass-army.campaign"), 0, 1,
      "Add 4 attack dice")
    assertEquals(ServerUiSupport.campaignPlanButtonLabel(brass),
      "Brass Army (Place 1 Secret)")
    assertEquals(ServerUiSupport.campaignSelectedPlansLabel(Vector(brass, outriders)),
      "Selected: 1. Brass Army · 2. Outriders")
    val action = BoardTargetAction("campaign-conquest", "Campaign", 1, 1,
      false, Vector(BoardTargetCandidate(BoardTargetRef.Site("site:b"),
        "Site B", Vector.empty)), Some(BoardTargetFormation(1, 4, 4, 2)))
    val formation = BoardTargetFormationState(
      BoardSelectionContext("game", "red", 7), action,
      BoardTargetRef.Site("site:b"), 2)
    assertEquals(ServerUiSupport.campaignFormationSummary(formation),
      "Committed force: 2. Board warbands remaining: 2. " +
        "Attack dice before plans: 2. Cost: 2 Supply.")
    assertEquals(ServerUiSupport.campaignForceChoiceLabel(2), "Commit 2 warbands")
    assertEquals(ServerUiSupport.campaignForceAdjustmentLabel(increase = false),
      "Decrease committed force")
    assertEquals(ServerUiSupport.campaignForceAdjustmentLabel(increase = true),
      "Increase committed force")
    assertEquals(ServerUiSupport.commandForFormation(formation, "red"),
      Some(GameCommand.CampaignConquest("red", "site:b", 2)))
    assertEquals(ServerUiSupport.commandForFormation(formation.copy(
      targets = Vector(BoardTargetRef.PlayerRelic("red", "R1"))), "red"), None)
    val empty = formation.copy(force = 0)
    assertEquals(ServerUiSupport.campaignFormationSummary(empty),
      "Committed force: 0. Board warbands remaining: 4. " +
        "Attack dice before plans: 0. Cost: 2 Supply.")
    assertEquals(ServerUiSupport.commandForFormation(empty, "red"),
      Some(GameCommand.CampaignConquest("red", "site:b", 0)))
    val raid = formation.copy(action = action.copy(actionKind = "campaign-raid"),
      targets = Vector(BoardTargetRef.PlayerPawn("blue"),
        BoardTargetRef.PlayerBanner("blue", "darkest-secret")))
    assertEquals(ServerUiSupport.commandForFormation(raid, "red"),
      Some(GameCommand.CampaignRaid("red", raid.targets, 2)))
  }

  test("available controls use durable ordered presentation categories") {
    assertEquals(ServerUiSupport.actionCategoryOrder.map(_._2),
      Vector("Major actions", "Minor actions", "Powers"))
    assertEquals(ServerUiSupport.actionCategory("travel"), "major")
    assertEquals(ServerUiSupport.actionCategory("challenge"), "major")
    assertEquals(ServerUiSupport.actionCategory("unrecognized-power"), "powers")
    assertEquals(ServerUiSupport.majorFamilyOrder, Vector("search", "travel", "campaign",
      "muster", "trade", "forge", "recover", "challenge"))
    assertEquals(Vector("campaign-conquest", "campaign-raid").map(
      ServerUiSupport.actionFamily), Vector("campaign", "campaign"))
    assertEquals(Vector("trade-favor", "trade-secret").map(
      ServerUiSupport.actionFamily), Vector("trade", "trade"))
  }

  test("Campaign placement distributes locally and clears stale context") {
    val context = BoardSelectionContext("game", "red", 9)
    val targets = Vector(CampaignPlacementTarget("site:a", "Site A"),
      CampaignPlacementTarget("site:b", "Site B"))
    val campaign = CampaignState(decisionId = "campaign-9",
      targetSiteIds = Vector("site:a", "site:b"), force = 3,
      plansFinished = true, planChoices = Vector.empty,
      selectedPlans = Vector.empty, attackDice = Vector.empty, attack = 3,
      skullLosses = 0, maxSacrifice = 3, sacrificed = Some(0),
      defenseDice = Vector.empty, defense = Some(0), victorious = Some(true),
      maxPlacement = 3, placementTargets = targets)
    val initial = CampaignPlacementState.reconcile(None, context,
      Some(campaign)).get
    assertEquals(initial.allocations,
      Vector(CampaignPlacement("site:a", 0), CampaignPlacement("site:b", 0)))
    val distributed = initial.increment("site:a").increment("site:a")
      .increment("site:b").increment("site:b")
    assertEquals(distributed.total -> distributed.remaining, 3 -> 0)
    assertEquals(distributed.count("site:a") -> distributed.count("site:b"),
      2 -> 1)
    assertEquals(distributed.decrement("site:a").remaining, 1)
    assertEquals(distributed.reset.total, 0)
    assertEquals(CampaignPlacementState.reconcile(Some(distributed), context,
      Some(campaign)), Some(distributed))
    assertEquals(CampaignPlacementState.reconcile(Some(distributed),
      context.copy(sequence = 10), Some(campaign)).get.total, 0)
    assertEquals(CampaignPlacementState.reconcile(Some(distributed), context,
      Some(campaign.copy(decisionId = "campaign-new"))).get.total, 0)
    assertEquals(CampaignPlacementState.reconcile(Some(distributed), context,
      Some(campaign.copy(victorious = Some(false)))), None)
  }

  test("Raid relocation commands are scoped to the attacking owner") {
    val decision = CampaignRaidRelocation("raid-9", "red", "blue", "site:a",
      Vector("site:b", "site:c"))
    assertEquals(ServerUiSupport.raidRelocationCommands(decision, "red"), Vector(
      GameCommand.RelocateCampaignRaidPawn("red", "raid-9", "site:b"),
      GameCommand.RelocateCampaignRaidPawn("red", "raid-9", "site:c")))
    assertEquals(ServerUiSupport.raidRelocationCommands(decision, "blue"), Vector.empty)
  }

  test("a parked walker roll classifies as a Roll control carrying the projected pool") {
    val roll = WalkerDecisionState("recover", "walker.recover.roll", "roll",
      pool = Some("recover"), count = Some(2))
    assertEquals(WalkerPanelSupport.recoverWalkerStep(roll),
      Some(WalkerPanelSupport.RecoverWalkerStep.Roll("recover")))
    // No die faces ride the command -- only the projected pool key does.
    assertEquals(GameCommand.RollWalker("red", "recover"),
      oathdigital.protocol.GameIntent.RollWalker("recover"))
  }

  test("a roll park with no projected pool renders no control rather than guessing one") {
    assertEquals(WalkerPanelSupport.recoverWalkerStep(
      WalkerDecisionState("recover", "walker.recover.roll", "roll")), None)
  }

  /** Task 4: both decide parks take their option set from the projected
    * query, and one generic command builder serves both -- a projected
    * option already carries the `kind`/`id` pair a `ChooseOneWire` needs,
    * so the client never has to know which variant it is holding.
    */
  test("the parked Recover choice decision resolves its projected button " +
      "options against its own decision id, distinct from the relic park " +
      "sharing its \"decide\" kind") {
    val continueOption = DecisionOptionState("button", "continue", "Continue")
    val stopOption = DecisionOptionState("button", "stop", "Stop")
    val choiceQuery = DecisionQueryState("choose-one",
      Vector(continueOption, stopOption), heading = Some("Recover"))
    val choice = WalkerDecisionState("recover", "recover.choice", "decide",
      query = Some(choiceQuery))
    // Task 5b: the step carries the whole query, not just its options, so
    // the panel reads the heading the action declared from the same place
    // it reads what to offer.
    assertEquals(WalkerPanelSupport.recoverWalkerStep(choice),
      Some(WalkerPanelSupport.RecoverWalkerStep.Choice(choiceQuery)))
    assertEquals(
      WalkerPanelSupport.resolveChooseOneCommand(choice, continueOption),
      GameCommand.ResolveWalker("red", "recover.choice",
        DecisionAnswerWire.ChooseOneWire("button", "continue")))
    assertEquals(WalkerPanelSupport.resolveChooseOneCommand(choice, stopOption),
      GameCommand.ResolveWalker("red", "recover.choice",
        DecisionAnswerWire.ChooseOneWire("button", "stop")))
    // A power that drops an option drops the control with it: the step
    // carries whatever the projection offered, never a fixed pair.
    val stopOnly = DecisionQueryState("choose-one", Vector(stopOption),
      heading = Some("Recover"))
    assertEquals(WalkerPanelSupport.recoverWalkerStep(
      choice.copy(query = Some(stopOnly))),
      Some(WalkerPanelSupport.RecoverWalkerStep.Choice(stopOnly)))
  }

  test("the parked Recover relic decision offers one control per projected " +
      "option, never a preselected relic") {
    val bronze = DecisionOptionState("relic", "relic-1", "Bronze Idol",
      Some(CardDetails("relic-1", "relic", "Bronze Idol")))
    val silver = DecisionOptionState("relic", "relic-2", "Silver Idol",
      Some(CardDetails("relic-2", "relic", "Silver Idol")))
    val relicQuery = DecisionQueryState("choose-one", Vector(bronze, silver),
      heading = Some("Take a relic"))
    val relic = WalkerDecisionState("recover", "recover.relic", "decide",
      query = Some(relicQuery))
    assertEquals(WalkerPanelSupport.recoverWalkerStep(relic),
      Some(WalkerPanelSupport.RecoverWalkerStep.Relic(relicQuery)))
    assertEquals(WalkerPanelSupport.resolveChooseOneCommand(relic, bronze),
      GameCommand.ResolveWalker("red", "recover.relic",
        DecisionAnswerWire.ChooseOneWire("relic", "relic-1")))
    assertEquals(WalkerPanelSupport.resolveChooseOneCommand(relic, silver),
      GameCommand.ResolveWalker("red", "recover.relic",
        DecisionAnswerWire.ChooseOneWire("relic", "relic-2")))
  }

  test("a decide park whose query was suppressed renders no control, since " +
      "there is no answer the client could safely build") {
    Vector("recover.choice", "recover.relic").foreach(decisionId =>
      assertEquals(WalkerPanelSupport.recoverWalkerStep(
        WalkerDecisionState("recover", decisionId, "decide")), None,
        s"$decisionId must render nothing without a projected query"))
  }

  test("an unrecognized parked walker decision renders no Recover control") {
    assertEquals(WalkerPanelSupport.recoverWalkerStep(
      WalkerDecisionState("recover", "some.other.decision", "decide")), None)
  }

  test("a parked decision for a walker procedure other than Recover renders no " +
      "Recover control, even if it happens to reuse a Recover-shaped kind") {
    assertEquals(WalkerPanelSupport.recoverWalkerStep(
      WalkerDecisionState("teleport", "walker.recover.roll", "roll",
        pool = Some("recover"))), None)
  }

  test("site forces retain accessible labels counts and stable color classes") {
    val cases = Vector(
      SiteForces("exile", 2, "player", Some("red-exile"),
        "Red Warbands", "red") -> ("Red Warbands x2", "force-red"),
      SiteForces("exile", 1, "player", Some("blue-exile"),
        "Blue Warbands", "blue") -> ("Blue Warbands x1", "force-blue"),
      SiteForces("imperial", 1, "empire", None,
        "Imperial Warbands", "empire") -> ("Imperial Warbands x1", "force-empire"),
      SiteForces("bandit", 3, "bandit", None,
        "Bandit Warbands", "bandit") -> ("Bandit Warbands x3", "force-bandit")
    )
    cases.foreach { case (forces, (label, cssClass)) =>
      assertEquals(ServerUiSupport.forceText(forces), label)
      assertEquals(ServerUiSupport.forceCssClass(forces), cssClass)
    }
    assertEquals(GameSite("empty", "Empty", 0, 0, 0, 0, Vector.empty,
      GameSiteRelics(0)).forces, None)
  }

  test("Take Wealth actions use the active-player labels and commands") {
    val actions = ServerUiSupport.takeWealthActions(
      projection(Set("takeFavor", "takeSecret", "endWake")),
      "red-exile"
    )

    assertEquals(
      actions.map(_.label),
      Vector("Take Wealth: 1 favor", "Take Wealth: 1 secret")
    )
    assertEquals(
      actions.map(_.command),
      Vector(
        GameCommand.TakeWealth("red-exile", "favor"),
        GameCommand.TakeWealth("red-exile", "secret")
      )
    )
  }

  test("unavailable Take Wealth actions are absent") {
    assertEquals(
      ServerUiSupport.takeWealthActions(
        projection(Set("endWake")),
        "red-exile"
      ),
      Vector.empty
    )
  }

  test("blocked Take Wealth resource is absent while the legal one remains") {
    val actions = ServerUiSupport.takeWealthActions(
      projection(Set("takeSecret", "endWake")),
      "red-exile"
    )

    assertEquals(actions.map(_.label), Vector("Take Wealth: 1 secret"))
  }

  test("already-used Take Wealth actions are absent from a later phase") {
    assertEquals(
      ServerUiSupport.takeWealthActions(
        projection(Set("takeFavor", "takeSecret"), phase = "act-action-selection"),
        "red-exile"
      ),
      Vector.empty
    )
  }

  test("inactive Wake viewer waits and receives no gameplay controls") {
    val value = projection(
      Set("takeFavor", "takeSecret", "endWake"),
      activeParticipantId = "red-exile"
    )
    val presentation = ServerUiSupport.viewerPresentation(value, "blue-exile")

    assertEquals(presentation.showGameplayControls, false)
    assertEquals(presentation.waitingForPlayerId, Some("red-exile"))
    assertEquals(presentation.waitingForDisplayName, Some("Red Exile"))
    assertEquals(
      ServerUiSupport.takeWealthActions(value, "blue-exile"),
      Vector.empty
    )
  }

  test("Negotiation participants control the procedure regardless of active turn") {
    val deal = NegotiationState("deal", "red-exile", "site:1",
      Vector("red-exile", "blue-exile"), Vector.empty, Vector.empty,
      Vector.empty, 3, Vector.empty, Vector.empty, Vector.empty)
    val participantView = projection(
      Set("replaceNegotiationTerms", "acceptNegotiation", "declineNegotiation"))
      .copy(negotiation = Some(deal))

    val active = ServerUiSupport.viewerPresentation(participantView, "red-exile")
    val offTurn = ServerUiSupport.viewerPresentation(participantView, "blue-exile")
    assert(active.showGameplayControls)
    assert(offTurn.showGameplayControls)
    assert(ServerUiSupport.showNegotiationControls(participantView, offTurn))
    assertEquals(offTurn.waitingForPlayerId, None)
    assertEquals(offTurn.procedureStatus, Some("Negotiation in progress."))
    val nonparticipant = ServerUiSupport.viewerPresentation(
      participantView.copy(negotiation = None, negotiationWaiting = true), "yellow-exile")
    assert(!nonparticipant.showGameplayControls)
    assert(!ServerUiSupport.showNegotiationControls(
      participantView.copy(negotiation = None, negotiationWaiting = true), nonparticipant))
    assertEquals(nonparticipant.waitingForPlayerId, None)
    assertEquals(nonparticipant.procedureStatus,
      Some("Waiting for the negotiation to finish."))
  }

  /** Fix round 1: a parked walker `Decide`'s owner is not always the active
    * participant (Task 5). `walkerDecision` is projected to the owner alone
    * regardless of whose turn it is, so `viewerPresentation` must grant
    * controls off that field directly rather than off `activeParticipantId`
    * -- the bug this guards against left the owner and the active player
    * each waiting on the other.
    */
  test("an off-turn owner of a parked walker decision keeps gameplay controls") {
    val value = projection(Set.empty, activeParticipantId = "red-exile")
      .copy(walkerDecision = Some(forgeParked))
    val owner = ServerUiSupport.viewerPresentation(value, "blue-exile")
    assert(owner.showGameplayControls)
    assertEquals(owner.waitingForPlayerId, None)
    assertEquals(owner.waitingForDisplayName, None)
  }

  test("the active participant waits for the walker decision's off-turn owner") {
    val value = projection(Set.empty, activeParticipantId = "red-exile")
      .copy(walkerWaiting = Some(WalkerWaitingState("blue-exile",
        Some("Choose the Oathkeeper"))))
    val active = ServerUiSupport.viewerPresentation(value, "red-exile")
    assert(!active.showGameplayControls)
    assertEquals(active.waitingForPlayerId, Some("blue-exile"))
    assertEquals(active.waitingForDisplayName, Some("Blue Exile"))
  }

  test("inactive Act viewer sees no action-selection controls") {
    val value = projection(Set("beginRest"), phase = "act-action-selection")
      .copy(actionSelectionOpen = true,
        legalSearchSources = Vector(LegalSearchSource("world", None, 2)),
        legalTravelDestinations = Vector(LegalTravelDestination("site:1", 2)),
        legalMusters = Vector(LegalMuster(EconomyTarget("denizen", "d1"),
          "Muster target", "order", 1, 2)),
        legalTrades = Vector(LegalTrade(EconomyTarget("denizen", "d1"),
          "Trade target", "order", "favor", 1, 2)))
    val inactive = ServerUiSupport.viewerPresentation(value, "blue-exile")
    val active = ServerUiSupport.viewerPresentation(value, "red-exile")

    assertEquals(inactive.waitingForPlayerId, Some("red-exile"))
    assert(!ServerUiSupport.showActActionControls(value, inactive))
    assert(ServerUiSupport.showActActionControls(value, active))
  }

  test("inactive setup viewer waits without pawn or private adviser controls") {
    val value = projection(
      Set("placePawn", "chooseAdviser"),
      phase = "awaiting-adviser",
      activeParticipantId = "red-exile",
      ready = false
    )

    val presentation = ServerUiSupport.viewerPresentation(value, "blue-exile")

    assertEquals(presentation.showGameplayControls, false)
    assertEquals(presentation.waitingForDisplayName, Some("Red Exile"))
  }

  test("active viewer retains Wake and setup gameplay controls") {
    val wake = projection(Set("takeFavor", "endWake"))
    assert(ServerUiSupport.viewerPresentation(
      wake,
      "red-exile"
    ).showGameplayControls)
    assertEquals(
      ServerUiSupport.takeWealthActions(wake, "red-exile").map(_.label),
      Vector("Take Wealth: 1 favor")
    )

    val setup = projection(
      Set("chooseAdviser"),
      phase = "awaiting-adviser",
      ready = false
    )
    assert(ServerUiSupport.viewerPresentation(
      setup,
      "red-exile"
    ).showGameplayControls)
  }

  test("board target classes distinguish candidate selected and read-only state") {
    assertEquals(ServerUiSupport.siteTargetClasses(false, false),
      "site site-readonly")
    assertEquals(ServerUiSupport.siteTargetClasses(true, false),
      "site board-target")
    assertEquals(ServerUiSupport.siteTargetClasses(true, true),
      "site board-target board-target-selected")
    assert(ServerUiSupport.cardTargetClasses(true, true)
      .contains("board-target-selected"))
  }

  test("round tracker geometry is eight circular ring wedges") {
    val segments = (1 to 8).map(WorldBoardRenderer.roundSegment)
    assertEquals(segments.map(_.path).distinct.size, 8)
    segments.foreach { segment =>
      assert(segment.path.startsWith("M "))
      assertEquals(" A ".r.findAllIn(segment.path).length, 2)
      assert(segment.path.contains(" L "))
      assert(segment.path.endsWith(" Z"))
      assert(segment.labelX >= 30.0 && segment.labelX <= 170.0)
      assert(segment.labelY >= 30.0 && segment.labelY <= 170.0)
    }
    val limiter = segments(3)
    val markerRadius = Math.hypot(limiter.markerX - 100.0, limiter.markerY - 100.0)
    val labelRadius = Math.hypot(limiter.labelX - 100.0, limiter.labelY - 100.0)
    assert(markerRadius > labelRadius)
  }

  test("winner banner class resolves the winner's stable color token") {
    assertEquals(ServerUiSupport.winnerColorClass(
      projection(Set.empty), "red-exile"), "player-red")
    assertEquals(ServerUiSupport.winnerColorClass(
      projection(Set.empty), "missing"), "player-neutral")
  }

  test("visible target detail badges contain details without duplicating names") {
    val candidate = BoardTargetCandidate(BoardTargetRef.Site("site:a"),
      "Ancient City", Vector("2 Supply", "+1 warband"))
    val badge = ServerUiSupport.candidateDetailText(candidate)
    assertEquals(badge, Some("2 Supply · +1 warband"))
    assert(!badge.get.contains(candidate.label))
    assertEquals(ServerUiSupport.candidateDetailText(candidate.copy(details = Vector.empty)), None)
  }

  test("card-decision zone helpers are specific to starting advisers") {
    val adviser = PendingCardDecision("d", "starting-adviser", "red", "Choose",
      Vector.empty, Vector(CardDetails("a", "denizen", "A")), 1, 1, false, Map.empty)
    val search = adviser.copy(kind = "search", prompt = "Resolve search",
      orderingRequired = true)
    assertEquals(ServerUiSupport.cardDecisionZoneHelpers(adviser),
      ServerUiSupport.CardDecisionZoneHelpers("Move exactly one adviser to Keep.",
        "The remaining candidates are discarded in order."))
    assertEquals(ServerUiSupport.cardDecisionZoneHelpers(search).discard,
      "The remaining cards are discarded in order.")
    assert(!ServerUiSupport.cardDecisionZoneHelpers(search).keep.contains("adviser"))
  }

  test("targetable players and banners render exactly one detail badge") {
    val candidates = Vector(
      BoardTargetCandidate(BoardTargetRef.Player("blue"), "Blue", Vector("1 Favor")),
      BoardTargetCandidate(BoardTargetRef.PlayerBanner("blue", "peoples-favor"),
        "People's Favor", Vector("2 Defense", "3 Favor")))
    candidates.foreach(candidate => assertEquals(
      ServerUiSupport.candidateDetailBadgeTexts(candidate).size, 1))
  }

  test("shared-bank Challenge target identity selects only projected legal banners") {
    val legal = BoardTargetRef.PlayerBanner("shared-bank", "peoples-favor")
    val illegal = BoardTargetRef.PlayerBanner("shared-bank", "darkest-secret")
    val action = BoardTargetAction("challenge", "Choose a banner", 1, 1,
      autoActivate = false, Vector(BoardTargetCandidate(legal,
        "People's Favor", Vector("1 Supply"))))
    val state = BoardTargetSelectionState.reconcile(None,
      BoardSelectionContext("game", "red", 2), Vector(action))
      .activate("challenge")
    assert(ServerUiSupport.candidateForTarget(Some(state), legal).nonEmpty)
    assertEquals(ServerUiSupport.candidateForTarget(Some(state), illegal), None)
  }

  test("populated site details render properties, stable IDs, and hidden relics") {
    val site = GameSite(
      "site:woods",
      "Woods",
      looseFavor = 2,
      looseSecrets = 1,
      denizenCapacity = 3,
      relicCapacity = 2,
      denizens = Vector(
        GameSiteCard("denizen:fox", "Fox"),
        GameSiteCard("denizen:owl", "Owl")
      ),
      relics = GameSiteRelics(2)
    )
    val details = SiteCardPresentation.from(site)

    assertEquals(details.metrics, Vector(
      SiteMetric("Favor", "2"),
      SiteMetric("Secrets", "1"),
      SiteMetric("Defense", "0")
    ))
    assertEquals(site.denizens.map(_.label), Vector("Fox", "Owl"))
    assertEquals(site.denizens.map(_.denizenId),
      Vector("denizen:fox", "denizen:owl"))
    assertEquals(details.relicSummary, "2 facedown relics")
    assertEquals(details.unknownRelicCount, 2)
  }

  test("peeked site relics replace opaque slots only for the scoped viewer") {
    val known = CardDetails("R1", "relic", "Ancient Crown", rulesText = Some("Rule"))
    val owner = SiteCardPresentation.from(GameSite("site", "Site", 0, 0, 0, 2,
      Vector.empty, GameSiteRelics(2, Vector(known))))
    val other = SiteCardPresentation.from(GameSite("site", "Site", 0, 0, 0, 2,
      Vector.empty, GameSiteRelics(2)))
    assertEquals(owner.unknownRelicCount, 1)
    assertEquals(other.unknownRelicCount, 2)
    assertEquals(owner.peekedRelics.map(_.card.name), Vector("Ancient Crown"))
    assert(owner.peekedRelics.forall(_.concealedAtRest))
    assertEquals(owner.peekedRelics.head.revealInteractions,
      Vector("hover", "focus", "press-and-hold"))
    assertEquals(other.peekedRelics, Vector.empty)
  }

  test("empty site details have image-independent empty states") {
    val details = SiteCardPresentation.from(GameSite(
      "site:empty",
      "Empty",
      0,
      0,
      0,
      0,
      Vector.empty,
      GameSiteRelics(0)
    ))

    assertEquals(details.metrics.map(_.value), Vector("0", "0", "0"))
    assertEquals(details.denizenEmpty, "None")
    assertEquals(details.relicSummary, "None")
  }

  test("site metric uses Forge cost instead of Recover difficulty") {
    val forged = SiteCardPresentation.from(GameSite("forge", "Forge", 0, 0,
      3, 0, Vector.empty, GameSiteRelics(0), recoverDifficulty = Some(4),
      forgeCost = Some(ForgeCost(2, 1))))
    assert(forged.metrics.contains(SiteMetric("Forge cost", "2 favor · 1 secrets")))
    assert(!forged.metrics.exists(_.label == "Recover difficulty"))

    val recover = SiteCardPresentation.from(GameSite("recover", "Recover", 0, 0,
      2, 0, Vector.empty, GameSiteRelics(0), recoverDifficulty = Some(3)))
    assert(recover.metrics.contains(SiteMetric("Recover difficulty", "3")))
    assert(!recover.metrics.exists(_.label == "Forge cost"))
  }

  test("pile symbols and shape classes distinguish public tops and empty piles") {
    assertEquals(ServerUiSupport.pileSymbol(2, Some("denizen")), "D")
    assertEquals(ServerUiSupport.pileSymbol(1, Some("vision")), "V")
    assertEquals(ServerUiSupport.pileSymbol(0, None), "")
    assertEquals(ServerUiSupport.pileCardClasses(2), "pile-card pile-back")
    assertEquals(ServerUiSupport.pileCardClasses(0), "pile-card pile-empty")
  }

  test("site and denizen visuals deterministically fall back without assets") {
    val details = SiteCardPresentation.from(GameSite(
      "woods",
      "Woods",
      0,
      0,
      1,
      0,
      Vector(GameSiteCard("fox", "Fox")),
      GameSiteRelics(0)
    ))

    assertEquals(details.siteVisual.instruction,
      VisualInstruction.Placeholder(
        "W", "Woods", AccessibleLabel("Woods")
      ))
    assertEquals(details.denizenVisuals.head._2.instruction,
      VisualInstruction.Placeholder("F", "Fox", AccessibleLabel("Fox")))
  }

  test("failed and stale assets share the same accessible fallback") {
    val expected = ImageRef("sites/woods.webp")
    val site = SiteView(
      ViewId("site:woods"),
      AccessibleLabel("Woods"),
      Some(expected),
      FallbackVisual("W", "Woods")
    )
    val fallback = VisualInstruction.Placeholder(
      "W", "Woods", AccessibleLabel("Woods")
    )

    assertEquals(VisualRenderPlan.from(
      site, ImageLoadResult.Failed(expected)).instruction, fallback)
    assertEquals(VisualRenderPlan.from(
      site,
      ImageLoadResult.Loaded(ImageRef("sites/mine.webp"))
    ).instruction, fallback)
  }

  test("an image load error deterministically selects the labelled fallback") {
    val expected = ImageRef("sites/woods.webp")
    val site = SiteView(
      ViewId("site:woods"),
      AccessibleLabel("Woods"),
      Some(expected),
      FallbackVisual("W", "Woods")
    )
    val plan = VisualRenderPlan.from(
      site,
      ImageLoadResult.Loaded(expected)
    )

    assertEquals(plan.instruction,
      VisualInstruction.Image(expected, AccessibleLabel("Woods")))
    assertEquals(plan.afterFailure, VisualInstruction.Placeholder(
      "W", "Woods", AccessibleLabel("Woods")
    ))
  }

  test("a legal phase power becomes one usePower command") {
    val power = PhasePowerState("denizen.silver-tongue",
      DecisionOptionState("denizen", "92", "Silver Tongue"),
      "Silver Tongue", "Take a favor.")
    val legal = projection(Set("usePower:denizen.silver-tongue:92", "finishRest"),
      phase = "rest", phasePowers = Vector(power))
    assertEquals(PhasePowerButtons.actions(legal), Vector(power ->
      oathdigital.protocol.GameIntent.UsePower("denizen.silver-tongue",
        oathdigital.protocol.WalkerStartArgWire("denizen", "92"))))
    assertEquals(PhasePowerButtons.actions(projection(Set("finishRest"),
      phase = "rest", phasePowers = Vector(power))), Vector.empty)
  }

  test("Finish Rest is offered only when finishRest is legal") {
    assert(PhasePowerButtons.showsFinishRest(projection(Set("finishRest"),
      phase = "rest")))
    assert(!PhasePowerButtons.showsFinishRest(projection(Set.empty,
      phase = "rest")))
    assert(!PhasePowerButtons.showsFinishRest(projection(Set("finishRest"),
      phase = "wake")))
  }

  private def projection(
      legalControls: Set[String],
      phase: String = "wake",
      activeParticipantId: String = "red-exile",
      ready: Boolean = true,
      phasePowers: Vector[PhasePowerState] = Vector.empty
  ): GameProjection =
    GameProjection(
      gameId = "game-1",
      nextSequence = 8L,
      phase = phase,
      activeParticipantId = Some(activeParticipantId),
      players = Vector(
        GamePlayer(
          "red-exile",
          "Red Exile",
          "exile",
          PlayerColorToken.Red
        ),
        GamePlayer(
          "blue-exile",
          "Blue Exile",
          "exile",
          PlayerColorToken.Blue
        )
      ),
      world = Vector.empty,
      pawnLocations = Vector.empty,
      legalControls = legalControls.toVector.sorted,
      ready = ready,
      completed = false,
      phasePowers = phasePowers
    )

  /** Task 5: a `GameProjection` parked on the Forge decision above, for the
    * waiting-notice test below. Reuses `forgeParked` -- the same
    * `WalkerDecisionState` the "Forge is answered by..." test builds and
    * asserts against -- rather than authoring a second, possibly diverging
    * walker decision.
    */
  private def forgeProjection: GameProjection =
    projection(Set.empty).copy(walkerDecision = Some(forgeParked))

  test("a parked walker waiting on another player names them and the question") {
    val waitingOn = forgeProjection.copy(walkerDecision = None,
      walkerWaiting = Some(WalkerWaitingState(
        forgeProjection.players.head.playerId, Some("Choose the Oathkeeper"))))
    assertEquals(WalkerPanelSupport.waitingNotice(waitingOn),
      Some(s"Waiting for ${forgeProjection.players.head.displayName}: " +
        "Choose the Oathkeeper"))
    assertEquals(WalkerPanelSupport.waitingNotice(
      waitingOn.copy(walkerWaiting = None)), None)
  }
  test("a choose-one decision outside Recover is answered from its projected options") {
    val decision = WalkerDecisionState("oathkeeper", "oathkeeper.recipient",
      "decide", query = Some(DecisionQueryState("choose-one", Vector(
        DecisionOptionState("player", "blue", "blue"),
        DecisionOptionState("player", "yellow", "yellow")),
        heading = Some("Choose the Oathkeeper"))))
    assertEquals(WalkerPanelSupport.chooseOneStep(decision).map(_.options.map(_.id)),
      Some(Vector("blue", "yellow")))
    assertEquals(WalkerPanelSupport.chooseOneStep(decision.copy(action = "recover")),
      None)
    assertEquals(WalkerPanelSupport.resolveChooseOneCommand(decision,
      decision.query.get.options(1)),
      GameCommand.ResolveWalker("red", "oathkeeper.recipient",
        DecisionAnswerWire.ChooseOneWire("player", "yellow")))
  }
}
