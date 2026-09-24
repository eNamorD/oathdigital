package oathdigital.gameplay.operations

import oathdigital.model._

/** The two dispatchers over the operation families.
  *
  * [[validate]] aggregates every family's shape guard against `ready`, in a
  * fixed order, so the resolver can inspect all of an operation's reasons
  * before anything runs. [[mutate]] runs the same guard as its precondition
  * and then the family mutations in their fixed order. The live pipeline and
  * replay both reach the mutation through [[OperationExecutor]], so a guard
  * written once beside its mutation is a guard on both paths.
  *
  * The mutation runs families in the order counted resources, pawn and
  * banner, cards, then everything else; the guard checks in the order the
  * old shape layer used, which is what keeps every rejection code and detail
  * the same as before the families were split.
  *
  * The family objects' `apply*` and mutation members are package-visible
  * only because this dispatcher is their sole caller. Call [[mutate]], never
  * a family directly: a family entered on its own runs with no guard.
  */
private[gameplay] object OperationApplication {
  import OperationError._

  /** All shape violations for one operation against `ready`, aggregated. */
  def validate(
      ready: ReadyGame,
      operation: CoreOperation
  ): Vector[OperationReason] =
    violations(ready, operation).map(reason(_, operation))

  /** Guard first, then mutate: the first shape violation rejects before any
    * family runs, so no family mutation ever sees an operation the shape
    * guard refuses.
    */
  private[operations] def mutate(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, ReadyGame] =
    violations(ready, operation).headOption.toLeft(())
      .flatMap(_ => applyFamilies(ready, operation))

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
    accumulated ++= BoardControlOperations.pawnAndBannerViolations(
      ready, leaves)
    accumulated ++= nonMoveViolations(ready, leaves, plannedSecrets)
    accumulated.result()
  }

  private def applyFamilies(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, ReadyGame] = {
    val leaves = Operation.flatten(operation)
    for {
      resources <- ResourceOperations.applyCountedMoves(ready, leaves)
      pieces <- BoardControlOperations.applyPawnAndBannerMoves(
        resources, leaves)
      cards <- CardMovementOperations.applyCardMoves(pieces, leaves)
      finished <- applyNonMoveLeaves(cards, leaves)
    } yield finished
  }

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
