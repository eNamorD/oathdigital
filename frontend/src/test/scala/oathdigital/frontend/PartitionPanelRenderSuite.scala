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
  * pointer drag, the confirm button's
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

  test("a card dropped short of a card's midpoint lands before it"):
    val ui = controls()
    val panel = layOutInRows(render(ui, forgeDraft()))
    drop(optionNode(panel, 1), optionKey(3), at = (40, 70))
    assertEquals(optionLabelsIn(render(ui, ui.drafts.partition), "pay-favor"),
      Vector("Denizen 3", "Denizen 1", "Denizen 2"))
    assertEquals(optionLabelsIn(render(ui, ui.drafts.partition), "pay-secret"),
      Vector.empty)

  /** A drop on a card used to place the dragged one in front of it, so a
    * card dropped on its right-hand neighbour never moved.
    */
  test("a card dropped past its right-hand neighbour's midpoint lands after it"):
    val ui = controls()
    val panel = layOutInRows(render(ui, forgeDraft()))
    drop(optionNode(panel, 2), optionKey(1), at = (170, 70))
    assertEquals(optionLabelsIn(render(ui, ui.drafts.partition), "pay-favor"),
      Vector("Denizen 2", "Denizen 1"))

  test("while a card is dragged the zones show where it would land"):
    val ui = controls()
    val panel = layOutInRows(render(ui, forgeDraft()))
    carry(optionNode(panel, 2), optionKey(1), at = (170, 70))
    assertEquals(optionLabelsIn(panel, "pay-favor"),
      Vector("Denizen 2", "Denizen 1"))
    assertEquals(ui.drafts.partition, None, "a preview stages nothing")
    // Back short of the neighbour's midpoint, the card returns before it.
    moveOver(optionNode(panel, 2), at = (40, 70))
    assertEquals(optionLabelsIn(panel, "pay-favor"),
      Vector("Denizen 1", "Denizen 2"))
    cancel(panel)

  test("a drag the browser takes over puts every card back"):
    val ui = controls()
    val panel = layOutInRows(render(ui, forgeDraft()))
    carry(one(panel, """[data-section-key="pay-favor"]"""), optionKey(3),
      at = (40, 70))
    assertEquals(optionLabelsIn(panel, "pay-secret"), Vector.empty)
    cancel(panel)
    assertEquals(optionLabelsIn(panel, "pay-favor"),
      Vector("Denizen 1", "Denizen 2"))
    assertEquals(optionLabelsIn(panel, "pay-secret"), Vector("Denizen 3"))
    assertEquals(ui.drafts.partition, None)

  test("a card carried out of the zones goes back, and letting go there stages nothing"):
    val ui = controls()
    val panel = layOutInRows(render(ui, forgeDraft()))
    carry(optionNode(panel, 2), optionKey(1), at = (170, 70))
    assertEquals(optionLabelsIn(panel, "pay-favor"),
      Vector("Denizen 2", "Denizen 1"))
    // Captured, the move reaches the zones from outside them.
    pointingAt(dom.document.body)
    pointer("pointermove", one(panel, ".partition-zones"), (900, 900))
    assertEquals(optionLabelsIn(panel, "pay-favor"),
      Vector("Denizen 1", "Denizen 2"))
    letGo(panel, at = (900, 900))
    assertEquals(ui.drafts.partition, None)

  /** The preview runs the move a drop would make, so the single slot's swap
    * shows before the card is let go.
    */
  test("dragging a discard over the keep zone previews the swap"):
    val panel = layOutInRows(pickPanel(keepOneDraft()))
    carry(one(panel, """[data-section-key="keep"]"""), optionKey(2),
      at = (40, 70))
    assertEquals(optionLabelsIn(panel, "keep"), Vector("Denizen 2"))
    assertEquals(optionLabelsIn(panel, "discard"),
      Vector("Denizen 1", "Denizen 3"))
    // Carried back over the discard zone, the swap is undone.
    moveOver(one(panel, """[data-section-key="discard"]"""), at = (40, 70))
    assertEquals(optionLabelsIn(panel, "keep"), Vector("Denizen 1"))
    assertEquals(optionLabelsIn(panel, "discard"),
      Vector("Denizen 2", "Denizen 3"))
    cancel(panel)

  test("dropping a dragged option on a zone moves it there"):
    val ui = controls()
    val panel = render(ui, forgeDraft())
    drop(one(panel, """[data-section-key="pay-favor"]"""), optionKey(3))
    val moved = render(ui, ui.drafts.partition)
    assertEquals(optionLabelsIn(moved, "pay-favor"),
      Vector("Denizen 1", "Denizen 2", "Denizen 3"))
    assertEquals(optionLabelsIn(moved, "pay-secret"), Vector.empty)

  test("a copy of the held card follows the pointer and goes when it is let go"):
    val panel = render(controls(), forgeDraft())
    carry(one(panel, """[data-section-key="pay-favor"]"""), optionKey(3),
      at = (40, 70))
    val ghosts = all(panel, ".partition-ghost")
    assertEquals(ghosts.size, 1)
    assertEquals(ghosts.head.getAttribute("aria-hidden"), "true")
    // jsdom lays nothing out, so the card's box is at the origin and the
    // copy keeps the pointer where it took the card.
    assertEquals(as[dom.html.Element](ghosts.head).style.left, "35px")
    assert(optionNode(panel, 3).classList.contains("dragging"))
    letGo(panel, at = (40, 70))
    assertEquals(all(panel, ".partition-ghost"), Vector.empty)
    assert(!optionNode(panel, 3).classList.contains("dragging"))

  test("letting go of a drag swallows its click; a plain press keeps it"):
    val panel = render(controls(), forgeDraft())
    dom.document.body.appendChild(panel)
    try
      val card = optionNode(panel, 1)
      var clicks = 0
      card.addEventListener("click", (_: dom.Event) => clicks += 1)
      def clickCard(): Unit = card.dispatchEvent(new dom.MouseEvent("click",
        new dom.MouseEventInit { bubbles = true; cancelable = true }))

      pointer("pointerdown", card, (10, 10))
      pointer("pointermove", card, (12, 11))
      letGo(panel, at = (12, 11))
      clickCard()
      assertEquals(clicks, 1, "a 2px wobble is a press, not a drag")
      assertEquals(all(panel, ".partition-ghost"), Vector.empty)

      drop(optionNode(panel, 2), optionKey(1), at = (40, 40))
      clickCard()
      assertEquals(clicks, 1, "a drag must not also open the overlay")

      pointer("pointerdown", card, (10, 10))
      letGo(panel, at = (10, 10))
      clickCard()
      assertEquals(clicks, 2, "the guard resets between gestures")
    finally panel.remove()

  test("a plain press leaves every card where it is, so its click still comes"):
    // A browser drops the click when its card is moved between the press
    // and the release, even back to the same place; jsdom does not, so the
    // test watches for the move itself.
    val panel = render(controls(), forgeDraft())
    val zones = one(panel, ".partition-zones")
    val moves = new dom.MutationObserver((_, _) => ())
    moves.observe(zones, new dom.MutationObserverInit {
      childList = true; subtree = true })
    val card = optionNode(panel, 1)
    pointer("pointerdown", card, (10, 10))
    letGo(panel, at = (10, 10))
    pointer("pointerdown", card, (10, 10), touch = true, time = 0)
    letGo(panel, at = (10, 10), touch = true)
    assertEquals(moves.takeRecords().length, 0)
    moves.disconnect()

  test("a finger that moves at once scrolls the pane rather than the card"):
    val ui = controls()
    val panel = render(ui, forgeDraft())
    val card = optionNode(panel, 3)
    pointer("pointerdown", card, (5, 5), touch = true, time = 0)
    pointingAt(one(panel, """[data-section-key="pay-favor"]"""))
    pointer("pointermove", card, (5, 60), touch = true, time = 100)
    assert(!card.classList.contains("dragging"))
    assert(!touchMove(card).defaultPrevented, "the pane must still scroll")
    // The rest of that swipe is the browser's too.
    pointer("pointermove", card, (5, 90), touch = true,
      time = PartitionDrag.HoldMs + 100)
    assert(!card.classList.contains("dragging"))
    letGo(panel, at = (5, 90), touch = true)
    assertEquals(ui.drafts.partition, None)

  test("a finger that rests on a card first drags it, and the pane holds still"):
    val ui = controls()
    val panel = render(ui, forgeDraft())
    val card = optionNode(panel, 3)
    pointer("pointerdown", card, (5, 5), touch = true, time = 0)
    pointingAt(one(panel, """[data-section-key="pay-favor"]"""))
    pointer("pointermove", card, (5, 60), touch = true,
      time = PartitionDrag.HoldMs + 10)
    assert(card.classList.contains("dragging"))
    assert(touchMove(card).defaultPrevented,
      "the pane must not scroll under a held card")
    letGo(panel, at = pastEveryCard, touch = true)
    assertEquals(optionLabelsIn(render(ui, ui.drafts.partition), "pay-favor"),
      Vector("Denizen 1", "Denizen 2", "Denizen 3"))

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


  override def afterEach(context: AfterEach): Unit =
    js.special.delete(dom.document, "elementFromPoint")

  /** jsdom lays nothing out, so the page answers what lies under the
    * pointer with `under` until the next call.
    */
  private def pointingAt(under: dom.Element): Unit =
    dom.document.asInstanceOf[js.Dynamic].elementFromPoint =
      (_: Double, _: Double) => under

  /** Fires the pointer event `kind` on `target` at `at`. jsdom has no
    * PointerEvent, so a mouse event carries the fields the drag reads, the
    * way `MapViewportSuite` sends them.
    */
  private def pointer(kind: String, target: dom.EventTarget,
      at: (Double, Double), touch: Boolean = false, time: Double = 0)
      : dom.Event =
    val event = new dom.MouseEvent(kind, new dom.MouseEventInit {
      bubbles = true; cancelable = true; button = 0
      clientX = at._1; clientY = at._2 })
    val fields = js.Dynamic.literal(
      pointerType = if touch then "touch" else "mouse", pointerId = 1.0,
      timeStamp = time)
    js.Object.keys(fields.asInstanceOf[js.Object]).foreach(key =>
      js.Object.defineProperty(event, key, js.Dynamic.literal(
        value = fields.selectDynamic(key)).asInstanceOf[js.PropertyDescriptor]))
    target.dispatchEvent(event)
    event

  private def touchMove(target: dom.Element): dom.Event =
    val event = new dom.Event("touchmove", new dom.EventInit {
      bubbles = true; cancelable = true })
    target.dispatchEvent(event)
    event

  /** Past every card of any zone the tests lay out: a drop there lands
    * last.
    */
  private val pastEveryCard = (10000.0, 10000.0)

  private def zonesOf(node: dom.Element): dom.Element =
    as[dom.Element](node.closest(".partition-zones"))

  /** Takes `item` with the mouse and carries it over `target`, a zone or a
    * card, with the pointer at `at`.
    */
  private def carry(target: dom.Element, item: String,
      at: (Double, Double)): Unit =
    val card = one(zonesOf(target), s"""[data-option-id="$item"]""")
    pointer("pointerdown", card, (5, 5))
    moveOver(target, at)

  /** Moves the held card over `target` with the pointer at `at`. */
  private def moveOver(target: dom.Element, at: (Double, Double)): Unit =
    pointingAt(target)
    pointer("pointermove", target, at)

  /** Captured, the release reaches the zones wherever the pointer is. */
  private def letGo(panel: dom.Element, at: (Double, Double),
      touch: Boolean = false): Unit =
    pointer("pointerup", one(panel, ".partition-zones"), at, touch)

  private def cancel(panel: dom.Element): Unit =
    pointer("pointercancel", one(panel, ".partition-zones"), pastEveryCard)

  /** Drags `item` onto `target`, a zone or a card, and lets it go there. */
  private def drop(target: dom.Element, item: String,
      at: (Double, Double) = pastEveryCard): Unit =
    carry(target, item, at)
    letGo(as[dom.Element](zonesOf(target).parentNode), at)

  /** jsdom lays nothing out, so every option reports the box it would have
    * in a row of 100-wide cards 10 apart, from wherever it sits in its row
    * right now: a card a preview moves reports its new place. Midpoints
    * fall at 50, 160 and 270.
    */
  private def layOutInRows(panel: dom.Element): dom.Element =
    all(panel, ".decision-option").foreach(node =>
      node.asInstanceOf[js.Dynamic].getBoundingClientRect = () =>
        val index = all(as[dom.Element](node.parentNode), ".decision-option")
          .indexOf(node)
        js.Dynamic.literal(left = index * 110.0, top = 0.0,
          right = index * 110.0 + 100, bottom = 140.0))
    panel

  test("a decision option is not a focus stop"):
    val options = all(render(controls(), forgeDraft()), ".decision-option")
    assertEquals(options.size, 3)
    assertEquals(options.map(_.getAttribute("tabindex")),
      Vector.fill(options.size)(null),
      "a decision-option has no keydown handler, so a tab stop here is dead")
