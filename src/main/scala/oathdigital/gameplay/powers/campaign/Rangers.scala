package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.model._

object RangersCard extends Denizen(DenizenId("45"), "Rangers", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.rangers"),
    persistent = false, cost = Cost(favor = 1),
    text = "Ignore all skulls [skull] you roll. +2 [attack-die] if the " +
      "defense pool has 4+ [defense-die].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Rangers (card 45), an attacker's battle plan: "[favor] Ignore all skulls
  * you roll. +2 [attack-die] if the defense pool has 4+ [defense-die]."
  *
  * A favor is placed onto the card. Every skull the attack rolls is ignored,
  * exactly as Outriders does (`Outriders.ignoreSkulls`). The two dice are
  * added when the defense pool holds four or more dice when Rangers is
  * chosen; the defender's window comes later, so dice the defender adds do
  * not count. It is offered when the condition fails too, for the skulls.
  */
case object Rangers extends BattlePlan:
  val cardId: DenizenId = RangersCard.id
  val id: PowerId = RangersCard.power.id
  val Dice: Int = 2
  /** The defense dice that earn the extra attack dice. */
  val Pool: Int = 4

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  private val notes = NoteSet(id)
  notes.include(Outriders.notes)
  override def noteKeys: Vector[NoteKey] = notes.declared

  private def defensePool(ready: ReadyGame): Int =
    ready.game.current.rollPools.get(CampaignIds.defensePool).fold(0)(_.count)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    val dice = Option.when(defensePool(context.ready) >= Rangers.Pool)(
      Rangers.Dice)
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      dice.fold("Rangers: ignore all attack skulls")(count =>
        s"${PlanDice.label("Rangers", context.side, count)} and ignore all " +
          "attack skulls"),
      Vector(CampaignPlanCost.Favor(1)),
      dice.map(CampaignPlanEffect.AddAttackDice(_)).toVector))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use =>
      Outriders.ignoreSkulls(use, id, PowerSourceRef.Card(cardId))))
