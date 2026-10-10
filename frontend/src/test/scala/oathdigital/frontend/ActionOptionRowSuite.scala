package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.GameIntent
import TestProjections.actingProjection
import org.scalajs.dom

/** Action Selection lists each option on its own line: a row of buttons
  * run together is hard to read.
  *
  * A power offering an action is a minor action, so it is listed with the
  * others rather than loose above End Act.
  */
class ActionOptionRowSuite extends munit.FunSuite:
  /** Silver Tongue's catalog id. */
  private val silverTongueId = "92"
  private val silverTongue = PhasePowerState("denizen.silver-tongue",
    DecisionOptionState("denizen", silverTongueId, "Silver Tongue"),
    "Silver Tongue", "Take a favor.")

  /** The Act pane for Red. Unless `legalControls` says otherwise she may
    * Recover (the one major row), peek at the relics at her site and move
    * warbands (the minor rows).
    */
  private def panel(powers: Vector[PhasePowerState] = Vector.empty,
      legalControls: Vector[String] = Vector("beginRecover",
        "peekSiteRelics", "moveWarbands"),
      controls: RecordingControls = new RecordingControls()): dom.Element =
    ActionDecisionRenderer.actionsPanel(
      actingProjection(
        players = Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
        legalControls = legalControls).copy(
        actionSelectionOpen = true,
        phasePowers = powers,
        minorActions = Some(MinorActionsState(Vector.empty,
          canPeekSiteRelics = true, Vector.empty, Some("site:a"),
          maxBoardToSite = 2, maxSiteToBoard = 1))),
      ServerUiSupport.ViewerPresentation(showGameplayControls = true, None,
        None, playerId = "red"),
      ParkedDecision.Routed(None, None), canControl = true, SessionDrafts.empty,
      controls)

  private def rows(group: String,
      of: dom.Element = panel()): Vector[dom.Element] = of
    .querySelectorAll(s".action-group-$group > .action-option").toVector
    .map(_.asInstanceOf[dom.Element])

  test("each action option sits on its own row"):
    assertEquals(rows("major").map(_.textContent), Vector("Recover (1 Supply)"))
    assertEquals(rows("minor").map(_.textContent), Vector(
      "Peek at relics at your site", "Move warbands"))

  test("a power's action is listed as a minor action, not beside End Act"):
    val acting = panel(Vector(silverTongue),
      Vector("beginRecover",
        s"usePower:denizen.silver-tongue:$silverTongueId"))
    assertEquals(acting.querySelectorAll(
      ".action-group-minor .phase-power").length, 1)
    // Nothing is left loose: every power button the panel draws is inside a
    // section, so none of them sits next to the button that ends the Act.
    assertEquals(acting.querySelectorAll(".phase-power").length, 1)
    assert(rows("minor", acting).map(_.textContent).contains("Silver Tongue"))

  test("a minor action's button starts its procedure, which asks the rest"):
    val controls = new RecordingControls()
    val acting = panel(controls = controls)
    acting.querySelector(".minor-move-warband")
      .asInstanceOf[dom.html.Button].click()
    assertEquals(controls.submitted,
      Vector(GameIntent.StartWalker("move-warbands")))
