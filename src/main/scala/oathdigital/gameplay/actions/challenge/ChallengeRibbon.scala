package oathdigital.gameplay.actions.challenge

import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

/** What a Challenge does with the challenged banner's resources before the
  * challenger pays, per banner (rule W: every state-derived effect is a
  * `BuildOps` or a lazily selected `Branch`, so a resumed walk never rebuilds
  * it from a board an earlier step has changed).
  *
  * People's Favor returns every favor to the least-favor banks, leftmost
  * first. Wandering Flame first returns the previous holder's retained half,
  * then places the rest on the least-stocked sites: each pass places one
  * secret on every tied site while it can, and when fewer secrets remain than
  * tied sites the player picks which of them, once.
  */
private[challenge] object ChallengeRibbon {
  val siteDecisionId = "challenge.ribbon-site"

  private val secretBanner = PositionedLocation(
    Location.OnBanner(Banner.DarkestSecret))

  def steps(actor: PlayerId, banner: Banner): Vector[Operation] = banner match {
    case Banner.PeoplesFavor =>
      Vector(BuildOps((ready, _) => Right(favorReturns(ready))))
    case Banner.DarkestSecret => Vector(
      BuildOps((ready, _) => Right(retainedHalf(ready))),
      Repeat((ready, _) => BannerRules.resources(ready.game.current,
        Banner.DarkestSecret) > 0,
        Branch((ready, _) => pass(ready, actor))))
  }

  private def favorReturns(ready: ReadyGame): Vector[CoreOperation] = {
    val amount = BannerRules.resources(ready.game.current, Banner.PeoplesFavor)
    BannerRules.raidFavorReturn(ready.banks.favor, amount).map(suit =>
      Move(Piece.Favor(1),
        PositionedLocation(Location.OnBanner(Banner.PeoplesFavor)),
        PositionedLocation(Location.FavorBank(suit))))
  }

  /** The previous holder keeps `P - P / 2` of the `P` secrets; an unclaimed
    * banner keeps none. Afterwards every secret left on the banner is one to
    * place, so the count still to place is always the banner's own total.
    */
  private def retainedHalf(ready: ReadyGame): Vector[CoreOperation] = {
    val current = ready.game.current
    val total = BannerRules.resources(current, Banner.DarkestSecret)
    BannerRules.holder(current, Banner.DarkestSecret).toVector.flatMap { holder =>
      val kept = total - total / 2
      Option.when(kept > 0)(Move(Piece.Secrets(kept), secretBanner,
        PositionedLocation(Location.PlayArea(holder))): CoreOperation)
    }
  }

  private def pass(ready: ReadyGame, actor: PlayerId): Vector[Operation] = {
    val current = ready.game.current
    val remaining = BannerRules.resources(current, Banner.DarkestSecret)
    val tied = BannerRules.leastSites(current, Vector.empty)
    if (remaining >= tied.size)
      Vector(BuildOps((_, _) => Right(tied.map(place))))
    else Vector(
      // No window on this Decide: the enclosing `ChallengeRibbon` sequence
      // already gathered it, and a second gather would apply a power twice.
      Decide(siteDecisionId, actor, DecisionQuery.ChooseMany(remaining,
        tied.map(id => DecisionOption.Site(DecisionOptionRef.Site(id))),
        Some(s"Place $remaining secrets on tied least-stocked sites"))),
      BuildOps((_, pending) => chosen(pending).map(_.map(place))))
  }

  private def place(site: SiteId): CoreOperation = Move(Piece.Secrets(1),
    secretBanner, PositionedLocation(Location.Site(site)))

  private def chosen(pending: PendingTree): Either[OathViolation, Vector[SiteId]] =
    pending.answered.findLast(_.decisionId == siteDecisionId).map(_.answer) match {
      case Some(DecisionAnswer.ChooseManyAnswer(refs)) => Right(refs.collect {
        case DecisionOptionRef.Site(id) => id
      }.sortBy(_.value))
      case _ => Left(OathViolation.InvalidEventOrder(
        "the Challenge ribbon has no answered site choice"))
    }
}
