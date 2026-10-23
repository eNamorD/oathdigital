package oathdigital.frontend

import oathdigital.protocol.projection.DieFace

import oathdigital.frontend.Dom.{one, press}
import oathdigital.protocol.{DecisionAnswerWire, DecisionOptionWire,
  GameIntent => Intent}
import oathdigital.testkit.Unwrap.as
import ParkedDecision.Surface
import org.scalajs.dom

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

  private def button(root: dom.Element, selector: String): dom.html.Button =
    as[dom.html.Button](one(root, selector))

  test("choose-many renders a toggle per option and confirms only at the count"):
    val ui = new RecordingControls()
    val draft = opened("challenge.ribbon-site", many)
    assertEquals(one(render(ui, draft, "challenge.ribbon-site", many), "h2")
      .textContent, "Choose sites")
    val confirm0 = button(render(ui, draft, "challenge.ribbon-site", many),
      ".walker-many-confirm")
    assert(confirm0.disabled)
    button(render(ui, draft, "challenge.ribbon-site", many),
      """[data-option-id="site:a"]""").click()
    button(render(ui, ui.drafts.selection, "challenge.ribbon-site", many),
      """[data-option-id="site:c"]""").click()
    val panel = render(ui, ui.drafts.selection, "challenge.ribbon-site", many)
    assertEquals(one(panel, """[data-option-id="site:a"]""")
      .getAttribute("aria-pressed"), "true")
    val confirm = button(panel, ".walker-many-confirm")
    // A choose-many that declares no counted label gets the generic one.
    assertEquals(confirm.textContent, "Confirm")
    assert(!confirm.disabled)
    confirm.click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker("challenge.ribbon-site",
      DecisionAnswerWire.ChooseManyWire(Vector(DecisionOptionWire("site", "a"),
        DecisionOptionWire("site", "c"))))))

  /** Setup's Reveal Cards: the button says what pressing it does, counted
    * off the selection, and its own words at zero.
    */
  test("choose-many confirm counts the selection: none, one, then many"):
    val reveal = many.copy(minOptions = 0, maxOptions = 3,
      confirm = Some(CountedConfirmState("Reveal", "card", "cards",
        Some("Reveal nothing"))))
    val ui = new RecordingControls()
    def confirmText(draft: Option[WalkerSelectionDraft]) = one(render(ui,
      draft, "setup.reveal", reveal), ".walker-many-confirm").textContent
    assertEquals(confirmText(opened("setup.reveal", reveal)), "Reveal nothing")
    button(render(ui, opened("setup.reveal", reveal), "setup.reveal", reveal),
      """[data-option-id="site:a"]""").click()
    assertEquals(confirmText(ui.drafts.selection), "Reveal 1 card")
    button(render(ui, ui.drafts.selection, "setup.reveal", reveal),
      """[data-option-id="site:b"]""").click()
    assertEquals(confirmText(ui.drafts.selection), "Reveal 2 cards")

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
    as[dom.html.Input](one(panel, "input.walker-amount"))
  private def confirmOf(panel: dom.Element): dom.html.Button =
    button(panel, ".walker-amount-confirm")
  private def listOf(panel: dom.Element): dom.Element =
    one(panel, ".walker-amount-options")
  private def type_(input: dom.html.Input, value: String): Unit =
    input.value = value
    input.dispatchEvent(new dom.Event("input"))
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
    assertEquals(ui.drafts, SessionDrafts.empty)
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
    // The first press opened the list at the typed 3; the next two move to
    // 4 and 5, and a fourth, past the last option, stays on 5.
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
    assert(button(panel, ".walker-amount-toggle").disabled)
    assert(confirmOf(panel).disabled)
    mouseDown(one(panel, ".walker-amount-toggle"))
    assert(listOf(panel).hasAttribute("hidden"))

  test("the sacrifice panel draws dice, then totals, then the prompt"):
    val outcome = WalkerRollOutcomeState("campaign.attack",
      Vector(DieFace.TwoSwordsSkull, DieFace.OneSword), 3, None,
      Vector("1 skull loss"))
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
    assert(!panel.textContent.contains("two swords and a skull"))

  test("a choose-many beside a roll draws dice, then totals, then the " +
      "heading"):
    val outcome = WalkerRollOutcomeState("recover",
      Vector(DieFace.OneShield, DieFace.Blank), 1, Some(3), Vector.empty)
    val earlier = DecisionQueryState.ChooseMany(Vector(
      DecisionOptionState("button", "die-0", "Die 1: shield"),
      DecisionOptionState("button", "die-1", "Die 2: blank")),
      minOptions = 0, maxOptions = 2,
      heading = Some("Reroll earlier dice with this roll"))
    val panel = render(new RecordingControls(),
      opened("recover.silver-charm", earlier), "recover.silver-charm",
      earlier, rollOutcome = Some(outcome))
    one(panel, ".walker-roll-faces .die-faces")
    assertEquals(one(panel, ".walker-roll-totals").textContent,
      "Shields 1 · need 3")
    val order = panel.children.toVector.map(child =>
      Option(child.getAttribute("class")).getOrElse(""))
    assertEquals(order.take(3),
      Vector("walker-roll-faces", "walker-roll-totals", ""))

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
      button(render(ui, current, id, banks),
        s"""[data-option-id="favor-bank:$suit"]""").click()
      ui.drafts.selection
    }
    val panel = render(ui, draft, id, banks)
    assertEquals(panel.querySelectorAll(".walker-many-option").length, 4)
    val confirm = button(panel, ".walker-many-confirm")
    assert(!confirm.disabled)
    confirm.click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker(id,
      DecisionAnswerWire.ChooseManyWire(
        chosen.map(DecisionOptionWire("favor-bank", _))))))

  test("a locked option shows pressed and an unavailable one cannot be toggled"):
    val ui = new RecordingControls()
    val shown = many.copy(options = Vector(site("a").copy(state = "locked"),
      site("b"), site("c").copy(state = "unavailable",
        reason = Some("Not ruled by the defender"))), minOptions = 0,
      maxOptions = 1)
    val draft = opened("campaign.targets", shown)
    val panel = render(ui, draft, "campaign.targets", shown)
    val locked = button(panel, """[data-option-id="site:a"]""")
    assertEquals(locked.getAttribute("aria-pressed"), "true")
    assert(locked.disabled)
    val blocked = button(panel, """[data-option-id="site:c"]""")
    assertEquals(blocked.getAttribute("aria-disabled"), "true")
    assertEquals(blocked.getAttribute("data-tip"), "Not ruled by the defender")
    // The reason is only in the note, never printed beside the control.
    assert(!panel.textContent.contains("Not ruled by the defender"))
    blocked.click()
    assertEquals(ui.staged, Vector.empty)
    assertEquals(draft.collect { case d: WalkerChooseManyDraft => d }
      .map(_.toggle("site:c").selected), Some(Vector.empty))
    assertEquals(draft.collect { case d: WalkerChooseManyDraft => d }
      .map(_.toggle("site:a").selected), Some(Vector.empty))
