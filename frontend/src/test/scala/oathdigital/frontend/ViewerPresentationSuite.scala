package oathdigital.frontend

import oathdigital.frontend.ServerModeFixtures.projection

import munit.FunSuite

/** What each viewer is shown for the projected phase: the Take Wealth
  * actions, who waits for whom, and the status line.
  */
class ViewerPresentationSuite extends FunSuite:

  test("Take Wealth actions use the active-player labels and commands"):
    val actions = ServerUiSupport.takeWealthActions(
      projection(Set("takeFavor", "takeSecret", "endWake"), phase = "wake"),
      "red-exile"
    )

    assertEquals(
      actions.map(_.label),
      Vector("Take Wealth: 1 favor", "Take Wealth: 1 secret")
    )
    assertEquals(
      actions.map(_.command),
      Vector(
        oathdigital.protocol.GameIntent.StartWalker("take-wealth", Vector.empty,
          Vector(oathdigital.protocol.WalkerStartArgWire("button", "favor"))),
        oathdigital.protocol.GameIntent.StartWalker("take-wealth", Vector.empty,
          Vector(oathdigital.protocol.WalkerStartArgWire("button", "secret")))
      )
    )

  test("unavailable Take Wealth actions are absent"):
    assertEquals(
      ServerUiSupport.takeWealthActions(
        projection(Set("endWake"), phase = "wake"),
        "red-exile"
      ),
      Vector.empty
    )

  test("blocked Take Wealth resource is absent while the legal one remains"):
    val actions = ServerUiSupport.takeWealthActions(
      projection(Set("takeSecret", "endWake"), phase = "wake"),
      "red-exile"
    )

    assertEquals(actions.map(_.label), Vector("Take Wealth: 1 secret"))

  test("already-used Take Wealth actions are absent from a later phase"):
    assertEquals(
      ServerUiSupport.takeWealthActions(
        projection(Set("takeFavor", "takeSecret"), phase = "act-action-selection"),
        "red-exile"
      ),
      Vector.empty
    )

  test("inactive Wake viewer waits and receives no gameplay controls"):
    val value = projection(
      Set("takeFavor", "takeSecret", "endWake"),
      phase = "wake",
      activeParticipantId = "red-exile"
    )
    val presentation = ServerUiSupport.viewerPresentation(value, "blue-exile")

    assertEquals(presentation.showGameplayControls, false)
    assertEquals(presentation.waitingForPlayerId, Some("red-exile"))
    assertEquals(presentation.waitingForDisplayName, Some("Red Exile"))
    assertEquals(
      ServerUiSupport.takeWealthActions(value, "blue-exile"),
      Vector.empty
    )

  /** Fix round 1: a parked walker `Decide`'s owner is not always the active
    * participant (Task 5). `walkerDecision` is projected to the owner alone
    * regardless of whose turn it is, so `viewerPresentation` must grant
    * controls off that field directly rather than off `activeParticipantId`
    * -- the bug this guards against left the owner and the active player
    * each waiting on the other.
    */
  test("an off-turn owner of a parked walker decision keeps gameplay controls"):
    val value = projection(Set.empty, activeParticipantId = "red-exile")
      .copy(walkerDecision = Some(ForgeFixture.parked))
    val owner = ServerUiSupport.viewerPresentation(value, "blue-exile")
    assert(owner.showGameplayControls)
    assertEquals(owner.waitingForPlayerId, None)
    assertEquals(owner.waitingForDisplayName, None)

  test("the active participant waits for the walker decision's off-turn owner"):
    val value = projection(Set.empty, activeParticipantId = "red-exile")
      .copy(walkerWaiting = Some(WalkerWaitingState("blue-exile",
        Some("Choose the Oathkeeper"))))
    val active = ServerUiSupport.viewerPresentation(value, "red-exile")
    assert(!active.showGameplayControls)
    assertEquals(active.waitingForPlayerId, Some("blue-exile"))
    assertEquals(active.waitingForDisplayName, Some("Blue Exile"))

  test("inactive Act viewer sees no action-selection controls"):
    val value = projection(Set("beginRest"), phase = "act-action-selection")
      .copy(actionSelectionOpen = true,
        legalSearchSources = Vector(LegalSearchSource("world", None, 2)))
    val inactive = ServerUiSupport.viewerPresentation(value, "blue-exile")
    val active = ServerUiSupport.viewerPresentation(value, "red-exile")

    assertEquals(inactive.waitingForPlayerId, Some("red-exile"))
    assert(!ServerUiSupport.showActActionControls(value, inactive))
    assert(ServerUiSupport.showActActionControls(value, active))

  /** Regression: the owner of a parked walker decision has
    * `waitingForPlayerId = None` (Task 5, tested above), but the status line
    * matched on that field alone, so this viewer fell through every named
    * phase case straight to the `activeParticipantId` debug fallback --
    * showing "setup-walker-decision; active participant: <someone else>"
    * to the very player who needs to act. Assert the decision's own heading
    * renders instead, for both the owner and an off-turn viewer.
    */
  test("a parked walker decision's owner sees its heading, not the debug fallback"):
    val site = DecisionOptionState("site", "site:ancient-city", "Ancient City")
    val pawnQuery = DecisionQueryState.ChooseOne(Vector(site),
      heading = Some("Choose your starting site"))
    val pawnDecision = WalkerDecisionState("setup", "setup.pawn-placement.blue-exile",
      "decide", query = Some(pawnQuery))
    val value = projection(
      Set.empty,
      phase = "setup-walker-decision",
      activeParticipantId = "red-exile",
      ready = false
    ).copy(walkerDecision = Some(pawnDecision))

    val ownerStatus = ActionDecisionRenderer.status(value,
      ServerUiSupport.viewerPresentation(value, "blue-exile"))
    assertEquals(ownerStatus.textContent, "Choose your starting site")
    assert(!ownerStatus.textContent.contains("active participant"))

  test("a parked walker decision without a heading still avoids the debug fallback"):
    val vote = DecisionOptionState("button", "yes", "Yes")
    val query = DecisionQueryState.ChooseOne(Vector(vote))
    val decision = WalkerDecisionState("setup", "setup.some-decision.blue-exile",
      "decide", query = Some(query))
    val value = projection(
      Set.empty,
      phase = "setup-walker-decision",
      activeParticipantId = "red-exile",
      ready = false
    ).copy(walkerDecision = Some(decision))

    val ownerStatus = ActionDecisionRenderer.status(value,
      ServerUiSupport.viewerPresentation(value, "blue-exile"))
    assertEquals(ownerStatus.textContent, "Your decision.")

  test("Wake and the Act phase leave the active player's status empty"):
    // The Game Log names the phase, and the Act prompt went stale after the
    // first action, so neither phase prints its own instruction.
    Vector("wake", "act-action-selection").foreach { phase =>
      val value = projection(Set.empty, phase = phase)
      val status = ActionDecisionRenderer.status(value,
        ServerUiSupport.viewerPresentation(value, "red-exile"))
      assertEquals(status.textContent, "", phase)
    }
