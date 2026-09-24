package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionOptionWire,
  GameIntent => Intent}
import org.scalajs.dom

class WalkerSelectionPanelsSuite extends munit.FunSuite {
  private def site(id: String) = DecisionOptionState("site", id, s"Site $id")
  private val many = DecisionQueryState("choose-many",
    Vector(site("a"), site("b"), site("c")), heading = Some("Choose sites"),
    minimum = Some(2), maximum = Some(2))
  private val amount = DecisionQueryState("choose-amount", Vector.empty,
    heading = Some("Place more than 2 favor"), confirmLabel = Some("Take banner"),
    minimum = Some(3), maximum = Some(5))
  private val presentation = ServerUiSupport.ViewerPresentation(
    showGameplayControls = true, None, None)

  private def projection(id: String, query: DecisionQueryState,
      rollOutcome: Option[WalkerRollOutcomeState] = None): GameProjection =
    GameProjection("game", 9L, "act-action-selection", Some("red"), Vector.empty,
      Vector.empty, Vector.empty, Vector.empty, ready = true, completed = false,
      walkerDecision = Some(WalkerDecisionState("challenge", id, "decide",
        query = Some(query), rollOutcome = rollOutcome)))

  private def opened(id: String, query: DecisionQueryState) = {
    val ui = new RecordingView("game", "red")
    ui.currentWalkerSelection = WalkerSelectionDraft.reconcile(None,
      BoardSelectionContext("game", "red", 9), projection(id, query).walkerDecision)
    ui
  }

  private def render(ui: RecordingView, id: String, query: DecisionQueryState,
      canControl: Boolean = true,
      rollOutcome: Option[WalkerRollOutcomeState] = None): dom.Element = {
    val panel = dom.document.createElement("div")
    WalkerSelectionPanels.render(projection(id, query, rollOutcome), presentation,
      canControl, panel, ui)
    panel
  }

  private def one(root: dom.Element, selector: String): dom.Element = {
    val found = root.querySelectorAll(selector)
    assertEquals(found.length, 1, s"expected exactly one $selector")
    found(0).asInstanceOf[dom.Element]
  }

  test("choose-many renders a toggle per option and confirms only at the count") {
    val ui = opened("challenge.ribbon-site", many)
    assertEquals(one(render(ui, "challenge.ribbon-site", many), "h2").textContent,
      "Choose sites")
    val confirm0 = one(render(ui, "challenge.ribbon-site", many),
      ".walker-many-confirm").asInstanceOf[dom.html.Button]
    assert(confirm0.disabled)
    one(render(ui, "challenge.ribbon-site", many),
      """[data-option-id="site:a"]""").asInstanceOf[dom.html.Button].click()
    one(render(ui, "challenge.ribbon-site", many),
      """[data-option-id="site:c"]""").asInstanceOf[dom.html.Button].click()
    val panel = render(ui, "challenge.ribbon-site", many)
    assertEquals(one(panel, """[data-option-id="site:a"]""")
      .getAttribute("aria-pressed"), "true")
    val confirm = one(panel, ".walker-many-confirm").asInstanceOf[dom.html.Button]
    assert(!confirm.disabled)
    confirm.click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker("challenge.ribbon-site",
      DecisionAnswerWire.ChooseManyWire(Vector(DecisionOptionWire("site", "a"),
        DecisionOptionWire("site", "c"))))))
  }

  test("choose-amount renders a dropdown over its range and submits the choice") {
    val ui = opened("challenge.amount", amount)
    val panel = render(ui, "challenge.amount", amount)
    val select = one(panel, "select.walker-amount").asInstanceOf[dom.html.Select]
    assertEquals((0 until select.options.length).map(i =>
      select.options(i).value), Vector("3", "4", "5"))
    assertEquals(select.value, "3")
    select.value = "5"
    select.dispatchEvent(new dom.Event("change"))
    val confirm = one(panel, ".walker-amount-confirm").asInstanceOf[dom.html.Button]
    assertEquals(confirm.textContent, "Take banner")
    confirm.click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker("challenge.amount",
      DecisionAnswerWire.ChooseAmountWire(5))))
  }

  test("a viewer who cannot control sees disabled controls") {
    val ui = opened("challenge.amount", amount)
    val panel = render(ui, "challenge.amount", amount, canControl = false)
    assert(one(panel, "select.walker-amount").asInstanceOf[dom.html.Select].disabled)
    assert(one(panel, ".walker-amount-confirm").asInstanceOf[dom.html.Button].disabled)
  }

  test("the sacrifice panel draws dice, then totals, then the prompt") {
    val outcome = WalkerRollOutcomeState("campaign.attack",
      Vector("two-swords-skull", "one-sword"), 3, None, Vector("1 skull loss"))
    val sacrifice = DecisionQueryState("choose-amount", Vector.empty,
      heading = Some("Sacrifice up to 2 warbands for one attack each"),
      confirmLabel = Some("Sacrifice"), minimum = Some(0), maximum = Some(2))
    val ui = opened("campaign.sacrifice", sacrifice)
    val panel = render(ui, "campaign.sacrifice", sacrifice,
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
  }

  test("a choose-amount no roll belongs beside draws no roll") {
    val ui = opened("challenge.amount", amount)
    val panel = render(ui, "challenge.amount", amount)
    assertEquals(panel.querySelectorAll(".walker-roll-totals").length, 0)
  }
}
