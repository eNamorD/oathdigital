package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.walker.{RollPayload, WalkerStepRecorded}
import oathdigital.model._

/** Test-only scaffolding shared by `RecoverProcedureSuite` and
  * `WalkerReplayDriftSuite`: folds a walker command's recorded
  * `WalkerStepRecorded` events into `ReadyGame` state by merging roll
  * outcomes the same way `ProcedureWalker.writeRollOutcome` merges them and
  * otherwise re-applying each step's recorded ops through
  * `OperationPipeline`.
  *
  * This is NOT a replay path: it never calls `ProcedureWalker.applyRecorded`
  * (the actual production replay dispatch `OathRules.evolve` uses) and is
  * never referenced from `src/main`. `RecoverProcedureSuite` uses it to
  * fold a live walk's own recorded steps forward so the next `advance`/
  * `roll`/`resolve` call sees consistent state; `WalkerReplayDriftSuite`
  * uses it for its independent "live" state track, which it then compares
  * against a genuinely different reconstruction — state rebuilt purely via
  * `ProcedureWalker.applyRecorded` — to catch drift between what the walker
  * records and what it would derive again from replayed state. Sharing this
  * reducer does not weaken that comparison: the two tracks in the drift
  * suite are still reconstructed by two different mechanisms
  * (`ProcedureWalker.applyRecorded` vs. this helper); only the scaffolding
  * that two unrelated test suites both happened to reimplement is shared.
  */
private[gameplay] trait WalkerRecordedOpsReducer { self: munit.Assertions =>
  protected def foldRecordedOps(state: ReadyGame, events: Vector[OathEvent],
      failureContext: String): ReadyGame =
    events.foldLeft(state) { (current, event) =>
      val step = event match {
        case recorded: WalkerStepRecorded => recorded
        case other => self.fail(s"expected a WalkerStepRecorded, got $other")
      }
      step.payload match {
        case RollPayload(pool, faces, _) =>
          def derive(all: Vector[DieFace]): (Int, Int) = {
            val attack = all.collect { case face: AttackDieFace => face }
            if (attack.nonEmpty)
              (AttackDieFace.skulls(attack), AttackDieFace.score(attack))
            else (0, DefenseDieFace.score(all.collect {
              case face: DefenseDieFace => face }))
          }
          val (skulls, score) = derive(faces)
          val outcome = RollOutcome(pool, faces.size, faces, skulls, score)
          val accumulated =
            current.game.current.rollOutcomes.get(pool).fold(outcome) {
              previous =>
                val accumulatedFaces = previous.faces ++ outcome.faces
                RollOutcome(pool, previous.count + outcome.count,
                  accumulatedFaces, previous.skulls + outcome.skulls,
                  derive(accumulatedFaces)._2)
            }
          current.updateCurrent(_.copy(rollOutcomes =
              current.game.current.rollOutcomes.updated(pool, accumulated)))
        case _ if step.ops.isEmpty => current
        case _ =>
          OperationPipeline.run(current, step.ops,
            OperationPolicy.Permissive)(Right(_)) match {
            case Right(updated) => updated.state
            case Left(violation) =>
              self.fail(s"$failureContext: $violation")
          }
      }
    }
}
