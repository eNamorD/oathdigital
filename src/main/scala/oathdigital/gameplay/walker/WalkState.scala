package oathdigital.gameplay.walker

import oathdigital.model.{Answered, OathEvent, Operation, PlayerId, ReadyGame}

/** Command-local walk state: the state threaded through executed deltas,
  * the events recorded so far, the acting player, and every decision
  * already answered during this action.
  */
private[walker] final case class WalkCtx(
    state: ReadyGame,
    events: Vector[OathEvent],
    activePlayer: PlayerId,
    answered: Vector[Answered],
    powers: WalkerPowers,
    dice: WalkerDice,
    /** The procedure of the parked position being resumed. */
    procedure: Option[oathdigital.model.ProcedureRef],
    /** The whole action tree, which the search reads. */
    root: Operation,
    /** The states before and after the leaf this command ran last, which
      * a note reads. A leaf that changed nothing gives the same state
      * twice. */
    previous: Option[(ReadyGame, ReadyGame)] = None,
    /** Set on a search's walk (global operation restrictions design, "Lazy
      * pruning"): it stops at hidden information and at a decision some
      * answer leads on from, and its events are thrown away. */
    searching: Boolean = false,
    /** The decisions this search stands at, with the state at each. */
    visited: Set[(Vector[String], ReadyGame)] = Set.empty
)

private[walker] sealed trait Step extends Product with Serializable
/** The walked region completed; `ctx` carries the result state/events. */
private[walker] final case class Done(ctx: WalkCtx) extends Step
/** A park bubbled up from a Decide/Roll leaf at `position`. */
private[walker] final case class Park(position: Vector[String], ctx: WalkCtx)
    extends Step
/** A search stopped here and counts the path as legal: at hidden
  * information, at a decision some answer leads on from, or back at a
  * decision it already stands at in the same state. Only a search's walk
  * returns it. */
private[walker] final case class Stopped(ctx: WalkCtx) extends Step
