package oathdigital.gameplay.actions

import oathdigital.model._

/** Shared printed banner invariants. Mob and Raid both use the printed
  * deterministic leftmost policy. Wandering Flame site ties belong to the player.
  */
object BannerRules {
  def holder(current: CurrentGameState, banner: Banner): Option[PlayerId] = banner match {
    case Banner.PeoplesFavor => current.banners.peoplesFavor.holder
    case Banner.DarkestSecret => current.banners.darkestSecret.holder
  }
  def resources(current: CurrentGameState, banner: Banner): Int = banner match {
    case Banner.PeoplesFavor => current.banners.peoplesFavor.favor
    case Banner.DarkestSecret => current.banners.darkestSecret.secrets
  }
  def playerResources(player: PlayerState, banner: Banner): Int = banner match {
    case Banner.PeoplesFavor => player.board.favor
    case Banner.DarkestSecret => player.board.faceUpSecrets // CR p.26: facedown do not count.
  }
  def leastFavorBanks(banks: Map[Suit, Int]): Vector[Suit] = {
    val minimum = Suit.all.map(s => banks.getOrElse(s, 0)).min
    Suit.all.filter(s => banks.getOrElse(s, 0) == minimum)
  }
  def addFavor(banks: Map[Suit, Int], order: Vector[Suit]): Map[Suit, Int] =
    order.foldLeft(banks)((b, s) => b.updated(s, b.getOrElse(s, 0) + 1))
  def leastSites(current: CurrentGameState, placements: Vector[SiteId]): Vector[SiteId] = {
    val counts = placements.groupBy(identity).view.mapValues(_.size).toMap
    val totals = current.map.inPlay.map { id =>
      val site = current.map.sites(id)
      id -> (site.tokens.favor + site.tokens.secrets + counts.getOrElse(id, 0))
    }
    val minimum = totals.map(_._2).min
    totals.collect { case (id, n) if n == minimum => id }
  }
  def automaticSitePrefix(current: CurrentGameState, existing: Vector[SiteId],
      remaining: Int): Vector[SiteId] = {
    def loop(placed: Vector[SiteId], left: Int, out: Vector[SiteId]): Vector[SiteId] =
      if (left == 0) out else leastSites(current, placed) match {
        case Vector(one) => loop(placed :+ one, left - 1, out :+ one)
        case _ => out
      }
    loop(existing, remaining, Vector.empty)
  }
  def raidFavorReturn(banks: Map[Suit, Int], amount: Int): Vector[Suit] =
    (0 until amount).foldLeft(Vector.empty[Suit]) { (order, _) =>
      val current = addFavor(banks, order)
      order :+ leastFavorBanks(current).head
    }
}
