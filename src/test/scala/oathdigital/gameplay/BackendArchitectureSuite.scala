package oathdigital.gameplay

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

import oathdigital.catalog.CatalogHandlerInventory
import oathdigital.gameplay.actions.{CampaignRules, RecoverRules}
import oathdigital.gameplay.setup.{FirstGameSetupRules, FirstGameSetupFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class BackendArchitectureSuite extends munit.FunSuite {
  test("shared transition helper applies ordered events and stops at failure") {
    val events = Vector(OathEvent.FirstGameCompleted, OathEvent.FirstGameCompleted)
    val violation = OathViolation.InvalidEventOrder("second event rejected")
    var applied = 0
    val result = GameplayTransition(OathState.NoGame, events,
      OathContinue.ReadyForFirstTurn(PlayerId("p1"))) { (state, _) =>
      applied += 1
      if (applied == 1) Right(state) else Left(violation)
    }
    assertEquals(result, Left(violation))
    assertEquals(applied, 2)
  }

  test("factual source index preserves declared handlers and hidden faces") {
    val OathState.Ready(ready) =
      FirstGameSetupFixture.execute(new FirstGameSetupRules(catalog))._1: @unchecked
    val facts = RuleSourceIndex.enumerate(catalog, ready)
    val printed = facts.collectFirst {
      case value @ IndexedRuleSource(RuleSourceRef.Site(id), _, _)
          if id == ready.game.current.map.inPlay.head => value
    }.get
    assertEquals(printed.handlerIds,
      catalog.sites.find(_.id == ready.game.current.map.inPlay.head).get.handlers)
    assert(facts.exists(_.face == RuleSourceFace.FaceDown))
    assertEquals(facts.map(f => f.source.stableKey -> f.handlerIds),
      RuleSourceIndex.enumerate(catalog, ready)
        .map(f => f.source.stableKey -> f.handlerIds))
  }

  test("central handler inventory equals the audited catalog vocabulary") {
    val expected = (catalog.denizens.flatMap(_.handlers) ++
      catalog.relics.flatMap(_.handlers) ++ catalog.legacies.flatMap(_.handlers) ++
      catalog.sites.flatMap(_.handlers) ++ catalog.edifices.flatMap(e =>
        e.intact.handlers ++ e.ruined.handlers)).distinct.sorted
    assertEquals(CatalogHandlerInventory.handlerIds(catalog), expected)
    assertEquals(CatalogHandlerInventory.fingerprint(catalog),
      "70b57be7a3a4751e81d5235e033fdb62d1773f1e90fa2354d1c275e3e9d12f97")
  }

  test("Recover and Campaign relevance is exact handler-ID data") {
    assert(RecoverRules.isRelevantHandler("edifice.e17.intact"))
    assert(!RecoverRules.isRelevantHandler("denizen.future-recover-text"))
    assert(CampaignRules.classify("relic.bag-of-siegeworks", catalog)
      .isInstanceOf[CampaignRules.HandlerSupport.Blocked])
    assertEquals(CampaignRules.classify("denizen.extra-provisions", catalog),
      CampaignRules.HandlerSupport.IrrelevantToBanditConquest)
  }

  test("gameplay production sources never infer mechanics from rulesText") {
    val root = Paths.get("src/main/scala/oathdigital/gameplay")
    val offenders = Files.walk(root).iterator.asScala.filter(path =>
      path.toString.endsWith(".scala") &&
        Files.readString(path).contains("rulesText")).map(_.toString).toVector
    assertEquals(offenders, Vector.empty)
  }
}
