package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.PowerFixture
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips

class RoyalStablesSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val stables = DenizenId("245")
  private val modifiers = Vector(RoyalStables.id)
  /** Royal Stables at the actor's site, the first plains. */
  private def atHome: ReadyGame = denizenAt(board(), stables, plains.head)

  test("a Travel costs one less Supply"):
    val ready = passRuled(atHome)
    val province = plains(1)
    assertEquals(candidates(ready).get(province), Some(2))
    assertEquals(candidates(ready, modifiers).get(province), Some(1))
    val done = travel(ready, province, modifiers).toOption.get
    val result = after(done)
    assertEquals(Look(result).player(actor).pawnSite, Some(province))
    assertEquals(supplyOf(result), 7 - 1)
    // The Stables themselves are free.
    assertEquals(Look(result).player(actor).board.favor, Look(ready)
      .player(actor).board.favor)
    assertEquals(Look(result).player(actor).board.faceUpSecrets,
      Look(ready).player(actor).board.faceUpSecrets)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("a Travel never costs less than 1 Supply"):
    assertEquals(candidates(atHome).get(coast), Some(1))
    assertEquals(candidates(atHome, modifiers).get(coast), Some(1))
    assertEquals(supplyOf(after(travel(atHome, coast, modifiers).toOption.get)),
      7 - 1)

  test("terrain applies first: a mountain in the region costs 1 + 1 - 1"):
    // The cradle holds the mountain and the coast here, so the printed base
    // from the coast to the mountain is 1 and the mountain adds 1. Reducing
    // first would clamp 1 to 1 and then add 1, costing 2.
    val cradle = Table.from(board(source = mountain)).pawn(actor, coast)
      .unchecked
    val ready = denizenAt(cradle, stables, coast)
    assertEquals(candidates(ready).get(mountain), Some(2))
    assertEquals(candidates(ready, modifiers).get(mountain), Some(1))

  test("with Tents the Travel is free: Tents removes the payment"):
    val tents = DenizenId("29")
    val ready = Table.from(adviser(atHome, tents)).favor(actor, 1).unchecked
    val both = Vector(Tents.id, RoyalStables.id)
    assertEquals(candidates(ready, both).get(coast), Some(0))
    assertEquals(supplyOf(after(travel(ready, coast, both).toOption.get)), 7)

  test("it may be used at a site the actor rules"):
    val ruled = ruledBy(denizenAt(board(), stables, plains(2)), plains(2), actor)
    val ready = passRuled(ruled)
    assertEquals(candidates(ready, modifiers).get(plains(1)), Some(1))

  test("it may not be used at a site the actor neither stands at nor rules"):
    val ready = passRuled(denizenAt(board(), stables, plains(2)))
    assertEquals(travel(ready, plains(1), modifiers).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "power denizen.royal-stables is not applicable to this travel")))

  test("it is a Travel modifier only"):
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(atHome,
      actor, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Travel).contains(RoyalStables.id))
    assert(!offered(ActionRef.Muster).contains(RoyalStables.id))
