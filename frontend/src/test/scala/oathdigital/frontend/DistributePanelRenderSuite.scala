package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DistributeAmountWire,
  GameIntent => Intent}
import Dom.{click, one}
import ParkedDecision.Surface
import org.scalajs.dom

/** The Distribute panel at the DOM: rows, steppers, Shift+click and the
  * confirm button's enablement, all read off the rendered tree.
  */
class DistributePanelRenderSuite extends munit.FunSuite:
  private def bank(id: String, label: String) =
    DecisionOptionState("favor-bank", id, label)
  private val query = DecisionQueryState.Distribute(
    Vector(DecisionSlotState(bank("arcane", "Arcane"), 0, 2, Some(2)),
      DecisionSlotState(bank("nomad", "Nomad"), 0, 4, Some(0))),
    minTotal = 2, maxTotal = 2, confirmLabel = "Move favor",
    heading = Some("League Treaty"))
  private val parked = WalkerDecisionState("begin-rest",
    "rest.league-treaty.distribution", "decide", query = Some(query))

  private def opened(): Option[WalkerDistributeDraft] =
    WalkerDistributeDraft.reconcile(None,
      BoardSelectionContext("game", "red", 9), Some(parked))

  private def render(ui: RecordingControls, draft: Option[WalkerDistributeDraft],
      canControl: Boolean = true): dom.Element =
    val panel = dom.document.createElement("div")
    DistributePanelRenderer.render(Surface.Distribute(parked, query), draft,
      canControl, panel, ui)
    panel

  private def amount(panel: dom.Element, item: String): String =
    one(panel, s"""[data-option-id="$item"] .distribute-amount""").textContent

  test("the panel renders one row per slot at its suggested amount"):
    val panel = render(new RecordingControls(), opened())
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
    val ui = new RecordingControls()
    def staged(): dom.Element = render(ui, ui.drafts.distribute)
    // Arcane opens at 2, so one step down is 1.
    click(one(render(ui, opened()),
      """[data-option-id="favor-bank:arcane"] .distribute-decrement"""))
    assertEquals(amount(staged(), "favor-bank:arcane"), "1")
    click(one(staged(), """[data-option-id="favor-bank:arcane"] .distribute-decrement"""),
      shift = true)
    assertEquals(amount(staged(), "favor-bank:arcane"), "0")
    // Nothing is placed now, so filling nomad takes the 2 that remain.
    click(one(staged(), """[data-option-id="favor-bank:nomad"] .distribute-increment"""),
      shift = true)
    assertEquals(amount(staged(), "favor-bank:nomad"), "2")
    // One stage per click.
    assertEquals(ui.staged.size, 3)

  test("confirm is enabled exactly when nothing remains and submits the amounts"):
    val ui = new RecordingControls()
    // Arcane 2 steps down to 1, leaving 1 to place, so confirm is disabled.
    click(one(render(ui, opened()),
      """[data-option-id="favor-bank:arcane"] .distribute-decrement"""))
    val short = render(ui, ui.drafts.distribute)
    val confirm = one(short, ".distribute-confirm").asInstanceOf[dom.html.Button]
    assert(confirm.disabled)
    // Nomad 0 steps up to 1: arcane 1 + nomad 1 places all 2.
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
    val panel = render(new RecordingControls(), opened(),
      canControl = false)
    assert(one(panel, """[data-option-id="favor-bank:nomad"] .distribute-increment""")
      .asInstanceOf[dom.html.Button].disabled)
    assert(one(panel, ".distribute-confirm").asInstanceOf[dom.html.Button].disabled)

  private val rangedQuery = query.copy(slots = Vector(
    DecisionSlotState(bank("arcane", "Arcane"), 0, 3, None),
    DecisionSlotState(bank("nomad", "Nomad"), 0, 3, None)),
    minTotal = 1, maxTotal = 3)
  private val rangedParked = parked.copy(query = Some(rangedQuery))

  test("a range shows its minimum and confirms anywhere inside it"):
    val ui = new RecordingControls()
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
