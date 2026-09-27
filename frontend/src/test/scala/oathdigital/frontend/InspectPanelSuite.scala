package oathdigital.frontend

import org.scalajs.dom
import oathdigital.protocol.{DecisionAnswerWire, GameIntent}

/** The Inspect decision's panel: the cards in order, faces and backs, and
  * the one Done button. Runs under jsdom. */
class InspectPanelSuite extends munit.FunSuite:
  private val oak = CardDetails("d1", "denizen", "Old Oak",
    suit = Some("order"))
  private val back = CardDetails("hidden", "vision", "Facedown vision",
    orientation = Some("face-down"), hidden = true)
  private val done = DecisionOptionState("button", "done", "Done")
  private val query = DecisionQueryState.Inspect(Vector(oak, back), done,
    Some("Scryer: the Cradle discard pile"))
  private val decision = WalkerDecisionState("use-power",
    "power.scryer.inspect", "decide", query = Some(query))

  private def render(canControl: Boolean, ui: RecordingControls): dom.Element =
    val panel = dom.document.createElement("div")
    InspectPanel.render(ParkedDecision.Surface.Inspect(decision, query),
      canControl, panel, ui)
    panel

  private def doneButton(panel: dom.Element): dom.html.Button =
    panel.querySelector("button.walker-choice").asInstanceOf[dom.html.Button]

  test("the panel shows its heading and every card in order, face or back"):
    val panel = render(canControl = true, new RecordingControls())
    assertEquals(panel.querySelector("h2").textContent,
      "Scryer: the Cradle discard pile")
    assertEquals(panel.querySelector(".card-list-title").textContent, "2 cards")
    val faces = panel.querySelectorAll(".card-list-item .card-face").toVector
      .map(_.asInstanceOf[dom.Element])
    assertEquals(faces.map(_.getAttribute("data-card-id")),
      Vector("d1", "hidden"))
    assert(faces(1).classList.contains("card-face-down"))

  test("Done answers with its one button, and only for a player in control"):
    val ui = new RecordingControls()
    doneButton(render(canControl = true, ui)).click()
    assertEquals(ui.submitted, Vector(GameIntent.ResolveWalker(
      "power.scryer.inspect", DecisionAnswerWire.ChooseOneWire("button", "done"))))
    assert(doneButton(render(canControl = false, new RecordingControls()))
      .disabled)
