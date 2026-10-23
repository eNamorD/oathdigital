package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{NoteSupport, PowerAccess, SpitefulMirrorCard}
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

/** Spiteful Mirror (relic R41), a selected Trade and Muster modifier:
  * "[secret] This turn, you may act as if any cards you use to trade or
  * muster are [suit-discord] cards." (catalog batch 8 rulings, slice 8).
  *
  * Picked, it turns the source card Discord: `EffectiveSuit.traded` reads
  * the pick in the walk's answers, so the base gain, its matching advisers
  * and every reader of the source see Discord. The power itself only
  * prices the pick and writes the line.
  *
  * Its first pick in a turn pays 1 secret onto the relic and records the
  * use (`useRef`); later picks that turn are free. It is offered only while
  * its player holds the relic.
  *
  * Its line comes once the source is chosen, before the cost: "Acting as if
  * {card} is a Discord card."
  */
final case class SpitefulMirror private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  import SpitefulMirror._

  val cardId: RelicId = SpitefulMirrorCard.id
  def id: PowerId = SpitefulMirror.id
  def actions: Set[MajorActionType] =
    Set(MajorActionType.Trade, MajorActionType.Muster)
  override def cost: Cost = SpitefulMirrorCard.power.cost
  override def noteKeys: Vector[NoteKey] = SpitefulMirror.noteKeys

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeCost -> Vector(Transform((_, children) =>
      line(TradeProcedure.decisionId) +: children)),
    PowerWindow.MusterCost -> Vector(Transform((_, children) =>
      line(MusterProcedure.decisionId) +: children)))

  override def selectable(ready: ReadyGame, actor: PlayerId): Boolean =
    if used(ready) then PowerAccess.locate(ready, actor, cardId).isDefined
    else super.selectable(ready, actor)

  override def selectionPayments(ready: ReadyGame, actor: PlayerId)
      : Vector[CoreOperation] =
    if used(ready) then Vector.empty
    else Vector(payment(actor), RecordPowerUse(useRef))

  private def used(ready: ReadyGame): Boolean =
    ready.game.current.turn.usedPowers.contains(useRef)

  /** "Acting as if {card} is a Discord card.", naming the answer to the
    * action's source decision `decisionId`. */
  private def line(decisionId: String): Operation = Note(id, states =>
    NoteSupport.answer(states, decisionId).collect {
      case DecisionOptionRef.Denizen(card) => card: CardId
      case DecisionOptionRef.Edifice(card) => card: CardId
    }.map(card => acting(PowerSourceRef.Card(cardId), NoteArg.Card(card))))

object SpitefulMirror:
  val id: PowerId = SpitefulMirrorCard.power.id
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** The record of this turn's payment. */
  val useRef: PowerUseRef = PowerUseRef(PowerTiming.Act,
    PowerSourceRef.Card(SpitefulMirrorCard.id), id)
  /** "Acting as if {card} is a Discord card." */
  val acting: NoteKey = notes.key("acting", Vector(
    NotePart.Text("Acting as if "), NotePart.Arg(0),
    NotePart.Text(" is a Discord card.")))

  def forCatalog(catalog: ExecutableCatalog): SpitefulMirror =
    new SpitefulMirror(catalog)
