package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignBattle
import oathdigital.model._

object LancersCard extends Denizen(DenizenId("154"), "Lancers", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.lancers"),
    persistent = false, cost = Cost.free,
    text = "Double your total attack roll. At end, discard Lancers.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Lancers (card 154), an attacker's battle plan: "Double your total attack
  * roll. At end, discard Lancers."
  *
  * It is free. Once the attack is scored, the count of every face rolled is
  * doubled, skulls included, before the skull cap (`AttackScore`); the
  * sacrifice is added later and is never doubled. The card is discarded
  * through the standard discard once the Campaign has resolved, whoever won,
  * as Horse Archers is.
  *
  * When the attack rolled a die it writes "The attack roll was doubled."
  */
final case class Lancers private (catalog: ExecutableCatalog)
    extends BattlePlan with DiscardedAtEnd:
  val cardId: DenizenId = LancersCard.id
  def id: PowerId = Lancers.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def planNoteKeys: Vector[NoteKey] = Lancers.ownNoteKeys

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Lancers: double the attack roll", Vector.empty, Vector.empty))

  override def otherWindows
      : Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use => Vector(
      AttackScore.rescore(use.actor),
      Note(id, states => Option.when(
        CampaignBattle.attackFacesOf(states.now).nonEmpty)(
        Lancers.doubled(PowerSourceRef.Card(cardId)))))))

object Lancers:
  val id: PowerId = LancersCard.power.id
  private val notes = NoteSet(id)
  /** This plan's own keys. Its `noteKeys` adds the discard's
    * line, which `DiscardedAtEnd` owns, not this object. */
  def ownNoteKeys: Vector[NoteKey] = notes.declared
  /** "The attack roll was doubled." */
  val doubled: NoteKey = notes.key("doubled",
    Vector(NotePart.Text("The attack roll was doubled.")))

  def forCatalog(catalog: ExecutableCatalog): Lancers = new Lancers(catalog)
