package oathdigital.gameplay.powers.action

import oathdigital.model._

/** One secret leaving a player's board, faceup first, for Sleight of Hand
  * and Witch's Bargain.
  *
  * A one-secret move from a board holding both orientations is ambiguous
  * (`AmbiguousSecretOrientation`), so the batch flips the board's facedown
  * secrets up first, moves the secret, and flips the same number back down.
  * The net effect is exactly "one faceup secret moves". A board of one
  * orientation moves its secret as it lies.
  */
private[action] object SecretMoves:
  /** `move`, of one secret off `holder`'s board, faceup first. */
  def faceupFirst(holder: PlayerState, move: CoreOperation)
      : Vector[CoreOperation] =
    val faceDown = holder.board.faceDownSecrets
    if holder.board.faceUpSecrets > 0 && faceDown > 0 then Vector(
      FlipSecrets(holder.player, faceDown, SecretSide.FaceDown,
        SecretSide.FaceUp),
      move,
      FlipSecrets(holder.player, faceDown, SecretSide.FaceUp,
        SecretSide.FaceDown))
    else Vector(move)
