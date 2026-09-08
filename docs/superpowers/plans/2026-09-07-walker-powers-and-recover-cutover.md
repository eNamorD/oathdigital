# Walker Powers + Recover Cutover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire power contributions (Transform / Restriction / named-ignore) into the procedure walker, prove the ≤50-line power-authoring bar by porting Catacombs, then finish Recover's migration end to end — UI on the walker commands, legacy Recover path deleted — so Recover becomes the reference implementation every later action copies.

**Architecture:** A power becomes one object declaring `contributions: Map[PowerWindow, Vector[Contribution]]`. At each tree node carrying a `window`, the walker gathers applicable contributions through a pure collector (discovery → named-ignore → deterministic sort → transform chain → restriction set), applies transforms to that node's children vector, validates restrictions against the whole action tree, and records the surviving contribution order in the node's event. Replay is unchanged: it applies recorded ops and never re-runs a contribution. Once powers can attach to the walker tree, Recover's UI moves onto `StartWalker`/`RollWalker`/`ResolveWalker` and the legacy `Recover.scala` state machine, its `PendingProcedure` cases, and its events are deleted.

**Tech Stack:** Scala 2.13, sbt multi-project (root engine + frontend Scala.js), munit, ujson. Full gate: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.

**Spec:** `docs/superpowers/specs/2026-09-05-procedure-walker-design.md` (approved). This plan implements the spec's migration-plan step 2 (power contributions against the slice) plus the per-action cutover for Recover. The batch port of the remaining actions (step 3) is a later plan and is out of scope here.

## Global Constraints

- Do not break any other action. Full gate green at every commit (root + frontend).
- Engine code contains no power-specific logic. A power is one object under `gameplay/powers/`; the walker knows only `Contribution`. This is the spec's power-authoring bar and Task 5 asserts it in a test.
- Engine never calls random ports; randomness is prepared at the application boundary. `RollWalker` carries no die faces — faces come from `defenseDicePort` inside the `prepareFaces` callback that `OathRules.rollWalkerPrepared` invokes only after the parked pool is validated (ruling from the Recover slice; it overrides the older "dice faces ride commands" wording).
- Replay applies recorded ops only (J1). Contributions, transforms, restrictions and the validator never run at replay. Drift checks stay dev/test-only.
- Recorded contribution order is an audit fact, not a replay input: `applyRecorded` must not consult it to decide what to apply.
- A pending walker action blocks legacy actions and non-walker commands (`OathLifecycle`, `GameApplicationService`). Keep that gate working through the cutover.
- `usedPowers` tracking stays state; no walker code reads or writes it.
- All new leaf/composite `Operation` cases live in `src/main/scala/oathdigital/gameplay/operations/CoreOperations.scala`: `CoreOperation`/`PrimitiveOperation` are sealed and Scala 2.13 requires sealed subclasses in the same source file.
- `ReviewedPowerCatalog.AuditedCatalogFingerprint` is a fail-closed audit gate. Any change to the catalog's handler inventory requires recomputing it in the same commit, or every resolve call fails.
- Old-journal compatibility is explicitly not a constraint (spec, Out of scope): the journal is forward-only pre-release. Deleting a legacy event case and its codec branch is permitted.
- Per-task gate: root suite `./sbtw "test"` green, plus `python3 scripts/check-architecture.py`. Tasks 6-8 touch `frontend/` and additionally run `./sbtw "frontend/test" "frontend/fastLinkJS"`. Task 10 runs the full gate.
- Commit per task with the exact message shown. Work on branch `feat/walker-powers` cut from `feat/engine-redesign`.

---

## Naming decisions locked before Task 1

The legacy `Power` trait (`gameplay/powerresolver/PowerModel.scala:125-129`) stays alive until Task 9 deletes Recover's legacy path, and other actions keep using it after that. The new shape therefore needs its own name rather than replacing `Power` in place:

- New trait: `ContributingPower` (`gameplay/powerresolver/ContributingPower.scala`).
- New contribution ADT: `Contribution`, with cases `Transform` and `Restriction`.
- The existing `CostContribution` (`PowerContributions.scala:12-23`) is Travel's separate vocabulary and is untouched by this plan.

Both power vocabularies coexist until the batch migration retires the legacy one. That is deliberate, not an oversight.

---

### Task 1: Contribution vocabulary and the ContributingPower shape

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`
- Test: `src/test/scala/oathdigital/gameplay/ContributingPowerSuite.scala`

**Interfaces:**
- Consumes: `PowerWindow`, `PowerId`, `RuleSourceRef` (`gameplay/powerresolver/PowerModel.scala`), `Operation` (`gameplay/operations/Operation.scala`), `OathViolation`, `ReadyGame`, `PlayerId`.
- Produces, all in package `oathdigital.gameplay.powerresolver`:
  - `final case class PowerCtx(state: ReadyGame, actor: PlayerId, source: RuleSourceRef, window: PowerWindow, nodePath: Vector[String])` — everything a contribution may read. It carries no mutable state and no catalog.
  - `sealed trait Contribution extends Product with Serializable`
  - `final case class Transform(fn: (PowerCtx, Vector[Operation]) => Vector[Operation]) extends Contribution`
  - `final case class Restriction(fn: (PowerCtx, Operation) => Option[OathViolation]) extends Contribution`
  - `trait ContributingPower { def id: PowerId; def source: RuleSourceRef; def priority: Int; def contributions: Map[PowerWindow, Vector[Contribution]]; def applicable(ctx: PowerCtx): Boolean; def shouldIgnore(other: PowerId): Boolean }`
  - Defaults on the trait: `priority = 0`, `applicable(_) = true`, `shouldIgnore(_) = false`. A trivial power then declares only `id`, `source` and `contributions`.
  - `object ContributingPower { def sortKey(power: ContributingPower): (Int, String, String) = (power.priority, power.source.stableKey, power.id.value) }` — the single deterministic ordering used everywhere contributions are chained (spec decision 10c).

A `Transform` receives and returns the hooked node's **children vector**, never the whole tree — whole-action restructure is explicitly out of scope (spec decision 9). A `Restriction` receives the whole action tree root, because Vow-of-Peace-style powers reject an action wholesale (same decision).

- [ ] **Step 1: failing test** in `ContributingPowerSuite`: (a) `sortKey` orders two powers with equal priority by `source.stableKey`, then by `id.value` — assert the exact sorted vector for three fixtures whose keys interleave; (b) a power declaring only `id`/`source`/`contributions` compiles and reports `priority == 0`, `applicable(ctx) == true`, `shouldIgnore(anyId) == false`. Run `./sbtw "testOnly oathdigital.gameplay.ContributingPowerSuite"`; expected FAIL — `ContributingPower` does not exist.
- [ ] **Step 2: implement** the file exactly as specified above. No collector logic here — this task is vocabulary only.
- [ ] **Step 3:** re-run the focused suite; expected PASS.
- [ ] **Step 4:** run `./sbtw "test"` and `python3 scripts/check-architecture.py`; both green.
- [ ] **Step 5: commit** `feat(powers): contribution vocabulary and ContributingPower shape`.

---

### Task 2: The gather protocol as a pure collector

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powerresolver/ContributionCollector.scala`
- Test: `src/test/scala/oathdigital/gameplay/ContributionCollectorSuite.scala`

**Interfaces:**
- Consumes: Task 1's types.
- Produces:
  - `final case class GatheredContributions(transforms: Vector[(PowerId, Transform)], restrictions: Vector[(PowerId, Restriction)], order: Vector[PowerId])`
  - `object ContributionCollector { def gather(window: PowerWindow, ctx: PowerId => PowerCtx, powers: Vector[ContributingPower]): GatheredContributions }`

`ctx` is a function rather than a value because `PowerCtx.source` differs per power — the collector builds each candidate's context from its own `source`. Implement the spec's decision-10 protocol exactly, in this order:

1. **Discovery:** keep powers whose `contributions` contains `window`.
2. **Applicability:** keep those where `applicable(ctx(power.id))` is true.
3. **Named ignore, one pass, no transitivity:** compute the set `ignored = applicable.flatMap(p => applicable.map(_.id).filter(p.shouldIgnore))` from the powers surviving step 2, then drop every power whose id is in `ignored`. A dropped power's own `shouldIgnore` votes still count — they were collected before the drop. That is what "one pass, no transitivity" means; do not iterate to a fixpoint.
4. **Deterministic order:** sort survivors by `ContributingPower.sortKey`.
5. **Split:** in that order, collect each survivor's `Transform`s into `transforms` and `Restriction`s into `restrictions`, both tagged with the owning `PowerId`, and record every surviving power's id once, in order, into `order`.

- [ ] **Step 1: failing tests** in `ContributionCollectorSuite`, one per protocol property, each with hand-built fixture powers:
  - a power that does not declare the window is not gathered;
  - a power whose `applicable` returns false is not gathered;
  - A ignores B: B's transform is absent, A's is present;
  - A ignores B and B ignores C: C survives (no transitivity — B's vote counts even though B was dropped);
  - two powers with interleaved sort keys produce transforms in `sortKey` order, asserted as an exact vector;
  - a power declaring both a `Transform` and a `Restriction` at the same window lands one in each output and its id once in `order`.
  Run `./sbtw "testOnly oathdigital.gameplay.ContributionCollectorSuite"`; expected FAIL — `ContributionCollector` does not exist.
- [ ] **Step 2: implement** `ContributionCollector`. It is pure: no state reads beyond the `PowerCtx` it is handed, no catalog lookups, no walker imports.
- [ ] **Step 3:** re-run the focused suite; expected PASS.
- [ ] **Step 4:** `./sbtw "test"` and `python3 scripts/check-architecture.py` green.
- [ ] **Step 5: commit** `feat(powers): contribution gather protocol collector`.

---

### Task 3: Walker applies contributions and records their order

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala`
- Test: `src/test/scala/oathdigital/gameplay/ProcedureWalkerSuite.scala`, `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`

**Interfaces:**
- Consumes: Tasks 1-2.
- Produces:
  - `WalkerStepRecorded` gains `contributions: Vector[PowerId]` (default `Vector.empty` is NOT acceptable — make it an explicit field so every construction site states it). Its codec gains a `"contributions"` array of power-id strings, encoded and decoded by the existing bounded walker codec.
  - `ProcedureWalker` gains a power source in its walk context: `final case class WalkerPowers(powers: Vector[ContributingPower])`, threaded through `advance`/`roll`/`resolve` as a new parameter. `OathRules` supplies it; `applyRecorded` does not take one and must not gain one.
  - `ProcedureWalker.restrictionViolations(tree: Operation, powers: WalkerPowers, state: ReadyGame, actor: PlayerId): Vector[OathViolation]` — collects every window's restrictions and runs each against the whole tree root.

Wiring rules, in the order the walker must apply them:

- At a composite node whose `window` is `Some(w)`, before walking its children: gather at `w`, fold the node's children vector through the gathered transforms in order, and walk the transformed vector. The declared node is not mutated; the transformed vector is local to this walk.
- At a leaf whose `window` is `Some(w)`: gather at `w` and fold `Vector(leaf)` through the transforms, then walk the resulting vector in place of the leaf. A transform may therefore replace one leaf with several — that is how a "must" effect inserts operations.
- Every `WalkerStepRecorded` a node emits carries the `order` from the gather that produced it, or `Vector.empty` for a node with no window.
- Restrictions run once per command, at command entry in `OathRules`, before the walk: if `restrictionViolations` is non-empty, return the first as a `Left` and append no events.
- `applyRecorded` is untouched except for the new field: it reads `ops` as before and ignores `contributions`.

- [ ] **Step 1: failing tests** in `ProcedureWalkerSuite`: (a) a tree whose delta leaf carries a window, plus a power whose `Transform` prepends a second delta, records both ops in one step and lists the power id in `contributions`; (b) a node with no window records `contributions == Vector.empty`; (c) a `Restriction` returning `Some(violation)` makes the command `Left` with that violation and appends no events; (d) replay of a stream recorded with contributions reaches the same state as the live walk when the power vector is empty at replay — proving contributions are not a replay input. In `GameEventWireSuite`: a `WalkerStepRecorded` carrying two contribution ids round-trips. Run the two focused suites; expected FAIL.
- [ ] **Step 2-4: TDD implement** the wiring above; re-run the focused suites to green, then `./sbtw "test"` and `python3 scripts/check-architecture.py`.
- [ ] **Step 5: commit** `feat(walker): apply power contributions and record their order`.

---

### Task 4: Recover tree carries windows; StartWalker carries chosen modifiers

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala`
- Modify: `src/main/scala/oathdigital/application/GameCommands.scala`
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala`
- Test: `src/test/scala/oathdigital/gameplay/RecoverProcedureSuite.scala`, `src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala`

**Interfaces:**
- Consumes: Tasks 1-3.
- Produces:
  - Recover's declared tree sets `window` on three nodes, reusing the existing vocabulary (`PowerModel.scala:52-53, 85-89`): the tree root carries `RecoverActionEligibility`; the head node that opens the dice pool carries `RecoverBeforeFirstRoll`; the `BuildOps` node that moves the chosen relic carries `RecoverAfterRelic`. `RecoverModifierSelection` is the window a player-selected power is offered at and is not a tree node — it is answered by the command, per the next bullet.
  - `GameCommand.StartWalker(action: ActionRef, actor: PlayerId, modifiers: Vector[PowerId])` — `modifiers` is the ordered list of player-selected powers the client chose. Empty vector means "no modifiers", which is every Recover today.
  - `OathRules.startWalker(state: OathState, action: ActionRef, actor: PlayerId, modifiers: Vector[PowerId])` — validates every id in `modifiers` against the audited catalog and rejects an unknown or inapplicable id with `InvalidEventOrder` before walking. The surviving ids select which `ContributingPower`s are offered to the collector for this command; automatic powers are always offered.
  - `OathRules.walkerPowers(ready: ReadyGame, actor: PlayerId, modifiers: Vector[PowerId]): WalkerPowers` — the single place that turns the catalog plus the chosen modifiers into the vector handed to the walker.

- [ ] **Step 1: failing test** in `RecoverProcedureSuite`: the built tree's root, pool-opening node and relic-moving node report the three windows above; every other node reports `None`. In `GameApplicationServiceSuite`: `StartWalker` with an unknown power id in `modifiers` is rejected with `InvalidEventOrder` and appends nothing; `StartWalker` with an empty `modifiers` behaves exactly as today (assert against the existing walker Recover expectations). Run both focused suites; expected FAIL.
- [ ] **Step 2-4: TDD implement**; re-run focused suites, then `./sbtw "test"` and `python3 scripts/check-architecture.py`.
- [ ] **Step 5: commit** `feat(recover): power windows on the walker tree and modifier-carrying start`.

---

### Task 5: Port Catacombs to a contribution, and assert the authoring bar

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/recover/CatacombsContribution.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/RecoverPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ReviewedPowerCatalog.scala` (fingerprint only, if the handler inventory changes)
- Test: `src/test/scala/oathdigital/gameplay/CatacombsContributionSuite.scala`, `src/test/scala/oathdigital/architecture/BackendArchitectureSuite.scala`

**Interfaces:**
- Consumes: Tasks 1-4.
- Produces: `object CatacombsContribution extends ContributingPower` — the whole power in one file, engine untouched.

Catacombs' rulebook behaviour, as the legacy implementation encodes it (`powers/recover/RecoverPowerIntegration.scala`, `actions/Recover.scala:24-78`, `powers/RecoverPowers.scala:27-110`): it makes a site with no facedown relic eligible for Recover, and on use it places a relic facedown at the site for the cost of 1 secret. Express that as exactly two contributions:

- A `Restriction` at `RecoverActionEligibility` that returns `None` when the power is applicable — its presence is what lets the eligibility gate pass on a relic-less site. The base eligibility check moves from "the site has a facedown relic" to "the site has a facedown relic, or some applicable contribution supplies one"; implement that by having `RecoverProcedure.build`'s gate consult the gathered restrictions rather than hardcoding the relic-presence test.
- A `Transform` at `RecoverBeforeFirstRoll` that prepends the operations placing a relic facedown at the site and paying 1 secret, expressed with existing `CoreOperation` cases (`Move`, the secret-paying op the legacy `Cost` construction uses). No new operation cases.

The legacy `Catacombs` power object, `RecoverPowerHandler`, `operationBackedSelected` and `RecoverPowerIntegration` all stay in place this task — they still serve the legacy path until Task 9 deletes it. Only the new contribution is added.

- [ ] **Step 1: failing tests** in `CatacombsContributionSuite`, exercised through the walker (not by calling the contribution directly): starting a walker Recover on a relic-less site with `modifiers = Vector(catacombsId)` succeeds, records the relic placement and the secret payment in the first step's ops, and lists Catacombs in that step's `contributions`; the same start without the modifier is rejected; a site that already has a facedown relic still starts with no modifier and records no placement ops. In `BackendArchitectureSuite`: `CatacombsContribution.scala` is at most 50 lines and no file under `gameplay/walker/` or `gameplay/operations/` mentions "catacombs" (case-insensitive) — this is the spec's power-authoring bar, and it replaces nothing; the existing file-content guard on `Recover.scala` stays until Task 9. Run both focused suites; expected FAIL.
- [ ] **Step 2-4: TDD implement.** If the handler inventory changes, recompute `AuditedCatalogFingerprint` in the same commit. Re-run focused suites, then `./sbtw "test"` and `python3 scripts/check-architecture.py`.
- [ ] **Step 5: commit** `feat(powers): Catacombs as a walker contribution`.

---

### Task 6: Wire protocol for the walker commands

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntentCodec.scala` and `CommandIntentDecoders.scala`
- Modify: `src/main/scala/oathdigital/application/GameIntentMapper.scala`
- Test: `src/test/scala/oathdigital/protocol/CommandIntentCodecSuite.scala` (or the existing intent-codec suite, whichever the repository already has), `src/test/scala/oathdigital/application/GameIntentMapperSuite.scala` if one exists — otherwise extend `GameApplicationServiceSuite`

**Interfaces:**
- Consumes: Task 4's `StartWalker` shape.
- Produces:
  - `Intent.StartWalker(action: String, modifiers: Vector[String])`, `Intent.RollWalker(pool: String)`, `Intent.ResolveWalker(decisionId: String, payload: DecisionPayloadWire)` in the shared protocol.
  - `DecisionPayloadWire` — the wire form of the open `DecisionPayload` trait, bounded for this slice to Recover's two payloads: `RecoverChoiceWire(choice: String)` where choice is `"continue"` or `"stop"`, and `RecoverRelicWire(relicId: String)`. Encode with a `"kind"` discriminator exactly as `WalkerEventCodec` already does for its payloads, and reject an unknown kind with the codec's existing typed error rather than throwing.
  - `GameIntentMapper` cases translating each intent into the matching `GameCommand`, mapping `action` string `"recover"` to `ActionRef.Recover` and rejecting any other value.

The engine-side `ActionRef` wire key is already `"recover"` (`model/ActionRef.scala`); reuse that constant rather than a new literal.

- [ ] **Step 1: failing test:** each of the three intents round-trips through encode/decode; an unknown decision-payload kind decodes to the typed wire error, not an exception; an unknown action string is rejected by the mapper. Run the focused codec suite; expected FAIL.
- [ ] **Step 2-4: TDD implement**; re-run focused suites, then `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` — the shared module compiles into the frontend, so the frontend gate runs from this task onward — and `python3 scripts/check-architecture.py`.
- [ ] **Step 5: commit** `feat(protocol): wire intents for the walker command surface`.

---

### Task 7a: Project the parked walker decision to the wire

**Split from the original Task 7 after its implementer reported NEEDS_CONTEXT.** The task assumed the projection already carried what the UI needs; it does not. `WalkerDecisionProjection` is `private[application]` and never reaches the wire `GameProjection` DTO, and `PendingProcedureProjector`/`LegalActionProjector` collapse every parked decision into indistinguishable strings — `phase = "recover-walker-decision"` and control `"resolveWalkerDecision"` for both `recover.choice` and `recover.relic`. There is also no relic-candidate list on the walker path at all: the legacy `pendingCardDecision`/`recover` fields that carry relic identity are populated only from `PendingProcedure.Recover`, which the walker never sets. So a client can tell a walker decision is parked but not which one, and cannot offer relics to pick.

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/GameProjectionDto.scala`
- Modify: its codec
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`, `ScopedProjectionContext.scala`, `GameProjection.scala`
- Test: `src/test/scala/oathdigital/application/WalkerDecisionProjectionSuite.scala`

**Interfaces:**
- Produces: a wire projection of the parked walker decision carrying its `decisionId`, its kind, the pool and count for a roll park, and for the relic park the candidate relics the actor may take.
- Owner privacy is binding: relic identity reaches the acting player only. Other viewers see that a decision is parked and nothing more — match how the legacy Recover and Forge projections redact.
- The relic decision's payload is a placeholder marker id, not a chosen relic; the concrete relic rides the answer. The projection must not present the marker as a preselected choice.

- [ ] **Step 1: failing test** in `WalkerDecisionProjectionSuite`: each of the three parks (roll, choice, relic) projects a distinguishable wire decision with its own `decisionId`; the relic park lists the site's facedown relics for the actor and none for another viewer.
- [ ] **Step 2-4: TDD implement; gate** root suite plus `frontend/test` and `frontend/fastLinkJS`, since `shared/` compiles into the frontend.
- [ ] **Step 5: commit** `feat(projection): wire the parked walker decision`.

### Task 7b: Recover UI drives the walker

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ModifierWorkflow.scala` (Catacombs is offered here today)
- Test: the frontend suite covering `ActionDecisionRenderer`

**Interfaces:**
- Consumes: Task 6's intents and Task 7a's wire projection.
- Produces: the Recover controls send walker intents. Specifically: the Act-phase "Recover" button sends `Intent.StartWalker("recover", modifiers)` where `modifiers` carries the ids the existing modifier workflow collected; the in-progress panel's roll control sends `Intent.RollWalker(pool)`; Continue/Stop send `Intent.ResolveWalker(RecoverProcedure.choiceDecisionId, RecoverChoiceWire(...))`; the relic pick sends `Intent.ResolveWalker(RecoverProcedure.relicDecisionId, RecoverRelicWire(relicId))` in place of today's generic `ResolveCardDecision`/`TakeFacedownRelic` route.

Delete the legacy Recover control wiring in the same task — the `beginRecover` / `addRecoverDice` / `stopRecover` blocks — so no dead path is left rendering. The generic card-decision UI stays: Search and other actions still use it.

- [ ] **Step 1: failing test** in the frontend suite: given a projection with a parked walker roll, the renderer produces a roll control that dispatches `Intent.RollWalker`; given a parked choice decision, Continue and Stop controls dispatching `Intent.ResolveWalker` with the right decision id; given a parked relic decision, a relic control dispatching `Intent.ResolveWalker` with `RecoverRelicWire`. Run `./sbtw "frontend/test"`; expected FAIL.
- [ ] **Step 2-4: TDD implement**; re-run the frontend suite, then `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` and `python3 scripts/check-architecture.py`.
- [ ] **Step 5: commit** `feat(ui): Recover controls drive the walker commands`.

---

### Task 8: Generalize the walker's action dispatch and phase labels

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala` (`buildWalker`)
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`
- Modify: `src/main/scala/oathdigital/application/PendingProcedureProjector.scala`
- Test: `src/test/scala/oathdigital/application/WalkerDecisionProjectionSuite.scala`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `object WalkerActionRegistry { def build(action: ActionRef, catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId): Either[OathViolation, Operation]; def rebuild(action: ActionRef, catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId): Either[OathViolation, Operation] }` in `src/main/scala/oathdigital/gameplay/walker/WalkerActionRegistry.scala` — one keyed lookup replacing the two hardcoded `case ActionRef.Recover =>` matches (`OathRules.buildWalker`, `WalkerDecisionProjector`). Registering a second action becomes one entry here.
  - Phase labels become action-keyed instead of the hardcoded `"recover-walker-roll"` / `"recover-walker-decision"` / `"recover-walker-waiting"` string literals: build them as `s"${action.wireKey}-walker-roll"` and so on, using `ActionRef`'s existing wire key.

This is the debt the Recover slice's own status note flags as blocking reuse; it must land before the batch port starts, and it is cheapest here where only one action exists to break.

- [ ] **Step 1: failing test** in `WalkerDecisionProjectionSuite`: the projected phase label for a parked Recover roll is derived from the action's wire key (assert the exact string `"recover-walker-roll"` so the refactor is behaviour-preserving); `WalkerActionRegistry.build` returns a `Left` for an action with no registration rather than throwing a match error. Run the focused suite; expected FAIL.
- [ ] **Step 2-4: TDD implement**; re-run focused suite, then `./sbtw "test"` and `python3 scripts/check-architecture.py`.
- [ ] **Step 5: commit** `refactor(walker): action registry and derived phase labels`.

---

### Task 9: Delete the legacy Recover path

**Files:**
- Delete: `src/main/scala/oathdigital/gameplay/actions/Recover.scala`, `src/main/scala/oathdigital/gameplay/powers/recover/RecoverPowerIntegration.scala`, `src/test/scala/oathdigital/gameplay/RecoverSuite.scala`
- Modify: `src/main/scala/oathdigital/model/PendingProcedures.scala` (remove `PendingProcedure.Recover` and `RecoverPowerApplied`, lines 362-377)
- Modify: `src/main/scala/oathdigital/gameplay/model/GameEventProtocol.scala` (remove `RecoverPowerEvent`, `RecoverRolled`, `RecoverStopped`, `RelicRecovered`, `CatacombsResolved`)
- Modify: `src/main/scala/oathdigital/serialization/ActionEventCodec.scala`, `GameEventWire.scala` (remove those events' codec branches and type tags)
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala` (remove `BeginRecover`, `AddRecoverDice`, `StopRecover`, the `TakeFacedownRelic` Recover route, and the `WithModifiers(BeginRecover(...))` branch), `GameCommands.scala`, `GameIntentMapper.scala`, `shared/.../CommandIntents.scala` (remove the three legacy Recover intents)
- Modify: `src/main/scala/oathdigital/application/PendingProcedureProjector.scala`, `LegalActionProjector.scala` (remove Recover-specific projections and controls)
- Modify: `src/main/scala/oathdigital/gameplay/FirstGameSetup.scala:254-266` (remove the deleted event names from its match)
- Modify: `src/main/scala/oathdigital/gameplay/powers/RecoverPowers.scala` (remove the legacy `Catacombs` power object and its `catacombsInspector`/`prepareCatacombs`/`canonicalCatacombs` helpers; the four reviewed-but-unimplemented powers stay as catalog entries)
- Modify: `src/test/scala/oathdigital/architecture/BackendArchitectureSuite.scala` (the `"Catacombs mechanics remain owned by Recover powers"` guard reads `Recover.scala` by path and will fail with file-not-found — repoint it at `CatacombsContribution.scala` and the walker/operations packages)
- Modify: `src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala` (remove the legacy-only Recover tests — the Catacombs integration test and the legacy HSQL round-trip — keeping every walker test; the walker/legacy equivalence assertions lose their legacy side and must be rewritten as walker-only assertions of the same final state)

**Interfaces:**
- Consumes: Tasks 5-8. Do not start this task until a player can complete a Recover, with and without Catacombs, entirely through the walker.
- Produces: exactly one Recover path.

Recompute `AuditedCatalogFingerprint` in this commit if the handler inventory changed.

- [ ] **Step 1:** delete in dependency order — application/protocol entry points first, then projections, then the events and their codecs, then `PendingProcedure` cases, then the action module and its power integration. Compile between groups; the compiler's exhaustiveness errors are the checklist.
- [ ] **Step 2:** rewrite `BackendArchitectureSuite`'s Catacombs guard and the affected `GameApplicationServiceSuite` tests as described above.
- [ ] **Step 3:** `grep -rn "BeginRecover\|AddRecoverDice\|StopRecover\|RecoverCommand\|PendingProcedure.Recover\|RecoverRolled\|RecoverStopped\|RelicRecovered" src/ shared/ frontend/` returns nothing outside comments.
- [ ] **Step 4:** `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` green, `python3 scripts/check-architecture.py` green, `git diff --check` clean.
- [ ] **Step 5: commit** `refactor(recover): delete the legacy Recover path`.

---

### Task 10: Verification checkpoint and spec update

- [ ] **Step 1: full gate** `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` exit 0.
- [ ] **Step 2: `git diff --check`** clean; `python3 scripts/check-architecture.py` green.
- [ ] **Step 3: extend the drift suite.** `WalkerReplayDriftSuite` gains a corpus entry for a Catacombs-modified Recover: recorded ops must equal the ops a rebuilt tree derives when the same contributions are gathered. This is the first drift case where a power changed the tree, and it is the check that proves transforms are deterministic. Dev/test only, as before.
- [ ] **Step 4: prove the authoring bar** — `BackendArchitectureSuite` asserts a contributing power is one file of at most 50 lines with no engine imports, and that no file under `gameplay/walker/` or `gameplay/operations/` names a specific power.
- [ ] **Step 5: update the spec** — replace the "Slice status" section with the state after this plan: Recover fully migrated with powers and UI, legacy path deleted, what the batch port inherits (the registry entry point, the contribution vocabulary, the wire intents), and which of the spec's migration-plan steps remain. Commit `docs: record walker powers and Recover cutover`.

---

## Out of scope (later plans)

- Porting the remaining actions (Search, Economy, Forge, Challenge, Campaign, Negotiation, CardPlay, Rest, Wake, Visions) — the batch plan follows this one.
- Deleting the legacy `Power`/`PowerHandler`/`PowerResolver` machinery: other actions still use it until the batch port finishes.
- Authoring the MVP power set on the new framework (spec migration step 5).
- Active-player ordering of simultaneous same-window effects; the deterministic sort key stands.
- Whole-action restructure contributions.

## Acceptance Criteria

- A power is one object declaring contributions; adding Catacombs to the walker touched no engine file, and a test asserts it.
- The gather protocol matches the spec's decision 10 including one-pass named ignore, and each clause has its own test.
- Restrictions reject an illegal action at the whole-tree level before any event is appended.
- Recorded events carry contribution order, and replay reaches the same state with no powers present — contributions are audit data, not replay input.
- A player completes Recover, with and without Catacombs, entirely through `StartWalker`/`RollWalker`/`ResolveWalker`.
- No legacy Recover code, events, intents, or pending cases remain; every other action is untouched and green.
