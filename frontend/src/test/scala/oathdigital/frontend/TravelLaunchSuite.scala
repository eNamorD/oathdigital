package oathdigital.frontend

import oathdigital.frontend.Dom.one
import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => Intent}
import oathdigital.testkit.Unwrap.as
import org.scalajs.dom

/** Travel starts the walker with no destination, and the parked destination
  * decision draws each site's cost or reason on its site box (travel
  * destination design, rulings 2 to 4). */
class TravelLaunchSuite extends munit.FunSuite:
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private def site(id: String, name: String) = GameSite(id, name,
    looseFavor = 0, looseSecrets = 0, denizenCapacity = 3, relicCapacity = 0,
    denizens = Vector.empty, relics = GameSiteRelics(0), defense = 1)
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

  test("a destination's badge is drawn on its site box, and a refused one " +
      "says why on hover"):
    val reason = "Needs 3 Supply; you have 2"
    val badged = OptionView(DecisionOptionState("site", "woods", "Deep Woods",
      badge = Some("2 Supply")), OptionState.Available, None)
    val refused = OptionView(DecisionOptionState("site", "shore",
      "Desolate Shore", state = "unavailable", reason = Some(reason)),
      OptionState.Unavailable, Some(reason))
    val options = new BoardOptions(Vector(badged, refused).map(view =>
      BoardOptionViews.home(view.option) -> view).toMap, _ => ())
    val world = WorldBoardRenderer.world(table, options)
    assertEquals(one(one(world, """article[aria-label="Deep Woods"]"""),
      ".target-detail-badge").textContent, "2 Supply")
    val shore = one(world, """article[aria-label="Desolate Shore"]""")
    assertEquals(shore.getAttribute("title"), reason)
    assertEquals(shore.getAttribute("aria-disabled"), "true")
