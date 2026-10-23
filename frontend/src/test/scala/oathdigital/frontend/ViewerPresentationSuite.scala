package oathdigital.frontend

import oathdigital.frontend.ServerModeFixtures.twoSeatProjection

import munit.FunSuite

/** What each viewer is shown for the projected phase: who waits for whom,
  * and the status line.
  */
class ViewerPresentationSuite extends FunSuite:

  test("inactive Wake viewer waits and receives no gameplay controls"):
    val value = twoSeatProjection(
      phase = "wake",
      activeParticipantId = "red-exile"
    )
    val presentation = ServerUiSupport.viewerPresentation(value, "blue-exile")

    assert(!presentation.showGameplayControls)
    assertEquals(presentation.waitingForPlayerId, Some("red-exile"))
    assertEquals(presentation.waitingForDisplayName, Some("Red Exile"))

  /** Fix round 1: a parked walker `Decide`'s owner is not always the active
    * participant (Task 5). `walkerDecision` is projected to the owner alone
    * regardless of whose turn it is, so `viewerPresentation` must grant
    * controls off that field directly rather than off `activeParticipantId`
    * -- the bug this guards against left the owner and the active player
    * each waiting on the other.
    */
  test("an off-turn owner of a parked walker decision keeps gameplay controls"):
    val value = twoSeatProjection(activeParticipantId = "red-exile")
      .copy(walkerDecision = Some(ForgeFixture.parked))
    val owner = ServerUiSupport.viewerPresentation(value, "blue-exile")
    assert(owner.showGameplayControls)
    assertEquals(owner.waitingForPlayerId, None)
    assertEquals(owner.waitingForDisplayName, None)

  test("the active participant waits for the walker decision's off-turn owner"):
    val value = twoSeatProjection(activeParticipantId = "red-exile")
      .copy(walkerWaiting = Some(WalkerWaitingState("blue-exile",
        Some("Choose the Oathkeeper"))))
    val active = ServerUiSupport.viewerPresentation(value, "red-exile")
    assert(!active.showGameplayControls)
    assertEquals(active.waitingForPlayerId, Some("blue-exile"))
    assertEquals(active.waitingForDisplayName, Some("Blue Exile"))

  /** The player waited on is named once: the status line carries the
    * question their parked decision asks, so the pane draws no second
    * waiting line under it.
    */
  test("the status line names who is waited on and their decision's question"):
    val value = twoSeatProjection(activeParticipantId = "red-exile")
      .copy(walkerWaiting = Some(WalkerWaitingState("blue-exile",
        Some("Choose your starting site"))))
    val status = ActionDecisionRenderer.status(value,
      ServerUiSupport.viewerPresentation(value, "red-exile"))
    assertEquals(status.textContent,
      "Waiting for Blue Exile: Choose your starting site")
    assertEquals(status.querySelector(".player-ref").textContent, "Blue Exile")

  test("a parked Roll's wait names only the player, having no question"):
    val value = twoSeatProjection(activeParticipantId = "red-exile")
      .copy(walkerWaiting = Some(WalkerWaitingState("blue-exile")))
    val status = ActionDecisionRenderer.status(value,
      ServerUiSupport.viewerPresentation(value, "red-exile"))
    assertEquals(status.textContent, "Waiting for Blue Exile")

  test("an inactive Act viewer waits for the active player"):
    val value = twoSeatProjection(phase = "act")
    val inactive = ServerUiSupport.viewerPresentation(value, "blue-exile")
    val active = ServerUiSupport.viewerPresentation(value, "red-exile")

    assertEquals(inactive.waitingForPlayerId, Some("red-exile"))
    assertEquals(active.waitingForPlayerId, None)

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
      query = Some(pawnQuery))
    val value = twoSeatProjection(
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
      query = Some(query))
    val value = twoSeatProjection(
      phase = "setup-walker-decision",
      activeParticipantId = "red-exile",
      ready = false
    ).copy(walkerDecision = Some(decision))

    val ownerStatus = ActionDecisionRenderer.status(value,
      ServerUiSupport.viewerPresentation(value, "blue-exile"))
    assertEquals(ownerStatus.textContent, "Your decision.")

  test("a parked Act menu states its heading as the active player's status"):
    // A live Act always has its menu parked, so the status is its heading.
    val query = DecisionQueryState.ChooseOne(
      Vector(DecisionOptionState("button", "end-act", "End Act and Rest")),
      heading = Some("Act: take an action, use a power or rest"))
    val value = twoSeatProjection(
      phase = "act-menu-walker-decision").copy(walkerDecision = Some(
      WalkerDecisionState("act-menu", "phase.menu",
        query = Some(query))))
    val status = ActionDecisionRenderer.status(value,
      ServerUiSupport.viewerPresentation(value, "red-exile"))
    assertEquals(status.textContent,
      "Act: take an action, use a power or rest")
