package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.PowerFixture
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class RoyalStablesSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val stables = CatalogNames.denizen("Royal Stables")
  private val modifiers = Vector(RoyalStables.id)
  /** Royal Stables at the actor's site, Buried Giant, the Cradle's plains. */
  private def atHome: ReadyGame = Table.from(board()).denizen(stables,
    at = cradlePlains).unchecked

  test("a Travel costs one less Supply"):
    val ready = Table.from(atHome).warbandsAt(pass, actor, 1).unchecked
    val province = provincePlains
    assertEquals(candidates(ready).get(province), Some(2))
    assertEquals(candidates(ready, modifiers).get(province), Some(1))
    val done = accepted(travel(ready, province, modifiers))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).player(actor).pawnSite, Some(province))
    assertEquals(Look(result).supply(actor), 7 - 1)
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
    assertEquals(Look(Outcomes.ready(accepted(travel(atHome, coast,
      modifiers)).state)).supply(actor),
      7 - 1)

  test("terrain applies first: a mountain in the region costs 1 + 1 - 1"):
    // The cradle holds the mountain and the coast here, so the printed base
    // from the coast to the mountain is 1 and the mountain adds 1. Reducing
    // first would clamp 1 to 1 and then add 1, costing 2.
    val cradle = Table.from(board(source = mountain)).pawn(actor, coast)
      .unchecked
    val ready = Table.from(cradle).denizen(stables, at = coast).unchecked
    assertEquals(candidates(ready).get(mountain), Some(2))
    assertEquals(candidates(ready, modifiers).get(mountain), Some(1))

  test("with Tents the Travel is free: Tents removes the payment"):
    val tents = CatalogNames.denizen("Tents")
    val ready = Table.from(atHome).adviser(actor, tents).favor(actor,
      1).unchecked
    val both = Vector(Tents.id, RoyalStables.id)
    assertEquals(candidates(ready, both).get(coast), Some(0))
    assertEquals(Look(Outcomes.ready(accepted(travel(ready, coast,
      both)).state)).supply(actor), 7)

  test("it may be used at a site the actor rules"):
    val ruled = Table.from(board()).denizen(stables,
      at = hinterlandPlains).warbandsAt(hinterlandPlains, actor, 1).unchecked
    val ready = Table.from(ruled).warbandsAt(pass, actor, 1).unchecked
    assertEquals(candidates(ready, modifiers).get(provincePlains), Some(1))

  test("it may not be used at a site the actor neither stands at nor rules"):
    val ready = Table.from(board()).denizen(stables,
      at = hinterlandPlains).warbandsAt(pass, actor, 1).unchecked
    assertEquals(travel(ready, provincePlains, modifiers).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "power denizen.royal-stables is not applicable to this travel")))

  test("it is a Travel modifier only"):
    val offered = (action: ActionRef) => accepted(rules.offerableWalkerPowers(
      atHome,
      actor, action)).map(_.id)
    assert(offered(ActionRef.Travel).contains(RoyalStables.id))
    assert(!offered(ActionRef.Muster).contains(RoyalStables.id))
