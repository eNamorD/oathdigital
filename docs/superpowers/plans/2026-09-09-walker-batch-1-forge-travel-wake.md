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

## The declarative decision contract has landed (added 2026-09-11)

`docs/superpowers/plans/2026-09-11-declarative-walker-decisions.md` is complete.
Everything Task 2's text above describes in the declarative vocabulary now
exists in shipped code, so that text and `ForgeProcedure` no longer disagree:
`DecisionQuery`, `DecisionOption`, `DecisionOptionRef`, `DecisionSection`,
`DecisionPlacement` and both `DecisionAnswer` cases live in
`model/Decisions.scala`, and one generic validator in `DecisionQueries`
replaces every per-action `validate` closure that used to guard a `Decide`.
Forge's assignment decision is a real `DecisionQuery.Partition` and Recover's
two decisions are `ChooseOne` queries.

**What this means for the tasks still open.** Any action that declares a
`Decide` from here on states a query and writes no validation closure — the
walker validates the answer generically and `WalkerDecisionProjector` projects
the same transformed query, so what is legal and what is offered can no longer
drift apart. Prompt copy for a heading, a confirm control, a button or a
section is authored on the query by the action that asks the question; game-
object names are still resolved by `GamePresentationProjector` from an option's
reference and never enter a query.

**Travel is not affected.** Task 5 below gives Travel a flat tree with no
`Decide` at all — destination selection is the parameter that selects a
complete tree, not a persisted interruption inside one — so there is no
closure there to replace and no query to declare. The contract binds the next
action that actually parks. That correction supersedes the declarative plan's
own framing, which was written expecting Travel to carry a decision.


---

## Where this plan stands (2026-09-12)

Tasks 1 through 5 are done and their boxes are ticked below, each annotated with
the commit that closed it, and Tasks 6 and 7 followed. **The batch resumes at
Task 8, the close-out.**

| Task | State | Commits |
|---|---|---|
| 1 — per-action modifier window | done | `f9fa243` |
| 1b — extract the walker surface | done | `9f0f16c` |
| 2 — Forge declares its tree | done | `d46f511`, migrated by `9e078cc` |
| 3 — Forge cutover + legacy delete | done | `4f209b3`, `b0640e8`, `b49ff11` |
| 4 — Travel terrain as contributions | done | `d8d6853`, hardened to `2e4a0a6` |
| 5 — Travel cutover + vocabulary delete | done | `c70ec1a` |
| 6 — Take Wealth once-per-turn | done | `654e75a` |
| 7 — Wake cutover + legacy delete | done | `0139c6c`, completed by `e314210`, seam fixed in `870afdd` |
| 8 — batch close-out | Step 0 superseded; rest open | — |

Task 1b was inserted after Task 1 reported `OathRules.scala` at exactly the
800-line cap, which the next added line would have broken.

**A correction to Task 3's text below.** Step 2b records moving the eligibility
window onto `WalkerActionRegistry.Entry`, and it did. A later commit on this
same branch, `372397c` (`refactor(recover): centralize supply validation`),
removed the relaxation concept entirely — there is no `eligibilityWindow` field
and no `eligibilityRelaxed` anywhere in `src/main` today. The box stays ticked
because the work happened; the field it describes no longer exists, and Tasks 6
and 7 should not go looking for it.

**Why the branch has thirty commits for four tasks.** After Task 4 the batch
paused and `docs/superpowers/plans/2026-09-11-declarative-walker-decisions.md`
ran to completion on this same branch. That plan replaced every per-action
`validate` closure on a `Decide` with a declared `DecisionQuery`, which changed
the contract Tasks 5 through 7 must write against — so it ran before them
deliberately, not as a detour. Task 2's prose and `ForgeProcedure` were both
rewritten by it, which is why that task's text describes a declarative shape its
original implementation did not have.

Gate after Task 5: 670 root tests, 148 frontend, architecture check over 196
production files, all green.

Caps before Task 6 starts, measured rather than assumed. Task 5 took the
extraction this note previously warned it would need, so
`gameplay/walker/ProcedureWalker.scala` now sits at 666 of 800 with its replay
half in `WalkerReplay.scala` (176). `gameplay/actions/Campaign.scala` is still
at exactly 800 and still out of this batch's scope. The files Tasks 6 and 7
actually touch have room: `OathRulesWalker.scala` 362, `OathRules.scala` 396,
`serialization/WalkerEventCodec.scala` 752.

---

### Task 1: The modifier-selection window becomes per-action

> **Done** at `f9fa243`. The registry entry owns the window; `modifierWindow` is `Option` because not every action has one. Proven by mutation: restoring the hardcoded window fails the two window-dependent tests and leaves the `None` case passing.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerActionRegistry.scala`, `src/main/scala/oathdigital/gameplay/OathRules.scala`, `src/main/scala/oathdigital/application/GameApplicationService.scala`
- Test: `src/test/scala/oathdigital/gameplay/walker/WalkerActionRegistrySuite.scala`

`OathRules.offerableWalkerPowers` and `validateModifiers` both name `PowerWindow.RecoverModifierSelection` as a literal. With one registered action that reads as harmless; the moment a second action registers, every action's player-selected powers are filtered through Recover's window and a Forge modifier is either wrongly offered or wrongly rejected. This is a known open item and it blocks every later task, so it lands first, alone, while Recover is still the only action and the change is provably behaviour-preserving.

- `WalkerActionRegistry.Entry` gains `modifierWindow: Option[PowerWindow]`. `ActionRef.Recover`'s entry declares `Some(PowerWindow.RecoverModifierSelection)`, so today's behaviour is unchanged by construction. It is optional because not every action has such a window: `PowerWindow` has a `*ModifierSelection` case for each of the eight `MajorActionType`s, and Take Wealth is not one of them — its only window is `WakeTakeWealth`, an `OtherWindow` with no associated major action. `None` means the action offers no player-selected powers, so `offerableWalkerPowers` returns empty and `validateModifiers` rejects any id. Do not invent a `WakeModifierSelection` window to avoid the option; a window that exists only to satisfy a signature is a window a reviewer cannot audit.
- `WalkerActionRegistry.modifierWindow(action): Either[OathViolation, Option[PowerWindow]]`, alongside the existing `fallbackKind` / `rollDecisionId` accessors.
- `OathRules.offerableWalkerPowers(ready, actor, action)` and `validateModifiers(ready, actor, action, modifiers)` take the `ActionRef` and read the window from the registry. Neither names a window literal.
- `GameApplicationService.preview` already resolves the `ActionRef` at `walkerAction(action)` and discards it as `case Some(_)`. Bind it and pass it through.

- [x] **Step 1: failing tests** in `WalkerActionRegistrySuite`: (a) register a second `Entry` in a test-local `registrations` map declaring a *different* modifier window, and assert `offerableWalkerPowers` returns a different set for it than for Recover — a fixture `PlayerSelected` power applicable only at the second window is offered for the second action and not for Recover; (b) an entry declaring `None` offers nothing and rejects every modifier id. Expected FAIL: the accessor does not exist and both calls consult Recover's window.
- [x] **Step 2: implement** the four bullets above. No behaviour change for Recover.
- [x] **Step 3:** re-run the focused suite; expected PASS. Confirm the existing Recover modifier tests still pass untouched.
- [x] **Step 4:** `./sbtw "test"`, `./sbtw "frontend/test" "frontend/fastLinkJS"`, `python3 scripts/check-architecture.py`, `git diff --check`.
- [x] **Step 5: commit** `refactor(walker): let each registered action own its modifier window`.

---

### Task 1b: Extract the walker command surface out of `OathRules`

> **Done** at `9f0f16c`. Verified a pure move byte-for-byte — the extracted block compares identically against the new file but for the trait's closing brace. `OathRules.scala` 800 → 466, `OathRulesWalker.scala` 374. No test file was edited, which was the acceptance criterion.

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala`

Inserted after Task 1 reported that `OathRules.scala` now sits at exactly 800 lines — the cap `scripts/check-architecture.py` enforces, which fails at 801. Task 1 reached it by tightening doc comments, which is not a strategy that survives another round. Tasks 3, 5 and 7 all modify this file, and Task 5 in particular must thread a travel destination through `startWalker`. The next line added to `OathRules` fails the build, and the global constraints forbid raising the cap.

Doing this now, alone, keeps it a reviewable pure move. Folded into Task 3 instead, the Forge cutover commit would carry a 300-line file relocation alongside its real change, and neither would get read properly.

The walker command surface is already contiguous: `startWalker` at line 131 through `parkedContinue` ending around line 456 — `startWalker`, `walkerPowers`, `offerableWalkerPowers`, `validateModifiers`, `checkRestrictions`, `eligibilityGathered`, `resolveWalker`, `rollWalkerPrepared`, `resumeWalker`, `walkerResumeContext`, `buildWalker`, `walkerCall`, `walkerTransition`, `foldEvents`, `parkedContinue`. That block is the extraction. Everything above it is legacy per-action `handle` methods and everything below is turn/phase plumbing.

The mechanism is the implementer's call — a trait `OathRulesWalker` that `OathRules` mixes in is the cheapest thing that keeps `catalog` and `walkerPowerCatalog` reachable and every call site unchanged, but a collaborator class taking those two as constructor arguments is equally acceptable if it reads better. What is not acceptable is changing what any of these methods does.

- [x] **Step 1:** move the block. No signature changes, no behaviour changes, no doc-comment rewrites beyond what the move mechanically requires. Public methods stay public; `validateModifiers` keeps the `private[gameplay]` visibility Task 1 gave it.
- [x] **Step 2:** the proof of a pure move is that **not one test file is edited**. Run `./sbtw "test"` and confirm 587 passing with `git status` showing no change under `src/test/`. If a test needs editing, the move was not pure — stop and report what forced it.
- [x] **Step 3:** `python3 scripts/check-architecture.py`, and record both files' line counts in the report. `OathRules.scala` should land far enough below 800 that Tasks 3, 5 and 7 have room; if it does not, say so, because that means the extraction was too small to solve the problem it exists for.
- [x] **Step 4:** `./sbtw "frontend/test" "frontend/fastLinkJS"` and `git diff --check`.
- [x] **Step 5: commit** `refactor(walker): extract the walker command surface from OathRules`.

---

### Task 2: Forge declares its tree

> **Done** at `d46f511`, later migrated to the declarative contract by `9e078cc`. **This task answered the batch's central question: the recipe generalises.** No engine change, no new `Operation` case, no new window. The prose above was rewritten afterwards to describe the declarative shape Forge now has.

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

- [x] **Step 1: failing tests** in `ForgeProcedureSuite`: (a) `build` rejects every semantic start gate, including not ruling the pawn site and insufficient aggregate player favor/secrets; (b) zero Supply passes `build` but the first `AdjustSupply` is rejected before any park; (c) an empty relic deck passes `build`, the complete action still pays Supply/resources, and no `Play` is emitted; (d) a successful build has the tree above; (e) the partition query exposes exactly the three live denizens and printed minima; (f) stale/malformed partition answers reject generically; (g) a legal answer produces exactly three player-funded `PayCost`s plus an optional top-relic `Play`.
- [x] **Step 2: implement** `ForgeProcedure` against the declarative decision contract, with aggregate affordability at the start gate and authoritative operation validation at execution.
- [x] **Step 3:** re-run the focused suite; expected PASS.
- [x] **Step 4:** `./sbtw "test"` and `python3 scripts/check-architecture.py`.
- [x] **Step 5: commit** `feat(walker): declare the Forge procedure tree`.

---

### Task 3: Forge cuts over and its legacy path is deleted

> **Done** at `4f209b3` → `b0640e8` → `b49ff11`, in the three-commit split R23 allowed. Step 2b (the eligibility window) and Step 2c (the codec branch) both landed here. Two accepted deviations: `OathContinue.AwaitingForgeAssignment` survives because the registry entry's own `continuationFor` returns it, exactly as Recover's continuations survived its cutover; and the per-command decision id became a constant, which is the walker's established model rather than a Forge regression.

**Files:**
- Modify: `src/main/scala/oathdigital/model/ActionRef.scala`, `WalkerActionRegistry.scala`, `OathRulesWalker.scala`, `OathRules.scala`, `WalkerDecisionProjector.scala`, `GameIntentMapper.scala`, `LegalActionProjector.scala`, `PendingProcedureProjector.scala`, `GameApplicationService.scala`, `frontend/.../ActionDecisionRenderer.scala`

Note (from Task 1b): the walker command surface now lives in `OathRulesWalker.scala`, not `OathRules.scala` — `startWalker`, `buildWalker` and `rollDecisionId`'s call site are all there. `OathRules.scala` keeps `declaredWalkerTree` and `eligibilityRelaxed`.
- Delete: `src/main/scala/oathdigital/gameplay/actions/Forge.scala`'s `Forge` object (keeping `ForgeRules`, which `ForgeProcedure` consumes), the `ForgeStarted`/`ForgeCompleted` event cases, their `ActionEventCodec` branches and `GameEventWire` types, `PendingProcedure.Forge`, `OathContinue.AwaitingForgeAssignment`, and `ForgeCommand`.

`ActionRef` gains `Forge` and `ActionRef.all` gains it in the same edit — `WalkerActionRegistrySuite` asserts the registry covers `ActionRef.all`, so a missing entry fails loudly rather than at runtime. The entry declares `fallbackKind = MajorActionKind.Forge`, `modifierWindow = Some(PowerWindow.ForgeModifierSelection)`, no `rollDecisionId` park (Forge has no `Roll` node — see the note below), and `continuationFor` mapping `"forge.assignment"` to its client-facing continuation.

`Entry.rollDecisionId` is currently a bare `String` because Recover has a roll. Forge does not. Make it `Option[String]` rather than inventing an unreachable sentinel id: `OathRules.parkedContinue` and `WalkerDecisionProjector` both consult it, and a `None` there must produce a typed rejection, not a silent match against a string no tree ever uses.

- [x] **Step 1: failing test** — an end-to-end Forge through `GameApplicationService` using only `StartWalker`/`ResolveWalker`, asserting the same final state the legacy `ForgeCommand` path produced (three denizens each carrying their resource, relic facedown in the play area, 1 supply spent, no pending). Plus a replay assertion: reconstructing from the journal reproduces that state. Expected FAIL: `ActionRef.Forge` does not exist.
- [x] **Step 2: implement** the registry entry, the `Option[String]` roll-id change with its two call sites, and the projector/mapper wiring.
- [x] **Step 2b: the eligibility window becomes per-action too.** `OathRules.eligibilityRelaxed` names `PowerWindow.RecoverActionEligibility` as a literal — the same shape Task 1 removed from the modifier window, one window over, found by Task 1b. It is reached by every `startWalker`, so registering Forge makes a second action gather at Recover's eligibility window. It is inert only while no power is applicable there, and Task 4 registers real powers. Move the window onto `WalkerActionRegistry.Entry` beside `modifierWindow`, with the same `Option` treatment and the same `registrations` override, and update the projector call site. Prove it the way Task 1 was proven: a fixture power applicable only at another action's eligibility window relaxes for that action and not for Recover, and a mutation restoring the literal fails that test.
- [x] **Step 2c: the walker event codec has no branch for the Forge assignment payload** (found by Task 2). `WalkerEventCodec.encodeDecisionPayload` matches the two Recover payloads and then *throws* `IllegalArgumentException`; the decode side returns a typed `Left`. So the first journalled Forge throws, and no test in Task 2 could catch it because nothing there reaches the codec. Add both branches. Note the file is at 771 of the 800-line cap, so budget for that the way Task 1b had to. Two further things while you are in there, both judgement calls to make explicitly rather than by default: whether the encode side should return a typed error like its decode counterpart instead of throwing, and whether an exhaustive match would have made this a compile error rather than a runtime one.
- [x] **Step 3: delete** the legacy path in the same commit, and delete or port each legacy Forge test to the walker path. A test asserting a deleted event's codec round-trip is deleted with the event. `OathViolation.ForgeDecisionMismatch` loses its only producer with the legacy path — the walker checks actor, phase and decision id generically — so it goes too.
- [x] **Step 4:** re-run; expected PASS. `grep -rn "ForgeCommand\|ForgeStarted\|ForgeCompleted\|PendingProcedure.Forge" src frontend` returns nothing outside the journal fixtures being deleted.
- [x] **Step 5:** `./sbtw "test"`, `./sbtw "frontend/test" "frontend/fastLinkJS"`, `python3 scripts/check-architecture.py`.
- [x] **Step 6: commit** `feat(walker): move Forge onto the walker and delete its legacy path`.

**Report before Task 4.** Forge is the recipe proof. If porting it needed anything the plan did not anticipate — an engine change, a new `Operation` case, a window that did not exist — say so plainly in the ledger before Travel starts, because Travel and Wake were scoped assuming the recipe holds.

---

### Task 4: Travel's terrain costs become transforms

> **Done** at `d8d6853`, hardened over two review rounds to `2e4a0a6`. **CR1 superseded R29 in one respect:** `PowerCtx` now carries the generic hooked `operation`, so a power can inspect the route without the collector or walker knowing Travel. The prose above already reflects that, and R28 resolved to a single mechanism — Coast declares no priority and `shouldIgnore` does the work.

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

- [x] **Step 1: failing tests** in `TravelSitePowersSuite` driving `ContributionCollector.gather` directly, one per parity case the retired `TravelCostWindow.fold` implements: ordinary route to a mountain (+1), to an island (+2), coast route (replaced with 1), coast route where the destination also has an add (the add is ignored, not stacked), non-coast route from a coastal site (no replace), and a pass crossing regions (blocked) versus a coast route past a pass (allowed). Expected FAIL: the powers do not exist.
- [x] **Step 2: implement** the powers and register them in `WalkerPowerCatalog.default`.
- [x] **Step 3:** re-run; expected PASS. Confirm each case's number matches what `TravelCostWindow.fold` returns for the same route today — parity is the acceptance criterion, not plausibility.
- [x] **Step 3b: prove each transform actually fires.** A transform whose pattern match hits nothing returns its input unchanged and is indistinguishable from an absent power in every assertion about a *route*, because the base cost still comes back. So for each terrain kind, assert the cost differs from the base cost with that power absent, and additionally mutate one transform's match so it hits nothing and quote the resulting failure. This is the vacuity trap this task is most likely to fall into, and a green suite is not evidence against it.
- [x] **Step 4:** `./sbtw "test"` and `python3 scripts/check-architecture.py`. The architecture suite must still find no engine source naming any of these powers.
- [x] **Step 5: commit** `feat(powers): state Travel terrain costs as walker contributions`.

---

### Task 5: Travel cuts over, and the typed-cost vocabulary is deleted

> **Done** at `c70ec1a`. The typed-cost vocabulary is retired and Step 4's grep is clean. **Review changed one thing in the design below:** "a typed Travel start argument" resolved to a vector of `DecisionOptionRef`, not a per-action payload type — a sealed family enumerating actions would have put per-action knowledge in the model and the journal codec, which the global constraints forbid. The prose below still says "typed start argument"; read it as the registry entry's builders receiving the player's start *selection*. See **What Task 5 settled** after the steps.

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

The base cost is the printed region-to-region table from `TravelRules.cost`, with the `TravelCostWindow.fold` call removed — terrain is now Task 4's transforms folding over this node. The destination rides the `StartWalker` command, so the registry entry's fresh-start builder receives a typed Travel start argument; Recover and Forge need no start argument. Resume needs none because Travel's flat tree cannot park. The walker dispatches opaque registered start arguments and does not gain destination-specific logic.

**Destination selection remains outside the tree.** Travel does not gain a
`Decide`: choosing a destination is the parameter that selects a complete,
atomic Travel tree, not a persisted interruption inside that tree. This avoids
pending Travel state, cancel semantics, and a route-less eligibility window.

Projection obtains authoritative costs by simulating one candidate tree per
in-play destination other than the actor's source. For each destination it:

1. runs the same semantic build used by `StartWalker` (Act actor, pawn source,
   distinct in-play destination, and region-derived printed base cost);
2. selects the same automatic and player-selected `WalkerPowers` as command
   execution;
3. evaluates `ProcedureWalker.restrictionViolations` on the complete route;
4. calls `ProcedureWalker.advance` against immutable state without persisting
   its returned events;
5. accepts only a `Finished` outcome and reads the final negative
   `AdjustSupply` from its recorded transformed operations.

That shared candidate evaluator returns `(destination, finalSupplyCost)` and is
used by both the initial legal-action projection (automatic powers) and the
major-action preview after its selected modifiers have been validated. The
preview response, not a second `TravelRules.legalDestinations` calculation,
supplies the target costs the frontend shows. `StartWalker` rebuilds and walks
the same candidate with the chosen destination and modifiers. A restricted,
malformed, or unaffordable candidate is omitted; a forged direct command is
rejected by the same path with no events appended.

Base Travel does not validate player role, Foundation faces, or Supply.
Role/Foundation state is unrelated to the printed action. Supply sufficiency
is owned by the transformed `AdjustSupply` and `OperationValidator`; because
the tree is flat, failure leaves neither pawn movement nor pending state.

Task 1b's finding, which shapes this: the resume path passes no extra data to `buildWalker`, so anything threaded into a `starting` build must be re-derivable from state on resume or persisted the way `walkerModifiers` already is. Travel's flat tree may never park, in which case this is moot — establish that first rather than building persistence for a resume that cannot happen.

Deleting `CostContribution` and `SuppressionRegistry` is the point of this task, not a bonus. If either still has a live reference after the port, the reconciliation is incomplete: say so rather than leaving both vocabularies alive.

- [x] **Step 1: failing tests** — (a) candidate simulation and `StartWalker` produce the same final cost for plain, Mountain, Island, Coast, and Coast-suppresses-add routes; (b) Narrow Pass removes the projected candidate and rejects a forged command; (c) zero Supply is not a semantic build gate but operation simulation omits the unaffordable destination and direct execution appends no event or pawn move; (d) role and Foundation changes do not gate Travel; (e) initial projection uses automatic powers and modifier preview uses the exact selected power vector; (f) end-to-end Travel remains one atomic `StartWalker`, with replay parity and no parked state.
- [x] **Step 2: implement** `TravelProcedure`, its registry start argument, and one shared candidate evaluator that dry-runs the declared tree for projection/preview without persisting events.
- [x] **Step 3: delete** the legacy path and the typed-cost vocabulary in the same commit; port or delete each legacy Travel test.
- [x] **Step 4:** `grep -rn "CostContribution\|SuppressionRegistry\|TravelCostWindow\|TravelCommand\|Traveled" src frontend` returns nothing.
- [x] **Step 5:** `./sbtw "test"`, `./sbtw "frontend/test" "frontend/fastLinkJS"`, `python3 scripts/check-architecture.py`.
- [x] **Step 6: commit** `feat(walker): move Travel onto the walker and retire the typed-cost vocabulary`.

#### What Task 5 settled (`c70ec1a`)

Task 8 collects these; they are recorded here while the detail is fresh.

**The cost reconciliation, which was the reason Travel is in this batch.**
`Transform` over a pay node states terrain honestly, and the whole typed-cost
vocabulary is gone rather than left coexisting: the per-power terrain kind, the
typed cost fact each kind owned, the window fold that summed them, the global
suppression registry that expressed the coast route's ignore, and the codec that
round-tripped the pass's typed violation through a reason string. Step 4's grep
is clean. Nothing needed a new `Operation` case or a window that did not exist.

**A start argument is `Vector[DecisionOptionRef]`, not a per-action type.** This
is the second `Entry` shape change of the batch, after `rollDecisionId` became
`Option`. The first attempt declared a sealed `WalkerStartArgs` with a
`TravelStart` case, and review rejected it: a sealed family enumerating actions
puts per-action knowledge in the model and the journal codec, which the global
constraints forbid, and every later argument-bearing action would have widened
both. Reusing the reference vocabulary a decision option already names means the
model, the codec and the walker all handle a vector of game-object references
and none of them names an action; the journal reuses `DecisionAnswerCodec`'s
existing spelling rather than adding a second. **The limit, for whoever hits it
first:** a selection that is not a game-object reference — a warband count, say,
which Campaign will want — has no spelling yet. Widen that vocabulary; do not
add a case per action, which is the shape `PendingProcedure` had.

An action that declares no start selection must *reject* one rather than ignore
it, and that rejection needs its own test. Mutating the guard to accept anything
left the whole suite green until `WalkerActionRegistrySuite` gained a case for
it: every other test passes an empty vector, so nothing noticed.

**Travel cannot park, and the destination is persisted anyway.** The plan asked
to establish the first before building for the second. Established: Travel's
declared tree has no `Decide` and no `Roll`, so it finishes inside the command
that starts it. Persisting the selection on `WalkerParked` was nonetheless the
ruling, so `build` and `rebuild` are the same function for Travel and the first
action that both parks and selects — Campaign, probably — is a registry entry
rather than an engine change. Nothing writes a non-empty selection to a
production journal yet; `GameEventWireSuite` round-trips a synthetic one.

**`PowerCtx` reached everything Task 4's powers needed.** The route came from
`ctx.operation` plus `ctx.state`, and the end-to-end port confirms it: no field
was added and no gap was recorded.

**Projection and preview stopped calculating cost a second way.** One evaluator
dry-runs the declared tree per destination through `WalkerSimulation`. The
projection costs with automatic powers, the preview re-costs with the exact
selected vector, and `MajorActionPreviewAccepted` gained a `targets` field to
carry it, because the projection a route builds beside the preview is costed
before the player has chosen anything. **Unmeasured:** this replaces arithmetic
with one tree build and walk per in-play destination on every projection read.

**Two costs worth carrying forward.** `ProcedureWalker` crossed the 800-line
bound and its replay half moved to `WalkerReplay` — the second extraction this
batch has needed, after Task 1b. And Task 4's parity table compared each route
against the legacy fold this task deletes, so those numbers are now literals:
weaker evidence than Task 4 had, and unavoidable once the oracle is gone. A
later batch that retires a legacy oracle should expect the same trade.

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

- [x] **Step 1: failing tests**: the restriction allows the first take at a site, blocks the second at the same site in the same turn with `PowerAlreadyUsed`, allows a take at a *different* site in the same turn, and allows the same site again after the turn advances. Drive them through `ContributionCollector.gather` plus the restriction, not through the full action.
- [x] **Step 2: implement** attempt 1, or stop and report. Attempt 1 held; nothing was escalated.
- [x] **Step 3:** re-run; expected PASS. Eight cases pass, and each of the two mutations below fails one.
- [x] **Step 4:** `./sbtw "test"` (678 passing) and `python3 scripts/check-architecture.py` (197 production files). `git diff --check` clean. Frontend is untouched, so its gate is not this task's.
- [x] **Step 5: commit** `feat(powers): state Take Wealth's once-per-turn limit as a restriction` (`654e75a`).

---

#### What Task 6 settled (`654e75a`)

**Attempt 1 held, so the spec's standing decision survives contact.** The limit
is a `Restriction` at `PowerWindow.WakeTakeWealth` reading
`TurnState.usedPowers` through the `ReadyGame` every contribution already sees.
No field joined `PowerCtx`, the walker learned no "power was used" concept, and
no engine file changed. Attempt 2's escalation was not reached, so the spec
needs no amendment on this point — Task 8 can record that as settled rather
than as a question.

**The site comes from the actor's pawn site, not from the tree.** Travel's
powers read their route out of `ctx.operation`, and the same shape was
available here — flatten the tree, collect the `Take` whose source is a site.
It was rejected because Task 7's declared tree builds its take at execution
inside `BuildOps`, and restrictions run at command entry, before any of that:
a tree-reading restriction would have matched nothing, returned no violation
and been indistinguishable from a correct one in every green assertion. That is
Task 4's vacuity trap with the sign flipped, and reading state closes it. The
cost is stated for whoever hits it: the rule now names the pawn's site rather
than the take's, so a future take-at-a-distance power would read the wrong one.

**One power object, not one per site.** Per-site scope is already in the ref:
`PowerUseRef` carries `PowerSourceRef.Site`, so a second take where the pawn
stands is blocked while a take at another site the same turn is not. Per-site
instances would buy the same behaviour for a fabricated `PowerId` per site —
`ContributionCollector` keys powers by id, so instances sharing one id would
resolve a restriction's context to another site's instance, and the per-site id
would then be what `usedPowers` records. The source is `RuleSourceRef.GameRule`:
taking wealth is a standing Wake-phase option, not a power printed on a site.

**The `PowerUseRef` spelling lives on the power, and Task 7's write side imports
it.** A read side and a write side that spelled the ref differently would leave
the limit permanently silent, and only an end-to-end test would notice. The
suite pins the spelling twice: against `Wake.takeWealthPower` while the legacy
object still exists, and against the literal. **Task 7 must delete the first
assertion with the legacy object and keep the second** — and build its
`usedPowers` write through `TakeWealthLimit.useRef`, never a second literal.

**Registered in `WalkerPowerCatalog.default` now, inert until Task 7.**
Discovery keeps only powers hooking the window being gathered, and nothing
declares `WakeTakeWealth` yet, so no current gather sees it. The catalog audit
fingerprint covers the executable catalog's handler inventory, not this vector,
so it is untouched. The power carries no catalog id at all, which is a first
for this seam: there is nothing to look up and nothing to omit.

**An architecture guard was misfiring and is now narrowed.** "Procedure power
inventories use named Power objects, not raw ID tables" rejects any
`*Powers.scala` under `powers/` containing `Map(` or `Set(`. Every
`ContributingPower` declares `contributions: Map[PowerWindow, Vector[
Contribution]]`, so the guard reads an ordinary field of the walker seam as the
smell it hunts. It was already being paid for silently: `TravelSitePowers.scala`
spells its contribution map `Map.empty.updated(...)` for no reason but this
scan. The guard now skips files declaring a `ContributingPower`, by what they
declare rather than by filename, and asserts it still covers at least five
files so the exemption cannot quietly empty it. Proven both ways: a `Map(` added
to `ActionPowers.scala` still fails it. **Available cleanup, deliberately not
taken here:** `TravelSitePowers.scala` can drop its workaround, which is Task 5's
file and not this task's to churn.

**Mutation evidence, since a restriction that matches nothing looks identical to
an absent one.** Making the used-set check unconditional (`filter(_ => false)`)
fails the same-site block and the turn-advance precondition. Replacing the pawn
site with a constant fails the different-site case. Both were run and reverted.

**Reachability, stated rather than assumed.** "A different site the same turn"
is a property of the rule, not a scenario Wake can reach today: nothing moves a
pawn during the Wake phase. The test fixes the rule's shape before an action
that moves one exists.

Sizes for Task 7's budget: the power is 54 lines, its suite 131. Gate after
Task 6: 678 root tests, architecture check over 197 production files. The
frontend is untouched by this task, so its gate is Task 7's to run.

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

~~`WakeCommand.EndWake` is not an action and does not move to the walker; it stays a phase transition. Keep it and say so, rather than porting it for symmetry.~~

**Struck during implementation.** This clause contradicted the delete list above, which asks for the `Wake` object — and once Take Wealth leaves, that object holds nothing but End Wake. Its classification was right and the conclusion drawn from it was wrong: ending Wake IS a phase transition, and it is declared as a walker tree anyway, because a phase change is a state write and the walker is where a procedure's state writes are declared, journalled and replayed. What follows from it not being an action is decided in the boundary read, not by keeping it out of the registry. See "Ending Wake is a phase transition AND a walker procedure" in the settled notes.

**Found by Task 5, and this task is where it bites.** `OathRulesWalker
.walkerResumeContext` hard-codes `Phase.Act` (`OathRulesWalker.scala:254`): every
resume rejects with `WrongPhase` outside Act. `startWalker` does not check phase
— each action's `build` does — so a Wake-phase action starts fine and only a
*resume* is blocked. Take Wealth's declared tree is two `BuildOps` with no
`Decide` and no `Roll`, exactly like Travel's, so it finishes inside the command
that starts it and never reaches that line. Establish that before changing it:
if the tree really cannot park, the gate is untouched and the finding is
recorded for whichever action parks outside Act first. If it can, the phase
belongs on the registry entry beside `modifierWindow`, by the same argument
Task 1 made — and that is a change to make deliberately, not to discover.

- [x] **Step 1: failing test** — end-to-end Take Wealth through `GameApplicationService` on `StartWalker`, asserting the token moved, the `PowerUseRef` recorded, and a second take at the same site rejected. Plus a take during the Act phase rejected. Plus replay parity.
- [x] **Step 2: implement** the procedure and the registry entry.
- [x] **Step 3: delete** the legacy path in the same commit; port or delete each legacy Wake test. End Wake followed in a second pass, which is what emptied and deleted `phases/Wake.scala`.
- [x] **Step 4:** `./sbtw "test"` (689), `./sbtw "frontend/test" "frontend/fastLinkJS"` (148), `python3 scripts/check-architecture.py` (196 production files), `git diff --check` clean.
- [x] **Step 5: commit** `feat(walker): move Take Wealth onto the walker and delete its legacy path` (`0139c6c`) and `feat(walker): move End Wake onto the walker and delete the Wake object` (`e314210`).

---

#### What Task 7 settled (`0139c6c`)

**The two Act-phase assumptions, both taken deliberately.**

1. `walkerResumeContext`'s `Phase.Act` gate is untouched, and the finding
   stands for whoever meets it next. Take Wealth's tree is two leaves with no
   `Decide` and no `Roll`, so it finishes inside the command that starts it;
   the suite pins that by asserting no walker pending and no walker action
   after a completed take. Nothing outside Act can reach a resume yet, so
   changing that line would have been an untestable behaviour change.
2. `walkerTransition`'s Finished branch no longer hardcodes
   `ActActionSelection` and no longer runs `completeAction` unconditionally.
   **The phase did not become registry data.** Review rejected that: the
   walker and its registry state what a procedure DOES, and which phase a
   player is in is neither's business. The continuation is read off the phase
   in `OathRulesWalker` — the command surface that already owns phase and
   continuation plumbing — so a procedure declares nothing and the walker
   learns nothing. A phase with no walker continuation is a typed rejection
   rather than a default, so the first procedure registered in Rest fails
   loudly instead of silently returning its player to Act.

   Ending Wake (below) then split that single phase read in two, because it
   is the one procedure whose tree changes the phase mid-walk. The phase it
   FINISHED in names the continuation — where the player now is. The phase it
   STARTED in decides the boundary — which phase's procedure just completed.
   The second read restates what the rest of the engine already does: every
   legacy `handle` in `OathRules` runs `completeAction` after an Act command
   (Economy, Search, Challenge, Campaign, Visions, Negotiation, minor
   actions) and none of the Wake or Rest ones ever did.

**Use limits needed a new generic operation, because replay applies recorded
operations and nothing else.** The legacy path wrote `usedPowers` in a state
callback beside its operation pipeline, which a walker action cannot do: a
limit written outside the recorded operations would be absent from a reloaded
game and the same site could be taken from twice. `RecordPowerUse(PowerUseRef)`
is that operation — generic, naming no power and no action, with its own
journal spelling. Adding a ref the turn already holds is a no-op, matching the
set it writes; whether a second use is legal at all is the restriction's
question, asked before the walk.

**The resource choice rides `DecisionOptionRef.Button`.** Take Wealth is the
first action whose start selection is not a game object, which is the limit
Task 5 recorded. It needed no widening after all: `Button` is already the
variant for a choice with no game object behind it, so the model, the wire and
the journal are unchanged and `TakeWealthProcedure` interprets the button the
way `TravelProcedure` interprets its site. A selection that genuinely has no
spelling — a warband count — is still waiting for the first action that needs
one.

**`ActionRef.TakeWealth` keys itself `take-wealth`, breaking the convention
that every action key is also a `MajorActionKind` key.** Wake is a phase that
also holds ending Wake, so there is no major-action kind this could honestly
share a key with; the entry points at `MajorActionKind.Wake` through
`fallbackKind` instead, which is what the Wake-timing fallback diagnostics
want. The consequence, stated rather than discovered: a preview asked for the
Wake kind keeps taking its existing path instead of being answered as this
action.

**Ending Wake is a phase transition AND a walker procedure, and `object Wake`
is gone.** The plan's file list asked for the object's deletion and its prose
asked for End Wake to stay a phase transition; the first draft of this task
kept the object to honour the second, which left a two-method legacy state
machine alive beside the walker for the sake of a classification. Review
rejected that. Both hold at once: ending Wake is declared as a tree because a
phase change is a state write and the walker is where a procedure's state
writes are declared, journalled and replayed — not because ending Wake was
reclassified as something a player spends a turn on. What follows from it NOT
being an action is decided where the difference is visible, in the boundary
read above, and not by a flag on its registry entry.

Its tree is one leaf, `Sequence(EnterPhase(Act))`, under no window: a window
would invite powers to transform a phase change, and the Wake timing's
fallback diagnostics already run through the entry's `fallbackKind`, which is
`MajorActionKind.Wake` — the same kind the deleted object's `withFallback`
wrapper used, so those diagnostics are recorded exactly as before.

**`EnterPhase(Phase)` is the second new operation, and it rejects a no-op.**
It takes any phase and states no phase order — that rule belongs to the
procedures that perform transitions, and an operation encoding it would state
the same rule twice in the vocabulary every future power can reach for. What
it does reject is entering the phase the turn is already in
(`OperationError.PhaseAlreadyEntered`). That is not a rule about order; it is
what keeps replay checking the phase sequence that the deleted `WakeEnded`
evolve used to check. Without it a doubled End Wake in the journal would
replay clean, because a walker step's replay applies its ops and re-runs no
gates.

**The client's spelling of ending Wake survived; the engine's did not.**
`GameIntent.EndWake` and `GameCommand.EndWake` are kept deliberately, against
the precedent Take Wealth set by deleting its intent outright: the transports,
the frontend's button and some forty test call sites say "end wake", and
`GameApplicationService` routes that to `startWalker(ActionRef.EndWake, …)` in
one line. The cost, stated rather than discovered: two spellings can start the
same procedure. It also kept `MajorActionPreviewCodec`'s borrowed carrier
intent valid, which the deletion would have broken.

**The tree carries plain operations, not the `BuildOps` pair the plan
sketched.** The site is the actor's pawn site and the resource is the start
selection, so nothing needed deferring to walk time. Task 6 chose to read the
site from state rather than from the tree partly because the tree was expected
to be opaque here; that choice is still right for its other reason — a
restriction that depends on a tree's shape is one tree edit away from matching
nothing — and the test comment now says so instead of citing `BuildOps`.

**The Wake projection consumes `TakeWealthProcedure.candidates`.**
`TakeWealthRules` was the projector's oracle as well as the command's, so
deleting it without replacing the seam would have left the offer and the
command as two rules again. The first pass replaced it in the wrong layer:
`LegalActionProjector` built the tree, spelled the start selection and ran
`WalkerSimulation` itself, which is gameplay assembly living in the
application layer and a second copy of the button spelling `resourceOf`
reads. The seam is now the one Travel already had — the procedure owns
`candidates`, dry-running its own tree per resource, and the projector only
names the results as controls. `LegalActionProjector` no longer imports
`WalkerSimulation` at all.

The simulation is what keeps a site already taken from this turn out of the
offer, since the once-per-turn limit is a `Restriction` rather than a build
gate; candidates that only built the tree fail both the candidates test and
the ported agreement test. The resource's wire spelling is now a `key` on
`WakeResource`, read by `selection` and `resourceOf` alike, so the string a
client sends and the string the command accepts are one definition.

**Deleted:** the whole of `phases/Wake.scala` — `object Wake` and the
`WakeCommand` vocabulary with it — plus `TakeWealthRules`,
`WakeOperationPolicy`, the `WealthTaken` and `WakeEnded` events with their
codec branches and `gameplay.take-wealth` / `gameplay.wake-ended` wire types,
`GameCommand.TakeWealth`, the `takeWealth` authorization helper,
`GameIntent.TakeWealth` with its codec and decoder, and
`RuleQueryContext.TakeWealth`, which lost its only producer. The Wake phase
now journals no event of its own: both of its commands are walker steps.
`WakeOperationPolicy` needed no replacement: a walker command carries no
operations, so there is no forged semantic root to reject — the tree is built
from the actor's own pawn site.

**Proven by mutation, ten ways.** From the Take Wealth half: treating a Wake
completion as an Act one fails three tests including the journal's; dropping
the use record from the tree fails five; costing the projection from `build`
alone instead of the simulation fails the agreement test's already-taken case;
and dropping the timing on the journalled ref fails the reload assertion,
which is what proves the new operation round-trips rather than merely
applying. From the End Wake half: reading the boundary off the phase the
procedure finished in fails the boundary test; letting `EnterPhase` accept the
phase the turn is already in fails the corrupt-index test; dropping End Wake's
start gate fails two; and ignoring the journalled phase key to decode `Act`
unconditionally fails the round-trip written for exactly that mutation — End
Wake is `EnterPhase`'s only caller, so without a test that journals a
different phase, a codec that dropped the value would pass the whole tree.
From the projection seam: candidates that build without simulating fail two,
and a drifted `WakeResource` key fails four across gameplay, the projection
and the application journal.

Gate: 691 root tests, 148 frontend, architecture check over 196 production
files. `WalkerEventCodec.scala` is at 786 of 800 after its three new branches
— the file to watch before the next batch adds an operation. The phase's wire
spelling is a `key` on `Phase` itself rather than a match in that codec, which
is both the `OathkeeperGoal` shape and what kept those branches to four
lines.

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

- [x] **Step 0: already done, by another plan.** The declarative-decisions plan's Task 3 (`9e078cc`) extracted `serialization/DecisionAnswerCodec.scala` and made its `encode` exhaustive over a sealed `DecisionAnswer`, so the throw this step existed to remove is gone and `WalkerEventCodec.scala` sits at 731 lines. Nothing remains here. As originally written: extract from `WalkerEventCodec.scala` until it has real
  headroom, as a pure move proven by no test file being edited — the same
  acceptance criterion Task 1b used. Then convert the encode side to a typed
  error, as its own commit.
- [ ] **Step 1:** write the spec updates above. Correct any spec text this batch falsified rather than appending a note beside it.
- [ ] **Step 2:** `grep -rn "PendingProcedure" src/main` — list which cases remain and which actions still own them, as the starting inventory for the next batch's plan.
- [ ] **Step 3:** full gate `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`, `python3 scripts/check-architecture.py`, `git diff --check`.
- [ ] **Step 4: commit** `docs(spec): record what walker batch 1 settled`.
