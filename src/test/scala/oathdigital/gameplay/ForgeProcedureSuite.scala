package oathdigital.gameplay

import oathdigital.gameplay.actions.ForgeRules
import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.operations._
import oathdigital.gameplay.powerresolver.PowerWindow
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.walker.{ChoicePayload, DecisionQueries,
  ProcedureWalker, WalkerOutcome, WalkerPowers, WalkerStepPayload,
  WalkerStepRecorded}
import oathdigital.gameplay.OathState.Ready
import oathdigital.model.DecisionAnswer.PartitionAnswer
import oathdigital.model._

/** Task 2: the declared Forge tree reproduces the legacy `Forge.handle`/
  * `Forge.evolve` observable flow on `ProcedureWalker.advance`/`resolve`.
  *
  * Nothing here goes through the registry or the application layer -- Forge is
  * not a registered walker action until Task 3 -- so every walk drives
  * `ProcedureWalker` directly against a tree `ForgeProcedure.build`/`rebuild`
  * returned, exactly the way `RecoverProcedureSuite` proved Recover's tree
  * before its cutover.
  */
class ForgeProcedureSuite extends munit.FunSuite
    with WalkerRecordedOpsReducer {
  import ForgeProcedureSuite.Forgeable

  private val setup = new FirstGameSetupRules(catalog)

  /** Forge declares no `ContributingPower` (Task 2 ports the tree only;
    * `ForgePowers.powers` is empty), so every walk states an empty power
    * source explicitly rather than relying on a default.
    */
  private val noPowers: WalkerPowers = WalkerPowers.empty

  /** Full setup finished; the active player's pawn rules an in-play site with
    * a printed Forge cost and exactly three empty faceup denizens, with a
    * non-empty relic deck, full supply, and enough favor and secrets in the
    * actor's own play area to pay the printed cost.
    *
    * The site is chosen by its printed cost rather than taken as whichever
    * comes first, because the printed split is now load-bearing: a cost
    * naming both resources parks and prompts, and a cost of three of one
    * resource deliberately does not (see [[ForgeProcedure.parks]]). Both
    * shapes exist in the shipped catalog and both are exercised below.
    */
  private def forgeable: Forgeable = forgeableWhere(cost =>
    cost.favor > 0 && cost.secrets > 0)

  private def forgeableWhere(printed: Tokens => Boolean): Forgeable = {
    val Ready(base) = execute(setup)._1: @unchecked
    val actor0 = base.game.current.players.find(
      _.player == base.game.current.turn.activePlayer).get
    val actor = actor0.copy(board = actor0.board.copy(favor = 5,
      faceUpSecrets = 5))
    val definition = catalog.sites.find(site =>
      site.forgeRequirements.exists(printed) &&
        base.game.current.map.inPlay.contains(site.id))
      .getOrElse(fail("fixture needs an in-play site with a matching cost"))
    val siteId = definition.id
    val ids = catalog.denizens.take(3).map(d => DenizenId(d.id.value))
    val denizens = ids.map(DenizenState(_, Orientation.FaceUp, Tokens.empty))
    val site = base.game.current.map.sites(siteId).copy(
      forces = SiteForces.Occupied(ForceKind.Exile(actor.lineage), 1),
      denizens = denizens)
    val moved = actor.copy(pawnSite = Some(siteId))
    val ready = base.copy(game = base.game.copy(current =
      base.game.current.copy(
        turn = base.game.current.turn.copy(phase = Phase.Act),
        commonCards = base.game.current.commonCards.copy(worldDeck =
          base.game.current.commonCards.worldDeck.filterNot(ids.toSet)),
        players = base.game.current.players.map(p =>
          if (p.player == actor.player) moved else p),
        map = base.game.current.map.copy(sites =
          base.game.current.map.sites.updated(siteId, site)))))
    Forgeable(ready, moved, siteId, ids.map(SiteDenizenTarget(siteId, _)),
      definition.forgeRequirements.get,
      ready.game.current.commonCards.relicDeck.head)
  }

  private def siteOf(state: ReadyGame, siteId: SiteId): SiteState =
    state.game.current.map.sites(siteId)

  private def withSite(state: ReadyGame, siteId: SiteId,
      site: SiteState): ReadyGame =
    state.copy(game = state.game.copy(current = state.game.current.copy(
      map = state.game.current.map.copy(sites =
        state.game.current.map.sites.updated(siteId, site)))))

  private def mapPlayer(state: ReadyGame, player: PlayerId)(
      update: PlayerState => PlayerState): ReadyGame =
    state.copy(game = state.game.copy(current = state.game.current.copy(
      players = state.game.current.players.map(p =>
        if (p.player == player) update(p) else p))))

  private def withSupply(state: ReadyGame, player: PlayerId,
      amount: Int): ReadyGame = mapPlayer(state, player)(p =>
    p.copy(board = p.board.copy(supply = SupplyTrack(amount))))

  private def withPawn(state: ReadyGame, player: PlayerId,
      site: Option[SiteId]): ReadyGame =
    mapPlayer(state, player)(_.copy(pawnSite = site))

  private def supplyOf(state: ReadyGame, player: PlayerId): Int =
    state.game.current.players.find(_.player == player).get.board.supply.supply

  /** The printed cost spread over the three eligible targets in target order,
    * as partition placements -- the legal answer every accepting test uses.
    */
  private def legalPlacements(f: Forgeable): Vector[DecisionPlacement] =
    f.targets.zip(Vector.fill(f.cost.favor)(ForgeProcedure.favorSectionKey) ++
      Vector.fill(f.cost.secrets)(ForgeProcedure.secretSectionKey)).map {
      case (target, section) =>
        DecisionPlacement(DecisionOptionRef.Denizen(target.denizenId), section)
    }

  private def answerOf(placements: Vector[DecisionPlacement], by: PlayerId)
      : Answered =
    Answered(ForgeProcedure.assignmentDecisionId,
      PartitionAnswer(placements), by)

  /** The `PayCost` a placement in `section` onto `denizen` must produce: out
    * of the ACTOR'S own play area, never a suit bank.
    */
  private def expectedPayment(actor: PlayerId, denizen: DenizenId,
      section: String): CoreOperation =
    PayCost(actor, Location.OnCard(denizen),
      if (section == ForgeProcedure.favorSectionKey) Cost(favor = 1)
      else Cost(secret = 1))


  private def rejects(result: Either[OathViolation, Operation]): OathViolation =
    result match {
      case Left(violation) => violation
      case Right(_) => fail("expected the Forge start gate to reject")
    }

  private def expectParked(outcome: Either[OathViolation, WalkerOutcome],
      at: Vector[String]): (PendingTree, Vector[OathEvent]) =
    outcome match {
      case Right(WalkerOutcome.Parked(pending, events)) =>
        assertEquals(pending.at, at)
        (pending, events)
      case other => fail(s"expected a Parked outcome at $at, got $other")
    }

  private def expectFinished(outcome: Either[OathViolation, WalkerOutcome])
      : (ReadyGame, Vector[WalkerStepRecorded]) = outcome match {
    case Right(WalkerOutcome.Finished(treeless, events)) =>
      (treeless, events.map(_.asInstanceOf[WalkerStepRecorded]))
    case other => fail(s"expected a Finished outcome, got $other")
  }

  /** Walks a fresh tree to the assignment park, folding the recorded supply
    * payment forward the way journal replay would, and hands back the park,
    * the state at the park, and the steps recorded on the way.
    */
  private def parkAtAssignment(state: ReadyGame, tree: Operation)
      : (PendingTree, ReadyGame, Vector[WalkerStepRecorded]) = {
    // The decision sits inside a `Branch` (its options are the eligible
    // targets, read live), so the park addresses the branch's selected child.
    val (pending, events) = expectParked(
      ProcedureWalker.advance(state, tree, None, noPowers), Vector("1", "0"))
    (pending, foldRecordedOps(state, events, "Forge supply payment"),
      events.map(_.asInstanceOf[WalkerStepRecorded]))
  }

  private def resolveWith(f: Forgeable, state: ReadyGame, tree: Operation,
      pending: PendingTree, placements: Vector[DecisionPlacement])
      : Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.resolve(state, tree, pending,
      answerOf(placements, f.actor.player), noPowers)

  private def rejection(outcome: Either[OathViolation, WalkerOutcome])
      : String = outcome match {
    case Left(OathViolation.InvalidEventOrder(detail)) => detail
    case other => fail(s"expected an InvalidEventOrder rejection, got $other")
  }

  // ---------------------------------------------------------------------
  // P1: one test per start gate in `ForgeRules.validate`.
  // ---------------------------------------------------------------------

  test("P1: build rejects an actor whose pawn is not at a site") {
    val f = forgeable
    assertEquals(rejects(ForgeProcedure.build(catalog,
      withPawn(f.ready, f.actor.player, None), f.actor.player)),
      OathViolation.PawnSiteMissing(f.actor.player): OathViolation)
  }

  test("P1: build rejects a site the actor does not rule") {
    val f = forgeable
    val contested = withSite(f.ready, f.siteId, siteOf(f.ready, f.siteId).copy(
      forces = SiteForces.Occupied(ForceKind.Bandit, 1)))
    assertEquals(
      rejects(ForgeProcedure.build(catalog, contested, f.actor.player)),
      OathViolation.ForgeUnavailable(
        "actor does not rule their pawn site"): OathViolation)
  }

  test("P1: build rejects a site with no printed Forge cost") {
    val f = forgeable
    val plain = catalog.sites.find(definition =>
      definition.forgeRequirements.isEmpty &&
        f.ready.game.current.map.inPlay.contains(definition.id)).get.id
    val ruled = withSite(f.ready, plain, siteOf(f.ready, plain).copy(
      forces = SiteForces.Occupied(ForceKind.Exile(f.actor.lineage), 1)))
    val state = withPawn(ruled, f.actor.player, Some(plain))
    assertEquals(rejects(ForgeProcedure.build(catalog, state, f.actor.player)),
      OathViolation.ForgeUnavailable(
        "site has no printed Forge cost"): OathViolation)
  }

  test("P1: build rejects a printed cost that does not total three resources") {
    val f = forgeable
    val altered = catalog.copy(sites = catalog.sites.map(definition =>
      if (definition.id != f.siteId) definition
      else definition.copy(forgeRequirements = Some(Tokens(1, 1)))))
    assertEquals(rejects(ForgeProcedure.build(altered, f.ready, f.actor.player)),
      OathViolation.ForgeUnavailable(
        "printed Forge cost must contain three resources"): OathViolation)
  }

  test("P1: build rejects a site without exactly three empty faceup denizens") {
    val f = forgeable
    val site = siteOf(f.ready, f.siteId)
    val short = withSite(f.ready, f.siteId,
      site.copy(denizens = site.denizens.drop(1)))
    assertEquals(rejects(ForgeProcedure.build(catalog, short, f.actor.player)),
      OathViolation.ForgeUnavailable(
        "site must contain exactly three empty denizens"): OathViolation)
    // A denizen carrying a token is not empty, so a full-capacity site whose
    // denizens are not all bare fails the same gate.
    val tokened = site.copy(denizens = site.denizens.zipWithIndex.map {
      case (d: DenizenState, 0) => d.copy(tokens = Tokens(1, 0))
      case (other, _) => other
    })
    assertEquals(rejects(ForgeProcedure.build(catalog,
      withSite(f.ready, f.siteId, tokened), f.actor.player)),
      OathViolation.ForgeUnavailable(
        "site must contain exactly three empty denizens"): OathViolation)
  }

  test("P1: build rejects an actor with no supply") {
    val f = forgeable
    assertEquals(rejects(ForgeProcedure.build(catalog,
      withSupply(f.ready, f.actor.player, 0), f.actor.player)),
      OathViolation.InsufficientSupply(1, 0): OathViolation)
  }

  test("P1: build rejects an empty relic deck") {
    val f = forgeable
    val empty = f.ready.copy(game = f.ready.game.copy(current =
      f.ready.game.current.copy(commonCards =
        f.ready.game.current.commonCards.copy(relicDeck = Vector.empty))))
    assertEquals(rejects(ForgeProcedure.build(catalog, empty, f.actor.player)),
      OathViolation.ForgeUnavailable("relic deck is empty"): OathViolation)
  }

  // ---------------------------------------------------------------------
  // P2 / P3: tree shape and park.
  // ---------------------------------------------------------------------

  test("P2: a successful build roots at ForgeActionEligibility and pays one " +
      "supply at a ForgeCost-windowed first leaf") {
    val f = forgeable
    val tree = ForgeProcedure.build(catalog, f.ready, f.actor.player)
      .toOption.get

    assertEquals(tree.window,
      Some(PowerWindow.ForgeActionEligibility): Option[PowerWindow])
    val first = tree.children.head
    assertEquals(first.window, Some(PowerWindow.ForgeCost): Option[PowerWindow])
    assertEquals(first.asInstanceOf[BuildOps].build(f.ready,
      PendingTree(Vector.empty, Vector.empty)),
      Right(Vector[CoreOperation](SpendSupply(f.actor.player, 1))):
        Either[OathViolation, Vector[CoreOperation]])

    // The walk records exactly that one leaf before parking. A windowed leaf
    // walks its own folded vector, so its node id sits one level deeper than
    // an unwindowed sibling's would ("0.0", not "0") -- the same shape
    // Recover's windowed head leaf records.
    val (_, atPark, steps) = parkAtAssignment(f.ready, tree)
    assertEquals(steps.map(_.nodeId), Vector("0.0"))
    assertEquals(steps.head.ops,
      Vector[CoreOperation](SpendSupply(f.actor.player, 1)))
    assertEquals(supplyOf(atPark, f.actor.player), SupplyTrack.Maximum - 1)
  }

  test("P3: walking the tree parks at forge.assignment, owned by the actor, " +
      "declaring both sections with their printed minima and one option per " +
      "live target") {
    val f = forgeable
    val tree = ForgeProcedure.build(catalog, f.ready, f.actor.player)
      .toOption.get
    val (pending, atPark, _) = parkAtAssignment(f.ready, tree)

    assertEquals(pending.answered, Vector.empty[Answered])
    val decide = ProcedureWalker.parkedDecide(atPark, tree, pending, noPowers)
      .getOrElse(fail("expected the park to resolve to a Decide"))
    assertEquals(decide.decisionId, ForgeProcedure.assignmentDecisionId)
    assertEquals(decide.owner, f.actor.player)

    val DecisionQuery.Partition(sections, options, heading, confirmLabel) =
      decide.query: @unchecked
    // Task 5b: the panel's own copy, declared beside the sections it frames.
    assertEquals(heading, Some("Forge a relic"))
    assertEquals(confirmLabel, Some("Complete Forge"))
    assertEquals(sections, Vector(
      DecisionSection(ForgeProcedure.favorSectionKey, "Pay Favor", f.cost.favor),
      DecisionSection(ForgeProcedure.secretSectionKey, "Pay Secret",
        f.cost.secrets)))
    assertEquals(options, f.targets.map(target =>
      DecisionOption.Denizen(DecisionOptionRef.Denizen(target.denizenId))))

    // The query states enough to be answerable and worth asking -- nothing
    // here appeals to the suit banks, which Forge no longer reads at all.
    assertEquals(DecisionQueries.wellFormed(decide.decisionId, decide.query),
      Right(()): Either[OathViolation, Unit])
  }

  test("the shipped catalog really does print four single-resource Forge " +
      "costs, so the no-park path is not a synthetic case") {
    val single = catalog.sites.flatMap(_.forgeRequirements)
      .filter(cost => cost.favor == 0 || cost.secrets == 0)
    assertEquals(single.size, 4)
    assertEquals(single.toSet, Set(Tokens(3, 0), Tokens(0, 3)))
    assert(single.forall(cost => !ForgeProcedure.parks(cost)))
  }

  test("a three-favor or three-secret site declares no Decide node at all " +
      "and resolves its forced split without parking") {
    // The setup fixture deals no single-resource forge site into play, so
    // the printed cost is overridden on the site it does deal -- exactly as
    // the neighbouring cost tests already do. The shipped catalog's own four
    // such sites are covered by the test above.
    Vector(Tokens(3, 0), Tokens(0, 3)).foreach { printed =>
      val base = forgeable
      val altered = catalog.copy(sites = catalog.sites.map(definition =>
        if (definition.id != base.siteId) definition
        else definition.copy(forgeRequirements = Some(printed))))
      val f = base.copy(cost = printed)
      assert(!ForgeProcedure.parks(f.cost), s"${f.cost} should not park")
      val tree = ForgeProcedure.build(altered, f.ready, f.actor.player)
        .toOption.get
      // The decision lives inside a `Branch`, so its absence shows as the
      // branch's absence from the root's children.
      assert(tree.children.forall(!_.isInstanceOf[Branch]),
        s"a ${f.cost} Forge must declare no decision branch: $tree")
      assertEquals(tree.children.size, 2)

      // One command: no park, no answer, and the determined split applied.
      val (finalState, steps) = expectFinished(
        ProcedureWalker.advance(f.ready, tree, None, noPowers))
      val section =
        if (f.cost.favor > 0) ForgeProcedure.favorSectionKey
        else ForgeProcedure.secretSectionKey
      assertEquals(steps.map(_.nodeId), Vector("0.0", "1"))
      assertEquals(steps(1).ops, f.targets.map(target =>
        expectedPayment(f.actor.player, target.denizenId, section)) :+
        Play(f.relic,
          PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
          Location.PlayArea(f.actor.player), Orientation.FaceDown))
      assertEquals(
        finalState.game.current.map.sites(f.siteId).denizens.collect {
          case d: DenizenState => d.tokens },
        Vector.fill(3)(
          if (f.cost.favor > 0) Tokens(1, 0) else Tokens(0, 1)))
      assert(finalState.game.current.walkerPending.isEmpty)
    }
  }

  // ---------------------------------------------------------------------
  // P4: the generic validator bites, in both directions.
  // ---------------------------------------------------------------------

  test("P4: the assignment decision rejects stale, duplicate, incomplete and " +
      "minimum-violating answers") {
    val f = forgeable
    val tree = ForgeProcedure.build(catalog, f.ready, f.actor.player)
      .toOption.get
    val (pending, atPark, _) = parkAtAssignment(f.ready, tree)
    val legal = legalPlacements(f)

    val stale = legal.updated(2, DecisionPlacement(DecisionOptionRef.Denizen(
      DenizenId(catalog.denizens(7).id.value)), legal(2).sectionKey))
    assert(rejection(resolveWith(f, atPark, tree, pending, stale))
      .contains("does not offer a placed option"))

    val duplicate = legal.updated(2,
      legal(2).copy(option = legal.head.option))
    assert(rejection(resolveWith(f, atPark, tree, pending, duplicate))
      .contains("places an option more than once"))

    assert(rejection(resolveWith(f, atPark, tree, pending, legal.drop(1)))
      .contains("leaves an option unplaced"))

    // Moving one option out of the section that needs it breaks that
    // section's minimum -- the generic shape of "these are not the printed
    // resources", now stated by the query rather than by a Forge closure.
    val shortSection = legal.head.sectionKey
    val starved = legal.updated(0, legal.head.copy(sectionKey =
      if (shortSection == ForgeProcedure.favorSectionKey)
        ForgeProcedure.secretSectionKey
      else ForgeProcedure.favorSectionKey))
    assert(rejection(resolveWith(f, atPark, tree, pending, starved))
      .contains(s"leaves section '$shortSection' below its minimum"))

    val unknownSection = legal.updated(0,
      legal.head.copy(sectionKey = "pay-warbands"))
    assert(rejection(resolveWith(f, atPark, tree, pending, unknownSection))
      .contains("has no section 'pay-warbands'"))

    // A choose-one answer to a partition question is rejected on shape.
    assert(rejection(ProcedureWalker.resolve(atPark, tree, pending,
      Answered(ForgeProcedure.assignmentDecisionId,
        DecisionAnswer.ChooseOneAnswer(legal.head.option), f.actor.player),
      noPowers))
      .contains("expects a partition answer"))
  }

  test("P4: an answer from a player who is not the parked actor is rejected " +
      "as the wrong player") {
    val f = forgeable
    val tree = ForgeProcedure.build(catalog, f.ready, f.actor.player)
      .toOption.get
    val (pending, atPark, _) = parkAtAssignment(f.ready, tree)
    val other = f.ready.game.current.players
      .find(_.player != f.actor.player).get.player

    assertEquals(ProcedureWalker.resolve(atPark, tree,
      pending, answerOf(legalPlacements(f), other), noPowers),
      Left(OathViolation.WrongPlayer(f.actor.player, other)):
        Either[OathViolation, WalkerOutcome])
  }

  test("P4/R14: the decision reads its eligible targets live, so a denizen " +
      "that gains a token after the park makes the old answer stale") {
    val f = forgeable
    val tree = ForgeProcedure.build(catalog, f.ready, f.actor.player)
      .toOption.get
    val (pending, atPark, _) = parkAtAssignment(f.ready, tree)
    val legal = legalPlacements(f)

    val site = siteOf(atPark, f.siteId)
    val moved = withSite(atPark, f.siteId,
      site.copy(denizens = site.denizens.map {
        case d: DenizenState if d.id == f.targets.head.denizenId =>
          d.copy(tokens = Tokens(1, 0))
        case other => other
      }))

    assertEquals(ForgeProcedure.eligibleTargets(moved, f.actor.player),
      f.targets.drop(1))

    // Losing a target does not merely invalidate the old answer: the printed
    // cost needs three placements and only two options remain, so the
    // rebuilt query cannot be satisfied by ANY answer and is rejected as
    // unanswerable before the submitted one is even compared against it.
    assert(rejection(resolveWith(f, moved, tree, pending, legal))
      .contains("declares section minimums no answer can meet"))
  }

  // ---------------------------------------------------------------------
  // P6: the accepted answer's full operation vector.
  // ---------------------------------------------------------------------

  test("P6: a legal answer finishes the walk with three player-funded " +
      "PayCosts and the relic play, consulting no suit bank") {
    val f = forgeable
    val tree = ForgeProcedure.build(catalog, f.ready, f.actor.player)
      .toOption.get
    val (pending, atPark, _) = parkAtAssignment(f.ready, tree)
    val legal = legalPlacements(f)
    val banksBefore = atPark.banks.favor
    val favorBefore = atPark.game.current.players
      .find(_.player == f.actor.player).get.board.favor
    val secretsBefore = atPark.game.current.players
      .find(_.player == f.actor.player).get.board.faceUpSecrets

    val (finalState, steps) =
      expectFinished(resolveWith(f, atPark, tree, pending, legal))

    val expected: Vector[CoreOperation] = legal.map { placement =>
      val DecisionOptionRef.Denizen(denizen) = placement.option: @unchecked
      expectedPayment(f.actor.player, denizen, placement.sectionKey)
    } :+ Play(f.relic,
      PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
      Location.PlayArea(f.actor.player), Orientation.FaceDown)

    assertEquals(steps.map(_.nodeId), Vector("1.0", "2"))
    assertEquals(steps.head.payload,
      ChoicePayload(ForgeProcedure.assignmentDecisionId,
        PartitionAnswer(legal), f.actor.player): WalkerStepPayload)
    assertEquals(steps.head.ops, Vector.empty[CoreOperation])
    assertEquals(steps(1).ops, expected)

    assertEquals(supplyOf(finalState, f.actor.player), SupplyTrack.Maximum - 1)
    assertEquals(finalState.game.current.map.sites(f.siteId).denizens.collect {
      case d: DenizenState => d.tokens.favor + d.tokens.secrets
    }, Vector(1, 1, 1))

    // The actor paid, and the suit banks did not move at all.
    assertEquals(finalState.banks.favor, banksBefore)
    val after = finalState.game.current.players
      .find(_.player == f.actor.player).get
    assertEquals(after.board.favor, favorBefore - f.cost.favor)
    assertEquals(after.board.faceUpSecrets, secretsBefore - f.cost.secrets)

    assertEquals(finalState.game.current.commonCards.relicDeck.headOption,
      f.ready.game.current.commonCards.relicDeck.drop(1).headOption)
    assertEquals(after.relics.last,
      RelicState(f.relic, Orientation.FaceDown, Tokens.empty))
    assert(finalState.game.current.walkerPending.isEmpty)
  }

  test("P6: the section a target is placed in decides which resource it " +
      "receives, whatever the site's own printed split") {
    val f = forgeable
    val tree = ForgeProcedure.build(catalog, f.ready, f.actor.player)
      .toOption.get
    val (pending, atPark, _) = parkAtAssignment(f.ready, tree)

    // Reverse the printed order: the same three targets, the same two
    // minima, the opposite assignment.
    val reversed = legalPlacements(f).map(_.sectionKey).reverse
    val placements = f.targets.zip(reversed).map { case (target, section) =>
      DecisionPlacement(DecisionOptionRef.Denizen(target.denizenId), section) }

    val (finalState, steps) =
      expectFinished(resolveWith(f, atPark, tree, pending, placements))
    assertEquals(steps(1).ops.init, placements.map { placement =>
      val DecisionOptionRef.Denizen(denizen) = placement.option: @unchecked
      expectedPayment(f.actor.player, denizen, placement.sectionKey)
    })
    assertEquals(finalState.game.current.map.sites(f.siteId).denizens.collect {
      case denizen: DenizenState => denizen.tokens
    }, reversed.map(section =>
      if (section == ForgeProcedure.favorSectionKey) Tokens(1, 0)
      else Tokens(0, 1)))
  }


  // ---------------------------------------------------------------------
  // P5 (R15): a start-only gate never re-gates a resume.
  // ---------------------------------------------------------------------

  test("P5: a Forge started with exactly one supply is still resumable and " +
      "answerable") {
    val f = forgeable
    val poor = withSupply(f.ready, f.actor.player, 1)
    val tree = ForgeProcedure.build(catalog, poor, f.actor.player).toOption.get
    val (pending, atPark, _) = parkAtAssignment(poor, tree)
    assertEquals(supplyOf(atPark, f.actor.player), 0)

    // Re-running the start gate here would strand a legally started Forge.
    assertEquals(rejects(ForgeProcedure.build(catalog, atPark, f.actor.player)),
      OathViolation.InsufficientSupply(1, 0): OathViolation)
    val resumed = ForgeProcedure.rebuild(catalog, atPark, f.actor.player)
    assert(resumed.isRight,
      s"resume derivation must not re-run the start-only supply gate: $resumed")

    val (finalState, steps) = expectFinished(resolveWith(f, atPark,
      resumed.toOption.get, pending, legalPlacements(f)))
    assertEquals(steps.map(_.nodeId), Vector("1.0", "2"))
    assertEquals(supplyOf(finalState, f.actor.player), 0)
    assertEquals(finalState.game.current.players.find(
      _.player == f.actor.player).get.relics.last,
      RelicState(f.relic, Orientation.FaceDown, Tokens.empty))
  }

  // ---------------------------------------------------------------------
  // Shared readers and window inventory.
  // ---------------------------------------------------------------------

  test("eligibleTargets is the same set the start gate derives") {
    val f = forgeable
    assertEquals(ForgeProcedure.eligibleTargets(f.ready, f.actor.player),
      ForgeRules.validate(catalog, f.ready, f.actor, f.siteId).toOption.get._1)
    assertEquals(ForgeProcedure.eligibleTargets(f.ready, f.actor.player),
      f.targets)
    assertEquals(ForgeProcedure.eligibleTargets(
      withPawn(f.ready, f.actor.player, None), f.actor.player),
      Vector.empty[SiteDenizenTarget])
  }

  test("the forged relic is the authoritative deck top, read at execution " +
      "time rather than closed over by the tree") {
    val f = forgeable
    val tree = ForgeProcedure.build(catalog, f.ready, f.actor.player)
      .toOption.get
    val (pending, atPark, _) = parkAtAssignment(f.ready, tree)
    val legal = legalPlacements(f)

    // The same tree, walked against a state whose deck top changed after the
    // park, forges the NEW top.
    val restacked = atPark.copy(game = atPark.game.copy(current =
      atPark.game.current.copy(commonCards =
        atPark.game.current.commonCards.copy(relicDeck =
          atPark.game.current.commonCards.relicDeck.reverse))))
    val newTop = restacked.game.current.commonCards.relicDeck.head
    assertNotEquals(newTop, f.relic)
    val (_, steps) =
      expectFinished(resolveWith(f, restacked, tree, pending, legal))
    assertEquals(steps(1).ops.last, Play(newTop,
      PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
      Location.PlayArea(f.actor.player),
      Orientation.FaceDown): CoreOperation)

    // An emptied deck fails the leaf rather than forging nothing silently.
    val emptied = atPark.copy(game = atPark.game.copy(current =
      atPark.game.current.copy(commonCards =
        atPark.game.current.commonCards.copy(relicDeck = Vector.empty))))
    assertEquals(resolveWith(f, emptied, tree, pending, legal),
      Left(OathViolation.ForgeUnavailable("relic deck is empty")):
        Either[OathViolation, WalkerOutcome])
  }

  /** Every node reachable from `node` through STATIC children, including
    * `node` itself. Forge's assignment decision hangs off a `Branch`, whose
    * children are chosen at walk time, so it is deliberately not reached
    * here -- it carries no window, which is what this is used to inventory.
    */
  private def allNodes(node: Operation): Vector[Operation] = node match {
    case _: PrimitiveOperation => Vector(node)
    case _ => node +: node.children.flatMap(allNodes)
  }

  test("the tree windows exactly its root and its supply payment; every " +
      "other node carries none") {
    val f = forgeable
    val tree = ForgeProcedure.build(catalog, f.ready, f.actor.player)
      .toOption.get
    val windowed = allNodes(tree).filter(_.window.isDefined)

    assertEquals(windowed.size, 2,
      s"expected exactly 2 windowed nodes, found ${windowed.size}: $windowed")
    assertEquals(windowed.map(_.window).toSet, Set[Option[PowerWindow]](
      Some(PowerWindow.ForgeActionEligibility), Some(PowerWindow.ForgeCost)))
    assert(windowed.contains(tree),
      "the tree root must carry ForgeActionEligibility")
    assert(windowed.count(_.isInstanceOf[BuildOps]) == 1,
      "exactly the supply-paying BuildOps carries ForgeCost -- the " +
        "completion BuildOps stays unwindowed")
  }
}

object ForgeProcedureSuite {
  /** The board every test starts from, plus the facts a legal answer is
    * built out of. Declared outside the suite class so its case-class type
    * test carries no unchecked outer reference.
    */
  final case class Forgeable(ready: ReadyGame, actor: PlayerState,
      siteId: SiteId, targets: Vector[SiteDenizenTarget], cost: Tokens,
      relic: RelicId)
}
