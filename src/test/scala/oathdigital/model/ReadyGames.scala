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
  private val seatColors = Vector("red", "blue", "yellow", "purple")

  def of(
      game: OathGame = TestGameFixtures.game,
      favorPerSuit: Int = 5
  ): ReadyGame =
    ReadyGame.start(
      game,
      game.current.players.zipWithIndex.map { case (player, seat) =>
        player.player -> PlayerColor(seatColors.lift(seat).getOrElse(s"seat-$seat"))
      }.toMap,
      firstPlayer = game.current.turn.activePlayer,
      favorBanks = Suit.all.map(_ -> favorPerSuit).toMap
    )
}
