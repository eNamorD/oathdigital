package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.gameplay.powerresolver.{ContributingPower, PowerCtx}
import oathdigital.model.{OathViolation, PlayerId, PowerResolution,
  ProcedureRef, ReadyGame, Sequence}

/** The modifiers a player may select for a procedure, read from one walker
  * power catalog. The modifier decision asks `offerable` which to list, and
  * judges each option exactly, inside the walk.
  */
object ModifierOffers:

  /** The `PlayerSelected` powers in `catalog` that are applicable at
    * `procedure`'s modifier-selection window and whose own selection payment
    * `actor` can make. An entry declaring no window offers nothing; a
    * procedure absent from `registrations` is a `Left`.
    */
  private[gameplay] def offerable(catalog: WalkerPowers, ready: ReadyGame,
      actor: PlayerId, procedure: ProcedureRef,
      registrations: Map[ProcedureRef, WalkerProcedureRegistry.Entry])
      : Either[OathViolation, Vector[ContributingPower]] =
    WalkerProcedureRegistry.modifierWindow(procedure, registrations).map:
      case None => Vector.empty
      case Some(window) => catalog.powers.filter(power =>
        power.resolution == PowerResolution.PlayerSelected &&
        power.applicable(PowerCtx(ready, actor, power.source, window,
          Vector.empty, Sequence(Vector.empty, Some(window)),
          Some(procedure))) &&
        paying(catalog, ready, actor, Vector(power)).isRight)

  /** [[offerable]] against the production registry. */
  def offerable(catalog: WalkerPowers, ready: ReadyGame, actor: PlayerId,
      procedure: ProcedureRef): Either[OathViolation, Vector[ContributingPower]] =
    offerable(catalog, ready, actor, procedure, WalkerProcedureRegistry.entries)

  /** Dry-runs the selection payments of `powers` together against `ready`:
    * whether the player could pick them all. */
  private def paying(catalog: WalkerPowers, ready: ReadyGame, actor: PlayerId,
      powers: Vector[ContributingPower]): Either[OathViolation, Unit] =
    val payments = powers.flatMap(_.selectionPayments(ready, actor))
    if payments.isEmpty then Right(())
    else OperationPipeline.run(ready, payments, OperationPolicy.Permissive,
      WalkerPowers.selected(catalog, powers.map(_.id))
        .operationRestrictions)(Right(_)).map(_ => ())
