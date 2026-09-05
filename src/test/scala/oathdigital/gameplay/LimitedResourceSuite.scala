package oathdigital.gameplay

class LimitedResourceSuite extends munit.FunSuite {
  test("clamp takes the full requested amount when available") {
    assertEquals(LimitedResource.clamp(available = 5, requested = 3), 3)
  }
  test("clamp takes the available remainder when short") {
    assertEquals(LimitedResource.clamp(available = 2, requested = 5), 2)
  }
  test("clamp yields zero when nothing is available") {
    assertEquals(LimitedResource.clamp(available = 0, requested = 5), 0)
  }
  test("clamp yields zero when nothing is requested") {
    assertEquals(LimitedResource.clamp(available = 5, requested = 0), 0)
  }
  test("clamp guards negative inputs") {
    assertEquals(LimitedResource.clamp(available = -1, requested = 5), 0)
    assertEquals(LimitedResource.clamp(available = 5, requested = -1), 0)
  }
}
