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

  /** `None` exactly when nothing is parked: the state is not `Ready`, or it
    * is and `walkerPending` is empty -- the same fact `WalkerCompleted`
    * clears alongside `walkerProcedure` and `OathRules.unlessWalkerPending`
    * reads to mean "no walker is pending" (fix-round 1 ruling). Anything
    * else that keeps this from reporting the park -- a rebuild failure, a
    * position resolving to neither a `Decide` nor a `Roll`, a Roll park
    * whose procedure declares no roll decision id, or no awaited player --
    * is `walkerPending` being SET while the module cannot explain it, and
    * fails loudly instead: silently returning `None` there would let a
    * broken rebuild masquerade as "not parked" for every
    * `assertNotParked`/`assertResumed` call site, exactly the silent
    * weakening this module exists to prevent.
    */
  def parkedDecision(state: OathState)(using munit.Location)
      : Option[ParkedDecisionFacts] =
    state match
      case Ready(ready) =>
        val current = ready.game.current
        current.walkerPending match
          case None => None
          case Some(pending) =>
            val at = pending.at.mkString(".")
            val procedure = current.walkerProcedure.getOrElse(fail(
              s"a walker is pending at '$at' but no walkerProcedure is " +
                "recorded"))
            val tree = WalkerProcedureRegistry.rebuild(procedure, catalog,
                ready, current.turn.activePlayer, current.walkerStartArgs,
                phasePowerCatalog) match
              case Right(tree) => tree
              case Left(violation) => fail(
                s"could not rebuild $procedure's tree to read the park at " +
                  s"'$at': $violation")
            val powers = WalkerPowers.selected(walkerPowerCatalog,
              current.walkerModifiers)
            // A Roll park holds no Decide, so its id is the one the
            // procedure declares -- the same resolution the projector makes.
            val decision = ProcedureWalker.parkedDecide(ready, tree, pending,
                powers).map(_.decisionId)
              .orElse(ProcedureWalker.parkedRoll(ready, tree, pending, powers)
                .map(_ => WalkerProcedureRegistry.rollDecisionId(procedure) match
                  case Right(id) => id
                  case Left(violation) => fail(
                    s"a Roll parked at '$at' of $procedure, which declares " +
                      s"no roll decision id: $violation")))
              .getOrElse(fail(
                s"a walker is pending at '$at' of $procedure but it " +
                  "resolves to neither a Decide nor a Roll"))
            val awaiting = ProcedureWalker.awaitedPlayer(ready, tree, pending,
                powers).getOrElse(fail(
              s"a walker is pending at '$at' of $procedure on '$decision' " +
                "but no player is awaited"))
            Some(ParkedDecisionFacts(procedure, decision, awaiting,
              ProcedureWalker.awaitedPlayers(ready, tree, pending, powers)))
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
