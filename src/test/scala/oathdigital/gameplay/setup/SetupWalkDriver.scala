package oathdigital.gameplay.setup

import oathdigital.gameplay.WalkerRecordedOpsReducer
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{Park, Situation}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Walks `tree` from scratch, answering every park with
  * `Situation.defaultAnswer`, until the tree finishes -- shared by
  * `SetupProcedureSuite` and the slice-3 edifice-power suites, which pass a real `WalkerPowers`
  * (unlike the bare-procedure suite, which passes `WalkerPowers.empty`) so
  * their `ContributingPower`s actually fire. Each park names `procedure`,
  * the procedure `tree` belongs to, so an answer policy that reads it is
  * told the truth.
  */
object SetupWalkDriver extends WalkerRecordedOpsReducer with munit.Assertions:
  /** The game a walk finished in, and every event it journaled, in walk
    * order. */
  final case class SetupWalked(ready: ReadyGame, events: Vector[OathEvent])

  def driveToCompletion(ready: ReadyGame, tree: Operation,
      procedure: ProcedureRef, powers: WalkerPowers)(
      using munit.Location): ReadyGame =
    driveWithEvents(ready, tree, procedure, powers).ready

  /** `driveToCompletion`, also returning every event the walk journaled, in
    * walk order, for the suites that read a rule's notes. */
  def driveWithEvents(ready: ReadyGame, tree: Operation,
      procedure: ProcedureRef, powers: WalkerPowers)(
      using munit.Location): SetupWalked =
    @annotation.tailrec
    def walk(state: ReadyGame, outcome: WalkerOutcome,
        journaled: Vector[OathEvent]): SetupWalked =
      outcome match
        case WalkerOutcome.Finished(finished, events) =>
          SetupWalked(finished, journaled ++ events)
        case WalkerOutcome.Parked(pending, events) =>
          val next = foldRecordedOps(state, events, "setup walk failed")
          val decide = present(ProcedureWalker.parkedDecide(next, tree, pending,
            powers), "the decision the walk parked on")
          val park = Park(decide, next, decide.owner, procedure)
          val answer = Answered(decide.decisionId,
            Situation.defaultAnswer.applyOrElse(park,
              _ => fail(s"SetupWalkDriver cannot auto-answer ${decide.query}")),
            decide.owner)
          walk(next, accepted(ProcedureWalker.resolve(next, tree, pending,
            answer, powers)), journaled ++ events)
    walk(ready, accepted(ProcedureWalker.advance(ready, tree, None, powers)),
      Vector.empty)
