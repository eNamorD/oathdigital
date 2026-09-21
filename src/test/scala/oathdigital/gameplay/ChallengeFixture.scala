package oathdigital.gameplay

import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** The board the Challenge suites share: the active player in the Act phase
  * with chosen favor, secrets and Supply, and one banner in a chosen state.
  */
object ChallengeFixture {
  private val setup = new FirstGameSetupRules(catalog)

  def ready(resources: Int, favor: Int = 6, faceup: Int = 6, facedown: Int = 4,
      supply: Int = 7, banner: Banner = Banner.PeoplesFavor,
      holder: Option[PlayerId] = None): (ReadyGame, PlayerState) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val actor0 = base.game.current.players
      .find(_.player == base.game.current.turn.activePlayer).get
    val actor = actor0.copy(board = actor0.board.copy(favor = favor,
      faceUpSecrets = faceup, faceDownSecrets = facedown,
      supply = SupplyTrack(supply)))
    val banners = banner match {
      case Banner.PeoplesFavor => base.game.current.banners.copy(peoplesFavor =
        base.game.current.banners.peoplesFavor.copy(holder = holder,
          favor = resources))
      case Banner.DarkestSecret => base.game.current.banners.copy(darkestSecret =
        base.game.current.banners.darkestSecret.copy(holder = holder,
          secrets = resources))
    }
    val value = base.updateCurrent(_.copy(
      players = base.game.current.players.map(p =>
        if (p.player == actor.player) actor else p),
      banners = banners, turn = base.game.current.turn.copy(phase = Phase.Act)))
    value -> actor
  }

  def active(ready: ReadyGame): PlayerId = ready.game.current.turn.activePlayer

  def enemy(ready: ReadyGame): PlayerState =
    ready.game.current.players.find(_.player != active(ready)).get

  /** Puts the other player at the actor's site and gives them the banner. */
  def enemyHolds(ready: ReadyGame, banner: Banner, resources: Int,
      colocated: Boolean = true): ReadyGame = {
    val actor = ready.game.current.players.find(_.player == active(ready)).get
    val rival = enemy(ready)
    val elsewhere = ready.game.current.map.inPlay.find(id =>
      !actor.pawnSite.contains(id))
    val moved = rival.copy(pawnSite =
      if (colocated) actor.pawnSite else elsewhere)
    val banners = banner match {
      case Banner.PeoplesFavor => ready.game.current.banners.copy(peoplesFavor =
        ready.game.current.banners.peoplesFavor.copy(holder = Some(rival.player),
          favor = resources))
      case Banner.DarkestSecret => ready.game.current.banners.copy(darkestSecret =
        ready.game.current.banners.darkestSecret.copy(holder = Some(rival.player),
          secrets = resources))
    }
    ready.updateCurrent(_.copy(banners = banners, players =
      ready.game.current.players.map(p => if (p.player == rival.player) moved else p)))
  }

  /** Every in-play site holds `others` secrets except the named ones. */
  def withSiteSecrets(ready: ReadyGame, secrets: Map[SiteId, Int],
      others: Int = 10): ReadyGame = ready.updateCurrent(current =>
    current.copy(map = current.map.copy(sites = current.map.sites.map {
      case (id, site) => id -> (if (!current.map.inPlay.contains(id)) site
        else site.copy(tokens = Tokens(0, secrets.getOrElse(id, others))))
    })))
}
