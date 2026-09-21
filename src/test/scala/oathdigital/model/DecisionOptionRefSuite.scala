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

  private val slot = DecisionOptionRef.RelicSlot(PlayerId("blue"), 2)
  private val banner =
    DecisionOptionRef.Banner(oathdigital.model.Banner.DarkestSecret)

  test("a relic slot spells itself as a kind and owner-and-index id and parses back") {
    assertEquals((slot.kind, slot.wireId), ("relic-slot", "blue:2"))
    assertEquals(DecisionOptionRef.fromWire(slot.kind, slot.wireId), Some(slot))
  }

  test("a relic slot owner may contain a colon and still round trips") {
    val odd = DecisionOptionRef.RelicSlot(PlayerId("a:b"), 0)
    assertEquals(DecisionOptionRef.fromWire(odd.kind, odd.wireId), Some(odd))
  }

  test("a malformed relic slot is not a reference") {
    Vector("blue", "blue:", ":2", "blue:-1", "blue:x", "blue:1.5").foreach { id =>
      assertEquals(DecisionOptionRef.fromWire("relic-slot", id), None, id)
    }
  }

  test("a banner spells itself as its key and an unknown key is not a reference") {
    assertEquals((banner.kind, banner.wireId), ("banner", "darkest-secret"))
    assertEquals(DecisionOptionRef.fromWire(banner.kind, banner.wireId),
      Some(banner))
    assertEquals(DecisionOptionRef.fromWire("banner", "the-crown"), None)
  }

  test("a relic slot and a banner are presentable from the reference alone") {
    assertEquals(DecisionOption.forRef(slot),
      Some(DecisionOption.RelicSlot(slot)))
    assertEquals(DecisionOption.forRef(banner),
      Some(DecisionOption.Banner(banner)))
  }
}
