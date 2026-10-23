package oathdigital.frontend

import oathdigital.frontend.Dom.{all, one}
import oathdigital.protocol.{DecisionAnswerWire, DecisionPlacementWire,
  GameIntent => Intent}
import oathdigital.testkit.Unwrap.as
import ParkedDecision.Surface
import org.scalajs.dom
import scala.scalajs.js

/** Task 5, at the DOM. `PartitionDecisionStateSuite` proves the interaction
  * state; this drives the controls the panel actually builds, so renderer
  * wiring cannot break while a state-level test stays green: the zones and
  * options are read out of the rendered tree, the moves go through a real
  * drop event, the confirm button's
  * disabled state is read off the element, and the submitted command is
  * whatever a click on it produced.
  *
  * Runs under jsdom (`Test / jsEnv` in `build.sbt`), which is why
  * `dom.document` exists here at all.
  */
class PartitionPanelRenderSuite extends munit.FunSuite:
  /** Task 5b: the heading and the confirm label are the query's own, the
    * way `ForgeProcedure` now declares them -- the panel no longer reads
    * `decision.action` to decide what to call itself.
    */
  private val query = ForgeFixture.query.copy(
    confirmLabel = Some("Complete Forge"), heading = Some("Forge a relic"))

  private val parked = ForgeFixture.parked.copy(query = Some(query))

  /** Renders the panel from `draft` into a detached container, so a test
    * can read the tree and then re-render it from what a click staged
    * (`ui.drafts.partition`) the way the session would.
    */
  private def render(ui: RecordingControls, draft: Option[WalkerPartitionDraft],
      canControl: Boolean = true,
      decision: WalkerDecisionState = parked): dom.Element =
    val parkedQuery = decision.query match
      case Some(partition: DecisionQueryState.Partition) => partition
      case other => fail(s"expected a partition, got $other")
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderPartitionPanel(
      Surface.Partition(decision, parkedQuery), draft, "red",
      canControl, panel, ui)
    panel

  private def controls(): RecordingControls = new RecordingControls()

  /** The draft opened on the Forge decision `parked`. */
  private def forgeDraft(): Option[WalkerPartitionDraft] =
    WalkerPartitionDraft.reconcile(None, ForgeFixture.context, Some(parked))

  /** The DOM key of the option with the id "denizen:`n`": the option's kind,
    * "denizen", then its id, so the kind shows twice.
    */
  private def optionKey(n: Int): String = s"denizen:denizen:$n"

  /** The rendered option with id "denizen:`n`". */
  private def optionNode(panel: dom.Element, n: Int): dom.Element =
    one(panel, s"""[data-option-id="${optionKey(n)}"]""")

  private def optionLabelsIn(panel: dom.Element, sectionKey: String)
      : Vector[String] =
    all(one(panel, s"""[data-section-key="$sectionKey"]"""),
      ".decision-option").map(_.getAttribute("aria-label"))

  private def confirm(panel: dom.Element): dom.html.Button =
    as[dom.html.Button](one(panel, ".partition-confirm"))

  private def click(element: dom.Element): Unit =
    as[dom.html.Element](element).click()

  test("the panel renders one zone per projected section, holding the " +
      "options placed there"):
    val panel = render(controls(), forgeDraft())
    assertEquals(one(panel, "h2").textContent, "Forge a relic")
    assertEquals(one(panel, ".partition-instruction").textContent,
      "Drag and drop the cards. Assign every option: Pay Favor (2), " +
        "Pay Secret (1).")
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
      Vector(optionKey(1), optionKey(2), optionKey(3)))

  /** The options sit in their own row inside the zone. A zone that held them
    * directly measured as wide as its heading plus every card laid end to
    * end, so a keep-one zone claimed a full column it had no use for.
    */
  test("a zone's options live in a row of their own"):
    val zone = all(render(controls(), forgeDraft()), ".partition-zone").head
    val row = one(zone, ".partition-options")
    assertEquals(row.parentNode, zone)
    assertEquals(all(row, ".decision-option").size, 2)
    assertEquals(all(zone, ":scope > .decision-option"), Vector.empty)

  /** Order within a zone is part of the answer, but only a zone that takes
    * whatever is left over -- no minimum, no cap -- is one whose order the
    * player needs explaining. Keyed on that shape rather than on the word
    * "Discard", which would stop matching the day the section is renamed.
    */
  private val keepDiscard = DecisionQueryState.Partition(
    Vector(DecisionSectionState("keep", "Keep", 1, Some(1)),
      DecisionSectionState("discard", "Discard", 0)),
    Vector("1", "2", "3").map(id =>
      DecisionOptionState("denizen", s"denizen:$id", s"Denizen $id")),
    confirmLabel = Some("Confirm Adviser"),
    heading = Some("Choose your starting adviser"))

  private def keepOneDraft(): Option[WalkerPartitionDraft] =
    WalkerPartitionDraft.reconcile(None, ForgeFixture.context,
      Some(WalkerDecisionState("setup", "setup-1",
        query = Some(keepDiscard))))

  private def pickPanel(ui: RecordingControls,
      draft: Option[WalkerPartitionDraft]): dom.Element =
    render(ui, draft,
      decision = WalkerDecisionState("setup", "setup-1",
        query = Some(keepDiscard)))

  private def pickPanel(draft: Option[WalkerPartitionDraft]): dom.Element =
    pickPanel(controls(), draft)

  test("a leftover zone holding several options says its order counts"):
    val zone = one(pickPanel(keepOneDraft()),
      """[data-section-key="discard"]""")
    assertEquals(one(zone, ".decision-zone-order").textContent,
      "The last one lands on top of the pile.")

  /** The keep-one shape reads like the rulebook's own sentence, and its
    * minimums are not worth a line: the slot always holds its one and the
    * rest has none. Forge, with a minimum to meet in each zone, keeps both.
    */
  test("a keep-one partition says keep one, discard the rest, without minimum lines"):
    val panel = pickPanel(keepOneDraft())
    assertEquals(one(panel, ".partition-instruction").textContent,
      "Drag and drop the cards. Keep one; discard the rest.")
    assertEquals(all(panel, ".decision-zone-helper"), Vector.empty)

  test("a partition that must meet a minimum in each zone is not keep-one"):
    assertEquals(WalkerPanelSupport.keepOne(query), None)

  /** Land Warden's Search keeps one or two: the capped zone holds up to
    * two, and the order of its cards is the order they are played in. */
  private val keepUpToTwo = keepDiscard.copy(sections = Vector(
    DecisionSectionState("keep", "Keep", 1, Some(2)),
    DecisionSectionState("discard", "Discard", 0)))

  private val keepUpToTwoDecision = WalkerDecisionState("setup", "setup-1",
    query = Some(keepUpToTwo))

  /** The draft opened on `keepUpToTwo`: Denizens 1 and 2 kept. */
  private def keepUpToTwoDraft(): Option[WalkerPartitionDraft] =
    WalkerPartitionDraft.reconcile(None, ForgeFixture.context,
      Some(keepUpToTwoDecision))

  test("a keep-one-or-two partition says so, without minimum lines"):
    val panel = render(controls(), keepUpToTwoDraft(),
      decision = keepUpToTwoDecision)
    assertEquals(one(panel, ".partition-instruction").textContent,
      "Drag and drop the cards. Keep one or two; discard the rest.")
    assertEquals(all(panel, ".decision-zone-helper"), Vector.empty)

  test("a keep zone holding two says they are played in that order"):
    def keepZone(draft: Option[WalkerPartitionDraft]) = one(render(
      controls(), draft, decision = keepUpToTwoDecision),
      """[data-section-key="keep"]""")
    assertEquals(one(keepZone(keepUpToTwoDraft()),
      ".decision-zone-order").textContent, "They are played in this order.")
    val keepingOne = keepUpToTwoDraft().map(_.move(optionKey(2), "discard"))
    assertEquals(all(keepZone(keepingOne), ".decision-zone-order"),
      Vector.empty)

  test("a drop on a full keep zone is refused; with room it lands"):
    val ui = controls()
    drop(one(render(ui, keepUpToTwoDraft(), decision = keepUpToTwoDecision),
      """[data-section-key="keep"]"""), optionKey(3))
    assertEquals(ui.drafts.partition, None)
    val withRoom = keepUpToTwoDraft().map(_.move(optionKey(2), "discard"))
    drop(one(render(ui, withRoom, decision = keepUpToTwoDecision),
      """[data-section-key="keep"]"""), optionKey(3))
    assertEquals(optionLabelsIn(render(ui, ui.drafts.partition,
      decision = keepUpToTwoDecision), "keep"), Vector("Denizen 1", "Denizen 3"))

  test("dropping a discard on the keep zone swaps it with the kept card"):
    val ui = controls()
    drop(one(pickPanel(ui, keepOneDraft()), """[data-section-key="keep"]"""),
      optionKey(2))
    val swapped = pickPanel(ui, ui.drafts.partition)
    assertEquals(optionLabelsIn(swapped, "keep"), Vector("Denizen 2"))
    assertEquals(optionLabelsIn(swapped, "discard"),
      Vector("Denizen 1", "Denizen 3"))

  test("the capped zone says nothing about order"):
    assertEquals(all(one(pickPanel(keepOneDraft()),
      """[data-section-key="keep"]"""), ".decision-zone-order"), Vector.empty)

  /** One card cannot be in an order, so the sentence would be noise. A pick
    * of two leaves exactly one card behind, whichever one is kept.
    */
  test("a zone holding one option says nothing about order"):
    val twoCards = keepDiscard.copy(options = keepDiscard.options.take(2))
    val decision = WalkerDecisionState("setup", "setup-1",
      query = Some(twoCards))
    val draft = WalkerPartitionDraft.reconcile(None, ForgeFixture.context,
      Some(decision))
    assertEquals(all(render(controls(), draft,
      decision = decision), ".decision-zone-order"), Vector.empty)

  /** Forge pays into zones that each carry a minimum, so none of them is a
    * leftover zone and none claims an order.
    */
  test("a partition of payments claims no order anywhere"):
    assertEquals(all(render(controls(), forgeDraft()), ".decision-zone-order"),
      Vector.empty)

  test("the confirm button names the card the one-card zone holds"):
    assertEquals(confirm(pickPanel(keepOneDraft())).textContent,
      "Keep Denizen 1")

  /** With the slot empty or the query declaring no single-slot section, the
    * query's own label is what the button says.
    */
  test("without a filled single slot the query's own label stands"):
    val emptied = keepOneDraft().map(_.move(optionKey(1), "discard"))
    assertEquals(confirm(pickPanel(emptied)).textContent, "Confirm Adviser")
    assertEquals(confirm(render(controls(), forgeDraft())).textContent,
      "Complete Forge")

  /** Drag is the only way to move an option, so an option holds its card
    * and no controls, in every zone and on every shape.
    */
  test("an option holds only its card, with no move or order controls"):
    for panel <- Vector(pickPanel(keepOneDraft()), render(controls(),
        forgeDraft()))
    do
      assertEquals(all(panel, ".decision-option button"), Vector.empty)
      assertEquals(all(panel, ".decision-option").map(option =>
        all(option, ":scope > *").map(_.getAttribute("class"))),
        Vector.fill(3)(Vector("option-summary")))

  test("the confirm button sits in its own footer strip"):
    val panel = pickPanel(keepOneDraft())
    assertEquals(as[dom.Element](confirm(panel).parentNode)
      .getAttribute("class"), "partition-footer")

  test("dropping an option on another places it before that one"):
    val ui = controls()
    val draft = forgeDraft()
    val panel = render(ui, draft)
    val dragged = dragStartPayload(optionNode(panel, 3))
    drop(optionNode(panel, 1), dragged)
    assertEquals(optionLabelsIn(render(ui, ui.drafts.partition), "pay-favor"),
      Vector("Denizen 3", "Denizen 1", "Denizen 2"))
    assertEquals(optionLabelsIn(render(ui, ui.drafts.partition), "pay-secret"),
      Vector.empty)

  test("dropping a dragged option on a zone moves it there"):
    val ui = controls()
    val draft = forgeDraft()
    val panel = render(ui, draft)
    // Whatever `dragstart` puts on the transfer is exactly what the drop
    // reads back, so the two halves of the drag are tested together.
    val dragged = dragStartPayload(optionNode(panel, 3))
    assertEquals(dragged, optionKey(3))
    drop(one(panel, """[data-section-key="pay-favor"]"""), dragged)
    val moved = render(ui, ui.drafts.partition)
    assertEquals(optionLabelsIn(moved, "pay-favor"),
      Vector("Denizen 1", "Denizen 2", "Denizen 3"))
    assertEquals(optionLabelsIn(moved, "pay-secret"), Vector.empty)

  test("confirmation is refused until every projected minimum is met"):
    val ui = controls()
    val draft = forgeDraft()
    assert(!confirm(render(ui, draft)).disabled)
    // Emptying the secret zone leaves it below its projected minimum.
    drop(one(render(ui, draft), """[data-section-key="pay-favor"]"""),
      optionKey(3))
    val short = render(ui, ui.drafts.partition)
    assert(confirm(short).disabled)
    click(confirm(short))
    assertEquals(ui.submitted, Vector.empty)
    // Putting one back satisfies it again.
    drop(one(render(ui, ui.drafts.partition),
      """[data-section-key="pay-secret"]"""), optionKey(1))
    assert(!confirm(render(ui, ui.drafts.partition)).disabled)

  test("a player who cannot control the game gets a disabled confirm"):
    val ui = controls()
    val panel = render(ui, forgeDraft(), canControl = false)
    assert(confirm(panel).disabled)
    click(confirm(panel))
    assertEquals(ui.submitted, Vector.empty)

  test("clicking confirm submits every option in the zone it was left in"):
    val ui = controls()
    val draft = forgeDraft()
    // Swap the first and last options, which keeps both minima met.
    drop(one(render(ui, draft), """[data-section-key="pay-secret"]"""),
      optionKey(1))
    drop(one(render(ui, ui.drafts.partition),
      """[data-section-key="pay-favor"]"""), optionKey(3))
    val arranged = render(ui, ui.drafts.partition)
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
  test("the panel titles itself from the query, not from the action"):
    val retitled = query.copy(heading = Some("Pay for the relic"),
      confirmLabel = Some("Pay"))
    val panel = render(controls(), forgeDraft(),
      decision = parked.copy(query = Some(retitled)))
    assertEquals(one(panel, "h2").textContent, "Pay for the relic")
    assertEquals(confirm(panel).textContent, "Pay")

  test("a partition query declaring no copy falls back to generic copy"):
    val bare = query.copy(heading = None, confirmLabel = None)
    val panel = render(controls(), forgeDraft(),
      decision = parked.copy(query = Some(bare)))
    assertEquals(one(panel, "h2").textContent, "Resolve decision")
    assertEquals(confirm(panel).textContent, "Confirm")
    // Untitled, not unusable: the zones and the options are still there.
    assertEquals(all(panel, ".partition-zone").size, 2)
    assertEquals(all(panel, ".decision-option").size, 3)


  /** Fires `dragstart` and returns what the handler wrote to the transfer.
    * jsdom implements neither `DragEvent` nor `DataTransfer`, so the event
    * carries a recording stand-in for the one property the handler reads.
    */
  private def dragStartPayload(node: dom.Element): String =
    var written = ""
    val transfer = js.Dynamic.literal(
      setData = (_: String, value: String) => written = value,
      getData = (_: String) => written)
    node.dispatchEvent(transferEvent("dragstart", transfer))
    written

  private def drop(zone: dom.Element, item: String): Unit =
    val transfer = js.Dynamic.literal(getData = (_: String) => item)
    zone.dispatchEvent(transferEvent("drop", transfer))

  private def transferEvent(name: String, transfer: js.Dynamic): dom.Event =
    val event = js.Dynamic.newInstance(js.Dynamic.global.Event)(name,
      js.Dynamic.literal(bubbles = true, cancelable = true))
    event.updateDynamic("dataTransfer")(transfer)
    event.asInstanceOf[dom.Event]

  test("a decision option is not a focus stop"):
    val options = all(render(controls(), forgeDraft()), ".decision-option")
    assertEquals(options.size, 3)
    assertEquals(options.map(_.getAttribute("tabindex")),
      Vector.fill(options.size)(null),
      "a decision-option has no keydown handler, so a tab stop here is dead")

  test("a drag on an option swallows the click that follows; a plain press does not"):
    val node = as[dom.html.Element](dom.document.createElement("div"))
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
