package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.model._

class ChronicleFirstGamePlanSuite extends munit.FunSuite {
  private val config = FirstGameBootstrapConfig(participants, PlayerId("p2"))

  private val atlasBox: Vector[StoredSite] = sites.map { siteId =>
    homelandEdifices.find(_._1 == siteId) match {
      case Some((_, edificeId)) => StoredSite(siteId, Vector(edificeId))
      case None => StoredSite(siteId)
    }
  }

  private val chronicle = Chronicle(atlasBox, worldDeck = denizens, relicDeck = relics)

  test("bridging the fixture's equivalent Chronicle reproduces its exact plan") {
    val built = ChronicleFirstGamePlan.build(catalog, chronicle, config).toOption.get
    assertEquals(built, plan)
  }

  test("the bridged plan still satisfies the unchanged setup machine") {
    val built = ChronicleFirstGamePlan.build(catalog, chronicle, config).toOption.get
    assert(new FirstGameSetupRules(catalog)
      .handle(OathState.NoGame, FirstGameSetupCommand.Begin(built)).isRight)
  }

  test("fewer than 8 atlas box sites is refused") {
    assertEquals(
      ChronicleFirstGamePlan.build(catalog, chronicle.copy(atlasBox = atlasBox.take(7)),
        config),
      Left(ChronicleBridgeFailure.TooFewAtlasSites(7)))
  }

  test("a Homeland site in play with no stored edifice is refused") {
    val homelandSite = homelandEdifices.head._1
    val stripped = atlasBox.map(stored =>
      if (stored.site == homelandSite) StoredSite(homelandSite) else stored)
    assertEquals(
      ChronicleFirstGamePlan.build(catalog, chronicle.copy(atlasBox = stripped), config),
      Left(ChronicleBridgeFailure.MissingHomelandEdifice(homelandSite)))
  }
}
