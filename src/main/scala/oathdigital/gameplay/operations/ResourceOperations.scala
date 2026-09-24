package oathdigital.gameplay.operations

import oathdigital.model._

/** Favor, secrets and warbands: counted moves, secret flips, and the
  * resource descriptions a discard carries. The mutation debits every source
  * before crediting any destination, so a batch that moves the same resource
  * twice within one operation never sees an intermediate credit.
  */
private[operations] object ResourceOperations {
  import OperationError._
  import OperationStateAdapter.{playerState, quantity, siteState}
  import OperationStateWrites.{updateCardTokens, updatePlayer, updateSite}

  // ------------------------------------------------------------------
  // Guards
  // ------------------------------------------------------------------

  def resourceDescriptionViolations(ready: ReadyGame,
      operation: CoreOperation): Vector[OperationError] = {
    val described: Option[(CardId, Option[Int], Int)] = operation match {
      case value: Discard.Denizen =>
        Some((value.card, Some(value.favor), value.secrets))
      case value: Discard.RuinedEdifice =>
        Some((value.card, Some(value.favor), value.secrets))
      case value: Discard.Relic => Some((value.card, None, value.secrets))
      case _ => None
    }
    described.toVector.flatMap { case (cardId, favor, secrets) =>
      val at = Location.OnCard(cardId)
      val counts = for {
        actualFavor <- quantity(ready, Piece.Favor(1), at)
        actualSecrets <- quantity(ready, Piece.Secrets(1), at)
      } yield (actualFavor, actualSecrets)
      counts match {
        case Right((AvailableQuantity.Finite(actualFavor),
            AvailableQuantity.Finite(actualSecrets)))
            if favor.exists(_ != actualFavor) || actualSecrets != secrets =>
          Vector(InvalidDescription(
            s"discard resources on ${cardId.value} do not match the card"))
        case _ => Vector.empty
      }
    }
  }

  def planSecrets(
      ready: ReadyGame,
      moves: Vector[Move]
  ): (Vector[OperationError],
      Option[Vector[(Move, OperationSecretPlanner.SecretSplit)]]) =
    OperationSecretPlanner.plan(ready, moves) match {
      case Left(error) => (Vector(error), None)
      case Right(planned) => (Vector.empty, Some(planned))
    }

  def countedSourceViolations(
      ready: ReadyGame,
      favorMoves: Vector[Move],
      warbandMoves: Vector[Move],
      secretReasons: Vector[OperationError]
  ): Vector[OperationError] =
    favorSourceViolations(ready, favorMoves) ++
      secretReasons ++
      warbandSourceViolations(ready, warbandMoves)

  def countedDestinationViolations(ready: ReadyGame,
      leaves: Vector[Operation]): Vector[OperationError] =
    leaves.flatMap {
      case Move(piece: Piece.Counted, _, to, _)
          if piece.isInstanceOf[Piece.Favor] &&
            to.location == Location.SharedBank => Vector.empty
      case Move(piece: Piece.Counted, _, to, _) =>
        quantity(ready, piece, to.location).left.toOption.toVector
      case _ => Vector.empty
    }

  private def favorSourceViolations(
      ready: ReadyGame,
      moves: Vector[Move]
  ): Vector[OperationError] =
    moves.groupBy(_.from.location).toVector.flatMap {
      case (location, values) =>
        val amount = values.map(_.piece.asInstanceOf[Piece.Favor].amount).sum
        val piece = Piece.Favor(amount)
        quantity(ready, piece, location) match {
          case Left(error) => Vector(error)
          case Right(available) =>
            finiteSufficiency(piece, location, available, amount)
        }
    }

  private def warbandSourceViolations(
      ready: ReadyGame,
      moves: Vector[Move]
  ): Vector[OperationError] =
    moves.groupBy(move =>
      move.piece.asInstanceOf[Piece.Warbands].kind -> move.from.location)
      .toVector.flatMap { case ((kind, location), values) =>
        val amount = values.map(
          _.piece.asInstanceOf[Piece.Warbands].amount).sum
        val piece = Piece.Warbands(kind, amount)
        quantity(ready, piece, location) match {
          case Left(error) => Vector(error)
          case Right(available) =>
            finiteSufficiency(piece, location, available, amount)
        }
      }

  private def finiteSufficiency(
      piece: Piece,
      location: Location,
      available: AvailableQuantity,
      requested: Int
  ): Vector[OperationError] = available match {
    case AvailableQuantity.Unbounded => Vector.empty
    case AvailableQuantity.Finite(value) =>
      if (value >= requested) Vector.empty
      else Vector(InsufficientPieces(piece, location, value))
  }

  /** Threads the running faceup and facedown counts through one operation's
    * leaves; `RunningBoards.initial` in the dispatcher seeds them with the
    * planned secret moves so a flip after a move sees the moved secrets.
    */
  def flipSecretsViolation(
      ready: ReadyGame,
      player: PlayerId,
      amount: Int,
      from: SecretSide,
      to: SecretSide,
      faceUp: Map[PlayerId, Int],
      faceDown: Map[PlayerId, Int]
  ): (Vector[OperationError], Map[PlayerId, Int], Map[PlayerId, Int]) =
    playerState(ready, player) match {
      case Left(error) => (Vector(error), faceUp, faceDown)
      case Right(_) =>
        val available = from match {
          case SecretSide.FaceUp => faceUp.getOrElse(player, 0)
          case SecretSide.FaceDown => faceDown.getOrElse(player, 0)
        }
        if (available < amount)
          (Vector(InsufficientPieces(Piece.Secrets(amount),
            Location.PlayArea(player), available)), faceUp, faceDown)
        else {
          val faceUpDelta = (from, to) match {
            case (SecretSide.FaceUp, SecretSide.FaceDown) => -amount
            case (SecretSide.FaceDown, SecretSide.FaceUp) => amount
            case _ => 0
          }
          (Vector.empty,
            faceUp.updated(player, faceUp.getOrElse(player, 0) + faceUpDelta),
            faceDown.updated(player,
              faceDown.getOrElse(player, 0) - faceUpDelta))
        }
    }

  // ------------------------------------------------------------------
  // Mutations
  // ------------------------------------------------------------------

  def applyCountedMoves(
      ready: ReadyGame,
      leaves: Vector[Operation]
  ): Either[OperationError, ReadyGame] = {
    val favorMoves = leaves.collect {
      case move @ Move(_: Piece.Favor, _, _, _) => move
    }
    val secretMoves = leaves.collect {
      case move @ Move(_: Piece.Secrets, _, _, _) => move
    }
    val warbandMoves = leaves.collect {
      case move @ Move(_: Piece.Warbands, _, _, _) => move
    }
    for {
      plannedSecrets <- OperationSecretPlanner.plan(ready, secretMoves)
      withoutFavor <- favorMoves.foldLeft[Either[OperationError, ReadyGame]](
        Right(ready)) { (result, move) =>
        val piece = move.piece.asInstanceOf[Piece.Favor]
        result.flatMap(adjustFavor(_, move.from.location, -piece.amount))
      }
      withFavor <- favorMoves.foldLeft[Either[OperationError, ReadyGame]](
        Right(withoutFavor)) { (result, move) =>
        val piece = move.piece.asInstanceOf[Piece.Favor]
        result.flatMap(adjustFavor(_, move.to.location, piece.amount))
      }
      withoutSecrets <- plannedSecrets.foldLeft[
        Either[OperationError, ReadyGame]](Right(withFavor)) {
        case (result, (move, split)) => result.flatMap(adjustSecrets(
          _, move.from.location, -split.faceUp, -split.faceDown))
      }
      withSecrets <- plannedSecrets.foldLeft[
        Either[OperationError, ReadyGame]](Right(withoutSecrets)) {
        case (result, (move, split)) => result.flatMap(adjustSecrets(
          _, move.to.location, split.faceUp, split.faceDown))
      }
      withoutWarbands <- warbandMoves.foldLeft[
        Either[OperationError, ReadyGame]](Right(withSecrets)) {
        case (result, move) =>
          val piece = move.piece.asInstanceOf[Piece.Warbands]
          result.flatMap(adjustWarbands(_, piece.kind, move.from.location,
            -piece.amount))
      }
      withWarbands <- warbandMoves.foldLeft[
        Either[OperationError, ReadyGame]](Right(withoutWarbands)) {
        case (result, move) =>
          val piece = move.piece.asInstanceOf[Piece.Warbands]
          result.flatMap(adjustWarbands(_, piece.kind, move.to.location,
            piece.amount))
      }
    } yield withWarbands
  }

  private def adjustFavor(
      ready: ReadyGame,
      at: Location,
      delta: Int
  ): Either[OperationError, ReadyGame] = at match {
    case Location.PlayArea(player) => updatePlayer(ready, player) { state =>
      state.copy(board = state.board.copy(favor = state.board.favor + delta))
    }
    case Location.Site(site) => updateSite(ready, site) { state =>
      state.copy(tokens = state.tokens.copy(favor = state.tokens.favor + delta))
    }
    case Location.OnCard(id) => updateCardTokens(ready, id) { tokens =>
      tokens.copy(favor = tokens.favor + delta)
    }
    case Location.OnBanner(Banner.PeoplesFavor) => Right(ready.copy(
      game = ready.game.copy(current = ready.game.current.copy(
        banners = ready.game.current.banners.copy(
          peoplesFavor = ready.game.current.banners.peoplesFavor.copy(
            favor = ready.game.current.banners.peoplesFavor.favor + delta
          )
        )
      ))
    ))
    case Location.FavorBank(suit) => Right(ready.copy(
      banks = ready.banks.copy(favor = ready.banks.favor.updated(
        suit,
        ready.banks.favor.getOrElse(suit, 0) + delta
      ))
    ))
    case Location.SharedBank if delta > 0 => Right(ready)
    case _ => Left(IncompatibleLocation(Piece.Favor(math.max(1, math.abs(delta))), at))
  }

  private def adjustSecrets(
      ready: ReadyGame,
      at: Location,
      faceUpDelta: Int,
      faceDownDelta: Int
  ): Either[OperationError, ReadyGame] = at match {
    case Location.PlayArea(player) => updatePlayer(ready, player) { state =>
      state.copy(board = state.board.copy(
        faceUpSecrets = state.board.faceUpSecrets + faceUpDelta,
        faceDownSecrets = state.board.faceDownSecrets + faceDownDelta
      ))
    }
    case Location.Site(site) if faceDownDelta == 0 => updateSite(ready, site) {
      state => state.copy(tokens = state.tokens.copy(
        secrets = state.tokens.secrets + faceUpDelta))
    }
    case Location.OnCard(id) if faceDownDelta == 0 =>
      updateCardTokens(ready, id) { tokens =>
        tokens.copy(secrets = tokens.secrets + faceUpDelta)
      }
    case Location.OnBanner(Banner.DarkestSecret) if faceDownDelta == 0 =>
      Right(ready.copy(game = ready.game.copy(current = ready.game.current.copy(
        banners = ready.game.current.banners.copy(
          darkestSecret = ready.game.current.banners.darkestSecret.copy(
            secrets = ready.game.current.banners.darkestSecret.secrets + faceUpDelta
          )
        )
      ))))
    case Location.SharedBank => Right(ready)
    case _ => Left(IncompatibleLocation(Piece.Secrets(
      math.max(1, math.abs(faceUpDelta) + math.abs(faceDownDelta))), at))
  }

  private def adjustWarbands(
      ready: ReadyGame,
      kind: ForceKind,
      at: Location,
      delta: Int
  ): Either[OperationError, ReadyGame] = at match {
    case Location.WarbandBank(`kind`) => Right(ready)
    case Location.PlayArea(player) => updatePlayer(ready, player) { state =>
      state.copy(board = state.board.copy(warbands = state.board.warbands + delta))
    }.flatMap { updated =>
      quantity(updated, Piece.Warbands(kind, 1), Location.PlayArea(player))
        .map(_ => updated)
    }
    case Location.Site(site) => siteState(ready, site).flatMap { state =>
      state.forces match {
        case SiteForces.Occupied(other, _) if other != kind =>
          Left(ConflictingDeltas("site cannot contain multiple force kinds"))
        case forces =>
          val current = forces match {
            case SiteForces.Empty => 0
            case SiteForces.Occupied(_, count) => count
          }
          val next = current + delta
          Either.cond(next >= 0, (), InsufficientPieces(
            Piece.Warbands(kind, math.max(1, -delta)), at, current)).flatMap { _ =>
            updateSite(ready, site)(_.copy(forces =
              if (next == 0) SiteForces.Empty
              else SiteForces.Occupied(kind, next)))
          }
      }
    }
    case _ => Left(IncompatibleLocation(Piece.Warbands(kind, math.max(1,
      math.abs(delta))), at))
  }

  def flipPlayerSecrets(ready: ReadyGame, player: PlayerId, amount: Int,
      from: SecretSide, to: SecretSide): Either[OperationError, ReadyGame] =
    playerState(ready, player).flatMap { state =>
      val available = from match {
        case SecretSide.FaceUp => state.board.faceUpSecrets
        case SecretSide.FaceDown => state.board.faceDownSecrets
      }
      Either.cond(available >= amount, (), InsufficientPieces(
        Piece.Secrets(amount), Location.PlayArea(player), available)).flatMap { _ =>
        updatePlayer(ready, player) { value =>
          val faceUpDelta = (from, to) match {
            case (SecretSide.FaceUp, SecretSide.FaceDown) => -amount
            case (SecretSide.FaceDown, SecretSide.FaceUp) => amount
            case _ => 0
          }
          value.copy(board = value.board.copy(
            faceUpSecrets = value.board.faceUpSecrets + faceUpDelta,
            faceDownSecrets = value.board.faceDownSecrets - faceUpDelta))
        }
      }
    }
}
