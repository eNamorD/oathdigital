package oathdigital.application

import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerParked, WalkerPowers}
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.OathRules
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.{PowerStubs, WalkerStubs}
import oathdigital.testkit.Outcomes

/** The point of Task 4, in one suite: a power that transforms a parked
  * decision moves what the projector OFFERS and what the walker ACCEPTS in
  * the same stroke, because both read the same transformed `Decide.query`.
  *
  * Before this change the two were separate expressions -- the walker
  * consulted a per-action `validate` closure while the projector ran its own
  * candidate-discovery code -- and they agreed only because both happened to
  * call the same helper. A power could therefore change one without the
  * other, and nothing failed.
  *
  * Both the tree and the power catalog are injected, through the same two
  * seams `OathRulesWalkerPowerSuite` and [[WalkerDecisionProjectorSuite]]
  * already use, and BOTH sides are handed the identical declared tree. So
  * the only thing that can differ between the walk and the projection is the
  * power fold itself, which is what is under test. The production trees
  * declare their own options live off state and are covered in
  * [[WalkerDecisionProjectionSuite]].
  */
class WalkerDecisionQueryPowerSuite extends munit.FunSuite:
  private def presentation = new GamePresentationProjector(catalog)

  private val window: PowerWindow = PowerWindow.RecoverModifierSelection

  private val continueOption = WalkerStubs.continueOption
  private val stopOption = WalkerStubs.stopOption
  private val extraOption = DecisionOptionRef.Button("extra")

  /** One windowed composite holding one `Decide`: the transform below sees
    * the composite's children, which is exactly the vector the live walk
    * folds and the projector re-folds on its way to the park.
    */
  private def tree(actor: PlayerId): Operation =
    Sequence(WalkerStubs.WindowedNode(window, Vector(Decide(
      decisionId = RecoverProcedure.choiceDecisionId,
      owner = actor,
      query = DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(continueOption, "Continue"),
        DecisionOption.Button(stopOption, "Stop")),
        heading = Some("Recover"))))))

  /** A power that rewrites the option list of whatever choose-one decision
    * it is handed, and nothing else. Two instances below add an option and
    * remove one; both are `Automatic`, so no `StartWalker` modifier is
    * needed to select them.
    */
  private def rewriting(id: String)(
      change: DecisionQuery.ChooseOne => DecisionQuery.ChooseOne) =
    WalkerPowers(Vector(PowerStubs.TransformStub(PowerId(id),
      window, (_, ops) => ops.map {
        case decide: Decide => decide.query match {
          case query: DecisionQuery.ChooseOne =>
            decide.copy(query = change(query))
          case _ => decide
        }
        case other => other
      })))

  private val adding = rewriting("test.adds-an-option")(query =>
    query.copy(options = query.options :+
      DecisionOption.Button(extraOption, "Extra")))

  private val removing = rewriting("test.removes-an-option")(query =>
    query.copy(options = query.options.filterNot(_.ref == continueOption)))

  /** Task 5b: the same transform seam, applied to the decision's panel copy
    * rather than to its options. A heading is authored by the action and
    * carried on the query, so a power rewrites it exactly the way it
    * rewrites an option -- one edit, and both the prompt and the option set
    * travel together.
    */
  private val retitling = rewriting("test.retitles-the-decision")(query =>
    query.copy(options = query.options.filterNot(_.ref == continueOption),
      heading = Some("Abandon the attempt")))

  private def rules(actor: PlayerId, powers: WalkerPowers): OathRules =
    new OathRules(catalog, walkerPowerCatalog = powers,
      walkerTree = (_, _, _, _, _, _) => Right(tree(actor)))

  private def projector(actor: PlayerId, powers: WalkerPowers) =
    new WalkerDecisionProjector(catalog, presentation, powers,
      (_, _, _, _, _) => Right(tree(actor)))

  /** Starts the walker under `powers` and returns the parked state. */
  private def parked(ready: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): oathdigital.model.OathState =
    val started = rules(actor, powers).startWalker(Ready(ready),
      ActionRef.Recover, actor) match
      case Right(transition) => transition
      case other => fail(s"expected the walker to park, got $other")
    assert(started.events.last.isInstanceOf[WalkerParked])
    started.state

  /** The option keys the projector offers at the park. */
  private def offered(state: oathdigital.model.OathState, actor: PlayerId,
      powers: WalkerPowers): Vector[String] =
    val ready = Outcomes.ready(state)
    projector(actor, powers)
      .project(ScopedProjectionContext(ready, Some(actor)))
      .flatMap(_.query).map(_.offeredOptions.map(_.id)).getOrElse(
        fail("the parked actor must be offered the decision"))

  /** The heading the projector offers at the park, if any. */
  private def titled(state: oathdigital.model.OathState, actor: PlayerId,
      powers: WalkerPowers): Option[String] =
    val ready = Outcomes.ready(state)
    projector(actor, powers)
      .project(ScopedProjectionContext(ready, Some(actor)))
      .flatMap(_.query).map(_.heading).getOrElse(
        fail("the parked actor must be offered the decision"))

  private def answering(state: oathdigital.model.OathState,
      actor: PlayerId, powers: WalkerPowers,
      selected: DecisionOptionRef) =
    rules(actor, powers).resolveWalker(state, actor,
      RecoverProcedure.choiceDecisionId,
      DecisionAnswer.ChooseOneAnswer(selected))

  // Each test starts from p1's Act on the quiet table: the injected tree
  // replaces Recover's own eligibility gates, so the command never consults
  // them.

  test("with no power the projected options and the accepted answers are " +
      "the two the tree declares"):
    val ready = Table.start.ready
    val state = parked(ready, p1, WalkerPowers.empty)
    assertEquals(offered(state, p1, WalkerPowers.empty),
      Vector("continue", "stop"))
    assert(answering(state, p1, WalkerPowers.empty, continueOption).isRight)
    assert(answering(state, p1, WalkerPowers.empty, extraOption).isLeft)

  test("a power that adds an option adds it to the projection and to what " +
      "the walker accepts"):
    val ready = Table.start.ready
    val state = parked(ready, p1, adding)
    assertEquals(offered(state, p1, adding),
      Vector("continue", "stop", "extra"))
    assert(answering(state, p1, adding, extraOption).isRight,
      "the walker must accept the option the power added")

  test("a power that removes an option removes it from the projection and " +
      "from what the walker accepts"):
    val ready = Table.start.ready
    val state = parked(ready, p1, removing)
    assertEquals(offered(state, p1, removing), Vector("stop"))
    assert(answering(state, p1, removing, continueOption).isLeft,
      "the walker must reject the option the power removed")
    assert(answering(state, p1, removing, stopOption).isRight,
      "the surviving option must still be answerable")

  test("a power that rewrites a decision's heading changes what is " +
      "projected, in the same edit that moves its options"):
    val ready = Table.start.ready
    assertEquals(titled(parked(ready, p1, WalkerPowers.empty), p1,
      WalkerPowers.empty), Some("Recover"),
      "the tree's own declared heading is what an unpowered park projects")

    val state = parked(ready, p1, retitling)
    assertEquals(titled(state, p1, retitling),
      Some("Abandon the attempt"))
    // The one edit moved both: the option the power dropped is gone from
    // the projection and from what the walker accepts, and the prompt above
    // them is the power's. Copy still decides nothing -- the surviving
    // option is answerable under either title.
    assertEquals(offered(state, p1, retitling), Vector("stop"))
    assert(answering(state, p1, retitling, continueOption).isLeft)
    assert(answering(state, p1, retitling, stopOption).isRight)

  /** Task 5: a power that reowns the parked `Decide` moves who may answer it
    * (the walker), who is projected it (`project`) and who the public
    * waiting projection names (`waiting`) -- all in the one edit, because
    * all three read the same transformed node's `owner`.
    */
  test("a power that reowns a parked Decide moves who may answer it, who is " +
      "projected it, and who the waiting projection names"):
    val ready = Table.start.ready
    val powers = WalkerPowers(Vector(PowerStubs.TransformStub(
      PowerId("test.reowns"), window, (_, ops) => ops.map {
        case decide: Decide => decide.copy(owner = p2)
        case op => op
      })))
    val state = parked(ready, p1, powers)

    assert(rules(p1, powers).resolveWalker(state, p2,
      RecoverProcedure.choiceDecisionId,
      DecisionAnswer.ChooseOneAnswer(continueOption)).isRight,
      "the new owner must be accepted")
    assertEquals(rules(p1, powers).resolveWalker(state, p1,
      RecoverProcedure.choiceDecisionId,
      DecisionAnswer.ChooseOneAnswer(continueOption)),
      Left(oathdigital.model.OathViolation.WrongPlayer(p2, p1)))

    val readyState = Outcomes.ready(state)
    assert(projector(p1, powers).project(
      ScopedProjectionContext(readyState, Some(p2))).nonEmpty,
      "the new owner must be projected the decision")
    assertEquals(projector(p1, powers).project(
      ScopedProjectionContext(readyState, Some(p1))), None,
      "the former owner must no longer be projected the decision")
    assertEquals(projector(p1, powers).waiting(
      ScopedProjectionContext(readyState, Some(p1))).map(_.playerId),
      Some(p2.value))
