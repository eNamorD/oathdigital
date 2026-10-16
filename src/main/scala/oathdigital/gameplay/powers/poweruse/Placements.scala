package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.powerresolver.PowerUse
import oathdigital.model._

/** Placing favor or secrets on a card (catalog batch 8, slice 10): a cost
  * paid onto it on the payer's own turn, or a gift of favor or secrets onto
  * it. Off its payer's turn a payment settles to the bank and places
  * nothing (`PayCostSettlement`). */
private[powers] object Placements:
  /** The placer and the card, when `operation` places on a card. */
  def onto(ready: ReadyGame, operation: CoreOperation)
      : Option[(PlayerId, CardId)] = operation match
    case pay: PayCost if pay.cost.favor > 0 || pay.cost.secret > 0 =>
      (pay.placedAt, pay.player) match
        case (Location.OnCard(card), player)
            if player == ready.game.current.turn.activePlayer =>
          Some(player -> card)
        case _ => None
    case give: Give => (give.piece, give.to) match
      case (_: Piece.Favor | _: Piece.Secrets, Location.OnCard(card)) =>
        Some(give.giver -> card)
      case _ => None
    case _ => None

  /** The card `use`'s cost places on, when it places on its user's turn. */
  def byUse(ready: ReadyGame, use: PowerUse): Option[CardId] =
    use.card.filter(_ =>
      use.places && use.user == ready.game.current.turn.activePlayer)
