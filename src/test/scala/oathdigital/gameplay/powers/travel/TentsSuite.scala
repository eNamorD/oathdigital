package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.PowerFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class TentsSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val tents = CatalogNames.denizen("Tents")
  private val modifiers = Vector(Tents.id)
  /** The actor holds Tents as a faceup adviser and 1 favor, at Buried Giant,
    * the Cradle's plains. */
  private def held = on(board(supply = 7))(_.adviser(actor, tents)
    .favor(actor, 1))

  test("a destination in the region of the pawn's site costs no Supply, and is " +
      "still offered"):
    // The printed cost within the Cradle is 1.
    assertEquals(candidates(held).get(coast), Some(1))
    assertEquals(candidates(held, modifiers).get(coast), Some(0))

  test("Travel in the region places the favor on the card and spends no Supply"):
    val ready = held
    val done = accepted(travel(ready, coast, modifiers))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).player(actor).pawnSite, Some(coast))
    // 7 Supply, nothing paid.
    assertEquals(Look(result).supply(actor), 7)
    assertEquals(Look(result).favor(actor), 0)
    assertEquals(Look(result).tokensOn(tents), Tokens(1, 0))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("the same power at the pawn's site takes its favor onto the site card"):
    val ready = on(board(supply = 7))(_.denizen(tents, at = cradlePlains)
      .favor(actor, 1))
    val result = Outcomes.ready(accepted(travel(ready, coast, modifiers)).state)
    // 7 Supply, nothing paid.
    assertEquals(Look(result).supply(actor), 7)
    assertEquals(Look(result).tokensOn(tents), Tokens(1, 0))

  test("a route into another region is not changed, but the favor is still " +
      "paid: the player owns the choice to select it"):
    val ready = on(held)(_.warbandsAt(pass, actor, 1))
    assertEquals(candidates(ready, modifiers).get(provincePlains),
      candidates(ready).get(provincePlains))
    val done = accepted(travel(ready, provincePlains, modifiers))
    val result = Outcomes.ready(done.state)
    // The printed 2 from the Cradle to the Provinces, still charged.
    assertEquals(Look(result).supply(actor), 7 - 2)
    assertEquals(Look(result).favor(actor), 0)
    assertEquals(Look(result).tokensOn(tents), Tokens(1, 0))

  test("without a favor to place, or onto an occupied card, it is not " +
      "offered: the Travel starts on its destination"):
    val broke = on(held)(_.favor(actor, 0))
    assertEquals(startParksOn(broke), TravelProcedure.destinationDecisionId)
    val occupied = on(held)(_.tokens(tents, favor = 1))
    assertEquals(startParksOn(occupied),
      TravelProcedure.destinationDecisionId)

  test("it is a Travel modifier only"):
    val offered = (action: ActionRef) => accepted(
      rules.offerableWalkerPowers(held, actor, action)).map(_.id)
    assert(offered(ActionRef.Travel).contains(Tents.id))
    assert(!offered(ActionRef.Search).contains(Tents.id))
    assert(!offered(ActionRef.Muster).contains(Tents.id))

  test("Tents and Forest Paths together need two favor: with one, picking " +
      "Tents leaves Forest Paths unpaid for"):
    val paths = CatalogNames.denizen("Forest Paths")
    val both = Vector(Tents.id, ForestPaths.id)
    def ready(favor: Int) = on(held)(_.adviser(actor, paths)
      .favor(actor, favor))
    val begun = accepted(rules.startWalker(Ready(ready(1)), ActionRef.Travel,
      actor))
    val picked = accepted(rules.resolveWalker(begun.state, actor,
      modifierDecisionId, ModifierPlay.pick(Tents.id)))
    val listed = parkedDecision(picked.state).query match
      case DecisionQuery.ChooseOne(options, _, _) => options
      case other => fail(s"expected a choose-one, got $other")
    assertEquals(listed.find(_.ref == DecisionOptionRef.Denizen(paths)),
      Some(DecisionOption.Unavailable(
        DecisionOption.Denizen(DecisionOptionRef.Denizen(paths)),
        OptionReason.Written("Needs 1 favor; you have 0"))))
    val done = Outcomes.ready(accepted(travel(ready(2), coast, both)).state)
    assertEquals(Look(done).favor(actor), 0)
    assertEquals(Look(done).tokensOn(tents), Tokens(1, 0))
    assertEquals(Look(done).tokensOn(paths), Tokens(1, 0))
