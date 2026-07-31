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
    assertEquals(catalog.relics.head.value, 42)
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
    assertEquals(catalog.denizens.map(_.id.value).toSet.size, 255)
    assert(catalog.denizens.forall(_.id.value.forall(_.isDigit)))
    assertEquals(
      catalog.relics.filter(_.role == RelicRole.Ordinary).map(_.id.value).toSet,
      (1 to 47).map(number => f"R$number%02d").toSet
    )
    assertEquals(
      catalog.edifices.map(_.id.value).toSet,
      (1 to 30).map(number => f"E$number%02d").toSet
    )
    assertEquals(
      catalog.legacies.map(_.id.value).toSet,
      (1 to 36).map(number => f"L$number%02d").toSet
    )
    assertEquals(catalog.setupCards, Vector.empty)
    assertEquals(catalog.supplyBoards, Vector.empty)
    assertEquals(catalog.visions, Vector.empty)
  }

  test("printed relic values and reviewed symbol transcription are loaded") {
    val catalog = CatalogLoader
      .load(Paths.get("docs/catalog/new-foundations-component-catalog.json"))
      .toOption
      .get

    val stickyFire = catalog.relics.find(_.id.value == "R01").get
    assertEquals(stickyFire.value, 3)
    assertEquals(stickyFire.defense, 3)
    assert(stickyFire.rulesText.contains("[favor]"))

    val alchemist = catalog.denizens.find(_.id.value == "9").get
    assert(alchemist.rulesText.startsWith("[secret] [secret-burnt]"))
    assert(alchemist.rulesText.contains("**ACTION:**"))

    assertEquals(
      catalog.legacies.find(_.id.value == "L17").map(_.name),
      Some("Rival")
    )
    assertEquals(
      catalog.legacies.find(_.id.value == "L18").map(_.name),
      Some("The Standard Bearer")
    )
    assert(
      catalog.edifices
        .find(_.id.value == "E01")
        .get
        .ruined
        .rulesText
        .contains("[favor-burnt]")
    )

    val grandScepter =
      catalog.relics.find(_.role == RelicRole.GrandScepter).get
    assertEquals(grandScepter.id.value, "grand-scepter")
    assertEquals(grandScepter.value, 0)
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
        |      "id": "1",
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
