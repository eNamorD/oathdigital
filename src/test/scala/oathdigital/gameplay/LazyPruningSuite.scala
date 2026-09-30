package oathdigital.gameplay

import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerSimulation}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** The walker hides an option from which no path reaches a legal end, and
  * stops looking at hidden information (global operation restrictions
  * design, "Lazy pruning" and "Testing: Slice 2"). Locked refuses to discard
  * the faceup Sealing Ward, which is what makes a path fail here.
  */
class LazyPruningSuite extends munit.FunSuite:
  private val powers = WalkerPowerCatalog.default(catalog)
  private val lockedCard = CatalogNames.denizen("Sealing Ward")
  private val ready = Table.start.adviser(p1, lockedCard).ready
  private val ask = "test.ask"
  private val next = "test.next"
  private def button(key: String) =
    DecisionOption.Button(DecisionOptionRef.Button(key), key)
  private def ref(key: String): DecisionOptionRef = DecisionOptionRef.Button(key)

  private def discardLocked(required: Boolean): Operation =
    Discard.Denizen(lockedCard, PositionedLocation(Location.PlayArea(p1)),
      Region.Provinces, catalog.suitOf(lockedCard).get, 0, 0, p1,
      required = required)

  private def answer(pending: PendingTree): Option[DecisionOptionRef] =
    pending.answered.collectFirst {
      case Answered(`ask`, DecisionAnswer.ChooseOneAnswer(chosen), _) => chosen
    }

  /** Asks `ask` with `keys`, then walks what `after` gives for the answer. */
  private def asking(keys: Vector[String],
      after: DecisionOptionRef => Vector[Operation]): Operation =
    Sequence(Vector[Operation](
      Decide(ask, p1, DecisionQuery.ChooseOne(keys.map(button))),
      Branch((_, pending) => answer(pending).toVector.flatMap(after))))

  private def offered(tree: Operation): Vector[DecisionOptionRef] =
    ProcedureWalker.advance(ready, tree, None, powers) match
      case Right(WalkerOutcome.Parked(pending, _)) =>
        ProcedureWalker.openDecisions(ready, tree, pending, powers).head
          .query match
          case DecisionQuery.ChooseOne(options, _) => options.map(_.ref)
          case other => fail(s"expected a choose-one, got $other")
      case other => fail(s"expected a park, got $other")

  private def discarding(required: Boolean): Operation =
    asking(Vector("discard", "keep"), chosen =>
      if chosen == ref("discard") then Vector(discardLocked(required))
      else Vector.empty)

  test("an option whose required operation is refused is hidden"):
    assertEquals(offered(discarding(required = true)), Vector(ref("keep")))

  test("an option whose refused operation is optional stays offered"):
    assertEquals(offered(discarding(required = false)),
      Vector(ref("discard"), ref("keep")))

  test("a later required decision, another player's, with every option " +
      "pruned hides the earlier option"):
    val tree = asking(Vector("onward", "stop"), chosen =>
      if chosen == ref("onward") then Vector(
        Decide(next, p2, DecisionQuery.ChooseOne(Vector(button("discard")))),
        discardLocked(required = true))
      else Vector.empty)
    assertEquals(offered(tree), Vector(ref("stop")))

  test("an action with no legal path does not start"):
    val tree = asking(Vector("discard"), _ => Vector(discardLocked(true)))
    assert(!WalkerSimulation.starts(tree, ready, powers))
    assert(ProcedureWalker.advance(ready, tree, None, powers).isLeft)

  test("the search stops at a roll: a refusal after it stays offered"):
    val roll = Roll(PoolKey("test.roll"), DiceSpec(DiceKind.Attack),
      RollMode.Automatic)
    val tree = asking(Vector("roll", "keep"), chosen =>
      if chosen == ref("roll") then Vector(roll, discardLocked(true))
      else Vector.empty)
    assertEquals(offered(tree), Vector(ref("roll"), ref("keep")))

  test("a decision that loops back to itself keeps both its options"):
    val stop = DecisionAnswer.ChooseOneAnswer(ref("stop"))
    val loop = Repeat((_, pending) =>
      !pending.answered.lastOption.exists(_.answer == stop),
      Decide(ask, p1, DecisionQuery.ChooseOne(Vector(button("again"),
        button("stop")))))
    assertEquals(offered(loop), Vector(ref("again"), ref("stop")))

  test("a hidden option is refused when it is answered anyway"):
    val tree = discarding(required = true)
    val Right(WalkerOutcome.Parked(pending, _)) =
      ProcedureWalker.advance(ready, tree, None, powers): @unchecked
    assertEquals(ProcedureWalker.resolve(ready, tree, pending,
      Answered(ask, DecisionAnswer.ChooseOneAnswer(ref("discard")), p1),
      powers), Left(OathViolation.InvalidEventOrder(
        s"decision $ask does not offer the selected option")))

  test("a park read again with the same powers is not searched again"):
    var builds = 0
    val tree = Sequence(Vector[Operation](
      Decide(ask, p1, DecisionQuery.ChooseOne(Vector(button("a"),
        button("b")))),
      BuildOps((_, _) => { builds += 1; Right(Vector.empty) })))
    val Right(WalkerOutcome.Parked(pending, _)) =
      ProcedureWalker.advance(ready, tree, None, powers): @unchecked
    val searched = builds
    assert(searched > 0, "the park's search must reach the build")
    ProcedureWalker.openDecisions(ready, tree, pending, powers)
    ProcedureWalker.openDecisions(ready, tree, pending, powers)
    assertEquals(builds, searched)

  test("a verdict is not reused for another state"):
    val tree = discarding(required = true)
    def offeredIn(state: ReadyGame): Vector[DecisionOptionRef] =
      val Right(WalkerOutcome.Parked(pending, _)) =
        ProcedureWalker.advance(state, tree, None, powers): @unchecked
      ProcedureWalker.openDecisions(state, tree, pending, powers).head.query
        match
          case DecisionQuery.ChooseOne(options, _) => options.map(_.ref)
          case other => fail(s"expected a choose-one, got $other")
    assertEquals(offeredIn(ready), Vector(ref("keep")))
    val facedown = Table.start.adviser(p1, lockedCard, facedown = true).ready
    assertEquals(offeredIn(facedown), Vector(ref("discard"), ref("keep")))
    assertEquals(offeredIn(ready), Vector(ref("keep")))

