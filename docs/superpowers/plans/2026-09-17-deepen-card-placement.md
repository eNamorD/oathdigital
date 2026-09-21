# Deepen Card Placement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put legal placement and replacement discovery beside `CardPlay` validation and planning while preserving walker decisions and gameplay behavior.

**Architecture:** `CardPlay` exposes ordered, domain-level placement choices and remains the operation planner. `CardPlayProcedure` maps those choices into its existing walker tree and revalidates the chosen placement at execution. Silver Tongue still transforms the `SearchPlayAdviser` tree; Search's unkept-card discard loop stays unchanged.

**Tech Stack:** Scala 2.13, sbt, munit.

**Spec:** `docs/superpowers/specs/2026-09-17-deepen-card-placement-design.md`.

## Global Constraints

- Preserve placement rules, intent and replacement order, decision IDs/options/labels, failure text, power timing, Conspiracy handoff, and recorded operations.
- Do not cache an operation plan across a parked decision; final `BuildOps` must call `CardPlay.plannedOperations` on current state.
- Do not change Search's unkept-card discard loop, facedown-adviser start gates, protocol DTOs/codecs, or frontend.
- Keep `PlacementTree` and `PowerWindow.SearchPlayAdviser`; pass effective adviser limits as data to `CardPlay`.
- No new generic policy module, adapter interface, persistent choice type, or dependency.

## File map and interfaces

- `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`: add `Choice` and `legalChoices`; keep `plannedOperations` and all semantic validation here.
- `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`: map domain choices to existing walker buttons and replacement options; remove local legality and replacement-candidate probes.
- `src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala`: characterize domain choice order and end-to-end decision behavior.
- `src/test/scala/oathdigital/gameplay/powers/rest/SilverTongueSuite.scala`: existing power-window tests are preservation gates; edit only if a missing behavior assertion is needed.

---

### Task 1: Expose ordered legal choices from CardPlay

**Files:** Modify `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala:15-48`; test `src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala:20-135`.

**Interfaces:** Produce `CardPlay.Choice(placement: SearchPlacement, replacements: Vector[CardId])` and `CardPlay.legalChoices(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId, card: WorldCardId, origin: CardPlay.Origin, faceupLimit: Int, facedownLimit: Int): Vector[CardPlay.Choice]`. `placement` is the base intent with `replace = None`.

- [ ] **Step 1: Baseline.** Run `./sbtw 'testOnly oathdigital.gameplay.CardPlayProcedureSuite oathdigital.gameplay.powers.rest.SilverTongueSuite oathdigital.gameplay.SearchProcedureSuite oathdigital.gameplay.VisionsSuite'`; require pass before edits. Record existing placement-button order and full-area replacement order from the tests.

- [ ] **Step 2: Write a failing public-seam test.** In `CardPlayProcedureSuite`, add this test. In the existing full-adviser test, assert the faceup choice's replacement IDs equal the current advisers' IDs filtered by `CardPlay.plannedOperations(catalog, full, actor, card, SearchPlacement.Adviser(Orientation.FaceUp, Some(id)), CardPlay.Origin.TemporaryHand).isRight`, preserving their order. In the locked-adviser test, assert no faceup choice. Run the focused suite and require compilation failure because `legalChoices` does not exist.

```scala
test("CardPlay exposes legal placement choices in decision order") {
  val (ready, actor, card) = handState
  val choices = CardPlay.legalChoices(catalog, ready, actor, card,
    CardPlay.Origin.TemporaryHand, 3, 3)
  val expected = Vector[SearchPlacement](SearchPlacement.Discard,
    SearchPlacement.Site(None),
    SearchPlacement.Adviser(Orientation.FaceUp, None),
    SearchPlacement.Adviser(Orientation.FaceDown, None))
  assertEquals(choices.map(_.placement), expected)
}
```

- [ ] **Step 3: Implement choice discovery in CardPlay.** Add the case class and method immediately after `Origin`. Use the following shape; retain existing `plannedOperations` unchanged:

```scala
final case class Choice(placement: SearchPlacement,
    replacements: Vector[CardId])

def legalChoices(catalog: ExecutableCatalog, ready: ReadyGame,
    actor: PlayerId, card: WorldCardId, origin: Origin,
    faceupLimit: Int, facedownLimit: Int): Vector[Choice] = {
  val player = ready.game.current.players.find(_.player == actor)
  val placements = Vector[SearchPlacement](SearchPlacement.Discard,
    SearchPlacement.Site(None),
    SearchPlacement.Adviser(Orientation.FaceUp, None),
    SearchPlacement.Adviser(Orientation.FaceDown, None))
  placements.flatMap { placement =>
    val limit = placement match {
      case SearchPlacement.Adviser(Orientation.FaceUp, _) => faceupLimit
      case _ => facedownLimit
    }
    val direct = plannedOperations(catalog, ready, actor, card,
      placement, origin, limit).isRight
    val candidateIds: Vector[CardId] = placement match {
      case _: SearchPlacement.Site => player.toVector.flatMap(_.pawnSite)
        .flatMap(ready.game.current.map.sites.get)
        .flatMap(_.denizens.map(_.id))
      case SearchPlacement.Adviser(Orientation.FaceUp, _)
          if card.isInstanceOf[VisionId] =>
        player.toVector.flatMap(_.revealedVision).map(_.id)
      case _: SearchPlacement.Adviser => player.toVector.flatMap(_.advisers)
        .filterNot(value => origin == Origin.FacedownAdviser &&
          value.id == card).map(_.id)
      case SearchPlacement.Discard => Vector.empty
    }
    val replacements = if (direct) Vector.empty else candidateIds.filter { id =>
      val selected = placement match {
        case _: SearchPlacement.Site => SearchPlacement.Site(Some(id))
        case value: SearchPlacement.Adviser => value.copy(replace = Some(id))
        case SearchPlacement.Discard => SearchPlacement.Discard
      }
      plannedOperations(catalog, ready, actor, card, selected,
        origin, limit).isRight
    }
    Option.when(direct || replacements.nonEmpty)(Choice(placement, replacements))
  }
}
```

- [ ] **Step 4: Green gate.** Run the focused `CardPlayProcedureSuite` and `SilverTongueSuite`; require pass. Compare domain choices against the existing walker queries in the full-adviser and locked-area fixtures. Commit only Task 1 files as `refactor(cardplay): own legal placement choices`.

### Task 2: Consume choices in walker adapter

**Files:** Modify `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala:78-169`; test `src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala:20-225`.

**Interfaces:** Consume `CardPlay.legalChoices` from Task 1. Keep `CardPlayProcedure.build`, `buildFacedown`, `rebuildFacedown`, and `PlacementTree.withAdviserLimit`/`withFaceupAdviserLimit` signatures unchanged.

- [ ] **Step 1: Characterize the adapter.** In the `temporary-hand card builds a reusable placement decision` test, after obtaining `decision`, add the assertion below. In the full-adviser test, compare the replacement query's complete `options.map(_.ref)` with the faceup choice's `replacements.map { case id: DenizenId => DecisionOptionRef.Denizen(id); case id: VisionId => DecisionOptionRef.Vision(id); case id => DecisionOptionRef.Button(s"replace:${id.kind}:${id.value}") }`. Run `CardPlayProcedureSuite` before the production edit; require pass.

```scala
val domain = CardPlay.legalChoices(catalog, ready, actor, card,
  CardPlay.Origin.TemporaryHand, 3, 3)
val expected = domain.map(_.placement).map {
  case SearchPlacement.Discard => DecisionOptionRef.Button("discard")
  case _: SearchPlacement.Site => DecisionOptionRef.Button("site")
  case SearchPlacement.Adviser(Orientation.FaceUp, _) =>
    DecisionOptionRef.Button("adviser-faceup")
  case SearchPlacement.Adviser(Orientation.FaceDown, _) =>
    DecisionOptionRef.Button("adviser-facedown")
}
assertEquals(decision.query.asInstanceOf[DecisionQuery.ChooseOne]
  .options.map(_.ref), expected)
```

- [ ] **Step 2: Replace local discovery.** In `childrenFor`, keep the `legacyOrigin` conversion. Replace `placements`, `direct`, `replacementIds`, and probe loop with:

```scala
val candidates = CardPlay.legalChoices(catalog, ready, actor, card,
  legacyOrigin, faceupLimit, facedownLimit).map { choice =>
  val ref = choice.placement match {
    case SearchPlacement.Discard => discard
    case _: SearchPlacement.Site => site
    case SearchPlacement.Adviser(Orientation.FaceUp, _) => adviserFaceUp
    case SearchPlacement.Adviser(Orientation.FaceDown, _) => adviserFaceDown
  }
  (ref, choice.placement,
    choice.replacements.map(id => replacementOption(id) -> id))
}
```

  Leave decision construction, selected-answer lookup, selected replacement conversion, `BuildOps` revalidation, and `CardPlayed` hook in place. Remove now-unused local `player` and `VisionId`-specific candidate logic. Do not move presentation labels into `CardPlay`.

- [ ] **Step 3: Focused gate.** Run `./sbtw 'testOnly oathdigital.gameplay.CardPlayProcedureSuite oathdigital.gameplay.powers.rest.SilverTongueSuite oathdigital.gameplay.SearchProcedureSuite oathdigital.gameplay.VisionsSuite oathdigital.application.GameApplicationServiceSuite'`; require pass. Confirm tests cover full/locked adviser areas, full site, Vision replacement, Conspiracy, facedown start, Silver Tongue, and persisted Search replay. Commit Task 2 files as `refactor(cardplay): render walker choices from placement rules`.

### Task 3: Final behavior and architecture gate

**Files:** No production changes expected. If verification exposes a defect, stop and diagnose; do not fold a rule fix into this refactor.

**Interfaces:** No additional interface.

- [ ] **Step 1: Full verification.** Run `./sbtw 'test' 'frontend/test' 'frontend/fastLinkJS'`, `python3 scripts/check-architecture.py`, `python3 scripts/check-markdown-links.py`, and `git diff --check`; require all pass.
- [ ] **Step 2: Review the branch diff.** Use `git diff <pre-task-commit>..HEAD -- src/main/scala/oathdigital/gameplay/actions/CardPlay.scala src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala` and `git status --short`. Verify Search, Silver Tongue, protocol, and frontend production files did not change. Verify decision strings, option order, limit transform, Conspiracy path, and execution-time `plannedOperations` call remain intact.
- [ ] **Step 3: Report.** Summarize exact files and tests, preserve the existing untracked projection documents, and ask the user how to integrate the branch. Do not merge or push without their choice.

## Self-review gate

- [ ] `CardPlay` owns intent and replacement legality; `CardPlayProcedure` owns walker decisions and presentation.
- [ ] Both Search and facedown-adviser play still use the shared subtree.
- [ ] Silver Tongue still modifies the effective adviser limit at `SearchPlayAdviser`.
- [ ] Final operation planning uses current state after each parked decision.
- [ ] No gameplay, protocol, frontend, or Search discard behavior changed.
