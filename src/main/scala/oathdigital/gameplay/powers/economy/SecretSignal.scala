package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Deferred,
  FoldPhase, ModifierHost, OfferReason, OperationRewrite, PowerCtx,
  Transform}
import oathdigital.gameplay.powers.{NoteSupport, SelectedModifier}
import oathdigital.model._

object SecretSignalCard extends Denizen(DenizenId("55"), "Secret Signal", Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.secret-signal"),
    persistent = false, cost = Cost.free,
    text = "If you gain only one [favor], gain one more [favor].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Secret Signal (card 55, adviser-only), a selected Trade modifier with no
  * cost: "If you gain only one favor, gain one more favor."
  *
  * It reads the Trade's whole favor gain. Its rewrite of the gain node
  * records the trader's favor gain as it runs, under [[TradeFavor.key]],
  * where Moving Market records its own. A step appended to the Trade's root
  * folds `Late`, after Moving Market's: when the recorded favor sums to
  * exactly one, the trader gains one more from the bank of the first gain,
  * the Trade's own. Two gains of one sum to two. The extra favor is not
  * recorded, so it never counts again.
  *
  * The Trade's own gain is recorded as requested. When its bank holds less,
  * the bank is empty after it, and the extra favor, drawn from the same
  * bank, is nothing either way.
  *
  * Unavailable for a Trade for secrets, read from the modifier decision's
  * start selection. In a Trade for favor it is always available: the source,
  * and so the gain, is chosen after the modifiers. Its line, "Gained 1 more
  * favor.", covers the extra gain's generic line.
  */
final case class SecretSignal private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  import SecretSignal._

  val cardId: DenizenId = SecretSignalCard.id
  def id: PowerId = SecretSignal.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)
  override def noteKeys: Vector[NoteKey] = Vector(gained)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeGain -> Vector(Transform((ctx, operations) =>
      OperationRewrite.ops(ctx, operations)((_, _) => {
        case gain @ Gain.Favor(player, _, _) if player == ctx.activePlayer =>
          Vector(gain, Defer(TradeFavor.key, Vector(gain)))
      }))),
    PowerWindow.TradeActionEligibility -> Vector(Transform((ctx, children) =>
      children :+ extra(ctx.activePlayer), FoldPhase.Late)))

  override def offerReason(ctx: PowerCtx): Option[OfferReason] =
    ctx.operation match
      case host: ModifierHost if host.startSelection ==
          TradeProcedure.selection(TradeResource.Secret) =>
        Some(OfferReason.Unavailable(noFavor))
      case _ => None

  /** One more favor, when the recorded favor sums to exactly one, then its
    * line. */
  private def extra(actor: PlayerId): Operation =
    Deferred.resolve(TradeFavor.key)((_, _, held) => {
      val gains = held.collect {
        case gain: Gain.Favor if gain.player == actor => gain
      }
      gains.headOption.filter(_ => gains.map(_.amount).sum == Only)
        .toVector.flatMap(first => Vector[Operation](
          Gain.Favor(actor, first.suit, Extra),
          Note(id, gainedNote(_, actor), covers = true)))
    })

  /** The favor the step before gained. */
  private def gainedNote(states: NoteStates, actor: PlayerId)
      : Option[PowerNote] = for
    step <- states.previous
    amount = NoteSupport.favor(step, actor)
    if amount > 0
  yield gained(PowerSourceRef.Card(cardId), NoteArg.Number(amount))

object SecretSignal:
  val id: PowerId = SecretSignalCard.power.id
  /** The whole gain that earns the extra favor. */
  val Only: Int = 1
  val Extra: Int = 1
  val noFavor: String = "A Trade for secrets gains no favor"
  /** "Gained {n} more favor." */
  val gained: NoteKey = NoteKey("gained", Vector(NotePart.Text("Gained "),
    NotePart.Arg(0), NotePart.Text(" more favor.")))

  def forCatalog(catalog: ExecutableCatalog): SecretSignal =
    new SecretSignal(catalog)
