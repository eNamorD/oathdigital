package oathdigital.model

class PlayerSetupStateSuite extends munit.FunSuite {
  import TestGameFixtures._

  test("a player may exist before placing their pawn") {
    val unplacedGame = game.copy(
      current = game.current.copy(
        players = Vector(player.copy(pawnSite = None))
      )
    )

    assertEquals(DomainValidation.validate(unplacedGame), Vector.empty)
  }

  test("a placed pawn must still reference an in-play site") {
    val invalidSite = SiteId("not-in-play")
    val invalidGame = game.copy(
      current = game.current.copy(
        players = Vector(player.copy(pawnSite = Some(invalidSite)))
      )
    )

    assert(
      DomainValidation.validate(invalidGame).contains(
        DomainProblem.PawnOutsideMap(playerId, invalidSite)
      )
    )
  }

  test("multiple starting advisers retain order and derived locations") {
    val first = DenizenState(
      DenizenId("D-start-1"),
      Orientation.FaceDown,
      Tokens.empty
    )
    val second = VisionState(
      VisionId("V-start-2"),
      Orientation.FaceDown
    )
    val updatedLineage = lineage.copy(
      startingAdvisers = Vector(first, second)
    )
    val updatedGame = game.copy(
      campaign = game.campaign.copy(
        lineages = Map(lineageId -> updatedLineage)
      )
    )

    val index = CardIndex.from(updatedGame).toOption.get

    assertEquals(
      index.locationOf(first.id),
      Some(
        CardLocation(
          CardContainer.Lineage(
            lineageId,
            LineageCardArea.StartingAdvisers
          ),
          0
        )
      )
    )
    assertEquals(
      index.locationOf(second.id),
      Some(
        CardLocation(
          CardContainer.Lineage(
            lineageId,
            LineageCardArea.StartingAdvisers
          ),
          1
        )
      )
    )
    assertEquals(DomainValidation.validate(updatedGame), Vector.empty)
  }
}
