package oathdigital.gameplay.powers.action

import oathdigital.model._

/** Elders (card 26), ACTION: place 2 favor on this card, then gain 1 secret
  * from the shared bank, which holds an unlimited supply.
  */
case object Elders extends PaidAction("denizen.elders", Cost(favor = 2)) {
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Gain.Secrets(player, 1))
}
