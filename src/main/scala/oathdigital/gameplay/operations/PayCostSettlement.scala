package oathdigital.gameplay.operations

import oathdigital.gameplay.EffectiveSuit
import oathdigital.model._

/** Outside its own turn a player pays at once: favor goes straight to the
  * matching suit bank and secrets flip facedown, so nothing rests on the card.
  * The pipeline reads the active player from state and settles every
  * `PayCost` whose payer is somebody else, before validation and execution.
  *
  * The recorded operation is the requested one (`OperationRun.canonical`
  * clears `offTurn`), so replay runs the same function on the same state and
  * expands identically.
  *
  * A payment onto a faceup adviser of a Pledge to Discord holder settles to
  * the Discord bank, whatever bank the caller named: the caller has no state
  * to read.
  */
private[gameplay] object PayCostSettlement:
  def prepare(ready: ReadyGame,
      operation: CoreOperation): Either[OathViolation, CoreOperation] =
    operation match
      case pay: PayCost if pay.player != ready.game.current.turn.activePlayer =>
        val bank = pay.placedAt match
          case Location.OnCard(card) if EffectiveSuit.pledged(ready, card) =>
            Some(Suit.Discord)
          case _ => pay.matchingBank
        if pay.cost.favor > 0 && bank.isEmpty then
          Left(OathViolation.CoreOperationRejected("no-matching-bank",
            "favor paid outside the payer's turn needs the card's suit bank"))
        else Right(pay.copy(matchingBank = bank, offTurn = true))
      case other => Right(other)
