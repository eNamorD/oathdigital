package oathdigital.gameplay.walker

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.ParkedNode

/** What a parked position holds, derived the way the walker derives it
  * rather than read off a value the rules stored.
  */
final case class ParkedDecisionFacts(procedure: ProcedureRef, decision: String,
    awaiting: PlayerId)

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

  /** `None` exactly when nothing is parked; fails loudly when a walker is
    * pending but [[ParkedNode.of]] cannot explain the park, since silently
    * returning `None` there would let a broken rebuild masquerade as "not
    * parked" for every `assertNotParked`/`assertResumed` call site.
    */
  def parkedDecision(state: OathState)(using munit.Location)
      : Option[ParkedDecisionFacts] =
    ParkedNode.of(state, catalog, walkerPowerCatalog, phasePowerCatalog) match
      case Left(reason) => fail(reason)
      case Right(node) => node.map(parked =>
        ParkedDecisionFacts(parked.procedure, parked.decisionId,
          parked.awaiting))

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
