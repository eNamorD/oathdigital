# Walker Batch 1: Forge, Travel, Wake — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the first batch of actions off their bespoke handle/evolve state machines onto the generic walker, and delete each legacy path as it goes. The batch is chosen for information, not for ease: Forge proves the migration recipe generalises past Recover, Travel forces the `CostContribution`-versus-`Transform` reconciliation, and Wake forces the turn-scoped-activation question. Whatever this batch teaches shapes the plan for the remaining seven actions.

**Architecture:** Each action declares one `Operation` tree (`build` for a fresh start, `rebuild` to resume), registers one `WalkerActionRegistry.Entry`, and its powers become `ContributingPower` objects contributing `Transform`s and `Restriction`s at the windows its tree nodes carry. `ProcedureWalker` executes the tree; the application layer projects parked decisions and prepares randomness. Nothing in the engine learns an action's or a power's name beyond its registry entry.

**Tech Stack:** Scala 2.13, sbt multi-project (root engine + frontend Scala.js), munit, ujson. Full gate: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.

**Spec:** `docs/superpowers/specs/2026-09-05-procedure-walker-design.md` (approved). This plan implements the first batch of the spec's migration-plan step 3, and takes the step-4 deletions that each ported action makes possible. The remaining actions (Search, Economy, Challenge, Campaign, Negotiation, CardPlay, Rest, Visions) are out of scope and get their own plan once this batch reports.

---

## Why these three

`Recover` is the only action on the walker today, and it is a poor sample of one: it is the action the walker was designed against. Picking batch 1 by ease would port three more Recover-shaped actions and learn nothing. Each pick here answers a question that is currently open.

**Forge — does the recipe generalise?** `ForgePowers.powers` is `Vector.empty`, so Forge is a pure structural port with no power reconciliation to confound it. Its shape is the one Recover did not exercise: two commands (`Begin`, `Complete`), one `Decide` carrying a substantial validation body, no dice, no `Repeat` loop. It also re-tests spec decision 4 from the other side — the forged relic is the authoritative relic-deck top, which turns out to be a plain state read rather than prepared randomness at all (see Task 2), so porting Forge is what lets that port be deleted. If the recipe is wrong, Forge is where it is cheapest to find out.

**Travel — which cost vocabulary survives?** `CostContribution` (`powerresolver/PowerContributions.scala`) exists for exactly one action: outside its own definition its only references are `powers/travel/TravelCostWindow.scala` and `TravelPowerFactsSuite`. Travel therefore owns the entire typed-cost-fact vocabulary the spec's decision 9 says a `Transform` replaces, plus `SuppressionRegistry` (a second, parallel expression of the named-ignore that `shouldIgnore` now covers) and `TravelCostLegality` (a hand-rolled restriction seam predating `Restriction`). Porting Travel is the only way to learn whether `Transform` over a pay node can state terrain cost honestly, and it is what lets the whole vocabulary be deleted rather than left coexisting.

**Wake — where does turn-scoped activation live?** Take Wealth is once per turn per site, tracked in `TurnState.usedPowers` as a `PowerUseRef`. The spec parks this deliberately: `usedPowers` tracking stays state and no walker code reads it. That decision was made when no walker action needed it; Wake is the first that does, and the question is whether a power can express "already used this turn" through `applicable(ctx)` reading `ctx.state`, or whether the walker needs a concept it does not have. Wake also runs in the Wake phase, not Act, so it proves the walker is not Act-only — `startWalker` never checks phase itself, each action's `build` does, but that has never been tested with an action that gates on a different phase.

Deliberately excluded from batch 1: Campaign (800 lines, the most likely to need a spec change, and worth planning against what this batch learns), Search (draw loops and temporary hands, a second `Repeat` shape that teaches less than Travel's cost work), and Economy (single-command, no decision — the same shape Travel already covers).

---

## Global Constraints

- Do not break any other action. Full gate green at every commit (root + frontend).
- Engine code contains no power-specific logic and no per-action logic outside a `WalkerActionRegistry.Entry`. `BackendArchitectureSuite`'s "a walker power imports no engine, and the engine never learns its name" asserts both halves; it must stay green without being weakened.
- The power-authoring bar (one class, engine untouched) is the design target. The ≤50-line half is a guideline a reviewer judges, not an asserted property (spec, Verification). A power that needs more lines to state its rule honestly gets them; a power that needs an engine change does not.
- Engine never calls random ports. Forge's relic-deck top and any prepared value reach the walker through the command, inside the application-layer prepare callback, never by the tree reading a port.
- Replay applies recorded ops only. Contributions, transforms, restrictions and the validator never run at replay.
- Old-journal compatibility is not a constraint. Deleting a legacy event case with its codec branch and wire type is permitted and expected.
- `ReviewedPowerCatalog.AuditedCatalogFingerprint` is a fail-closed audit gate. Any change to the catalog's handler inventory recomputes it in the same commit or every resolve call fails.
- Each action's port and its legacy deletion land in the same task. A half-ported action with two live paths is the state this migration exists to end.
- Per-task gate: `./sbtw "test"` green plus `python3 scripts/check-architecture.py`. Tasks touching `frontend/` additionally run `./sbtw "frontend/test" "frontend/fastLinkJS"`. Task 8 runs the full gate.
- Commit per task with the exact message shown. Work on branch `feat/walker-batch-1` cut from `feat/engine-redesign`.

---

### Task 1: The modifier-selection window becomes per-action

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerActionRegistry.scala`, `src/main/scala/oathdigital/gameplay/OathRules.scala`, `src/main/scala/oathdigital/application/GameApplicationService.scala`
- Test: `src/test/scala/oathdigital/gameplay/walker/WalkerActionRegistrySuite.scala`

`OathRules.offerableWalkerPowers` and `validateModifiers` both name `PowerWindow.RecoverModifierSelection` as a literal. With one registered action that reads as harmless; the moment a second action registers, every action's player-selected powers are filtered through Recover's window and a Forge modifier is either wrongly offered or wrongly rejected. This is a known open item and it blocks every later task, so it lands first, alone, while Recover is still the only action and the change is provably behaviour-preserving.

- `WalkerActionRegistry.Entry` gains `modifierWindow: Option[PowerWindow]`. `ActionRef.Recover`'s entry declares `Some(PowerWindow.RecoverModifierSelection)`, so today's behaviour is unchanged by construction. It is optional because not every action has such a window: `PowerWindow` has a `*ModifierSelection` case for each of the eight `MajorActionType`s, and Take Wealth is not one of them — its only window is `WakeTakeWealth`, an `OtherWindow` with no associated major action. `None` means the action offers no player-selected powers, so `offerableWalkerPowers` returns empty and `validateModifiers` rejects any id. Do not invent a `WakeModifierSelection` window to avoid the option; a window that exists only to satisfy a signature is a window a reviewer cannot audit.
- `WalkerActionRegistry.modifierWindow(action): Either[OathViolation, Option[PowerWindow]]`, alongside the existing `fallbackKind` / `rollDecisionId` accessors.
- `OathRules.offerableWalkerPowers(ready, actor, action)` and `validateModifiers(ready, actor, action, modifiers)` take the `ActionRef` and read the window from the registry. Neither names a window literal.
- `GameApplicationService.preview` already resolves the `ActionRef` at `walkerAction(action)` and discards it as `case Some(_)`. Bind it and pass it through.

- [ ] **Step 1: failing tests** in `WalkerActionRegistrySuite`: (a) register a second `Entry` in a test-local `registrations` map declaring a *different* modifier window, and assert `offerableWalkerPowers` returns a different set for it than for Recover — a fixture `PlayerSelected` power applicable only at the second window is offered for the second action and not for Recover; (b) an entry declaring `None` offers nothing and rejects every modifier id. Expected FAIL: the accessor does not exist and both calls consult Recover's window.
- [ ] **Step 2: implement** the four bullets above. No behaviour change for Recover.
- [ ] **Step 3:** re-run the focused suite; expected PASS. Confirm the existing Recover modifier tests still pass untouched.
- [ ] **Step 4:** `./sbtw "test"`, `./sbtw "frontend/test" "frontend/fastLinkJS"`, `python3 scripts/check-architecture.py`, `git diff --check`.
- [ ] **Step 5: commit** `refactor(walker): let each registered action own its modifier window`.

---

### Task 1b: Extract the walker command surface out of `OathRules`

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala`

Inserted after Task 1 reported that `OathRules.scala` now sits at exactly 800 lines — the cap `scripts/check-architecture.py` enforces, which fails at 801. Task 1 reached it by tightening doc comments, which is not a strategy that survives another round. Tasks 3, 5 and 7 all modify this file, and Task 5 in particular must thread a travel destination through `startWalker`. The next line added to `OathRules` fails the build, and the global constraints forbid raising the cap.

Doing this now, alone, keeps it a reviewable pure move. Folded into Task 3 instead, the Forge cutover commit would carry a 300-line file relocation alongside its real change, and neither would get read properly.

The walker command surface is already contiguous: `startWalker` at line 131 through `parkedContinue` ending around line 456 — `startWalker`, `walkerPowers`, `offerableWalkerPowers`, `validateModifiers`, `checkRestrictions`, `eligibilityGathered`, `resolveWalker`, `rollWalkerPrepared`, `resumeWalker`, `walkerResumeContext`, `buildWalker`, `walkerCall`, `walkerTransition`, `foldEvents`, `parkedContinue`. That block is the extraction. Everything above it is legacy per-action `handle` methods and everything below is turn/phase plumbing.

The mechanism is the implementer's call — a trait `OathRulesWalker` that `OathRules` mixes in is the cheapest thing that keeps `catalog` and `walkerPowerCatalog` reachable and every call site unchanged, but a collaborator class taking those two as constructor arguments is equally acceptable if it reads better. What is not acceptable is changing what any of these methods does.

- [ ] **Step 1:** move the block. No signature changes, no behaviour changes, no doc-comment rewrites beyond what the move mechanically requires. Public methods stay public; `validateModifiers` keeps the `private[gameplay]` visibility Task 1 gave it.
- [ ] **Step 2:** the proof of a pure move is that **not one test file is edited**. Run `./sbtw "test"` and confirm 587 passing with `git status` showing no change under `src/test/`. If a test needs editing, the move was not pure — stop and report what forced it.
- [ ] **Step 3:** `python3 scripts/check-architecture.py`, and record both files' line counts in the report. `OathRules.scala` should land far enough below 800 that Tasks 3, 5 and 7 have room; if it does not, say so, because that means the extraction was too small to solve the problem it exists for.
- [ ] **Step 4:** `./sbtw "frontend/test" "frontend/fastLinkJS"` and `git diff --check`.
- [ ] **Step 5: commit** `refactor(walker): extract the walker command surface from OathRules`.

---

### Task 2: Forge declares its tree

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/forge/ForgeProcedure.scala`
- Test: `src/test/scala/oathdigital/gameplay/ForgeProcedureSuite.scala`

Declared tree, mirroring `Forge.handle`/`Forge.evolve`'s observable flow:

```
Sequence(                                      // window = ForgeActionEligibility
  BuildOps(AdjustSupply(actor, -1)),           // window = ForgeCost
  Decide("forge.assignment",                   // declarative Partition query
    sections = Pay Favor / Pay Secret,
    options = the three eligible denizens),
  BuildOps(PayCost(actor, each assigned denizen),
           optional Play(relicDeck top -> play area, FaceDown)))
```

Start gates are `ForgeRules.validate`: exile-only unaltered foundations, audited catalog, actor rules their pawn site, printed Forge cost present and totalling three resources, exactly three empty faceup denizens at the site, and the actor can afford the printed favor/secret total. Supply and relic availability are deliberately not start gates. The first `AdjustSupply` is validated by `OperationPipeline` before the walker can park, so zero Supply rejects without starting a pending procedure. An empty relic deck is legal: the player still spends Supply and pays the printed resources, but the trailing `BuildOps` has no relic `Play` to emit.

The `Decide` uses the declarative `DecisionQuery.Partition` contract from `docs/superpowers/specs/2026-09-10-declarative-walker-decisions-design.md`: the printed favor/secret amounts become the two section minima and the three live empty faceup denizens become its options. The generic walker validates that every option is assigned exactly once and both minima are met. The trailing `BuildOps` converts each placement into `PayCost(actor, Location.OnCard(denizen), Cost(favor = 1))` or its secret equivalent. Those resources come from the actor's play area; Forge never consults suit banks. `OperationPipeline` validates the concrete payments atomically again at resolution.

The relic id is the authoritative relic-deck top and is **not** closed over by the tree. Nor does it ride the answer or come from a randomness port. The trailing `BuildOps` reads `headOption` at execution time and emits `Play` only when a top relic exists. Replay records and reapplies that optional `Play` exactly.

- [ ] **Step 1: failing tests** in `ForgeProcedureSuite`: (a) `build` rejects every semantic start gate, including not ruling the pawn site and insufficient aggregate player favor/secrets; (b) zero Supply passes `build` but the first `AdjustSupply` is rejected before any park; (c) an empty relic deck passes `build`, the complete action still pays Supply/resources, and no `Play` is emitted; (d) a successful build has the tree above; (e) the partition query exposes exactly the three live denizens and printed minima; (f) stale/malformed partition answers reject generically; (g) a legal answer produces exactly three player-funded `PayCost`s plus an optional top-relic `Play`.
- [ ] **Step 2: implement** `ForgeProcedure` against the declarative decision contract, with aggregate affordability at the start gate and authoritative operation validation at execution.
- [ ] **Step 3:** re-run the focused suite; expected PASS.
- [ ] **Step 4:** `./sbtw "test"` and `python3 scripts/check-architecture.py`.
- [ ] **Step 5: commit** `feat(walker): declare the Forge procedure tree`.

---

### Task 3: Forge cuts over and its legacy path is deleted

**Files:**
- Modify: `src/main/scala/oathdigital/model/ActionRef.scala`, `WalkerActionRegistry.scala`, `OathRulesWalker.scala`, `OathRules.scala`, `WalkerDecisionProjector.scala`, `GameIntentMapper.scala`, `LegalActionProjector.scala`, `PendingProcedureProjector.scala`, `GameApplicationService.scala`, `frontend/.../ActionDecisionRenderer.scala`

Note (from Task 1b): the walker command surface now lives in `OathRulesWalker.scala`, not `OathRules.scala` — `startWalker`, `buildWalker` and `rollDecisionId`'s call site are all there. `OathRules.scala` keeps `declaredWalkerTree` and `eligibilityRelaxed`.
- Delete: `src/main/scala/oathdigital/gameplay/actions/Forge.scala`'s `Forge` object (keeping `ForgeRules`, which `ForgeProcedure` consumes), the `ForgeStarted`/`ForgeCompleted` event cases, their `ActionEventCodec` branches and `GameEventWire` types, `PendingProcedure.Forge`, `OathContinue.AwaitingForgeAssignment`, and `ForgeCommand`.

`ActionRef` gains `Forge` and `ActionRef.all` gains it in the same edit — `WalkerActionRegistrySuite` asserts the registry covers `ActionRef.all`, so a missing entry fails loudly rather than at runtime. The entry declares `fallbackKind = MajorActionKind.Forge`, `modifierWindow = Some(PowerWindow.ForgeModifierSelection)`, no `rollDecisionId` park (Forge has no `Roll` node — see the note below), and `continuationFor` mapping `"forge.assignment"` to its client-facing continuation.

`Entry.rollDecisionId` is currently a bare `String` because Recover has a roll. Forge does not. Make it `Option[String]` rather than inventing an unreachable sentinel id: `OathRules.parkedContinue` and `WalkerDecisionProjector` both consult it, and a `None` there must produce a typed rejection, not a silent match against a string no tree ever uses.

- [ ] **Step 1: failing test** — an end-to-end Forge through `GameApplicationService` using only `StartWalker`/`ResolveWalker`, asserting the same final state the legacy `ForgeCommand` path produced (three denizens each carrying their resource, relic facedown in the play area, 1 supply spent, no pending). Plus a replay assertion: reconstructing from the journal reproduces that state. Expected FAIL: `ActionRef.Forge` does not exist.
- [ ] **Step 2: implement** the registry entry, the `Option[String]` roll-id change with its two call sites, and the projector/mapper wiring.
- [ ] **Step 2b: the eligibility window becomes per-action too.** `OathRules.eligibilityRelaxed` names `PowerWindow.RecoverActionEligibility` as a literal — the same shape Task 1 removed from the modifier window, one window over, found by Task 1b. It is reached by every `startWalker`, so registering Forge makes a second action gather at Recover's eligibility window. It is inert only while no power is applicable there, and Task 4 registers real powers. Move the window onto `WalkerActionRegistry.Entry` beside `modifierWindow`, with the same `Option` treatment and the same `registrations` override, and update the projector call site. Prove it the way Task 1 was proven: a fixture power applicable only at another action's eligibility window relaxes for that action and not for Recover, and a mutation restoring the literal fails that test.
- [ ] **Step 2c: the walker event codec has no branch for the Forge assignment payload** (found by Task 2). `WalkerEventCodec.encodeDecisionPayload` matches the two Recover payloads and then *throws* `IllegalArgumentException`; the decode side returns a typed `Left`. So the first journalled Forge throws, and no test in Task 2 could catch it because nothing there reaches the codec. Add both branches. Note the file is at 771 of the 800-line cap, so budget for that the way Task 1b had to. Two further things while you are in there, both judgement calls to make explicitly rather than by default: whether the encode side should return a typed error like its decode counterpart instead of throwing, and whether an exhaustive match would have made this a compile error rather than a runtime one.
- [ ] **Step 3: delete** the legacy path in the same commit, and delete or port each legacy Forge test to the walker path. A test asserting a deleted event's codec round-trip is deleted with the event. `OathViolation.ForgeDecisionMismatch` loses its only producer with the legacy path — the walker checks actor, phase and decision id generically — so it goes too.
- [ ] **Step 4:** re-run; expected PASS. `grep -rn "ForgeCommand\|ForgeStarted\|ForgeCompleted\|PendingProcedure.Forge" src frontend` returns nothing outside the journal fixtures being deleted.
- [ ] **Step 5:** `./sbtw "test"`, `./sbtw "frontend/test" "frontend/fastLinkJS"`, `python3 scripts/check-architecture.py`.
- [ ] **Step 6: commit** `feat(walker): move Forge onto the walker and delete its legacy path`.

**Report before Task 4.** Forge is the recipe proof. If porting it needed anything the plan did not anticipate — an engine change, a new `Operation` case, a window that did not exist — say so plainly in the ledger before Travel starts, because Travel and Wake were scoped assuming the recipe holds.

---

### Task 4: Travel's terrain costs become transforms

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/TravelSitePowersSuite.scala`

The terrain powers become `ContributingPower`s before Travel's tree exists, so the reconciliation is proven against the collector in isolation rather than tangled with the tree port.

Each terrain site power becomes its own contribution class, grouped in one file:

- **Mountain** and **Island**: a `Transform` at `PowerWindow.TravelCost` that increases the pay node's `AdjustSupply` amount by 1 and 2 respectively, applicable only when the power's site is the destination.
- **Coast**: a `Transform` at the same window that *replaces* the amount with 1, applicable only on a coast route (this power's site is the source, the source is coastal, the destination is coastal or an island). It uses `shouldIgnore(other)` to drop destination-side adds — the single load-bearing mechanism that `SuppressionRegistry` expresses today, now stated on the power that owns the rule. It declares no priority.
- **Narrow Pass**: a `Restriction` at `PowerWindow.TravelActionEligibility` returning `OathViolation.TravelPassBlocked` — the typed violation directly, which retires `TravelPassBlockedCodec`'s encode/decode round-trip through a reason string.

**How a windowed node presents its children, which decides the shape of every transform here** (found by Task 2). A `Transform` receives the hooked node's children vector. For a `PrimitiveOperation` that vector is `Vector(theLeafItself)` — a leaf's `children` is a self-reference — so a transform hooked on a windowed *leaf* can only replace that leaf wholesale, and cannot see inside a `BuildOps` closure at all. For a composite it is the real children.

Therefore Travel's cost node is `Sequence(AdjustSupply, Move)` carrying
`PowerWindow.TravelCost`, wrapped by `TravelActionEligibility`, **not** a
`BuildOps` wrapping an `AdjustSupply`.
The walker executes the plain delta leaf directly, and each terrain transform
receives both `AdjustSupply(actor, -base)` and its sibling destination `Move`.
It rewrites the amount honestly, without reconstructing base-cost computation
inside a replacement closure.

Only four `Operation` cases carry a `window` at all — `ModifyDicePool`, `Decide`, `BuildOps` and `Sequence` — so a delta that needs to be hookable is made hookable by the composite it sits in, not by itself.

The route facts each `applicable` needs (which site is source, which is destination) come from `ctx.state` and the hooked operation. The actor's pawn site is the source and the destination rides the windowed `Sequence` as its sibling `Move`. **CR1 (controller ruling):** `PowerCtx` carries that generic hooked `operation`, populated at every gather call, so applicability and named ignore can inspect its route without the collector or walker knowing Travel. `PowerCtx` remains catalog-free; static catalog data stays on a catalog-parameterized contribution factory such as `CatacombsContribution.forCatalog`.

- [ ] **Step 1: failing tests** in `TravelSitePowersSuite` driving `ContributionCollector.gather` directly, one per parity case the retired `TravelCostWindow.fold` implements: ordinary route to a mountain (+1), to an island (+2), coast route (replaced with 1), coast route where the destination also has an add (the add is ignored, not stacked), non-coast route from a coastal site (no replace), and a pass crossing regions (blocked) versus a coast route past a pass (allowed). Expected FAIL: the powers do not exist.
- [ ] **Step 2: implement** the powers and register them in `WalkerPowerCatalog.default`.
- [ ] **Step 3:** re-run; expected PASS. Confirm each case's number matches what `TravelCostWindow.fold` returns for the same route today — parity is the acceptance criterion, not plausibility.
- [ ] **Step 3b: prove each transform actually fires.** A transform whose pattern match hits nothing returns its input unchanged and is indistinguishable from an absent power in every assertion about a *route*, because the base cost still comes back. So for each terrain kind, assert the cost differs from the base cost with that power absent, and additionally mutate one transform's match so it hits nothing and quote the resulting failure. This is the vacuity trap this task is most likely to fall into, and a green suite is not evidence against it.
- [ ] **Step 4:** `./sbtw "test"` and `python3 scripts/check-architecture.py`. The architecture suite must still find no engine source naming any of these powers.
- [ ] **Step 5: commit** `feat(powers): state Travel terrain costs as walker contributions`.

---

### Task 5: Travel cuts over, and the typed-cost vocabulary is deleted

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/travel/TravelProcedure.scala`
- Modify: `ActionRef.scala`, `WalkerActionRegistry.scala`, `OathRulesWalker.scala`, the projectors, the frontend renderer
- Delete: `actions/Travel.scala`'s `Travel` object and `TravelLegality`, `actions/TravelOperationPolicy.scala`, `powers/travel/TravelCostWindow.scala`, `powerresolver/CostContribution` and `SuppressionRegistry` (`PowerContributions.scala`), `TravelPassBlockedCodec`, the `Traveled` event with its codec branch and wire type, and `TravelCommand`

Travel's tree is flat — no `Decide`, no `Roll`:

```
Sequence(                                      // TravelActionEligibility
  Sequence(                                    // TravelCost
    AdjustSupply(actor, -base),
    Move(Pawn, source -> destination)))
```

The cost node is a windowed `Sequence` around the bare `AdjustSupply` leaf and
its sibling `Move`, for the reason Task 4 sets out: a transform hooked on a
windowed leaf receives only that leaf and cannot reach inside it, while this
composite hands over both real children. The outer eligibility `Sequence` wraps
that complete cost node, so the Narrow Pass restriction sees the same route.

The base cost is the printed region-to-region table from `TravelRules.cost`, with the `TravelCostWindow.fold` call removed — terrain is now Task 4's transforms folding over this node. The destination rides the `StartWalker` command, so the entry's `build` receives it; `Recover` derives its site from state and needs no such parameter, so this is the first action to need a start argument. Carry it the way `eligibilityRelaxed` is carried — as plain data on the entry's `build` — rather than teaching the walker about destinations.

Task 1b's finding, which shapes this: the resume path passes no extra data to `buildWalker`, so anything threaded into a `starting` build must be re-derivable from state on resume or persisted the way `walkerModifiers` already is. Travel's flat tree may never park, in which case this is moot — establish that first rather than building persistence for a resume that cannot happen.

Deleting `CostContribution` and `SuppressionRegistry` is the point of this task, not a bonus. If either still has a live reference after the port, the reconciliation is incomplete: say so rather than leaving both vocabularies alive.

- [ ] **Step 1: failing test** — end-to-end Travel through `GameApplicationService` on `StartWalker` alone, asserting the pawn moved and the exact supply spent, for a plain route, a mountain route, and a coast route. Plus: an insufficient-supply start is rejected before any event is appended, and a pass-blocked route is rejected by the restriction. Plus replay parity.
- [ ] **Step 2: implement** `TravelProcedure` and the registry entry.
- [ ] **Step 3: delete** the legacy path and the typed-cost vocabulary in the same commit; port or delete each legacy Travel test.
- [ ] **Step 4:** `grep -rn "CostContribution\|SuppressionRegistry\|TravelCostWindow\|TravelCommand\|Traveled" src frontend` returns nothing.
- [ ] **Step 5:** `./sbtw "test"`, `./sbtw "frontend/test" "frontend/fastLinkJS"`, `python3 scripts/check-architecture.py`.
- [ ] **Step 6: commit** `feat(walker): move Travel onto the walker and retire the typed-cost vocabulary`.

---

### Task 6: Take Wealth's once-per-turn limit as a power

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/wake/TakeWealthPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/TakeWealthPowerSuite.scala`

Take Wealth is a site power used at most once per turn per site, tracked as a `PowerUseRef` in `TurnState.usedPowers`. The spec's standing decision is that this tracking stays state and no walker code reads it. That decision predates any walker action needing it, and this task tests whether it survives contact.

Attempt, in this order, and stop at the first that works:

1. **A `Restriction` reading `ctx.state`.** The power declares a restriction at `PowerWindow.WakeTakeWealth` returning `OathViolation.PowerAlreadyUsed` when `ctx.state.game.current.turn.usedPowers` already contains this site's `PowerUseRef`. The walker never reads `usedPowers`; one power does, through the state every contribution already sees. This keeps the spec's decision intact and needs no engine change.
2. If (1) cannot express it, record precisely what it could not reach and **stop for a ruling.** Do not add a field to `PowerCtx`, and do not add a "power was used" concept to the walker, on your own judgement. The spec commits to `usedPowers` staying outside the power framework; changing that is a spec amendment, and a plan task is not where a spec gets amended.

The write side — appending the `PowerUseRef` after a successful take — stays engine state, written by the tree's own `BuildOps`, not by a power. A contribution returns operations; it does not mutate turn state.

- [ ] **Step 1: failing tests**: the restriction allows the first take at a site, blocks the second at the same site in the same turn with `PowerAlreadyUsed`, allows a take at a *different* site in the same turn, and allows the same site again after the turn advances. Drive them through `ContributionCollector.gather` plus the restriction, not through the full action.
- [ ] **Step 2: implement** attempt 1, or stop and report.
- [ ] **Step 3:** re-run; expected PASS.
- [ ] **Step 4:** `./sbtw "test"` and `python3 scripts/check-architecture.py`.
- [ ] **Step 5: commit** `feat(powers): state Take Wealth's once-per-turn limit as a restriction`.

---

### Task 7: Wake cuts over and its legacy path is deleted

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/phases/wake/TakeWealthProcedure.scala`
- Modify: `ActionRef.scala`, `WalkerActionRegistry.scala`, the projectors, the frontend renderer
- Delete: `phases/Wake.scala`'s `Wake` object, `phases/TakeWealthRules.scala`, `phases/WakeOperationPolicy.scala`, the `WealthTaken` event with its codec branch and wire type, and `WakeCommand.TakeWealth`

```
Sequence(                                          // window = WakeTakeWealth
  BuildOps(Take(Favor(1)|Secrets(1), site -> play area)),
  BuildOps(record the site's PowerUseRef in usedPowers))
```

Start gates: Wake phase (not Act — this is the first registered action gating on a phase other than Act, and it is the point of including Wake), actor's pawn at the site, no enemy pawn at the site, the requested resource present in the site's tokens. The once-per-turn gate is Task 6's restriction, gathered at the root window, not a gate in `build`.

The entry declares `fallbackKind = MajorActionKind.Wake` and `modifierWindow = None` — Take Wealth has no modifier-selection window, which is the case Task 1 made the field optional for.

**Two Act-phase assumptions in the walker surface, found by Task 1b, that this task is the first to meet.** Neither is a bug today; both are decisions this task must take deliberately rather than discover.

1. `walkerResumeContext` requires `Phase.Act` and rejects any other phase with `WrongPhase`. The plan's claim that the walker never checks phase is true of `startWalker` and false of resume. It only matters if Take Wealth's tree ever parks — a flat two-leaf tree does not, so establish that before changing anything. If it never parks, say so in the report and leave the check alone.
2. `walkerTransition`'s Finished branch hardcodes `OathContinue.ActActionSelection` and runs the Act boundary pipeline through `completeAction` for every completed walker action, whatever the phase. The legacy Wake path never called `completeAction` at all — a completed Take Wealth returns to `AwaitingWakeAction`, not to Act action selection. Porting Wake as-is would therefore end the player's Wake phase after one take. Fix this deliberately, and make the continuation registry data if that is what it takes; do not let it become a behaviour change nobody chose.

`WakeCommand.EndWake` is not an action and does not move to the walker; it stays a phase transition. Keep it and say so, rather than porting it for symmetry.

- [ ] **Step 1: failing test** — end-to-end Take Wealth through `GameApplicationService` on `StartWalker`, asserting the token moved, the `PowerUseRef` recorded, and a second take at the same site rejected. Plus a take during the Act phase rejected. Plus replay parity.
- [ ] **Step 2: implement** the procedure and the registry entry.
- [ ] **Step 3: delete** the legacy path in the same commit; port or delete each legacy Wake test.
- [ ] **Step 4:** `./sbtw "test"`, `./sbtw "frontend/test" "frontend/fastLinkJS"`, `python3 scripts/check-architecture.py`.
- [ ] **Step 5: commit** `feat(walker): move Take Wealth onto the walker and delete its legacy path`.

---

### Task 8: Batch close-out

**Files:**
- Modify: `docs/superpowers/specs/2026-09-05-procedure-walker-design.md`

Record what the batch settled, in the spec, so the next batch's plan is written against facts rather than against this plan's assumptions:

- Whether the recipe generalised unchanged, and any `Entry` or `Operation` shape that had to change to admit a second, third and fourth action (`rollDecisionId` becoming optional is already one).
- The resolution of `CostContribution` versus `Transform`, and that the typed-cost vocabulary is retired.
- The resolution of turn-scoped activation: whether decision 11 survived contact, and if a ruling changed it, what the new rule is.
- Anything a power needed that `PowerCtx` could not reach, and what was done instead.
- That a walker action's decision ids are constants, not per-command tokens. Recover set this at its cutover and Forge inherited it; the legacy Forge path minted `DecisionId(s"forge-$nextSequence")` per command and used it as a stale-request token. Staleness is now covered by `expectedNextSequence` and the one-pending-action invariant instead. Worth stating in the spec so the next batch does not rediscover it as a regression.

**Carried here from Task 3 (R19): the walker event codec's encode side throws.**
`WalkerEventCodec.encodeDecisionPayload` throws on a payload it has no branch
for, while its decode counterpart returns a typed error. Task 3 established
that an exhaustive match cannot fix this — spec decision 11 makes
`DecisionPayload` an open trait deliberately — so a typed error is the right
answer. The failure mode argues for it harder than expected: `encodePayloadSafe`
swallows the throw, so a missing branch makes an action silently unplayable
*after* its cost has been spent, rather than failing loudly. **The file is at
795 of the 800-line cap**, so this needs a Task-1b-style extraction FIRST, as
its own commit, not folded into the conversion.

- [ ] **Step 0:** extract from `WalkerEventCodec.scala` until it has real
  headroom, as a pure move proven by no test file being edited — the same
  acceptance criterion Task 1b used. Then convert the encode side to a typed
  error, as its own commit.
- [ ] **Step 1:** write the spec updates above. Correct any spec text this batch falsified rather than appending a note beside it.
- [ ] **Step 2:** `grep -rn "PendingProcedure" src/main` — list which cases remain and which actions still own them, as the starting inventory for the next batch's plan.
- [ ] **Step 3:** full gate `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`, `python3 scripts/check-architecture.py`, `git diff --check`.
- [ ] **Step 4: commit** `docs(spec): record what walker batch 1 settled`.
