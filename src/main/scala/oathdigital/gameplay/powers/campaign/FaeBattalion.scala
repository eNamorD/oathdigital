package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.model._

object FaeBattalionCard extends Denizen(DenizenId("215"), "Fae Battalion", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.fae-battalion"),
    persistent = false, cost = Cost.free,
    text = "If you have the Darkest Secret, you may count each " +
      "[hollow-sword] as a [sword] instead.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Fae Battalion (card 215), an attacker's battle plan: "If you have the
  * Darkest Secret, you may count each [hollow-sword] as a [sword] instead."
  *
  * It is free, and offered only while its user holds the Darkest Secret.
  * Choosing it is the "may": once chosen it always applies, and each hollow
  * sword scores one sword (`AttackScore`).
  *
  * When the attack rolled a hollow sword it writes "Hollow swords counted as
  * swords."
  */
case object FaeBattalion extends BattlePlan:
  val cardId: DenizenId = FaeBattalionCard.id
  val id: PowerId = FaeBattalionCard.power.id
  /** "Hollow swords counted as swords." */
  val counted: NoteKey = NoteKey("counted",
    Vector(NotePart.Text("Hollow swords counted as swords.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = Vector(FaeBattalion.counted)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).filter(_ => context.holds(Banner.DarkestSecret))
      .map(source => CampaignPlanOffer(source,
        "Fae Battalion: count hollow swords as swords", Vector.empty,
        Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use => Vector(
      AttackScore.rescore(use.actor),
      Note(id, states => Option.when(AttackScore.hollowRolled(states.now))(
        FaeBattalion.counted(PowerSourceRef.Card(cardId)))))))
