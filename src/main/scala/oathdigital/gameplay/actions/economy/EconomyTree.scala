package oathdigital.gameplay.actions.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.walker.{ModifierChoice, PricedOptions,
  WalkerPowers}
import oathdigital.model._

/** The tree Muster and Trade share (modifier walker design for its first
  * node):
  *
  * {{{
  * Sequence(
  *   Repeat(ModifierChoice),                 // the modifiers, one per pass,
  *                                           // then "Start Muster" or
  *                                           // "Start Trade"
  *   Sequence(                               // window = <Action>ActionEligibility
  *     Decide(source),                       // window = <Action>SourceSelection
  *     Branch { after the answer:
  *       Sequence(PayCost, SpendSupply(1)),  // window = <Action>Cost
  *       Sequence(the gain) }))              // window = <Action>Gain
  * }}}
  *
  * The modifiers come first. Each pick pays at once, and a picked modifier
  * then folds at the windows after the decision. The start carries no
  * selection of modifiers; Trade's resource is still its start selection.
  *
  * The source is a decision, not a start argument, so a power can add to or
  * remove from the offered cards through the decision's window. The gain is a
  * concrete operation inside its own windowed node, not an opaque `BuildOps`,
  * so a power can see and rewrite the requested amount; it is requested
  * unclamped and the best-effort `Gain` takes what the supply or bank holds.
  * Legacy computed the gain from the state before the cost, and no cost feeds
  * a gain input, so it is built from the state at the `Branch`.
  */
private[economy] object EconomyTree:

  /** What differs between Muster and the two Trades. */
  final case class Kind(
      decisionId: String,
      heading: String,
      modifiers: PowerWindow,
      startLabel: String,
      eligibility: PowerWindow,
      sourceSelection: PowerWindow,
      cost: PowerWindow,
      gain: PowerWindow,
      payment: Cost,
      yields: (PlayerId, MusterSource, Int, ForceKind) => Option[CoreOperation],
      /** What the player selected when the action started, which the
        * modifier decision names. */
      selection: Vector[DecisionOptionRef] = Vector.empty,
      /** The line this action writes at its gain, if it writes one: the
        * note's id and a builder that reads the resolved source. The tree
        * places the leaf; the procedure owns the words. */
      note: Option[(PowerId, (NoteStates, MusterSource) => Option[LogNote])] =
        None)

  /** Fresh start: the lifecycle gate, a pawn site, a resolvable ruler for
    * every site.
    */
  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      kind: Kind): Either[OathViolation, Operation] =
    gated(state, actor).map(_ => tree(catalog, state, actor, kind))

  private def gated(state: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Unit] = for
    _ <- state.game.current.pawnSite(actor)
      .toRight(OathViolation.PawnSiteMissing(actor))
    _ <- siteRulers(state)
  yield ()

  /** Every site's forces must name a ruler the game can identify. Legacy
    * Economy refused a board where one did not, and nothing else rejects an
    * unknown or duplicated lineage on a site, so the check survives as a start
    * gate.
    */
  private def siteRulers(state: ReadyGame): Either[OathViolation, Unit] =
    state.game.current.map.sites.valuesIterator
      .map(site => SiteRule.ruler(site.forces, state.game.current.players))
      .collectFirst { case Left(error) => OathViolation.UnsupportedEconomyState(
        s"invalid site ruler mapping: $error") }
      .toLeft(())

  /** The same tree without the start-only gates, for a resume. */
  def tree(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      kind: Kind): Operation =
    val rest = afterModifiers(catalog, state, actor, kind)
    Sequence(Vector(ModifierChoice.loop(actor, kind.modifiers, rest,
      kind.startLabel, Some(startReason), kind.selection), rest))

  /** The source pick, then the payment and the gain. */
  private def afterModifiers(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, kind: Kind): Operation = Sequence(Vector[Operation](
    Decide(kind.decisionId, actor, DecisionQuery.ChooseOne(
      MusterSource.atSite(catalog, state, actor),
      heading = Some(kind.heading)), window = Some(kind.sourceSelection)),
    Branch((ready, pending) => answered(pending, kind.decisionId).toVector
      .flatMap(ref => afterSource(catalog, ready, actor, kind, ref,
        pending.answered).fold(error => Vector[Operation](fail(error)),
          identity)))),
    Some(kind.eligibility))

  /** Why "Start" cannot go on: the source decision priced, so that a
    * shortfall of Supply is named ("Needs 1 Supply; you have 0"). The parked
    * source decision itself still shows only the sources a start could
    * complete. */
  private def startReason(state: ReadyGame, tree: Operation,
      pending: PendingTree, powers: WalkerPowers, decide: Decide)
      : Vector[DecisionOption] =
    PricedOptions.priced(state, tree, pending, powers, decide,
      fallback = ModifierChoice.cannotStart)

  private def afterSource(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, kind: Kind, ref: DecisionOptionRef,
      answered: Vector[Answered])
      : Either[OathViolation, Vector[Operation]] = for
    source <- MusterSource.resolve(catalog, ready, actor, ref, answered)
    player <- ready.game.current.players.find(_.player == actor)
      .toRight(OathViolation.PawnSiteMissing(actor))
    force <- PlayerForceKind.of(ready, player).toRight(
      OathViolation.UnsupportedEconomyState(
        s"no warband kind for lineage ${player.lineage.value}"))
  yield Vector[Operation](
    // The payment is a required `PayCost`, so an unaffordable one rejects
    // rather than shrinking, and the preview drops the option. Both operations
    // are the window's own children, so a power that changes or removes the
    // cost rewrites them, and whatever payment remains is still enforced.
    Sequence(Vector[Operation](
      PayCost(actor, Location.OnCard(source.card), kind.payment),
      SpendSupply(actor, 1)), Some(kind.cost)),
    Sequence(kind.yields(actor, source,
      MusterSource.matching(catalog, ready, actor, source.suit), force)
        .toVector ++
      // Last in the window, so the note's `previous` is the gain. Every
      // `MusterGain` power appends, so this holds; a power that prepended
      // would make the note read another operation's delta, and nothing
      // would say so. Three `TradeGain` powers rewrite the gain in place
      // to record it, which puts a second leaf in the gain's position:
      // each holds its `Defer` before the gain so the gain is still the
      // last leaf here (`TradeGainNoteSuite`).
      kind.note.toVector.map((id, build) => Note(id, build(_, source))),
      Some(kind.gain)))

  private def answered(pending: PendingTree, decisionId: String)
      : Option[DecisionOptionRef] = pending.answered.collectFirst:
    case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref

  private def fail(error: OathViolation): Operation =
    BuildOps((_, _) => Left(error))
