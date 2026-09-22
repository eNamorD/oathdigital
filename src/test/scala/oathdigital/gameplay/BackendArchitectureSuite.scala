package oathdigital.gameplay

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

import oathdigital.catalog.CatalogHandlerInventory
import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powers.{ReviewedPowerCatalog, ReviewedPowerFacts,
  ReviewedPowerInspector}
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class BackendArchitectureSuite extends munit.FunSuite {
  test("shared transition helper applies ordered events and stops at failure") {
    val events = Vector(OathEvent.BanditsRefilled(Vector.empty),
      OathEvent.BanditsRefilled(Vector.empty))
    val violation = OathViolation.InvalidEventOrder("second event rejected")
    var applied = 0
    val result = GameplayTransition(OathState.NoGame, events,
      OathContinue.AwaitingWakeAction(PlayerId("p1"))) { (state, _) =>
      applied += 1
      if (applied == 1) Right(state) else Left(violation)
    }
    assertEquals(result, Left(violation))
    assertEquals(applied, 2)
  }

  test("factual source index enumerates every source category deterministically") {
    val ready = FirstGameSetupFixture.initialReady
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
    val ready = FirstGameSetupFixture.initialReady
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
    val base = FirstGameSetupFixture.initialReady
    val actor = base.game.current.turn.activePlayer
    val (siteId, relic) = base.game.current.map.inPlay.iterator.flatMap(id =>
      base.game.current.map.sites(id).relics.headOption.map(id -> _)).next()
    val players = base.game.current.players.map(player =>
      if (player.player == actor) player.copy(pawnSite = Some(siteId)) else player)
    val site = base.game.current.map.sites(siteId)
    val faceup = relic.copy(orientation = Orientation.FaceUp)
    val changed = base.updateCurrent(_.copy(
      players = players, map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(siteId, site.copy(relics =
          faceup +: site.relics.tail)))))
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
    val base = FirstGameSetupFixture.initialReady
    val changed = base.updateCurrent(_.copy(banners =
      BannersState(
        PeoplesFavorState(PeoplesFavorFace.GrandCouncil, Some(PlayerId("p1")), 3),
        DarkestSecretState(DarkestSecretFace.Festival, Some(PlayerId("p2")), 2))))
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
    val base = FirstGameSetupFixture.initialReady
    val altered = base.updateCampaign(_.copy(foundations = base.game.campaign.foundations.updated(
        FoundationNumber.III, FoundationState(FoundationFace.Altered,
          Set(LegacyId("L23"), LegacyId("L01"))))))
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
    val base = FirstGameSetupFixture.initialReady
    val lineageId = base.game.campaign.lineages.keys.toVector.sortBy(_.value).head
    val legacyDefinition = catalog.legacies.head
    val legacy = LegacyState(LegacyId(legacyDefinition.id.value), active = false)
    val lineage = base.game.campaign.lineages(lineageId)
    val changed = base.updateCampaign(_.copy(lineages = base.game.campaign.lineages.updated(
        lineageId, lineage.copy(legacies = Vector(legacy)))))
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

  test("Recover registry uses exact power-ID data") {
    val registry = ReviewedPowerCatalog.registry(catalog).toOption.get
    assert(registry.lookup(PowerId("edifice.e17.intact")).nonEmpty)
    assert(registry.lookup(PowerId("denizen.future-recover-text")).isEmpty)
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
    // Post-cutover (Task 9b): the legacy `Recover.scala`/
    // `RecoverPowerIntegration.scala` are gone, so this guard now asserts
    // the property they used to stand in for directly -- the application
    // layer, the rules-dispatch layer, and the Recover action module itself
    // route every Recover command generically and never learn Catacombs'
    // name -- while the walker's typed contribution is the one place that
    // does.
    val contribution = Paths.get("src/main/scala/oathdigital/gameplay/" +
      "powers/recover/CatacombsContribution.scala")
    assert(Files.exists(contribution), s"$contribution must exist")
    Vector(
      Paths.get("src/main/scala/oathdigital/application/GameApplicationService.scala"),
      Paths.get("src/main/scala/oathdigital/gameplay/OathRules.scala"),
      Paths.get("src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala")
    ).foreach { path =>
      assert(!Files.readString(path).toLowerCase.contains("catacombs"),
        s"$path must use the typed Recover power boundary")
    }
  }

  test("a walker power imports no engine, and the engine never learns its " +
      "name") {
    // Two architectural boundaries keep the walker generic: a power sees only
    // the `Operation`/contribution vocabulary (`gameplay.operations`,
    // `gameplay.powerresolver`), never `gameplay.walker` itself; and
    // symmetrically the engine (`gameplay/walker`, `gameplay/operations`)
    // never names a specific power. Both are hard properties -- violating
    // either means the engine and its powers have grown a direct dependency,
    // which is the failure this whole seam exists to prevent.
    //
    // The spec's ≤50-line power-authoring bar is NOT asserted here. It is a
    // design guideline, not a specification: a power that needs 55 lines to
    // state its rule honestly should be allowed to, and the reviewer is a
    // better judge of that than a line count. It was enforced mechanically
    // until 2026-09-09, and the enforcement cost roughly a hundred lines of
    // hand-rolled brace matching that had to know about comments, string
    // literals and nesting -- machinery that silently missed a power declared
    // inside a family object, and with it the engine-name check that actually
    // matters. Deleting the size assertion removes the need to parse Scala
    // here at all.
    //
    // Power names therefore come from a backward search: for each
    // `extends ContributingPower` or `PhasePower`, the nearest preceding declaration
    // identifier, with a trailing `Contribution` stripped. That works at any
    // nesting depth without tracking braces. Limits: it reads text, so an
    // `extends ContributingPower` inside a comment counts (the count
    // assertion below turns that into a loud failure, not a silent miss); a
    // trailing `Power` is deliberately NOT stripped, since a `TravelPower`
    // class would strip to `Travel`, a word the engine says constantly, and a
    // guard that fires falsely gets deleted rather than fixed; and the engine
    // scan is a lowercase substring match, so rename a power that collides
    // with unrelated engine text rather than loosening the scan.
    val powersRoot = Paths.get("src/main/scala/oathdigital/gameplay/powers")
    val declaresPower = "(?:extends|with)\\s+(?:ContributingPower|PhasePower)\\b".r
    val declaration = "(?:class|object|trait)\\s+([A-Za-z0-9_]+)".r
    val contributionStream = Files.walk(powersRoot)
    val contributions =
      try contributionStream.iterator.asScala.filter(path =>
        path.toString.endsWith(".scala") &&
          declaresPower.findFirstIn(Files.readString(path)).isDefined)
        .toVector
      finally contributionStream.close()
    assert(contributions.nonEmpty,
      s"expected at least one ContributingPower under $powersRoot")

    val engineImporting = contributions.filter(path =>
      Files.readString(path).contains("import oathdigital.gameplay.walker"))
      .map(_.toString).sorted
    assertEquals(engineImporting, Vector.empty,
      "a power must import no part of the walker engine")

    // One name per `extends ContributingPower`, or the file fails: a power
    // whose name cannot be read is a power the engine scan below would skip.
    val powerNames = contributions.flatMap { path =>
      val source = Files.readString(path)
      val declared = declaresPower.findAllMatchIn(source).toVector
      val named = declared.flatMap(hit =>
        declaration.findAllMatchIn(source.substring(0, hit.start)).toVector
          .lastOption.map(_.group(1)))
      assertEquals(named.size, declared.size, s"$path declares " +
        s"${declared.size} ContributingPower(s) but only ${named.size} could " +
        "be traced back to a declaration name; the engine-name scan would " +
        "skip the rest")
      named.map(_.stripSuffix("Contribution"))
    }.distinct
    assert(powerNames.forall(_.length >= 4), "a power name is too short to " +
      s"scan for safely; rename the power. Names were $powerNames")

    val engineRoots = Vector(
      Paths.get("src/main/scala/oathdigital/gameplay/walker"),
      Paths.get("src/main/scala/oathdigital/gameplay/operations"))
    val offenders = engineRoots.flatMap { root =>
      val stream = Files.walk(root)
      try stream.iterator.asScala.filter(path =>
        path.toString.endsWith(".scala")).flatMap { path =>
        val source = Files.readString(path).toLowerCase
        powerNames.filter(name => source.contains(name.toLowerCase))
          .map(name => s"$path names power $name")
      }.toVector
      finally stream.close()
    }.sorted
    assertEquals(offenders, Vector.empty)
  }

  test("wire decoders read strings through the validating helpers") {
    // Untrusted client JSON reaches the engine through `protocol`'s decoders,
    // and the id types it feeds (`SiteId`, `DecisionId`, `RelicId`, ...)
    // validate with a THROWING `require(value.trim.nonEmpty)`. Nothing today
    // can trip that, because every decoded string goes through
    // `CommandJsonSupport.string`/`strings`, which reject a blank with a
    // typed `InvalidValue` first -- so the constructors' `require` is
    // unreachable rather than merely unexercised.
    //
    // That safety is a property of the decoders, not of the id types, and it
    // is one `value("id").str` away from being lost: the raw accessor throws
    // on a non-string and yields "" for a blank without complaint, handing
    // the mapper a value the constructor then rejects with an exception
    // escaping as a 500 instead of a typed 400. This guard pins the property
    // for all ~48 id constructions in `GameIntentMapper` at once, and for
    // every one added later, rather than defensively parsing at each site.
    //
    // Limits worth knowing: it catches the raw accessor, which is the
    // reachable footgun, not every conceivable bypass -- an inline
    // `case ujson.Str(v) =>` without a blank guard would still slip past.
    // And it says nothing about validation STRICTER than non-blank: `PowerId`
    // carries a regex, so a well-formed non-blank string can still fail it,
    // which is why that type ships a `fromValue` safe parse. Any future id
    // validating beyond non-blank needs the same.
    //
    // Like this suite's other file-content guards, it scans text, so a `.str`
    // written inside a comment trips it too -- reword the comment rather than
    // loosening the pattern.
    val protocolRoot = Paths.get("shared/src/main/scala/oathdigital/protocol")
    val stream = Files.walk(protocolRoot)
    val sources =
      try stream.iterator.asScala
        .filter(_.toString.endsWith(".scala")).toVector
      finally stream.close()
    assert(sources.nonEmpty, s"expected decoder sources under $protocolRoot")

    val rawAccessor = """\.str\b""".r
    val offenders = sources.filter(path =>
      rawAccessor.findFirstIn(Files.readString(path)).isDefined)
      .map(_.toString).sorted
    assertEquals(offenders, Vector.empty,
      "decode wire strings with CommandJsonSupport.string/strings, which " +
        "reject blanks with a typed error, not the raw .str accessor")
  }

  test("generic power operations are not independently replayable events") {
    val protocol = Files.readString(Paths.get(
      "src/main/scala/oathdigital/model/GameEventProtocol.scala"))
    val aggregate = Files.readString(Paths.get(
      "src/main/scala/oathdigital/gameplay/OathRules.scala"))
    val codec = Files.readString(Paths.get(
      "src/main/scala/oathdigital/serialization/ActionEventCodec.scala"))
    val walkerEvents = Files.readString(Paths.get(
      "src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala"))
    Vector("CostsPaid", "RelicPlacedAtSite").foreach { name =>
      assert(!protocol.contains(s"case class $name"))
      assert(!aggregate.contains(s"case event: $name"))
      assert(!codec.contains(s"case _: $name"))
      assert(!walkerEvents.contains(s"case class $name"))
    }
    // Post-cutover (Task 9b): Catacombs no longer has its own
    // `CatacombsResolved` case class in `GameEventProtocol.scala` -- it
    // records through the walker's own aggregate event instead. The
    // property this test guards (granular operations never become their own
    // replayable event) now rests on `WalkerStepRecorded`.
    assert(walkerEvents.contains("case class WalkerStepRecorded"))
  }

  test("individual power definitions use factories instead of handler subclasses") {
    val root = Paths.get("src/main/scala/oathdigital/gameplay/powers")
    val powerDefinition = "\\bobject\\s+[A-Za-z0-9_]+\\s+extends\\s+Power\\b".r
    val bespokeHandler = "\\bextends\\s+[A-Za-z0-9_]*PowerHandler\\b".r
    val offenders = Files.walk(root).iterator.asScala.filter(
      _.toString.endsWith(".scala")).flatMap { path =>
      val source = Files.readString(path)
      Option.when(powerDefinition.findFirstIn(source).nonEmpty &&
        bespokeHandler.findFirstIn(source).nonEmpty)(path.toString)
    }.toVector
    assertEquals(offenders, Vector.empty)
  }

  test("Rest registry cannot own procedure orchestration or state mutation") {
    val source = Files.readString(Paths.get(
      "src/main/scala/oathdigital/gameplay/powers/RestPowers.scala"))
    Vector("def begin", "def evolve", "OathTransition", "PendingProcedure",
      ".copy(").foreach { forbidden =>
      assert(!source.contains(forbidden),
        s"RestPowers must leave '$forbidden' to typed handlers/integration")
    }
  }

  test("procedure power inventories use named Power objects, not raw ID tables") {
    // Aimed at the legacy `Power` inventories (`ActionPowers`, `SearchPowers`,
    // ...), where a collection literal meant an id table standing in for named
    // power objects. A `ContributingPower` is not one of those: it declares
    // `contributions: Map[PowerWindow, Vector[Contribution]]`, so the scan
    // read an ordinary field of the walker seam as the smell it hunts. That
    // misfire was already being paid for -- `TravelSitePowers.scala` spells
    // its contribution map `Map.empty.updated(...)` for no reason but this
    // guard -- and batch-1 Task 6 would have paid it again. Contribution
    // files are therefore skipped by what they declare, not by filename.
    val root = Paths.get("src/main/scala/oathdigital/gameplay/powers")
    val declaresContribution = "(?:extends|with)\\s+ContributingPower\\b".r
    val scanned = Files.walk(root).iterator.asScala.filter(path =>
      path.getFileName.toString.endsWith("Powers.scala") &&
        declaresContribution.findFirstIn(Files.readString(path)).isEmpty)
      .toVector
    // The exemption narrows the guard; it must not empty it. A refactor that
    // left nothing scanned would pass this test while checking nothing.
    assert(scanned.size >= 5,
      s"the inventory scan covers too few files to be meaningful: $scanned")
    val offenders = scanned.filter { path =>
      val text = Files.readString(path)
      text.contains("Set(") || text.contains("Map(") ||
        text.contains("handlerId match")
    }.map(_.toString)
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

  test("migrated modules never directly edit owned material state") {
    // Phase 9 boundary: migrated action/phase/power modules express every
    // owned-material change (card vectors, site cards/tokens, banner custody
    // and resources, bank favor, hand keys, empty-key drops) as core
    // operations. The only sanctioned direct writes are the Conspiracy boxing
    // removals, which run after the operation batch validates and whose nearest
    // preceding comment line is the strict `// executor bypass:` sentinel.
    val migrated = Vector(
      Paths.get("src/main/scala/oathdigital/gameplay/actions"),
      Paths.get("src/main/scala/oathdigital/gameplay/phases"),
      Paths.get("src/main/scala/oathdigital/gameplay/powers"))
    val files = migrated.flatMap { root =>
      val stream = Files.walk(root)
      try stream.iterator.asScala.filter(_.toString.endsWith(".scala")).toVector
      finally stream.close()
    } ++
      Vector(Paths.get("src/main/scala/oathdigital/gameplay/StateBasedEvaluation.scala"))
    val markers = Vector(
      "advisers.filterNot(_.id == ",
      "copy(advisers =",
      "temporaryHands - ",
      "temporaryHands.updated(",
      "temporaryHands.removed(",
      "filterNot(_._2.isEmpty)",
      "tokens.copy(favor = ",
      "tokens.copy(secrets = ",
      "banks.favor.updated",
      "denizens = denizens.map",
      "relics = relics.zipWithIndex.map",
      "map.copy(sites = ")
    val sentinel = "// executor bypass:"
    val offenders = files.flatMap { path =>
      val lines = Files.readAllLines(path).asScala
      lines.zipWithIndex.flatMap { case (line, index) =>
        markers.find(line.contains).flatMap { marker =>
          val precedingComment = lines.take(index).reverseIterator
            .find(_.trim.startsWith("//"))
          val documented = precedingComment.exists(_.trim.startsWith(sentinel))
          Option.unless(documented)(
            s"${path.toString}:${index + 1}: direct owned-material write " +
              s"'$marker' lacks the '$sentinel' comment immediately above it")
        }
      }
    }.sorted
    assertEquals(offenders, Vector.empty)
  }
}
