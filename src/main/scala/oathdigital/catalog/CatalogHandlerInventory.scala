package oathdigital.catalog

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Factual catalog power inventory. It records declared IDs and source faces;
  * it does not decide whether or when a power is active.
  */
object CatalogHandlerInventory {
  def handlerIds(catalog: ExecutableCatalog): Vector[String] =
    (catalog.denizens.flatMap(_.powers.map(_.id)) ++
      catalog.relics.flatMap(_.powers.map(_.id)) ++
      catalog.legacies.flatMap(_.powers.map(_.id)) ++
      catalog.sites.flatMap(_.handlers) ++
      catalog.edifices.flatMap(e => e.intact.powers.map(_.id) ++
        e.ruined.powers.map(_.id)))
      .distinct.sorted

  def entries(catalog: ExecutableCatalog): Vector[String] =
    (catalog.denizens.sortBy(_.id.value).map(d =>
      s"denizen|${d.id.value}|${d.powers.map(_.id).sorted.mkString(",")}") ++
      catalog.relics.sortBy(_.id.value).map(r =>
        s"relic|${r.id.value}|${r.powers.map(_.id).sorted.mkString(",")}") ++
      catalog.edifices.sortBy(_.id.value).flatMap(e => Vector(
        s"edifice-intact|${e.id.value}|${e.intact.powers.map(_.id).sorted.mkString(",")}",
        s"edifice-ruined|${e.id.value}|${e.ruined.powers.map(_.id).sorted.mkString(",")}")) ++
      catalog.legacies.sortBy(_.id.value).map(l =>
        s"legacy|${l.id.value}|${l.powers.map(_.id).sorted.mkString(",")}") ++
      catalog.sites.sortBy(_.id.value).map(s =>
        s"site|${s.id.value}|${s.handlers.sorted.mkString(",")}"))

  def fingerprint(catalog: ExecutableCatalog): String =
    sha256(handlerIds(catalog))

  def structuralFingerprint(catalog: ExecutableCatalog): String =
    sha256(entries(catalog))

  private def sha256(values: Vector[String]): String =
    MessageDigest.getInstance("SHA-256")
      .digest(values.mkString("\n").getBytes(StandardCharsets.UTF_8))
      .map("%02x".format(_)).mkString
}
