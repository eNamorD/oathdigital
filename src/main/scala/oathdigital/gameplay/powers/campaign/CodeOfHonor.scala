package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powerresolver.OptionRestriction
import oathdigital.model._

object CodeOfHonorCard extends Denizen(DenizenId("103"), "Code of Honor", Suit.Order):
  val power = PrintedPower(PowerId("denizen.code-of-honor"),
    persistent = false, cost = Cost.free,
    text = "±2 [attack-die] but you **cannot** use other battle plans.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Code of Honor (card 103), a battle plan for either side: "±2 [attack-die]
  * but you cannot use other battle plans."
  *
  * It is free. An attacker adds two attack dice; a defender removes two. It
  * is withheld once its user has chosen another plan in the window, with
  * "Code of Honor: Cannot be used with other battle plans.". Once it is
  * chosen, the user's other plans are withheld, with "Code of Honor: No
  * other battle plans." (`SolePlan`). A bandit defender that would apply it
  * applies it and no other plan (FAQ).
  */
case object CodeOfHonor extends BattlePlan:
  val cardId: DenizenId = CodeOfHonorCard.id
  val id: PowerId = CodeOfHonorCard.power.id
  val Dice: Int = 2
  private val notes = NoteSet(id)
  /** "Cannot be used with other battle plans." */
  val notWithOthers: NoteKey = notes.key("not-with-others",
    Vector(NotePart.Text("Cannot be used with other battle plans.")))
  /** "No other battle plans." */
  val noOthers: NoteKey = notes.key("no-others",
    Vector(NotePart.Text("No other battle plans.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = notes.declared

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      PlanDice.label("Code of Honor", context.side, CodeOfHonor.Dice),
      Vector.empty, Vector(PlanDice.effect(context.side, CodeOfHonor.Dice)),
      alone = true))

  override def restrictions: Map[PowerWindow, Vector[OptionRestriction]] =
    SolePlan.restrictions(cardRef, sides, PowerSourceRef.Card(cardId),
      CodeOfHonor.notWithOthers, CodeOfHonor.noOthers)
