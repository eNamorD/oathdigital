package oathdigital.gameplay.setup

import oathdigital.gameplay.WalkerRecordedOpsReducer
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{Park, Situation}

/** Walks `tree` from scratch, answering every park with
  * `Situation.defaultAnswer`, until the tree finishes -- shared by
  * `SetupProcedureSuite` and the slice-3 edifice-power suites, which pass a real `WalkerPowers`
  * (unlike the bare-procedure suite, which passes `WalkerPowers.empty`) so
  * their `ContributingPower`s actually fire.
  */
object SetupWalkDriver extends WalkerRecordedOpsReducer with munit.Assertions:
  def driveToCompletion(ready: ReadyGame, tree: Operation, powers: WalkerPowers)
      : ReadyGame = driveWithEvents(ready, tree, powers)._1

  /** `driveToCompletion`, also returning every event the walk journaled, in
    * walk order, for the suites that read a rule's notes. */
  def driveWithEvents(ready: ReadyGame, tree: Operation, powers: WalkerPowers)
      : (ReadyGame, Vector[OathEvent]) =
    var state = ready
    var journaled = Vector.empty[OathEvent]
    var outcome = ProcedureWalker.advance(state, tree, None, powers).toOption.get
    while outcome.isInstanceOf[WalkerOutcome.Parked] do
      val WalkerOutcome.Parked(pending, events) = outcome: @unchecked
      journaled = journaled ++ events
      state = foldRecordedOps(state, events, "setup walk failed")
      val decide = ProcedureWalker.parkedDecide(state, tree, pending, powers).get
      val park = Park(decide, state, decide.owner,
        TriggeredProcedureRef.Setup)
      val answer = Answered(decide.decisionId, Situation.defaultAnswer
        .applyOrElse(park, _ => fail(
          s"SetupWalkDriver cannot auto-answer ${decide.query}")),
        decide.owner)
      outcome = ProcedureWalker.resolve(state, tree, pending, answer, powers)
        .toOption.get
    val WalkerOutcome.Finished(finished, events) = outcome: @unchecked
    (finished, journaled ++ events)
