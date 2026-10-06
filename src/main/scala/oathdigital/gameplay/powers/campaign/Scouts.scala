package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object ScoutsCard extends Denizen(DenizenId("8"), "Scouts", Suit.Order):
  val power = PrintedPower(PowerId("denizen.scouts"),
    persistent = false, cost = Cost.free,
    text = "Gain 1 Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Scouts (card 8), an attacker's battle plan: "Gain 1 Supply." The printed
  * card's action icon is the red battle plan (catalog batch 4 design, "Goal
  * and scope").
  *
  * It is free. When it is applied the attacker gains 1 Supply, up to the
  * Supply track's maximum (`GainSupply`). It writes "Gained {n} Supply." with
  * the Supply gained, and nothing when none was.
  */
case object Scouts extends BattlePlan:
  val cardId: DenizenId = ScoutsCard.id
  val id: PowerId = ScoutsCard.power.id
  val Supply: Int = 1
  /** "Gained {n} Supply." */
  val gained: NoteKey = NoteKey("gained", Vector(NotePart.Text("Gained "),
    NotePart.Arg(0), NotePart.Text(".")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = Vector(Scouts.gained)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.user.flatMap(user => context.denizen(cardId).map(source =>
      CampaignPlanOffer(source, "Scouts: gain 1 Supply", Vector.empty,
        Vector(CampaignPlanEffect.Run(Vector(GainSupply(user, Scouts.Supply),
          Note(id, states => states.previous
            .map(NoteSupport.supply(_, user)).filter(_ > 0)
            .map(supply => Scouts.gained(PowerSourceRef.Card(cardId),
              NoteArg.Amount(supply, NoteUnit.Supply))))))))))
