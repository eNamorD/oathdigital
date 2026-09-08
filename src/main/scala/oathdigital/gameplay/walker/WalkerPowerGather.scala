package oathdigital.gameplay.walker

import oathdigital.gameplay.{OathViolation, ReadyGame}
import oathdigital.gameplay.operations.{BuildOps, CoreOperation, Decide,
  Operation, PrimitiveOperation, Roll}
import oathdigital.gameplay.powerresolver.{ContributingPower,
  ContributionCollector, PowerCtx, PowerWindow}
import oathdigital.model.{PlayerId, PowerId}

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

  /** Contribution order only, for a windowed leaf whose type-specific
    * handling (Roll/Decide resume) never accepts an inserted/removed
    * operation -- see `ProcedureWalker.walkLeaf`'s `Some(remaining)` branch.
    */
  def gatherOrderAt(window: Option[PowerWindow], state: ReadyGame,
      actor: PlayerId, powers: WalkerPowers, path: Vector[String])
      : Vector[PowerId] = window match {
    case None => Vector.empty
    case Some(w) =>
      ContributionCollector.gather(w, powers.powers, power =>
        PowerCtx(state, actor, power.source, w, path)).order
  }

  /** `true` for an operation the walker can batch-execute directly (a plain
    * delta): `Decide`/`Roll` need to park, and `BuildOps` needs its closure
    * resolved against live state, neither of which fits "run through the
    * executor and record one step" -- a node whose transform produced one of
    * these instead falls back to normal per-child walking.
    */
  private def isPlainDelta(op: CoreOperation): Boolean = op match {
    case _: Decide | _: Roll | _: BuildOps => false
    case _ => true
  }

  /** Flattens `ops` to leaves and reports them as an executable batch only
    * when every leaf is a plain delta (`isPlainDelta`); `None` otherwise, so
    * the caller can fall back to walking the vector structurally instead
    * (needed for a Decide/Roll a transform inserted or left in place).
    */
  def resolvePlainBatch(ops: Vector[Operation]): Option[Vector[CoreOperation]] = {
    val flattened = ops.flatMap(Operation.flatten)
    val plain = flattened.collect {
      case op: CoreOperation if isPlainDelta(op) => op
    }
    Option.when(plain.size == flattened.size)(plain)
  }

  /** Collects every restriction violation from every windowed node in
    * `tree`, run against the tree root (spec decision 9's `Restriction`
    * kind), as `OathRules` requires the whole command to check before any
    * node runs (Task 3 wiring rule: restrictions run once per command, at
    * command entry, before the walk -- not per-node during the walk as
    * decision 10's general protocol describes, a deliberate simplification
    * since resolving every `Branch`'s dynamic children up front would
    * require walking before restrictions are known to pass). The traversal
    * is static (`Operation.children`), so a `Branch`'s dynamically-selected
    * children (empty statically) are not visited -- a future task may need
    * to widen this once a power restricts something reachable only through a
    * Branch.
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
      val nested = node match {
        // A PrimitiveOperation's `children` is `Vector(this)` (leaf
        // self-reference, see `Operation.scala`); descending into it would
        // recurse forever, so leaves never contribute nested windows.
        case _: PrimitiveOperation => Vector.empty
        case _ => node.children.zipWithIndex.flatMap { case (child, index) =>
          windowsIn(child, path :+ index.toString) }
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
