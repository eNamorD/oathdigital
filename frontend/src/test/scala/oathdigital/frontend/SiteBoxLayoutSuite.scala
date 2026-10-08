package oathdigital.frontend

import oathdigital.frontend.TestBoardOptions.recording
import oathdigital.frontend.TestSites.quietSite
import oathdigital.model.PlayerColor

import org.scalajs.dom

/** Every site box is the same size, whatever it happens to hold. What a site
  * has this turn -- a pawn standing on it, a Travel preview, forces -- must
  * not change the box, or the board shifts under the player mid-decision.
  */
class SiteBoxLayoutSuite extends munit.FunSuite:
  private val site = quietSite("site:woods", "Deep Woods")

  private def projection(pawns: Vector[GamePawn] = Vector.empty): GameProjection =
    GameProjection("game", 1L, "act", Some("red"),
      Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
      Vector(GameRegion("cradle", Vector(site))), pawns, Vector.empty,
      ready = true, completed = false)

  private def world(value: GameProjection,
      options: BoardOptions = BoardOptions.none): dom.Element =
    WorldBoardRenderer.world(value, options)

  /** The row is drawn empty rather than left out, so the cards under it start
    * at the same height on a site nobody stands on as on one they do.
    */
  test("a site nobody stands on still draws its pawn row"):
    val empty = Dom.all(world(projection()), ".site-pawns")
    assertEquals(empty.size, 1)
    assertEquals(empty.head.childNodes.length, 0)
    val occupied = Dom.all(
      world(projection(Vector(GamePawn("red", "site:woods")))), ".site-pawns")
    assertEquals(occupied.size, 1)
    assert(occupied.head.textContent.contains("Red"), occupied.head.textContent)

  /** Travel's supply preview is drawn over the site rather than in its flow,
    * so choosing an action cannot push the board about.
    */
  test("the travel supply badge hangs off the site, not inside its details"):
    val badged = OptionView(DecisionOptionState("site", "site:woods",
      "Deep Woods", badge = Some("2 Supply")), OptionState.Available, None)
    val node = world(projection(), recording(badged).options)
    val badges = Dom.all(node, ".target-detail-badge")
    assertEquals(badges.size, 1)
    assertEquals(badges.head.parentNode.asInstanceOf[dom.Element]
      .getAttribute("class").contains("site"), true)
    assertEquals(Dom.all(node, ".site-details .target-detail-badge"),
      Vector.empty)
