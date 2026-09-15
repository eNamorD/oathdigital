package oathdigital.gameplay.walker

import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powerresolver.PowerWindow
import oathdigital.gameplay.{MajorActionKind, OathContinue, OathViolation,
  ReadyGame}
import oathdigital.model.{ActionRef, DecisionId, DecisionOptionRef,
  PhaseTransitionRef, PlayerId, ProcedureRef, SiteId}

/** Task 8: `WalkerProcedureRegistry.build`/`rebuild` are the single keyed
  * lookup both `OathRules.buildWalker` and `WalkerDecisionProjector` now
  * dispatch through, replacing the two hardcoded
  * `case ActionRef.Recover =>` matches. A missing branch there used to be a
  * runtime `MatchError`; here it is a typed `Left`.
  *
  * `build`/`rebuild` accept `registrations` as a parameter defaulting to the
  * production `entries` map (Task 8 fix round 1) -- every production call
  * site is unaffected, but this suite overrides it with a map that omits a
  * real, registered-in-production procedure to drive THESE public entry
  * points themselves down the missing-registration branch, rather than
  * testing the previously-extracted `lookup` helper as a stand-in for them.
  * A `match` reintroduced inside `build`/`rebuild` that bypassed
  * `registrations`/`lookup` entirely would compile but fail these tests.
  *
  * `catalog`/`state` below are never dereferenced: `registrations = Map.
  * empty` makes the internal `lookup` fail before `Entry.build`/`rebuild`
  * is ever invoked, so `null` is safe here and keeps this suite free of
  * full-game fixtures that this failure path has no use for.
  */
class WalkerProcedureRegistrySuite extends munit.FunSuite {

  private val unregistered =
    Map.empty[ProcedureRef, WalkerProcedureRegistry.Entry]
  private val actor = PlayerId("p1")
  private val state: ReadyGame = null

  test("build rejects an action absent from the registrations map with a " +
      "typed Left, not a MatchError") {
    val result = WalkerProcedureRegistry.build(ActionRef.Recover,
      catalog = null, state = state, activePlayer = actor,
      registrations = unregistered)
    assertEquals(result, Left(OathViolation.InvalidEventOrder(
      "no walker procedure registered for recover")))
  }

  test("rebuild rejects an action absent from the registrations map with a " +
      "typed Left, not a MatchError") {
    val result = WalkerProcedureRegistry.rebuild(ActionRef.Recover,
      catalog = null, state = state, activePlayer = actor,
      registrations = unregistered)
    assertEquals(result, Left(OathViolation.InvalidEventOrder(
      "no walker procedure registered for recover")))
  }

  test("the production entries register every procedure reference") {
    assertEquals(WalkerProcedureRegistry.entries.keySet, ProcedureRef.all.toSet)
  }

  /** Batch-1 Task 1: the modifier-selection window is per-action registry
    * data, alongside `fallbackKind`/`rollDecisionId`. Recover declares the
    * window both `OathRules.offerableWalkerPowers` and
    * `validateModifiers` used to name as a literal, so its behaviour is
    * unchanged by construction.
    */
  test("modifierWindow reads the registered entry, and an unregistered " +
      "action is a typed Left") {
    assertEquals(WalkerProcedureRegistry.modifierWindow(ActionRef.Recover),
      Right(Some(PowerWindow.RecoverModifierSelection)))
    assertEquals(
      WalkerProcedureRegistry.modifierWindow(ActionRef.Recover, unregistered),
      Left(OathViolation.InvalidEventOrder(
        "no walker procedure registered for recover")))
  }

  /** Batch-1 Task 3, ruling R18. `Entry.rollDecisionId` is `Option[String]`
    * because Forge's tree has no `Roll` node at all, and the accessor
    * flattens `None` into a typed rejection rather than handing a sentinel
    * id to `OathRules.parkedContinue` and `WalkerDecisionProjector` -- the
    * two call sites that ask it "which id is the Roll park this action just
    * produced".
    *
    * This test pins the accessor's VALUE only. That both call sites carry
    * the rejection through rather than continuing on a sentinel is proven
    * where those call sites actually run, since a `Left` nobody honours
    * would keep this test green:
    *  - `OathRulesWalker.parkedContinue` --
    *    `OathRulesWalkerPowerSuite`, "a Roll park under an action declaring
    *    no roll decision id rejects the whole command with the accessor's
    *    typed Left, appending nothing".
    *  - `WalkerDecisionProjector` --
    *    `GameApplicationServiceSuite`, "walker Forge completes through
    *    StartWalker/ResolveWalker alone and replays to the same final
    *    state", which asserts the parked Forge decision carries no
    *    `rollOutcome`.
    */
  test("rollDecisionId rejects an action whose entry declares none, and " +
      "still answers for the action that has one") {
    assertEquals(WalkerProcedureRegistry.rollDecisionId(ActionRef.Recover),
      Right(RecoverProcedure.rollDecisionId))
    assertEquals(WalkerProcedureRegistry.rollDecisionId(ActionRef.Forge),
      Left(OathViolation.InvalidEventOrder(
        "walker procedure forge declares no roll decision id")))
    assertEquals(
      WalkerProcedureRegistry.rollDecisionId(ActionRef.Recover, unregistered),
      Left(OathViolation.InvalidEventOrder(
        "no walker procedure registered for recover")))
  }

  /** The Forge entry's own facts, asserted as a whole rather than left to
    * whichever end-to-end test happens to exercise them: a wrong
    * `fallbackKind` or `modifierWindow` here is a silent misrouting, not a
    * failure.
    */
  test("the Forge entry declares Forge's own kind, modifier window and continuation") {
    val entry = WalkerProcedureRegistry.entries(ActionRef.Forge)
    assertEquals(entry.fallbackKind, Some(MajorActionKind.Forge))
    assertEquals(entry.modifierWindow, Some(PowerWindow.ForgeModifierSelection))
    assertEquals(entry.rollDecisionId, None)
    val actor = PlayerId("p1")
    val decision = DecisionId("forge-1")
    assertEquals(WalkerProcedureRegistry.continuationFor(ActionRef.Forge,
      ForgeProcedure.assignmentDecisionId, actor, decision),
      Right(Some(OathContinue.AwaitingForgeAssignment(actor, decision))))
    assertEquals(WalkerProcedureRegistry.continuationFor(ActionRef.Forge,
      "recover.relic", actor, decision), Right(None))
  }

  /** The Travel entry's own facts, asserted as a whole for the same reason
    * Forge's are. Travel is the only entry whose builders take a start
    * selection, and the only one whose `rebuild` is its `build`: every gate
    * Travel has is a fact about the route, so a resume must re-check exactly
    * what a start checked.
    */
  test("the Travel entry declares Travel's own kind and no park of any shape") {
    val entry = WalkerProcedureRegistry.entries(ActionRef.Travel)
    assertEquals(entry.fallbackKind, Some(MajorActionKind.Travel))
    assertEquals(entry.modifierWindow, Some(PowerWindow.TravelModifierSelection))
    assertEquals(entry.rollDecisionId, None)
    // A flat tree parks nowhere, so no decision id of any spelling maps to a
    // continuation.
    Vector("travel", "walker.travel.roll", "recover.relic").foreach { id =>
      assertEquals(WalkerProcedureRegistry.continuationFor(ActionRef.Travel, id,
        PlayerId("p1"), DecisionId("d1")), Right(None))
    }
  }

  test("Begin Rest records Rest diagnostics; Finish Rest records none and " +
      "parks as a generic Rest decision") {
    assertEquals(WalkerProcedureRegistry.fallbackKind(
      PhaseTransitionRef.BeginRest), Right(Some(MajorActionKind.Rest)))
    assertEquals(WalkerProcedureRegistry.fallbackKind(
      PhaseTransitionRef.FinishRest), Right(None))
    assertEquals(WalkerProcedureRegistry.continuationFor(
      PhaseTransitionRef.FinishRest, "any", actor, DecisionId("d")),
      Right(Some(OathContinue.AwaitingRestDecision(actor, DecisionId("d")))))
  }

  /** An action that selects nothing at its start must REJECT a selection, not
    * ignore one.
    *
    * Ignoring it is the silent failure: a client that attached a destination
    * to a Recover would get a Recover that succeeded as though it had not,
    * and no assertion anywhere else in the suite would notice, because every
    * other test passes an empty vector. This one passes a real reference and
    * pins the rejection, so weakening the guard to accept anything fails
    * here.
    */
  test("an action declaring no start selection rejects one rather than " +
      "ignoring it") {
    val selection = Vector[DecisionOptionRef](
      DecisionOptionRef.Site(SiteId("site:somewhere")))
    Vector(ActionRef.Recover, ActionRef.Forge).foreach { action =>
      Vector(
        WalkerProcedureRegistry.build(action, null, state, actor, selection),
        WalkerProcedureRegistry.rebuild(action, null, state, actor, selection)
      ).foreach { result =>
        // The message, not just the failure: `state` is null here, so ANY
        // builder that read state would also fail, and asserting only
        // `isLeft` would pass for a guard that had been removed entirely.
        // Naming the selection is what proves this rejection is the guard.
        assertEquals(result, Left(OathViolation.InvalidEventOrder(
          s"walker procedure ${action.key} takes no start selection, got site")))
      }
    }
  }
}
