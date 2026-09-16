# Search Walker Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Search and facedown-adviser play to a shared walker card-play subtree, activate Silver Tongue and Dazzle, and delete the retired Search path.

**Architecture:** Search owns draw, payment, card selection, and Visions Drawn advancement. Both Search and facedown-adviser play embed one card-play subtree for placement, replacement, and `CardPlayed` effects. Walker events record final operations and choices; replay never reruns powers.

**Tech Stack:** Scala 2.13, sbt, munit, Scala.js frontend, ujson.

**Spec:** `docs/superpowers/specs/2026-09-16-search-walker-design.md` (approved), with the walker and declarative-decision specs linked there.

## Global Constraints

- Preserve current first-game Search gates, stack orientation, temporary-hand ownership, hidden projections, and typed violations.
- Keep `SearchModifierSelection` as the pre-start selection window for Search and facedown-adviser play. Selected IDs survive park/reload/resume; automatic Silver Tongue and Dazzle need no selection.
- Site denizens are not generally discardable. Only existing supported Homeland replacement permission may offer a site-card discard; People's Favor and other unimplemented permissions remain inactive.
- Dazzle skips rule-immune candidates before building discard operations. Never swallow executor errors as immunity.
- Replay applies recorded operations and facts, not current catalog powers. No old-journal compatibility is required in this pre-release repository.
- Keep diagnostics for unimplemented `WHEN PLAYED` handlers. Retire only executable Dazzle and Silver Tongue diagnostic handlers.
- Do not modify `gameplay/walker` or `gameplay/operations` to name a specific power. Preserve the 800-line source cap and architecture check.
- Per task: focused red/green test, `./sbtw "test"`, `python3 scripts/check-architecture.py`, `git diff --check`; tasks touching frontend also run `./sbtw "frontend/test" "frontend/fastLinkJS"`. Commit each independently reviewed task.

## File map and interfaces

- `model/Decisions.scala`, `gameplay/walker/DecisionQueries.scala`: generic `DecisionSection.maxAllowed: Option[Int]`, partition well-formedness and answer bounds; preserve `PartitionAnswer.placements` order.
- `shared/.../projection/ActionProjectionDtos.scala` and `ActionProjectionCodec.scala`, `application/WalkerDecisionProjector.scala`, `frontend/PartitionDecisionState.scala`: carry maximum to UI, keep within-section drag order in submitted answers.
- `gameplay/operations/CoreOperations.scala`, operation mutation/validation, and `serialization/WalkerOperationCodec.scala`: recorded generic Visions Drawn advancement, if no existing operation can express it; semantic `CardPlayed` hook is not recorded as a delta.
- `gameplay/actions/search/SearchProcedure.scala`: Search start gates, source order, payment, draw, card-selection `Partition`, and embedding of card play.
- `gameplay/actions/cardplay/CardPlayProcedure.scala`: reusable placement query, adviser/site replacement branches, operation planning, and `CardPlayed` hook. No walker invocation inside this unit.
- `gameplay/powers/rest/SilverTongue.scala`, `gameplay/powers/whenplayed/Dazzle.scala`, `gameplay/powers/WalkerPowerCatalog.scala`: automatic adviser limit and Dazzle effects.
- `model/ProcedureRef.scala`, `gameplay/walker/WalkerProcedureRegistry.scala`, `gameplay/OathRulesWalker.scala`: registered Search and facedown-adviser entries, modifier selection, start arguments and parked continuations.
- `application/GameApplicationService.scala`, `GameIntentMapper.scala`, `Authorization.scala`, `LegalActionProjector.scala`, `WalkerDecisionProjector.scala`, `frontend/ModifierWorkflow.scala`, `ActionDecisionRenderer.scala`: generic start/resolve routing and scoped controls.
- `gameplay/actions/Search.scala`, `CardPlay.scala`, `MinorActions.scala`, `model/PendingProcedures.scala`, event/codec/projection files: legacy cutover deletions after parity.

Paths above are relative to `src/main/scala/oathdigital/` unless prefixed by `shared/` or `frontend/`. Confirm exact declarations with `rg` before editing; preserve unrelated cases in shared files.

---

### Task 1: Partition capacity and actual discard order

**Files:** Modify `src/main/scala/oathdigital/model/Decisions.scala`, `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala`, `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala`, `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala`, `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`, `frontend/src/main/scala/oathdigital/frontend/PartitionDecisionState.scala`. Create `src/test/scala/oathdigital/gameplay/DecisionQueriesSuite.scala`; test `src/test/scala/oathdigital/application/WalkerDecisionProjectionSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/PartitionDecisionStateSuite.scala`.

**Interfaces:** `DecisionSection(key: String, label: String, minRequired: Int, maxAllowed: Option[Int] = None)`; matching projected section. `PartitionAnswer.placements` remains ordered.

- [ ] **Step 1: Write failing tests.** A Keep section with `minRequired = 1, maxAllowed = Some(1)` rejects two Keep placements; malformed `maximum < minimum` rejects at `wellFormed`; a frontend draft moved to Discard as `C,B,A` submits `C,B,A`, not original option order. Assert existing Forge partition still validates with `None` maximum.

```scala
val sections = Vector(DecisionSection("keep", "Keep", 1, Some(1)),
  DecisionSection("discard", "Discard", 0))
assert(DecisionQueries.accepts("search.cards",
  DecisionQuery.Partition(sections, options),
  DecisionAnswer.PartitionAnswer(twoKeep)).isLeft)
```

- [ ] **Step 2: Run** `./sbtw "testOnly *DecisionQueriesSuite *WalkerDecisionProjectionSuite" "frontend/testOnly *PartitionDecisionStateSuite"`; expect new tests to fail on missing maximum and original-order submission.
- [ ] **Step 3: Implement** generic maximum in model, validator, projection DTO/codec/projector, and `WalkerPartitionDraft`. Change `PartitionDecisionState.placements` to iterate `sections.flatMap(s => itemsIn(s.key).map(_ -> s.key))`; use projected maximum when constructing `PartitionSection`. Ensure `filled` does not put slack in a full section: place remaining items into first section with capacity. The current implementation uses original `items` order for submission and always sends slack to the first section; both are wrong for Search. Do not add a Search-specific answer or wire form.

```scala
def placements: Vector[(String, String)] =
  sections.flatMap(section => itemsIn(section.key).map(_ -> section.key))
```

- [ ] **Step 4: Run** the focused tests and full per-task gates, including frontend.
- [ ] **Step 5: Commit** `feat(walker): bound partition sections and preserve placement order`.

### Task 2: Recorded track change and semantic card-play hook

**Files:** Modify `src/main/scala/oathdigital/gameplay/operations/CoreOperations.scala`, relevant operation validation/mutation files, `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`. Test `src/test/scala/oathdigital/gameplay/OperationExecutorSuite.scala`, `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`, `src/test/scala/oathdigital/gameplay/ProcedureWalkerSuite.scala` (use actual existing suite name found by `rg --files`).

**Interfaces:** A recorded `AdvanceVisionsDrawn(amount: Int)` leaf changes the track by a validated positive amount; use a more general existing track operation if present at implementation time. `CardPlayed(card: WorldCardId, resultingSource: RuleSourceRef)` is a `CoreOperation` with `window = Some(ActionCardPlayed)` and empty children; it is never a recorded delta.

- [ ] **Step 1: Write failing tests.** Advancing the track records and replays the same value; invalid amount or overflow returns typed error. A synthetic tree with `CardPlayed` gathers `ActionCardPlayed`, inserts a test operation, and records that inserted operation, not the empty hook.

```scala
val tree = Sequence(Vector(CardPlayed(dazzleId,
  RuleSourceRef.SiteCard(siteId, dazzleId))))
assertEquals(tree.children.size, 1)
```

- [ ] **Step 2: Run** focused operation, codec, and walker suites; expect compile/test failures.
- [ ] **Step 3: Implement** track leaf through existing executor/state adapter and wire codec with typed bounds; add generic hook composite without embedding power names or replaying the hook as an operation. If current track range has a domain bound, validate it; never use `require` on journal input.
- [ ] **Step 4: Run** focused and per-task gates.
- [ ] **Step 5: Commit** `feat(walker): record Vision track changes and card-play hook`.

### Task 3: Shared card-play placement tree and Silver Tongue

**Files:** Create `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`. Modify `src/main/scala/oathdigital/gameplay/powerresolver/PowerModel.scala`, `src/main/scala/oathdigital/gameplay/powers/rest/SilverTongue.scala`. Test new `src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala` and `SilverTongueSuite.scala`.

**Interfaces:** `CardPlayProcedure.build(catalog, ready, actor, card, origin): Either[OathViolation, Operation]` returns an embeddable tree; `origin` distinguishes temporary hand from held facedown adviser without persisting a second pending procedure. Stable decision IDs distinguish placement, adviser replacement, and site replacement. `SearchPlayAdviser` replaces the old facedown-only window for both orientations. Faceup Conspiracy retains its existing post-play legacy pending handoff, recorded as a state operation so replay preserves it; this slice does not migrate the Conspiracy procedure itself.

- [ ] **Step 1: Write failing tests.** Query offers only legal placement types; ordinary adviser capacity is three, Silver Tongue capacity two; at limit a `ChooseOne` offers only discardable advisers; no candidate yields typed violation. Full non-Homeland site offers no replacement; matching Homeland offers only its already-supported candidate set. Faceup placement reaches `CardPlayed`; facedown and discard do not. Playing Conspiracy faceup from Search leaves the card in the temporary hand and, after walker completion, parks the existing `PendingProcedure.Conspiracy`; replay reconstructs that same handoff without running the tree.

```scala
val tree = CardPlayProcedure.build(catalog, ready, actor, card,
  CardPlayProcedure.Origin.TemporaryHand)
assert(tree.isRight)
```

- [ ] **Step 2: Run** `./sbtw "testOnly *CardPlayProcedureSuite *SilverTongueSuite"`; expect failure.
- [ ] **Step 3: Implement** a placement `Decide`, then `Branch` for the selected destination and any required replacement `Decide`, then `BuildOps` for legal semantic discard/move/play/gain operations. Reuse current `CardPlay` legality and operation ordering as the temporary oracle; extract shared pure planner logic instead of copying it. Put `SearchPlayAdviser` on both adviser orientations. Silver Tongue transforms the adviser decision/query at that window to enforce capacity two and retains a final restriction; do not hardcode its card ID in the procedure or walker. Ensure replacement eligibility checks catalog lock and current holder. The Conspiracy handoff needs one recorded, typed state operation applied after Search's walker has made its card choice; never introduce simultaneous legacy and walker pending positions.
- [ ] **Step 4: Run** focused and per-task gates. Compare complete state for representative old/new placement outcomes before deleting legacy code.
- [ ] **Step 5: Commit** `feat(cardplay): declare shared walker placement tree`.

### Task 4: Search tree and modifier contract

**Files:** Create `src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala`; modify `src/main/scala/oathdigital/model/ProcedureRef.scala`, `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`, `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala`, `src/main/scala/oathdigital/application/GameApplicationService.scala`, `src/main/scala/oathdigital/application/LegalActionProjector.scala`. Test new `SearchProcedureSuite.scala`, `WalkerProcedureRegistrySuite.scala`, and application Search tests.

**Interfaces:** `ActionRef.Search`, `SearchProcedure.build/rebuild(catalog, ready, actor, startArgs)`, `startArgs` carrying exactly one generic `DecisionOptionRef.Button` (`search:world` or `search:regional-discard:<region-key>`). The sealed option family has no regional-discard ref; parse only these two source spellings in Search, without adding a Search-specific model case. Registry declares `modifierWindow = Some(SearchModifierSelection)` and continuation IDs for card selection and placement. Application retains draw-port preparation at command boundary; prepared identities must match authoritative source order.

- [ ] **Step 1: Write failing tests.** World cost bands and Vision stop, regional end-as-top draw, insufficient Supply, occupied temporary hand, wrong-region source, one-card automatic Keep, ordered next-region discards, Vision track replay, private hand after park. Preview and command reject same unoffered modifier IDs; selected modifiers persist across park/reload/resume.

```scala
val startArgs = Vector(DecisionOptionRef.Button("search:world"))
val started = rules.startWalker(Ready(ready), ActionRef.Search, actor,
  modifiers = Vector.empty, startArgs = startArgs)
assert(started.isRight)
```

- [ ] **Step 2: Run** `./sbtw "testOnly *SearchProcedureSuite *WalkerProcedureRegistrySuite *GameApplicationServiceSuite"`; expect failure.
- [ ] **Step 3: Implement** source parser/gates, windowed payment/draw nodes, authoritative `Draw`, recorded track leaf, one-card bypass or Keep/Discard `Partition`, and shared `CardPlayProcedure` subtree. Register the action. Route source selection to `StartWalker`; preserve draw-port as application-only preparation/validation, never let engine call randomness. Keep old Search handling available solely for differential tests until Task 7.
- [ ] **Step 4: Run** focused and per-task gates; document parity on complete ready state and hidden/public projections for world, regional, Vision, and replacement examples.
- [ ] **Step 5: Commit** `feat(search): run Search on the procedure walker`.

### Task 5: Dazzle and selective diagnostic retirement

**Files:** Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/Dazzle.scala`; modify `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, `ActionPowers.scala`, `SearchPowers.scala`, and source-scoped diagnostic dispatch in `OathRules.scala` if required. Test new `DazzleSuite.scala`, `SearchSuite.scala`, and `MinorActionsSuite.scala`.

**Interfaces:** Dazzle contributes at `ActionCardPlayed`, matches `CardPlayed` for its catalog card ID, and emits ordered semantic `Discard` operations only for currently eligible non-immune Hearth/Order site cards in actor's pawn region.

- [ ] **Step 1: Write failing tests.** Dazzle at site and faceup adviser hits same region targets; facedown/discarded Dazzle does not fire; an immune card remains while another eligible card is discarded; resources return under `Discard`; unexpected missing-source error aborts rather than being treated as immunity. Other `WHEN PLAYED` powers still emit scoped diagnostics, Dazzle does not.

```scala
val played = CardPlayed(dazzleId,
  RuleSourceRef.Adviser(actor, dazzleId))
assertEquals(played.window, Some(PowerWindow.ActionCardPlayed))
```

- [ ] **Step 2: Run** `./sbtw "testOnly *DazzleSuite *SearchSuite *MinorActionsSuite"`; expect failure.
- [ ] **Step 3: Implement** catalog-parameterized Dazzle contribution. Enumerate region sites in map order and denizens in site order; resolve suit and immunity per candidate against staged state. Filter immune candidates before building each `Discard`, but let executor report structural errors. Remove only Dazzle reviewed fallback handler and adjust catalog fingerprint in same commit. Keep remaining `WHEN PLAYED` diagnostics. Do not delete Silver Tongue diagnostic until both Search and facedown routes use its walker restriction.
- [ ] **Step 4: Run** focused and per-task gates, including catalog audit/fingerprint suites.
- [ ] **Step 5: Commit** `feat(power): execute Dazzle when played`.

### Task 6: Facedown-adviser wrapper and generic UI

**Files:** Modify `src/main/scala/oathdigital/model/ProcedureRef.scala`, `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`, `src/main/scala/oathdigital/application/GameApplicationService.scala`, `GameIntentMapper.scala`, `Authorization.scala`, `WalkerDecisionProjector.scala`; modify `frontend/src/main/scala/oathdigital/frontend/ModifierWorkflow.scala`, `ActionDecisionRenderer.scala`, `ServerModeUi.scala` and relevant shared protocol files. Test `MinorActionsSuite.scala`, `WalkerDecisionProjectionSuite.scala`, `frontend/.../ServerModeUiSuite.scala`.

**Interfaces:** `ActionRef.PlayFacedownAdviser` starts a thin wrapper over `CardPlayProcedure` with one `DecisionOptionRef.Denizen` or `.Vision` card start argument. It declares `SearchModifierSelection`; its continuation maps placement/replacement decisions to existing generic `WalkerDecisionProjection` rather than `PendingCardDecisionProjection`.

- [ ] **Step 1: Write failing tests.** Starting the wrapper with a card not held facedown fails; correct card parks at placement, owner-only projection exposes options, hidden viewer sees only waiting state. Both wrapper and Search offer the same modifier-selection contract. Faceup placement fires Dazzle; facedown placement does not. Frontend sends `StartWalker` then `ResolveWalker`, preserving drag order for Search's Discard section.

```scala
val started = rules.startWalker(Ready(ready),
  ActionRef.PlayFacedownAdviser, actor, Vector.empty,
  Vector(DecisionOptionRef.Denizen(adviserId)))
assert(started.isRight)
```

- [ ] **Step 2: Run** focused backend/frontend suites; expect failure on missing wrapper and old command route.
- [ ] **Step 3: Implement** registry wrapper, generic intent mapping, player-scoped decision projection and frontend controls. Reuse `WalkerPartitionDraft` and generic `ResolveWalker`; no Search-specific answer wire type. Keep the old command temporarily for differential tests, but remove its client route after new controls pass. Preserve other minor actions.
- [ ] **Step 4: Run** focused and per-task gates, including frontend link.
- [ ] **Step 5: Commit** `feat(cardplay): route facedown advisers through walker`.

### Task 7: Differential gate and legacy Search deletion

**Files:** Modify `src/main/scala/oathdigital/gameplay/actions/Search.scala`, `CardPlay.scala`, `MinorActions.scala`, `model/PendingProcedures.scala`, `gameplay/model/GameEventProtocol.scala`, `gameplay/OathRules.scala`, `application/GameCommands.scala`, `GameApplicationService.scala`, `PendingProcedureProjector.scala`, `serialization/ActionEventCodec.scala`, `GameEventWire.scala`, shared intent/projection codecs and frontend legacy card-decision files. Update fixtures and affected tests, including `ForgeWalkerFixture.scala`, `VisionsSuite.scala`, `PendingWalkerInvariantSuite.scala`, `GameApplicationServiceSuite.scala`.

**Interfaces:** Only `StartWalker`/`ResolveWalker` express Search and facedown-adviser card play. No `PendingProcedure.Search`, `SearchStarted`, `SearchCompleted`, `BeginSearch`, or `CompleteSearch` production path survives. Other pending cases, Vision/Conspiracy behavior, and unrelated diagnostics remain.

- [ ] **Step 1: Freeze parity and replay tests before deletion.** Compare legacy and walker complete state, `CardIndex`, continuation, scoped/public projections, and event intent on world/regional, Vision-stop, placement, and replacement examples. Add persisted-stream replay and powered drift cases; Dazzle uses explicit expected state because legacy only diagnosed it.

```scala
assertEquals(walkerResult.state, legacyResult.state)
assertEquals(CardIndex.from(walkerReady.game), CardIndex.from(legacyReady.game))
```

- [ ] **Step 2: Run** parity suites; fix walker behavior if comparisons fail. Do not weaken expected states or hide mismatches behind projection-only checks.
- [ ] **Step 3: Delete** Search-specific command/event/codec/pending/projection branches and facedown-adviser bespoke placement route, then update fixtures to walker decisions. Remove Silver Tongue's legacy Search handler and refresh audited fingerprint. Retain `SearchRules` only for pure rules still shared by production; otherwise move necessary pure rules into `SearchProcedure` and delete the old module. Confirm `rg` finds no production `PendingProcedure.Search`, `SearchStarted`, `SearchCompleted`, `BeginSearch`, or `CompleteSearch` references.
- [ ] **Step 4: Run** `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`, `python3 scripts/check-architecture.py`, `git diff --check`, and repository Markdown link check. Confirm private Search identities never enter public projection and replay does not call contributions.
- [ ] **Step 5: Commit** `refactor(search): retire legacy Search and facedown card play`.

## Final self-review gate

- [ ] Every approved spec section maps to a task above, including modifiers, site immunity, Silver Tongue, Dazzle, replay, and deletion.
- [ ] No second production path or duplicate diagnostic remains for migrated behavior.
- [ ] Full tests and architecture check pass on final commit; `git status --short` is clean.
