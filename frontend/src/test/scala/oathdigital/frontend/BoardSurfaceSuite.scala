package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.{DecisionAnswerWire, GameIntent => Intent}
import ParkedDecision.{Routed, Surface}
import org.scalajs.dom
import scala.scalajs.js

/** The board as the surface for a parked decision. A site is picked by
  * clicking it; a card inside the site is read by clicking it; the two never
  * mean each other. With `confirm`, a pick waits in a board draft for the
  * action pane's Confirm button. Without it, a pick submits at once. Runs
  * under jsdom, like `SiteBoxLayoutSuite`.
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
  private val query = DecisionQueryState("choose-one",
    Vector(woodsOption, cityOption), heading = Some("Choose your starting site"))
  private val decision = WalkerDecisionState("setup", "setup.pawn-placement.red",
    "decide", query = Some(query))
  private val context = BoardSelectionContext("game", "red", 1L)
  private val woodsDraft = WalkerBoardDraft(context, decision.decisionId, query,
    woodsOption)
  private val placeAtWoods = Intent.ResolveWalker(decision.decisionId,
    DecisionAnswerWire.ChooseOneWire("site", "site:woods"))

  private def projection(parked: Option[WalkerDecisionState] = Some(decision))
      : GameProjection =
    GameProjection("game", 1L, "setup-walker-decision", Some("red"), Vector(red),
      Vector(GameRegion("cradle", Vector(woods, city))), Vector.empty,
      Vector.empty, ready = false, completed = false, walkerDecision = parked)

  private def surface(confirm: Boolean): Surface.Board =
    Surface.Board(decision, query, confirm)

  private def world(ui: RecordingControls, board: Option[Surface.Board] =
      Some(surface(confirm = true)), draft: Option[WalkerBoardDraft] = None,
      targets: Option[BoardTargetSelectionState] = None): dom.Element =
    WorldBoardRenderer.world(projection(), board,
      ServerUiSupport.viewerPresentation(projection(), "red"),
      canControl = true,
      SessionDrafts.empty.copy(context = Some(context), board = draft,
        boardTargets = targets), ui)

  private def pane(ui: RecordingControls, board: Surface.Board,
      draft: Option[WalkerBoardDraft] = None): dom.Element =
    val panel = dom.document.createElement("div")
    ParkedDecision.render(projection(),
      ServerUiSupport.viewerPresentation(projection(), "red"),
      Routed(Some(board), None), canControl = true, panel,
      SessionDrafts.empty.copy(context = Some(context), board = draft), ui)
    panel

  private def one(root: dom.Element, selector: String): dom.Element =
    val found = root.querySelectorAll(selector)
    assertEquals(found.length, 1, s"expected one $selector")
    found(0).asInstanceOf[dom.Element]

  private def site(root: dom.Element, label: String): dom.Element =
    one(root, s"""article[aria-label="$label"]""")

  private def click(node: dom.Element): Unit =
    node.dispatchEvent(new dom.MouseEvent("click", js.Dynamic.literal(
      bubbles = true).asInstanceOf[dom.MouseEventInit]))

  private def press(node: dom.Element, key: String): Unit =
    node.dispatchEvent(new dom.KeyboardEvent("keydown", js.Dynamic.literal(
      bubbles = true, key = key).asInstanceOf[dom.KeyboardEventInit]))

  /** Records what the board-selection path chooses, which `RecordingControls`
    * drops.
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
    assertEquals(ui.submitted, Vector.empty)

  test("a card click inside a board-selection target does not choose it"):
    val ui = new SelectingView
    val targets = BoardTargetSelectionState(context,
      Vector(BoardTargetAction("travel", "Choose a destination", 1, 1,
        autoActivate = true, Vector(BoardTargetCandidate(
          BoardTargetRef.Site("site:woods"), "Deep Woods", Vector.empty)))),
      Some("travel"), Set.empty)
    val root = world(ui, board = None, targets = Some(targets))
    click(one(root, "button.card-face"))
    assertEquals(ui.chosen, Vector.empty)
    click(site(root, "Deep Woods"))
    assertEquals(ui.chosen.size, 1)

  test("with confirm, a site click drafts the pick and submits nothing"):
    val ui = new RecordingControls()
    click(site(world(ui), "Deep Woods"))
    assertEquals(ui.drafts.board, Some(woodsDraft))
    assertEquals(ui.submitted, Vector.empty)
    assertEquals(ui.staged.size, 1)

  test("a second click on the drafted site clears the draft"):
    val ui = new RecordingControls()
    click(site(world(ui, draft = Some(woodsDraft)), "Deep Woods"))
    assertEquals(ui.staged, Vector(Draft.Board(None)))

  test("a click on another site moves the draft"):
    val ui = new RecordingControls()
    click(site(world(ui, draft = Some(woodsDraft)), "Ancient City"))
    assertEquals(ui.drafts.board, Some(woodsDraft.copy(option = cityOption)))

  test("the drafted site is drawn selected"):
    val ui = new RecordingControls()
    val root = world(ui, draft = Some(woodsDraft))
    assert(site(root, "Deep Woods").getAttribute("class")
      .contains("board-target-selected"))
    assertEquals(site(root, "Deep Woods").getAttribute("aria-pressed"), "true")
    assertEquals(site(root, "Ancient City").getAttribute("aria-pressed"), "false")

  test("without confirm, a site click submits at once"):
    val ui = new RecordingControls()
    click(site(world(ui, Some(surface(confirm = false))), "Deep Woods"))
    assertEquals(ui.submitted, Vector(placeAtWoods))
    assertEquals(ui.staged, Vector.empty)

  test("the pane's Confirm waits for a draft, then submits it"):
    val ui = new RecordingControls()
    val empty = pane(ui, surface(confirm = true))
    assertEquals(one(empty, "h2").textContent, "Choose your starting site")
    assertEquals(one(empty, ".board-draft").textContent,
      "Choose a site on the board.")
    assert(one(empty, ".walker-board-confirm")
      .asInstanceOf[dom.html.Button].disabled)

    val drafted = pane(ui, surface(confirm = true), Some(woodsDraft))
    assertEquals(one(drafted, ".board-draft").textContent, "Deep Woods")
    val confirm = one(drafted, ".walker-board-confirm")
      .asInstanceOf[dom.html.Button]
    assert(!confirm.disabled)
    confirm.click()
    assertEquals(ui.submitted, Vector(placeAtWoods))

  test("without confirm, the pane offers no Confirm button"):
    val ui = new RecordingControls()
    val panel = pane(ui, surface(confirm = false))
    assertEquals(panel.querySelectorAll(".walker-board-confirm").length, 0)
    assertEquals(one(panel, ".board-draft").textContent,
      "Choose a site on the board.")

  test("a draft survives a refresh and dies with its decision or sequence"):
    val kept = WalkerBoardDraft.reconcile(Some(woodsDraft), context,
      Some(decision))
    assertEquals(kept, Some(woodsDraft))
    assertEquals(WalkerBoardDraft.reconcile(Some(woodsDraft),
      context.copy(sequence = 2L), Some(decision)), None)
    assertEquals(WalkerBoardDraft.reconcile(Some(woodsDraft), context,
      Some(decision.copy(decisionId = "setup.pawn-placement.blue"))), None)
    assertEquals(WalkerBoardDraft.reconcile(Some(woodsDraft), context,
      Some(decision.copy(query = Some(query.copy(options = Vector(woodsOption)))))),
      None)
    assertEquals(WalkerBoardDraft.reconcile(Some(woodsDraft), context, None),
      None)
    assertEquals(WalkerBoardDraft.reconcile(None, context, Some(decision)), None)
