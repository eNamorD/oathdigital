package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.FirstGameContinue._
import oathdigital.setup.FirstGameSetupEvent._
import oathdigital.setup.FirstGameSetupState._
import oathdigital.setup.FirstGameSetupViolation._

import oathdigital.gameplay.{GameStateUpdates, OathLifecycle}
import oathdigital.gameplay._
import GameStateUpdates.updateCurrent

sealed trait TravelCommand extends Product with Serializable
object TravelCommand {
  final case class Travel(playerId: PlayerId, destinationSiteId: SiteId)
      extends TravelCommand
}

object Travel {
  def handle(
      catalog: ExecutableCatalog,
      state: FirstGameSetupState,
      command: TravelCommand
  ): Either[FirstGameSetupViolation, FirstGameTransition] = command match {
    case TravelCommand.Travel(playerId, destination) =>
      OathLifecycle.validateAct(state, playerId).flatMap { ready =>
        val player = ready.game.current.players.find(_.player == playerId).get
        player.pawnSite.toRight(PawnSiteMissing(playerId)).flatMap { source =>
          TravelRules.cost(catalog, ready, player, source, destination)
            .flatMap { cost =>
              if (player.board.supply.supply < cost)
                Left(InsufficientSupply(cost, player.board.supply.supply))
              else transition(
                catalog,
                state,
                Vector(Traveled(playerId, source, destination, cost)),
                ActActionSelection(playerId)
              )
            }
        }
      }
  }
  def evolve(
      catalog: ExecutableCatalog,
      state: FirstGameSetupState,
      event: Traveled
  ): Either[FirstGameSetupViolation, FirstGameSetupState] =
    OathLifecycle.validateAct(state, event.playerId).flatMap { ready =>
      val current = ready.game.current
      val player = current.players.find(_.player == event.playerId).get
      player.pawnSite.toRight(PawnSiteMissing(event.playerId)).flatMap { source =>
        if (source != event.sourceSiteId)
          Left(TravelSourceMismatch(source, event.sourceSiteId))
        else TravelRules.cost(
          catalog, ready, player, source, event.destinationSiteId
        ).flatMap { expected =>
          if (expected != event.supplySpent)
            Left(TravelCostMismatch(expected, event.supplySpent))
          else if (player.board.supply.supply < expected)
            Left(InsufficientSupply(expected, player.board.supply.supply))
          else Right(Ready(updateCurrent(ready) { existing =>
            existing.copy(players = existing.players.map { candidate =>
              if (candidate.player != event.playerId) candidate
              else candidate.copy(
                pawnSite = Some(event.destinationSiteId),
                board = candidate.board.copy(supply = SupplyTrack(
                  candidate.board.supply.supply - expected)))
            })
          }))
        }
      }
    }

  private def transition(
      catalog: ExecutableCatalog,
      state: FirstGameSetupState,
      events: Vector[FirstGameSetupEvent],
      continue: FirstGameContinue
  ): Either[FirstGameSetupViolation, FirstGameTransition] =
    events.foldLeft[Either[FirstGameSetupViolation, FirstGameSetupState]](Right(state))(
      (next, event) => next.flatMap {
        case current => event match {
          case traveled: Traveled => evolve(catalog, current, traveled)
          case _ => Left(InvalidEventOrder("Travel received a non-Travel event"))
        }
      }).map(FirstGameTransition(_, events, continue))
}

object TravelRules {
  import FirstGameSetupViolation._

  def legalDestinations(
      catalog: ExecutableCatalog,
      ready: ReadyFirstGame,
      player: PlayerState
  ): Vector[(SiteId, Int)] =
    player.pawnSite.toVector.flatMap(source =>
      ready.game.current.map.inPlay.flatMap(destination =>
        cost(catalog, ready, player, source, destination).toOption
          .filter(_ <= player.board.supply.supply)
          .map(destination -> _)))

  def cost(
      catalog: ExecutableCatalog,
      ready: ReadyFirstGame,
      player: PlayerState,
      source: SiteId,
      destination: SiteId
  ): Either[FirstGameSetupViolation, Int] = {
    val map = ready.game.current.map
    for {
      _ <- validateSupportedState(ready)
      from <- map.regionOf(source).toRight(SiteNotInPlay(source))
      to <- map.regionOf(destination).toRight(SiteNotInPlay(destination))
      _ <- if (source == destination) Left(SameTravelSite(source)) else Right(())
      sourceDefinition <- catalog.sites.find(_.id == source)
        .toRight(SiteNotInPlay(source))
      destinationDefinition <- catalog.sites.find(_.id == destination)
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
      resolved <- resolveTravel(catalog, ready, player, source, destination,
        from, to, base, sourceDefinition.handlers, destinationDefinition.handlers)
    } yield resolved
  }

  def validateSupportedState(
      ready: ReadyFirstGame
  ): Either[FirstGameSetupViolation, Unit] = {
    val game = ready.game
    val current = game.current
    val reason =
      if (game.campaign.lineages.values.exists(_.role != Role.Exile))
        Some("Travel is limited to the exile-only first game")
      else if (game.campaign.foundations.values.exists(f =>
        f.face != FoundationFace.Normal || f.alterationSources.nonEmpty))
        Some("altered Foundations are not supported for Travel")
      else if (game.campaign.lineages.values.exists(_.legacies.exists(_.active)))
        Some("active legacy Travel modifiers are not supported")
      else if (current.players.exists(_.advisers.exists {
        case DenizenState(_, Orientation.FaceUp, _) => true
        case _ => false
      })) Some("face-up adviser Travel modifiers are not supported")
      else if (current.players.exists(_.relics.nonEmpty))
        Some("held relic Travel modifiers are not supported")
      else if (current.map.sites.values.exists(_.denizens.exists {
        case EdificeState(_, EdificeSide.Intact, _) => true
        case _ => false
      })) Some("intact edifice Travel modifiers are not supported")
      else None
    reason match {
      case Some(value) => Left(UnsupportedTravelState(value))
      case None => Right(())
    }
  }

  private def resolveTravel(
      catalog: ExecutableCatalog,
      ready: ReadyFirstGame,
      player: PlayerState,
      source: SiteId,
      destination: SiteId,
      from: Region,
      to: Region,
      baseCost: Int,
      sourceHandlers: Vector[String],
      destinationHandlers: Vector[String]
  ): Either[FirstGameSetupViolation, Int] = {
    val registry = RuntimeRuleRegistry.default
    def handlersWithRole(
        handlers: Vector[String],
        roles: Set[TravelModifierKind]
    ): Vector[String] = handlers.filter(id =>
      registry.lookup(id).flatMap(_.travelModifierKind).exists(roles))
    def activations(
        id: SiteId,
        handlers: Vector[String],
        roles: Set[TravelModifierKind],
        priority: Int
    ) = handlersWithRole(handlers, roles).map(RuleActivation(
      RuleSourceRef.Site(id), _, priority))
    val coastRoute = handlersWithRole(sourceHandlers,
      Set(TravelModifierKind.Coast)).nonEmpty && handlersWithRole(
      destinationHandlers, Set(TravelModifierKind.Coast,
        TravelModifierKind.Island)
    ).nonEmpty
    val passActivations = if (coastRoute || from == to) Vector.empty else
      ready.game.current.map.inPlay.flatMap { id =>
        if (ready.game.current.map.regionOf(id).contains(to) && id != destination)
          catalog.sites.find(_.id == id).toVector.flatMap(definition =>
            activations(id, definition.handlers,
              Set(TravelModifierKind.Pass), 10))
        else Vector.empty
      }
    val active = if (coastRoute)
      activations(source, sourceHandlers, Set(TravelModifierKind.Coast), 0)
    else passActivations ++
      activations(destination, destinationHandlers,
        Set(TravelModifierKind.Island, TravelModifierKind.Mountain), 20)
    val context = RuleQueryContext.Travel(
      ready, player, source, destination, from, to, baseCost)
    registry.resolve(active, context).foldLeft[
      Either[FirstGameSetupViolation, Int]](Right(baseCost)) {
      case (failure @ Left(_), _) => failure
      case (Right(cost), ResolvedRule(_, RuleOutcome.Allow)) => Right(cost)
      case (_, ResolvedRule(_, RuleOutcome.Block(value))) => Left(value)
      case (Right(cost), ResolvedRule(_, RuleOutcome.ModifyCost(value, false))) =>
        Right(cost + value)
      case (_, ResolvedRule(_, RuleOutcome.ModifyCost(value, true))) => Right(value)
      case (_, ResolvedRule(_, RuleOutcome.RequireDecision(decision))) =>
        Left(UnsupportedTravelState(s"decision ${decision.decision.value} required"))
      case (Right(cost), ResolvedRule(_, RuleOutcome.PostActionEffect(_))) =>
        Right(cost)
      case (_, ResolvedRule(_, RuleOutcome.UnsupportedRelevantRule(handler))) =>
        Left(UnsupportedTravelState(s"unsupported active handler $handler"))
    }
  }
}
