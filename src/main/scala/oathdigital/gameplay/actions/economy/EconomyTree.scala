package oathdigital.gameplay.actions.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathLifecycle, PowerRuntime}
import oathdigital.model._

/** The tree Muster and Trade share:
  *
  * {{{
  * Sequence(                                       // window = <Action>ActionEligibility
  *   Decide(source),                               // window = <Action>SourceSelection
  *   Branch { after the answer:
  *     Sequence(PayCost, SpendSupply(1)),           // window = <Action>Cost
  *     Sequence(the gain) })                       // window = <Action>Gain
  * }}}
  *
  * The source is a decision, not a start argument, so a power can add to or
  * remove from the offered cards through the decision's window. The gain is a
  * concrete operation inside its own windowed node, not an opaque `BuildOps`,
  * so a power can see and rewrite the requested amount; it is requested
  * unclamped and the best-effort `Gain` takes what the supply or bank holds.
  * Legacy computed the gain from the state before the cost, and no cost feeds
  * a gain input, so it is built from the state at the `Branch`.
  */
private[economy] object EconomyTree {

  /** What differs between Muster and the two Trades. */
  final case class Kind(
      decisionId: String,
      heading: String,
      eligibility: PowerWindow,
      sourceSelection: PowerWindow,
      cost: PowerWindow,
      gain: PowerWindow,
      payment: Cost,
      yields: (PlayerId, MusterSource, Int, ForceKind) => Option[CoreOperation])

  /** Fresh start: the lifecycle gate, a pawn site, a resolvable ruler for
    * every site, and the audited catalog.
    */
  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      kind: Kind): Either[OathViolation, Operation] = for {
    _ <- OathLifecycle.validateAct(OathState.Ready(state), actor)
    _ <- state.game.current.players.find(_.player == actor).flatMap(_.pawnSite)
      .toRight(OathViolation.PawnSiteMissing(actor))
    _ <- siteRulers(state)
    _ <- PowerRuntime.requireAudited(catalog)
  } yield tree(catalog, state, actor, kind)

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
      kind: Kind): Operation = Sequence(Vector[Operation](
    Decide(kind.decisionId, actor, DecisionQuery.ChooseOne(
      MusterSource.atSite(catalog, state, actor).map(_.option),
      heading = Some(kind.heading)), window = Some(kind.sourceSelection)),
    Branch((ready, pending) => answered(pending, kind.decisionId).toVector
      .flatMap(ref => afterSource(catalog, ready, actor, kind, ref)
        .fold(error => Vector[Operation](fail(error)), identity)))),
    Some(kind.eligibility))

  private def afterSource(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, kind: Kind, ref: DecisionOptionRef)
      : Either[OathViolation, Vector[Operation]] = for {
    source <- MusterSource.resolve(catalog, ready, actor, ref)
    player <- ready.game.current.players.find(_.player == actor)
      .toRight(OathViolation.PawnSiteMissing(actor))
    force <- PlayerForceKind.of(ready, player).toRight(
      OathViolation.UnsupportedEconomyState(
        s"no warband kind for lineage ${player.lineage.value}"))
  } yield Vector[Operation](
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
      : Option[DecisionOptionRef] = pending.answered.collectFirst {
    case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
  }

  private def fail(error: OathViolation): Operation =
    BuildOps((_, _) => Left(error))
}
