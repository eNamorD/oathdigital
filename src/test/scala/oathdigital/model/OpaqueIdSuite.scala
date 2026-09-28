package oathdigital.model

import oathdigital.catalog.DefinitionId

class OpaqueIdSuite extends munit.FunSuite:

  test("ids reject the values they always rejected"):
    intercept[IllegalArgumentException](PlayerId(" "))
    intercept[IllegalArgumentException](LineageId(""))
    intercept[IllegalArgumentException](DecisionId(""))
    intercept[IllegalArgumentException](PowerId("Not-A-Power"))
    assertEquals(PowerId.fromValue("Not-A-Power"), None)
    assertEquals(PowerId.fromValue("site.coast").map(_.value), Some("site.coast"))

  test("an id prints as its raw string"):
    assertEquals(PlayerId("p1").toString, "p1")
    assertEquals(s"${PowerId("site.coast")}", "site.coast")

  test("DefinitionId keeps its validation and prints raw"):
    assertEquals(DefinitionId("denizen.coast").value, "denizen.coast")
    intercept[IllegalArgumentException](DefinitionId(" "))
    assertEquals(DefinitionId("x").toString, "x")
