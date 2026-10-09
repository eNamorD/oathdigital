package oathdigital.gameplay.powerresolver

import oathdigital.model.{AdviserState, CardId, CoreOperation, DenizenState,
  Note, Orientation, PlayerId, ReadyGame, VisionState}

/** An adviser that arrived during one walker leaf: `card` is new among
  * `player`'s advisers, or turned faceup there. */
final case class AdviserArrival(player: PlayerId, card: CardId)

/** What an arrival rule reads (catalog batch 5, "The arrival pass"): the
  * active player, the states before and after the leaf, and the advisers
  * that arrived in it, in seat and then held order. `after` already holds
  * the follow-ups of the rules asked before. */
final case class AdviserArrivals(actor: PlayerId, before: ReadyGame,
    after: ReadyGame, arrivals: Vector[AdviserArrival])

object AdviserArrivals:
  /** The advisers that arrived between `before` and `after`. */
  def between(actor: PlayerId, before: ReadyGame, after: ReadyGame)
      : AdviserArrivals =
    val held = before.game.current.players
      .map(player => player.player -> player.advisers).toMap
    AdviserArrivals(actor, before, after, for
      player <- after.game.current.players
      adviser <- player.advisers
      earlier = held.getOrElse(player.player, Vector.empty)
        .find(_.id == adviser.id)
      if earlier.forall(was => !faceUp(was) && faceUp(adviser))
    yield AdviserArrival(player.player, adviser.id))

  private def faceUp(adviser: AdviserState): Boolean = adviser match
    case DenizenState(_, orientation, _) => orientation == Orientation.FaceUp
    case VisionState(_, orientation) => orientation == Orientation.FaceUp

/** An arrival rule's answer: the operations to run after the leaf, as one
  * required batch, and the line journaled after them. */
final case class ArrivalFollowUp(operations: Vector[CoreOperation],
    note: Note)
