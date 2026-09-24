package oathdigital.frontend

import org.scalajs.dom

/** Which pane each standing line of state belongs to. The oath and the
  * usurper limit describe the table, not the acting player's options, so
  * they sit with the board rather than in the action panel.
  */
class PanelPlacementSuite extends munit.FunSuite {
  private def all(node: dom.Element, selector: String): Vector[dom.Element] =
    node.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  private def one(node: dom.Element, selector: String): Option[dom.Element] =
    all(node, selector).headOption

  private val tracks = oathdigital.protocol.projection.GameTracksProjection(round = 2, visionsDrawn = 0,
    usurperLimited = true, limiterRound = 4, firstPlayerId = "red")

  private val oath = OathkeeperStatus("supremacy", None, "oathkeeper",
    usurperLimited = true, None, None)

  private def projection(
      oathkeeper: Option[OathkeeperStatus] = Some(oath),
      trackState: Option[oathdigital.protocol.projection.GameTracksProjection] = Some(tracks)): GameProjection =
    GameProjection("game", 1L, "act", Some("red"),
      Vector(GamePlayer("red", "Red", "Exile", PlayerColorToken.Red)),
      // The tracker is drawn inside the cradle, so the region has to exist
      // for the notice under it to be reachable at all.
      Vector(GameRegion("cradle", Vector.empty)),
      Vector.empty, Vector.empty, ready = true, completed = false,
      activePlayerResources = Some(ActivePlayerResources(3, 1, 0, 0, 1, 5)),
      currentSiteResources = Some(CurrentSiteResources("site:woods", 2, 1)),
      oathkeeper = oathkeeper, tracks = trackState)

  private def world(value: GameProjection): dom.Element =
    WorldBoardRenderer.world(value, ServerUiSupport.ViewerPresentation(
      showGameplayControls = true, None, None), new RecordingView("game", "red"))

  private def actions(value: GameProjection): dom.Element =
    ActionDecisionRenderer.actionsPanel(value,
      ServerUiSupport.ViewerPresentation(showGameplayControls = true, None, None),
      new RecordingView("game", "red"))

  test("the oath and its holder are listed in the shared bank") {
    val line = one(world(projection()), ".shared-bank .oathkeeper-status")
      .getOrElse(fail("no oath line in the shared bank"))
    assertEquals(line.textContent, "Oath of Supremacy · Oathkeeper: unheld")
  }

  /** The goal is projected, so the panel prints the oath actually in play
    * rather than the one the first game happens to start with.
    */
  test("a goal other than supremacy is named as itself") {
    assertEquals(one(world(projection(oathkeeper =
      Some(oath.copy(goal = "the-people", holderPlayerId = Some("Blue"))))),
      ".shared-bank .oathkeeper-status").map(_.textContent),
      Some("Oath of the People · Oathkeeper: Blue"))
  }

  test("the usurper notice sits under the round tracker, at the projected round") {
    val notice = one(world(projection()), ".round-tracker .usurper-notice")
      .getOrElse(fail("no usurper notice"))
    assertEquals(notice.textContent, "Usurper locked until round 4")
    assertEquals(one(world(projection(trackState =
      Some(tracks.copy(limiterRound = 5)))), ".usurper-notice")
      .map(_.textContent), Some("Usurper locked until round 5"))
  }

  test("an unlimited usurper gets no notice") {
    assertEquals(all(world(projection(trackState =
      Some(tracks.copy(usurperLimited = false)))), ".usurper-notice"),
      Vector.empty)
  }

  /** The player strip already carries the acting player's resources, and the
    * site's loose wealth is drawn on the site itself.
    */
  test("the action panel carries neither the oath nor a second resource line") {
    val panel = actions(projection())
    assertEquals(all(panel, ".oathkeeper-status"), Vector.empty)
    assertEquals(all(panel, ".resources"), Vector.empty)
    assertEquals(all(panel, ".site-resources"), Vector.empty)
    assert(!panel.textContent.contains("loose wealth"), panel.textContent)
    assert(!panel.textContent.contains("Usurper"), panel.textContent)
  }
}
