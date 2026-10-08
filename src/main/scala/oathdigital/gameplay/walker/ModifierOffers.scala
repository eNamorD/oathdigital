package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.gameplay.powerresolver.{ContributingPower, PowerCtx}
import oathdigital.model.{OathViolation, PlayerId, PowerResolution,
  ProcedureRef, ReadyGame, Sequence}

/** The modifiers a player may select for a procedure, read from one walker
  * power catalog. `OathRules` validates a start's selection against
  * `offerable`, and a start control asks `selections` whether some selection
  * the player could make lets the procedure start, so the two cannot drift.
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

  /** Dry-runs the selection payments of `powers` together against `ready`. */
  def paying(catalog: WalkerPowers, ready: ReadyGame, actor: PlayerId,
      powers: Vector[ContributingPower]): Either[OathViolation, Unit] =
    val payments = powers.flatMap(_.selectionPayments(ready, actor))
    if payments.isEmpty then Right(())
    else OperationPipeline.run(ready, payments, OperationPolicy.Permissive,
      WalkerPowers.selected(catalog, powers.map(_.id))
        .operationRestrictions)(Right(_)).map(_ => ())

  /** The selections a start control tries, as the powers each start would
    * walk with: none, each offerable modifier alone, and all of them when
    * their payments can be made together. Each is a selection the player
    * could make, so a control these open is a start the command accepts. A
    * start that needs two modifiers out of three, but not the third, is not
    * found.
    */
  def selections(catalog: WalkerPowers, ready: ReadyGame, actor: PlayerId,
      procedure: ProcedureRef): Vector[WalkerPowers] =
    val offered = offerable(catalog, ready, actor, procedure)
      .getOrElse(Vector.empty)
    val together = Option.when(offered.size > 1 &&
      paying(catalog, ready, actor, offered).isRight)(offered)
    (Vector(Vector.empty) ++ offered.map(Vector(_)) ++ together)
      .map(chosen => WalkerPowers.selected(catalog, chosen.map(_.id)))
