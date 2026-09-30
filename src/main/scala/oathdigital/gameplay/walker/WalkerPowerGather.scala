package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{ContributingPower, ContributionCollector, OfferHost, OptionRestriction, PowerCtx, Restriction}
import oathdigital.model.{Answered, Branch, Decide, DecisionAnswer, DecisionOptionRef, DecisionQuery, OathViolation, OfferedPlan, Operation, PendingTree, PlayerId, PowerId, PowerWindow, PrimitiveOperation, ProcedureRef, ReadyGame}

/** Task 3's power-gather/fold mechanics for [[ProcedureWalker]], split into
  * their own file to keep `ProcedureWalker.scala` under the project's
  * per-file line bound (`BackendArchitectureSuite`'s "all production Scala
  * files stay bounded"). Everything here is `private[walker]`:
  * `ProcedureWalker` is the sole caller, and the only power-shaped surface it
  * re-exports publicly is `WalkerPowers` (declared on `ProcedureWalker`),
  * `ProcedureWalker.restrictionViolations` (delegates to
  * [[restrictionViolations]] below), and `ProcedureWalker.parkedRoll`/
  * `ProcedureWalker.parkedDecide` (delegate to [[leafAt]] below, fix-round
  * ruling J).
  */
private[walker] object WalkerPowerGather:

  /** A restriction's violation with the power and context that produced it,
    * so the look-ahead can ask that restriction for its note. */
  private final case class Attributed(power: PowerId, ctx: PowerCtx,
      restriction: Restriction, violation: OathViolation)

  /** Gathers powers at `window` (a no-op for `None` -- spec decision 9's
    * third case: an engine-internal node contributes nothing) and folds
    * `ops` through the resulting transforms, in the deterministic order
    * `ContributionCollector.gather` produced. Returns the folded operations
    * (`== ops` unchanged when there is no window or nothing applicable) and
    * the gather's contribution order, to be recorded verbatim on whichever
    * `WalkerStepRecorded` this node's execution produces (Task 3 wiring
    * rules 1-3).
    *
    * `answered` are the decisions answered so far, which a contribution reads
    * from its `PowerCtx`. `resuming` is true when the walk is resuming inside
    * this node: an [[OfferHost]] then keeps its shape whatever it is offered.
    * When the node is an [[OfferHost]], the offers the window gathered are
    * turned into its children after the transforms have run.
    */
  def applyWindow(window: Option[PowerWindow], operation: Operation,
      state: ReadyGame,
      activePlayer: PlayerId, powers: WalkerPowers, path: Vector[String],
      ops: Vector[Operation], procedure: Option[oathdigital.model.ProcedureRef],
      answered: Vector[Answered] = Vector.empty, resuming: Boolean = false)
      : (Vector[Operation], Vector[PowerId]) =
    val (folded, order, _) = applyWindowNoted(window, operation, state,
      activePlayer, powers, path, ops, procedure, answered, resuming,
      noting = false)
    (folded, order)

  /** As [[applyWindow]], and, when `noting`, the notes of the options each
    * `OptionRestriction` hid from a `Decide`. The walker notes only a window
    * it enters fresh, so a resume never says it twice.
    */
  def applyWindowNoted(window: Option[PowerWindow], operation: Operation,
      state: ReadyGame,
      activePlayer: PlayerId, powers: WalkerPowers, path: Vector[String],
      ops: Vector[Operation], procedure: Option[oathdigital.model.ProcedureRef],
      answered: Vector[Answered], resuming: Boolean, noting: Boolean)
      : (Vector[Operation], Vector[PowerId], Vector[PowerNoted]) =
    window match
      case None => (ops, Vector.empty, Vector.empty)
      case Some(w) =>
        val byId: Map[PowerId, ContributingPower] =
          powers.powers.map(power => power.id -> power).toMap
        def ctxFor(power: ContributingPower): PowerCtx =
          PowerCtx(state, activePlayer, power.source, w, path, operation,
            procedure, answered)
        val gathered = ContributionCollector.gather(w, powers.powers, ctxFor)
        val folded = gathered.transforms.foldLeft(ops):
          case (acc, (powerId, transform)) =>
            transform.fn(ctxFor(byId(powerId)), acc)
        operation match
          case _: Decide =>
            (restrictOptions(folded, gathered.optionRestrictions, ctxFor, byId),
              gathered.order,
              if noting then hiddenNotes(folded, gathered.optionRestrictions,
                ctxFor, byId) else Vector.empty)
          case host: OfferHost =>
            val offered = gathered.offers.flatMap { case (powerId, offer) =>
              offer.plan(ctxFor(byId(powerId))).map(OfferedPlan(powerId, _))
            }
            (folded ++ host.expand(offered, OfferHost.Pass(state, answered,
              resuming, WalkerSimulation.applies(_, state, powers))),
              gathered.order, Vector.empty)
          case _ => (folded, gathered.order, Vector.empty)

  /** The options of a choose-one or choose-many decision, in order. */
  private def optionRefs(decide: Decide): Vector[DecisionOptionRef] =
    decide.query match
      case one: DecisionQuery.ChooseOne => one.options.map(_.ref)
      case many: DecisionQuery.ChooseMany => many.options.map(_.ref)
      case _ => Vector.empty

  /** For each option a restriction forbids, the note of the first power
    * that forbids it. */
  private def hiddenNotes(ops: Vector[Operation],
      restrictions: Vector[(PowerId, OptionRestriction)],
      ctxFor: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower]): Vector[PowerNoted] =
    if restrictions.isEmpty then Vector.empty
    else ops.collect { case decide: Decide => decide }.flatMap(decide =>
      optionRefs(decide).flatMap { ref =>
        restrictions.find { case (id, restriction) =>
          restriction.fn(ctxFor(byId(id)), ref).nonEmpty
        }.flatMap { case (id, restriction) =>
          restriction.note(ctxFor(byId(id)), ref)
            .map(PowerNoted(id, _, covers = false))
        }
      })

  private def permits(restrictions: Vector[(PowerId, OptionRestriction)],
      ctxFor: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower]): DecisionOptionRef => Boolean =
    ref => restrictions.forall { case (id, restriction) =>
      restriction.fn(ctxFor(byId(id)), ref).isEmpty
    }

  /** Removes the forbidden options from every `Decide` in `ops`; see
    * [[OptionRestriction]] for what happens when nothing is left.
    */
  private def restrictOptions(ops: Vector[Operation],
      restrictions: Vector[(PowerId, OptionRestriction)],
      ctxFor: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower]): Vector[Operation] =
    if restrictions.isEmpty then ops
    else
      val permitted = permits(restrictions, ctxFor, byId)
      ops.flatMap:
        case decide: Decide => WalkerSearch.narrowed(decide, permitted).toVector
        case other => Vector(other)

  /** A required decision with every option forbidden cannot be answered: the
    * violation is the first restriction's, for the first option.
    */
  private def emptiedDecision(decide: Decide,
      restrictions: Vector[(PowerId, OptionRestriction)],
      ctx: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower]): Vector[OathViolation] =
    val required: Option[Vector[DecisionOptionRef]] = decide.query match
      case one: DecisionQuery.ChooseOne => Some(one.options.map(_.ref))
      case many: DecisionQuery.ChooseMany if many.min >= 1 =>
        Some(many.options.map(_.ref))
      case _ => None
    required.filter(_.nonEmpty).toVector.flatMap { refs =>
      val verdicts = refs.map(ref => restrictions.flatMap {
        case (id, restriction) => restriction.fn(ctx(byId(id)), ref)
      }.headOption)
      if verdicts.forall(_.nonEmpty) then verdicts.head.toVector else Vector.empty
    }

  /** Collects every restriction violation from every windowed node in
    * `tree`, run against the tree root (spec decision 9's `Restriction`
    * kind), as `OathRules` requires the whole command to check before any
    * node runs (Task 3 wiring rule: restrictions run once per command, at
    * command entry, before the walk -- not per-node during the walk as
    * decision 10's general protocol describes, a deliberate simplification
    * since resolving every `Branch`'s dynamic children up front would
    * require walking before restrictions are known to pass). A `Branch` is
    * resolved the way the walk resolves it -- `select` against the current
    * state (fix-round ruling H) -- so restrictions declared inside a
    * branch's selected children are collected; `Branch.children` is
    * statically empty and reading it would silently skip them. `select` is
    * already required to be a pure function of state, and the traversal has
    * no answered decisions to offer it, so it passes an empty `PendingTree`
    * at the branch's own path. The traversal also reports a required
    * decision whose every option an `OptionRestriction` forbids, since such a
    * decision could never be answered.
    */
  def restrictionViolations(tree: Operation, powers: WalkerPowers,
      state: ReadyGame, activePlayer: PlayerId,
      answered: Vector[Answered] = Vector.empty): Vector[OathViolation] =
    val (rejected, emptied) = attributed(tree, powers, state, activePlayer,
      answered)
    rejected.map(_.violation) ++ emptied

  /** [[restrictionViolations]], keeping who rejected: the restrictions'
    * violations with their power, and the emptied decisions. */
  private def attributed(tree: Operation, powers: WalkerPowers,
      state: ReadyGame, activePlayer: PlayerId, answered: Vector[Answered])
      : (Vector[Attributed], Vector[OathViolation]) =
    val byId: Map[PowerId, ContributingPower] =
      powers.powers.map(power => power.id -> power).toMap
    /** Each contribution's context carries `answered`, so a restriction can
      * read what was chosen.
      */
    def ctxFor(window: PowerWindow, path: Vector[String], operation: Operation)
        : ContributingPower => PowerCtx =
      power => PowerCtx(state, activePlayer, power.source, window, path,
        operation, state.game.current.walkerProcedure, answered)
    def windowsIn(node: Operation, path: Vector[String])
        : Vector[(PowerWindow, Vector[String], Operation)] =
      val own = node.window.map(w => Vector((w, path, node))).getOrElse(Vector.empty)
      def descend(children: Vector[Operation]) =
        children.zipWithIndex.flatMap { case (child, index) =>
          windowsIn(child, path :+ index.toString) }
      val nested = node match
        // A PrimitiveOperation's `children` is `Vector(this)` (leaf
        // self-reference, see `Operation.scala`); descending into it would
        // recurse forever, so leaves never contribute nested windows.
        case _: PrimitiveOperation => Vector.empty
        case branch: Branch => descend(applyWindow(branch.window, branch,
          state, activePlayer, powers, path, branch.select(state,
            PendingTree(at = path, answered = answered)),
          state.game.current.walkerProcedure, answered)._1)
        case _ => descend(applyWindow(node.window, node, state,
          activePlayer, powers, path, node.children,
          state.game.current.walkerProcedure, answered)._1)
      own ++ nested
    val windows = windowsIn(tree, Vector.empty)
    val rejected = windows.flatMap:
      case (window, path, operation) =>
        val ctx = ctxFor(window, path, operation)
        ContributionCollector.gather(window, powers.powers, ctx).restrictions
          .flatMap { case (powerId, restriction) =>
            val at = ctx(byId(powerId))
            restriction.fn(at, tree).map(Attributed(powerId, at, restriction, _))
          }
    val emptied = windows.flatMap:
      case (window, path, decide: Decide) =>
        val ctx = ctxFor(window, path, decide)
        emptiedDecision(decide, ContributionCollector.gather(window,
          powers.powers, ctx).optionRestrictions, ctx, byId)
      case _ => Vector.empty
    (rejected, emptied)

  /** The restriction look-ahead (rule-gaps design, section 1): `decide`
    * without the options whose answer would break a `Restriction` somewhere in
    * `root`. Each option is probed by appending a hypothetical answer by the
    * decision's owner to `answered` and running [[restrictionViolations]]. An
    * option is removed only when that adds a violation the answers so far do
    * not already produce, so a violation no option causes never empties a
    * decision; the answer-time check still reports it.
    *
    * `ChooseOne` and `ChooseMany` are probed option by option and narrowed as
    * [[restrictOptions]] narrows them. `ChooseAmount` is probed value by value
    * and narrowed to the permitted values when they form one range; with a
    * gap it is left whole and the answer-time check decides. Other query
    * kinds are not probed. `Left` is a required decision with nothing left,
    * carrying the first option's violation. `Right(None)` is an optional one
    * with nothing left, which is not asked.
    *
    * The traversal runs with probing off, so a dry run inside it never probes
    * in turn, and against the state as the walk sees it (no pending tree, the
    * walk's procedure), so the live walk and [[leafAt]] offer the same
    * options.
    */
  def probe(root: Operation, decide: Decide, state: ReadyGame,
      activePlayer: PlayerId, powers: WalkerPowers, answered: Vector[Answered],
      procedure: Option[ProcedureRef]): Either[OathViolation, Option[Decide]] =
    if !powers.probing || !powers.hasRestrictions then Right(Some(decide))
    else
      val quiet = powers.copy(probing = false)
      val seen = state.updateCurrent(_.copy(walkerPending = None,
        walkerProcedure = procedure))
      def violations(answers: Vector[Answered]): Vector[OathViolation] =
        restrictionViolations(root, quiet, seen, activePlayer, answers)
      val baseline = violations(answered).toSet
      def added(answer: DecisionAnswer): Option[OathViolation] =
        violations(answered :+ Answered(decide.decisionId, answer,
          decide.owner)).find(!baseline(_))
      def byOption(refs: Vector[DecisionOptionRef],
          answer: DecisionOptionRef => DecisionAnswer,
          required: Boolean): Either[OathViolation, Option[Decide]] =
        val verdicts = refs.map(ref => ref -> added(answer(ref))).toMap
        if required && refs.nonEmpty && refs.forall(verdicts(_).nonEmpty) then
          Left(verdicts(refs.head).get)
        else Right(WalkerSearch.narrowed(decide, ref => verdicts(ref).isEmpty))
      decide.query match
        case one: DecisionQuery.ChooseOne => byOption(one.options.map(_.ref),
          DecisionAnswer.ChooseOneAnswer(_), required = true)
        case many: DecisionQuery.ChooseMany => byOption(
          many.options.map(_.ref),
          ref => DecisionAnswer.ChooseManyAnswer(Vector(ref)), many.min >= 1)
        case amount: DecisionQuery.ChooseAmount =>
          val verdicts = (amount.min to amount.max).toVector.map(value =>
            value -> added(DecisionAnswer.ChooseAmountAnswer(value)))
          val allowed = verdicts.collect { case (value, None) => value }
          if verdicts.isEmpty then Right(Some(decide))
          else if allowed.isEmpty then Left(verdicts.flatMap(_._2).head)
          else if allowed.last - allowed.head + 1 != allowed.size then
            Right(Some(decide))
          else Right(Some(decide.copy(query = amount.copy(min = allowed.head,
            max = allowed.last, suggested = amount.suggested.map(value =>
              math.max(allowed.head, math.min(allowed.last, value)))))))
        case _ => Right(Some(decide))

  /** The notes the look-ahead writes for the options `narrowed` no longer
    * offers: for each, the note of the restriction whose violation answering
    * it would add. The walker asks only at a decision reached fresh.
    */
  def lookAheadNotes(root: Operation, decide: Decide, narrowed: Decide,
      state: ReadyGame, activePlayer: PlayerId, powers: WalkerPowers,
      answered: Vector[Answered], procedure: Option[ProcedureRef])
      : Vector[PowerNoted] =
    val offered = optionRefs(narrowed).toSet
    val hidden = optionRefs(decide).filterNot(offered)
    if hidden.isEmpty then Vector.empty
    else
      val quiet = powers.copy(probing = false)
      val seen = state.updateCurrent(_.copy(walkerPending = None,
        walkerProcedure = procedure))
      def found(answers: Vector[Answered]): Vector[Attributed] =
        attributed(root, quiet, seen, activePlayer, answers)._1
      val baseline = found(answered).map(_.violation).toSet
      hidden.flatMap { ref =>
        val answer = decide.query match
          case _: DecisionQuery.ChooseMany =>
            DecisionAnswer.ChooseManyAnswer(Vector(ref))
          case _ => DecisionAnswer.ChooseOneAnswer(ref)
        found(answered :+ Answered(decide.decisionId, answer, decide.owner))
          .find(breach => !baseline(breach.violation))
          .flatMap(breach => breach.restriction.note(breach.ctx, ref)
            .map(PowerNoted(breach.power, _, covers = false)))
      }

  /** Resolves the node addressed by `pending.at` (a child-index path rooted
    * at `action`), or `None` when a segment is non-numeric or out of range (a
    * fabricated or stale position). Each step down the path resolves children
    * through [[foldedChildrenAt]] -- the SAME fold the live walk applied on
    * its way to this park (fix-round ruling J) -- rather than the declared,
    * unfolded tree: a `Branch` is resolved by evaluating `select(state, ...)`
    * (with `at` set to the path consumed so far, matching `walkBranch`'s
    * `branchTree`), and any windowed node's children are folded through
    * `powers` exactly as `walkComposite`/`walkBranch`/`walkLeaf` do, so a
    * transform that inserts operations around a windowed node does not make
    * this address the wrong node. `powers` MUST be the same vector the live
    * walk that parked here used -- a `Transform` is required to be a pure
    * function of state (same invariant `Branch.select` already carries), so
    * re-folding here with the same `powers` reproduces the exact indices the
    * walk parked at. The callers are [[ProcedureWalker.parkedRoll]] and
    * [[ProcedureWalker.openDecisions]] (and through it `parkedDecide`), which
    * answer checks, projections and awaited-player lookups use. It lives here
    * (like [[applyWindow]]/[[restrictionViolations]] above) to keep
    * `ProcedureWalker.scala` under the project's line bound.
    *
    * When `powers.probing` is on, a `Decide` is narrowed by the restriction
    * look-ahead ([[probe]]), exactly as the walk narrowed it before
    * parking. A position the walk would not have parked at, whose probe
    * empties the decision, is returned as declared, and the answer-time
    * check refuses it.
    */
  def leafAt(state: ReadyGame, action: Operation, pending: PendingTree,
      powers: WalkerPowers): Option[Operation] =
    resolveAt(state, pending, powers, action, pending.at, Vector.empty,
      Set.empty).map:
        case decide: Decide => probe(action, decide, state,
          state.game.current.turn.activePlayer, powers, pending.answered,
          state.game.current.walkerProcedure).toOption.flatten
          .getOrElse(decide)
        case other => other

  private def resolveAt(state: ReadyGame, pending: PendingTree,
      powers: WalkerPowers, node: Operation, remaining: Vector[String],
      consumed: Vector[String], gathered: Set[PowerWindow]): Option[Operation] =
    remaining.headOption match
      case None => Some(node)
      case Some(segment) => segment.toIntOption.flatMap { index =>
        val (children, nextGathered) = foldedChildrenAt(state, pending,
          powers, node, consumed, gathered)
        if index >= 0 && index < children.size then
          resolveAt(state, pending, powers, children(index), remaining.tail,
            consumed :+ segment, nextGathered)
        else None
      }

  /** The children `node` presents at `path` for the purpose of navigating one
    * more path segment -- mirroring `walkBranch`/`walkLeaf`/`walkComposite`'s
    * dispatch, but computing only the folded children rather than executing
    * anything. `gathered` mirrors [[WalkerHooks.gathered]]: a windowed leaf's
    * own fold (applied to `Vector(leaf)`, since a `PrimitiveOperation`'s
    * `children` is a self-reference) is applied at most once per branch, so a
    * transform that hands the leaf back unchanged does not re-trigger its own
    * window on the next path segment. A composite's fold needs no such guard
    * -- its fold cannot reproduce the composite itself, only its children.
    */
  private def foldedChildrenAt(state: ReadyGame, pending: PendingTree,
      powers: WalkerPowers, node: Operation, path: Vector[String],
      gathered: Set[PowerWindow]): (Vector[Operation], Set[PowerWindow]) =
    val activePlayer = state.game.current.turn.activePlayer
    node match
      case branch: Branch =>
        val selected = branch.select(state, pending.copy(at = path))
        val (folded, _) = applyWindow(branch.window, branch, state,
          activePlayer, powers, path, selected, state.game.current.walkerProcedure,
          pending.answered, resuming = true)
        (folded, gathered)
      case leaf: PrimitiveOperation =>
        leaf.window match
          case Some(w) if !gathered.contains(w) =>
            val (folded, _) = applyWindow(Some(w), leaf, state,
              activePlayer, powers, path, Vector(leaf),
              state.game.current.walkerProcedure, pending.answered,
              resuming = true)
            (folded, gathered + w)
          case _ => (leaf.children, gathered)
      case composite =>
        val (folded, _) = applyWindow(composite.window, composite, state,
          activePlayer, powers, path, composite.children,
          state.game.current.walkerProcedure, pending.answered, resuming = true)
        (folded, gathered)

/** Power attribution and re-entry guard threaded DOWN one walk branch (unlike
  * the walker's `WalkCtx`, which threads FORWARD across siblings).
  *
  *  - `inherited` is the contribution order gathered by every enclosing
  *    windowed node, outermost first, de-duplicated with first occurrence
  *    winning. A leaf records exactly this on its event: a windowed composite
  *    emits no event of its own, so its transform reaches the journal only
  *    through the leaves it shaped (fix-round ruling F).
  *  - `gathered` holds the windows already folded on this branch. A leaf's
  *    fold is applied to `Vector(leaf)`, so the ordinary "insert an op"
  *    transform hands the leaf back inside its own folded vector; without this
  *    guard, walking that vector would gather the same window forever. A
  *    composite's fold cannot reproduce the composite (a transform only ever
  *    sees the children), so composites add nothing here.
  *  - `strict` is set inside a `required` composite (`PayCost`, `Draw`, ...).
  *    The walker runs such a composite as its `Move` children, which are
  *    best-effort on their own, so the composite's `required` has to ride down
  *    to the leaves it is walked as.
  */
private[walker] final case class WalkerHooks(inherited: Vector[PowerId],
    gathered: Set[PowerWindow], strict: Boolean = false):
  def withOrder(order: Vector[PowerId]): WalkerHooks =
    if order.isEmpty then this
    else copy(inherited = (inherited ++ order).distinct)

private[walker] object WalkerHooks:
  val none: WalkerHooks = WalkerHooks(Vector.empty, Set.empty)
