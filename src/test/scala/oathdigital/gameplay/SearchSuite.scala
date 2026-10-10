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
        assertEquals(SearchRules.cost(state, SearchSource.WorldDeck),
          expected)
        assertEquals(SearchRules.cost(state,
          SearchSource.RegionalDiscard(Region.Cradle)), 2)

  test("every region's discard pile costs 2 Supply"):
    Region.all.foreach(region => assertEquals(
      SearchRules.cost(ready, SearchSource.RegionalDiscard(region)), 2))

  test("world Search draw stops at first Vision"):
    val first = CatalogNames.denizen("Threatening Roar")
    val vision = VisionRules.Faith
    val state = Table.start.worldDeckTop(first, vision, "Fae Merchant",
      "Second Chance").ready
    assertEquals(SearchRules.draw(state, SearchSource.WorldDeck),
      Vector(first, vision))

  test("a draw from the bottom of a discard pile takes its bottom cards, " +
      "bottom first"):
    val pile = Vector("Threatening Roar", "Fae Merchant", "Second Chance",
      "Wrestlers").map(CatalogNames.denizen(_))
    val state = Table.start.discarded(Region.Provinces, pile*).ready
    assertEquals(SearchRules.draw(state,
      SearchSource.RegionalDiscard(Region.Provinces),
      position = StackPosition.Bottom), pile.take(3))

  test("a draw from the bottom of the world deck stops at the first " +
      "Vision"):
    val above = CatalogNames.denizen("Threatening Roar")
    val bottom = CatalogNames.denizen("Fae Merchant")
    val state = Table.start.worldDeckBottom(above, VisionRules.Faith,
      bottom).ready
    assertEquals(SearchRules.draw(state, SearchSource.WorldDeck,
      position = StackPosition.Bottom), Vector(bottom, VisionRules.Faith))

  test("a pile lists its cards top first"):
    val state = Table.start
      .worldDeckTop("Threatening Roar", "Fae Merchant")
      .discarded(Region.Hinterland, "Defame", "Scryer").ready
    assertEquals(SearchRules.pile(state, SearchSource.WorldDeck).take(2),
      Vector("Threatening Roar", "Fae Merchant")
        .map(CatalogNames.denizen(_)))
    assertEquals(SearchRules.pile(state,
      SearchSource.RegionalDiscard(Region.Hinterland)).take(2),
      Vector("Scryer", "Defame").map(CatalogNames.denizen(_)))
