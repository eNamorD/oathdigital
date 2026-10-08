package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.PowerFixture
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Refusals}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class RoyalStablesSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val stables = CatalogNames.denizen("Royal Stables")
  private val modifiers = Vector(RoyalStables.id)
  /** Royal Stables at the actor's site, Buried Giant, the Cradle's plains. */
  private def stablesAtHome: ReadyGame = on(board(supply = 7))(_
    .denizen(stables, at = cradlePlains))

  // The printed Travel cost is 1 within the Cradle and 2 from the Cradle to
  // the Provinces; the Stables take 1 off, never below 1.

  test("a Travel costs one less Supply"):
    val ready = on(stablesAtHome)(_.warbandsAt(pass, actor, 1))
    assertEquals(candidates(ready).get(provincePlains), Some(2))
    assertEquals(candidates(ready, modifiers).get(provincePlains), Some(1))
    val done = accepted(travel(ready, provincePlains, modifiers))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).player(actor).pawnSite, Some(provincePlains))
    assertEquals(Look(result).supply(actor), 7 - 1)
    // The Stables themselves are free.
    assertEquals(Look(result).favor(actor), Look(ready).favor(actor))
    assertEquals(Look(result).faceUpSecrets(actor),
      Look(ready).faceUpSecrets(actor))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("a Travel never costs less than 1 Supply"):
    assertEquals(candidates(stablesAtHome).get(coast), Some(1))
    assertEquals(candidates(stablesAtHome, modifiers).get(coast), Some(1))
    val done = accepted(travel(stablesAtHome, coast, modifiers))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).supply(actor), 7 - 1)

  test("terrain applies first: a mountain in the region costs 1 + 1 - 1"):
    // The cradle holds the mountain and the coast here, so the printed base
    // from the coast to the mountain is 1 and the mountain adds 1. Reducing
    // first would clamp 1 to 1 and then add 1, costing 2.
    val cradle = on(board(source = mountain))(_.pawn(actor, coast)
      )
    val ready = on(cradle)(_.denizen(stables, at = coast))
    assertEquals(candidates(ready).get(mountain), Some(2))
    assertEquals(candidates(ready, modifiers).get(mountain), Some(1))

  test("with Tents the Travel is free: Tents removes the payment"):
    val tents = CatalogNames.denizen("Tents")
    val ready = on(stablesAtHome)(_.adviser(actor, tents).favor(actor, 1)
      )
    val both = Vector(Tents.id, RoyalStables.id)
    assertEquals(candidates(ready, both).get(coast), Some(0))
    val result = Outcomes.ready(accepted(travel(ready, coast, both)).state)
    assertEquals(Look(result).supply(actor), 7)

  test("it may be used at a site the actor rules"):
    val ruled = on(board())(_.denizen(stables, at = hinterlandPlains)
      .warbandsAt(hinterlandPlains, actor, 1))
    val ready = on(ruled)(_.warbandsAt(pass, actor, 1))
    assertEquals(candidates(ready, modifiers).get(provincePlains), Some(1))

  test("it may not be used at a site the actor neither stands at nor rules"):
    val ready = on(board())(_.denizen(stables, at = hinterlandPlains)
      .warbandsAt(pass, actor, 1))
    assertEquals(travel(ready, provincePlains, modifiers).left.toOption,
      Some(Refusals.notApplicable(RoyalStables.id, "travel")))

  test("it is a Travel modifier only"):
    val offered = (action: ActionRef) => accepted(
      rules.offerableWalkerPowers(stablesAtHome, actor, action)).map(_.id)
    assert(offered(ActionRef.Travel).contains(RoyalStables.id))
    assert(!offered(ActionRef.Muster).contains(RoyalStables.id))
