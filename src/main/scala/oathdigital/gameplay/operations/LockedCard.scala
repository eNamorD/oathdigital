package oathdigital.gameplay.operations

import oathdigital.model._

/** A card with the lock icon cannot be moved or flipped while it shows the
  * lock (global operation restrictions design, "Rules"). A discard, a `Take`,
  * a `Give` and a `Swap` all move the card, so each is refused; `Bury`
  * ignores locked and is never refused. A facedown card has no restrictions,
  * and a card in a hand or a deck is not in play.
  *
  * One instance exists per lock-icon card (`OperationRestrictions.printed`),
  * so a refusal names its card.
  */
final case class LockedCard(card: CardId) extends OperationRestriction:
  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    Option.when(touches(operation) && LockedCard.showing(ready, card))(
      OperationReason("locked", s"${card.value} is locked",
        OperationReasonKind.Impossible))

  private def touches(operation: CoreOperation): Boolean =
    Operation.flatten(operation).exists {
      case Move(Piece.Card(moved), _, _, _) => moved == card
      case Flip(flipped, _, _) => flipped == card
      case _ => false
    }

object LockedCard:
  /** `card` shows its lock now: a faceup adviser in a play area, a denizen at
    * a site (always faceup), or an edifice at a site on its intact side. */
  def showing(ready: ReadyGame, card: CardId): Boolean =
    val current = ready.game.current
    current.players.exists(_.advisers.exists {
      case held: DenizenState =>
        held.id == card && held.orientation == Orientation.FaceUp
      case _ => false
    }) || current.map.sites.values.exists(_.denizens.exists {
      case edifice: EdificeState =>
        edifice.id == card && edifice.side == EdificeSide.Intact
      case other => other.id == card
    })
