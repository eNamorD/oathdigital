package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{MajorActionKind, OathViolation, PowerRuntime, ReadyGame}
import oathdigital.gameplay.powerresolver.Power
import oathdigital.model.PlayerId

object SearchPowers {
  val powers: Vector[Power] = Vector.empty

  /** Search-derived facedown-adviser play shares Search modifier discovery.
    * No current reviewed Search modifier is executable, but this exact-window
    * query preserves authoritative audit and future option revalidation.
    */
  def validateModifierSelection(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerId): Either[OathViolation, Unit] =
    PowerRuntime.options(catalog, ready, player, MajorActionKind.Search).map(_ => ())
}
