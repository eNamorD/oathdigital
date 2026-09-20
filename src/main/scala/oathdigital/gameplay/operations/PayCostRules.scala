package oathdigital.gameplay.operations

import oathdigital.model._

/** The placement rule for costs: a cost is placed onto a card only when the
  * card is empty. Battle plans opt out with `PayCost.intoOccupied`. Burnt
  * portions never rest on the card, so they are exempt.
  *
  * Muster and Trade check the same fact earlier (`MusterSource`, with its own
  * rejection) so their projection can drop the option. This is the enforcement
  * point for every other `PayCost`.
  */
private[gameplay] object PayCostRules {
  def isEmpty(ready: ReadyGame, card: CardId): Boolean = {
    val at = Location.OnCard(card)
    def held(piece: Piece): Int = OperationStateAdapter.quantity(ready, piece, at)
      .toOption.collect { case AvailableQuantity.Finite(value) => value }
      .getOrElse(0)
    held(Piece.Favor(1)) == 0 && held(Piece.Secrets(1)) == 0
  }

  def violations(ready: ReadyGame,
      operation: CoreOperation): Vector[OperationError] = operation match {
    case pay: PayCost if !pay.intoOccupied && !pay.offTurn && pay.cost.favor + pay.cost.secret > 0 =>
      pay.placedAt match {
        case Location.OnCard(card) if !isEmpty(ready, card) =>
          Vector(OperationError.CardOccupied(card))
        case _ => Vector.empty
      }
    case _ => Vector.empty
  }
}
