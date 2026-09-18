package oathdigital.gameplay.phases.wake

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.operations.{EnterPhase, Operation, Sequence}
import oathdigital.gameplay.OathLifecycle
import oathdigital.model._

/** Declared End Wake procedure tree for the walker (batch 1, Task 7).
  *
  * {{{
  * Sequence(EnterPhase(Act))                          // no window
  * }}}
  *
  * **This is not an action, and the walker does not need it to be.** Ending
  * Wake costs nothing, selects nothing and targets nothing; it moves the turn
  * from one phase to the next. It is declared here because a phase change is
  * a state write, and the walker is where a procedure's state writes are
  * declared, journalled and replayed -- not because ending Wake was
  * reclassified as something a player spends a turn on. The plan asked for it
  * to stay a phase transition and it has: what changed is that it is no
  * longer a second hand-written `handle`/`evolve` pair beside the walker's.
  *
  * **What follows Wake is stated here, once.** [[EnterPhase]] takes any
  * phase and the walker asks nothing about phases, so the turn's order lives
  * in the procedure that performs the change. `OathLifecycle.validateReady`
  * is the same gate the deleted `Wake` object ran -- active player, live
  * game, Wake phase -- so a second End Wake in the same turn still fails as
  * `WrongPhase(Wake, Act)`.
  *
  * **No window.** A `Sequence` with no window gathers no contributions, which
  * is the honest statement for a tree nothing can modify. `PowerWindow
  * .WakeBoundary` exists and would have compiled, but it names the Wake
  * timing's fallback diagnostics -- which this procedure already runs through
  * its registry entry's `fallbackKind` -- and declaring it here would invite
  * powers to transform a phase change.
  */
object EndWakeProcedure {

  /** Fresh start and resume build the same tree. End Wake declares one leaf,
    * so it finishes inside the command that starts it and a resume never
    * reaches this.
    */
  def build(catalog: ExecutableCatalog, state: ReadyGame, activePlayer: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    _ <- OathLifecycle.validateReady(OathState.Ready(state), activePlayer)
    _ <- Either.cond(args.isEmpty, (), OathViolation.InvalidEventOrder(
      "ending Wake selects nothing, got " +
        args.map(ref => s"${ref.kind}/${ref.wireId}").mkString(", ")))
  } yield Sequence(Vector(EnterPhase(Phase.Act)))
}
