package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

object GreatCrusadeCard extends Denizen(DenizenId("164"), "Great Crusade", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.great-crusade"),
    persistent = false, cost = Cost.free,
    text = "± [attack-die] per [suit-nomad] card you rule. At end, " +
      "discard Great Crusade.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Great Crusade (card 164), a battle plan for either side: "± [attack-die]
  * per [suit-nomad] card you rule. At end, discard Great Crusade."
  *
  * It is free. An attacker adds one attack die per nomad card they rule; a
  * defender removes one per card. It counts itself, so it is always worth at
  * least one die. A bandit defender counts the nomad cards at every site
  * bandits rule. The card is discarded through the standard discard once the
  * Campaign has resolved, whoever won, as Horse Archers is.
  */
final case class GreatCrusade private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = GreatCrusade.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map { source =>
      val dice = GreatCrusade.nomads(catalog, context, cardId)
      CampaignPlanOffer(source, PlanDice.label("Great Crusade", context.side,
        dice), Vector.empty, Vector(PlanDice.effect(context.side, dice)))
    }

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      PlanDiscard.afterCampaign(catalog, id, use, cardId)))

object GreatCrusade:
  val id: PowerId = PowerId("denizen.great-crusade")

  /** The nomad cards the plan's user rules, counting `card` once whatever its
    * orientation: choosing a plan reveals a facedown adviser, so it is faceup
    * and ruled once chosen. A bandit defender counts the nomad cards at every
    * site bandits rule. Always at least 1. Pledge of Defense counts the same.
    */
  private[campaign] def nomads(catalog: ExecutableCatalog, context: PlanContext,
      card: DenizenId): Int =
    context.cardsRuled(catalog, context.ruler, Suit.Nomad).count(_ != card) + 1

  def forCatalog(catalog: ExecutableCatalog): Option[GreatCrusade] =
    CatalogCards.denizen(catalog, id).map(new GreatCrusade(_, catalog))
