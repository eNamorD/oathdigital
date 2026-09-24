package oathdigital.gameplay.walker

import oathdigital.gameplay.walker.WalkerStepPayload.DeltaRecorded
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

/** Replay applies recorded operations through the executor. The executor
  * runs the shape guard as the mutation's precondition, so a recorded
  * operation the live pipeline would have refused is refused on replay too,
  * instead of applying with the guard skipped.
  */
class WalkerReplayGuardSuite extends munit.FunSuite {
  private val held = ready.updateCurrent(current => current.copy(
    commonCards = current.commonCards.copy(
      worldDeck = current.commonCards.worldDeck.filterNot(_ == worldDenizen)),
    temporaryHands = Map(playerId -> Vector(worldDenizen))))

  private def discard(position: StackPosition) = Move(
    Piece.Card(worldDenizen),
    PositionedLocation(Location.Hand(playerId)),
    PositionedLocation(Location.RegionalDiscard(Region.Cradle), position))

  private def step(operation: CoreOperation) = WalkerStepRecorded("0",
    DeltaRecorded(DeltaMeaning.OperationApplied("discard")), Vector(operation),
    Vector.empty)

  test("a recorded discard with a stack position replays") {
    val replayed = ProcedureWalker.applyRecorded(OathState.Ready(held),
      step(discard(StackPosition.Top)))
    val OathState.Ready(after) = replayed.toOption.get: @unchecked
    assertEquals(after.game.current.commonCards.discard(Region.Cradle),
      Vector(worldDenizen))
    assertEquals(after.game.current.temporaryHands(playerId), Vector.empty)
  }

  test("a recorded discard without a stack position is rejected on replay " +
      "instead of losing the card") {
    val replayed = ProcedureWalker.applyRecorded(OathState.Ready(held),
      step(discard(StackPosition.Unspecified)))
    assertEquals(replayed, Left(OathViolation.CoreOperationRejected(
      "invalid-stack-position",
      "stack destination must specify top or bottom")))
  }
}
