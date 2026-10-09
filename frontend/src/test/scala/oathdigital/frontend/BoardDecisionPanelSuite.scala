package oathdigital.frontend

import oathdigital.frontend.Dom.{all, one}
import oathdigital.frontend.TestBoardOptions.adviserPlayAtRedSlot3
import oathdigital.frontend.TestBoards.quietBoard
import oathdigital.frontend.TestSites.quietSite
import oathdigital.model.PlayerColor
import oathdigital.protocol.{DecisionAnswerWire, GameIntent => Intent,
  WalkerStartArgWire}
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
  private def site(id: String) = quietSite(id, s"Site $id")
  private val table = TestProjections.actingProjection(players = Vector(red),
    world = Vector(GameRegion("cradle", Vector(site("plains"),
      site("hills"), site("marsh")))))
    .copy(playerBoards = Vector(quietBoard("red")))
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
    "decide", presentation = "board")

  /** The panel of `parked` asking `query`; the query is the one the panel
    * reads, so `parked` carries none of its own.
    */
  private def pane(ui: RecordingControls, query: ParkedDecision.BoardForm,
      parked: WalkerDecisionState,
      draft: Option[BoardSelectionDraft] = None): dom.Element =
    val panel = dom.document.createElement("div")
    val drafts = SessionDrafts.empty.copy(context = Some(context),
      board = draft)
    ParkedDecision.render(table, ServerUiSupport.viewerPresentation(table,
      "red"), Routed(Some(Surface.BoardDecision(
      parked.copy(query = Some(query)), query)), None),
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
    val panel = pane(new RecordingControls(), needsOne, decision)
    assert(as[dom.html.Button](one(panel, ".board-decision-confirm")).disabled)
    assertEquals(one(panel, ".board-decision-reason").textContent,
      "Choose at least 1.")

  test("Clear appears with a pick and empties the draft"):
    val ui = new RecordingControls()
    assertEquals(all(pane(ui, targets, decision), ".board-decision-clear"),
      Vector.empty)
    as[dom.html.Button](one(pane(ui, targets, decision, picked("site:hills")),
      ".board-decision-clear")).click()
    assertEquals(ui.drafts.board, picked().map(_.cleared))

  test("a Decide button submits at once, under its own rule"):
    val ui = new RecordingControls()
    val plans = DecisionQueryState.ChooseOne(Vector(option("hills"),
      DecisionOptionState("button", "finish", "Finish battle plans")),
      Some("Choose a battle plan, or finish"))
    val panel = pane(ui, plans, decision.copy(decisionId =
      "campaign.attacker-plan"))
    assertEquals(all(panel, ".board-decision-rule").size, 1)
    val finish = as[dom.html.Button](one(panel, ".board-decision-decide"))
    assertEquals(finish.textContent, "Finish battle plans")
    finish.click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker(
      "campaign.attacker-plan",
      DecisionAnswerWire.ChooseOneWire("button", "finish"))))
    assertEquals(one(panel, ".board-decision-rule-labelled").textContent,
      "Decision")

  private def slot(site: String, at: Int) = DecisionOptionState("site-slot",
    s"$site:$at", s"Empty slot at Site $site")
  private val move = DecisionQueryState.ChooseOne(Vector(slot("hills", 0),
    slot("marsh", 1), DecisionOptionState("button", "skip", "Skip")),
    Some("Moving Market: move it to another site?"))
  private val moving = decision.copy(decisionId = "trade.moving-market.move")

  test("slots at several sites stay on the table, not in the panel"):
    val panel = pane(new RecordingControls(), move, moving)
    assertEquals(all(panel, ".board-decision-loose-option"), Vector.empty)

  test("a Skip button submits at once"):
    val ui = new RecordingControls()
    val skip = as[dom.html.Button](one(pane(ui, move, moving),
      ".board-decision-decide"))
    assertEquals(skip.textContent, "Skip")
    skip.click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker(
      "trade.moving-market.move",
      DecisionAnswerWire.ChooseOneWire("button", "skip"))))

  test("an option with no place on the table is a toggle in the panel"):
    val ui = new RecordingControls()
    val elsewhere = DecisionOptionState("relic", "cup", "Cup")
    val query = targets.copy(options = targets.options :+ elsewhere)
    val panel = pane(ui, query, decision)
    val toggle = as[dom.html.Button](one(panel,
      ".board-decision-loose-option"))
    assertEquals(toggle.textContent, "Cup")
    toggle.click()
    assertEquals(ui.drafts.board.map(_.keys), Some(Vector("relic:cup")))

  private val placement = DecisionQueryState.ChooseOne(
    Vector(adviserPlayAtRedSlot3("faceup"), adviserPlayAtRedSlot3("facedown")), Some("Play Wayward Spirit"))
  private val placing = WalkerDecisionState("search",
    "cardplay.place.denizen.spirit", "decide", presentation = "board")
  private def open(query: DecisionQueryState.ChooseOne, keys: String*) =
    Some(BoardSelectionDraft(context, "cardplay.place.denizen.spirit", query,
      keys.toVector, Some("adviser-slot:red:2")))
  private def variants(panel: dom.Element): Vector[dom.html.Button] =
    all(panel, ".board-decision-variant").map(as[dom.html.Button](_))

  test("an open home shows its options as a toggle with no default, and " +
      "Confirm waits for one"):
    val panel = pane(new RecordingControls(), placement, placing,
      open(placement))
    assertEquals(variants(panel).map(_.textContent), Vector("Faceup", "Facedown"))
    assertEquals(variants(panel).map(_.getAttribute("aria-pressed")),
      Vector("false", "false"))
    assertEquals(one(panel, ".board-decision-summary").textContent,
      "Choose one: Faceup or Facedown.")
    assert(as[dom.html.Button](one(panel, ".board-decision-confirm")).disabled)

  test("a click on a facing toggle drafts that facing"):
    val ui = new RecordingControls()
    variants(pane(ui, placement, placing, open(placement)))(1).click()
    assertEquals(ui.drafts.board.map(_.keys),
      Some(Vector("adviser-play:red:2:facedown")))

  test("a picked facing is pressed and the summary is its label"):
    val panel = pane(new RecordingControls(), placement, placing,
      open(placement, "adviser-play:red:2:faceup"))
    assertEquals(variants(panel).map(_.getAttribute("aria-pressed")),
      Vector("true", "false"))
    assertEquals(one(panel, ".board-decision-summary").textContent,
      "Adviser slot 3, faceup")

  test("Confirm submits the picked facing"):
    val ui = new RecordingControls()
    val panel = pane(ui, placement, placing,
      open(placement, "adviser-play:red:2:faceup"))
    as[dom.html.Button](one(panel, ".board-decision-confirm")).click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker(
      "cardplay.place.denizen.spirit",
      DecisionAnswerWire.ChooseOneWire("adviser-play", "red:2:faceup"))))

  test("an unavailable facing is disabled and says why"):
    val refused = DecisionQueryState.ChooseOne(Vector(
      adviserPlayAtRedSlot3("faceup").copy(state = "unavailable",
        reason = Some("Refused by a test power")), adviserPlayAtRedSlot3("facedown")),
      Some("Play Wayward Spirit"))
    val panel = pane(new RecordingControls(), refused, placing,
      open(refused, "adviser-play:red:2:facedown"))
    assertEquals(variants(panel).map(_.disabled), Vector(true, false))
    assertEquals(all(panel, ".board-decision-variant-reason")
      .map(_.textContent), Vector("Refused by a test power"))

  test("a Replace pick's summary is the sentence the projector wrote"):
    val replace = DecisionOptionState("site-slot", "plains:0",
      "Discard Tavern Songs; play Wayward Spirit here",
      replaces = Some(CardDetails("songs", "denizen", "Tavern Songs")))
    val query = DecisionQueryState.ChooseOne(Vector(replace),
      Some("Play Wayward Spirit"))
    val panel = pane(new RecordingControls(), query, placing,
      Some(BoardSelectionDraft(context,
        "cardplay.place.denizen.spirit", query, Vector("site-slot:plains:0"))))
    assertEquals(one(panel, ".board-decision-summary").textContent,
      "Discard Tavern Songs; play Wayward Spirit here")

  test("Clear closes an open home that has no pick yet"):
    val ui = new RecordingControls()
    val panel = pane(ui, placement, placing, open(placement))
    as[dom.html.Button](one(panel, ".board-decision-clear")).click()
    assertEquals(ui.drafts.board.map(draft => (draft.home, draft.keys)),
      Some((None, Vector.empty)))

  test("an askable Decide button asks before it sends"):
    val ui = new RecordingControls()
    var opened = Vector.empty[CardInspection.Request]
    CardInspection.onOpen(request => opened :+= request)
    try
      val play = DecisionOptionState("button", "play", "Play",
        state = "askable", reason = Some("Secret Police: Cannot play a " +
          "Vision faceup without ruler's consent."),
        consent = Some(ConsentAskState(Vector("red"), Vector("Secret Police"))))
      val placement = DecisionQueryState.ChooseOne(Vector(option("hills"),
        play))
      val panel = pane(ui, placement, decision.copy(decisionId =
        "cardplay.place.vision.vision:conspiracy"))
      val decide = as[dom.html.Button](one(panel, ".board-decision-decide"))
      assert(decide.classList.contains("board-option-askable"))
      assert(!decide.disabled)
      decide.click()
      assertEquals(ui.submitted, Vector.empty)
      opened match
        case Vector(confirm: CardInspection.Request.Confirm) =>
          confirm.confirm()
        case other => fail(s"expected one confirmation, got $other")
      assertEquals(ui.submitted, Vector(Intent.RequestConsent(
        "cardplay.place.vision.vision:conspiracy",
        WalkerStartArgWire("button", "play"))))
    finally CardInspection.clear()
