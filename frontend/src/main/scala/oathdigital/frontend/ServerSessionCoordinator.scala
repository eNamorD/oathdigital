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

/** Invalidates every outstanding callback whenever the game or view changes. */
final class ServerSessionCoordinator(initialGameId: String, initialPlayer: String) {
  private var generation = 0L
  private var gameId = initialGameId
  private var playerId = initialPlayer

  def switchSession(game: String, player: String): ServerRequestIdentity = {
    generation += 1
    gameId = game
    playerId = player
    capture
  }

  def capture: ServerRequestIdentity =
    ServerRequestIdentity(generation, gameId, playerId)

  def accepts(request: ServerRequestIdentity): Boolean = request == capture

  def route(
      request: ServerRequestIdentity,
      projection: FirstGameProjection,
      notice: Option[FirstGameClientFailure]
  ): Option[ProjectionRoute] =
    if (!accepts(request)) None
    else projection.activeParticipantId match {
      case Some(active) if active != playerId && !projection.ready =>
        Some(ProjectionRoute.ReloadForActivePlayer(
          projection,
          switchSession(gameId, active),
          notice
        ))
      case _ => Some(ProjectionRoute.Display(projection, notice))
    }
}
