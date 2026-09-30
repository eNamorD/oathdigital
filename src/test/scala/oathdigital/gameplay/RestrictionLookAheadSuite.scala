package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{OfferHost, PowerCtx}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers,
  WalkerSimulation}
import oathdigital.model._
import oathdigital.testkit.Table

/** The walker hides an option whose answer would break a `Restriction`, even
  * when the restriction's window exists only once that answer is given, so a
  * player is never refused a choice they were offered.
  */
class RestrictionLookAheadSuite extends munit.FunSuite:
  import ProcedureWalkerSuite.TestRestrictionPower

  private val ready = Table.start.ready
  private val actor = ready.game.current.turn.activePlayer
  private val nested = PowerWindow.CampaignActionEligibility
  private val ask = "test.ask"
  private val violation = OathViolation.CampaignUnavailable("the test forbids it")
  private def button(key: String) =
    DecisionOption.Button(DecisionOptionRef.Button(key), key)
  private def ref(key: String): DecisionOptionRef = DecisionOptionRef.Button(key)

  private val forbidding = TestRestrictionPower(PowerId("test.forbidding"),
    nested, (_, _) => Some(violation))
  private val powers = WalkerPowers(Vector(forbidding))

  /** A subtree carrying the forbidding window. */
  private val opened: Vector[Operation] =
    Vector(Sequence(Vector.empty, Some(nested)))

  /** `question`, then the forbidding subtree when `forbids` holds for the
    * recorded answer.
    */
  private def asking(question: DecisionQuery,
      forbids: DecisionAnswer => Boolean): Operation =
    Sequence(Vector[Operation](Decide(ask, actor, question),
      Branch((_, pending) => if pending.answered.exists {
        case Answered(`ask`, answer, _) => forbids(answer)
        case _ => false
      } then opened else Vector.empty)))

  private val yesOrNo = asking(DecisionQuery.ChooseOne(
    Vector(button("yes"), button("no"))), _ ==
      DecisionAnswer.ChooseOneAnswer(ref("yes")))

  private def parked(tree: Operation): PendingTree =
    val Right(WalkerOutcome.Parked(pending, _)) =
      ProcedureWalker.advance(ready, tree, None, powers): @unchecked
    pending

  private def parkedQuery(tree: Operation): DecisionQuery =
    ProcedureWalker.openDecisions(ready, tree, parked(tree), powers).head.query

  test("an option whose answer opens a forbidden subtree is not offered"):
    assertEquals(parkedQuery(yesOrNo),
      DecisionQuery.ChooseOne(Vector(button("no"))))
    assertEquals(WalkerSimulation.previewParked(ready, yesOrNo, parked(yesOrNo),
      powers).map(_.map(_.option.ref)), Right(Vector(ref("no"))))

  test("a submitted forbidden answer is still rejected"):
    assertEquals(ProcedureWalker.resolve(ready, yesOrNo, parked(yesOrNo),
      Answered(ask, DecisionAnswer.ChooseOneAnswer(ref("yes")), actor), powers),
      Left(OathViolation.InvalidEventOrder(
        s"decision $ask does not offer the selected option")))
    assert(ProcedureWalker.resolve(ready, yesOrNo, parked(yesOrNo),
      Answered(ask, DecisionAnswer.ChooseOneAnswer(ref("no")), actor),
      powers).isRight)

  test("a choose-many loses the forbidden option and keeps its bounds valid"):
    val tree = asking(DecisionQuery.ChooseMany(1, 2,
      Vector(button("a"), button("b"), button("c")), Some("Pick")), {
        case DecisionAnswer.ChooseManyAnswer(selected) =>
          selected.contains(ref("b"))
        case _ => false
      })
    assertEquals(parkedQuery(tree), DecisionQuery.ChooseMany(1, 2,
      Vector(button("a"), button("c")), Some("Pick")))

  test("an optional choose-many the probe empties is not asked"):
    val tree = asking(DecisionQuery.ChooseMany(0, 1, Vector(button("b")),
      Some("Pick")), _ => true)
    ProcedureWalker.advance(ready, tree, None, powers) match
      case Right(WalkerOutcome.Finished(_, _)) => ()
      case other => fail(s"the emptied decision must be passed, got $other")

  private def amounts(forbidden: Set[Int]): Operation =
    asking(DecisionQuery.ChooseAmount(0, 4, Some("How many?"), "Confirm"), {
      case DecisionAnswer.ChooseAmountAnswer(amount) => forbidden(amount)
      case _ => false
    })

  test("a choose-amount narrows to the permitted values"):
    assertEquals(parkedQuery(amounts(Set(3, 4))),
      DecisionQuery.ChooseAmount(0, 2, Some("How many?"), "Confirm"))
    assertEquals(parkedQuery(amounts(Set(0))),
      DecisionQuery.ChooseAmount(1, 4, Some("How many?"), "Confirm"))

  test("a choose-amount whose permitted values have a gap is left whole"):
    assertEquals(parkedQuery(amounts(Set(2))),
      DecisionQuery.ChooseAmount(0, 4, Some("How many?"), "Confirm"))

  test("a choose-amount with an empty range is left declared, not thrown"):
    val tree = asking(DecisionQuery.ChooseAmount(3, 2, Some("How many?"),
      "Confirm"), _ => true)
    assertEquals(parkedQuery(tree),
      DecisionQuery.ChooseAmount(3, 2, Some("How many?"), "Confirm"))

  test("a required decision the probe empties stops the action from starting"):
    val onlyYes = asking(DecisionQuery.ChooseOne(Vector(button("yes"))),
      _ => true)
    assertEquals(ProcedureWalker.advance(ready, onlyYes, None, powers),
      Left(violation))
    assert(!WalkerSimulation.starts(onlyYes, ready, powers))

  test("a violation the answers so far already produce hides nothing"):
    val always = Sequence(Vector[Operation](Sequence(Vector.empty, Some(nested)),
      Decide(ask, actor, DecisionQuery.ChooseOne(Vector(button("yes"),
        button("no"))))))
    assertEquals(parkedQuery(always), DecisionQuery.ChooseOne(
      Vector(button("yes"), button("no"))))

  test("the walk inside a probe does not probe its own decisions"):
    var calls = 0
    val counting = TestRestrictionPower(PowerId("test.counting"), nested,
      (_, _) => { calls += 1; None })
    val inner: Operation = Sequence(Vector[Operation](
      Sequence(Vector.empty, Some(nested)),
      Decide("test.inner", actor, DecisionQuery.ChooseOne(
        Vector(button("x"), button("y"), button("z"))))))
    val host = new OfferHost:
      override val window: Option[PowerWindow] =
        Some(PowerWindow.CampaignAttackerBattlePlans)
      override val children: Vector[Operation] = Vector.empty
      def expand(offers: Vector[OfferedPlan], pass: OfferHost.Pass)
          : Vector[Operation] =
        val _ = pass.applies(inner)
        Vector.empty
    val root = Sequence(Vector[Operation](Sequence(Vector.empty, Some(nested)),
      Decide(ask, actor, DecisionQuery.ChooseOne(Vector(button("yes"),
        button("no")))), host))
    val _ = ProcedureWalker.advance(ready, root, None,
      WalkerPowers(Vector(counting)))
    // One traversal per option, each meeting the root's window once. The
    // baseline is taken only when an option adds a violation, and none does
    // here. A probe inside the host's dry run would add four more traversals
    // per host fold.
    assertEquals(calls, 2)

  test("a Restriction reads the answers so far from its context"):
    val reading = TestRestrictionPower(PowerId("test.reading"), nested,
      (ctx: PowerCtx, _) => Option.when(ctx.answered.exists(
        _.decisionId == ask))(violation))
    val root = Sequence(Vector.empty, Some(nested))
    val answered = Vector(Answered(ask,
      DecisionAnswer.ChooseOneAnswer(ref("yes")), actor))
    assertEquals(ProcedureWalker.restrictionViolations(root,
      WalkerPowers(Vector(reading)), ready, actor), Vector.empty[OathViolation])
    assertEquals(ProcedureWalker.restrictionViolations(root,
      WalkerPowers(Vector(reading)), ready, actor, answered), Vector(violation))
