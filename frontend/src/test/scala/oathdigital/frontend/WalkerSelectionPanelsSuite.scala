package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionOptionWire,
  GameIntent => Intent}
import ParkedDecision.Surface
import org.scalajs.dom
import scala.scalajs.js

class WalkerSelectionPanelsSuite extends munit.FunSuite:
  private def site(id: String) = DecisionOptionState("site", id, s"Site $id")
  private val many = DecisionQueryState.ChooseMany(
    Vector(site("a"), site("b"), site("c")), minOptions = 2, maxOptions = 2,
    heading = Some("Choose sites"))
  private val amount = DecisionQueryState.ChooseAmount(minAmount = 3,
    maxAmount = 5, suggested = None, confirmLabel = "Take banner",
    heading = Some("Place more than 2 favor"))
  private def decision(id: String, query: DecisionQueryState,
      rollOutcome: Option[WalkerRollOutcomeState] = None): WalkerDecisionState =
    WalkerDecisionState("challenge", id, "decide", query = Some(query),
      rollOutcome = rollOutcome)

  private def opened(id: String, query: DecisionQueryState)
      : Option[WalkerSelectionDraft] =
    WalkerSelectionDraft.reconcile(None,
      BoardSelectionContext("game", "red", 9), Some(decision(id, query)))

  private def render(ui: RecordingControls, draft: Option[WalkerSelectionDraft],
      id: String, query: ParkedDecision.SelectionForm,
      canControl: Boolean = true,
      rollOutcome: Option[WalkerRollOutcomeState] = None): dom.Element =
    val panel = dom.document.createElement("div")
    WalkerSelectionPanels.render(Surface.Selection(
      decision(id, query, rollOutcome), query), draft, canControl, panel, ui)
    panel

  private def one(root: dom.Element, selector: String): dom.Element =
    val found = root.querySelectorAll(selector)
    assertEquals(found.length, 1, s"expected exactly one $selector")
    found(0).asInstanceOf[dom.Element]

  test("choose-many renders a toggle per option and confirms only at the count"):
    val ui = new RecordingControls()
    val draft = opened("challenge.ribbon-site", many)
    assertEquals(one(render(ui, draft, "challenge.ribbon-site", many), "h2")
      .textContent, "Choose sites")
    val confirm0 = one(render(ui, draft, "challenge.ribbon-site", many),
      ".walker-many-confirm").asInstanceOf[dom.html.Button]
    assert(confirm0.disabled)
    one(render(ui, draft, "challenge.ribbon-site", many),
      """[data-option-id="site:a"]""").asInstanceOf[dom.html.Button].click()
    one(render(ui, ui.drafts.selection, "challenge.ribbon-site", many),
      """[data-option-id="site:c"]""").asInstanceOf[dom.html.Button].click()
    val panel = render(ui, ui.drafts.selection, "challenge.ribbon-site", many)
    assertEquals(one(panel, """[data-option-id="site:a"]""")
      .getAttribute("aria-pressed"), "true")
    val confirm = one(panel, ".walker-many-confirm").asInstanceOf[dom.html.Button]
    // A choose-many declares no confirm label, so the panel names the control
    // itself. Asserted rather than reasoned about: the string became a literal
    // when the query stopped carrying an optional label.
    assertEquals(confirm.textContent, "Confirm")
    assert(!confirm.disabled)
    confirm.click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker("challenge.ribbon-site",
      DecisionAnswerWire.ChooseManyWire(Vector(DecisionOptionWire("site", "a"),
        DecisionOptionWire("site", "c"))))))

  test("choose-many instruction names its count without a zero lower bound"):
    def instruction(min: Int, max: Int): String =
      val query = many.copy(minOptions = min, maxOptions = max)
      one(render(new RecordingControls(), opened("setup.reveal", query),
        "setup.reveal", query), ".walker-many-instruction").textContent
    assertEquals(instruction(0, 3), "Choose any number.")
    assertEquals(instruction(0, 2), "Choose up to 2.")
    assertEquals(instruction(2, 2), "Choose 2.")
    assertEquals(instruction(1, 3), "Choose 1 to 3.")

  private def field(panel: dom.Element): dom.html.Input =
    one(panel, "input.walker-amount").asInstanceOf[dom.html.Input]
  private def confirmOf(panel: dom.Element): dom.html.Button =
    one(panel, ".walker-amount-confirm").asInstanceOf[dom.html.Button]
  private def listOf(panel: dom.Element): dom.Element =
    one(panel, ".walker-amount-options")
  private def type_(input: dom.html.Input, value: String): Unit =
    input.value = value
    input.dispatchEvent(new dom.Event("input"))
  private def press(node: dom.Element, key: String): Unit =
    node.dispatchEvent(new dom.KeyboardEvent("keydown",
      js.Dynamic.literal(key = key, bubbles = true)
        .asInstanceOf[dom.KeyboardEventInit]))
  private def mouseDown(node: dom.Element): Unit =
    node.dispatchEvent(new dom.MouseEvent("mousedown",
      new dom.MouseEventInit { bubbles = true; cancelable = true }))
  override def afterEach(context: AfterEach): Unit =
    dom.document.body.innerHTML = ""

  private def amountPanel(ui: RecordingControls,
      canControl: Boolean = true): dom.Element =
    val panel = render(ui, opened("challenge.amount", amount),
      "challenge.amount", amount, canControl)
    // Focus and blur reach only a node in the document.
    dom.document.body.appendChild(panel)
    panel

  test("choose-amount offers a typed field over its range and submits it"):
    val ui = new RecordingControls()
    val panel = amountPanel(ui)
    val input = field(panel)
    assertEquals(input.value, "3")
    assertEquals(input.getAttribute("inputmode"), "numeric")
    assertEquals(one(panel, ".walker-amount-instruction").textContent,
      "Enter 3 to 5.")
    assert(listOf(panel).hasAttribute("hidden"))
    assertEquals(listOf(panel).querySelectorAll("[role=option]").toVector
      .map(_.textContent), Vector("3", "4", "5"))
    type_(input, "5")
    assertEquals(ui.staged, Vector.empty)
    assertEquals(confirmOf(panel).textContent, "Take banner")
    confirmOf(panel).click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker("challenge.amount",
      DecisionAnswerWire.ChooseAmountWire(5))))

  test("choose-amount refuses a typed value outside its range"):
    val ui = new RecordingControls()
    val panel = amountPanel(ui)
    val input = field(panel)
    Vector("9", "2", "x", "", "4.5").foreach { value =>
      type_(input, value)
      assert(confirmOf(panel).disabled, s"confirm enabled for '$value'")
      assertEquals(input.getAttribute("aria-invalid"), "true", value)
    }
    press(input, "Enter")
    assertEquals(ui.submitted, Vector.empty)
    type_(input, " 4 ")
    assert(!confirmOf(panel).disabled)
    assertEquals(input.getAttribute("aria-invalid"), "false")

  test("choose-amount opens its list from the keyboard and picks from it"):
    val ui = new RecordingControls()
    val panel = amountPanel(ui)
    val input = field(panel)
    press(input, "ArrowDown")
    assert(!listOf(panel).hasAttribute("hidden"))
    assertEquals(input.getAttribute("aria-expanded"), "true")
    press(input, "ArrowDown")
    press(input, "ArrowDown")
    press(input, "ArrowDown")
    assertEquals(input.getAttribute("aria-activedescendant"),
      one(panel, "[role=option][data-amount='5']").id)
    press(input, "Enter")
    assertEquals(input.value, "5")
    assert(listOf(panel).hasAttribute("hidden"))
    assertEquals(ui.submitted, Vector.empty)
    press(input, "Enter")
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker("challenge.amount",
      DecisionAnswerWire.ChooseAmountWire(5))))

  test("choose-amount opens its list from the toggle and closes it on " +
      "Escape or a pick"):
    val ui = new RecordingControls()
    val panel = amountPanel(ui)
    val input = field(panel)
    val toggle = one(panel, ".walker-amount-toggle")
    mouseDown(toggle)
    assert(!listOf(panel).hasAttribute("hidden"))
    assertEquals(one(panel, "[aria-selected=true]").textContent, "3")
    press(input, "Escape")
    assert(listOf(panel).hasAttribute("hidden"))
    mouseDown(toggle)
    mouseDown(one(panel, "[role=option][data-amount='4']"))
    assertEquals(input.value, "4")
    assert(listOf(panel).hasAttribute("hidden"))
    assert(!confirmOf(panel).disabled)

  test("a viewer who cannot control sees disabled controls"):
    val panel = amountPanel(new RecordingControls(), canControl = false)
    assert(field(panel).disabled)
    assert(one(panel, ".walker-amount-toggle").asInstanceOf[dom.html.Button]
      .disabled)
    assert(confirmOf(panel).disabled)
    mouseDown(one(panel, ".walker-amount-toggle"))
    assert(listOf(panel).hasAttribute("hidden"))

  test("the sacrifice panel draws dice, then totals, then the prompt"):
    val outcome = WalkerRollOutcomeState("campaign.attack",
      Vector("two-swords-skull", "one-sword"), 3, None, Vector("1 skull loss"))
    val sacrifice = DecisionQueryState.ChooseAmount(minAmount = 0,
      maxAmount = 2, suggested = None, confirmLabel = "Sacrifice",
      heading = Some("Sacrifice up to 2 warbands for one attack each"))
    val panel = render(new RecordingControls(),
      opened("campaign.sacrifice", sacrifice), "campaign.sacrifice", sacrifice,
      rollOutcome = Some(outcome))
    one(panel, ".walker-roll-faces .die-faces")
    assertEquals(one(panel, ".walker-roll-totals").textContent,
      "Attack 3 · 1 skull loss")
    assertEquals(one(panel, "h2").textContent,
      "Sacrifice up to 2 warbands for one attack each")
    val order = panel.children.toVector.map(child =>
      Option(child.getAttribute("class")).getOrElse(""))
    assertEquals(order.take(3),
      Vector("walker-roll-faces", "walker-roll-totals", ""))
    assertEquals(panel.textContent.contains("two swords and a skull"), false)

  test("a choose-amount no roll belongs beside draws no roll"):
    val panel = render(new RecordingControls(),
      opened("challenge.amount", amount), "challenge.amount", amount)
    assertEquals(panel.querySelectorAll(".walker-roll-totals").length, 0)

  test("choose-many offers favor banks as toggles and submits the banks " +
      "chosen"):
    def bank(suit: String, label: String) =
      DecisionOptionState("favor-bank", suit, label)
    val banks = DecisionQueryState.ChooseMany(Vector(bank("discord", "Discord"),
      bank("order", "Order"), bank("hearth", "Hearth"), bank("beast", "Beast")),
      minOptions = 3, maxOptions = 3,
      heading = Some("Salad Days: choose three banks to gain 1 favor from each"))
    val id = "cardplay.salad-days.banks"
    val chosen = Vector("discord", "hearth", "beast")
    val ui = new RecordingControls()
    val draft = chosen.foldLeft(opened(id, banks)) { (current, suit) =>
      one(render(ui, current, id, banks),
        s"""[data-option-id="favor-bank:$suit"]""")
        .asInstanceOf[dom.html.Button].click()
      ui.drafts.selection
    }
    val panel = render(ui, draft, id, banks)
    assertEquals(panel.querySelectorAll(".walker-many-option").length, 4)
    val confirm = one(panel, ".walker-many-confirm").asInstanceOf[dom.html.Button]
    assert(!confirm.disabled)
    confirm.click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker(id,
      DecisionAnswerWire.ChooseManyWire(
        chosen.map(DecisionOptionWire("favor-bank", _))))))
