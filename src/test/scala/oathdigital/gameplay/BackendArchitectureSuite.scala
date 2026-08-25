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

  test("factual source index enumerates every source category deterministically") {
    val OathState.Ready(ready) =
      FirstGameSetupFixture.execute(new FirstGameSetupRules(catalog))._1: @unchecked
    val facts = RuleSourceIndex.enumerate(catalog, ready)
    val printed = facts.collectFirst {
      case value @ IndexedRuleSource(RuleSourceRef.Site(id), _, _, _)
          if id == ready.game.current.map.inPlay.head => value
    }.get
    assertEquals(printed.handlerIds,
      catalog.sites.find(_.id == ready.game.current.map.inPlay.head).get.handlers)
    assert(facts.exists(_.face == RuleSourceFace.FaceDown))
    assert(facts.exists(_.source.isInstanceOf[RuleSourceRef.Edifice]))
    assert(facts.exists(_.source.isInstanceOf[RuleSourceRef.Adviser]))
    assertEquals(facts, RuleSourceIndex.enumerate(catalog, ready))
    assertEquals(facts.map(_.source.stableKey).distinct.size, facts.size)
  }

  test("site relics retain site identity, orientation, and declared handlers") {
    val OathState.Ready(ready) =
      FirstGameSetupFixture.execute(new FirstGameSetupRules(catalog))._1: @unchecked
    val (siteId, relic) = ready.game.current.map.inPlay.iterator.flatMap(id =>
      ready.game.current.map.sites(id).relics.headOption.map(id -> _)).next()
    val indexed = RuleSourceIndex.enumerate(catalog, ready).find(
      _.source == RuleSourceRef.SiteRelic(siteId, relic.id)).get
    assertEquals(indexed.source.stableKey,
      s"site-relic:${siteId.value}:${relic.id.value}")
    assertEquals(indexed.handlerIds,
      catalog.relics.find(_.id.value == relic.id.value).get.handlers)
    assertEquals(indexed.face, relic.orientation match {
      case Orientation.FaceUp => RuleSourceFace.FaceUp
      case Orientation.FaceDown => RuleSourceFace.FaceDown
    })
  }

  test("both banners expose faces, holdings, and exact synthetic handlers") {
    val OathState.Ready(base) =
      FirstGameSetupFixture.execute(new FirstGameSetupRules(catalog))._1: @unchecked
    val current = base.game.current
    val changed = base.copy(game = base.game.copy(current = current.copy(banners =
      BannersState(
        PeoplesFavorState(PeoplesFavorFace.GrandCouncil, Some(PlayerId("p1")), 3),
        DarkestSecretState(DarkestSecretFace.Festival, Some(PlayerId("p2")), 2)))))
    val banners = RuleSourceIndex.enumerate(catalog, changed).filter(
      _.source.isInstanceOf[RuleSourceRef.Banner])
    assertEquals(banners.map(_.source.stableKey),
      Vector("banner:peoples-favor", "banner:darkest-secret"))
    assertEquals(banners.map(_.face),
      Vector(RuleSourceFace.GrandCouncil, RuleSourceFace.Festival))
    assertEquals(banners.map(_.state), Vector(
      RuleSourceState.Banner(Some(PlayerId("p1")), 3),
      RuleSourceState.Banner(Some(PlayerId("p2")), 2)))
    assertEquals(banners.map(_.handlerIds), Vector(
      Vector("banner.peoples-favor.grand-council"),
      Vector("banner.darkest-secret.festival")))
  }

  test("all six Foundations expose ordered identities, faces, and state") {
    val OathState.Ready(base) =
      FirstGameSetupFixture.execute(new FirstGameSetupRules(catalog))._1: @unchecked
    val altered = base.copy(game = base.game.copy(campaign =
      base.game.campaign.copy(foundations = base.game.campaign.foundations.updated(
        FoundationNumber.III, FoundationState(FoundationFace.Altered,
          Set(LegacyId("L23"), LegacyId("L01")))))))
    val foundations = RuleSourceIndex.enumerate(catalog, altered).filter(
      _.source.isInstanceOf[RuleSourceRef.Foundation])
    assertEquals(foundations.map(_.source.stableKey),
      FoundationNumber.all.map(number => s"foundation:${number.value}"))
    assertEquals(foundations.map(_.face), Vector(
      RuleSourceFace.Normal, RuleSourceFace.Normal, RuleSourceFace.Altered,
      RuleSourceFace.Normal, RuleSourceFace.Normal, RuleSourceFace.Normal))
    assertEquals(foundations(2).handlerIds, Vector("foundation.altered"))
    assertEquals(foundations(2).state, RuleSourceState.Foundation(
      Vector(LegacyId("L01"), LegacyId("L23"))))
    assert(foundations.zipWithIndex.filterNot(_._2 == 2).forall {
      case (source, _) => source.handlerIds.isEmpty &&
        source.state == RuleSourceState.Foundation(Vector.empty)
    })
  }

  test("legacy inventory remains declared and lineage-qualified") {
    val OathState.Ready(base) =
      FirstGameSetupFixture.execute(new FirstGameSetupRules(catalog))._1: @unchecked
    val lineageId = base.game.campaign.lineages.keys.toVector.sortBy(_.value).head
    val legacyDefinition = catalog.legacies.head
    val legacy = LegacyState(LegacyId(legacyDefinition.id.value), active = false)
    val lineage = base.game.campaign.lineages(lineageId)
    val changed = base.copy(game = base.game.copy(campaign =
      base.game.campaign.copy(lineages = base.game.campaign.lineages.updated(
        lineageId, lineage.copy(legacies = Vector(legacy))))))
    val indexed = RuleSourceIndex.enumerate(catalog, changed).find(
      _.source == RuleSourceRef.Legacy(lineageId, legacy.id)).get
    assertEquals(indexed.source.stableKey,
      s"legacy:${lineageId.value}:${legacy.id.value}")
    assertEquals(indexed.handlerIds, legacyDefinition.handlers)
    assertEquals(indexed.face, RuleSourceFace.Inactive)
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

  test("application never imports server or serialization layers") {
    val root = Paths.get("src/main/scala/oathdigital/application")
    val forbidden = Vector("import oathdigital.server", "import oathdigital.serialization")
    val offenders = Files.walk(root).iterator.asScala.filter(path =>
      path.toString.endsWith(".scala") && forbidden.exists(
        Files.readString(path).contains)).map(_.toString).toVector
    assertEquals(offenders, Vector.empty)
  }

  test("shared command protocol is compiled by both configured runtimes") {
    val build = Files.readString(Paths.get("build.sbt"))
    assert(build.contains("shared\" / \"src\" / \"main\" / \"scala"))
    assert(build.contains("shared\" / \"src\" / \"test\" / \"scala"))
    assert(Files.exists(Paths.get(
      "frontend/target/scala-2.13/test-classes/oathdigital/protocol/CommandProtocolSuite.class")) ||
      Files.exists(Paths.get("shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala")))
  }
}
