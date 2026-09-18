package oathdigital.gameplay.walker

import oathdigital.gameplay.DiceKind
import oathdigital.gameplay.operations.{SpendSupply, Branch, BuildOps,
  CoreOperation, Decide, Location, ModifyDicePool, Move, Operation,
  OperationPipeline, OperationPolicy, OperationRestriction, Piece,
  PositionedLocation, PrimitiveOperation, Repeat, Roll}
import oathdigital.gameplay.powerresolver.{ContributingPower, PowerResolution,
  PowerWindow}
import oathdigital.model.{Answered, DefenseDieFace, DieFace, OathEvent, OathState, OathViolation, PendingTree, PlayerId, PoolKey, PowerId, ReadyGame, RelicId, RollOutcome, WalkerEvent}
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

/** The powers available to one `advance`/`roll`/`resolve` command (Task 3).
  * `OathRules` supplies it at command entry; `applyRecorded` (replay) never
  * takes one -- replay applies recorded ops only (spec decision 5) and must
  * never re-gather or re-transform.
  */
final case class WalkerPowers(powers: Vector[ContributingPower])
object WalkerPowers {
  val empty: WalkerPowers = WalkerPowers(Vector.empty)

  /** Powers offered to one command out of a full catalog: an `Automatic`
    * power fires unconditionally; a `PlayerSelected` power fires only when
    * its id appears in `modifiers`. Shared by `OathRules.walkerPowers`
    * (command time) and `WalkerDecisionProjector` (park-time projection) so
    * both always fold a shared window identically (Task 5 projector seam).
    */
  def selected(catalog: WalkerPowers, modifiers: Vector[PowerId]): WalkerPowers =
    WalkerPowers(catalog.powers.filter(power =>
      power.resolution == PowerResolution.Automatic ||
        modifiers.contains(power.id)))
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
    * and payload state writes only; it never derives or walks an action tree
    * -- see [[WalkerReplay]], which holds the whole of it (split out to keep
    * this file under the project's line bound, like [[WalkerPowerGather]]).
    */
  def applyRecorded(state: OathState,
      event: WalkerEvent): Either[OathViolation, OathState] =
    WalkerReplay.applyRecorded(state, event)


  def advance(state: ReadyGame, action: Operation,
      pending: Option[PendingTree], powers: WalkerPowers)
      : Either[OathViolation, WalkerOutcome] = {
    val activePlayer = state.game.current.turn.activePlayer
    val answered = pending.fold(Vector.empty[Answered])(_.answered)
    val cursor: Option[Vector[String]] = pending.map(_.at)
    // The stored pending tree is navigation state passed by parameter; a
    // running walk must not carry it inside CurrentGameState (dual-pending
    // guard), so clear the stored field before executing deltas.
    val base = strip(state)
    walk(action, WalkCtx(base, Vector.empty, activePlayer, answered, powers),
      Vector.empty, cursor, PlainResume, WalkerHooks.none).map(toOutcome)
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
      faces: Vector[DieFace], powers: WalkerPowers)
      : Either[OathViolation, WalkerOutcome] = {
    val base = strip(state)
    walk(action, WalkCtx(base, Vector.empty,
      state.game.current.turn.activePlayer, pending.answered, powers),
      Vector.empty, Some(pending.at), RollResume(faces), WalkerHooks.none)
      .map(toOutcome)
  }

  /** Resolves the `Decide` park recorded by `advance`/`roll` (Task 5 ruling
    * 5.3).
    *
    * The resumed node is the leaf addressed by `pending.at` and MUST be a
    * `Decide` whose `decisionId` equals `answer.decisionId` (a Roll park, a
    * mismatched decision id, or any other position rejects with an
    * `OathViolation`). Semantics: check the answer's submitter against the
    * node's `owner`, validate the submitted answer against its query, append
    * ONE [[WalkerStepRecorded]] carrying [[ChoicePayload]] (`ops` empty — the
    * answer is a state write into `pending.answered`, not a delta batch), add
    * `answer` to `answered`, then continue auto-walking from the node after
    * the Decide exactly like `advance` (so `resolve` may park again at the
    * next Roll/Decide or finish the tree).
    */
  def resolve(state: ReadyGame, action: Operation, pending: PendingTree,
      answer: Answered, powers: WalkerPowers)
      : Either[OathViolation, WalkerOutcome] = {
    val base = strip(state)
    walk(action, WalkCtx(base, Vector.empty,
      state.game.current.turn.activePlayer, pending.answered, powers),
      Vector.empty, Some(pending.at), AnswerResume(answer), WalkerHooks.none)
      .map(toOutcome)
  }

  /** Collects every restriction violation from every windowed node in `tree`
    * (spec decision 9's `Restriction` kind), run against the tree root --
    * `OathRules` calls this once per command, before the walk (Task 3
    * wiring rule). See [[WalkerPowerGather.restrictionViolations]] for the
    * traversal (split out to keep this file under the project's line bound).
    */
  def restrictionViolations(tree: Operation, powers: WalkerPowers,
      state: ReadyGame, activePlayer: PlayerId): Vector[OathViolation] =
    WalkerPowerGather.restrictionViolations(tree, powers, state, activePlayer)

  /** When `pending` parks on a `Roll` node of `action`, reports the node's
    * `pool` and the face count that node requires (read from
    * `state.rollPools`), so the app layer can pre-roll exactly that many
    * faces for the next `roll` command (Task 6 consumes it). `None` when the
    * park is a Decide or the position does not resolve to a Roll. `powers`
    * (fix-round ruling J) must be the same vector the live walk that parked
    * here used -- `leafAt` folds every window on the path exactly like the
    * walk does, so a transform that inserts operations around a windowed
    * node does not make this address the wrong leaf.
    */
  def parkedRoll(state: ReadyGame, action: Operation,
      pending: PendingTree, powers: WalkerPowers): Option[(PoolKey, Int)] =
    WalkerPowerGather.leafAt(state, action, pending, powers).collect {
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
      pending: PendingTree, powers: WalkerPowers): Option[Decide] =
    WalkerPowerGather.leafAt(state, action, pending, powers).collect {
      case decide: Decide => decide
    }

  /** Who a parked position waits on: a parked `Decide`'s owner, read off the
    * rebuilt and transformed node, or the active player for a parked `Roll`.
    * Never stored -- a power that changes an owner changes this answer on the
    * next command, and authorization, projection and continuation all read
    * it (Task 5).
    */
  def awaitedPlayer(state: ReadyGame, action: Operation, pending: PendingTree,
      powers: WalkerPowers): Option[PlayerId] =
    parkedDecide(state, action, pending, powers).map(_.owner).orElse(
      parkedRoll(state, action, pending, powers).map(_ =>
        state.game.current.turn.activePlayer))

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
      activePlayer: PlayerId,
      answered: Vector[Answered],
      powers: WalkerPowers
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
        answered = ctx.answered), ctx.events)
  }

  private def strip(state: ReadyGame): ReadyGame =
    state.copy(game = state.game.copy(current =
      state.game.current.copy(walkerPending = None, walkerProcedure = None)))

  private def finish(ctx: WalkCtx): ReadyGame =
    ctx.state.copy(game = ctx.state.game.copy(current =
      ctx.state.game.current.copy(walkerPending = None,
        rollPools = Map.empty, walkerProcedure = None)))

  private def contractViolation(message: String): Left[OathViolation, Nothing] =
    Left(OathViolation.InvalidEventOrder(s"walker contract violation: $message"))

  /** Case-class short name used by the generic semantic fallback. */
  private def leafLabel(node: Operation): String = node match {
    case product: Product => product.productPrefix
    case other => other.getClass.getSimpleName
  }

  /** Walks `node` from `path` (its root-relative child-index address), either
    * from scratch (`cursor = None`) or resuming at `cursor` (the remaining
    * child-index segments from this node down to the parked leaf). `hooks`
    * carries the enclosing windows' attribution and re-entry guard.
    */
  private def walk(node: Operation, ctx: WalkCtx, path: Vector[String],
      cursor: Option[Vector[String]], resume: Resume,
      hooks: WalkerHooks): Either[OathViolation, Step] =
    node match {
      case repeat: Repeat => walkRepeat(repeat, ctx, path, cursor, resume, hooks)
      case branch: Branch => walkBranch(branch, ctx, path, cursor, resume, hooks)
      case leaf: PrimitiveOperation =>
        walkLeaf(leaf, ctx, path, cursor, resume, hooks)
      case composite =>
        walkComposite(composite, ctx, path, cursor, resume, hooks)
    }

  /** Gathers at `window` (a no-op when `None`) and folds `children` through
    * the gathered transforms, then walks the folded vector child by child --
    * exactly what an unwindowed composite does with its declared children
    * (fix-round ruling E). The declared node is never mutated: the fold is
    * local to this walk. A composite emits no event of its own; its gather
    * order rides down to the leaves it shaped (ruling F).
    */
  private def walkFolded(window: Option[PowerWindow], operation: Operation,
      children: Vector[Operation], ctx: WalkCtx, path: Vector[String],
      cursor: Option[Vector[String]], resume: Resume,
      hooks: WalkerHooks): Either[OathViolation, Step] = {
    val (folded, order) = WalkerPowerGather.applyWindow(window, operation, ctx.state,
      ctx.activePlayer, ctx.powers, path, children)
    walkChildren(folded, ctx, path, cursor, resume, hooks.withOrder(order))
  }

  private def walkComposite(composite: Operation, ctx: WalkCtx,
      path: Vector[String], cursor: Option[Vector[String]],
      resume: Resume, hooks: WalkerHooks): Either[OathViolation, Step] =
    walkFolded(composite.window, composite, composite.children, ctx, path, cursor,
      resume, hooks)

  /** A `Branch` has no static children: its `select` chooses the children to
    * walk at walk time, and a resume cursor addresses the selected vector the
    * same way it would address static children (the branch's position is
    * `path`; the parked child index heads the remaining cursor segments). A
    * window on the Branch folds that SELECTED vector -- the branch's children
    * are what it selects.
    */
  private def walkBranch(branch: Branch, ctx: WalkCtx, path: Vector[String],
      cursor: Option[Vector[String]], resume: Resume,
      hooks: WalkerHooks): Either[OathViolation, Step] = {
    val branchTree = PendingTree(at = path, answered = ctx.answered)
    walkFolded(branch.window, branch, branch.select(ctx.state, branchTree), ctx, path,
      cursor, resume, hooks)
  }

  /** Runs whole passes of `repeat.body` while its guard holds, plus, on a
    * resume, the remainder of the already-started pass first. A Repeat
    * exposes its body as its single child, so a pass walks the (folded)
    * children vector from this node's path and the body keeps child index 0.
    */
  private def walkRepeat(repeat: Repeat, ctx: WalkCtx, path: Vector[String],
      cursor: Option[Vector[String]], resume: Resume,
      hooks: WalkerHooks): Either[OathViolation, Step] = {

    /** One whole body pass, then back to the guard. `at` is non-empty only
      * for the pass a resume re-enters part-way through.
      */
    def pass(current: WalkCtx,
        at: Option[Vector[String]]): Either[OathViolation, Step] =
      walkFolded(repeat.window, repeat, repeat.children, current, path, at, resume,
        hooks).flatMap {
          case park: Park => Right(park)
          case Done(next) => passes(next)
        }

    /** The guard is pure and re-evaluated against the state the previous pass
      * reached, so it never needs an iteration counter; the window fold is
      * re-applied per pass for the same reason.
      */
    def passes(current: WalkCtx): Either[OathViolation, Step] = {
      val guardTree = PendingTree(at = path, answered = current.answered)
      if (!repeat.guard(current.state, guardTree)) Right(Done(current))
      else pass(current, None)
    }

    // A resume re-enters the pass that already started (its guard was true at
    // pass time): finish its remainder, then keep looping whole passes.
    if (cursor.isEmpty) passes(ctx) else pass(ctx, cursor)
  }

  /** A leaf whose `window` is `Some(w)` folds `Vector(leaf)` through the
    * gathered transforms and walks the result as its children -- the same
    * path a composite's folded vector takes (fix-round ruling G) -- so a
    * transform may legally insert or yield a `Decide`/`Roll`/`BuildOps` and
    * the walk parks on it like any other. The shape does not depend on
    * whether a power fired: a windowed leaf always runs one level down (the
    * unchanged fold is `Vector(leaf)`, walked at child index 0), and `w` is
    * marked gathered so the leaf handed back by its own transform is
    * dispatched instead of re-gathered. Resuming re-applies the same fold,
    * which is why a `Transform` must be a pure function of state.
    */
  private def walkLeaf(leaf: PrimitiveOperation, ctx: WalkCtx,
      path: Vector[String], cursor: Option[Vector[String]], resume: Resume,
      hooks: WalkerHooks): Either[OathViolation, Step] = leaf.window match {
    case Some(w) if !hooks.gathered.contains(w) =>
      walkFolded(Some(w), leaf, Vector(leaf), ctx, path, cursor, resume,
        hooks.copy(gathered = hooks.gathered + w))
    case _ => runLeaf(leaf, ctx, path, cursor, resume, hooks.inherited)
  }

  /** Executes or parks one leaf at its own position, recording
    * `contributions` (every enclosing window's gather order, this leaf's own
    * included) on whatever event it emits.
    */
  private def runLeaf(leaf: PrimitiveOperation, ctx: WalkCtx,
      path: Vector[String], cursor: Option[Vector[String]],
      resume: Resume,
      contributions: Vector[PowerId]): Either[OathViolation, Step] =
    cursor match {
      case Some(remaining) =>
        if (remaining.nonEmpty)
          contractViolation(
            s"resume path $remaining overruns leaf ${leafLabel(leaf)}")
        else resume match {
          case RollResume(faces) =>
            leaf match {
              case roll: Roll =>
                recordRoll(roll, ctx, path, faces, contributions).map(Done(_))
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
                answerDecide(decide, ctx, path, answer, contributions)
                  .map(Done(_))
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
          case build: BuildOps =>
            runBuildOps(build, ctx, path, contributions).map(Done(_))
          case delta => record(delta, ctx, path, contributions).map(Done(_))
        }
    }

  /** Walks `children` in order, skipping children already executed before a
    * resume point (`cursor` heads the index of the resumed child inside this
    * node's children).
    */
  private def walkChildren(children: Vector[Operation], ctx: WalkCtx,
      path: Vector[String], cursor: Option[Vector[String]], resume: Resume,
      hooks: WalkerHooks): Either[OathViolation, Step] =
    cursor match {
      case None => continue(children, 0, ctx, path, None, resume, hooks)
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
              continue(children, index, ctx, path, Some(remaining.tail), resume,
                hooks)
          }
        }
    }

  private def continue(children: Vector[Operation], index: Int, ctx: WalkCtx,
      path: Vector[String], cursorAt: Option[Vector[String]], resume: Resume,
      hooks: WalkerHooks): Either[OathViolation, Step] =
    if (index >= children.size) Right(Done(ctx))
    else
      walk(children(index), ctx, path :+ index.toString, cursorAt, resume,
        hooks).flatMap {
          case park: Park => Right(park)
          case Done(next) => continue(children, index + 1, next, path, None,
            resume, hooks)
        }

  /** Executes one delta leaf through the pipeline and records its step. */
  private def record(delta: CoreOperation, ctx: WalkCtx, path: Vector[String],
      contributions: Vector[PowerId]): Either[OathViolation, WalkCtx] =
    recordBatch(Vector(delta), contributions, ctx, path, leafLabel(delta))

  /** Executes a [[BuildOps]] leaf: `build(state, pending)` returns the delta
    * batch to run through the pipeline, recorded as the node's step ops. An
    * empty batch runs nothing and records nothing (the node produced no
    * state change).
    */
  private def runBuildOps(build: BuildOps, ctx: WalkCtx, path: Vector[String],
      contributions: Vector[PowerId]): Either[OathViolation, WalkCtx] = {
    val tree = PendingTree(at = path, answered = ctx.answered)
    build.build(ctx.state, tree).flatMap { ops =>
      if (ops.isEmpty) Right(ctx)
      else recordBatch(ops, contributions, ctx, path, leafLabel(build),
        build.restrictions(ctx.state, tree))
    }
  }

  /** Executes `ops` through the pipeline as one atomic batch and records ONE
    * [[WalkerStepRecorded]] carrying the whole batch and `contributions` --
    * the shared mechanic behind a plain delta leaf and a [[BuildOps]] leaf,
    * the two leaf kinds that run rather than park (spec decision 5: one
    * hookable node, one event, its final operation batch). `ops` must already
    * be non-empty; callers short-circuit an empty batch themselves (an empty
    * [[BuildOps]] batch records nothing).
    */
  private def recordBatch(ops: Vector[CoreOperation],
      contributions: Vector[PowerId], ctx: WalkCtx, path: Vector[String],
      label: String,
      restrictions: Vector[OperationRestriction] = Vector.empty)
      : Either[OathViolation, WalkCtx] =
    OperationPipeline.run(ctx.state, ops, OperationPolicy.Permissive,
      restrictions)(
      Right(_)).map { updated =>
      val nodeId = if (path.isEmpty) label else path.mkString(".")
      val events = if (updated.executed.isEmpty) ctx.events else
        ctx.events :+ WalkerStepRecorded(
          nodeId = nodeId,
          payload = WalkerStepPayload.DeltaRecorded(
            deltaMeaning(updated.executed, label)),
          ops = updated.executed,
          contributions = contributions)
      ctx.copy(state = updated.state, events = events)
    }

  private def deltaMeaning(ops: Vector[CoreOperation],
      fallback: String): DeltaMeaning = ops match {
    case Vector(ModifyDicePool(pool, delta, _)) =>
      DicePoolModified(pool, delta)
    case Vector(SpendSupply(player, amount, _)) =>
      SupplySpent(player, amount)
    case Vector(Move(Piece.Card(relic: RelicId),
        PositionedLocation(Location.Site(site), _),
        PositionedLocation(Location.PlayArea(player), _), _)) =>
      RelicAcquired(player, relic, site)
    case _ => OperationApplied(fallback)
  }

  /** Validates a resolved answer against the parked Decide and records its
    * step: the answer's submitter must be the node's owner, the query must
    * be answerable at all, and that query must accept the submitted answer.
    * On success `answer` is appended to `answered` and ONE
    * [[WalkerStepRecorded]] carrying a [[ChoicePayload]] (ops empty -- the
    * answer is a state write into `pending.answered`) is appended.
    *
    * Both checks are generic and read no game state (see [[DecisionQueries]]),
    * so the walker learns nothing here about which action parked: a legality
    * fact that used to live in a per-node `validate` closure now lives in the
    * declared option set, which is also what the projector offers.
    */
  private def answerDecide(decide: Decide, ctx: WalkCtx,
      path: Vector[String], answer: Answered,
      contributions: Vector[PowerId]): Either[OathViolation, WalkCtx] = {
    for {
      _ <- Either.cond(decide.owner == answer.by, (),
        OathViolation.WrongPlayer(decide.owner, answer.by))
      _ <- DecisionQueries.wellFormed(decide.decisionId, decide.query)
      _ <- DecisionQueries.accepts(decide.decisionId, decide.query,
        answer.answer)
    } yield {
      val nodeId =
        if (path.isEmpty) leafLabel(decide) else path.mkString(".")
      ctx.copy(
        answered = ctx.answered :+ answer,
        events = ctx.events :+ WalkerStepRecorded(
          nodeId = nodeId,
          payload = ChoicePayload(answer.decisionId, answer.answer, answer.by),
          ops = Vector.empty,
          contributions = contributions))
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
      faces: Vector[DieFace], contributions: Vector[PowerId])
      : Either[OathViolation, WalkCtx] = {
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
        events = ctx.events :+ WalkerStepRecorded(
          nodeId = nodeId, payload = RollPayload(pool, faces),
          ops = Vector.empty, contributions = contributions))
    }
  }

  /** Merges `outcome` into the pool's accumulated roll entry: repeated rolls
    * of one pool (e.g. a Recover `Repeat` re-rolling "recover") accumulate
    * faces/count/skulls and re-score all defense faces together. Re-scoring is
    * required because each Doubler multiplies shields from every accumulated
    * roll, not only the roll containing that Doubler.
    */
  /** `private[walker]`, not `private`: [[WalkerReplay]] merges a recorded
    * `RollPayload` into the same accumulator the live walk writes through, so
    * a replayed roll and a rolled one accumulate identically by construction
    * rather than by two implementations agreeing.
    */
  private[walker] def writeRollOutcome(ready: ReadyGame,
      outcome: RollOutcome): ReadyGame = {
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

}
