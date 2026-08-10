package oathdigital.gameplay.phases

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.FirstGameContinue._
import oathdigital.setup.FirstGameSetupEvent._
import oathdigital.setup.FirstGameSetupState._
import oathdigital.setup.FirstGameSetupViolation._

import oathdigital.gameplay.{GameStateUpdates, OathLifecycle}
import oathdigital.gameplay.TakeWealthRules
import GameStateUpdates.updateCurrent

sealed trait WakeCommand extends Product with Serializable
object WakeCommand {
  final case class TakeWealth(playerId: PlayerId, resource: WakeResource)
      extends WakeCommand
  final case class EndWake(playerId: PlayerId) extends WakeCommand
}


object Wake {
  def handle(
      catalog: ExecutableCatalog,
      state: FirstGameSetupState,
      command: WakeCommand
  ): Either[FirstGameSetupViolation, FirstGameTransition] =
    command match {
      case WakeCommand.TakeWealth(playerId, resource) =>
        OathLifecycle.validateReady(state, playerId).flatMap { ready =>
          val player = ready.game.current.players.find(_.player == playerId).get
          player.pawnSite.toRight(PawnSiteMissing(playerId)).flatMap { siteId =>
            TakeWealthRules.validate(ready, player, siteId, resource).flatMap {
              _ => transition(
                catalog,
                state,
                  Vector(WealthTaken(playerId, siteId, resource)),
                  AwaitingWakeAction(playerId)
                )
            }
          }
        }
      case WakeCommand.EndWake(playerId) =>
        OathLifecycle.validateReady(state, playerId).flatMap(_ =>
          transition(
                catalog,
                state,
            Vector(WakeEnded(playerId)),
            ActActionSelection(playerId)
          ))
    }
  def evolve(
      catalog: ExecutableCatalog,
      state: FirstGameSetupState,
      event: FirstGameSetupEvent
  ): Either[FirstGameSetupViolation, FirstGameSetupState] = event match {
    case event: WealthTaken => evolveWealth(state, event)
    case WakeEnded(playerId) =>
      OathLifecycle.validateReady(state, playerId).map { ready =>
        Ready(updateCurrent(ready)(current =>
          current.copy(turn = current.turn.copy(phase = Phase.Act))))
      }
    case _ => Left(InvalidEventOrder("Wake received a non-Wake event"))
  }

  private def evolveWealth(
      state: FirstGameSetupState,
      event: WealthTaken
  ): Either[FirstGameSetupViolation, FirstGameSetupState] =
    OathLifecycle.validateReady(state, event.playerId).flatMap { ready =>
      val player = ready.game.current.players.find(
        _.player == event.playerId).get
      if (!player.pawnSite.contains(event.siteId))
        Left(InvalidEventOrder("Take Wealth site must be the current pawn site"))
      else {
        val power = Wake.takeWealthPower(event.siteId)
        ready.game.current.map.sites.get(event.siteId)
          .toRight(SiteNotInPlay(event.siteId)).flatMap { site =>
            TakeWealthRules.validate(ready, player, event.siteId, event.resource)
              .map(_ => Ready(updateCurrent(ready) { current =>
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

  private def transition(
      catalog: ExecutableCatalog,
      state: FirstGameSetupState,
      events: Vector[FirstGameSetupEvent],
      continue: FirstGameContinue
  ): Either[FirstGameSetupViolation, FirstGameTransition] =
    events.foldLeft[Either[FirstGameSetupViolation, FirstGameSetupState]](Right(state))(
      (next, event) => next.flatMap(evolve(catalog, _, event)))
      .map(FirstGameTransition(_, events, continue))

  def takeWealthPower(siteId: SiteId): PowerUseRef =
    PowerUseRef(PowerTiming.Wake, PowerSourceRef.Site(siteId), PowerId("take-wealth"))
}
