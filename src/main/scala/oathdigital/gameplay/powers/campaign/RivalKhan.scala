package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Rival Khan (card 156), a battle plan for either side: "±4 [attack-die] if
  * your enemy has a [suit-nomad] adviser. At end, discard Rival Khan."
  *
  * It is free. An attacker adds four attack dice; a defender removes four. It
  * is offered only when the enemy has a faceup nomad adviser, so never against
  * bandits. The card is discarded through the standard discard once the
  * Campaign has resolved, whoever won, as Horse Archers is.
  */
final case class RivalKhan private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = RivalKhan.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.enemyHasAdviser(catalog, Suit.Nomad))
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Rival Khan", context.side, RivalKhan.Dice),
        Vector.empty, Vector(PlanDice.effect(context.side, RivalKhan.Dice))))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      PlanDiscard.afterCampaign(catalog, id, use, cardId)))

object RivalKhan:
  val id: PowerId = PowerId("denizen.rival-khan")
  val Dice: Int = 4

  def forCatalog(catalog: ExecutableCatalog): Option[RivalKhan] =
    CatalogCards.denizen(catalog, id).map(new RivalKhan(_, catalog))
