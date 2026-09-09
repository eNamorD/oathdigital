package oathdigital.server

import oathdigital.application._
import oathdigital.model.PlayerId
import oathdigital.protocol._
import oathdigital.protocol.projection.GameProjection

sealed trait TrustedSeatFailure extends Product with Serializable
object TrustedSeatFailure {
  case object Forbidden extends TrustedSeatFailure
  case object InvalidIntent extends TrustedSeatFailure
  case object StorageFailure extends TrustedSeatFailure
  final case class Application(error: GameApplicationError) extends TrustedSeatFailure
}

/** TrustedSeat is resolved from a credential before crossing this boundary. */
final class TrustedGameGateway(service: GameApplicationService, projector: GameProjector) {
  import TrustedSeatFailure._

  private def actor(gameId: String, seat: TrustedSeat): Either[TrustedSeatFailure, PlayerId] =
    Either.cond(gameId == seat.gameId, PlayerId(seat.playerId), Forbidden)

  def load(gameId: String, seat: TrustedSeat): Either[TrustedSeatFailure, GameProjection] =
    for {
      player <- actor(gameId, seat)
      loaded <- service.load(gameId).left.map(Application)
      game <- loaded.toRight(Application(GameApplicationError.StreamNotFound(gameId)))
    } yield projector.project(gameId, game, player).copy(viewerPlayerId = Some(player.value))

  def submit(gameId: String, seat: TrustedSeat, request: ActorlessCommandRequest)
      : Either[TrustedSeatFailure, GameProjection] = for {
    player <- actor(gameId, seat)
    command <- GameIntentMapper.bind(player, request.intent, request.orderedModifiers)
      .left.map(_ => InvalidIntent)
    accepted <- service.handle(gameId, request.expectedNextSequence, command).left.map(Application)
  } yield projector.project(gameId, LoadedGame(accepted.state, accepted.nextSequence), player)
    .copy(viewerPlayerId = Some(player.value))

  def preview(gameId: String, seat: TrustedSeat, request: MajorActionPreviewRequest)
      : Either[TrustedSeatFailure, MajorActionPreviewResponse] = for {
    player <- actor(gameId, seat)
    action <- oathdigital.gameplay.MajorActionKind.fromKey(request.action).toRight(InvalidIntent)
    selected <- GameIntentMapper.bindModifiers(player, request.orderedModifiers)
      .left.map(_ => InvalidIntent)
    accepted <- service.preview(gameId, request.expectedNextSequence, player, action, selected)
      .left.map(Application)
    projection = projector.project(gameId, accepted.loaded, player)
    _ <- Either.cond(projection.activeParticipantId.contains(player.value) &&
      projection.actionSelectionOpen, (), Application(GameApplicationError.CommandRejected(
        oathdigital.gameplay.OathViolation.InvalidModifierInvocation(
          "major-action preview is unavailable for this actor or phase"))))
    _ <- MajorActionPreviewTargets.validate(projection, request).left.map(Application)
  } yield MajorActionPreviewResponse(accepted.loaded.nextSequence, request.action,
    accepted.options.map(v => PreviewModifier(v.source.stableKey, v.handlerId, v.handlerId)),
    Vector.empty, MajorActionPreviewTargets.from(projection, request))
}
