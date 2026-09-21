package oathdigital.gameplay.powers

import oathdigital.model._

/** Takes a card out of wherever the first game put it, so a fixture can place
  * it somewhere else without leaving a duplicate. `PowerFixture` removes a card
  * from the world deck or the relic deck only, but the first game also deals
  * cards to sites, regional discards and advisers.
  */
object CardStaging {
  def without(ready: ReadyGame, id: CardId): ReadyGame = ready.updateCurrent {
    current =>
      val cards = current.commonCards
      current.copy(
        commonCards = cards.copy(
          worldDeck = cards.worldDeck.filterNot(_ == id),
          relicDeck = cards.relicDeck.filterNot(_ == id),
          edificeDeck = cards.edificeDeck.filterNot(_ == id),
          regionalDiscards = cards.regionalDiscards.view
            .mapValues(_.filterNot(_ == id)).toMap),
        setAsideRelics = current.setAsideRelics.filterNot(_ == id),
        temporaryHands = current.temporaryHands.view
          .mapValues(_.filterNot(_ == id)).toMap,
        players = current.players.map(player => player.copy(
          advisers = player.advisers.filterNot(_.id == id),
          relics = player.relics.filterNot(_.id == id))),
        map = current.map.copy(sites = current.map.sites.view.mapValues(site =>
          site.copy(denizens = site.denizens.filterNot(_.id == id),
            relics = site.relics.filterNot(_.id == id))).toMap))
  }
}
