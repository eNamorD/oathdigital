package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PhasePower, PowerCtx, Restriction}
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts}
import oathdigital.gameplay.powers.cardplay.FavorBankChoice
import oathdigital.gameplay.PowerAccess
import oathdigital.model._

object VowOfPovertyCard extends Denizen(DenizenId("193"), "Vow of Poverty", Suit.Beast) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-poverty"),
    persistent = false, cost = Cost.free,
    text = "You **cannot** gain [favor] from Trade. **REST:** If you " +
      "have no [favor], take [favor] [favor] from any one favor " +
      "bank.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Vow of Poverty (card 193, adviser-only, locked): "You cannot gain favor
  * from Trade. REST: If you have no favor, take 2 favor from any one favor
  * bank."
  *
  * The rule is a `Restriction` on the Trade's gain node, which refuses a gain
  * of favor for a faceup holder. The node exists once the source is
  * answered, and the restriction look-ahead probes each source with its
  * answer, so every source of a Trade for favor is hidden and that Trade is
  * not offered. Favor a Trade modifier grants, such as Moving Market's, is
  * gained at the Trade's root, outside the node. The hidden sources write no
  * line.
  *
  * The REST is a [[PhasePower]], as Vow of Obedience's is, usable while the
  * holder's board holds no favor and some bank holds favor. It takes 2 favor
  * from the bank the holder chooses, or what that bank holds, and writes
  * "Took {n favor} from {the Order bank}.", covering the generic line. The
  * catalog marks the power `persistent: false` because of its REST, so the
  * rule keeps the default automatic resolution.
  */
case object VowOfPoverty extends PhasePower with ContributingPower:
  val cardId: DenizenId = VowOfPovertyCard.id
  val id: PowerId = VowOfPovertyCard.power.id
  /** What the REST takes, or what the chosen bank holds. */
  val Taken: Int = 2
  private val notes = NoteSet(id)
  /** "Took {n favor} from {the Order bank}." */
  val took: NoteKey = notes.key(NoteKey.Used, Vector(NotePart.Text("Took "),
    NotePart.Arg(0), NotePart.Text(" from "), NotePart.Arg(1),
    NotePart.Text(".")))

  def choiceDecisionId(ready: ReadyGame, player: PlayerId): String =
    s"vow-of-poverty-${ready.game.current.tracks.round}-${player.value}"

  def timing: PowerTiming = PowerTiming.Rest
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] = notes.declared

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeGain -> Vector(Restriction((ctx, _) =>
      Option.when(holds(ctx.state, ctx.actor) && gainsFavor(ctx))(
        OathViolation.NoPlayableOption(ActionRef.Trade.key)))))

  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean =
    PlayerFacts.player(ready, player).exists(_.board.favor == 0) &&
      FavorBankChoice.stocked(ready).nonEmpty

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    val choice = choiceDecisionId(ready, player)
    Right(Sequence(Vector(
      Branch((state, _) => FavorBankChoice.take(state, player, Taken, choice,
        "Vow of Poverty: take 2 favor from a bank")),
      Note(id, tookNote(source, player), covers = true))))

  /** Whether the hooked gain node gives the trader favor. */
  private def gainsFavor(ctx: PowerCtx): Boolean =
    ctx.operation.children.exists {
      case Gain.Favor(player, _, _) => player == ctx.actor
      case _ => false
    }

  /** Whether `player` holds this card as a faceup adviser. */
  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    PowerAccess.holdsFaceupAdviser(ready, player, cardId)

  /** The favor `player` took from one bank in the step before the note. */
  private def tookNote(source: DecisionOptionRef, player: PlayerId)(
      states: NoteStates): Option[LogNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    amount = NoteSupport.favor(step, player)
    if amount > 0
    bank <- NoteSupport.bankPaid(step)
  yield took(card, NoteArg.Amount(amount, NoteUnit.Favor), NoteArg.Bank(bank))
