package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.powers.action.{PaidAction, PawnMoves}
import oathdigital.model._

/** Wandering Flame, move (the Banner of the Darkest Secret, Wandering Flame
  * face), ACTION: place your pawn at any other site with a secret on the site
  * itself. It costs nothing and has no once-per-turn limit.
  *
  * The engine finds the banner as the source, and only for its holder while
  * the Wandering Flame face is up (`RuleSourceIndex` lists the power only
  * then), so nothing here checks either. The pawn goes by a plain `Move`, not
  * Travel, so no Travel window runs. A secret on a card at a site does not
  * count. The decision is a live `Branch`, asked only when more than one site
  * qualifies. Nothing runs before it, so the resume derives the same choice.
  * The power is usable only when a site qualifies.
  */
case object WanderingFlameMove extends PaidAction(
    "banner.darkest-secret.wandering-flame.move", Cost.free) {
  val decisionId: String = "power.wandering-flame.site"

  override def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = destinations(ready, player).nonEmpty

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((state, _) => ask(state, player)),
    BuildOps((state, pending) => move(state, player, pending)))))

  /** The other sites, in map order, with a secret on the site itself. */
  private def destinations(ready: ReadyGame, player: PlayerId): Vector[SiteId] =
    PawnMoves.pawnSite(ready, player).toOption.toVector.flatMap(here =>
      PawnMoves.sitesOtherThan(ready, here).filter(site =>
        ready.game.current.map.sites.get(site).exists(_.tokens.secrets > 0)))

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] =
    destinations(ready, player) match {
      case sites if sites.size > 1 => Vector(PawnMoves.siteChoice(decisionId,
        player, sites, "Wandering Flame: choose the site to move your pawn to"))
      case _ => Vector.empty
    }

  private def move(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    destinations(ready, player) match {
      case Vector() => Right(Vector.empty)
      case Vector(only) => PawnMoves.relocate(ready, player, only)
      case sites => PawnMoves.chosenSite(pending, decisionId).flatMap(site =>
        if (sites.contains(site)) PawnMoves.relocate(ready, player, site)
        else Left(OathViolation.InvalidEventOrder(
          s"${site.value} holds no secret of its own")))
    }
}
