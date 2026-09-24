package oathdigital.gameplay.operations

import oathdigital.model._

/** Aggregated validator owned by [[OperationPipeline]] for one run:
  * `validateOne` checks every operation against the staged state during the
  * fold, and `validateResolvedOne` re-checks a shrunk operation without the
  * allowlist.
  *
  * Both return every violation as an [[OperationReason]] (never first-fail),
  * so callers can inspect all of them. Pipeline rejection stays first-fail:
  * it takes the head reason.
  */
final class OperationValidator(
    allowlist: OperationPolicy,
    restrictions: Vector[OperationRestriction]
) {
  /** Allowlist reasons first: the retired executor ran the per-action policy
    * before any shape/mutation check, so a both-fail operation rejects with
    * `RestrictedOperation` — mirrored here for byte-identical precedence.
    */
  def validateOne(
      ready: ReadyGame,
      operation: CoreOperation
  ): Vector[OperationReason] =
    allowlistReasons(ready, Vector(operation)) ++
      validateResolvedOne(ready, operation)

  /** Revalidation after a permitted operation shrinks must not re-run an
    * exact allowlist against a different amount.
    */
  def validateResolvedOne(ready: ReadyGame,
      operation: CoreOperation): Vector[OperationReason] =
    OperationShape.validate(ready, operation) ++
      restrictionReasons(ready, operation)

  private def restrictionReasons(ready: ReadyGame,
      operation: CoreOperation): Vector[OperationReason] =
    restrictions.flatMap(_.reason(ready, operation))

  private def allowlistReasons(
      ready: ReadyGame,
      operations: Vector[CoreOperation]
  ): Vector[OperationReason] =
    operations.flatMap { operation =>
      allowlist.validate(ready, operation) match {
        case Left(error) => Vector(OperationReason(error.code, error.detail))
        case Right(_) => Vector.empty
      }
    }
}

/** Pure shape partition of the checks [[OperationStateMutation]] runs while
  * applying one operation. It owns the *structural* validation of an operation
  * against a ready state — primitive position conventions, card source and
  * destination legality, counted-source sufficiency, pawn/banner
  * preconditions, and the guards of non-move primitives — aggregated instead
  * of first-fail so callers can inspect every violation of an operation or of
  * a whole batch before anything is executed.
  *
  * Every reason mirrors the exact [[OperationError]] instance the mutation
  * guards produce for the same condition, so [[first]] is byte-identical to
  * the typed rejection the executor observes today.
  */
object OperationShape {
  import OperationError._

  /** All shape violations for one operation against `ready`, aggregated. */
  def validate(
      ready: ReadyGame,
      operation: CoreOperation
  ): Vector[OperationReason] =
    violations(ready, operation).map(reason(_, operation))

  private def reason(error: OperationError,
      operation: CoreOperation): OperationReason = {
    val impossible = error match {
      case _: InsufficientSupply => true
      case InsufficientPieces(piece, _, _) => operation match {
        case _: Discard | _: PayCost => false
        case _ => piece.isInstanceOf[Piece.Counted]
      }
      case _ => false
    }
    OperationReason(error.code, error.detail,
      if (impossible) OperationReasonKind.Impossible
      else OperationReasonKind.Invalid)
  }

  private def violations(
      ready: ReadyGame,
      operation: CoreOperation
  ): Vector[OperationError] = {
    val leaves = Operation.flatten(operation)
    val favorMoves = leaves.collect {
      case move @ Move(_: Piece.Favor, _, _, _) => move
    }
    val secretMoves = leaves.collect {
      case move @ Move(_: Piece.Secrets, _, _, _) => move
    }
    val warbandMoves = leaves.collect {
      case move @ Move(_: Piece.Warbands, _, _, _) => move
    }
    val (secretReasons, plannedSecrets) =
      ResourceOperations.planSecrets(ready, secretMoves)

    val accumulated = Vector.newBuilder[OperationError]
    accumulated ++= CardMovementOperations.positionViolations(leaves)
    accumulated ++= CardMovementOperations.cardViolations(ready, leaves)
    accumulated ++= ResourceOperations.resourceDescriptionViolations(
      ready, operation)
    accumulated ++= PayCostRules.violations(ready, operation)
    accumulated ++= ResourceOperations.countedSourceViolations(
      ready, favorMoves, warbandMoves, secretReasons)
    accumulated ++= ResourceOperations.countedDestinationViolations(
      ready, leaves)
    accumulated ++= BoardControlOperations.pawnAndBannerViolations(ready, leaves)
    accumulated ++= nonMoveViolations(ready, leaves, plannedSecrets)
    accumulated.result()
  }

  // ------------------------------------------------------------------
  // 5. Non-move primitive guards
  // ------------------------------------------------------------------

  private final case class RunningBoards(
      faceUp: Map[PlayerId, Int],
      faceDown: Map[PlayerId, Int],
      supply: Map[PlayerId, Int]
  )

  private object RunningBoards {
    def initial(
        ready: ReadyGame,
        planned: Option[Vector[(Move, OperationSecretPlanner.SecretSplit)]]
    ): RunningBoards = {
      val boards = ready.game.current.players.iterator.map { player =>
        (player.player, (player.board.faceUpSecrets,
          player.board.faceDownSecrets, player.board.supply.supply))
      }.toMap
      val deltas = planned match {
        case None => Map.empty[PlayerId, (Int, Int)]
        case Some(values) => values.foldLeft(Map.empty[PlayerId, (Int, Int)]) {
          case (accumulated, (move, split)) =>
            val fromAdjusted = move.from.location match {
              case Location.PlayArea(player) => addDelta(accumulated, player,
                -split.faceUp, -split.faceDown)
              case _ => accumulated
            }
            move.to.location match {
              case Location.PlayArea(player) => addDelta(fromAdjusted, player,
                split.faceUp, split.faceDown)
              case _ => fromAdjusted
            }
        }
      }
      RunningBoards(
        faceUp = boards.iterator.map { case (player, (up, _, _)) =>
          player -> (up + deltas.getOrElse(player, (0, 0))._1)
        }.toMap,
        faceDown = boards.iterator.map { case (player, (_, down, _)) =>
          player -> (down + deltas.getOrElse(player, (0, 0))._2)
        }.toMap,
        supply = boards.iterator.map { case (player, (_, _, value)) =>
          player -> value
        }.toMap
      )
    }

    private def addDelta(
        deltas: Map[PlayerId, (Int, Int)],
        player: PlayerId,
        faceUp: Int,
        faceDown: Int
    ): Map[PlayerId, (Int, Int)] = {
      val current = deltas.getOrElse(player, (0, 0))
      deltas.updated(player, (current._1 + faceUp, current._2 + faceDown))
    }
  }

  private def nonMoveViolations(
      ready: ReadyGame,
      leaves: Vector[Operation],
      plannedSecrets: Option[Vector[(Move,
        OperationSecretPlanner.SecretSplit)]]
  ): Vector[OperationError] = {
    val initial = RunningBoards.initial(ready, plannedSecrets)
    val (reasons, _) = leaves.foldLeft[(Vector[OperationError],
      RunningBoards)]((Vector.empty, initial)) {
      case ((result, state), Flip(id, at, orientation)) =>
        (result ++ CardFaceOperations.flipViolation(ready, id, at, orientation),
          state)
      case ((result, state), FlipSecrets(player, amount, from, to)) =>
        val (violations, faceUp, faceDown) =
          ResourceOperations.flipSecretsViolation(ready, player, amount,
            from, to, state.faceUp, state.faceDown)
        (result ++ violations, state.copy(faceUp = faceUp, faceDown = faceDown))
      case ((result, state), Peek(viewer, id, at)) =>
        (result ++ CardFaceOperations.peekViolation(ready, viewer, id, at), state)
      case ((result, state), SpendSupply(player, amount, _)) =>
        val (violations, supply) = TurnStateOperations.supplyViolation(
          ready, player, -amount, state.supply)
        (result ++ violations, state.copy(supply = supply))
      case ((result, state), GainSupply(player, amount)) =>
        val (violations, supply) = TurnStateOperations.supplyViolation(
          ready, player, amount, state.supply)
        (result ++ violations, state.copy(supply = supply))
      case ((result, state), AdvanceVisionsDrawn) =>
        (result ++ TurnStateOperations.visionsDrawnViolation(ready), state)
      case ((result, state), _) => (result, state)
    }
    reasons
  }

}
