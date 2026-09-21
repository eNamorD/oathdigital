package oathdigital.gameplay.powers

import oathdigital.model._

/** Relic draws that powers share. */
object RelicDraws {
  /** Draws the top relic of the relic deck and takes it facedown into the
    * player's play area. An empty relic deck draws nothing.
    */
  def takeTop(ready: ReadyGame, actor: PlayerId): Vector[CoreOperation] =
    ready.game.current.commonCards.relicDeck.headOption.toVector.map(relic =>
      Play(relic, PositionedLocation(Location.Deck(CardDeck.Relic),
        StackPosition.Top), Location.PlayArea(actor), Orientation.FaceDown))
}
