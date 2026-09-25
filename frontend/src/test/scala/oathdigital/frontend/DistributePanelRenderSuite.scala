package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DistributeAmountWire,
  GameIntent => Intent}
import ParkedDecision.Surface
import org.scalajs.dom
import scala.scalajs.js

/** The Distribute panel at the DOM: rows, steppers, Shift+click and the
  * confirm button's enablement, all read off the rendered tree.
  */
class DistributePanelRenderSuite extends munit.FunSuite:
  private def bank(id: String, label: String) =
    DecisionOptionState("favor-bank", id, label)
  private val query = DecisionQueryState("distribute", Vector.empty,
    heading = Some("League Treaty"), confirmLabel = Some("Move favor"),
    slots = Vector(DecisionSlotState(bank("arcane", "Arcane"), 0, 2, Some(2)),
      DecisionSlotState(bank("nomad", "Nomad"), 0, 4, Some(0))),
    minTotal = Some(2), maxTotal = Some(2))
  private val parked = WalkerDecisionState("begin-rest",
    "rest.league-treaty.distribution", "decide", query = Some(query))

  private def opened(): Option[WalkerDistributeDraft] =
    WalkerDistributeDraft.reconcile(None,
      BoardSelectionContext("game", "red", 9), Some(parked))

  private def render(ui: RecordingView, draft: Option[WalkerDistributeDraft],
      canControl: Boolean = true): dom.Element =
    val panel = dom.document.createElement("div")
    DistributePanelRenderer.render(Surface.Distribute(parked, query), draft,
      canControl, panel, ui)
    panel

  private def one(root: dom.Element, selector: String): dom.Element =
    val found = root.querySelectorAll(selector)
    assertEquals(found.length, 1, s"expected exactly one $selector")
    found(0).asInstanceOf[dom.Element]

  private def amount(panel: dom.Element, item: String): String =
    one(panel, s"""[data-option-id="$item"] .distribute-amount""").textContent

  private def click(element: dom.Element, shift: Boolean = false): Unit =
    element.dispatchEvent(new dom.MouseEvent("click", js.Dynamic.literal(
      bubbles = true, shiftKey = shift).asInstanceOf[dom.MouseEventInit]))

  test("the panel renders one row per slot at its suggested amount"):
    val panel = render(new RecordingView("game", "red"), opened())
    assertEquals(one(panel, "h2").textContent, "League Treaty")
    assertEquals(amount(panel, "favor-bank:arcane"), "2")
    assertEquals(amount(panel, "favor-bank:nomad"), "0")
    assertEquals(one(panel, """[data-option-id="favor-bank:nomad"] .distribute-maximum""")
      .textContent, "max 4")
    assertEquals(one(panel, """[data-option-id="favor-bank:nomad"] .distribute-increment""")
      .getAttribute("title"), "Shift+click: all")
    assertEquals(one(panel, """[data-option-id="favor-bank:nomad"] .distribute-decrement""")
      .getAttribute("title"), "Shift+click: all")

  test("a plain click steps by one and a Shift+click drains or fills"):
    val ui = new RecordingView("game", "red")
    def staged(): dom.Element = render(ui, ui.drafts.distribute)
    click(one(render(ui, opened()),
      """[data-option-id="favor-bank:arcane"] .distribute-decrement"""))
    assertEquals(amount(staged(), "favor-bank:arcane"), "1")
    click(one(staged(), """[data-option-id="favor-bank:arcane"] .distribute-decrement"""),
      shift = true)
    assertEquals(amount(staged(), "favor-bank:arcane"), "0")
    click(one(staged(), """[data-option-id="favor-bank:nomad"] .distribute-increment"""),
      shift = true)
    assertEquals(amount(staged(), "favor-bank:nomad"), "2")
    assert(ui.staged.size >= 3)

  test("confirm is enabled exactly when nothing remains and submits the amounts"):
    val ui = new RecordingView("game", "red")
    click(one(render(ui, opened()),
      """[data-option-id="favor-bank:arcane"] .distribute-decrement"""))
    val short = render(ui, ui.drafts.distribute)
    val confirm = one(short, ".distribute-confirm").asInstanceOf[dom.html.Button]
    assert(confirm.disabled)
    click(one(short, """[data-option-id="favor-bank:nomad"] .distribute-increment"""))
    val ready = one(render(ui, ui.drafts.distribute), ".distribute-confirm")
      .asInstanceOf[dom.html.Button]
    assertEquals(ready.textContent, "Move favor")
    assert(!ready.disabled)
    ready.click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker(
      "rest.league-treaty.distribution", DecisionAnswerWire.DistributeWire(Vector(
        DistributeAmountWire("favor-bank", "arcane", 1),
        DistributeAmountWire("favor-bank", "nomad", 1))))))

  test("a viewer who cannot control sees disabled steppers and confirm"):
    val panel = render(new RecordingView("game", "red"), opened(),
      canControl = false)
    assert(one(panel, """[data-option-id="favor-bank:nomad"] .distribute-increment""")
      .asInstanceOf[dom.html.Button].disabled)
    assert(one(panel, ".distribute-confirm").asInstanceOf[dom.html.Button].disabled)

  private val rangedQuery = query.copy(slots = Vector(
    DecisionSlotState(bank("arcane", "Arcane"), 0, 3, None),
    DecisionSlotState(bank("nomad", "Nomad"), 0, 3, None)),
    minTotal = Some(1), maxTotal = Some(3))
  private val rangedParked = parked.copy(query = Some(rangedQuery))

  test("a range shows its minimum and confirms anywhere inside it"):
    val ui = new RecordingView("game", "red")
    val draft = WalkerDistributeDraft.reconcile(None,
      BoardSelectionContext("game", "red", 9), Some(rangedParked))
    def draw(from: Option[WalkerDistributeDraft]): dom.Element =
      val panel = dom.document.createElement("div")
      DistributePanelRenderer.render(Surface.Distribute(rangedParked, rangedQuery),
        from, canControl = true, panel, ui)
      panel
    assertEquals(one(draw(draft), ".distribute-minimum").textContent,
      "At least 1 must be placed")
    assert(one(draw(draft), ".distribute-confirm").asInstanceOf[dom.html.Button].disabled)
    click(one(draw(draft), """[data-option-id="favor-bank:arcane"] .distribute-increment"""))
    val staged = ui.drafts.distribute
    assert(!one(draw(staged), ".distribute-confirm").asInstanceOf[dom.html.Button].disabled)
    assertEquals(one(draw(staged), ".distribute-remaining").textContent, "Remaining: 2")
