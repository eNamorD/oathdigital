package oathdigital.gameplay.powers.recover

import oathdigital.catalog.{ExecutableCatalog, PrintedPower, Relic}
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseManyAnswer

object SilverCharmCard extends Relic(RelicId("R31"), "Silver Charm", value = 37, defense = 1):
  val power = PrintedPower(PowerId("relic.silver-charm"),
    persistent = false, cost = Cost.free,
    text = "Each time you spend Supply to roll [defense-die], you may " +
      "also reroll any number of previous [defense-die] at the same " +
      "time.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Silver Charm (relic R31), a selected Recover modifier: "Each time you
  * spend Supply to roll [defense-die], you may also reroll any number of
  * previous [defense-die] at the same time." The card misprints
  * `[attack-die]` (product owner, 2026-10-09).
  *
  * It hooks each Recover roll at `RecoverRoll`. On a payment after the
  * first, before the roll, it asks which earlier dice to reroll, one option
  * per die; the first payment has none, so nothing is asked. The chosen
  * dice are rerolled right after the two new dice are rolled, so the throw
  * holds them all and a reroll power that follows (Jinx) rerolls every one.
  * There is no once-limit: a die may be rerolled on every payment.
  *
  * Its line names the chosen dice as they showed, "Rerolled blank,
  * shield.", and covers the roll's own line; the throw restated after the
  * reroll shows every die. Choosing no die changes nothing and writes
  * nothing.
  */
final case class SilverCharm private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: RelicId = SilverCharmCard.id
  def id: PowerId = SilverCharm.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Recover)
  override def noteKeys: Vector[NoteKey] = SilverCharm.noteKeys
  override def narratedDecisions: Set[String] = Set(SilverCharm.decisionId)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.RecoverRoll -> Vector(Transform((ctx, operations) =>
      choice(ctx.activePlayer) +: operations :+ reroll)))

  /** Which earlier dice to reroll, on a payment after the first. */
  private def choice(actor: PlayerId): Operation = Branch((ready, _) =>
    val earlier = SilverCharm.earlier(ready)
    if earlier.isEmpty then Vector.empty
    else Vector(Decide(SilverCharm.decisionId, actor,
      DecisionQuery.ChooseMany(0, earlier.size, earlier.map((index, face) =>
        DecisionOption.Button(SilverCharm.die(index),
          SilverCharm.label(index, face))),
        heading = Some(SilverCharm.heading),
        summary = Some(SilverCharm.summary),
        confirm = Some(CountedConfirm("Reroll", "die", "dice",
          Some("Keep the roll")))))))

  /** The chosen dice, rerolled after the roll, and the line naming them.
    * The `Reroll` is a leaf, never inside a `BuildOps`, so the walker
    * fills its faces. */
  private def reroll: Operation = Branch((_, pending) =>
    val chosen = SilverCharm.chosen(pending)
    if chosen.isEmpty then Vector.empty
    else Vector(Note(id, states => named(states.now, chosen), covers = true),
      Reroll(RecoverProcedure.recoverPool, chosen)))

  /** "Rerolled {the chosen dice}.", read before the reroll changes them. */
  private def named(ready: ReadyGame, chosen: Vector[Int])
      : Option[PowerNote] =
    ready.game.current.rollOutcomes.get(RecoverProcedure.recoverPool)
      .map(outcome => SilverCharm.rerolled(PowerSourceRef.Card(cardId),
        NoteArg.Dice(chosen.flatMap(outcome.faces.lift))))

object SilverCharm:
  val id: PowerId = SilverCharmCard.power.id
  /** Asked on every payment after the first. Each answer is read back by
    * the walk that gives it, as Recover's continue-or-stop decision is. */
  val decisionId: String = RecoverProcedure.decisionPrefix + "silver-charm"
  val heading: String = "Reroll earlier dice with this roll"
  /** "Reroll 2 dice". */
  val summary: DecisionSummary = DecisionSummary("Reroll", "die", "dice")
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Rerolled {dice}." */
  val rerolled: NoteKey = notes.key("rerolled", Vector(
    NotePart.Text("Rerolled "), NotePart.Arg(0), NotePart.Text(".")))
  private val DiePrefix = "die-"

  /** The option naming the earlier die at `index` of the Recover pool. */
  def die(index: Int): DecisionOptionRef.Button =
    DecisionOptionRef.Button(s"$DiePrefix$index")

  /** "Die 1: shield". */
  def label(index: Int, face: DefenseDieFace): String =
    s"Die ${index + 1}: ${word(face)}"

  /** A face as a roll line words it. */
  private def word(face: DefenseDieFace): String = face match
    case DefenseDieFace.Blank => "blank"
    case DefenseDieFace.OneShield => "shield"
    case DefenseDieFace.TwoShields => "two shields"
    case DefenseDieFace.Doubler => "doubler"

  /** The Recover pool's faces so far, with their indices: the dice a
    * payment may reroll with its roll. */
  private def earlier(ready: ReadyGame): Vector[(Int, DefenseDieFace)] =
    ready.game.current.rollOutcomes.get(RecoverProcedure.recoverPool)
      .fold(Vector.empty[(Int, DefenseDieFace)])(_.faces.zipWithIndex
        .collect { case (face: DefenseDieFace, index) => index -> face })

  /** The dice the answer just given chose, in index order; none when the
    * last answer is not this decision's. */
  private def chosen(pending: PendingTree): Vector[Int] =
    pending.answered.lastOption.collect {
      case Answered(`decisionId`, ChooseManyAnswer(selected), _) =>
        selected.collect {
          case DecisionOptionRef.Button(key) if key.startsWith(DiePrefix) =>
            key.drop(DiePrefix.length).toIntOption
        }.flatten.distinct.sorted
    }.getOrElse(Vector.empty)

  def forCatalog(catalog: ExecutableCatalog): SilverCharm =
    new SilverCharm(catalog)
