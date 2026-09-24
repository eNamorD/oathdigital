package oathdigital.frontend

import oathdigital.model.PlayerColor

import org.scalajs.dom

/** Action Selection lists each option on its own line: a row of buttons
  * run together is hard to read. A warband move is one option, so its count
  * and its button share a row.
  */
class ActionOptionRowSuite extends munit.FunSuite {
  private def panel: dom.Element =
    ActionDecisionRenderer.actionsPanel(
      GameProjection("game", 1L, "act", Some("red"),
        Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
        Vector.empty, Vector.empty, Vector("beginRecover"), ready = true,
        completed = false, actionSelectionOpen = true,
        minorActions = Some(MinorActionsState(Vector.empty,
          canPeekSiteRelics = true, Vector.empty, Some("site:a"), 2, 1))),
      ServerUiSupport.ViewerPresentation(showGameplayControls = true, None,
        None),
      new RecordingView("game", "red"))

  private def rows(group: String): Vector[dom.Element] = panel
    .querySelectorAll(s".action-group-$group > .action-option").toVector
    .map(_.asInstanceOf[dom.Element])

  test("each action option sits on its own row") {
    assertEquals(rows("major").map(_.textContent), Vector("Recover (1 Supply)"))
    assertEquals(rows("minor").map(_.textContent), Vector(
      "Peek at relics at your site",
      "Warbands board to site Move warbands",
      "Warbands site to board Move warbands"))
  }

  test("a warband move keeps its count and its button on one row") {
    val moves = rows("minor").drop(1)
    assertEquals(moves.length, 2)
    moves.foreach { row =>
      assertEquals(row.querySelectorAll("input[type=number]").length, 1)
      assertEquals(row.querySelectorAll(".minor-move-warband").length, 1)
    }
  }
}
