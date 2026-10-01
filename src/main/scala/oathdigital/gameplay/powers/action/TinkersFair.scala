package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.RelicDraws
import oathdigital.model._

/** Tinker's Fair (card 13, site-only), ACTION: place 3 favor on this card,
  * then draw a relic and take it facedown, as Dowsing Sticks does. An empty
  * relic deck pays the cost and does nothing else.
  */
case object TinkersFair extends PaidAction("denizen.tinker-s-fair",
    Cost(favor = 3)):
  override def noteKeys: Vector[NoteKey] =
    Vector(RelicDraws.drew, RelicDraws.emptyDeck)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    Right(Sequence(RelicDraws.drawSteps(id, player, source)))
