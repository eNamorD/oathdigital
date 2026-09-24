package oathdigital.frontend

import oathdigital.model.PlayerColor

import org.scalajs.dom

/** Every site box is the same size, whatever it happens to hold. What a site
  * has this turn -- a pawn standing on it, a Travel preview, forces -- must
  * not change the box, or the board shifts under the player mid-decision.
  */
class SiteBoxLayoutSuite extends munit.FunSuite {
  private def all(node: dom.Element, selector: String): Vector[dom.Element] =
    node.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  private val site = GameSite("site:woods", "Deep Woods",
    looseFavor = 0, looseSecrets = 0, denizenCapacity = 3, relicCapacity = 0,
    denizens = Vector.empty, relics = GameSiteRelics(0), defense = 0)

  private def projection(pawns: Vector[GamePawn] = Vector.empty): GameProjection =
    GameProjection("game", 1L, "act", Some("red"),
      Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
      Vector(GameRegion("cradle", Vector(site))), pawns, Vector.empty,
      ready = true, completed = false)

  private def world(value: GameProjection, ui: ServerUiView): dom.Element =
    WorldBoardRenderer.world(value, ServerUiSupport.ViewerPresentation(
      showGameplayControls = true, None, None), ui)

  /** The row is drawn empty rather than left out, so the cards under it start
    * at the same height on a site nobody stands on as on one they do.
    */
  test("a site nobody stands on still draws its pawn row") {
    val empty = all(world(projection(), new RecordingView("game", "red")), ".site-pawns")
    assertEquals(empty.size, 1)
    assertEquals(empty.head.childNodes.length, 0)
    val occupied = all(world(projection(Vector(GamePawn("red", "site:woods"))),
      new RecordingView("game", "red")), ".site-pawns")
    assertEquals(occupied.size, 1)
    assert(occupied.head.textContent.contains("Red"), occupied.head.textContent)
  }

  /** Travel's supply preview is drawn over the site rather than in its flow,
    * so choosing an action cannot push the board about.
    */
  test("the travel supply badge hangs off the site, not inside its details") {
    val ui = new RecordingView("game", "red")
    ui.boardSelection = Some(BoardTargetSelectionState(
      BoardSelectionContext("game", "red", 1L),
      Vector(BoardTargetAction("travel", "Choose a destination", 1, 1,
        autoActivate = true, Vector(BoardTargetCandidate(
          BoardTargetRef.Site("site:woods"), "Deep Woods", Vector("Supply 2"))))),
      Some("travel"), Set.empty))
    val node = world(projection(), ui)
    val badges = all(node, ".target-detail-badge")
    assertEquals(badges.size, 1)
    assertEquals(badges.head.parentNode.asInstanceOf[dom.Element]
      .getAttribute("class").contains("site"), true)
    assertEquals(all(node, ".site-details .target-detail-badge"), Vector.empty)
  }
}
