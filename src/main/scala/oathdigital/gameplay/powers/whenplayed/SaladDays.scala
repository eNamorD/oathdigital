package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.gameplay.powers.cardplay.FavorBankChoice
import oathdigital.model._

object SaladDaysCard extends Denizen(DenizenId("147"), "Salad Days", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.salad-days"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** gain [favor] [favor] [favor]- one each " +
      "from three different favor banks.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Salad Days (card 147), WHEN PLAYED: gain 3 favor, one each from three
  * different favor banks.
  *
  * The banks holding favor are read live, in suit order. With four or more,
  * the actor chooses three. With one to three, the actor gains 1 from each
  * and is not asked. The Gain lines name the banks, so the choice is
  * narrated and the power writes no line of its own, except when every bank
  * is empty.
  */
case object SaladDays extends WhenPlayedPower:
  val cardId: DenizenId = SaladDaysCard.id
  val id: PowerId = SaladDaysCard.power.id
  val decisionId: String = "cardplay.salad-days.banks"
  /** The banks it takes from. */
  val Banks: Int = 3
  /** The favor it takes from each. */
  val Each: Int = 1
  private val notes = NoteSet(id)
  /** "Every favor bank was empty." */
  val empty: NoteKey = notes.key("none", Vector(
    NotePart.Text("Every favor bank was empty.")))

  private def gains(actor: PlayerId, suits: Vector[Suit])
      : Vector[CoreOperation] = suits.map(Gain.Favor(actor, _, Each))

  /** The three different stocked banks answered. */
  private def chosen(ready: ReadyGame, pending: PendingTree)
      : Either[OathViolation, Vector[Suit]] =
    val banks = FavorBankChoice.stocked(ready)
    PowerAnswers.many(pending, decisionId)
      .toRight(PowerAnswers.missing(decisionId)).flatMap { refs =>
        val suits = refs.collect { case DecisionOptionRef.FavorBank(suit) =>
          suit }
        Either.cond(suits.size == refs.size && suits.distinct.size == Banks &&
          suits.forall(banks.contains), suits, OathViolation.InvalidEventOrder(
            "Salad Days takes from three different banks that hold favor"))
      }

  override def noteKeys: Vector[NoteKey] = notes.declared
  override def narratedDecisions: Set[String] = Set(decisionId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    Vector(Branch((live, _) =>
      val banks = FavorBankChoice.stocked(live)
      if banks.isEmpty then Vector(Note(id, _ =>
        Some(empty(PowerSourceRef.Card(cardId)))))
      else if banks.size <= Banks then Vector(BuildOps((ready, _) =>
        Right(gains(ctx.actor, FavorBankChoice.stocked(ready)))))
      else Vector(
        Decide(decisionId, ctx.actor, DecisionQuery.ChooseMany(Banks, Banks,
          banks.map(suit => DecisionOption.FavorBank(
            DecisionOptionRef.FavorBank(suit))),
          heading = Some("Salad Days: choose three banks to gain 1 favor " +
            "from each"),
          confirm = Some(CountedConfirm("Gain favor from", "bank",
            "banks")))),
        BuildOps((ready, pending) => chosen(ready, pending)
          .map(gains(ctx.actor, _))))))
