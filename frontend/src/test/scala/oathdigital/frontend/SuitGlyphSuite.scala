package oathdigital.frontend

import oathdigital.model.PlayerColor

import org.scalajs.dom

/** A suit is read as its symbol everywhere else on the table, so the places
  * that name a favor bank print the symbol too rather than the word alone.
  */
class SuitGlyphSuite extends munit.FunSuite:
  private def glyphs(node: dom.Element): Vector[String] =
    node.querySelectorAll(".token-glyph").toVector
      .map(_.asInstanceOf[dom.Element].getAttribute("class"))

  test("the shared banks print each suit's symbol before its count"):
    val value = GameProjection("game", 1L, "act", Some("red"),
      Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
      Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false,
      favorBanks = Vector(FavorBankState("arcane", 3),
        FavorBankState("nomad", 0)))
    val panel = WorldBoardRenderer.world(value, None,
      canControl = true,
      SessionDrafts.empty, new RecordingControls())
    val banks = panel.querySelectorAll(".favor-bank").toVector
      .map(_.asInstanceOf[dom.Element])
    assertEquals(banks.map(_.textContent), Vector("Arcane: 3", "Nomad: 0"))
    assertEquals(banks.flatMap(glyphs),
      Vector("token-glyph token-suit-arcane", "token-glyph token-suit-nomad"))

  test("a bank offered as a choice carries its symbol too"):
    val query = DecisionQueryState.ChooseOne(
      Vector(DecisionOptionState("favor-bank", "hearth", "Hearth")),
      Some("Gain 3 favor from one bank"))
    val parked = WalkerDecisionState("use-power", "gambling-hall.bank",
      "decide", query = Some(query))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(
      ParkedDecision.Surface.ChooseOne(parked, query),
      GameProjection("game", 9L, "act", Some("red"), Vector.empty,
        Vector.empty, Vector.empty, Vector.empty, ready = true,
        completed = false),
      canControl = true, panel, new RecordingControls())
    val choice = panel.querySelector(".walker-choice").asInstanceOf[dom.Element]
    assertEquals(choice.textContent, "Hearth")
    assertEquals(glyphs(choice), Vector("token-glyph token-suit-hearth"))

  test("a bank offered beside a button keeps its symbol, and the button " +
      "has none"):
    val query = DecisionQueryState.ChooseOne(
      Vector(DecisionOptionState("favor-bank", "order", "Order"),
        DecisionOptionState("button", "burn",
          "Burn 1 favor from the People's Favor")),
      Some("Firebrand: add 1 favor to the People's Favor from a bank, or " +
        "burn 1 from it"))
    val parked = WalkerDecisionState("use-power", "power.firebrand.choice",
      "decide", query = Some(query))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(
      ParkedDecision.Surface.ChooseOne(parked, query),
      GameProjection("game", 9L, "act", Some("red"), Vector.empty,
        Vector.empty, Vector.empty, Vector.empty, ready = true,
        completed = false),
      canControl = true, panel, new RecordingControls())
    val choices = panel.querySelectorAll(".walker-choice").toVector
      .map(_.asInstanceOf[dom.Element])
    assertEquals(choices.map(_.textContent),
      Vector("Order", "Burn 1 favor from the People's Favor"))
    assertEquals(choices.map(glyphs(_).size), Vector(1, 0))

  test("a bank offered among several carries its symbol too"):
    val query = DecisionQueryState.ChooseMany(
      Vector(DecisionOptionState("favor-bank", "discord", "Discord"),
        DecisionOptionState("favor-bank", "beast", "Beast")),
      minOptions = 2, maxOptions = 2,
      heading = Some("Salad Days: choose banks to gain 1 favor from each"))
    val parked = WalkerDecisionState("use-power", "cardplay.salad-days.banks",
      "decide", query = Some(query))
    val draft = WalkerSelectionDraft.reconcile(None,
      BoardSelectionContext("game", "red", 9), Some(parked))
    val panel = dom.document.createElement("div")
    WalkerSelectionPanels.render(
      ParkedDecision.Surface.Selection(parked, query), draft,
      canControl = true, panel, new RecordingControls())
    val toggles = panel.querySelectorAll(".walker-many-option").toVector
      .map(_.asInstanceOf[dom.Element])
    assertEquals(toggles.map(_.textContent), Vector("Discord", "Beast"))
    assertEquals(toggles.flatMap(glyphs),
      Vector("token-glyph token-suit-discord", "token-glyph token-suit-beast"))
