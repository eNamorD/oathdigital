package oathdigital.gameplay

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

import oathdigital.catalog.CatalogHandlerInventory
import oathdigital.gameplay.actions.CampaignRules
import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powers.{ReviewedPowerCatalog, ReviewedPowerFacts,
  ReviewedPowerInspector}
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
    assertEquals(indexed.powerIds,
      catalog.relics.find(_.id.value == relic.id.value).get.powers.map(power =>
        power.id))
    assertEquals(indexed.face, relic.orientation match {
      case Orientation.FaceUp => RuleSourceFace.FaceUp
      case Orientation.FaceDown => RuleSourceFace.FaceDown
    })
  }

  test("resolver treats a faceup relic at the actor pawn site as accessible") {
    val OathState.Ready(base) =
      FirstGameSetupFixture.execute(new FirstGameSetupRules(catalog))._1: @unchecked
    val actor = base.game.current.turn.activePlayer
    val (siteId, relic) = base.game.current.map.inPlay.iterator.flatMap(id =>
      base.game.current.map.sites(id).relics.headOption.map(id -> _)).next()
    val players = base.game.current.players.map(player =>
      if (player.player == actor) player.copy(pawnSite = Some(siteId)) else player)
    val site = base.game.current.map.sites(siteId)
    val faceup = relic.copy(orientation = Orientation.FaceUp)
    val changed = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = players, map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(siteId, site.copy(relics =
          faceup +: site.relics.tail))))))
    val source = RuleSourceRef.SiteRelic(siteId, relic.id)
    val indexed = IndexedRuleSource(source, Vector(PowerId("test.site-relic")),
      RuleSourceFace.FaceUp)
    val handler = new PowerHandler {
      val window = PowerWindow.RestStart
      val resolution = PowerResolution.PlayerSelected
      val implemented = true
      def inspect(context: PowerContext) = ReviewedPowerInspector.inspect(context)
    }
    val power = new Power {
      val id = PowerId("test.site-relic")
      val modifier = None
      val handlers = Vector(handler)
    }
    val result = new PowerResolver(PowerRegistry(power)).resolve(
      PowerWindow.RestStart, Vector(source -> Vector(power.id)),
      ReviewedPowerFacts(catalog, changed, actor, Map(source -> indexed))).toOption.get
    assertEquals(result.offered.map(_.source), Vector(source))
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
      "7e333f6b4bdd033e2c1e76c3b4f8889c7d44cb5325f8d7da32ba514291b154e2")
  }

  test("Recover registry and Campaign relevance use exact power-ID data") {
    val registry = ReviewedPowerCatalog.registry(catalog).toOption.get
    assert(registry.lookup(PowerId("edifice.e17.intact")).nonEmpty)
    assert(registry.lookup(PowerId("denizen.future-recover-text")).isEmpty)
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

  test("legacy central power shell cannot return") {
    val root = Paths.get("src/main/scala/oathdigital/gameplay")
    assert(!Files.exists(root.resolve("MajorActionPowerShell.scala")))
    val offenders = Files.walk(root).iterator.asScala.filter(path =>
      path.toString.endsWith(".scala") &&
        Files.readString(path).contains("object MajorActionPowerShell"))
      .map(_.toString).toVector
    assertEquals(offenders, Vector.empty)
  }

  test("Catacombs mechanics remain owned by Recover powers") {
    Vector(
      Paths.get("src/main/scala/oathdigital/gameplay/actions/Recover.scala"),
      Paths.get("src/main/scala/oathdigital/application/GameApplicationService.scala")
    ).foreach { path =>
      assert(!Files.readString(path).toLowerCase.contains("catacombs"),
        s"$path must use the typed Recover power boundary")
    }
  }

  test("procedure power inventories use named Power objects, not raw ID tables") {
    val root = Paths.get("src/main/scala/oathdigital/gameplay/powers")
    val offenders = Files.walk(root).iterator.asScala.filter(path =>
      path.getFileName.toString.endsWith("Powers.scala") && {
        val text = Files.readString(path)
        text.contains("Set(") || text.contains("Map(") ||
          text.contains("handlerId match")
      }).map(_.toString).toVector
    assertEquals(offenders, Vector.empty)
  }

  test("application never imports server or serialization layers") {
    val root = Paths.get("src/main/scala/oathdigital/application")
    val forbidden = Vector("import oathdigital.server", "import oathdigital.persistence",
      "import oathdigital.serialization")
    val offenders = Files.walk(root).iterator.asScala.filter(path =>
      path.toString.endsWith(".scala") && forbidden.exists(
        Files.readString(path).contains)).map(_.toString).toVector
    assertEquals(offenders, Vector.empty)
  }

  test("application projection collaborators stay bounded and layer-independent") {
    val root = Paths.get("src/main/scala/oathdigital/application")
    val projectionFiles = Files.walk(root).iterator.asScala.filter(path =>
      path.toString.endsWith(".scala") &&
        (path.getFileName.toString.contains("Projection") ||
          path.getFileName.toString.contains("Projector") ||
          path.getFileName.toString == "ScopedProjectionContext.scala")).toVector
    val forbidden = Vector("import oathdigital.server",
      "import oathdigital.persistence", "import oathdigital.serialization")
    val badImports = projectionFiles.filter(path => forbidden.exists(
      Files.readString(path).contains)).map(_.toString).sorted
    val oversized = projectionFiles.flatMap { path =>
      val lines = Files.readAllLines(path).size
      Option.when(lines > 800)(s"$path:$lines")
    }.sorted
    assertEquals(badImports, Vector.empty)
    assertEquals(oversized, Vector.empty)
    assert(projectionFiles.exists(_.getFileName.toString ==
      "ScopedProjectionContext.scala"))
  }

  test("shared command protocol is compiled by both configured runtimes") {
    val build = Files.readString(Paths.get("build.sbt"))
    assert(build.contains("shared\" / \"src\" / \"main\" / \"scala"))
    assert(build.contains("shared\" / \"src\" / \"test\" / \"scala"))
    assert(Files.exists(Paths.get(
      "shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala")))
  }

  test("client and server define no duplicate command intent DTO vocabulary") {
    val roots = Vector(Paths.get("src/main/scala"), Paths.get("frontend/src/main/scala"))
    val offenders = roots.flatMap(root => Files.walk(root).iterator.asScala)
      .filter(path => path.toString.endsWith(".scala") &&
        !path.endsWith("oathdigital/application/GameCommands.scala") &&
        Files.readAllLines(path).asScala.exists(_.matches(
          "\\s*(sealed trait|final case class) (GameIntent|GameCommand)(\\s|\\().*")))
      .map(_.toString).sorted
    assertEquals(offenders, Vector.empty)
  }

  test("projection and bootstrap transport DTOs are defined only in shared protocol") {
    val roots = Vector(Paths.get("src/main/scala"), Paths.get("frontend/src/main/scala"))
    val forbidden = Set("GameProjection", "SetupPlayerProjection",
      "CardDetailsProjection", "PendingCardDecisionProjection",
      "PlayerBoardProjection", "FirstGameBootstrapRequest",
      "BootstrapParticipantRequest")
    val definition = "\\s*final case class ([A-Za-z0-9_]+).*".r
    val offenders = roots.flatMap(root => Files.walk(root).iterator.asScala)
      .filter(_.toString.endsWith(".scala")).flatMap { path =>
        Files.readAllLines(path).asScala.collect {
          case definition(name) if forbidden(name) => s"$path:$name"
        }
      }.sorted
    assertEquals(offenders, Vector.empty)
    assert(Files.exists(Paths.get(
      "shared/src/main/scala/oathdigital/protocol/projection/GameProjectionDto.scala")))
    assert(Files.exists(Paths.get(
      "shared/src/main/scala/oathdigital/protocol/BootstrapProtocolCodec.scala")))
  }

  test("frontend production sources stay bounded and renderers remain isolated") {
    val root = Paths.get("frontend/src/main/scala/oathdigital/frontend")
    val sources = Files.walk(root).iterator.asScala
      .filter(_.toString.endsWith(".scala")).toVector
    val oversized = sources.flatMap { path =>
      val lines = Files.readAllLines(path).size
      Option.when(lines > 800)(s"$path:$lines")
    }.sorted
    val forbidden = Vector("import oathdigital.application",
      "import oathdigital.gameplay", "import oathdigital.server")
    val rendererViolations = sources.filter(path =>
      path.getFileName.toString.contains("Renderer") && forbidden.exists(
        Files.readString(path).contains)).map(_.toString).sorted
    assertEquals(oversized, Vector.empty)
    assertEquals(rendererViolations, Vector.empty)
  }

  test("all production Scala files stay bounded") {
    val roots = Vector(Paths.get("src/main/scala"),
      Paths.get("frontend/src/main/scala"), Paths.get("shared/src/main/scala"))
    val oversized = roots.flatMap(root => Files.walk(root).iterator.asScala)
      .filter(_.toString.endsWith(".scala")).flatMap { path =>
        val lines = Files.readAllLines(path).size
        Option.when(lines > 800)(s"$path:$lines")
      }.sorted
    assertEquals(oversized, Vector.empty)
  }

  test("inner production packages do not import outer adapters") {
    val constraints = Vector(
      Paths.get("src/main/scala/oathdigital/model") -> Vector(
        "application", "gameplay", "persistence", "serialization", "server"),
      Paths.get("src/main/scala/oathdigital/gameplay") -> Vector(
        "application", "persistence", "presentation", "protocol",
        "serialization", "server"))
    val offenders = constraints.flatMap { case (root, packages) =>
      val forbidden = packages.map(name => s"import oathdigital.$name")
      Files.walk(root).iterator.asScala.filter(_.toString.endsWith(".scala"))
        .filter(path => forbidden.exists(Files.readString(path).contains))
        .map(_.toString)
    }.sorted
    assertEquals(offenders, Vector.empty)
  }

  test("retired setup and browser-memory symbols do not return") {
    val roots = Vector(Paths.get("src/main/scala"),
      Paths.get("frontend/src/main/scala"), Paths.get("shared/src/main/scala"))
    val retired = Vector("oathdigital.setup", "SetupEventWire", "SetupState",
      "BeginSetup", "BrowserMemory")
    val offenders = roots.flatMap(root => Files.walk(root).iterator.asScala)
      .filter(_.toString.endsWith(".scala"))
      .filter(path => retired.exists(Files.readString(path).contains))
      .map(_.toString).sorted
    assertEquals(offenders, Vector.empty)
  }
}
