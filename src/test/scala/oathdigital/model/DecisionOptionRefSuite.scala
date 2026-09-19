package oathdigital.model

class DecisionOptionRefSuite extends munit.FunSuite {
  private val hall = DecisionOptionRef.Edifice(EdificeId("E16"))

  test("an edifice reference spells itself as a kind and id pair and parses back") {
    assertEquals((hall.kind, hall.wireId), ("edifice", "E16"))
    assertEquals(DecisionOptionRef.fromWire(hall.kind, hall.wireId), Some(hall))
  }

  test("a blank edifice id is not a reference") {
    assertEquals(DecisionOptionRef.fromWire("edifice", "  "), None)
  }

  test("an edifice reference is presentable from the reference alone") {
    assertEquals(DecisionOption.forRef(hall), Some(DecisionOption.Edifice(hall)))
  }
}
