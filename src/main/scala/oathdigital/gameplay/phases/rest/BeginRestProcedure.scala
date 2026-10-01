package oathdigital.gameplay.phases.rest

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.OathLifecycle
import oathdigital.model.OathViolation._
import oathdigital.model._

/** Begin Rest: the Act-phase gate and the phase change, nothing else.
  *
  * Cleanup waits for Finish Rest so REST powers can be used in between. The
  * exile-only check stays here because Rest's cleanup and Supply bands
  * are reviewed only for that game.
  */
object BeginRestProcedure:
  def validateBegin(catalog: ExecutableCatalog, state: OathState,
      playerId: PlayerId): Either[OathViolation, ReadyGame] =
    OathLifecycle.validateAct(state, playerId).flatMap(ready =>
      validateSupportedState(catalog, ready).map(_ => ready))

  def validateSupportedState(catalog: ExecutableCatalog,
      ready: ReadyGame): Either[OathViolation, Unit] =
    val game = ready.game
    val missing = game.current.players.iterator
      .map(player => ForceKind.Exile(player.lineage)).toVector.distinct
      .filterNot(ready.banks.warbandSupply.contains)
    if game.campaign.lineages.values.exists(_.role != Role.Exile) then
      Left(UnsupportedRestState("Rest is limited to the exile-only first game"))
    else if missing.nonEmpty then
      Left(UnsupportedRestState(
        s"no bounded warband supply for ${missing.mkString(", ")}"))
    else Right(())

  /** One leaf, so it finishes in the command that starts it; resume never
    * reaches this.
    */
  def build(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for
    _ <- Either.cond(args.isEmpty, (), InvalidEventOrder(
      "Begin Rest selects nothing"))
    _ <- validateBegin(catalog, OathState.Ready(ready), player)
  yield Sequence(Vector(EnterPhase(Phase.Rest)))
