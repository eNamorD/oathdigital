package oathdigital.gameplay.operations

import oathdigital.gameplay.{OathViolation, ReadyGame}
import oathdigital.model._

trait OperationPolicy {
  def validate(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, Unit]
}

object OperationPolicy {
  /** For tests and paths whose owning procedure has already enforced every
    * semantic restriction. Production migrations should prefer a contextual
    * policy rather than treating this as a legality check.
    */
  case object Permissive extends OperationPolicy {
    override def validate(
        ready: ReadyGame,
        operation: CoreOperation
    ): Either[OperationError, Unit] = Right(())
  }

  def all(policies: Vector[OperationPolicy]): OperationPolicy =
    new OperationPolicy {
      override def validate(
          ready: ReadyGame,
          operation: CoreOperation
      ): Either[OperationError, Unit] =
        policies.foldLeft[Either[OperationError, Unit]](Right(())) {
          case (result, policy) =>
            result.flatMap(_ => policy.validate(ready, operation))
        }
    }

  /** Restricts execution to semantic roots reconstructed from one validated
    * authoritative event. Structural checks still belong to the executor.
    */
  def exact(
      expected: Vector[CoreOperation],
      rejectionDetail: String
  ): OperationPolicy = new OperationPolicy {
    override def validate(
        ready: ReadyGame,
        operation: CoreOperation
    ): Either[OperationError, Unit] = Either.cond(
      expected.contains(operation),
      (),
      OperationError.RestrictedOperation(rejectionDetail)
    )
  }
}

final case class OperationReceipt private[gameplay] (
    operation: CoreOperation,
    primitives: Vector[PrimitiveOperation]
)

final case class OperationExecution private[gameplay] (
    ready: ReadyGame,
    receipts: Vector[OperationReceipt]
)

final class OperationExecutor(policy: OperationPolicy) {
  def execute(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, OperationExecution] =
    for {
      expected <- OperationStateInvariant.cardIds(ready)
      _ <- OperationStateInvariant.validate(ready, expected)
      _ <- policy.validate(ready, operation)
      evolved <- OperationError
        .describe(OperationStateAdapter.applyOperation(ready, operation))
        .flatMap(identity)
      _ <- OperationStateInvariant.validate(evolved, expected)
    } yield OperationExecution(
      evolved,
      Vector(OperationReceipt(operation, operation.primitives))
    )

  def executeAll(
      ready: ReadyGame,
      operations: Vector[CoreOperation]
  ): Either[OperationError, OperationExecution] =
    if (operations.isEmpty) Left(OperationError.EmptyOperationBatch)
    else
      operations.foldLeft[Either[OperationError, OperationExecution]](
        Right(OperationExecution(ready, Vector.empty))
      ) { (result, operation) =>
        result.flatMap { staged =>
          execute(staged.ready, operation).map { next =>
            next.copy(receipts = staged.receipts ++ next.receipts)
          }
        }
      }
}

object OperationTransaction {
  def evolve(
      ready: ReadyGame,
      operations: Vector[CoreOperation],
      executor: OperationExecutor
  )(
      update: ReadyGame => Either[OathViolation, ReadyGame]
  ): Either[OathViolation, OperationExecution] =
    for {
      expected <- OperationStateInvariant.cardIds(ready)
        .left.map(_.toViolation)
      executed <- executor.executeAll(ready, operations)
        .left.map(_.toViolation)
      updated <- OperationError.describe(update(executed.ready))
        .left.map(_.toViolation)
        .flatMap(identity)
      _ <- OperationStateInvariant.validate(updated, expected)
        .left.map(_.toViolation)
    } yield executed.copy(ready = updated)
}

private[operations] object OperationStateInvariant {
  import OperationError._

  def cardIds(ready: ReadyGame): Either[OperationError, Set[CardId]] =
    CardIndex.from(ready.game).left.map(InvalidCardIndex).map(_.ids)

  def validate(
      ready: ReadyGame,
      expectedCards: Set[CardId]
  ): Either[OperationError, Unit] =
    for {
      actual <- cardIds(ready)
      _ <- Either.cond(
        actual == expectedCards,
        (),
        CardInventoryChanged(expectedCards -- actual, actual -- expectedCards)
      )
      problems = DomainValidation.validate(ready.game, expectedCards)
      _ <- Either.cond(problems.isEmpty, (), InvalidPostState(problems))
      _ <- validateOrientations(ready)
      _ <- validateWarbands(ready)
    } yield ()

  private def validateOrientations(
      ready: ReadyGame
  ): Either[OperationError, Unit] = {
    val invalidRevealed = ready.game.current.players.exists(
      _.revealedVision.exists(_.orientation != Orientation.FaceUp)
    )
    val invalidAdviser = ready.game.current.players.exists(
      _.advisers.exists {
        case vision: VisionState => vision.orientation != Orientation.FaceDown
        case _ => false
      }
    )
    Either.cond(
      !invalidRevealed && !invalidAdviser,
      (),
      ConflictingDeltas("Vision orientation is invalid for its container")
    )
  }

  private def validateWarbands(
      ready: ReadyGame
  ): Either[OperationError, Unit] = {
    val playerKinds = ready.game.current.players.flatMap { player =>
      ready.game.campaign.lineages.get(player.lineage).map { lineage =>
        if (lineage.role.isImperial) ForceKind.Imperial
        else ForceKind.Exile(player.lineage)
      }
    }
    val siteKinds = ready.game.current.map.sites.valuesIterator.flatMap {
      _.forces match {
        case SiteForces.Occupied(kind, _) => Some(kind)
        case SiteForces.Empty => None
      }
    }.toVector
    val kinds = (playerKinds ++ siteKinds ++
      ready.banks.warbandSupply.keys).distinct

    kinds.foldLeft[Either[OperationError, Unit]](Right(())) {
      case (result, kind) =>
        result.flatMap { _ =>
          OperationStateAdapter.quantity(
            ready,
            Piece.Warbands(kind, 1),
            Location.WarbandBank(kind)
          ).map(_ => ())
        }
    }
  }
}
