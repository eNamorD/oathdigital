package oathdigital.gameplay.phases

import oathdigital.gameplay.{GameplayTransition, GameStateUpdates, OathLifecycle}
import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.gameplay.OathContinue._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._
import oathdigital.gameplay.operations.{Location, OperationPipeline,
  OperationPolicy, Piece, Take => CoreTake}

import GameStateUpdates.updateCurrent

sealed trait WakeCommand extends Product with Serializable
object WakeCommand {
  final case class TakeWealth(playerId: PlayerId, resource: WakeResource)
      extends WakeCommand
  final case class EndWake(playerId: PlayerId) extends WakeCommand
}

object Wake {
  private val operationAllowlist: OperationPolicy =
    WakeOperationPolicy

  def handle(
      state: OathState,
      command: WakeCommand
  ): Either[OathViolation, OathTransition] =
    command match {
      case WakeCommand.TakeWealth(playerId, resource) =>
        OathLifecycle.validateReady(state, playerId).flatMap { ready =>
          val player = ready.game.current.players.find(_.player == playerId).get
          player.pawnSite.toRight(PawnSiteMissing(playerId)).flatMap { siteId =>
            TakeWealthRules.validate(ready, player, siteId, resource).flatMap { _ =>
              transition(
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
            state,
            Vector(WakeEnded(playerId)),
            ActActionSelection(playerId)
          ))
    }

  def evolve(
      state: OathState,
      event: OathEvent
  ): Either[OathViolation, OathState] = event match {
    case event: WealthTaken => evolveWealth(state, event)
    case WakeEnded(playerId) =>
      OathLifecycle.validateReady(state, playerId).map { ready =>
        Ready(
          updateCurrent(ready)(current =>
            current.copy(turn = current.turn.copy(phase = Phase.Act)))
        )
      }
    case _ => Left(InvalidEventOrder("Wake received a non-Wake event"))
  }

  private def evolveWealth(
      state: OathState,
      event: WealthTaken
  ): Either[OathViolation, OathState] =
    OathLifecycle.validateReady(state, event.playerId).flatMap { ready =>
      val player = ready.game.current.players.find(
        _.player == event.playerId).get
      if (!player.pawnSite.contains(event.siteId))
        Left(InvalidEventOrder("Take Wealth site must be the current pawn site"))
      else {
        val power = Wake.takeWealthPower(event.siteId)
        for {
          _ <- TakeWealthRules.validate(
            ready, player, event.siteId, event.resource)
          piece = event.resource match {
            case WakeResource.Favor => Piece.Favor(1)
            case WakeResource.Secret => Piece.Secrets(1)
          }
          execution <- OperationPipeline.run(
            ready,
            Vector(CoreTake(
              piece,
              event.playerId,
              Location.Site(event.siteId),
              Location.PlayArea(event.playerId)
            )),
            operationAllowlist
          ) { moved =>
            Right(updateCurrent(moved)(current => current.copy(
              turn = current.turn.copy(
                usedPowers = current.turn.usedPowers + power))))
          }
        } yield Ready(execution)
      }
    }

  private def transition(
      state: OathState,
      events: Vector[OathEvent],
      continue: OathContinue
  ): Either[OathViolation, OathTransition] =
    GameplayTransition(state, events, continue)(evolve)

  def takeWealthPower(siteId: SiteId): PowerUseRef =
    PowerUseRef(
      PowerTiming.Wake,
      PowerSourceRef.Site(siteId),
      PowerId("site.take-wealth")
    )
}
