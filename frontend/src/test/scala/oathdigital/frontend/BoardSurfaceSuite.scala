package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.{DecisionAnswerWire, GameIntent => Intent}
import Dom.{click, one, press}
import ParkedDecision.{Routed, Surface}
import TestProjections.actingProjection
import org.scalajs.dom

/** The board as the surface for a parked decision. A site is picked by
  * clicking it; a card inside the site is read by clicking it; the two never
  * mean each other. A pick waits in a board draft for the action pane's
  * Confirm button. Runs under jsdom, like `SiteBoxLayoutSuite`.
  */
class BoardSurfaceSuite extends munit.FunSuite:
  private val fox = CardDetails("denizen:fox", "denizen", "Fox",
    rulesText = Some("Sly."))
  private val woods = GameSite("site:woods", "Deep Woods",
    looseFavor = 0, looseSecrets = 0, denizenCapacity = 3, relicCapacity = 0,
    denizens = Vector(GameSiteCard("denizen:fox", "Fox", Some(fox))),
    relics = GameSiteRelics(0), defense = 0)
  private val city = woods.copy(siteId = "site:city", label = "Ancient City",
    denizens = Vector.empty)
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)

  private val woodsOption = DecisionOptionState("site", "site:woods", "Deep Woods")
  private val cityOption = DecisionOptionState("site", "site:city", "Ancient City")
  private val query = DecisionQueryState.ChooseOne(
    Vector(woodsOption, cityOption), Some("Choose your starting site"))
  private val decision = WalkerDecisionState("setup", "setup.pawn-placement.red",
    "decide", query = Some(query), presentation = "board")
  private val context = BoardSelectionContext("game", "red", 1L)
  private val surface: Surface.BoardDecision =
    Surface.BoardDecision(decision, query)
  private val woodsDraft = BoardSelectionDraft(context, decision.decisionId,
    query, Vector("site:site:woods"))

  private val placeAtWoods = Intent.ResolveWalker(decision.decisionId,
    DecisionAnswerWire.ChooseOneWire("site", "site:woods"))

  private def projection(parked: Option[WalkerDecisionState] = Some(decision))
      : GameProjection =
    actingProjection(phase = "setup-walker-decision", players = Vector(red),
      world = Vector(GameRegion("cradle", Vector(woods, city))), ready = false)
      .copy(walkerDecision = parked)

  private def world(ui: RecordingControls,
      draft: Option[BoardSelectionDraft] = None,
      targets: Option[BoardTargetSelectionState] = None,
      board: Boolean = true): dom.Element =
    val drafts = SessionDrafts.empty.copy(context = Some(context),
      board = draft, boardTargets = targets)
    WorldBoardRenderer.world(projection(), if board then
      BoardOptions.of(surface, drafts, canControl = true, ui)
    else BoardOptions.none, drafts, ui)

  private def pane(ui: RecordingControls,
      draft: Option[BoardSelectionDraft] = None): dom.Element =
    val panel = dom.document.createElement("div")
    ParkedDecision.render(projection(),
      ServerUiSupport.viewerPresentation(projection(), "red"),
      Routed(Some(surface), None), canControl = true, panel,
      SessionDrafts.empty.copy(context = Some(context), board = draft), ui)
    panel

  private def site(root: dom.Element, label: String): dom.Element =
    one(root, s"""article[aria-label="$label"]""")

  /** Spy of `ActionControls`, through `RecordingControls`: also records what
    * the board-selection path chooses (`handleSelection`), which
    * `RecordingControls` drops.
    */
  private final class SelectingView extends RecordingControls:
    var chosen: Vector[BoardSelectionResult] = Vector.empty
    override def handleSelection(result: BoardSelectionResult): Unit =
      chosen :+= result

  test("a click on a card inside a site reads the card, not the site"):
    val ui = new RecordingControls()
    click(one(world(ui), "button.card-face"))
    assertEquals(ui.staged, Vector.empty)
    assertEquals(ui.submitted, Vector.empty)

  test("Enter on a card inside a site does not pick the site"):
    val ui = new RecordingControls()
    press(one(world(ui), "button.card-face"), "Enter")
    assertEquals(ui.staged, Vector.empty)

  test("a site click drafts the pick and submits nothing"):
    val ui = new RecordingControls()
    click(site(world(ui), "Deep Woods"))
    assertEquals(ui.drafts.board, Some(woodsDraft))
    assertEquals(ui.submitted, Vector.empty)

  test("a second click on the drafted site clears the pick"):
    val ui = new RecordingControls()
    click(site(world(ui, Some(woodsDraft)), "Deep Woods"))
    assertEquals(ui.drafts.board.map(_.keys), Some(Vector.empty))

  test("a click on another site moves the pick"):
    val ui = new RecordingControls()
    click(site(world(ui, Some(woodsDraft)), "Ancient City"))
    assertEquals(ui.drafts.board.map(_.keys), Some(Vector("site:site:city")))

  test("the drafted site is drawn selected"):
    val root = world(new RecordingControls(), Some(woodsDraft))
    assert(site(root, "Deep Woods").classList
      .contains("board-option-selected"))
    assertEquals(site(root, "Deep Woods").getAttribute("aria-pressed"), "true")
    assertEquals(site(root, "Ancient City").getAttribute("aria-pressed"),
      "false")

  test("the pane's Confirm waits for a pick, then submits it"):
    val ui = new RecordingControls()
    val empty = pane(ui)
    assertEquals(one(empty, "h2").textContent, "Choose your starting site")
    assertEquals(one(empty, ".board-decision-summary").textContent,
      "Choose on the board.")
    assert(one(empty, ".board-decision-confirm")
      .asInstanceOf[dom.html.Button].disabled)
    val drafted = pane(ui, Some(woodsDraft))
    assertEquals(one(drafted, ".board-decision-summary").textContent,
      "Deep Woods")
    val confirm = one(drafted, ".board-decision-confirm")
      .asInstanceOf[dom.html.Button]
    assertEquals(confirm.textContent, "Confirm")
    confirm.click()
    assertEquals(ui.submitted, Vector(placeAtWoods))

  test("a card click inside a board-selection target does not choose it"):
    val ui = new SelectingView
    val targets = BoardTargetSelectionState(context,
      Vector(BoardTargetAction("travel", "Choose a destination",
        minimum = 1, maximum = 1, autoActivate = true,
        Vector(BoardTargetCandidate(
          BoardTargetRef.Site("site:woods"), "Deep Woods", Vector.empty)))),
      Some("travel"), Set.empty)
    val root = world(ui, targets = Some(targets), board = false)
    click(one(root, "button.card-face"))
    assertEquals(ui.chosen, Vector.empty)
    click(site(root, "Deep Woods"))
    assertEquals(ui.chosen.size, 1)
