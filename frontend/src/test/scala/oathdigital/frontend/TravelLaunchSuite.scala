package oathdigital.frontend

import oathdigital.frontend.Dom.one
import oathdigital.frontend.TestBoardOptions.recording
import oathdigital.frontend.TestSites.quietSite
import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => Intent}
import oathdigital.testkit.Unwrap.as
import org.scalajs.dom

/** Travel starts the walker with no destination, and the parked destination
  * decision draws each site's cost or reason on its site box (travel
  * destination design, rulings 2 to 4). */
class TravelLaunchSuite extends munit.FunSuite:
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private def site(id: String, name: String) = quietSite(id, name)
  private val table = TestProjections.actingProjection(players = Vector(red),
    world = Vector(GameRegion("cradle", Vector(site("woods", "Deep Woods"),
      site("shore", "Desolate Shore")))),
    legalControls = Vector("beginTravel")).copy(actionSelectionOpen = true)

  test("the Travel control starts the walker and names no destination"):
    val ui = new RecordingControls()
    val pane = ActionDecisionRenderer.actionsPanel(table,
      ActionPanels.actingViewer("red"), ParkedDecision.Routed(None, None),
      canControl = true, SessionDrafts.empty, ui).element
    as[dom.html.Button](one(pane, ".travel-action")).click()
    assertEquals(ui.submitted, Vector(Intent.StartWalker("travel",
      Vector.empty)))

  test("a destination's badge is drawn on its site box"):
    val badged = OptionView(DecisionOptionState("site", "woods", "Deep Woods",
      badge = Some("2 Supply")), OptionState.Available, None)
    val world = WorldBoardRenderer.world(table, recording(badged).options)
    assertEquals(one(one(world, """article[aria-label="Deep Woods"]"""),
      ".target-detail-badge").textContent, "2 Supply")

  test("a refused destination says why on hover"):
    val reason = "Needs 3 Supply; you have 2"
    val refused = OptionView(DecisionOptionState("site", "shore",
      "Desolate Shore", state = "unavailable", reason = Some(reason)),
      OptionState.Unavailable, Some(reason))
    val world = WorldBoardRenderer.world(table, recording(refused).options)
    val shore = one(world, """article[aria-label="Desolate Shore"]""")
    assertEquals(shore.getAttribute("title"), reason)
    assertEquals(shore.getAttribute("aria-disabled"), "true")
