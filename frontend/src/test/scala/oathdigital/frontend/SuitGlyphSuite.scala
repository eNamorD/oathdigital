package oathdigital.frontend

import oathdigital.model.PlayerColor

import org.scalajs.dom

/** A suit is read as its symbol everywhere else on the table, so the places
  * that name a favor bank print the symbol too rather than the word alone.
  */
class SuitGlyphSuite extends munit.FunSuite:
  private def glyphs(node: dom.Element): Vector[String] =
    Dom.all(node, ".token-glyph").map(_.getAttribute("class"))

  test("the shared banks print each suit's symbol before its count"):
    val value = TestProjections.actingProjection(players = Vector(
      GamePlayer("red", "Red", "Exile", PlayerColor.Red)))
      .copy(favorBanks = Vector(FavorBankState("arcane", 3),
        FavorBankState("nomad", 0)))
    val panel = WorldBoardRenderer.world(value, BoardOptions.none)
    val banks = Dom.all(panel, ".favor-bank")
    assertEquals(banks.map(_.textContent), Vector("Arcane: 3", "Nomad: 0"))
    assertEquals(banks.flatMap(glyphs),
      Vector("token-glyph token-suit-arcane", "token-glyph token-suit-nomad"))

  test("a bank offered as a choice carries its symbol too"):
    val query = DecisionQueryState.ChooseOne(
      Vector(DecisionOptionState("favor-bank", "hearth", "Hearth")),
      Some("Gain 3 favor from one bank"))
    val parked = WalkerDecisionState("use-power", "gambling-hall.bank",
      query = Some(query))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(
      ParkedDecision.Surface.ChooseOne(parked, query),
      TestProjections.actingProjection(nextSequence = 9L),
      canControl = true, panel, new RecordingControls())
    val choice = Dom.one(panel, ".walker-choice")
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
      query = Some(query))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(
      ParkedDecision.Surface.ChooseOne(parked, query),
      TestProjections.actingProjection(nextSequence = 9L),
      canControl = true, panel, new RecordingControls())
    val choices = Dom.all(panel, ".walker-choice")
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
      query = Some(query))
    val draft = WalkerSelectionDraft.reconcile(None,
      BoardSelectionContext("game", "red", 9), Some(parked))
    val panel = dom.document.createElement("div")
    WalkerSelectionPanels.render(
      ParkedDecision.Surface.Selection(parked, query), draft,
      canControl = true, panel, new RecordingControls())
    val toggles = Dom.all(panel, ".walker-many-option")
    assertEquals(toggles.map(_.textContent), Vector("Discord", "Beast"))
    assertEquals(toggles.flatMap(glyphs),
      Vector("token-glyph token-suit-discord", "token-glyph token-suit-beast"))

  test("a secret is a solid book and a defense die a shield; both, and the " +
      "burnt secret, are filled even-odd"):
    val root = dom.document.createElement("div")
    TokenSprite.mount(root)
    def path(id: String): dom.Element =
      Option(root.querySelector(s"symbol#token-$id path"))
        .getOrElse(fail(s"no path for $id"))
    // The book: its cover outline (a rounded corner at the top right, one at
    // the bottom left), then the spine notch that even-odd cuts out of it.
    assert(path("secret").getAttribute("d").startsWith("M4 3h13a2 2 0 0 1 2 2v16H7a3 3 0 0 1-3-3zM8 6v12"))
    // The shield: its outline from the top point to the right shoulder.
    assert(path("defense-die").getAttribute("d").startsWith("M12 1.5 20.5 4.8"))
    assertEquals(path("secret").getAttribute("fill-rule"), "evenodd")
    assertEquals(path("secret-burnt").getAttribute("fill-rule"), "evenodd")
    assertEquals(path("defense-die").getAttribute("fill-rule"), "evenodd")
