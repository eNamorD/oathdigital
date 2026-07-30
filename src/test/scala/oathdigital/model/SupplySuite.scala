package oathdigital.model

class SupplySuite extends munit.FunSuite {
  private val exileRules = SupplyRules(
    maximum = SupplyTrack.Maximum,
    refreshBands = Vector(
      SupplyRefreshBand(InclusiveIntRange(9, Int.MaxValue), 6),
      SupplyRefreshBand(InclusiveIntRange(4, 8), 5),
      SupplyRefreshBand(InclusiveIntRange(0, 3), 4)
    )
  )

  private val chancellorRules = SupplyRules(
    maximum = SupplyTrack.Maximum,
    refreshBands = Vector(
      SupplyRefreshBand(InclusiveIntRange(18, Int.MaxValue), 6),
      SupplyRefreshBand(InclusiveIntRange(11, 17), 5),
      SupplyRefreshBand(InclusiveIntRange(4, 10), 4),
      SupplyRefreshBand(InclusiveIntRange(0, 3), 3)
    )
  )

  test("an Exile with nine or more banked warbands refreshes to six") {
    assertEquals(exileRules.refresh(9, 0), Some(SupplyTrack(6)))
    assertEquals(exileRules.refresh(20, 0), Some(SupplyTrack(6)))
  }

  test("saved Supply moves the effective marker left and caps at seven") {
    assertEquals(exileRules.refresh(9, 1), Some(SupplyTrack(7)))
    assertEquals(exileRules.refresh(9, 4), Some(SupplyTrack(7)))
  }

  test("the Chancellor uses the distinct Imperial refresh bands") {
    assertEquals(
      chancellorRules.refresh(18, 0),
      Some(SupplyTrack(6))
    )
    assertEquals(
      chancellorRules.refresh(3, 0),
      Some(SupplyTrack(3))
    )
  }

  test("numeric Supply rejects values outside the physical track") {
    intercept[IllegalArgumentException](SupplyTrack(-1))
    intercept[IllegalArgumentException](SupplyTrack(8))
    intercept[IllegalArgumentException](
      SupplyRefreshBand(InclusiveIntRange(0, 3), -1)
    )
  }
}
