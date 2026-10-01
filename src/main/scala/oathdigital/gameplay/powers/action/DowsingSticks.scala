package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.RelicDraws
import oathdigital.model._

/** Dowsing Sticks (relic R09), ACTION: place 1 secret on this relic and burn
  * 2 secrets, then draw a relic and take it facedown. An empty relic deck
  * pays the cost and does nothing else. The draw and its line are
  * `RelicDraws.drawSteps`, shared with Tinker's Fair.
  */
case object DowsingSticks extends PaidAction("relic.dowsing-sticks",
    Cost(secret = 1, secretBurnt = 2)):
  /** Its line when the deck had no relic to draw. */
  val emptyDeck: NoteKey = RelicDraws.emptyDeck
  override def noteKeys: Vector[NoteKey] = Vector(RelicDraws.drew, emptyDeck)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    Right(Sequence(RelicDraws.drawSteps(id, player, source)))
