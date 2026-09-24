package oathdigital.model

/** Builds the `ReadyGame` around a hand-assembled `OathGame`.
  *
  * Suites that test one rule against a small board start from
  * `TestGameFixtures.game` (or a copy of it) and need only the table
  * bookkeeping filled in. `of` gives seats their colors in player order, makes
  * the active player the first player, stocks every suit bank equally, and
  * uses the printed warband supply. Override a field with `copy` when a test
  * is about that field.
  */
object ReadyGames {
  import PlayerColor._
  private val seatColors =
    Vector(Red, Blue, Yellow, Purple, White, Black, Pink, Brown)

  def of(
      game: OathGame = TestGameFixtures.game,
      favorPerSuit: Int = 5
  ): ReadyGame =
    ReadyGame.start(
      game,
      game.current.players.zipWithIndex.map { case (player, seat) =>
        player.player -> seatColors(seat)
      }.toMap,
      firstPlayer = game.current.turn.activePlayer,
      favorBanks = Suit.all.map(_ -> favorPerSuit).toMap
    )
}
