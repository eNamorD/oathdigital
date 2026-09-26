package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
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
object CatalogResolution:
  def of(catalog: ExecutableCatalog, id: PowerId): PowerResolution =
    if catalog.printedPower(id).exists(_.persistent) then PowerResolution.Automatic
    else PowerResolution.PlayerSelected
