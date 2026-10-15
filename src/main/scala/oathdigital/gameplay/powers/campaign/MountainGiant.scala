package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignProcedure, PlanAmounts}
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

object MountainGiantCard extends Denizen(DenizenId("155"), "Mountain Giant",
    Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.mountain-giant"),
    persistent = false, cost = Cost(secret = 1),
    text = "±1 [attack-die] or ±3 [attack-die] At end, discard.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Mountain Giant (card 155), a battle plan for either side: "±1
  * [attack-die] or ±3 [attack-die] At end, discard." The card leaves out
  * the second icon; it is an attack die (product owner).
  *
  * It costs a secret, placed onto the card. Its user then chooses one or
  * three: an attacker adds that many attack dice, a defender removes them.
  * The card is discarded through the standard discard once the Campaign
  * has resolved, only when three was chosen (FAQ). A bandit defender pays
  * nothing, so it never applies the plan.
  *
  * It writes "Added {n} attack dice." ("Removed" for a defender), and the
  * discard line when it is discarded.
  */
final case class MountainGiant private (catalog: ExecutableCatalog)
    extends BattlePlan with DiscardedAtEnd:
  val cardId: DenizenId = MountainGiantCard.id
  def id: PowerId = MountainGiant.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def planNoteKeys: Vector[NoteKey] = MountainGiant.ownNoteKeys

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    val side = context.side
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      MountainGiant.label(side), Vector(CampaignPlanCost.Secret(1)),
      Vector.empty, amount = Some(PlanAmounts.oneOf(MountainGiant.decisionId,
        MountainGiant.heading(side), Vector(1, MountainGiant.Three).map(
          dice => dice -> MountainGiant.choice(side, dice)))(dice => Vector(
        PlanDice.effect(side, dice),
        CampaignPlanEffect.Run(Vector(note(side, dice))))))))

  /** "Added {n} attack dice.", or "Removed" for a defender. */
  private def note(side: CampaignPlanSide, dice: Int): Note =
    val key = side match
      case CampaignPlanSide.Attacker => MountainGiant.added
      case CampaignPlanSide.Defender => MountainGiant.removed
    Note(id, _ => Some(key(PowerSourceRef.Card(cardId),
      NoteArg.Number(dice))))

  override def endDiscard: PlanUse => Vector[Operation] = use =>
    Vector(Branch((_, pending) =>
      if PowerAnswers.one(pending, MountainGiant.decisionId).contains(
          PlanAmounts.button(MountainGiant.Three)) then
        PlanDiscard.afterCampaign(catalog, id, use, cardId)
      else Vector.empty))

object MountainGiant:
  val id: PowerId = MountainGiantCard.power.id
  /** The dice that discard the card after the Campaign. */
  val Three: Int = 3
  /** Under the Campaign's prefix, so a parked question is a Campaign
    * decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix +
    "mountain-giant"
  private val notes = NoteSet(id)
  /** This plan's own keys. Its `noteKeys` adds the discard's
    * line, which `DiscardedAtEnd` owns, not this object. */
  def ownNoteKeys: Vector[NoteKey] = notes.declared
  /** "Added {n} attack dice." */
  val added: NoteKey = notes.key("added", Vector(NotePart.Text("Added "),
    NotePart.Arg(0), NotePart.Plural(0, " attack die.", " attack dice.")))
  /** "Removed {n} attack dice." */
  val removed: NoteKey = notes.key("removed", Vector(
    NotePart.Text("Removed "), NotePart.Arg(0),
    NotePart.Plural(0, " attack die.", " attack dice.")))

  /** "Mountain Giant: add 1 or 3 attack dice" */
  def label(side: CampaignPlanSide): String =
    s"Mountain Giant: ${verb(side)} 1 or $Three attack dice"

  /** "Mountain Giant: add 1 or 3 attack dice?" */
  def heading(side: CampaignPlanSide): String = s"${label(side)}?"

  /** "Add 1 attack die"; "Add 3 attack dice, and discard Mountain Giant
    * after the Campaign". */
  def choice(side: CampaignPlanSide, dice: Int): String =
    val noun = if dice == 1 then "attack die" else "attack dice"
    val words = s"${verb(side).capitalize} $dice $noun"
    if dice == Three then
      s"$words, and discard Mountain Giant after the Campaign"
    else words

  private def verb(side: CampaignPlanSide): String = side match
    case CampaignPlanSide.Attacker => "add"
    case CampaignPlanSide.Defender => "remove"

  def forCatalog(catalog: ExecutableCatalog): MountainGiant =
    new MountainGiant(catalog)
