package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionOptionWire,
  GameIntent => Intent}

/** The board draft and the option states drawn from it, with no DOM: a
  * choose-one replaces its pick, a choose-many toggles within its maximum
  * and its tallies' limits, and locked and unavailable options never move.
  */
class BoardSelectionDraftSuite extends munit.FunSuite:
  private val context = BoardSelectionContext("game", "red", 9L)
  private def site(id: String) = DecisionOptionState("site", id, s"Site $id")
  private val origin = site("origin").copy(state = "locked")
  private val wolves = site("wolves").copy(state = "unavailable",
    reason = Some("Not ruled by the defender"))
  private val needed = DecisionTallyState("Warbands needed", "warbands",
    Vector(DecisionTallyValueState("site", "origin", 1),
      DecisionTallyValueState("site", "a", 2),
      DecisionTallyValueState("site", "b", 2)), Some(4))
  private val many = DecisionQueryState.ChooseMany(
    Vector(origin, site("a"), site("b"), site("c"), wolves), 0, 3,
    Some("Targets"), tallies = Vector(needed))
  private val one = DecisionQueryState.ChooseOne(
    Vector(site("a"), site("b"), wolves), Some("Where?"))
  private def decision(query: DecisionQueryState) =
    WalkerDecisionState("campaign", "campaign.targets", "decide",
      query = Some(query), presentation = "board")
  private def draft(query: DecisionQueryState, keys: String*) =
    BoardSelectionDraft(context, "campaign.targets", query, keys.toVector)

  test("a choose-one click replaces the pick, and a second click clears it"):
    val picked = draft(one).click(site("a")).click(site("b"))
    assertEquals(picked.keys, Vector("site:b"))
    assertEquals(picked.click(site("b")).keys, Vector.empty)

  test("a choose-many click toggles, in the order picked"):
    val picked = draft(many).click(site("c")).click(site("a"))
    assertEquals(picked.keys, Vector("site:c", "site:a"))
    assertEquals(picked.click(site("c")).keys, Vector("site:a"))

  test("locked and unavailable options never move"):
    assertEquals(draft(many).click(origin).keys, Vector.empty)
    assertEquals(draft(many).click(wolves).keys, Vector.empty)
    assertEquals(draft(one).click(wolves).keys, Vector.empty)

  test("a tally's limit blocks the option that would pass it, counting " +
      "locked options"):
    // Locked origin 1, a 2: 3 of 4. Adding b would make 5.
    val picked = draft(many, "site:a")
    assertEquals(BoardOptionViews.blockedBy(many, picked.keys, site("b")),
      Some("Needs 5 warbands; limit 4"))
    assertEquals(picked.click(site("b")).keys, Vector("site:a"))
    assertEquals(BoardOptionViews.total(many, needed, picked.keys), 3)

  test("the maximum blocks a further pick"):
    val full = draft(many.copy(maxOptions = 1), "site:c")
    assertEquals(BoardOptionViews.blockedBy(full.query, full.keys, site("a")),
      Some("Choose at most 1."))

  test("each option's view: locked, unavailable with its reason, selected, " +
      "and blocked by a limit"):
    val views = BoardOptionViews.of(decision(many), many,
      Some(draft(many, "site:a")))
    assertEquals(views("site:origin").state, OptionState.Locked)
    assertEquals(views("site:wolves"), OptionView(wolves,
      OptionState.Unavailable, Some("Not ruled by the defender")))
    assertEquals(views("site:a").state, OptionState.Selected)
    assertEquals(views("site:b"), OptionView(site("b"),
      OptionState.Unavailable, Some("Needs 5 warbands; limit 4")))
    assertEquals(views("site:c").state, OptionState.Available)

  test("a choose-one's answers so far show locked"):
    val played = DecisionOptionState("denizen", "wrestlers", "Wrestlers")
    val views = BoardOptionViews.of(decision(one).copy(
      answeredOptions = Vector(played)), one, None)
    assertEquals(views("denizen:wrestlers").state, OptionState.Locked)

  test("a draft of another decision marks nothing selected"):
    val other = draft(many, "site:a").copy(decisionId = "other")
    assertEquals(BoardOptionViews.of(decision(many), many, Some(other))(
      "site:a").state, OptionState.Available)

  test("Confirm waits for the minimum and submits the picks in order"):
    val needsTwo = many.copy(minOptions = 2)
    assertEquals(draft(needsTwo, "site:c").blocker, Some("Choose at least 2."))
    assertEquals(draft(needsTwo, "site:c", "site:a").command,
      Some(Intent.ResolveWalker("campaign.targets",
        DecisionAnswerWire.ChooseManyWire(Vector(
          DecisionOptionWire("site", "c"), DecisionOptionWire("site", "a"))))))

  test("an optional choose-many confirms the empty selection"):
    assertEquals(draft(many).command, Some(Intent.ResolveWalker(
      "campaign.targets", DecisionAnswerWire.ChooseManyWire(Vector.empty))))

  test("a choose-one confirms its one pick and nothing before it"):
    assertEquals(draft(one).blocker, Some("Choose an option first."))
    assertEquals(draft(one, "site:b").command, Some(Intent.ResolveWalker(
      "campaign.targets", DecisionAnswerWire.ChooseOneWire("site", "b"))))

  test("a draft is kept while the same question is asked, and dropped " +
      "otherwise"):
    val kept = draft(many, "site:a")
    assertEquals(BoardSelectionDraft.reconcile(Some(kept), context,
      Some(decision(many))), Some(kept))
    assertEquals(BoardSelectionDraft.reconcile(Some(kept), context,
      Some(decision(many.copy(maxOptions = 2)))), None)
    assertEquals(BoardSelectionDraft.reconcile(Some(kept),
      context.copy(sequence = 10L), Some(decision(many))), None)
    assertEquals(BoardSelectionDraft.reconcile(Some(kept), context, None),
      None)

  test("a click on an empty or stale draft starts a fresh one"):
    val stale = draft(one, "site:a").copy(decisionId = "other")
    assertEquals(BoardSelectionDraft.click(Some(stale), context,
      "campaign.targets", many, site("c")).keys, Vector("site:c"))
    assertEquals(BoardSelectionDraft.click(None, context,
      "campaign.targets", many, site("c")).keys, Vector("site:c"))
