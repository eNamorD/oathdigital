package oathdigital.gameplay

import oathdigital.model._

final case class PlayerSecretSummary(available: Int, facedown: Int, committed: Int) {
  require(available >= 0 && facedown >= 0 && committed >= 0,
    "secret summary counts must be non-negative")
  def totalSecrets: Int = available + facedown + committed
}

object PlayerSecretSummary {
  def derive(ready: ReadyGame, playerId: PlayerId): Either[String, PlayerSecretSummary] = {
    val active = ready.game.current.turn.activePlayer == playerId
    if (active) PlayerResourceSources.discover(ready, playerId).map { sources =>
      val committed = sources.adviserDenizens.map(_.tokens.secrets).sum +
        sources.heldRelics.map(_.tokens.secrets).sum +
        sources.siteCards.map(_.tokens.secrets).sum
      PlayerSecretSummary(sources.player.board.faceUpSecrets,
        sources.player.board.faceDownSecrets, committed)
    } else PlayerResourceSources.player(ready, playerId).flatMap { player =>
      val adviserSecrets = player.advisers.collect {
        case value: DenizenState => value.tokens.secrets
      }.sum
      val relicSecrets = player.relics.map(_.tokens.secrets).sum
      Either.cond(adviserSecrets == 0 && relicSecrets == 0,
        PlayerSecretSummary(player.board.faceUpSecrets,
          player.board.faceDownSecrets, committed = 0),
        s"inactive player ${playerId.value} has ambiguous committed secrets " +
          s"on owned cards (advisers=$adviserSecrets, relics=$relicSecrets)")
    }
  }
}
