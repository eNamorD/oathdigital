package oathdigital.gameplay

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class BackendArchitectureSuite extends munit.FunSuite:
  test("gameplay production sources never infer mechanics from rulesText"):
    val root = Paths.get("src/main/scala/oathdigital/gameplay")
    val offenders = Files.walk(root).iterator.asScala.filter(path =>
      path.toString.endsWith(".scala") &&
        Files.readString(path).contains("rulesText")).map(_.toString).toVector
    assertEquals(offenders, Vector.empty)

  test("Catacombs mechanics remain owned by Recover powers"):
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

  test("a walker power imports no engine, and the engine never learns its " +
      "name"):
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

  test("wire decoders read strings through the validating helpers"):
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

  test("individual power definitions use factories instead of handler subclasses"):
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

  test("Rest registry cannot own procedure orchestration or state mutation"):
    val source = Files.readString(Paths.get(
      "src/main/scala/oathdigital/gameplay/powers/RestPowers.scala"))
    Vector("def begin", "def evolve", "OathTransition", "PendingProcedure",
      ".copy(").foreach { forbidden =>
      assert(!source.contains(forbidden),
        s"RestPowers must leave '$forbidden' to typed handlers/integration")
    }

  test("procedure power inventories use named Power objects, not raw ID tables"):
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

  test("application never imports server or serialization layers"):
    val root = Paths.get("src/main/scala/oathdigital/application")
    val forbidden = Vector("import oathdigital.server", "import oathdigital.persistence",
      "import oathdigital.serialization")
    val offenders = Files.walk(root).iterator.asScala.filter(path =>
      path.toString.endsWith(".scala") && forbidden.exists(
        Files.readString(path).contains)).map(_.toString).toVector
    assertEquals(offenders, Vector.empty)

  test("shared command protocol is compiled by both configured runtimes"):
    val build = Files.readString(Paths.get("build.sbt"))
    assert(build.contains("shared\" / \"src\" / \"main\" / \"scala"))
    assert(build.contains("shared\" / \"src\" / \"test\" / \"scala"))
    assert(Files.exists(Paths.get(
      "shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala")))

  test("client and server define no duplicate command intent DTO vocabulary"):
    val roots = Vector(Paths.get("src/main/scala"), Paths.get("frontend/src/main/scala"))
    val offenders = roots.flatMap(root => Files.walk(root).iterator.asScala)
      .filter(path => path.toString.endsWith(".scala") &&
        !path.endsWith("oathdigital/application/GameCommands.scala") &&
        Files.readAllLines(path).asScala.exists(_.matches(
          "\\s*(sealed trait|final case class) (GameIntent|GameCommand)(\\s|\\().*")))
      .map(_.toString).sorted
    assertEquals(offenders, Vector.empty)

  test("projection and bootstrap transport DTOs are defined only in shared protocol"):
    val roots = Vector(Paths.get("src/main/scala"), Paths.get("frontend/src/main/scala"))
    val forbidden = Set("GameProjection", "SetupPlayerProjection",
      "CardDetailsProjection", "PendingCardDecisionProjection",
      "PlayerBoardProjection", "FirstGameBootstrapRequest",
      "BootstrapParticipantRequest")
    val definition = "\\s*final case class ([A-Za-z0-9_]+).*".r
    val offenders = roots.flatMap(root => Files.walk(root).iterator.asScala)
      .filter(_.toString.endsWith(".scala")).flatMap { path =>
        Files.readAllLines(path).asScala.collect:
          case definition(name) if forbidden(name) => s"$path:$name"
      }.sorted
    assertEquals(offenders, Vector.empty)
    assert(Files.exists(Paths.get(
      "shared/src/main/scala/oathdigital/protocol/projection/GameProjectionDto.scala")))
    assert(Files.exists(Paths.get(
      "shared/src/main/scala/oathdigital/protocol/BootstrapProtocolCodec.scala")))

  test("all production Scala files stay bounded"):
    val roots = Vector(Paths.get("src/main/scala"),
      Paths.get("frontend/src/main/scala"), Paths.get("shared/src/main/scala"))
    val oversized = roots.flatMap(root => Files.walk(root).iterator.asScala)
      .filter(_.toString.endsWith(".scala")).flatMap { path =>
        val lines = Files.readAllLines(path).size
        Option.when(lines > 800)(s"$path:$lines")
      }.sorted
    assertEquals(oversized, Vector.empty)

  test("inner production packages do not import outer adapters"):
    val constraints = Vector(
      Paths.get("src/main/scala/oathdigital/model") -> Vector(
        "application", "gameplay", "persistence", "serialization", "server"),
      Paths.get("src/main/scala/oathdigital/gameplay") -> Vector(
        "application", "persistence", "protocol",
        "serialization", "server"))
    val offenders = constraints.flatMap { case (root, packages) =>
      val forbidden = packages.map(name => s"import oathdigital.$name")
      Files.walk(root).iterator.asScala.filter(_.toString.endsWith(".scala"))
        .filter(path => forbidden.exists(Files.readString(path).contains))
        .map(_.toString)
    }.sorted
    assertEquals(offenders, Vector.empty)

  test("migrated modules never directly edit owned material state"):
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
