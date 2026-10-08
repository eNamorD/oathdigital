package oathdigital.gameplay.powers.search

import oathdigital.gameplay.SearchPlay
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class DisciplesSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val disciples = CatalogNames.denizen("Disciples")
  private val modifiers = Vector(Disciples.id)
  // Hearth and Order denizens whose powers these tests do not use.
  private val deckTop = Vector("Homesteaders", "Land Warden", "News from Afar")
  private val discardPile = Vector("Code of Honor", "Messenger", "Shield Wall")
  private val region = Region.Cradle // Ancient City's region
  private val startingSupply = 5

  /** p1 in Act at Ancient City, with Disciples as a faceup adviser and
    * `startingSupply` Supply. The world deck is topped by three Hearth cards,
    * the Cradle discard holds three Order cards, `visions` Visions were drawn
    * so far, and `holder` holds the Darkest Secret. */
  private def holdingDisciples(visions: Int,
      holder: Option[PlayerId] = Some(p1)): ReadyGame =
    Table.start.pawn(p1, "Ancient City").worldDeckTop(deckTop*)
      .supply(p1, startingSupply).adviser(p1, disciples)
      .discarded(region, discardPile*).darkestSecret(holder, 1)
      .visionsDrawn(visions).ready

  private val world: SearchSource = SearchSource.WorldDeck
  private def regional: SearchSource = SearchSource.RegionalDiscard(region)

  /** The Supply a Search from `source` spends at its start. */
  private def spent(ready: ReadyGame, selected: Vector[PowerId],
      source: SearchSource = world): Int =
    val started = accepted(SearchPlay.searched(rules, Ready(ready), p1,
      source, selected))
    startingSupply - Look(SearchFixture.after(started)).supply(p1)

  // A world Search costs 2 with no Vision drawn, 3 with one or two, and 4
  // with three or more.
  test("holding the Darkest Secret, a world Search costing 3 costs 2"):
    val ready = holdingDisciples(visions = 1)
    assertEquals(spent(ready, modifiers), 2)
    assertEquals(spent(ready, Vector.empty), 3)
    val started = accepted(SearchPlay.searched(rules, Ready(ready), p1,
      world, modifiers))
    assertEquals(ActPowerHarness.replayed(rules, ready, started.events),
      SearchFixture.after(started))
    assertWireRoundTrips(started.events)

  test("holding the Darkest Secret, a world Search costing 4 costs 2"):
    assertEquals(spent(holdingDisciples(visions = 3), modifiers), 2)
    assertEquals(spent(holdingDisciples(visions = 3), Vector.empty), 4)

  test("without the Darkest Secret the cost is unchanged"):
    assertEquals(
      spent(holdingDisciples(visions = 1, holder = Some(p2)), modifiers), 3)
    assertEquals(
      spent(holdingDisciples(visions = 1, holder = None), modifiers), 3)

  test("a cost of 2 is unchanged: the first world Search, and a regional " +
      "discard, which always costs 2"):
    assertEquals(spent(holdingDisciples(visions = 0), modifiers), 2)
    assertEquals(spent(holdingDisciples(visions = 1), modifiers, regional), 2)

  test("it may be selected without the Darkest Secret, for a Search only"):
    val offered = (action: ActionRef) => accepted(rules.offerableWalkerPowers(
      holdingDisciples(visions = 0, holder = None), p1, action)).map(_.id)
    assert(offered(ActionRef.Search).contains(Disciples.id))
    assert(!offered(ActionRef.Muster).contains(Disciples.id))

  test("a holder with only 2 Supply can afford a world Search costing 4"):
    // Three Visions drawn put the printed cost at 4; Disciples makes it 2.
    val ready = Table.start.worldDeckTop(deckTop*).supply(p1, 2)
      .adviser(p1, disciples).darkestSecret(Some(p1), 1).visionsDrawn(3).ready
    val started = accepted(SearchPlay.searched(rules, Ready(ready), p1,
      world, modifiers))
    assertEquals(Look(SearchFixture.after(started)).supply(p1), 0)
