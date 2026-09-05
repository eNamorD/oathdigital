package oathdigital.gameplay

class SupplyResourceSuite extends munit.FunSuite {
  test("favor clamp takes the full requested amount when available") {
    assertEquals(SupplyResource.favor(available = 5, requested = 3), 3)
  }
  test("favor clamp takes the available remainder when short") {
    assertEquals(SupplyResource.favor(available = 2, requested = 5), 2)
  }
  test("favor clamp yields zero when nothing is available") {
    assertEquals(SupplyResource.favor(available = 0, requested = 5), 0)
  }
  test("favor clamp yields zero when nothing is requested") {
    assertEquals(SupplyResource.favor(available = 5, requested = 0), 0)
  }
  test("favor clamp guards negative inputs") {
    assertEquals(SupplyResource.favor(available = -1, requested = 5), 0)
    assertEquals(SupplyResource.favor(available = 5, requested = -1), 0)
  }
}
