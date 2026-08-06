package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.EventEvolution
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.FirstGameContinue._
import oathdigital.setup.FirstGameSetupEvent._
import oathdigital.setup.FirstGameSetupState._
import oathdigital.setup.FirstGameSetupViolation._

sealed trait WakeCommand extends Product with Serializable
object WakeCommand {
  final case class TakeWealth(playerId: PlayerId, resource: WakeResource)
      extends WakeCommand
  final case class EndWake(playerId: PlayerId) extends WakeCommand
}

sealed trait TravelCommand extends Product with Serializable
object TravelCommand {
  final case class Travel(playerId: PlayerId, destinationSiteId: SiteId)
      extends TravelCommand
}

/** Unified setup/gameplay evolution for bounded first-turn Wake and Travel. */
final class FirstGameRules(catalog: ExecutableCatalog)
    extends EventEvolution[
      FirstGameSetupState,
      FirstGameSetupEvent,
      FirstGameSetupViolation
    ] {
  private val setup = new FirstGameSetupRules(catalog)

  override val initialState: FirstGameSetupState = setup.initialState

  def handle(
      state: FirstGameSetupState,
      command: WakeCommand
  ): Either[FirstGameSetupViolation, FirstGameTransition] =
    command match {
      case WakeCommand.TakeWealth(playerId, resource) =>
        validateReady(state, playerId).flatMap { ready =>
          val player = ready.game.current.players.find(_.player == playerId).get
          player.pawnSite.toRight(PawnSiteMissing(playerId)).flatMap { siteId =>
            val power = takeWealthPower(siteId)
            val enemies = ready.game.current.players.collect {
              case other if other.player != playerId &&
                    other.pawnSite.contains(siteId) => other.player
            }
            ready.game.current.map.sites.get(siteId)
              .toRight(SiteNotInPlay(siteId)).flatMap { site =>
                if (ready.game.current.turn.usedPowers.contains(power))
                  Left(PowerAlreadyUsed(power))
                else if (enemies.nonEmpty)
                  Left(EnemyPawnBlocksTakeWealth(siteId, enemies))
                else if (!available(site.tokens, resource))
                  Left(ResourceUnavailable(siteId, resource))
                else transition(
                  state,
                  Vector(WealthTaken(playerId, siteId, resource)),
                  AwaitingWakeAction(playerId)
                )
              }
          }
        }
      case WakeCommand.EndWake(playerId) =>
        validateReady(state, playerId).flatMap(_ =>
          transition(
            state,
            Vector(WakeEnded(playerId)),
            ActActionSelection(playerId)
          ))
    }

  def handle(
      state: FirstGameSetupState,
      command: TravelCommand
  ): Either[FirstGameSetupViolation, FirstGameTransition] = command match {
    case TravelCommand.Travel(playerId, destination) =>
      validateAct(state, playerId).flatMap { ready =>
        val player = ready.game.current.players.find(_.player == playerId).get
        player.pawnSite.toRight(PawnSiteMissing(playerId)).flatMap { source =>
          TravelRules.cost(catalog, ready, player, source, destination)
            .flatMap { cost =>
              if (player.board.supply.supply < cost)
                Left(InsufficientSupply(cost, player.board.supply.supply))
              else transition(
                state,
                Vector(Traveled(playerId, source, destination, cost)),
                ActActionSelection(playerId)
              )
            }
        }
      }
  }

  override def evolve(
      state: FirstGameSetupState,
      event: FirstGameSetupEvent
  ): Either[FirstGameSetupViolation, FirstGameSetupState] =
    event match {
      case event: WealthTaken => evolveWealth(state, event)
      case WakeEnded(playerId) =>
        validateReady(state, playerId).map { ready =>
          Ready(updateCurrent(ready)(current =>
            current.copy(turn = current.turn.copy(phase = Phase.Act))))
        }
      case event: Traveled => evolveTravel(state, event)
      case setupEvent => setup.evolve(state, setupEvent)
    }

  private def evolveTravel(
      state: FirstGameSetupState,
      event: Traveled
  ): Either[FirstGameSetupViolation, FirstGameSetupState] =
    validateAct(state, event.playerId).flatMap { ready =>
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

  private def evolveWealth(
      state: FirstGameSetupState,
      event: WealthTaken
  ): Either[FirstGameSetupViolation, FirstGameSetupState] =
    validateReady(state, event.playerId).flatMap { ready =>
      val player = ready.game.current.players.find(
        _.player == event.playerId).get
      if (!player.pawnSite.contains(event.siteId))
        Left(InvalidEventOrder("Take Wealth site must be the current pawn site"))
      else {
        val power = takeWealthPower(event.siteId)
        val enemies = ready.game.current.players.collect {
          case other if other.player != event.playerId &&
                other.pawnSite.contains(event.siteId) => other.player
        }
        ready.game.current.map.sites.get(event.siteId)
          .toRight(SiteNotInPlay(event.siteId)).flatMap { site =>
            if (ready.game.current.turn.usedPowers.contains(power))
              Left(PowerAlreadyUsed(power))
            else if (enemies.nonEmpty)
              Left(EnemyPawnBlocksTakeWealth(event.siteId, enemies))
            else if (!available(site.tokens, event.resource))
              Left(ResourceUnavailable(event.siteId, event.resource))
            else Right(Ready(updateCurrent(ready) { current =>
              val players = current.players.map { existing =>
                if (existing.player != event.playerId) existing
                else existing.copy(board = event.resource match {
                  case WakeResource.Favor =>
                    existing.board.copy(favor = existing.board.favor + 1)
                  case WakeResource.Secret =>
                    existing.board.copy(
                      faceUpSecrets = existing.board.faceUpSecrets + 1)
                })
              }
              val sites = current.map.sites.updated(
                event.siteId,
                site.copy(tokens = event.resource match {
                  case WakeResource.Favor =>
                    site.tokens.copy(favor = site.tokens.favor - 1)
                  case WakeResource.Secret =>
                    site.tokens.copy(secrets = site.tokens.secrets - 1)
                })
              )
              current.copy(
                players = players,
                map = current.map.copy(sites = sites),
                turn = current.turn.copy(
                  usedPowers = current.turn.usedPowers + power
                )
              )
            }))
          }
      }
    }

  private def validateReady(
      state: FirstGameSetupState,
      playerId: PlayerId
  ): Either[FirstGameSetupViolation, ReadyFirstGame] =
    state match {
      case NoGame | _: InProgress => Left(GameNotStarted)
      case Ready(ready) =>
        val current = ready.game.current
        if (current.result.nonEmpty) Left(GameEnded)
        else if (current.turn.activePlayer != playerId)
          Left(WrongPlayer(current.turn.activePlayer, playerId))
        else if (current.turn.phase != Phase.Wake)
          Left(WrongPhase(Phase.Wake, current.turn.phase))
        else if (current.title.holder.nonEmpty)
          Left(UnsupportedWakeVictoryState("Oathkeeper or Usurper is held"))
        else if (current.players.exists(_.revealedVision.nonEmpty))
          Left(UnsupportedWakeVictoryState("a Vision is revealed"))
        else Right(ready)
    }

  private def validateAct(
      state: FirstGameSetupState,
      playerId: PlayerId
  ): Either[FirstGameSetupViolation, ReadyFirstGame] = state match {
    case NoGame | _: InProgress => Left(GameNotStarted)
    case Ready(ready) =>
      val current = ready.game.current
      if (current.result.nonEmpty) Left(GameEnded)
      else if (current.turn.activePlayer != playerId)
        Left(WrongPlayer(current.turn.activePlayer, playerId))
      else if (current.turn.phase != Phase.Act)
        Left(WrongPhase(Phase.Act, current.turn.phase))
      else current.pending match {
        case Some(value) => Left(PendingProcedureBlocksAction(value.decision))
        case None => TravelRules.validateSupportedState(ready).map(_ => ready)
      }
  }

  private def transition(
      state: FirstGameSetupState,
      events: Vector[FirstGameSetupEvent],
      continue: FirstGameContinue
  ): Either[FirstGameSetupViolation, FirstGameTransition] =
    events.foldLeft[Either[FirstGameSetupViolation, FirstGameSetupState]](
      Right(state))((next, event) => next.flatMap(evolve(_, event)))
      .map(FirstGameTransition(_, events, continue))

  private def updateCurrent(
      ready: ReadyFirstGame
  )(f: CurrentGameState => CurrentGameState): ReadyFirstGame =
    ready.copy(game = ready.game.copy(current = f(ready.game.current)))

  private def available(tokens: Tokens, resource: WakeResource): Boolean =
    resource match {
      case WakeResource.Favor => tokens.favor > 0
      case WakeResource.Secret => tokens.secrets > 0
    }

  def takeWealthPower(siteId: SiteId): PowerUseRef =
    PowerUseRef(
      PowerTiming.Wake,
      PowerSourceRef.Site(siteId),
      PowerId("take-wealth")
    )
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
      coastOverride = has(sourceDefinition, "coast") &&
        (has(destinationDefinition, "coast") ||
          has(destinationDefinition, "island"))
      _ <- if (coastOverride) Right(())
        else validatePasses(catalog, ready, player, from, to, destination)
    } yield if (coastOverride) 1 else {
      val base = (from, to) match {
        case (Region.Cradle, Region.Cradle) => 1
        case (Region.Cradle, Region.Provinces) => 2
        case (Region.Cradle, Region.Hinterland) => 4
        case (Region.Provinces, _) => 2
        case (Region.Hinterland, Region.Cradle) => 4
        case (Region.Hinterland, Region.Provinces) => 2
        case (Region.Hinterland, Region.Hinterland) => 3
      }
      base + (if (has(destinationDefinition, "island")) 2 else 0) +
        (if (has(destinationDefinition, "mountain")) 1 else 0)
    }
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

  private def validatePasses(
      catalog: ExecutableCatalog,
      ready: ReadyFirstGame,
      player: PlayerState,
      from: Region,
      to: Region,
      destination: SiteId
  ): Either[FirstGameSetupViolation, Unit] =
    if (from == to) Right(()) else {
      val map = ready.game.current.map
      val passes = map.inPlay.filter(id =>
        map.regionOf(id).contains(to) &&
          catalog.sites.find(_.id == id).exists(has(_, "pass")) &&
          id != destination)
      passes.foldLeft[Either[FirstGameSetupViolation, Unit]](Right(())) {
        case (failure @ Left(_), _) => failure
        case (Right(_), pass) => map.sites(pass).forces match {
          case SiteForces.Occupied(ForceKind.Exile(lineage), _)
              if lineage == player.lineage => Right(())
          case SiteForces.Occupied(ForceKind.Exile(lineage), _) =>
            ready.game.current.players.find(_.lineage == lineage) match {
              case Some(ruler) => Left(TravelConsentUnsupported(
                pass, ruler.player))
              case None => Left(TravelPassBlocked(pass, destination))
            }
          case _ => Left(TravelPassBlocked(pass, destination))
        }
      }
    }

  private def has(
      definition: oathdigital.catalog.SiteDefinition,
      power: String
  ): Boolean = definition.handlers.exists(_.endsWith(s".$power"))
}
