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

/** Unified setup/gameplay evolution for the bounded first-turn Wake slice. */
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
      case setupEvent => setup.evolve(state, setupEvent)
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
