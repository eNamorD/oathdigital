# Test readability ledger

Date: 2026-10-02. Spec: [test readability sweep](2026-10-02-test-readability-design.md).
Status: survey in progress.

## Baseline

Measured on 2026-10-02 at commit f9abb76d with `.superpowers/readability/metrics.sh`.

- suites (root): 372
- suites (frontend): 43
- replayed setup files (execute()/initialReady): 7
- Table.start files: 157
- state patches (updateCurrent/copy(current =): 313
- @unchecked: 158
- numeric card ids: 84
- tuple destructures with _: 69
- Table.update escapes: 14
- test files over 800 lines: 4
- largest test file: 1829 src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala

## Summary

| Area | R1 | R2 | R3 | R4 | R5 | R6 | R7 | R8 | R9 | R10 | R11 | Keep | Total |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|

## Calibration

Calibration run: area 08 (model, catalog, serialization; 19 root suites and 6
shared-module suites) surveyed once with the first prompt, then every row
checked against the code on 2026-10-02.

**Area 08 rows.** 48 rows checked. 36 right, 6 right in substance but filed
under the wrong rule, 6 wrong. The wrong-row rate is 12 of 48, exactly a
quarter, so the "more than a quarter" rerun threshold is not crossed and area
08 is not rerun. Three marginal rows were kept as right (08-013, 08-033,
08-038); counting them wrong would cross the threshold.

Wrong rows, removed from the rows file (their IDs are left as gaps):

| Row | Filed as | Why it is wrong | Kind |
|---|---|---|---|
| 08-001 | R8 | The title and the named faces give the 12 and the 0 ((1 + 2) shields doubled twice, a Blank doubled). | R8 on an expectation the body explains |
| 08-007 | R1 | Not a board fact: the private vals sit directly above the test that asserts their wire form. | R1 outside its definition |
| 08-009 | R7 | The title names both assertions in the abstract (identities, required slots); no assertion falls outside it. | R7 on an accurate title |
| 08-016 | R1 | "The rest of the sample game is valid" is the premise of every validate test, not a board fact. | R1 outside its definition |
| 08-021 | R8 | The titles state 6 and 7; the argument order of refresh is not a rubric finding. | R8 on an expectation the title explains |
| 08-022 | R7 | The empty-dice result is still a Conquest, so "both kinds" is accurate. | R7 on an accurate title |

Right in substance, wrong rule, corrected in the rows file:

- 08-004, 08-011, 08-015, 08-017: Keep rows filed as R3. The prompt's own
  example says Rule Keep. Now Keep.
- 08-029, 08-044: repeated mechanics (stream boilerplate, an encode-decode
  round trip written 17 times) filed as R2. R2 is for an opaque helper, not
  for missing one. Now R4.

Count corrections in right rows: 08-006 (4 uses of `toOption.get`, not 5),
08-023 (3 uses at lines 32, 33 and 43, not 4), 08-024 (41 tests, not 38),
08-030 (64 uses, not 63), 08-029 (17 stream sites and 14 repeated steps).
Two cross-references fixed: 08-029 cited 08-035 for PayCostWireSuite (it is
08-034); 08-027 and 08-028 now use the names 00-141 and 00-142 propose
(`SetupResult(state, events)`, `setupThroughWake`). 08-020 duplicates 00-150
(TestGameFixtures); it is now R1 and says "count once", so the summary must
not count it twice.

Rows after correction: 42. Per rule: R7 14, R4 8, R1 6, R8 5, Keep 4, R2 1,
R3 1, R6 1, R10 1, R11 1, R5 0.

**Missing findings.** The seven files with no rows (CatalogHandlerInventorySuite,
ExecutableCatalogSuite, ActionKindSuite, ChronicleSuite, EnumWireKeySuite,
WorldModelSuite, VisionCardPresentationSuite) were all opened; each deserves
none. One borderline case was not added: WorldModelSuite line 28 titles
itself "support Campaign target derivation" while the body only asserts
`sameRuler` and `enemies`. The numeric ids in ExecutableCatalogSuite and
GameEventWireSuite fall under the wire and catalog exemption. No clear
misses.

**Examples added to the prompt** (one per kind of misjudgment; the plan's
Agent prompt and `.superpowers/readability/survey-prompt.md` agree):

- Keep, not R3 (the four mislabeled Keep rows).
- R4, not R2 (repeated mechanics).
- R6: `val (state, events) = execute()` (a tuple fixture the agent found but
  had no example for).
- R7 pass, and when R7 applies: an accurate title over named faces; the
  `usePower` title with an extra field in the body (08-001, 08-009, 08-022).
- R8 pass: `ExileSupply.refresh(9, 1)` under titles that give 6 and 7
  (08-021).
- R1 pass: "the rest of the game is valid" is not a board fact (08-007,
  08-016).
- One reporting line: every count comes from grep or a line count (four rows
  had a wrong count).

**Notes for Plan 2.**

1. The R4 accessor for the shared protocol suites (CommandProtocolSuite,
   ProjectionProtocolSuite and the others under `shared/src/test`) must live
   in `shared/src/test`, because those suites also compile for Scala.js.
2. The `execute()` helper and the unqualified wildcard imports from
   FirstGameSetupFixture (08-028) reach well beyond area 08. Measured:
   `execute` has six callers (GameEventWireSuite, MinorActionsSuite,
   ChallengeProcedureSuite, NegotiationProcedureSuite, WalkerReplayDriftSuite,
   EndWakeProcedureSuite); row 00-138 puts the fixture's `catalog` in 193 files (agent's count, not
   rechecked), so the wildcard import is wider than the helper.

**Shared fixtures (area 00).** 168 rows (Keep 51, R2 40, R3 16, R4 13, R1 11,
R5 9, R6 9, R9 9, R10 7, R8 3). Five rows spot-checked against the code with
`grep -rlw`; caller counts for generic names (`base`, `board`, `staged`,
`execute`) run high under a plain `git grep -w`, so each was recounted among
the files that name the owning fixture:

| Row | Check | Result |
|---|---|---|
| 00-062 PowerFixture.base | 67 callers; verdict R2 | Count right (67 files besides PowerFixture). `base` is `Table.start.ready`, so R2 holds. |
| 00-069 SearchFixture.staged | 28 callers; default supply 5 against the quiet 7 | Count right (28). Default `supply = 5` confirmed, stated in no caller; R1 holds. |
| 00-033 CampaignFixture.board | 49 callers; defaults differ from the quiet table | Count right (46 unqualified plus 3 qualified call sites). Corrected the Problem: only `warbands = 5` differs from the quiet 3; `supply = 7` equals the quiet 7. R1 holds. |
| 00-099 BannerFixture.withoutSiteSecrets | 2 callers; no-op on `base` | Right. Both callers pass PowerFixture.base, whose Table.start clears every site's tokens, so the helper does nothing and its doc is stale. R1 holds. |
| 00-141 FirstGameSetupFixture.execute | 9 callers; R6 tuple | Count wrong: 6 callers. Corrected. R6 verdict holds (returns `(OathState, Vector[OathEvent])`). |

Also confirmed: 00-139 (`orders` fixes the first player as p2 while
Table.start overrides it to p1) and 00-143 (3 callers of `initialReady`).
One verdict conflicts with the spec text: 00-040 files `againstPlayer` under
R2, while the spec's "Shared fixtures" paragraph names `againstPlayer` as a
helper that stays. The code supports the row (the helper sets 2 warbands at
the origin and hands the Oathkeeper title to the other player, so it hides
two facts), so the row is left as written. The spec example should be changed
or the row overturned before Plan 2 acts on it.

## Spot check

## Shared fixtures

| ID | File | Helper | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 01-service

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 02-game-log-and-testkit

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 03-engine

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 04-powers-action

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 05-powers-play-and-economy

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 06-powers-phases-and-travel

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 07-campaign-targeting

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 08-model-catalog-serialization

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 09-setup-server-persistence

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 10-procedures

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 11-frontend-a

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 12-frontend-b

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|
