package oathdigital.frontend

import oathdigital.model.PlayerColor

import org.scalajs.dom

/** A suit is read as its symbol everywhere else on the table, so the places
  * that name a favor bank print the symbol too rather than the word alone.
  */
class SuitGlyphSuite extends munit.FunSuite {
  private val presentation = ServerUiSupport.ViewerPresentation(
    showGameplayControls = true, None, None)

  private def glyphs(node: dom.Element): Vector[String] =
    node.querySelectorAll(".token-glyph").toVector
      .map(_.asInstanceOf[dom.Element].getAttribute("class"))

  test("the shared banks print each suit's symbol before its count") {
    val panel = WorldBoardRenderer.world(
      GameProjection("game", 1L, "act", Some("red"),
        Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
        Vector.empty, Vector.empty, Vector.empty, ready = true,
        completed = false,
        favorBanks = Vector(FavorBankState("arcane", 3),
          FavorBankState("nomad", 0))),
      presentation, new RecordingView("game", "red"))
    val banks = panel.querySelectorAll(".favor-bank").toVector
      .map(_.asInstanceOf[dom.Element])
    assertEquals(banks.map(_.textContent), Vector("Arcane: 3", "Nomad: 0"))
    assertEquals(banks.flatMap(glyphs),
      Vector("token-glyph token-suit-arcane", "token-glyph token-suit-nomad"))
  }

  test("a bank offered as a choice carries its symbol too") {
    val parked = WalkerDecisionState("use-power", "gambling-hall.bank",
      "decide", query = Some(DecisionQueryState("choose-one",
        Vector(DecisionOptionState("favor-bank", "hearth", "Hearth")),
        heading = Some("Gain 3 favor from one bank"))))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(
      GameProjection("game", 9L, "act", Some("red"), Vector.empty,
        Vector.empty, Vector.empty, Vector.empty, ready = true,
        completed = false, walkerDecision = Some(parked)),
      presentation, canControl = true, panel, new RecordingView("game", "red"))
    val choice = panel.querySelector(".walker-choice").asInstanceOf[dom.Element]
    assertEquals(choice.textContent, "Hearth")
    assertEquals(glyphs(choice), Vector("token-glyph token-suit-hearth"))
  }
}
