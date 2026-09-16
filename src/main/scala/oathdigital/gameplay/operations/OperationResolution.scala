package oathdigital.gameplay.operations

import oathdigital.gameplay.{OathViolation, ReadyGame}
import oathdigital.model._

/** Pure command-time resolution. It never applies an operation to game state. */
object OperationResolution {
  sealed trait Result
  final case class Execute(actual: CoreOperation) extends Result
  final case class Skip(reasons: Vector[OperationReason]) extends Result

  def resolve(ready: ReadyGame, requested: CoreOperation,
      validator: OperationValidator): Either[OathViolation, Result] = {
    val reasons = validator.validateOne(ready, requested)
    val invalid = reasons.find(_.kind == OperationReasonKind.Invalid)
    invalid match {
      case Some(reason) => Left(rejection(reason))
      case None if requested.required =>
        reasons.headOption match {
          case Some(reason) => Left(rejection(reason))
          case None => Right(Execute(requested))
        }
      case None => optional(ready, requested, validator, reasons)
    }
  }

  private def optional(ready: ReadyGame, requested: CoreOperation,
      validator: OperationValidator,
      reasons: Vector[OperationReason]): Either[OathViolation, Result] = {
    counted(requested) match {
      case None =>
        if (reasons.isEmpty) Right(Execute(requested))
        else Right(Skip(reasons))
      case Some((maximum, rebuild)) =>
        val capped = requested match {
          case GainSupply(player, _) => ready.game.current.players
            .find(_.player == player).fold(0)(p =>
              math.max(0, SupplyTrack.Maximum - p.board.supply.supply))
          case ModifyDicePool(pool, delta, _) =>
            val current = ready.game.current.rollPools.get(pool).fold(0)(_.count)
            if (delta < 0) math.max(0, current)
            else math.max(0, Int.MaxValue - current)
          case _ => maximum
        }
        val upper = math.min(maximum, capped)
        if (upper == maximum && reasons.isEmpty)
          Right(Execute(requested))
        else if (upper == 0)
          Right(Skip(if (reasons.nonEmpty) reasons else Vector(
            OperationReason("no-available-count", "no counted effect is available",
              OperationReasonKind.Impossible))))
        else {
          var low = 0
          var high = upper
          while (low < high) {
            val mid = low + (high - low + 1) / 2
            val candidate = rebuild(mid)
            val candidateReasons = validator.validateResolvedOne(ready, candidate)
            candidateReasons.find(_.kind == OperationReasonKind.Invalid) match {
              case Some(reason) => return Left(rejection(reason))
              case None if candidateReasons.isEmpty => low = mid
              case None => high = mid - 1
            }
          }
          if (low == 0) Right(Skip(reasons))
          else Right(Execute(rebuild(low)))
        }
    }
  }

  private def counted(operation: CoreOperation): Option[(Int, Int => CoreOperation)] =
    operation match {
      case value: GainSupply => Some(value.amount -> (n => value.copy(amount = n)))
      case value: SpendSupply => Some(value.amount -> (n => value.copy(amount = n)))
      case value: Gain.Favor => Some(value.amount -> (n => value.copy(amount = n)))
      case value: Gain.Secrets => Some(value.amount -> (n => value.copy(amount = n)))
      case value: Gain.Warbands => Some(value.amount -> (n => value.copy(amount = n)))
      case value: FlipSecrets => Some(value.amount -> (n => value.copy(amount = n)))
      case value: Move => countedPiece(value.piece).map { case (amount, piece) =>
        amount -> ((n: Int) => value.copy(piece = piece(n)))
      }
      case value: Take => countedPiece(value.piece).map { case (amount, piece) =>
        amount -> ((n: Int) => value.copy(piece = piece(n)))
      }
      case value: Give => countedPiece(value.piece).map { case (amount, piece) =>
        amount -> ((n: Int) => value.copy(piece = piece(n)))
      }
      case value: Burn => countedPiece(value.resource).map {
        case (amount, piece) => amount -> ((n: Int) => piece(n) match {
          case Piece.Favor(count) => Burn.favor(count, value.from)
          case Piece.Secrets(count) => Burn.secrets(count, value.from)
          case _ => throw new IllegalStateException("burn resource changed kind")
        })
      }
      case value: Kill => Some(value.warbands.amount -> ((n: Int) =>
        value.copy(warbands = value.warbands.copy(amount = n))))
      case value: Sacrifice => Some(value.warbands.amount -> ((n: Int) =>
        value.copy(warbands = value.warbands.copy(amount = n))))
      case value: Replace => Some(value.removed.amount -> ((n: Int) =>
        value.copy(removed = value.removed.copy(amount = n),
          replacements = value.replacements.copy(amount = n))))
      case value: ModifyDicePool if value.delta != 0 =>
        Some((if (value.delta == Int.MinValue) Int.MaxValue
          else math.abs(value.delta)) -> ((n: Int) =>
          value.copy(delta = if (value.delta < 0) -n else n)))
      case _ => None
    }

  private def countedPiece(piece: Piece): Option[(Int, Int => Piece)] = piece match {
    case Piece.Favor(amount) => Some(amount -> (n => Piece.Favor(n)))
    case Piece.Secrets(amount) => Some(amount -> (n => Piece.Secrets(n)))
    case Piece.Warbands(kind, amount) =>
      Some(amount -> (n => Piece.Warbands(kind, n)))
    case _ => None
  }

  private def rejection(reason: OperationReason): OathViolation =
    OathViolation.CoreOperationRejected(reason.code, reason.detail)
}
