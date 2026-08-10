package oathdigital.frontend

class CardDecisionStateSuite extends munit.FunSuite {
  private val cards = Vector(
    CardDetails("a", "denizen", "A"),
    CardDetails("b", "denizen", "B"),
    CardDetails("c", "vision", "C"))
  private val decision = PendingCardDecision("search-8", "search", "p1",
    "Resolve Search", Vector("Remaining cards are discarded from left to right."),
    cards, 1, 1, orderingRequired = true, Map.empty)

  test("Search starts with every card in Discard and no kept card") {
    val state = CardDecisionState.initial(decision)
    assertEquals(state.keep, Vector.empty)
    assertEquals(state.discard, cards)
    assert(!state.arrangementValid(cards))
  }

  test("exactly one keep and complete unique discard order is required") {
    val state = CardDecisionState.initial(decision).moveToKeep("b")
    assert(state.arrangementValid(cards))
    assertEquals(state.keep.map(_.cardId), Vector("b"))
    assertEquals(state.move("a", 1).discard.map(_.cardId), Vector("c", "a"))
    assert(!state.copy(discard = state.discard :+ state.discard.head)
      .arrangementValid(cards))
  }

  test("moving kept card back and drag ordering preserve local-only state") {
    val state = CardDecisionState.initial(decision).moveToKeep("a")
      .arrangeDrop("c", Some("b"))
    assertEquals(state.discard.map(_.cardId), Vector("c", "b"))
    assertEquals(state.moveToDiscard("a").discard.map(_.cardId),
      Vector("c", "b", "a"))
  }
}
