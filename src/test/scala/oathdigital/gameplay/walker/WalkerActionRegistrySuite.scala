package oathdigital.gameplay.walker

import oathdigital.gameplay.{OathViolation, ReadyGame}
import oathdigital.model.{ActionRef, PlayerId}

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
}
