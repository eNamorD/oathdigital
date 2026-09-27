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

  /** "{player} drew {relic} facedown.": a relic draw's own line. */
  val drew: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" drew "), NotePart.Arg(1), NotePart.Text(" facedown.")))

  /** The relic the step before the note drew, restated. Nothing when that
    * step drew none. */
  def drawNote(source: DecisionOptionRef, player: PlayerId)(
      states: NoteStates): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    relic <- NoteSupport.relicsGained(step, player).headOption
  yield drew(card, NoteArg.Player(player), NoteArg.Card(relic))
