package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionPlacementWire}

/** Task 5: the two-zone move/drag interaction, with no idea what it is
  * partitioning. Nothing below names a card, a denizen or a resource -- the
  * sections carry their own keys, labels and minima, and the confirmation
  * predicate is computed from those minima alone.
  */
class PartitionDecisionStateSuite extends munit.FunSuite {
  private val sections = Vector(
    PartitionSection("left", "Left", 2),
    PartitionSection("right", "Right", 1))
  private val items = Vector("a", "b", "c")
  private val opening = PartitionDecisionState.filled(sections, items)

  test("the opening draft fills each section to its declared minimum in " +
      "declared order") {
    assertEquals(opening.itemsIn("left"), Vector("a", "b"))
    assertEquals(opening.itemsIn("right"), Vector("c"))
    assertEquals(opening.placements,
      Vector("a" -> "left", "b" -> "left", "c" -> "right"))
    assert(opening.canConfirm)
  }

  test("an item moved into a section leaves the one it came from") {
    val moved = opening.moveTo("a", "right")
    assertEquals(moved.itemsIn("left"), Vector("b"))
    assertEquals(moved.itemsIn("right"), Vector("c", "a"))
    assertEquals(moved.sectionOf("a"), Some("right"))
    // A section the interaction never declared is ignored rather than
    // recorded, so a stale click cannot build an unanswerable draft.
    assertEquals(moved.moveTo("a", "nowhere"), moved)
    // An item is not in the business of moving to where it already is.
    assertEquals(moved.moveTo("a", "right"), moved)
    assertEquals(moved.moveTo("missing", "left"), moved)
  }

  test("a minimum not yet met blocks confirmation") {
    val short = opening.moveTo("c", "left")
    assertEquals(short.itemsIn("right"), Vector.empty)
    assert(!short.minimaMet)
    assert(!short.canConfirm)
    assert(short.moveTo("b", "right").canConfirm)
  }

  test("every item must be placed exactly once before confirmation") {
    val unplaced = opening.copy(
      contents = opening.contents.updated("left", Vector("a")))
    assert(!unplaced.complete)
    assert(!unplaced.canConfirm)
    val duplicated = opening.copy(
      contents = opening.contents.updated("right", Vector("c", "a")))
    assert(!duplicated.complete)
    assert(!duplicated.canConfirm)
  }

  test("a single-slot section swaps its occupant back, a wider full one " +
      "refuses the move") {
    val single = PartitionDecisionState.allIn(Vector(
      PartitionSection("keep", "Keep", 1, Some(1)),
      PartitionSection("rest", "Rest", 0)), items, "rest")
    val kept = single.moveTo("a", "keep")
    assertEquals(kept.itemsIn("keep"), Vector("a"))
    assertEquals(kept.itemsIn("rest"), Vector("b", "c"))
    val swapped = kept.moveTo("b", "keep")
    assertEquals(swapped.itemsIn("keep"), Vector("b"))
    assertEquals(swapped.itemsIn("rest"), Vector("a", "c"))

    val pair = PartitionDecisionState.allIn(Vector(
      PartitionSection("keep", "Keep", 0, Some(2)),
      PartitionSection("rest", "Rest", 0)), items, "rest")
    val full = pair.moveTo("a", "keep").moveTo("b", "keep")
    assertEquals(full.itemsIn("keep"), Vector("a", "b"))
    assertEquals(full.moveTo("c", "keep"), full)
  }

  test("ordering within a section survives placement and shifting") {
    val listed = PartitionDecisionState.allIn(
      Vector(PartitionSection("all", "All", 0)), items, "all")
    assertEquals(listed.placeBefore("c", "all", Some("b")).itemsIn("all"),
      Vector("a", "c", "b"))
    // An unknown anchor appends rather than refusing.
    assertEquals(listed.placeBefore("a", "all", Some("gone")).itemsIn("all"),
      Vector("b", "c", "a"))
    assertEquals(listed.placeBefore("a", "all", None).itemsIn("all"),
      Vector("b", "c", "a"))
    assertEquals(listed.shift("a", 1).itemsIn("all"), Vector("b", "a", "c"))
    assertEquals(listed.shift("a", -1), listed)
    assertEquals(listed.shift("c", 4).itemsIn("all"), Vector("a", "b", "c"))
  }

  test("submitted placements follow the player's within-section order") {
    val draft = PartitionDecisionState.allIn(Vector(
      PartitionSection("keep", "Keep", 1, Some(1)),
      PartitionSection("discard", "Discard", 0)), items, "discard")
      .moveTo("a", "keep")
      .placeBefore("c", "discard", Some("b"))
    assertEquals(draft.placements,
      Vector("a" -> "keep", "c" -> "discard", "b" -> "discard"))
  }

  test("filled draft places slack outside a full section") {
    val draft = PartitionDecisionState.filled(Vector(
      PartitionSection("keep", "Keep", 1, Some(1)),
      PartitionSection("discard", "Discard", 0)), items)
    assertEquals(draft.itemsIn("keep"), Vector("a"))
    assertEquals(draft.itemsIn("discard"), Vector("b", "c"))
    assert(draft.canConfirm)
  }

  /** The walker half: a parked partition decision adapted into the same
    * interaction, answered generically.
    */
  private val query = DecisionQueryState("partition",
    Vector("1", "2", "3").map(id =>
      DecisionOptionState("denizen", s"denizen:$id", s"Denizen $id")),
    Vector(DecisionSectionState("pay-favor", "Pay Favor", 2),
      DecisionSectionState("pay-secret", "Pay Secret", 1)))
  private val parked =
    WalkerDecisionState("forge", "forge-9", "decide", query = Some(query))
  private val context = BoardSelectionContext("game", "red", 9)

  test("a confirmed draft names each projected option exactly once in its " +
      "own section") {
    val draft = WalkerPartitionDraft.reconcile(None, context, Some(parked)).get
    assertEquals(draft.optionsIn("pay-favor").map(_.id),
      Vector("denizen:1", "denizen:2"))
    assertEquals(draft.optionsIn("pay-secret").map(_.id), Vector("denizen:3"))
    assertEquals(draft.command("red"), Some(GameCommand.ResolveWalker(
      "red", "forge-9", DecisionAnswerWire.PartitionWire(Vector(
        DecisionPlacementWire("denizen", "denizen:1", "pay-favor"),
        DecisionPlacementWire("denizen", "denizen:2", "pay-favor"),
        DecisionPlacementWire("denizen", "denizen:3", "pay-secret"))))))
  }

  test("a draft below a projected minimum refuses to answer at all") {
    val draft = WalkerPartitionDraft.reconcile(None, context, Some(parked)).get
    val short = draft.move(WalkerPartitionDraft.itemId(query.options(2)),
      "pay-favor")
    assert(!short.canConfirm)
    assertEquals(short.command("red"), None)
    val repaired = short.move(WalkerPartitionDraft.itemId(query.options.head),
      "pay-secret")
    assert(repaired.canConfirm)
    assertEquals(repaired.command("red"), Some(GameCommand.ResolveWalker(
      "red", "forge-9", DecisionAnswerWire.PartitionWire(Vector(
        DecisionPlacementWire("denizen", "denizen:2", "pay-favor"),
        DecisionPlacementWire("denizen", "denizen:3", "pay-favor"),
        DecisionPlacementWire("denizen", "denizen:1", "pay-secret"))))))
  }
}
