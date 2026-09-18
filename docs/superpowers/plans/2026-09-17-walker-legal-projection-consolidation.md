# Walker Legal-Action Projection Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compute Travel candidates once per active-player projection and reuse them in both Travel views without changing projected behavior.

**Architecture:** `TravelProcedure` retains candidate legality and cost simulation. `LegalActionProjector` retains viewer gating and DTO mapping, but reuses one immutable candidate vector inside each `project` call. No new module, adapter, or wire type.

**Tech Stack:** Scala 2.13, sbt, munit, Scala.js frontend.

**Spec:** `docs/superpowers/specs/2026-09-17-walker-legal-projection-consolidation-design.md`.

## Global Constraints

- Preserve projected controls, candidate IDs, order, Supply labels, viewer visibility, and error behavior exactly.
- Keep every walker procedure's candidate or start check in its gameplay module. Do not add a generic candidate registry.
- Keep legacy pending branches, shared protocol DTOs/codecs, `GameProjector`, and frontend unchanged.
- Do not add an injection seam solely to count calls in tests; this is a safe refactor, so bracket the edit with existing behavior tests and review the call sites.

## File map and interfaces

- `src/main/scala/oathdigital/application/LegalActionProjector.scala`: only production edit. `project(context, projectedPhasePowers): LegalProjection` remains unchanged; private `boardTargetActions` consumes the already-computed Travel candidates.
- `src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala`: strengthen the existing `Wake projection is actor-private and Act boundary is informational` test to compare the two Travel views and public omission. No production test seam is introduced.

---

### Task 1: Reuse Travel candidates within one projection

**Files:** Modify `src/main/scala/oathdigital/application/LegalActionProjector.scala:68-102,232-238`; test `src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala:1559-1600`.

**Interfaces:** `LegalActionProjector.project(context, projectedPhasePowers)` stays unchanged. Private `boardTargetActions` takes `travelFacts: Vector[(SiteId, Int)]` in addition to `context`; caller passes the same vector used for `LegalProjection.travel`.

- [ ] **Step 1: Capture baseline.** Run `./sbtw 'testOnly oathdigital.application.GameApplicationServiceSuite'`; require pass before edits. Record active/private and public Travel projection values from the existing Wake/Act test.

- [ ] **Step 2: Strengthen behavior proof.** In that test, after `val travel = ...`, compare ordered site IDs and Supply costs between `act.legalTravelDestinations` and `travel.candidates`; keep the existing public omission assertion. This characterization test should pass before the refactor because behavior is not changing.

```scala
assertEquals(
  travel.candidates.map(candidate => candidate.target -> candidate.details),
  act.legalTravelDestinations.map(destination =>
    BoardTargetRefProjection.Site(destination.siteId) ->
      Vector(s"${destination.supplyCost} Supply")))
```

- [ ] **Step 3: Run characterized baseline.** Run `./sbtw 'testOnly oathdigital.application.GameApplicationServiceSuite'`; require pass. If it fails, diagnose existing projection disagreement rather than changing expected values to hide it.

- [ ] **Step 4: Consolidate.** In `project`, compute `val travelFacts = if (ordinaryAct) travelCandidates(context) else Vector.empty[(SiteId, Int)]` once. Map `travelFacts` into `LegalProjection.travel`. Pass `travelFacts` to `boardTargetActions(context, travelFacts)` in its ordinary-Act branch. In `boardTargetActions`, map the argument to `BoardTargetCandidateProjection` instead of calling `travelCandidates(context)` again. Preserve candidate order and label construction.

```scala
val travelFacts = if (ordinaryAct) travelCandidates(context)
  else Vector.empty[(SiteId, Int)]
// In LegalProjection:
travelFacts.map { case (site, cost) =>
  LegalTravelDestinationProjection(site.value, cost)
}
// In the ordinary-Act branch:
boardTargetActions(context, travelFacts)
```

- [ ] **Step 5: Verify.** Run `./sbtw 'testOnly oathdigital.application.GameApplicationServiceSuite'`, then `./sbtw 'test' 'frontend/test' 'frontend/fastLinkJS'`, `python3 scripts/check-architecture.py`, `python3 scripts/check-markdown-links.py`, and `git diff --check`. Check `rg -n 'travelCandidates\(context\)' src/main/scala/oathdigital/application/LegalActionProjector.scala` shows one invocation. Check other walker-backed paths remain procedure-owned and no DTO/codec or frontend files changed.

- [ ] **Step 6: Commit.** Stage only the two task files; commit `refactor(projection): reuse Travel candidates within one view`. Leave the approved design and this plan as documentation, with their review status explicit before execution.

## Self-review gate

- [ ] Active Travel views use the same immutable candidate facts.
- [ ] Inactive, public, and pending viewers trigger no Travel simulation and see the same projections as before.
- [ ] Search, Take Wealth, Recover, Forge, Begin/Finish Rest, phase powers, facedown-adviser play, and parked decisions retain existing ownership and behavior.
- [ ] No new module, adapter, interface, protocol field, or control string appears.
