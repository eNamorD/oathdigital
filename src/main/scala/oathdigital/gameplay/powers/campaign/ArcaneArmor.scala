package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignIds,
  CampaignProcedure, PlanAmounts}
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

object ArcaneArmorCard extends Denizen(DenizenId("206"), "Arcane Armor",
    Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.arcane-armor"),
    persistent = false, cost = Cost.free,
    text = "X [secret] Set X [defense-die] you have collected to their " +
      "[shield][shield] face instead of rolling them.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Arcane Armor (card 206), a defender's battle plan: "X [secret] Set X
  * [defense-die] you have collected to their [shield][shield] face instead
  * of rolling them."
  *
  * Its user chooses X, from one to the smaller of their faceup secrets and
  * the dice in the defense pool, counted when the question is asked, after
  * any surcharge has taken its secret, and pays X secrets onto the card, which
  * settle at once because a defender pays off turn. With no X possible it
  * is shown unavailable. A bandit defender has no secrets, so it never
  * applies the plan.
  *
  * At the defense roll, X dice are set to double shields and the rest are
  * rolled. If the pool holds fewer than X by then, only as many as it holds
  * are set, and the secrets stay spent. The set dice are no part of the
  * roll's throw, so no reroll changes them.
  *
  * It writes "Set {n} defense dice to double shields." at the roll.
  */
case object ArcaneArmor extends BattlePlan:
  val cardId: DenizenId = ArcaneArmorCard.id
  val id: PowerId = ArcaneArmorCard.power.id
  /** Under the Campaign's prefix, so a parked question is a Campaign
    * decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix + "arcane-armor"
  val heading: String = "Arcane Armor: set how many defense dice to " +
    "double shields, for a secret each?"
  val confirm: String = "Pay and set"
  val noSecret: String = "Needs 1 secret; you have 0"
  val noDefenseDice: String = "No defense dice to set"
  private val notes = NoteSet(id)
  /** "Set {n} defense dice to double shields." */
  val set: NoteKey = notes.key("set", Vector(NotePart.Text("Set "),
    NotePart.Arg(0), NotePart.Plural(0, " defense die to double shields.",
      " defense dice to double shields.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = notes.declared

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map { source =>
      val secrets = context.user.fold(0)(faceUpSecrets(context.ready, _))
      val dice = pool(context.ready)
      val offer = CampaignPlanOffer(source,
        "Arcane Armor: set defense dice to double shields", Vector.empty,
        Vector.empty)
      if secrets < 1 then
        offer.copy(withheld = Some(OptionReason.Written(noSecret)))
      else if dice < 1 then
        offer.copy(withheld = Some(OptionReason.Written(noDefenseDice)))
      else offer.copy(amount = Some(PlanAmounts.rangeBy(decisionId, bounds,
        heading, confirm, secretsEach = 1)(_ => Vector.empty)))
    }

  /** X runs from one to the smaller of the secrets left to pay with and the
    * dice in the pool, when the question is asked: a surcharge on the plan
    * (Gleaming Armor) has taken its secret by then. */
  private def bounds(ready: ReadyGame, user: PlayerId)
      : Either[OathViolation, (Int, Int)] =
    val secrets = faceUpSecrets(ready, user)
    if secrets < 1 then Left(OathViolation.InsufficientSecrets(1, secrets))
    else Right(1 -> math.min(secrets, pool(ready)))

  private def faceUpSecrets(ready: ReadyGame, user: PlayerId): Int =
    ready.game.current.players.find(_.player == user)
      .fold(0)(_.board.faceUpSecrets)

  override def wrapping: Map[PowerWindow, BattlePlan.Wrap] = Map(
    PowerWindow.CampaignDefenseRoll -> ((_, _, children) =>
      setDice +: children))

  /** X dice set to double shields, as many as the pool holds now, and the
    * line saying how many. */
  private def setDice: Operation = Branch((ready, pending) =>
    PowerAnswers.amount(pending, decisionId).map(math.min(_, pool(ready)))
      .filter(_ > 0).toVector.flatMap(count => Vector[Operation](
        SetDieFaces(CampaignIds.defensePool,
          Vector.fill(count)(DefenseDieFace.TwoShields)),
        Note(id, _ => Some(set(PowerSourceRef.Card(cardId),
          NoteArg.Number(count)))))))

  /** The dice the defense pool holds. */
  private def pool(ready: ReadyGame): Int =
    ready.game.current.rollPools.get(CampaignIds.defensePool).fold(0)(_.count)
