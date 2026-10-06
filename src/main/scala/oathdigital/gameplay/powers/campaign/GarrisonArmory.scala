package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignSetup}
import oathdigital.model._

object GarrisonArmoryCard extends Denizen(DenizenId("255"), "Garrison Armory", Suit.Order):
  val power = PrintedPower(PowerId("denizen.garrison-armory"),
    persistent = false, cost = Cost(favor = 1),
    text = "In a conquest, warbands on targeted sites each add +2 " +
      "defense _(instead of +1)_.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Garrison Armory (card 255), a defender's battle plan: "[favor] In a
  * conquest, warbands on targeted sites each add +2 defense (instead of
  * +1)."
  *
  * A favor is placed onto the card. It is offered only in a Conquest. When the
  * defense is scored, it writes the defense through the defense scorer
  * (`DefenseScore`), which adds the warbands at the targets twice, after any
  * single-shield rescoring of the dice and after the doubler, which multiplies
  * the dice only. Bandits pay nothing, so a bandit defender never applies it.
  *
  * When the targets held a warband it writes "Warbands on the targets added
  * {n} more defense."
  */
case object GarrisonArmory extends BattlePlan:
  val cardId: DenizenId = GarrisonArmoryCard.id
  val id: PowerId = GarrisonArmoryCard.power.id

  /** "Warbands on the targets added {n} more defense." */
  val added: NoteKey = NoteKey("added", Vector(
    NotePart.Text("Warbands on the targets added "), NotePart.Arg(0),
    NotePart.Text(" more defense.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(GarrisonArmory.added)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.setup.kind == CampaignKind.Conquest)
      .map(source => CampaignPlanOffer(source,
        "Garrison Armory: warbands on the targets add 2 defense each",
        Vector(CampaignPlanCost.Favor(1)), Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignDefenseResult -> (use => Vector(
      DefenseScore.rescore(use.actor),
      Branch((ready, pending) => CampaignSetup.setup(ready, use.actor, pending)
        .toVector.flatMap { setup =>
          val force = CampaignBattle.defenderForce(ready, setup)
          Option.when(force > 0)(Note(id, _ => Some(GarrisonArmory.added(
            PowerSourceRef.Card(cardId), NoteArg.Number(force))))).toVector
        }))))
