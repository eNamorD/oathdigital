package oathdigital.gameplay.powers

import oathdigital.catalog.{CatalogPower, ExecutableCatalog}
import oathdigital.model.{PowerId, PowerResolution}

/** How a modifier or a persistent rule activates, read from the catalog: a
  * persistent power is automatic, a non-persistent one is selected by the
  * player at the start of the major action.
  *
  * Only modifiers and persistent rules use this. The catalog also marks When
  * Played powers (Dazzle) `persistent: false`, but those fire on the play and
  * must stay automatic. Phase powers and battle plans do not activate this
  * way either.
  */
object CatalogResolution {
  def printed(catalog: ExecutableCatalog, id: PowerId): Option[CatalogPower] =
    (catalog.denizens.flatMap(_.powers) ++ catalog.relics.flatMap(_.powers) ++
      catalog.edifices.flatMap(e => e.intact.powers ++ e.ruined.powers) ++
      catalog.legacies.flatMap(_.powers)).find(_.id == id)

  def of(catalog: ExecutableCatalog, id: PowerId): PowerResolution =
    if (printed(catalog, id).exists(_.persistent)) PowerResolution.Automatic
    else PowerResolution.PlayerSelected
}
