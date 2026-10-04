package oathdigital.frontend

import oathdigital.frontend.Dom.{all, one}
import oathdigital.model.PlayerColor
import oathdigital.protocol.{DecisionAnswerWire, GameIntent => Intent}
import oathdigital.testkit.Unwrap.as
import ParkedDecision.{Routed, Surface}
import org.scalajs.dom

/** The Action panel of a board decision, under jsdom: the summary, the
  * locked and picked rows with their tally values, the totals, the panel-only
  * options, the Decide buttons, and Clear and Confirm.
  */
class BoardDecisionPanelSuite extends munit.FunSuite:
  private val context = BoardSelectionContext("game", "red", 1L)
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private def site(id: String) = GameSite(id, s"Site $id", looseFavor = 0,
    looseSecrets = 0, denizenCapacity = 3, relicCapacity = 0,
    denizens = Vector.empty, relics = GameSiteRelics(0), defense = 1)
  private val table = TestProjections.actingProjection(players = Vector(red),
    world = Vector(GameRegion("cradle", Vector(site("plains"),
      site("hills"), site("marsh")))))
  private def option(id: String) = DecisionOptionState("site", id, s"Site $id")
  private val origin = option("plains").copy(state = "locked")
  private val force = DecisionTallyState("Defending force", "warbands",
    Vector(DecisionTallyValueState("site", "plains", 2),
      DecisionTallyValueState("site", "hills", 1)))
  private val targets = DecisionQueryState.ChooseMany(
    Vector(origin, option("hills"), option("marsh")), 0, 2,
    Some("Also target these sites"), tallies = Vector(force),
    summary = Some(DecisionSummaryState("Target", "site", "sites")))
  private val decision = WalkerDecisionState("campaign", "campaign.targets",
    "decide", query = Some(targets), presentation = "board")

  private def pane(ui: RecordingControls, query: ParkedDecision.BoardForm,
      parked: WalkerDecisionState,
      draft: Option[BoardSelectionDraft] = None): dom.Element =
    val panel = dom.document.createElement("div")
    val drafts = SessionDrafts.empty.copy(context = Some(context),
      board = draft)
    ParkedDecision.render(table, ServerUiSupport.viewerPresentation(table,
      "red"), Routed(Some(Surface.BoardDecision(parked, query)), None),
      canControl = true, panel, drafts, ui)
    panel

  private def picked(keys: String*) = Some(BoardSelectionDraft(context,
    "campaign.targets", targets, keys.toVector))

  test("the summary counts the locked and picked targets, and each row " +
      "shows its tally values"):
    val panel = pane(new RecordingControls(), targets, decision,
      picked("site:hills"))
    assertEquals(one(panel, ".board-decision-summary").textContent,
      "Target 2 sites")
    assertEquals(all(panel, ".board-decision-row").map(row =>
      (row.getAttribute("data-option-ref"), row.textContent)), Vector(
      ("site:plains", "Locked: Site plains2 warbands"),
      ("site:hills", "Site hills1 warbands")))
    assertEquals(all(panel, ".board-decision-total").map(_.textContent),
      Vector("Defending force3"))

  test("Confirm submits the picks, and an empty optional selection too"):
    val ui = new RecordingControls()
    as[dom.html.Button](one(pane(ui, targets, decision, picked("site:hills")),
      ".board-decision-confirm")).click()
    as[dom.html.Button](one(pane(ui, targets, decision),
      ".board-decision-confirm")).click()
    assertEquals(ui.submitted, Vector(
      Intent.ResolveWalker("campaign.targets", DecisionAnswerWire
        .ChooseManyWire(Vector(oathdigital.protocol.DecisionOptionWire(
          "site", "hills")))),
      Intent.ResolveWalker("campaign.targets",
        DecisionAnswerWire.ChooseManyWire(Vector.empty))))

  test("Confirm is disabled below the minimum and says why"):
    val needsOne = targets.copy(minOptions = 1)
    val panel = pane(new RecordingControls(), needsOne,
      decision.copy(query = Some(needsOne)))
    assert(as[dom.html.Button](one(panel, ".board-decision-confirm")).disabled)
    assertEquals(one(panel, ".board-decision-reason").textContent,
      "Choose at least 1.")

  test("Clear appears with a pick and empties the draft"):
    val ui = new RecordingControls()
    assertEquals(all(pane(ui, targets, decision), ".board-decision-clear"),
      Vector.empty)
    as[dom.html.Button](one(pane(ui, targets, decision, picked("site:hills")),
      ".board-decision-clear")).click()
    assertEquals(ui.staged, Vector(Draft.Board(picked().map(_.cleared))))

  test("a Decide button submits at once, under its own rule"):
    val ui = new RecordingControls()
    val plans = DecisionQueryState.ChooseOne(Vector(option("hills"),
      DecisionOptionState("button", "finish", "Finish battle plans")),
      Some("Choose a battle plan, or finish"))
    val panel = pane(ui, plans, decision.copy(decisionId =
      "campaign.attacker-plan", query = Some(plans)))
    assertEquals(all(panel, ".board-decision-rule").size, 1)
    val finish = as[dom.html.Button](one(panel, ".board-decision-decide"))
    assertEquals(finish.textContent, "Finish battle plans")
    finish.click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker(
      "campaign.attacker-plan",
      DecisionAnswerWire.ChooseOneWire("button", "finish"))))
    assertEquals(one(panel, ".board-decision-rule-labelled").textContent,
      "Decision")

  test("an option with no place on the table is a toggle in the panel"):
    val ui = new RecordingControls()
    val elsewhere = DecisionOptionState("relic", "cup", "Cup")
    val query = targets.copy(options = targets.options :+ elsewhere)
    val panel = pane(ui, query, decision.copy(query = Some(query)))
    val toggle = as[dom.html.Button](one(panel,
      ".board-decision-loose-option"))
    assertEquals(toggle.textContent, "Cup")
    toggle.click()
    assertEquals(ui.drafts.board.map(_.keys), Some(Vector("relic:cup")))
