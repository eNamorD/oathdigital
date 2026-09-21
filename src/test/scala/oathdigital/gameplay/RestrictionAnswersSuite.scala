package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, PowerCtx, Restriction, Transform}
import oathdigital.gameplay.setup.FirstGameSetupFixture.initialReady
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers}
import oathdigital.model._

/** The restriction traversal selects a `Branch` with the answers recorded so
  * far, so a Restriction hooked inside a subtree that only exists once a
  * decision is answered is checked once it does.
  */
class RestrictionAnswersSuite extends munit.FunSuite {
  private val actor = initialReady.game.current.turn.activePlayer
  private val nested = PowerWindow.CampaignActionEligibility
  private val ask = "test.ask"
  private val yes = DecisionOptionRef.Button("yes")
  private val no = DecisionOptionRef.Button("no")
  private val violation = OathViolation.CampaignUnavailable("the test forbids it")

  private val forbidding: ContributingPower = new ContributingPower {
    def id: PowerId = PowerId("test.forbidding")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(nested -> Vector(Restriction((_: PowerCtx, _) => Some(violation))))
  }
  private val powers = WalkerPowers(Vector(forbidding))

  /** A decision, then a subtree that exists only when it was answered "yes". */
  private val tree: Operation = Sequence(Vector[Operation](
    Decide(ask, actor, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(yes, "Yes"), DecisionOption.Button(no, "No")))),
    Branch((_, pending) => if (pending.answered.exists {
      case Answered(`ask`, DecisionAnswer.ChooseOneAnswer(`yes`), _) => true
      case _ => false
    }) Vector(Sequence(Vector.empty, Some(nested))) else Vector.empty)))

  private def answered(ref: DecisionOptionRef): Vector[Answered] =
    Vector(Answered(ask, DecisionAnswer.ChooseOneAnswer(ref), actor))

  private def violations(answers: Vector[Answered]): Vector[OathViolation] =
    ProcedureWalker.restrictionViolations(tree, powers, initialReady, actor,
      answers)

  test("a Restriction inside a subtree the answers have not opened is not checked") {
    assertEquals(violations(Vector.empty), Vector.empty[OathViolation])
    assertEquals(violations(answered(no)), Vector.empty[OathViolation])
  }

  test("once the answer opens the subtree its Restriction is checked") {
    assertEquals(violations(answered(yes)), Vector[OathViolation](violation))
  }

  test("the answers default to none, so every existing caller is unchanged") {
    assertEquals(ProcedureWalker.restrictionViolations(tree, powers,
      initialReady, actor), Vector.empty[OathViolation])
  }

  // ---- What a Transform inserts ----

  private val inserting: ContributingPower = new ContributingPower {
    def id: PowerId = PowerId("test.inserting")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.MusterActionEligibility -> Vector(Transform((_, children) =>
        children :+ Sequence(Vector.empty, Some(nested)))))
  }
  private val root: Operation =
    Sequence(Vector.empty, Some(PowerWindow.MusterActionEligibility))

  test("a subtree a Transform inserts is checked too, not only the declared tree") {
    val without = WalkerPowers(Vector(forbidding))
    assertEquals(ProcedureWalker.restrictionViolations(root, without,
      initialReady, actor), Vector.empty[OathViolation])
    val both = WalkerPowers(Vector(forbidding, inserting))
    assertEquals(ProcedureWalker.restrictionViolations(root, both,
      initialReady, actor), Vector[OathViolation](violation))
  }
}
