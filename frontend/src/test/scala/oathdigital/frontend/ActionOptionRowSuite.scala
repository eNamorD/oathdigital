package oathdigital.frontend

import oathdigital.model.PlayerColor

import org.scalajs.dom

/** Action Selection lists each option on its own line: a row of buttons
  * run together is hard to read. A warband move is one option, so its count
  * and its button share a row.
  *
  * A power offering an action is a minor action, so it is listed with the
  * others rather than loose above End Act.
  */
class ActionOptionRowSuite extends munit.FunSuite {
  private val silverTongue = PhasePowerState("denizen.silver-tongue",
    DecisionOptionState("denizen", "92", "Silver Tongue"),
    "Silver Tongue", "Take a favor.")

  private def panel(powers: Vector[PhasePowerState] = Vector.empty,
      legalControls: Vector[String] = Vector("beginRecover")): dom.Element =
    ActionDecisionRenderer.actionsPanel(
      GameProjection("game", 1L, "act", Some("red"),
        Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
        Vector.empty, Vector.empty, legalControls, ready = true,
        completed = false, actionSelectionOpen = true,
        phasePowers = powers,
        minorActions = Some(MinorActionsState(Vector.empty,
          canPeekSiteRelics = true, Vector.empty, Some("site:a"), 2, 1))),
      ServerUiSupport.ViewerPresentation(showGameplayControls = true, None,
        None),
      new RecordingView("game", "red"))

  private def rows(group: String,
      of: dom.Element = panel()): Vector[dom.Element] = of
    .querySelectorAll(s".action-group-$group > .action-option").toVector
    .map(_.asInstanceOf[dom.Element])

  test("each action option sits on its own row") {
    assertEquals(rows("major").map(_.textContent), Vector("Recover (1 Supply)"))
    assertEquals(rows("minor").map(_.textContent), Vector(
      "Peek at relics at your site",
      "Warbands board to site Move warbands",
      "Warbands site to board Move warbands"))
  }

  test("a power's action is listed as a minor action, not beside End Act") {
    val acting = panel(Vector(silverTongue),
      Vector("beginRecover", "usePower:denizen.silver-tongue:92"))
    assertEquals(acting.querySelectorAll(
      ".action-group-minor .phase-power").length, 1)
    // Nothing is left loose: every power button the panel draws is inside a
    // section, so none of them sits next to the button that ends the Act.
    assertEquals(acting.querySelectorAll(".phase-power").length, 1)
    assert(rows("minor", acting).map(_.textContent).contains("Silver Tongue"))
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
