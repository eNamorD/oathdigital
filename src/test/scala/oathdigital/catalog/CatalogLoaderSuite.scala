package oathdigital.catalog

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import oathdigital.catalog.CatalogLoadError.{
  DuplicateDefinitionId,
  IncompatibleCatalog,
  InvalidJson,
  MissingField,
  UnsupportedSchemaVersion
}
import oathdigital.model.{CatalogRef, SiteId, Tokens}

class CatalogLoaderSuite extends munit.FunSuite {
  private val fixturePath =
    Paths.get(getClass.getResource("/catalog/executable-subset.json").toURI)
  private val fixture =
    Files.readString(fixturePath, StandardCharsets.UTF_8)

  test("all five runtime component families decode atomically") {
    val result = CatalogLoader.load(
      fixture,
      CatalogLoadRequest(
        expectedCatalog =
          Some(CatalogRef("oath-new-foundations", "fixture-1"))
      )
    )

    val catalog = result.toOption.get
    assertEquals(catalog.denizens.head.suit, Suit.Arcane)
    assertEquals(
      catalog.denizens.head.handlers,
      Vector("denizen.fixture-denizen")
    )
    assertEquals(catalog.relics.head.defense, 2)
    assertEquals(catalog.edifices.head.ruined.name, "Ruined Fixture")
    assertEquals(catalog.legacies.head.name, "Fixture Legacy")
    assertEquals(catalog.sites.map(_.id), Vector(SiteId("site:fixture-site")))
    assertEquals(catalog.sites.head.startingResources, Tokens(1, 2))
    assertEquals(catalog.sites.head.forgeRequirements, None)
  }

  test("the production catalog contains only the final runtime corpus") {
    val catalog = CatalogLoader
      .load(
        Paths.get("docs/catalog/new-foundations-component-catalog.json"),
        CatalogLoadRequest(
          expectedCatalog = Some(
            CatalogRef("oath-new-foundations", "2026.07.27-pre2")
          )
        )
      )
      .toOption
      .get

    assertEquals(catalog.denizens.size, 255)
    assertEquals(catalog.relics.size, 48)
    assertEquals(catalog.edifices.size, 30)
    assertEquals(catalog.legacies.size, 36)
    assertEquals(catalog.sites.size, 24)
    assertEquals(
      catalog.relics.count(_.role == RelicRole.GrandScepter),
      1
    )
    assertEquals(catalog.setupCards, Vector.empty)
    assertEquals(catalog.supplyBoards, Vector.empty)
    assertEquals(catalog.visions, Vector.empty)
  }

  test("production sites retain verified printed gameplay data") {
    val sites = CatalogLoader
      .load(Paths.get("docs/catalog/new-foundations-component-catalog.json"))
      .toOption
      .get
      .sites

    val deepWoods = sites.find(_.id == SiteId("site:deep-woods")).get
    assertEquals(deepWoods.startingResources, Tokens(0, 0))
    assertEquals(deepWoods.forgeRequirements, Some(Tokens(1, 2)))
    assertEquals(
      sites
        .find(_.id == SiteId("site:ancient-city"))
        .get
        .recoverDifficulty,
      None
    )
    assertEquals(
      sites.find(_.id == SiteId("site:headwaters")).get.relicSlots,
      1
    )
  }

  test("legacy selection flags do not produce partial catalogs") {
    val catalog = CatalogLoader
      .load(
        fixture,
        CatalogLoadRequest(CatalogSelection(sites = true))
      )
      .toOption
      .get

    assertEquals(catalog.denizens.size, 1)
    assertEquals(catalog.relics.size, 1)
    assertEquals(catalog.sites.size, 1)
  }

  test("catalog compatibility is checked before returning definitions") {
    val expected = CatalogRef("oath-new-foundations", "fixture-2")
    val result = CatalogLoader.load(
      fixture,
      CatalogLoadRequest(expectedCatalog = Some(expected))
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
        .load(unsupported)
        .left
        .toOption
        .get
        .exists(_.isInstanceOf[UnsupportedSchemaVersion])
    )
    assert(
      CatalogLoader
        .load("{")
        .left
        .toOption
        .get
        .exists(_.isInstanceOf[InvalidJson])
    )
  }

  test("duplicate identities and absent required fields are rejected") {
    val duplicateDenizen =
      """{
        |      "id": "denizen:fixture-denizen",
        |      "name": "Duplicate Denizen",
        |      "suit": "beast",
        |      "handlers": ["denizen.duplicate-denizen"],
        |      "rulesText": ""
        |    },""".stripMargin
    val duplicateId =
      fixture.replace(
        "\"denizens\": [",
        s"\"denizens\": [$duplicateDenizen"
      )
    val missingCapacity = fixture.replace("\"capacity\": 2,", "")

    assert(
      CatalogLoader
        .load(duplicateId)
        .left
        .toOption
        .get
        .exists(_.isInstanceOf[DuplicateDefinitionId])
    )
    assert(
      CatalogLoader
        .load(missingCapacity)
        .left
        .toOption
        .get
        .exists {
          case MissingField(path) => path.endsWith(".capacity")
          case _ => false
        }
    )
  }
}
