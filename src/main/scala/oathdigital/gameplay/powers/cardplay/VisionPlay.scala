package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

/** Reads a faceup Vision play off the card-play hook
  * (`ActionCardPlayedFaceup`), for the cards that forbid or reward one. The
  * player is the one whose action runs, `ctx.activePlayer`.
  */
private[cardplay] object VisionPlay:

  /** The Vision the hook plays faceup, the Conspiracy included. */
  def played(ctx: PowerCtx): Option[VisionId] = ctx.operation match
    case CardPlayedFaceup(card: VisionId, _) => Some(card)
    case _ => None

  /** The Vision the hook plays faceup, while the play is still to be made:
    * the card is in the player's temporary hand or among their facedown
    * advisers. The hook stays in the tree after the card has moved, and a
    * restriction must not refuse a play that was legal when it was made
    * because the state has changed since.
    */
  def pending(ctx: PowerCtx): Option[VisionId] =
    played(ctx).filter(held(ctx.state, ctx.activePlayer, _))

  def forbidden(card: String): OathViolation =
    OathViolation.InvalidSearchPlacement(s"$card forbids playing a Vision faceup")

  private def held(ready: ReadyGame, player: PlayerId, card: VisionId): Boolean =
    val current = ready.game.current
    current.temporaryHands.getOrElse(player, Vector.empty).contains(card) ||
      current.players.find(_.player == player).exists(
        _.advisers.contains(VisionState(card, Orientation.FaceDown)))
