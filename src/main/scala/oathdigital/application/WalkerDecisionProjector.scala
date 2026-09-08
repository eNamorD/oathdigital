package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.ReadyGame
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations.Operation
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers}
import oathdigital.model.{ActionRef, PendingTree, PlayerId}

/** Projects a parked generic-walker position (`CurrentGameState.walkerPending`
  * + `walkerAction`, Task 6) into the small owner-private
  * [[WalkerDecisionProjection]]. Recover is the only action registered on
  * the walker in this slice, so the tree rebuild below is Recover-specific
  * by design — mirroring `OathRules.buildWalker`'s own exhaustive dispatch
  * on [[ActionRef]], not a structural coincidence.
  *
  * Reuses [[ProcedureWalker.parkedRoll]]/[[ProcedureWalker.parkedDecide]] —
  * the same reorder-safe, decisionId-keyed introspection `OathRules`
  * dispatches on to pick a live command's `OathContinue` — rather than
  * inspecting `PendingTree.at` directly, for the identical reason: a
  * structural path match would silently point at the wrong node if
  * `RecoverProcedure`'s tree shape ever changes.
  *
  * `walkerPowerCatalog` (Task 5) is the same full catalog `OathRules`
  * offers a live command; `context.current.walkerModifiers` -- the durable
  * fact persisted from the `StartWalker` that parked here -- narrows it down
  * through `WalkerPowers.selected`, the SAME projection `OathRules
  * .walkerPowers` applies at command time. Without this, a power that
  * inserts operations at a shared window (Catacombs at
  * `RecoverActionEligibility`) would make this projector re-fold the tree
  * differently from the walker that actually parked it, misreporting the
  * parked node.
  */
private[application] final class WalkerDecisionProjector(
    catalog: ExecutableCatalog, walkerPowerCatalog: WalkerPowers) {

  def this(catalog: ExecutableCatalog) =
    this(catalog, WalkerPowerCatalog.default(catalog))

  def project(context: ScopedProjectionContext)
      : Option[WalkerDecisionProjection] =
    for {
      pending <- context.current.walkerPending
      action <- context.current.walkerAction
      if context.viewer.contains(pending.actor)
      tree <- rebuild(context.ready, action, pending.actor).toOption
      powers = WalkerPowers.selected(walkerPowerCatalog,
        context.current.walkerModifiers)
      projection <- parked(action, tree, context.ready, pending, powers)
    } yield projection

  private def rebuild(ready: ReadyGame, action: ActionRef, actor: PlayerId) =
    action match {
      case ActionRef.Recover => RecoverProcedure.rebuild(catalog, ready, actor)
    }

  private def parked(action: ActionRef, tree: Operation, ready: ReadyGame,
      pending: PendingTree, powers: WalkerPowers)
      : Option[WalkerDecisionProjection] =
    ProcedureWalker.parkedRoll(ready, tree, pending, powers) match {
      case Some((pool, count)) => Some(WalkerDecisionProjection(action.key,
        RecoverProcedure.rollDecisionId, "roll", pool = Some(pool.value),
        count = Some(count)))
      case None => ProcedureWalker.parkedDecide(ready, tree, pending,
          powers).map(
        decide => WalkerDecisionProjection(action.key, decide.decisionId,
          "decide"))
    }
}
