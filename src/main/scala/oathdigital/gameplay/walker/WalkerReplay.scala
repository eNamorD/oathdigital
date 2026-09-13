package oathdigital.gameplay.walker

import oathdigital.gameplay.{OathState, OathViolation, ReadyGame, WalkerEvent}
import oathdigital.gameplay.operations.OperationExecutor
import oathdigital.model.{Answered, DefenseDieFace, PendingTree,
  RollOutcome}

/** Replay half of the walker: applies durable walker facts to state without
  * ever deriving or walking an action tree (batch-1 Task 5).
  *
  * Split out of `ProcedureWalker.scala`, which Task 5's start-argument
  * threading pushed one line over the project's per-file bound
  * (`BackendArchitectureSuite`'s "all production Scala files stay bounded"),
  * exactly as [[WalkerPowerGather]] was split out for the same reason. The
  * cut is along the seam the walker already had rather than wherever the
  * line count fell: replay applies RECORDED operations and payload state
  * writes and nothing else (spec decision 5), while everything left in
  * `ProcedureWalker` derives, gathers, transforms and executes. Nothing here
  * takes a `WalkerPowers`, and that is the whole distinction -- a replay that
  * could re-gather would let a power edit history.
  *
  * `ProcedureWalker.applyRecorded` remains the public entry point and
  * delegates here, so no caller learns that this file exists.
  */
private[walker] object WalkerReplay {

  def applyRecorded(state: OathState,
      event: WalkerEvent): Either[OathViolation, OathState] = state match {
    case OathState.Ready(ready) => applyRecordedReady(ready, event)
      .map(OathState.Ready)
    case _ => Left(OathViolation.GameNotStarted)
  }

  private def applyRecordedReady(ready: ReadyGame,
      event: WalkerEvent): Either[OathViolation, ReadyGame] = {
    def invalid(detail: String) = Left(OathViolation.InvalidEventOrder(detail))
    def validateStep(step: WalkerStepRecorded)
        : Either[OathViolation, Unit] =
      Either.cond(validNodeId(step.nodeId), (),
        OathViolation.InvalidEventOrder(
          s"invalid walker node id '${step.nodeId}'"))
    def validateParkedStep(step: WalkerStepRecorded)
        : Either[OathViolation, PendingTree] = for {
      _ <- validateStep(step)
      pending <- ready.game.current.walkerPending.toRight(
        OathViolation.InvalidEventOrder(
          "walker step requires a durable pending position"))
      _ <- Either.cond(pending.at.mkString(".") == step.nodeId, (),
        OathViolation.InvalidEventOrder(
          s"walker step ${step.nodeId} does not match pending position " +
            pending.at.mkString(".")))
    } yield pending

    event match {
      // `contributions` is deliberately unmatched (`_`) below: replay applies
      // `ops` only and must never consult which powers produced them (spec
      // decision 5) -- see `WalkerStepRecorded.contributions`'s doc.
      case step @ WalkerStepRecorded(_, RollPayload(pool, faces), ops, _) =>
        for {
          _ <- validateParkedStep(step)
          _ <- Either.cond(ops.isEmpty, (), OathViolation.InvalidEventOrder(
            "recorded RollPayload must not contain operations"))
          count <- ready.game.current.rollPools.get(pool).map(_.count).toRight(
            OathViolation.InvalidEventOrder(
              s"recorded roll references missing pool ${pool.value}"))
          _ <- Either.cond(faces.size == count, (),
            OathViolation.InvalidEventOrder(
              s"recorded roll has ${faces.size} faces but pool count is $count"))
          _ <- Either.cond(faces.forall(_.isInstanceOf[DefenseDieFace]), (),
            OathViolation.InvalidEventOrder(
              "recorded Recover roll contains a non-defense face"))
        } yield ProcedureWalker.writeRollOutcome(ready, RollOutcome(pool,
          count, faces,
          skulls = 0, score = DefenseDieFace.score(faces.collect {
            case face: DefenseDieFace => face
          })))

      case step @ WalkerStepRecorded(_,
          ChoicePayload(decisionId, payload, by), ops, _) =>
        for {
          pending <- validateParkedStep(step)
          _ <- Either.cond(ops.isEmpty, (), OathViolation.InvalidEventOrder(
            "recorded ChoicePayload must not contain operations"))
          answered = pending.copy(answered = pending.answered :+
            Answered(decisionId, payload, by))
        } yield ready.copy(game = ready.game.copy(current =
          ready.game.current.copy(walkerPending = Some(answered))))

      case step @ WalkerStepRecorded(_,
          _: WalkerStepPayload.DeltaRecorded, ops, _) =>
        for {
          _ <- validateStep(step)
          _ <- Either.cond(ops.nonEmpty, (), OathViolation.InvalidEventOrder(
            "recorded delta step must contain operations"))
          updated <- new OperationExecutor().executeAll(ready, ops)
            .left.map(_.toViolation)
        } yield updated

      case WalkerParked(procedure, at, answered, modifiers, startArgs) => for {
        _ <- Either.cond(at.nonEmpty && at.forall(segment =>
          segment.nonEmpty && segment.forall(_.isDigit)), (),
          OathViolation.InvalidEventOrder("invalid durable walker park path"))
        _ <- ready.game.current.walkerProcedure match {
          case Some(existing) => for {
            _ <- Either.cond(existing == procedure, (),
              OathViolation.InvalidEventOrder(
                s"walker procedure ${procedure.key} does not match " +
                  existing.key))
            _ <- Either.cond(ready.game.current.walkerModifiers == modifiers, (),
              OathViolation.InvalidEventOrder(
                "durable walker park modifiers do not match the recorded " +
                  "selection"))
            _ <- Either.cond(
              ready.game.current.walkerStartArgs == startArgs, (),
              OathViolation.InvalidEventOrder(
                "durable walker park start selections do not match the " +
                  "recorded start"))
          } yield ()
          case None => Right(())
        }
        _ <- ready.game.current.walkerPending match {
          case Some(existing) => Either.cond(existing.answered == answered, (),
            OathViolation.InvalidEventOrder(
              "durable walker park answers do not match recorded choices"))
          case None => Either.cond(answered.isEmpty, (),
            OathViolation.InvalidEventOrder(
              "initial durable walker park has unexpected answers"))
        }
      } yield ready.copy(game = ready.game.copy(current =
        ready.game.current.copy(
          walkerPending = Some(PendingTree(at, answered)),
          walkerProcedure = Some(procedure),
          walkerModifiers = modifiers,
          walkerStartArgs = startArgs)))

      case WalkerCompleted(procedure) => for {
        // No active procedure means the walk never parked: a tree that
        // declares no Decide and no Roll runs to the end inside the command
        // that started it, so nothing set `walkerProcedure` (Forge at a
        // single-resource site is exactly that). The completion still names
        // the procedure, and the clear below is a no-op either way.
        _ <- ready.game.current.walkerProcedure match {
          case Some(existing) => Either.cond(existing == procedure, (),
            OathViolation.InvalidEventOrder(
              s"walker completion ${procedure.key} does not match " +
                existing.key))
          case None => Right(())
        }
      } yield ready.copy(game = ready.game.copy(current =
        ready.game.current.copy(
          walkerPending = None,
          walkerProcedure = None,
          walkerModifiers = Vector.empty,
          walkerStartArgs = Vector.empty,
          rollPools = Map.empty,
          rollOutcomes = Map.empty)))

      case step: WalkerStepRecorded =>
        invalid(s"unsupported recorded walker payload ${step.payload.productPrefix}")
      case other =>
        invalid(s"unsupported walker event ${other.productPrefix}")
    }
  }

  private def validNodeId(nodeId: String): Boolean =
    nodeId.nonEmpty && nodeId.split('.').forall(segment =>
      segment.nonEmpty && segment.forall(_.isDigit))
}
