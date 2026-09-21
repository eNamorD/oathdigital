package oathdigital.frontend

class CardDecisionStateSuite extends munit.FunSuite {
  private val cards = Vector(
    CardDetails("a", "denizen", "A"),
    CardDetails("b", "denizen", "B"))
  private val decision = PendingCardDecision("setup-adviser-0-p1",
    "starting-adviser", "p1", "Choose your starting adviser", Vector.empty,
    cards, 1, 1, orderingRequired = false, Map.empty)

  test("starting adviser requires exactly one kept card") {
    val initial = CardDecisionState.initial(decision)
    assertEquals(initial.keep, Vector.empty)
    assertEquals(initial.discard, cards)
    assert(!initial.arrangementValid(cards))
    val chosen = initial.moveToKeep("b")
    assertEquals(chosen.keep.map(_.cardId), Vector("b"))
    assert(chosen.arrangementValid(cards))
  }

  test("starting adviser swaps full Keep and rejects incomplete arrangement") {
    val chosen = CardDecisionState.initial(decision).moveToKeep("a")
      .moveToKeep("b")
    assertEquals(chosen.keep.map(_.cardId), Vector("b"))
    assertEquals(chosen.discard.map(_.cardId), Vector("a"))
    val malformed = chosen.copy(partition = chosen.partition.copy(
      contents = chosen.partition.contents.updated("discard", Vector.empty)))
    assert(!malformed.arrangementValid(cards))
  }
}
