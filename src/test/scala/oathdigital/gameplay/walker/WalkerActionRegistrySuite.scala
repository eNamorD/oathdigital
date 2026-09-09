package oathdigital.gameplay.walker

import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powerresolver.PowerWindow
import oathdigital.gameplay.{MajorActionKind, OathContinue, OathViolation,
  ReadyGame}
import oathdigital.model.{ActionRef, DecisionId, PlayerId}

/** Task 8: `WalkerActionRegistry.build`/`rebuild` are the single keyed
  * lookup both `OathRules.buildWalker` and `WalkerDecisionProjector` now
  * dispatch through, replacing the two hardcoded
  * `case ActionRef.Recover =>` matches. A missing branch there used to be a
  * runtime `MatchError`; here it is a typed `Left`.
  *
  * `ActionRef` is sealed with exactly one inhabitant today (`Recover`), so
  * there is no way to construct a genuinely unregistered `ActionRef` from
  * outside `ActionRef.scala`. Instead, `build`/`rebuild` accept
  * `registrations` as a parameter defaulting to the production `entries`
  * map (Task 8 fix round 1) -- every production call site is unaffected,
  * but this suite overrides it with a map that omits a real,
  * registered-in-production action to drive THESE public entry points
  * themselves down the missing-registration branch, rather than testing
  * the previously-extracted `lookup` helper as a stand-in for them. A
  * `match` reintroduced inside `build`/`rebuild` that bypassed
  * `registrations`/`lookup` entirely would compile but fail these tests.
  *
  * `catalog`/`state` below are never dereferenced: `registrations = Map.
  * empty` makes the internal `lookup` fail before `Entry.build`/`rebuild`
  * is ever invoked, so `null` is safe here and keeps this suite free of
  * full-game fixtures that this failure path has no use for.
  */
class WalkerActionRegistrySuite extends munit.FunSuite {

  private val unregistered = Map.empty[ActionRef, WalkerActionRegistry.Entry]
  private val actor = PlayerId("p1")
  private val state: ReadyGame = null

  test("build rejects an action absent from the registrations map with a " +
      "typed Left, not a MatchError") {
    val result = WalkerActionRegistry.build(ActionRef.Recover, catalog = null,
      state = state, actor = actor, eligibilityRelaxed = false,
      registrations = unregistered)
    assertEquals(result, Left(OathViolation.InvalidEventOrder(
      "no walker action registered for recover")))
  }

  test("rebuild rejects an action absent from the registrations map with a " +
      "typed Left, not a MatchError") {
    val result = WalkerActionRegistry.rebuild(ActionRef.Recover,
      catalog = null, state = state, actor = actor,
      registrations = unregistered)
    assertEquals(result, Left(OathViolation.InvalidEventOrder(
      "no walker action registered for recover")))
  }

  test("the production entries register every known action") {
    assertEquals(WalkerActionRegistry.entries.keySet, ActionRef.all.toSet)
  }

  /** Batch-1 Task 1: the modifier-selection window is per-action registry
    * data, alongside `fallbackKind`/`rollDecisionId`. Recover declares the
    * window both `OathRules.offerableWalkerPowers` and
    * `validateModifiers` used to name as a literal, so its behaviour is
    * unchanged by construction.
    */
  test("modifierWindow reads the registered entry, and an unregistered " +
      "action is a typed Left") {
    assertEquals(WalkerActionRegistry.modifierWindow(ActionRef.Recover),
      Right(Some(PowerWindow.RecoverModifierSelection)))
    assertEquals(
      WalkerActionRegistry.modifierWindow(ActionRef.Recover, unregistered),
      Left(OathViolation.InvalidEventOrder(
        "no walker action registered for recover")))
  }

  /** Batch-1 Task 3, ruling R18. `Entry.rollDecisionId` is `Option[String]`
    * because Forge's tree has no `Roll` node at all, and the accessor
    * flattens `None` into a typed rejection rather than handing a sentinel
    * id to `OathRules.parkedContinue` and `WalkerDecisionProjector` -- the
    * two call sites that ask it "which id is the Roll park this action just
    * produced". This test pins the accessor's value; that both call sites
    * carry the rejection through rather than projecting a sentinel is
    * proven separately, at the cutover.
    */
  test("rollDecisionId rejects an action whose entry declares none, and " +
      "still answers for the action that has one") {
    assertEquals(WalkerActionRegistry.rollDecisionId(ActionRef.Recover),
      Right(RecoverProcedure.rollDecisionId))
    assertEquals(WalkerActionRegistry.rollDecisionId(ActionRef.Forge),
      Left(OathViolation.InvalidEventOrder(
        "walker action forge declares no roll decision id")))
    assertEquals(
      WalkerActionRegistry.rollDecisionId(ActionRef.Recover, unregistered),
      Left(OathViolation.InvalidEventOrder(
        "no walker action registered for recover")))
  }

  /** Batch-1 Task 3, Step 2b: the eligibility window is registry data
    * alongside `modifierWindow`. Each registered action declares its own;
    * `OathRules.eligibilityRelaxed` reads it instead of naming
    * `RecoverActionEligibility`.
    */
  test("eligibilityWindow reads the registered entry, and an unregistered " +
      "action is a typed Left") {
    assertEquals(WalkerActionRegistry.eligibilityWindow(ActionRef.Recover),
      Right(Some(PowerWindow.RecoverActionEligibility)))
    assertEquals(WalkerActionRegistry.eligibilityWindow(ActionRef.Forge),
      Right(Some(PowerWindow.ForgeActionEligibility)))
    assertEquals(
      WalkerActionRegistry.eligibilityWindow(ActionRef.Recover, unregistered),
      Left(OathViolation.InvalidEventOrder(
        "no walker action registered for recover")))
  }

  /** The Forge entry's own facts, asserted as a whole rather than left to
    * whichever end-to-end test happens to exercise them: a wrong
    * `fallbackKind` or `modifierWindow` here is a silent misrouting, not a
    * failure.
    */
  test("the Forge entry declares Forge's own kind, windows and continuation") {
    val entry = WalkerActionRegistry.entries(ActionRef.Forge)
    assertEquals(entry.fallbackKind, MajorActionKind.Forge)
    assertEquals(entry.modifierWindow, Some(PowerWindow.ForgeModifierSelection))
    assertEquals(entry.eligibilityWindow,
      Some(PowerWindow.ForgeActionEligibility))
    assertEquals(entry.rollDecisionId, None)
    val actor = PlayerId("p1")
    val decision = DecisionId("forge-1")
    assertEquals(WalkerActionRegistry.continuationFor(ActionRef.Forge,
      ForgeProcedure.assignmentDecisionId, actor, decision),
      Right(Some(OathContinue.AwaitingForgeAssignment(actor, decision))))
    assertEquals(WalkerActionRegistry.continuationFor(ActionRef.Forge,
      "recover.relic", actor, decision), Right(None))
  }
}
