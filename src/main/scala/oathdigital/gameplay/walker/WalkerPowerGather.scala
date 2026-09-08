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
  * re-exports publicly is `WalkerPowers` (declared on `ProcedureWalker`) and
  * `ProcedureWalker.restrictionViolations`, which just delegates to
  * [[restrictionViolations]] below.
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
  def applyWindow(window: Option[PowerWindow], state: ReadyGame,
      actor: PlayerId, powers: WalkerPowers, path: Vector[String],
      ops: Vector[Operation]): (Vector[Operation], Vector[PowerId]) =
    window match {
      case None => (ops, Vector.empty)
      case Some(w) =>
        val byId: Map[PowerId, ContributingPower] =
          powers.powers.map(power => power.id -> power).toMap
        def ctxFor(power: ContributingPower): PowerCtx =
          PowerCtx(state, actor, power.source, w, path)
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
    def ctxFor(window: PowerWindow, path: Vector[String])
        : ContributingPower => PowerCtx =
      power => PowerCtx(state, actor, power.source, window, path)
    def windowsIn(node: Operation, path: Vector[String])
        : Vector[(PowerWindow, Vector[String])] = {
      val own = node.window.map(w => Vector(w -> path)).getOrElse(Vector.empty)
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
    windowsIn(tree, Vector.empty).flatMap { case (window, path) =>
      val gathered = ContributionCollector.gather(window, powers.powers,
        ctxFor(window, path))
      gathered.restrictions.flatMap { case (powerId, restriction) =>
        restriction.fn(ctxFor(window, path)(byId(powerId)), tree)
      }
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
