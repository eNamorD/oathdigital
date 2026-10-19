package oathdigital.gameplay.walker

import oathdigital.model._
import oathdigital.model.TestGameFixtures._
import oathdigital.testkit.{Outcomes, Table}
import oathdigital.testkit.Unwrap.accepted

/** Replay applies recorded operations through the executor. The executor
  * runs the shape guard as the mutation's precondition, so a recorded
  * operation the live pipeline would have refused is refused on replay too,
  * instead of applying with the guard skipped.
  */
class WalkerReplayGuardSuite extends munit.FunSuite:
  /** TestGameFixtures' red holding D1, the world deck's only card. */
  private val held = Table.from(ready).hand(playerId, worldDenizen).unchecked

  private def discard(position: StackPosition) = Move(
    Piece.Card(worldDenizen),
    PositionedLocation(Location.Hand(playerId)),
    PositionedLocation(Location.RegionalDiscard(Region.Cradle), position))

  private def step(operation: CoreOperation) = WalkerStepRecorded("0",
    DeltaPayload(Vector(operation)), Vector.empty)

  test("a recorded discard with a stack position replays"):
    val replayed = ProcedureWalker.applyRecorded(OathState.Ready(held),
      step(discard(StackPosition.Top)))
    val after = Outcomes.ready(accepted(replayed))
    assertEquals(after.game.current.commonCards.discard(Region.Cradle),
      Vector(worldDenizen))
    assertEquals(after.game.current.temporaryHands(playerId), Vector.empty)

  test("a recorded discard without a stack position is rejected on replay " +
      "instead of losing the card"):
    val replayed = ProcedureWalker.applyRecorded(OathState.Ready(held),
      step(discard(StackPosition.Unspecified)))
    assertEquals(replayed, Left(OathViolation.CoreOperationRejected(
      "invalid-stack-position",
      "stack destination must specify top or bottom")))

  test("a recorded delta step carrying no operations is rejected on replay"):
    // The one guard of the three that survives the payload move. The type
    // cannot make an empty batch unrepresentable -- the wire is untrusted
    // input -- so the check stays where decoded facts are applied. Nothing
    // covered it before this plan.
    assertEquals(
      ProcedureWalker.applyRecorded(OathState.Ready(held),
        WalkerStepRecorded("0", DeltaPayload(Vector.empty), Vector.empty)),
      Left(OathViolation.InvalidEventOrder(
        "recorded delta step must contain operations")))
