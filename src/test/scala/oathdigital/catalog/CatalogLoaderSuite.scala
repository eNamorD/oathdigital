package oathdigital.catalog

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import oathdigital.catalog.CatalogLoadError.{
  DuplicateDefinitionId,
  DuplicatePowerId,
  IncompatibleCatalog,
  InvalidJson,
  InvalidValue,
  MissingField,
  UnsupportedSchemaVersion,
  WrongType
}
import oathdigital.model.{CatalogRef, PowerId, SiteId, Suit, Tokens}

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
      catalog.denizens.head.restrictions,
      CardRestrictions.Unrestricted
    )
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

  test("ordered powers preserve IDs text and persistence") {
    val value = ujson.read(fixture)
    value("denizens")(0)("powers").arr.append(ujson.Obj(
      "id" -> "denizen.fixture-second",
      "persistent" -> true,
      "rulesText" -> "Second exact paragraph."
    ))
    val powers = CatalogLoader.load(value.render()).toOption.get.denizens.head.powers
    assertEquals(powers, Vector(
      CatalogPower(PowerId("denizen.fixture-denizen"), persistent = false,
        "ACTION: Perform the fixture action."),
      CatalogPower(PowerId("denizen.fixture-second"), persistent = true,
        "Second exact paragraph.")
    ))
    assertEquals(powers.map(_.id), Vector(PowerId("denizen.fixture-denizen"),
      PowerId("denizen.fixture-second")))
  }

  test("catalog powers round-trip the shared PowerId type") {
    val power = CatalogLoader.load(fixture).toOption.get.denizens.head.powers.head
    assertEquals(power.id, PowerId("denizen.fixture-denizen"))
    assertEquals(power.id.value, "denizen.fixture-denizen")
  }

  test("power IDs are globally unique across rendered component families") {
    val value = ujson.read(fixture)
    value("relics")(0)("powers")(0)("id") = "denizen.fixture-denizen"
    val errors = CatalogLoader.load(value.render()).left.toOption.get
    assert(errors.exists {
      case DuplicatePowerId(path, PowerId("denizen.fixture-denizen"), first) =>
        path == "$.relics[0].powers[0].id" &&
          first == "$.denizens[0].powers[0].id"
      case _ => false
    })
  }

  test("site handler IDs share the rendered power namespace") {
    val value = ujson.read(fixture)
    value("sites")(0)("handlers")(0) = "denizen.fixture-denizen"
    val errors = CatalogLoader.load(value.render()).left.toOption.get
    assert(errors.exists {
      case DuplicatePowerId(path, PowerId("denizen.fixture-denizen"), first) =>
        path == "$.sites[0].handlers[0]" &&
          first == "$.denizens[0].powers[0].id"
      case _ => false
    })
  }

  test("an unknown suit is rejected with the offending path") {
    val value = ujson.read(fixture)
    value("denizens")(0)("suit") = "sun"
    val errors = CatalogLoader.load(value.render()).left.toOption.get
    assert(errors.contains(
      InvalidValue("$.denizens[0].suit", "unsupported suit sun")), errors)
  }

  test("power rules text must contain a non-whitespace character") {
    Vector("", "  \n\t ").foreach { invalid =>
      val value = ujson.read(fixture)
      value("denizens")(0)("powers")(0)("rulesText") = invalid
      val errors = CatalogLoader.load(value.render()).left.toOption.get
      assert(errors.exists {
        case InvalidValue(path, detail) =>
          path == "$.denizens[0].powers[0].rulesText" &&
            detail.contains("blank")
        case _ => false
      })
    }
    intercept[IllegalArgumentException](
      CatalogPower(PowerId("test.blank"), persistent = false, " \t "))
  }

  test("production power text exactly equals checked-in JSON in source order") {
    val raw = ujson.read(Files.readString(
      Paths.get("docs/catalog/new-foundations-component-catalog.json")))
    val loaded = CatalogLoader.load(raw.render()).toOption.get
    val rawDenizens = raw("denizens").arr.map(component => component("id").str ->
      component("powers").arr.map(power => (power("id").str,
        power("persistent").bool, power("rulesText").str)).toVector).toMap
    assertEquals(loaded.denizens.map(component => component.id.value ->
      component.powers.map(power =>
        (power.id.value, power.persistent, power.rulesText))).toMap, rawDenizens)
  }

  test("reviewed runtime-power mirror exactly preserves authoritative structures") {
    val runtime = ujson.read(Files.readString(
      Paths.get("docs/catalog/new-foundations-component-catalog.json")))
    val mirror = ujson.read(Files.readString(
      Paths.get("reference/catalog-ingestion/reviewed-runtime-powers.json")))
    def powersById(family: String) = ujson.Obj.from(
      runtime(family).arr.map(component => component("id").str -> component("powers")))
    val edificeFaces = ujson.Obj.from(runtime("edifices").arr.map { component =>
      component("id").str -> ujson.Obj(
        "intact" -> ujson.Obj(
          "restrictions" -> component("intact")("restrictions"),
          "powers" -> component("intact")("powers")),
        "ruined" -> ujson.Obj(
          "restrictions" -> component("ruined")("restrictions"),
          "powers" -> component("ruined")("powers")))
    })
    assertEquals(mirror("denizens"), powersById("denizens"))
    assertEquals(mirror("relics"), powersById("relics"))
    assertEquals(mirror("edifices"), edificeFaces)
    assertEquals(mirror("legacies"), powersById("legacies"))
  }

  test("edifice face restrictions are exact and typed") {
    val catalog = CatalogLoader.load(fixture).toOption.get
    assertEquals(catalog.edifices.head.intact.restrictions,
      CardRestrictions.Locked)
    assertEquals(catalog.edifices.head.ruined.restrictions,
      CardRestrictions.Unrestricted)

    val topLevel = fixture.replace("\"suit\": \"discord\",",
      "\"suit\": \"discord\",\n      \"restrictions\": null,")
    assert(CatalogLoader.load(topLevel).isLeft)
    val wrongIntact = fixture.replace("\"restrictions\": [\"locked\"]",
      "\"restrictions\": null")
    assert(CatalogLoader.load(wrongIntact).left.toOption.get.exists {
      case InvalidValue(path, _) => path == "$.edifices[0].intact.restrictions"
      case _ => false
    })
    val wrongRuined = fixture.replace(
      "\"name\": \"Ruined Fixture\",\n        \"restrictions\": null",
      "\"name\": \"Ruined Fixture\",\n        \"restrictions\": [\"locked\"]")
    assert(CatalogLoader.load(wrongRuined).left.toOption.get.exists {
      case InvalidValue(path, _) => path == "$.edifices[0].ruined.restrictions"
      case _ => false
    })
  }

  test("malformed empty duplicate and incomplete powers report exact paths") {
    def changed(update: ujson.Value => Unit) = {
      val value = ujson.read(fixture)
      update(value("denizens")(0))
      CatalogLoader.load(value.render()).left.toOption.get
    }
    assert(changed(component => component("powers") = ujson.Arr()).exists {
      case InvalidValue(path, _) => path == "$.denizens[0].powers"
      case _ => false
    })
    assert(changed(component => component("powers")(0).obj.remove("persistent"))
      .exists {
        case MissingField(path) => path == "$.denizens[0].powers[0].persistent"
        case _ => false
      })
    assert(changed(component => component("powers")(0)("persistent") = "yes")
      .exists {
        case WrongType(path, _, _) =>
          path == "$.denizens[0].powers[0].persistent"
        case _ => false
      })
    assert(changed(component => component("powers").arr.append(
      component("powers")(0))).exists {
        case InvalidValue(path, _) => path == "$.denizens[0].powers"
        case _ => false
      })
    assert(changed(component => component("powers")(0)("id") = "bad")
      .exists {
        case InvalidValue(path, _) => path == "$.denizens[0].powers[0].id"
        case _ => false
      })
  }

  test("the production catalog contains only the final runtime corpus") {
    val catalog = CatalogLoader
      .load(
        Paths.get("docs/catalog/new-foundations-component-catalog.json"),
        CatalogLoadRequest(
          expectedCatalog = Some(
            CatalogRef("oath-new-foundations", "2026.08.29-pre5")
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

    assertEquals(
      catalog.denizens.groupBy(_.restrictions).view.mapValues(_.size).toMap,
      Map(
        CardRestrictions.Unrestricted -> 133,
        CardRestrictions.SiteOnly -> 51,
        CardRestrictions.AdviserOnly -> 40,
        CardRestrictions.LockedAdviserOnly -> 31
      )
    )
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
    assertEquals(alchemist.restrictions, CardRestrictions.SiteOnly)

    val witchsBargain = catalog.denizens.find(_.id.value == "77").get
    assertEquals(witchsBargain.handlers, Vector("denizen.witchs-bargain"))

    val bedOfRoots = catalog.denizens.find(_.id.value == "212").get
    assert(bedOfRoots.rulesText.startsWith("[favor-burnt] [favor-burnt]"))

    val pressgangs = catalog.denizens.find(_.id.value == "6").get
    assert(pressgangs.rulesText.contains("already have"))
    assertEquals(
      catalog.denizens.find(_.id.value == "7").get.restrictions,
      CardRestrictions.SiteOnly
    )
    assertEquals(
      catalog.denizens.find(_.id.value == "111").get.restrictions,
      CardRestrictions.LockedAdviserOnly
    )

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

  test("the printed-ID catalog rejects the incompatible pre2 reference") {
    val expected = CatalogRef("oath-new-foundations", "2026.07.27-pre2")
    val result = CatalogLoader.load(
      Paths.get("docs/catalog/new-foundations-component-catalog.json"),
      CatalogLoadRequest(expectedCatalog = Some(expected))
    )

    assert(
      result.left.toOption.get.exists {
        case IncompatibleCatalog(_, `expected`, actual) =>
          actual == CatalogRef("oath-new-foundations", "2026.08.29-pre5")
        case _ => false
      }
    )
  }

  test("denizen restrictions decode to typed placement semantics") {
    val unrestrictedDenizen =
      "\"suit\": \"arcane\",\n      \"restrictions\": null"
    val siteOnly = fixture.replace(
      unrestrictedDenizen,
      "\"suit\": \"arcane\",\n      \"restrictions\": [\"site-only\"]"
    )
    val adviserOnly = fixture.replace(
      unrestrictedDenizen,
      "\"suit\": \"arcane\",\n      \"restrictions\": [\"adviser-only\"]"
    )
    val locked = fixture.replace(
      unrestrictedDenizen,
      "\"suit\": \"arcane\",\n      \"restrictions\": [\"adviser-only\", \"locked\"]"
    )

    assertEquals(
      CatalogLoader.load(siteOnly).toOption.get.denizens.head.restrictions,
      CardRestrictions.SiteOnly
    )
    assertEquals(
      CatalogLoader.load(adviserOnly).toOption.get.denizens.head.restrictions,
      CardRestrictions.AdviserOnly
    )
    assertEquals(
      CatalogLoader.load(locked).toOption.get.denizens.head.restrictions,
      CardRestrictions.LockedAdviserOnly
    )
  }

  test("invalid denizen restrictions report their exact catalog paths") {
    val unrestrictedDenizen =
      "\"suit\": \"arcane\",\n      \"restrictions\": null"
    def restricted(value: String): String =
      fixture.replace(
        unrestrictedDenizen,
        s"\"suit\": \"arcane\",\n      \"restrictions\": $value"
      )
    val empty = restricted("[]")
    val missing = fixture.replace(
      "      \"restrictions\": null,\n      \"powers\": [",
      "      \"powers\": ["
    )
    val lockedAlone = fixture.replace(
      unrestrictedDenizen,
      "\"suit\": \"arcane\",\n      \"restrictions\": [\"locked\"]"
    )
    val unknown = restricted("[\"elsewhere\"]")
    val wrongType = restricted("[1]")

    for (json <- Vector(empty, lockedAlone))
      assert(
        CatalogLoader.load(json).left.toOption.get.exists {
          case InvalidValue(path, _) => path == "$.denizens[0].restrictions"
          case _ => false
        }
      )
    assert(
      CatalogLoader.load(unknown).left.toOption.get.exists {
        case InvalidValue(path, _) => path == "$.denizens[0].restrictions[0]"
        case _ => false
      }
    )
    assert(
      CatalogLoader.load(wrongType).left.toOption.get.exists {
        case WrongType(path, _, _) => path == "$.denizens[0].restrictions[0]"
        case _ => false
      }
    )
    assert(
      CatalogLoader.load(missing).left.toOption.get.exists {
        case MissingField(path) => path == "$.denizens[0].restrictions"
        case _ => false
      }
    )
  }

  test("schema versions and malformed JSON have explicit errors") {
    val unsupported =
      fixture.replace(
        "\"schemaVersion\": \"1.3.0\"",
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
        |      "restrictions": null,
        |      "powers": [{
        |        "id": "denizen.duplicate-denizen",
        |        "persistent": false,
        |        "rulesText": "Duplicate rule."
        |      }]
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
