package oathdigital.frontend

/** Where a card dragged over a partition zone lands, read off the pointer.
  *
  * A card the pointer has passed comes before the dragged one: a card on a
  * row above the pointer, or a card on the pointer's row whose midpoint it
  * has crossed. Crossing a midpoint is the same gesture leftward and
  * rightward, so a card moves past its neighbour either way. A pointer above
  * the first row or below the last reads across the nearest one, so hovering
  * a zone's heading still picks a place in its row.
  */
private[frontend] object PartitionDropTarget:
  /** A card's box in viewport coordinates, the way `getBoundingClientRect`
    * reports it.
    */
  final case class Box(left: Double, top: Double, right: Double,
      bottom: Double):
    def centerX: Double = (left + right) / 2

  /** How many of `cards` -- the zone's other cards, in reading order --
    * the dragged card lands after, with the pointer at (`x`, `y`).
    */
  def index(cards: Vector[Box], x: Double, y: Double): Int =
    if cards.isEmpty then 0
    else
      val rowY = math.max(cards.map(_.top).min,
        math.min(cards.map(_.bottom).max, y))
      cards.count(card => rowY > card.bottom ||
        (rowY >= card.top && x > card.centerX))
