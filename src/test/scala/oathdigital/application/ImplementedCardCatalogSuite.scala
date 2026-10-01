package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.{CatalogRef, DenizenId, EdificeId, PowerId, RelicId, Suit}
import oathdigital.testkit.TestCards

class ImplementedCardCatalogSuite extends munit.FunSuite:
  private val implemented: PowerId => Boolean = Set(
    "denizen.solar-hearth-child.done",
    "relic.cup-of-plenty.done",
    "edifice.hall-of-debate.intact",
    "edifice.hall-of-debate.ruined").map(PowerId(_))

  private def powers(ids: Vector[String]) = ids.map(TestCards.power(_))

  private def denizen(id: String, suit: Suit, powerIds: Vector[String]) =
    TestCards.denizen(DenizenId(id), id, suit, powers(powerIds))

  private def relic(id: String, powerIds: Vector[String]) =
    TestCards.relic(RelicId(id), id, powers(powerIds))

  private def edifice(id: String, intact: (String, Vector[String]),
      ruined: (String, Vector[String])) =
    TestCards.edifice(EdificeId(id), Suit.Hearth, intact._1,
      powers(intact._2), ruined._1, powers(ruined._2))

  private val catalog = ExecutableCatalog(
    ref = CatalogRef("test", "1"),
    denizens = Vector(
      denizen("solar-hearth-child", Suit.Hearth,
        Vector("denizen.solar-hearth-child.done")),
      denizen("unwired-card", Suit.Hearth, Vector("denizen.unwired-card.todo")),
      denizen("blank-card", Suit.Hearth, Vector.empty)),
    relics = Vector(
      relic("cup-of-plenty", Vector("relic.cup-of-plenty.done")),
      relic("unwired-relic", Vector("relic.unwired-relic.todo")),
      // The Grand Scepter's printed id: never an ordinary relic.
      relic("grand-scepter", Vector("relic.cup-of-plenty.done"))),
    edifices = Vector(
      edifice("hall-of-debate",
        "Hall of Debate" -> Vector("edifice.hall-of-debate.intact"),
        "Ruined Hall" -> Vector("edifice.hall-of-debate.ruined")),
      edifice("unwired-edifice",
        "Unwired" -> Vector("edifice.unwired-edifice.intact"),
        "Ruined Unwired" -> Vector("edifice.unwired-edifice.ruined"))),
    legacies = Vector.empty,
    sites = Vector.empty)

  test("a denizen is implemented only when every printed power is implemented"):
    assertEquals(ImplementedCardCatalog.denizens(catalog, implemented),
      Set(DenizenId("solar-hearth-child"), DenizenId("blank-card")))

  test("only ordinary relics with every power implemented count as implemented"):
    assertEquals(ImplementedCardCatalog.ordinaryRelics(catalog, implemented),
      Set(RelicId("cup-of-plenty")))

  test("a Homeland's implemented edifice needs both faces implemented"):
    assertEquals(ImplementedCardCatalog.homelandEdifice(catalog, Suit.Hearth, implemented),
      Some(EdificeId("hall-of-debate")))
    assertEquals(ImplementedCardCatalog.homelandEdifice(catalog, Suit.Beast, implemented),
      None)
