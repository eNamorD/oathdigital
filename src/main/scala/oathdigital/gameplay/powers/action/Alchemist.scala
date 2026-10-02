package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object AlchemistCard extends Denizen(DenizenId("9"), "Alchemist", Suit.Arcane) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.alchemist"),
    persistent = false, cost = Cost(secret = 1, secretBurnt = 1),
    text = "**ACTION:** Gain [favor] [favor] [favor] [favor] from any " +
      "favor bank or banks.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Alchemist (card 9), ACTION: place 1 secret on this card and burn 1, then
  * gain 4 favor from any bank or banks.
  *
  * The split is [[FavorSplit]]'s: the player chooses it only when two or
  * more banks hold favor and more than 4 is available in all.
  */
case object Alchemist extends PaidAction(AlchemistCard.power):
  val Favor: Int = 4
  val decisionId: String = "power.alchemist.banks"
  /** Its own line: the whole favor it gained. Each bank's Gain line stays. */
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  override def noteKeys: Vector[NoteKey] = Vector(gained)

  private val banks = new FavorSplit(decisionId, Suit.all, "Take favor")

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => banks.ask(live, player, Favor,
      "Alchemist: take 4 favor from any banks")),
    BuildOps((live, pending) => banks.split(live, pending, Favor).map(
      _.map { case (suit, n) => Gain.Favor(player, suit, n) })),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Favor,
      NoteSupport.favor)))))
