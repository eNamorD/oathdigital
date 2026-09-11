package oathdigital.gameplay

import oathdigital.gameplay.actions.ForgeRules
import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.operations._
import oathdigital.gameplay.powerresolver.PowerWindow
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.walker.{ChoicePayload, ProcedureWalker, WalkerCtx,
  WalkerOutcome, WalkerPowers, WalkerStepPayload, WalkerStepRecorded}
import oathdigital.gameplay.OathState.Ready
import oathdigital.model.DecisionAnswer.ForgeAssignmentAnswer
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
    * non-empty relic deck and full supply. Mirrors `ForgeSuite.forgeable` so
    * the walker path is proven against the same board the legacy path is.
    */
  private def forgeable: Forgeable = {
    val Ready(base) = execute(setup)._1: @unchecked
    val actor = base.game.current.players.find(
      _.player == base.game.current.turn.activePlayer).get
    val definition = catalog.sites.find(_.forgeRequirements.nonEmpty).get
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

  private def suitOf(denizen: DenizenId): Suit =
    catalog.denizens.find(_.id.value == denizen.value)
      .flatMap(d => Suit.all.find(_.key == d.suit.value)).get

  /** The printed cost spread over the three eligible targets, in target order
    * -- the legal answer every accepting test uses.
    */
  private def legalAssignments(f: Forgeable)
      : Vector[ForgeResourceAssignment] =
    f.targets.zip(Vector.fill(f.cost.favor)(ForgeResource.Favor) ++
      Vector.fill(f.cost.secrets)(ForgeResource.Secret)).map {
      case (target, resource) => ForgeResourceAssignment(target, resource)
    }

  private def answerOf(assignments: Vector[ForgeResourceAssignment]): Answered =
    Answered(ForgeProcedure.assignmentDecisionId,
      ForgeAssignmentAnswer(assignments))

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
    val (pending, events) = expectParked(
      ProcedureWalker.advance(state, tree, None, noPowers), Vector("1"))
    (pending, foldRecordedOps(state, events, "Forge supply payment"),
      events.map(_.asInstanceOf[WalkerStepRecorded]))
  }

  private def resolveWith(f: Forgeable, state: ReadyGame, tree: Operation,
      pending: PendingTree, assignments: Vector[ForgeResourceAssignment])
      : Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.resolve(state, tree, pending, answerOf(assignments),
      noPowers)

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
      PendingTree(Vector.empty, Vector.empty, f.actor.player)),
      Right(Vector[CoreOperation](AdjustSupply(f.actor.player, -1))):
        Either[OathViolation, Vector[CoreOperation]])

    // The walk records exactly that one leaf before parking. A windowed leaf
    // walks its own folded vector, so its node id sits one level deeper than
    // an unwindowed sibling's would ("0.0", not "0") -- the same shape
    // Recover's windowed head leaf records.
    val (_, atPark, steps) = parkAtAssignment(f.ready, tree)
    assertEquals(steps.map(_.nodeId), Vector("0.0"))
    assertEquals(steps.head.ops,
      Vector[CoreOperation](AdjustSupply(f.actor.player, -1)))
    assertEquals(supplyOf(atPark, f.actor.player), SupplyTrack.Maximum - 1)
  }

  test("P3: walking the tree parks at forge.assignment with the actor as " +
      "owner") {
    val f = forgeable
    val tree = ForgeProcedure.build(catalog, f.ready, f.actor.player)
      .toOption.get
    val (pending, atPark, _) = parkAtAssignment(f.ready, tree)

    assertEquals(pending.actor, f.actor.player)
    assertEquals(pending.answered, Vector.empty[Answered])
    val decide = ProcedureWalker.parkedDecide(atPark, tree, pending, noPowers)
      .getOrElse(fail("expected the park to resolve to a Decide"))
    assertEquals(decide.decisionId, ForgeProcedure.assignmentDecisionId)
    assertEquals(decide.owner.owner(WalkerCtx(atPark)), Some(f.actor.player))
  }

  // ---------------------------------------------------------------------
  // P4: the Decide's validate bites, in both directions.
  // ---------------------------------------------------------------------

  test("P4: the assignment decision rejects stale, duplicate, mismatched and " +
      "unaffordable answers") {
    val f = forgeable
    val tree = ForgeProcedure.build(catalog, f.ready, f.actor.player)
      .toOption.get
    val (pending, atPark, _) = parkAtAssignment(f.ready, tree)
    val legal = legalAssignments(f)

    val stale = legal.updated(2, ForgeResourceAssignment(SiteDenizenTarget(
      f.siteId, DenizenId(catalog.denizens(7).id.value)), legal(2).resource))
    assertEquals(resolveWith(f, atPark, tree, pending, stale),
      Left(OathViolation.ForgeOutcomeMismatch(
        "assignment targets are stale or ineligible")):
        Either[OathViolation, WalkerOutcome])

    val duplicate = legal.updated(2,
      ForgeResourceAssignment(legal.head.target, legal(2).resource))
    assertEquals(resolveWith(f, atPark, tree, pending, duplicate),
      Left(OathViolation.ForgeOutcomeMismatch(
        "assign exactly one resource to each of three distinct denizens")):
        Either[OathViolation, WalkerOutcome])

    // Swapping one resource for the other kind always changes the multiset,
    // whatever the fixture site's printed split happens to be.
    val wrongResources = legal.updated(0, legal.head.copy(
      resource = legal.head.resource match {
        case ForgeResource.Favor => ForgeResource.Secret
        case ForgeResource.Secret => ForgeResource.Favor
      }))
    assertEquals(resolveWith(f, atPark, tree, pending, wrongResources),
      Left(OathViolation.ForgeOutcomeMismatch(
        "assignments do not match the printed Forge resources")):
        Either[OathViolation, WalkerOutcome])

    val depleted = atPark.copy(banks = atPark.banks.copy(
      favor = atPark.banks.favor.view.mapValues(_ => 0).toMap))
    resolveWith(f, depleted, tree, pending, legal) match {
      case Left(OathViolation.ForgeUnavailable(detail)) =>
        assert(detail.contains("favor bank lacks"),
          s"violation detail '$detail' should name the empty favor bank")
      case other => fail(s"expected a favor-bank rejection, got $other")
    }
  }

  test("P4/R14: the decision reads its eligible targets live, so a denizen " +
      "that gains a token after the park makes the old answer stale") {
    val f = forgeable
    val tree = ForgeProcedure.build(catalog, f.ready, f.actor.player)
      .toOption.get
    val (pending, atPark, _) = parkAtAssignment(f.ready, tree)
    val legal = legalAssignments(f)

    val site = siteOf(atPark, f.siteId)
    val moved = withSite(atPark, f.siteId,
      site.copy(denizens = site.denizens.map {
        case d: DenizenState if d.id == f.targets.head.denizenId =>
          d.copy(tokens = Tokens(1, 0))
        case other => other
      }))

    assertEquals(ForgeProcedure.eligibleTargets(moved, f.actor.player),
      f.targets.drop(1))
    assertEquals(resolveWith(f, moved, tree, pending, legal),
      Left(OathViolation.ForgeOutcomeMismatch(
        "assignment targets are stale or ineligible")):
        Either[OathViolation, WalkerOutcome])
  }

  // ---------------------------------------------------------------------
  // P6: the accepted answer's full operation vector.
  // ---------------------------------------------------------------------

  test("P6: a legal answer finishes the walk with exactly the three resource " +
      "moves and the relic play") {
    val f = forgeable
    val tree = ForgeProcedure.build(catalog, f.ready, f.actor.player)
      .toOption.get
    val (pending, atPark, _) = parkAtAssignment(f.ready, tree)
    val legal = legalAssignments(f)

    val (finalState, steps) =
      expectFinished(resolveWith(f, atPark, tree, pending, legal))

    // Independently written against legacy `Forge.completionOperations`: one
    // move per assignment in assignment order, then the relic play from the
    // authoritative deck top.
    val expected: Vector[CoreOperation] = legal.map { assignment =>
      assignment.resource match {
        case ForgeResource.Favor => Move(Piece.Favor(1),
          PositionedLocation(Location.FavorBank(
            suitOf(assignment.target.denizenId))),
          PositionedLocation(Location.OnCard(assignment.target.denizenId)))
        case ForgeResource.Secret => Move(Piece.Secrets(1),
          PositionedLocation(Location.SharedBank),
          PositionedLocation(Location.OnCard(assignment.target.denizenId)))
      }
    } :+ Play(f.relic,
      PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
      Location.PlayArea(f.actor.player), Orientation.FaceDown)

    assertEquals(steps.map(_.nodeId), Vector("1", "2"))
    assertEquals(steps.head.payload,
      ChoicePayload(ForgeProcedure.assignmentDecisionId,
        ForgeAssignmentAnswer(legal)): WalkerStepPayload)
    assertEquals(steps.head.ops, Vector.empty[CoreOperation])
    assertEquals(steps(1).ops, expected)

    assertEquals(supplyOf(finalState, f.actor.player), SupplyTrack.Maximum - 1)
    assertEquals(finalState.game.current.map.sites(f.siteId).denizens.collect {
      case d: DenizenState => d.tokens.favor + d.tokens.secrets
    }, Vector(1, 1, 1))
    assertEquals(finalState.game.current.commonCards.relicDeck.headOption,
      f.ready.game.current.commonCards.relicDeck.drop(1).headOption)
    assertEquals(finalState.game.current.players.find(
      _.player == f.actor.player).get.relics.last,
      RelicState(f.relic, Orientation.FaceDown, Tokens.empty))
    assert(finalState.game.current.walkerPending.isEmpty)
  }

  test("P6: a mixed printed cost draws favor from the target denizen's own " +
      "suit bank and secrets from the shared bank") {
    val f = forgeable
    // The fixture site's printed cost is all favor, so the secret half of
    // `assignmentOperations` would otherwise never run. Altering only the
    // printed requirement keeps the audited handler inventory untouched.
    val altered = catalog.copy(sites = catalog.sites.map(definition =>
      if (definition.id != f.siteId) definition
      else definition.copy(forgeRequirements = Some(Tokens(1, 2)))))
    val tree = ForgeProcedure.build(altered, f.ready, f.actor.player)
      .toOption.get
    val (pending, atPark, _) = parkAtAssignment(f.ready, tree)
    val mixed = f.targets.zip(Vector(ForgeResource.Favor,
      ForgeResource.Secret, ForgeResource.Secret)).map {
      case (target, resource) => ForgeResourceAssignment(target, resource)
    }

    val (finalState, steps) =
      expectFinished(resolveWith(f, atPark, tree, pending, mixed))
    assertEquals(steps(1).ops, Vector[CoreOperation](
      Move(Piece.Favor(1),
        PositionedLocation(Location.FavorBank(
          suitOf(f.targets.head.denizenId))),
        PositionedLocation(Location.OnCard(f.targets.head.denizenId))),
      Move(Piece.Secrets(1), PositionedLocation(Location.SharedBank),
        PositionedLocation(Location.OnCard(f.targets(1).denizenId))),
      Move(Piece.Secrets(1), PositionedLocation(Location.SharedBank),
        PositionedLocation(Location.OnCard(f.targets(2).denizenId))),
      Play(f.relic,
        PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
        Location.PlayArea(f.actor.player), Orientation.FaceDown)))
    assertEquals(finalState.game.current.map.sites(f.siteId).denizens.collect {
      case denizen: DenizenState => denizen.tokens
    }, Vector(Tokens(1, 0), Tokens(0, 1), Tokens(0, 1)))

    // The all-favor answer the unaltered printed cost accepts is rejected by
    // this tree: the expected multiset comes from the cost the tree was built
    // with, not from whatever the site once required.
    assertEquals(resolveWith(f, atPark, tree, pending, legalAssignments(f)),
      Left(OathViolation.ForgeOutcomeMismatch(
        "assignments do not match the printed Forge resources")):
        Either[OathViolation, WalkerOutcome])
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
      resumed.toOption.get, pending, legalAssignments(f)))
    assertEquals(steps.map(_.nodeId), Vector("1", "2"))
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
    val legal = legalAssignments(f)

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

  /** Every node reachable from `node`, including `node` itself. Forge's tree
    * declares no `Branch`, so static children are the whole tree.
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
