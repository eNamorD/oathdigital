package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds}
import oathdigital.model._

object ZealotsCard extends Denizen(DenizenId("87"), "Zealots", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.zealots"),
    persistent = false, cost = Cost(favor = 1),
    text = "If the defending force is larger than your force, each " +
      "warband you sacrifice will add three _(not one)_ to your " +
      "attack.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Zealots (card 87), an attacker's battle plan: "If the defending force is
  * larger than your force, each warband you sacrifice will add three (not
  * one) to your attack."
  *
  * One favor is placed onto the card. The condition compares the defending
  * warbands (`CampaignBattle.defenderForce`) with the committed force when
  * the plan is chosen, and the outcome is recorded then (FAQ: later skull
  * losses do not trigger it). While it holds, the plan records a worth of
  * three in `CampaignIds.sacrificeWorth`, which the sacrifice question,
  * the battle's result and the Game Log read. When it does not hold, the
  * plan may still be chosen and does nothing, and its option carries the
  * warning "Has no effect unless defending force is larger than yours".
  *
  * It writes no line of its own.
  */
case object Zealots extends BattlePlan:
  val cardId: DenizenId = ZealotsCard.id
  val id: PowerId = ZealotsCard.power.id
  /** What each sacrificed warband adds while the plan holds. */
  val Worth: Int = 3
  private val notes = NoteSet(id)
  /** "Has no effect unless defending force is larger than yours", the
    * option's warning. */
  val noEffect: NoteKey = notes.key("no-effect", Vector(NotePart.Text(
    "Has no effect unless defending force is larger than yours")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = notes.declared

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map { source =>
      val offer = CampaignPlanOffer(source,
        "Zealots: each sacrificed warband adds three",
        Vector(CampaignPlanCost.Favor(1)), Vector.empty)
      if holds(context) then offer.copy(effects = Vector(
        CampaignPlanEffect.Run(Vector(
          ModifyDicePool(CampaignIds.sacrificeWorth, Zealots.Worth)))))
      else offer.copy(warning = Some(OptionReason.Noted(id,
        Zealots.noEffect(PowerSourceRef.Card(cardId)))))
    }

  /** Whether the defending force is larger than the committed one now. */
  private def holds(context: PlanContext): Boolean =
    CampaignBattle.defenderForce(context.ready, context.setup) >
      context.setup.force
