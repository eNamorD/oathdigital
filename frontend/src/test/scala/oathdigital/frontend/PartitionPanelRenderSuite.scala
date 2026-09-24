package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionPlacementWire,
  GameIntent => Intent}
import org.scalajs.dom
import scala.scalajs.js

/** Task 5, at the DOM. `PartitionDecisionStateSuite` proves the interaction
  * state; this drives the controls the panel actually builds, so renderer
  * wiring cannot break while a state-level test stays green: the zones and
  * options are read out of the rendered tree, the moves go through the
  * accessible button and through a real drop event, the confirm button's
  * disabled state is read off the element, and the submitted command is
  * whatever a click on it produced.
  *
  * Runs under jsdom (`Test / jsEnv` in `build.sbt`), which is why
  * `dom.document` exists here at all.
  */
class PartitionPanelRenderSuite extends munit.FunSuite {
  /** Task 5b: the heading and the confirm label are the query's own, the
    * way `ForgeProcedure` now declares them -- the panel no longer reads
    * `decision.action` to decide what to call itself.
    */
  private val query = DecisionQueryState("partition",
    Vector("1", "2", "3").map(id =>
      DecisionOptionState("denizen", s"denizen:$id", s"Denizen $id")),
    Vector(DecisionSectionState("pay-favor", "Pay Favor", 2),
      DecisionSectionState("pay-secret", "Pay Secret", 1)),
    heading = Some("Forge a relic"),
    confirmLabel = Some("Complete Forge"))

  private val parked =
    WalkerDecisionState("forge", "forge-9", "decide", query = Some(query))

  private def projectionWith(decision: Option[WalkerDecisionState])
      : GameProjection = GameProjection("game", 9L, "act", Some("red"),
    Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
    completed = false, walkerDecision = decision)

  private val presentation = ServerUiSupport.ViewerPresentation(
    showGameplayControls = true, None, None)

  /** Renders the panel into a detached container and hands back both, so a
    * test can read the tree and then re-render it after a click the way the
    * real `rerender()` would.
    */
  private def render(ui: RecordingView, canControl: Boolean = true,
      decision: Option[WalkerDecisionState] = Some(parked)): dom.Element = {
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderPartitionPanel(projectionWith(decision),
      presentation, canControl, panel, ui)
    panel
  }

  private def opened(): RecordingView = {
    val ui = new RecordingView("game", "red")
    ui.currentWalkerPartition = WalkerPartitionDraft.reconcile(None,
      BoardSelectionContext("game", "red", 9), Some(parked))
    ui
  }

  private def all(root: dom.Element, selector: String): Vector[dom.Element] =
    root.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  private def one(root: dom.Element, selector: String): dom.Element = {
    val found = all(root, selector)
    assertEquals(found.size, 1, s"expected exactly one $selector")
    found.head
  }

  private def optionLabelsIn(panel: dom.Element, sectionKey: String)
      : Vector[String] =
    all(one(panel, s"""[data-section-key="$sectionKey"]"""),
      ".decision-option").map(_.getAttribute("aria-label"))

  private def confirm(panel: dom.Element): dom.html.Button =
    one(panel, ".partition-confirm").asInstanceOf[dom.html.Button]

  private def click(element: dom.Element): Unit =
    element.asInstanceOf[dom.html.Element].click()

  test("the panel renders one zone per projected section, holding the " +
      "options placed there") {
    val panel = render(opened())
    assertEquals(one(panel, "h2").textContent, "Forge a relic")
    assertEquals(one(panel, ".partition-instruction").textContent,
      "Assign every option: Pay Favor (2), Pay Secret (1).")
    val zones = all(panel, ".partition-zone")
    assertEquals(zones.map(_.getAttribute("data-section-key")),
      Vector("pay-favor", "pay-secret"))
    assertEquals(zones.map(zone => one(zone, "h3").textContent),
      Vector("Pay Favor", "Pay Secret"))
    assertEquals(zones.map(zone =>
      one(zone, ".decision-zone-helper").textContent),
      Vector("At least 2.", "At least 1."))
    assertEquals(optionLabelsIn(panel, "pay-favor"),
      Vector("Denizen 1", "Denizen 2"))
    assertEquals(optionLabelsIn(panel, "pay-secret"), Vector("Denizen 3"))
    assertEquals(all(panel, ".decision-option")
      .map(_.getAttribute("data-option-id")),
      Vector("denizen:denizen:1", "denizen:denizen:2", "denizen:denizen:3"))
  }

  /** The options sit in their own row inside the zone. A zone that held them
    * directly measured as wide as its heading plus every card laid end to
    * end, so a keep-one zone claimed a full column it had no use for.
    */
  test("a zone's options live in a row of their own") {
    val zone = all(render(opened()), ".partition-zone").head
    val row = one(zone, ".partition-options")
    assertEquals(row.parentNode, zone)
    assertEquals(all(row, ".decision-option").size, 2)
    assertEquals(all(zone, ":scope > .decision-option"), Vector.empty)
  }

  /** Order within a zone is part of the answer, but only a zone that takes
    * whatever is left over -- no minimum, no cap -- is one whose order the
    * player needs explaining. Keyed on that shape rather than on the word
    * "Discard", which would stop matching the day the section is renamed.
    */
  private val keepDiscard = DecisionQueryState("partition",
    Vector("1", "2", "3").map(id =>
      DecisionOptionState("denizen", s"denizen:$id", s"Denizen $id")),
    Vector(DecisionSectionState("keep", "Keep", 1, Some(1)),
      DecisionSectionState("discard", "Discard", 0)),
    heading = Some("Choose your starting adviser"),
    confirmLabel = Some("Confirm Adviser"))

  private def picking(): RecordingView = {
    val ui = new RecordingView("game", "red")
    ui.currentWalkerPartition = WalkerPartitionDraft.reconcile(None,
      BoardSelectionContext("game", "red", 9),
      Some(WalkerDecisionState("setup", "setup-1", "decide",
        query = Some(keepDiscard))))
    ui
  }

  private def pickPanel(ui: RecordingView): dom.Element = {
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderPartitionPanel(
      projectionWith(Some(WalkerDecisionState("setup", "setup-1", "decide",
        query = Some(keepDiscard)))),
      presentation, canControl = true, panel, ui)
    panel
  }

  test("a leftover zone holding several options says its order counts") {
    val zone = one(pickPanel(picking()), """[data-section-key="discard"]""")
    assertEquals(one(zone, ".decision-zone-order").textContent,
      "Discard happens in the order shown.")
  }

  test("the capped zone says nothing about order") {
    assertEquals(all(one(pickPanel(picking()),
      """[data-section-key="keep"]"""), ".decision-zone-order"), Vector.empty)
  }

  /** One card cannot be in an order, so the sentence would be noise. A pick
    * of two leaves exactly one card behind, whichever one is kept.
    */
  test("a zone holding one option says nothing about order") {
    val twoCards = keepDiscard.copy(options = keepDiscard.options.take(2))
    val decision = WalkerDecisionState("setup", "setup-1", "decide",
      query = Some(twoCards))
    val ui = new RecordingView("game", "red")
    ui.currentWalkerPartition = WalkerPartitionDraft.reconcile(None,
      BoardSelectionContext("game", "red", 9), Some(decision))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderPartitionPanel(projectionWith(Some(decision)),
      presentation, canControl = true, panel, ui)
    assertEquals(all(panel, ".decision-zone-order"), Vector.empty)
  }

  /** Forge pays into zones that each carry a minimum, so none of them is a
    * leftover zone and none claims an order.
    */
  test("a partition of payments claims no order anywhere") {
    assertEquals(all(render(opened()), ".decision-zone-order"), Vector.empty)
  }

  test("the confirm button names the card the one-card zone holds") {
    assertEquals(confirm(pickPanel(picking())).textContent, "Keep Denizen 1")
  }

  /** With the slot empty or the query declaring no single-slot section, the
    * query's own label is what the button says.
    */
  test("without a filled single slot the query's own label stands") {
    val ui = picking()
    ui.currentWalkerPartition = ui.currentWalkerPartition
      .map(_.move("denizen:denizen:1", "discard"))
    assertEquals(confirm(pickPanel(ui)).textContent, "Confirm Adviser")
    assertEquals(confirm(render(opened())).textContent, "Complete Forge")
  }

  test("the accessible move button moves one option to the other zone") {
    val ui = opened()
    val panel = render(ui)
    val move = one(panel,
      """[data-option-id="denizen:denizen:1"] [aria-label=""" +
        """"Move Denizen 1 to Pay Secret"]""")
    assertEquals(move.textContent, "Pay Secret")
    click(move)
    assertEquals(ui.rerenders, 1)
    // The draft the panel will be re-rendered from has actually moved.
    val moved = render(ui)
    assertEquals(optionLabelsIn(moved, "pay-favor"), Vector("Denizen 2"))
    assertEquals(optionLabelsIn(moved, "pay-secret"),
      Vector("Denizen 3", "Denizen 1"))
  }

  /** Order inside a zone is an answer, not a display detail: a discard zone
    * is discarded in the order it is left in.
    */
  test("the reorder buttons slide an option within its zone") {
    val ui = opened()
    val later = one(render(ui),
      """[data-option-id="denizen:denizen:1"] .move-later""")
    assertEquals(later.getAttribute("aria-label"),
      "Move Denizen 1 later in Pay Favor")
    click(later)
    assertEquals(optionLabelsIn(render(ui), "pay-favor"),
      Vector("Denizen 2", "Denizen 1"))
    click(one(render(ui),
      """[data-option-id="denizen:denizen:1"] .move-earlier"""))
    assertEquals(optionLabelsIn(render(ui), "pay-favor"),
      Vector("Denizen 1", "Denizen 2"))
  }

  /** Disabled rather than absent, so moving an option never reflows the row
    * out from under the pointer that is working it.
    */
  test("an option at the end of its zone keeps a disabled reorder button") {
    val panel = render(opened())
    def enabled(id: String, cls: String): Boolean = !one(panel,
      s"""[data-option-id="$id"] .$cls""").asInstanceOf[dom.html.Button].disabled
    assert(!enabled("denizen:denizen:1", "move-earlier"))
    assert(enabled("denizen:denizen:1", "move-later"))
    assert(enabled("denizen:denizen:2", "move-earlier"))
    assert(!enabled("denizen:denizen:2", "move-later"))
    // The only option in its zone can go neither way.
    assert(!enabled("denizen:denizen:3", "move-earlier"))
    assert(!enabled("denizen:denizen:3", "move-later"))
  }

  test("dropping an option on another places it before that one") {
    val ui = opened()
    val panel = render(ui)
    val dragged = dragStartPayload(one(panel,
      """[data-option-id="denizen:denizen:3"]"""))
    drop(one(panel, """[data-option-id="denizen:denizen:1"]"""), dragged)
    assertEquals(optionLabelsIn(render(ui), "pay-favor"),
      Vector("Denizen 3", "Denizen 1", "Denizen 2"))
    assertEquals(optionLabelsIn(render(ui), "pay-secret"), Vector.empty)
  }

  test("dropping a dragged option on a zone moves it there") {
    val ui = opened()
    val panel = render(ui)
    // Whatever `dragstart` puts on the transfer is exactly what the drop
    // reads back, so the two halves of the drag are tested together.
    val dragged = dragStartPayload(one(panel,
      """[data-option-id="denizen:denizen:3"]"""))
    assertEquals(dragged, "denizen:denizen:3")
    drop(one(panel, """[data-section-key="pay-favor"]"""), dragged)
    assertEquals(ui.rerenders, 1)
    val moved = render(ui)
    assertEquals(optionLabelsIn(moved, "pay-favor"),
      Vector("Denizen 1", "Denizen 2", "Denizen 3"))
    assertEquals(optionLabelsIn(moved, "pay-secret"), Vector.empty)
  }

  test("confirmation is refused until every projected minimum is met") {
    val ui = opened()
    assert(!confirm(render(ui)).disabled)
    // Emptying the secret zone leaves it below its projected minimum.
    drop(one(render(ui), """[data-section-key="pay-favor"]"""),
      "denizen:denizen:3")
    val short = render(ui)
    assert(confirm(short).disabled)
    click(confirm(short))
    assertEquals(ui.submitted, Vector.empty)
    // Putting one back satisfies it again.
    drop(one(render(ui), """[data-section-key="pay-secret"]"""),
      "denizen:denizen:1")
    assert(!confirm(render(ui)).disabled)
  }

  test("a player who cannot control the game gets a disabled confirm") {
    val ui = opened()
    val panel = render(ui, canControl = false)
    assert(confirm(panel).disabled)
    click(confirm(panel))
    assertEquals(ui.submitted, Vector.empty)
  }

  test("clicking confirm submits every option in the zone it was left in") {
    val ui = opened()
    // Swap the first and last options, which keeps both minima met.
    drop(one(render(ui), """[data-section-key="pay-secret"]"""),
      "denizen:denizen:1")
    drop(one(render(ui), """[data-section-key="pay-favor"]"""),
      "denizen:denizen:3")
    val arranged = render(ui)
    assertEquals(optionLabelsIn(arranged, "pay-favor"),
      Vector("Denizen 2", "Denizen 3"))
    assertEquals(optionLabelsIn(arranged, "pay-secret"), Vector("Denizen 1"))
    assertEquals(confirm(arranged).textContent, "Complete Forge")
    click(confirm(arranged))
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker("forge-9",
      DecisionAnswerWire.PartitionWire(Vector(
        DecisionPlacementWire("denizen", "denizen:2", "pay-favor"),
        DecisionPlacementWire("denizen", "denizen:3", "pay-favor"),
        DecisionPlacementWire("denizen", "denizen:1", "pay-secret"))))))
  }

  /** The copy is read from the query and from nowhere else, so these two
    * cases are the whole of Task 5b at the DOM.
    *
    * The first proves the panel renders what the query declares rather than
    * what it recognises: the action stays `"forge"` and the copy changes,
    * which the deleted `action == "forge"` branch could not have done. The
    * second proves the generic fallback, which is what any decision that
    * declares no copy gets -- the panel must still be usable, just
    * untitled.
    */
  test("the panel titles itself from the query, not from the action") {
    val retitled = query.copy(heading = Some("Pay for the relic"),
      confirmLabel = Some("Pay"))
    val panel = render(opened(),
      decision = Some(parked.copy(query = Some(retitled))))
    assertEquals(one(panel, "h2").textContent, "Pay for the relic")
    assertEquals(confirm(panel).textContent, "Pay")
  }

  test("a partition query declaring no copy falls back to generic copy") {
    val bare = query.copy(heading = None, confirmLabel = None)
    val panel = render(opened(),
      decision = Some(parked.copy(query = Some(bare))))
    assertEquals(one(panel, "h2").textContent, "Resolve decision")
    assertEquals(confirm(panel).textContent, "Confirm")
    // Untitled, not unusable: the zones and the options are still there.
    assertEquals(all(panel, ".partition-zone").size, 2)
    assertEquals(all(panel, ".decision-option").size, 3)
  }

  test("a park with no partition query renders no controls at all") {
    val ui = opened()
    assertEquals(all(render(ui, decision = None), ".partition-zone"),
      Vector.empty)
    val suppressed = Some(parked.copy(query = None))
    assertEquals(all(render(ui, decision = suppressed), ".partition-zone"),
      Vector.empty)
    val chooseOne = Some(WalkerDecisionState("recover", "recover.choice",
      "decide", query = Some(DecisionQueryState("choose-one",
        Vector(DecisionOptionState("button", "stop", "Stop"))))))
    assertEquals(all(render(ui, decision = chooseOne), ".partition-zone"),
      Vector.empty)
  }

  /** Fires `dragstart` and returns what the handler wrote to the transfer.
    * jsdom implements neither `DragEvent` nor `DataTransfer`, so the event
    * carries a recording stand-in for the one property the handler reads.
    */
  private def dragStartPayload(node: dom.Element): String = {
    var written = ""
    val transfer = js.Dynamic.literal(
      setData = (_: String, value: String) => written = value,
      getData = (_: String) => written)
    node.dispatchEvent(transferEvent("dragstart", transfer))
    written
  }

  private def drop(zone: dom.Element, item: String): Unit = {
    val transfer = js.Dynamic.literal(getData = (_: String) => item)
    zone.dispatchEvent(transferEvent("drop", transfer))
  }

  private def transferEvent(name: String, transfer: js.Dynamic): dom.Event = {
    val event = js.Dynamic.newInstance(js.Dynamic.global.Event)(name,
      js.Dynamic.literal(bubbles = true, cancelable = true))
    event.updateDynamic("dataTransfer")(transfer)
    event.asInstanceOf[dom.Event]
  }

  test("a decision option is not a focus stop; the move buttons are the keyboard path") {
    val panel = render(opened())
    val options = all(panel, ".decision-option")
    assert(options.nonEmpty)
    options.foreach(option =>
      assertEquals(option.getAttribute("tabindex"), null,
        "a decision-option has no keydown handler, so a tab stop here is dead"))
    assert(all(panel, ".move-option").nonEmpty)
    all(panel, ".move-option").foreach(move =>
      assert(move.getAttribute("aria-label").startsWith("Move ")))
  }

  test("a drag on an option swallows the click that follows; a plain press does not") {
    val node = dom.document.createElement("div").asInstanceOf[dom.html.Element]
    dom.document.body.appendChild(node)
    var clicks = 0
    node.addEventListener("click", (_: dom.Event) => clicks += 1)
    DragClickGuard.attach(node)

    def at(kind: String, x: Double, y: Double): Unit =
      node.dispatchEvent(new dom.MouseEvent(kind,
        new dom.MouseEventInit { bubbles = true; clientX = x; clientY = y }))

    at("mousedown", 10, 10); at("mousemove", 12, 11); at("click", 12, 11)
    assertEquals(clicks, 1, "a 2px wobble is a press, not a drag")

    at("mousedown", 10, 10); at("mousemove", 40, 40); at("click", 40, 40)
    assertEquals(clicks, 1, "a 30px drag must not also open the overlay")

    at("mousedown", 10, 10); at("click", 10, 10)
    assertEquals(clicks, 2, "the guard resets between gestures")

    node.remove()
  }
}
