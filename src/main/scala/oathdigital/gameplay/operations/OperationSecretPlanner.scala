package oathdigital.gameplay.operations

import oathdigital.model.ReadyGame


/** Resolves secret orientations for one atomic operation snapshot. */
private[operations] object OperationSecretPlanner {
  import OperationError._
  import OperationStateAdapter.secrets

  private[operations] final case class SecretSplit(
      faceUp: Int,
      faceDown: Int
  )

  private[operations] def plan(
      ready: ReadyGame,
      moves: Vector[Move]
  ): Either[OperationError, Vector[(Move, SecretSplit)]] = {
    val grouped = moves.zipWithIndex
      .groupBy(_._1.from.location)
      .toVector
      .sortBy(_._2.map(_._2).min)
    sequence(grouped.map { case (source, indexed) =>
      planSecretSource(ready, source, indexed.sortBy(_._2))
    }).map(_.flatten.sortBy(_._1).map { case (_, move, split) =>
      move -> split
    })
  }

  private def planSecretSource(
      ready: ReadyGame,
      source: Location,
      indexed: Vector[(Move, Int)]
  ): Either[OperationError, Vector[(Int, Move, SecretSplit)]] =
    if (source == Location.SharedBank)
      Right(indexed.map { case (move, index) =>
        val amount = move.piece.asInstanceOf[Piece.Secrets].amount
        (index, move, SecretSplit(amount, 0))
      })
    else secrets(ready, source).flatMap { inventory =>
      // Only faceup secrets can be burned, so a SharedBank (burn) destination
      // is a mandatory-faceup move just like a card or site destination. A burn
      // that would consume facedown secrets therefore fails as if the source
      // held no faceup secrets at all.
      val (faceUpOnly, flexible) =
        indexed.foldLeft[(Vector[(Move, Int)], Vector[(Move, Int)])](
          (Vector.empty, Vector.empty)) {
          case ((fixed, flexible), entry) => entry._1.to.location match {
            case _: Location.PlayArea => (fixed, flexible :+ entry)
            case _ => (fixed :+ entry, flexible)
          }
        }
      val mandatoryFaceUp = secretAmount(faceUpOnly)
      val flexibleTotal = secretAmount(flexible)
      val total = mandatoryFaceUp + flexibleTotal
      val availableFaceUp = inventory.faceUp.toLong
      val availableFaceDown = inventory.faceDown.toLong
      val availableTotal = availableFaceUp + availableFaceDown

      if (mandatoryFaceUp > availableFaceUp)
        Left(InsufficientPieces(
          Piece.Secrets(clampPositive(mandatoryFaceUp)),
          source,
          clampNonNegative(availableFaceUp)
        ))
      else if (total > availableTotal)
        Left(InsufficientPieces(
          Piece.Secrets(clampPositive(total)),
          source,
          clampNonNegative(availableTotal)
        ))
      else {
        val remainingFaceUp = availableFaceUp - mandatoryFaceUp
        val minimumFlexibleFaceUp = math.max(
          0L, flexibleTotal - availableFaceDown)
        val maximumFlexibleFaceUp = math.min(
          flexibleTotal, remainingFaceUp)
        uniqueFlexibleSecretSplits(
          source,
          flexible,
          flexibleTotal,
          minimumFlexibleFaceUp,
          maximumFlexibleFaceUp
        ).map { flexibleSplits =>
          val mandatory = faceUpOnly.map { case (move, index) =>
            val amount = move.piece.asInstanceOf[Piece.Secrets].amount
            (index, move, SecretSplit(amount, 0))
          }
          mandatory ++ flexibleSplits
        }
      }
    }

  private def uniqueFlexibleSecretSplits(
      source: Location,
      flexible: Vector[(Move, Int)],
      total: Long,
      minimumFaceUp: Long,
      maximumFaceUp: Long
  ): Either[OperationError, Vector[(Int, Move, SecretSplit)]] = {
    val movedAmount = clampPositive(total)
    if (minimumFaceUp > maximumFaceUp)
      Left(InsufficientPieces(Piece.Secrets(movedAmount), source, 0))
    else if (minimumFaceUp != maximumFaceUp)
      Left(AmbiguousSecretOrientation(source, movedAmount))
    else {
      val faceUp = minimumFaceUp
      flexible match {
        case Vector() => Right(Vector.empty)
        case Vector((move, index)) =>
          val amount = move.piece.asInstanceOf[Piece.Secrets].amount
          Right(Vector((index, move, SecretSplit(
            faceUp.toInt, amount - faceUp.toInt))))
        case values if faceUp == 0L => Right(values.map {
          case (move, index) =>
            val amount = move.piece.asInstanceOf[Piece.Secrets].amount
            (index, move, SecretSplit(0, amount))
        })
        case values if faceUp == total => Right(values.map {
          case (move, index) =>
            val amount = move.piece.asInstanceOf[Piece.Secrets].amount
            (index, move, SecretSplit(amount, 0))
        })
        case _ => Left(AmbiguousSecretOrientation(source, movedAmount))
      }
    }
  }

  private def secretAmount(values: Vector[(Move, Int)]): Long =
    values.iterator.map { case (move, _) =>
      move.piece.asInstanceOf[Piece.Secrets].amount.toLong
    }.sum

  private def clampPositive(value: Long): Int =
    math.max(1L, math.min(Int.MaxValue.toLong, value)).toInt

  private def clampNonNegative(value: Long): Int =
    math.max(0L, math.min(Int.MaxValue.toLong, value)).toInt

  private def sequence[A](
      values: Vector[Either[OperationError, A]]
  ): Either[OperationError, Vector[A]] =
    values.foldLeft[Either[OperationError, Vector[A]]](Right(Vector.empty)) {
      case (result, value) => for {
        accumulated <- result
        next <- value
      } yield accumulated :+ next
    }
}
