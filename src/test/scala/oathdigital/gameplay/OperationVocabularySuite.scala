package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.model._
import oathdigital.gameplay.actions.VisionRules
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class OperationVocabularySuite extends munit.FunSuite:
  private val actor = p1
  private val other = p2

  private def run(state: ReadyGame, operations: CoreOperation*) =
    OperationPipeline.run(state, operations.toVector,
      OperationPolicy.Permissive, Vector.empty)(Right(_))

  test("a default Give shrinks to what the giver holds and a required Give rejects"):
    val funded = Table.start.favor(actor, 1).ready
    def give(required: Boolean) = Give(Piece.Favor(3), actor,
      Location.PlayArea(actor), Location.PlayArea(other), required)

    val shrunk = accepted(run(funded, give(required = false)))
    assertEquals(shrunk.executed, Vector[CoreOperation](Give(Piece.Favor(1),
      actor, Location.PlayArea(actor), Location.PlayArea(other))))
    assertEquals(run(funded, give(required = true)).left.toOption,
      Some(OathViolation.CoreOperationRejected("insufficient-pieces",
        "player play area contains 1 of requested favor")))

  test("a Vision adviser can be buried"):
    val vision = VisionRules.Conspiracy
    val held = Table.start.adviser(actor, vision, facedown = true).ready
    val buried = accepted(run(held, Bury(BuryableCard.Vision(vision),
      PositionedLocation(Location.PlayArea(actor))))).state
    assertEquals(buried.game.current.commonCards.worldDeck.lastOption,
      Some(vision))
    assertEquals(Look(buried).player(actor).advisers, Vector.empty)

  test("Bury.standard is the discard's returns followed by the bury"):
    val card = DenizenId("denizen:one")
    val from = PositionedLocation(Location.Site(SiteId("site:one")))
    assertEquals(Bury.standard(BuryableCard.Denizen(card), from,
      Some(Suit.Hearth), favor = 2, secrets = 1, actor), Vector[CoreOperation](
      Move(Piece.Favor(2), PositionedLocation(Location.OnCard(card)),
        PositionedLocation(Location.FavorBank(Suit.Hearth))),
      Move(Piece.Secrets(1), PositionedLocation(Location.OnCard(card)),
        PositionedLocation(Location.PlayArea(actor))),
      FlipSecrets(actor, 1, SecretSide.FaceUp, SecretSide.FaceDown),
      Bury(BuryableCard.Denizen(card), from)))
    assertEquals(Bury.standard(BuryableCard.Relic(RelicId("relic:one")), from,
      None, favor = 0, secrets = 0, actor),
      Vector[CoreOperation](Bury(BuryableCard.Relic(RelicId("relic:one")), from)))

  test("a buried site denizen returns its favor to the bank and its secrets " +
      "to the acting player facedown"):
    val siteId = CatalogNames.site("Ancient City")
    val denizen = CatalogNames.denizen("Errand Boy")
    val suit = Suit.Beast  // Errand Boy is a Beast.
    val loaded = Table.start.denizen(denizen, at = siteId)
      .tokens(denizen, favor = 2, secrets = 1).ready
    val before = Look(loaded).player(actor).board
    val result = run(loaded, Bury.standard(BuryableCard.Denizen(denizen),
      PositionedLocation(Location.Site(siteId)), Some(suit), 2, 1, actor)*)
      .fold(error => fail(error.toString), _.state)
    val after = Look(result).player(actor).board
    assertEquals(after.faceDownSecrets, before.faceDownSecrets + 1)
    assertEquals(result.banks.favor.getOrElse(suit, 0), loaded.banks.favor.getOrElse(suit, 0) + 2)
    assertEquals(result.game.current.commonCards.worldDeck.lastOption,
      Some(denizen))

  test("a Give to the shared bank moves the favor out of the giver's hands, " +
      "which is how giving to Bandits is modelled"):
    val funded = Table.start.favor(actor, 2).ready
    val gave = accepted(run(funded, Give(Piece.Favor(1), actor,
      Location.PlayArea(actor), Location.SharedBank)))
    // 2 favor, 1 given.
    assertEquals(Look(gave.state).favor(actor), 1)
