package oathdigital.frontend

import org.scalajs.dom

/** Panels that ask a player to pick one card out of several. A card the
  * player is allowed to read is drawn face-up whichever way it physically
  * lies, and the picking is a button of its own, so clicking the card still
  * means "show me this" everywhere in the game.
  */
class CardChoicePanelSuite extends munit.FunSuite {
  private def all(node: dom.Element, selector: String): Vector[dom.Element] =
    node.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  private def faces(node: dom.Element): Vector[String] =
    all(node, ".card-face .card-name").map(_.textContent)

  private def adviser(id: String, name: String): MinorAdviser =
    MinorAdviser(CardDetails(id, "denizen", name, suit = Some("order"),
      orientation = Some("face-down")), Vector.empty)

  private val draft = FacedownAdviserDraft(
    BoardSelectionContext("game", "red", 1L),
    Vector(adviser("a1", "Old Oak"), adviser("a2", "Bandits")), None)

  private def advisers(value: FacedownAdviserDraft): dom.Element =
    FacedownAdviserRenderer.render(value, new RecordingView("game", "red"))

  test("every facedown adviser on offer is drawn face-up") {
    val panel = advisers(draft)
    assertEquals(faces(panel), Vector("Old Oak", "Bandits"))
    assertEquals(all(panel, ".card-face-down"), Vector.empty)
    assertEquals(all(panel, ".card-back-letter"), Vector.empty)
  }

  test("choosing is a button of its own, and says which card it picks") {
    val panel = advisers(draft)
    val choices = all(panel, ".facedown-adviser-choice")
    assertEquals(choices.map(_.getAttribute("aria-label")),
      Vector("Choose Old Oak", "Choose Bandits"))
    assertEquals(choices.map(_.getAttribute("aria-pressed")),
      Vector("false", "false"))
    assertEquals(all(advisers(draft.choose("a2")), ".facedown-adviser-choice")
      .map(_.getAttribute("aria-pressed")), Vector("false", "true"))
  }

  /** One adviser is not a choice, so it gets no picker -- but it is still
    * the card the player is about to play, so it is still shown.
    */
  test("a lone adviser is shown without a picker") {
    val panel = advisers(draft.copy(advisers = draft.advisers.take(1),
      selectedCardId = Some("a1")))
    assertEquals(faces(panel), Vector("Old Oak"))
    assertEquals(all(panel, ".facedown-adviser-choice"), Vector.empty)
  }

  private val relicQuery = DecisionQueryState("choose-one",
    Vector(DecisionOptionState("relic", "relic:crown", "Ancient Crown",
      card = Some(CardDetails("relic:crown", "relic", "Ancient Crown",
        orientation = Some("face-down"), defense = Some(2)))),
      DecisionOptionState("relic", "relic:horn", "Brass Horn",
        card = Some(CardDetails("relic:horn", "relic", "Brass Horn",
          orientation = Some("face-down"), defense = Some(1))))),
    Vector.empty, heading = Some("Take a relic"))

  private def recoverRelics(): dom.Element = {
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderRecoverPanel(
      GameProjection("game", 1L, "act", Some("red"), Vector.empty, Vector.empty,
        Vector.empty, Vector.empty, ready = true, completed = false,
        walkerDecision = Some(WalkerDecisionState("recover",
          WalkerPanelSupport.recoverRelicDecisionId, "decide",
          query = Some(relicQuery)))),
      ServerUiSupport.ViewerPresentation(showGameplayControls = true, None, None),
      canControl = true, panel, new RecordingView("game", "red"))
    panel
  }

  test("a relic offered by Recover is drawn face-up, with its own take button") {
    val panel = recoverRelics()
    assertEquals(faces(panel), Vector("Ancient Crown", "Brass Horn"))
    assertEquals(all(panel, ".card-face-down"), Vector.empty)
    val takes = all(panel, ".recover-relic-choice")
    assertEquals(takes.map(_.textContent), Vector("Take facedown", "Take facedown"))
    assertEquals(takes.map(_.getAttribute("aria-label")),
      Vector("Take Ancient Crown facedown", "Take Brass Horn facedown"))
  }

  test("taking a relic submits the option the button belongs to") {
    val ui = new RecordingView("game", "red")
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderRecoverPanel(
      GameProjection("game", 1L, "act", Some("red"), Vector.empty, Vector.empty,
        Vector.empty, Vector.empty, ready = true, completed = false,
        walkerDecision = Some(WalkerDecisionState("recover",
          WalkerPanelSupport.recoverRelicDecisionId, "decide",
          query = Some(relicQuery)))),
      ServerUiSupport.ViewerPresentation(showGameplayControls = true, None, None),
      canControl = true, panel, ui)
    all(panel, ".recover-relic-choice").last.asInstanceOf[dom.html.Element].click()
    assertEquals(ui.submitted.size, 1)
    assert(ui.submitted.head.toString.contains("relic:horn"),
      ui.submitted.head.toString)
  }
}
