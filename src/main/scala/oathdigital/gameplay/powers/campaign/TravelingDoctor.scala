package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powerresolver.{ContributingPower, PowerCtx}
import oathdigital.model._

object TravelingDoctorCard extends Denizen(DenizenId("51"), "Traveling Doctor", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.traveling-doctor"),
    persistent = false, cost = Cost.free,
    text = "If you're defeated, kill no warbands in your force and " +
      "discard Traveling Doctor. **Ignore** powers that kill all " +
      "of your force.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Traveling Doctor (card 51), a battle plan for either side: "If you're
  * defeated, kill no warbands in your force and discard Traveling Doctor.
  * Ignore powers that kill all of your force."
  *
  * Free. The rule, its ignore and its line are `SparedForce`'s, shared with
  * Billowing Fog. When its user was defeated, the card takes the standard
  * discard once the Campaign has resolved (`PlanDiscard.afterCampaign`). A
  * bandit defender applies it too: bandits are no player, so the rule spares
  * nothing and every targeted bandit still dies, and the card is discarded.
  */
final case class TravelingDoctor private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = TravelingDoctorCard.id
  def id: PowerId = TravelingDoctor.id

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def priority: Int = SparedForce.Priority
  override def noteKeys: Vector[NoteKey] =
    Vector(SparedForce.spared, PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Traveling Doctor: if defeated, kill no warbands in your force, then " +
        "discard it", Vector.empty, Vector.empty))

  override def wrapping: Map[PowerWindow, BattlePlan.Wrap] = Map(
    PowerWindow.CampaignLosses -> ((ctx, use, losses) =>
      SparedForce.spare(ctx, id, cardId, use, losses)))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if use.won.contains(false) then
        PlanDiscard.afterCampaign(catalog, id, use, cardId)
      else Vector.empty))

  override def ignores(ctx: PowerCtx, other: ContributingPower): Boolean =
    SparedForce.ignores(ctx, this, other)

object TravelingDoctor:
  val id: PowerId = TravelingDoctorCard.power.id

  def forCatalog(catalog: ExecutableCatalog): TravelingDoctor =
    new TravelingDoctor(catalog)
