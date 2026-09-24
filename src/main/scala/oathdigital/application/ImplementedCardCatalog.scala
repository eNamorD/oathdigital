package oathdigital.application

import oathdigital.catalog.{CatalogPower, ExecutableCatalog, RelicRole}
import oathdigital.model.{DenizenId, EdificeId, PowerId, RelicId, Suit}

/**
 * Which catalog cards have every printed power implemented (2026-09-21
 * Chronicle design, "Randomness"). `implemented` is the per-power answer;
 * production passes `PowerImplementationStatus.implemented(catalog)`, the
 * same source the UI's unimplemented-card marker reads. Being present in
 * `ReviewedPowerCatalog`'s registry is not enough: that registry also holds
 * stubs declared unimplemented, and it has no entry for a power that only
 * the walker or phase catalogs implement.
 */
object ImplementedCardCatalog {
  def denizens(catalog: ExecutableCatalog,
      implemented: PowerId => Boolean): Set[DenizenId] =
    catalog.denizens.collect {
      case definition if fullyImplemented(definition.powers, implemented) =>
        DenizenId(definition.id.value)
    }.toSet

  def ordinaryRelics(catalog: ExecutableCatalog,
      implemented: PowerId => Boolean): Set[RelicId] =
    catalog.relics.collect {
      case definition if definition.role == RelicRole.Ordinary &&
          fullyImplemented(definition.powers, implemented) =>
        RelicId(definition.id.value)
    }.toSet

  /** The lowest-id fully implemented edifice for `suit` (both faces
    * implemented). `None` if the suit has no implemented edifice yet. */
  def homelandEdifice(catalog: ExecutableCatalog, suit: Suit,
      implemented: PowerId => Boolean): Option[EdificeId] =
    catalog.edifices.filter(_.suit == suit)
      .filter(e => fullyImplemented(e.intact.powers, implemented) &&
        fullyImplemented(e.ruined.powers, implemented))
      .sortBy(_.id.value)
      .headOption
      .map(e => EdificeId(e.id.value))

  private def fullyImplemented(powers: Vector[CatalogPower],
      implemented: PowerId => Boolean): Boolean =
    powers.forall(power => implemented(power.id))
}
