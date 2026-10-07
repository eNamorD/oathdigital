package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.PowerFixture
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class AFastSteedSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val steed = CatalogNames.denizen("A Fast Steed")
  private val modifiers = Vector(AFastSteed.id)

  /** The actor holds A Fast Steed faceup, 1 favor, 7 Supply and `warbands`
    * warbands on the board, at Buried Giant, the Cradle's plains. */
  private def ridingWith(warbands: Int): ReadyGame =
    Table.from(board(supply = 7)).adviser(actor, steed).favor(actor, 1)
      .boardWarbands(actor, warbands).unchecked

  test("with three warbands on the board, Travel spends no Supply and the " +
      "favor is placed on it"):
    val ready = ridingWith(warbands = 3)
    assertEquals(candidates(ready, modifiers).get(coast), Some(0))
    val done = accepted(travel(ready, coast, modifiers))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).player(actor).pawnSite, Some(coast))
    assertEquals(Look(result).supply(actor), 7)
    assertEquals(Look(result).favor(actor), 0)
    assertEquals(Look(result).tokensOn(steed), Tokens(1, 0))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("with four warbands on the board, Travel pays its Supply, and the " +
      "favor is still placed: the player owns the choice to select it"):
    val result = Outcomes.ready(accepted(travel(ridingWith(warbands = 4), coast,
      modifiers)).state)
    // The printed 1 for a Travel within the Cradle.
    assertEquals(Look(result).supply(actor), 7 - 1)
    assertEquals(Look(result).tokensOn(steed), Tokens(1, 0))

  test("it is a Travel modifier, and cannot be selected without a favor to " +
      "place"):
    val offered = (ready: ReadyGame, action: ActionRef) =>
      accepted(rules.offerableWalkerPowers(ready, actor, action)).map(_.id)
    val ready = ridingWith(warbands = 3)
    assert(offered(ready, ActionRef.Travel).contains(AFastSteed.id))
    assert(!offered(ready, ActionRef.Search).contains(AFastSteed.id))
    val broke = Table.from(ready).favor(actor, 0).unchecked
    assert(!offered(broke, ActionRef.Travel).contains(AFastSteed.id))
