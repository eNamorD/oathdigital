package oathdigital.gameplay

import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ChoicePayload, ProcedureWalker,
  WalkerOutcome, WalkerSimulation, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** A choose-one marked `passWhenEmpty` is passed when the search leaves it
  * no option, and parks as any decision does when one is left (global
  * operation restrictions design, "Empty choices"). Locked refuses to discard
  * the faceup Sealing Ward, which is what prunes an option here.
  */
class PassWhenEmptySuite extends munit.FunSuite:
  private val powers = WalkerPowerCatalog.default(catalog)
  private val lockedCard = CatalogNames.denizen("Sealing Ward")
  private val ready = Table.start.adviser(p1, lockedCard).ready
  private val ask = "test.ask"
  private val next = "test.next"
  private def button(key: String) =
    DecisionOption.Button(DecisionOptionRef.Button(key), key)
  private def ref(key: String): DecisionOptionRef = DecisionOptionRef.Button(key)
  private def chose(key: String): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(ref(key))

  private val discardLocked: Operation = Discard.Denizen(lockedCard,
    PositionedLocation(Location.PlayArea(p1)), Region.Provinces,
    catalog.suitOf(lockedCard).get, 0, 0, p1, required = true)

  /** A choice of `keys` passed when empty, then the discard when "discard"
    * was chosen. */
  private def passable(keys: String*): Operation = Sequence(Vector[Operation](
    Decide(ask, p1, DecisionQuery.ChooseOne(keys.toVector.map(button)),
      passWhenEmpty = true),
    Branch((_, pending) => pending.answered.collectFirst {
      case Answered(`ask`, answer, _) => answer
    }.filter(_ == chose("discard")).toVector.map(_ => discardLocked))))

  private def offered(tree: Operation): Vector[DecisionOptionRef] =
    ProcedureWalker.advance(ready, tree, None, powers) match
      case Right(WalkerOutcome.Parked(pending, _)) =>
        ProcedureWalker.openDecisions(ready, tree, pending, powers).head
          .query match
          case DecisionQuery.ChooseOne(options, _) => options.map(_.ref)
          case other => fail(s"expected a choose-one, got $other")
      case other => fail(s"expected a park, got $other")

  test("one option left parks, offering it for the player to confirm"):
    assertEquals(offered(passable("discard", "keep")), Vector(ref("keep")))

  test("a single declared option parks"):
    assertEquals(offered(passable("keep")), Vector(ref("keep")))

  test("with no option left the decision is passed and the action still runs"):
    val tree = passable("discard")
    assert(WalkerSimulation.starts(tree, ready, powers))
    ProcedureWalker.advance(ready, tree, None, powers) match
      case Right(WalkerOutcome.Finished(_, events)) =>
        assertEquals(events.collect {
          case WalkerStepRecorded(_, choice: ChoicePayload, _, _) => choice
        }, Vector.empty)
      case other => fail(s"expected a finished walk, got $other")

  test("a search passes a later choice with nothing left, so the earlier " +
      "option stays offered"):
    val tree = Sequence(Vector[Operation](
      Decide(next, p1, DecisionQuery.ChooseOne(Vector(button("onward"),
        button("stop")))),
      Branch((_, pending) =>
        if pending.answered.exists(_.answer == chose("onward"))
        then Vector(passable("discard")) else Vector.empty)))
    assertEquals(offered(tree), Vector(ref("onward"), ref("stop")))

  test("only a choose-one may be passed when empty"):
    intercept[IllegalArgumentException](Decide(ask, p1,
      DecisionQuery.ChooseMany(0, 1, Vector(button("a")), None),
      passWhenEmpty = true))
