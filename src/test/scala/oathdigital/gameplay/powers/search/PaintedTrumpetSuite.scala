package oathdigital.gameplay.powers.search

import oathdigital.gameplay.{ModifierPlay, SearchPlay}
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Painted Trumpet (relic R23): a Search of a discard pile draws the top
  * card of each discard pile instead, and nothing modifies that draw
  * (catalog batch 6, slice 3). */
class PaintedTrumpetSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val modifiers = Vector(PaintedTrumpet.id)
  private val cradleTop = CatalogNames.denizen("Fae Merchant")
  private val cradleBelow = CatalogNames.denizen("Threatening Roar")
  private val provincesTop = CatalogNames.denizen("Mercenaries")
  private val hinterlandTop = CatalogNames.denizen("Scryer")
  private val deckTop = Vector("Second Chance", "Homesteaders", "Messenger")
    .map(CatalogNames.denizen(_))

  /** p1 at Ancient City holding Painted Trumpet faceup, with 5 Supply and
    * the world deck topped by three plain cards. The Provinces and
    * Hinterland piles hold two cards each. The Cradle pile holds two
    * unless `emptyCradle`. */
  private def holdingTrumpet(emptyCradle: Boolean = false): Table =
    val table = Table.start.pawn(p1, "Ancient City")
      .relic(p1, "Painted Trumpet").supply(p1, 5).worldDeckTop(deckTop*)
      .discarded(Region.Provinces, "Wrestlers", "Mercenaries")
      .discarded(Region.Hinterland, "Defame", "Scryer")
    if emptyCradle then table
    else table.discarded(Region.Cradle, cradleBelow, cradleTop)

  private def searched(table: Table, pile: SearchSource,
      picked: Vector[PowerId]): ReadyGame =
    SearchFixture.after(accepted(SearchPlay.searched(rules,
      Ready(table.ready), p1, pile, picked)))

  private val cradle = SearchSource.RegionalDiscard(Region.Cradle)

  test("a discard pile picked draws the top card of each pile, for 2 " +
      "Supply"):
    val after = searched(holdingTrumpet(), cradle, modifiers)
    assertEquals(after.game.current.temporaryHands(p1),
      Vector(cradleTop, provincesTop, hinterlandTop))
    assertEquals(Look(after).supply(p1), 5 - 2)
    assertEquals(after.game.current.commonCards.discard(Region.Cradle),
      Vector(cradleBelow))

  test("Augury adds no card to its draw"):
    val table = holdingTrumpet().denizen("Augury", at = "Ancient City")
    val after = searched(table, cradle,
      Vector(PaintedTrumpet.id, Augury.id))
    assertEquals(after.game.current.temporaryHands(p1),
      Vector(cradleTop, provincesTop, hinterlandTop))

  test("an empty pile may be picked while another pile has a card"):
    val table = holdingTrumpet(emptyCradle = true)
    val shown = ShownOptions.sourcesAfterPicking(table.ready, modifiers)
    assertEquals(ShownOptions.optionFor(shown, SearchSource.ref(cradle))
      .map(option => (option.state, option.badge)),
      Some(("available", Some("2 Supply"))))
    assertEquals(searched(table, cradle, modifiers).game.current
      .temporaryHands(p1), Vector(provincesTop, hinterlandTop))

  test("a world Search is unchanged, and the world deck warns"):
    val after = searched(holdingTrumpet(), SearchSource.WorldDeck,
      modifiers)
    assertEquals(after.game.current.temporaryHands(p1), deckTop)
    val shown = ShownOptions.sourcesAfterPicking(holdingTrumpet().ready,
      modifiers)
    assertEquals(ShownOptions.optionFor(shown,
      SearchSource.ref(SearchSource.WorldDeck)).map(option =>
      (option.state, option.reason)),
      Some(("warning", Some(PaintedTrumpet.deckWarning))))

  test("Painted Trumpet and Mushrooms exclude each other"):
    val ready = holdingTrumpet().denizen("Mushrooms", at = "Ancient City")
      .ready
    val begun = accepted(rules.startWalker(Ready(ready), ActionRef.Search,
      p1))
    val decisionId =
      ModifierPicks.decisionId(PowerWindow.SearchModifierSelection)
    def shownAfterPicking(power: PowerId) = ShownOptions.atParkedDecision(
      accepted(rules.resolveWalker(begun.state, p1, decisionId,
        ModifierPlay.pick(power))).state)
    assertEquals(ShownOptions.optionFor(shownAfterPicking(Mushrooms.id),
      DecisionOptionRef.Relic(PaintedTrumpetCard.id)).map(option =>
      (option.state, option.reason)),
      Some(("unavailable", Some(PaintedTrumpet.notWithMushrooms))))
    assertEquals(ShownOptions.optionFor(shownAfterPicking(PaintedTrumpet.id),
      DecisionOptionRef.Denizen(MushroomsCard.id)).map(option =>
      (option.state, option.reason)),
      Some(("unavailable", Some(Mushrooms.notWithTrumpet))))
