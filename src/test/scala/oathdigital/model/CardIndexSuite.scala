package oathdigital.model

class CardIndexSuite extends munit.FunSuite {
  import TestGameFixtures._

  test("container membership derives a card's location") {
    val index = CardIndex.from(game).toOption.get

    assertEquals(
      index.locationOf(worldDenizen.id),
      Some(CardLocation(CardContainer.Deck(DeckKind.World), 0))
    )
    assertEquals(
      index.locationOf(siteDenizen.id),
      Some(
        CardLocation(
          CardContainer.Site(sites.head, SiteCardArea.Denizens),
          0
        )
      )
    )
    assertEquals(
      index.locationOf(storedEdifice.id),
      Some(
        CardLocation(
          CardContainer.AtlasSite(
            atlasPosition = 0,
            site = SiteId("S9"),
            area = SiteCardArea.Denizens
          ),
          0
        )
      )
    )
  }

  test("the derived index rejects a card present in two containers") {
    val duplicatedSite = game.current.map.sites(sites(2)).copy(
      denizens = Vector(worldDenizen)
    )
    val duplicatedGame = game.copy(
      current = game.current.copy(
        map = game.current.map.copy(
          sites = game.current.map.sites.updated(sites(2), duplicatedSite)
        )
      )
    )

    val result = CardIndex.from(duplicatedGame)

    assert(
      result.left.toOption.get.exists {
        case CardIndexProblem.DuplicateCard(id, locations) =>
          id == worldDenizen.id && locations.size == 2
        case _ => false
      }
    )
  }

  test("expected catalog cards can be checked without storing an instance ID") {
    val absent = RelicId("R-missing")
    val result = CardIndex.from(game, expectedCards = Set(absent))

    assertEquals(
      result,
      Left(Vector(CardIndexProblem.MissingCard(absent)))
    )
  }

  test("the fixture satisfies structural domain invariants") {
    assertEquals(DomainValidation.validate(game), Vector.empty)
  }
}
