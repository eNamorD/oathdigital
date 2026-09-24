package oathdigital.gameplay.operations

import oathdigital.model._

/** Pawn and banner moves. The guard threads the pawns' sites and the
  * banners' holders through one operation's leaves so two moves of one piece
  * in one operation are checked in sequence.
  */
private[operations] object BoardControlOperations {
  import OperationError._
  import OperationStateAdapter.{bannerHolder, playerState, quantity, siteState}
  import OperationStateWrites.updatePlayer

  private final case class MovedPieces(
      pawnSites: Map[PlayerId, Option[SiteId]],
      bannerHolders: Map[Banner, Option[PlayerId]]
  )

  def pawnAndBannerViolations(
      ready: ReadyGame,
      leaves: Vector[Operation]
  ): Vector[OperationError] = {
    val pawnSites = ready.game.current.players.iterator.map { player =>
      player.player -> player.pawnSite
    }.toMap
    val bannerHolders = Map(
      Banner.PeoplesFavor -> bannerHolder(ready, Banner.PeoplesFavor),
      Banner.DarkestSecret -> bannerHolder(ready, Banner.DarkestSecret)
    )
    val (reasons, _) = leaves.foldLeft[(Vector[OperationError],
      MovedPieces)]((Vector.empty, MovedPieces(pawnSites, bannerHolders))) {
      case ((result, state), Move(Piece.Pawn(player), from, to, _)) =>
        val (violations, updated) =
          pawnMoveViolation(ready, player, from.location, to.location, state)
        (result ++ violations, updated)
      case ((result, state), Move(Piece.Banner(banner), from, to, _)) =>
        val (violations, updated) =
          bannerMoveViolation(ready, banner, from.location, to.location, state)
        (result ++ violations, updated)
      case ((result, state), _) => (result, state)
    }
    reasons
  }

  private def pawnMoveViolation(
      ready: ReadyGame,
      player: PlayerId,
      from: Location,
      to: Location,
      state: MovedPieces
  ): (Vector[OperationError], MovedPieces) = (from, to) match {
    case (Location.PlayArea(source), Location.Site(destination))
        if source == player =>
      playerState(ready, player) match {
        case Left(error) => (Vector(error), state)
        case Right(_) =>
          val located = state.pawnSites.getOrElse(player, None)
          if (located.nonEmpty)
            (Vector(MissingPiece(Piece.Pawn(player), from)), state)
          else siteState(ready, destination) match {
            case Left(error) => (Vector(error), state)
            case Right(_) => (Vector.empty, state.copy(
              pawnSites = state.pawnSites.updated(player, Some(destination))))
          }
      }
    case (Location.Site(source), Location.Site(destination)) =>
      playerState(ready, player) match {
        case Left(error) => (Vector(error), state)
        case Right(_) =>
          val located = state.pawnSites.getOrElse(player, None)
          if (!located.contains(source))
            (Vector(MissingPiece(Piece.Pawn(player), from)), state)
          else siteState(ready, destination) match {
            case Left(error) => (Vector(error), state)
            case Right(_) => (Vector.empty, state.copy(
              pawnSites = state.pawnSites.updated(player, Some(destination))))
          }
      }
    case _ =>
      (Vector(IncompatibleLocation(Piece.Pawn(player), to)), state)
  }

  private def bannerMoveViolation(
      ready: ReadyGame,
      banner: Banner,
      from: Location,
      to: Location,
      state: MovedPieces
  ): (Vector[OperationError], MovedPieces) = (from, to) match {
    case (Location.PlayArea(source), Location.PlayArea(destination)) =>
      playerState(ready, destination) match {
        case Left(error) => (Vector(error), state)
        case Right(_) =>
          val holder = state.bannerHolders.getOrElse(banner, None)
          if (!holder.contains(source))
            (Vector(MissingPiece(Piece.Banner(banner), from)), state)
          else (Vector.empty, state.copy(
            bannerHolders = state.bannerHolders.updated(banner,
              Some(destination))))
      }
    case (Location.SharedBank, Location.PlayArea(destination)) =>
      playerState(ready, destination) match {
        case Left(error) => (Vector(error), state)
        case Right(_) =>
          val holder = state.bannerHolders.getOrElse(banner, None)
          if (holder.isEmpty)
            (Vector.empty, state.copy(
              bannerHolders = state.bannerHolders.updated(banner,
                Some(destination))))
          else (Vector(InsufficientPieces(Piece.Banner(banner), from, 0)),
            state)
      }
    case _ =>
      (Vector(IncompatibleLocation(Piece.Banner(banner), to)), state)
  }

  def applyPawnAndBannerMoves(
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
}
