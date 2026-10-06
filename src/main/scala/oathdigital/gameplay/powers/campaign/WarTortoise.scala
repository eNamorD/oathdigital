package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.model._

object WarTortoiseCard extends Denizen(DenizenId("187"), "War Tortoise", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.war-tortoise"),
    persistent = false, cost = Cost(favor = 1),
    text = "**Ignore** all attack or defense added by your enemy's " +
      "rolls of [sword] [sword] [skull] or [shield] [shield]. " +
      "_(Any [skull] they roll still kill their warbands.)_")
  val powers: Vector[PrintedPower] = Vector(power)

/** War Tortoise (card 187), a battle plan for either side: "[favor] Ignore
  * all attack or defense added by your enemy's rolls of two swords and a
  * skull, or two shields. (Any skull they roll still kills their warbands.)"
  *
  * A favor is placed onto the card. Used by the defender, each of the
  * attacker's skull faces scores nothing, Fearsome General's four swords
  * included, and payable skulls still kill (`AttackScore`). Used by the
  * attacker, each of the defender's double shields scores nothing, before
  * the doublers (`DefenseScore`). Bandits pay nothing, so a bandit defender
  * never applies it.
  *
  * It writes "Double swords added no attack." when a skull face was rolled
  * against it, and "Double shields added no defense." when a double shield
  * was.
  */
case object WarTortoise extends BattlePlan:
  val cardId: DenizenId = WarTortoiseCard.id
  val id: PowerId = WarTortoiseCard.power.id
  /** "Double swords added no attack." */
  val attack: NoteKey = NoteKey("attack",
    Vector(NotePart.Text("Double swords added no attack.")))
  /** "Double shields added no defense." */
  val defense: NoteKey = NoteKey("defense",
    Vector(NotePart.Text("Double shields added no defense.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] =
    Vector(WarTortoise.attack, WarTortoise.defense)

  private def label(side: CampaignPlanSide): String = side match
    case CampaignPlanSide.Attacker =>
      "War Tortoise: double shields add no defense"
    case CampaignPlanSide.Defender =>
      "War Tortoise: double swords add no attack"

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      label(context.side), Vector(CampaignPlanCost.Favor(1)), Vector.empty))

  /** The card its lines name. `BattlePlan.source` is the rule source, so
    * this takes another name. */
  private def lineSource: PowerSourceRef = PowerSourceRef.Card(cardId)

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use =>
      if use.side != CampaignPlanSide.Defender then Vector.empty
      else Vector(AttackScore.rescore(use.actor),
        Note(id, states => Option.when(AttackScore.skullRolled(states.now))(
          WarTortoise.attack(lineSource))))),
    PowerWindow.CampaignDefenseResult -> (use =>
      if use.side != CampaignPlanSide.Attacker then Vector.empty
      else Vector(DefenseScore.rescore(use.actor),
        Note(id, states => Option.when(DefenseScore.faces(states.now)
          .contains(DefenseDieFace.TwoShields))(
          WarTortoise.defense(lineSource))))))
