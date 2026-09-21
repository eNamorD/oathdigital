package oathdigital.gameplay.powers.action

import oathdigital.model._

/** Wayside Inn (card 47), ACTION: place 1 favor on this card, then gain
  * 2 Supply.
  */
case object WaysideInn extends PaidAction("denizen.wayside-inn",
    Cost(favor = 1)) {
  val Supply: Int = 2

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(GainSupply(player, Supply))
}
