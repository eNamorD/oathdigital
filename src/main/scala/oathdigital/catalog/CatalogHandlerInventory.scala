package oathdigital.catalog

/** Factual catalog power inventory. It records declared IDs; it does not
  * decide whether or when a power is active.
  */
object CatalogHandlerInventory:
  def handlerIds(catalog: ExecutableCatalog): Vector[String] =
    (catalog.denizens.flatMap(_.powers.map(_.id.value)) ++
      catalog.relics.flatMap(_.powers.map(_.id.value)) ++
      catalog.legacies.flatMap(_.powers.map(_.id.value)) ++
      catalog.sites.flatMap(_.handlers) ++
      catalog.edifices.flatMap(e => e.intact.powers.map(_.id.value) ++
        e.ruined.powers.map(_.id.value)))
      .distinct.sorted
