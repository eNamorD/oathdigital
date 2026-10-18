package oathdigital.gameplay.powers

import oathdigital.gameplay.SiteRulers
import oathdigital.model._

/** The toll Curfew and Forced Labor charge an enemy of their ruler, on top
  * of the action's own cost: 1 favor given to the ruling player, or burnt
  * when bandits rule. The rule card's ruler is the ruler of the site holding
  * it faceup; Empire rulers are not supported.
  *
  * The payment is required, so an enemy who cannot pay, and is not let off,
  * is refused. When a player rules the card, the payer is asked first to
  * pay or to go on free, which that ruler may consent to ([[PayOrAsk]]).
  * The payment runs after the rest of the cost either way. Toll Roads
  * charges the same toll for Travel, with lines that name the traveller.
  */
private[powers] object RulerToll:
  val Favor: Int = 1
  /** The keys this object owns, which no one power owns. A power that
    * writes one includes this set. */
  val notes: NoteSet = NoteSet.unowned("RulerToll")
  /** "Gave {1 favor} to {Blue}." */
  val gave: NoteKey = notes.key("gave", Vector(NotePart.Text("Gave "),
    NotePart.Arg(0), NotePart.Text(" to "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "Burned {1 favor}.", when bandits rule. */
  val burned: NoteKey = notes.key("burned", Vector(NotePart.Text("Burned "),
    NotePart.Arg(0), NotePart.Text(".")))
  /** "Gave nothing, with {Blue}'s consent.", when the ruler waived it. */
  val waived: NoteKey = notes.key("waived", Vector(
    NotePart.Text("Gave nothing, with "), NotePart.Arg(0),
    NotePart.Text("'s consent.")))
  /** Every key this toll writes, derived from the set rather than
    * relisted, so a key added above travels to every borrower. */
  def keys: Vector[NoteKey] = notes.declared

  /** `operations` with what `payer` owes `ruler` for `step`'s card on
    * `occasion`: the step when a player rules it, the payment and its line
    * under bandits, run after the rest of the cost either way. */
  def owed(step: PayOrAsk, ready: ReadyGame, payer: PlayerId,
      ruler: SiteRuler, operations: Vector[Operation], occasion: String,
      heading: String, freeLabel: String): Vector[Operation] =
    val paid = Vector(payment(payer, ruler),
      Note(step.power, note(step.card, payer, ruler)))
    ruler match
      case SiteRuler.Player(owner) => step.around(operations, payer,
        occasion, heading,
        payOption(ready, payer, owner, favorTaken(operations, payer)),
        freeLabel, paid,
        Vector(Note(step.power, _ => Some(waived(step.source,
          NoteArg.Player(owner))))))
      case _ => operations ++ paid

  /** The ruler of the site holding `card` faceup, when `payer` is its
    * enemy. */
  def enemyRuler(ready: ReadyGame, card: DenizenId, payer: PlayerId)
      : Option[SiteRuler] =
    SiteRulers.rulerOfCard(ready, card).filter(ruler =>
      SiteRule.enemies(ruler, SiteRuler.Player(payer)))

  /** The paying option of a toll step, "Give 1 favor to {owner}",
    * unavailable with what it needs when `payer` holds less than the toll
    * and `taken`, the favor the rest of the cost takes first. The step is
    * asked before any of its cost is paid, so the favor held now is the
    * favor the cost will find. */
  def payOption(ready: ReadyGame, payer: PlayerId, owner: PlayerId,
      taken: Int = 0): DecisionOption =
    val option = DecisionOption.Button(PayOrAsk.pay,
      s"Give $Favor favor to ${owner.value}")
    val held = ready.game.current.players.find(_.player == payer)
      .fold(0)(_.board.favor)
    val needed = Favor + taken
    if held >= needed then option
    else DecisionOption.Unavailable(option,
      OptionReason.Written(s"Needs $needed favor; you have $held"))

  /** The favor `operations` take from `payer` as they stand: a favor given,
    * or a cost's favor paid or burnt. A batch built when it runs is not
    * read. */
  def favorTaken(operations: Vector[Operation], payer: PlayerId): Int =
    operations.collect {
      case Give(Piece.Favor(amount), giver, _, _, _) if giver == payer =>
        amount
      case PayCost(player, _, cost, _, _, _) if player == payer =>
        cost.favor + cost.favorBurnt
    }.sum

  /** The required payment from `payer` to `ruler`. */
  def payment(payer: PlayerId, ruler: SiteRuler): CoreOperation = ruler match
    case SiteRuler.Player(owner) => Give(Piece.Favor(Favor), payer,
      Location.PlayArea(payer), Location.PlayArea(owner), required = true)
    case _ => PayCost(payer, Location.SharedBank, Cost(favorBurnt = Favor))

  /** The line, from what the payment's step took from `payer`. */
  def note(card: DenizenId, payer: PlayerId, ruler: SiteRuler)(
      states: NoteStates): Option[LogNote] = for
    step <- states.previous
    paid = -NoteSupport.favor(step, payer)
    if paid > 0
  yield
    val source = PowerSourceRef.Card(card)
    val amount = NoteArg.Amount(paid, NoteUnit.Favor)
    ruler match
      case SiteRuler.Player(owner) =>
        gave(source, amount, NoteArg.Player(owner))
      case _ => burned(source, amount)
