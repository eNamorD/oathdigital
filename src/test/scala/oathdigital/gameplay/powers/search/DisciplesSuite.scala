package oathdigital.gameplay.powers.search

import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class DisciplesSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val disciples = CatalogNames.denizen("Disciples")
  private val modifiers = Vector(Disciples.id)
  private val deckTop = SearchFixture.denizensOf(Suit.Hearth).take(3)
  private val region = Table.start.ready.game.current.map
    .regionOf(Table.homeOf(p1)).get

  /** p1 in Act at Ancient City, with Disciples as a faceup adviser and 5
    * Supply. The world deck is topped by plain cards, the regional discard
    * holds three, `visions` Visions were drawn so far, and `holder` holds the
    * Darkest Secret. */
  private def board(visions: Int, holder: Option[PlayerId] = Some(p1))
      : ReadyGame = Table.start.worldDeckTop(deckTop*).supply(p1, 5)
    .adviser(p1, disciples)
    .discarded(region, SearchFixture.denizensOf(Suit.Order).take(3)*)
    .darkestSecret(holder, 1)
    // No step states the Visions track.
    .update(_.updateCurrent(c => c.copy(tracks =
      c.tracks.copy(visionsDrawn = visions))))
    .ready

  private val world = "search:world"
  private def regional = s"search:regional-discard:${region.key}"

  /** The Supply a Search from `source` spends at its start. */
  private def spent(ready: ReadyGame, selected: Vector[PowerId],
      source: String = world): Int =
    val started = rules.startWalker(Ready(ready), ActionRef.Search, p1,
      selected, Vector(DecisionOptionRef.Button(source))).toOption.get
    5 - Look(SearchFixture.after(started)).supply(p1)

  test("holding the Darkest Secret, a world Search costing 3 costs 2"):
    val ready = board(visions = 1)
    assertEquals(spent(ready, modifiers), 2)
    assertEquals(spent(ready, Vector.empty), 3)
    val started = rules.startWalker(Ready(ready), ActionRef.Search, p1,
      modifiers, Vector(DecisionOptionRef.Button(world))).toOption.get
    assertEquals(PaidActionHarness.replayed(rules, ready, started.events),
      SearchFixture.after(started))
    assert(PaidActionHarness.wireRoundTrips(started.events))

  test("holding the Darkest Secret, a world Search costing 4 costs 2"):
    assertEquals(spent(board(visions = 3), modifiers), 2)
    assertEquals(spent(board(visions = 3), Vector.empty), 4)

  test("without the Darkest Secret the cost is unchanged"):
    assertEquals(spent(board(visions = 1, holder = Some(p2)), modifiers), 3)
    assertEquals(spent(board(visions = 1, holder = None), modifiers), 3)

  test("a cost of 2 is unchanged: the first world Search, and a regional " +
      "discard, which always costs 2"):
    assertEquals(spent(board(visions = 0), modifiers), 2)
    assertEquals(spent(board(visions = 1), modifiers, regional), 2)

  test("it may be selected without the Darkest Secret, for a Search only"):
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(
      board(visions = 0, holder = None), p1, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Search).contains(Disciples.id))
    assert(!offered(ActionRef.Muster).contains(Disciples.id))
