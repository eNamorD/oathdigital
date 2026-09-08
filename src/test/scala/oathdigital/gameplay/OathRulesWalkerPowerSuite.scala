package oathdigital.gameplay

import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations._
import oathdigital.gameplay.powerresolver.PowerWindow
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.walker.{ChoicePayload, WalkerParked, WalkerPowers,
  WalkerStepRecorded}
import oathdigital.gameplay.OathState.Ready
import oathdigital.model._

/** Task 3 wiring at the aggregate boundary: `OathRules` gathers restrictions
  * once per command, at command entry, and rejects the whole command before
  * any node runs.
  *
  * These tests drive REAL commands (`startWalker` and a real resume) instead
  * of re-composing the check the way a unit test of
  * `ProcedureWalker.restrictionViolations` would: delete
  * `OathRules.checkRestrictions` from either call site and a `Left` here
  * becomes a walk, failing the test.
  *
  * No action declares a `window` yet (Task 4 wires Recover's), so the tree a
  * command walks is supplied through `OathRules`' `walkerTree` seam -- the
  * same injectable-default shape `campaignLosingForceRegistry` and
  * `warExhaustionRandomPort` already use, and the production default is what
  * every other walker suite exercises. The wiring under test is generic; the
  * tree only has to carry a hookable node.
  */
class OathRulesWalkerPowerSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)

  private val window: PowerWindow = PowerWindow.RecoverModifierSelection

  private val violation: OathViolation = OathViolation.RecoverUnavailable(
    "a test power forbids this action")

  /** A ready game in the Act phase. The command never reaches Recover's own
    * eligibility gates -- the injected tree source replaces them.
    */
  private def actable: (ReadyGame, PlayerId) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val ready = base.copy(game = base.game.copy(current =
      base.game.current.copy(turn = base.game.current.turn.copy(
        phase = Phase.Act))))
    (ready, ready.game.current.turn.activePlayer)
  }

  /** `Sequence(WindowedNode(window, Vector(decide, decide)))`: two decisions,
    * so resolving the first parks again instead of completing the action and
    * the assertions stay inside the walker's own surface. Their ids are the
    * two `OathRules` maps to a client continuation.
    */
  private def hookedTree(actor: PlayerId): Operation = {
    def decide(id: String) = Decide(
      payload = ProcedureWalkerSuite.TestDecisionPayload(id),
      owner = ProcedureWalkerSuite.TestOwner(actor),
      decisionId = id)
    Sequence(ProcedureWalkerSuite.WindowedNode(window, Vector(
      decide(RecoverProcedure.choiceDecisionId),
      decide(RecoverProcedure.relicDecisionId))))
  }

  private def rules(actor: PlayerId, powers: WalkerPowers): OathRules =
    new OathRules(catalog, walkerPowers = powers,
      walkerTree = (_, _, _, _, _) => Right(hookedTree(actor)))

  private def forbidding: WalkerPowers = WalkerPowers(Vector(
    ProcedureWalkerSuite.TestRestrictionPower(PowerId("test.forbid"), window,
      (_, _) => Some(violation))))

  test("a restriction rejects a real startWalker command, appending no events") {
    val (ready, actor) = actable

    // Control: with no power the same command runs and appends events.
    val started = rules(actor, WalkerPowers.empty)
      .startWalker(Ready(ready), ActionRef.Recover, actor) match {
      case Right(transition) => transition
      case other => fail(s"expected the unrestricted command to run, got $other")
    }
    assert(started.events.nonEmpty)
    assert(started.events.last.isInstanceOf[WalkerParked])

    // A restriction gathered at the tree's window rejects the whole command.
    // A `Left` carries no transition at all: nothing is appended and the
    // caller's state is untouched.
    assertEquals(
      rules(actor, forbidding).startWalker(Ready(ready), ActionRef.Recover,
        actor),
      Left(violation))
  }

  test("a restriction rejects a real walker resume command, appending no events") {
    val (ready, actor) = actable
    val started = rules(actor, WalkerPowers.empty)
      .startWalker(Ready(ready), ActionRef.Recover, actor) match {
      case Right(transition) => transition
      case other => fail(s"expected the unrestricted start to run, got $other")
    }
    val answer = Answered(RecoverProcedure.choiceDecisionId,
      ProcedureWalkerSuite.TestDecisionPayload("continue"))

    // Control: the resume is legal from this parked state.
    val resumed = rules(actor, WalkerPowers.empty)
      .resolveWalker(started.state, answer)
    val events = resumed match {
      case Right(transition) => transition.events
      case other => fail(s"expected the unrestricted resume to run, got $other")
    }
    assert(events.exists {
      case step: WalkerStepRecorded => step.payload.isInstanceOf[ChoicePayload]
      case _ => false
    })

    assertEquals(rules(actor, forbidding).resolveWalker(started.state, answer),
      Left(violation))
  }
}
