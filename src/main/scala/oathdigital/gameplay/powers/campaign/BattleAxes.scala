package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

object BattleAxesCard extends Denizen(DenizenId("256"), "Battle Axes", Suit.Order):
  val power = PrintedPower(PowerId("denizen.battle-axes"),
    persistent = false, cost = Cost.free,
    text = "±2 [attack-die] if your enemy rules a [suit-beast] card.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Battle Axes (card 256), a battle plan for either side: "±2 [attack-die] if
  * your enemy rules a [suit-beast] card."
  *
  * It is free. An attacker adds two attack dice; a defender removes two. It is
  * offered only while the enemy rules a beast card (`RuledCards`). Against
  * bandits it holds when a beast card stands at any site bandits rule. A
  * bandit defender applies it at a site it rules when the attacker rules a
  * beast card.
  */
final case class BattleAxes private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = BattleAxes.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.cardsRuled(catalog, context.enemy, Suit.Beast)
        .nonEmpty)
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Battle Axes", context.side, BattleAxes.Dice),
        Vector.empty, Vector(PlanDice.effect(context.side, BattleAxes.Dice))))

object BattleAxes:
  val id: PowerId = PowerId("denizen.battle-axes")
  val Dice: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[BattleAxes] =
    CatalogCards.denizen(catalog, id).map(new BattleAxes(_, catalog))
