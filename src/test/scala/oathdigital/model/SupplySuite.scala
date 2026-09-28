package oathdigital.model

import oathdigital.gameplay.phases.rest.FinishRestProcedure.ExileSupply

class SupplySuite extends munit.FunSuite:
  test("an Exile with nine or more banked warbands refreshes to six"):
    assertEquals(ExileSupply.refresh(9, 0), Some(SupplyTrack(6)))
    assertEquals(ExileSupply.refresh(20, 0), Some(SupplyTrack(6)))

  test("saved Supply moves the effective marker left and caps at seven"):
    assertEquals(ExileSupply.refresh(9, 1), Some(SupplyTrack(7)))
    assertEquals(ExileSupply.refresh(9, 4), Some(SupplyTrack(7)))

  test("numeric Supply rejects values outside the physical track"):
    intercept[IllegalArgumentException](SupplyTrack(-1))
    intercept[IllegalArgumentException](SupplyTrack(8))
    intercept[IllegalArgumentException](
      SupplyRefreshBand(InclusiveIntRange(0, 3), -1)
    )
