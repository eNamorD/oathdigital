package oathdigital.application

import oathdigital.gameplay.EconomyFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.protocol.projection.CardDetailsProjection

/** A face-down adviser's card back is public: any player can tell a denizen
  * back from a Vision back without being told the card's identity. A viewer
  * who cannot identify the card must still be told which kind it is -- see
  * `GamePresentationProjector.hiddenCard`'s callers.
  */
class GamePresentationProjectorAdviserRedactionSuite extends munit.FunSuite {
  private val projector = new GamePresentationProjector(catalog)
  private val visionId = VisionId("vision:vision-of-conquest")
  private val denizenAdviser = DenizenState(EconomyFixture.matchingId,
    Orientation.FaceDown, Tokens.empty)
  private val visionAdviser = VisionState(visionId, Orientation.FaceDown)

  private def assertRedacted(details: Vector[CardDetailsProjection]): Unit = {
    assertEquals(details.map(_.cardKind), Vector("denizen", "vision"))
    assertEquals(details.map(_.name), Vector("Facedown denizen", "Facedown vision"))
    assert(details.forall(_.cardId == "hidden"))
    assert(details.forall(_.suit.isEmpty))
    assert(details.forall(_.rulesText.isEmpty))
    assert(details.forall(_.hidden))
  }

  test("playerBoards tells an unidentifying viewer the adviser's kind, not its identity") {
    val ready = EconomyFixture.act(advisers = Vector(denizenAdviser, visionAdviser))
    val actor = EconomyFixture.player(ready).player
    val other = ready.game.current.players.map(_.player).find(_ != actor).get
    val board = projector.playerBoards(ready, Some(other))
      .find(_.playerId == actor.value).get
    assertRedacted(board.advisers)
  }

  test("setupPlayerBoards tells every viewer the adviser's kind, not its identity") {
    val ready = EconomyFixture.act(advisers = Vector(denizenAdviser, visionAdviser))
    val actor = EconomyFixture.player(ready).player
    val material = FirstGameSetupMaterial(ready.game.current.players,
      ready.game.current.map, ready.game.current.commonCards,
      ready.game.current.banners, ready.game.current.tracks, ready.banks.favor,
      ready.game.current.temporaryHands)
    val board = projector.setupPlayerBoards(material)
      .find(_.playerId == actor.value).get
    assertRedacted(board.advisers)
  }
}
