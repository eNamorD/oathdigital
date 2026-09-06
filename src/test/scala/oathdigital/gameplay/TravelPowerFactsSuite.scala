package oathdigital.gameplay

import oathdigital.gameplay.operations.OperationReason
import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powerresolver.CostContribution.{Add, Replace}
import oathdigital.gameplay.powers.{TravelPassBlockedCodec, TravelPowers}
import oathdigital.gameplay.powers.travel._
import oathdigital.model.{PowerId, SiteId}

/** Phase-4 review hardening: kind↔contribution single-source facts, real-id
  * suppression wiring, and the pass codec round trip.
  */
class TravelPowerFactsSuite extends munit.FunSuite {
  private val coast = TravelTerrainKind.Coast
  private val island = TravelTerrainKind.Island
  private val mountain = TravelTerrainKind.Mountain

  test("each terrain power carries its kind's canonical contribution") {
    val byId = TravelPowers.powers.map(p => p.id -> p).toMap
    val terrain = TravelPowers.powers.filterNot(_.id == TravelPowers.NarrowPass.id)
    terrain.foreach { power =>
      val terrainPower = byId(power.id).asInstanceOf[TravelCostTerrainPower]
      assertEquals(terrainPower.contribution,
        terrainPower.terrain.contribution,
        s"${power.id.value} must carry its kind's canonical contribution")
      assert(terrainPower.contribution.nonEmpty,
        s"${power.id.value} is a terrain power and must contribute")
    }
  }

  test("terrain kinds canonical amounts are Coast=1 Island=2 Mountain=1") {
    assertEquals(coast.contribution, Some(Replace(PowerWindow.TravelCost, 1)))
    assertEquals(island.contribution, Some(Add(PowerWindow.TravelCost, 2)))
    assertEquals(mountain.contribution, Some(Add(PowerWindow.TravelCost, 1)))
    assertEquals(TravelTerrainKind.Pass.contribution, None)
  }

  test("real coast suppression wiring drops Island and Mountain on a coast route") {
    val coastId = TravelPowers.DesolateShoreCoast.powerId
    val islandId = TravelPowers.SunkenIslesIsland.powerId
    val mountainId = TravelPowers.MinesMountain.powerId
    val context = TravelCostWindowContext(
      Vector(coastId), Vector(islandId, mountainId),
      Vector(coastId, islandId, mountainId),
      sourceCoast = true, destinationCoastOrIsland = true)
    val suppressed = SuppressionRegistry.suppressed(PowerWindow.TravelCost,
      Vector(coastId, islandId, mountainId), context)
    assertEquals(suppressed, Set(islandId, mountainId))
  }

  test("travel pass codec round trips to the typed violation") {
    val pass = SiteId("site:narrow-pass")
    val destination = SiteId("site:dunes")
    val reason = OperationReason("travel-pass-blocked",
      TravelPassBlockedCodec.encode(pass, destination))
    assertEquals(TravelPassBlockedCodec.decode(reason),
      Some(OathViolation.TravelPassBlocked(pass, destination)))
    assertEquals(TravelPassBlockedCodec.decode(OperationReason(
      "other-code", TravelPassBlockedCodec.encode(pass, destination))), None)
  }
}
