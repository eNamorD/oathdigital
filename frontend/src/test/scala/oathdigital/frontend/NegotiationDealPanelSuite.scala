package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, GameIntent => Intent,
  NegotiationTerms, NegotiationTransfer}
import org.scalajs.dom

/** The deal panel for a parked negotiation, as an owner and as a spectator. */
class NegotiationDealPanelSuite extends munit.FunSuite {
  private val presentation = ServerUiSupport.ViewerPresentation(
    showGameplayControls = true, None, None)
  // Faceup, so it is offered as a transfer only and not also as a disclosure.
  private val relic = CardDetails("r1", "relic", "Relic One",
    orientation = Some("face-up"))

  private def deal(canAccept: Boolean = true, editing: Boolean = true) =
    NegotiationDealState(Vector("red", "blue"), Vector("blue"),
      Vector(NegotiationTransferState("blue", "red", 2, 0, Vector.empty)),
      Vector(NegotiationDisclosureState("blue", "red", "held-relic", None)),
      Option.when(editing)(NegotiationEditingState(5, Vector(relic),
        Vector.empty, Vector.empty, canAccept)))

  private def owner(value: NegotiationDealState): GameProjection =
    GameProjection("game", 9L, "act-action-selection", Some("red"), Vector.empty,
      Vector.empty, Vector.empty, Vector("resolveWalkerDecision"), ready = true,
      completed = false, walkerDecision = Some(WalkerDecisionState("negotiation",
        "negotiation.deal", "decide", query = Some(DecisionQueryState("negotiate",
          Vector.empty, heading = Some("Negotiation"), deal = Some(value))))))

  private def spectator(value: NegotiationDealState): GameProjection =
    GameProjection("game", 9L, "act-action-selection", Some("red"), Vector.empty,
      Vector.empty, Vector.empty, Vector.empty, ready = true, completed = false,
      walkerWaiting = Some(WalkerWaitingState("red", Some("Negotiation"),
        Vector("blue"), Some(value))))

  private def render(projection: GameProjection, ui: RecordingView,
      canControl: Boolean = true, shows: Boolean = true): dom.Element = {
    val panel = dom.document.createElement("div")
    NegotiationDealPanel.render(projection, presentation.copy(
      showGameplayControls = shows), canControl, panel, ui)
    panel
  }

  private def one(root: dom.Element, selector: String): dom.Element = {
    val found = root.querySelectorAll(selector)
    assertEquals(found.length, 1, s"expected exactly one $selector")
    found(0).asInstanceOf[dom.Element]
  }

  test("an owner sees the deal and can save, accept and decline") {
    val ui = new RecordingView("game", "red")
    val panel = render(owner(deal()), ui)
    assertEquals(one(panel, "h2").textContent, "Negotiation")
    assertEquals(one(panel, ".negotiation-status").textContent,
      "red: reviewing · blue: accepted")
    assertEquals(one(panel, ".negotiation-transfer").textContent,
      "blue gives red: 2 favor, 0 relic(s)")
    assertEquals(one(panel, ".negotiation-disclosure").textContent,
      "blue promises red a held-relic disclosure")
    val favor = one(panel, "input[type=number]").asInstanceOf[dom.html.Input]
    favor.value = "3"
    one(panel, "input[type=checkbox]").asInstanceOf[dom.html.Input].checked = true
    one(panel, ".negotiation-save").asInstanceOf[dom.html.Button].click()
    one(panel, ".negotiation-accept").asInstanceOf[dom.html.Button].click()
    one(panel, ".negotiation-decline").asInstanceOf[dom.html.Button].click()
    assertEquals(ui.submitted, Vector[Intent](
      Intent.ResolveWalker("negotiation.deal", DecisionAnswerWire.ProposeTermsWire(
        NegotiationTerms(Vector(NegotiationTransfer("blue", 3, Vector("r1"))),
          Vector.empty))),
      Intent.ResolveWalker("negotiation.deal", DecisionAnswerWire.AcceptDealWire),
      Intent.ResolveWalker("negotiation.deal", DecisionAnswerWire.DeclineDealWire)))
  }

  test("accept is disabled until the engine says this player may accept") {
    val ui = new RecordingView("game", "red")
    val panel = render(owner(deal(canAccept = false)), ui)
    assert(one(panel, ".negotiation-accept").asInstanceOf[dom.html.Button].disabled)
    assert(!one(panel, ".negotiation-decline").asInstanceOf[dom.html.Button].disabled)
    val blocked = render(owner(deal()), ui, canControl = false)
    assert(one(blocked, ".negotiation-save").asInstanceOf[dom.html.Button].disabled)
  }

  test("a spectator sees the deal read-only, with no inputs or buttons") {
    val ui = new RecordingView("game", "green")
    val panel = render(spectator(deal(editing = false)), ui, shows = false)
    assertEquals(one(panel, ".negotiation-status").textContent,
      "red: reviewing · blue: accepted")
    assertEquals(panel.querySelectorAll("input").length, 0)
    assertEquals(panel.querySelectorAll("button").length, 0)
  }

  test("nothing renders when no negotiation is parked") {
    val ui = new RecordingView("game", "red")
    val empty = GameProjection("game", 9L, "act-action-selection", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false)
    assertEquals(render(empty, ui).childNodes.length, 0)
  }
}
