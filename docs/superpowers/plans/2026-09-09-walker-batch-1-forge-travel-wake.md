# Walker Batch 1: Forge, Travel, Wake — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the first batch of actions off their bespoke handle/evolve state machines onto the generic walker, and delete each legacy path as it goes. The batch is chosen for information, not for ease: Forge proves the migration recipe generalises past Recover, Travel forces the `CostContribution`-versus-`Transform` reconciliation, and Wake forces the turn-scoped-activation question. Whatever this batch teaches shapes the plan for the remaining seven actions.

**Architecture:** Each action declares one `Operation` tree (`build` for a fresh start, `rebuild` to resume), registers one `WalkerActionRegistry.Entry`, and its powers become `ContributingPower` objects contributing `Transform`s and `Restriction`s at the windows its tree nodes carry. `ProcedureWalker` executes the tree; the application layer projects parked decisions and prepares randomness. Nothing in the engine learns an action's or a power's name beyond its registry entry.

**Tech Stack:** Scala 2.13, sbt multi-project (root engine + frontend Scala.js), munit, ujson. Full gate: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.

**Spec:** `docs/superpowers/specs/2026-09-05-procedure-walker-design.md` (approved). This plan implements the first batch of the spec's migration-plan step 3, and takes the step-4 deletions that each ported action makes possible. The remaining actions (Search, Economy, Challenge, Campaign, Negotiation, CardPlay, Rest, Visions) are out of scope and get their own plan once this batch reports.

---

## Why these three

`Recover` is the only action on the walker today, and it is a poor sample of one: it is the action the walker was designed against. Picking batch 1 by ease would port three more Recover-shaped actions and learn nothing. Each pick here answers a question that is currently open.

**Forge — does the recipe generalise?** `ForgePowers.powers` is `Vector.empty`, so Forge is a pure structural port with no power reconciliation to confound it. Its shape is the one Recover did not exercise: two commands (`Begin`, `Complete`), one `Decide` carrying a substantial validation body, no dice, no `Repeat` loop. It also re-tests spec decision 4 from the other side — the forged relic is the authoritative relic-deck top, a server-prepared value that must ride the resolve answer rather than the engine reading a port. If the recipe is wrong, Forge is where it is cheapest to find out.

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
  Decide("forge.assignment"),                  // validate = assignment legality
  BuildOps(favor/secret moves onto the three denizens,
           Play(relic, relicDeck top -> play area, FaceDown)))
```

Start gates are `ForgeRules.validate`: exile-only unaltered foundations, audited catalog, actor rules their pawn site, printed Forge cost present and totalling three resources, exactly three empty faceup denizens at the site, supply ≥ 1, relic deck non-empty. `rebuild` re-derives the same tree and does **not** re-run supply ≥ 1, matching `RecoverProcedure.rebuild`'s rule that a start-only gate never re-gates a resume.

The `Decide`'s `validate` is `Forge.validateCompletion`'s body: exactly three assignments to three distinct targets, targets equal to the eligible set, resources matching the printed cost, and each suit's favor bank able to cover its demand. Read the eligible targets live off `ready` rather than off the tree's closure, the way `RecoverProcedure.actorFacedownRelics` does, so the projector's candidate list and the set the resolver accepts have one definition.

The relic id is the authoritative relic-deck top and is **not** closed over by the tree. It rides the answer: the application layer reads `commonCards.relicDeck.head` in its prepare callback and the trailing `BuildOps` reads it from `pending.answered`, so the engine touches no port and a replay applies the recorded `Play` op.

- [ ] **Step 1: failing tests** in `ForgeProcedureSuite` against the first-game fixture: (a) `build` rejects each start gate above, one test per gate, asserting the exact `OathViolation`; (b) a successful `build` produces a tree whose root window is `ForgeActionEligibility` and whose first leaf is the `ForgeCost`-windowed supply payment; (c) walking to the park stops at `"forge.assignment"` with the actor as owner; (d) an assignment answer that names a stale target is rejected by the `Decide`'s validate; (e) a legal answer produces exactly the favor/secret moves plus the `Play`, and no other op. Expected FAIL: `ForgeProcedure` does not exist.
- [ ] **Step 2: implement** `ForgeProcedure` with `build`, `rebuild`, `assignmentDecisionId`, and an `eligibleTargets(ready, actor)` reader the projector will share.
- [ ] **Step 3:** re-run the focused suite; expected PASS.
- [ ] **Step 4:** `./sbtw "test"` and `python3 scripts/check-architecture.py`.
- [ ] **Step 5: commit** `feat(walker): declare the Forge procedure tree`.

---

### Task 3: Forge cuts over and its legacy path is deleted

**Files:**
- Modify: `src/main/scala/oathdigital/model/ActionRef.scala`, `WalkerActionRegistry.scala`, `WalkerDecisionProjector.scala`, `GameIntentMapper.scala`, `LegalActionProjector.scala`, `PendingProcedureProjector.scala`, `GameApplicationService.scala`, `OathRules.scala`, `frontend/.../ActionDecisionRenderer.scala`
- Delete: `src/main/scala/oathdigital/gameplay/actions/Forge.scala`'s `Forge` object (keeping `ForgeRules`, which `ForgeProcedure` consumes), the `ForgeStarted`/`ForgeCompleted` event cases, their `ActionEventCodec` branches and `GameEventWire` types, `PendingProcedure.Forge`, `OathContinue.AwaitingForgeAssignment`, and `ForgeCommand`.

`ActionRef` gains `Forge` and `ActionRef.all` gains it in the same edit — `WalkerActionRegistrySuite` asserts the registry covers `ActionRef.all`, so a missing entry fails loudly rather than at runtime. The entry declares `fallbackKind = MajorActionKind.Forge`, `modifierWindow = Some(PowerWindow.ForgeModifierSelection)`, no `rollDecisionId` park (Forge has no `Roll` node — see the note below), and `continuationFor` mapping `"forge.assignment"` to its client-facing continuation.

`Entry.rollDecisionId` is currently a bare `String` because Recover has a roll. Forge does not. Make it `Option[String]` rather than inventing an unreachable sentinel id: `OathRules.parkedContinue` and `WalkerDecisionProjector` both consult it, and a `None` there must produce a typed rejection, not a silent match against a string no tree ever uses.

- [ ] **Step 1: failing test** — an end-to-end Forge through `GameApplicationService` using only `StartWalker`/`ResolveWalker`, asserting the same final state the legacy `ForgeCommand` path produced (three denizens each carrying their resource, relic facedown in the play area, 1 supply spent, no pending). Plus a replay assertion: reconstructing from the journal reproduces that state. Expected FAIL: `ActionRef.Forge` does not exist.
- [ ] **Step 2: implement** the registry entry, the `Option[String]` roll-id change with its two call sites, and the projector/mapper wiring.
- [ ] **Step 3: delete** the legacy path in the same commit, and delete or port each legacy Forge test to the walker path. A test asserting a deleted event's codec round-trip is deleted with the event.
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
- **Coast**: a `Transform` at the same window that *replaces* the amount with 1, applicable only on a coast route (this power's site is the source, the source is coastal, the destination is coastal or an island). It declares `priority` above the adds so a replace lands last, and `shouldIgnore(other)` drops the destination-side adds — this is the named-ignore that `SuppressionRegistry` expresses today, now stated on the power that owns the rule.
- **Narrow Pass**: a `Restriction` at `PowerWindow.TravelActionEligibility` returning `OathViolation.TravelPassBlocked` — the typed violation directly, which retires `TravelPassBlockedCodec`'s encode/decode round-trip through a reason string.

The route facts each `applicable` needs (which site is source, which is destination) are not on `PowerCtx`. Read them from `ctx.state`: the actor's pawn site is the source and the destination rides the tree. **This is the open question of the task** — if the destination is not reachable from `PowerCtx` plus `ctx.state`, do not widen `PowerCtx` to fix it. Record the gap in the ledger with what the power actually needed, and stop for a ruling. `PowerCtx` staying catalog-free and narrow is a spec commitment, not an accident, and the last time a power needed static catalog data the answer was a factory on the power (`CatacombsContribution.forCatalog`), not a new context field.

- [ ] **Step 1: failing tests** in `TravelSitePowersSuite` driving `ContributionCollector.gather` directly, one per parity case the retired `TravelCostWindow.fold` implements: ordinary route to a mountain (+1), to an island (+2), coast route (replaced with 1), coast route where the destination also has an add (the add is ignored, not stacked), non-coast route from a coastal site (no replace), and a pass crossing regions (blocked) versus a coast route past a pass (allowed). Expected FAIL: the powers do not exist.
- [ ] **Step 2: implement** the powers and register them in `WalkerPowerCatalog.default`.
- [ ] **Step 3:** re-run; expected PASS. Confirm each case's number matches what `TravelCostWindow.fold` returns for the same route today — parity is the acceptance criterion, not plausibility.
- [ ] **Step 4:** `./sbtw "test"` and `python3 scripts/check-architecture.py`. The architecture suite must still find no engine source naming any of these powers.
- [ ] **Step 5: commit** `feat(powers): state Travel terrain costs as walker contributions`.

---

### Task 5: Travel cuts over, and the typed-cost vocabulary is deleted

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/travel/TravelProcedure.scala`
- Modify: `ActionRef.scala`, `WalkerActionRegistry.scala`, the projectors, the frontend renderer
- Delete: `actions/Travel.scala`'s `Travel` object and `TravelLegality`, `actions/TravelOperationPolicy.scala`, `powers/travel/TravelCostWindow.scala`, `powerresolver/CostContribution` and `SuppressionRegistry` (`PowerContributions.scala`), `TravelPassBlockedCodec`, the `Traveled` event with its codec branch and wire type, and `TravelCommand`

Travel's tree is flat — no `Decide`, no `Roll`:

```
Sequence(                                 // window = TravelActionEligibility
  BuildOps(AdjustSupply(actor, -base)),   // window = TravelCost
  BuildOps(Move(Pawn, source -> destination)))
```

The base cost is the printed region-to-region table from `TravelRules.cost`, with the `TravelCostWindow.fold` call removed — terrain is now Task 4's transforms folding over this node. The destination rides the `StartWalker` command, so the entry's `build` receives it; `Recover` derives its site from state and needs no such parameter, so this is the first action to need a start argument. Carry it the way `eligibilityRelaxed` is carried — as plain data on the entry's `build` — rather than teaching the walker about destinations.

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

- [ ] **Step 1:** write the spec updates above. Correct any spec text this batch falsified rather than appending a note beside it.
- [ ] **Step 2:** `grep -rn "PendingProcedure" src/main` — list which cases remain and which actions still own them, as the starting inventory for the next batch's plan.
- [ ] **Step 3:** full gate `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`, `python3 scripts/check-architecture.py`, `git diff --check`.
- [ ] **Step 4: commit** `docs(spec): record what walker batch 1 settled`.
