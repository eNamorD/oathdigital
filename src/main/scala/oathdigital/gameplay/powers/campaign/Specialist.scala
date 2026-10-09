package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignAnswers, CampaignIds}
import oathdigital.gameplay.powerresolver.{OptionRestriction, PowerCtx}
import oathdigital.model._

object SpecialistCard extends Denizen(DenizenId("114"), "Specialist", Suit.Order):
  val power = PrintedPower(PowerId("denizen.specialist"),
    persistent = false, cost = Cost(favor = 2),
    text = "The defender **cannot** use battle plans.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Specialist (card 114), an attacker's battle plan: "[favor][favor] The
  * defender cannot use battle plans."
  *
  * Two favor are placed onto the card. Once it is chosen, every plan of the
  * defender, the title's included, is withheld with "Specialist: The
  * defender cannot use battle plans.", so the defender is not asked, and a
  * bandit defender applies nothing. The restriction reads the attacker's
  * answered plan picks, so it holds only in a Campaign that chose it.
  */
case object Specialist extends BattlePlan:
  val cardId: DenizenId = SpecialistCard.id
  val id: PowerId = SpecialistCard.power.id
  val Favor: Int = 2
  /** "The defender cannot use battle plans." */
  val silenced: NoteKey = NoteKey("silenced",
    Vector(NotePart.Text("The defender cannot use battle plans.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = Vector(Specialist.silenced)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Specialist: the defender cannot use battle plans",
      Vector(CampaignPlanCost.Favor(Specialist.Favor)), Vector.empty))

  override def restrictions: Map[PowerWindow, Vector[OptionRestriction]] =
    Map(PowerWindow.CampaignDefenderBattlePlans -> Vector(OptionRestriction(
      (ctx, _) => Option.when(chosen(ctx))(OathViolation.CampaignUnavailable(
        "Specialist keeps the defender from using battle plans")),
      (ctx, _) => Option.when(chosen(ctx))(
        Specialist.silenced(PowerSourceRef.Card(cardId))))))

  /** Whether the attacker chose this plan in this Campaign. */
  private def chosen(ctx: PowerCtx): Boolean = CampaignAnswers.picks(
    PendingTree(Vector.empty, ctx.answered), CampaignIds.attackerPlan)
    .contains(cardRef)
