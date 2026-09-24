package oathdigital.application

import oathdigital.catalog._
import oathdigital.model.{CatalogRef, DenizenId, EdificeId, PowerId, RelicId, Suit}

class ImplementedCardCatalogSuite extends munit.FunSuite {
  private val implemented: PowerId => Boolean = Set(
    "denizen.solar-hearth-child.done",
    "relic.cup-of-plenty.done",
    "edifice.hall-of-debate.intact",
    "edifice.hall-of-debate.ruined").map(PowerId(_))

  private def catalogPower(id: String) =
    CatalogPower(id, persistent = false, rulesText = "text")

  private def denizen(id: String, suit: Suit, powerIds: Vector[String]) =
    DenizenDefinition(DefinitionId(id), id, suit, CardRestrictions.Unrestricted,
      powerIds.map(catalogPower))

  private def relic(id: String, role: RelicRole, powerIds: Vector[String]) =
    RelicDefinition(DefinitionId(id), id, role, value = 1, defense = 0,
      powers = powerIds.map(catalogPower))

  private def edificeFace(name: String, powerIds: Vector[String]) =
    EdificeFaceDefinition(name, CardRestrictions.Unrestricted,
      powerIds.map(catalogPower))

  private val implementedDenizen =
    denizen("solar-hearth-child", Suit.Hearth,
      Vector("denizen.solar-hearth-child.done"))
  private val unimplementedDenizen =
    denizen("unwired-card", Suit.Hearth, Vector("denizen.unwired-card.todo"))
  private val powerlessDenizen =
    denizen("blank-card", Suit.Hearth, Vector.empty)

  private val catalog = ExecutableCatalog(
    schemaVersion = "test", ref = CatalogRef("test", "1"),
    denizens = Vector(implementedDenizen, unimplementedDenizen, powerlessDenizen),
    relics = Vector(
      relic("cup-of-plenty", RelicRole.Ordinary, Vector("relic.cup-of-plenty.done")),
      relic("unwired-relic", RelicRole.Ordinary, Vector("relic.unwired-relic.todo")),
      relic("grand-scepter", RelicRole.GrandScepter,
        Vector("relic.cup-of-plenty.done"))),
    edifices = Vector(
      EdificeDefinition(DefinitionId("hall-of-debate"), Suit.Hearth,
        intact = edificeFace("Hall of Debate",
          Vector("edifice.hall-of-debate.intact")),
        ruined = edificeFace("Ruined Hall",
          Vector("edifice.hall-of-debate.ruined"))),
      EdificeDefinition(DefinitionId("unwired-edifice"), Suit.Hearth,
        intact = edificeFace("Unwired", Vector("edifice.unwired-edifice.intact")),
        ruined = edificeFace("Ruined Unwired",
          Vector("edifice.unwired-edifice.ruined")))),
    legacies = Vector.empty,
    sites = Vector.empty)

  test("a denizen is implemented only when every printed power is implemented") {
    assertEquals(ImplementedCardCatalog.denizens(catalog, implemented),
      Set(DenizenId("solar-hearth-child"), DenizenId("blank-card")))
  }

  test("only ordinary relics with every power implemented count as implemented") {
    assertEquals(ImplementedCardCatalog.ordinaryRelics(catalog, implemented),
      Set(RelicId("cup-of-plenty")))
  }

  test("a Homeland's implemented edifice needs both faces implemented") {
    assertEquals(ImplementedCardCatalog.homelandEdifice(catalog, Suit.Hearth, implemented),
      Some(EdificeId("hall-of-debate")))
    assertEquals(ImplementedCardCatalog.homelandEdifice(catalog, Suit.Beast, implemented),
      None)
  }
}
