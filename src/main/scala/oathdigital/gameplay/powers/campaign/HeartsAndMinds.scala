package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

object HeartsAndMindsCard extends Denizen(DenizenId("138"),
    "Hearts and Minds", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.hearts-and-minds"),
    persistent = false, cost = Cost(favor = 3),
    text = "As defender, you're victorious now. At end, discard Hearts " +
      "and Minds unless you hold the People's Favor.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Hearts and Minds (card 138, site only), a defender's battle plan: "As
  * defender, you're victorious now. At end, discard Hearts and Minds unless
  * you hold the People's Favor."
  *
  * Three favor are placed onto the card, so bandits never use it. Once
  * used, it decides the battle for the defender (`VictoriousNow`): nothing
  * is rolled or sacrificed, and the defender wins. The defeated attacker's
  * losses are the printed ones, half the surviving force. At the end of the
  * Campaign the card is discarded unless its user holds the People's Favor
  * then, a People's Favor won in this Campaign included.
  *
  * It writes no line of its own, only the discard's "Discarded after the
  * Campaign.".
  */
final case class HeartsAndMinds private (catalog: ExecutableCatalog)
    extends BattlePlan with DiscardedAtEnd:
  val cardId: DenizenId = HeartsAndMindsCard.id
  def id: PowerId = HeartsAndMinds.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Hearts and Minds: win the battle", Vector(CampaignPlanCost.Favor(3)),
      Vector.empty))

  override def wrapping: Map[PowerWindow, BattlePlan.Wrap] =
    VictoriousNow.silent

  override def endDiscard: PlanUse => Vector[Operation] = use =>
    if use.user.exists(favored(use.ready, _)) then Vector.empty
    else PlanDiscard.afterCampaign(catalog, id, use, cardId)

  private def favored(ready: ReadyGame, player: PlayerId): Boolean =
    BannerRules.holder(ready.game.current, Banner.PeoplesFavor)
      .contains(player)

object HeartsAndMinds:
  val id: PowerId = HeartsAndMindsCard.power.id

  def forCatalog(catalog: ExecutableCatalog): HeartsAndMinds =
    new HeartsAndMinds(catalog)
