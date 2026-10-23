package oathdigital.gameplay.operations

import oathdigital.model.ReadyGame
import oathdigital.model.{Location, Move, OperationError, Piece, SecretSide}


/** Resolves secret orientations for one atomic operation snapshot. */
private[operations] object OperationSecretPlanner:
  import OperationError._
  import OperationStateAdapter.secrets

  private[operations] final case class SecretSplit(
      faceUp: Int,
      faceDown: Int
  )

  private[operations] def plan(
      ready: ReadyGame,
      moves: Vector[Move]
  ): Either[OperationError, Vector[(Move, SecretSplit)]] =
    val grouped = moves.zipWithIndex
      .groupBy(_._1.from.location)
      .toVector
      .sortBy(_._2.map(_._2).min)
    OperationStateWrites.sequence(grouped.map { case (source, indexed) =>
      planSecretSource(ready, source, indexed.sortBy(_._2))
    }).map(_.flatten.sortBy(_._1).map { case (_, move, split) =>
      move -> split
    })

  private def planSecretSource(
      ready: ReadyGame,
      source: Location,
      indexed: Vector[(Move, Int)]
  ): Either[OperationError, Vector[(Int, Move, SecretSplit)]] =
    if source == Location.SharedBank then
      Right(indexed.map { case (move, index) =>
        val amount = move.piece.asInstanceOf[Piece.Secrets].amount
        (index, move, SecretSplit(amount, 0))
      })
    else secrets(ready, source).flatMap { inventory =>
      // A move naming a side takes exactly that side. Without one, only
      // faceup secrets can be burned, so a SharedBank (burn) destination is
      // a mandatory-faceup move just like a card or site destination, and a
      // move to a board takes either side.
      val empty = Vector.empty[(Move, Int)]
      val (faceUpOnly, faceDownOnly, flexible) =
        indexed.foldLeft((empty, empty, empty)):
          case ((up, down, free), entry) =>
            (entry._1.from.side, entry._1.to.location) match
              case (Some(SecretSide.FaceUp), _) => (up :+ entry, down, free)
              case (Some(SecretSide.FaceDown), _) => (up, down :+ entry, free)
              case (None, _: Location.PlayArea) => (up, down, free :+ entry)
              case (None, _) => (up :+ entry, down, free)
      val mandatoryFaceUp = secretAmount(faceUpOnly)
      val mandatoryFaceDown = secretAmount(faceDownOnly)
      val flexibleTotal = secretAmount(flexible)
      val total = mandatoryFaceUp + mandatoryFaceDown + flexibleTotal
      val availableFaceUp = inventory.faceUp.toLong
      val availableFaceDown = inventory.faceDown.toLong
      val availableTotal = availableFaceUp + availableFaceDown

      if mandatoryFaceUp > availableFaceUp then
        Left(InsufficientPieces(
          Piece.Secrets(clampPositive(mandatoryFaceUp)),
          source,
          clampNonNegative(availableFaceUp)
        ))
      else if mandatoryFaceDown > availableFaceDown then
        Left(InsufficientPieces(
          Piece.Secrets(clampPositive(mandatoryFaceDown)),
          source,
          clampNonNegative(availableFaceDown)
        ))
      else if total > availableTotal then
        Left(InsufficientPieces(
          Piece.Secrets(clampPositive(total)),
          source,
          clampNonNegative(availableTotal)
        ))
      else
        val remainingFaceUp = availableFaceUp - mandatoryFaceUp
        val remainingFaceDown = availableFaceDown - mandatoryFaceDown
        val minimumFlexibleFaceUp = math.max(
          0L, flexibleTotal - remainingFaceDown)
        val maximumFlexibleFaceUp = math.min(
          flexibleTotal, remainingFaceUp)
        uniqueFlexibleSecretSplits(
          source,
          flexible,
          flexibleTotal,
          minimumFlexibleFaceUp,
          maximumFlexibleFaceUp
        ).map { flexibleSplits =>
          val faceUp = faceUpOnly.map { case (move, index) =>
            val amount = move.piece.asInstanceOf[Piece.Secrets].amount
            (index, move, SecretSplit(amount, 0))
          }
          val faceDown = faceDownOnly.map { case (move, index) =>
            val amount = move.piece.asInstanceOf[Piece.Secrets].amount
            (index, move, SecretSplit(0, amount))
          }
          faceUp ++ faceDown ++ flexibleSplits
        }
    }

  private def uniqueFlexibleSecretSplits(
      source: Location,
      flexible: Vector[(Move, Int)],
      total: Long,
      minimumFaceUp: Long,
      maximumFaceUp: Long
  ): Either[OperationError, Vector[(Int, Move, SecretSplit)]] =
    val movedAmount = clampPositive(total)
    if minimumFaceUp > maximumFaceUp then
      Left(InsufficientPieces(Piece.Secrets(movedAmount), source, 0))
    else if minimumFaceUp != maximumFaceUp then
      Left(AmbiguousSecretOrientation(source, movedAmount))
    else
      val faceUp = minimumFaceUp
      flexible match
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

  private def secretAmount(values: Vector[(Move, Int)]): Long =
    values.iterator.map { case (move, _) =>
      move.piece.asInstanceOf[Piece.Secrets].amount.toLong
    }.sum

  private def clampPositive(value: Long): Int =
    math.max(1L, math.min(Int.MaxValue.toLong, value)).toInt

  private def clampNonNegative(value: Long): Int =
    math.max(0L, math.min(Int.MaxValue.toLong, value)).toInt
