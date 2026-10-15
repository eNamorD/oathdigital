package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.model._

object OutridersCard extends Denizen(DenizenId("104"), "Outriders", Suit.Order):
  val power = PrintedPower(PowerId("denizen.outriders"),
    persistent = false, cost = Cost.free,
    text = "Ignore all skulls [skull] you roll.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Outriders (card 104), an attacker's battle plan: "Ignore all skulls you roll."
  *
  * Choosing it costs nothing and changes no dice. Once the attack is scored, the
  * plan writes the roll outcome again without the skull cap, so no skull removes
  * a warband and every skull face keeps its two swords. A facedown Outriders is
  * revealed when it is chosen, as any plan's source is. The rewrite is
  * `Outriders.ignoreSkulls`, through the attack scorer (`AttackScore`),
  * which The Great Levy, Kindred Warriors and Rangers share.
  *
  * When the attack rolled a skull it writes "Skulls ignored."
  */
case object Outriders extends BattlePlan:
  val cardId: DenizenId = OutridersCard.id
  val id: PowerId = OutridersCard.power.id
  /** Exposed: other powers write this power's lines and include this
    * set to declare them. */
  val notes: NoteSet = NoteSet(id)
  /** "Skulls ignored." */
  val ignored: NoteKey = notes.key("ignored",
    Vector(NotePart.Text("Skulls ignored.")))

  /** The attack scored again by every chosen plan (`AttackScore`), then
    * `power`'s "Skulls ignored." line from `source`, only when the attack
    * rolled a skull to ignore. The faces are kept, so they still show the
    * skulls. */
  private[campaign] def ignoreSkulls(use: PlanUse, power: PowerId,
      source: PowerSourceRef): Vector[Operation] = Vector(
    AttackScore.rescore(use.actor),
    Note(power, states => Option.when(AttackScore.skullRolled(states.now))(
      ignored(source))))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Outriders: ignore all attack skulls", Vector.empty, Vector.empty))

  override def noteKeys: Vector[NoteKey] = notes.declared

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use =>
      Outriders.ignoreSkulls(use, id, PowerSourceRef.Card(cardId))))
