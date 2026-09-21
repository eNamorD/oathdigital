package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.RelicDraws
import oathdigital.model._

/** Dowsing Sticks (relic R09), ACTION: place 1 secret on this relic and burn
  * 2 secrets, then draw a relic and take it facedown. An empty relic deck
  * pays the cost and does nothing else.
  *
  * The draw sits in a `BuildOps` because whether the deck has a top card is a
  * fact of the state when the draw runs, not when the tree was built.
  */
case object DowsingSticks extends PaidAction("relic.dowsing-sticks",
    Cost(secret = 1, secretBurnt = 2)) {
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    Right(BuildOps((state, _) => Right(RelicDraws.takeTop(state, player))))
}
