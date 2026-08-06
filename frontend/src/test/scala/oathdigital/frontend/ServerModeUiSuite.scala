package oathdigital.frontend

import munit.FunSuite

class ServerModeUiSuite extends FunSuite {
  test("Take Wealth actions use the active-player labels and commands") {
    val actions = ServerModeUi.takeWealthActions(
      projection(Set("takeFavor", "takeSecret", "endWake")),
      "red-exile"
    )

    assertEquals(
      actions.map(_.label),
      Vector("Take Wealth: 1 favor", "Take Wealth: 1 secret")
    )
    assertEquals(
      actions.map(_.command),
      Vector(
        FirstGameCommand.TakeWealth("red-exile", "favor"),
        FirstGameCommand.TakeWealth("red-exile", "secret")
      )
    )
  }

  test("unavailable Take Wealth actions are absent") {
    assertEquals(
      ServerModeUi.takeWealthActions(
        projection(Set("endWake")),
        "red-exile"
      ),
      Vector.empty
    )
  }

  test("blocked Take Wealth resource is absent while the legal one remains") {
    val actions = ServerModeUi.takeWealthActions(
      projection(Set("takeSecret", "endWake")),
      "red-exile"
    )

    assertEquals(actions.map(_.label), Vector("Take Wealth: 1 secret"))
  }

  test("already-used Take Wealth actions are absent from a later phase") {
    assertEquals(
      ServerModeUi.takeWealthActions(
        projection(Set("takeFavor", "takeSecret"), phase = "act-action-selection"),
        "red-exile"
      ),
      Vector.empty
    )
  }

  private def projection(
      legalControls: Set[String],
      phase: String = "wake"
  ): FirstGameProjection =
    FirstGameProjection(
      gameId = "game-1",
      nextSequence = 8L,
      phase = phase,
      activeParticipantId = Some("red-exile"),
      players = Vector.empty,
      world = Vector.empty,
      pawnLocations = Vector.empty,
      legalControls = legalControls,
      ready = true,
      completed = false,
      privateAdviserChoices = Vector.empty
    )
}
