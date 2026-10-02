package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionOptionWire,
  GameIntent => Intent}
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

  test("choose-amount renders a dropdown over its range and submits the choice"):
    val ui = new RecordingControls()
    val panel = render(ui, opened("challenge.amount", amount),
      "challenge.amount", amount)
    val select = one(panel, "select.walker-amount").asInstanceOf[dom.html.Select]
    assertEquals((0 until select.options.length).map(i =>
      select.options(i).value), Vector("3", "4", "5"))
    assertEquals(select.value, "3")
    select.value = "5"
    select.dispatchEvent(new dom.Event("change"))
    assertEquals(ui.staged, Vector.empty)
    val confirm = one(panel, ".walker-amount-confirm").asInstanceOf[dom.html.Button]
    assertEquals(confirm.textContent, "Take banner")
    confirm.click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker("challenge.amount",
      DecisionAnswerWire.ChooseAmountWire(5))))

  test("a viewer who cannot control sees disabled controls"):
    val panel = render(new RecordingControls(),
      opened("challenge.amount", amount), "challenge.amount", amount,
      canControl = false)
    assert(one(panel, "select.walker-amount").asInstanceOf[dom.html.Select].disabled)
    assert(one(panel, ".walker-amount-confirm").asInstanceOf[dom.html.Button].disabled)

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
