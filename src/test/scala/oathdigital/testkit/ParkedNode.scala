package oathdigital.testkit

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers,
  WalkerProcedureRegistry}
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** The node a Ready game's walker is parked on, rebuilt from durable state
  * the way the projector rebuilds it: the procedure, the start selection and
  * the selected modifiers all come from the state, never from a test's own
  * memory of what it started.
  *
  * It is shared by `ParkedDecisionAssertions`, which asserts a park, and the
  * [[SituationDriver]], which answers one, so the rebuild has one owner.
  */
enum ParkedNode:
  /** Parked on a `Decide`: the whole `Decide`, because the driver answers
    * its `query`. */
  case Decision(procedure: ProcedureRef, decide: Decide, awaiting: PlayerId)
  /** Parked on a `Roll`, whose decision id is the one the procedure
    * declares -- the same resolution the projector makes. */
  case Roll(procedure: ProcedureRef, pool: PoolKey, count: Int,
      rollDecisionId: String, awaiting: PlayerId)

  def procedure: ProcedureRef
  def awaiting: PlayerId

  def decisionId: String = this match
    case Decision(_, decide, _) => decide.decisionId
    case Roll(_, _, _, id, _) => id

object ParkedNode:
  /** `None` exactly when nothing is parked: the state is not `Ready`, or it
    * is and `walkerPending` is empty -- the same fact `WalkerCompleted`
    * clears alongside `walkerProcedure` and `OathRules.unlessWalkerPending`
    * reads to mean "no walker is pending". Anything else that keeps this
    * from explaining the park -- a rebuild failure, a position resolving to
    * neither a `Decide` nor a `Roll`, a Roll park whose procedure declares
    * no roll decision id, or no awaited player -- is `walkerPending` being
    * SET while the park cannot be explained, and is a `Left` with the
    * reason: silently returning `None` there would let a broken rebuild
    * masquerade as "not parked".
    */
  def of(state: OathState, catalog: ExecutableCatalog,
      walkerPowers: WalkerPowers = WalkerPowers.empty,
      phasePowers: PhasePowers = PhasePowers.empty)
      : Either[String, Option[ParkedNode]] =
    state match
      case Ready(ready) =>
        val current = ready.game.current
        current.walkerPending match
          case None => Right(None)
          case Some(pending) =>
            val at = pending.at.mkString(".")
            for
              procedure <- current.walkerProcedure.toRight(
                s"a walker is pending at '$at' but no walkerProcedure is " +
                  "recorded")
              tree <- WalkerProcedureRegistry.rebuild(procedure, catalog,
                  ready, current.turn.activePlayer, current.walkerStartArgs,
                  phasePowers).left.map(violation =>
                s"could not rebuild $procedure's tree to read the park at " +
                  s"'$at': $violation")
              powers = WalkerPowers.selected(walkerPowers,
                current.walkerModifiers)
              awaiting = ProcedureWalker.awaitedPlayer(ready, tree, pending,
                powers)
              node <- ProcedureWalker.parkedDecide(ready, tree, pending,
                  powers) match
                case Some(decide) =>
                  awaiting.toRight(noneAwaited(at, procedure,
                      decide.decisionId))
                    .map(Decision(procedure, decide, _))
                case None => ProcedureWalker.parkedRoll(ready, tree, pending,
                    powers) match
                  case Some((pool, count)) =>
                    for
                      id <- WalkerProcedureRegistry.rollDecisionId(procedure)
                        .left.map(violation =>
                          s"a Roll parked at '$at' of $procedure, which " +
                            s"declares no roll decision id: $violation")
                      player <- awaiting.toRight(noneAwaited(at, procedure,
                        id))
                    yield Roll(procedure, pool, count, id, player)
                  case None => Left(
                    s"a walker is pending at '$at' of $procedure but it " +
                      "resolves to neither a Decide nor a Roll")
            yield Some(node)
      case _ => Right(None)

  private def noneAwaited(at: String, procedure: ProcedureRef,
      decision: String): String =
    s"a walker is pending at '$at' of $procedure on '$decision' but no " +
      "player is awaited"
