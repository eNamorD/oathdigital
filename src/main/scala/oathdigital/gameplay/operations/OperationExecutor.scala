package oathdigital.gameplay.operations

import oathdigital.gameplay.ReadyGame
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

/** Pure mutation engine: applies one operation (or a raw fold of several)
  * without any policy or state-invariant checks. [[OperationPipeline]] owns
  * pre-flight validation and the post-state invariant; this class is only the
  * mutation primitive for it and for raw-mutation-only consumers (shadow
  * comparisons, tests). The describe guard converts constructor failures
  * thrown by the mutation into typed [[OperationError]] rejections.
  *
  * NOTE: [[OperationStateAdapter.applyOperation]] still runs the
  * [[OperationShape]] first-violation guard internally, so a raw execute stays
  * shape-safe; [[OperationPipeline]] additionally pre-flights each operation
  * explicitly. Removing the internal double-run is deferred until the suite
  * proves it redundant.
  */
final class OperationExecutor {
  def execute(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, ReadyGame] =
    OperationError
      .describe(OperationStateAdapter.applyOperation(ready, operation))
      .flatMap(identity)

  def executeAll(
      ready: ReadyGame,
      operations: Vector[CoreOperation]
  ): Either[OperationError, ReadyGame] =
    if (operations.isEmpty) Left(OperationError.EmptyOperationBatch)
    else
      operations.foldLeft[Either[OperationError, ReadyGame]](Right(ready)) {
        (result, operation) => result.flatMap(staged => execute(staged, operation))
      }
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
