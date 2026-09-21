package oathdigital.gameplay.operations

import oathdigital.model._

/** Outside its own turn a player pays at once: favor goes straight to the
  * matching suit bank and secrets flip facedown, so nothing rests on the card.
  * The pipeline reads the active player from state and settles every
  * `PayCost` whose payer is somebody else, before validation and execution.
  *
  * The recorded operation is the requested one (`OperationRun.canonical`
  * clears `offTurn`), so replay runs the same function on the same state and
  * expands identically.
  */
private[gameplay] object PayCostSettlement {
  def prepare(ready: ReadyGame,
      operation: CoreOperation): Either[OathViolation, CoreOperation] =
    operation match {
      case pay: PayCost if pay.player != ready.game.current.turn.activePlayer =>
        if (pay.cost.favor > 0 && pay.matchingBank.isEmpty)
          Left(OathViolation.CoreOperationRejected("no-matching-bank",
            "favor paid outside the payer's turn needs the card's suit bank"))
        else Right(pay.copy(offTurn = true))
      case other => Right(other)
    }
}
