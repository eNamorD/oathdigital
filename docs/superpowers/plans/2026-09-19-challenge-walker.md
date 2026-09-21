# Challenge and Place Banner Resource on the Procedure Walker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run Challenge and Place Banner Resource on the procedure walker as two registry entries, with two new decision shapes (`ChooseMany`, `ChooseAmount`), and delete the legacy `Challenge` path.

**Architecture:** Each action is a walker tree of `Sequence`, `Decide`, `Branch`, `Repeat` and `BuildOps` in its own package beside the Economy, Search and Forge procedures. Banner and amount choices are `Decide`s. Every decision and every state-derived effect is built lazily, when the walk reaches it, from the board as it then stands. Two decision shapes join the sealed `DecisionQuery`/`DecisionAnswer` families with their codecs and generic frontend controls. The legacy commands, events, pending state, projection and panel are deleted after a differential parity test.

**Tech Stack:** Scala 2.13, sbt via `./sbtw`, munit, Scala.js frontend, `shared/` protocol module.

**Spec:** `docs/superpowers/specs/2026-09-19-challenge-walker-design.md` (read it first; this plan argues from it). Extends `docs/superpowers/specs/2026-09-18-economy-walker-design.md` and `docs/superpowers/specs/2026-09-10-declarative-walker-decisions-design.md`.

## Global Constraints

- Every decision is built from the board as it stands when it is asked; no `Branch` selection or decision query may read state that an earlier step of the same tree changes (spec, Goal and scope). The walker re-runs every enclosing `Branch.select` on each resume, so selections read only `pending.answered` plus facts no earlier step changes, and everything else is built inside a `BuildOps` or a lazily selected `Branch` (Global rule W below).
- The rest of the spec's gates: drop the altered-Foundation, non-Exile and active-legacy gates and the handler-fingerprint gate; keep the active-face gate (`UnsupportedBannerState`); use `PowerRuntime.requireAudited`.
- Production source files stay under 800 lines (`BackendArchitectureSuite`). No lowercase power name appears in walker or operations sources. Powers do not import `gameplay.walker`.
- Persisted text (code, comments, docs, commits) is normal English. Commit trailer: `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.
- Full gate: `./sbtw -no-colors "test" "frontend/test" "frontend/fastLinkJS"`. Pre-release history compatibility is not required.
- Service-level tests use `new OathRules(catalog)` (empty walker power catalog) unless they need automatic powers; then pass `walkerPowerCatalog = WalkerPowerCatalog.default(catalog)`.

**Global rule W (walker resume).** On every command the walker rebuilds the tree and re-runs the `select` of every `Branch` on the path to the resume point with live state, then skips children before the resume index without walking them. So: (1) a `Branch` that must sit above a state-dependent decision selects only that decision and builds its query inside the `select`, and is reached only before the state changes; (2) concrete `Move`s built in a selection may use only answered values; (3) anything derived from live state that an earlier step changes goes in a `BuildOps` closure, which runs once when reached and is skipped on resume.

## Deviations from the spec (this plan is the authority; Task 12 amends the spec)

- `ChooseMany` is well-formed only when `1 <= count < options.size`. A count equal to the option count is a forced answer, which `DecisionQueries.wellFormed` rejects for every other shape. Challenge never needs it (it parks only when secrets are fewer than tied sites).
- `OathContinue.AwaitingBannerDecision` is kept and reused as the walker continuation for both procedures' decisions, instead of being deleted.
- The `DecisionOption.Banner` projection changes: an unclaimed banner is presentable (label `Unclaimed <key>`), and the option carries the detail `Currently N resources`. This is how a build-time option gets its cost text; no model field is added. Cost text "1 Supply" is dropped from the banner options because the Supply is spent before the banner decision.
- The tree has three sibling `Branch`es after the Supply step (banner, amount, effects) so that each is selected lazily under rule W.
- A start control is offered when the same dry run a start performs succeeds (`WalkerSimulation.starts`), which covers Supply affordability without a second rule.

## File Structure

New production files:
- `src/main/scala/oathdigital/gameplay/actions/BannerRules.scala`: `BannerRules` moved out of `Challenge.scala` unchanged.
- `src/main/scala/oathdigital/gameplay/actions/challenge/ChallengeProcedure.scala`: Challenge tree, start gates, decision ids, banner legality.
- `src/main/scala/oathdigital/gameplay/actions/challenge/ChallengeRibbon.scala`: People's Favor and Wandering Flame ribbon steps.
- `src/main/scala/oathdigital/gameplay/actions/challenge/PlaceBannerResourceProcedure.scala`: Place Banner Resource tree.
- `frontend/src/main/scala/oathdigital/frontend/WalkerSelectionDraft.scala`: draft state for `choose-many` and `choose-amount`.
- `frontend/src/main/scala/oathdigital/frontend/WalkerSelectionPanels.scala`: the two generic panels.
- `frontend/src/main/scala/oathdigital/frontend/BannerControls.scala`: the two Act-phase start controls.

New test files: `ChallengeFixture.scala`, `ChallengeProcedureSuite.scala`, `PlaceBannerResourceProcedureSuite.scala`, `ChallengeParitySuite.scala` (deleted with the legacy path), `ChallengeProjectionSuite.scala`, `WalkerSelectionDraftSuite.scala`, `WalkerSelectionPanelsSuite.scala`, `BannerControlsSuite.scala`.

Deleted at the end (Task 10): `gameplay/actions/Challenge.scala` (after `BannerRules` left it), `ChallengeSuite.scala`, `ChallengeParitySuite.scala`, and the symbols listed in Task 10.

---

### Task 1: `ChooseMany` decision shape (model, validation, journal)

**Files:**
- Modify: `src/main/scala/oathdigital/model/Decisions.scala` (add the query and answer cases; update the "three cases" comment above `DecisionAnswer`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala` (`wellFormed`, `accepts`)
- Modify: `src/main/scala/oathdigital/serialization/DecisionAnswerCodec.scala`
- Test: `src/test/scala/oathdigital/gameplay/walker/DecisionQuerySuite.scala`, `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`

**Interfaces:**
- Produces: `DecisionQuery.ChooseMany(count: Int, options: Vector[DecisionOption], heading: Option[String] = None)`; `DecisionAnswer.ChooseManyAnswer(selected: Vector[DecisionOptionRef])`; journal tag `"choose-many"` with `"options"` array of `{kind, id}`.

- [ ] **Step 1: Write the failing tests**

Add to `DecisionQuerySuite` (imports `SiteId` from `oathdigital.model`; add `SiteId` to the existing import list):

```scala
  private def siteRef(id: String) = DecisionOptionRef.Site(SiteId(id))
  private val sites = Vector("a", "b", "c").map(id =>
    DecisionOption.Site(siteRef(id)))
  private def many(count: Int, options: Vector[DecisionOption] = sites) =
    DecisionQuery.ChooseMany(count, options, Some("Choose sites"))

  test("a choose-many query needs a count of at least one and fewer than its options") {
    assertEquals(wellFormed(many(1)), Right(()))
    assertEquals(wellFormed(many(2)), Right(()))
    assertEquals(wellFormed(many(0)),
      invalid("decision recover.choice declares no selection to make"))
    assertEquals(wellFormed(many(3)), invalid("decision recover.choice " +
      "declares a count that already takes every option, leaving nothing to decide"))
    assertEquals(wellFormed(many(1, sites :+ sites.head)),
      invalid("decision recover.choice declares duplicate options"))
  }

  test("a choose-many answer must name exactly count distinct offered options") {
    val q = many(2)
    assertEquals(accepts(q, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a"), siteRef("c")))), Right(()))
    assertEquals(accepts(q, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a")))), invalid(
      "decision recover.choice selects 1 options instead of 2"))
    assertEquals(accepts(q, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a"), siteRef("a")))), invalid(
      "decision recover.choice selects an option more than once"))
    assertEquals(accepts(q, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a"), siteRef("z")))), invalid(
      "decision recover.choice does not offer a selected option"))
    assertEquals(accepts(q, DecisionAnswer.ChooseOneAnswer(siteRef("a"))),
      invalid("decision recover.choice expects a multiple-choice answer"))
  }
```

Add to `GameEventWireSuite`:

```scala
  test("a choose-many answer round trips through the journal") {
    val player = PlayerId("red")
    val answer = DecisionAnswer.ChooseManyAnswer(Vector(
      DecisionOptionRef.Site(SiteId("a")), DecisionOptionRef.Site(SiteId("c"))))
    val event = WalkerStepRecorded("1.0", ChoicePayload("challenge.ribbon-site",
      answer, player), Vector.empty, Vector.empty)
    val encoded = GameEventWire.encodeEvent("walker", catalog.ref, 0, event)
      .toOption.get
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
  }
```

- [ ] **Step 2: Run to verify failure**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.walker.DecisionQuerySuite oathdigital.serialization.GameEventWireSuite"`
Expected: FAIL to compile (`ChooseMany`, `ChooseManyAnswer` not found).

- [ ] **Step 3: Implement**

`Decisions.scala`, inside `object DecisionQuery` after `ChooseOne`:

```scala
  /** Pick exactly `count` distinct options. Well-formed only when
    * `1 <= count < options.size`: a count that takes every option is a forced
    * answer, which no shape may park on.
    */
  final case class ChooseMany(count: Int, options: Vector[DecisionOption],
      heading: Option[String] = None) extends DecisionQuery
```

Inside `object DecisionAnswer`:

```scala
  /** Answer to a [[DecisionQuery.ChooseMany]]: the distinct option
    * references the player selected, exactly the query's `count` of them.
    */
  final case class ChooseManyAnswer(selected: Vector[DecisionOptionRef])
      extends DecisionAnswer
```

Replace the `DecisionAnswer` doc's "three cases" wording with "the cases below".

`DecisionQueries.wellFormed`, add before the `Partition` case:

```scala
    case DecisionQuery.ChooseMany(count, options, _) =>
      val refs = options.map(_.ref)
      for {
        _ <- require(refs.distinct.size == refs.size, decisionId,
          "declares duplicate options")
        _ <- require(count >= 1, decisionId, "declares no selection to make")
        _ <- require(count < refs.size, decisionId,
          "declares a count that already takes every option, leaving " +
            "nothing to decide")
      } yield ()
```

`DecisionQueries.accepts`, add before the `Partition` case:

```scala
    case DecisionQuery.ChooseMany(count, options, _) => answer match {
      case DecisionAnswer.ChooseManyAnswer(selected) =>
        for {
          _ <- require(selected.forall(options.map(_.ref).contains),
            decisionId, "does not offer a selected option")
          _ <- require(selected.distinct.size == selected.size, decisionId,
            "selects an option more than once")
          _ <- require(selected.size == count, decisionId,
            s"selects ${selected.size} options instead of $count")
        } yield ()
      case _ =>
        reject(decisionId, "expects a multiple-choice answer")
    }
```

`DecisionAnswerCodec`: add `private val ChooseManyTag = "choose-many"`; in `encode`:

```scala
    case DecisionAnswer.ChooseManyAnswer(selected) => ujson.Obj(
      "kind" -> ChooseManyTag,
      "options" -> ujson.Arr.from(selected.map(encodeRef)))
```

in `decode`:

```scala
      case ChooseManyTag =>
        traverse(value("options").arr.toVector.zipWithIndex) {
          case (entry, index) => decodeRef(entry, s"$path.options[$index]")
        }.map(DecisionAnswer.ChooseManyAnswer)
```

- [ ] **Step 4: Run to verify pass**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.walker.DecisionQuerySuite oathdigital.serialization.GameEventWireSuite"`
Expected: PASS. If the compiler reports a non-exhaustive match on `DecisionQuery` or `DecisionAnswer` elsewhere in `src/main` (`WalkerDecisionProjector.queryProjection`, `WalkerSimulation`), leave it for Task 3; if the warning is fatal, add a temporary `case _: DecisionQuery.ChooseMany => None` in `queryProjection` and remove it in Task 3.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/model/Decisions.scala src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala src/main/scala/oathdigital/serialization/DecisionAnswerCodec.scala src/test/scala/oathdigital/gameplay/walker/DecisionQuerySuite.scala src/test/scala/oathdigital/serialization/GameEventWireSuite.scala
git commit -m "feat(walker): add the choose-many decision shape" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: `ChooseAmount` decision shape (model, validation, journal)

**Files:**
- Modify: `src/main/scala/oathdigital/model/Decisions.scala`, `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala`, `src/main/scala/oathdigital/serialization/DecisionAnswerCodec.scala`
- Test: `src/test/scala/oathdigital/gameplay/walker/DecisionQuerySuite.scala`, `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`

**Interfaces:**
- Produces: `DecisionQuery.ChooseAmount(min: Int, max: Int, heading: Option[String], confirmLabel: String)`; `DecisionAnswer.ChooseAmountAnswer(amount: Int)`; journal tag `"choose-amount"` with `"amount"`.

- [ ] **Step 1: Write the failing tests**

`DecisionQuerySuite`:

```scala
  private def amount(min: Int, max: Int, heading: Option[String] = Some("Amount"),
      confirm: String = "Place") = DecisionQuery.ChooseAmount(min, max, heading, confirm)

  test("a choose-amount query needs a heading, a confirm label and 0 <= min <= max") {
    assertEquals(wellFormed(amount(3, 3)), Right(()))
    assertEquals(wellFormed(amount(1, 6)), Right(()))
    assertEquals(wellFormed(amount(4, 3)), invalid(
      "decision recover.choice declares an amount range 4..3"))
    assertEquals(wellFormed(amount(-1, 3)), invalid(
      "decision recover.choice declares an amount range -1..3"))
    assertEquals(wellFormed(amount(1, 3, heading = None)),
      invalid("decision recover.choice declares no heading"))
    assertEquals(wellFormed(amount(1, 3, confirm = " ")),
      invalid("decision recover.choice declares a blank confirm label"))
  }

  test("a choose-amount answer is accepted exactly inside its range") {
    val q = amount(3, 5)
    assertEquals(accepts(q, DecisionAnswer.ChooseAmountAnswer(3)), Right(()))
    assertEquals(accepts(q, DecisionAnswer.ChooseAmountAnswer(5)), Right(()))
    assertEquals(accepts(q, DecisionAnswer.ChooseAmountAnswer(2)), invalid(
      "decision recover.choice amount 2 is outside 3..5"))
    assertEquals(accepts(q, DecisionAnswer.ChooseAmountAnswer(6)), invalid(
      "decision recover.choice amount 6 is outside 3..5"))
    assertEquals(accepts(q, DecisionAnswer.ChooseOneAnswer(siteRef("a"))),
      invalid("decision recover.choice expects an amount answer"))
  }
```

`GameEventWireSuite`:

```scala
  test("a choose-amount answer round trips through the journal") {
    val player = PlayerId("red")
    val event = WalkerStepRecorded("2.0", ChoicePayload("challenge.amount",
      DecisionAnswer.ChooseAmountAnswer(4), player), Vector.empty, Vector.empty)
    val encoded = GameEventWire.encodeEvent("walker", catalog.ref, 0, event)
      .toOption.get
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
  }
```

- [ ] **Step 2: Run to verify failure**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.walker.DecisionQuerySuite oathdigital.serialization.GameEventWireSuite"`
Expected: FAIL to compile (`ChooseAmount` not found).

- [ ] **Step 3: Implement**

`Decisions.scala`, `object DecisionQuery`:

```scala
  /** Pick an integer from `min` to `max` inclusive. Both labels are required,
    * as on [[Distribute]]: the panel has a confirm step, and a range with no
    * heading says nothing about what is being chosen. A single-value range is
    * still a decision the player confirms.
    */
  final case class ChooseAmount(min: Int, max: Int, heading: Option[String],
      confirmLabel: String) extends DecisionQuery
```

`object DecisionAnswer`:

```scala
  /** Answer to a [[DecisionQuery.ChooseAmount]]: the amount picked. */
  final case class ChooseAmountAnswer(amount: Int) extends DecisionAnswer
```

`DecisionQueries.wellFormed` (before `Partition`):

```scala
    case DecisionQuery.ChooseAmount(min, max, heading, confirmLabel) =>
      for {
        _ <- require(heading.exists(_.trim.nonEmpty), decisionId,
          "declares no heading")
        _ <- require(confirmLabel.trim.nonEmpty, decisionId,
          "declares a blank confirm label")
        _ <- require(min >= 0 && min <= max, decisionId,
          s"declares an amount range $min..$max")
      } yield ()
```

`DecisionQueries.accepts` (before `Partition`):

```scala
    case DecisionQuery.ChooseAmount(min, max, _, _) => answer match {
      case DecisionAnswer.ChooseAmountAnswer(amount) =>
        require(amount >= min && amount <= max, decisionId,
          s"amount $amount is outside $min..$max")
      case _ =>
        reject(decisionId, "expects an amount answer")
    }
```

`DecisionAnswerCodec`: `private val ChooseAmountTag = "choose-amount"`; encode:

```scala
    case DecisionAnswer.ChooseAmountAnswer(amount) => ujson.Obj(
      "kind" -> ChooseAmountTag, "amount" -> amount)
```

decode:

```scala
      case ChooseAmountTag =>
        val raw = value("amount").num
        Either.cond(raw.isValidInt, DecisionAnswer.ChooseAmountAnswer(raw.toInt),
          InvalidValue(s"$path.amount", s"amount '$raw' is not an integer"))
```

- [ ] **Step 4: Run to verify pass**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.walker.DecisionQuerySuite oathdigital.serialization.GameEventWireSuite"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/model/Decisions.scala src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala src/main/scala/oathdigital/serialization/DecisionAnswerCodec.scala src/test/scala/oathdigital/gameplay/walker/DecisionQuerySuite.scala src/test/scala/oathdigital/serialization/GameEventWireSuite.scala
git commit -m "feat(walker): add the choose-amount decision shape" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: Wire and projection for both shapes; presentable unclaimed banners

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala`, `shared/src/main/scala/oathdigital/protocol/CommandNestedCodecs.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala`, `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala`
- Modify: `src/main/scala/oathdigital/application/GameIntentMapper.scala`, `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`
- Test: `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala`, `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`, `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala`

**Interfaces:**
- Consumes: `DecisionQuery.ChooseMany`/`ChooseAmount`, `DecisionAnswer.ChooseManyAnswer`/`ChooseAmountAnswer` (Tasks 1 and 2).
- Produces: wire answers `DecisionAnswerWire.ChooseManyWire(options: Vector[DecisionOptionWire])`, `DecisionAnswerWire.ChooseAmountWire(amount: Int)`, `final case class DecisionOptionWire(optionKind: String, optionId: String)`; projection forms `"choose-many"` (fields `options`, `count`) and `"choose-amount"` (fields `minimum`, `maximum`, `confirmLabel`); `DecisionQueryProjection` gains `count: Option[Int] = None`, `minimum: Option[Int] = None`, `maximum: Option[Int] = None`.

- [ ] **Step 1: Write the failing tests**

`CommandProtocolSuite`, add to the `ResolveWalker` vector (imports `DecisionOptionWire` via the existing `oathdigital.protocol._`):

```scala
    ResolveWalker("challenge.ribbon-site", DecisionAnswerWire.ChooseManyWire(
      Vector(DecisionOptionWire("site", "a"), DecisionOptionWire("site", "c")))),
    ResolveWalker("challenge.amount", DecisionAnswerWire.ChooseAmountWire(4)),
```

Add a decode-rejection test in the same suite:

```scala
  test("a choose-many wire answer rejects duplicate options and unknown keys") {
    val duplicate = ujson.Obj("kind" -> "choose-many", "options" -> ujson.Arr(
      ujson.Obj("optionKind" -> "site", "optionId" -> "a"),
      ujson.Obj("optionKind" -> "site", "optionId" -> "a")))
    assert(CommandNestedCodecs.decodeDecisionAnswerWire(duplicate, "$").isLeft)
    val extra = ujson.Obj("kind" -> "choose-amount", "amount" -> 2, "x" -> 1)
    assert(CommandNestedCodecs.decodeDecisionAnswerWire(extra, "$").isLeft)
  }
```

`ProjectionProtocolSuite`:

```scala
  test("choose-many and choose-amount queries round-trip their counts and bounds") {
    def site(id: String) = DecisionOptionProjection("site", id, id)
    val many = DecisionQueryProjection("choose-many",
      Vector(site("a"), site("b"), site("c")), heading = Some("Choose sites"),
      count = Some(2))
    val amount = DecisionQueryProjection("choose-amount", Vector.empty,
      heading = Some("Place more than 2 favor"), confirmLabel = Some("Take banner"),
      minimum = Some(3), maximum = Some(6))
    Vector(many, amount).foreach { query =>
      val carrying = projection.copy(walkerDecision =
        projection.walkerDecision.map(_.copy(query = Some(query))))
      assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(carrying)),
        Right(carrying))
    }
  }
```

`WalkerDecisionProjectorSuite` (uses the existing `parked`, `projects`, `projectorFor`, `Decide` helpers seen in the Distribute test):

```scala
  test("a parked choose-many projects its options and count") {
    val (context, actor) = parked(ActionRef.Recover)
    val siteIds = context.ready.game.current.map.inPlay.take(3)
    val tree = Sequence(Decide("test.many", actor, DecisionQuery.ChooseMany(2,
      siteIds.map(id => DecisionOption.Site(DecisionOptionRef.Site(id))),
      Some("Choose sites"))))
    val query = projectorFor(tree).project(context).flatMap(_.query)
      .getOrElse(fail("a parked choose-many must project"))
    assertEquals(query.form, "choose-many")
    assertEquals(query.count, Some(2))
    assertEquals(query.options.map(_.id), siteIds.map(_.value))
    assertEquals(query.heading, Some("Choose sites"))
  }

  test("a parked choose-amount projects its bounds and confirm label") {
    val (context, actor) = parked(ActionRef.Recover)
    val tree = Sequence(Decide("test.amount", actor, DecisionQuery.ChooseAmount(
      3, 6, Some("Place more than 2 favor"), "Take banner")))
    val query = projectorFor(tree).project(context).flatMap(_.query)
      .getOrElse(fail("a parked choose-amount must project"))
    assertEquals(query.form, "choose-amount")
    assertEquals((query.minimum, query.maximum), (Some(3), Some(6)))
    assertEquals(query.confirmLabel, Some("Take banner"))
    assertEquals(query.options, Vector.empty)
  }

  test("an unclaimed banner is presentable and every banner shows its resources") {
    val (base, actor) = parked(ActionRef.Recover)
    val option = DecisionOption.Banner(DecisionOptionRef.Banner(Banner.DarkestSecret))
    val unclaimed = base.copy(ready = base.ready.updateCurrent(current =>
      current.copy(banners = current.banners.copy(darkestSecret =
        current.banners.darkestSecret.copy(holder = None, secrets = 3)))))
    val query = projects(unclaimed, actor, Vector(option))
      .getOrElse(fail("an unclaimed banner must project"))
    assertEquals(query.options.map(row => (row.kind, row.id, row.label,
      row.details)), Vector(("banner", "darkest-secret",
      "Unclaimed darkest-secret", Vector("Currently 3 resources"))))
  }
```

In the existing test "a relic slot and a banner project from live state…", replace the final control (the `Banner.DarkestSecret` suppression) with an assertion that it now projects, and fix the test name's tail:

```scala
    // Control: a slot past the owner's relics has no live state to describe,
    // so it suppresses the whole decision. An unclaimed banner is presentable.
    assertEquals(projects(placed, actor, Vector(DecisionOption.RelicSlot(
      DecisionOptionRef.RelicSlot(enemy.player, 1)))), None)
    assert(projects(placed, actor, Vector(DecisionOption.Banner(
      DecisionOptionRef.Banner(Banner.DarkestSecret)))).nonEmpty)
```

- [ ] **Step 2: Run to verify failure**

Run: `./sbtw -no-colors "shared/testOnly oathdigital.protocol.*" "testOnly oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: FAIL to compile (`ChooseManyWire`, `count`, ... not found).

- [ ] **Step 3: Implement**

`CommandIntents.scala`, in `object DecisionAnswerWire` beside `DistributeWire`, and after `DistributeAmountWire`:

```scala
  /** Answers a choose-many decision with the options selected. */
  final case class ChooseManyWire(options: Vector[DecisionOptionWire])
      extends DecisionAnswerWire

  /** Answers a choose-amount decision with the amount picked. */
  final case class ChooseAmountWire(amount: Int) extends DecisionAnswerWire
```

```scala
/** One option named in a [[DecisionAnswerWire.ChooseManyWire]]. */
final case class DecisionOptionWire(optionKind: String, optionId: String)
```

`CommandNestedCodecs.scala`, `encodeDecisionAnswerWire`:

```scala
    case DecisionAnswerWire.ChooseManyWire(options) => ujson.Obj(
      "kind" -> "choose-many",
      "options" -> ujson.Arr.from(options.map(row => ujson.Obj(
        "optionKind" -> row.optionKind, "optionId" -> row.optionId))))
    case DecisionAnswerWire.ChooseAmountWire(amount) =>
      ujson.Obj("kind" -> "choose-amount", "amount" -> amount)
```

`decodeDecisionAnswerWire`, before the `case kind =>` fallthrough:

```scala
      case "choose-many" => for {
        _ <- exact(root, Set("kind", "options"), path)
        raw <- field(root, "options", path).flatMap(array(_, s"$path.options"))
        rows <- traverse(raw.zipWithIndex) { case (v, i) =>
          decodeDecisionOption(v, s"$path.options[$i]") }
        _ <- noDuplicates(rows.map(row => s"${row.optionKind}/${row.optionId}"),
          s"$path.options")
      } yield DecisionAnswerWire.ChooseManyWire(rows)
      case "choose-amount" => for {
        _ <- exact(root, Set("kind", "amount"), path)
        amount <- field(root, "amount", path).flatMap(integer(_, s"$path.amount"))
      } yield DecisionAnswerWire.ChooseAmountWire(amount)
```

and beside `decodeDecisionPlacement`:

```scala
  private def decodeDecisionOption(value: ujson.Value, path: String)
      : Either[ProtocolDecodeFailure, DecisionOptionWire] = obj(value, path).flatMap { row => for {
    _ <- exact(row, Set("optionKind", "optionId"), path)
    optionKind <- string(row, "optionKind", path)
    optionId <- string(row, "optionId", path)
  } yield DecisionOptionWire(optionKind, optionId) }
```

`GameIntentMapper.decisionAnswer` (imports `ChooseManyAnswer`, `ChooseAmountAnswer` from `oathdigital.model.DecisionAnswer`):

```scala
    case DecisionAnswerWire.ChooseManyWire(options) =>
      traverse(options)(row => optionRef(row.optionKind, row.optionId,
        "$.intent.payload.options")).map(ChooseManyAnswer)
    case DecisionAnswerWire.ChooseAmountWire(amount) =>
      Right(ChooseAmountAnswer(amount))
```

`ActionProjectionDtos.scala`: extend the `form` doc (`"choose-many"` (pick exactly `count` options) and `"choose-amount"` (pick an integer from `minimum` to `maximum`)) and add the three fields:

```scala
final case class DecisionQueryProjection(
    form: String,
    options: Vector[DecisionOptionProjection],
    sections: Vector[DecisionSectionProjection] = Vector.empty,
    heading: Option[String] = None,
    confirmLabel: Option[String] = None,
    slots: Vector[DecisionSlotProjection] = Vector.empty,
    total: Option[Int] = None,
    count: Option[Int] = None,
    minimum: Option[Int] = None,
    maximum: Option[Int] = None)
```

`ActionProjectionCodec.scala`: in `encodeDecisionQuery` append `"count" -> intOption(value.count), "minimum" -> intOption(value.minimum), "maximum" -> intOption(value.maximum)`; in `decodeDecisionQuery` add `"count", "minimum", "maximum"` to the `exact` key set, read each with `optionalInt(value, "count", path)` etc., and pass `count, minimum, maximum` as the last three constructor arguments.

`WalkerDecisionProjector.queryProjection`, add before `Partition`:

```scala
      case DecisionQuery.ChooseMany(count, options, heading) =>
        described(options).map(DecisionQueryProjection("choose-many", _,
          heading = heading, count = Some(count)))
      case DecisionQuery.ChooseAmount(min, max, heading, confirmLabel) =>
        Some(DecisionQueryProjection("choose-amount", Vector.empty,
          heading = heading, confirmLabel = Some(confirmLabel),
          minimum = Some(min), maximum = Some(max)))
```

Remove any temporary case added in Task 1.

`WalkerDecisionProjector.optionProjection`: give the local `row` an extra-details parameter and replace the `Banner` case:

```scala
    def row(label: String, card: Option[CardDetailsProjection] = None,
        extra: Vector[String] = Vector.empty) =
      Some(DecisionOptionProjection(ref.kind, ref.wireId, label, card,
        details ++ extra))
```

```scala
      case DecisionOption.Banner(held) =>
        val current = ready.game.current
        val owner = BannerRules.holder(current, held.banner)
          .fold("Unclaimed")(holder => presentation.safeLabel(holder.value))
        row(s"$owner ${presentation.safeLabel(held.banner.key)}",
          extra = Vector(
            s"Currently ${BannerRules.resources(current, held.banner)} resources"))
```

The Conspiracy decision's banner options gain the `Currently N resources` detail; that is intended.

- [ ] **Step 4: Run to verify pass**

Run: `./sbtw -no-colors "shared/testOnly oathdigital.protocol.*" "testOnly oathdigital.application.*" "testOnly oathdigital.gameplay.powers.whenplayed.*"`
Expected: PASS. Fix any exhaustive-match warning-as-error the compiler reports on `DecisionAnswerWire`/`DecisionQuery` (search `DistributeWire` and `DecisionQuery.Distribute` in `src/main`, `shared/src/main`, `frontend/src/main`; the frontend is Task 4).

- [ ] **Step 5: Commit**

```bash
git add shared/src src/main src/test
git commit -m "feat(protocol): carry choose-many and choose-amount decisions end to end" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: Generic frontend controls for both shapes

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/WalkerSelectionDraft.scala`, `frontend/src/main/scala/oathdigital/frontend/WalkerSelectionPanels.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala` (add the two `ServerUiView` members beside `currentWalkerDistribution`), `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala` (the draft `var`, its reconcile call and the two view members), `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala` (one render call beside `DistributePanelRenderer.render`)
- Test: `frontend/src/test/scala/oathdigital/frontend/WalkerSelectionDraftSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/WalkerSelectionPanelsSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/RecordingServerUiView.scala` (add the two members to `RecordingView`)

**Interfaces:**
- Consumes: projection forms `choose-many` (`count`, `options`) and `choose-amount` (`minimum`, `maximum`, `confirmLabel`); `DecisionAnswerWire.ChooseManyWire`, `ChooseAmountWire`, `DecisionOptionWire`; `WalkerPartitionDraft.itemId(option: DecisionOptionState): String` (existing, `"kind:id"`).
- Produces: `WalkerSelectionDraft` (sealed) with `WalkerChooseManyDraft(context, decisionId, query, selected: Vector[String])` and `WalkerAmountDraft(context, decisionId, query, amount: Int)`; `WalkerSelectionDraft.reconcile(previous: Option[WalkerSelectionDraft], context: BoardSelectionContext, decision: Option[WalkerDecisionState]): Option[WalkerSelectionDraft]`; `ServerUiView.currentWalkerSelection: Option[WalkerSelectionDraft]` and its setter; `WalkerSelectionPanels.render(value, presentation, canControl, panel, ui): Unit`. CSS hooks: `.walker-many-option` (`aria-pressed`), `.walker-many-confirm`, `select.walker-amount`, `.walker-amount-confirm`.

- [ ] **Step 1: Write the failing tests**

`WalkerSelectionDraftSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionOptionWire,
  GameIntent => Intent}

class WalkerSelectionDraftSuite extends munit.FunSuite {
  private def site(id: String) = DecisionOptionState("site", id, id)
  private val many = DecisionQueryState("choose-many",
    Vector(site("a"), site("b"), site("c")), heading = Some("Choose sites"),
    count = Some(2))
  private val amount = DecisionQueryState("choose-amount", Vector.empty,
    heading = Some("Place more than 2 favor"), confirmLabel = Some("Take banner"),
    minimum = Some(3), maximum = Some(6))
  private val context = BoardSelectionContext("game", "red", 9)
  private def parked(id: String, query: DecisionQueryState) =
    Some(WalkerDecisionState("challenge", id, "decide", query = Some(query)))

  test("a choose-many draft opens empty, toggles, and never exceeds its count") {
    val Some(draft: WalkerChooseManyDraft) = WalkerSelectionDraft.reconcile(None,
      context, parked("challenge.ribbon-site", many)): @unchecked
    assertEquals(draft.selected, Vector.empty)
    assert(!draft.canConfirm)
    val two = draft.toggle("site:a").toggle("site:c")
    assertEquals(two.selected, Vector("site:a", "site:c"))
    assert(two.canConfirm)
    assertEquals(two.toggle("site:b"), two)
    assertEquals(two.toggle("site:a").selected, Vector("site:c"))
  }

  test("a choose-many draft submits its selection in declared option order") {
    val Some(draft: WalkerChooseManyDraft) = WalkerSelectionDraft.reconcile(None,
      context, parked("challenge.ribbon-site", many)): @unchecked
    assertEquals(draft.toggle("site:c").command, None)
    assertEquals(draft.toggle("site:c").toggle("site:a").command,
      Some(Intent.ResolveWalker("challenge.ribbon-site",
        DecisionAnswerWire.ChooseManyWire(Vector(DecisionOptionWire("site", "a"),
          DecisionOptionWire("site", "c"))))))
  }

  test("a choose-amount draft opens at the minimum and clamps to its range") {
    val Some(draft: WalkerAmountDraft) = WalkerSelectionDraft.reconcile(None,
      context, parked("challenge.amount", amount)): @unchecked
    assertEquals(draft.amount, 3)
    assertEquals(draft.choose(9).amount, 6)
    assertEquals(draft.choose(1).amount, 3)
    assertEquals(draft.choose(5).command, Some(Intent.ResolveWalker(
      "challenge.amount", DecisionAnswerWire.ChooseAmountWire(5))))
  }

  test("reconcile keeps a draft for the same decision and drops it otherwise") {
    val Some(first: WalkerChooseManyDraft) = WalkerSelectionDraft.reconcile(None,
      context, parked("challenge.ribbon-site", many)): @unchecked
    val edited = first.toggle("site:a")
    assertEquals(WalkerSelectionDraft.reconcile(Some(edited), context,
      parked("challenge.ribbon-site", many)), Some(edited))
    assertEquals(WalkerSelectionDraft.reconcile(Some(edited), context.copy(
      sequence = 10), parked("challenge.ribbon-site", many)), Some(first))
    assertEquals(WalkerSelectionDraft.reconcile(Some(edited), context,
      parked("challenge.amount", amount)).map(_.getClass),
      Some(classOf[WalkerAmountDraft]))
    assertEquals(WalkerSelectionDraft.reconcile(Some(edited), context, None), None)
  }
}
```

(`BoardSelectionContext(gameId, playerId, sequence)` is the existing three-field context.)

`WalkerSelectionPanelsSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionOptionWire,
  GameIntent => Intent}
import org.scalajs.dom

class WalkerSelectionPanelsSuite extends munit.FunSuite {
  private def site(id: String) = DecisionOptionState("site", id, s"Site $id")
  private val many = DecisionQueryState("choose-many",
    Vector(site("a"), site("b"), site("c")), heading = Some("Choose sites"),
    count = Some(2))
  private val amount = DecisionQueryState("choose-amount", Vector.empty,
    heading = Some("Place more than 2 favor"), confirmLabel = Some("Take banner"),
    minimum = Some(3), maximum = Some(5))
  private val presentation = ServerUiSupport.ViewerPresentation(
    showGameplayControls = true, None, None)

  private def projection(id: String, query: DecisionQueryState): GameProjection =
    GameProjection("game", 9L, "act-action-selection", Some("red"), Vector.empty,
      Vector.empty, Vector.empty, Vector.empty, ready = true, completed = false,
      walkerDecision = Some(WalkerDecisionState("challenge", id, "decide",
        query = Some(query))))

  private def opened(id: String, query: DecisionQueryState) = {
    val ui = new RecordingView("game", "red")
    ui.currentWalkerSelection = WalkerSelectionDraft.reconcile(None,
      BoardSelectionContext("game", "red", 9), projection(id, query).walkerDecision)
    ui
  }

  private def render(ui: RecordingView, id: String, query: DecisionQueryState,
      canControl: Boolean = true): dom.Element = {
    val panel = dom.document.createElement("div")
    WalkerSelectionPanels.render(projection(id, query), presentation, canControl,
      panel, ui)
    panel
  }

  private def one(root: dom.Element, selector: String): dom.Element = {
    val found = root.querySelectorAll(selector)
    assertEquals(found.length, 1, s"expected exactly one $selector")
    found(0).asInstanceOf[dom.Element]
  }

  test("choose-many renders a toggle per option and confirms only at the count") {
    val ui = opened("challenge.ribbon-site", many)
    assertEquals(one(render(ui, "challenge.ribbon-site", many), "h2").textContent,
      "Choose sites")
    val confirm0 = one(render(ui, "challenge.ribbon-site", many),
      ".walker-many-confirm").asInstanceOf[dom.html.Button]
    assert(confirm0.disabled)
    one(render(ui, "challenge.ribbon-site", many),
      """[data-option-id="site:a"]""").asInstanceOf[dom.html.Button].click()
    one(render(ui, "challenge.ribbon-site", many),
      """[data-option-id="site:c"]""").asInstanceOf[dom.html.Button].click()
    val panel = render(ui, "challenge.ribbon-site", many)
    assertEquals(one(panel, """[data-option-id="site:a"]""")
      .getAttribute("aria-pressed"), "true")
    val confirm = one(panel, ".walker-many-confirm").asInstanceOf[dom.html.Button]
    assert(!confirm.disabled)
    confirm.click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker("challenge.ribbon-site",
      DecisionAnswerWire.ChooseManyWire(Vector(DecisionOptionWire("site", "a"),
        DecisionOptionWire("site", "c"))))))
  }

  test("choose-amount renders a dropdown over its range and submits the choice") {
    val ui = opened("challenge.amount", amount)
    val panel = render(ui, "challenge.amount", amount)
    val select = one(panel, "select.walker-amount").asInstanceOf[dom.html.Select]
    assertEquals((0 until select.options.length).map(i =>
      select.options(i).value), Vector("3", "4", "5"))
    assertEquals(select.value, "3")
    select.value = "5"
    select.dispatchEvent(new dom.Event("change"))
    val confirm = one(panel, ".walker-amount-confirm").asInstanceOf[dom.html.Button]
    assertEquals(confirm.textContent, "Take banner")
    confirm.click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker("challenge.amount",
      DecisionAnswerWire.ChooseAmountWire(5))))
  }

  test("a viewer who cannot control sees disabled controls") {
    val ui = opened("challenge.amount", amount)
    val panel = render(ui, "challenge.amount", amount, canControl = false)
    assert(one(panel, "select.walker-amount").asInstanceOf[dom.html.Select].disabled)
    assert(one(panel, ".walker-amount-confirm").asInstanceOf[dom.html.Button].disabled)
  }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./sbtw -no-colors "frontend/testOnly oathdigital.frontend.WalkerSelectionDraftSuite oathdigital.frontend.WalkerSelectionPanelsSuite"`
Expected: FAIL to compile.

- [ ] **Step 3: Implement**

`WalkerSelectionDraft.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionOptionWire,
  GameIntent => GameCommand}

/** A draft answer to a parked choose-many or choose-amount decision. Like
  * [[WalkerDistributeDraft]], everything it knows comes from the projected
  * query, so the submitted answer names only projected options or a
  * projected range.
  */
private[frontend] sealed trait WalkerSelectionDraft {
  def context: BoardSelectionContext
  def decisionId: String
  def query: DecisionQueryState
  def canConfirm: Boolean
  def command: Option[GameCommand.ResolveWalker]
}

private[frontend] final case class WalkerChooseManyDraft(
    context: BoardSelectionContext, decisionId: String,
    query: DecisionQueryState, selected: Vector[String])
    extends WalkerSelectionDraft {
  private def count: Int = query.count.getOrElse(0)

  /** Adds an unselected option while fewer than `count` are selected, and
    * removes a selected one; adding past the count changes nothing.
    */
  def toggle(item: String): WalkerChooseManyDraft =
    if (selected.contains(item)) copy(selected = selected.filterNot(_ == item))
    else if (selected.size < count &&
      query.options.exists(WalkerPartitionDraft.itemId(_) == item))
      copy(selected = selected :+ item)
    else this

  def canConfirm: Boolean = selected.size == count

  def command: Option[GameCommand.ResolveWalker] = Option.when(canConfirm)(
    GameCommand.ResolveWalker(decisionId, DecisionAnswerWire.ChooseManyWire(
      query.options.filter(option =>
        selected.contains(WalkerPartitionDraft.itemId(option)))
        .map(option => DecisionOptionWire(option.kind, option.id)))))
}

private[frontend] final case class WalkerAmountDraft(
    context: BoardSelectionContext, decisionId: String,
    query: DecisionQueryState, amount: Int) extends WalkerSelectionDraft {
  private def minimum: Int = query.minimum.getOrElse(0)
  private def maximum: Int = query.maximum.getOrElse(0)

  def choose(value: Int): WalkerAmountDraft =
    copy(amount = math.max(minimum, math.min(maximum, value)))

  def canConfirm: Boolean = amount >= minimum && amount <= maximum

  def command: Option[GameCommand.ResolveWalker] = Option.when(canConfirm)(
    GameCommand.ResolveWalker(decisionId,
      DecisionAnswerWire.ChooseAmountWire(amount)))
}

private[frontend] object WalkerSelectionDraft {
  /** Adopts whichever parked decision projects a choose-many or
    * choose-amount query, and drops the draft when the decision, the query or
    * the board context changes.
    */
  def reconcile(previous: Option[WalkerSelectionDraft],
      context: BoardSelectionContext, decision: Option[WalkerDecisionState])
      : Option[WalkerSelectionDraft] =
    decision.flatMap(parked => parked.query
        .filter(query => query.form == "choose-many" ||
          query.form == "choose-amount")
        .map(parked.decisionId -> _))
      .map { case (decisionId, query) =>
        previous.filter(draft => draft.context == context &&
            draft.decisionId == decisionId && draft.query == query)
          .getOrElse(
            if (query.form == "choose-many")
              WalkerChooseManyDraft(context, decisionId, query, Vector.empty)
            else WalkerAmountDraft(context, decisionId, query,
              query.minimum.getOrElse(0)))
      }
}
```

`WalkerSelectionPanels.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom

/** The generic panels for a parked choose-many and choose-amount decision.
  * Both render only what the projected query declares and name no action.
  */
private[frontend] object WalkerSelectionPanels {
  import ServerUiSupport.{ViewerPresentation, button, element, text}

  def render(value: GameProjection, presentation: ViewerPresentation,
      canControl: Boolean, panel: dom.Element, ui: ServerUiView): Unit =
    value.walkerDecision.filter(_ => presentation.showGameplayControls)
        .flatMap(decision => decision.query.map(decision -> _))
        .foreach { case (decision, query) =>
      ui.currentWalkerSelection.filter(_.decisionId == decision.decisionId)
          .foreach {
        case draft: WalkerChooseManyDraft =>
          renderMany(query, draft, canControl, panel, ui)
        case draft: WalkerAmountDraft =>
          renderAmount(query, draft, canControl, panel, ui)
      }
    }

  private def renderMany(query: DecisionQueryState, draft: WalkerChooseManyDraft,
      canControl: Boolean, panel: dom.Element, ui: ServerUiView): Unit = {
    panel.appendChild(text("h2", "", WalkerPanelSupport.decisionHeading(query)))
    panel.appendChild(text("p", "walker-many-instruction",
      s"Choose ${query.count.getOrElse(0)}."))
    val rows = element("div", "walker-many-options")
    query.options.foreach { option =>
      val item = WalkerPartitionDraft.itemId(option)
      val toggle = button(option.label, "walker-many-option")
      toggle.setAttribute("data-option-id", item)
      toggle.setAttribute("aria-pressed", draft.selected.contains(item).toString)
      toggle.disabled = !canControl
      toggle.onclick = _ => {
        ui.currentWalkerSelection = Some(draft.toggle(item))
        ui.rerender()
      }
      rows.appendChild(toggle)
    }
    panel.appendChild(rows)
    val confirm = button(WalkerPanelSupport.partitionConfirmLabel(query),
      "walker-many-confirm")
    confirm.disabled = !canControl || !draft.canConfirm
    confirm.onclick = _ => draft.command.foreach(ui.submitCommand)
    panel.appendChild(confirm)
  }

  /** A dropdown over the range. The change handler updates the draft without a
    * rerender, so the open control keeps focus; the confirm handler reads the
    * latest draft at click time.
    */
  private def renderAmount(query: DecisionQueryState, draft: WalkerAmountDraft,
      canControl: Boolean, panel: dom.Element, ui: ServerUiView): Unit = {
    panel.appendChild(text("h2", "", WalkerPanelSupport.decisionHeading(query)))
    val select = dom.document.createElement("select")
      .asInstanceOf[dom.html.Select]
    select.className = "walker-amount"
    select.setAttribute("aria-label", WalkerPanelSupport.decisionHeading(query))
    (query.minimum.getOrElse(0) to query.maximum.getOrElse(0)).foreach { n =>
      val choice = dom.document.createElement("option")
        .asInstanceOf[dom.html.Option]
      choice.value = n.toString
      choice.textContent = n.toString
      select.appendChild(choice)
    }
    select.value = draft.amount.toString
    select.disabled = !canControl
    select.onchange = _ => select.value.toIntOption.foreach(value =>
      ui.currentWalkerSelection = Some(draft.choose(value)))
    panel.appendChild(select)
    val confirm = button(query.confirmLabel.getOrElse("Confirm"),
      "walker-amount-confirm")
    confirm.disabled = !canControl || !draft.canConfirm
    confirm.onclick = _ => ui.currentWalkerSelection.collect {
      case latest: WalkerAmountDraft => latest
    }.flatMap(_.command).foreach(ui.submitCommand)
    panel.appendChild(confirm)
  }
}
```

`ServerUiView` (in `ServerUiSupport.scala`, beside `currentWalkerDistribution`):

```scala
  def currentWalkerSelection: Option[WalkerSelectionDraft]
  def currentWalkerSelection_=(value: Option[WalkerSelectionDraft]): Unit
```

`ServerModeUi.scala`: declare `var walkerSelectionDraft = Option.empty[WalkerSelectionDraft]` beside `walkerDistributeDraft`; after the `walkerDistributeDraft = WalkerDistributeDraft.reconcile(...)` statement add:

```scala
          walkerSelectionDraft = WalkerSelectionDraft.reconcile(
            walkerSelectionDraft,
            BoardSelectionContext(gameId, selectedPlayer,
              displayed.nextSequence), displayed.walkerDecision)
```

and in the `ui` object add:

```scala
      def currentWalkerSelection = walkerSelectionDraft
      def currentWalkerSelection_=(value: Option[WalkerSelectionDraft]) = walkerSelectionDraft = value
```

`ActionDecisionRenderer.scala`, right after `DistributePanelRenderer.render(value, presentation, canControl, panel, ui)`:

```scala
   WalkerSelectionPanels.render(value, presentation, canControl, panel, ui)
```

`RecordingServerUiView.scala` (`RecordingView`): add

```scala
  var selection: Option[WalkerSelectionDraft] = None
  def currentWalkerSelection: Option[WalkerSelectionDraft] = selection
  def currentWalkerSelection_=(value: Option[WalkerSelectionDraft]): Unit =
    selection = value
```

If the grep at the top of this task found other `ServerUiView` implementers, add the same two members there.

- [ ] **Step 4: Run to verify pass**

Run: `./sbtw -no-colors "frontend/test"`
Expected: PASS (all frontend suites).

- [ ] **Step 5: Commit**

```bash
git add frontend/src
git commit -m "feat(frontend): render choose-many and choose-amount decisions generically" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: Windows, and `BannerRules` in its own file

**Files:**
- Modify: `src/main/scala/oathdigital/model/PowerWindow.scala`
- Create: `src/main/scala/oathdigital/gameplay/actions/BannerRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/Challenge.scala` (remove `BannerRules`)
- Test: `src/test/scala/oathdigital/model/ChallengeWindowsSuite.scala`

**Interfaces:**
- Produces: `PowerWindow.ChallengeBannerSelection`, `ChallengeAmountSelection`, `ChallengeCost`, `ChallengeRibbon`, `ChallengePlacement` (all `ChallengeWindow`); `PowerWindow.PlaceBannerResourceEligibility`, `PlaceBannerResourceBannerSelection`, `PlaceBannerResourceAmountSelection`, `PlaceBannerResourcePlacement` (all `OtherWindow`). `oathdigital.gameplay.actions.BannerRules` unchanged, in `BannerRules.scala`.

- [ ] **Step 1: Write the failing test**

```scala
package oathdigital.model

class ChallengeWindowsSuite extends munit.FunSuite {
  test("Challenge windows carry the Challenge major action and stable keys") {
    val windows = Vector(
      PowerWindow.ChallengeBannerSelection -> "challenge.banner-selection",
      PowerWindow.ChallengeAmountSelection -> "challenge.amount-selection",
      PowerWindow.ChallengeCost -> "challenge.cost",
      PowerWindow.ChallengeRibbon -> "challenge.ribbon",
      PowerWindow.ChallengePlacement -> "challenge.placement")
    windows.foreach { case (window, key) =>
      assertEquals(window.key, key)
      assertEquals(window.associatedMajorAction, Some(MajorActionType.Challenge))
    }
  }

  test("Place Banner Resource windows are not tied to a major action") {
    val windows = Vector(
      PowerWindow.PlaceBannerResourceEligibility ->
        "place-banner-resource.eligibility",
      PowerWindow.PlaceBannerResourceBannerSelection ->
        "place-banner-resource.banner-selection",
      PowerWindow.PlaceBannerResourceAmountSelection ->
        "place-banner-resource.amount-selection",
      PowerWindow.PlaceBannerResourcePlacement ->
        "place-banner-resource.placement")
    windows.foreach { case (window, key) =>
      assertEquals(window.key, key)
      assertEquals(window.associatedMajorAction, None)
    }
  }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./sbtw -no-colors "testOnly oathdigital.model.ChallengeWindowsSuite"`
Expected: FAIL to compile.

- [ ] **Step 3: Implement**

In `PowerWindow.scala`, after `TradeGain`:

```scala
  case object ChallengeBannerSelection extends ChallengeWindow {
    val key = "challenge.banner-selection"
  }
  case object ChallengeAmountSelection extends ChallengeWindow {
    val key = "challenge.amount-selection"
  }
  case object ChallengeCost extends ChallengeWindow { val key = "challenge.cost" }
  case object ChallengeRibbon extends ChallengeWindow { val key = "challenge.ribbon" }
  case object ChallengePlacement extends ChallengeWindow {
    val key = "challenge.placement"
  }
  case object PlaceBannerResourceEligibility extends OtherWindow {
    val key = "place-banner-resource.eligibility"
  }
  case object PlaceBannerResourceBannerSelection extends OtherWindow {
    val key = "place-banner-resource.banner-selection"
  }
  case object PlaceBannerResourceAmountSelection extends OtherWindow {
    val key = "place-banner-resource.amount-selection"
  }
  case object PlaceBannerResourcePlacement extends OtherWindow {
    val key = "place-banner-resource.placement"
  }
```

Move `BannerRules` with a script (it cuts from the `/** Shared printed banner invariants.` comment up to, not including, `object Challenge {`):

```bash
python3 - <<'EOF'
p = 'src/main/scala/oathdigital/gameplay/actions/Challenge.scala'
s = open(p).read()
start = s.index('/** Shared printed banner invariants.')
end = s.index('object Challenge {')
rules = s[start:end].rstrip() + '\n'
open('src/main/scala/oathdigital/gameplay/actions/BannerRules.scala', 'w').write(
  'package oathdigital.gameplay.actions\n\nimport oathdigital.model._\n\n' + rules)
open(p, 'w').write(s[:start] + s[end:])
EOF
```

- [ ] **Step 4: Run to verify pass**

Run: `./sbtw -no-colors "testOnly oathdigital.model.ChallengeWindowsSuite oathdigital.gameplay.ChallengeSuite"`
Expected: PASS (the legacy suite still compiles because `BannerRules` stays in package `oathdigital.gameplay.actions`).

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/model/PowerWindow.scala src/main/scala/oathdigital/gameplay/actions src/test/scala/oathdigital/model/ChallengeWindowsSuite.scala
git commit -m "refactor(challenge): add Challenge windows and move BannerRules to its own file" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 6: Challenge as a walker procedure

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/challenge/ChallengeProcedure.scala`, `src/main/scala/oathdigital/gameplay/actions/challenge/ChallengeRibbon.scala`
- Modify: `src/main/scala/oathdigital/model/ProcedureRef.scala` (add `ActionRef.Challenge`, key `"challenge"`, and list it in `all`; extend the bridging comment)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` (one entry and its imports)
- Test: `src/test/scala/oathdigital/gameplay/ChallengeFixture.scala`, `src/test/scala/oathdigital/gameplay/ChallengeProcedureSuite.scala`; update `src/test/scala/oathdigital/model/ActionValuesSuite.scala` and `src/test/scala/oathdigital/gameplay/walker/WalkerProcedureRegistrySuite.scala` for the new ref

**Interfaces:**
- Consumes: `DecisionQuery.ChooseMany`, `ChooseAmount`, `DecisionAnswer.ChooseManyAnswer`, `ChooseAmountAnswer` (Tasks 1 and 2); the windows and `BannerRules` (Task 5); `OathViolation.NoPlayableOption(key: String)`, `OathViolation.UnsupportedBannerState(reason: String)`.
- Produces: `ChallengeProcedure.build(catalog, state, actor, args)` and `.rebuild(...)` (the `WalkerProcedureRegistry.Entry` function shape); `ChallengeProcedure.legalBanners(state: ReadyGame, actor: PlayerId): Vector[Banner]`; decision ids `ChallengeProcedure.bannerDecisionId = "challenge.banner"`, `amountDecisionId = "challenge.amount"`, and `ChallengeRibbon.siteDecisionId = "challenge.ribbon-site"`; `ActionRef.Challenge`.

The tree (rule W governs every choice below):

```
Sequence(                                            // ChallengeActionEligibility
  Sequence(SpendSupply(actor, 1)),                   // ChallengeCost
  Branch -> Decide("challenge.banner")               // ChallengeBannerSelection
  Branch -> Decide("challenge.amount")               // ChallengeAmountSelection
  Branch -> [ Sequence(ribbon steps),                // ChallengeRibbon
              Sequence(payment, custody) ])          // ChallengePlacement
```

- [ ] **Step 1: Write the fixture and the failing suite**

`ChallengeFixture.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** The board the Challenge suites share: the active player in the Act phase
  * with chosen favor, secrets and Supply, and one banner in a chosen state.
  */
object ChallengeFixture {
  private val setup = new FirstGameSetupRules(catalog)

  def ready(resources: Int, favor: Int = 6, faceup: Int = 6, facedown: Int = 4,
      supply: Int = 7, banner: Banner = Banner.PeoplesFavor,
      holder: Option[PlayerId] = None): (ReadyGame, PlayerState) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val actor0 = base.game.current.players
      .find(_.player == base.game.current.turn.activePlayer).get
    val actor = actor0.copy(board = actor0.board.copy(favor = favor,
      faceUpSecrets = faceup, faceDownSecrets = facedown,
      supply = SupplyTrack(supply)))
    val banners = banner match {
      case Banner.PeoplesFavor => base.game.current.banners.copy(peoplesFavor =
        base.game.current.banners.peoplesFavor.copy(holder = holder,
          favor = resources))
      case Banner.DarkestSecret => base.game.current.banners.copy(darkestSecret =
        base.game.current.banners.darkestSecret.copy(holder = holder,
          secrets = resources))
    }
    val value = base.updateCurrent(_.copy(
      players = base.game.current.players.map(p =>
        if (p.player == actor.player) actor else p),
      banners = banners, turn = base.game.current.turn.copy(phase = Phase.Act)))
    value -> actor
  }

  def active(ready: ReadyGame): PlayerId = ready.game.current.turn.activePlayer

  def enemy(ready: ReadyGame): PlayerState =
    ready.game.current.players.find(_.player != active(ready)).get

  /** Puts the other player at the actor's site and gives them the banner. */
  def enemyHolds(ready: ReadyGame, banner: Banner, resources: Int,
      colocated: Boolean = true): ReadyGame = {
    val actor = ready.game.current.players.find(_.player == active(ready)).get
    val rival = enemy(ready)
    val moved = if (colocated) rival.copy(pawnSite = actor.pawnSite) else rival
    val banners = banner match {
      case Banner.PeoplesFavor => ready.game.current.banners.copy(peoplesFavor =
        ready.game.current.banners.peoplesFavor.copy(holder = Some(rival.player),
          favor = resources))
      case Banner.DarkestSecret => ready.game.current.banners.copy(darkestSecret =
        ready.game.current.banners.darkestSecret.copy(holder = Some(rival.player),
          secrets = resources))
    }
    ready.updateCurrent(_.copy(banners = banners, players =
      ready.game.current.players.map(p => if (p.player == rival.player) moved else p)))
  }

  /** Every in-play site holds `others` secrets except the named ones. */
  def withSiteSecrets(ready: ReadyGame, secrets: Map[SiteId, Int],
      others: Int = 10): ReadyGame = ready.updateCurrent(current =>
    current.copy(map = current.map.copy(sites = current.map.sites.map {
      case (id, site) => id -> (if (!current.map.inPlay.contains(id)) site
        else site.copy(tokens = Tokens(0, secrets.getOrElse(id, others))))
    })))
}
```

`ChallengeProcedureSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.actions.challenge.ChallengeProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerParked,
  WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation.NoPlayableOption

/** Challenge through the rules, as a client drives it: start, answer the
  * banner, answer the amount, and (Wandering Flame) the tied sites.
  */
class ChallengeProcedureSuite extends munit.FunSuite {
  import ChallengeFixture._

  private val rules = new OathRules(catalog)
  private val pf = DecisionOptionRef.Banner(Banner.PeoplesFavor)
  private val ds = DecisionOptionRef.Banner(Banner.DarkestSecret)

  private def start(board: ReadyGame) =
    rules.startWalker(Ready(board), ActionRef.Challenge, active(board))

  private def chooseBanner(state: OathState, actor: PlayerId,
      banner: DecisionOptionRef) = rules.resolveWalker(state, actor,
    "challenge.banner", DecisionAnswer.ChooseOneAnswer(banner))

  private def chooseAmount(state: OathState, actor: PlayerId, amount: Int) =
    rules.resolveWalker(state, actor, "challenge.amount",
      DecisionAnswer.ChooseAmountAnswer(amount))

  private def chooseSites(state: OathState, actor: PlayerId,
      sites: Vector[SiteId]) = rules.resolveWalker(state, actor,
    "challenge.ribbon-site", DecisionAnswer.ChooseManyAnswer(
      sites.map(DecisionOptionRef.Site(_))))

  private def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")
  }

  private def player(board: ReadyGame, id: PlayerId): PlayerState =
    board.game.current.players.find(_.player == id).get

  /** Starts, answers the banner, and returns the state parked at the amount. */
  private def atAmount(board: ReadyGame, banner: DecisionOptionRef): OathState = {
    val started = start(board).getOrElse(fail("a legal Challenge must start"))
    chooseBanner(started.state, active(board), banner)
      .getOrElse(fail("the banner must be accepted")).state
  }

  test("starting spends the Supply and parks on the banner decision") {
    val (board, actor) = ChallengeFixture.ready(resources = 2)
    val started = start(board).getOrElse(fail("a legal Challenge must start"))
    assert(started.events.last.isInstanceOf[WalkerParked])
    assertEquals(player(ready(started.state), actor.player).board.supply,
      SupplyTrack(actor.board.supply.supply - 1))
    assertEquals(ready(started.state).game.current.banners,
      board.game.current.banners)
  }

  test("only banners the actor could take are offered") {
    val (board, actor) = ChallengeFixture.ready(resources = 2, faceup = 0)
    val started = start(board).getOrElse(fail("People's Favor is legal"))
    assert(chooseBanner(started.state, actor.player, ds).isLeft)
    assert(chooseBanner(started.state, actor.player, pf).isRight)
  }

  test("a start with no legal banner is rejected and changes nothing") {
    val (board, _) = ChallengeFixture.ready(resources = 6, favor = 6, faceup = 0)
    assertEquals(start(board), Left(NoPlayableOption("challenge")))
  }

  test("a start with no Supply is rejected") {
    val (board, _) = ChallengeFixture.ready(resources = 2, supply = 0)
    assert(start(board).isLeft)
  }

  test("an enemy-held banner needs co-location") {
    val (base, _) = ChallengeFixture.ready(resources = 2)
    val apart = enemyHolds(base, Banner.PeoplesFavor, 2, colocated = false)
    assertEquals(start(apart), Left(NoPlayableOption("challenge")))
    assert(start(enemyHolds(base, Banner.PeoplesFavor, 2)).isRight)
  }

  test("the amount must exceed the banner and fit the actor's resources") {
    val (board, actor) = ChallengeFixture.ready(resources = 2, favor = 6)
    val parked = atAmount(board, pf)
    assert(chooseAmount(parked, actor.player, 2).isLeft)
    assert(chooseAmount(parked, actor.player, 7).isLeft)
    assert(chooseAmount(parked, actor.player, 3).isRight)
    assert(chooseAmount(parked, actor.player, 6).isRight)
  }

  test("People's Favor: an unclaimed banner is taken and its favor returns to the least banks") {
    val (board, actor) = ChallengeFixture.ready(resources = 2)
    val done = chooseAmount(atAmount(board, pf), actor.player, 3)
      .getOrElse(fail("the amount must be accepted"))
    val after = ready(done.state)
    val current = after.game.current
    assertEquals(current.banners.peoplesFavor.holder, Some(actor.player))
    assertEquals(current.banners.peoplesFavor.favor, 3)
    assertEquals(player(after, actor.player).board.favor, actor.board.favor - 3)
    assertEquals(player(after, actor.player).board.supply,
      SupplyTrack(actor.board.supply.supply - 1))
    val expected = BannerRules.addFavor(board.banks.favor,
      BannerRules.raidFavorReturn(board.banks.favor, 2))
    assertEquals(after.banks.favor, expected)
    assertEquals(current.walkerPending, None)
    assertEquals(current.title.holder, Some(actor.player))
  }

  test("People's Favor: an enemy-held banner moves to the challenger") {
    val (base, actor) = ChallengeFixture.ready(resources = 2)
    val board = enemyHolds(base, Banner.PeoplesFavor, 2)
    val done = chooseAmount(atAmount(board, pf), actor.player, 3)
      .getOrElse(fail("the amount must be accepted"))
    val after = ready(done.state).game.current
    assertEquals(after.banners.peoplesFavor.holder, Some(actor.player))
    assertEquals(after.banners.peoplesFavor.favor, 3)
  }

  test("Wandering Flame: a unique least site takes secrets one at a time, with no site decision") {
    val (base, actor) = ChallengeFixture.ready(resources = 3,
      banner = Banner.DarkestSecret)
    val Vector(a, b, c) = base.game.current.map.inPlay.take(3): @unchecked
    val board = withSiteSecrets(base, Map(a -> 0, b -> 1, c -> 5))
    val done = chooseAmount(atAmount(board, ds), actor.player, 4)
      .getOrElse(fail("the amount must be accepted"))
    val after = ready(done.state)
    val sites = after.game.current.map.sites
    assertEquals((sites(a).tokens.secrets, sites(b).tokens.secrets,
      sites(c).tokens.secrets), (2, 2, 5))
    assertEquals(after.game.current.banners.darkestSecret.holder,
      Some(actor.player))
    assertEquals(after.game.current.banners.darkestSecret.secrets, 4)
    assertEquals(player(after, actor.player).board.faceUpSecrets,
      actor.board.faceUpSecrets - 4)
  }

  test("Wandering Flame: fewer secrets than tied sites parks one choose-many, then finishes") {
    val (base, actor) = ChallengeFixture.ready(resources = 2,
      banner = Banner.DarkestSecret)
    val Vector(a, b, c) = base.game.current.map.inPlay.take(3): @unchecked
    val board = withSiteSecrets(base, Map(a -> 0, b -> 0, c -> 0))
    val parked = chooseAmount(atAmount(board, ds), actor.player, 3)
      .getOrElse(fail("the amount must be accepted"))
    assert(parked.events.last.isInstanceOf[WalkerParked])
    assert(chooseSites(parked.state, actor.player, Vector(a)).isLeft)
    assert(chooseSites(parked.state, actor.player, Vector(a, a)).isLeft)
    val outsider = base.game.current.map.inPlay.find(id =>
      !Set(a, b, c).contains(id)).get
    assert(chooseSites(parked.state, actor.player, Vector(a, outsider)).isLeft)
    val done = chooseSites(parked.state, actor.player, Vector(a, c))
      .getOrElse(fail("two tied sites must be accepted"))
    val after = ready(done.state)
    val sites = after.game.current.map.sites
    assertEquals((sites(a).tokens.secrets, sites(b).tokens.secrets,
      sites(c).tokens.secrets), (1, 0, 1))
    assertEquals(after.game.current.banners.darkestSecret.holder,
      Some(actor.player))
    assertEquals(after.game.current.walkerPending, None)
  }

  test("Wandering Flame: an enemy holder gets the retained half back") {
    val (base, actor) = ChallengeFixture.ready(resources = 5,
      banner = Banner.DarkestSecret)
    val board = withSiteSecrets(enemyHolds(base, Banner.DarkestSecret, 5), Map.empty)
    val rival = enemy(board)
    val sites = board.game.current.map.inPlay
    val parked = chooseAmount(atAmount(board, ds), actor.player, 6)
      .getOrElse(fail("the amount must be accepted"))
    // 5 secrets: 2 are placed (5 / 2), 3 return to the holder. All sites tie
    // at 10, and 2 secrets are fewer than the tied sites, so the actor picks.
    assert(parked.events.last.isInstanceOf[WalkerParked])
    val done = chooseSites(parked.state, actor.player, sites.take(2))
      .getOrElse(fail("two tied sites must be accepted"))
    val after = ready(done.state)
    assertEquals(player(after, rival.player).board.faceUpSecrets,
      rival.board.faceUpSecrets + 3)
    assertEquals(after.game.current.banners.darkestSecret.holder,
      Some(actor.player))
    assertEquals(after.game.current.banners.darkestSecret.secrets, 6)
  }

  /** Adds Darkest Secret to the banner decision and widens the amount by two,
    * which is exactly what a power hooking those windows would do.
    */
  private final case class WidenChallenge(id: PowerId) extends ContributingPower {
    def source: RuleSourceRef = RuleSourceRef.Banner("test")
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.ChallengeBannerSelection -> Vector(Transform((_, operations) =>
        operations.map {
          case decide: Decide => decide.query match {
            case DecisionQuery.ChooseOne(options, heading) => decide.copy(query =
              DecisionQuery.ChooseOne(options :+ DecisionOption.Banner(ds),
                heading)): Operation
            case _ => decide
          }
          case other => other
        })),
      PowerWindow.ChallengeAmountSelection -> Vector(Transform((_, operations) =>
        operations.map {
          case decide: Decide => decide.query match {
            case query: DecisionQuery.ChooseAmount =>
              decide.copy(query = query.copy(max = query.max + 2)): Operation
            case _ => decide
          }
          case other => other
        })))
  }

  test("a power's transforms reach the banner and amount selections") {
    val (board, actor) = ChallengeFixture.ready(resources = 2, faceup = 0)
    val tree = ChallengeProcedure.build(catalog, board, actor.player, Vector.empty)
      .getOrElse(fail("the tree must build"))
    val powers = WalkerPowers(Vector(WidenChallenge(PowerId("test.widen-challenge"))))
    val Right(WalkerOutcome.Parked(atBanner, _)) =
      ProcedureWalker.advance(board, tree, None, powers): @unchecked
    val banner = ProcedureWalker.parkedDecide(board, tree, atBanner, powers)
      .getOrElse(fail("the walk must park on the banner decision"))
    assertEquals(banner.query match {
      case DecisionQuery.ChooseOne(options, _) => options.map(_.ref)
      case _ => Vector.empty
    }, Vector[DecisionOptionRef](pf, ds))
    val Right(WalkerOutcome.Parked(atAmount, _)) = ProcedureWalker.resolve(board,
      tree, atBanner, Answered("challenge.banner",
        DecisionAnswer.ChooseOneAnswer(pf), actor.player), powers): @unchecked
    val amount = ProcedureWalker.parkedDecide(board, tree, atAmount, powers)
      .getOrElse(fail("the walk must park on the amount decision"))
    assertEquals(amount.query match {
      case query: DecisionQuery.ChooseAmount => (query.min, query.max)
      case _ => (0, 0)
    }, (3, actor.board.favor + 2))
  }

  test("a completed Challenge replays exactly from its journal") {
    val wealthSite = catalog.sites.find(_.startingResources.favor > 0).get.id
    val orderedSites = wealthSite +: sites.filterNot(_ == wealthSite).take(7)
    val replayPlan = plan.copy(orderedSites = orderedSites)
    val (setupState, setupEvents) = execute(setup, replayPlan)
    val activeId = setupState.asInstanceOf[Ready].value.game.current.turn.activePlayer
    val wealth = rules.startWalker(setupState, ActionRef.TakeWealth, activeId,
      Vector.empty, Vector(DecisionOptionRef.Button("favor"))).toOption.get
    val act = rules.startWalker(wealth.state, PhaseTransitionRef.EndWake, activeId)
      .toOption.get
    val prior = BannerRules.resources(act.state.asInstanceOf[Ready].value.game.current,
      Banner.PeoplesFavor)
    val started = rules.startWalker(act.state, ActionRef.Challenge, activeId)
      .getOrElse(fail("Challenge must start"))
    val banner = chooseBanner(started.state, activeId, pf)
      .getOrElse(fail("banner"))
    val done = chooseAmount(banner.state, activeId, prior + 1)
      .getOrElse(fail("amount"))
    val events = setupEvents ++ wealth.events ++ act.events ++ started.events ++
      banner.events ++ done.events
    val replayed = new EventReplayEngine(rules).replay(events.zipWithIndex.map {
      case (event, index) => RecordedEvent(index.toLong, event)
    }).toOption.get
    assertEquals(replayed, done.state)
  }
}
```

(`setup`, `plan`, `sites`, `execute`, `catalog` come from `FirstGameSetupFixture._`; if `setup` is not exported there, add `private val setup = new FirstGameSetupRules(catalog)` with `import oathdigital.gameplay.setup.FirstGameSetupRules`, as `ChallengeSuite` does.)

- [ ] **Step 2: Run to verify failure**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.ChallengeProcedureSuite"`
Expected: FAIL to compile (`ActionRef.Challenge` not found).

- [ ] **Step 3: Implement**

`ProcedureRef.scala`: add `case object Challenge extends ActionRef { val key = "challenge" }` beside `Trade`, and append `Challenge` to `all`.

`ChallengeRibbon.scala`:

```scala
package oathdigital.gameplay.actions.challenge

import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

/** What a Challenge does with the challenged banner's resources before the
  * challenger pays, per banner (rule W: every state-derived effect is a
  * `BuildOps` or a lazily selected `Branch`, so a resumed walk never rebuilds
  * it from a board an earlier step has changed).
  *
  * People's Favor returns every favor to the least-favor banks, leftmost
  * first. Wandering Flame first returns the previous holder's retained half,
  * then places the rest on the least-stocked sites: each pass places one
  * secret on every tied site while it can, and when fewer secrets remain than
  * tied sites the player picks which of them, once.
  */
private[challenge] object ChallengeRibbon {
  val siteDecisionId = "challenge.ribbon-site"

  private val secretBanner = PositionedLocation(
    Location.OnBanner(Banner.DarkestSecret))

  def steps(actor: PlayerId, banner: Banner): Vector[Operation] = banner match {
    case Banner.PeoplesFavor =>
      Vector(BuildOps((ready, _) => Right(favorReturns(ready))))
    case Banner.DarkestSecret => Vector(
      BuildOps((ready, _) => Right(retainedHalf(ready))),
      Repeat((ready, _) => BannerRules.resources(ready.game.current,
        Banner.DarkestSecret) > 0,
        Branch((ready, _) => pass(ready, actor))))
  }

  private def favorReturns(ready: ReadyGame): Vector[CoreOperation] = {
    val amount = BannerRules.resources(ready.game.current, Banner.PeoplesFavor)
    BannerRules.raidFavorReturn(ready.banks.favor, amount).map(suit =>
      Move(Piece.Favor(1),
        PositionedLocation(Location.OnBanner(Banner.PeoplesFavor)),
        PositionedLocation(Location.FavorBank(suit))))
  }

  /** The previous holder keeps `P - P / 2` of the `P` secrets; an unclaimed
    * banner keeps none. Afterwards every secret left on the banner is one to
    * place, so the count still to place is always the banner's own total.
    */
  private def retainedHalf(ready: ReadyGame): Vector[CoreOperation] = {
    val current = ready.game.current
    val total = BannerRules.resources(current, Banner.DarkestSecret)
    BannerRules.holder(current, Banner.DarkestSecret).toVector.flatMap { holder =>
      val kept = total - total / 2
      Option.when(kept > 0)(Move(Piece.Secrets(kept), secretBanner,
        PositionedLocation(Location.PlayArea(holder))))
    }
  }

  private def pass(ready: ReadyGame, actor: PlayerId): Vector[Operation] = {
    val current = ready.game.current
    val remaining = BannerRules.resources(current, Banner.DarkestSecret)
    val tied = BannerRules.leastSites(current, Vector.empty)
    if (remaining >= tied.size)
      Vector(BuildOps((_, _) => Right(tied.map(place))))
    else Vector(
      // No window on this Decide: the enclosing `ChallengeRibbon` sequence
      // already gathered it, and a second gather would apply a power twice.
      Decide(siteDecisionId, actor, DecisionQuery.ChooseMany(remaining,
        tied.map(id => DecisionOption.Site(DecisionOptionRef.Site(id))),
        Some(s"Place $remaining secrets on tied least-stocked sites"))),
      BuildOps((_, pending) => chosen(pending).map(_.map(place))))
  }

  private def place(site: SiteId): CoreOperation = Move(Piece.Secrets(1),
    secretBanner, PositionedLocation(Location.Site(site)))

  private def chosen(pending: PendingTree): Either[OathViolation, Vector[SiteId]] =
    pending.answered.findLast(_.decisionId == siteDecisionId).map(_.answer) match {
      case Some(DecisionAnswer.ChooseManyAnswer(refs)) => Right(refs.collect {
        case DecisionOptionRef.Site(id) => id
      }.sortBy(_.value))
      case _ => Left(OathViolation.InvalidEventOrder(
        "the Challenge ribbon has no answered site choice"))
    }
}
```

`ChallengeProcedure.scala`:

```scala
package oathdigital.gameplay.actions.challenge

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathLifecycle, PowerRuntime}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

/** Challenge on the walker: spend one Supply, choose a banner, choose how many
  * resources replace it, return the banner's resources (the ribbon), then pay
  * the replacement and take the banner. Takes no start selection.
  *
  * The Supply is spent first so no decision reads it. The banner and the
  * amount are decided before any resource moves, so both read the board as
  * the player sees it; the ribbon and the payment are built when reached.
  */
object ChallengeProcedure {
  val bannerDecisionId: String = "challenge.banner"
  val amountDecisionId: String = "challenge.amount"
  val decisionIds: Set[String] =
    Set(bannerDecisionId, amountDecisionId, ChallengeRibbon.siteDecisionId)

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    _ <- noStartArgs(args)
    _ <- OathLifecycle.validateAct(OathState.Ready(state), actor)
    _ <- PowerRuntime.requireAudited(catalog)
    _ <- supportedFaces(state)
    _ <- Either.cond(legalBanners(state, actor).nonEmpty, (),
      OathViolation.NoPlayableOption(ActionRef.Challenge.key))
  } yield tree(actor)

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    noStartArgs(args).map(_ => tree(actor))

  /** The banners the actor could challenge now, in the banners' declared
    * order: not already held by the actor, the holder (if any) at the actor's
    * site, and strictly more relevant faceup resources than the banner holds.
    * Supply is a cost owned by `SpendSupply`, not a fact about a banner.
    */
  def legalBanners(state: ReadyGame, actor: PlayerId): Vector[Banner] = {
    val current = state.game.current
    current.players.find(_.player == actor).toVector.flatMap { player =>
      Banner.all.filter { banner =>
        val holder = BannerRules.holder(current, banner)
        !holder.contains(actor) &&
          holder.forall(rival => player.pawnSite.nonEmpty && player.pawnSite ==
            current.players.find(_.player == rival).flatMap(_.pawnSite)) &&
          BannerRules.playerResources(player, banner) >
            BannerRules.resources(current, banner)
      }
    }
  }

  private def noStartArgs(args: Vector[DecisionOptionRef])
      : Either[OathViolation, Unit] = Either.cond(args.isEmpty, (),
    OathViolation.InvalidEventOrder(
      s"walker procedure ${ActionRef.Challenge.key} takes no start selection, " +
        s"got ${args.map(_.kind).mkString(", ")}"))

  /** The ribbon rules below are the printed ones for People's Favor `Mob` and
    * Darkest Secret `WanderingFlame` only.
    */
  private def supportedFaces(state: ReadyGame): Either[OathViolation, Unit] = {
    val banners = state.game.current.banners
    Either.cond(banners.peoplesFavor.active == PeoplesFavorFace.Mob &&
      banners.darkestSecret.active == DarkestSecretFace.WanderingFlame, (),
      OathViolation.UnsupportedBannerState("unsupported active banner face"))
  }

  private def tree(actor: PlayerId): Operation = Sequence(Vector[Operation](
    Sequence(Vector[Operation](SpendSupply(actor, 1)),
      Some(PowerWindow.ChallengeCost)),
    Branch((ready, _) => Vector(Decide(bannerDecisionId, actor,
      DecisionQuery.ChooseOne(legalBanners(ready, actor).map(banner =>
        DecisionOption.Banner(DecisionOptionRef.Banner(banner))),
        heading = Some("Choose a banner to Challenge")),
      window = Some(PowerWindow.ChallengeBannerSelection)))),
    Branch((ready, pending) => bannerOf(pending).toVector.map(banner =>
      amountDecision(ready, actor, banner))),
    Branch((ready, pending) => effects(ready, actor, pending))),
    Some(PowerWindow.ChallengeActionEligibility))

  private def amountDecision(ready: ReadyGame, actor: PlayerId,
      banner: Banner): Operation = {
    val current = ready.game.current
    val prior = BannerRules.resources(current, banner)
    val own = current.players.find(_.player == actor)
      .fold(0)(BannerRules.playerResources(_, banner))
    val unit = banner match {
      case Banner.PeoplesFavor => "favor"
      case Banner.DarkestSecret => "secrets"
    }
    Decide(amountDecisionId, actor, DecisionQuery.ChooseAmount(prior + 1,
      math.max(prior + 1, own),
      Some(s"Place more than $prior $unit to take ${banner.key}"),
      "Take banner"),
      window = Some(PowerWindow.ChallengeAmountSelection))
  }

  /** Reached only after both answers exist. Selecting reads the two answers
    * and the banner's holder, which no earlier step changes; the ribbon and
    * the payment amount are built when walked.
    */
  private def effects(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    (for {
      banner <- bannerOf(pending)
      amount <- amountOf(pending)
    } yield Vector[Operation](
      Sequence(ChallengeRibbon.steps(actor, banner),
        Some(PowerWindow.ChallengeRibbon)),
      Sequence(Vector[Operation](payment(actor, banner, amount),
        custody(ready, actor, banner)), Some(PowerWindow.ChallengePlacement))))
      .getOrElse(Vector[Operation](BuildOps((_, _) => Left(
        OathViolation.InvalidEventOrder(
          "Challenge reached its effects without a banner and an amount")))))

  private def payment(actor: PlayerId, banner: Banner, amount: Int): Operation =
    Move(banner match {
      case Banner.PeoplesFavor => Piece.Favor(amount)
      case Banner.DarkestSecret => Piece.Secrets(amount)
    }, PositionedLocation(Location.PlayArea(actor)),
      PositionedLocation(Location.OnBanner(banner)))

  private def custody(ready: ReadyGame, actor: PlayerId,
      banner: Banner): Operation =
    Move(Piece.Banner(banner),
      BannerRules.holder(ready.game.current, banner).fold(
        PositionedLocation(Location.SharedBank))(holder =>
        PositionedLocation(Location.PlayArea(holder))),
      PositionedLocation(Location.PlayArea(actor)))

  private def bannerOf(pending: PendingTree): Option[Banner] =
    pending.answered.collectFirst {
      case Answered(`bannerDecisionId`, DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.Banner(banner)), _) => banner
    }

  private def amountOf(pending: PendingTree): Option[Int] =
    pending.answered.collectFirst {
      case Answered(`amountDecisionId`, DecisionAnswer.ChooseAmountAnswer(n), _) =>
        n
    }
}
```

`WalkerProcedureRegistry.scala`: import `oathdigital.gameplay.actions.challenge.ChallengeProcedure` and add after the Trade entry:

```scala
    /** Challenge. Its first step spends the Supply, so a start runs an
      * operation before its first decision and cannot use the playable-option
      * preview gate; the banner decision's own option filter is the gate.
      */
    ActionRef.Challenge -> Entry(
      fallbackKind = Some(MajorActionKind.Challenge),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.ChallengeModifierSelection),
      continuationFor = (decisionId, actor, decision) =>
        Option.when(ChallengeProcedure.decisionIds.contains(decisionId))(
          OathContinue.AwaitingBannerDecision(actor, decision)),
      build = ChallengeProcedure.build,
      rebuild = ChallengeProcedure.rebuild),
```

Update `ActionValuesSuite` and `WalkerProcedureRegistrySuite` for the new ref (each lists `ActionRef.all`; add `ActionRef.Challenge` where the Economy commit added `Muster` and `Trade`: `git show b241748 -- src/test/scala/oathdigital/model/ActionValuesSuite.scala src/test/scala/oathdigital/gameplay/walker/WalkerProcedureRegistrySuite.scala` shows the shape).

- [ ] **Step 4: Run to verify pass**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.ChallengeProcedureSuite oathdigital.model.ActionValuesSuite oathdigital.gameplay.walker.WalkerProcedureRegistrySuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS. Debug order if a Wandering Flame test fails: print `parked.events.last`; a tie count off by one means `BannerRules.leastSites(current, Vector.empty)` read the banner secrets in its totals (it must not: it sums site tokens only).

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/challenge src/main/scala/oathdigital/model/ProcedureRef.scala src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala src/test
git commit -m "feat(challenge): run Challenge on the walker" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 7: Place Banner Resource as a walker procedure

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/challenge/PlaceBannerResourceProcedure.scala`
- Modify: `src/main/scala/oathdigital/model/ProcedureRef.scala` (add `ActionRef.PlaceBannerResource`, key `"place-banner-resource"`, and list it in `all`), `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`
- Test: `src/test/scala/oathdigital/gameplay/PlaceBannerResourceProcedureSuite.scala`; update `ActionValuesSuite` and `WalkerProcedureRegistrySuite` as in Task 6

**Interfaces:**
- Consumes: `ChallengeFixture` (Task 6), the windows (Task 5), `ChooseAmount` (Task 2).
- Produces: `PlaceBannerResourceProcedure.build/rebuild` (registry function shape), `.heldBanners(state: ReadyGame, actor: PlayerId): Vector[Banner]`, decision ids `place-banner-resource.banner` and `place-banner-resource.amount`, `ActionRef.PlaceBannerResource`.

- [ ] **Step 1: Write the failing test**

```scala
package oathdigital.gameplay

import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation.NoPlayableOption

class PlaceBannerResourceProcedureSuite extends munit.FunSuite {
  import ChallengeFixture._

  private val rules = new OathRules(catalog)
  private val ds = DecisionOptionRef.Banner(Banner.DarkestSecret)

  /** The actor holds Darkest Secret with one secret on it. */
  private def holding: (ReadyGame, PlayerState) = {
    val (base, actor) = ChallengeFixture.ready(banner = Banner.DarkestSecret,
      resources = 1)
    base.updateCurrent(current => current.copy(banners = current.banners.copy(
      darkestSecret = current.banners.darkestSecret.copy(
        holder = Some(actor.player))))) -> actor
  }

  private def start(board: ReadyGame) = rules.startWalker(Ready(board),
    ActionRef.PlaceBannerResource, active(board))

  test("placing resources moves faceup secrets onto the held banner for no Supply") {
    val (board, actor) = holding
    val started = start(board).getOrElse(fail("a held banner must allow placing"))
    val banner = rules.resolveWalker(started.state, actor.player,
      "place-banner-resource.banner", DecisionAnswer.ChooseOneAnswer(ds))
      .getOrElse(fail("the banner must be accepted"))
    val done = rules.resolveWalker(banner.state, actor.player,
      "place-banner-resource.amount", DecisionAnswer.ChooseAmountAnswer(2))
      .getOrElse(fail("the amount must be accepted"))
    val Ready(after) = done.state: @unchecked
    val player = after.game.current.players.find(_.player == actor.player).get
    assertEquals(player.board.faceUpSecrets, actor.board.faceUpSecrets - 2)
    assertEquals(player.board.faceDownSecrets, actor.board.faceDownSecrets)
    assertEquals(player.board.supply, actor.board.supply)
    assertEquals(after.game.current.banners.darkestSecret.secrets, 3)
    assertEquals(after.game.current.walkerPending, None)
  }

  test("the amount ranges from one to the actor's relevant resources") {
    val (board, actor) = holding
    val started = start(board).getOrElse(fail("must start"))
    val banner = rules.resolveWalker(started.state, actor.player,
      "place-banner-resource.banner", DecisionAnswer.ChooseOneAnswer(ds))
      .getOrElse(fail("banner"))
    Vector(0, actor.board.faceUpSecrets + 1).foreach(amount =>
      assert(rules.resolveWalker(banner.state, actor.player,
        "place-banner-resource.amount",
        DecisionAnswer.ChooseAmountAnswer(amount)).isLeft, s"amount $amount"))
    assert(rules.resolveWalker(banner.state, actor.player,
      "place-banner-resource.amount",
      DecisionAnswer.ChooseAmountAnswer(actor.board.faceUpSecrets)).isRight)
  }

  test("only a banner the actor holds is offered") {
    val (board, actor) = holding
    val started = start(board).getOrElse(fail("must start"))
    assert(rules.resolveWalker(started.state, actor.player,
      "place-banner-resource.banner", DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Banner(Banner.PeoplesFavor))).isLeft)
  }

  test("a start with no held banner or no resources is rejected") {
    val (unheld, _) = ChallengeFixture.ready(resources = 1)
    assertEquals(start(unheld),
      Left(NoPlayableOption("place-banner-resource")))
    val (board, actor) = holding
    val broke = board.updateCurrent(current => current.copy(players =
      current.players.map(p => if (p.player == actor.player)
        p.copy(board = p.board.copy(faceUpSecrets = 0)) else p)))
    assertEquals(start(broke), Left(NoPlayableOption("place-banner-resource")))
  }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.PlaceBannerResourceProcedureSuite"`
Expected: FAIL to compile.

- [ ] **Step 3: Implement**

`ProcedureRef.scala`: `case object PlaceBannerResource extends ActionRef { val key = "place-banner-resource" }`; append to `all`.

`PlaceBannerResourceProcedure.scala`:

```scala
package oathdigital.gameplay.actions.challenge

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathLifecycle, PowerRuntime}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

/** Place Banner Resource on the walker: the holder of a banner moves favor or
  * faceup secrets from their own board onto it, for no Supply. Takes no start
  * selection; the banner and the amount are decisions.
  */
object PlaceBannerResourceProcedure {
  val bannerDecisionId: String = "place-banner-resource.banner"
  val amountDecisionId: String = "place-banner-resource.amount"
  val decisionIds: Set[String] = Set(bannerDecisionId, amountDecisionId)

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    _ <- noStartArgs(args)
    _ <- OathLifecycle.validateAct(OathState.Ready(state), actor)
    _ <- PowerRuntime.requireAudited(catalog)
    _ <- Either.cond(heldBanners(state, actor).nonEmpty, (),
      OathViolation.NoPlayableOption(ActionRef.PlaceBannerResource.key))
  } yield tree(actor)

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    noStartArgs(args).map(_ => tree(actor))

  /** Banners the actor holds and could put at least one resource on. */
  def heldBanners(state: ReadyGame, actor: PlayerId): Vector[Banner] = {
    val current = state.game.current
    current.players.find(_.player == actor).toVector.flatMap(player =>
      Banner.all.filter(banner => BannerRules.holder(current, banner)
        .contains(actor) && BannerRules.playerResources(player, banner) > 0))
  }

  private def noStartArgs(args: Vector[DecisionOptionRef])
      : Either[OathViolation, Unit] = Either.cond(args.isEmpty, (),
    OathViolation.InvalidEventOrder(
      s"walker procedure ${ActionRef.PlaceBannerResource.key} takes no start " +
        s"selection, got ${args.map(_.kind).mkString(", ")}"))

  private def tree(actor: PlayerId): Operation = Sequence(Vector[Operation](
    Branch((ready, _) => Vector(Decide(bannerDecisionId, actor,
      DecisionQuery.ChooseOne(heldBanners(ready, actor).map(banner =>
        DecisionOption.Banner(DecisionOptionRef.Banner(banner))),
        heading = Some("Choose a banner to place resources on")),
      window = Some(PowerWindow.PlaceBannerResourceBannerSelection)))),
    Branch((ready, pending) => bannerOf(pending).toVector.map(banner =>
      amountDecision(ready, actor, banner))),
    Branch((_, pending) => (for {
      banner <- bannerOf(pending)
      amount <- amountOf(pending)
    } yield Vector[Operation](Sequence(Vector[Operation](Move(banner match {
      case Banner.PeoplesFavor => Piece.Favor(amount)
      case Banner.DarkestSecret => Piece.Secrets(amount)
    }, PositionedLocation(Location.PlayArea(actor)),
      PositionedLocation(Location.OnBanner(banner)))),
      Some(PowerWindow.PlaceBannerResourcePlacement))))).getOrElse(
      Vector[Operation](BuildOps((_, _) => Left(OathViolation.InvalidEventOrder(
        "Place Banner Resource reached its move without a banner and an amount"
      ))))))),
    Some(PowerWindow.PlaceBannerResourceEligibility))

  private def amountDecision(ready: ReadyGame, actor: PlayerId,
      banner: Banner): Operation = {
    val own = ready.game.current.players.find(_.player == actor)
      .fold(0)(BannerRules.playerResources(_, banner))
    Decide(amountDecisionId, actor, DecisionQuery.ChooseAmount(1,
      math.max(1, own), Some(s"Place resources on ${banner.key}"),
      "Place resources"),
      window = Some(PowerWindow.PlaceBannerResourceAmountSelection))
  }

  private def bannerOf(pending: PendingTree): Option[Banner] =
    pending.answered.collectFirst {
      case Answered(`bannerDecisionId`, DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.Banner(banner)), _) => banner
    }

  private def amountOf(pending: PendingTree): Option[Int] =
    pending.answered.collectFirst {
      case Answered(`amountDecisionId`, DecisionAnswer.ChooseAmountAnswer(n), _) =>
        n
    }
}
```

`WalkerProcedureRegistry.scala`, after the Challenge entry (import `PlaceBannerResourceProcedure`):

```scala
    /** Place Banner Resource is not a major action: it has no fallback kind
      * and, like Take Wealth, no modifier window.
      */
    ActionRef.PlaceBannerResource -> Entry(
      fallbackKind = None,
      rollDecisionId = None,
      modifierWindow = None,
      continuationFor = (decisionId, actor, decision) =>
        Option.when(PlaceBannerResourceProcedure.decisionIds.contains(decisionId))(
          OathContinue.AwaitingBannerDecision(actor, decision)),
      build = PlaceBannerResourceProcedure.build,
      rebuild = PlaceBannerResourceProcedure.rebuild),
```

Update `ActionValuesSuite` and `WalkerProcedureRegistrySuite` for the new ref.

- [ ] **Step 4: Run to verify pass**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.PlaceBannerResourceProcedureSuite oathdigital.model.ActionValuesSuite oathdigital.gameplay.walker.WalkerProcedureRegistrySuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital src/test
git commit -m "feat(challenge): run Place Banner Resource on the walker" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 8: Start controls, projection and frontend switch-over

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala` (add `starts`), `src/main/scala/oathdigital/gameplay/actions/challenge/ChallengeProcedure.scala` and `PlaceBannerResourceProcedure.scala` (add `startable`), `src/main/scala/oathdigital/application/LegalActionProjector.scala`
- Create: `frontend/src/main/scala/oathdigital/frontend/BannerControls.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala`, `frontend/src/main/scala/oathdigital/frontend/ModifierWorkflow.scala`
- Test: `src/test/scala/oathdigital/gameplay/walker/WalkerPreviewSuite.scala` (add a `starts` test), `src/test/scala/oathdigital/application/ChallengeProjectionSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/BannerControlsSuite.scala`

**Interfaces:**
- Consumes: the two procedures (Tasks 6 and 7).
- Produces: `WalkerSimulation.starts(tree: Operation, state: ReadyGame, powers: WalkerPowers): Boolean`; `ChallengeProcedure.startable(catalog, state, actor, powers: WalkerPowers): Boolean` and the same on `PlaceBannerResourceProcedure`; legal controls `beginChallenge` and `placeBannerResource` derived from them; `BannerControls.render(value, canControl, groups, submit)`.

- [ ] **Step 1: Write the failing tests**

`WalkerPreviewSuite` (add; import `oathdigital.gameplay.ChallengeFixture`, `oathdigital.gameplay.powers.WalkerPowerCatalog` and `oathdigital.gameplay.setup.FirstGameSetupFixture.catalog` if the suite lacks them):

```scala
  test("starts is true for a tree a start would walk and false for one it rejects") {
    val (board, actor) = ChallengeFixture.ready(resources = 2)
    val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog), Vector.empty)
    val spend = Sequence(Vector[Operation](SpendSupply(actor.player, 1)))
    assert(WalkerSimulation.starts(spend, board, powers))
    val unaffordable = Sequence(Vector[Operation](
      SpendSupply(actor.player, actor.board.supply.supply + 1)))
    assert(!WalkerSimulation.starts(unaffordable, board, powers))
  }
```

`ChallengeProjectionSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.ChallengeFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.model.OathState.Ready

class ChallengeProjectionSuite extends munit.FunSuite {
  import ChallengeFixture._

  private def projection(board: ReadyGame, viewer: PlayerId) =
    new GameProjector(catalog).project("challenge", LoadedGame(Ready(board), 9),
      viewer)

  test("a legal Challenge is offered as a start control, not a board-target selection") {
    val (board, actor) = ChallengeFixture.ready(resources = 2)
    val shown = projection(board, actor.player)
    assert(shown.legalControls.contains("beginChallenge"))
    assert(!shown.boardTargetActions.exists(_.actionKind == "challenge"))
  }

  test("no Supply, or no strictly greater resources, withdraws the Challenge control") {
    val (broke, actor) = ChallengeFixture.ready(resources = 2, supply = 0)
    assert(!projection(broke, actor.player).legalControls.contains("beginChallenge"))
    val (equal, actor2) = ChallengeFixture.ready(resources = 6, faceup = 0)
    assert(!projection(equal, actor2.player).legalControls.contains("beginChallenge"))
  }

  test("a held banner with resources offers Place Banner Resource") {
    val (base, actor) = ChallengeFixture.ready(banner = Banner.DarkestSecret,
      resources = 1)
    val held = base.updateCurrent(current => current.copy(banners =
      current.banners.copy(darkestSecret = current.banners.darkestSecret.copy(
        holder = Some(actor.player)))))
    assert(projection(held, actor.player).legalControls
      .contains("placeBannerResource"))
    assert(!projection(base, actor.player).legalControls
      .contains("placeBannerResource"))
  }
}
```

`BannerControlsSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.GameIntent
import org.scalajs.dom

/** The Challenge and Place Banner Resource start controls at the DOM. */
class BannerControlsSuite extends munit.FunSuite {
  private def projection(controls: Vector[String]): GameProjection =
    GameProjection("game", 9L, "act-action-selection", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, controls, ready = true,
      completed = false, actionSelectionOpen = true)

  private def render(controls: Vector[String], canControl: Boolean = true)
      : (Vector[dom.html.Button], Vector[GameIntent]) = {
    var submitted = Vector.empty[GameIntent]
    val groups = new ServerUiSupport.ActionSections
    BannerControls.render(projection(controls), canControl, groups,
      command => submitted :+= command)
    val panel = dom.document.createElement("div")
    groups.appendTo(panel)
    val buttons = panel.querySelectorAll(".act-action").toVector
      .map(_.asInstanceOf[dom.html.Button])
    buttons.foreach(_.click())
    (buttons, submitted)
  }

  test("only the offered controls render and each starts its action") {
    val (buttons, submitted) = render(Vector("placeBannerResource", "beginChallenge"))
    assertEquals(buttons.map(_.textContent),
      Vector("Challenge (1 Supply)", "Place banner resources (0 Supply)"))
    assertEquals(submitted, Vector[GameIntent](
      GameIntent.StartWalker("challenge", Vector.empty),
      GameIntent.StartWalker("place-banner-resource", Vector.empty)))
  }

  test("nothing renders when nothing is offered, and controls disable without control") {
    assertEquals(render(Vector.empty)._1, Vector.empty)
    assert(render(Vector("beginChallenge"), canControl = false)._1.forall(_.disabled))
  }

  test("Challenge starts through the modifier workflow and Place Banner Resource does not") {
    assertEquals(ModifierWorkflow.action(
      GameIntent.StartWalker("challenge", Vector.empty)),
      Some("challenge" -> Map.empty[String, String]))
    assertEquals(ModifierWorkflow.action(
      GameIntent.StartWalker("place-banner-resource", Vector.empty)), None)
  }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.walker.WalkerPreviewSuite oathdigital.application.ChallengeProjectionSuite" "frontend/testOnly oathdigital.frontend.BannerControlsSuite"`
Expected: FAIL to compile.

- [ ] **Step 3: Implement**

`WalkerSimulation.scala`, beside `run`:

```scala
  /** Whether a freshly built tree could start now: the same restriction check
    * and first walk a start performs, with nothing persisted. A tree that
    * parks and one that finishes both start; a rejected cost or restriction
    * does not. This is how a start control learns Supply affordability without
    * a second rule beside `SpendSupply`.
    */
  def starts(tree: Operation, state: ReadyGame, powers: WalkerPowers): Boolean =
    guarded {
      ProcedureWalker.restrictionViolations(tree, powers, state,
        state.game.current.turn.activePlayer)
        .headOption.toLeft(())
        .flatMap(_ => ProcedureWalker.advance(state, tree, None, powers))
    }.isRight
```

`ChallengeProcedure` (add import `oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}`):

```scala
  /** Whether Challenge could start now: the gates pass and the first walk
    * (the Supply cost, up to the banner decision) is accepted.
    */
  def startable(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Boolean =
    build(catalog, state, actor, Vector.empty)
      .exists(WalkerSimulation.starts(_, state, powers))
```

`PlaceBannerResourceProcedure`: the same method (`build` is its own).

`LegalActionProjector.scala`: add beside `musterStartable`:

```scala
  private def challengeStartable(context: ScopedProjectionContext): Boolean =
    ChallengeProcedure.startable(catalog, context.ready, context.active.player,
      WalkerPowers.selected(walkerPowerCatalog, Vector.empty))

  private def placeBannerResourceStartable(
      context: ScopedProjectionContext): Boolean =
    PlaceBannerResourceProcedure.startable(catalog, context.ready,
      context.active.player, WalkerPowers.selected(walkerPowerCatalog, Vector.empty))
```

(import `oathdigital.gameplay.actions.challenge.{ChallengeProcedure, PlaceBannerResourceProcedure}`). In the Act-phase control list replace the two legacy entries with:

```scala
          Option.when(challengeStartable(context))("beginChallenge"),
          Option.when(placeBannerResourceStartable(context))("placeBannerResource"),
```

Delete the `val challenges = ChallengeRules.legal(...)...` block and the `selection("challenge", "Choose a banner to Challenge", challenges),` line in the board-target vector. Remove the `ChallengeRules` import from that file (keep `BannerRules` if still used elsewhere in it).

`BannerControls.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand}
import ServerUiSupport._

/** The Act-phase start controls for Challenge and Place Banner Resource. Each
  * is offered by the engine only while its dry run starts, so this layer
  * decides nothing: it draws what `legalControls` names. The banner and the
  * amount are chosen at the parked decisions, not here.
  */
private[frontend] object BannerControls {
  private final case class Control(control: String, kind: String,
      label: String, command: GameCommand)

  private val controls = Vector(
    Control("beginChallenge", "challenge", "Challenge (1 Supply)",
      GameCommand.StartWalker("challenge", Vector.empty)),
    Control("placeBannerResource", "place-banner-resource",
      "Place banner resources (0 Supply)",
      GameCommand.StartWalker("place-banner-resource", Vector.empty)))

  def render(value: GameProjection, canControl: Boolean, groups: ActionSections,
      submit: GameCommand => Unit): Unit =
    controls.filter(control => value.legalControls.contains(control.control))
      .foreach { control =>
        val node = button(control.label, s"act-action ${control.kind}-action")
        node.disabled = !canControl
        node.onclick = _ => submit(control.command)
        groups.appendKind(control.kind, node)
      }
}
```

`ActionDecisionRenderer.scala`: replace the whole `if (value.legalControls.contains("placeBannerResource")) { ... }` block (the legacy per-banner number input) with `BannerControls.render(value, canControl, groups, submitCommand)`, placed right after `EconomyControls.render(...)`.

`ModifierWorkflow.scala`: `walkerActions` becomes `Set("search", "recover", "forge", "travel", "muster", "trade", "challenge")`.

- [ ] **Step 4: Run to verify pass**

Run: `./sbtw -no-colors "test" "frontend/test"`
Expected: PASS. Legacy `ChallengeSuite` tests that projected the `challenge` board target or `beginChallenge` from legacy state fail here: the "legal unclaimed banners project as shared-bank targets" test and the owner-only projection test drive the legacy pending state. Delete exactly that one test (`legal unclaimed banners project as shared-bank targets and illegal banners do not`) from `ChallengeSuite` now; `ChallengeProjectionSuite` covers the behaviour. The rest of `ChallengeSuite`, including the owner-only legacy projection test, drives legacy pending state and still passes until Task 10.

- [ ] **Step 5: Commit**

```bash
git add src frontend
git commit -m "feat(challenge): offer Challenge and Place Banner Resource as start controls" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 9: Prove the walker Challenge matches the legacy result

**Files:**
- Create: `src/test/scala/oathdigital/gameplay/ChallengeParitySuite.scala` (deleted in Task 11 with the legacy path)

**Interfaces:**
- Consumes: the legacy `rules.handle(state, ChallengeCommand.…)` path (still present), the walker procedure (Task 6), `ChallengeFixture` (Task 6).

- [ ] **Step 1: Write the parity suite**

```scala
package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.{BannerRules, ChallengeCommand}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** The legacy Challenge and the walker Challenge, run on the same board and
  * compared on the state they leave and on what each viewer is shown. Legacy
  * asked a tie question the walker no longer asks, so the legacy run answers
  * it with the first tied site, which is the choice the walker's `ChooseMany`
  * is answered with here. Parity is possible only in the states legacy
  * accepts, so every fixture is the first-game one.
  */
class ChallengeParitySuite extends munit.FunSuite {
  import ChallengeFixture._

  private val rules = new OathRules(catalog)

  private def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")
  }

  private def legacy(board: ReadyGame, banner: Banner, amount: Int): OathState = {
    val actor = active(board)
    val id = DecisionId("parity")
    var transition = rules.handle(Ready(board),
      ChallengeCommand.Begin(actor, id, banner))
      .getOrElse(fail("legacy Begin must succeed"))
    def pending = ready(transition.state).game.current.pending.collect {
      case challenge: PendingProcedure.Challenge => challenge
    }
    while (pending.exists(_.remainingRibbonResources > 0)) {
      val site = BannerRules.leastSites(ready(transition.state).game.current,
        pending.get.secretsPlaced).head
      transition = rules.handle(transition.state,
        ChallengeCommand.ChooseSecretSite(actor, id, site))
        .getOrElse(fail("legacy site choice must succeed"))
    }
    rules.handle(transition.state, ChallengeCommand.Complete(actor, id, amount))
      .getOrElse(fail("legacy Complete must succeed")).state
  }

  private def walker(board: ReadyGame, banner: Banner, amount: Int): OathState = {
    val actor = active(board)
    val started = rules.startWalker(Ready(board), ActionRef.Challenge, actor)
      .getOrElse(fail("walker start must succeed"))
    val chosen = rules.resolveWalker(started.state, actor, "challenge.banner",
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Banner(banner)))
      .getOrElse(fail("walker banner must be accepted"))
    var step = rules.resolveWalker(chosen.state, actor, "challenge.amount",
      DecisionAnswer.ChooseAmountAnswer(amount))
      .getOrElse(fail("walker amount must be accepted"))
    while (ready(step.state).game.current.walkerPending.nonEmpty) {
      val current = ready(step.state).game.current
      val tied = BannerRules.leastSites(current, Vector.empty)
      val remaining = current.banners.darkestSecret.secrets
      step = rules.resolveWalker(step.state, actor, "challenge.ribbon-site",
        DecisionAnswer.ChooseManyAnswer(
          tied.take(remaining).map(DecisionOptionRef.Site(_))))
        .getOrElse(fail("walker site choice must be accepted"))
    }
    step.state
  }

  private def assertSameOutcome(board: ReadyGame, banner: Banner,
      amount: Int): Unit = {
    val expected = legacy(board, banner, amount)
    val actual = walker(board, banner, amount)
    val (a, b) = (ready(expected), ready(actual))
    assertEquals(b.game.current.players, a.game.current.players)
    assertEquals(b.game.current.banners, a.game.current.banners)
    assertEquals(b.game.current.map, a.game.current.map)
    assertEquals(b.game.current.title, a.game.current.title)
    assertEquals(b.game.current.commonCards, a.game.current.commonCards)
    assertEquals(b.banks, a.banks)
    assertEquals(b.game.campaign, a.game.campaign)
    val projector = new GameProjector(catalog)
    val viewers = Vector(active(board), enemy(board).player)
    viewers.foreach(viewer => assertEquals(
      projector.project("parity", LoadedGame(actual, 20), viewer),
      projector.project("parity", LoadedGame(expected, 20), viewer)))
    assertEquals(projector.projectPublic("parity", LoadedGame(actual, 20)),
      projector.projectPublic("parity", LoadedGame(expected, 20)))
  }

  test("People's Favor: unclaimed banner") {
    val (board, _) = ChallengeFixture.ready(resources = 2)
    assertSameOutcome(board, Banner.PeoplesFavor, 3)
  }

  test("People's Favor: enemy-held banner") {
    val (base, _) = ChallengeFixture.ready(resources = 2)
    assertSameOutcome(enemyHolds(base, Banner.PeoplesFavor, 3),
      Banner.PeoplesFavor, 4)
  }

  test("Wandering Flame: unclaimed banner with a unique least site") {
    val (base, _) = ChallengeFixture.ready(resources = 3,
      banner = Banner.DarkestSecret)
    val Vector(a, b, c) = base.game.current.map.inPlay.take(3): @unchecked
    assertSameOutcome(withSiteSecrets(base, Map(a -> 0, b -> 1, c -> 5)),
      Banner.DarkestSecret, 4)
  }

  test("Wandering Flame: unclaimed banner, fewer secrets than tied sites") {
    val (base, _) = ChallengeFixture.ready(resources = 2,
      banner = Banner.DarkestSecret)
    val Vector(a, b, c) = base.game.current.map.inPlay.take(3): @unchecked
    assertSameOutcome(withSiteSecrets(base, Map(a -> 0, b -> 0, c -> 0)),
      Banner.DarkestSecret, 3)
  }

  test("Wandering Flame: enemy-held banner with every site tied") {
    val (base, _) = ChallengeFixture.ready(resources = 5,
      banner = Banner.DarkestSecret)
    assertSameOutcome(withSiteSecrets(
      enemyHolds(base, Banner.DarkestSecret, 5), Map.empty),
      Banner.DarkestSecret, 6)
  }

  test("Wandering Flame: enemy-held banner with a unique least site") {
    val (base, _) = ChallengeFixture.ready(resources = 4,
      banner = Banner.DarkestSecret)
    val Vector(a, b) = base.game.current.map.inPlay.take(2): @unchecked
    assertSameOutcome(withSiteSecrets(
      enemyHolds(base, Banner.DarkestSecret, 4), Map(a -> 0, b -> 3)),
      Banner.DarkestSecret, 5)
  }

  test("illegal states offer no start and reject a forced one on both paths") {
    val (base, actor) = ChallengeFixture.ready(resources = 2)
    val illegal = Vector(
      "no Supply" -> ChallengeFixture.ready(resources = 2, supply = 0)._1,
      "actor holds the banner" -> ChallengeFixture.ready(resources = 2,
        holder = Some(actor.player))._1,
      "enemy-held without co-location" ->
        enemyHolds(base, Banner.PeoplesFavor, 2, colocated = false),
      "not strictly more" -> ChallengeFixture.ready(resources = 6, favor = 6,
        faceup = 0)._1)
    illegal.foreach { case (name, board) =>
      assert(rules.handle(Ready(board), ChallengeCommand.Begin(active(board),
        DecisionId("x"), Banner.PeoplesFavor)).isLeft, s"legacy: $name")
      assert(rules.startWalker(Ready(board), ActionRef.Challenge, active(board))
        .isLeft, s"walker: $name")
    }
  }
}
```

- [ ] **Step 2: Run**

Run: `./sbtw -no-colors "testOnly oathdigital.gameplay.ChallengeParitySuite"`
Expected: PASS. If a whole-field comparison fails on a field that legitimately differs (for example a journal-shaped field on `game.current`), narrow that one assertion to the differing field's meaning and note why in a comment; do not weaken the players, banners, map, banks or projection assertions. If `actor holds the banner` cannot be built through the fixture because `holder = Some(actor.player)` needs the actor id first, build the board with `ChallengeFixture.ready(resources = 2)` then set `peoplesFavor.holder` to `active(board)` with `updateCurrent`.

- [ ] **Step 3: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/ChallengeParitySuite.scala
git commit -m "test(challenge): prove legacy and walker Challenge agree" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 10: Retire the legacy Challenge commands, intents, projection and panel

**Files (modify unless noted):**
- `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala`, `CommandIntentCodec.scala`, `CommandIntentDecoders.scala`: remove `BeginChallenge`, `ChooseChallengeSecretSite`, `CompleteChallenge`, `PlaceBannerResource` and their codec and decoder cases.
- `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala`, `GameProjectionDto.scala`, `GameProjectionCodec.scala`: remove `ChallengeProjection`, the `challenge` field, its key in the exact-key list, its encode, decode and `decodeChallenge`.
- `src/main/scala/oathdigital/application/GameCommands.scala`, `Authorization.scala`, `GameApplicationService.scala`, `GameIntentMapper.scala`: remove the four commands, their `Authorization` helpers (`beginChallenge`, `chooseChallengeSecretSite`, `completeChallenge`, `placeBannerResource`), the dispatch and action-kind lines, and the four intent mappings.
- `src/main/scala/oathdigital/application/GameProjection.scala`, `ScopedProjectionContext.scala`, `LegalActionProjector.scala`, `PendingProcedureProjector.scala`: remove the `challenge` projection field and its builder, the `PendingProcedure.Challenge` cases in the legal-controls match and the phase match (`challenge-decision`, `challenge-waiting`), and any `ChallengeRules` use.
- `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala`, `package.scala`, `ActionDecisionRenderer.scala`: remove the `("challenge", …) => BeginChallenge` board-target case, `challengeSiteCommands`, `completeChallengeCommand`, the `ChallengeState` alias, and the `value.challenge…` panel block.
- Tests: `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala`, `ProjectionProtocolSuite.scala`; `frontend/src/test/scala/oathdigital/frontend/BoardTargetSelectionStateSuite.scala`, `HttpGameClientSuite.scala`, `ModifierSelectionStateSuite.scala`, `ProtocolTestCommands.scala`, `ServerModeUiSuite.scala`; `src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala`, `PendingWalkerInvariantSuite.scala`, `src/test/scala/oathdigital/server/AuthenticatedGameRoutesSuite.scala`: delete or migrate each reference (each listed file names a legacy Challenge command, intent or projection).

**Interfaces:**
- Consumes: the start controls and walker procedures (Tasks 6 to 8).
- Produces: a build with no client path to the legacy Challenge. The legacy rules, events and `PendingProcedure.Challenge` still exist (Task 11) and are unreachable from any command.

- [ ] **Step 1: Remove the surface and let the compiler list what is left**

Delete the symbols above. Then run the compiler over everything and fix each error by deleting the dead reference or migrating a test:

Run: `./sbtw -no-colors "test:compile" "frontend/test:compile"`
Expected: PASS after the last fix. Test references migrate this way: a test that submitted a legacy Challenge or Place Banner Resource command through the service now starts the walker (`GameCommand.StartWalker`/`rules.startWalker` with `ActionRef.Challenge` or `ActionRef.PlaceBannerResource`) and answers the decisions; a test that only listed the four intents in a round-trip vector simply loses those entries; a test that decoded the `challenge` projection field loses it. Keep a test's other assertions.

- [ ] **Step 2: Prove nothing names the retired surface**

Run:

```bash
grep -rnE "BeginChallenge|ChooseChallengeSecretSite|CompleteChallenge|PlaceBannerResource\(|ChallengeProjection|ChallengeState|challengeSiteCommands|completeChallengeCommand|\"placeBannerResource\"|beginChallenge\(|chooseChallengeSecretSite\(|completeChallenge\(|placeBannerResource\(" src shared frontend --include='*.scala'
```

Expected: only the two string literals `"beginChallenge"` and `"placeBannerResource"` in `LegalActionProjector`, `BannerControls` and `BannerControlsSuite`/`ChallengeProjectionSuite` (the legal-control names), and nothing else. Anything else is a leftover: remove it.

- [ ] **Step 3: Run the full gate**

Run: `./sbtw -no-colors "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add -A shared src frontend
git commit -m "refactor(challenge): retire the legacy Challenge commands, intents and panel" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 11: Delete the legacy Challenge rules, events and pending state

**Files:**
- Delete: `src/main/scala/oathdigital/gameplay/actions/Challenge.scala`, `src/test/scala/oathdigital/gameplay/ChallengeSuite.scala`, `src/test/scala/oathdigital/gameplay/ChallengeParitySuite.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala` (the `handle(state, command: ChallengeCommand)` method with its `Challenge`/`ChallengeCommand` imports, and the four `Challenge.evolve` event cases), `src/main/scala/oathdigital/gameplay/setup/FirstGameSetup.scala` (the four `Banner*` events in the "requires the gameplay evolution" list)
- Modify: `src/main/scala/oathdigital/model/GameEventProtocol.scala` (`BannerChallengeStarted`, `BannerRibbonChoiceMade`, `BannerChallengeCompleted`, `BannerResourcePlaced`), `src/main/scala/oathdigital/serialization/ActionEventCodec.scala`, `GameEventWire.scala` (the four type strings and codec cases), `GameEventJsonSupport.scala` if it names them
- Modify: `src/main/scala/oathdigital/model/PendingProcedures.scala` (remove `PendingProcedure.Challenge`), `src/main/scala/oathdigital/model/GameViolation.scala` (violations left without a user)
- Modify tests: `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala` (the `BannerChallengeStarted`… round-trip case around line 146), `src/test/scala/oathdigital/gameplay/PendingWalkerRulesSuite.scala` (the `"challenge" -> rules.handle(…ChallengeCommand.Begin…)` entry; a walker-pending Challenge park replaces it if the suite lists a pending example per action, otherwise delete the entry), `src/test/scala/oathdigital/gameplay/PowerResolverSuite.scala` and `src/test/scala/oathdigital/gameplay/oathkeeper/OathkeeperProcedureSuite.scala` (migrate any legacy Challenge driving to the walker: `startWalker(ActionRef.Challenge)` then the banner and amount answers, using `ChallengeFixture`)

**Interfaces:**
- Keeps: `OathContinue.AwaitingBannerDecision` (the walker continuation), `BannerRules`, `OathViolation.UnsupportedBannerState`, `OathViolation.NoPlayableOption`, `PowerRuntime`'s `MajorActionKind.Challenge` fallback.
- Deletes: everything that only the legacy Challenge used.

- [ ] **Step 1: Delete the rules, events and pending case**

Remove the files and members listed above. For `GameViolation.scala`, delete a candidate only if a grep proves nothing else reads it:

```bash
for v in ChallengeUnavailable ChallengeOutcomeMismatch ChallengeDecisionMismatch InsufficientFavor InsufficientSecrets; do echo "== $v"; grep -rn "$v" src shared frontend --include='*.scala' | grep -v "GameViolation.scala"; done
```

A violation with no hit outside `GameViolation.scala` is deleted; one with hits (Campaign, Negotiation or the operations layer may use `InsufficientFavor`/`InsufficientSecrets`) stays.

- [ ] **Step 2: Compile and fix**

Run: `./sbtw -no-colors "test:compile" "frontend/test:compile"`
Expected: PASS after removing each dangling reference. Remove imports the deletions leave unused.

- [ ] **Step 3: Prove nothing names the legacy path**

```bash
grep -rnE "ChallengeCommand|Challenge\.(handle|evolve)|ChallengeRules|BannerChallengeStarted|BannerRibbonChoiceMade|BannerChallengeCompleted|BannerResourcePlaced|PendingProcedure\.Challenge|ChallengeSuite|ChallengeParitySuite" src shared frontend docs/superpowers/specs docs/superpowers/plans --include='*.scala' --include='*.md' | grep -v "docs/superpowers/plans/2026-09-19-challenge-walker.md\|docs/superpowers/specs/2026-09-19-challenge-walker-design.md"
```

Expected: no output.

- [ ] **Step 4: Run the full gate**

Run: `./sbtw -no-colors "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS. If `BackendArchitectureSuite` reports a file over 800 lines, split it at a responsibility boundary before continuing.

- [ ] **Step 5: Commit**

```bash
git add -A src shared frontend
git commit -m "refactor(challenge): delete the legacy Challenge rules, events and pending state" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 12: Record the slice in the specs

**Files:**
- Modify: `docs/superpowers/specs/2026-09-19-challenge-walker-design.md`, `docs/superpowers/specs/2026-09-05-procedure-walker-design.md`

- [ ] **Step 1: Amend the Challenge spec to match what was built**

Run this script; each replacement asserts its target occurs exactly once, so a spec edit since this plan was written fails loudly instead of silently:

```bash
python3 - <<'EOF'
p = 'docs/superpowers/specs/2026-09-19-challenge-walker-design.md'
s = open(p).read()
def rep(old, new):
    global s
    assert s.count(old) == 1, old
    s = s.replace(old, new)
rep('> Status: design, not yet planned.',
    '> Status: implemented by [the plan](../plans/2026-09-19-challenge-walker.md).')
rep('Well-formed only if `count` is at least 1 and no more than the option count.',
    'Well-formed only if `1 <= count < options.size`: a count that takes every option is a forced answer, which no decision shape may park on. Challenge parks it only when secrets are fewer than the tied sites.')
rep('Kept: `BannerRules`, moved to its own file as `VisionRules` was',
    'Kept: `OathContinue.AwaitingBannerDecision` (reused as the walker continuation for both procedures), `BannerRules`, moved to its own file as `VisionRules` was')
rep('- `PendingProcedure.Challenge` and `OathContinue.AwaitingBannerDecision`, the',
    '- `PendingProcedure.Challenge`, the')
open(p, 'w').write(s)
EOF
```

Then add one paragraph under "Challenge procedure", after the tree diagram, by hand: the tree has three sibling `Branch`es after the Supply step (banner, amount, effects) because the walker re-runs every enclosing `Branch.select` on each resume, so each is selected only when reached and reads only answered values or state no earlier step changes; and one under "Banner decision": the banner option's cost text is projected from state (`Currently N resources`, and `Unclaimed` for a banner with no holder), not authored on the option, and a start control is offered when `WalkerSimulation.starts` accepts the same first walk a start performs.

- [ ] **Step 2: Update the walker roadmap**

Run `grep -n "Remaining: Search\|Four .PendingProcedure. cases\|Step 3: Search, Economy\|- .Challenge.: Challenge" docs/superpowers/specs/2026-09-05-procedure-walker-design.md` to see the wrapped lines, then apply these replacements with a script like the one above (assert one occurrence each; adjust the old strings to the printed wrapping):

- "Remaining: Search, Economy, Challenge, Campaign, Negotiation, CardPlay, Rest, Visions." becomes "Remaining: Campaign and Negotiation."; append after the Visions sentence: "Challenge and Place Banner Resource are ported and their legacy path deleted; see the Challenge design."
- "Four `PendingProcedure` cases remain (see Migration status); the ninth, `OathkeeperRecipient`, was ported…" becomes "Three `PendingProcedure` cases remain (see Migration status). `OathkeeperRecipient`, once one more, was ported…" (keep the rest of that sentence).
- In "What remains": "Step 3: Search, Economy (Muster/Trade), Challenge, Campaign, Negotiation, CardPlay and Rest. Step 5 has not started." becomes "Step 3: Campaign and Negotiation. Step 5 has not started."; "Four `PendingProcedure` cases remain" becomes "Three"; delete the "- `Challenge`: Challenge." bullet.

- [ ] **Step 3: Run the full gate once more and commit**

Run: `./sbtw -no-colors "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS.

```bash
git add docs
git commit -m "docs: record Challenge and Place Banner Resource as ported to the walker" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Self-review

**Spec coverage.**
- Goal and scope, design rule, rule changes: Global Constraints and Tasks 6 to 8.
- Challenge tree (Supply first, banner, amount, ribbon both banners, payment, custody): Task 6. The banner legality predicate and the empty-start rejection: Task 6 (`legalBanners`, `NoPlayableOption`).
- Place Banner Resource: Task 7.
- Start gates (face gate kept, audited, dropped gates): Task 6 (`supportedFaces`, `requireAudited`).
- Two decision shapes with every codec and the frontend controls: Tasks 1 to 4.
- Nine windows: Task 5.
- Start flow and controls: Task 8.
- Cutover and deletion: Tasks 10 and 11 (events, commands, pending, projection, panel, violations).
- Verification: parity (Task 9, every scenario in the spec's list, including the illegal states), journal round trips (Tasks 1, 2), replay (Task 6), resumed `ChooseMany` reading current totals (Task 6 tie tests, which park mid-ribbon), wrong or stale answers (Tasks 1, 2, 6), architecture rules (Global Constraints, run in Tasks 6 and 7).
- Test-only transform reaching the two selection windows: Task 6 (`a power's transforms reach the banner and amount selections`).
- Documented deviations from the spec are at the top and are folded into the spec in Task 12.

**Placeholder scan.** No "TBD" or "similar to Task N". Task 10 and 11 give symbol lists and grep proofs instead of code because they delete; each names the file and symbol.

**Type consistency.** `ChooseMany(count, options, heading)`, `ChooseManyAnswer(selected)`, `ChooseAmount(min, max, heading, confirmLabel)`, `ChooseAmountAnswer(amount)`, projection fields `count`/`minimum`/`maximum`, wire `ChooseManyWire(options: Vector[DecisionOptionWire])`/`ChooseAmountWire(amount)`, decision ids and `startable`/`legalBanners`/`heldBanners` are spelled the same in every task that uses them.
