package oathdigital.gameplay.walker

import oathdigital.gameplay.{DiceKind, OathEvent, OathState, OathViolation,
  ReadyGame, WalkerEvent}
import oathdigital.gameplay.operations.{AdjustSupply, Branch, BuildOps,
  CoreOperation, Decide, Location, ModifyDicePool, Move, Operation,
  OperationExecutor, OperationPipeline, OperationPolicy, Piece,
  PositionedLocation, PrimitiveOperation, Repeat, Roll}
import oathdigital.model.{Answered, DefenseDieFace, DieFace,
  PendingTree, PlayerId, PoolKey, RelicId, RollOutcome}
import oathdigital.gameplay.walker.DeltaMeaning.{DicePoolModified,
  OperationApplied, RelicAcquired, SupplySpent}

/** Outcome of one walker `advance`/`roll`/`resolve` command.
  *
  * A walk either runs until it must stop for a human/app decision
  * ([[Parked]]) or consumes the whole action tree ([[Finished]]).
  */
sealed trait WalkerOutcome extends Product with Serializable
object WalkerOutcome {
  /** The walk parked at a `Decide` (a `Roll` park is *resumed* this slice
    * through [[ProcedureWalker.roll]], never through a further `advance`).
    * `tree` is the exact position to resume from on the next command;
    * `events` holds any steps executed before the park in this command. The
    * application appends a [[WalkerParked]] fact after these events so replay
    * can restore the pointer without running the walker.
    */
  final case class Parked(tree: PendingTree, events: Vector[OathEvent])
      extends WalkerOutcome

  /** The whole action tree was consumed. The resulting state no longer
    * carries a pending tree and its dice pools are cleared (brief behavior
    * 6); `events` holds every delta executed by this command. Roll outcomes
    * written during the walk are retained on the finished state for the next
    * resolution step to consume; the application-level [[WalkerCompleted]]
    * fact clears them at the completed action boundary.
    */
  final case class Finished(treeless: ReadyGame, events: Vector[OathEvent])
      extends WalkerOutcome
}

/** Auto-walk engine over an [[Operation]] action tree (Tasks 3-5).
  *
  * Contract (plan-owner rulings + brief):
  *  - `advance(state, action, pending)` walks `action` from `pending.at`
  *    (the caller supplies the tree every command — S1 pending stores only a
  *    pointer); `pending = None` starts fresh at the root.
  *  - `Decide` nodes park; `Roll` nodes park in `advance` (their faces ride
  *    a later command) and are resumed by
  *    `roll(state, action, pending, faces)`: faces are validated against the
  *    parked node's pool count, a `RollOutcome` is merged into
  *    `CurrentGameState.rollOutcomes` for the pool, one [[WalkerStepRecorded]]
  *    with a [[RollPayload]] is appended, and the walk continues from the
  *    node after the Roll exactly like `advance`.
  *  - A parked `Decide` is answered through
  *    `resolve(state, action, pending, answer)`: the parked node's owner and
  *    optional semantic `validate` (Decide.validate) are checked, the answer
  *    is appended to `pending.answered`, ONE [[WalkerStepRecorded]] carrying a
  *    [[ChoicePayload]] (`ops` empty) is recorded, and the walk continues.
  *  - Every other leaf validates + executes via `OperationPipeline`
  *    (`OperationPolicy.Permissive`) and records one [[WalkerStepRecorded]]
  *    per executed leaf. [[BuildOps]] leaves are executed at walk time:
  *    `build(state, pending)` yields the delta batch to run and record (an
  *    empty batch runs nothing and records nothing).
  *  - [[Branch]] composites are resolved at walk time by evaluating
  *    `select(state, pending)` and walking the selected operations as the
  *    branch's children (statically the branch has none).
  *  - `Repeat(guard, body)` runs whole body passes while `guard(state,
  *    pending)` holds; a pass that parks is resumed at the same body point on
  *    the next command and the guard is re-checked only at pass boundaries,
  *    so deltas that already ran (their events are in the journal) are never
  *    re-executed or re-recorded.
  *  - Tree exhausted -> [[WalkerOutcome.Finished]].
  *
  * Roll outcomes: one entry per pool holds the ACCUMULATED outcome of every
  * roll of that pool this action. Faces/count/skulls accumulate across passes;
  * defense score is re-derived from all accumulated faces so a later Doubler
  * also multiplies shields from earlier rolls. A `Repeat` body that rolls the
  * same pool again can therefore score the action cumulatively (Task 5 ruling
  * D). Replay re-derives the same accumulation by merging each recorded
  * `RollPayload` in journal order.
  *
  * Navigation state: `PendingTree.at` is the root-relative child-index path
  * of the parked leaf (`Vector("0","0","1")` = child 0, its child 0, that
  * node's child 1). A `Repeat` contributes its child index like any
  * composite — no iteration marker is needed because the guard is a pure
  * function of state re-evaluated at each pass boundary, and parks inside a
  * body resume at the same structural point regardless of which pass they
  * belong to.
  */
object ProcedureWalker {

  /** Replays one durable walker fact. This path applies recorded operations
    * and payload state writes only; it never derives or walks an action tree.
    */
  def applyRecorded(state: OathState,
      event: WalkerEvent): Either[OathViolation, OathState] = state match {
    case OathState.Ready(ready) => applyRecordedReady(ready, event)
      .map(OathState.Ready)
    case _ => Left(OathViolation.GameNotStarted)
  }

  private def applyRecordedReady(ready: ReadyGame,
      event: WalkerEvent): Either[OathViolation, ReadyGame] = {
    def invalid(detail: String) = Left(OathViolation.InvalidEventOrder(detail))
    def validateActor(actor: PlayerId): Either[OathViolation, Unit] =
      Either.cond(actor == ready.game.current.turn.activePlayer, (),
        OathViolation.WrongPlayer(ready.game.current.turn.activePlayer, actor))
    def validateStep(step: WalkerStepRecorded)
        : Either[OathViolation, Unit] = for {
      _ <- validateActor(step.actor)
      _ <- Either.cond(validNodeId(step.nodeId), (),
        OathViolation.InvalidEventOrder(
          s"invalid walker node id '${step.nodeId}'"))
    } yield ()
    def validateParkedStep(step: WalkerStepRecorded)
        : Either[OathViolation, PendingTree] = for {
      _ <- validateStep(step)
      pending <- ready.game.current.walkerPending.toRight(
        OathViolation.InvalidEventOrder(
          "walker step requires a durable pending position"))
      _ <- Either.cond(pending.actor == step.actor, (),
        OathViolation.WrongPlayer(pending.actor, step.actor))
      _ <- Either.cond(pending.at.mkString(".") == step.nodeId, (),
        OathViolation.InvalidEventOrder(
          s"walker step ${step.nodeId} does not match pending position " +
            pending.at.mkString(".")))
    } yield pending

    event match {
      case step @ WalkerStepRecorded(_, _, RollPayload(pool, faces), ops) =>
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
        } yield writeRollOutcome(ready, RollOutcome(pool, count, faces,
          skulls = 0, score = DefenseDieFace.score(faces.collect {
            case face: DefenseDieFace => face
          })))

      case step @ WalkerStepRecorded(_, _,
          ChoicePayload(decisionId, payload), ops) =>
        for {
          pending <- validateParkedStep(step)
          _ <- Either.cond(ops.isEmpty, (), OathViolation.InvalidEventOrder(
            "recorded ChoicePayload must not contain operations"))
          answered = pending.copy(answered = pending.answered :+
            Answered(decisionId, payload))
        } yield ready.copy(game = ready.game.copy(current =
          ready.game.current.copy(walkerPending = Some(answered))))

      case step @ WalkerStepRecorded(_, _,
          _: WalkerStepPayload.DeltaRecorded, ops) =>
        for {
          _ <- validateStep(step)
          _ <- Either.cond(ops.nonEmpty, (), OathViolation.InvalidEventOrder(
            "recorded delta step must contain operations"))
          updated <- new OperationExecutor().executeAll(ready, ops)
            .left.map(_.toViolation)
        } yield updated

      case WalkerParked(actor, action, at, answered) => for {
        _ <- validateActor(actor)
        _ <- Either.cond(at.nonEmpty && at.forall(segment =>
          segment.nonEmpty && segment.forall(_.isDigit)), (),
          OathViolation.InvalidEventOrder("invalid durable walker park path"))
        _ <- ready.game.current.walkerAction match {
          case Some(existing) => Either.cond(existing == action, (),
            OathViolation.InvalidEventOrder(
              s"walker action ${action.key} does not match ${existing.key}"))
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
          walkerPending = Some(PendingTree(at, answered, actor)),
          walkerAction = Some(action))))

      case WalkerCompleted(actor, action) => for {
        _ <- validateActor(actor)
        _ <- ready.game.current.walkerAction match {
          case Some(existing) => Either.cond(existing == action, (),
            OathViolation.InvalidEventOrder(
              s"walker completion ${action.key} does not match ${existing.key}"))
          case None => invalid("walker completion has no active walker action")
        }
      } yield ready.copy(game = ready.game.copy(current =
        ready.game.current.copy(
          walkerPending = None,
          walkerAction = None,
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

  def advance(state: ReadyGame, action: Operation,
      pending: Option[PendingTree]): Either[OathViolation, WalkerOutcome] = {
    val actor = pending.fold(state.game.current.turn.activePlayer)(_.actor)
    val answered = pending.fold(Vector.empty[Answered])(_.answered)
    val cursor: Option[Vector[String]] = pending.map(_.at)
    // The stored pending tree is navigation state passed by parameter; a
    // running walk must not carry it inside CurrentGameState (dual-pending
    // guard), so clear the stored field before executing deltas.
    val base = strip(state)
    walk(action, WalkCtx(base, Vector.empty, actor, answered), Vector.empty,
      cursor, PlainResume).map(toOutcome)
  }

  /** Resumes the `Roll` park recorded by `advance` (Task 4 roll contract).
    *
    * The resumed node is the leaf addressed by `pending.at` and MUST be a
    * `Roll` (any other park rejects with `OathViolation.InvalidEventOrder`).
    * Semantics: validate `faces.size` against the pool count read from
    * `state.rollPools` for the node's `pool`; on success merge the derived
    * `RollOutcome` into `state.rollOutcomes` (accumulating across repeated
    * rolls of the same pool), append ONE [[WalkerStepRecorded]] carrying
    * [[RollPayload]] (`ops` empty — the outcome is a state write, not a delta
    * batch), then continue auto-walking the tree from the node after the Roll
    * exactly like `advance`: further deltas execute until the next
    * Decide/Roll park (`Parked`) or the tree ends (`Finished`).
    *
    * Only `DiceKind.Defense` rolls are legal this slice: an `Attack` die, a
    * face count differing from the pool count, or a non-`DefenseDieFace` in
    * `faces` each reject with `OathViolation.InvalidEventOrder`.
    */
  def roll(state: ReadyGame, action: Operation, pending: PendingTree,
      faces: Vector[DieFace]): Either[OathViolation, WalkerOutcome] = {
    val base = strip(state)
    walk(action, WalkCtx(base, Vector.empty, pending.actor, pending.answered),
      Vector.empty, Some(pending.at), RollResume(faces)).map(toOutcome)
  }

  /** Resolves the `Decide` park recorded by `advance`/`roll` (Task 5 ruling
    * 5.3).
    *
    * The resumed node is the leaf addressed by `pending.at` and MUST be a
    * `Decide` whose `decisionId` equals `answer.decisionId` (a Roll park, a
    * mismatched decision id, or any other position rejects with an
    * `OathViolation`). Semantics: check the node's `owner` resolves to the
    * acting player, run the node's optional `validate(state, pending,
    * payload)` against `answer.payload` when present, append ONE
    * [[WalkerStepRecorded]] carrying [[ChoicePayload]] (`ops` empty — the
    * answer is a state write into `pending.answered`, not a delta batch), add
    * `answer` to `answered`, then continue auto-walking from the node after
    * the Decide exactly like `advance` (so `resolve` may park again at the
    * next Roll/Decide or finish the tree).
    */
  def resolve(state: ReadyGame, action: Operation, pending: PendingTree,
      answer: Answered): Either[OathViolation, WalkerOutcome] = {
    val base = strip(state)
    walk(action, WalkCtx(base, Vector.empty, pending.actor, pending.answered),
      Vector.empty, Some(pending.at), AnswerResume(answer)).map(toOutcome)
  }

  /** When `pending` parks on a `Roll` node of `action`, reports the node's
    * `pool` and the face count that node requires (read from
    * `state.rollPools`), so the app layer can pre-roll exactly that many
    * faces for the next `roll` command (Task 6 consumes it). `None` when the
    * park is a Decide or the position does not resolve to a Roll.
    */
  def parkedRoll(state: ReadyGame, action: Operation,
      pending: PendingTree): Option[(PoolKey, Int)] =
    leafAt(state, action, pending).collect {
      case Roll(pool, _) => (pool, poolCount(state, pool))
    }

  /** When `pending` parks on a `Decide` node of `action`, reports the node
    * itself so the caller can dispatch on its stable `decisionId` (e.g. to
    * pick the right `OathContinue` prompt) rather than on the park's
    * structural path, which shifts if the tree is edited. Symmetric to
    * [[parkedRoll]]. `None` when the park is a Roll or the position does not
    * resolve to a Decide.
    */
  def parkedDecide(state: ReadyGame, action: Operation,
      pending: PendingTree): Option[Decide] =
    leafAt(state, action, pending).collect {
      case decide: Decide => decide
    }

  private def poolCount(state: ReadyGame, pool: PoolKey): Int =
    state.game.current.rollPools.get(pool).fold(0)(_.count)

  // --------------------------------------------------------------------------
  // Walking core
  // --------------------------------------------------------------------------

  /** Command-local walk state: the state threaded through executed deltas,
    * the events recorded so far, the acting player, and every decision
    * already answered during this action.
    */
  private final case class WalkCtx(
      state: ReadyGame,
      events: Vector[OathEvent],
      actor: PlayerId,
      answered: Vector[Answered]
  )

  private sealed trait Step extends Product with Serializable
  /** The walked region completed; `ctx` carries the result state/events. */
  private final case class Done(ctx: WalkCtx) extends Step
  /** A park bubbled up from a Decide/Roll leaf at `position`. */
  private final case class Park(position: Vector[String], ctx: WalkCtx)
      extends Step

  /** How a resumed command treats the leaf parked at `pending.at`: a plain
    * `advance` re-parks an unanswered Decide/Roll (or passes a Decide the
    * caller already recorded in `answered`); a `roll` consumes the parked
    * Roll (validating faces, merging the outcome, recording the event); a
    * `resolve` consumes the parked Decide (validating the answer, recording
    * the ChoicePayload event) — both then walk on from the following node.
    */
  private sealed trait Resume extends Product with Serializable
  private case object PlainResume extends Resume
  private final case class RollResume(faces: Vector[DieFace]) extends Resume
  private final case class AnswerResume(answer: Answered) extends Resume

  private def toOutcome(step: Step): WalkerOutcome = step match {
    case Done(ctx) =>
      WalkerOutcome.Finished(finish(ctx), ctx.events)
    case Park(position, ctx) =>
      WalkerOutcome.Parked(PendingTree(at = position,
        answered = ctx.answered, actor = ctx.actor), ctx.events)
  }

  private def strip(state: ReadyGame): ReadyGame =
    state.copy(game = state.game.copy(current =
      state.game.current.copy(walkerPending = None, walkerAction = None)))

  private def finish(ctx: WalkCtx): ReadyGame =
    ctx.state.copy(game = ctx.state.game.copy(current =
      ctx.state.game.current.copy(walkerPending = None,
        rollPools = Map.empty, walkerAction = None)))

  private def contractViolation(message: String): Left[OathViolation, Nothing] =
    Left(OathViolation.InvalidEventOrder(s"walker contract violation: $message"))

  /** Case-class short name used by the generic semantic fallback. */
  private def leafLabel(node: Operation): String = node match {
    case product: Product => product.productPrefix
    case other => other.getClass.getSimpleName
  }

  /** Walks `node` from `path` (its root-relative child-index address), either
    * from scratch (`cursor = None`) or resuming at `cursor` (the remaining
    * child-index segments from this node down to the parked leaf).
    */
  private def walk(node: Operation, ctx: WalkCtx, path: Vector[String],
      cursor: Option[Vector[String]],
      resume: Resume): Either[OathViolation, Step] =
    node match {
      case repeat: Repeat => walkRepeat(repeat, ctx, path, cursor, resume)
      case branch: Branch => walkBranch(branch, ctx, path, cursor, resume)
      case leaf: PrimitiveOperation => walkLeaf(leaf, ctx, path, cursor, resume)
      case composite => walkChildren(composite.children, ctx, path, cursor,
        resume)
    }

  /** A `Branch` has no static children: its `select` chooses the children to
    * walk at walk time, and a resume cursor addresses the selected vector the
    * same way it would address static children (the branch's position is
    * `path`; the parked child index heads the remaining cursor segments).
    */
  private def walkBranch(branch: Branch, ctx: WalkCtx, path: Vector[String],
      cursor: Option[Vector[String]],
      resume: Resume): Either[OathViolation, Step] = {
    val branchTree = PendingTree(at = path, answered = ctx.answered,
      actor = ctx.actor)
    val selected = branch.select(ctx.state, branchTree)
    walkChildren(selected, ctx, path, cursor, resume)
  }

  /** Runs whole passes of `repeat.body` while its guard holds, plus, on a
    * resume, the remainder of the already-started pass first.
    */
  private def walkRepeat(repeat: Repeat, ctx: WalkCtx, path: Vector[String],
      cursor: Option[Vector[String]],
      resume: Resume): Either[OathViolation, Step] = {
    // A Repeat exposes its body as its single child, so body passes live at
    // child index 0 of this node's path.
    val bodyPath = path :+ "0"

    /** One guard check then a full body pass; repeats while the guard holds.
      * The guard is pure and re-evaluated against the state reached by the
      * previous pass, so it never needs an explicit iteration counter.
      */
    def passes(current: WalkCtx): Either[OathViolation, Step] = {
      val guardTree = PendingTree(at = path, answered = current.answered,
        actor = current.actor)
      if (!repeat.guard(current.state, guardTree)) Right(Done(current))
      else
        walk(repeat.body, current, bodyPath, None, resume).flatMap {
          case park: Park => Right(park)
          case Done(next) => passes(next)
        }
    }

    cursor match {
      case None =>
        passes(ctx)
      case Some(remaining) =>
        if (!remaining.headOption.contains("0"))
          contractViolation(
            "resume path into a Repeat must address its body (child 0)")
        else
          // The current pass already started (its guard was true at pass time);
          // finish its remainder, then keep looping whole passes.
          walk(repeat.body, ctx, bodyPath, Some(remaining.tail), resume).flatMap {
            case park: Park => Right(park)
            case Done(next) => passes(next)
          }
    }
  }

  private def walkLeaf(leaf: PrimitiveOperation, ctx: WalkCtx,
      path: Vector[String], cursor: Option[Vector[String]],
      resume: Resume): Either[OathViolation, Step] =
    cursor match {
      case Some(remaining) =>
        if (remaining.nonEmpty)
          contractViolation(
            s"resume path $remaining overruns leaf ${leafLabel(leaf)}")
        else resume match {
          case RollResume(faces) =>
            leaf match {
              case roll: Roll => recordRoll(roll, ctx, path, faces).map(Done(_))
              case _: Decide => Left(OathViolation.InvalidEventOrder(
                s"roll() resumed at a ${leafLabel(leaf)} park; " +
                  "expected a Roll"))
              case other =>
                contractViolation(s"resume position ${path.mkString(".")} " +
                  s"is not a Decide/Roll park (leaf ${leafLabel(other)})")
            }
          case AnswerResume(answer) =>
            leaf match {
              case decide: Decide if decide.decisionId == answer.decisionId =>
                answerDecide(decide, ctx, path, answer).map(Done(_))
              case decide: Decide => Left(OathViolation.InvalidEventOrder(
                s"resolve() answer ${answer.decisionId} does not match the " +
                  s"parked Decide ${decide.decisionId} at " +
                  path.mkString(".")))
              case roll: Roll => Left(OathViolation.InvalidEventOrder(
                s"resolve() resumed at a Roll park; expected a Decide"))
              case other =>
                contractViolation(s"resume position ${path.mkString(".")} " +
                  s"is not a Decide/Roll park (leaf ${leafLabel(other)})")
            }
          case PlainResume =>
            leaf match {
              case _: Decide | _: Roll =>
                // A plain advance never consumes a park. In particular, a
                // repeated Decide can reuse its stable decision ID across
                // passes, so older answers must not make a fresh pass skip it.
                // Decide and Roll parks resume only through `resolve`/`roll`.
                Right(Park(path, ctx))
              case _ =>
                contractViolation(s"resume position ${path.mkString(".")} " +
                  s"is not a Decide/Roll park (leaf ${leafLabel(leaf)})")
            }
        }
      case None =>
        leaf match {
          case _: Decide | _: Roll => Right(Park(path, ctx))
          case build: BuildOps => runBuildOps(build, ctx, path).map(Done(_))
          case delta => record(delta, ctx, path).map(Done(_))
        }
    }

  /** Walks `children` in order, skipping children already executed before a
    * resume point (`cursor` heads the index of the resumed child inside this
    * node's children).
    */
  private def walkChildren(children: Vector[Operation], ctx: WalkCtx,
      path: Vector[String], cursor: Option[Vector[String]],
      resume: Resume): Either[OathViolation, Step] =
    cursor match {
      case None => continue(children, 0, ctx, path, None, resume)
      case Some(remaining) =>
        remaining.headOption match {
          case None => contractViolation(
            "resume path ends at a composite node; only Decide/Roll parks resume")
          case Some(segment) => segment.toIntOption match {
            case None => contractViolation(s"invalid resume path segment '$segment'")
            case Some(index) if index < 0 || index >= children.size =>
              contractViolation(
                s"resume path segment '$segment' out of range for a node with " +
                  s"${children.size} children")
            case Some(index) =>
              // Children before `index` already ran in an earlier command;
              // start at `index` with the cursor consumed past this level.
              continue(children, index, ctx, path, Some(remaining.tail), resume)
          }
        }
    }

  private def continue(children: Vector[Operation], index: Int, ctx: WalkCtx,
      path: Vector[String], cursorAt: Option[Vector[String]],
      resume: Resume): Either[OathViolation, Step] =
    if (index >= children.size) Right(Done(ctx))
    else
      walk(children(index), ctx, path :+ index.toString, cursorAt, resume)
        .flatMap {
          case park: Park => Right(park)
          case Done(next) => continue(children, index + 1, next, path, None,
            resume)
        }

  /** Executes one delta leaf through the pipeline and records its step. */
  private def record(delta: CoreOperation, ctx: WalkCtx,
      path: Vector[String]): Either[OathViolation, WalkCtx] =
    OperationPipeline.run(ctx.state, Vector(delta),
      OperationPolicy.Permissive)(Right(_)).map { updated =>
      val nodeId =
        if (path.isEmpty) leafLabel(delta) else path.mkString(".")
      ctx.copy(
        state = updated,
        events = ctx.events :+ WalkerStepRecorded(
          actor = ctx.actor,
          nodeId = nodeId,
          payload = WalkerStepPayload.DeltaRecorded(
            deltaMeaning(Vector(delta), leafLabel(delta))),
          ops = Vector(delta)))
    }

  /** Executes a [[BuildOps]] leaf: `build(state, pending)` returns the delta
    * batch to run through the pipeline, recorded as the node's step ops. An
    * empty batch runs nothing and records nothing (the node produced no
    * state change).
    */
  private def runBuildOps(build: BuildOps, ctx: WalkCtx,
      path: Vector[String]): Either[OathViolation, WalkCtx] = {
    val tree = PendingTree(at = path, answered = ctx.answered,
      actor = ctx.actor)
    build.build(ctx.state, tree).flatMap { ops =>
      if (ops.isEmpty) Right(ctx)
      else
        OperationPipeline.run(ctx.state, ops, OperationPolicy.Permissive)(
          Right(_)).map { updated =>
          val nodeId =
            if (path.isEmpty) leafLabel(build) else path.mkString(".")
          ctx.copy(
            state = updated,
            events = ctx.events :+ WalkerStepRecorded(
              actor = ctx.actor,
              nodeId = nodeId,
              payload = WalkerStepPayload.DeltaRecorded(
                deltaMeaning(ops, leafLabel(build))),
              ops = ops))
        }
    }
  }

  private def deltaMeaning(ops: Vector[CoreOperation],
      fallback: String): DeltaMeaning = ops match {
    case Vector(ModifyDicePool(pool, delta)) =>
      DicePoolModified(pool, delta)
    case Vector(AdjustSupply(player, amount)) if amount < 0 =>
      SupplySpent(player, -amount)
    case Vector(Move(Piece.Card(relic: RelicId),
        PositionedLocation(Location.Site(site), _),
        PositionedLocation(Location.PlayArea(player), _), _)) =>
      RelicAcquired(player, relic, site)
    case _ => OperationApplied(fallback)
  }

  /** Validates a resolved answer against the parked Decide and records its
    * step: the owner must resolve to the acting player, and the node's
    * optional `validate` must accept the payload; on success `answer` is
    * appended to `answered` and ONE [[WalkerStepRecorded]] carrying a
    * [[ChoicePayload]] (ops empty — the answer is a state write into
    * `pending.answered`) is appended.
    */
  private def answerDecide(decide: Decide, ctx: WalkCtx,
      path: Vector[String], answer: Answered): Either[OathViolation, WalkCtx] = {
    val parkTree = PendingTree(at = path, answered = ctx.answered,
      actor = ctx.actor)
    for {
      _ <- decide.owner.owner(WalkerCtx(ctx.state)) match {
        case None => Left(OathViolation.InvalidEventOrder(
          s"decision ${decide.decisionId} at ${path.mkString(".")} has no " +
            "resolving owner"))
        case Some(owner) => Either.cond(owner == ctx.actor, (),
          OathViolation.WrongPlayer(owner, ctx.actor))
      }
      _ <- decide.validate.fold[Either[OathViolation, Unit]](
        Right(()))(_(ctx.state, parkTree, answer.payload))
    } yield {
      val nodeId =
        if (path.isEmpty) leafLabel(decide) else path.mkString(".")
      ctx.copy(
        answered = ctx.answered :+ answer,
        events = ctx.events :+ WalkerStepRecorded(
          actor = ctx.actor,
          nodeId = nodeId,
          payload = ChoicePayload(answer.decisionId, answer.payload),
          ops = Vector.empty))
    }
  }

  /** Records one roll step: validates the resumed Roll's die kind (defense
    * only this slice), the face count vs the pool count, and that every face
    * is a [[DefenseDieFace]]; merges the derived [[RollOutcome]] into
    * `ctx.state` (accumulating across rolls of the same pool) and appends the
    * [[WalkerStepRecorded]] carrying a [[RollPayload]] (with no ops — the
    * outcome is a state write).
    */
  private def recordRoll(roll: Roll, ctx: WalkCtx, path: Vector[String],
      faces: Vector[DieFace]): Either[OathViolation, WalkCtx] = {
    val pool = roll.pool
    for {
      _ <- roll.dice.die match {
        case DiceKind.Attack => Left(OathViolation.InvalidEventOrder(
          "attack dice not supported in this slice"))
        case DiceKind.Defense => Right(())
      }
      count = poolCount(ctx.state, pool)
      _ <- Either.cond(faces.size == count, (),
        OathViolation.InvalidEventOrder(
          s"rolled ${faces.size} dice for pool $pool but pool count is $count"))
      _ <- Either.cond(faces.forall(_.isInstanceOf[DefenseDieFace]), (),
        OathViolation.InvalidEventOrder(
          s"defense roll for pool $pool received a non-defense die face"))
    } yield {
      val nodeId =
        if (path.isEmpty) leafLabel(roll) else path.mkString(".")
      val score = DefenseDieFace.score(faces.collect {
        case face: DefenseDieFace => face
      })
      ctx.copy(
        state = writeRollOutcome(ctx.state, RollOutcome(pool, count, faces,
          skulls = 0, score)),
        events = ctx.events :+ WalkerStepRecorded(actor = ctx.actor,
          nodeId = nodeId, payload = RollPayload(pool, faces),
          ops = Vector.empty))
    }
  }

  /** Merges `outcome` into the pool's accumulated roll entry: repeated rolls
    * of one pool (e.g. a Recover `Repeat` re-rolling "recover") accumulate
    * faces/count/skulls and re-score all defense faces together. Re-scoring is
    * required because each Doubler multiplies shields from every accumulated
    * roll, not only the roll containing that Doubler.
    */
  private def writeRollOutcome(ready: ReadyGame, outcome: RollOutcome): ReadyGame = {
    val accumulated = ready.game.current.rollOutcomes.get(outcome.pool)
      .fold(outcome) { previous =>
        val faces = previous.faces ++ outcome.faces
        RollOutcome(outcome.pool,
          count = previous.count + outcome.count,
          faces = faces,
          skulls = previous.skulls + outcome.skulls,
          score = DefenseDieFace.score(faces.collect {
            case face: DefenseDieFace => face
          }))
      }
    ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      rollOutcomes = ready.game.current.rollOutcomes
        .updated(outcome.pool, accumulated))))
  }

  /** Resolves the node addressed by `pending.at` (a child-index path rooted
    * at `action`), or `None` when a segment is non-numeric or out of range (a
    * fabricated or stale position). A [[Branch]] encountered along the path
    * is resolved the same way a live walk would reach it: `select(state, ...)`
    * is evaluated (with `at` set to the path consumed so far, matching
    * `walkBranch`'s `branchTree`) to get its current children before
    * indexing into the next path segment, so a park inside a Branch's
    * dynamically-selected children (e.g. Recover's success-only relic
    * decision) resolves to the real node instead of `None`.
    */
  private def leafAt(state: ReadyGame, action: Operation,
      pending: PendingTree): Option[Operation] =
    resolveAt(state, pending, action, pending.at, Vector.empty)

  private def resolveAt(state: ReadyGame, pending: PendingTree,
      node: Operation, remaining: Vector[String],
      consumed: Vector[String]): Option[Operation] =
    remaining.headOption match {
      case None => Some(node)
      case Some(segment) => segment.toIntOption.flatMap { index =>
        val children = node match {
          case branch: Branch =>
            branch.select(state, pending.copy(at = consumed))
          case other => other.children
        }
        if (index >= 0 && index < children.size)
          resolveAt(state, pending, children(index), remaining.tail,
            consumed :+ segment)
        else None
      }
    }
}
