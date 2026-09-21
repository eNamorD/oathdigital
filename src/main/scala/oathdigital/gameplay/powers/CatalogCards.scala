package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** Which card prints a power, read from the catalog. `None` for a catalog
  * without the card (a test stub), so a power whose card is absent is omitted
  * rather than failing construction.
  */
object CatalogCards {
  def denizen(catalog: ExecutableCatalog, power: PowerId): Option[DenizenId] =
    catalog.denizens.find(_.powers.exists(_.id == power))
      .map(card => DenizenId(card.id.value))

  def relic(catalog: ExecutableCatalog, power: PowerId): Option[RelicId] =
    catalog.relics.find(_.powers.exists(_.id == power))
      .map(card => RelicId(card.id.value))

  def edifice(catalog: ExecutableCatalog, power: PowerId): Option[EdificeId] =
    catalog.edifices.find(card => card.intact.powers.exists(_.id == power) ||
      card.ruined.powers.exists(_.id == power))
      .map(card => EdificeId(card.id.value))
}
