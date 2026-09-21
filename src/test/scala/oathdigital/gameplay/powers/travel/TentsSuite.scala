package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.PowerFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class TentsSuite extends munit.FunSuite {
  import PowerFixture._
  import TravelFixture._

  private val tents = DenizenId("29")
  private val modifiers = Vector(Tents.id)
  /** The actor holds Tents as a faceup adviser and 1 favor, at the first plains. */
  private def held = withBoard(adviser(board(), tents))(_.copy(favor = 1))

  test("Tents is a registered selected Travel modifier that costs 1 favor") {
    val power = Tents.forCatalog(catalog).get
    assertEquals(power.cardId, tents)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Travel))
    assertEquals(power.cost, Cost(favor = 1))
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("a destination in the region of the pawn's site costs no Supply, and is " +
      "still offered") {
    assertEquals(candidates(held).get(coast), Some(1))
    assertEquals(candidates(held, modifiers).get(coast), Some(0))
  }

  test("Travel in the region places the favor on the card and spends no Supply") {
    val ready = held
    val done = travel(ready, coast, modifiers).toOption.get
    val result = after(done)
    assertEquals(player(result).pawnSite, Some(coast))
    assertEquals(supplyOf(result), 7)
    assertEquals(player(result).board.favor, 0)
    assertEquals(adviserTokens(result, tents), Tokens(1, 0))
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), result)
    assert(PaidActionHarness.wireRoundTrips(done.events))
  }

  test("the same power at the pawn's site takes its favor onto the site card") {
    val ready = withBoard(denizenAt(board(), tents, plains.head))(_.copy(favor = 1))
    val result = after(travel(ready, coast, modifiers).toOption.get)
    assertEquals(supplyOf(result), 7)
    assertEquals(PaidActionHarness.tokensOn(result, tents), Tokens(1, 0))
  }

  test("a route into another region is not changed, but the favor is still " +
      "paid: the player owns the choice to select it") {
    val ready = passRuled(held)
    val province = plains(1)
    assertEquals(candidates(ready, modifiers).get(province),
      candidates(ready).get(province))
    val result = after(travel(ready, province, modifiers).toOption.get)
    assertEquals(supplyOf(result), 7 - 2)
    assertEquals(player(result).board.favor, 0)
    assertEquals(adviserTokens(result, tents), Tokens(1, 0))
  }

  test("it cannot be selected without a favor to place, or onto an occupied card") {
    val broke = withBoard(held)(_.copy(favor = 0))
    assert(travel(broke, coast, modifiers).isLeft)
    val occupied = updateActor(held)(p => p.copy(advisers = p.advisers.map {
      case card: DenizenState if card.id == tents => card.copy(tokens = Tokens(1, 0))
      case other => other
    }))
    assert(travel(occupied, coast, modifiers).isLeft)
  }

  test("it is a Travel modifier only") {
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(held,
      actor, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Travel).contains(Tents.id))
    assert(!offered(ActionRef.Search).contains(Tents.id))
    assert(!offered(ActionRef.Muster).contains(Tents.id))
  }

  test("it is not offered when the card is facedown") {
    val facedown = withBoard(adviser(board(), tents, Orientation.FaceDown))(
      _.copy(favor = 1))
    assert(travel(facedown, coast, modifiers).isLeft)
  }

  test("Tents and Forest Paths together need two favor: one favor is refused at " +
      "selection, before anything is paid") {
    val paths = DenizenId("43")
    val both = Vector(Tents.id, ForestPaths.id)
    def ready(favor: Int) = withBoard(adviser(held, paths))(_.copy(favor = favor))
    val refused = travel(ready(1), coast, both)
    assert(refused.left.toOption.exists(_.toString.contains(
      "cannot all be paid together")), refused.toString)
    val done = after(travel(ready(2), coast, both).toOption.get)
    assertEquals(player(done).board.favor, 0)
    assertEquals(adviserTokens(done, tents), Tokens(1, 0))
    assertEquals(adviserTokens(done, paths), Tokens(1, 0))
  }
}
