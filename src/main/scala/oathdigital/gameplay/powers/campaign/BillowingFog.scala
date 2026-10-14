package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powerresolver.{ContributingPower, PowerCtx}
import oathdigital.model._

object BillowingFogCard extends Denizen(DenizenId("59"), "Billowing Fog", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.billowing-fog"),
    persistent = false, cost = Cost(secret = 1),
    text = "If you're defeated, kill no warbands in your force. " +
      "**Ignore** powers that kill all of your force.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Billowing Fog (card 59), a battle plan for either side: "[secret] If
  * you're defeated, kill no warbands in your force. Ignore powers that kill
  * all of your force."
  *
  * A secret is placed onto the card, so a bandit defender never uses it. The
  * rule, its ignore and its line are `SparedForce`'s, shared with Traveling
  * Doctor.
  */
case object BillowingFog extends BattlePlan:
  val cardId: DenizenId = BillowingFogCard.id
  val id: PowerId = BillowingFogCard.power.id
  /** The secret placed to choose it. */
  val Secret: Int = 1

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def priority: Int = SparedForce.Priority
  private val notes = NoteSet(id)
  notes.include(SparedForce.notes)
  override def noteKeys: Vector[NoteKey] = notes.declared

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Billowing Fog: if defeated, kill no warbands in your force",
      Vector(CampaignPlanCost.Secret(BillowingFog.Secret)), Vector.empty))

  override def wrapping: Map[PowerWindow, BattlePlan.Wrap] = Map(
    PowerWindow.CampaignLosses -> ((ctx, use, losses) =>
      SparedForce.spare(ctx, id, cardId, use, losses)))

  override def ignores(ctx: PowerCtx, other: ContributingPower): Boolean =
    SparedForce.ignores(ctx, this, other)
