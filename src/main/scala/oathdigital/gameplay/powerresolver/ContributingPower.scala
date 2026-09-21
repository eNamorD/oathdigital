package oathdigital.gameplay.powerresolver

import oathdigital.model.{CoreOperation, DecisionOptionRef, OathViolation, Operation, PlayerId, PowerId, PowerResolution, PowerWindow, ProcedureRef, ReadyGame, RuleSourceRef}

/** Everything a contribution may read at the node it hooks. Carries no
  * mutable state and no catalog -- a power looks up whatever else it needs
  * (catalog data, other game state) through `state`.
  */
final case class PowerCtx(
    state: ReadyGame,
    /** The player whose procedure is running -- the traveller, the recoverer,
      * the taker. Not the power's activator (only the active player selects
      * powers) and not a parked decision's owner, which a contribution hooked
      * on a `Decide` reads from `operation`.
      */
    activePlayer: PlayerId,
    source: RuleSourceRef,
    window: PowerWindow,
    nodePath: Vector[String],
    /** Exact windowed operation a contribution is being collected for. */
    operation: Operation,
    /** The procedure the window is walked for, when it is known: the
      * procedure of the parked position a command resumes, or the one a
      * modifier is being selected for. `None` for the command that starts a
      * procedure, which has not recorded one yet.
      */
    procedure: Option[ProcedureRef] = None
)

/** The three ways a power may speak at a hooked node (spec decision 9). A
  * `Transform` rewrites the hooked node's children vector -- never the whole
  * tree, whole-action restructure is out of scope. A `Restriction` checks the
  * whole action tree, since a Vow-of-Peace-style power rejects an action
  * wholesale rather than editing it. An `OptionRestriction` forbids single
  * options of the `Decide` it hooks.
  */
sealed trait Contribution extends Product with Serializable

/** Rewrites the hooked node's children. Covers must-effects (insert ops),
  * cost changes (modify the pay ops), and reordering (roll order).
  *
  * `fn` MUST be a pure function of the state it is handed: the walker derives
  * the tree and re-folds every window on every command (S1), so a fold that
  * differed between two commands would make a recorded park position address
  * a different node on resume. This is the same invariant `Branch.select`
  * already carries.
  */
final case class Transform(
    fn: (PowerCtx, Vector[Operation]) => Vector[Operation]
) extends Contribution

/** Validates the whole action tree root, returning a violation to reject the
  * action wholesale. Covers cannot-effects.
  */
final case class Restriction(
    fn: (PowerCtx, Operation) => Option[OathViolation]
) extends Contribution

/** Forbids one option of the `Decide` the window hooks. Called once per offered
  * option in the window fold, before the query is parked, so a forbidden
  * option is absent from what the projector offers, from what `accepts`
  * validates and from what a simulation answers. Covers cannot-effects that
  * name a choice rather than the whole action.
  */
final case class OptionRestriction(
    fn: (PowerCtx, DecisionOptionRef) => Option[OathViolation]
) extends Contribution

/** One object per power (spec decision 8). No engine code lives in a power --
  * only the windows it hooks and the contributions it offers there.
  *
  * `resolution` (Task 4) reuses the `PowerResolution` vocabulary the legacy
  * `PowerHandler` already carries: `Automatic` powers are offered to every
  * walker command regardless of what the player chose; `PlayerSelected`
  * powers are offered only when their `id` appears in the command's
  * `modifiers` (see `OathRules.walkerPowers`). Defaults to `Automatic` so a
  * power that never expects to be player-chosen (the common case for a
  * "must"/"cannot" rule) declares nothing extra.
  */
trait ContributingPower {
  def id: PowerId
  def source: RuleSourceRef
  def priority: Int = 0
  def contributions: Map[PowerWindow, Vector[Contribution]]
  def applicable(ctx: PowerCtx): Boolean = true
  /** What selecting this power pays, as the operations its action runs first.
    * A command that selects several powers dry-runs all of their payments
    * together (`OathRules.validateModifiers`), so a combination the player
    * cannot pay is refused at selection. Free by default.
    */
  def selectionPayments(ready: ReadyGame, actor: PlayerId)
      : Vector[CoreOperation] = Vector.empty
  /** Decision 10(b)'s named ignore. Receives the whole candidate rather than
    * its id, because a rule of the shape "ignore other modifiers of this
    * action" needs the candidate's classification, and that lives on the
    * windows it hooks (`PowerWindow.associatedMajorAction`) rather than in
    * its `PowerId`.
    */
  def shouldIgnore(other: ContributingPower): Boolean = false
  /** As `shouldIgnore`, decided with the context this power is gathered in,
    * so it can ignore `other` for the node at hand and not for another.
    * The collector calls this one.
    */
  def ignores(ctx: PowerCtx, other: ContributingPower): Boolean =
    shouldIgnore(other)
  def resolution: PowerResolution = PowerResolution.Automatic
}

object ContributingPower {

  /** The single deterministic ordering used everywhere contributions are
    * chained (spec decision 10c).
    */
  def sortKey(power: ContributingPower): (Int, String, String) =
    (power.priority, power.source.stableKey, power.id.value)
}
