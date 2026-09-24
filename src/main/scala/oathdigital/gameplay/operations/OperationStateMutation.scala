package oathdigital.gameplay.operations

import oathdigital.model._

/** Internal immutable mutation planner for counted pieces and primitive effects. */
private[operations] object OperationStateMutation {
  import OperationError._
  import OperationStateAdapter._
  import OperationStateWrites._

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
      resources <- applyCountedMoves(ready, leaves)
      pieces <- applyPawnAndBannerMoves(resources, leaves)
      cards <- OperationCardMutation.applyCardMoves(pieces, leaves)
      finished <- applyNonMoveLeaves(cards, leaves)
    } yield finished
  }

  private def applyCountedMoves(
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

  private def requireFinite(
      piece: Piece,
      location: Location,
      available: AvailableQuantity,
      requested: Int
  ): Either[OperationError, Unit] = available match {
    case AvailableQuantity.Unbounded => Right(())
    case AvailableQuantity.Finite(value) => Either.cond(
      value >= requested,
      (),
      InsufficientPieces(piece, location, value)
    )
  }

  private def applyPawnAndBannerMoves(
      ready: ReadyGame,
      leaves: Vector[Operation]
  ): Either[OperationError, ReadyGame] =
    leaves.foldLeft[Either[OperationError, ReadyGame]](Right(ready)) {
      case (result, Move(Piece.Pawn(player), from, to, _)) =>
        result.flatMap(movePawn(_, player, from.location, to.location))
      case (result, Move(Piece.Banner(banner), from, to, _)) =>
        result.flatMap(moveBanner(_, banner, from.location, to.location))
      case (result, _) => result
    }

  private def movePawn(ready: ReadyGame, player: PlayerId,
      from: Location, to: Location): Either[OperationError, ReadyGame] =
    (from, to) match {
      case (Location.PlayArea(source), Location.Site(destination))
          if source == player =>
        playerState(ready, player).flatMap { state =>
          Either.cond(state.pawnSite.isEmpty, (),
            MissingPiece(Piece.Pawn(player), from)).flatMap { _ =>
            siteState(ready, destination).flatMap(_ =>
              updatePlayer(ready, player)(_.copy(pawnSite = Some(destination))))
          }
        }
      case (Location.Site(source), Location.Site(destination)) =>
        playerState(ready, player).flatMap { state =>
          Either.cond(state.pawnSite.contains(source), (),
            MissingPiece(Piece.Pawn(player), from)).flatMap { _ =>
            siteState(ready, destination).flatMap(_ =>
              updatePlayer(ready, player)(_.copy(pawnSite = Some(destination))))
          }
        }
      case _ => Left(IncompatibleLocation(Piece.Pawn(player), to))
    }

  private def moveBanner(ready: ReadyGame, banner: Banner,
      from: Location, to: Location): Either[OperationError, ReadyGame] =
    (from, to) match {
      case (Location.PlayArea(source), Location.PlayArea(destination)) =>
        for {
          _ <- playerState(ready, destination)
          _ <- Either.cond(bannerHolder(ready, banner).contains(source), (),
            MissingPiece(Piece.Banner(banner), from))
        } yield setBannerHolder(ready, banner, destination)
      case (Location.SharedBank, Location.PlayArea(destination)) =>
        for {
          _ <- playerState(ready, destination)
          available <- quantity(ready, Piece.Banner(banner), from)
          _ <- requireFinite(Piece.Banner(banner), from, available, 1)
        } yield setBannerHolder(ready, banner, destination)
      case _ => Left(IncompatibleLocation(Piece.Banner(banner), to))
    }

  private def setBannerHolder(ready: ReadyGame, banner: Banner,
      player: PlayerId): ReadyGame = {
    val current = ready.game.current
    val banners = banner match {
      case Banner.PeoplesFavor => current.banners.copy(
        peoplesFavor = current.banners.peoplesFavor.copy(holder = Some(player)))
      case Banner.DarkestSecret => current.banners.copy(
        darkestSecret = current.banners.darkestSecret.copy(holder = Some(player)))
    }
    ready.copy(game = ready.game.copy(current = current.copy(banners = banners)))
  }

  private def applyNonMoveLeaves(
      ready: ReadyGame,
      leaves: Vector[Operation]
  ): Either[OperationError, ReadyGame] =
    leaves.foldLeft[Either[OperationError, ReadyGame]](Right(ready)) {
      case (result, Flip(id, at, orientation)) =>
        result.flatMap(CardFaceOperations.flipCard(_, id, at, orientation))
      case (result, FlipSecrets(player, amount, from, to)) =>
        result.flatMap(flipPlayerSecrets(_, player, amount, from, to))
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

  private def flipPlayerSecrets(ready: ReadyGame, player: PlayerId, amount: Int,
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
