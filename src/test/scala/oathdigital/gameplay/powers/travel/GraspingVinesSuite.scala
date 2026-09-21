package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{PowerFixture, TargetsFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class GraspingVinesSuite extends munit.FunSuite {
  import PowerFixture._
  import TravelFixture._

  private val vines = DenizenId("178")
  private val rival = TargetsFixture.others(base).head
  private def warbands(ready: ReadyGame): Int = player(ready).board.warbands

  /** The Vines stand at the actor's own site, which `ruler` rules. */
  private def vinesAtHome(ruler: Option[PlayerId]): ReadyGame = {
    val ready = denizenAt(board(), vines, plains.head)
    ruler.fold(ready)(ruledBy(ready, plains.head, _))
  }

  test("Grasping Vines is a registered persistent rule, so it is automatic") {
    val power = GraspingVines.forCatalog(catalog).get
    assertEquals(power.cardId, vines)
    assertEquals(power.resolution, PowerResolution.Automatic)
  }

  test("an enemy traveling from a site the ruler rules kills a warband of " +
      "their own") {
    val ready = vinesAtHome(Some(rival))
    val done = travel(ready, coast).toOption.get
    val result = after(done)
    assertEquals(player(result).pawnSite, Some(coast))
    assertEquals(warbands(result), warbands(ready) - 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), result)
    assert(PaidActionHarness.wireRoundTrips(done.events))
  }

  test("bandits rule the site: the traveller is still an enemy") {
    val ready = vinesAtHome(None)
    assertEquals(warbands(after(travel(ready, coast).toOption.get)),
      warbands(ready) - 1)
  }

  test("the ruler is exempt") {
    val ready = vinesAtHome(Some(actor))
    assertEquals(warbands(after(travel(ready, coast).toOption.get)),
      warbands(ready))
  }

  test("only the site the traveller leaves counts, not the destination") {
    val ready = ruledBy(denizenAt(board(), vines, plains(1)), plains(1), rival)
    assertEquals(warbands(after(travel(ready, coast).toOption.get)),
      warbands(ready))
    // Travelling out of the Vines' site does kill.
    val leaving = ruledBy(denizenAt(board(source = plains(1)), vines,
      plains(1)), plains(1), rival)
    val out = after(travel(leaving, coast).toOption.get)
    assertEquals(warbands(out), warbands(leaving) - 1)
  }

  test("the kill is not required: a traveller with no warband is not stopped") {
    val ready = withBoard(vinesAtHome(Some(rival)))(_.copy(warbands = 0))
    val result = after(travel(ready, coast).toOption.get)
    assertEquals(player(result).pawnSite, Some(coast))
    assertEquals(warbands(result), 0)
  }

  test("a facedown Grasping Vines is not active") {
    val ready = ruledBy(adviser(board(), vines, Orientation.FaceDown),
      plains.head, rival)
    assertEquals(warbands(after(travel(ready, coast).toOption.get)),
      warbands(ready))
  }
}
