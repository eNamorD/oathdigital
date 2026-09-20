package oathdigital.gameplay

import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerDice, WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** The boards the Campaign suites share. The actor stands at `origin`, ruled
  * by two Bandits, in the Act phase with Supply and warbands; `extras` further
  * sites are also Bandit-ruled and every other site is empty and unruled; no
  * site holds a denizen. The other player stands elsewhere.
  */
object CampaignFixture {
  final case class Board(ready: ReadyGame, actor: PlayerId, other: PlayerId,
      origin: SiteId) {
    def player(id: PlayerId): PlayerState =
      ready.game.current.players.find(_.player == id).get
    def extras: Vector[SiteId] = ready.game.current.map.inPlay.filter(site =>
      site != origin && ready.game.current.map.sites(site).forces ==
        SiteForces.Occupied(ForceKind.Bandit, 2))
  }

  private val setup = new FirstGameSetupRules(catalog)

  def board(extras: Int = 0, warbands: Int = 5, supply: Int = 7): Board = {
    val Ready(base) = execute(setup)._1: @unchecked
    val current = base.game.current
    val inPlay = current.map.inPlay
    val origin = inPlay.find(id => catalog.sites.find(_.id == id).exists(
      _.handlers.forall(h => !h.endsWith(".mountain") && !h.endsWith(".plains")))).get
    val ruled = (origin +: inPlay.filter(_ != origin).take(extras)).toSet
    val elsewhere = inPlay.find(!ruled(_)).getOrElse(inPlay.find(_ != origin).get)
    val activeId = current.turn.activePlayer
    val otherId = current.players.map(_.player).find(_ != activeId).get
    val players = current.players.map { player =>
      if (player.player == activeId) player.copy(pawnSite = Some(origin),
        board = player.board.copy(warbands = warbands,
          supply = SupplyTrack(supply)))
      else player.copy(pawnSite = Some(elsewhere))
    }
    val sites = current.map.sites.map { case (id, site) =>
      id -> site.copy(denizens = Vector.empty, forces =
        if (ruled(id)) SiteForces.Occupied(ForceKind.Bandit, 2)
        else SiteForces.Empty)
    }
    val ready = base.updateCurrent(_.copy(players = players, pending = None,
      map = current.map.copy(sites = sites),
      turn = current.turn.copy(phase = Phase.Act)))
    Board(ready, activeId, otherId, origin)
  }

  /** The other player joins the actor at `origin`, so a Raid is legal. */
  def withEnemyAtOrigin(b: Board): Board = b.copy(ready = b.ready.updateCurrent(
    current => current.copy(players = current.players.map(p =>
      if (p.player == b.other) p.copy(pawnSite = Some(b.origin)) else p))))

  def rules(dice: WalkerDice = WalkerDice.unavailable,
      powers: Boolean = false): OathRules = new OathRules(catalog,
    walkerPowerCatalog =
      if (powers) WalkerPowerCatalog.default(catalog) else WalkerPowers.empty,
    walkerDice = dice)

  /** Dice that return exactly these faces, and fail loudly on a wrong count. */
  def dice(attack: Vector[AttackDieFace] = Vector.empty,
      defense: Vector[DefenseDieFace] = Vector.empty): WalkerDice =
    (kind, count) => kind match {
      case DiceKind.Attack => Either.cond(attack.size == count, attack,
        OathViolation.InvalidEventOrder(
          s"test dice: ${attack.size} attack faces for a pool of $count"))
      case DiceKind.Defense => Either.cond(defense.size == count, defense,
        OathViolation.InvalidEventOrder(
          s"test dice: ${defense.size} defense faces for a pool of $count"))
    }
}
