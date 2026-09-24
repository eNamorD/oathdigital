package oathdigital.model

class GameStartedSuite extends munit.FunSuite {
  private val participant = FirstGameParticipant(
    PlayerId("p1"), LineageId("lineage-1"), PlayerColor.Red)
  private val orders = SetupOrders(
    Vector(participant), PlayerId("p1"), Vector.empty, Vector.empty)
  private val chronicle = Chronicle(
    atlasBox = Vector.empty, worldDeck = Vector.empty, relicDeck = Vector.empty)

  test("GameStarted carries a Chronicle and its resolved deal order") {
    val event = OathEvent.GameStarted(chronicle, orders)
    assertEquals(event.chronicle, chronicle)
    assertEquals(event.orders.firstPlayer, PlayerId("p1"))
  }

  test("Phase.Setup round-trips through its wire key") {
    assertEquals(Phase.fromKey("setup"), Some(Phase.Setup))
    assertEquals(Phase.Setup.key, "setup")
  }
}
