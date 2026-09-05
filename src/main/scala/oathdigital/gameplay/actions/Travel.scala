package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.gameplay.OathContinue._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._
import GameStateUpdates.updateCurrent
import oathdigital.gameplay.operations.{AdjustSupply, CoreOperation,
  Location, Move => CoreMove,
  OperationPipeline, OperationPolicy, Piece, PositionedLocation}
import oathdigital.gameplay.powers.travel.{TravelCostLegality,
  TravelCostWindow}

sealed trait TravelCommand extends Product with Serializable
object TravelCommand {
  final case class Travel(playerId: PlayerId, destinationSiteId: SiteId)
      extends TravelCommand
}

object Travel {
  private val operationAllowlist: OperationPolicy =
    TravelOperationPolicy

  def handle(
      catalog: ExecutableCatalog,
      state: OathState,
      command: TravelCommand
  ): Either[OathViolation, OathTransition] = command match {
    case TravelCommand.Travel(playerId, destination) =>
      OathLifecycle.validateAct(state, playerId).flatMap { ready =>
        val player = ready.game.current.players.find(_.player == playerId).get
        player.pawnSite.toRight(PawnSiteMissing(playerId)).flatMap { source =>
          for {
            cost <- TravelRules.cost(catalog, ready, player, source, destination)
            _ <- TravelLegality.check(catalog, ready, player, source,
              destination)
            _ <- if (player.board.supply.supply < cost)
              Left(InsufficientSupply(cost, player.board.supply.supply))
            else Right(())
            transition <- transition(
              catalog,
              state,
              Vector(Traveled(playerId, source, destination, cost)),
              ActActionSelection(playerId)
            )
          } yield transition
        }
      }
  }
  def evolve(
      catalog: ExecutableCatalog,
      state: OathState,
      event: Traveled
  ): Either[OathViolation, OathState] =
    OathLifecycle.validateAct(state, event.playerId).flatMap { ready =>
      val current = ready.game.current
      val player = current.players.find(_.player == event.playerId).get
      player.pawnSite.toRight(PawnSiteMissing(event.playerId)).flatMap { source =>
        if (source != event.sourceSiteId)
          Left(TravelSourceMismatch(source, event.sourceSiteId))
        else for {
          expected <- TravelRules.cost(
            catalog, ready, player, source, event.destinationSiteId
          )
          _ <- if (expected != event.supplySpent)
            Left(TravelCostMismatch(expected, event.supplySpent))
          else Right(())
          _ <- TravelLegality.check(catalog, ready, player, source,
            event.destinationSiteId)
          _ <- if (player.board.supply.supply < expected)
            Left(InsufficientSupply(expected, player.board.supply.supply))
          else Right(())
          execution <- OperationPipeline.run(
            ready,
            Vector[CoreOperation](
              CoreMove(
                Piece.Pawn(event.playerId),
                PositionedLocation(Location.Site(event.sourceSiteId)),
                PositionedLocation(Location.Site(event.destinationSiteId))
              ),
              AdjustSupply(event.playerId, -expected)
            ),
            operationAllowlist
          )(Right(_))
        } yield Ready(execution)
      }
    }

  private def transition(
      catalog: ExecutableCatalog,
      state: OathState,
      events: Vector[OathEvent],
      continue: OathContinue
  ): Either[OathViolation, OathTransition] =
    GameplayTransition(state, events, continue) { (current, event) =>
      event match {
        case traveled: Traveled => evolve(catalog, current, traveled)
        case _ => Left(InvalidEventOrder("Travel received a non-Travel event"))
      }
    }
}

object TravelRules {
  import OathViolation._

  def legalDestinations(
      catalog: ExecutableCatalog,
      ready: ReadyGame,
      player: PlayerState
  ): Vector[(SiteId, Int)] =
    player.pawnSite.toVector.flatMap(source =>
      ready.game.current.map.inPlay.flatMap(destination =>
        TravelLegality.check(catalog, ready, player, source, destination)
          .flatMap(_ => cost(catalog, ready, player, source, destination))
          .toOption
          .filter(_ <= player.board.supply.supply)
          .map(destination -> _)))

  def cost(
      catalog: ExecutableCatalog,
      ready: ReadyGame,
      player: PlayerState,
      source: SiteId,
      destination: SiteId
  ): Either[OathViolation, Int] = {
    val map = ready.game.current.map
    for {
      _ <- validateSupportedState(catalog, ready)
      from <- map.regionOf(source).toRight(SiteNotInPlay(source))
      to <- map.regionOf(destination).toRight(SiteNotInPlay(destination))
      _ <- if (source == destination) Left(SameTravelSite(source)) else Right(())
      _ <- catalog.sites.find(_.id == source)
        .toRight(SiteNotInPlay(source))
      _ <- catalog.sites.find(_.id == destination)
        .toRight(SiteNotInPlay(destination))
      base = (from, to) match {
        case (Region.Cradle, Region.Cradle) => 1
        case (Region.Cradle, Region.Provinces) => 2
        case (Region.Cradle, Region.Hinterland) => 4
        case (Region.Provinces, _) => 2
        case (Region.Hinterland, Region.Cradle) => 4
        case (Region.Hinterland, Region.Provinces) => 2
        case (Region.Hinterland, Region.Hinterland) => 3
      }
    } yield TravelCostWindow.fold(
      catalog, ready, source, destination, base)
  }

  def validateSupportedState(
      catalog: ExecutableCatalog,
      ready: ReadyGame
  ): Either[OathViolation, Unit] = {
    val game = ready.game
    val reason =
      if (game.campaign.lineages.values.exists(_.role != Role.Exile))
        Some("Travel is limited to the exile-only first game")
      else if (game.campaign.foundations.values.exists(f =>
        f.face != FoundationFace.Normal || f.alterationSources.nonEmpty))
        Some("altered Foundations are not supported for Travel")
      else None
    reason match {
      case Some(value) => Left(UnsupportedTravelState(value))
      case None => PowerRuntime.requireAudited(catalog)
    }
  }
}

/** Travel-bound legality wrapper (Q53): Narrow Pass restrictions run only for
  * the Travel action, so a Campaign Raid pawn move never triggers them.
  */
object TravelLegality {
  def check(
      catalog: ExecutableCatalog,
      ready: ReadyGame,
      player: PlayerState,
      source: SiteId,
      destination: SiteId
  ): Either[OathViolation, Unit] =
    TravelCostLegality.blocked(catalog, ready, player.player, source,
      destination).toLeft(())
}
