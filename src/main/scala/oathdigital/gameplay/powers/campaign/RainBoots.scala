package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.model._

object RainBootsCard extends Denizen(DenizenId("14"), "Rain Boots", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.rain-boots"),
    persistent = false, cost = Cost.free,
    text = "Ignore all your enemy's rolls of single shields [shield]. " +
      "At end, discard Rain Boots.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Rain Boots (card 14), an attacker's battle plan: "Ignore all your enemy's
  * rolls of single shields [shield]. At end, discard Rain Boots."
  *
  * It is free. Only the defender rolls shields, so it is the attacker's plan.
  * Once the defense is rolled, each single-shield die scores 0, exactly as for
  * Bag of Siegeworks (`SingleShields`); two shields and doublers are
  * unaffected. Unlike the Bag it is offered in a Raid too. With the Bag also
  * chosen, both write the same score. The card is discarded through the
  * standard discard once the Campaign has resolved, whoever won, as Horse
  * Archers is.
  */
final case class RainBoots private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = RainBootsCard.id
  def id: PowerId = RainBoots.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] =
    Vector(SingleShields.ignored, PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Rain Boots: ignore single shields", Vector.empty, Vector.empty))

  override def wrapping
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] = Map(
    PowerWindow.CampaignDefenseResult -> ((_, children) =>
      SingleShields.ignore(id, PowerSourceRef.Card(cardId)) ++ children))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      PlanDiscard.afterCampaign(catalog, id, use, cardId)))

object RainBoots:
  val id: PowerId = RainBootsCard.power.id

  def forCatalog(catalog: ExecutableCatalog): RainBoots =
    new RainBoots(catalog)
