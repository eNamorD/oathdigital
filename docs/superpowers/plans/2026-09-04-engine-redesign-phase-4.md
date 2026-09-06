> **OUTDATED — engine redesign superseded.** The forward architecture is the
> procedure-walker design (`docs/superpowers/specs/2026-09-05-procedure-walker-design.md`):
> actions become `Operation` trees, a generic walker executes them, powers are
> contributors (`Transform`/`Restriction`), replay applies recorded ops only.
> This file is a historical record of the pre-walker design/code. Read the new
> spec before planning new work.

# Phase 4 — Terrain Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Travel terrain cost + Narrow-Pass blocking off `RuntimeRuleRegistry`/typed-rule machinery onto real power bodies under a generic per-window runner; delete the travel path of `RuntimeRuleRegistry`/`RuleResolution`.

**Architecture:** Terrain site powers (coast/island/mountain/pass) become real `Power` objects declaring typed cost contributions + an ignore/suppression rule in a static window-keyed registry; a generic window runner (powers-owned) gathers applicable powers, applies suppressions, and folds contributions into cost. Narrow Pass becomes a restriction-holding power whose `OperationRestriction` is evaluated by Travel legality against a simulated pawn-Move op (code/detail strings; typed ids encoded in detail and decoded to `OathViolation.TravelPassBlocked` for the typed surface). `RuntimeRuleRegistry` travel handlers die; an empty stub stays for Negotiation's blocking boundary. Validator restriction-vector wiring stays Phase 5 (per decision), but restriction objects exist now on powers and Travel evaluates them.

**Tech Stack:** Scala 2.13, sbt multi-project (root engine + `frontend` Scala.js), munit. Full gate: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.

**Spec:** `docs/superpowers/plans/2026-09-04-engine-redesign.md` Decisions 11, 8, 5 + Phase-4 roadmap bullet; this plan + its addendum record the design Q&A below.

## Grilled Design Decisions (user-locked 2026)

1. Powers hold **real bodies** as typed facts + predicates; **no new `PowerHandler` execute method** (would ripple 50+ implementors). Generic logic runs at each `PowerWindow`, finds + executes relevant powers — **no per-procedure integration mirrors** (RecoverPowerIntegration/RestPowerIntegration stay as-is, not copied).
2. Restriction **data now, validator wiring Phase 5**: powers carry `OperationRestriction` objects; the pipeline restriction vector is NOT folded into `validateOne`/`validateBatch` this phase.
3. **Ignore is generic**: separate static suppression registry keyed by window (not terrain-specific). Coast ignores Island/Mountain/Pass when a coast route is active.
4. **Single pass violation**: reuse `OathViolation.TravelPassBlocked(passSiteId, destination)`; delete `TravelConsentUnsupported`. Block iff pass ruler != actor (passes are never unruled). Coast-route exemption flows through suppression.
5. Travel legality (handle + legalDestinations + evolve drift) **simulates the prospective pawn-Move op** and evaluates the pass `OperationRestriction`; pipeline also runs it on the real op (same object, two call sites). Travel is action-bound via wrapper, so Campaign Raid pawn moves never trigger it.
6. `TravelRules.cost` stays `Either[OathViolation,Int]`, **pure cost** (region base + terrain contributions, no pass gating); a NEW legality path carries restriction checks. Evolve drift recomputes **cost AND legality**.
7. **Multi-layer Operations (Operation → CoreOperation → primitives; restriction sees "Travel") split to its own later phase** — not in Phase 4. Program Decision 2 amended now to document it.
8. TakeWealthRules moves to a Wake-owned file. RuntimeRuleRegistry keeps an **empty stub** for Negotiation. Travel-only deletion of `RuleResolution` content (TravelModifierKind, Travel handlers, unused travel outcome cases) — shared vocabulary (RuleSourceRef/parse, RuleQueryContext.Campaign/TakeWealth/Negotiation, RuleActivation/RuleOutcome/RuleRegistry/TypedRuleHandler) stays for Campaign + Negotiation.
9. ReviewedPowerCatalog audited-fingerprint gate **untouched**; terrain powers swap to bundle powers with identical ids.
10. Wire/projection unchanged (`Traveled` untouched; blocked destinations still omitted by legality). Suite-level verification only (no live smoke).

## Global Constraints

- Engine semantics byte-identical where behavior exists: all TravelSuite cost numbers unchanged; region base-cost matrix stays in TravelRules (module-owned, like Challenge/Forge supply).
- No changes to catalog handler ids or `AuditedCatalogFingerprint`; no new `CoreOperation`/`PrimitiveOperation` cases; no `PowerHandler` trait change; no serialization format change (Traveled event identical).
- Delete-only hygiene: `grep -rn "RuntimeRuleRegistry|TravelModifierKind|TravelConsentUnsupported|ModifyCost|TravelModifierKind"` in src/main ends clean except intentional stub/remaining consumers (Negotiation, CampaignRules).
- Decision 2 text amended in program doc; ROADMAP + phase-4 plan doc record commits.
- Existing gates green before each commit: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` exit 0.

---

### Task 1: Decision 2 amendment + phase-4 plan doc commit

**Files:**
- Modify: `docs/superpowers/plans/2026-09-04-engine-redesign.md` (Decision 2 text: two executable layers kept; action-level composite Operations may wrap CoreOperations so restrictions see the action — executor still applies primitives only)
- Create: `docs/superpowers/plans/2026-09-04-engine-redesign-phase-4.md` (this plan)

- [ ] **Step 1: Edit Decision 2**

Append: "Action-level composite `Operation`s (e.g. Travel; later battle plans) may wrap several `CoreOperation`s so restrictions and validation see the action root rather than only its moves; the executor still applies primitives only." Mark `(Phase 4: documented; hierarchy lands in its own later phase)`.

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/plans/2026-09-04-engine-redesign.md
git commit -m "docs: amend decision 2 for action-level composite Operations"
```

### Task 2: Contribution vocabulary + suppression registry (powerresolver)

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powerresolver/PowerContributions.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/PowerModel.scala` (no interface change needed; Power untouched)

**Interfaces:**
- Consumes: existing `Power`, `PowerWindow`, `RuleSourceRef`, `PowerId`.
- Produces:
  - `sealed trait CostContribution { def window: PowerWindow }` with `final case class Add(window, amount: Int)` and `final case class Replace(window, amount: Int)`.
  - `object SuppressionRegistry { def register(window: PowerWindow)(dominant: PowerId, suppressed: Set[PowerId])(when: WindowContext => Boolean): Unit; def suppress(window: PowerWindow, active: Vector[PowerId], context: WindowContext): Set[PowerId] }` — static, empty by default; `register` require-unique per (window, dominant).
  - `trait WindowContext { def activePowers: Vector[PowerId] }`-style minimal marker (each window family extends with its own data).

- [ ] **Step 1: Write failing test** (in `PowerResolverSuite.scala` or new `SuppressionRegistrySuite.scala`): register a dominant suppressing two others with a condition; assert `suppress` returns suppressed ids when condition true, empty otherwise.
- [ ] **Step 2: Run to verify fail**: `./sbtw "testOnly oathdigital.gameplay.*Suppression*"` → FAIL (missing symbols).
- [ ] **Step 3: Implement** `PowerContributions.scala` per interfaces above.
- [ ] **Step 4: Pass + commit.**

### Task 3: Terrain powers become contribution-bearing bundle powers

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/TravelPowers.scala`
- Modify (helper home): `src/main/scala/oathdigital/gameplay/powers/PowerSupport.scala` (add a `TravelCostPower`-style private bundle base: id, modifier, `CostContribution`, optional restriction) — keep ≤20-line authoring.

**Interfaces:**
- Consumes: Task 2 `CostContribution`, `SuppressionRegistry`; existing `ReviewedPowerCatalog.powers` ordering.
- Produces: `TravelPowers.powers` unchanged list of 13 `Power` objects, now carrying contributions:
  - Coast powers (`site.*.coast`, 6): `Replace(TravelCost, 1)`; register suppression in `SuppressionRegistry` at TravelCost: dominant = the coast power id, suppressed = Island/Mountain/Pass power ids, `when` = context declares coast route (source-coast AND dest-coast-or-island both active).
  - Island powers (2): `Add(TravelCost, 2)`; Mountain powers (4): `Add(TravelCost, 1)`.
  - Narrow Pass (`site.narrow-pass.pass`): contribution none; declares the pass restriction object (Task 5 dependency — stub shape now, real body Task 5).

- [ ] **Step 1: failing test** in `TravelPowers`-adjacent suite: registry lookup by id returns each terrain power with expected contribution window/kind; SuppressionRegistry contains coast-dominant entries.
- [ ] **Step 2: fail → implement → pass → commit.**

### Task 4: Generic TravelCost window fold + legality

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/travel/TravelCostWindow.scala` (powers-owned window object; NOT an integration mirror)
- Modify: `src/main/scala/oathdigital/gameplay/actions/Travel.scala` (`TravelRules`)
- Modify: `src/main/scala/oathdigital/gameplay/actions/TravelOperationPolicy.scala` (only if needed for the simulated-op legality; likely untouched)

**Interfaces:**
- Consumes: Task 3 powers; `ReviewedPowerCatalog.resolver`; region base matrix (moved/kept in TravelRules).
- Produces:
  - `object TravelCostWindow { def fold(ready, player, source, destination, baseCost: Int): Either[OathViolation, Int] }` — gathers candidate site powers (source terrain + destination terrain; pass candidates only when crossing regions), asks `SuppressionRegistry.suppress(TravelCost, …)`, applies survivors in declared order (Replace overwrites, Add sums).
  - `object TravelLegality { def blocked(ready, player, source, destination): Option[OathViolation] }` — simulates pawn-Move op, evaluates Travel-bound restrictions (Narrow Pass), decodes reason detail → `TravelPassBlocked`.
- Keep `TravelRules.cost(catalog, ready, player, source, destination): Either[OathViolation, Int]` signature: region-matrix base + `TravelCostWindow.fold`; NO pass gating. New `TravelRules.legalDestinations` filters by cost AND legality. `handle` and `evolve` check cost then legality then supply.

- [ ] **Step 1: failing tests** in TravelSuite: same cost numbers as today (region matrix, coast override, island/mountain adds).
- [ ] **Step 2: fail → implement TravelCostWindow + rewire TravelRules.cost internals → pass.**

### Task 5: Narrow Pass restriction power body

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/TravelPowers.scala` (fill Narrow Pass body)

**Interfaces:**
- Consumes: `OperationRestriction` (from `operations/OperationValidator.scala`); typed `OathViolation.TravelPassBlocked(passSiteId, destination)`.
- Produces: Narrow Pass power exposes an `OperationRestriction` (may exceed 20 lines — user-approved): `reason(ready, operation)` matches a site-to-site pawn Move; when destination region contains the pass site (candidate selection is fold-owned), pass ruler != actor, destination != pass site, and pawn is not in the pass region → `Some(OperationReason("travel-pass-blocked", encodeIds(pass, destination)))`; detail encodes pass site + destination stably.
- Candidate pass-site selection stays in the window fold (module-authoritative); power decides ruler/allow logic only.
- Decoding helper maps encoded reason back to typed `TravelPassBlocked` for the typed surface (tests/UI), per Q81-A.

- [ ] **Step 1: failing TravelSuite tests**: existing pass assertions migrate: `TravelConsentUnsupported` → `TravelPassBlocked`; ruler==actor allows; bandit/enemy blocks; pass-as-destination allows; coast route ignores passes (TravelSuite lines 114–143 updated accordingly).
- [ ] **Step 2: fail → implement restriction body + TravelLegality wiring → pass.**
- [ ] **Step 3: commit** (with Task 4 if granularity favors).

### Task 6: Delete travel machinery from RuleResolution + move TakeWealthRules

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/RuleResolution.scala` — delete `RuntimeRuleRegistry` travel handlers (`default` → empty `RuleRegistry()` stub), `TravelModifierKind`, `travelModifierKind` member, `RuleQueryContext.Travel`, unused travel-only `RuleOutcome` cases (ModifyCost/RequireDecision/PostActionEffect only if no remaining consumers — verify by compile), `RuleDecisionBoundary` if orphaned.
- Create: `src/main/scala/oathdigital/gameplay/phases/TakeWealthRules.scala` (move `TakeWealthRules.validate` verbatim; usedPowers/PowerUseRef semantics untouched, decision 11).
- Modify: consumers imports (`Wake.scala`, `LegalActionProjector.scala`).
- Modify: `src/main/scala/oathdigital/gameplay/actions/Travel.scala` remove activations/`resolveTravel` machinery.
- Modify: `src/main/scala/oathdigital/gameplay/actions/Negotiation.scala` if its `RuntimeRuleRegistry.default.resolve` still compiles against empty stub (behavior unchanged: relevant handlers unregistered → unsupported block).

- [ ] **Step 1: compile-driven deletion** — `./sbtw "Test/compile"`; fix imports; keep RuleRegistry/RuleActivation/RuleOutcome/RuleQueryContext (Campaign/Negotiation/TakeWealth) shared.
- [ ] **Step 2: test updates**: `RuleResolutionSuite.scala` — delete travel-handler lookup assertions (lines 39–50 partial), keep generic-registry tests; `TravelSuite` unaffected beyond Task 5; add stub test asserting Negotiation still blocks identically.
- [ ] **Step 3: full gate + delete grep proof.**

### Task 7: Docs update + full verification (Phase 4 done)

**Files:**
- Modify: `docs/architecture/rule-resolution.md` (travel canonical example → powers/contribution fold; RuntimeRuleRegistry stub note; TakeWealth home)
- Modify: `docs/architecture/gameplay-modules.md` if RuleResolution content list changes
- Modify: `docs/ROADMAP.md` Phase-4 DONE entry w/ commit hashes + plan link
- Modify: phase-4 plan doc addendum (post-implementation record)

- [ ] **Step 1: full gate** `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` exit 0; `git diff --check` clean.
- [ ] **Step 2: grep proofs** (empty in src/main): `RuntimeRuleRegistry` (except stub + Negotiation), `TravelModifierKind`, `TravelConsentUnsupported`, `travelModifierKind`.
- [ ] **Step 3: commit docs.**

## Out of Scope (explicit)

- Multi-layer `Operation` hierarchy (restriction sees Travel, not Move) — own later phase, planning appendix here.
- Phase-5 validator restriction-vector wiring (restrictions exist as data on powers now; TravelLegality evaluates them).
- Campaign Targeting pass wiring (restriction predicate carries action tag for later; CampaignRules.passAllowsTarget untouched).
- Negotiation power migration (stub only). MVP power authoring (Phase 8).

## Acceptance Criteria

- TravelSuite cost matrix numbers identical; pass tests pass with single `TravelPassBlocked`; coast-route ignores passes/island/mountain.
- `RuntimeRuleRegistry` travel path gone; Negotiation blocking behavior identical (suite green); TakeWealthRules relocated, usedPowers semantics unchanged.
- No `PowerHandler` change; audited catalog fingerprint unchanged; `Traveled` event + wire unchanged.
- Full gate green; grep proofs clean; docs (program Decision 2, ROADMAP, architecture) committed.

## Explicit Assumptions

- Region base-cost matrix is module-owned in TravelRules (not a power), consistent with Challenge/Forge/Campaign supply precedent.
- SuppressionRegistry is static and empty-default; powers register at load; inert until registered.
- Coast "replace cost to 1" applies on coast route regardless of regions; Island +2 and Mountain +1 apply only to destination-site terrain (parity with current activations).
- The empty RuntimeRuleRegistry stub keeps Negotiation blocking identical because relevant negotiation handler ids were never registered.

## Addendum (post-execution)

- Shipped commits: `c38e779` (decision-2 amendment + plan doc), `83e5f2c` (typed `CostContribution` + `SuppressionRegistry`), `33fea9b` (terrain powers carry typed cost facts; `TravelCostWindow.fold` + Narrow Pass restriction legality + TravelRules rewired to pure-cost + separate legality), `9a39b71` (delete travel machinery from `RuleResolution.scala`; `RuntimeRuleRegistry` = empty stub for Negotiation; `TakeWealthRules` moved to `gameplay/phases/TakeWealthRules.scala`), `3c3e66f` (docs).
- **Executed design (post-grilling):** powers hold typed terrain kind + typed `CostContribution` facts (sub-trait accessor `TravelCostTerrainPower`, no new `PowerHandler` method). The powers-owned `TravelCostWindow.fold` computes route topology from the typed kinds, consults the generic `SuppressionRegistry` (each coast power registers "on a coast route I ignore Island/Mountain/Pass"), then folds surviving contributions over the module-provided region base. `TravelRules.cost` is pure cost (no pass gating); a separate `TravelLegality`/`TravelCostLegality` evaluates Narrow Pass power restrictions against a simulated pawn-Move op and decodes the encoded reason detail back into `OathViolation.TravelPassBlocked`. Single pass violation per decision: `TravelConsentUnsupported` deleted; pass blocks iff ruler != mover.
- **Deletion scope executed:** `RuntimeRuleRegistry` travel handlers, `TravelModifierKind`/`travelModifierKind`, `RuleQueryContext.Travel`, and the travel-only `RuleOutcome` forms (`ModifyCost`/`RequireDecision`/`PostActionEffect`, `RuleDecisionBoundary`) are gone. Shared vocabulary (`RuleSourceRef`/parse, `RuleActivation`/`RuleOutcome`/`ResolvedRule`/`RuleRegistry`/`TypedRuleHandler`, `RuleQueryContext.Campaign`/`TakeWealth`/`Negotiation`, `CampaignTimingWindow`) stays for Campaign + Negotiation. Validator restriction-vector wiring remains Phase 5, per decision.
- **Parity evidence:** full gate `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` exit 0 (root 464, frontend 114); `git diff --check` clean. TravelSuite region-matrix numbers identical (base 1/2/3/4 matrix, Coast replaces to 1, Island +2/Mountain +1 at destination); pass tests migrated from `cost(...).left` to the handle/legality surface with the single `TravelPassBlocked` code; cost itself is assertably pass-free (`cost(...).left.toOption == None` on a blocked destination).
- **Out of scope (recorded for later):** multi-layer `Operation` hierarchy (restriction sees `Travel`, not a raw `Move`) — its own later phase, captured in the Decision-2 amendment; Campaign Targeting pass wiring; Negotiation power migration (stub only). `TravelCostWindowContext` remains source/dest-power aware so future action-tagged restrictions and validator folding can reuse the same power-declared predicates.
- **Code-review fixes (post-merge prep):** (1) DRY — each `TravelTerrainKind` now owns its single canonical `CostContribution` (`TravelCostWindow.scala`), and `TravelCostTerrainPower.contribution` derives from the kind, so a site power cannot mis-author its effect; per-power declarations shrink to `TerrainPower(id, kind)`. (2) `SuppressionRegistry` is documented honestly as a *registered ignore seam*: coast powers register "on a coast route I ignore Island/Mountain/Pass", real-id wiring tests in `TravelPowerFactsSuite` exercise it, and the current one-terrain-power-per-site fold already implements the ignore structurally in its coast-route branch (the registry query is not the deciding mechanism today). (3) `register` is now idempotent for identical (window, dominant, suppressed) and rejects conflicting re-registration; the entry map is an immutable snapshot (`@volatile var`), removing the mutable-global race. (4) New tests: kind↔contribution fact check, real-id suppression wiring, and `TravelPassBlockedCodec` round trip.
