package oathdigital.gameplay.powers.targeting

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignSetup}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, OptionRestriction, PowerCtx, Restriction}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** A rule of the Fortress edifice (E28) that keeps a player from being the
  * target of a Challenge or a Raid. A Conquest is not affected. Each face is a
  * persistent rule of the edifice wherever it stands, so it applies to every
  * player, and it needs no selection.
  *
  * A protected player is taken out of the decisions that name them:
  *
  *  - a Raid's defender decision (`CampaignDefenderSelection`), which is asked
  *    when several enemy pawns stand at the attacker's site;
  *  - a Challenge's banner choice (`ChallengeBannerSelection`), which loses the
  *    banner a protected player holds.
  *
  * Campaign asks neither of those when it needs no choice, so two more hooks
  * cover a single defender. The kind decision loses its Raid when every enemy
  * pawn at the site is protected, and a Campaign whose only legal kind is such a
  * Raid is refused when it starts. That refusal applies only until the Campaign
  * has answered one of its own decisions: once a Campaign is under way it must
  * be able to finish, whatever the board has since become. (A Conquest that
  * takes the site leaves the Raid as the only legal kind, for one.) The answers
  * are read from the pending position the restriction is checked against, so it
  * holds for a Campaign that a power runs inside another action as well.
  */
sealed abstract class FortressRule extends ContributingPower {
  def catalog: ExecutableCatalog
  protected def fortress: EdificeId
  protected def side: EdificeSide

  /** Whether `defender` may not be targeted by `attacker`. */
  protected def shields(ready: ReadyGame, attacker: PlayerId,
      defender: PlayerId): Boolean

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  final override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  final def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignKindSelection -> Vector(OptionRestriction(kindGuard)),
    PowerWindow.CampaignDefenderSelection ->
      Vector(OptionRestriction(defenderGuard)),
    PowerWindow.CampaignActionEligibility ->
      Vector(Restriction((ctx, _) => startGuard(ctx))),
    PowerWindow.ChallengeBannerSelection ->
      Vector(OptionRestriction(bannerGuard)))

  private def blocked(detail: String): OathViolation =
    OathViolation.CampaignUnavailable(detail)

  /** The sites where this face of the Fortress stands. */
  protected final def sites(ready: ReadyGame): Vector[SiteId] =
    ready.game.current.map.sites.collect {
      case (id, site) if site.denizens.exists {
        case card: EdificeState => card.id == fortress && card.side == side
        case _ => false
      } => id
    }.toVector

  protected final def pawnSite(ready: ReadyGame, player: PlayerId)
      : Option[SiteId] = ready.game.current.players.find(_.player == player)
    .flatMap(_.pawnSite)

  private def raidBlocked(ready: ReadyGame, attacker: PlayerId): Boolean = {
    val defenders = CampaignSetup.raidDefenders(ready, attacker)
    defenders.nonEmpty && defenders.forall(shields(ready, attacker, _))
  }

  private def kindGuard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = ref match {
    case DecisionOptionRef.Button("raid")
        if raidBlocked(ctx.state, ctx.activePlayer) =>
      Some(blocked("a Fortress protects every player a Raid could target"))
    case _ => None
  }

  private def defenderGuard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = ref match {
    case DecisionOptionRef.Player(defender)
        if shields(ctx.state, ctx.activePlayer, defender) =>
      Some(blocked(s"a Fortress protects ${defender.value} from a Raid"))
    case _ => None
  }

  private def startGuard(ctx: PowerCtx): Option[OathViolation] =
    Option.when(!underway(ctx.state) &&
      CampaignSetup.legalKinds(ctx.state, ctx.activePlayer) ==
        Vector(CampaignKind.Raid) && raidBlocked(ctx.state, ctx.activePlayer))(
      blocked("a Fortress protects every player a Raid could target"))

  /** The Campaign has answered one of its own decisions. */
  private def underway(ready: ReadyGame): Boolean =
    ready.game.current.walkerPending.exists(_.answered.exists(answered =>
      CampaignIds.all.contains(answered.decisionId)))

  private def bannerGuard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = ref match {
    case DecisionOptionRef.Banner(banner)
        if BannerRules.holder(ctx.state.game.current, banner)
          .exists(shields(ctx.state, ctx.activePlayer, _)) =>
      Some(OathViolation.InvalidEventOrder(
        s"a Fortress protects the holder of ${banner.key} from a Challenge"))
    case _ => None
  }
}

/** The Oaken Fortress (E28, intact): while its ruler is at this site, they
  * cannot be targeted by a Challenge or a Raid. Empire rulers are not
  * supported.
  */
final case class OakenFortress private (fortress: EdificeId,
    catalog: ExecutableCatalog) extends FortressRule {
  def id: PowerId = OakenFortress.id
  protected def side: EdificeSide = EdificeSide.Intact

  protected def shields(ready: ReadyGame, attacker: PlayerId,
      defender: PlayerId): Boolean = defender != attacker &&
    sites(ready).exists(site => pawnSite(ready, defender).contains(site) &&
      ready.game.current.map.sites.get(site).flatMap(state =>
        SiteRule.ruler(state.forces, ready.game.current.players).toOption)
        .contains(SiteRuler.Player(defender)))
}

object OakenFortress {
  val id: PowerId = PowerId("edifice.e28.intact")

  def forCatalog(catalog: ExecutableCatalog): Option[OakenFortress] =
    CatalogCards.edifice(catalog, id).map(new OakenFortress(_, catalog))
}

/** The Rotting Fortress (E28, ruined): players at this site cannot be targeted
  * by a Challenge or a Raid, unless the targeting player has a faceup beast
  * adviser.
  */
final case class RottingFortress private (fortress: EdificeId,
    catalog: ExecutableCatalog) extends FortressRule {
  def id: PowerId = RottingFortress.id
  protected def side: EdificeSide = EdificeSide.Ruined

  protected def shields(ready: ReadyGame, attacker: PlayerId,
      defender: PlayerId): Boolean = defender != attacker &&
    sites(ready).exists(site => pawnSite(ready, defender).contains(site)) &&
    !holdsBeastAdviser(ready, attacker)

  private def holdsBeastAdviser(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player).exists(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) =>
        catalog.suitOf(card).contains(Suit.Beast)
      case _ => false
    })
}

object RottingFortress {
  val id: PowerId = PowerId("edifice.e28.ruined")

  def forCatalog(catalog: ExecutableCatalog): Option[RottingFortress] =
    CatalogCards.edifice(catalog, id).map(new RottingFortress(_, catalog))
}
