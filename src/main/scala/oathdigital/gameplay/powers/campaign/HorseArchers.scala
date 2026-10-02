package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

object HorseArchersCard extends Denizen(DenizenId("24"), "Horse Archers", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.horse-archers"),
    persistent = false, cost = Cost.free,
    text = "±3 [attack-die] At end, discard Horse Archers.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Horse Archers (card 24), a battle plan for either side: "±3 [attack-die]
  * At end, discard Horse Archers."
  *
  * It is free. An attacker adds three attack dice; a defender removes three.
  * The card is discarded through the standard discard once the Campaign has
  * resolved, whoever won, as Warning Signals is. A bandit defender applies it
  * at a site it rules, and it is discarded then too.
  */
final case class HorseArchers private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = HorseArchers.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      PlanDice.label("Horse Archers", context.side, HorseArchers.Dice),
      Vector.empty, Vector(PlanDice.effect(context.side, HorseArchers.Dice))))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      PlanDiscard.afterCampaign(catalog, id, use, cardId)))

object HorseArchers:
  val id: PowerId = PowerId("denizen.horse-archers")
  val Dice: Int = 3

  def forCatalog(catalog: ExecutableCatalog): Option[HorseArchers] =
    CatalogCards.denizen(catalog, id).map(new HorseArchers(_, catalog))
