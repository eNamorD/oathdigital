package oathdigital.gameplay.powers

import oathdigital.model._

/** A board's secrets burnt down to the number its player keeps, for Book
  * Burning and Ancient Binding. Only a faceup secret can be burnt, so the
  * facedown ones go first: they are turned faceup and burnt in the same
  * batch. The secrets kept are faceup when any faceup one remains.
  */
object SecretBurns:
  /** Every secret on `held`'s board but `kept`. A board holding `kept` or
    * fewer loses nothing. A batch runs its operations in order, so the burn
    * sees the secrets just turned. */
  def keeping(held: PlayerState, kept: Int): Vector[CoreOperation] =
    val burnt = held.board.faceUpSecrets + held.board.faceDownSecrets - kept
    val turned = math.min(held.board.faceDownSecrets, burnt)
    if burnt <= 0 then Vector.empty
    else Option.when[CoreOperation](turned > 0)(FlipSecrets(held.player,
      turned, SecretSide.FaceDown, SecretSide.FaceUp)).toVector :+
      Burn.secrets(burnt, PositionedLocation(Location.PlayArea(held.player)))
