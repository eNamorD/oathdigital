package oathdigital.model

class CardIndexSuite extends munit.FunSuite {
  import TestGameFixtures._

  test("container membership derives a card's location") {
    val index = CardIndex.from(game).toOption.get

    assertEquals(
      index.locationOf(worldDenizen),
      Some(CardLocation(CardContainer.Deck(DeckKind.World), 0))
    )
    assertEquals(index.stateOf(worldDenizen), None)
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
      denizens = Vector(
        DenizenState(worldDenizen, Orientation.FaceUp, Tokens.empty)
      )
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
          id == worldDenizen && locations.size == 2
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

  test("temporary hands and set-aside relics are indexed as owning zones") {
    val prepared = game.copy(
      campaign = game.campaign.copy(reliquary = Vector.empty),
      current = game.current.copy(
        commonCards = game.current.commonCards.copy(worldDeck = Vector.empty),
        temporaryHands = Map(playerId -> Vector(worldDenizen)),
        setAsideRelics = Vector(reliquaryRelic)
      )
    )
    val index = CardIndex.from(prepared).toOption.get

    assertEquals(index.locationOf(worldDenizen), Some(CardLocation(
      CardContainer.Player(playerId, PlayerCardArea.Hand), 0)))
    assertEquals(index.locationOf(reliquaryRelic), Some(CardLocation(
      CardContainer.SetAsideRelics, 0)))
  }

  test("temporary hands remain indexed even when their owner is invalid") {
    val unknown = PlayerId("unknown")
    val prepared = game.copy(current = game.current.copy(
      commonCards = game.current.commonCards.copy(worldDeck = Vector.empty),
      temporaryHands = Map(unknown -> Vector(worldDenizen))))

    val index = CardIndex.from(prepared).toOption.get
    assertEquals(index.locationOf(worldDenizen), Some(CardLocation(
      CardContainer.Player(unknown, PlayerCardArea.Hand), 0)))
    assert(DomainValidation.validate(prepared).contains(
      DomainProblem.UnknownTemporaryHandOwner(unknown)))
  }

  test("the fixture satisfies structural domain invariants") {
    assertEquals(DomainValidation.validate(game), Vector.empty)
  }
}
