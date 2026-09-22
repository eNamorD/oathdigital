package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.{EdificeId, EdificeSide, EdificeState, Tokens, VisionId}

/** Card presentation must echo the authoritative printed text, independent
  * of which plan factory derived the game. */
class GamePresentationProjectorPrintedFacesSuite extends munit.FunSuite {
  private val projector = new GamePresentationProjector(catalog)

  test("every Vision card detail uses the authoritative printed presentation") {
    val ids = Vector("vision:vision-of-conquest", "vision:vision-of-sanctuary",
      "vision:vision-of-rebellion", "vision:vision-of-faith", "vision:conspiracy")
    ids.foreach { id =>
      val expected = oathdigital.protocol.projection.VisionCardPresentation.byId(id)
      val details = projector.cardDetails(VisionId(id), None, hidden = false)
      assertEquals(details.name, expected.name)
      assertEquals(details.rulesText, Some(expected.rulesText))
    }
  }

  test("edifice card details use intact and ruined catalog face text") {
    val definition = catalog.edifices.head
    val id = EdificeId(definition.id.value)
    Vector(EdificeSide.Intact -> definition.intact,
      EdificeSide.Ruined -> definition.ruined).foreach { case (side, face) =>
      val details = projector.edificeCardDetails(EdificeState(id, side, Tokens.empty))
      assertEquals(details.name, face.name)
      assertEquals(details.rulesText, Some(face.rulesText))
      assertEquals(details.restrictions, Some(side match {
        case EdificeSide.Intact => "locked"
        case EdificeSide.Ruined => "unrestricted"
      }))
    }
  }
}
