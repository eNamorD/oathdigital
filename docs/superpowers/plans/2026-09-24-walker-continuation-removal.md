# Walker Continuation Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Delete `OathContinue`, a seventeen-case model family that every command constructs and nothing reads, and replace the 218 test sites that assert it with assertions about the parked decision the walker actually holds.

**Architecture:** The fact "who is this game waiting on, and for which decision" already has two owners: `Decide.owner`/`Decide.owners` in the operation tree, and `ProcedureWalker.awaitedPlayer`/`parkedDecide`, which recompute it from the rebuilt tree on every command. `OathContinue` is a third copy, written at every park and completion and read nowhere. Production loses the type, the `continue` field on four carriers, and the registry lambda that built it; it keeps both of the gates that `parkedContinue` also happened to run. Tests gain one shared module, `ParkedDecisionAssertions`, that derives the same facts the walker derives, and the seven existing fixture helpers are re-implemented on top of it so their call sites do not move.

**Tech Stack:** Scala 3.9.0, sbt via `./sbtw`, munit. Architecture gates: `BackendArchitectureSuite` and `python3 scripts/check-architecture.py`.

**Spec:** `docs/superpowers/specs/2026-09-24-walker-continuation-removal-design.md`. Retention decision for the sibling write-only field: `docs/superpowers/specs/2026-09-24-delta-meaning-retention-decision.md`.

## Global Constraints

- Production Scala files stay at or below 800 lines (`BackendArchitectureSuite` "all production Scala files stay bounded"). This plan only shortens production files.
- Files under `src/main/.../gameplay/walker` and `src/main/.../gameplay/operations` must not contain any power's name as a lowercase substring (`BackendArchitectureSuite` "a walker power imports no engine, and the engine never learns its name"). The scan covers `src/main` only. `ParkedDecisionAssertions.scala` sits in the test tree and is therefore not scanned, but it is engine-adjacent: keep power names out of the module itself. Its SUITE may name a power where a test genuinely needs one.
- **No production behaviour changes.** No rejection code or detail string, no operation order, no walker event, no wire shape, no protocol DTO, no frontend edit. If a step appears to require one, stop and report it.
- **A failing strengthened assertion is a discovered defect, not a rewrite error.** Several negatives get stronger in Task 3 and Task 5. If one fails, record what it revealed, stop the task, and report it for a separate change. Do not weaken the assertion to make it pass.
- Commit messages: Conventional Commits, `refactor(walker): ...` unless noted. End every commit message with the trailer line `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>` — this plan's trailer overrides a worker's own model trailer.
- Run tests with `./sbtw "testOnly <fully.qualified.Suite>"`. The full gate is `./sbtw test`, `./sbtw frontend/test`, `python3 scripts/check-architecture.py`, `python3 scripts/check-markdown-links.py`.
- Line numbers in this plan are as of commit `f5c1508c` and drift as tasks run. Locate members by their signature and assertions by their text, not by line number.
- Tasks 2 through 6 each leave both assertion styles compiling. `OathContinue` is not deleted until Task 7, so every task before it ends on a green `./sbtw test`.

---

## Baseline

Before Task 1, record the baseline so the end state can be compared against it rather than against memory.

- [ ] **Step 0: Record the baseline**

Run and record the output of each:

```bash
./sbtw test
```

```bash
./sbtw frontend/test
```

```bash
python3 scripts/check-architecture.py
```

```bash
python3 scripts/check-markdown-links.py
```

```bash
grep -rn "OathContinue\|\.continue\b" src/test --include='*.scala' | grep -v "import \|DecisionOption.Button\|= Answered(\|DecisionAnswer\." | wc -l
```

Expected: the suites pass; the last command prints `218`. If it prints a different number the repository has moved since this plan was written — re-measure the affected task's file list before editing it.

---

## File structure

Created:

- `src/test/scala/oathdigital/gameplay/walker/ParkedDecisionAssertions.scala` — `ParkedDecisionFacts` and the assertion class.
- `src/test/scala/oathdigital/gameplay/walker/ParkedDecisionAssertionsSuite.scala` — the module's own tests.

Modified (production):

- `model/GameProcedureProtocol.scala` — `OathContinue` deleted; `OathTransition` loses `continue`.
- `gameplay/OathLifecycle.scala` — `GameplayTransition` loses `continue`.
- `gameplay/OathRulesWalker.scala` — `parkedContinue` and `continuationIn` replaced by `checkAnswerable`.
- `gameplay/OathRules.scala`, `gameplay/actions/MinorActions.scala`, `gameplay/phases/rest/TurnBoundary.scala` — construction and copy sites.
- `gameplay/walker/WalkerProcedureRegistry.scala` — `Entry.continuationFor`, its nineteen literals, and the accessor.
- `application/GameApplicationService.scala` — `GameAccepted.continue`, `PreparedGameBootstrap.continue`.
- `application/WalkerDecisionProjector.scala` — one stale scaladoc sentence.

Modified (tests): 53 files, listed per task.

Deleted: nothing but the members above. No test file is deleted.

---

### Task 1: The shared assertion module

**Files:**
- Create: `src/test/scala/oathdigital/gameplay/walker/ParkedDecisionAssertions.scala`
- Create: `src/test/scala/oathdigital/gameplay/walker/ParkedDecisionAssertionsSuite.scala`

**Interfaces:**
- Consumes: `CurrentGameState.walkerProcedure`/`walkerPending`/`walkerModifiers`/`walkerStartArgs`, `WalkerProcedureRegistry.rebuild`/`rollDecisionId`, `ProcedureWalker.parkedDecide`/`parkedRoll`/`awaitedPlayer`/`awaitedPlayers`, `WalkerPowers.selected`.
- Produces: `ParkedDecisionFacts`, and `parkedDecision`/`assertParked`/`assertNotParked`/`assertResumed` on `ParkedDecisionAssertions`.

- [ ] **Step 1: Write the module**

Create `ParkedDecisionAssertions.scala` in package `oathdigital.gameplay.walker`:

```scala
package oathdigital.gameplay.walker

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.PhasePowers
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** What a parked position holds, derived the way the walker derives it
  * rather than read off a value the rules stored. `coOwners` is the whole
  * answering set, including `awaiting`.
  */
final case class ParkedDecisionFacts(procedure: ProcedureRef, decision: String,
    awaiting: PlayerId, coOwners: Set[PlayerId])

/** The parked decision as a test may assert it.
  *
  * Constructed beside a suite's own `OathRules`, with the same catalog and
  * power catalogs: the tree a park sits in is only rebuildable against them,
  * and `awaitedPlayer` is only meaningful against the rebuilt tree. That is
  * why this is a class and not an object.
  *
  * The rebuild mirrors `OathRules.declaredWalkerTree` and the resume path in
  * `OathRulesWalker.walkerResumeContext`: the procedure, the start selection
  * and the selected modifiers all come from durable state, never from the
  * test's own memory of what it started.
  */
final class ParkedDecisionAssertions(
    catalog: ExecutableCatalog,
    walkerPowerCatalog: WalkerPowers = WalkerPowers.empty,
    phasePowerCatalog: PhasePowers = PhasePowers.empty)
    extends munit.Assertions:

  /** `None` when nothing is parked. */
  def parkedDecision(state: OathState): Option[ParkedDecisionFacts] =
    state match
      case Ready(ready) =>
        val current = ready.game.current
        for
          procedure <- current.walkerProcedure
          pending <- current.walkerPending
          tree <- WalkerProcedureRegistry.rebuild(procedure, catalog, ready,
            current.turn.activePlayer, current.walkerStartArgs,
            phasePowerCatalog).toOption
          powers = WalkerPowers.selected(walkerPowerCatalog,
            current.walkerModifiers)
          // A Roll park holds no Decide, so its id is the one the procedure
          // declares -- the same resolution the projector makes.
          decision <- ProcedureWalker.parkedDecide(ready, tree, pending, powers)
            .map(_.decisionId)
            .orElse(ProcedureWalker.parkedRoll(ready, tree, pending, powers)
              .flatMap(_ =>
                WalkerProcedureRegistry.rollDecisionId(procedure).toOption))
          awaiting <- ProcedureWalker.awaitedPlayer(ready, tree, pending, powers)
        yield ParkedDecisionFacts(procedure, decision, awaiting,
          ProcedureWalker.awaitedPlayers(ready, tree, pending, powers))
      case _ => None

  /** The game is parked on `decision` of `procedure`, awaiting `awaiting`.
    *
    * `awaiting` has no default on purpose: one parked decision awaiting one
    * named player is the invariant, and the identity half of it is otherwise
    * asserted nowhere.
    */
  def assertParked(state: OathState, procedure: ProcedureRef, decision: String,
      awaiting: PlayerId)(using munit.Location): Unit =
    assertEquals(parkedDecision(state).map(facts =>
      (facts.procedure, facts.decision, facts.awaiting)),
      Some((procedure, decision, awaiting)))

  /** Nothing is parked at all -- strictly stronger than "not parked on THIS
    * kind of decision", which is what the assertions this replaces said.
    */
  def assertNotParked(state: OathState)(using munit.Location): Unit =
    assertEquals(parkedDecision(state), None)

  /** Nothing is parked and the turn is where it should be: the whole content
    * of the three continuations that named a phase rather than a decision.
    */
  def assertResumed(state: OathState, phase: Phase, active: PlayerId)(
      using munit.Location): Unit =
    assertNotParked(state)
    val turn = state match
      case Ready(ready) => ready.game.current.turn
      case other => fail(s"expected a ready game, got $other")
    assertEquals((turn.phase, turn.activePlayer), (phase, active))

  /** Co-owners, for a decision more than one player may answer. */
  def assertCoOwners(state: OathState, owners: Set[PlayerId])(
      using munit.Location): Unit =
    assertEquals(parkedDecision(state).map(_.coOwners), Some(owners))
```

Check the import of `PhasePowers` and `WalkerPowers` against their real
packages before compiling; `WalkerPowers` and `WalkerProcedureRegistry.Entry`
are `private[gameplay]` at most, and this file is inside `oathdigital.gameplay
.walker`, so both are nameable here.

- [ ] **Step 2: Write the module's own suite**

Create `ParkedDecisionAssertionsSuite.scala`. This is the one place the module
is tested rather than used, so it must cover the three resolutions that carry
risk. Drive a real procedure through `OathRules` — do not construct a
`PendingTree` by hand.

Tests to write:

1. `"a Decide park reports its procedure, decision id and owner"` — start
   Recover from `FirstGameSetupFixture`, assert `parkedDecision` returns
   `ActionRef.Recover`, the decision id the walker parked on, and the active
   player.
2. `"a Roll park reports the procedure's declared roll decision id"` — the
   Recover roll park, asserting the id is `RecoverProcedure.rollDecisionId`.
   Without this the Roll branch could silently return `None` and every
   Recover assertion in the suite would weaken to nothing.
3. `"an off-turn owner is reported, not the active player"` — reuse the
   ownership setup from `OathRulesWalkerPowerSuite`'s "an off-turn decision is
   answered by its owner" test; assert `awaiting` is the owner.
4. `"a completed action is not parked"` — `assertNotParked` after a walker
   completes.
5. `"a state that is not Ready is not parked"` — `OathState.NoGame`.

- [ ] **Step 3: Run the new suite**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.ParkedDecisionAssertionsSuite"`
Expected: PASS, 5 tests.

- [ ] **Step 4: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/walker/ParkedDecisionAssertions.scala src/test/scala/oathdigital/gameplay/walker/ParkedDecisionAssertionsSuite.scala
git commit -m "test(walker): add ParkedDecisionAssertions, derived from the parked tree

The parked decision's procedure, id and owner are recomputed from the rebuilt
tree the way the walker recomputes them, so a suite can assert the park itself
rather than the continuation value the rules happen to carry beside it.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: The campaign family

The largest cluster, and the one with the most leverage: eight suites assert
only through `PlanDriver.awaits` and never name `OathContinue`. Re-implementing
the two `awaits` helpers converts them without touching a call site.

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala`
- Modify: `src/test/scala/oathdigital/gameplay/CampaignPlanWindowSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/CampaignRaidSuite.scala`
- Unchanged but re-run: `MercenariesSuite`, `GleamingArmorSuite`, `RampartSuite`, `StickyFireSuite`, `FearsomeShieldSuite`, `BattleHonorsSuite`, `WrestlersSuite`, `WarningSignalsSuite` (all in `gameplay/powers/campaign/`).

- [ ] **Step 1: Give `PlanDriver` a `ParkedDecisionAssertions`**

In `PlanDriver.scala`, beside the existing `rules`/`dice` members, add one
constructed with the same catalog and walker power catalog those use.

- [ ] **Step 2: Re-implement `PlanDriver.awaits` as a fact, not a value**

`awaits(who, id)` currently returns an `OathContinue` that call sites compare
against `run.continue`. Replace the pair with a predicate so the call sites'
shape is preserved:

```scala
  /** Whether the run is parked on `id`, awaiting `who`. */
  def awaits(run: Run, who: PlayerId, id: String): Boolean =
    parked.parkedDecision(run.state).exists(facts =>
      facts.decision == id && facts.awaiting == who)
```

Do not pin the procedure to `ActionRef.Campaign` here: Knights Errant runs a
Campaign inside a Muster, so a legitimate park in this family can sit under
`ActionRef.Muster`. The direct `assertParked` call sites still name the
procedure; this predicate asserts the decision and the owner.

Delete `def continue: OathContinue` from `Run`.

- [ ] **Step 3: Convert the `awaits` call sites in the eight power suites**

Each site reads `assertEquals(run.continue, awaits(b.actor, CampaignIds.x))`.
Rewrite as `assert(awaits(run, b.actor, CampaignIds.x))`. The three
`assertNotEquals` sites in `StickyFireSuite:72,73,78` become
`assert(!awaits(...))`.

The full list, by file:
`MercenariesSuite:35,92,103,134`; `GleamingArmorSuite:68,101,102,136`;
`RampartSuite:43,90`; `StickyFireSuite:42,72,73,78,129`;
`FearsomeShieldSuite:27,42,55`; `BattleHonorsSuite:79`;
`WrestlersSuite:30,57,63,70`; `WarningSignalsSuite:58,72,136,144,147`.

- [ ] **Step 4: Rewrite `PlanDriver.finish`**

`finish` destructures the continuation to decide what to answer next. Replace
the match on `OathContinue.AwaitingCampaignDecision(who, DecisionId(id))` with
one on the facts:

```scala
    def finish: Run = parked.parkedDecision(state) match
      case Some(facts) => facts.decision match
        case CampaignIds.attackerPlan | CampaignIds.defenderPlan =>
          answer(facts.awaiting, facts.decision,
            ChooseOneAnswer(CampaignIds.finish)).finish
        case CampaignIds.sacrifice | CampaignIds.placement =>
          answer(facts.awaiting, facts.decision, ChooseAmountAnswer(0)).finish
        case _ => this
      case None => this
```

Rewrite `commit`'s two `if run.continue == awaits(...)` guards
(`PlanDriver:105,109`) as `if awaits(run, b.actor, CampaignIds.kind)` and
`if awaits(kind, b.actor, CampaignIds.targets)`.

- [ ] **Step 5: Convert `CampaignPlanWindowSuite`**

It defines its own `awaits(who, id)` at `:91-92`. Replace it the same way as
`PlanDriver`'s, then rewrite its twelve call sites (`:109,158,175,186,212,233,
251,258,283,295,361,425,436`) as `assert(awaits(...))`.

- [ ] **Step 6: Convert `CampaignProcedureSuite`**

Twenty sites. Delete the suite's private `parkedDecision(b, transition)` helper
at `:33-38` — it hand-rolls the rebuild this module now owns — and point its
callers at the shared module.

The eighteen `assertEquals(x.continue, OathContinue.AwaitingCampaignDecision(
b.actor, DecisionId(CampaignIds.y)))` sites become
`parked.assertParked(x.state, ActionRef.Campaign, CampaignIds.y, b.actor)`.
Note `:267` awaits `b.other`, not `b.actor`.

The three `assertEquals(x.continue, OathContinue.ActActionSelection(b.actor))`
sites (`:340,372,397`) become
`parked.assertResumed(x.state, Phase.Act, b.actor)`.

- [ ] **Step 7: Convert `CampaignRaidSuite`**

`:50,109` become `assertParked`; `:78,90` become
`assertResumed(_, Phase.Act, b.actor)`.

- [ ] **Step 8: Run the campaign suites**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.CampaignPlanWindowSuite oathdigital.gameplay.CampaignRaidSuite oathdigital.gameplay.powers.campaign.*"`
Expected: PASS, with no change in test counts.

- [ ] **Step 9: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala src/test/scala/oathdigital/gameplay/CampaignPlanWindowSuite.scala src/test/scala/oathdigital/gameplay/CampaignRaidSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/
git commit -m "test(campaign): assert the parked decision, not the continuation value

PlanDriver's awaits becomes a predicate over the parked decision, so the eight
campaign power suites keep their call sites and gain an assertion about the
procedure and owner the walker actually holds.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: The power fixtures

Three fixtures carry boolean helpers used by fifteen suites. Re-implementing
the bodies converts every user; only the clue strings move.

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/powers/TargetsFixture.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/action/MovementFixture.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/banner/BannerFixture.scala`
- Modify (clue strings and negatives): `powers/wake/HornedMaskSuite.scala`, `powers/action/IvoryEyeSuite.scala`, `powers/action/CrystalVialSuite.scala`, `powers/action/SleightOfHandSuite.scala`, `powers/action/AlchemistSuite.scala`, `powers/action/WolvesSuite.scala`, `powers/banner/PeoplesFavorMobSuite.scala`
- Unchanged but re-run: `CoOwnedDecideSuite`, `CatacombsContributionSuite`, `powers/action/BrassHorseSuite`, `powers/action/MagicCarpetSuite`, `powers/action/WhistleSuite`, `powers/banner/WanderingFlameMoveSuite`, `powers/banner/WanderingFlamePlaceSuite`

- [ ] **Step 1: Re-implement the three fixtures' helpers**

`TargetsFixture.awaits(transition, decision)` (`:71-73`):

```scala
  def awaits(transition: OathTransition, decision: String): Boolean =
    parked.parkedDecision(transition.state).exists(facts =>
      facts.decision == decision && facts.awaiting == actor)
```

`MovementFixture.parkedAt` (`:81-82`) takes the same body.
`MovementFixture.backToActing` (`:85`) and `BannerFixture.backToActing` (`:74`)
become:

```scala
  def backToActing(transition: OathTransition): Boolean =
    val turn = readyOf(transition.state).game.current.turn
    parked.parkedDecision(transition.state).isEmpty &&
      turn.phase == Phase.Act && turn.activePlayer == actor
```

Compare `phase` and `activePlayer`, not the whole `TurnState`: `usedPowers`
differs between the two sides and is not what the assertion claims.

Each fixture constructs its own `ParkedDecisionAssertions` with the catalog and
power catalogs its `rules` was built with. `TargetsFixture`'s rules carry
`phasePowerCatalog = PhasePowerCatalog.default(catalog)`; pass the same.

- [ ] **Step 2: Move the clue strings**

Fifteen assertions read `t.continue.toString` as their failure message. Replace
each with `parked.parkedDecision(t.state).toString`. The sites:
`HornedMaskSuite:54,92,125,149,164`; `IvoryEyeSuite:42,100`;
`CrystalVialSuite:65,138`; `SleightOfHandSuite:42,87`; `WolvesSuite:30`;
`AlchemistSuite:43,64`; and `SilverTongueSuite:62` / `LeagueTreatySuite:29`,
which Task 5 covers.

- [ ] **Step 3: Strengthen the negatives**

Eighteen sites assert
`!t.continue.isInstanceOf[OathContinue.AwaitingPowerDecision]`, which passes
when the walk parked on some other family. Replace each with
`parked.assertNotParked(t.state)`.

The sites in this task: `HornedMaskSuite:138,148,157,163,172`;
`CrystalVialSuite:137`; `AlchemistSuite:42,50`; `SleightOfHandSuite:86,95`;
`IvoryEyeSuite:99`.

**If one of these fails, stop.** It means the walk parks somewhere the suite did
not know about. Record the suite, the test, and what `parkedDecision` reports,
report it, and leave the assertion strong.

- [ ] **Step 4: Convert the remaining direct sites in this family**

`PeoplesFavorMobSuite:43` (`transition.continue == OathContinue
.AwaitingSearchDecision(actor, ...)`) becomes an `assertParked` against
`ActionRef.PlayFacedownAdviser` or whichever procedure the suite starts — read
it from `walkerProcedure` rather than assuming.

`TargetsFixture:72` and `MovementFixture:81,85` are the helper bodies from
Step 1 and need no second edit.

- [ ] **Step 5: Run the power suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.*"`
Expected: PASS.

Run: `./sbtw "testOnly oathdigital.gameplay.CoOwnedDecideSuite oathdigital.gameplay.CatacombsContributionSuite oathdigital.application.BannerFaceProjectionSuite"`
Expected: PASS — these never mention the type and must not need an edit.
`BannerFaceProjectionSuite` is outside the `gameplay.powers` package but
imports `TargetsFixture`, so it is the one consumer the package run misses.

- [ ] **Step 6: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/powers/ src/test/scala/oathdigital/gameplay/CoOwnedDecideSuite.scala src/test/scala/oathdigital/gameplay/CatacombsContributionSuite.scala
git commit -m "test(powers): assert nothing is parked, not that one family is absent

The power fixtures' predicates now read the parked decision, and eighteen
negatives that only excluded AwaitingPowerDecision now exclude every park.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: The application and server suites

**Files:**
- Modify: `src/test/scala/oathdigital/application/ParkedServiceFixture.scala`
- Modify: `src/test/scala/oathdigital/application/ForgeWalkerFixture.scala`
- Modify: `src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala`
- Modify: `src/test/scala/oathdigital/application/PendingWalkerInvariantSuite.scala`
- Modify: `src/test/scala/oathdigital/server/TrustedGameProvisioningSuite.scala`

**Interfaces:**
- These suites hold `GameAccepted`, not `OathTransition`. Pass `accepted.state`; the module takes an `OathState` for exactly this reason.

- [ ] **Step 1: `ParkedServiceFixture`**

Five sites. `:141-144` (the ruler's Rest decision, matched for its owner) and
`:218` (the Oathkeeper recipient, owned by the holder) are two of the eight
owner-substitute sites: they are the rule under test, so they become
`assertParked` with the off-turn owner named —
`parked.assertParked(accepted.state, PhaseTransitionRef.FinishRest,
<the decision id>, ruler)`. Read the procedure the fixture actually parked
under rather than assuming; `:216-220` already asserts a triggered Oathkeeper
park on the event, which names it.

`:177` becomes `assertParked(_, ActionRef.Recover,
RecoverProcedure.choiceDecisionId, actor)`.
`:253` becomes `assertResumed(_, Phase.Rest, active)`.
`:258` becomes `assertParked` naming the power procedure and decision the
fixture just used — not `assertNotParked`, because this fixture's whole purpose
is to hand back a park.

- [ ] **Step 2: `ForgeWalkerFixture`**

`:103` and `:114-115` destructure the continuation for a decision id to send
back. Replace with `parked.parkedDecision(accepted.state)` and read
`.decision`. Keep the surrounding control flow identical.

- [ ] **Step 3: `GameApplicationServiceSuite`**

Fourteen sites. Three are deletions:

- `:165-168` — delete the continuation assertion. The two lines above it
  already assert `walkerProcedure` and `walkerPending`.
- `:1074` — delete. `active` is read off `turn.activePlayer` on the line above,
  so the assertion restates its own input.
- `:77` — not an assertion: it destructures for a decision id. Rewrite as
  `val decision = parked.parkedDecision(parked.state).get.decision`.

The rest convert: `:189,973,1193` to `assertResumed(_, Phase.Act, actor)`;
`:267,289,298,357,376,530,822,1215` to `assertParked` naming the procedure,
decision id and actor each already carries.

- [ ] **Step 4: `PendingWalkerInvariantSuite`**

`:96-99` and `:106` destructure for a decision id. Rewrite both as
`parked.parkedDecision(...).get.decision`. Leave `:51-66` — the exclusion half
of the invariant — untouched.

- [ ] **Step 5: `TrustedGameProvisioningSuite`**

`:154` asserts `prepared.continue == accepted.continue`, which is a claim that
bootstrapping and handling agree. The honest replacement is that both reach the
same parked decision: `assertEquals(parked.parkedDecision(prepared.state),
parked.parkedDecision(accepted.state))`.

- [ ] **Step 6: Run the application and server suites**

Run: `./sbtw "testOnly oathdigital.application.* oathdigital.server.*"`
Expected: PASS, three fewer assertions and no fewer tests.

- [ ] **Step 7: Commit**

```bash
git add src/test/scala/oathdigital/application/ src/test/scala/oathdigital/server/TrustedGameProvisioningSuite.scala
git commit -m "test(application): assert the parked decision behind GameAccepted

Three assertions go: two restated a line above them, one restated its own
input. The rest name the procedure, decision and awaited player.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: The phase and procedure suites

**Files:** `gameplay/PhasePowerSuite.scala`, `gameplay/NegotiationProcedureSuite.scala`, `gameplay/EconomyWalkerSuite.scala`, `gameplay/MinorActionsSuite.scala`, `gameplay/RestWalkerSuite.scala`, `gameplay/RestSuite.scala`, `gameplay/SearchProcedureSuite.scala`, `gameplay/CardPlayProcedureSuite.scala`, `gameplay/VisionPlaySuite.scala`, `gameplay/TravelProcedureSuite.scala`, `gameplay/TakeWealthProcedureSuite.scala`, `gameplay/EndWakeProcedureSuite.scala`, `gameplay/PendingWalkerRulesSuite.scala`, `gameplay/OathRulesWalkerPowerSuite.scala`, `gameplay/oathkeeper/OathkeeperProcedureSuite.scala`, `gameplay/setup/GameStartToWakeSuite.scala`, `gameplay/powers/rest/SilverTongueSuite.scala`, `gameplay/powers/rest/LeagueTreatySuite.scala`, `gameplay/powers/economy/KnightsErrantSuite.scala`

- [ ] **Step 1: The two deletions in this group**

- `RestWalkerSuite:32` — delete. `next.activePlayer` is read on the line above.
- `TakeWealthProcedureSuite:150` — delete. Same restatement, through
  `ready.game.current.turn.activePlayer`.

- [ ] **Step 2: `KnightsErrantSuite`**

`:61-67` folds over the continuation to extract a decision id across two
families. Replace the whole fold with
`parked.parkedDecision(transition.state).fold("")(_.decision)` — the two-arm
match exists only because the continuation split one fact across two cases.

`:103-105` asserts the same decision id the line above already asserts: delete
the continuation assertion, keep the neighbour. `:133` converts to
`assertParked`, naming `ActionRef.Muster` as the procedure a Campaign forced
inside a Muster parks under — verify against `walkerProcedure` before writing
it.

- [ ] **Step 3: The two `GameFinished` assertions**

`RestWalkerSuite:79` and `RestSuite:345` assert only
`isInstanceOf[GameFinished]` and never check the winner. Replace each with an
assertion on `ready.game.current.result`: assert the winner where the test
already determines it (both stage a deterministic `WarExhaustionRandomPort`
that picks `candidates.last`), otherwise assert `result.nonEmpty`. The winner
is the stronger assertion; prefer it.

- [ ] **Step 4: The phase-carrying conversions**

To `assertResumed(_, Phase.Act, actor)`: `PhasePowerSuite:76,133`;
`MinorActionsSuite:117,159,199`; `NegotiationProcedureSuite:98,125`;
`EconomyWalkerSuite:59`; `TravelProcedureSuite:214`;
`EndWakeProcedureSuite:59`; `MurkyFountainSuite:42,81`;
`GamblingHallSuite:47,80`; `FaeMerchantSuite:42,71,79,104`;
`BoneDiceSuite:29`; `DowsingSticksSuite:30`;
`OathkeeperProcedureSuite:83`.

To `assertResumed(_, Phase.Wake, …)`: `PhasePowerSuite:36`;
`GameStartToWakeSuite:49`; `SilverTongueSuite:61`; `LeagueTreatySuite:28,47,63,
91`; `HornedMaskSuite:70`; `OathkeeperProcedureSuite:121`;
`TakeWealthProcedureSuite:122`.

To `assertResumed(_, Phase.Rest, …)`: `PhasePowerSuite:113,115`;
`SilverTongueSuite:27,51`; `MurkyFountainSuite:66`.

Several of these (`FaeMerchantSuite`, `MurkyFountainSuite`, `GamblingHallSuite`,
`BoneDiceSuite`, `DowsingSticksSuite`) sit in `powers/action/` and may already
have been converted in Task 3 if they used a fixture helper. Check before
editing; do not convert twice.

- [ ] **Step 5: The parked conversions**

To `assertParked`: `PhasePowerSuite:130`; `NegotiationProcedureSuite:47,53,82`;
`EconomyWalkerSuite:40,86`; `SearchProcedureSuite:84`;
`CardPlayProcedureSuite:306-307`; `VisionPlaySuite:112`;
`PendingWalkerRulesSuite:29,38`; `GamblingHallSuite:34`;
`FaeMerchantSuite:30`; `SilverTongueSuite:41-42`;
`OathRulesWalkerPowerSuite:507`.

To `assertNotParked`: `SearchProcedureSuite:118`.

`GameStartToWakeSuite:17-18` and `OathkeeperProcedureSuite:60,116` and
`LeagueTreatySuite:40-41,78-79` and `OathRulesWalkerPowerSuite:543` are the
owner substitutes: they name an owner who is not the acting player, which is
the rule under test. Write them as `assertParked` with that owner named
explicitly.

- [ ] **Step 6: `OathkeeperProcedureSuite:127`**

This constructs an `OathTransition(parked.state, Vector.empty, parked.continue)`
to feed something else. Drop the third argument in Task 7; for now leave the
line, since `OathTransition` still has the field.

- [ ] **Step 7: `OathRulesWalkerPowerSuite:507`, carefully**

This test's subject is the roll-decision-id gate, not the continuation. Convert
`:507-508` to `assertParked(started.state, ActionRef.Recover,
RecoverProcedure.rollDecisionId, actor)` and leave the `Left` assertion below
it exactly as it is. That `Left` is what Task 7 must keep alive.

- [ ] **Step 8: Run the group**

Run: `./sbtw "testOnly oathdigital.gameplay.*"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/
git commit -m "test(gameplay): assert the park, the phase and the winner directly

Continuations that named a phase become an assertion that nothing is parked
and the turn is where it should be; the two game-over checks now assert the
winner rather than the shape of a value nobody read.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: The registry and architecture suites

These are the assertions that restate the registry's own lambdas: they are
tautologies that die with the code they describe.

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/walker/WalkerProcedureRegistrySuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/BackendArchitectureSuite.scala`

- [ ] **Step 1: Delete the four `continuationFor` assertions**

In `WalkerProcedureRegistrySuite`:

1. In `"the Forge entry declares Forge's own kind, modifier window and continuation"`, delete the two `assertEquals(WalkerProcedureRegistry.continuationFor(ActionRef.Forge, …))` calls and the now-unused `actor`/`decision` locals. Retitle to `"the Forge entry declares Forge's own kind and modifier window"`.
2. In `"the Travel entry declares Travel's own kind and no park of any shape"`, delete the `Vector("travel", …).foreach` loop and its two-line comment. Retitle to `"the Travel entry declares Travel's own kind and rolls nothing"` — `rollDecisionId == None` is what carries the remaining claim.
3. In `"Begin Rest records Rest diagnostics; Finish Rest records none and parks as a generic Rest decision"`, delete the `continuationFor` assertion. Retitle to `"Begin Rest records Rest diagnostics and Finish Rest records none"`.
4. In `"every use-power reference is registered and parks as a power decision"`, delete the `continuationFor` assertion. Retitle to `"every use-power reference is registered"`.

**Do not touch** `"the production entries register every procedure reference"`.
That test is the registration gate's real replacement and it already exists.

- [ ] **Step 2: Note the gate in the suite's doc**

Above the key-set test, add:

```scala
  /** This is the registration gate. `OathRulesWalker.parkedContinue` used to
    * reject an unregistered continuation at command time, inside a running
    * game; with that gone, a procedure reachable but absent from `entries`
    * fails here instead, before anything runs.
    */
```

- [ ] **Step 3: `BackendArchitectureSuite`**

Leave `:20-21` alone for now; the argument disappears with the parameter in
Task 7.

- [ ] **Step 4: Run both suites**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.WalkerProcedureRegistrySuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS, with four fewer assertions and the same test count.

- [ ] **Step 5: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/walker/WalkerProcedureRegistrySuite.scala
git commit -m "test(walker): drop the assertions that restate the registry's lambdas

Four assertions checked that a continuation lambda returns what its literal
says it returns. The registration gate they were mistaken for is the key-set
invariant beside them, which stays.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: Delete the type and its carriers

Only now, with nothing asserting it, does the type go. The compiler finds every
remaining site: `OathTransition` is a case class and every construction is
exhaustive.

**Files:**
- Modify: `src/main/scala/oathdigital/model/GameProcedureProtocol.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathLifecycle.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/MinorActions.scala`
- Modify: `src/main/scala/oathdigital/gameplay/phases/rest/TurnBoundary.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala`
- Modify: `src/test/scala/oathdigital/gameplay/BackendArchitectureSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/oathkeeper/OathkeeperProcedureSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/OathRulesWalkerPowerSuite.scala`

- [ ] **Step 1: Replace `parkedContinue` with `checkAnswerable`**

In `OathRulesWalker.scala`, replace `parkedContinue` with a guard that keeps
its two surviving rejections and returns nothing:

```scala
  /** A park a client cannot answer is refused at command time, before the
    * `WalkerParked` fact is appended -- the rejection `parkedContinue` ran
    * on its way to building a continuation nobody read.
    *
    * A Roll park carries no `Decide` and therefore no decision id of its own,
    * so the procedure must declare one; `WalkerDecisionProjector` reads the
    * same accessor when it projects the park, and a park whose id no tree
    * declares would be handed to the client as an unanswerable question.
    */
  private def checkAnswerable(ready: ReadyGame, tree: Operation,
      pending: PendingTree, powers: WalkerPowers, procedure: ProcedureRef)
      : Either[OathViolation, Unit] =
    ProcedureWalker.parkedRoll(ready, tree, pending, powers) match
      case Some(_) =>
        WalkerProcedureRegistry.rollDecisionId(procedure).map(_ => ())
      case None => ProcedureWalker.parkedDecide(ready, tree, pending,
          powers) match
        case Some(_) => Right(())
        case None => Left(InvalidEventOrder(
          "parked walker position is neither a Roll nor a Decide"))
```

In `walkerTransition`'s `Parked` branch, replace
`continue <- parkedContinue(liveReady, tree, pending, powers, procedure)` with
`_ <- checkAnswerable(liveReady, tree, pending, powers, procedure)` and yield
`OathTransition(finalState, steps :+ fact)`.

Keep the comment above `foldEvents` about deriving from the live state: it is
still why `checkAnswerable` runs against `liveReady` rather than the pre-walk
snapshot.

- [ ] **Step 2: Delete `continuationIn` and the Finished branch's continuation**

Delete `continuationIn` entirely. In the `Finished` branch, the `continued`
value and its `flatMap` collapse:

```scala
    case WalkerOutcome.Finished(treeless, steps) =>
      GameplayTransition(state, steps :+ WalkerCompleted(procedure))(evolve)
        .flatMap(recordCardPlayFallback(_, procedure, steps))
        .flatMap(transition =>
          if runsActionBoundary(procedure) then completeAction(transition)
          else if runsTurnBoundary(procedure) then turnBoundary(transition)
          else Right(transition))
        .flatMap(transition =>
          if procedure == PhaseTransitionRef.BeginRest then autoFinishRest(transition)
          else Right(transition))
```

The `treeless` binding and the `turn` local become unused — check whether
`treeless` is still needed by the branch and remove what is not.

`continuationIn`'s doc explains why a phase with no continuation is a typed
rejection. That reasoning dies with the member: `RoundEnd` no longer needs an
answer because nothing asks. Do not preserve the rejection.

- [ ] **Step 3: `OathTransition` and `GameplayTransition`**

In `GameProcedureProtocol.scala`, delete `sealed trait OathContinue` and its
whole companion, and drop the field:

```scala
final case class OathTransition(
    state: OathState,
    events: Vector[OathEvent]
)
```

In `OathLifecycle.scala`, drop `continue` from `GameplayTransition.apply` and
from the `OathTransition` it builds.

- [ ] **Step 4: The three copy sites**

- `OathRules.scala:113` — `GameplayTransition(state, Vector(event))(evolve)`;
  the `AwaitingSetupPawn` argument and its `SetupProcedure.pawnDecisionId`
  import go with it. Check whether the import is still used elsewhere in the
  file before deleting it.
- `OathRules.scala:178-184` — `enterWake`'s `append` becomes
  `evolve(current.state, event).map(next => current.copy(state = next,
  events = current.events :+ event))`, and the `event match` that produced
  `GameFinished` goes.
- `TurnBoundary.scala:20-27` — the same shape: the `continue` local and its
  match go; `current.copy(state = next, events = current.events :+ event)`
  remains.
- `MinorActions.scala:176-178` — `OathTransition(_, Vector(event))`. The
  `playerId(event)` helper below it exists only to fill the continuation:
  delete it too if nothing else calls it, and check whether the
  `ActActionSelection` import and the `IllegalArgumentException` arm go with
  it.

- [ ] **Step 5: The registry**

Delete `Entry.continuationFor` from the case class, all nineteen
`continuationFor = …` literals (including `usePowerEntry`'s), and
`WalkerProcedureRegistry.continuationFor`. In the `Entry` scaladoc, delete the
sentence describing the mapping to `OathContinue` (`:57-63`) and leave the
`rollDecisionId` sentences, which still apply.

- [ ] **Step 6: The application DTOs**

Drop `continue` from `GameAccepted` and `PreparedGameBootstrap`, and from the
two construction sites (`GameApplicationService.scala:105-106,303-307`).

- [ ] **Step 7: The scaladoc mention**

`application/WalkerDecisionProjector.scala:25` names `OathContinue` in prose.
Reword to name the parked decision rather than the deleted type.

- [ ] **Step 8: The three remaining test arguments**

- `BackendArchitectureSuite:20-21` — drop the third argument.
- `OathkeeperProcedureSuite:127` — drop the third argument.
- `OathRulesWalkerPowerSuite`'s `entryWindowed` — delete the
  `continuationFor = (_, _, _) => None` line from the `Entry` it builds.

- [ ] **Step 9: Compile and run everything**

Run: `./sbtw test`
Expected: PASS. Any remaining reference is a compile error, not a silent
survival.

Run: `grep -rn 'OathContinue' src frontend/src shared/src`
Expected: no output. (Specs and plans under `docs/` keep their references; they
are records.)

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital src/test/scala/oathdigital
git commit -m "refactor(walker): delete OathContinue, a fact with no reader

Every command constructed one and nothing read it: not a route, not a codec,
not a projection. The parked decision's owner is recomputed from the tree on
every command by ProcedureWalker.awaitedPlayer, which is what authorization and
projection already use. The two gates parkedContinue also ran -- a Roll park
under a procedure declaring no roll decision id, and a park that is neither a
Roll nor a Decide -- keep their codes and detail strings in checkAnswerable.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 8: Documentation and the full gate

**Files:**
- Modify: `docs/superpowers/specs/2026-09-24-walker-continuation-removal-design.md`
- Modify: `docs/ROADMAP.md` (only if it names the removed items)

- [ ] **Step 1: Mark the spec delivered**

Add to the spec's status blockquote the commit range that delivered it, in the
form the other delivered specs use. Check one of them first, and match it.

- [ ] **Step 2: Check ROADMAP**

Run: `grep -n 'OathContinue\|continuation' docs/ROADMAP.md`
If a roadmap item names this work, mark it done; if not, change nothing.

- [ ] **Step 3: Run the full gate**

Run: `./sbtw test`
Expected: PASS.

Run: `./sbtw frontend/test`
Expected: PASS — nothing in `frontend/` changed; this is the repository gate.

Run: `python3 scripts/check-architecture.py`
Expected: `architecture check passed: N production Scala files`.

Run: `python3 scripts/check-markdown-links.py`
Expected: PASS.

Run: `grep -rn "OathContinue" src frontend/src shared/src | wc -l`
Expected: `0`.

Compare the test count against the Baseline recording. It should be identical:
this plan deletes 31 assertions and no tests.

- [ ] **Step 4: Commit**

```bash
git add docs/
git commit -m "docs: mark the walker continuation removal delivered

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```
