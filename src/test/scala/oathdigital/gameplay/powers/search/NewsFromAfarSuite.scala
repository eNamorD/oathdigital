package oathdigital.gameplay.powers.search

import oathdigital.gameplay.SearchPlay
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class NewsFromAfarSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val news = CatalogNames.denizen("News from Afar")
  private val modifiers = Vector(NewsFromAfar.id)
  /** Plain denizens for the world deck and the Cradle discard; none is
    * played. */
  private val deckTop = Vector("Wrestlers", "Errand Boy", "Mercenaries")
    .map(CatalogNames.denizen(_))
  private val discardPile = Vector("Code of Honor", "Messenger", "Shield Wall")
    .map(CatalogNames.denizen(_))
  private val region = Region.Cradle // Ancient City's region
  private val startingSupply = 5

  /** p1 in Act at Ancient City holding News from Afar faceup and `favor`
    * favor, with `startingSupply` Supply, the world deck topped by three
    * plain cards and the Cradle discard holding three more. */
  private def holdingNews(favor: Int = 2): ReadyGame = Table.start
    .worldDeckTop(deckTop*).discarded(region, discardPile*)
    .pawn(p1, "Ancient City").supply(p1, startingSupply).adviser(p1, news)
    .favor(p1, favor).ready

  private def searchedWithNews(ready: ReadyGame, source: SearchSource)
      : OathTransition = accepted(SearchPlay.searched(rules, Ready(ready), p1,
    source, modifiers))

  test("selected, a world Search places 2 favor on it and spends no Supply"):
    val ready = holdingNews()
    val started = searchedWithNews(ready, SearchSource.WorldDeck)
    val after = Look(SearchFixture.after(started))
    assertEquals(after.supply(p1), startingSupply)
    assertEquals(after.favor(p1), 0)
    assertEquals(after.tokensOn(news), Tokens(2, 0))
    assertEquals(ActPowerHarness.replayed(rules, ready, started.events),
      SearchFixture.after(started))
    assertWireRoundTrips(started.events)

  test("a Search of a regional discard spends no Supply either"):
    val started = searchedWithNews(holdingNews(),
      SearchSource.RegionalDiscard(region))
    assertEquals(Look(SearchFixture.after(started)).supply(p1), startingSupply)

  test("it is a Search modifier, and cannot be selected without 2 favor to " +
      "place"):
    val offered = (ready: ReadyGame, action: ActionRef) =>
      accepted(rules.offerableWalkerPowers(ready, p1, action)).map(_.id)
    assert(offered(holdingNews(), ActionRef.Search).contains(NewsFromAfar.id))
    assert(!offered(holdingNews(), ActionRef.Travel).contains(NewsFromAfar.id))
    assert(!offered(holdingNews(favor = 1), ActionRef.Search)
      .contains(NewsFromAfar.id))
