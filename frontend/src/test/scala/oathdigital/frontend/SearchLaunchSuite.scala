package oathdigital.frontend

import oathdigital.frontend.Dom.{all, one}
import oathdigital.frontend.TestBoardOptions.recording
import oathdigital.frontend.TestSites.quietSite
import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => Intent}
import oathdigital.testkit.Unwrap.as
import org.scalajs.dom

/** Search starts the walker with no source, and the parked source decision
  * draws each pile's cost or reason on the pile (search source design,
  * rulings 2 to 5). */
class SearchLaunchSuite extends munit.FunSuite:
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val woods = quietSite("woods", "Deep Woods")
  private val table = TestProjections.actingProjection(players = Vector(red),
    world = Vector(GameRegion("cradle", Vector(woods))),
    legalControls = Vector("beginSearch"))
    .copy(actionSelectionOpen = true, worldDeckCount = 20)

  private def actions(value: GameProjection, ui: RecordingControls)
      : dom.Element = ActionDecisionRenderer.actionsPanel(value,
    ActionPanels.actingViewer("red"), ParkedDecision.Routed(None, None),
    canControl = true, SessionDrafts.empty, ui).element

  test("the Search control starts the walker and names no source"):
    val ui = new RecordingControls()
    val search = as[dom.html.Button](one(actions(table, ui), ".search-action"))
    assertEquals(search.textContent, "Search")
    search.click()
    assertEquals(ui.submitted, Vector(Intent.StartWalker("search",
      Vector.empty)))

  test("no Search control is drawn without beginSearch"):
    val pane = actions(table.copy(legalControls = Vector.empty),
      new RecordingControls())
    assertEquals(all(pane, ".search-action").size, 0)

  test("a pile's badge is drawn on the pile, and an empty pile says why on " +
      "hover"):
    val reason = "The Cradle discard pile is empty"
    val deck = OptionView(DecisionOptionState("deck", "world", "World deck",
      badge = Some("2 Supply")), OptionState.Available, None)
    val discard = OptionView(DecisionOptionState("discard-pile", "cradle",
      "Cradle discard pile", state = "unavailable", reason = Some(reason)),
      OptionState.Unavailable, Some(reason))
    val world = WorldBoardRenderer.world(table,
      recording(deck, discard).options)
    val pile = one(world, """[data-option-ref="deck:world"]""")
    assert(pile.classList.contains("board-option"))
    assertEquals(one(pile, ".target-detail-badge").textContent, "2 Supply")
    val empty = one(world, """[data-option-ref="discard-pile:cradle"]""")
    assertEquals(empty.getAttribute("title"), reason)
    assertEquals(empty.getAttribute("aria-disabled"), "true")
