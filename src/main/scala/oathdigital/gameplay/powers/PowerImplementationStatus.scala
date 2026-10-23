package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.operations.OperationRestrictions
import oathdigital.model.PowerId

/** Whether a power genuinely runs for `catalog`: it is wired into
  * `WalkerPowerCatalog` or `PhasePowerCatalog` for this catalog, or an
  * operation restriction in `OperationRestrictions.printedRules` enforces
  * it. No catalog carries an "implemented" flag; being wired in is what
  * implemented means. The presentation layer's unimplemented-card marker,
  * `ImplementedCardCatalog` and the first-game generator all read this one
  * predicate.
  */
object PowerImplementationStatus:
  def implemented(catalog: ExecutableCatalog): PowerId => Boolean =
    val covered = WalkerPowerCatalog.default(catalog).powers.map(_.id).toSet ++
      PhasePowerCatalog.default(catalog).powers.map(_.id).toSet ++
      OperationRestrictions.printedRules.map(_.id).toSet
    covered.contains
