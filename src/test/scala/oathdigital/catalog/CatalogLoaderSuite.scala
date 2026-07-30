package oathdigital.catalog

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import oathdigital.catalog.CatalogLoadError.{
  DuplicateDefinitionId,
  IncompatibleCatalog,
  InvalidJson,
  MissingField,
  UnresolvedRequiredFields,
  UnsupportedSchemaVersion
}
import oathdigital.model.{CatalogRef, SiteId, Tokens, VisionId}

class CatalogLoaderSuite extends munit.FunSuite {
  private val fixturePath =
    Paths.get(getClass.getResource("/catalog/executable-subset.json").toURI)
  private val fixture =
    Files.readString(fixturePath, StandardCharsets.UTF_8)

  private val allSelections = CatalogSelection(
    setupCards = true,
    supplyBoards = true,
    sites = true,
    visions = true
  )

  test("a verified executable subset decodes to typed definitions") {
    val result = CatalogLoader.load(
      fixture,
      CatalogLoadRequest(
        allSelections,
        expectedCatalog =
          Some(CatalogRef("oath-new-foundations", "fixture-1"))
      )
    )

    val catalog = result.toOption.get
    assertEquals(catalog.setupCards.map(_.step), Vector(1))
    assertEquals(catalog.sites.map(_.id), Vector(SiteId("site:S1")))
    assertEquals(catalog.sites.head.startingResources, Tokens(1, 2))
    assertEquals(catalog.sites.head.forgeRequirements, None)
    assertEquals(catalog.sites.head.powers, Vector("coast"))
    assertEquals(catalog.visions.map(_.id), Vector(VisionId("V1")))
    assertEquals(catalog.supplyBoards.head.rules.maximum, 7)
    assertEquals(
      catalog.supplyBoards.head.excludedReviewItems,
      Vector(
        "non-Supply board icons require crop-level review"
      )
    )
  }

  test("the production catalog loads its setup and Supply projection") {
    val result = CatalogLoader.load(
      Paths.get("docs/catalog/new-foundations-component-catalog.json"),
      CatalogLoadRequest(
        CatalogSelection.SetupFoundation,
        expectedCatalog = Some(
          CatalogRef("oath-new-foundations", "2026.07.27-pre2")
        )
      )
    )

    val catalog = result.toOption.get
    assertEquals(catalog.setupCards.map(_.step), Vector(1, 2, 3, 10, 11))
    assertEquals(catalog.supplyBoards.size, 8)
    assert(catalog.supplyBoards.forall(_.rules.maximum == 7))
    assert(catalog.sites.isEmpty)
  }

  test("production sites load from definition IDs with verified printed data") {
    val result = CatalogLoader.load(
      Paths.get("docs/catalog/new-foundations-component-catalog.json"),
      CatalogLoadRequest(CatalogSelection(sites = true))
    )

    val sites = result.toOption.get.sites
    assertEquals(sites.size, 24)
    val deepWoods = sites.find(_.id == SiteId("site:deep-woods")).get
    assertEquals(deepWoods.startingResources, Tokens(0, 0))
    assertEquals(deepWoods.forgeRequirements, Some(Tokens(1, 2)))
    assertEquals(sites.find(_.id == SiteId("site:broken-peaks")).get.startingResources, Tokens(0, 2))
    assertEquals(sites.find(_.id == SiteId("site:fair-isle")).get.startingResources, Tokens(3, 0))
    assertEquals(sites.find(_.id == SiteId("site:ancient-city")).get.recoverDifficulty, None)
    assertEquals(sites.find(_.id == SiteId("site:ancient-city")).get.powers, Vector("enduring", "river"))
    val headwaters = sites.find(_.id == SiteId("site:headwaters")).get
    assertEquals(headwaters.capacity, 2)
    assertEquals(headwaters.relicSlots, 1)
  }

  test("a Supply projection rejects review items outside its explicit exclusion") {
    val unverifiedSupply = fixture.replace(
      "non-Supply board icons require crop-level review",
      "Supply refresh values require crop-level review"
    )
    val result = CatalogLoader.load(
      unverifiedSupply,
      CatalogLoadRequest(CatalogSelection(supplyBoards = true))
    )

    assert(
      result.left.toOption.get.exists {
        case UnresolvedRequiredFields(_, DefinitionId("player-board:chancellor"), items) =>
          items == Vector("Supply refresh values require crop-level review")
        case _ => false
      }
    )
  }

  test("catalog compatibility is checked before returning definitions") {
    val expected = CatalogRef("oath-new-foundations", "fixture-2")
    val result = CatalogLoader.load(
      fixture,
      CatalogLoadRequest(allSelections, Some(expected))
    )

    assert(
      result.left.toOption.get.exists {
        case IncompatibleCatalog(_, `expected`, actual) =>
          actual == CatalogRef("oath-new-foundations", "fixture-1")
        case _ => false
      }
    )
  }

  test("schema versions and malformed JSON have explicit errors") {
    val unsupported =
      fixture.replace(
        "\"schemaVersion\": \"1.0.0\"",
        "\"schemaVersion\": \"2.0.0\""
      )

    assert(
      CatalogLoader
        .load(unsupported, CatalogLoadRequest(allSelections))
        .left
        .toOption
        .get
        .exists(_.isInstanceOf[UnsupportedSchemaVersion])
    )
    assert(
      CatalogLoader
        .load("{", CatalogLoadRequest(allSelections))
        .left
        .toOption
        .get
        .exists(_.isInstanceOf[InvalidJson])
    )
  }

  test("duplicate identities and absent required fields are rejected") {
    val duplicateId =
      fixture.replace(
        "\"definitionId\": \"vision:V1\"",
        "\"definitionId\": \"site:S1\""
      )
    val missingCapacity =
      fixture.replace("\"capacity\": 2,", "")

    assert(
      CatalogLoader
        .load(duplicateId, CatalogLoadRequest(allSelections))
        .left
        .toOption
        .get
        .exists(_.isInstanceOf[DuplicateDefinitionId])
    )
    assert(
      CatalogLoader
        .load(missingCapacity, CatalogLoadRequest(allSelections))
        .left
        .toOption
        .get
        .exists {
          case MissingField(path) =>
            path.endsWith(".statistics.capacity")
          case _ => false
        }
    )
  }
}
