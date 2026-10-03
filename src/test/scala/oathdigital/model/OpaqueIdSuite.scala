package oathdigital.model

class OpaqueIdSuite extends munit.FunSuite:

  test("blank ids and a malformed power id are rejected; " +
      "PowerId.fromValue answers None instead of throwing"):
    intercept[IllegalArgumentException](PlayerId(" "))
    intercept[IllegalArgumentException](LineageId(""))
    intercept[IllegalArgumentException](DecisionId(""))
    // A power id is two or more dot-separated lowercase segments;
    // "Not-A-Power" has capitals and no dot, "site.coast" is well formed.
    intercept[IllegalArgumentException](PowerId("Not-A-Power"))
    assertEquals(PowerId.fromValue("Not-A-Power"), None)
    assertEquals(PowerId.fromValue("site.coast").map(_.value), Some("site.coast"))

  test("an id prints as its raw string"):
    assertEquals(PlayerId("p1").toString, "p1")
    assertEquals(s"${PowerId("site.coast")}", "site.coast")

