package oathdigital.server

import oathdigital.application._
import oathdigital.model.PlayerId
import oathdigital.protocol._
import oathdigital.protocol.projection.{GameProjection, LogPage}

sealed trait TrustedSeatFailure extends Product with Serializable
object TrustedSeatFailure:
  case object Forbidden extends TrustedSeatFailure
  case object InvalidIntent extends TrustedSeatFailure
  case object StorageFailure extends TrustedSeatFailure
  final case class Application(error: GameApplicationError) extends TrustedSeatFailure

/** TrustedSeat is resolved from a credential before crossing this boundary. */
final class TrustedGameGateway(service: GameApplicationService, projector: GameProjector):
  import TrustedSeatFailure._

  private def actor(gameId: String, seat: TrustedSeat): Either[TrustedSeatFailure, PlayerId] =
    Either.cond(gameId == seat.gameId, PlayerId(seat.playerId), Forbidden)

  def load(gameId: String, seat: TrustedSeat): Either[TrustedSeatFailure, GameProjection] =
    for
      player <- actor(gameId, seat)
      loaded <- service.load(gameId).left.map(Application.apply)
      game <- loaded.toRight(Application(GameApplicationError.StreamNotFound(gameId)))
    yield projector.project(gameId, game, player).copy(viewerPlayerId = Some(player.value))

  /** The seat's log page. A cursor past the journal's end is an invalid
    * request, which the route reports as malformed. */
  def log(gameId: String, seat: TrustedSeat, after: Long)
      : Either[TrustedSeatFailure, LogPage] = for
    player <- actor(gameId, seat)
    loaded <- service.history(gameId).left.map(Application.apply)
    history <- loaded.toRight(Application(GameApplicationError.StreamNotFound(gameId)))
    page <- projector.logPage(gameId, history, after, player).toRight(InvalidIntent)
  yield page

  def submit(gameId: String, seat: TrustedSeat, request: ActorlessCommandRequest)
      : Either[TrustedSeatFailure, GameProjection] = for
    player <- actor(gameId, seat)
    command <- GameIntentMapper.bind(player, request.intent)
      .left.map(_ => InvalidIntent)
    accepted <- service.handle(gameId, request.expectedNextSequence, command).left.map(Application.apply)
  yield projector.project(gameId, accepted.loaded, player)
    .copy(viewerPlayerId = Some(player.value))
