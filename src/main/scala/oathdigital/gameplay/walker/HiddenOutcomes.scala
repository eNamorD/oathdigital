package oathdigital.gameplay.walker

import oathdigital.model.{Draw, Flip, Operation, Orientation, Peek, Reroll,
  Reveal, Roll, Shuffle}

/** Operations that show a player something the chooser cannot see yet:
  * dice, a reroll, a draw, a shuffle, a peek, or a card turned faceup. The
  * legality search stops there, since pruning past one would leak the
  * outcome, and undo never takes one back (undo design, "Barriers"). */
object HiddenOutcomes:
  def hides(operation: Operation): Boolean = operation match
    case _: Roll | _: Reroll | _: Shuffle | _: Draw | _: Peek | _: Reveal =>
      true
    case flip: Flip => flip.orientation == Orientation.FaceUp
    case _ => false
