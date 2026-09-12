package oathdigital.application

import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations.{Decide, Operation, Roll, Sequence}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.walker.{WalkerActionRegistry, WalkerPowers}
import oathdigital.gameplay.{DiceKind, DiceSpec, OathViolation, ReadyGame}
import oathdigital.gameplay.OathState.Ready
import oathdigital.model._

/** Batch-1 Task 3, ruling R18 (P4), second consulting call site.
  *
  * `WalkerActionRegistry.rollDecisionId` is a typed `Left` for an action
  * whose entry declares none (Forge), and `WalkerActionRegistrySuite` pins
  * that value. This suite proves the OTHER half -- that
  * [[WalkerDecisionProjector]] honours the rejection rather than projecting
  * a decision the client would then send back as a `ResolveWalker` id.
  * Without a test here, replacing the accessor with a sentinel string, or
  * recovering from the `Left` with a `getOrElse`, would leave every existing
  * suite green.
  *
  * Reaching that branch needs a `Roll` park under an action declaring no
  * roll decision id, and no production tree can produce one: Forge is the
  * only such action and its tree has no `Roll` node at all. So the tree --
  * and ONLY the tree -- is substituted, through
  * `WalkerDecisionProjector.TreeSource`, the projection-side twin of the
  * `walkerTree` seam `OathRulesWalkerPowerSuite` already uses for the same
  * reason. The roll decision id under test is still read from the
  * PRODUCTION registry entries, so the substitution cannot also supply the
  * answer.
  */
class WalkerDecisionProjectorSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)

  /** A one-node tree that parks on a `Roll` at path `Vector("0")`. */
  private val rollTree: Operation =
    Sequence(Roll(PoolKey("test.roll"), DiceSpec(DiceKind.Defense)))

  /** A ready game parked on `rollTree`'s single node, owned by `action`. */
  private def parked(action: ActionRef): (ScopedProjectionContext, PlayerId) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val actor = base.game.current.turn.activePlayer
    val ready: ReadyGame = base.copy(game = base.game.copy(
      current = base.game.current.copy(
        turn = base.game.current.turn.copy(phase = Phase.Act),
        walkerAction = Some(action),
        walkerPending = Some(PendingTree(Vector("0"), Vector.empty, actor)))))
    (ScopedProjectionContext(ready, Some(actor)), actor)
  }

  /** Whatever the action, the tree is [[rollTree]] -- so the two calls
    * below differ in nothing but the `ActionRef` that parked.
    */
  private def projector = new WalkerDecisionProjector(catalog,
    new GamePresentationProjector(catalog), WalkerPowers.empty,
    (_, _, _, _) => Right(rollTree))

  test("a Roll park under an action declaring no roll decision id projects " +
      "nothing, rather than a projection naming a sentinel") {
    // Control: the same tree, the same park, the same projector -- under
    // Recover, whose entry DOES declare a roll decision id.
    val (recoverContext, _) = parked(ActionRef.Recover)
    val projected = projector.project(recoverContext).getOrElse(
      fail("Recover's Roll park must project a roll decision"))
    assertEquals(projected.action, ActionRef.Recover.key)
    assertEquals(projected.decisionId, RecoverProcedure.rollDecisionId)
    assertEquals(projected.kind, "roll")

    // Forge declares none, so there is nothing to project at all.
    val (forgeContext, _) = parked(ActionRef.Forge)
    assertEquals(projector.project(forgeContext), None)

    // ...and the reason is the accessor's typed rejection, not a rebuild
    // failure or an ownership mismatch: both were satisfied above.
    assertEquals(WalkerActionRegistry.rollDecisionId(ActionRef.Forge),
      Left(OathViolation.InvalidEventOrder(
        "walker action forge declares no roll decision id")))
  }

  /** The spec's presentation-failure rule (Task 4): an option whose identity
    * cannot be presented suppresses the ENTIRE decision projection rather
    * than emitting a half-described option a client would render as a blank
    * button and then submit.
    *
    * This needs a parked `Decide` naming a card that is nowhere in
    * authoritative state, and no production tree can build one -- both
    * Recover and Forge read their options live off `ready`, which is the
    * property that makes them safe. So the tree is substituted the same way
    * and for the same reason the roll test above substitutes one.
    */
  private def decideTree(options: Vector[DecisionOption],
      actor: PlayerId): Operation =
    Sequence(Decide("test.decide", actor, DecisionQuery.ChooseOne(options)))

  private def projectorFor(tree: Operation) =
    new WalkerDecisionProjector(catalog,
      new GamePresentationProjector(catalog), WalkerPowers.empty,
      (_, _, _, _) => Right(tree))

  test("a declared option whose id is absent from authoritative state " +
      "suppresses the whole decision projection") {
    val (context, actor) = parked(ActionRef.Recover)
    val ready = context.ready
    val present = ready.game.current.map.sites.values
      .flatMap(_.relics.map(_.id)).headOption.getOrElse(
        fail("the fixture board must hold at least one site relic"))
    val absent = RelicId("relic:not-on-this-board")
    assert(!CardIndex.from(ready.game).toOption.get.ids.contains(absent),
      "the fixture must not actually hold the absent relic")

    // Control: the same shape, the same park, with an option the board
    // really holds -- so the suppression below is the absent id and not the
    // substituted tree.
    val live = projectorFor(decideTree(Vector(
      DecisionOption.Relic(DecisionOptionRef.Relic(present))), actor))
      .project(context).flatMap(_.query).getOrElse(
        fail("a present relic option must project"))
    assertEquals(live.options.map(_.id), Vector(present.value))

    // One unpresentable option among two takes the whole projection with
    // it: not a one-option query, and not a blank second option.
    assertEquals(projectorFor(decideTree(Vector(
      DecisionOption.Relic(DecisionOptionRef.Relic(present)),
      DecisionOption.Relic(DecisionOptionRef.Relic(absent))), actor))
      .project(context), None)
  }
}
