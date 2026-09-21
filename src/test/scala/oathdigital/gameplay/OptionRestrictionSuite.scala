package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction, PowerCtx}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers,
  WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

object OptionRestrictionSuite {
  /** A power whose only contribution is one option restriction at one window. */
  final case class TestOptionRestrictionPower(id: PowerId, hook: PowerWindow,
      fn: (PowerCtx, DecisionOptionRef) => Option[OathViolation])
      extends ContributingPower {
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(hook -> Vector(OptionRestriction(fn)))
  }
}

class OptionRestrictionSuite extends munit.FunSuite {
  import OptionRestrictionSuite.TestOptionRestrictionPower

  private val actor: PlayerId = playerId
  private val window = PowerWindow.ChallengeAmountSelection
  private val blocked = OathViolation.InvalidEventOrder("blocked by test power")
  private def site(id: String) = DecisionOptionRef.Site(SiteId(id))
  private def option(id: String) = DecisionOption.Site(site(id))
  private val all = Vector("a", "b", "c").map(option)
  private val after = ModifyDicePool(PoolKey("after"), 1)

  private def forbid(power: String, sites: String*) = TestOptionRestrictionPower(
    PowerId(power), window, (_, ref) => Option.when(sites.exists(site(_) == ref))(blocked))

  private val optionalPick = Sequence(Vector[Operation](
    Decide("pick", actor, DecisionQuery.ChooseMany(0, 3, all, Some("Pick")),
      window = Some(window)), after))
  private def requiredPick(query: DecisionQuery): Operation = Sequence(
    Vector[Operation](Decide("pick", actor, query, window = Some(window))))

  test("without a restriction the parked query is what the tree declared") {
    val Right(WalkerOutcome.Parked(pending, _)) = ProcedureWalker.advance(ready,
      optionalPick, None, WalkerPowers.empty): @unchecked
    assertEquals(ProcedureWalker.openDecisions(ready, optionalPick, pending,
      WalkerPowers.empty).map(_.query),
      Vector(DecisionQuery.ChooseMany(0, 3, all, Some("Pick"))))
  }

  test("a forbidden option is absent from the parked query and from every answer") {
    val powers = WalkerPowers(Vector(forbid("test.no-b", "b")))
    val Right(WalkerOutcome.Parked(pending, _)) = ProcedureWalker.advance(ready,
      optionalPick, None, powers): @unchecked
    assertEquals(ProcedureWalker.openDecisions(ready, optionalPick, pending,
      powers).map(_.query), Vector(DecisionQuery.ChooseMany(0, 2,
      Vector(option("a"), option("c")), Some("Pick"))))
    assertEquals(ProcedureWalker.resolve(ready, optionalPick, pending, Answered(
      "pick", DecisionAnswer.ChooseManyAnswer(Vector(site("b"))), actor), powers),
      Left(OathViolation.InvalidEventOrder(
        "decision pick does not offer a selected option")))
    assert(ProcedureWalker.resolve(ready, optionalPick, pending, Answered("pick",
      DecisionAnswer.ChooseManyAnswer(Vector(site("a"))), actor), powers).isRight)
  }

  test("restrictions from two powers compose") {
    val powers = WalkerPowers(Vector(forbid("test.no-a", "a"),
      forbid("test.no-b", "b")))
    val Right(WalkerOutcome.Parked(pending, _)) = ProcedureWalker.advance(ready,
      optionalPick, None, powers): @unchecked
    assertEquals(ProcedureWalker.openDecisions(ready, optionalPick, pending,
      powers).map(_.query), Vector(DecisionQuery.ChooseMany(0, 1,
      Vector(option("c")), Some("Pick"))))
  }

  test("an optional decision with nothing left to offer is not asked") {
    val powers = WalkerPowers(Vector(forbid("test.no-all", "a", "b", "c")))
    ProcedureWalker.advance(ready, optionalPick, None, powers) match {
      case Right(WalkerOutcome.Finished(_, events)) =>
        assertEquals(events.collect { case step: WalkerStepRecorded => step.ops },
          Vector(Vector[CoreOperation](after)))
      case other => fail(s"the emptied decision must be dropped, got $other")
    }
  }

  test("a restriction at another window changes nothing") {
    val elsewhere = TestOptionRestrictionPower(PowerId("test.elsewhere"),
      PowerWindow.ChallengeBannerSelection, (_, _) => Some(blocked))
    val Right(WalkerOutcome.Parked(pending, _)) = ProcedureWalker.advance(ready,
      optionalPick, None, WalkerPowers(Vector(elsewhere))): @unchecked
    assertEquals(ProcedureWalker.openDecisions(ready, optionalPick, pending,
      WalkerPowers(Vector(elsewhere))).map(_.query),
      Vector(DecisionQuery.ChooseMany(0, 3, all, Some("Pick"))))
  }

  test("a required decision whose every option is forbidden reports the restriction's violation at command entry") {
    val one = requiredPick(DecisionQuery.ChooseOne(Vector(option("a"), option("b"))))
    val many = requiredPick(DecisionQuery.ChooseMany(1, 2, all.take(2)))
    val all2 = WalkerPowers(Vector(forbid("test.no-ab", "a", "b", "c")))
    assertEquals(ProcedureWalker.restrictionViolations(one, all2, ready, actor),
      Vector(blocked))
    assertEquals(ProcedureWalker.restrictionViolations(many, all2, ready, actor),
      Vector(blocked))
    val some = WalkerPowers(Vector(forbid("test.no-a", "a")))
    assertEquals(ProcedureWalker.restrictionViolations(one, some, ready, actor),
      Vector.empty[OathViolation])
    assertEquals(ProcedureWalker.restrictionViolations(optionalPick, all2, ready,
      actor), Vector.empty[OathViolation])
  }
}
