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
    Cost(secret = 1, secretBurnt = 2)):
  /** Its line when the deck had no relic to draw. */
  val emptyDeck: NoteKey = NoteKey("used.empty", Vector(
    NotePart.Text("The relic deck was empty.")))
  override def noteKeys: Vector[NoteKey] = Vector(RelicDraws.drew, emptyDeck)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    BuildOps((state, _) => Right(RelicDraws.takeTop(state, player))),
    Note(id, states => RelicDraws.drawNote(source, player)(states)
      .orElse(PowerSourceRef.of(source).map(emptyDeck(_)))))))
