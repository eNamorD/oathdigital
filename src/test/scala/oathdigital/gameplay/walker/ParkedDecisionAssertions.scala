package oathdigital.gameplay.walker

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** What a parked position holds, derived the way the walker derives it
  * rather than read off a value the rules stored. `coOwners` is the whole
  * answering set, including `awaiting`.
  */
final case class ParkedDecisionFacts(procedure: ProcedureRef, decision: String,
    awaiting: PlayerId, coOwners: Set[PlayerId])

/** The parked decision as a test may assert it.
  *
  * Constructed beside a suite's own `OathRules`, with the same catalog and
  * power catalogs: the tree a park sits in is only rebuildable against them,
  * and `awaitedPlayer` is only meaningful against the rebuilt tree. That is
  * why this is a class and not an object.
  *
  * The rebuild mirrors `OathRules.declaredWalkerTree` and the resume path in
  * `OathRulesWalker.walkerResumeContext`: the procedure, the start selection
  * and the selected modifiers all come from durable state, never from the
  * test's own memory of what it started.
  */
final class ParkedDecisionAssertions(
    catalog: ExecutableCatalog,
    walkerPowerCatalog: WalkerPowers = WalkerPowers.empty,
    phasePowerCatalog: PhasePowers = PhasePowers.empty)
    extends munit.Assertions:

  /** `None` when nothing is parked. */
  def parkedDecision(state: OathState): Option[ParkedDecisionFacts] =
    state match
      case Ready(ready) =>
        val current = ready.game.current
        for
          procedure <- current.walkerProcedure
          pending <- current.walkerPending
          tree <- WalkerProcedureRegistry.rebuild(procedure, catalog, ready,
            current.turn.activePlayer, current.walkerStartArgs,
            phasePowerCatalog).toOption
          powers = WalkerPowers.selected(walkerPowerCatalog,
            current.walkerModifiers)
          // A Roll park holds no Decide, so its id is the one the procedure
          // declares -- the same resolution the projector makes.
          decision <- ProcedureWalker.parkedDecide(ready, tree, pending, powers)
            .map(_.decisionId)
            .orElse(ProcedureWalker.parkedRoll(ready, tree, pending, powers)
              .flatMap(_ =>
                WalkerProcedureRegistry.rollDecisionId(procedure).toOption))
          awaiting <- ProcedureWalker.awaitedPlayer(ready, tree, pending, powers)
        yield ParkedDecisionFacts(procedure, decision, awaiting,
          ProcedureWalker.awaitedPlayers(ready, tree, pending, powers))
      case _ => None

  /** The game is parked on `decision` of `procedure`, awaiting `awaiting`.
    *
    * `awaiting` has no default on purpose: one parked decision awaiting one
    * named player is the invariant, and the identity half of it is otherwise
    * asserted nowhere.
    */
  def assertParked(state: OathState, procedure: ProcedureRef, decision: String,
      awaiting: PlayerId)(using munit.Location): Unit =
    assertEquals(parkedDecision(state).map(facts =>
      (facts.procedure, facts.decision, facts.awaiting)),
      Some((procedure, decision, awaiting)))

  /** Nothing is parked at all -- strictly stronger than "not parked on THIS
    * kind of decision", which is what the assertions this replaces said.
    */
  def assertNotParked(state: OathState)(using munit.Location): Unit =
    assertEquals(parkedDecision(state), None)

  /** Nothing is parked and the turn is where it should be: the whole content
    * of the three continuations that named a phase rather than a decision.
    */
  def assertResumed(state: OathState, phase: Phase, active: PlayerId)(
      using munit.Location): Unit =
    assertNotParked(state)
    val turn = state match
      case Ready(ready) => ready.game.current.turn
      case other => fail(s"expected a ready game, got $other")
    assertEquals((turn.phase, turn.activePlayer), (phase, active))

  /** Co-owners, for a decision more than one player may answer. */
  def assertCoOwners(state: OathState, owners: Set[PlayerId])(
      using munit.Location): Unit =
    assertEquals(parkedDecision(state).map(_.coOwners), Some(owners))
