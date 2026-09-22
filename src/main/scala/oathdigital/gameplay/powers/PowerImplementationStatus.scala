package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.PowerId

/** Whether a power genuinely runs for `catalog`, across every catalog that
  * can make one true: `ReviewedPowerCatalog` carries the flag explicitly per
  * handler, while `WalkerPowerCatalog`/`PhasePowerCatalog` carry no such flag
  * -- for those, being wired in for this catalog at all is what implemented
  * means. The single source both `ReviewedPowerCatalog`'s registry (so the
  * legacy "ignored rule" diagnostic stops firing for a power a walker or
  * phase catalog already covers) and the presentation layer (the UI's
  * unimplemented-card marker) read, so the two cannot drift the way a
  * `ReviewedPowerCatalog` handler left at `implemented = false` after its
  * power ported elsewhere once did.
  */
object PowerImplementationStatus {
  def implemented(catalog: ExecutableCatalog): PowerId => Boolean = {
    val covered = WalkerPowerCatalog.default(catalog).powers.map(_.id).toSet ++
      PhasePowerCatalog.default(catalog).powers.map(_.id).toSet
    val reviewedHandlers = ReviewedPowerCatalog.powers
      .map(power => power.id -> power.handlers).toMap
    id => covered.contains(id) ||
      reviewedHandlers.get(id).exists(_.forall(_.implemented))
  }
}
