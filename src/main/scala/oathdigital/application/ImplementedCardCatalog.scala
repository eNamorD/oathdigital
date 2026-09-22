package oathdigital.application

import oathdigital.catalog.{CatalogPower, ExecutableCatalog, RelicRole}
import oathdigital.gameplay.powerresolver.PowerRegistry
import oathdigital.model.{DenizenId, EdificeId, RelicId, Suit}

/**
 * Which catalog cards have every printed power implemented, read from the
 * reviewed power catalog rather than a hand-kept list (2026-09-21 Chronicle
 * design, "Randomness").
 */
object ImplementedCardCatalog {
  def denizens(catalog: ExecutableCatalog, registry: PowerRegistry): Set[DenizenId] =
    catalog.denizens.collect {
      case definition if fullyImplemented(definition.powers, registry) =>
        DenizenId(definition.id.value)
    }.toSet

  def ordinaryRelics(catalog: ExecutableCatalog, registry: PowerRegistry): Set[RelicId] =
    catalog.relics.collect {
      case definition if definition.role == RelicRole.Ordinary &&
          fullyImplemented(definition.powers, registry) =>
        RelicId(definition.id.value)
    }.toSet

  /** The lowest-id fully implemented edifice for `suit` (both faces
    * implemented). `None` if the suit has no implemented edifice yet. */
  def homelandEdifice(catalog: ExecutableCatalog, suit: Suit,
      registry: PowerRegistry): Option[EdificeId] =
    catalog.edifices.filter(_.suit == suit)
      .filter(e => fullyImplemented(e.intact.powers, registry) &&
        fullyImplemented(e.ruined.powers, registry))
      .sortBy(_.id.value)
      .headOption
      .map(e => EdificeId(e.id.value))

  private def fullyImplemented(powers: Vector[CatalogPower],
      registry: PowerRegistry): Boolean =
    powers.forall(power => registry.lookup(power.id).isDefined)
}
