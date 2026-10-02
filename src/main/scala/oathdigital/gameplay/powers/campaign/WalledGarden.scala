package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.model._

object WalledGardenCard extends Denizen(DenizenId("195"), "Walled Garden", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.walled-garden"),
    persistent = false, cost = Cost.free,
    text = "+[defense-die] per [suit-beast] at any sites if this site " +
      "is targeted.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Walled Garden (card 195), a defender's battle plan: "+[defense-die] per
  * [suit-beast] at any sites if this site is targeted."
  *
  * It is site-only and free, used by the ruler of its site, and offered only
  * when a Conquest targets that site; a Raid targets no site. It adds one
  * defense die per faceup beast denizen and per beast edifice, on either face,
  * at every site in play, whoever rules it. A facedown denizen has no suit. It
  * counts itself, so it adds at least one. A bandit defender applies it at a
  * site it rules.
  */
final case class WalledGarden private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = WalledGardenCard.id
  def id: PowerId = WalledGarden.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).collect {
      case source: CampaignPlanSource.SiteCard if context.targets(source.siteId) =>
        val dice = beasts(context)
        val noun = if dice == 1 then "defense die" else "defense dice"
        CampaignPlanOffer(source, s"Walled Garden: add $dice $noun",
          Vector.empty, Vector(CampaignPlanEffect.AddDefenseDice(dice)))
    }

  /** The beast cards at every site in play. */
  private def beasts(context: PlanContext): Int =
    val map = context.ready.game.current.map
    map.inPlay.flatMap(map.sites.get).flatMap(_.denizens).count {
      case DenizenState(held, Orientation.FaceUp, _) =>
        catalog.suitOf(held).contains(Suit.Beast)
      case edifice: EdificeState => catalog.suitOf(edifice.id).contains(Suit.Beast)
      case _ => false
    }

object WalledGarden:
  val id: PowerId = WalledGardenCard.power.id

  def forCatalog(catalog: ExecutableCatalog): WalledGarden =
    new WalledGarden(catalog)
