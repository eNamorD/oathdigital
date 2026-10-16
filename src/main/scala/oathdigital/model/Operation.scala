package oathdigital.model

/** Root of the reusable operation algebra: the vocabulary the walker itself
  * speaks, apart from the operations of Oath.
  *
  * An operation exposes its immediate children; a tree's leaves are
  * [[LeafOperation]]s, which expose themselves as their only child so the
  * whole action tree shares one accessor. `window` is the hook point for
  * powers to wire their effects on the tree; `None` means engine-internal.
  *
  * This root trait is deliberately not `sealed`, and that is now a feature
  * rather than a workaround. Scala confines a sealed type's direct
  * subclasses to one source file, so an unsealed root is what lets generic
  * vocabulary be authored outside Oath's own family: the control operations
  * below, and a test's own windowed node (`WalkerStubs.WindowedNode`).
  * Oath's operations live under the sealed [[CoreOperation]] in
  * `CoreOperations.scala`, where exhaustive matching over the game's
  * vocabulary is what the serializer and the restriction screener rely on.
  */
trait Operation:
  /** Hook point for power windows; None = engine-internal. */
  def window: Option[PowerWindow] = None
  /** Batch posture: a required operation rejects its batch rather than being
    * skipped when a restriction refuses it. Generic, because generic walker
    * code reads it off an `Operation`-typed node, exactly as it reads
    * `window`. */
  def required: Boolean = false
  /** Immediate children of this node. Leaves expose `Vector(this)`. */
  def children: Vector[Operation]

/** A leaf of the operation tree: it exposes itself as its only child, so the
  * whole tree shares one `children` accessor and [[Operation.flatten]] needs
  * one leaf test. Unsealed, so a leaf may be authored outside
  * `CoreOperations.scala`; [[PrimitiveOperation]] is Oath's sealed half of
  * this.
  */
trait LeafOperation extends Operation:
  final val children: Vector[Operation] = Vector(this)

/** A custom composite that plans its children from inputs it keeps, so it
  * cannot be rebuilt from new children alone (a card play's body).
  * `rewritten` rebuilds it with each child passed through `rewrite`, and
  * the rebuilt node keeps the rewrite whenever it plans its children
  * again. `OperationRewrite` descends it unless it carries a window of its
  * own.
  */
trait RewritableComposite extends Operation:
  def rewritten(rewrite: Operation => Operation): Operation

/** Ends the walk where it is reached: the procedure finishes at once, and
  * nothing after it in the tree runs. It changes no state and is never
  * part of a recorded batch, so the journal holds only the procedure's
  * usual completion. A Campaign that is skipped, or left with nothing to
  * attack, ends with it. */
case object EndWalk extends Operation:
  val children: Vector[Operation] = Vector.empty

object Operation:
  /** Depth-first leaf sequence of an operation tree.
    *
    * A [[LeafOperation]] is a leaf and flattens to itself; composites
    * flatten to the leaves of each child, in child order.
    */
  def flatten(operation: Operation): Vector[Operation] =
    operation match
      case _: LeafOperation => Vector(operation)
      case _ => operation.children.flatMap(flatten)

/** Parks a walker until `owner` resolves the decision `query` states.
  *
  * `query` is the single source of both halves of the contract: the walker
  * accepts exactly the answers it declares (via `DecisionQueries`), and the
  * projector offers exactly the options it declares. There is deliberately
  * no `validate` closure beside it — a legality fact that is not expressible
  * as an option set is a fact the client could never have been shown, so it
  * belongs in how the tree BUILDS the query, not in a second check the
  * projector cannot read. An option that authoritative state no longer
  * supports is simply absent from the query the next command rebuilds.
  *
  * `owner` is a concrete player rather than a resolver: the tree is rebuilt
  * against live state on every command, so whatever would have been computed
  * at resume time can be computed at build time instead.
  *
  * `coOwners` are further players who may answer the same decision. One
  * answer from any owner resolves it, so a `Repeat` around it re-asks it
  * after each answer.
  *
  * `window` makes the decision hookable: the walker folds the gathered
  * transforms over `Vector(this)` before walking it, so a power may insert
  * operations around the decision, or replace its query.
  *
  * `passWhenEmpty` lets the search leave a choose-one with no option (global
  * operation restrictions design, "Empty choices"): a search passes it, as
  * it passes an empty optional choose-many, instead of failing the path.
  * The live walk parks it with every option shown unavailable and one
  * Continue button, [[Decide.Pass]], which passes it. With one option left
  * it parks as any decision does, so the player confirms it.
  *
  * `presentation` says where a client answers it: on the board, where each
  * option with a place on the table is drawn, or in the Action panel.
  */
final case class Decide(decisionId: String, owner: PlayerId,
    query: DecisionQuery,
    override val window: Option[PowerWindow] = None,
    coOwners: Vector[PlayerId] = Vector.empty,
    passWhenEmpty: Boolean = false,
    presentation: DecisionPresentation = DecisionPresentation.Board)
    extends LeafOperation:
  require(!passWhenEmpty || query.isInstanceOf[DecisionQuery.ChooseOne],
    "only a choose-one can be passed when empty")

  /** Everyone who may answer: `owner` first, then the co-owners, each once.
    * `owner` stays the primary owner, the player `awaitedPlayer` names.
    */
  def owners: Vector[PlayerId] = (owner +: coOwners).distinct

object Decide:
  /** The answer that passes a choose-one marked `passWhenEmpty`, offered as
    * a Continue button when nothing else may be chosen (catalog batch 8,
    * slice 10, part 2b). A power reads it as no answer. */
  val Pass: DecisionOptionRef.Button = DecisionOptionRef.Button("pass")

/** A leaf whose concrete deltas are decided AT WALK TIME: the walker calls
  * `build(state, pending)` when it reaches the node and executes whatever
  * `CoreOperation`s come back, recording them in the node's
  * `WalkerStepRecorded.ops`.
  *
  * The closure lives in the action tree, which is derived per command and
  * never persisted, so it may close over anything reachable at build time
  * (e.g. a chosen relic id from an earlier answered decision). A `build` that
  * returns `Vector.empty` runs nothing and records no step (nothing ran).
  * Flatten sees a leaf: `Operation.flatten(BuildOps(...))` is itself.
  *
  * A `required` batch runs whole or rejects: an operation a restriction
  * refuses rejects the batch instead of being skipped, so a search counts the
  * path as failed.
  */
final case class BuildOps(
    build: (ReadyGame, PendingTree) => Either[OathViolation, Vector[CoreOperation]],
    override val window: Option[PowerWindow] = None,
    override val required: Boolean = false)
    extends LeafOperation

/** Re-executes `body` until `guard` is false. The guard runs only at command
  * time (the walker's job); `Operation.flatten` always descends into `body`,
  * and replay applies recorded ops, never re-guarding.
  */
final case class Repeat(guard: (ReadyGame, PendingTree) => Boolean,
    body: Operation) extends Operation:
  override val children: Vector[Operation] = Vector(body)

/** A composite whose children are chosen AT WALK TIME: the walker evaluates
  * `select(state, pending)` when it reaches the node and walks the returned
  * operations in order.
  *
  * `children` is statically empty because the selection is dynamic, so
  * `Operation.flatten(Branch(...))` is empty and a Branch must never sit on
  * an action root that other code flattens — the walker resolves it and
  * replay applies recorded ops, never re-selecting. Selected children are
  * addressed by child index inside the branch, exactly like static children.
  */
final case class Branch(select: (ReadyGame, PendingTree) => Vector[Operation])
    extends Operation:
  override val children: Vector[Operation] = Vector.empty

/** An automatic power's effect, judged where the walk reaches it (catalog
  * batch 8, slice 10): the walker runs `effect` when no power-use
  * restriction refuses `user` using `power` from `card`, and nothing
  * otherwise. Like a `Branch`, it has no static children: a resume cursor
  * addresses the vector the walker resolved. The engine judges it without
  * naming the power. */
final case class AutomaticUse(user: PlayerId, power: PowerId, card: CardId,
    effect: (ReadyGame, PendingTree) => Vector[Operation]) extends Operation:
  override val children: Vector[Operation] = Vector.empty

/** Runs `children` in order. The walker's sequence composite; `Repeat`,
  * `Sequence`, and `Branch` are the composites the walker consumes this slice.
  */
final case class Sequence(override val children: Vector[Operation],
    override val window: Option[PowerWindow] = None) extends Operation


object Sequence:
  /** Vararg builder so action trees read `Sequence(a, b)`
   *  instead of wrapping a vector by hand.
   */
  def apply(first: Operation, rest: Operation*): Sequence =
    new Sequence(first +: rest.toVector)
