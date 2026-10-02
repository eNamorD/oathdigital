package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object RelicBreakerCard extends Denizen(DenizenId("139"), "Relic Breaker", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.relic-breaker"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** Bury a relic you hold to gain [secret].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Relic Breaker (card 139), ACTION: bury a relic you hold, faceup or
  * facedown, to gain 1 secret.
  *
  * Free. The relic is chosen as for Arcane Brokers ([[HeldRelicSpend]]) and
  * buried at the bottom of the relic deck with the standard returns: its
  * secrets go back to the player facedown. Its line names the relic and the
  * secret gained, not those returned, and covers the generic Buried and gain
  * lines. With no relic held the line says so. Holding only the Grand
  * Scepter, it writes nothing.
  */
case object RelicBreaker extends PaidAction("denizen.relic-breaker",
    Cost.free):
  val decisionId: String = "power.relic-breaker.relic"
  val Gained: Int = 1
  /** "{Red} buried {relic} and gained {n secrets}." */
  val buried: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" buried "), NotePart.Arg(1),
    NotePart.Text(" and gained "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} held no relic." */
  val bare: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" held no relic.")))
  override def noteKeys: Vector[NoteKey] = Vector(buried, bare)
  override def narratedDecisions: Set[String] = Set(decisionId)

  private val relic = new HeldRelicSpend(decisionId,
    "Relic Breaker: bury a relic to gain a secret", (_, player, held) =>
      Bury.standard(BuryableCard.Relic(held.id),
        PositionedLocation(Location.PlayArea(player)), None, 0,
        held.tokens.secrets, player) :+ Gain.Secrets(player, Gained))

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(
    relic.steps(player) :+ Note(id, buryNote(_, player, source),
      covers = true)))

  private def buryNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      relic.spentRelic(states, player) match
        case Some((spent, tokens)) => states.previous.map(step =>
          buried(card, NoteArg.Player(player), NoteArg.Card(spent),
            NoteArg.Amount(NoteSupport.secrets(step, player) - tokens.secrets,
              NoteUnit.Secret)))
        case None => Option.when(relic.emptyHanded(states, player))(
          bare(card, NoteArg.Player(player))))
