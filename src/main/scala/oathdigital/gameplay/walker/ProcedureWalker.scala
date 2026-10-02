package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.OperationResolution
import oathdigital.model.{Answered, Branch, BuildOps, CoreOperation, Decide, DecisionAnswer, DieFace, Note, NoteStates, OathState, OathViolation, Operation, PendingTree, PlayerId, PoolKey, PowerId, PowerWindow, PrimitiveOperation, ReadyGame, Repeat, Roll, RollMode, Shuffle, WalkerEvent}

/** Auto-walk engine over an [[oathdigital.model.Operation]] action tree (Tasks 3-5).
  *
  * Contract (plan-owner rulings + brief):
  *  - `advance(state, action, pending)` walks `action` from `pending.at`
  *    (the caller supplies the tree every command — S1 pending stores only a
  *    pointer); `pending = None` starts fresh at the root.
  *  - `Decide` nodes park; `Roll` nodes park in `advance` (their faces ride
  *    a later command, unless the node is `Automatic`, which rolls from the dice source) and are resumed by
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
  *    per executed leaf. [[oathdigital.model.BuildOps]] leaves are executed at walk time:
  *    `build(state, pending)` yields the delta batch to run and record (an
  *    empty batch runs nothing and records nothing).
  *  - [[oathdigital.model.Branch]] composites are resolved at walk time by evaluating
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
object ProcedureWalker:

  /** Replays one durable walker fact. This path applies recorded operations
    * and payload state writes only; it never derives or walks an action tree
    * -- see [[WalkerReplay]], which holds the whole of it (split out to keep
    * this file under the project's line bound, like [[WalkerPowerGather]]).
    */
  def applyRecorded(state: OathState,
      event: WalkerEvent): Either[OathViolation, OathState] =
    WalkerReplay.applyRecorded(state, event)

  /** Applies ONE operation of a recorded batch, exactly as replay applies
    * each in turn. The game log steps through a batch with it, so a line
    * about one operation is judged against the state just before and just
    * after that operation rather than the whole batch.
    */
  def applyRecordedOperation(state: ReadyGame, operation: CoreOperation)
      : Either[OathViolation, ReadyGame] =
    WalkerReplay.applyOperation(state, operation)


  def advance(state: ReadyGame, action: Operation,
      pending: Option[PendingTree], powers: WalkerPowers,
      dice: WalkerDice = WalkerDice.unavailable)
      : Either[OathViolation, WalkerOutcome] =
    val activePlayer = state.game.current.turn.activePlayer
    val answered = pending.fold(Vector.empty[Answered])(_.answered)
    val cursor: Option[Vector[String]] = pending.map(_.at)
    // The stored pending tree is navigation state passed by parameter; a
    // running walk must not carry it inside CurrentGameState, so clear the
    // stored field before executing deltas.
    val base = strip(state)
    walk(action, WalkCtx(base, Vector.empty, activePlayer, answered, powers, dice,
      state.game.current.walkerProcedure, action),
      Vector.empty, cursor, PlainResume, WalkerHooks.none).flatMap(toOutcome)

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
    * A face count differing from the pool count, or a face outside the roll's
    * die kind, rejects with `OathViolation.InvalidEventOrder`.
    */
  def roll(state: ReadyGame, action: Operation, pending: PendingTree,
      faces: Vector[DieFace], powers: WalkerPowers,
      dice: WalkerDice = WalkerDice.unavailable)
      : Either[OathViolation, WalkerOutcome] =
    val base = strip(state)
    walk(action, WalkCtx(base, Vector.empty,
      state.game.current.turn.activePlayer, pending.answered, powers, dice,
      state.game.current.walkerProcedure, action),
      Vector.empty, Some(pending.at), RollResume(faces), WalkerHooks.none)
      .flatMap(toOutcome)

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
      answer: Answered, powers: WalkerPowers,
      dice: WalkerDice = WalkerDice.unavailable)
      : Either[OathViolation, WalkerOutcome] =
    val base = strip(state)
    walk(action, WalkCtx(base, Vector.empty,
      state.game.current.turn.activePlayer, pending.answered, powers, dice,
      state.game.current.walkerProcedure, action),
      Vector.empty, Some(pending.at), AnswerResume(answer), WalkerHooks.none)
      .flatMap(toOutcome)

  /** Collects every restriction violation from every windowed node in `tree`
    * (spec decision 9's `Restriction` kind), run against the tree root --
    * `OathRules` calls this once per command, before the walk (Task 3
    * wiring rule). See [[WalkerPowerGather.restrictionViolations]] for the
    * traversal (split out to keep this file under the project's line bound).
    */
  def restrictionViolations(tree: Operation, powers: WalkerPowers,
      state: ReadyGame, activePlayer: PlayerId,
      answered: Vector[oathdigital.model.Answered] = Vector.empty)
      : Vector[OathViolation] = WalkerPowerGather.restrictionViolations(tree,
    powers, state, activePlayer, answered)

  /** When `pending` parks on a `Roll` node of `action`, reports the node's
    * `pool` and the face count that node requires (read from
    * `state.rollPools`), so the app layer can pre-roll exactly that many
    * faces for the next `roll` command (Task 6 consumes it). `None` when the
    * park is a Decide or the position does not resolve to a Roll. `powers`
    * (fix-round ruling J) must be the same vector the live walk that parked
    * here used -- `leafAt` folds every window on the path exactly like the
    * walk does, so a transform that inserts operations around a windowed
    * node does not make this address the wrong leaf.
    *
    * Always resolves with the restriction look-ahead off: the look-ahead
    * only narrows a `Decide`'s options, never a `Roll`'s, so probing here
    * could only be wasted work on a position that turns out to be a
    * `Decide` (discarded below either way).
    */
  def parkedRoll(state: ReadyGame, action: Operation,
      pending: PendingTree, powers: WalkerPowers): Option[(PoolKey, Int)] =
    WalkerPowerGather.leafAt(state, action, pending, powers.quiet)
      .collect:
        case roll: Roll if roll.mode == RollMode.Parked =>
          (roll.pool, WalkerRolls.poolCount(state, roll.pool))

  /** Every decision open at the park: one for a plain or co-owned `Decide`,
    * none for a `Roll` or a position that does not resolve. Reports the nodes
    * themselves so the caller can dispatch on their stable `decisionId`
    * rather than on the park's structural path, which shifts if the tree is
    * edited. A future `Simultaneous` node would return one per unanswered
    * child, and nothing else here would change.
    *
    * `probing` (default on) runs the restriction look-ahead, narrowing the
    * options a caller offers or validates against. A caller that only reads
    * the decision's identity, owner or labels -- never which options are
    * still legal -- passes `probing = false` to skip that work.
    */
  def openDecisions(state: ReadyGame, action: Operation,
      pending: PendingTree, powers: WalkerPowers,
      probing: Boolean = true): Vector[Decide] =
    val looked = if probing then powers else powers.quiet
    WalkerPowerGather.leafAt(state, action, pending, looked).collect {
      case decide: Decide => decide
    }.toVector

  /** The single-decision view of [[openDecisions]]. `None` when the park is a
    * Roll or the position does not resolve to a Decide. Symmetric to
    * [[parkedRoll]]. See [[openDecisions]] for `probing`.
    */
  def parkedDecide(state: ReadyGame, action: Operation,
      pending: PendingTree, powers: WalkerPowers,
      probing: Boolean = true): Option[Decide] =
    openDecisions(state, action, pending, powers, probing).headOption

  /** Who a parked position waits on, as one player: a parked `Decide`'s
    * primary owner, read off the rebuilt and transformed node, or the active
    * player for a parked `Roll`.
    * Never stored -- a power that changes an owner changes this answer on the
    * next command, and authorization and projection both read it (Task 5).
    *
    * Skips the restriction look-ahead: an owner never changes with which
    * options the look-ahead would hide, so probing here is pure overhead.
    */
  def awaitedPlayer(state: ReadyGame, action: Operation, pending: PendingTree,
      powers: WalkerPowers): Option[PlayerId] =
    parkedDecide(state, action, pending, powers, probing = false).map(_.owner)
      .orElse(parkedRoll(state, action, pending, powers).map(_ =>
        state.game.current.turn.activePlayer))

  /** Everyone who may answer the parked position: the owners and co-owners of
    * its open decisions, or the active player for a parked `Roll`. Skips the
    * restriction look-ahead for the same reason [[awaitedPlayer]] does: an
    * owner set never depends on which options are hidden.
    */
  def awaitedPlayers(state: ReadyGame, action: Operation,
      pending: PendingTree, powers: WalkerPowers): Set[PlayerId] =
    val open = openDecisions(state, action, pending, powers, probing = false)
    if open.nonEmpty then open.flatMap(_.owners).toSet
    else parkedRoll(state, action, pending, powers)
      .map(_ => Set(state.game.current.turn.activePlayer)).getOrElse(Set.empty)

  // --------------------------------------------------------------------------
  // Walking core
  // --------------------------------------------------------------------------

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

  private def toOutcome(step: Step): Either[OathViolation, WalkerOutcome] =
    step match
      case Done(ctx) =>
        Right(WalkerOutcome.Finished(finish(ctx), ctx.events))
      case Park(position, ctx) =>
        Right(WalkerOutcome.Parked(PendingTree(at = position,
          answered = ctx.answered), ctx.events))
      case Stopped(_) => contractViolation("only a search stops short of a park")

  private def strip(state: ReadyGame): ReadyGame =
    state.copy(game = state.game.copy(current =
      state.game.current.copy(walkerPending = None, walkerProcedure = None)))

  private def finish(ctx: WalkCtx): ReadyGame =
    ctx.state.copy(game = ctx.state.game.copy(current =
      ctx.state.game.current.copy(walkerPending = None,
        rollPools = Map.empty, walkerProcedure = None)))

  private def contractViolation(message: String): Left[OathViolation, Nothing] =
    Left(OathViolation.InvalidEventOrder(s"walker contract violation: $message"))

  /** Walks `node` from `path` (its root-relative child-index address), either
    * from scratch (`cursor = None`) or resuming at `cursor` (the remaining
    * child-index segments from this node down to the parked leaf). `hooks`
    * carries the enclosing windows' attribution and re-entry guard.
    */
  private def walk(node: Operation, ctx: WalkCtx, path: Vector[String],
      cursor: Option[Vector[String]], resume: Resume,
      hooks: WalkerHooks): Either[OathViolation, Step] =
    node match
      case repeat: Repeat => walkRepeat(repeat, ctx, path, cursor, resume, hooks)
      case branch: Branch => walkBranch(branch, ctx, path, cursor, resume, hooks)
      case leaf: PrimitiveOperation =>
        walkLeaf(leaf, ctx, path, cursor, resume, hooks)
      case note: Note => Right(Done(noted(note, ctx)))
      case composite =>
        walkComposite(composite, ctx, path, cursor, resume, hooks)

  /** Journals a power's note. A resumed walk skips the children before its
    * cursor, so a note runs only on the walk that first reaches it. */
  private def noted(note: Note, ctx: WalkCtx): WalkCtx =
    note.build(NoteStates(ctx.state, ctx.previous, ctx.answered)).fold(ctx)(
      built => ctx.copy(events = ctx.events :+
        PowerNoted(note.power, built, note.covers)))

  /** Events other than notes: a note changes nothing a guard reads. */
  private def recorded(ctx: WalkCtx): Int =
    ctx.events.count(!_.isInstanceOf[PowerNoted])

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
      hooks: WalkerHooks): Either[OathViolation, Step] =
    val (folded, order, hidden) = WalkerPowerGather.applyWindowNoted(window,
      operation, ctx.state, ctx.activePlayer,
      if ctx.searching then ctx.powers.quiet else ctx.powers, path, children,
      ctx.procedure, ctx.answered, cursor.isDefined, noting = cursor.isEmpty)
    walkChildren(folded, ctx.copy(events = ctx.events ++ hidden), path, cursor,
      resume, hooks.withOrder(order))

  private def walkComposite(composite: Operation, ctx: WalkCtx,
      path: Vector[String], cursor: Option[Vector[String]],
      resume: Resume, hooks: WalkerHooks): Either[OathViolation, Step] =
    // A search stops at a composite that shows hidden information (a `Draw`,
    // a `Reveal`) before any of it runs.
    if ctx.searching && cursor.isEmpty && WalkerSearch.hides(composite) then
      Right(Stopped(ctx))
    else
      // The composite is walked as its children, and a bare child is
      // best-effort: without this its own `required` would be lost, and an
      // unaffordable `PayCost` would shrink to what the player holds.
      val required = composite match
        case core: CoreOperation => core.required
        case _ => false
      // A fresh composite is checked whole first; a resumed one already was.
      val runs = composite match
        case core: CoreOperation if cursor.isEmpty =>
          OperationResolution.screen(ctx.state, core,
            ctx.powers.operationRestrictions, required || hooks.strict)
        case _ => Right(true)
      runs.flatMap { run =>
        if !run then Right(Done(ctx.copy(previous = Some((ctx.state, ctx.state)))))
        else walkFolded(composite.window, composite, composite.children, ctx,
          path, cursor, resume,
          if required then hooks.copy(strict = true) else hooks)
      }

  /** A `Branch` has no static children: its `select` chooses the children to
    * walk at walk time, and a resume cursor addresses the selected vector the
    * same way it would address static children (the branch's position is
    * `path`; the parked child index heads the remaining cursor segments). A
    * window on the Branch folds that SELECTED vector -- the branch's children
    * are what it selects.
    */
  private def walkBranch(branch: Branch, ctx: WalkCtx, path: Vector[String],
      cursor: Option[Vector[String]], resume: Resume,
      hooks: WalkerHooks): Either[OathViolation, Step] =
    val branchTree = PendingTree(at = path, answered = ctx.answered)
    walkFolded(branch.window, branch, branch.select(ctx.state, branchTree), ctx, path,
      cursor, resume, hooks)

  /** Runs whole passes of `repeat.body` while its guard holds, plus, on a
    * resume, the remainder of the already-started pass first. A Repeat
    * exposes its body as its single child, so a pass walks the (folded)
    * children vector from this node's path and the body keeps child index 0.
    */
  private def walkRepeat(repeat: Repeat, ctx: WalkCtx, path: Vector[String],
      cursor: Option[Vector[String]], resume: Resume,
      hooks: WalkerHooks): Either[OathViolation, Step] =

    /** One whole body pass, then back to the guard. `at` is non-empty only
      * for the pass a resume re-enters part-way through.
      */
    def pass(current: WalkCtx,
        at: Option[Vector[String]]): Either[OathViolation, Step] =
      walkFolded(repeat.window, repeat, repeat.children, current, path, at, resume,
        hooks).flatMap:
          case stop @ (_: Park | _: Stopped) => Right(stop)
          // A pass that recorded nothing and asked nothing cannot change what
          // the guard reads, so it would only repeat itself for ever.
          case Done(next) if recorded(next) == recorded(current) &&
              next.answered.size == current.answered.size => Right(Done(next))
          case Done(next) => passes(next)

    /** The guard is pure and re-evaluated against the state the previous pass
      * reached, so it never needs an iteration counter; the window fold is
      * re-applied per pass for the same reason.
      */
    def passes(current: WalkCtx): Either[OathViolation, Step] =
      val guardTree = PendingTree(at = path, answered = current.answered)
      if !repeat.guard(current.state, guardTree) then Right(Done(current))
      else pass(current, None)

    // A resume re-enters the pass that already started (its guard was true at
    // pass time): finish its remainder, then keep looping whole passes.
    if cursor.isEmpty then passes(ctx) else pass(ctx, cursor)

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
      hooks: WalkerHooks): Either[OathViolation, Step] = leaf.window match
    case Some(w) if !hooks.gathered.contains(w) =>
      walkFolded(Some(w), leaf, Vector(leaf), ctx, path, cursor, resume,
        hooks.copy(gathered = hooks.gathered + w))
    case _ => runLeaf(leaf, ctx, path, cursor, resume, hooks.inherited,
      hooks.strict)

  /** Executes or parks one leaf at its own position, recording
    * `contributions` (every enclosing window's gather order, this leaf's own
    * included) on whatever event it emits. A `Decide` where it is asked --
    * reached fresh or being answered -- is first narrowed by the search
    * ([[narrowAt]]). A required decision with nothing left rejects the
    * command, and an optional one with nothing left is passed without
    * asking. A search's walk does not park at a decision it reaches: it asks
    * whether some answer leads on ([[reach]]).
    */
  private def runLeaf(leaf: PrimitiveOperation, ctx: WalkCtx,
      path: Vector[String], cursor: Option[Vector[String]],
      resume: Resume, contributions: Vector[PowerId],
      strict: Boolean): Either[OathViolation, Step] =
    leaf match
      case decide: Decide if ctx.searching && cursor.isEmpty =>
        reach(decide, ctx, path)
      case decide: Decide if !ctx.searching && asked(cursor, resume) =>
        narrowAt(decide, ctx, path).flatMap:
          case Some(narrowed) =>
            // Reached fresh, so this is the only time it is asked this pass.
            val hidden = if cursor.nonEmpty then Vector.empty
              else WalkerPowerGather.lookAheadNotes(ctx.root, decide, narrowed,
                ctx.state, ctx.activePlayer, ctx.powers, ctx.answered,
                ctx.procedure)
            runNarrowed(narrowed, ctx.copy(events = ctx.events ++ hidden), path,
              cursor, resume, contributions, strict)
          case None if cursor.isEmpty => Right(Done(ctx))
          case None => Left(OathViolation.InvalidEventOrder(
            s"decision ${decide.decisionId} has nothing left to offer"))
      case _ => runNarrowed(leaf, ctx, path, cursor, resume, contributions,
        strict)

  /** `decide` narrowed to the options from which some path reaches a legal
    * end (global operation restrictions design, "Lazy pruning"), or as
    * declared when the powers do not search. */
  private def narrowAt(decide: Decide, ctx: WalkCtx, path: Vector[String])
      : Either[OathViolation, Option[Decide]] =
    if !ctx.powers.probing then Right(Some(decide))
    else WalkerSearch.narrow(decide, verdict(decide, ctx, path))

  /** A search at a decision it reaches. Back at a decision it already stands
    * at in the same state, it stops: the loop leads on exactly when the first
    * visit does, and the first visit tries the other answers. */
  private def reach(decide: Decide, ctx: WalkCtx, path: Vector[String])
      : Either[OathViolation, Step] =
    if ctx.visited((path, ctx.state)) then Right(Stopped(ctx))
    else WalkerSearch.reach(decide, verdict(decide, ctx, path)).map:
      case WalkerSearch.Reach.Skipped => Done(ctx)
      case WalkerSearch.Reach.Answerable => Stopped(ctx)

  /** Whether one answer to `decide`, given by its owner, can reach a legal
    * end: it adds no tree-level `Restriction` violation the answers so far do
    * not already produce, and a search's walk from it on a copy of the state
    * is not rejected. The search runs the real pipeline, so any rejection of
    * a required operation fails the path. */
  private def verdict(decide: Decide, ctx: WalkCtx, path: Vector[String])
      : DecisionAnswer => Either[OathViolation, Unit] =
    lazy val baseline = WalkerPowerGather.breaches(ctx.root, ctx.state,
      ctx.activePlayer, ctx.powers, ctx.answered, ctx.procedure).toSet
    val searching = ctx.copy(events = Vector.empty, searching = true,
      visited = ctx.visited + ((path, ctx.state)))
    def reached(answer: DecisionAnswer): Either[OathViolation, Unit] =
      val hypothetical = Answered(decide.decisionId, answer, decide.owner)
      WalkerPowerGather.breaches(ctx.root, ctx.state, ctx.activePlayer,
          ctx.powers, ctx.answered :+ hypothetical, ctx.procedure)
        .find(!baseline(_)).toLeft(())
        .flatMap(_ => searched(walk(ctx.root, searching, Vector.empty,
          Some(path), AnswerResume(hypothetical), WalkerHooks.none)))
    // Only the live walk's verdicts are kept: one asked inside a search also
    // depends on the decisions that search stands at.
    if ctx.searching then reached
    else
      val base = new SearchMemo.Base(ctx.root, path, ctx.state, ctx.answered,
        ctx.procedure, ctx.activePlayer, decide.decisionId, decide.owner)
      answer => ctx.powers.searchMemo.verdict(base, answer)(reached(answer))

  /** A search's walk as a verdict. A broken position is a rejection, as it
    * is for a simulation. */
  private def searched(walked: => Either[OathViolation, Step])
      : Either[OathViolation, Unit] =
    try walked.map(_ => ())
    catch
      case error: IllegalArgumentException => Left(
        OathViolation.InvalidEventOrder(Option(error.getMessage)
          .getOrElse("invalid searched walker position")))

  /** `decide`, parked at `pending`, narrowed as the walk narrowed it before
    * parking. A position whose search empties a required decision is
    * returned as declared, and the answer-time check refuses it. */
  private[walker] def narrowParked(state: ReadyGame, action: Operation,
      pending: PendingTree, powers: WalkerPowers, decide: Decide): Decide =
    val ctx = WalkCtx(strip(state), Vector.empty,
      state.game.current.turn.activePlayer, pending.answered, powers,
      WalkerDice.placeholder, state.game.current.walkerProcedure, action)
    narrowAt(decide, ctx, pending.at).toOption.flatten.getOrElse(decide)

  /** Whether the leaf is where a decision is asked: reached fresh, or at the
    * cursor's end while an answer resumes. A plain resume only re-parks it.
    */
  private def asked(cursor: Option[Vector[String]], resume: Resume): Boolean =
    cursor match
      case None => true
      case Some(remaining) =>
        remaining.isEmpty && resume.isInstanceOf[AnswerResume]

  /** Executes or parks one leaf that the look-ahead has already narrowed. */
  private def runNarrowed(leaf: PrimitiveOperation, ctx: WalkCtx,
      path: Vector[String], cursor: Option[Vector[String]],
      resume: Resume, contributions: Vector[PowerId],
      strict: Boolean): Either[OathViolation, Step] =
    cursor match
      case Some(remaining) =>
        if remaining.nonEmpty then
          contractViolation(
            s"resume path $remaining overruns leaf ${WalkerSteps.leafLabel(leaf)}")
        else resume match
          case RollResume(faces) =>
            leaf match
              case roll: Roll =>
                WalkerSteps.recordRoll(roll, ctx, path, faces, contributions).map(Done(_))
              case _: Decide => Left(OathViolation.InvalidEventOrder(
                s"roll() resumed at a ${WalkerSteps.leafLabel(leaf)} park; " +
                  "expected a Roll"))
              case other =>
                contractViolation(s"resume position ${path.mkString(".")} " +
                  s"is not a Decide/Roll park (leaf ${WalkerSteps.leafLabel(other)})")
          case AnswerResume(answer) =>
            leaf match
              case decide: Decide if decide.decisionId == answer.decisionId =>
                WalkerSteps.answerDecide(decide, ctx, path, answer, contributions)
                  .map(Done(_))
              case decide: Decide => Left(OathViolation.InvalidEventOrder(
                s"resolve() answer ${answer.decisionId} does not match the " +
                  s"parked Decide ${decide.decisionId} at " +
                  path.mkString(".")))
              case roll: Roll => Left(OathViolation.InvalidEventOrder(
                s"resolve() resumed at a Roll park; expected a Decide"))
              case other =>
                contractViolation(s"resume position ${path.mkString(".")} " +
                  s"is not a Decide/Roll park (leaf ${WalkerSteps.leafLabel(other)})")
          case PlainResume =>
            leaf match
              case _: Decide | _: Roll =>
                // A plain advance never consumes a park. In particular, a
                // repeated Decide can reuse its stable decision ID across
                // passes, so older answers must not make a fresh pass skip it.
                // Decide and Roll parks resume only through `resolve`/`roll`.
                Right(Park(path, ctx))
              case _ =>
                contractViolation(s"resume position ${path.mkString(".")} " +
                  s"is not a Decide/Roll park (leaf ${WalkerSteps.leafLabel(leaf)})")
      case None =>
        leaf match
          case hidden if ctx.searching && WalkerSearch.hides(hidden) =>
            Right(Stopped(ctx))
          case roll: Roll if roll.mode == RollMode.Automatic =>
            WalkerSteps.runAutomaticRoll(roll, ctx, path, contributions).map(Done(_))
          case shuffle: Shuffle if shuffle.order.isEmpty =>
            WalkerShuffles.ordered(shuffle, ctx.state, ctx.dice).flatMap(
              WalkerSteps.record(_, ctx, path, contributions, strict)).map(Done(_))
          case _: Decide | _: Roll => Right(Park(path, ctx))
          case build: BuildOps => WalkerSteps.runBuildOps(build, ctx, path, contributions)
          case delta =>
            WalkerSteps.record(delta, ctx, path, contributions, strict).map(Done(_))

  /** Walks `children` in order, skipping children already executed before a
    * resume point (`cursor` heads the index of the resumed child inside this
    * node's children).
    */
  private def walkChildren(children: Vector[Operation], ctx: WalkCtx,
      path: Vector[String], cursor: Option[Vector[String]], resume: Resume,
      hooks: WalkerHooks): Either[OathViolation, Step] =
    cursor match
      case None => continue(children, 0, ctx, path, None, resume, hooks)
      case Some(remaining) =>
        remaining.headOption match
          case None => contractViolation(
            "resume path ends at a composite node; only Decide/Roll parks resume")
          case Some(segment) => segment.toIntOption match
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

  private def continue(children: Vector[Operation], index: Int, ctx: WalkCtx,
      path: Vector[String], cursorAt: Option[Vector[String]], resume: Resume,
      hooks: WalkerHooks): Either[OathViolation, Step] =
    if index >= children.size then Right(Done(ctx))
    else
      walk(children(index), ctx, path :+ index.toString, cursorAt, resume,
        hooks).flatMap:
          case stop @ (_: Park | _: Stopped) => Right(stop)
          case Done(next) => continue(children, index + 1, next, path, None,
            resume, hooks)
