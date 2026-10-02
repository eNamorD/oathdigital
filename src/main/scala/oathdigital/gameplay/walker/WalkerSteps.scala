package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.model.{Answered, BuildOps, CoreOperation, Decide, DieFace, OathViolation, Operation, PendingTree, PowerId, Roll}

/** The leaf steps [[ProcedureWalker]] runs rather than parks, and the
  * answers and rolls that resume a park. Each executes one leaf against a
  * [[WalkCtx]] and records its [[WalkerStepRecorded]]; none walks further.
  */
private[walker] object WalkerSteps:
  /** Case-class short name used by the generic semantic fallback. */
  def leafLabel(node: Operation): String = node match
    case product: Product => product.productPrefix
    case other => other.getClass.getSimpleName

  /** Executes one delta leaf through the pipeline and records its step. */
  def record(delta: CoreOperation, ctx: WalkCtx, path: Vector[String],
      contributions: Vector[PowerId],
      strict: Boolean): Either[OathViolation, WalkCtx] =
    recordBatch(Vector(delta), contributions, ctx, path, leafLabel(delta),
      requireAll = strict)

  /** Executes a [[oathdigital.model.BuildOps]] leaf: `build(state, pending)` returns the delta
    * batch to run through the pipeline, recorded as the node's step ops. An
    * empty batch runs nothing and records nothing, and a note after it reads
    * that nothing changed. A search stops at a batch that shows hidden
    * information.
    */
  def runBuildOps(build: BuildOps, ctx: WalkCtx, path: Vector[String],
      contributions: Vector[PowerId]): Either[OathViolation, Step] =
    val tree = PendingTree(at = path, answered = ctx.answered)
    build.build(ctx.state, tree).flatMap { ops =>
      if ctx.searching && ops.exists(WalkerSearch.hides) then
        Right(Stopped(ctx))
      else if ops.isEmpty then
        Right(Done(ctx.copy(previous = Some((ctx.state, ctx.state)))))
      else recordBatch(ops, contributions, ctx, path, leafLabel(build),
        requireAll = build.required).map(Done(_))
    }

  /** Executes `ops` through the pipeline as one atomic batch and records ONE
    * [[WalkerStepRecorded]] carrying the whole batch and `contributions` --
    * the shared mechanic behind a plain delta leaf and a [[oathdigital.model.BuildOps]] leaf,
    * the two leaf kinds that run rather than park (spec decision 5: one
    * hookable node, one event, its final operation batch). `ops` must already
    * be non-empty; callers short-circuit an empty batch themselves (an empty
    * [[oathdigital.model.BuildOps]] batch records nothing).
    */
  def recordBatch(ops: Vector[CoreOperation],
      contributions: Vector[PowerId], ctx: WalkCtx, path: Vector[String],
      label: String,
      requireAll: Boolean)
      : Either[OathViolation, WalkCtx] =
    OperationPipeline.run(ctx.state, ops, OperationPolicy.Permissive,
      ctx.powers.operationRestrictions, requireAll)(
      Right(_)).map { updated =>
      val nodeId = if path.isEmpty then label else path.mkString(".")
      val events = if updated.executed.isEmpty then ctx.events else
        ctx.events :+ WalkerStepRecorded(
          nodeId = nodeId,
          payload = WalkerStepPayload.DeltaRecorded(
            DeltaMeaning.of(updated.executed, label)),
          ops = updated.executed,
          contributions = contributions)
      ctx.copy(state = updated.state, events = events,
        previous = Some((ctx.state, updated.state)))
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
  def answerDecide(decide: Decide, ctx: WalkCtx,
      path: Vector[String], answer: Answered,
      contributions: Vector[PowerId]): Either[OathViolation, WalkCtx] =
    for
      _ <- Either.cond(decide.owners.contains(answer.by), (),
        OathViolation.WrongPlayer(decide.owner, answer.by))
      _ <- DecisionQueries.wellFormed(decide.decisionId, decide.query)
      _ <- DecisionQueries.accepts(decide.decisionId, decide.query,
        answer.answer, answer.by)
    yield
      val nodeId =
        if path.isEmpty then leafLabel(decide) else path.mkString(".")
      ctx.copy(
        answered = ctx.answered :+ answer,
        previous = Some((ctx.state, ctx.state)),
        events = ctx.events :+ WalkerStepRecorded(
          nodeId = nodeId,
          payload = ChoicePayload(answer.decisionId, answer.answer, answer.by),
          ops = Vector.empty,
          contributions = contributions))

  /** Records one roll step: the outcome is derived and validated by
    * [[WalkerRolls.outcomeFor]], merged into `ctx.state`, and the step carries
    * a [[RollPayload]] with no ops (the outcome is a state write).
    */
  def recordRoll(roll: Roll, ctx: WalkCtx, path: Vector[String],
      faces: Vector[DieFace], contributions: Vector[PowerId],
      automatic: Boolean = false): Either[OathViolation, WalkCtx] =
    WalkerRolls.outcomeFor(roll, ctx.state, faces).map { outcome =>
      val nodeId =
        if path.isEmpty then leafLabel(roll) else path.mkString(".")
      val written = WalkerRolls.write(ctx.state, outcome)
      ctx.copy(
        state = written,
        previous = Some((ctx.state, written)),
        events = ctx.events :+ WalkerStepRecorded(
          nodeId = nodeId, payload = RollPayload(roll.pool, faces, automatic),
          ops = Vector.empty, contributions = contributions))
    }

  /** Rolls an `Automatic` node: the faces come from the dice source and the
    * step is recorded like a resumed roll, marked `automatic`. A pool of zero
    * dice is skipped and records nothing: replay needs the pool to exist, and
    * a Campaign with no force and no plans never creates one.
    */
  def runAutomaticRoll(roll: Roll, ctx: WalkCtx, path: Vector[String],
      contributions: Vector[PowerId]): Either[OathViolation, WalkCtx] =
    val count = WalkerRolls.poolCount(ctx.state, roll.pool)
    if count == 0 then Right(ctx)
    else ctx.dice.roll(roll.dice.die, count).flatMap(faces =>
      recordRoll(roll, ctx, path, faces, contributions, automatic = true))
