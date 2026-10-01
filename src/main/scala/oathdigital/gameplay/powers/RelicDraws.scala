package oathdigital.gameplay.powers

import oathdigital.model._

/** Relic draws that powers share. */
object RelicDraws:
  /** Draws the top relic of the relic deck and takes it facedown into the
    * player's play area. An empty relic deck draws nothing.
    */
  def takeTop(ready: ReadyGame, actor: PlayerId): Vector[CoreOperation] =
    ready.game.current.commonCards.relicDeck.headOption.toVector.map(relic =>
      Play(relic, PositionedLocation(Location.Deck(CardDeck.Relic),
        StackPosition.Top), Location.PlayArea(actor), Orientation.FaceDown))

  /** "The relic deck was empty.": a relic draw's line when it drew none. */
  val emptyDeck: NoteKey = NoteKey("used.empty", Vector(
    NotePart.Text("The relic deck was empty.")))

  /** A power's draw of the top relic, taken facedown, then its line: the
    * relic drawn, or the empty deck. Dowsing Sticks and Tinker's Fair.
    *
    * The draw sits in a `BuildOps` because whether the deck has a top card
    * is a fact of the state when the draw runs, not when the tree was built.
    */
  def drawSteps(power: PowerId, player: PlayerId, source: DecisionOptionRef)
      : Vector[Operation] = Vector(
    BuildOps((state, _) => Right(takeTop(state, player))),
    Note(power, states => drawNote(source, player)(states)
      .orElse(PowerSourceRef.of(source).map(emptyDeck(_)))))

  /** "{player} drew {relic} facedown.": a relic draw's own line. */
  val drew: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" drew "), NotePart.Arg(1), NotePart.Text(" facedown.")))

  /** The relic the step before the note drew, restated. Nothing when that
    * step drew none. */
  def drawNote(source: DecisionOptionRef, player: PlayerId)(
      states: NoteStates): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(drewNote(_, player)(states))

  /** `drawNote` for a power whose card is `card`. */
  def drewNote(card: PowerSourceRef, player: PlayerId)(
      states: NoteStates): Option[PowerNote] = for
    step <- states.previous
    relic <- NoteSupport.relicsGained(step, player).headOption
  yield drew(card, NoteArg.Player(player), NoteArg.Card(relic))
