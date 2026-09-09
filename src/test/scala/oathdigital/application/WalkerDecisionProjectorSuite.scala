package oathdigital.application

import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations.{Operation, Roll, Sequence}
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
}
