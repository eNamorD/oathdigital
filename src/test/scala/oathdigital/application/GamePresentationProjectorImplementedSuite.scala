package oathdigital.application

import oathdigital.catalog.{CardRestrictions, DefinitionId, DenizenDefinition}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** `implemented` on a projected [[oathdigital.protocol.projection.CardDetailsProjection]]
  * must agree across the game's three separate power catalogs: a
  * `ReviewedPowerCatalog` handler carries the flag explicitly, while a
  * `WalkerPowerCatalog`/`PhasePowerCatalog` power carries no flag at all --
  * for those, being wired in for this catalog at all is what implemented
  * means. Dazzle, Revelation, and Catacombs are real denizens covering one
  * case each; picking them by their declared power id keeps this suite
  * honest to the production catalog instead of a synthetic stand-in.
  */
class GamePresentationProjectorImplementedSuite extends munit.FunSuite {
  private val projector = new GamePresentationProjector(catalog)

  private def denizenWith(powerId: String): DenizenId =
    DenizenId(catalog.denizens.find(_.powers.exists(_.id.value == powerId))
      .getOrElse(fail(s"no denizen declares $powerId")).id.value)

  test("a denizen whose power is reviewed and marked implemented reads as implemented") {
    val dazzle = denizenWith("denizen.dazzle")
    assert(projector.cardDetails(dazzle, None, hidden = false).implemented)
  }

  test("a denizen whose power is reviewed but not yet built reads as not implemented") {
    val revelation = denizenWith("denizen.revelation")
    assert(!projector.cardDetails(revelation, None, hidden = false).implemented)
  }

  test("a denizen whose power runs only through the walker catalog reads as implemented") {
    val catacombs = denizenWith("denizen.catacombs")
    assert(projector.cardDetails(catacombs, None, hidden = false).implemented)
  }

  test("a denizen with no declared power reads as not implemented") {
    // Every production denizen prints at least one power, so this shape --
    // a card with none at all -- is exercised with a card grafted onto the
    // real catalog rather than one already in it.
    val blank = DenizenDefinition(DefinitionId("test-blank"), "Blank",
      catalog.denizens.head.suit, CardRestrictions.Unrestricted, Vector.empty)
    val blankProjector = new GamePresentationProjector(
      catalog.copy(denizens = catalog.denizens :+ blank))
    assert(!blankProjector.cardDetails(DenizenId("test-blank"), None,
      hidden = false).implemented)
  }

  test("an edifice face whose power is reviewed but not yet built reads as not implemented") {
    val e13 = EdificeId(catalog.edifices.find(
      _.ruined.powers.exists(_.id.value == "edifice.e13.ruined"))
      .getOrElse(fail("no edifice declares edifice.e13.ruined")).id.value)
    val ruined = EdificeState(e13, EdificeSide.Ruined, Tokens.empty)
    assert(!projector.edificeCardDetails(ruined).implemented)
  }
}
