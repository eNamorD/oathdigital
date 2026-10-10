package oathdigital.testkit

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.walker.{QueuedStart, WalkerPowers,
  WalkerProcedureRegistry, WalkerSimulation}
import oathdigital.gameplay.walker.WalkerSimulation.PreviewedOption
import oathdigital.model._

/** A start dry-run as the phase menu runs it (walker-driven game loop
  * design, slice 5, plan-time ruling 4). It is for suites that asked a
  * procedure's own start check, which the menu's exact check replaced. */
object StartPreview:
  private def entry(actor: PlayerId, procedure: StartableRef,
      args: Vector[DecisionOptionRef]): QueuedProcedure =
    QueuedProcedure(actor, procedure, args, None, QueueKind.Run)

  /** Whether `actor` could start `procedure` with `args` now under
    * `powers`: the check that makes a menu option available. */
  def opens(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      procedure: StartableRef, powers: WalkerPowers,
      args: Vector[DecisionOptionRef] = Vector.empty): Boolean =
    QueuedStart.priced(catalog, ready, entry(actor, procedure, args), powers,
      PhasePowers.empty).isRight

  /** The options of the first decision past `procedure`'s modifier
    * decision, each previewed, as the start reaches it under `powers`.
    * Empty when the start would be refused or asks nothing. */
  def firstDecisionOptions(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, procedure: StartableRef, powers: WalkerPowers,
      args: Vector[DecisionOptionRef] = Vector.empty)
      : Vector[PreviewedOption] =
    val state = QueuedStart.startingFrom(ready, entry(actor, procedure, args))
    WalkerProcedureRegistry.build(procedure, catalog, state, actor, args)
      .flatMap(tree => WalkerSimulation.started(tree, state, powers)
        .flatMap(_.at.fold[Either[OathViolation, Vector[PreviewedOption]]](
          Right(Vector.empty)) { (parked, pending) =>
          WalkerSimulation.previewParked(parked, tree, pending, powers)
        }))
      .getOrElse(Vector.empty)
