package oathdigital.model

import oathdigital.application.UserId
import oathdigital.catalog.DefinitionId

class OpaqueIdSuite extends munit.FunSuite {
  test("ids construct, extract and expose their value") {
    val PlayerId(player) = PlayerId("p1")
    assertEquals(player, "p1")
    assertEquals(LineageId("l1").value, "l1")
    assertEquals(DecisionId("d1").value, "d1")
    assertEquals(PowerId("site.coast").value, "site.coast")
  }

  test("ids reject the values they always rejected") {
    intercept[IllegalArgumentException](PlayerId(" "))
    intercept[IllegalArgumentException](LineageId(""))
    intercept[IllegalArgumentException](DecisionId(""))
    intercept[IllegalArgumentException](PowerId("Not-A-Power"))
    assertEquals(PowerId.fromValue("Not-A-Power"), None)
    assertEquals(PowerId.fromValue("site.coast").map(_.value), Some("site.coast"))
  }

  test("an id prints as its raw string") {
    assertEquals(PlayerId("p1").toString, "p1")
    assertEquals(s"${PowerId("site.coast")}", "site.coast")
  }

  test("DefinitionId keeps its validation and prints raw") {
    assertEquals(DefinitionId("denizen.coast").value, "denizen.coast")
    intercept[IllegalArgumentException](DefinitionId(" "))
    assertEquals(DefinitionId("x").toString, "x")
  }

  test("UserId wraps any string and prints raw") {
    val UserId(raw) = UserId("u-1")
    assertEquals(raw, "u-1")
    assertEquals(UserId("u-1").toString, "u-1")
  }
}
