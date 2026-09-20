package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{ContributingPower, ContributionCollector, OptionRestriction, PowerCtx}
import oathdigital.model.{Branch, Decide, DecisionOptionRef, DecisionQuery, OathViolation, Operation, PendingTree, PlayerId, PowerId, PowerWindow, PrimitiveOperation, ReadyGame}

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
private[walker] object WalkerPowerGather {

  /** Gathers powers at `window` (a no-op for `None` -- spec decision 9's
    * third case: an engine-internal node contributes nothing) and folds
    * `ops` through the resulting transforms, in the deterministic order
    * `ContributionCollector.gather` produced. Returns the folded operations
    * (`== ops` unchanged when there is no window or nothing applicable) and
    * the gather's contribution order, to be recorded verbatim on whichever
    * `WalkerStepRecorded` this node's execution produces (Task 3 wiring
    * rules 1-3).
    */
  def applyWindow(window: Option[PowerWindow], operation: Operation,
      state: ReadyGame,
      activePlayer: PlayerId, powers: WalkerPowers, path: Vector[String],
      ops: Vector[Operation]): (Vector[Operation], Vector[PowerId]) =
    window match {
      case None => (ops, Vector.empty)
      case Some(w) =>
        val byId: Map[PowerId, ContributingPower] =
          powers.powers.map(power => power.id -> power).toMap
        def ctxFor(power: ContributingPower): PowerCtx =
          PowerCtx(state, activePlayer, power.source, w, path, operation)
        val gathered = ContributionCollector.gather(w, powers.powers, ctxFor)
        val folded = gathered.transforms.foldLeft(ops) {
          case (acc, (powerId, transform)) =>
            transform.fn(ctxFor(byId(powerId)), acc)
        }
        val restricted = operation match {
          case _: Decide => restrictOptions(folded, gathered.optionRestrictions,
            ctxFor, byId)
          case _ => folded
        }
        (restricted, gathered.order)
    }

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
    if (restrictions.isEmpty) ops
    else {
      val permitted = permits(restrictions, ctxFor, byId)
      ops.flatMap {
        case decide: Decide => decide.query match {
          case one: DecisionQuery.ChooseOne => Vector(decide.copy(query =
            one.copy(options = one.options.filter(o => permitted(o.ref)))))
          case many: DecisionQuery.ChooseMany =>
            val options = many.options.filter(o => permitted(o.ref))
            if (many.min == 0 && options.isEmpty) Vector.empty
            else Vector(decide.copy(query = many.copy(
              min = math.min(many.min, options.size),
              max = math.min(many.max, options.size), options = options)))
          case _ => Vector(decide)
        }
        case other => Vector(other)
      }
    }

  /** A required decision with every option forbidden cannot be answered: the
    * violation is the first restriction's, for the first option.
    */
  private def emptiedDecision(decide: Decide,
      restrictions: Vector[(PowerId, OptionRestriction)],
      ctx: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower]): Vector[OathViolation] = {
    val required: Option[Vector[DecisionOptionRef]] = decide.query match {
      case one: DecisionQuery.ChooseOne => Some(one.options.map(_.ref))
      case many: DecisionQuery.ChooseMany if many.min >= 1 =>
        Some(many.options.map(_.ref))
      case _ => None
    }
    required.filter(_.nonEmpty).toVector.flatMap { refs =>
      val verdicts = refs.map(ref => restrictions.flatMap {
        case (id, restriction) => restriction.fn(ctx(byId(id)), ref)
      }.headOption)
      if (verdicts.forall(_.nonEmpty)) verdicts.head.toVector else Vector.empty
    }
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
      state: ReadyGame, activePlayer: PlayerId): Vector[OathViolation] = {
    val byId: Map[PowerId, ContributingPower] =
      powers.powers.map(power => power.id -> power).toMap
    def ctxFor(window: PowerWindow, path: Vector[String], operation: Operation)
        : ContributingPower => PowerCtx =
      power => PowerCtx(state, activePlayer, power.source, window, path, operation)
    def windowsIn(node: Operation, path: Vector[String])
        : Vector[(PowerWindow, Vector[String], Operation)] = {
      val own = node.window.map(w => Vector((w, path, node))).getOrElse(Vector.empty)
      def descend(children: Vector[Operation]) =
        children.zipWithIndex.flatMap { case (child, index) =>
          windowsIn(child, path :+ index.toString) }
      val nested = node match {
        // A PrimitiveOperation's `children` is `Vector(this)` (leaf
        // self-reference, see `Operation.scala`); descending into it would
        // recurse forever, so leaves never contribute nested windows.
        case _: PrimitiveOperation => Vector.empty
        case branch: Branch => descend(branch.select(state,
          PendingTree(at = path, answered = Vector.empty)))
        case _ => descend(node.children)
      }
      own ++ nested
    }
    val rejected = windowsIn(tree, Vector.empty).flatMap {
      case (window, path, operation) =>
        val gathered = ContributionCollector.gather(window, powers.powers,
          ctxFor(window, path, operation))
        gathered.restrictions.flatMap { case (powerId, restriction) =>
          restriction.fn(ctxFor(window, path, operation)(byId(powerId)), tree)
        }
    }
    val emptied = windowsIn(tree, Vector.empty).flatMap {
      case (window, path, decide: Decide) =>
        val ctx = ctxFor(window, path, decide)
        emptiedDecision(decide, ContributionCollector.gather(window,
          powers.powers, ctx).optionRestrictions, ctx, byId)
      case _ => Vector.empty
    }
    rejected ++ emptied
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
    * walk parked at. The sole caller is [[ProcedureWalker.parkedRoll]]/
    * [[ProcedureWalker.parkedDecide]] (in turn `OathRules.parkedContinue`),
    * moved here (like [[applyWindow]]/[[restrictionViolations]] above) to
    * keep `ProcedureWalker.scala` under the project's line bound.
    */
  def leafAt(state: ReadyGame, action: Operation, pending: PendingTree,
      powers: WalkerPowers): Option[Operation] =
    resolveAt(state, pending, powers, action, pending.at, Vector.empty,
      Set.empty)

  private def resolveAt(state: ReadyGame, pending: PendingTree,
      powers: WalkerPowers, node: Operation, remaining: Vector[String],
      consumed: Vector[String], gathered: Set[PowerWindow]): Option[Operation] =
    remaining.headOption match {
      case None => Some(node)
      case Some(segment) => segment.toIntOption.flatMap { index =>
        val (children, nextGathered) = foldedChildrenAt(state, pending,
          powers, node, consumed, gathered)
        if (index >= 0 && index < children.size)
          resolveAt(state, pending, powers, children(index), remaining.tail,
            consumed :+ segment, nextGathered)
        else None
      }
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
      gathered: Set[PowerWindow]): (Vector[Operation], Set[PowerWindow]) = {
    val activePlayer = state.game.current.turn.activePlayer
    node match {
      case branch: Branch =>
        val selected = branch.select(state, pending.copy(at = path))
        val (folded, _) = applyWindow(branch.window, branch, state,
          activePlayer, powers, path, selected)
        (folded, gathered)
      case leaf: PrimitiveOperation =>
        leaf.window match {
          case Some(w) if !gathered.contains(w) =>
            val (folded, _) = applyWindow(Some(w), leaf, state,
              activePlayer, powers, path, Vector(leaf))
            (folded, gathered + w)
          case _ => (leaf.children, gathered)
        }
      case composite =>
        val (folded, _) = applyWindow(composite.window, composite, state,
          activePlayer, powers, path, composite.children)
        (folded, gathered)
    }
  }
}

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
    gathered: Set[PowerWindow], strict: Boolean = false) {
  def withOrder(order: Vector[PowerId]): WalkerHooks =
    if (order.isEmpty) this
    else copy(inherited = (inherited ++ order).distinct)
}

private[walker] object WalkerHooks {
  val none: WalkerHooks = WalkerHooks(Vector.empty, Set.empty)
}
