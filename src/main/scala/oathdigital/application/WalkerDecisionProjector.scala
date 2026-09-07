package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.ReadyGame
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations.Operation
import oathdigital.gameplay.walker.ProcedureWalker
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
  */
private[application] final class WalkerDecisionProjector(
    catalog: ExecutableCatalog) {

  def project(context: ScopedProjectionContext)
      : Option[WalkerDecisionProjection] =
    for {
      pending <- context.current.walkerPending
      action <- context.current.walkerAction
      if context.viewer.contains(pending.actor)
      tree <- rebuild(context.ready, action, pending.actor).toOption
      projection <- parked(action, tree, context.ready, pending)
    } yield projection

  private def rebuild(ready: ReadyGame, action: ActionRef, actor: PlayerId) =
    action match {
      case ActionRef.Recover => RecoverProcedure.rebuild(catalog, ready, actor)
    }

  private def parked(action: ActionRef, tree: Operation, ready: ReadyGame,
      pending: PendingTree): Option[WalkerDecisionProjection] =
    ProcedureWalker.parkedRoll(ready, tree, pending) match {
      case Some((pool, count)) => Some(WalkerDecisionProjection(action.key,
        RecoverProcedure.rollDecisionId, "roll", pool = Some(pool.value),
        count = Some(count)))
      case None => ProcedureWalker.parkedDecide(ready, tree, pending).map(
        decide => WalkerDecisionProjection(action.key, decide.decisionId,
          "decide"))
    }
}
