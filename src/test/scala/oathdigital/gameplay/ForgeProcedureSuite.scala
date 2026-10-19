package oathdigital.gameplay

import oathdigital.gameplay.actions.ForgeRules
import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ChoicePayload, DecisionQueries,
  LogNoted, ProcedureWalker, WalkerOutcome, WalkerPowers,
  WalkerStepPayload, WalkerStepRecorded}
import oathdigital.model.DecisionAnswer.PartitionAnswer
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, EmptyWindowModifier, Look, Outcomes,
  RecordedOps, Table, TestCards}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, as, present}

/** The declared Forge tree, walked with `ProcedureWalker.advance`/`resolve`.
  *
  * Nothing here goes through the registry or the application layer: every
  * walk drives `ProcedureWalker` directly against a tree
  * `ForgeProcedure.build`/`rebuild` returned.
  */
class ForgeProcedureSuite extends munit.FunSuite
    with WalkerRecordedOpsReducer:
  import ForgeProcedureSuite.Forgeable
  /** Forge declares no `ContributingPower` (`ForgePowers.powers` is empty),
    * so every walk states an empty power source explicitly rather than
    * relying on a default.
    */
  private val noPowers: WalkerPowers = WalkerPowers.empty

  private val actor = p1
  // Deep Woods prints a Forge cost of 1 favor and 2 secrets. A cost naming
  // both resources parks and prompts; a cost of three of one resource does
  // not (see [[ForgeProcedure.parks]]).
  private val forgeSite = CatalogNames.site("Deep Woods")
  // Three denizens whose powers these tests do not use.
  private val forgeDenizens = Vector("Wrestlers", "Martial Culture",
    "Bandit Chief").map(CatalogNames.denizen(_))

  /** p1, in Act, stands at Deep Woods and rules it with one warband, with 5
    * favor, 5 faceup secrets and the quiet table's 7 Supply. Deep Woods holds
    * `siteCards` faceup and token-free, and the relic deck is the quiet
    * table's. */
  private def forgeBoard(siteCards: Vector[DenizenId] = forgeDenizens): Table =
    val funded = Table.start.pawn(actor, forgeSite)
      .warbandsAt(forgeSite, actor, 1).favor(actor, 5)
      .secrets(actor, faceUp = 5)
    siteCards.foldLeft(funded)(_.denizen(_, at = forgeSite))

  private def forgeable: Forgeable =
    val ready = forgeBoard().ready
    val cost = present(catalog.site(forgeSite).flatMap(_.forgeRequirements),
      "Deep Woods' printed Forge cost")
    assertEquals(cost, Tokens(1, 2))
    Forgeable(ready, Look(ready).player(actor), forgeSite,
      forgeDenizens.map(SiteDenizenTarget(forgeSite, _)), cost,
      ready.game.current.commonCards.relicDeck.head)

  /** `ready` with the actor's pawn off the board; no Table step removes a
    * pawn. */
  private def withoutPawn(ready: ReadyGame): ReadyGame =
    ready.updateCurrent(c => c.copy(players = c.players.map(p =>
      if p.player == actor then p.copy(pawnSite = None) else p)))

  /** The printed cost spread over the three eligible targets in target order,
    * as partition placements -- the legal answer every accepting test uses.
    */
  private def legalPlacements(f: Forgeable): Vector[DecisionPlacement] =
    f.targets.zip(Vector.fill(f.cost.favor)(ForgeProcedure.favorSectionKey) ++
      Vector.fill(f.cost.secrets)(ForgeProcedure.secretSectionKey)).map:
      case (target, section) =>
        DecisionPlacement(DecisionOptionRef.Denizen(target.denizenId), section)

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
      if section == ForgeProcedure.favorSectionKey then Cost(favor = 1)
      else Cost(secret = 1))


  private def rejects(result: Either[OathViolation, Operation]): OathViolation =
    result match
      case Left(violation) => violation
      case Right(_) => fail("expected the Forge start gate to reject")

  /** A walk that finished: the state it left and the steps it recorded. */
  private final case class ForgeFinished(state: ReadyGame,
      steps: Vector[WalkerStepRecorded])

  private def expectFinished(outcome: Either[OathViolation, WalkerOutcome])
      : ForgeFinished =
    val finished = Outcomes.finished(outcome)
    // The two trailing `Note` leaves journal `LogNoted`, which is the
    // Game Log's business and not this suite's: only the recorded steps are.
    // Both are still counted, so dropping one does not pass unnoticed.
    assertEquals(finished.events.count(_.isInstanceOf[LogNoted]), 2,
      s"a finished Forge journals both of its lines: ${finished.events}")
    ForgeFinished(finished.treeless,
      finished.events.collect { case step: WalkerStepRecorded => step })

  /** The walk parked on the assignment decision: the park, the state at the
    * park, and the steps recorded on the way. */
  private final case class AssignmentPark(pending: PendingTree,
      state: ReadyGame, steps: Vector[WalkerStepRecorded])

  /** Walks a fresh tree to the assignment park, folding the recorded supply
    * payment forward the way journal replay would.
    */
  private def parkAtAssignment(state: ReadyGame, tree: Operation)
      : AssignmentPark =
    val parked = Outcomes.parked(
      ProcedureWalker.advance(state, tree, None, noPowers))
    // The decision sits inside a `Branch` (its options are the eligible
    // targets, read live), so the park addresses the branch's selected child.
    // The Forge is the root's second child, after the modifier decision.
    assertEquals(parked.tree.at, Vector("1", "1", "0"))
    AssignmentPark(parked.tree,
      foldRecordedOps(state, parked.events, "Forge supply payment"),
      parked.events.map(as[WalkerStepRecorded](_)))

  private def resolveWith(f: Forgeable, state: ReadyGame, tree: Operation,
      pending: PendingTree, placements: Vector[DecisionPlacement])
      : Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.resolve(state, tree, pending,
      answerOf(placements, actor), noPowers)

  private def rejection(outcome: Either[OathViolation, WalkerOutcome])
      : String = outcome match
    case Left(OathViolation.InvalidEventOrder(detail)) => detail
    case other => fail(s"expected an InvalidEventOrder rejection, got $other")

  // ---------------------------------------------------------------------
  // Start gates: one test per gate in `ForgeRules.validate`.
  // ---------------------------------------------------------------------

  test("build rejects an actor whose pawn is not at a site"):
    val f = forgeable
    assertEquals(rejects(ForgeProcedure.build(catalog,
      withoutPawn(f.ready), actor)),
      OathViolation.PawnSiteMissing(actor): OathViolation)

  test("build rejects a site the actor does not rule"):
    val f = forgeable
    val contested = Table.from(f.ready).bandits(f.siteId, 1).unchecked
    assertEquals(
      rejects(ForgeProcedure.build(catalog, contested, actor)),
      OathViolation.ForgeUnavailable(
        "actor does not rule their pawn site"): OathViolation)

  test("build rejects a site with no printed Forge cost"):
    val f = forgeable
    // Broken Peaks prints no Forge cost.
    val plain = CatalogNames.site("Broken Peaks")
    assertEquals(catalog.site(plain).flatMap(_.forgeRequirements), None)
    val state = Table.from(f.ready).warbandsAt(plain, actor, 1)
      .pawn(actor, plain).unchecked
    assertEquals(rejects(ForgeProcedure.build(catalog, state, actor)),
      OathViolation.ForgeUnavailable(
        "site has no printed Forge cost"): OathViolation)

  test("build rejects a printed cost that does not total three resources"):
    val f = forgeable
    val altered = catalog.copy(sites = catalog.sites.map(definition =>
      if definition.id != f.siteId then definition
      else TestCards.siteLike(definition)(
        forgeRequirements = Some(Tokens(1, 1)))))
    assertEquals(rejects(ForgeProcedure.build(altered, f.ready, actor)),
      OathViolation.ForgeUnavailable(
        "printed Forge cost must contain three resources"): OathViolation)

  test("build rejects a site without exactly three empty faceup denizens"):
    val short = forgeBoard(forgeDenizens.drop(1)).ready
    assertEquals(rejects(ForgeProcedure.build(catalog, short, actor)),
      OathViolation.ForgeUnavailable(
        "site must contain exactly three empty denizens"): OathViolation)
    // A denizen carrying a token is not empty, so a full-capacity site whose
    // denizens are not all bare fails the same gate.
    val tokened = forgeBoard().tokens(forgeDenizens.head, favor = 1).ready
    assertEquals(rejects(ForgeProcedure.build(catalog, tokened, actor)),
      OathViolation.ForgeUnavailable(
        "site must contain exactly three empty denizens"): OathViolation)

  test("build rejects an actor with no supply"):
    val f = forgeable
    assertEquals(rejects(ForgeProcedure.build(catalog,
      Table.from(f.ready).supply(actor, 0).unchecked, actor)),
      OathViolation.InsufficientSupply(1, 0): OathViolation)

  test("build rejects an empty relic deck"):
    val f = forgeable
    val empty = Table.from(f.ready).relicDeckEmptied.unchecked
    assertEquals(rejects(ForgeProcedure.build(catalog, empty, actor)),
      OathViolation.ForgeUnavailable("relic deck is empty"): OathViolation)

  test("build rejects an actor who cannot fund the printed cost from " +
      "their own faceup favor and secrets"):
    // A Forge that starts unable to pay would spend Supply, walk to its last
    // node and fail there with nothing recoverable, so this is a start gate.
    val f = forgeable
    def withFunds(favor: Int, faceUp: Int, faceDown: Int = 0) =
      Table.from(f.ready).favor(actor, favor)
        .secrets(actor, faceUp = faceUp, faceDown = faceDown).unchecked
    def build(state: ReadyGame) =
      ForgeProcedure.build(catalog, state, actor)
    // Exactly enough of each resource is affordable.
    accepted(build(withFunds(f.cost.favor, f.cost.secrets)))
    assertEquals(rejects(build(withFunds(f.cost.favor - 1, f.cost.secrets))),
      OathViolation.InsufficientFavor(f.cost.favor, f.cost.favor - 1): OathViolation)
    assertEquals(rejects(build(withFunds(f.cost.favor, f.cost.secrets - 1))),
      OathViolation.InsufficientSecrets(f.cost.secrets, f.cost.secrets - 1)
        : OathViolation)
    // Facedown secrets are not spendable, so they do not fund a Forge.
    assertEquals(rejects(build(withFunds(f.cost.favor, 0, f.cost.secrets + 3))),
      OathViolation.InsufficientSecrets(f.cost.secrets, 0): OathViolation)

  // ---------------------------------------------------------------------
  // Tree shape and park.
  // ---------------------------------------------------------------------

  test("a successful build's Forge sits at ForgeActionEligibility and pays " +
      "one supply at a ForgeCost-windowed first leaf"):
    val f = forgeable
    val tree = accepted(ForgeProcedure.build(catalog, f.ready, actor))
    // The modifier decision comes first; the Forge is the second child.
    val forge = tree.children(1)
    assertEquals(forge.window,
      Some(PowerWindow.ForgeActionEligibility): Option[PowerWindow])
    val first = forge.children.head
    assertEquals(first.window, Some(PowerWindow.ForgeCost): Option[PowerWindow])
    assertEquals(as[BuildOps](first).build(f.ready,
      PendingTree(Vector.empty, Vector.empty)),
      Right(Vector[CoreOperation](SpendSupply(actor, 1)))
        : Either[OathViolation, Vector[CoreOperation]])

    // The walk records exactly that one leaf before parking. A windowed leaf
    // walks its own folded vector, so its node id sits one level deeper than
    // an unwindowed sibling's would ("1.0.0", not "1.0") -- the same shape
    // Recover's windowed head leaf records.
    val park = parkAtAssignment(f.ready, tree)
    val atPark = park.state
    val steps = park.steps
    assertEquals(steps.map(_.nodeId), Vector("1.0.0"))
    assertEquals(RecordedOps.of(steps.head),
      Vector[CoreOperation](SpendSupply(actor, 1)))
    assertEquals(Look(atPark).supply(actor), SupplyTrack.Maximum - 1)

  test("walking the tree parks at forge.assignment, owned by the actor, " +
      "declaring the panel's heading and confirm label, both sections with " +
      "their printed minima, and one option per live target"):
    val f = forgeable
    val tree = accepted(ForgeProcedure.build(catalog, f.ready, actor))
    val park = parkAtAssignment(f.ready, tree)
    val pending = park.pending
    val atPark = park.state

    assertEquals(pending.answered, Vector.empty[Answered])
    val decide = ProcedureWalker.parkedDecide(atPark, tree, pending, noPowers)
      .getOrElse(fail("expected the park to resolve to a Decide"))
    assertEquals(decide.decisionId, ForgeProcedure.assignmentDecisionId)
    assertEquals(decide.owner, actor)

    val partition = as[DecisionQuery.Partition](decide.query)
    // The panel's own copy, declared beside the sections it frames.
    assertEquals(partition.heading, Some("Forge a relic"))
    assertEquals(partition.confirmLabel, Some("Complete Forge"))
    assertEquals(partition.sections, Vector(
      DecisionSection(ForgeProcedure.favorSectionKey, "Pay Favor",
        f.cost.favor),
      DecisionSection(ForgeProcedure.secretSectionKey, "Pay Secret",
        f.cost.secrets)))
    assertEquals(partition.options, f.targets.map(target =>
      DecisionOption.Denizen(DecisionOptionRef.Denizen(target.denizenId))))

    // The query states enough to be answerable and worth asking -- nothing
    // here appeals to the suit banks, which Forge no longer reads at all.
    assertEquals(DecisionQueries.wellFormed(decide.decisionId, decide.query),
      Right(()): Either[OathViolation, Unit])

  test("a three-favor or three-secret site declares no Decide node at all " +
      "and resolves its forced split without parking"):
    // Deep Woods' printed cost is overridden to a single resource, exactly
    // as the neighbouring cost tests do.
    Vector(Tokens(3, 0), Tokens(0, 3)).foreach { printed =>
      val base = forgeable
      val altered = catalog.copy(sites = catalog.sites.map(definition =>
        if definition.id != base.siteId then definition
        else TestCards.siteLike(definition)(
          forgeRequirements = Some(printed))))
      val f = base.copy(cost = printed)
      assert(!ForgeProcedure.parks(f.cost), s"${f.cost} should not park")
      val tree = accepted(
        ForgeProcedure.build(altered, f.ready, actor))
      // The decision lives inside a `Branch`, so its absence shows as the
      // branch's absence from the root's children.
      assertEquals(tree.children(1).children.collect {
        case branch: Branch => branch }, Vector.empty,
        s"a ${f.cost} Forge must declare no decision branch")
      // The supply payment, the node that pays and forges, and the two
      // Game Log notes behind it.
      assertEquals(tree.children(1).children.size, 4)

      // One command: no park, no answer, and the determined split applied.
      val finished = expectFinished(
        ProcedureWalker.advance(f.ready, tree, None, noPowers))
      val finalState = finished.state
      val steps = finished.steps
      val section =
        if f.cost.favor > 0 then ForgeProcedure.favorSectionKey
        else ForgeProcedure.secretSectionKey
      assertEquals(steps.map(_.nodeId), Vector("1.0.0", "1.1"))
      assertEquals(RecordedOps.of(steps(1)), f.targets.map(target =>
        expectedPayment(actor, target.denizenId, section)) :+
        Play(f.relic,
          PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
          Location.PlayArea(actor), Orientation.FaceDown))
      assertEquals(
        finalState.game.current.map.sites(f.siteId).denizens.collect {
          case d: DenizenState => d.tokens },
        Vector.fill(3)(
          if f.cost.favor > 0 then Tokens(1, 0) else Tokens(0, 1)))
      assertEquals(finalState.game.current.walkerPending, None)
    }

  // ---------------------------------------------------------------------
  // Answer validation: the generic validator bites, in both directions.
  // ---------------------------------------------------------------------

  test("the assignment decision rejects stale, duplicate, incomplete, " +
      "minimum-violating, unknown-section and choose-one answers"):
    val f = forgeable
    val tree = accepted(ForgeProcedure.build(catalog, f.ready, actor))
    val park = parkAtAssignment(f.ready, tree)
    val pending = park.pending
    val atPark = park.state
    val legal = legalPlacements(f)

    // Messenger is not a card at the Forge site.
    val stale = legal.updated(2, DecisionPlacement(DecisionOptionRef.Denizen(
      CatalogNames.denizen("Messenger")), legal(2).sectionKey))
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
      if shortSection == ForgeProcedure.favorSectionKey then
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
        DecisionAnswer.ChooseOneAnswer(legal.head.option), actor),
      noPowers))
      .contains("expects a partition answer"))

  test("an answer from a player who is not the parked actor is rejected " +
      "as the wrong player"):
    val f = forgeable
    val tree = accepted(ForgeProcedure.build(catalog, f.ready, actor))
    val park = parkAtAssignment(f.ready, tree)
    val pending = park.pending
    val atPark = park.state
    val other = p2

    assertEquals(ProcedureWalker.resolve(atPark, tree,
      pending, answerOf(legalPlacements(f), other), noPowers),
      Left(OathViolation.WrongPlayer(actor, other))
        : Either[OathViolation, WalkerOutcome])

  test("a denizen that gains a token after the park leaves too few " +
      "targets, so the Forge cannot be answered"):
    val f = forgeable
    val tree = accepted(ForgeProcedure.build(catalog, f.ready, actor))
    val park = parkAtAssignment(f.ready, tree)
    val pending = park.pending
    val atPark = park.state
    val legal = legalPlacements(f)

    val moved = Table.from(atPark).tokens(f.targets.head.denizenId, favor = 1)
      .unchecked

    assertEquals(ForgeProcedure.eligibleTargets(moved, actor),
      f.targets.drop(1))

    // Losing a target does not merely invalidate the old answer: the printed
    // cost needs three placements and only two options remain, so the
    // rebuilt query cannot be satisfied by ANY answer and is rejected as
    // unanswerable before the submitted one is even compared against it.
    assert(rejection(resolveWith(f, moved, tree, pending, legal))
      .contains("declares section minimums no answer can meet"))

  // ---------------------------------------------------------------------
  // Operations: the accepted answer's full operation vector.
  // ---------------------------------------------------------------------

  test("a legal answer finishes the walk with three player-funded " +
      "PayCosts and the relic play, consulting no suit bank"):
    val f = forgeable
    val tree = accepted(ForgeProcedure.build(catalog, f.ready, actor))
    val park = parkAtAssignment(f.ready, tree)
    val pending = park.pending
    val atPark = park.state
    val legal = legalPlacements(f)
    val banksBefore = atPark.banks.favor
    val favorBefore = Look(atPark).favor(actor)
    val secretsBefore = Look(atPark).faceUpSecrets(actor)

    val finished = expectFinished(resolveWith(f, atPark, tree, pending, legal))
    val finalState = finished.state
    val steps = finished.steps

    val expected: Vector[CoreOperation] = legal.map { placement =>
      val denizen = as[DecisionOptionRef.Denizen](placement.option).id
      expectedPayment(actor, denizen, placement.sectionKey)
    } :+ Play(f.relic,
      PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
      Location.PlayArea(actor), Orientation.FaceDown)

    assertEquals(steps.map(_.nodeId), Vector("1.1.0", "1.2"))
    assertEquals(steps.head.payload,
      ChoicePayload(ForgeProcedure.assignmentDecisionId,
        PartitionAnswer(legal), actor): WalkerStepPayload)
    assertEquals(RecordedOps.of(steps(1)), expected)

    assertEquals(Look(finalState).supply(actor), SupplyTrack.Maximum - 1)
    assertEquals(finalState.game.current.map.sites(f.siteId).denizens.collect {
      case d: DenizenState => d.tokens.favor + d.tokens.secrets
    }, Vector(1, 1, 1))

    // The actor paid, and the suit banks did not move at all.
    assertEquals(finalState.banks.favor, banksBefore)
    val after = Look(finalState).player(actor)
    assertEquals(after.board.favor, favorBefore - f.cost.favor)
    assertEquals(after.board.faceUpSecrets, secretsBefore - f.cost.secrets)

    assertEquals(finalState.game.current.commonCards.relicDeck.headOption,
      f.ready.game.current.commonCards.relicDeck.drop(1).headOption)
    assertEquals(after.relics.last,
      RelicState(f.relic, Orientation.FaceDown, Tokens.empty))
    assertEquals(finalState.game.current.walkerPending, None)

  test("the section a target is placed in decides which resource it " +
      "receives, whatever the site's own printed split"):
    val f = forgeable
    val tree = accepted(ForgeProcedure.build(catalog, f.ready, actor))
    val park = parkAtAssignment(f.ready, tree)
    val pending = park.pending
    val atPark = park.state

    // Reverse the printed order: the same three targets, the same two
    // minima, the opposite assignment.
    val reversed = legalPlacements(f).map(_.sectionKey).reverse
    val placements = f.targets.zip(reversed).map { case (target, section) =>
      DecisionPlacement(DecisionOptionRef.Denizen(target.denizenId), section) }

    val finished = expectFinished(
      resolveWith(f, atPark, tree, pending, placements))
    val finalState = finished.state
    val steps = finished.steps
    assertEquals(RecordedOps.of(steps(1)).init, placements.map { placement =>
      val denizen = as[DecisionOptionRef.Denizen](placement.option).id
      expectedPayment(actor, denizen, placement.sectionKey)
    })
    assertEquals(finalState.game.current.map.sites(f.siteId).denizens.collect {
      case denizen: DenizenState => denizen.tokens
    }, reversed.map(section =>
      if section == ForgeProcedure.favorSectionKey then Tokens(1, 0)
      else Tokens(0, 1)))


  // ---------------------------------------------------------------------
  // Resume: a start-only gate never re-gates a resume.
  // ---------------------------------------------------------------------

  test("a Forge started with exactly one supply is still resumable and " +
      "answerable"):
    val f = forgeable
    val poor = Table.from(f.ready).supply(actor, 1).unchecked
    val tree = accepted(ForgeProcedure.build(catalog, poor, actor))
    val park = parkAtAssignment(poor, tree)
    val pending = park.pending
    val atPark = park.state
    assertEquals(Look(atPark).supply(actor), 0)

    // Re-running the start gate here would strand a legally started Forge.
    assertEquals(rejects(ForgeProcedure.build(catalog, atPark, actor)),
      OathViolation.InsufficientSupply(1, 0): OathViolation)
    // Resume derivation must not re-run the start-only supply gate.
    val resumed = accepted(ForgeProcedure.rebuild(catalog, atPark, actor))

    val finished = expectFinished(resolveWith(f, atPark,
      resumed, pending, legalPlacements(f)))
    val finalState = finished.state
    val steps = finished.steps
    assertEquals(steps.map(_.nodeId), Vector("1.1.0", "1.2"))
    assertEquals(Look(finalState).supply(actor), 0)
    assertEquals(Look(finalState).player(actor).relics.last,
      RelicState(f.relic, Orientation.FaceDown, Tokens.empty))

  // ---------------------------------------------------------------------
  // Shared readers and window inventory.
  // ---------------------------------------------------------------------

  test("eligibleTargets is the same set the start gate derives, and empty " +
      "without a pawn"):
    val f = forgeable
    assertEquals(ForgeProcedure.eligibleTargets(f.ready, actor),
      accepted(ForgeRules.validate(catalog, f.ready, f.actorState,
        f.siteId))._1)
    assertEquals(ForgeProcedure.eligibleTargets(f.ready, actor), f.targets)
    assertEquals(ForgeProcedure.eligibleTargets(withoutPawn(f.ready), actor),
      Vector.empty[SiteDenizenTarget])

  test("the forged relic is the authoritative deck top, read at execution " +
      "time rather than closed over by the tree"):
    val f = forgeable
    val tree = accepted(ForgeProcedure.build(catalog, f.ready, actor))
    val park = parkAtAssignment(f.ready, tree)
    val pending = park.pending
    val atPark = park.state
    val legal = legalPlacements(f)

    // The same tree, walked against a state whose deck top changed after the
    // park, forges the NEW top.
    // No Table step reorders the relic deck.
    val restacked = atPark.updateCurrent(_.copy(commonCards =
        atPark.game.current.commonCards.copy(relicDeck =
          atPark.game.current.commonCards.relicDeck.reverse)))
    val newTop = restacked.game.current.commonCards.relicDeck.head
    assertNotEquals(newTop, f.relic)
    val steps = expectFinished(resolveWith(f, restacked, tree, pending, legal))
      .steps
    assertEquals(RecordedOps.of(steps(1)).last, Play(newTop,
      PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
      Location.PlayArea(actor),
      Orientation.FaceDown): CoreOperation)

    // An emptied deck fails the leaf rather than forging nothing silently.
    val emptied = Table.from(atPark).relicDeckEmptied.unchecked
    assertEquals(resolveWith(f, emptied, tree, pending, legal),
      Left(OathViolation.ForgeUnavailable("relic deck is empty"))
        : Either[OathViolation, WalkerOutcome])

  /** Every node reachable from `node` through STATIC children, including
    * `node` itself. Forge's assignment decision hangs off a `Branch`, whose
    * children are chosen at walk time, so it is deliberately not reached
    * here -- it carries no window, which is what this is used to inventory.
    */
  private def allNodes(node: Operation): Vector[Operation] = node match
    case _: LeafOperation => Vector(node)
    case _ => node +: node.children.flatMap(allNodes)

  test("the tree windows exactly its modifier decision, the Forge and its " +
      "supply payment; every other node carries none"):
    val f = forgeable
    val tree = accepted(ForgeProcedure.build(catalog, f.ready, actor))
    val windowed = allNodes(tree).filter(_.window.isDefined)

    assertEquals(windowed.size, 3,
      s"expected exactly 3 windowed nodes, found ${windowed.size}: $windowed")
    assertEquals(windowed.map(_.window).toSet, Set[Option[PowerWindow]](
      Some(PowerWindow.ForgeModifierSelection),
      Some(PowerWindow.ForgeActionEligibility), Some(PowerWindow.ForgeCost)))
    assert(windowed.contains(tree.children(1)),
      "the Forge, after the modifier decision, must carry " +
        "ForgeActionEligibility")
    assertEquals(windowed.collect { case node: BuildOps => node.window },
      Vector(Some(PowerWindow.ForgeCost)),
      "exactly the supply-paying BuildOps carries ForgeCost -- the " +
        "completion BuildOps stays unwindowed")

  test("a modifier for the Forge is asked about first, beside Start Forge"):
    val powers = EmptyWindowModifier.powersFor(MajorActionType.Forge)
    val board = forgeBoard().adviser(actor, "Tents").ready
    val started = accepted(new OathRules(catalog, walkerPowerCatalog = powers)
      .startWalker(Ready(board), ActionRef.Forge, actor))
    assertEquals(
      EmptyWindowModifier.parkedWithFirstOption(started.state, powers),
      ("forge.modifiers", Some(DecisionOption.Button(ModifierPicks.start,
        "Start Forge"))))

object ForgeProcedureSuite:
  /** The board every test starts from, plus the facts a legal answer is
    * built out of. Declared outside the suite class so its case-class type
    * test carries no unchecked outer reference.
    */
  final case class Forgeable(ready: ReadyGame, actorState: PlayerState,
      siteId: SiteId, targets: Vector[SiteDenizenTarget], cost: Tokens,
      relic: RelicId)
