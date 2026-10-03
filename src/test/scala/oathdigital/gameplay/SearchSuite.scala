package oathdigital.gameplay

import oathdigital.gameplay.actions.{SearchRules, VisionRules}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}

class SearchSuite extends munit.FunSuite:

  private def ready: ReadyGame = Table.start.ready

  test("world Search cost follows Visions Drawn track bands"):
    val base = ready
    Vector(0 -> 2, 1 -> 3, 2 -> 3, 3 -> 4, 4 -> 4, 5 -> 4).foreach:
      case (visions, expected) =>
        val state = Table.from(base).visionsDrawn(visions).unchecked
        assertEquals(SearchRules.cost(state, SearchSource.WorldDeck,
          Region.Cradle), Right(expected))
        assertEquals(SearchRules.cost(state,
          SearchSource.RegionalDiscard(Region.Cradle), Region.Cradle), Right(2))

  test("world Search draw stops at first Vision"):
    val first = CatalogNames.denizen("Threatening Roar")
    val vision = VisionRules.Faith
    val state = Table.start.worldDeckTop(first, vision, "Fae Merchant",
      "Second Chance").ready
    assertEquals(SearchRules.draw(state, SearchSource.WorldDeck, Region.Cradle),
      Right(Vector(first, vision)))
