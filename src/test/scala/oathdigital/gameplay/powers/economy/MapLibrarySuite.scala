package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ModifierOffers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, PhasePowerHarness, PowerRules, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class MapLibrarySuite extends munit.FunSuite:
  private val library = CatalogNames.denizen("Map Library")
  private val selected = Vector(MapLibrary.id)
  private val map = Table.start.unchecked.game.current.map
  /** p1's pawn site. */
  private val home = CatalogNames.site("Ancient City")
  /** Another site in play in the pawn's region. */
  private val near = map.inPlay.find(site => site != home &&
    map.regionOf(site) == map.regionOf(home)).get
  /** A site in play in another region. */
  private val away = map.inPlay.find(site =>
    map.regionOf(site) != map.regionOf(home)).get
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** A plain Beast denizen. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val hallowedSpring = CatalogNames.edifice("Hallowed Spring")
  private val rules = PowerRules.withWalkerPowers

  /** p1 at Ancient City beside Map Library, with the start's 1 faceup secret
    * to pay a Trade for favor. Wrestlers and the intact Hallowed Spring stand
    * at the other site in the region, Errand Boy in another region, and the
    * Order bank holds 3 favor. */
  private def atLibraryWithRegionCards: Table = Table.start.pawn(p1, at = home)
    .denizen(library, at = home).denizen(wrestlers, at = near)
    .edifice(hallowedSpring, EdificeSide.Intact, at = near)
    .denizen(errandBoy, at = away).bankFavor(Suit.Order, 3)

  private def tradeStarted(ready: ReadyGame): OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.Trade, p1, selected,
      Vector(DecisionOptionRef.Button("favor"))))

  private def tradeModifiers(ready: ReadyGame): Vector[PowerId] =
    accepted(ModifierOffers.offerable(WalkerPowerCatalog.default(catalog),
      ready, p1, ActionRef.Trade)).map(_.id)

  test("selected, a Trade offers every card at the sites in the pawn's " +
      "region, edifices too, and none elsewhere"):
    assertEquals(PhasePowerHarness.offered(
      tradeStarted(atLibraryWithRegionCards.ready), p1),
      Some(Vector("denizen" -> library.value, "denizen" -> wrestlers.value,
        "edifice" -> hallowedSpring.value)))

  test("trading with a card at another site places the secret there and " +
      "gains favor of its suit"):
    val ready = atLibraryWithRegionCards.ready
    val done = EconomyActions.trade(rules, ready, p1, selected, "favor",
      DecisionOptionRef.Denizen(wrestlers))
    val after = Look(done.result)
    assertEquals(after.tokensOn(wrestlers), Tokens(0, 1))
    // 1, and p1 holds no Order adviser.
    assertEquals(after.favor(p1), Look(ready).favor(p1) + 1)
    assertEquals(after.bankFavor(Suit.Order), 2)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("a card at another site that holds favor or secrets is shown " +
      "unavailable"):
    val started = tradeStarted(atLibraryWithRegionCards
      .tokens(wrestlers, favor = 1).ready)
    assertEquals(PhasePowerHarness.queryOf(started, p1).map(_.offeredOptions
      .map(option => (option.id, option.state))), Some(Vector(
        library.value -> "available", wrestlers.value -> "unavailable",
        hallowedSpring.value -> "available")))

  test("it is selectable only while the pawn stands at its site"):
    assert(tradeModifiers(atLibraryWithRegionCards.ready)
      .contains(MapLibrary.id))
    // p1 rules Map Library's site with two warbands, but the pawn stands in
    // another region.
    val ruledOnly = atLibraryWithRegionCards.pawn(p1, at = away)
      .warbandsAt(home, p1, 2).ready
    assert(!tradeModifiers(ruledOnly).contains(MapLibrary.id))

  test("it is selectable at its site while another player rules the site"):
    val ruledByP2 = atLibraryWithRegionCards.warbandsAt(home, p2, 2).ready
    assert(tradeModifiers(ruledByP2).contains(MapLibrary.id))
