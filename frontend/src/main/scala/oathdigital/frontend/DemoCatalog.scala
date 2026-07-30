package oathdigital.frontend

import oathdigital.catalog._
import oathdigital.model._

object DemoCatalog {
  val ref: CatalogRef =
    CatalogRef("oath-new-foundations", "ui-spike")

  private def metadata(index: Int, name: String): ComponentMetadata =
    ComponentMetadata(
      DefinitionId(s"demo-site-$index"),
      "site",
      name,
      None,
      Vector.empty,
      TranscriptionMetadata("ui-spike", "demo", "not-catalog-data"),
      Vector.empty
    )

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
        metadata(index, name),
        SiteId(s"demo-site-$index"),
        defense = 1,
        capacity = 3,
        relicSlots = 1,
        recoverDifficulty = None,
        startingResources = Tokens.empty,
        forgeRequirements = None,
        powers = Vector.empty
      )
    }

  val catalog: ExecutableCatalog =
    ExecutableCatalog(
      schemaVersion = "ui-spike",
      ref = ref,
      ruleset = RulesetMetadata(
        ref.ruleset,
        ref.version,
        "bounded-setup-domain"
      ),
      setupCards = Vector.empty,
      supplyBoards = Vector.empty,
      sites = sites,
      visions = Vector.empty
    )
}
