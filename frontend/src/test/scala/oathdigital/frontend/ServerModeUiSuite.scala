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

  test("banner and Challenge action labels are presentable") {
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
    assertEquals(ServerUiSupport.facedownAdviserLaunchCount(
      minor(Vector(MinorAdviser(card, Vector.empty)))), 1)
    assert(FacedownAdviserDraft.initial(context,
      minor(Vector(MinorAdviser(card, Vector.empty)))).nonEmpty)
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
    assertEquals(ServerUiSupport.cardinalityInstruction(single.copy(
      actionKind = "travel", minimum = 0, maximum = 0)),
      "No target is available; confirm to play this action.")
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
        legalTravelDestinations = Vector(LegalTravelDestination("site:1", 2)))
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
    assertEquals(ServerUiSupport.cardDecisionZoneHelpers(adviser),
      ServerUiSupport.CardDecisionZoneHelpers("Move exactly one adviser to Keep.",
        "The remaining candidates are discarded in order."))
  }

  test("targetable players and sites render exactly one detail badge") {
    val candidates = Vector(
      BoardTargetCandidate(BoardTargetRef.Player("blue"), "Blue", Vector("1 Favor")),
      BoardTargetCandidate(BoardTargetRef.Site("b"),
        "Site B", Vector("2 Defense", "3 Favor")))
    candidates.foreach(candidate => assertEquals(
      ServerUiSupport.candidateDetailBadgeTexts(candidate).size, 1))
  }

  test("target identity selects only projected legal candidates") {
    val legal = BoardTargetRef.Site("a")
    val illegal = BoardTargetRef.Site("b")
    val action = BoardTargetAction("challenge", "Choose a site", 1, 1,
      autoActivate = false, Vector(BoardTargetCandidate(legal,
        "Site A", Vector("1 Supply"))))
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
  test("selection actions map only authorized single target shapes to commands") {
    val placeholderCandidates = Vector("a", "b", "c", "d").map(id =>
      BoardTargetCandidate(BoardTargetRef.Site(id), id, Vector.empty))
    def action(kind: String) = BoardTargetAction(kind, "Choose", 1, 1,
      false, placeholderCandidates)
    assertEquals(ServerUiSupport.commandForSelection(action("travel"),
      Vector(BoardTargetRef.Site("site:b")), "red"),
      Some(oathdigital.protocol.GameIntent.StartWalker("travel", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("site", "site:b")))))
    assertEquals(ServerUiSupport.commandForSelection(action("travel"), Vector(
      BoardTargetRef.PlayerAdviser("red", "R1")), "red"), None)
    // Campaign is no longer a board-target selection: it starts from its own
    // control and asks its questions as walker decisions.
    assertEquals(ServerUiSupport.commandForSelection(action("campaign-conquest"),
      Vector(BoardTargetRef.Site("site:b")), "red"), None)
  }
  test("available controls use durable ordered presentation categories") {
    assertEquals(ServerUiSupport.actionCategoryOrder.map(_._2),
      Vector("Major actions", "Minor actions", "Powers"))
    assertEquals(ServerUiSupport.actionCategory("travel"), "major")
    assertEquals(ServerUiSupport.actionCategory("challenge"), "major")
    assertEquals(ServerUiSupport.actionCategory("campaign"), "major")
    assertEquals(ServerUiSupport.actionCategory("unrecognized-power"), "powers")
    assertEquals(ServerUiSupport.majorFamilyOrder, Vector("search", "travel", "campaign",
      "muster", "trade", "forge", "recover", "challenge"))
    assertEquals(Vector("trade-favor", "trade-secret").map(
      ServerUiSupport.actionFamily), Vector("trade", "trade"))
  }
}
