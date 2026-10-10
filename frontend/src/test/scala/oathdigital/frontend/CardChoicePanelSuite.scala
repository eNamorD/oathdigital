package oathdigital.frontend

import ParkedDecision.{RecoverStep, Surface}
import org.scalajs.dom

/** Recover's relic panel asks a player to pick one card out of several. A card the
  * player is allowed to read is drawn face-up whichever way it physically
  * lies, and the picking is a button of its own, so clicking the card still
  * means "show me this" everywhere in the game.
  */
class CardChoicePanelSuite extends munit.FunSuite:
  private def faces(node: dom.Element): Vector[String] =
    Dom.all(node, ".card-face .card-name").map(_.textContent)

  private val relicQuery = DecisionQueryState.ChooseOne(
    Vector(DecisionOptionState("relic", "relic:crown", "Ancient Crown",
      card = Some(CardDetails("relic:crown", "relic", "Ancient Crown",
        orientation = Some("face-down"), defense = Some(2)))),
      DecisionOptionState("relic", "relic:horn", "Brass Horn",
        card = Some(CardDetails("relic:horn", "relic", "Brass Horn",
          orientation = Some("face-down"), defense = Some(1))))),
    heading = Some("Take a relic"))

  private val relicPark: Surface.Recover = Surface.Recover(WalkerDecisionState("recover",
    ParkedDecision.recoverRelicDecisionId, "decide",
    query = Some(relicQuery)), RecoverStep.Relic(relicQuery))

  private val table = GameProjection("game", 1L, "act", Some("red"),
    Vector.empty, Vector.empty, Vector.empty, ready = true,
    completed = false)

  private def recoverRelics(ui: RecordingControls = new RecordingControls())
      : dom.Element =
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderRecoverPanel(relicPark, table, canControl = true,
      panel, ui)
    panel

  test("a relic offered by Recover is drawn face-up, with its own take button"):
    val panel = recoverRelics()
    assertEquals(faces(panel), Vector("Ancient Crown", "Brass Horn"))
    assertEquals(Dom.all(panel, ".card-face-down"), Vector.empty)
    val takes = Dom.all(panel, ".recover-relic-choice")
    assertEquals(takes.map(_.textContent), Vector("Take facedown", "Take facedown"))
    assertEquals(takes.map(_.getAttribute("aria-label")),
      Vector("Take Ancient Crown facedown", "Take Brass Horn facedown"))

  test("taking a relic submits the option the button belongs to"):
    val ui = new RecordingControls()
    val panel = recoverRelics(ui)
    Dom.all(panel, ".recover-relic-choice").last
      .asInstanceOf[dom.html.Element].click()
    assertEquals(ui.submitted, Vector(oathdigital.protocol.GameIntent.ResolveWalker(
      ParkedDecision.recoverRelicDecisionId,
      oathdigital.protocol.DecisionAnswerWire.ChooseOneWire("relic", "relic:horn"))))
