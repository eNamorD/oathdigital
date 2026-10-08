package oathdigital.gameplay

import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
// ProcedureWalkerFixture's ready, with S3 cleared, replaces the fixture's.
import oathdigital.model.TestGameFixtures.{ready as _, _}
import oathdigital.testkit.Table
import oathdigital.testkit.Outcomes.recorded
import oathdigital.testkit.PowerStubs.OperationRestrictionStub
import oathdigital.testkit.ProcedureWalkerFixture._

/** ProcedureWalker's delta leaves: a walk records one
  * `WalkerStepRecorded` per executed leaf, BuildOps and composites shrink or
  * reject as their requiredness says, and restrictions are checked first.
  */
class ProcedureWalkerDeltaSuite extends munit.FunSuite:
  /** The powers of one test power that registers `restriction`. */
  private def restricting(restriction: OperationRestriction): WalkerPowers =
    WalkerPowers(Vector(OperationRestrictionStub(
      PowerId("test.restriction"), restriction)))

  /** Stub of OperationRestriction: refuses every `SpendSupply`. */
  private val noSpending = new OperationRestriction:
    override def reason(state: ReadyGame, operation: CoreOperation)
        : Option[OperationReason] = operation match
      case _: SpendSupply => Some(OperationReason("no-spending",
        "supply cannot be spent", OperationReasonKind.Impossible))
      case _ => None

  test("a power's operation restriction holds at a plain delta leaf"):
    val optional = Sequence(Vector(SpendSupply(actor, 1, required = false)))
    ProcedureWalker.advance(ready, optional, None, restricting(noSpending)) match
      case Right(WalkerOutcome.Finished(state, events)) =>
        assertEquals(state, ready)
        assertEquals(events, Vector.empty)
      case other => fail(s"expected a Finished walk, got $other")
    assertEquals(ProcedureWalker.advance(ready,
      Sequence(Vector(SpendSupply(actor, 1))), None, restricting(noSpending)),
      Left(OathViolation.CoreOperationRejected("no-spending",
        "supply cannot be spent")))

  test("fresh walk of a legal delta pair finishes and records one event per leaf"):
    val tree: Operation = Sequence(movePawnToS3, spendOneSupply)

    val (finalState, events) = ProcedureWalker.advance(ready, tree, None,
        noPowers) match
      case Right(WalkerOutcome.Finished(state, recorded)) => (state, recorded)
      case other => fail(s"expected a Finished walk, got $other")

    assertEquals(pawnSiteOf(finalState), Some(sites(2)))
    assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
    assertEquals(events.size, 2)
    val steps = events.map(recorded)
    assertEquals(steps.map(_.nodeId), Vector("0", "1"))
    assertEquals(steps.map(_.ops),
      Vector(Vector[CoreOperation](movePawnToS3),
      Vector[CoreOperation](spendOneSupply)))
    steps.foreach(step => assert(step.ops.nonEmpty))
    // Re-applying each recorded ops batch reproduces the walked state.
    assertEquals(applyEvents(ready, events), finalState)

  test("all-skipped BuildOps records no delta step"):
    val empty = Table.from(ready).bankFavor(Suit.Order, 0).unchecked
    val tree = BuildOps((_, _) =>
      Right(Vector(Gain.Favor(actor, Suit.Order, 1))))
    ProcedureWalker.advance(empty, tree, None, noPowers) match
      case Right(WalkerOutcome.Finished(state, events)) =>
        assertEquals(state, empty)
        assertEquals(events, Vector.empty)
      case other => fail(s"expected a Finished walk, got $other")

  test("BuildOps records actual reduced count"):
    val six = Table.from(ready).supply(actor, 6).unchecked
    val tree = BuildOps((_, _) => Right(Vector(GainSupply(actor, 3))))
    ProcedureWalker.advance(six, tree, None, noPowers) match
      case Right(WalkerOutcome.Finished(_, events)) =>
        assertEquals(
          events.collect { case step: WalkerStepRecorded => step.ops },
          Vector(Vector[CoreOperation](GainSupply(actor, 1))))
      case other => fail(s"expected a Finished walk, got $other")

  /** Stub of OperationRestriction: refuses the `Discard.Denizen` composite
    * only, never the `Move`s it holds, so it can only act if the walker
    * checks the composite before splitting it. */
  private val wholeDiscard = new OperationRestriction:
    override def reason(state: ReadyGame, operation: CoreOperation)
        : Option[OperationReason] = operation match
      case _: Discard.Denizen => Some(OperationReason("immune",
        "the discard is refused", OperationReasonKind.Impossible))
      case _ => None

  test("a composite in the tree is checked whole before it is split"):
    val discard = Discard.Denizen(siteDenizen.id,
      PositionedLocation(Location.Site(sites.head)), Region.Cradle,
      Suit.Order, 1, 0, actor)
    ProcedureWalker.advance(ready, Sequence(Vector(discard)), None,
      restricting(wholeDiscard)) match
      case Right(WalkerOutcome.Finished(state, events)) =>
        assertEquals(state, ready)
        assertEquals(events, Vector.empty)
      case other => fail(s"expected a Finished walk, got $other")
    assertEquals(ProcedureWalker.advance(ready,
      Sequence(Vector(discard.copy(required = true))), None,
      restricting(wholeDiscard)), Left(OathViolation.CoreOperationRejected(
        "immune", "the discard is refused")))

  test("BuildOps filters immune discard and replays the legal discard"):
    val first = Discard.Denizen(siteDenizen.id,
      PositionedLocation(Location.Site(sites.head)), Region.Cradle,
      Suit.Order, 1, 0, actor)
    val second = Discard.Denizen(worldDenizen,
      PositionedLocation(Location.Site(sites(1))), Region.Cradle,
      Suit.Order, 0, 0, actor)
    // D1 leaves the world deck for S2.
    val source = Table.from(ready).denizen(worldDenizen, at = sites(1))
      .unchecked
    /** Stub of OperationRestriction: refuses `first` as immune. */
    val immunity = new OperationRestriction:
      override def reason(state: ReadyGame, operation: CoreOperation) =
        Option.when(operation == first)(OperationReason("immune",
          "first card cannot be discarded", OperationReasonKind.Impossible))
    val tree = Sequence(
      Vector(BuildOps((_, _) => Right(Vector(first, second)))))
    ProcedureWalker.advance(source, tree, None, restricting(immunity)) match
      case Right(WalkerOutcome.Finished(state, events)) =>
        assertEquals(
          events.collect { case step: WalkerStepRecorded => step.ops },
          Vector(Vector[CoreOperation](second)))
        val replay = events.foldLeft[Either[OathViolation, OathState]](
          Right(OathState.Ready(source))) { (result, event) =>
          result.flatMap(ProcedureWalker.applyRecorded(_, recorded(event)))
        }
        assertEquals(replay, Right(OathState.Ready(state)))
      case other => fail(s"expected a Finished walk, got $other")

  test("optional immune-only discard records nothing; required discard rejects"):
    val discard = Discard.Denizen(siteDenizen.id,
      PositionedLocation(Location.Site(sites.head)), Region.Cradle,
      Suit.Order, 1, 0, actor)
    /** Stub of OperationRestriction: refuses every operation as immune. */
    val immunity = new OperationRestriction:
      override def reason(state: ReadyGame, operation: CoreOperation): Option[OperationReason] =
        Some(OperationReason("immune", "site card cannot be discarded",
          OperationReasonKind.Impossible))
    def tree(operation: CoreOperation): Operation = Sequence(Vector(
      BuildOps((_, _) => Right(Vector(operation)))))
    ProcedureWalker.advance(ready, tree(discard), None,
      restricting(immunity)) match
      case Right(WalkerOutcome.Finished(state, events)) =>
        assertEquals(state, ready)
        assertEquals(events, Vector.empty)
      case other => fail(s"expected a Finished walk, got $other")
    assertEquals(ProcedureWalker.advance(ready,
      tree(discard.copy(required = true)), None, restricting(immunity)),
      Left(OathViolation.CoreOperationRejected("immune",
        "site card cannot be discarded")))

  // `required` lives on the composite (PayCost, Draw, Exchange, a required
  // Play/Replace/Discard), but the walker walks a composite's `Move` children
  // one by one and a bare `Move` is best-effort. The flag must survive the
  // walk, or an unaffordable payment silently shrinks instead of rejecting.
  private val tooMuchFavor: Int =
    ready.game.current.players.find(_.player == actor).get.board.favor + 1

  test("a required composite in the tree rejects when it cannot fully execute"):
    val payment = PayCost(actor, Location.SharedBank,
      Cost(favorBurnt = tooMuchFavor))
    assert(payment.required)
    // The actor holds one favor fewer than the payment burns.
    val rejection = Left(OathViolation.CoreOperationRejected(
      "insufficient-pieces",
      s"player play area contains ${tooMuchFavor - 1} of requested favor"))
    assertEquals(ProcedureWalker.advance(ready, Sequence(payment), None,
      noPowers), rejection)
    assertEquals(ProcedureWalker.advance(ready,
      Sequence(Vector[Operation](payment)), None, noPowers), rejection)
    // The same payment as a batch item was already strict.
    assertEquals(ProcedureWalker.advance(ready,
      BuildOps((_, _) => Right(Vector(payment))), None, noPowers), rejection)

  test("a required composite that can execute records its children and finishes"):
    val payment = PayCost(actor, Location.SharedBank, Cost(favorBurnt = 1))
    ProcedureWalker.advance(ready, Sequence(payment), None, noPowers) match
      case Right(WalkerOutcome.Finished(state, events)) =>
        assertEquals(
          events.collect { case step: WalkerStepRecorded => step.ops },
          Vector(payment.children.collect { case child: Move => child }))
        assertEquals(state.game.current.players.find(_.player == actor).get
          .board.favor, tooMuchFavor - 2)
      case other => fail(s"expected a Finished walk, got $other")

  test("an optional composite still shrinks best-effort"):
    val gain = Gain.Favor(actor, Suit.Order, 99)
    ProcedureWalker.advance(ready, Sequence(gain), None, noPowers) match
      case Right(WalkerOutcome.Finished(state, _)) =>
        // It takes all the Order bank holds rather than rejecting.
        assertEquals(state.game.current.players.find(_.player == actor).get
          .board.favor, ready.game.current.players.find(_.player == actor).get
          .board.favor + ready.banks.favor(Suit.Order))
        assertEquals(state.banks.favor(Suit.Order), 0)
      case other => fail(s"expected a Finished walk, got $other")
