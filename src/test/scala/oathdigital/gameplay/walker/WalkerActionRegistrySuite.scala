package oathdigital.gameplay.walker

import oathdigital.gameplay.OathViolation
import oathdigital.model.ActionRef

/** Task 8: `WalkerActionRegistry.build`/`rebuild` are the single keyed
  * lookup both `OathRules.buildWalker` and `WalkerDecisionProjector` now
  * dispatch through, replacing the two hardcoded
  * `case ActionRef.Recover =>` matches. A missing branch there used to be a
  * runtime `MatchError`; here it is a typed `Left`.
  *
  * `ActionRef` is sealed with exactly one inhabitant today (`Recover`), so
  * there is no way to construct a genuinely unregistered `ActionRef` from
  * outside `ActionRef.scala` to drive `build`/`rebuild` themselves down the
  * missing-entry branch. This suite instead exercises `lookup` --
  * `private[walker]`, the exact function `build`/`rebuild` call against the
  * production `entries` map -- with a registrations map that omits a real,
  * registered-in-production action. That is precisely the branch a second,
  * unregistered action would hit, without fabricating a fake `ActionRef`.
  */
class WalkerActionRegistrySuite extends munit.FunSuite {

  test("an action absent from the registrations map is rejected with a " +
      "typed Left, not a MatchError") {
    val result = WalkerActionRegistry.lookup(ActionRef.Recover, Map.empty)
    assertEquals(result, Left(OathViolation.InvalidEventOrder(
      "no walker action registered for recover")))
  }

  test("the production entries register every known action") {
    assertEquals(WalkerActionRegistry.entries.keySet, ActionRef.all.toSet)
  }
}
