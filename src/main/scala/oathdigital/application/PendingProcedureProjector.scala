package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._
import oathdigital.protocol.projection._

private[application] final class PendingProcedureProjector(
    catalog: ExecutableCatalog,
    presentation: GamePresentationProjector,
    walkerDecisions: WalkerDecisionProjector
) {
  def project(context: ScopedProjectionContext): PendingProjection = {
    val walkerDecision = walkerDecisions.project(context)
    val walkerWaiting = walkerDecisions.waiting(context)
    PendingProjection(phase(context, walkerDecision), None, walkerDecision,
      walkerWaiting)
  }

  private def phase(context: ScopedProjectionContext,
      walkerDecision: Option[WalkerDecisionProjection]): String =
    if (context.current.result.nonEmpty) "game-over"
    else if (context.current.walkerPending.nonEmpty)
      // The label is keyed off the parked procedure's own wire key (Task 8)
      // instead of a hardcoded "recover-*" literal, so a second procedure
      // parked on the walker reports its own phase rather than borrowing
      // Recover's. `walkerProcedure` is always populated alongside
      // `walkerPending` (both are written by `WalkerParked` and cleared
      // together by `WalkerCompleted`), so the `None` arm below is
      // unreachable in practice; it exists only so this stays total.
      context.current.walkerProcedure.fold("walker-waiting") { procedure =>
        walkerDecision match {
          case Some(w) if w.kind == "roll" => s"${procedure.key}-walker-roll"
          case Some(_) => s"${procedure.key}-walker-decision"
          case None => s"${procedure.key}-walker-waiting"
        }
      }
    else context.current.turn.phase match {
      case Phase.Wake => "wake"
      case Phase.Act => "act-action-selection"
      case Phase.Rest => "rest"
      case Phase.RoundEnd => "round-end"
      case Phase.WarExhaustion => "war-exhaustion"
    }
}
