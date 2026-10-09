package oathdigital.gameplay.powers.search

import oathdigital.gameplay.{ModifierPlay, SearchPlay}
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Errand Boy (card 11): for 1 favor, the other regions' discard piles are
  * offered, each at 2 Supply. The actor's own pile and the world deck stay
  * choosable, warned (catalog batch 6, slice 3). */
class ErrandBoySuite extends munit.FunSuite:
  import SearchFixture.rules

  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val modifiers = Vector(ErrandBoy.id)
  private val provinces = Vector("Wrestlers", "Mercenaries", "Homesteaders")
    .map(CatalogNames.denizen(_))
  private val hinterland = Vector("Defame", "Scryer", "Blackmail")
    .map(CatalogNames.denizen(_))
  private val everySource: Vector[DecisionOptionRef] =
    (SearchSource.WorldDeck +: Region.all.map(SearchSource.RegionalDiscard(_)))
      .map(SearchSource.ref)

  /** p1 at Ancient City (Cradle) holding Errand Boy faceup, with the
    * start's 1 favor, 5 Supply, and three cards in the Provinces and
    * Hinterland discard piles. The Cradle pile holds one. */
  private def holdingErrandBoy: Table = Table.start
    .pawn(p1, "Ancient City").adviser(p1, errandBoy).supply(p1, 5)
    .discarded(Region.Cradle, "Threatening Roar")
    .discarded(Region.Provinces, provinces*)
    .discarded(Region.Hinterland, hinterland*)

  test("picked, it places 1 favor on itself and offers the other regions' " +
      "piles at 2 Supply"):
    val started = accepted(ModifierPlay.started(rules,
      Ready(holdingErrandBoy.ready), ActionRef.Search, p1,
      PowerWindow.SearchModifierSelection, modifiers))
    val after = Look(SearchFixture.after(started))
    assertEquals(after.favor(p1), 0)
    assertEquals(after.tokensOn(errandBoy), Tokens(1, 0))
    val shown = ShownOptions.atParkedDecision(started.state)
    assertEquals(shown.map(option => (option.kind, option.id, option.badge)),
      everySource.map(ref => (ref.kind, ref.wireId, Some("2 Supply"))))

  test("the world deck and the actor's own pile stay choosable, warned"):
    val shown = ShownOptions.sourcesAfterPicking(holdingErrandBoy.ready,
      modifiers)
    def stateOf(source: SearchSource) = ShownOptions.optionFor(shown,
      SearchSource.ref(source)).map(option => (option.state, option.reason))
    Vector(SearchSource.WorldDeck, SearchSource.RegionalDiscard(Region.Cradle))
      .foreach(source => assertEquals(stateOf(source),
        Some(("warning", Some(ErrandBoy.idle)))))
    Vector(Region.Provinces, Region.Hinterland).foreach(region =>
      assertEquals(stateOf(SearchSource.RegionalDiscard(region)),
        Some(("available", None))))

  test("a Search of another region's pile draws its top three"):
    val searched = accepted(SearchPlay.searched(rules,
      Ready(holdingErrandBoy.ready), p1,
      SearchSource.RegionalDiscard(Region.Hinterland), modifiers))
    val after = SearchFixture.after(searched)
    assertEquals(after.game.current.temporaryHands(p1), hinterland.reverse)
    assertEquals(Look(after).supply(p1), 5 - 2)

  test("with Observatory, each pile is offered once"):
    val ready = holdingErrandBoy.denizen("Observatory", at = "Ancient City")
      .ready
    val shown = ShownOptions.sourcesAfterPicking(ready,
      Vector(Observatory.id, ErrandBoy.id))
    assertEquals(shown.map(option => (option.kind, option.id)),
      everySource.map(ref => (ref.kind, ref.wireId)))
