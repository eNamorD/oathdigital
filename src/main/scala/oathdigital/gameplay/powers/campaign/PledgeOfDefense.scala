package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Pledge of Defense (card 243), a defender's battle plan: "+X [defense-die]
  * X equals all [suit-nomad] cards you rule. At end, discard this card."
  *
  * It is free. It adds one defense die per nomad card its user rules,
  * counting itself, as Great Crusade counts (`GreatCrusade.nomads`). A bandit
  * defender counts the nomad cards at every site bandits rule. The card is
  * discarded through the standard discard once the Campaign has resolved,
  * whoever won, as Horse Archers is.
  */
final case class PledgeOfDefense private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = PledgeOfDefense.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map { source =>
      val dice = GreatCrusade.nomads(catalog, context, cardId)
      val noun = if dice == 1 then "defense die" else "defense dice"
      CampaignPlanOffer(source, s"Pledge of Defense: add $dice $noun",
        Vector.empty, Vector(CampaignPlanEffect.AddDefenseDice(dice)))
    }

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      PlanDiscard.afterCampaign(catalog, id, use, cardId)))

object PledgeOfDefense:
  val id: PowerId = PowerId("denizen.pledge-of-defense")

  def forCatalog(catalog: ExecutableCatalog): Option[PledgeOfDefense] =
    CatalogCards.denizen(catalog, id).map(new PledgeOfDefense(_, catalog))
