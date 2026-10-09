package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Restriction,
  Waiver}
import oathdigital.model.{PlayerId, PowerId, PowerSourceRef, ReadyGame}

/** The waivable powers (consent design, "Powers declare waivability"): every
  * power with a contribution that declares a `Waiver`. The grid, the
  * stale-preference check and the log all look a power up here. */
final class ConsentPowers(powers: Vector[ContributingPower]):
  private val waivers: Map[PowerId, Waiver] = (for
    power <- powers
    waiver <- power.contributions.values.flatten.collectFirst {
      case Restriction(_, _, Some(waiver)) => waiver }
  yield power.id -> waiver).toMap

  def waiver(power: PowerId): Option[Waiver] = waivers.get(power)

  def granter(ready: ReadyGame, power: PowerId): Option[PlayerId] =
    waiver(power).flatMap { _.granter(ready) }

  def source(power: PowerId): Option[PowerSourceRef] =
    waiver(power).map(_.source)

  /** The waivable powers `player` rules now, by id (ruling 16). */
  def ruledBy(ready: ReadyGame, player: PlayerId): Vector[PowerId] =
    waivers.keys.toVector.sortBy(_.value)
      .filter(granter(ready, _).contains(player))

object ConsentPowers:
  def default(catalog: ExecutableCatalog): ConsentPowers =
    new ConsentPowers(WalkerPowerCatalog.default(catalog).powers)
