package oathdigital.gameplay.powers

import oathdigital.model._

/** A board's secrets burnt down to the number its player keeps, for Book
  * Burning and Ancient Binding. A burn takes faceup secrets unless it names
  * the facedown side, so the facedown ones are burnt by name, first: the
  * secrets kept are faceup when any faceup one remains.
  */
object SecretBurns:
  /** Every secret on `held`'s board but `kept`. A board holding `kept` or
    * fewer loses nothing. */
  def keeping(held: PlayerState, kept: Int): Vector[CoreOperation] =
    val burnt = held.board.faceUpSecrets + held.board.faceDownSecrets - kept
    val faceDown = math.min(held.board.faceDownSecrets, burnt)
    def side(value: SecretSide) = PositionedLocation(
      Location.PlayArea(held.player), side = Some(value))
    if burnt <= 0 then Vector.empty
    else Vector(
      Option.when(faceDown > 0)(Burn.secrets(faceDown,
        side(SecretSide.FaceDown))),
      Option.when(burnt > faceDown)(Burn.secrets(burnt - faceDown,
        side(SecretSide.FaceUp)))).flatten
