package oathdigital.frontend

import oathdigital.model.PlayerColor

import org.scalajs.dom

/** Which pane each standing line of state belongs to. The oath and the
  * usurper limit describe the table, not the acting player's options, so
  * they sit with the board rather than in the action panel.
  */
class PanelPlacementSuite extends munit.FunSuite:
  private val tracks = oathdigital.protocol.projection.GameTracksProjection(round = 2, visionsDrawn = 0,
    usurperLimited = true, limiterRound = 4, firstPlayerId = "red")

  private val oath = OathkeeperStatus("supremacy", None, "oathkeeper",
    usurperLimited = true, None, None)

  private def projection(
      oathkeeper: Option[OathkeeperStatus] = Some(oath),
      trackState: Option[oathdigital.protocol.projection.GameTracksProjection] = Some(tracks)): GameProjection =
    TestProjections.actingProjection(
      players = Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
      // The tracker is drawn inside the cradle, so the region has to exist
      // for the notice under it to be reachable at all.
      world = Vector(GameRegion("cradle", Vector.empty)))
      .copy(oathkeeper = oathkeeper, tracks = trackState)

  private def world(value: GameProjection): dom.Element =
    WorldBoardRenderer.world(value, BoardOptions.none,
      SessionDrafts.empty, new RecordingControls())

  test("the oath and its holder are listed in the shared bank"):
    val value = projection(oathkeeper = Some(
      oath.copy(goal = "supremacy", holderPlayerId = None)))
    val line = Dom.firstMatch(world(value), ".shared-bank .oathkeeper-status")
      .getOrElse(fail("no oath line in the shared bank"))
    assertEquals(line.textContent, "Oathkeeper of Supremacy · Oathkeeper: unheld")

  /** The goal is projected, so the panel prints the oath actually in play
    * rather than the one the first game happens to start with.
    */
  test("a goal other than supremacy is named as itself"):
    assertEquals(Dom.firstMatch(world(projection(oathkeeper =
      Some(oath.copy(goal = "the-people", holderPlayerId = Some("Blue"))))),
      ".shared-bank .oathkeeper-status").map(_.textContent),
      Some("Oathkeeper of the People · Oathkeeper: Blue"))

  test("the usurper notice sits under the round tracker, at the projected round"):
    val round4 = projection(trackState =
      Some(tracks.copy(usurperLimited = true, limiterRound = 4)))
    val notice = Dom.firstMatch(world(round4),
      ".round-tracker .usurper-notice").getOrElse(fail("no usurper notice"))
    assertEquals(notice.textContent, "Usurper locked until round 4")
    assertEquals(Dom.firstMatch(world(projection(trackState =
      Some(tracks.copy(limiterRound = 5)))), ".usurper-notice")
      .map(_.textContent), Some("Usurper locked until round 5"))

  test("an unlimited usurper gets no notice"):
    assertEquals(Dom.all(world(projection(trackState =
      Some(tracks.copy(usurperLimited = false)))), ".usurper-notice"),
      Vector.empty)

  /** The player strip already carries the acting player's resources, and the
    * site's loose wealth is drawn on the site itself.
    */
  test("the action panel carries neither the oath, a second resource line " +
      "nor the usurper notice"):
    // The projection carries both the acting player's resources and the
    // current site's loose wealth, and the oath and the usurper limit too.
    val value = projection().copy(
      activePlayerResources = Some(ActivePlayerResources(favor = 3,
        faceUpSecrets = 1, faceDownSecrets = 0, committedSecrets = 0,
        totalSecrets = 1, supply = 5)),
      currentSiteResources = Some(CurrentSiteResources(siteId = "site:woods",
        favor = 2, secrets = 1)))
    val panel = ActionPanels.actionsPanelFor(value)
    assertEquals(Dom.all(panel, ".oathkeeper-status"), Vector.empty)
    assertEquals(Dom.all(panel, ".resources"), Vector.empty)
    assertEquals(Dom.all(panel, ".site-resources"), Vector.empty)
    assert(!panel.textContent.contains("loose wealth"), panel.textContent)
    assert(!panel.textContent.contains("Usurper"), panel.textContent)
