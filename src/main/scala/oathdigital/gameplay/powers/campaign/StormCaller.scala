package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

object StormCallerCard extends Denizen(DenizenId("167"), "Storm Caller", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.storm-caller"),
    persistent = false, cost = Cost.free,
    text = "+2 [defense-die] At end, discard Storm Caller.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Storm Caller (card 167), a defender's battle plan: "+2 [defense-die] At
  * end, discard Storm Caller."
  *
  * It is free and adds two defense dice. The card is discarded through the
  * standard discard once the Campaign has resolved, whoever won. A bandit
  * defender applies it at a site it rules, and it is discarded then too.
  */
final case class StormCaller private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = StormCaller.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Storm Caller: add 2 defense dice", Vector.empty,
      Vector(CampaignPlanEffect.AddDefenseDice(StormCaller.Dice))))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      PlanDiscard.afterCampaign(catalog, id, use, cardId)))

object StormCaller:
  val id: PowerId = PowerId("denizen.storm-caller")
  val Dice: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[StormCaller] =
    CatalogCards.denizen(catalog, id).map(new StormCaller(_, catalog))
