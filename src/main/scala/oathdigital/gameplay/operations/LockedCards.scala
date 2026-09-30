package oathdigital.gameplay.operations

import oathdigital.model._

/** The cards with the lock icon cannot be moved or flipped while they show the
  * lock (global operation restrictions design, "Rules"). A discard, a `Take`,
  * a `Give` and a `Swap` all move the card, so each is refused; `Bury`
  * ignores locked and is never refused. A facedown card has no restrictions,
  * and a card in a hand or a deck is not in play.
  *
  * One restriction holds every lock-icon card (`OperationRestrictions.printed`),
  * so an operation is flattened once however many cards are locked.
  */
final case class LockedCards(cards: Set[CardId]) extends OperationRestriction:
  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    touched(operation).find(card =>
      cards.contains(card) && LockedCards.showing(ready, card)).map(card =>
      OperationReason("locked", s"${card.value} is locked",
        OperationReasonKind.Impossible))

  /** The cards `operation` moves or flips. */
  private def touched(operation: CoreOperation): Vector[CardId] =
    Operation.flatten(operation).collect {
      case Move(Piece.Card(moved), _, _, _) => moved
      case Flip(flipped, _, _) => flipped
    }

object LockedCards:
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
