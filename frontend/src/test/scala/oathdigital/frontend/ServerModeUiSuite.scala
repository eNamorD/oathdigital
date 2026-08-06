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

  test("inactive Wake viewer waits and receives no gameplay controls") {
    val value = projection(
      Set("takeFavor", "takeSecret", "endWake"),
      activeParticipantId = "red-exile"
    )
    val presentation = ServerModeUi.viewerPresentation(value, "blue-exile")

    assertEquals(presentation.showGameplayControls, false)
    assertEquals(presentation.waitingForPlayerId, Some("red-exile"))
    assertEquals(presentation.waitingForDisplayName, Some("Red Exile"))
    assertEquals(
      ServerModeUi.takeWealthActions(value, "blue-exile"),
      Vector.empty
    )
  }

  test("inactive setup viewer waits without pawn or private adviser controls") {
    val value = projection(
      Set("placePawn", "chooseAdviser"),
      phase = "awaiting-adviser",
      activeParticipantId = "red-exile",
      ready = false,
      privateAdviserChoices = Vector(
        AdviserChoice("adviser-1", "Adviser One")
      )
    )

    val presentation = ServerModeUi.viewerPresentation(value, "blue-exile")

    assertEquals(presentation.showGameplayControls, false)
    assertEquals(presentation.waitingForDisplayName, Some("Red Exile"))
  }

  test("active viewer retains Wake and setup gameplay controls") {
    val wake = projection(Set("takeFavor", "endWake"))
    assert(ServerModeUi.viewerPresentation(
      wake,
      "red-exile"
    ).showGameplayControls)
    assertEquals(
      ServerModeUi.takeWealthActions(wake, "red-exile").map(_.label),
      Vector("Take Wealth: 1 favor")
    )

    val setup = projection(
      Set("chooseAdviser"),
      phase = "awaiting-adviser",
      ready = false,
      privateAdviserChoices = Vector(
        AdviserChoice("adviser-1", "Adviser One")
      )
    )
    assert(ServerModeUi.viewerPresentation(
      setup,
      "red-exile"
    ).showGameplayControls)
  }

  private def projection(
      legalControls: Set[String],
      phase: String = "wake",
      activeParticipantId: String = "red-exile",
      ready: Boolean = true,
      privateAdviserChoices: Vector[AdviserChoice] = Vector.empty
  ): FirstGameProjection =
    FirstGameProjection(
      gameId = "game-1",
      nextSequence = 8L,
      phase = phase,
      activeParticipantId = Some(activeParticipantId),
      players = Vector(
        FirstGamePlayer(
          "red-exile",
          "Red Exile",
          "exile",
          PlayerColorToken.Red
        ),
        FirstGamePlayer(
          "blue-exile",
          "Blue Exile",
          "exile",
          PlayerColorToken.Blue
        )
      ),
      world = Vector.empty,
      pawnLocations = Vector.empty,
      legalControls = legalControls,
      ready = ready,
      completed = false,
      privateAdviserChoices = privateAdviserChoices
    )
}
