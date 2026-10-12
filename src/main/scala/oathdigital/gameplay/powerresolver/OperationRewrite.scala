package oathdigital.gameplay.powerresolver

import oathdigital.model.{Branch, BuildOps, CoreOperation, Operation,
  PendingTree, PrimitiveOperation, ReadyGame, Repeat, RewritableComposite,
  Sequence}

/** Finds the operations a window's children hold by what they are, and
  * replaces them (operation rewrite design). A transform calls it on its
  * children; the walker knows nothing of it.
  *
  * It descends through `Sequence`, `Repeat`, `Branch`, `BuildOps` and a
  * `RewritableComposite`, and leaves alone any node below that carries a
  * window of its own: that
  * subtree belongs to its window's powers. The node being folded is entered
  * even when windowed, since a windowed leaf is handed to its own transforms
  * as its only child, and the walker does not fold that window again below
  * it.
  *
  * An operation in the tree is matched when the window folds, with the
  * fold's state and answers. One a `Branch` selects or a `BuildOps` builds
  * is matched when the walk reaches it, with that moment's state and
  * answers, so the tree keeps its shape on every fold. What a match returns
  * is not matched again. One replacement takes the operation's place;
  * several, or none, go in a `Sequence` there. The ops a `BuildOps` builds
  * are a flat batch, so there the replacements are spliced in.
  */
object OperationRewrite:
  /** What to replace, given the state and answers of the moment it is
    * matched. An operation it is not defined at stays as it is. */
  type Matcher[A, B] =
    (ReadyGame, PendingTree) => PartialFunction[A, Vector[B]]

  /** `children` with each core operation `matcher` matches replaced,
    * including those a closure builds. A core composite such as
    * `Discard.Denizen` is matched whole; only the walker's own composites
    * and a `RewritableComposite` are descended. Any other node stays as it
    * is. */
  def ops(ctx: PowerCtx, children: Vector[Operation])(
      matcher: Matcher[CoreOperation, CoreOperation]): Vector[Operation] =
    children.map(Ops(Scope(ctx), matcher).at(ctx.state, folding(ctx)))

  /** `children` with each node `matcher` matches replaced, in the tree only.
    * A `BuildOps` or a `Branch` it matches is replaced whole, and a
    * `BuildOps` it does not match is not entered. `Sequence` and `Repeat`
    * are descended, never matched. A `RewritableComposite` it does not
    * match is entered. */
  def nodes(ctx: PowerCtx, children: Vector[Operation])(
      matcher: Matcher[Operation, Operation]): Vector[Operation] =
    children.map(Nodes(Scope(ctx), matcher).at(ctx.state, folding(ctx)))

  /** The answers as the fold sees them. */
  private def folding(ctx: PowerCtx): PendingTree =
    PendingTree(ctx.nodePath, ctx.answered)

  /** One replacement in the operation's place; several, or none, in a
    * `Sequence` there. */
  private def placed(replacements: Vector[Operation]): Operation =
    replacements match
      case Vector(one) => one
      case several => Sequence(several)

  /** Which nodes below the fold belong to it. */
  private final case class Scope(ctx: PowerCtx):
    /** Whether `node` carries a window of its own, which its own powers
      * fold. */
    def nested(node: Operation): Boolean =
      node.window.nonEmpty && !folded(node)

    /** Whether `node` is the windowed leaf being folded, or a leaf below it
      * with its window (`WalkerHooks.gathered`). */
    private def folded(node: Operation): Boolean = ctx.operation match
      case leaf: PrimitiveOperation =>
        node.isInstanceOf[PrimitiveOperation] && node.window == leaf.window
      case _ => false

  private final case class Ops(scope: Scope,
      matcher: Matcher[CoreOperation, CoreOperation]):
    def at(ready: ReadyGame, pending: PendingTree)(node: Operation)
        : Operation = node match
      case _ if scope.nested(node) => node
      case build: BuildOps => build.copy(build = (now, answers) =>
        build.build(now, answers).map(_.flatMap(operation =>
          matcher(now, answers).applyOrElse(operation,
            (same: CoreOperation) => Vector(same)))))
      case branch: Branch => Branch((now, answers) =>
        branch.select(now, answers).map(at(now, answers)))
      case sequence: Sequence =>
        sequence.copy(children = sequence.children.map(at(ready, pending)))
      case repeat: Repeat =>
        repeat.copy(body = at(ready, pending)(repeat.body))
      case core: CoreOperation =>
        matcher(ready, pending).lift(core).fold[Operation](core)(placed)
      case composite: RewritableComposite =>
        composite.rewritten(at(ready, pending))
      case other => other

  private final case class Nodes(scope: Scope,
      matcher: Matcher[Operation, Operation]):
    def at(ready: ReadyGame, pending: PendingTree)(node: Operation)
        : Operation = node match
      case _ if scope.nested(node) => node
      case sequence: Sequence =>
        sequence.copy(children = sequence.children.map(at(ready, pending)))
      case repeat: Repeat =>
        repeat.copy(body = at(ready, pending)(repeat.body))
      case branch: Branch =>
        matcher(ready, pending).lift(branch).fold[Operation](
          Branch((now, answers) =>
            branch.select(now, answers).map(at(now, answers))))(placed)
      case composite: RewritableComposite =>
        matcher(ready, pending).lift(composite).fold[Operation](
          composite.rewritten(at(ready, pending)))(placed)
      case other =>
        matcher(ready, pending).lift(other).fold[Operation](other)(placed)
