package oathdigital.gameplay.powers

import oathdigital.model._

/** Reads about the acting player that several powers need. */
object PlayerFacts {
  def player(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, PlayerState] =
    ready.game.current.players.find(_.player == actor).toRight(
      OathViolation.WrongPlayer(ready.game.current.turn.activePlayer, actor))

  /** The kind of warband the player's own warbands are. */
  def forceKind(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, ForceKind] =
    player(ready, actor).flatMap(state => PlayerForceKind.of(ready, state)
      .toRight(OathViolation.UnsupportedEconomyState(
        s"no warband kind for lineage ${state.lineage.value}")))
}
