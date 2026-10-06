package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.model._

object FearsomeGeneralCard extends Denizen(DenizenId("253"), "Fearsome General", Suit.Order):
  val power = PrintedPower(PowerId("denizen.fearsome-general"),
    persistent = false, cost = Cost.free,
    text = "Each [skull] you roll adds two more [sword] _(4 total)_, " +
      "even if the [skull] is ignored _(Outriders, etc.)_.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Fearsome General (card 253), an attacker's battle plan: "Each skull you
  * roll adds two more swords (4 total), even if the skull is ignored
  * (Outriders, etc.)."
  *
  * It is free. Each skull face is worth four swords instead of two, under
  * every rule the face follows (`AttackScore`): an unpayable skull still
  * scores nothing, an ignored one scores four, and War Tortoise scores it as
  * nothing.
  *
  * When it added swords it writes "Skulls added {n} more swords.", with the
  * swords it added: the score with it, less the score without it.
  */
case object FearsomeGeneral extends BattlePlan:
  val cardId: DenizenId = FearsomeGeneralCard.id
  val id: PowerId = FearsomeGeneralCard.power.id
  /** "Skulls added {n} more swords." */
  val added: NoteKey = NoteKey("added", Vector(
    NotePart.Text("Skulls added "), NotePart.Arg(0),
    NotePart.Text(" more swords.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = Vector(FearsomeGeneral.added)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Fearsome General: each skull is worth four swords", Vector.empty,
      Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use => Vector(
      AttackScore.rescore(use.actor),
      Branch((ready, pending) => {
        val swords = AttackScore.addedBy(ready, pending, use.actor,
          _.copy(skullSwords = 2))
        Option.when(swords > 0)(Note(id, _ => Some(FearsomeGeneral.added(
          PowerSourceRef.Card(cardId), NoteArg.Number(swords))))).toVector
      }))))
