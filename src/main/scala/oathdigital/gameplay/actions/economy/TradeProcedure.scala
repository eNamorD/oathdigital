package oathdigital.gameplay.actions.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.NoteSupport
import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.model._

/** Trade on the walker. The start selection is one resource button, `favor`
  * or `secret`, naming what the actor gains: Trade for favor pays one secret
  * and yields favor of the card's suit; Trade for secrets pays two favor, one
  * of them burnt, and yields secrets. Both spend one Supply, and both ask for
  * their modifiers in the `trade.modifiers` decision, then draw on the
  * `trade.source` decision.
  */
object TradeProcedure:
  val decisionId: String = "trade.source"

  private val notes = NoteSet(ProcedureNotes.id(ActionRef.Trade))
  /** "Traded with Hiding Place for 3 favor". The count is a `Number` and
    * not an `Amount`, so it renders as the plain text the rule wrote. */
  val tradedForFavor: NoteKey = notes.key("traded-favor", Vector(
    NotePart.Text("Traded with "), NotePart.Arg(0), NotePart.Text(" for "),
    NotePart.Arg(1), NotePart.Text(" favor")))
  /** "Traded with Hiding Place for no favor": the suit's bank was empty, or
    * a power removed the gain. The rule this replaced wrote "for no
    * secrets" here whichever resource the Trade was for, which was wrong
    * and which no test covered (slice 3 plan-time ruling 3). */
  val tradedForNoFavor: NoteKey = notes.key("traded-no-favor", Vector(
    NotePart.Text("Traded with "), NotePart.Arg(0),
    NotePart.Text(" for no favor")))
  /** "Traded with Hiding Place for 2 secrets". */
  val tradedForSecrets: NoteKey = notes.key("traded-secrets", Vector(
    NotePart.Text("Traded with "), NotePart.Arg(0), NotePart.Text(" for "),
    NotePart.Arg(1), NotePart.Text(" "),
    NotePart.Plural(1, "secret", "secrets")))
  /** "Traded with Hiding Place for no secrets": no adviser matched, so the
    * Trade requested no gain at all. */
  val tradedForNoSecrets: NoteKey = notes.key("traded-no-secrets", Vector(
    NotePart.Text("Traded with "), NotePart.Arg(0),
    NotePart.Text(" for no secrets")))
  def noteKeys: Vector[NoteKey] = notes.declared

  private def kind(resource: TradeResource, actor: PlayerId)
      : EconomyTree.Kind = resource match
    case TradeResource.Favor => EconomyTree.Kind(decisionId,
      "Choose a card to Trade for favor", PowerWindow.TradeModifierSelection,
      "Start Trade", PowerWindow.TradeActionEligibility,
      PowerWindow.TradeSourceSelection, PowerWindow.TradeCost,
      PowerWindow.TradeGain, Cost(secret = 1),
      (player, source, matching, _) =>
        Some(Gain.Favor(player, source.suit, 1 + matching)),
      selection = selection(resource),
      note = Some((ProcedureNotes.id(ActionRef.Trade),
        tradedNote(resource, actor, _, _))))
    case TradeResource.Secret => EconomyTree.Kind(decisionId,
      "Choose a card to Trade for secrets", PowerWindow.TradeModifierSelection,
      "Start Trade", PowerWindow.TradeActionEligibility,
      PowerWindow.TradeSourceSelection, PowerWindow.TradeCost,
      PowerWindow.TradeGain, Cost(favor = 1, favorBurnt = 1),
      (player, _, matching, _) =>
        Option.when(matching > 0)(Gain.Secrets(player, matching)),
      selection = selection(resource),
      note = Some((ProcedureNotes.id(ActionRef.Trade),
        tradedNote(resource, actor, _, _))))

  /** What the Trade's own gain put in the actor's play area, read as the
    * difference across the step before the note rather than as the
    * requested amount: a gain the bank clamped reads what arrived, and a
    * gain a power removed reads zero.
    *
    * `EconomyTree.afterSource` places this leaf last in the gain window, so
    * `previous` is the gain. The Old Oak's bonus secret, Mounted Library's
    * doubling and Secret Signal's extra favor each land after this note, so
    * the note still reads the Trade's own gain. All three also record the
    * gain as it runs, by rewriting it in place: each holds its `Defer`
    * before the gain for that reason, since a bookkeeping leaf after the
    * gain would be what this reads instead, and it moves no resource, so
    * the line would say the Trade gained nothing
    * (`TradeGainNoteSuite`).
    *
    * A Trade for secrets with no matching adviser requests no gain at all,
    * so the window holds only this note and `previous` is the cost: the
    * difference is zero and the line is "for no secrets", which is what the
    * rule wrote at completion.
    */
  private def tradedNote(resource: TradeResource, actor: PlayerId,
      states: NoteStates, source: MusterSource): Option[LogNote] =
    val gained = states.previous.fold(0)(step => resource match
      case TradeResource.Favor => NoteSupport.favor(step, actor)
      case TradeResource.Secret => NoteSupport.secrets(step, actor))
    val card = NoteArg.Card(source.card)
    Some(resource match
      case TradeResource.Favor =>
        if gained > 0 then
          tradedForFavor.sourceless(card, NoteArg.Number(gained))
        else tradedForNoFavor.sourceless(card)
      case TradeResource.Secret =>
        if gained > 0 then
          tradedForSecrets.sourceless(card, NoteArg.Number(gained))
        else tradedForNoSecrets.sourceless(card))

  def resourceOf(args: Vector[DecisionOptionRef])
      : Either[OathViolation, TradeResource] = args match
    case Vector(DecisionOptionRef.Button("favor")) => Right(TradeResource.Favor)
    case Vector(DecisionOptionRef.Button("secret")) => Right(TradeResource.Secret)
    case other => Left(OathViolation.InvalidEventOrder(
      "trade takes exactly one resource button, favor or secret, as its start " +
        s"selection, got ${other.map(ref => s"${ref.kind}/${ref.wireId}")
          .mkString(", ")}"))

  /** The start selection that trades for `resource`, as `resourceOf` reads
    * it. */
  def selection(resource: TradeResource): Vector[DecisionOptionRef] =
    resource match
      case TradeResource.Favor => Vector(DecisionOptionRef.Button("favor"))
      case TradeResource.Secret => Vector(DecisionOptionRef.Button("secret"))

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    resourceOf(args).flatMap(resource =>
      EconomyTree.build(catalog, state, actor, kind(resource, actor)))

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    resourceOf(args).map(resource =>
      EconomyTree.tree(catalog, state, actor, kind(resource, actor)))
