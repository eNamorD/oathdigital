package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignBattle
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

object EncirclementCard extends Denizen(DenizenId("124"), "Encirclement", Suit.Order):
  val power = PrintedPower(PowerId("denizen.encirclement"),
    persistent = false, cost = Cost(favor = 1),
    text = "±2 [attack-die] if your force is larger than your enemy's.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Encirclement (card 124), a battle plan for either side: "[favor] ±2
  * [attack-die] if your force is larger than your enemy's."
  *
  * A favor is placed onto the card. An attacker adds two attack dice; a
  * defender removes two. It is offered only while its user's force is strictly
  * larger than the enemy's. The attacker's force is the warbands committed:
  * dice from a plan such as Brass Army are not force. The defender's force is
  * what the defense adds (`CampaignBattle.defenderForce`): the warbands at
  * every target in a Conquest, bandits included, or the defender's board in a
  * Raid. Both are read when the plan is offered, so a plan chosen earlier in
  * the same window, such as a Wrestlers sacrifice, counts. Bandits pay nothing,
  * so a bandit defender never applies it.
  */
final case class Encirclement private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = Encirclement.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).filter(_ => larger(context))
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Encirclement", context.side, Encirclement.Dice),
        Vector(CampaignPlanCost.Favor(1)),
        Vector(PlanDice.effect(context.side, Encirclement.Dice))))

  /** Whether the user's force is strictly larger than the enemy's. */
  private def larger(context: PlanContext): Boolean =
    val attacker = context.setup.force
    val defender = CampaignBattle.defenderForce(context.ready, context.setup)
    context.side match
      case CampaignPlanSide.Attacker => attacker > defender
      case CampaignPlanSide.Defender => defender > attacker

object Encirclement:
  val id: PowerId = PowerId("denizen.encirclement")
  val Dice: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[Encirclement] =
    CatalogCards.denizen(catalog, id).map(new Encirclement(_))
