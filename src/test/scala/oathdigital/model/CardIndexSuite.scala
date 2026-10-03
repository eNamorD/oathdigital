package oathdigital.model

import oathdigital.testkit.Unwrap.{accepted, rejected}

class CardIndexSuite extends munit.FunSuite:
  import TestGameFixtures._

  test("container membership derives a card's location"):
    val index = accepted(CardIndex.from(game))

    // The fixture's world deck holds only worldDenizen, so it is at index 0.
    assertEquals(
      index.locationOf(worldDenizen),
      Some(CardLocation(CardContainer.Deck(CardDeck.World), 0))
    )
    assertEquals(index.stateOf(worldDenizen), None)
    // The fixture puts siteDenizen alone on its first site, sites.head.
    assertEquals(
      index.locationOf(siteDenizen.id),
      Some(
        CardLocation(
          CardContainer.Site(sites.head, SiteCardArea.Denizens),
          0
        )
      )
    )
    // The fixture stores storedEdifice in the Atlas stored site S9, the
    // first Atlas entry.
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

  test("the derived index rejects a card present in two containers"):
    // Patched with copy, not a Table step: the test makes an illegal state
    // on purpose.
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
      rejected(result).exists {
        case CardIndexProblem.DuplicateCard(id, locations) =>
          id == worldDenizen && locations.size == 2
        case _ => false
      }
    )

  test("expected catalog cards can be checked without storing an instance ID"):
    val absent = RelicId("R-missing")
    val result = CardIndex.from(game, expectedCards = Set(absent))

    assertEquals(
      result,
      Left(Vector(CardIndexProblem.MissingCard(absent)))
    )

  test("temporary hands and set-aside relics are indexed as owning zones"):
    // Patched with copy, not a Table step: the test makes an illegal state
    // on purpose. The reliquary and the world deck are emptied so
    // reliquaryRelic and worldDenizen are not also in them.
    val prepared = game.copy(
      campaign = game.campaign.copy(reliquary = Vector.empty),
      current = game.current.copy(
        commonCards = game.current.commonCards.copy(worldDeck = Vector.empty),
        temporaryHands = Map(playerId -> Vector(worldDenizen)),
        setAsideRelics = Vector(reliquaryRelic)
      )
    )
    val index = accepted(CardIndex.from(prepared))

    assertEquals(index.locationOf(worldDenizen), Some(CardLocation(
      CardContainer.Player(playerId, PlayerCardArea.Hand), 0)))
    assertEquals(index.locationOf(reliquaryRelic), Some(CardLocation(
      CardContainer.SetAsideRelics, 0)))

  test("temporary hands remain indexed even when their owner is invalid"):
    // Patched with copy, not a Table step: the test makes an illegal state
    // on purpose. The world deck is emptied so worldDenizen is not also in it.
    val unknown = PlayerId("unknown")
    val prepared = game.copy(current = game.current.copy(
      commonCards = game.current.commonCards.copy(worldDeck = Vector.empty),
      temporaryHands = Map(unknown -> Vector(worldDenizen))))

    val index = accepted(CardIndex.from(prepared))
    assertEquals(index.locationOf(worldDenizen), Some(CardLocation(
      CardContainer.Player(unknown, PlayerCardArea.Hand), 0)))
    assert(DomainValidation.validate(prepared).contains(
      DomainProblem.UnknownTemporaryHandOwner(unknown)))
