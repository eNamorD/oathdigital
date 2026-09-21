package oathdigital.gameplay.phases.rest

import oathdigital.catalog.{CatalogHandlerInventory, ExecutableCatalog}
import oathdigital.gameplay.OathLifecycle
import oathdigital.model.OathViolation._
import oathdigital.model._

/** Begin Rest: the Act-phase gate and the phase change, nothing else.
  *
  * Cleanup waits for Finish Rest so REST powers can be used in between. The
  * exile-only and handler-inventory checks stay here because Rest's cleanup
  * and Supply bands are reviewed only for that game.
  */
object BeginRestProcedure {
  private val ExpectedHandlerInventory =
    "5fc88b0d9622a3f523722c288ea7a78d0ec09b7ce191bdabc7f471139ec85898"

  def validateBegin(catalog: ExecutableCatalog, state: OathState,
      playerId: PlayerId): Either[OathViolation, ReadyGame] =
    OathLifecycle.validateAct(state, playerId).flatMap(ready =>
      validateSupportedState(catalog, ready).map(_ => ready))

  def validateSupportedState(catalog: ExecutableCatalog,
      ready: ReadyGame): Either[OathViolation, Unit] = {
    val game = ready.game
    val actual = CatalogHandlerInventory.structuralFingerprint(catalog)
    val missing = game.current.players.iterator
      .map(player => ForceKind.Exile(player.lineage)).toVector.distinct
      .filterNot(ready.banks.warbandSupply.contains)
    if (game.campaign.lineages.values.exists(_.role != Role.Exile))
      Left(UnsupportedRestState("Rest is limited to the exile-only first game"))
    else if (missing.nonEmpty)
      Left(UnsupportedRestState(
        s"no bounded warband supply for ${missing.mkString(", ")}"))
    else if (actual != ExpectedHandlerInventory)
      Left(UnsupportedRoundEndCatalogInventory(ExpectedHandlerInventory, actual))
    else Right(())
  }

  /** One leaf, so it finishes in the command that starts it; resume never
    * reaches this.
    */
  def build(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    _ <- Either.cond(args.isEmpty, (), InvalidEventOrder(
      "Begin Rest selects nothing"))
    _ <- validateBegin(catalog, OathState.Ready(ready), player)
  } yield Sequence(Vector(EnterPhase(Phase.Rest)))
}
