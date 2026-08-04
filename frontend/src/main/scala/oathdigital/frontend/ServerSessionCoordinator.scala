package oathdigital.frontend

final case class ServerRequestIdentity(
    generation: Long,
    gameId: String,
    playerId: String
)

sealed trait ProjectionRoute
object ProjectionRoute {
  final case class Display(
      projection: FirstGameProjection,
      notice: Option[FirstGameClientFailure]
  ) extends ProjectionRoute
  final case class ReloadForActivePlayer(
      projection: FirstGameProjection,
      request: ServerRequestIdentity,
      notice: Option[FirstGameClientFailure]
  ) extends ProjectionRoute
}

sealed trait ServerConnectionState
object ServerConnectionState {
  case object Connecting extends ServerConnectionState
  case object Connected extends ServerConnectionState
  final case class Disconnected(failure: FirstGameClientFailure)
      extends ServerConnectionState
}

/** Invalidates every outstanding callback whenever the game or view changes. */
final class ServerSessionCoordinator(initialGameId: String, initialPlayer: String) {
  private var generation = 0L
  private var gameId = initialGameId
  private var playerId = initialPlayer
  private var connection: ServerConnectionState =
    ServerConnectionState.Connecting

  def switchSession(game: String, player: String): ServerRequestIdentity = {
    generation += 1
    gameId = game
    playerId = player
    connection = ServerConnectionState.Connecting
    capture
  }

  def reconnect(): ServerRequestIdentity = switchSession(gameId, playerId)

  def capture: ServerRequestIdentity =
    ServerRequestIdentity(generation, gameId, playerId)

  def accepts(request: ServerRequestIdentity): Boolean = request == capture

  def connectionState: ServerConnectionState = connection

  def snapshotAdvances(
      request: ServerRequestIdentity,
      displayedNextSequence: Long,
      incomingNextSequence: Long
  ): Boolean =
    accepts(request) && incomingNextSequence > displayedNextSequence

  def recordSnapshotSuccess(request: ServerRequestIdentity): Boolean =
    if (!accepts(request)) false
    else {
      connection = ServerConnectionState.Connected
      true
    }

  def recordFailure(
      request: ServerRequestIdentity,
      failure: FirstGameClientFailure
  ): Boolean =
    if (!accepts(request)) false
    else {
      if (FirstGameClientFailure.isTransient(failure))
        connection = ServerConnectionState.Disconnected(failure)
      else connection = ServerConnectionState.Connected
      true
    }

  def route(
      request: ServerRequestIdentity,
      projection: FirstGameProjection,
      notice: Option[FirstGameClientFailure]
  ): Option[ProjectionRoute] =
    if (!accepts(request)) None
    else {
      connection = ServerConnectionState.Connected
      projection.activeParticipantId match {
      case Some(active) if active != playerId && !projection.ready =>
        Some(ProjectionRoute.ReloadForActivePlayer(
          projection,
          switchSession(gameId, active),
          notice
        ))
      case _ => Some(ProjectionRoute.Display(projection, notice))
      }
    }
}
