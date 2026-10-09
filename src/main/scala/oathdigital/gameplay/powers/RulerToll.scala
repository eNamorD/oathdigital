package oathdigital.gameplay.powers

import oathdigital.gameplay.SiteRulers
import oathdigital.model._

/** The toll Curfew and Forced Labor charge an enemy of their ruler, on top
  * of the action's own cost: 1 favor given to the ruling player, or burnt
  * when bandits rule. The rule card's ruler is the ruler of the site holding
  * it faceup; Empire rulers are not supported.
  *
  * The payment is required, so an enemy who cannot pay is refused. Toll
  * Roads charges the same toll for Travel, with lines that name the
  * traveller.
  */
private[powers] object RulerToll:
  val Favor: Int = 1
  /** "Gave {1 favor} to {Blue}." */
  val gave: NoteKey = NoteKey("gave", Vector(NotePart.Text("Gave "),
    NotePart.Arg(0), NotePart.Text(" to "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "Burned {1 favor}.", when bandits rule. */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Text("Burned "),
    NotePart.Arg(0), NotePart.Text(".")))
  val keys: Vector[NoteKey] = Vector(gave, burned)

  /** The ruler of the site holding `card` faceup, when `payer` is its
    * enemy. */
  def enemyRuler(ready: ReadyGame, card: DenizenId, payer: PlayerId)
      : Option[SiteRuler] =
    SiteRulers.rulerOfCard(ready, card).filter(ruler =>
      SiteRule.enemies(ruler, SiteRuler.Player(payer)))

  /** The paying option of a toll step, "Give 1 favor to {owner}",
    * unavailable with what it needs when `payer` holds less than the toll.
    * The step is asked before any of its cost is paid, so the favor held
    * now is the favor the payment will find. */
  def payOption(ready: ReadyGame, payer: PlayerId, owner: PlayerId)
      : DecisionOption =
    val option = DecisionOption.Button(PayOrAsk.pay,
      s"Give $Favor favor to ${owner.value}")
    val held = ready.game.current.players.find(_.player == payer)
      .fold(0)(_.board.favor)
    if held >= Favor then option
    else DecisionOption.Unavailable(option,
      OptionReason.Written(s"Needs $Favor favor; you have $held"))

  /** The required payment from `payer` to `ruler`. */
  def payment(payer: PlayerId, ruler: SiteRuler): CoreOperation = ruler match
    case SiteRuler.Player(owner) => Give(Piece.Favor(Favor), payer,
      Location.PlayArea(payer), Location.PlayArea(owner), required = true)
    case _ => PayCost(payer, Location.SharedBank, Cost(favorBurnt = Favor))

  /** The line, from what the payment's step took from `payer`. */
  def note(card: DenizenId, payer: PlayerId, ruler: SiteRuler)(
      states: NoteStates): Option[PowerNote] = for
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
