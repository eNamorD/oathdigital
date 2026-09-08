package oathdigital.gameplay.powerresolver

import oathdigital.gameplay.operations.Operation
import oathdigital.gameplay.{OathViolation, ReadyGame, RuleSourceRef}
import oathdigital.model.{PlayerId, PowerId}

/** Everything a contribution may read at the node it hooks. Carries no
  * mutable state and no catalog -- a power looks up whatever else it needs
  * (catalog data, other game state) through `state`.
  */
final case class PowerCtx(
    state: ReadyGame,
    actor: PlayerId,
    source: RuleSourceRef,
    window: PowerWindow,
    nodePath: Vector[String]
)

/** The two ways a power may speak at a hooked node (spec decision 9). A
  * `Transform` rewrites the hooked node's children vector -- never the whole
  * tree, whole-action restructure is out of scope. A `Restriction` checks the
  * whole action tree, since a Vow-of-Peace-style power rejects an action
  * wholesale rather than editing it.
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

/** One object per power (spec decision 8). No engine code lives in a power --
  * only the windows it hooks and the contributions it offers there.
  */
trait ContributingPower {
  def id: PowerId
  def source: RuleSourceRef
  def priority: Int = 0
  def contributions: Map[PowerWindow, Vector[Contribution]]
  def applicable(ctx: PowerCtx): Boolean = true
  def shouldIgnore(other: PowerId): Boolean = false
}

object ContributingPower {

  /** The single deterministic ordering used everywhere contributions are
    * chained (spec decision 10c).
    */
  def sortKey(power: ContributingPower): (Int, String, String) =
    (power.priority, power.source.stableKey, power.id.value)
}
