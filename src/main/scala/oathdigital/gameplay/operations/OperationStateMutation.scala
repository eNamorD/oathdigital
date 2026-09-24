package oathdigital.gameplay.operations

import oathdigital.model._

/** Internal immutable mutation planner for counted pieces and primitive effects. */
private[operations] object OperationStateMutation {

  /** Applies a validated operation's leaves. Shape/allowlist checks are
    * owned by OperationShape/OperationValidator and run by OperationPipeline
    * before this object is reached; the remaining Either guards below are
    * mutation-time defenses that only fire if validation drifted.
    */
  private[operations] def applyOperation(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, ReadyGame] =
    mutate(ready, operation)

  private def mutate(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, ReadyGame] = {
    val leaves = Operation.flatten(operation)
    for {
      resources <- ResourceOperations.applyCountedMoves(ready, leaves)
      pieces <- BoardControlOperations.applyPawnAndBannerMoves(resources, leaves)
      cards <- OperationCardMutation.applyCardMoves(pieces, leaves)
      finished <- applyNonMoveLeaves(cards, leaves)
    } yield finished
  }

  private def applyNonMoveLeaves(
      ready: ReadyGame,
      leaves: Vector[Operation]
  ): Either[OperationError, ReadyGame] =
    leaves.foldLeft[Either[OperationError, ReadyGame]](Right(ready)) {
      case (result, Flip(id, at, orientation)) =>
        result.flatMap(CardFaceOperations.flipCard(_, id, at, orientation))
      case (result, FlipSecrets(player, amount, from, to)) =>
        result.flatMap(ResourceOperations.flipPlayerSecrets(
          _, player, amount, from, to))
      case (result, Peek(viewer, id, at)) =>
        result.flatMap(CardFaceOperations.peek(_, viewer, id, at))
      case (result, SpendSupply(player, amount, _)) =>
        result.flatMap(TurnStateOperations.adjustSupply(_, player, -amount))
      case (result, GainSupply(player, amount)) =>
        result.flatMap(TurnStateOperations.adjustSupply(_, player, amount))
      case (result, AdvanceVisionsDrawn) =>
        result.flatMap(TurnStateOperations.advanceVisionsDrawn)
      case (result, ModifyDicePool(pool, delta, _)) =>
        result.flatMap(TurnStateOperations.adjustDicePool(_, pool, delta))
      case (result, ModifyRollOutcome(pool, skulls, score)) =>
        result.map(TurnStateOperations.modifyRollOutcome(_, pool, skulls, score))
      case (result, RecordPowerUse(power)) =>
        result.map(TurnStateOperations.recordPowerUse(_, power))
      case (result, EnterPhase(phase)) =>
        result.flatMap(TurnStateOperations.enterPhase(_, phase))
      case (result, SetOathkeeper(holder)) =>
        result.flatMap(TurnStateOperations.setOathkeeper(_, holder))
      case (result, RecordCampaignResult(fact)) =>
        result.map(TurnStateOperations.recordCampaignResult(_, fact))
      case (result, BeginTurn(player, phase)) =>
        result.flatMap(TurnStateOperations.beginTurn(_, player, phase))
      case (result, _) => result
    }

}
