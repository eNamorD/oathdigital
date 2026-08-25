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

  test("starting adviser starts in Discard and requires one card in Keep") {
    val adviserDecision = decision.copy(decisionId = "setup-adviser-0-p1",
      kind = "starting-adviser", prompt = "Choose your starting adviser",
      orderingRequired = false)
    val initial = CardDecisionState.initial(adviserDecision)
    assertEquals(initial.keep, Vector.empty)
    assertEquals(initial.discard, cards)
    assert(!initial.arrangementValid(cards))
    assert(initial.moveToKeep("b").arrangementValid(cards))
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

  test("zone drops move cards in both directions") {
    val initial = CardDecisionState.initial(decision)
    val kept = ServerUiSupport.dropOnKeep(initial, "b")
    assertEquals(kept.keep.map(_.cardId), Vector("b"))
    val returned = ServerUiSupport.dropOnDiscard(kept, "b")
    assertEquals(returned.keep, Vector.empty)
    assertEquals(returned.discard.map(_.cardId), Vector("a", "c", "b"))
    val before = ServerUiSupport.dropBeforeDiscard(kept, "b", "c")
    assertEquals(before.keep, Vector.empty)
    assertEquals(before.discard.map(_.cardId), Vector("a", "b", "c"))
  }

  test("projected keep bounds govern arrangement validity") {
    val bounded = decision.copy(keepMinimum = 0, keepMaximum = 2)
    val empty = CardDecisionState.initial(bounded)
    assert(empty.arrangementValid(cards))
    val two = empty.moveToKeep("a").moveToKeep("b")
    assert(two.arrangementValid(cards))
    assertEquals(two.keep.map(_.cardId), Vector("a", "b"))
    assertEquals(two.moveToKeep("c"), two)
  }

  test("required replacement remains explicit until a legal target is selected") {
    val targets = cards.take(2)
    val required = CardResolution("adviser", Some("face-down"),
      replacementRequired = true, replacementTargets = targets)
    val state = CardDecisionState.initial(decision).moveToKeep("c")
      .copy(stage = CardDecisionStage.Resolve).chooseResolution(required)
    assertEquals(state.selectedReplacement, None)
    assert(!state.resolutionValid)
    assert(state.chooseReplacement("a").resolutionValid)
    assert(!state.chooseReplacement("missing").resolutionValid)

    val optional = CardResolution("discard", None,
      replacementRequired = false, Vector.empty)
    assert(state.chooseResolution(optional).resolutionValid)
  }
}
