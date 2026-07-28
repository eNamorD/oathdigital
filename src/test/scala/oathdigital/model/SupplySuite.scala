package oathdigital.model

class SupplySuite extends munit.FunSuite {
  private val exileRules = SupplyRules(
    maximum = Supply.full,
    refreshBands = Vector(
      SupplyRefreshBand(InclusiveIntRange(9, Int.MaxValue), Supply(6)),
      SupplyRefreshBand(InclusiveIntRange(4, 8), Supply(5)),
      SupplyRefreshBand(InclusiveIntRange(0, 3), Supply(4))
    )
  )

  private val chancellorRules = SupplyRules(
    maximum = Supply.full,
    refreshBands = Vector(
      SupplyRefreshBand(InclusiveIntRange(18, Int.MaxValue), Supply(6)),
      SupplyRefreshBand(InclusiveIntRange(11, 17), Supply(5)),
      SupplyRefreshBand(InclusiveIntRange(4, 10), Supply(4)),
      SupplyRefreshBand(InclusiveIntRange(0, 3), Supply(3))
    )
  )

  test("an Exile with nine or more banked warbands refreshes to six") {
    assertEquals(exileRules.refresh(9, Supply.empty), Some(Supply(6)))
    assertEquals(exileRules.refresh(20, Supply.empty), Some(Supply(6)))
  }

  test("saved Supply moves the effective marker left and caps at seven") {
    assertEquals(exileRules.refresh(9, Supply(1)), Some(Supply(7)))
    assertEquals(exileRules.refresh(9, Supply(4)), Some(Supply(7)))
  }

  test("the Chancellor uses the distinct Imperial refresh bands") {
    assertEquals(
      chancellorRules.refresh(18, Supply.empty),
      Some(Supply(6))
    )
    assertEquals(
      chancellorRules.refresh(3, Supply.empty),
      Some(Supply(3))
    )
  }
}
