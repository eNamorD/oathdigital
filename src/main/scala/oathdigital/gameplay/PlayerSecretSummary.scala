package oathdigital.gameplay

import oathdigital.model._

final case class PlayerSecretSummary(available: Int, facedown: Int, committed: Int) {
  require(available >= 0 && facedown >= 0 && committed >= 0,
    "secret summary counts must be non-negative")
  def totalSecrets: Int = available + facedown + committed
}

object PlayerSecretSummary {
  def derive(ready: ReadyGame, playerId: PlayerId): Either[String, PlayerSecretSummary] = {
    PlayerResourceSources.player(ready, playerId).map { player =>
      val committed = if (ready.game.current.turn.activePlayer == playerId)
        InPlayCardResources.discover(ready).secrets else 0
      PlayerSecretSummary(player.board.faceUpSecrets,
        player.board.faceDownSecrets, committed)
    }
  }
}
