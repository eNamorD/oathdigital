package oathdigital.gameplay.walker

import oathdigital.model.{Draw, Flip, Operation, Orientation, Peek, Reroll,
  Reveal, Roll, Shuffle}

/** Operations that show a player something the chooser cannot see yet:
  * dice, a reroll, a draw, a shuffle, a peek, or a card turned faceup. The
  * legality search stops there, since pruning past one would leak the
  * outcome, and undo never takes one back (undo design, "Barriers").
  *
  * There are two questions here, and which one a caller asks follows from
  * what it holds, not from a choice it makes:
  *
  *   - [[hides]] asks about one node. The walk asks it, because the walk
  *     recurses itself: every child is reached on its own, so a composite
  *     is entered rather than judged by what lies under it. Stopping early
  *     on a subtree would cost pruning the search is entitled to.
  *   - [[hidesAnywhere]] asks about a node and everything beneath it. A
  *     caller holding a batch asks it, because
  *     `OperationApplication` runs an operation's [[Operation.flatten]]
  *     leaves, so a composite's children are never reached on their own.
  *
  * `hides` is confined to the walk for that reason: a caller outside it
  * holds a batch, and the node-level question is the wrong one there
  * (hidden outcome depth decision).
  */
object HiddenOutcomes:
  /** Whether `operation` itself shows something the chooser cannot see. */
  private[walker] def hides(operation: Operation): Boolean = operation match
    case _: Roll | _: Reroll | _: Shuffle | _: Draw | _: Peek | _: Reveal =>
      true
    case flip: Flip => flip.orientation == Orientation.FaceUp
    case _ => false

  /** Whether `operation` shows something the chooser cannot see anywhere
    * the pipeline will run it: the node itself, or any leaf beneath it. */
  def hidesAnywhere(operation: Operation): Boolean =
    hides(operation) || Operation.flatten(operation).exists(hides)
