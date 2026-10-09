package oathdigital.gameplay.actions

import oathdigital.model._

/** Pure Search source and cost rules shared by the walker and projections.
  * Which piles a Search may pick is the source decision's business: a power
  * offers another region's discard pile there (catalog batch 6, slice 3),
  * and these rules price and draw any pile alike.
  */
object SearchRules:
  def cost(ready: ReadyGame, source: SearchSource): Int = source match
    case SearchSource.WorldDeck => ready.game.current.tracks.visionsDrawn match
      case 0 => 2
      case 1 | 2 => 3
      case _ => 4
    case _: SearchSource.RegionalDiscard => 2

  /** How many cards a Search draws before any power changes it. */
  val DrawSize: Int = 3

  /** The cards a Search of `source` draws, in the order drawn: from the
    * top, or from the bottom when `position` says so. World decks use
    * head-as-top; discard piles use last-as-top. A world deck draw stops
    * after its first Vision, counted from the end it draws. `extra` is the
    * number of cards a power adds to the printed draw, or, below zero,
    * takes from it.
    */
  def draw(ready: ReadyGame, source: SearchSource, extra: Int = 0,
      position: StackPosition = StackPosition.Top): Vector[WorldCardId] =
    val topFirst = source match
      case SearchSource.WorldDeck => ready.game.current.commonCards.worldDeck
      case SearchSource.RegionalDiscard(region) =>
        ready.game.current.commonCards.discard(region).reverse
    val ordered =
      if position == StackPosition.Bottom then topFirst.reverse else topFirst
    val drawn = ordered.take(DrawSize + extra)
    if source == SearchSource.WorldDeck then
      drawn.takeThrough(_.isInstanceOf[VisionId])
    else drawn

  extension [A](values: Vector[A])
    private def takeThrough(stop: A => Boolean): Vector[A] =
      val index = values.indexWhere(stop)
      if index < 0 then values else values.take(index + 1)
