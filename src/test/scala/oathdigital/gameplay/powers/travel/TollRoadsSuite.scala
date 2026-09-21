package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{PowerFixture, TargetsFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class TollRoadsSuite extends munit.FunSuite {
  import PowerFixture._
  import TravelFixture._

  private val toll = DenizenId("118")
  private val rival = TargetsFixture.others(base).head

  /** Toll Roads stands at `plains(1)`, and `rival` rules it and the coast. The
    * actor, at the first plains, holds 1 favor and travels to the coast.
    */
  private def rivalRules: ReadyGame = {
    val ready = withBoard(denizenAt(board(), toll, plains(1)))(_.copy(favor = 1))
    ruledBy(ruledBy(ready, plains(1), rival), coast, rival)
  }

  test("Toll Roads is a registered persistent rule, so it is automatic") {
    val power = TollRoads.forCatalog(catalog).get
    assertEquals(power.cardId, toll)
    assertEquals(power.resolution, PowerResolution.Automatic)
  }

  test("an enemy pays the ruler 1 favor to travel to a site the ruler rules") {
    val ready = rivalRules
    val done = travel(ready, coast).toOption.get
    val result = after(done)
    assertEquals(player(result).pawnSite, Some(coast))
    assertEquals(player(result).board.favor, 0)
    assertEquals(player(result, rival).board.favor,
      player(ready, rival).board.favor + 1)
    assertEquals(supplyOf(result), 7 - 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), result)
    assert(PaidActionHarness.wireRoundTrips(done.events))
  }

  test("the rule covers every site the ruler holds, Toll Roads' own included") {
    val result = after(travel(passRuled(rivalRules), plains(1)).toOption.get)
    assertEquals(player(result).board.favor, 0)
    assertEquals(player(result, rival).board.favor,
      player(rivalRules, rival).board.favor + 1)
  }

  test("a traveller who cannot pay does not get the destination") {
    val broke = withBoard(rivalRules)(_.copy(favor = 0))
    assertEquals(candidates(broke).get(coast), None)
    assert(travel(broke, coast).isLeft)
    // A site the rival does not rule is still offered.
    assert(candidates(broke).contains(plains(2)))
  }

  test("a destination the ruler does not rule costs nothing extra") {
    val result = after(travel(rivalRules, plains(2)).toOption.get)
    assertEquals(player(result).board.favor, 1)
  }

  test("the ruler itself travels for free") {
    val ready = withBoard(denizenAt(board(), toll, plains(1)))(_.copy(favor = 1))
    val mine = ruledBy(ruledBy(ready, plains(1), actor), coast, actor)
    val result = after(travel(mine, coast).toOption.get)
    assertEquals(player(result).board.favor, 1)
  }

  test("when bandits rule, the favor is burnt") {
    val ready = withBoard(denizenAt(board(), toll, plains(1)))(_.copy(favor = 1))
    val result = after(travel(ready, coast).toOption.get)
    assertEquals(player(result).board.favor, 0)
    assertEquals(player(result, rival).board.favor,
      player(ready, rival).board.favor)
    assertEquals(candidates(withBoard(ready)(_.copy(favor = 0))).get(coast), None)
  }

  test("a facedown Toll Roads is not active") {
    val ready = withBoard(adviser(board(), toll, Orientation.FaceDown))(
      _.copy(favor = 0))
    assert(travel(ready, coast).isRight)
  }
}
