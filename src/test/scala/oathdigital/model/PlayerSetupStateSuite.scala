package oathdigital.model

import oathdigital.testkit.Unwrap.accepted

class PlayerSetupStateSuite extends munit.FunSuite:
  import TestGameFixtures._

  test("a player may exist before placing their pawn"):
    // Patched with copy: the player's pawn site is the subject.
    val unplacedGame = game.copy(
      current = game.current.copy(
        players = Vector(player.copy(pawnSite = None))
      )
    )

    assertEquals(DomainValidation.validate(unplacedGame), Vector.empty)

  test("a placed pawn must still reference an in-play site"):
    // Patched with copy: the pawn site is the subject, and the state is
    // invalid on purpose.
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

  test("multiple starting advisers retain order and derived locations"):
    // Patched with copy: the lineage's starting advisers are the subject,
    // and no Table step sets them.
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

    val index = accepted(CardIndex.from(updatedGame))

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
