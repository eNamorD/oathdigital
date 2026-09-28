package oathdigital.gameplay

import oathdigital.gameplay.actions.SearchRules
import oathdigital.model._
import oathdigital.testkit.Table

class SearchSuite extends munit.FunSuite:

  private def ready: ReadyGame =
    val state = Table.start.ready
    state

  test("world Search cost follows Visions Drawn track bands"):
    val base = ready
    Vector(0 -> 2, 1 -> 3, 2 -> 3, 3 -> 4, 4 -> 4, 5 -> 4).foreach:
      case (visions, expected) =>
        val state = base.updateCurrent(_.copy(tracks = base.game.current.tracks.copy(
            visionsDrawn = visions)))
        assertEquals(SearchRules.cost(state, SearchSource.WorldDeck,
          Region.Cradle), Right(expected))
        assertEquals(SearchRules.cost(state,
          SearchSource.RegionalDiscard(Region.Cradle), Region.Cradle), Right(2))

  test("world Search draw stops at first Vision"):
    val base = ready
    val denizens = base.game.current.commonCards.worldDeck.collect {
      case id: DenizenId => id
    }.take(3)
    val vision = base.game.current.commonCards.worldDeck.collectFirst {
      case id: VisionId => id
    }.get
    val state = base.updateCurrent(_.copy(commonCards = base.game.current.commonCards.copy(
        worldDeck = Vector(denizens.head, vision) ++ denizens.tail)))
    assertEquals(SearchRules.draw(state, SearchSource.WorldDeck, Region.Cradle),
      Right(Vector(denizens.head, vision)))
