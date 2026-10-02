package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.PowerFixture
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips

class TentsSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val tents = DenizenId("29")
  private val modifiers = Vector(Tents.id)
  /** The actor holds Tents as a faceup adviser and 1 favor, at the first plains. */
  private def held = Table.from(adviser(board(), tents)).favor(actor, 1)
    .unchecked

  test("a destination in the region of the pawn's site costs no Supply, and is " +
      "still offered"):
    assertEquals(candidates(held).get(coast), Some(1))
    assertEquals(candidates(held, modifiers).get(coast), Some(0))

  test("Travel in the region places the favor on the card and spends no Supply"):
    val ready = held
    val done = travel(ready, coast, modifiers).toOption.get
    val result = after(done)
    assertEquals(Look(result).player(actor).pawnSite, Some(coast))
    assertEquals(supplyOf(result), 7)
    assertEquals(Look(result).player(actor).board.favor, 0)
    assertEquals(adviserTokens(result, tents), Tokens(1, 0))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("the same power at the pawn's site takes its favor onto the site card"):
    val ready = Table.from(denizenAt(board(), tents, plains.head)).favor(actor,
      1).unchecked
    val result = after(travel(ready, coast, modifiers).toOption.get)
    assertEquals(supplyOf(result), 7)
    assertEquals(Look(result).tokensOn(tents), Tokens(1, 0))

  test("a route into another region is not changed, but the favor is still " +
      "paid: the player owns the choice to select it"):
    val ready = passRuled(held)
    val province = plains(1)
    assertEquals(candidates(ready, modifiers).get(province),
      candidates(ready).get(province))
    val result = after(travel(ready, province, modifiers).toOption.get)
    assertEquals(supplyOf(result), 7 - 2)
    assertEquals(Look(result).player(actor).board.favor, 0)
    assertEquals(adviserTokens(result, tents), Tokens(1, 0))

  test("it cannot be selected without a favor to place, or onto an occupied card"):
    val broke = Table.from(held).favor(actor, 0).unchecked
    assertEquals(travel(broke, coast, modifiers).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "power denizen.tents is not applicable to this travel")))
    val occupied = Table.from(held).tokens(tents, favor = 1).unchecked
    assertEquals(travel(occupied, coast, modifiers).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "power denizen.tents is not applicable to this travel")))

  test("it is a Travel modifier only"):
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(held,
      actor, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Travel).contains(Tents.id))
    assert(!offered(ActionRef.Search).contains(Tents.id))
    assert(!offered(ActionRef.Muster).contains(Tents.id))

  test("it is not offered when the card is facedown"):
    val facedown = Table.from(adviser(board(), tents, Orientation.FaceDown))
      .favor(actor, 1).unchecked
    assertEquals(travel(facedown, coast, modifiers).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "power denizen.tents is not applicable to this travel")))

  test("Tents and Forest Paths together need two favor: one favor is refused at " +
      "selection, before anything is paid"):
    val paths = DenizenId("43")
    val both = Vector(Tents.id, ForestPaths.id)
    def ready(favor: Int) = Table.from(adviser(held, paths)).favor(actor, favor)
      .unchecked
    val refused = travel(ready(1), coast, both)
    assert(refused.left.toOption.exists(_.toString.contains(
      "cannot all be paid together")), refused.toString)
    val done = after(travel(ready(2), coast, both).toOption.get)
    assertEquals(Look(done).player(actor).board.favor, 0)
    assertEquals(adviserTokens(done, tents), Tokens(1, 0))
    assertEquals(adviserTokens(done, paths), Tokens(1, 0))
