package oathdigital.gameplay.operations

import oathdigital.model._

/** A rule under which cards act as if locked for some players only: Ancient
  * Bloodline (catalog batch 8, slice 11). It judges each card an operation
  * moves or flips, with where the card stands and who moves it. */
trait AsIfLocked:
  /** Why `by` may not move or flip `card` from `at`, if it may not. */
  def refusal(ready: ReadyGame, card: CardId, at: Location,
      by: Option[PlayerId]): Option[OperationReason]

/** The cards with the lock icon cannot be moved or flipped while they show the
  * lock (global operation restrictions design, "Rules"). A discard, a `Take`,
  * a `Give` and a `Swap` all move the card, so each is refused; `Bury`
  * ignores locked and is never refused. A facedown card has no restrictions,
  * and a card in a hand or a deck is not in play.
  *
  * `asIf` are the rules that lock cards for some players only. A lock-icon
  * card's refusal comes first, since no consent lifts it.
  *
  * One restriction holds every lock (`OperationRestrictions.printed`), so an
  * operation is flattened once however many cards are locked.
  */
final case class LockedCards(cards: Set[CardId],
    asIf: Vector[AsIfLocked] = Vector.empty) extends OperationRestriction:
  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    val moved = LockedCards.touched(operation)
    moved.collectFirst {
      case (card, _, _) if cards.contains(card) &&
          LockedCards.showing(ready, card) =>
        OperationReason("locked", s"${card.value} is locked",
          OperationReasonKind.Impossible)
    }.orElse(moved.iterator.flatMap((card, at, by) =>
      asIf.iterator.flatMap(_.refusal(ready, card, at, by))).nextOption())

object LockedCards:
  /** The cards `operation` moves or flips, where each stands, and who
    * moves it. A flip names nobody: no rule flips a card at a site. */
  private def touched(operation: CoreOperation)
      : Vector[(CardId, Location, Option[PlayerId])] =
    Operation.flatten(operation).collect {
      case move @ Move(Piece.Card(moved), from, _, _, _) =>
        (moved, from.location, move.by)
      case Flip(flipped, at, _) => (flipped, at, None)
    }

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
