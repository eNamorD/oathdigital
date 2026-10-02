package oathdigital.gameplay.walker

import oathdigital.model.{OathEvent, PendingTree, ReadyGame}

/** Outcome of one walker `advance`/`roll`/`resolve` command.
  *
  * A walk either runs until it must stop for a human/app decision
  * ([[WalkerOutcome.Parked]]) or consumes the whole action tree ([[WalkerOutcome.Finished]]).
  */
sealed trait WalkerOutcome extends Product with Serializable
object WalkerOutcome:
  /** The walk parked at a `Decide` (a `Roll` park is *resumed* this slice
    * through [[ProcedureWalker.roll]], never through a further `advance`).
    * `tree` is the exact position to resume from on the next command;
    * `events` holds any steps executed before the park in this command. The
    * application appends a [[WalkerParked]] fact after these events so replay
    * can restore the pointer without running the walker.
    */
  final case class Parked(tree: PendingTree, events: Vector[OathEvent])
      extends WalkerOutcome

  /** The whole action tree was consumed. The resulting state no longer
    * carries a pending tree and its dice pools are cleared (brief behavior
    * 6); `events` holds every delta executed by this command. Roll outcomes
    * written during the walk are retained on the finished state for the next
    * resolution step to consume; the application-level [[WalkerCompleted]]
    * fact clears them at the completed action boundary.
    */
  final case class Finished(treeless: ReadyGame, events: Vector[OathEvent])
      extends WalkerOutcome
