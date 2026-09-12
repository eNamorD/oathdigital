package oathdigital.application

import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations.{Decide, Operation, Sequence}
import oathdigital.gameplay.powerresolver.PowerWindow
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.walker.{WalkerParked, WalkerPowers}
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.{OathRules, ProcedureWalkerSuite, ReadyGame}
import oathdigital.model._

/** The point of Task 4, in one suite: a power that transforms a parked
  * decision moves what the projector OFFERS and what the walker ACCEPTS in
  * the same stroke, because both read the same transformed `Decide.query`.
  *
  * Before this change the two were separate expressions -- the walker
  * consulted a per-action `validate` closure while the projector ran its own
  * candidate-discovery code -- and they agreed only because both happened to
  * call the same helper. A power could therefore change one without the
  * other, and nothing failed. The last test below is the negative control
  * that proves this suite would catch exactly that: it folds the tree for
  * walking but NOT for projection and asserts the two disagree, which is the
  * bug this contract exists to make impossible.
  *
  * Both the tree and the power catalog are injected, through the same two
  * seams `OathRulesWalkerPowerSuite` and [[WalkerDecisionProjectorSuite]]
  * already use, and BOTH sides are handed the identical declared tree. So
  * the only thing that can differ between the walk and the projection is the
  * power fold itself, which is what is under test. The production trees
  * declare their own options live off state and are covered in
  * [[WalkerDecisionProjectionSuite]].
  */
class WalkerDecisionQueryPowerSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private def presentation = new GamePresentationProjector(catalog)

  private val window: PowerWindow = PowerWindow.RecoverModifierSelection

  private val continueOption = ProcedureWalkerSuite.continueOption
  private val stopOption = ProcedureWalkerSuite.stopOption
  private val extraOption = DecisionOptionRef.Button("extra")

  /** A ready game in the Act phase; the injected tree replaces Recover's own
    * eligibility gates, so the command never consults them.
    */
  private def actable: (ReadyGame, PlayerId) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val ready = base.copy(game = base.game.copy(current =
      base.game.current.copy(turn = base.game.current.turn.copy(
        phase = Phase.Act))))
    (ready, ready.game.current.turn.activePlayer)
  }

  /** One windowed composite holding one `Decide`: the transform below sees
    * the composite's children, which is exactly the vector the live walk
    * folds and the projector re-folds on its way to the park.
    */
  private def tree(actor: PlayerId): Operation =
    Sequence(ProcedureWalkerSuite.WindowedNode(window, Vector(Decide(
      decisionId = RecoverProcedure.choiceDecisionId,
      owner = actor,
      query = DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(continueOption, "Continue"),
        DecisionOption.Button(stopOption, "Stop")))))))

  /** A power that rewrites the option list of whatever choose-one decision
    * it is handed, and nothing else. Two instances below add an option and
    * remove one; both are `Automatic`, so no `StartWalker` modifier is
    * needed to select them.
    */
  private def rewriting(id: String)(
      change: Vector[DecisionOption] => Vector[DecisionOption]) =
    WalkerPowers(Vector(ProcedureWalkerSuite.TestTransformPower(PowerId(id),
      window, (_, ops) => ops.map {
        case decide: Decide => decide.query match {
          case DecisionQuery.ChooseOne(options) =>
            decide.copy(query = DecisionQuery.ChooseOne(change(options)))
          case _ => decide
        }
        case other => other
      })))

  private val adding = rewriting("test.adds-an-option")(
    _ :+ DecisionOption.Button(extraOption, "Extra"))

  private val removing = rewriting("test.removes-an-option")(
    _.filterNot(_.ref == continueOption))

  private def rules(actor: PlayerId, powers: WalkerPowers): OathRules =
    new OathRules(catalog, walkerPowerCatalog = powers,
      walkerTree = (_, _, _, _, _) => Right(tree(actor)))

  private def projector(actor: PlayerId, powers: WalkerPowers) =
    new WalkerDecisionProjector(catalog, presentation, powers,
      (_, _, _, _) => Right(tree(actor)))

  /** Starts the walker under `powers` and returns the parked state. */
  private def parked(ready: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): oathdigital.gameplay.OathState = {
    val started = rules(actor, powers).startWalker(Ready(ready),
      ActionRef.Recover, actor) match {
      case Right(transition) => transition
      case other => fail(s"expected the walker to park, got $other")
    }
    assert(started.events.last.isInstanceOf[WalkerParked])
    started.state
  }

  /** The option keys the projector offers at the park. */
  private def offered(state: oathdigital.gameplay.OathState, actor: PlayerId,
      powers: WalkerPowers): Vector[String] = {
    val Ready(ready) = state: @unchecked
    projector(actor, powers)
      .project(ScopedProjectionContext(ready, Some(actor)))
      .flatMap(_.query).map(_.options.map(_.id)).getOrElse(
        fail("the parked actor must be offered the decision"))
  }

  private def answering(state: oathdigital.gameplay.OathState,
      actor: PlayerId, powers: WalkerPowers,
      selected: DecisionOptionRef) =
    rules(actor, powers).resolveWalker(state, actor,
      Answered(RecoverProcedure.choiceDecisionId,
        DecisionAnswer.ChooseOneAnswer(selected)))

  test("with no power the projected options and the accepted answers are " +
      "the two the tree declares") {
    val (ready, actor) = actable
    val state = parked(ready, actor, WalkerPowers.empty)
    assertEquals(offered(state, actor, WalkerPowers.empty),
      Vector("continue", "stop"))
    assert(answering(state, actor, WalkerPowers.empty, continueOption).isRight)
    assert(answering(state, actor, WalkerPowers.empty, extraOption).isLeft)
  }

  test("a power that adds an option adds it to the projection and to what " +
      "the walker accepts") {
    val (ready, actor) = actable
    val state = parked(ready, actor, adding)
    assertEquals(offered(state, actor, adding),
      Vector("continue", "stop", "extra"))
    assert(answering(state, actor, adding, extraOption).isRight,
      "the walker must accept the option the power added")
  }

  test("a power that removes an option removes it from the projection and " +
      "from what the walker accepts") {
    val (ready, actor) = actable
    val state = parked(ready, actor, removing)
    assertEquals(offered(state, actor, removing), Vector("stop"))
    assert(answering(state, actor, removing, continueOption).isLeft,
      "the walker must reject the option the power removed")
    assert(answering(state, actor, removing, stopOption).isRight,
      "the surviving option must still be answerable")
  }

  test("a transform applied to the walk but not to the projection makes the " +
      "two disagree, which is the failure this contract removes") {
    val (ready, actor) = actable

    // The walk folds `adding`, so the walker accepts the added option...
    val state = parked(ready, actor, adding)
    assert(answering(state, actor, adding, extraOption).isRight)

    // ...but a projector folding a DIFFERENT power set describes a query
    // the walker is not actually parked on. Both directions are visible:
    // an unfolded projection hides an option the walker accepts, and a
    // projection folding `removing` offers one it rejects.
    assert(!offered(state, actor, WalkerPowers.empty).contains("extra"))
    assertEquals(offered(state, actor, removing), Vector("stop"))
    assert(answering(state, actor, adding, continueOption).isRight,
      "the walker accepts an option the mismatched projection dropped")
  }
}
