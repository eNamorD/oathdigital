package oathdigital.frontend

import oathdigital.catalog._
import oathdigital.model._

object DemoCatalog {
  val ref: CatalogRef =
    CatalogRef("oath-new-foundations", "ui-spike")

  private val names = Vector(
    "The Tribunal",
    "Ancient City",
    "Shrouded Wood",
    "The Hidden Place",
    "Drowned Coast",
    "Great Slum",
    "Deep Woods",
    "Wastes"
  )

  val sites: Vector[SiteDefinition] =
    names.zipWithIndex.map { case (name, index) =>
      SiteDefinition(
        SiteId(s"demo-site-$index"),
        name,
        defense = 1,
        capacity = 3,
        relicSlots = 1,
        recoverDifficulty = None,
        startingResources = Tokens.empty,
        forgeRequirements = None,
        handlers = Vector.empty
      )
    }

  val catalog: ExecutableCatalog =
    ExecutableCatalog(
      schemaVersion = "ui-spike",
      ref = ref,
      denizens = Vector.empty,
      relics = Vector.empty,
      edifices = Vector.empty,
      legacies = Vector.empty,
      sites = sites
    )
}
