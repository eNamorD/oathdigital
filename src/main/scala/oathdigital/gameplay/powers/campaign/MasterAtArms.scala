package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignProcedure}
import oathdigital.gameplay.actions.dice.{DiceThrow, RerollChoice}
import oathdigital.gameplay.powerresolver.{Contribution, OfferReroll,
  PowerCtx}
import oathdigital.model._

object MasterAtArmsCard extends Denizen(DenizenId("249"), "Master at Arms",
    Suit.Order):
  val power = PrintedPower(PowerId("denizen.master-at-arms"),
    persistent = false, cost = Cost.free,
    text = "You may reroll any number of dice that show hollow swords " +
      "[hollow-sword] once.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Master at Arms (card 249), an attacker's battle plan: "You may reroll any
  * number of dice that show hollow swords once."
  *
  * It is free and does nothing when chosen. Once chosen, it offers a reroll
  * at the attack roll's roll step. Every hollow sword is the same face, so
  * it asks how many to reroll, from one to all of them, and rerolls the
  * first that many; with one it asks nothing. On a throw with no hollow
  * sword it is shown unavailable. The roll step stops offering it once it
  * is used, which is the "once".
  *
  * It writes "Rerolled {n} hollow swords.".
  */
final case class MasterAtArms private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = MasterAtArmsCard.id
  def id: PowerId = MasterAtArms.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = Vector(MasterAtArms.rerolled)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      MasterAtArms.label, Vector.empty, Vector.empty))

  override def otherContributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.Reroll -> Vector(OfferReroll(offer)))

  private def offer(ctx: PowerCtx): Option[RerollOffer] =
    RerollChoice.throwOf(ctx).filter(rolled =>
      rolled.pool == CampaignIds.attackPool &&
        rolled.roller == ctx.activePlayer &&
        PlanUse.chosen(ctx.state, PendingTree(ctx.nodePath, ctx.answered),
          ctx.activePlayer, cardRef, sides, afterOutcome = false).nonEmpty)
      .map(rolled => MasterAtArms.hollow(rolled) match
        case Vector() => RerollOffer(cardId, None, Vector.empty,
          Some(OptionReason.Written(MasterAtArms.noHollowSword)))
        case hollow => RerollOffer(cardId, None, steps(rolled, hollow)))

  private def note(amount: Int): Note = Note(id, _ =>
    Some(MasterAtArms.rerolled(PowerSourceRef.Card(cardId),
      NoteArg.Number(amount))))

  private def steps(rolled: DiceThrow, hollow: Vector[Int])
      : Vector[Operation] =
    if hollow.size == 1 then
      Vector(note(1), Reroll(rolled.pool, hollow))
    else Vector(
      Decide(MasterAtArms.decisionId, rolled.roller,
        DecisionQuery.ChooseAmount(1, hollow.size,
          Some(MasterAtArms.heading), "Reroll")),
      Branch((_, pending) => MasterAtArms.amount(pending).toVector
        .flatMap(amount => Vector(note(amount),
          Reroll(rolled.pool, hollow.take(amount))))))

object MasterAtArms:
  val id: PowerId = MasterAtArmsCard.power.id
  val decisionId: String = CampaignProcedure.decisionPrefix +
    "master-at-arms"
  val label: String = "Master at Arms: reroll hollow swords"
  val heading: String = "Reroll how many hollow swords?"
  val noHollowSword: String = "No die shows a hollow sword"
  /** "Rerolled {n} hollow swords." */
  val rerolled: NoteKey = NoteKey("rerolled", Vector(
    NotePart.Text("Rerolled "), NotePart.Arg(0),
    NotePart.Plural(0, " hollow sword.", " hollow swords.")))

  /** The indices of the throw's dice showing a hollow sword, in order. */
  def hollow(rolled: DiceThrow): Vector[Int] =
    rolled.indices.zip(rolled.faces).collect {
      case (index, AttackDieFace.HollowSword) => index }

  /** How many hollow swords the user chose to reroll. */
  private def amount(pending: PendingTree): Option[Int] =
    pending.answered.reverse.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseAmountAnswer(n), _) =>
        n
    }

  def forCatalog(catalog: ExecutableCatalog): MasterAtArms =
    new MasterAtArms(catalog)
