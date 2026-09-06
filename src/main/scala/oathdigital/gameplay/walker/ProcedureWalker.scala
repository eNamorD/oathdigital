package oathdigital.gameplay.walker

import oathdigital.gameplay.{DiceKind, OathEvent, OathViolation, ReadyGame}
import oathdigital.gameplay.operations.{CoreOperation, Decide, Operation,
  OperationPipeline, OperationPolicy, PrimitiveOperation, Repeat, Roll}
import oathdigital.model.{DefenseDieFace, DieFace, PendingTree, PlayerId,
  PoolKey, RollOutcome}

/** Outcome of one walker `advance`/`roll` command.
  *
  * A walk either runs until it must stop for a human/app decision
  * ([[Parked]]) or consumes the whole action tree ([[Finished]]).
  */
sealed trait WalkerOutcome extends Product with Serializable
object WalkerOutcome {
  /** The walk parked at a `Decide` (a `Roll` park is *resumed* this slice
    * through [[ProcedureWalker.roll]], never through a further `advance`).
    * `tree` is the exact position to resume from on the next command;
    * `events` holds any auto-deltas already executed before the park in this
    * command (the caller folds them into state — see the suite's
    * `applyEvents`). No events are recorded for the parked node itself.
    */
  final case class Parked(tree: PendingTree, events: Vector[OathEvent])
      extends WalkerOutcome

  /** The whole action tree was consumed. The resulting state no longer
    * carries a pending tree and its dice pools are cleared (brief behavior
    * 6); `events` holds every delta executed by this command. Roll outcomes
    * written during the walk are retained on the finished state for the next
    * resolution step to consume.
    */
  final case class Finished(treeless: ReadyGame, events: Vector[OathEvent])
      extends WalkerOutcome
}

/** Auto-walk engine over an [[Operation]] action tree (Tasks 3-4).
  *
  * Contract (plan-owner rulings + brief):
  *  - `advance(state, action, pending)` walks `action` from `pending.at`
  *    (the caller supplies the tree every command — S1 pending stores only a
  *    pointer); `pending = None` starts fresh at the root.
  *  - `Decide` nodes park; `Roll` nodes park in `advance` (their faces ride
  *    a later command) and are resumed by
  *    `roll(state, action, pending, faces)`: faces are validated against the
  *    parked node's pool count, a `RollOutcome` is written into state, one
  *    [[WalkerStepRecorded]] with a [[RollPayload]] is appended, and the walk
  *    continues from the node after the Roll exactly like `advance`.
  *  - Every other leaf validates + executes via `OperationPipeline`
  *    (`OperationPolicy.Permissive`) and records one [[WalkerStepRecorded]]
  *    per executed leaf.
  *  - `Repeat(guard, body)` runs whole body passes while `guard(state,
  *    pending)` holds; a pass that parks is resumed at the same body point on
  *    the next command and the guard is re-checked only at pass boundaries,
  *    so deltas that already ran (their events are in the journal) are never
  *    re-executed or re-recorded.
  *  - Tree exhausted -> [[WalkerOutcome.Finished]].
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

  def advance(state: ReadyGame, action: Operation,
      pending: Option[PendingTree]): Either[OathViolation, WalkerOutcome] = {
    val actor = pending.fold(state.game.current.turn.activePlayer)(_.actor)
    val answered = pending.fold(Vector.empty[String])(_.answered)
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
    * `state.rollPools` for the node's `pool`; on success write the derived
    * `RollOutcome` into `state.rollOutcomes`, append ONE
    * [[WalkerStepRecorded]] carrying [[RollPayload]] (`ops` empty — the
    * outcome is a state write, not a delta batch), then continue auto-walking
    * the tree from the node after the Roll exactly like `advance`: further
    * deltas execute until the next Decide/Roll park (`Parked`) or the tree
    * ends (`Finished`).
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

  /** When `pending` parks on a `Roll` node of `action`, reports the node's
    * `pool` and the face count that node requires (read from
    * `state.rollPools`), so the app layer can pre-roll exactly that many
    * faces for the next `roll` command (Task 6 consumes it). `None` when the
    * park is a Decide or the position does not resolve to a Roll.
    */
  def parkedRoll(state: ReadyGame, action: Operation,
      pending: PendingTree): Option[(PoolKey, Int)] =
    leafAt(action, pending.at).collect {
      case Roll(pool, _) => (pool, poolCount(state, pool))
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
      answered: Vector[String]
  )

  private sealed trait Step extends Product with Serializable
  /** The walked region completed; `ctx` carries the result state/events. */
  private final case class Done(ctx: WalkCtx) extends Step
  /** A park bubbled up from a Decide/Roll leaf at `position`. */
  private final case class Park(position: Vector[String], ctx: WalkCtx)
      extends Step

  /** How a resumed command treats the leaf parked at `pending.at`: a plain
    * `advance` re-parks an unanswered Decide/Roll; a `roll` consumes the
    * parked Roll (validating faces, writing the outcome, recording the
    * event) and walks on from the following node.
    */
  private sealed trait Resume extends Product with Serializable
  private case object PlainResume extends Resume
  private final case class RollResume(faces: Vector[DieFace]) extends Resume

  private def toOutcome(step: Step): WalkerOutcome = step match {
    case Done(ctx) =>
      WalkerOutcome.Finished(finish(ctx), ctx.events)
    case Park(position, ctx) =>
      WalkerOutcome.Parked(PendingTree(at = position,
        answered = ctx.answered, actor = ctx.actor), ctx.events)
  }

  private def strip(state: ReadyGame): ReadyGame =
    state.copy(game = state.game.copy(current =
      state.game.current.copy(walkerPending = None)))

  private def finish(ctx: WalkCtx): ReadyGame =
    ctx.state.copy(game = ctx.state.game.copy(current =
      ctx.state.game.current.copy(walkerPending = None,
        rollPools = Map.empty)))

  private def contractViolation(message: String): Nothing =
    throw new IllegalArgumentException(s"walker contract violation: $message")

  /** Case-class short name used for the placeholder payload label. */
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
      case leaf: PrimitiveOperation => walkLeaf(leaf, ctx, path, cursor, resume)
      case composite => walkChildren(composite.children, ctx, path, cursor,
        resume)
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
        require(remaining.headOption.contains("0"),
          "resume path into a Repeat must address its body (child 0)")
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
        require(remaining.isEmpty,
          s"resume path $remaining overruns leaf ${leafLabel(leaf)}")
        resume match {
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
          case PlainResume =>
            leaf match {
              case decide: Decide if ctx.answered.contains(decide.decisionId) =>
                // Already parked on and answered: skip past it.
                Right(Done(ctx))
              case _: Decide | _: Roll =>
                // Unanswered (or a Roll a plain advance never resumes past):
                // park; Roll parks resume through `roll`.
                Right(Park(path, ctx))
              case _ =>
                contractViolation(s"resume position ${path.mkString(".")} " +
                  s"is not a Decide/Roll park (leaf ${leafLabel(leaf)})")
            }
        }
      case None =>
        leaf match {
          case _: Decide | _: Roll => Right(Park(path, ctx))
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
        require(remaining.nonEmpty,
          "resume path ends at a composite node; only Decide/Roll parks resume")
        val segment = remaining.head
        require(segment.nonEmpty && segment.forall(_.isDigit),
          s"invalid resume path segment '$segment'")
        val index = segment.toInt
        require(index >= 0 && index < children.size,
          s"resume path segment '$segment' out of range for a node with " +
            s"${children.size} children")
        // Children before `index` already ran in an earlier command; start at
        // `index` with the cursor consumed past this level.
        continue(children, index, ctx, path, Some(remaining.tail), resume)
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
          payload = WalkerStepPayload.DeltaRecorded(leafLabel(delta)),
          ops = Vector(delta)))
    }

  /** Records one roll step: validates the resumed Roll's die kind (defense
    * only this slice), the face count vs the pool count, and that every face
    * is a [[DefenseDieFace]]; writes the derived [[RollOutcome]] into
    * `ctx.state` and appends the [[WalkerStepRecorded]] carrying a
    * [[RollPayload]] (with no ops — the outcome is a state write).
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

  private def writeRollOutcome(ready: ReadyGame, outcome: RollOutcome): ReadyGame =
    ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      rollOutcomes = ready.game.current.rollOutcomes
        .updated(outcome.pool, outcome))))

  /** Resolves the node addressed by a `PendingTree.at` child-index path, or
    * `None` when a segment is non-numeric or out of range (a fabricated or
    * stale position).
    */
  private def leafAt(node: Operation, path: Vector[String]): Option[Operation] =
    path.headOption match {
      case None => Some(node)
      case Some(segment) if segment.nonEmpty && segment.forall(_.isDigit) =>
        val children = node.children
        val index = segment.toInt
        if (index >= 0 && index < children.size)
          leafAt(children(index), path.tail)
        else None
      case _ => None
    }
}
