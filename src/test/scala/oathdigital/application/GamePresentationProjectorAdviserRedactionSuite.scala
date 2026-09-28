package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.protocol.projection.CardDetailsProjection

/** A face-down adviser's card back is public: any player can tell a denizen
  * back from a Vision back without being told the card's identity. A viewer
  * who cannot identify the card must still be told which kind it is -- see
  * `GamePresentationProjector.hiddenCard`'s callers.
  */
class GamePresentationProjectorAdviserRedactionSuite extends munit.FunSuite:
  private val projector = new GamePresentationProjector(catalog)
  private val visionId = VisionId("vision:vision-of-conquest")

  private def assertRedacted(details: Vector[CardDetailsProjection]): Unit =
    assertEquals(details.map(_.cardKind), Vector("denizen", "vision"))
    assertEquals(details.map(_.name), Vector("Facedown denizen", "Facedown vision"))
    assert(details.forall(_.cardId == "hidden"))
    assert(details.forall(_.suit.isEmpty))
    assert(details.forall(_.rulesText.isEmpty))
    assert(details.forall(_.hidden))

  test("playerBoards tells an unidentifying viewer the adviser's kind, not its identity"):
    val ready = Table.start
      .adviser(p1, "Magician's Code", facedown = true)
      .adviser(p1, visionId, facedown = true)
      .ready
    val board = projector.playerBoards(ready, Some(p2))
      .find(_.playerId == p1.value).get
    assertRedacted(board.advisers)

  test("setupPlayerBoards tells every viewer the adviser's kind, not its identity"):
    val ready = Table.start
      .adviser(p1, "Magician's Code", facedown = true)
      .adviser(p1, visionId, facedown = true)
      .ready
    val material = FirstGameSetupMaterial(ready.game.current.players,
      ready.game.current.map, ready.game.current.commonCards,
      ready.game.current.banners, ready.game.current.tracks, ready.banks.favor,
      ready.game.current.temporaryHands)
    val board = projector.setupPlayerBoards(material)
      .find(_.playerId == p1.value).get
    assertRedacted(board.advisers)

  test("playerBoards shows the tokens resting on the owner's faceup adviser"):
    val ready = Table.start.adviser(p1, "Magician's Code")
      .tokens("Magician's Code", favor = 1, secrets = 2).ready
    val board = projector.playerBoards(ready, Some(p1))
      .find(_.playerId == p1.value).get
    assertEquals(board.advisers.map(card => (card.favor, card.secrets)),
      Vector((1, 2)))
