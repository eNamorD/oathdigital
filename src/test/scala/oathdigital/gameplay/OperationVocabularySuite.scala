package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class OperationVocabularySuite extends munit.FunSuite {
  private val base = initialReady
  private val current = base.game.current
  private val actor = current.turn.activePlayer
  private val other = current.players.map(_.player).find(_ != actor).get

  private def run(state: ReadyGame, operations: CoreOperation*) =
    OperationPipeline.run(state, operations.toVector,
      OperationPolicy.Permissive)(Right(_))

  test("a default Give shrinks to what the giver holds and a required Give rejects") {
    val funded = base.updateCurrent(_.copy(players = current.players.map(p =>
      if (p.player == actor) p.copy(board = p.board.copy(favor = 1)) else p)))
    def give(required: Boolean) = Give(Piece.Favor(3), actor,
      Location.PlayArea(actor), Location.PlayArea(other), required)

    val shrunk = run(funded, give(required = false)).toOption.get
    assertEquals(shrunk.executed, Vector[CoreOperation](Give(Piece.Favor(1),
      actor, Location.PlayArea(actor), Location.PlayArea(other))))
    assert(run(funded, give(required = true)).isLeft)
  }

  test("BuryableCard.Vision buries to the bottom of the world deck") {
    val bury = Bury(BuryableCard.Vision(VisionId("vision:one")),
      PositionedLocation(Location.PlayArea(actor)))
    assertEquals(bury.to, PositionedLocation(Location.Deck(CardDeck.World),
      StackPosition.Bottom))
  }

  test("a Vision adviser can be buried") {
    val vision = current.commonCards.worldDeck.collectFirst {
      case id: VisionId => id
    }.get
    val held = base.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(worldDeck =
        c.commonCards.worldDeck.filterNot(_ == vision)),
      players = c.players.map(p => if (p.player != actor) p else
        p.copy(advisers = Vector(VisionState(vision, Orientation.FaceDown))))))
    val buried = run(held, Bury(BuryableCard.Vision(vision),
      PositionedLocation(Location.PlayArea(actor)))).toOption.get.state
    assertEquals(buried.game.current.commonCards.worldDeck.lastOption,
      Some(vision))
    assert(buried.game.current.players.find(_.player == actor).get
      .advisers.isEmpty)
  }

  test("Bury.standard is the discard's returns followed by the bury") {
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
  }

  test("a buried site denizen returns its favor to the bank and its secrets " +
      "to the acting player facedown") {
    val siteId = current.map.inPlay.head
    val denizen = current.commonCards.worldDeck.collectFirst {
      case id: DenizenId => id }.get
    val suit = catalog.suitOf(denizen).get
    val loaded = base.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(worldDeck =
        c.commonCards.worldDeck.filterNot(_ == denizen)),
      map = c.map.copy(sites = c.map.sites.updated(siteId,
        c.map.sites(siteId).copy(denizens = Vector(DenizenState(denizen,
          Orientation.FaceUp, Tokens(2, 1))))))))
    val before = loaded.game.current.players.find(_.player == actor).get.board
    val result = run(loaded, Bury.standard(BuryableCard.Denizen(denizen),
      PositionedLocation(Location.Site(siteId)), Some(suit), 2, 1, actor): _*)
      .fold(error => fail(error.toString), _.state)
    val after = result.game.current.players.find(_.player == actor).get.board
    assertEquals(after.faceDownSecrets, before.faceDownSecrets + 1)
    assertEquals(result.banks.favor.getOrElse(suit, 0), loaded.banks.favor.getOrElse(suit, 0) + 2)
    assertEquals(result.game.current.commonCards.worldDeck.lastOption,
      Some(denizen))
  }

  test("a Give to the shared bank moves the favor out of the giver's hands, " +
      "which is how giving to Bandits is modelled") {
    val funded = base.updateCurrent(_.copy(players = current.players.map(p =>
      if (p.player == actor) p.copy(board = p.board.copy(favor = 2)) else p)))
    val gave = run(funded, Give(Piece.Favor(1), actor,
      Location.PlayArea(actor), Location.SharedBank)).toOption.get
    assertEquals(gave.state.game.current.players.find(_.player == actor).get
      .board.favor, 1)
  }
}
