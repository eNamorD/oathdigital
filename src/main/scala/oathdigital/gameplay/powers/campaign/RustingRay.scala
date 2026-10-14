package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.model._

object RustingRayCard extends Denizen(DenizenId("57"), "Rusting Ray", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.rusting-ray"),
    persistent = false, cost = Cost(secret = 1),
    text = "If you hold the Darkest Secret, **ignore** all your enemy's " +
      "rolls of hollow swords [hollow-sword].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Rusting Ray (card 57), a defender's battle plan: "[secret] If you hold the
  * Darkest Secret, ignore all your enemy's rolls of hollow swords."
  *
  * A secret is placed onto the card. It is offered only while its user holds
  * the Darkest Secret, so a bandit defender never uses it. Once the attack is
  * scored, each hollow sword scores nothing (`AttackScore`). Fae Battalion,
  * the attacker's opposite, needs the Darkest Secret too, so the two never
  * meet.
  *
  * When the attack rolled a hollow sword it writes "Hollow swords ignored."
  */
case object RustingRay extends BattlePlan:
  val cardId: DenizenId = RustingRayCard.id
  val id: PowerId = RustingRayCard.power.id
  /** "Hollow swords ignored." */
  val ignored: NoteKey = NoteKey("ignored",
    Vector(NotePart.Text("Hollow swords ignored.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(RustingRay.ignored)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).filter(_ => context.holdsDarkestSecret)
      .map(source => CampaignPlanOffer(source,
        "Rusting Ray: the attack's hollow swords score nothing",
        Vector(CampaignPlanCost.Secret(1)), Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use => Vector(
      AttackScore.rescore(use.actor),
      Note(id, states => Option.when(AttackScore.hollowRolled(states.now))(
        RustingRay.ignored(PowerSourceRef.Card(cardId)))))))
