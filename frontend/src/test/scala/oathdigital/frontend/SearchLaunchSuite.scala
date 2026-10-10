package oathdigital.frontend

import oathdigital.frontend.Dom.one
import oathdigital.frontend.TestBoardOptions.recording
import oathdigital.frontend.TestSites.quietSite
import oathdigital.model.PlayerColor

/** The parked source decision draws each pile's cost or reason on the pile
  * (search source design, rulings 2 to 5). */
class SearchLaunchSuite extends munit.FunSuite:
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val woods = quietSite("woods", "Deep Woods")
  private val table = TestProjections.actingProjection(players = Vector(red),
    world = Vector(GameRegion("cradle", Vector(woods)))).copy(
    worldDeckCount = 20)

  test("a pile's badge is drawn on the pile"):
    val deck = OptionView(DecisionOptionState("deck", "world", "World deck",
      badge = Some("2 Supply")), OptionState.Available, None)
    val world = WorldBoardRenderer.world(table, recording(deck).options)
    val pile = one(world, """[data-option-ref="deck:world"]""")
    assert(pile.classList.contains("board-option"))
    assertEquals(one(pile, ".target-detail-badge").textContent, "2 Supply")

  test("an empty pile says why on hover"):
    val reason = "The Cradle discard pile is empty"
    val discard = OptionView(DecisionOptionState("discard-pile", "cradle",
      "Cradle discard pile", state = "unavailable", reason = Some(reason)),
      OptionState.Unavailable, Some(reason))
    val world = WorldBoardRenderer.world(table, recording(discard).options)
    val empty = one(world, """[data-option-ref="discard-pile:cradle"]""")
    assertEquals(empty.getAttribute("title"), reason)
    assertEquals(empty.getAttribute("aria-disabled"), "true")
