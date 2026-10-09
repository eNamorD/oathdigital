package oathdigital.gameplay.actions.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.OathLifecycle
import oathdigital.gameplay.walker.{ModifierChoice, PricedOptions,
  WalkerPowers, WalkerSimulation}
import oathdigital.gameplay.walker.WalkerSimulation.PreviewedOption
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
      yields: (PlayerId, MusterSource, Int, ForceKind) => Option[CoreOperation])

  /** Fresh start: the lifecycle gate, a pawn site, a resolvable ruler for
    * every site.
    */
  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      kind: Kind): Either[OathViolation, Operation] =
    gated(state, actor).map(_ => tree(catalog, state, actor, kind))

  /** Every source the actor could draw on now under `powers`, each previewed
    * through what follows the modifier decision. Empty when the action cannot
    * start at all. The start control and the parked decision read it.
    */
  def startOptions(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, kind: Kind, powers: WalkerPowers)
      : Vector[PreviewedOption] =
    gated(state, actor).map(_ => afterModifiers(catalog, state, actor, kind))
      .flatMap(WalkerSimulation.preview(_, state, powers))
      .getOrElse(Vector.empty)

  private def gated(state: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Unit] = for
    _ <- OathLifecycle.validateAct(OathState.Ready(state), actor)
    _ <- state.game.current.players.find(_.player == actor).flatMap(_.pawnSite)
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
      kind.startLabel, Some(startReason)), rest))

  /** The source pick, then the payment and the gain. */
  private def afterModifiers(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, kind: Kind): Operation = Sequence(Vector[Operation](
    Decide(kind.decisionId, actor, DecisionQuery.ChooseOne(
      MusterSource.atSite(catalog, state, actor),
      heading = Some(kind.heading)), window = Some(kind.sourceSelection)),
    Branch((ready, pending) => answered(pending, kind.decisionId).toVector
      .flatMap(ref => afterSource(catalog, ready, actor, kind, ref)
        .fold(error => Vector[Operation](fail(error)), identity)))),
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
      actor: PlayerId, kind: Kind, ref: DecisionOptionRef)
      : Either[OathViolation, Vector[Operation]] = for
    source <- MusterSource.resolve(catalog, ready, actor, ref)
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
      MusterSource.matching(catalog, ready, actor, source.suit), force).toVector,
      Some(kind.gain)))

  private def answered(pending: PendingTree, decisionId: String)
      : Option[DecisionOptionRef] = pending.answered.collectFirst:
    case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref

  private def fail(error: OathViolation): Operation =
    BuildOps((_, _) => Left(error))
