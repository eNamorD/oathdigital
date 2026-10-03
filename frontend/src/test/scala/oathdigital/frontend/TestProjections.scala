package oathdigital.frontend

/** Builders for the projected game a render suite draws. */
private[frontend] object TestProjections:
  /** A game at `phase` in which `activePlayer` is to act, with no pawns and
    * not completed. Every list is empty unless the call names it; a test
    * that needs anything more sets it with `copy`.
    */
  def actingProjection(gameId: String = "game", nextSequence: Long = 1L,
      phase: String = "act", activePlayer: Option[String] = Some("red"),
      players: Vector[GamePlayer] = Vector.empty,
      world: Vector[GameRegion] = Vector.empty,
      legalControls: Vector[String] = Vector.empty,
      ready: Boolean = true): GameProjection =
    GameProjection(gameId, nextSequence, phase, activePlayer,
      players = players, world = world, pawnLocations = Vector.empty,
      legalControls = legalControls, ready = ready, completed = false)
