package oathdigital.frontend

/** The game, seat and position a draft was staged at; a draft is dropped when
  * a snapshot arrives for any other. */
private[frontend] final case class BoardSelectionContext(
    gameId: String, playerId: String, sequence: Long)
