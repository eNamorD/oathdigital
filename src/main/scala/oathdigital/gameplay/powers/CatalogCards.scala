package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** Which relic or edifice prints a power, read from the catalog. `None` for a
  * catalog without the card (a test stub), so a power whose card is absent is
  * omitted rather than failing construction. A denizen power names its card
  * and needs no lookup.
  */
object CatalogCards:
  def relic(catalog: ExecutableCatalog, power: PowerId): Option[RelicId] =
    catalog.relicWithPower(power).map(card => RelicId(card.id.value))

  def edifice(catalog: ExecutableCatalog, power: PowerId): Option[EdificeId] =
    catalog.edificeWithPower(power).map(card => EdificeId(card.id.value))
