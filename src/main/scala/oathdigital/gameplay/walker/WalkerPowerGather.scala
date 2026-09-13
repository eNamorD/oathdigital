package oathdigital.gameplay.walker

import oathdigital.gameplay.{OathViolation, ReadyGame}
import oathdigital.gameplay.operations.{Branch, Operation, PrimitiveOperation}
import oathdigital.gameplay.powerresolver.{ContributingPower,
  ContributionCollector, PowerCtx, PowerWindow}
import oathdigital.model.{PendingTree, PlayerId, PowerId}

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
      actor: PlayerId, powers: WalkerPowers, path: Vector[String],
      ops: Vector[Operation]): (Vector[Operation], Vector[PowerId]) =
    window match {
      case None => (ops, Vector.empty)
      case Some(w) =>
        val byId: Map[PowerId, ContributingPower] =
          powers.powers.map(power => power.id -> power).toMap
        def ctxFor(power: ContributingPower): PowerCtx =
          PowerCtx(state, actor, power.source, w, path, operation)
        val gathered = ContributionCollector.gather(w, powers.powers, ctxFor)
        val folded = gathered.transforms.foldLeft(ops) {
          case (acc, (powerId, transform)) =>
            transform.fn(ctxFor(byId(powerId)), acc)
        }
        (folded, gathered.order)
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
    * at the branch's own path.
    */
  def restrictionViolations(tree: Operation, powers: WalkerPowers,
      state: ReadyGame, actor: PlayerId): Vector[OathViolation] = {
    val byId: Map[PowerId, ContributingPower] =
      powers.powers.map(power => power.id -> power).toMap
    def ctxFor(window: PowerWindow, path: Vector[String], operation: Operation)
        : ContributingPower => PowerCtx =
      power => PowerCtx(state, actor, power.source, window, path, operation)
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
          PendingTree(at = path, answered = Vector.empty, actor = actor)))
        case _ => descend(node.children)
      }
      own ++ nested
    }
    windowsIn(tree, Vector.empty).flatMap { case (window, path, operation) =>
      val gathered = ContributionCollector.gather(window, powers.powers,
        ctxFor(window, path, operation))
      gathered.restrictions.flatMap { case (powerId, restriction) =>
        restriction.fn(ctxFor(window, path, operation)(byId(powerId)), tree)
      }
    }
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
      gathered: Set[PowerWindow]): (Vector[Operation], Set[PowerWindow]) =
    node match {
      case branch: Branch =>
        val selected = branch.select(state, pending.copy(at = path))
        val (folded, _) = applyWindow(branch.window, branch, state, pending.actor,
          powers, path, selected)
        (folded, gathered)
      case leaf: PrimitiveOperation =>
        leaf.window match {
          case Some(w) if !gathered.contains(w) =>
            val (folded, _) = applyWindow(Some(w), leaf, state, pending.actor,
              powers, path, Vector(leaf))
            (folded, gathered + w)
          case _ => (leaf.children, gathered)
        }
      case composite =>
        val (folded, _) = applyWindow(composite.window, composite, state, pending.actor,
          powers, path, composite.children)
        (folded, gathered)
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
  */
private[walker] final case class WalkerHooks(inherited: Vector[PowerId],
    gathered: Set[PowerWindow]) {
  def withOrder(order: Vector[PowerId]): WalkerHooks =
    if (order.isEmpty) this
    else copy(inherited = (inherited ++ order).distinct)
}

private[walker] object WalkerHooks {
  val none: WalkerHooks = WalkerHooks(Vector.empty, Set.empty)
}
