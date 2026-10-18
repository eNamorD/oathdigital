package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.operations.SupplyStrip
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts,
  SelectedModifier}
import oathdigital.model._

object AwaitedReturnCard extends Denizen(DenizenId("150"), "Awaited Return", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.awaited-return"),
    persistent = false, cost = Cost.free,
    text = "Spend no Supply if you sacrifice one warband from your " +
      "board.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Awaited Return (card 150), a selected Trade modifier: the Trade spends no
  * Supply, and one warband from the player's board is sacrificed at its cost
  * step. The printed card's action icon is Trade.
  *
  * The Trade's cost node loses the player's Supply payment and gains a
  * required batch that kills one warband from the board. It is selectable
  * only with a warband on the board, so the kill always has one. It folds
  * before the Cup of Plenty, which then finds no payment to waive.
  *
  * Its line, "Spent no Supply after sacrificing {1} warband.", reads the
  * kill's step. No generic line exists for a kill.
  */
final case class AwaitedReturn private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = AwaitedReturnCard.id
  def id: PowerId = AwaitedReturn.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)
  override def noteKeys: Vector[NoteKey] = AwaitedReturn.noteKeys

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeCost -> Vector(Transform((ctx, operations) =>
      SupplyStrip(ctx, ctx.actor, operations) ++ Vector(
        sacrifice(ctx.actor),
        Note(id, sacrificedNote(_, ctx.actor))))))

  /** Selectable only with a warband on the board to sacrifice. */
  override def selectable(ready: ReadyGame, actor: PlayerId): Boolean =
    super.selectable(ready, actor) &&
      PlayerFacts.player(ready, actor).exists(_.board.warbands > 0)

  private def sacrifice(actor: PlayerId): Operation =
    BuildOps((ready, _) => PlayerFacts.forceKind(ready, actor).map(kind =>
      Vector[CoreOperation](Kill(
        Piece.Warbands(kind, AwaitedReturn.Sacrificed),
        PositionedLocation(Location.PlayArea(actor))))), required = true)

  private def sacrificedNote(states: NoteStates, actor: PlayerId)
      : Option[LogNote] = for
    step <- states.previous
    lost = -NoteSupport.warbands(step, actor)
    if lost > 0
  yield AwaitedReturn.sacrificed(PowerSourceRef.Card(cardId),
    NoteArg.Number(lost))

object AwaitedReturn:
  val id: PowerId = AwaitedReturnCard.power.id
  val Sacrificed: Int = 1
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Spent no Supply after sacrificing {n} warband." */
  val sacrificed: NoteKey = notes.key("sacrificed", Vector(
    NotePart.Text("Spent no Supply after sacrificing "), NotePart.Arg(0),
    NotePart.Plural(0, " warband.", " warbands.")))

  def forCatalog(catalog: ExecutableCatalog): AwaitedReturn =
    new AwaitedReturn(catalog)
