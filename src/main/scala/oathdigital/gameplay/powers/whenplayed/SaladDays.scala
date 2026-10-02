package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.PowerAnswers
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
  /** "Every favor bank was empty." */
  val empty: NoteKey = NoteKey("none", Vector(
    NotePart.Text("Every favor bank was empty.")))

  /** The banks that hold favor, in suit order. */
  private def stocked(ready: ReadyGame): Vector[Suit] =
    Suit.all.filter(suit => ready.banks.favor.getOrElse(suit, 0) > 0)

  private def gains(actor: PlayerId, suits: Vector[Suit])
      : Vector[CoreOperation] = suits.map(Gain.Favor(actor, _, Each))

  /** The three different stocked banks answered. */
  private def chosen(ready: ReadyGame, pending: PendingTree)
      : Either[OathViolation, Vector[Suit]] =
    val banks = stocked(ready)
    PowerAnswers.many(pending, decisionId)
      .toRight(PowerAnswers.missing(decisionId)).flatMap { refs =>
        val suits = refs.collect { case DecisionOptionRef.FavorBank(suit) =>
          suit }
        Either.cond(suits.size == refs.size && suits.distinct.size == Banks &&
          suits.forall(banks.contains), suits, OathViolation.InvalidEventOrder(
            "Salad Days takes from three different banks that hold favor"))
      }

  override def noteKeys: Vector[NoteKey] = Vector(empty)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(Branch((live, _) =>
      val banks = stocked(live)
      if banks.isEmpty then Vector(Note(id, _ =>
        Some(empty(PowerSourceRef.Card(cardId)))))
      else if banks.size <= Banks then Vector(BuildOps((ready, _) =>
        Right(gains(actor, stocked(ready)))))
      else Vector(
        Decide(decisionId, actor, DecisionQuery.ChooseMany(Banks, Banks,
          banks.map(suit => DecisionOption.FavorBank(
            DecisionOptionRef.FavorBank(suit))),
          heading = Some("Salad Days: choose three banks to gain 1 favor " +
            "from each"))),
        BuildOps((ready, pending) => chosen(ready, pending)
          .map(gains(actor, _))))))
