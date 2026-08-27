package oathdigital.frontend

import munit.FunSuite
import oathdigital.presentation._

class ServerModeUiSuite extends FunSuite {
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

  test("Forge assignment state enforces cardinality and resets stale context") {
    val context = BoardSelectionContext("game", "red", 9)
    val targets = Vector("1", "2", "3").map(id =>
      ForgeTarget("site:a", s"denizen:$id", s"Denizen $id"))
    val forge = ForgeState("forge-9", "red", 2, 1, targets)
    val initial = ForgeAssignmentState.reconcile(None, context, Some(forge)).get
    assertEquals(initial.assignments, Vector("favor", "favor", "secret"))
    assert(initial.canConfirm)
    assertEquals(initial.command("red"), Some(GameCommand.CompleteForge(
      "red", "forge-9", targets.zip(initial.assignments))))
    val invalid = initial.choose(2, "favor")
    assert(!invalid.canConfirm)
    assertEquals(invalid.command("red"), None)
    val repaired = invalid.choose(0, "secret")
    assert(repaired.canConfirm)
    assertEquals(repaired.assignments.count(_ == "favor") ->
      repaired.assignments.count(_ == "secret"), 2 -> 1)
    assertEquals(ForgeAssignmentState.reconcile(Some(repaired), context,
      Some(forge)), Some(repaired))
    assertEquals(ForgeAssignmentState.reconcile(Some(repaired),
      context.copy(sequence = 10), Some(forge)).get.assignments,
      initial.assignments)
    assertEquals(ForgeAssignmentState.reconcile(Some(repaired), context,
      Some(forge.copy(decisionId = "forge-new"))).get.assignments,
      initial.assignments)
    assertEquals(ForgeAssignmentState.reconcile(Some(repaired), context, None), None)
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
    val secretSite = action("conspiracy-secret-site")
      .copy(decisionId = Some("conspiracy-12"))
    assertEquals(ServerUiSupport.commandForSelection(secretSite,
      Vector(BoardTargetRef.Site("site:b")), "red"),
      Some(GameCommand.ChooseConspiracySecretSite(
        "red", "conspiracy-12", "site:b")))
    assertEquals(ServerUiSupport.commandForSelection(secretSite.copy(decisionId = None),
      Vector(BoardTargetRef.Site("site:b")), "red"), None)
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

  test("minor adviser controls map only projected typed placements") {
    val card = CardDetails("D1", "denizen", "The Adviser")
    val replacement = CardDetails("D2", "denizen", "The Old Denizen")
    val adviser = MinorAdviser(card, Vector.empty)
    assertEquals(ServerUiSupport.minorAdviserCommand(adviser,
      MinorAdviserPlacement("discard"), "red"),
      Some(GameCommand.DiscardFacedownAdviser("red", card)))
    assertEquals(ServerUiSupport.minorAdviserCommand(adviser,
      MinorAdviserPlacement("play-adviser"), "red"),
      Some(GameCommand.PlayFacedownAdviser("red", card, "adviser-face-up")))
    assertEquals(ServerUiSupport.minorAdviserCommand(adviser,
      MinorAdviserPlacement("play-site", Some(replacement)), "red"),
      Some(GameCommand.PlayFacedownAdviser("red", card, "site", Some(replacement))))
    assertEquals(ServerUiSupport.minorAdviserCommand(adviser,
      MinorAdviserPlacement("unsupported"), "red"), None)
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
      None, Some("R25"), "Brass Army", Some("relic.brass-army"), 0, 1,
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

  test("targetable players and banners render exactly one detail badge") {
    val candidates = Vector(
      BoardTargetCandidate(BoardTargetRef.Player("blue"), "Blue", Vector("1 Favor")),
      BoardTargetCandidate(BoardTargetRef.PlayerBanner("blue", "peoples-favor"),
        "People's Favor", Vector("2 Defense", "3 Favor")))
    candidates.foreach(candidate => assertEquals(
      ServerUiSupport.candidateDetailBadgeTexts(candidate).size, 1))
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

  private def projection(
      legalControls: Set[String],
      phase: String = "wake",
      activeParticipantId: String = "red-exile",
      ready: Boolean = true
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
      completed = false
    )
}
