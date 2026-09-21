package oathdigital.gameplay.powers.travel

import oathdigital.catalog.CardRestrictions
import oathdigital.gameplay.powers.PowerFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathViolation.TravelPassBlocked

class ForestPathsSuite extends munit.FunSuite {
  import PowerFixture._
  import TravelFixture._

  private val paths = DenizenId("43")
  private val modifiers = Vector(ForestPaths.id)
  private val beast: DenizenId = catalog.denizens.filter(d =>
    d.suit == Suit.Beast && d.restrictions == CardRestrictions.Unrestricted &&
      d.id.value != paths.value).map(d => DenizenId(d.id.value)).head
  private def held = withBoard(adviser(board(), paths))(_.copy(favor = 1))

  test("Forest Paths is a registered selected Travel modifier that costs 1 favor") {
    val power = ForestPaths.forCatalog(catalog).get
    assertEquals(power.cardId, paths)
    assertEquals(power.cost, Cost(favor = 1))
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("a destination with a beast card costs no Supply and ignores the " +
      "terrain of sites") {
    val ready = denizenAt(passRuled(held), beast, mountain)
    // The printed 2 and the Mountain's 1.
    assertEquals(candidates(ready).get(mountain), Some(3))
    assertEquals(candidates(ready, modifiers).get(mountain), Some(0))
    val done = travel(ready, mountain, modifiers).toOption.get
    val result = after(done)
    assertEquals(player(result).pawnSite, Some(mountain))
    assertEquals(supplyOf(result), 7)
    assertEquals(player(result).board.favor, 0)
    assertEquals(adviserTokens(result, paths), Tokens(1, 0))
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), result)
    assert(PaidActionHarness.wireRoundTrips(done.events))
  }

  test("it ignores Narrow Pass too: a pass the actor does not rule blocks nothing") {
    val ready = denizenAt(held, beast, plains(1))
    val blocked = travel(ready, plains(1))
    assertEquals(blocked.left.toOption.get, TravelPassBlocked(pass, plains(1)))
    assertEquals(candidates(ready).get(plains(1)), None)
    assertEquals(candidates(ready, modifiers).get(plains(1)), Some(0))
    val result = after(travel(ready, plains(1), modifiers).toOption.get)
    assertEquals(player(result).pawnSite, Some(plains(1)))
    assertEquals(supplyOf(result), 7)
  }

  test("a ruined beast edifice counts as a beast card") {
    val edifice = catalog.edifices.find(_.suit == Suit.Beast).get
    val ready = edificeAt(passRuled(held), EdificeId(edifice.id.value),
      EdificeSide.Ruined, mountain)
    assertEquals(candidates(ready, modifiers).get(mountain), Some(0))
  }

  test("without a beast card at the destination the favor is still paid, but " +
      "no Supply is saved and no site power is ignored") {
    val ready = passRuled(held)
    assertEquals(candidates(ready, modifiers), candidates(ready))
    val result = after(travel(ready, mountain, modifiers).toOption.get)
    // The printed 2 and the Mountain's 1, both still charged.
    assertEquals(supplyOf(result), 7 - 3)
    assertEquals(player(result).board.favor, 0)
    assertEquals(adviserTokens(result, paths), Tokens(1, 0))
  }

  test("Narrow Pass still blocks a route with no beast card at the destination") {
    val ready = held
    assertEquals(travel(ready, plains(1), modifiers).left.toOption.get,
      TravelPassBlocked(pass, plains(1)))
  }

  test("it cannot be selected without a favor to place") {
    val broke = withBoard(denizenAt(passRuled(held), beast, mountain))(
      _.copy(favor = 0))
    assert(travel(broke, mountain, modifiers).isLeft)
  }
}
