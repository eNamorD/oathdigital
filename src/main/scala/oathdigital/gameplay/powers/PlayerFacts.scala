package oathdigital.gameplay.powers

import oathdigital.model._

/** Reads about the acting player that several powers need. */
object PlayerFacts:
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

  /** The warbands of `kind` left in their bank: the printed supply less
    * those on boards and at sites. */
  def banked(ready: ReadyGame, kind: ForceKind): Int =
    val current = ready.game.current
    val onBoards = current.players
      .filter(state => PlayerForceKind.of(ready, state).contains(kind))
      .map(_.board.warbands).sum
    val atSites = current.map.sites.values.map(_.forces).collect {
      case SiteForces.Occupied(`kind`, count) => count }.sum
    math.max(0,
      ready.banks.warbandSupply.getOrElse(kind, 0) - onBoards - atSites)
