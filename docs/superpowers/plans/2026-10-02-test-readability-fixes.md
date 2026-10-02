# Test Readability Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix every approved ledger row so a new reader can say what any test checks from its body alone, hold every fake to its real object with a contract suite, and add guards that keep the mechanical rules from coming back.

**Architecture:** Two Foundation tasks add the testkit accessors and `Table` steps the ledger asks for. Then one fix task per area part (an area's rows split into parts of about 60 rows) rewrites arrange and act lines with assertions frozen, proves the tests still catch broken rules with sampled mutations, and records each row's outcome in the ledger. Three test-double tasks add the contract suites, and two closing tasks add the guards and write the Result.

**Tech Stack:** Scala 3, munit, sbt through `./sbtw` (projects `root` and `frontend`), Scala.js frontend tests in jsdom, `shared/src/test` compiled into both test trees.

**Spec:** [docs/superpowers/specs/2026-10-02-test-readability-design.md](../specs/2026-10-02-test-readability-design.md)

**Ledger:** [docs/superpowers/specs/2026-10-02-test-readability-ledger.md](../specs/2026-10-02-test-readability-ledger.md), approved by the user on 2026-10-02 (commit 5508f85b).

**Procedure:** [docs/superpowers/plans/2026-10-02-test-readability-fix-procedure.md](2026-10-02-test-readability-fix-procedure.md). Every fix task (Tasks 3 to 36) follows it; its implementer reads it first.

## Global Constraints

- Assertions are frozen: a fix task "rewrites only the arrange and act lines; an assertion may only get stronger, never weaker or dropped" (spec).
- Every fix task "runs in a short-lived worktree off local `main` and merges back to local `main` when green, since other sessions keep moving `main`" (spec). The merge is part of the approved flow; the controller does it (procedure, step 9).
- Every fix task "breaks three to five of the production rules its rewritten tests guard, one mutation at a time, confirms the right test fails, restores the rule from a backup, and records the result in the ledger" (spec).
- Every fix task "ends with `./sbtw root/test` and `./sbtw frontend/test` green, and the test count unchanged except for recorded splits or merges" (spec).
- Work from each row's planned outcome. Rule labels stay as filed (Review, decision 2). Placeholders such as `<printed name>` are filled in by the task (decision 4).
- A helper's name describes what it builds or does; a doc comment does not excuse a vague name (Review, decision 3).
- 00-040 `againstPlayer` is renamed `rivalRulesOriginAndHoldsOathkeeper` (Review, decision 1).
- A shared-fixture helper that is inlined or renamed changes with all its callers in the task that owns it, so no fixture is left half dissolved (spec).
- R10 rows (test doubles) are fixed only in Tasks 37 to 39.
- Out of scope: speed, pruning beyond a merge the rubric justifies, golden-log contents, state patches whose subject is the phase or turn. Production changes only to fix a fake's divergence or add a seam a contract suite needs.
- Never stage `docs/ROADMAP.md`.
- Commit messages end with the committing model's own trailer; the controller's is `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Subagents never run on a model above the controller's.
- Never use `preview_start`; no task needs a running server.

## Controller notes

- Create each task's worktree with the procedure's step 3 commands, not a tool that branches from the remote: the remote lags local `main`.
- The worktree guard refuses git commands on the main checkout from inside a worktree session. Leave the worktree (keep it) before the fast-forward merge in the procedure's step 9.
- `frozen.py` lives at `/Users/roman/projects/oathdigital/.superpowers/readability/frozen.py` (git-ignored). Pass the task reviewer its output with the review package.
- The task reviewer checks, beyond the usual review: the `frozen.py` lines (each must be a strengthening), that every row in the task's range has an Outcome, that each touched suite now passes the rubric, and that the mutations were caught.
- HEAD on `main` moves under you (other sessions). Re-check it before any amend, reset or rebase.

## Ownership of the shared-fixture rows

Each shared fixture's rows go to the area task holding most of its callers (counted on 2026-10-02 by files that name the fixture). Ties go to the area whose suites test the fixture's subject.

| Fixture rows | Fixture | Task |
|---|---|---|
| 00-001 to 00-015 | ForgeWalkerFixture, ParkedServiceFixture | Task 27 (01.1) |
| 00-016 to 00-031 | GoldenLog, LogScripts | Task 32 (02 fixtures) |
| 00-032 to 00-042 | CampaignFixture | Task 19 (07 fixtures) |
| 00-043 to 00-056, 00-059 | EconomyFixture, PhasePowerFixture, PlacementFixture, SearchBudget, WalkerRecordedOpsReducer | Task 12 (10 fixtures) |
| 00-057, 00-058 | WalkerDiceFixture | Task 24 (03.1) |
| 00-060 | CardStaging | Task 8 (06 fixtures) |
| 00-061 to 00-066, 00-071 to 00-093 | NoteText, PowerFixture, TargetsFixture, MovementFixture, PaidActionHarness | Task 3 (04 fixtures) |
| 00-067 to 00-070 | SearchFixture | Task 16 (05.1) |
| 00-094 to 00-100 | BannerFixture | Task 8 (06 fixtures) |
| 00-101 to 00-106 | PlanDriver | Task 19 (07 fixtures) |
| 00-107, 00-108 | VisionPlayFixture | Task 16 (05.1) |
| 00-109 to 00-117 | LeagueTreatyFixture, SilverTongueFixture | Task 8 (06 fixtures) |
| 00-118 to 00-124 | TargetingFixture | Task 19 (07 fixtures) |
| 00-125 to 00-134 | TravelFixture, RiverFixture | Task 8 (06 fixtures) |
| 00-135 to 00-137 | WhenPlayedHarness | Task 16 (05.1) |
| 00-138 to 00-144, 00-148 | FirstGameSetupFixture, ParkedDecisionAssertions | Task 12 (10 fixtures) |
| 00-145 to 00-147 | SetupWalkDriver | Task 8 (06 fixtures) |
| 00-149 to 00-151, and 08-020 | ReadyGames, TestGameFixtures | Task 24 (03.1) |
| 00-152 to 00-165 | Look, ParkedNode, Situation, SituationDriver, Table, TestCards | Tasks 1 and 2 (Foundation) |
| 00-166 | RecordingControls (R10) | Task 39 |
| 00-167, 00-168 | TestBrowser | Task 34 (11.1) |

R10 rows among these (00-014, 00-018, 00-043, 00-044, 00-047, 00-101) go to Task 38.

---

### Task 1: Foundation, accessors

The accessors the R4 rows ask for, settled to one name per shape (ledger, "Foundation inputs", Overlaps). No suite migrates to them here.

**Files:**
- Create: `shared/src/test/scala/oathdigital/testkit/Unwrap.scala`
- Create: `shared/src/test/scala/oathdigital/testkit/UnwrapSuite.scala`
- Create: `src/test/scala/oathdigital/testkit/Outcomes.scala`
- Create: `src/test/scala/oathdigital/testkit/OutcomesSuite.scala`
- Modify: `src/test/scala/oathdigital/testkit/Look.scala`
- Modify: `src/test/scala/oathdigital/testkit/TableSuite.scala` (Look read tests)
- Modify: `docs/superpowers/specs/2026-10-02-test-readability-ledger.md` (the Mutations section and the accessor settlement)

**Interfaces:**
- Produces (shared, all three test trees), in `object oathdigital.testkit.Unwrap`:
  - `def accepted[L, R](result: Either[L, R])(using munit.Location): R`
  - `def rejected[L, R](result: Either[L, R])(using munit.Location): L`
  - `def present[A](option: Option[A], what: String)(using munit.Location): A`
  - `def as[T](value: Any)(using ClassTag[T], munit.Location): T`
- Produces (root), in `object oathdigital.testkit.Outcomes`:
  - `def ready(state: OathState)(using munit.Location): ReadyGame`
  - `def parked(outcome: Either[OathViolation, WalkerOutcome])(using munit.Location): WalkerOutcome.Parked`
  - `def finished(outcome: Either[OathViolation, WalkerOutcome])(using munit.Location): WalkerOutcome.Finished`
  - `def assertWireRoundTrips(events: Vector[OathEvent])(using munit.Location): Unit`
- Produces (root), on `Look`: `def discardAfter(site: String | SiteId)(using munit.Location): Vector[WorldCardId]`, `def forceKind(p: PlayerId)(using munit.Location): ForceKind`

- [ ] **Step 1: Set up the worktree** with the procedure's step 3 (task name `foundation-1`).

- [ ] **Step 2: Write the failing shared test**

`shared/src/test/scala/oathdigital/testkit/UnwrapSuite.scala`:

```scala
package oathdigital.testkit

import Unwrap.*

/** The accessors return the value a test expects and, when the result went
  * the other way, fail with the value it got. */
class UnwrapSuite extends munit.FunSuite:
  private def failure(body: => Any): String =
    intercept[munit.FailException](body).getMessage

  test("accepted returns a Right and fails naming the Left"):
    assertEquals(accepted(Right(3)), 3)
    assert(failure(accepted(Left("no wealth here"))).contains("no wealth here"))

  test("rejected returns a Left and fails naming the Right"):
    assertEquals(rejected(Left("no wealth here")), "no wealth here")
    assert(failure(rejected(Right(3))).contains("Right(3)"))

  test("present returns the value and fails saying what was missing"):
    assertEquals(present(Some(3), "a stored game"), 3)
    assert(failure(present(None, "a stored game")).contains("a stored game"))

  test("as returns the case and fails naming the actual value"):
    val value: Either[String, Int] = Right(3)
    assertEquals(as[Right[String, Int]](value), Right(3))
    assert(failure(as[Left[String, Int]](value)).contains("Right(3)"))
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "root/testOnly oathdigital.testkit.UnwrapSuite"`
Expected: compile error, `Not found: Unwrap`.

- [ ] **Step 4: Write `Unwrap`**

`shared/src/test/scala/oathdigital/testkit/Unwrap.scala`:

```scala
package oathdigital.testkit

import scala.reflect.ClassTag

/** Reads of a result a test expects to have gone one way. Each fails the
  * test with the value it got instead, never with a bare
  * NoSuchElementException, MatchError or ClassCastException. The root,
  * frontend and shared suites all use them: this file compiles into both
  * test trees.
  *
  * One name per shape: a Right is `accepted`, a Left is `rejected`, an
  * Option's value is `present`, and a case of a sealed type is `as`. */
object Unwrap:
  /** The Right of `result`; fails with the Left. */
  def accepted[L, R](result: Either[L, R])(using munit.Location): R =
    result match
      case Right(value) => value
      case Left(refusal) =>
        munit.Assertions.fail(s"expected a Right, got Left($refusal)")

  /** The Left of `result`; fails with the Right. */
  def rejected[L, R](result: Either[L, R])(using munit.Location): L =
    result match
      case Left(refusal) => refusal
      case Right(value) =>
        munit.Assertions.fail(s"expected a Left, got Right($value)")

  /** The value in `option`; fails saying `what` was expected. */
  def present[A](option: Option[A], what: String)(using munit.Location): A =
    option.getOrElse(munit.Assertions.fail(s"expected $what, got None"))

  /** `value` as the case `T`; fails with the value's actual case. */
  def as[T](value: Any)(using tag: ClassTag[T], location: munit.Location): T =
    value match
      case tag(matched) => matched
      case other => munit.Assertions.fail(
        s"expected a ${tag.runtimeClass.getSimpleName}, got $other")
```

- [ ] **Step 5: Run the shared test on both platforms**

Run: `./sbtw "root/testOnly oathdigital.testkit.UnwrapSuite" "frontend/testOnly oathdigital.testkit.UnwrapSuite"`
Expected: PASS, 4 tests each.

- [ ] **Step 6: Write the failing root test**

`src/test/scala/oathdigital/testkit/OutcomesSuite.scala`:

```scala
package oathdigital.testkit

import oathdigital.gameplay.walker.WalkerOutcome
import oathdigital.model._
import oathdigital.model.OathState.Ready
import Outcomes.*

/** The rules-result reads return what a test expects and fail with what
  * they got. */
class OutcomesSuite extends munit.FunSuite:
  private def failure(body: => Any): String =
    intercept[munit.FailException](body).getMessage

  private val quiet = Table.start.ready
  private val walk = PendingTree(Vector("0"), Vector.empty)

  test("ready returns the game in a Ready state and fails naming another state"):
    assertEquals(ready(Ready(quiet)), quiet)
    assert(failure(ready(OathState.NoGame)).contains("NoGame"))

  test("parked and finished return their walk and fail naming the other"):
    val parkedWalk = WalkerOutcome.Parked(walk, Vector.empty)
    val finishedWalk = WalkerOutcome.Finished(quiet, Vector.empty)
    assertEquals(parked(Right(parkedWalk)), parkedWalk)
    assertEquals(finished(Right(finishedWalk)), finishedWalk)
    assert(failure(parked(Right(finishedWalk))).contains("Finished"))
    assert(failure(finished(Left(OathViolation.NoPlayableOption("Search"))))
      .contains("NoPlayableOption"))

  test("assertWireRoundTrips passes for the events of a real setup"):
    assertWireRoundTrips(FirstGameSetupFixture.execute()._2.take(3))
```

Import `oathdigital.gameplay.setup.FirstGameSetupFixture`. (Task 12 later renames `execute` to `setupThroughWake` and changes this caller with the others.)

- [ ] **Step 7: Run it to verify it fails**

Run: `./sbtw "root/testOnly oathdigital.testkit.OutcomesSuite"`
Expected: compile error, `Not found: Outcomes`.

- [ ] **Step 8: Write `Outcomes`**

`src/test/scala/oathdigital/testkit/Outcomes.scala`:

```scala
package oathdigital.testkit

import oathdigital.gameplay.walker.WalkerOutcome
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.serialization.GameEventWire

/** Reads of rules results that [[Unwrap]] cannot name generically. Each
  * fails the test with the value it got. */
object Outcomes:
  /** The game in `state`; fails naming the state when it is not Ready. */
  def ready(state: OathState)(using munit.Location): ReadyGame = state match
    case Ready(game) => game
    case other => munit.Assertions.fail(s"expected a ready game, got $other")

  /** The walk parked at a decision; fails with the violation or the
    * finished walk. */
  def parked(outcome: Either[OathViolation, WalkerOutcome])(
      using munit.Location): WalkerOutcome.Parked =
    Unwrap.as[WalkerOutcome.Parked](Unwrap.accepted(outcome))

  /** The walk that consumed its whole tree; fails with the violation or the
    * park. */
  def finished(outcome: Either[OathViolation, WalkerOutcome])(
      using munit.Location): WalkerOutcome.Finished =
    Unwrap.as[WalkerOutcome.Finished](Unwrap.accepted(outcome))

  /** Every event encodes and decodes back equal; fails naming the first
    * that does not, and why. */
  def assertWireRoundTrips(events: Vector[OathEvent])(
      using munit.Location): Unit =
    events.zipWithIndex.foreach { case (event, index) =>
      val back = GameEventWire.encodeEvent("g", index.toLong, event)
        .left.map(failure => s"encode failed: $failure")
        .flatMap(json => GameEventWire.decode(json)
          .left.map(failure => s"decode failed: $failure").map(_.event))
      munit.Assertions.assertEquals(back, Right(event),
        s"event $index does not round trip")
    }
```

- [ ] **Step 9: Run it to verify it passes**

Run: `./sbtw "root/testOnly oathdigital.testkit.OutcomesSuite"`
Expected: PASS, 3 tests.

- [ ] **Step 10: Write the failing Look tests** (append to `TableSuite.scala`)

```scala
  test("Look.discardAfter reads the discard pile of the region after the site's"):
    val table = Table.start.discarded(Region.Provinces, "Wolves")
    assertEquals(Look(table.ready).discardAfter("Ancient City"),
      Vector(CatalogNames.worldCard("Wolves")))

  test("Look.forceKind reads the kind of a player's own warbands"):
    val look = Look(Table.start.ready)
    assertNotEquals(look.forceKind(p1), look.forceKind(p2))
    assertNotEquals(look.forceKind(p1), ForceKind.Bandit)
```

Before running, check that Ancient City lies in the Cradle and that the region after the Cradle is the Provinces (`CardPlay.nextRegion`); if not, name the region `nextRegion` returns for Ancient City's region in the first test. "Wolves" must be a denizen the first game deals; if `CatalogNames` refuses it, pick another denizen name it lists.

- [ ] **Step 11: Run them to verify they fail**

Run: `./sbtw "root/testOnly oathdigital.testkit.TableSuite"`
Expected: compile error, `value discardAfter is not a member of oathdigital.testkit.Look`.

- [ ] **Step 12: Add the reads to `Look`**

Add the imports `oathdigital.gameplay.actions.CardPlay` and `oathdigital.gameplay.powers.PlayerFacts`, and these members:

```scala
  /** The discard pile of the region after `site`'s region: where a card a
    * power discards from that site goes. Fails when the site is in no
    * region. */
  def discardAfter(site: String | SiteId)(using munit.Location)
      : Vector[WorldCardId] =
    val id = CatalogNames.site(site)
    val region = current.map.regionOf(id).getOrElse(
      munit.Assertions.fail(s"$id is in no region"))
    current.commonCards.discard(CardPlay.nextRegion(region))

  /** The kind of warband `p`'s own warbands are; fails with the rules'
    * refusal. */
  def forceKind(p: PlayerId)(using munit.Location): ForceKind =
    Unwrap.accepted(PlayerFacts.forceKind(ready, p))
```

- [ ] **Step 13: Run them to verify they pass**

Run: `./sbtw "root/testOnly oathdigital.testkit.TableSuite"`
Expected: PASS.

- [ ] **Step 14: Record the settlement and the Mutations table in the ledger**

In the ledger's "Foundation inputs" section, after the Overlaps list, add:

```markdown
**Settled (Plan 2, Task 1).** One accessor per shape:
`Unwrap.accepted` (a Right: also what rows call `rightOf`, `built`,
`started`, `opened`, `executed`, `replayed`, `authorized`, `created`,
`begun`, `parsed`, `right`, `encoded`, `decoded`), `Unwrap.rejected` (a Left:
`leftOf`, `assertLeft`, `refusal`, `refused`, `failureOf`), `Unwrap.present`
(an Option: `loaded`), `Unwrap.as[T]` (a case: `chooseOne`, `chooseMany`,
`chooseAmount`, `updated`), and in the root testkit `Outcomes.ready`,
`Outcomes.parked`, `Outcomes.finished`, `Outcomes.assertWireRoundTrips`,
`Look.discardAfter` and `Look.forceKind`.
```

Before the "## Shared fixtures" heading, add:

```markdown
## Mutations

Each fix task breaks three to five production rules its rewritten tests
guard, one at a time, and records the result here.

| Task | Rule broken (file:line: change) | Test that failed | Result |
|---|---|---|---|
```

- [ ] **Step 15: Gate and commit**

Run the procedure's step 6 gate (no `frozen.py` lines are expected: this task adds tests only). Then:

```bash
git add shared/src/test/scala/oathdigital/testkit/Unwrap.scala shared/src/test/scala/oathdigital/testkit/UnwrapSuite.scala src/test/scala/oathdigital/testkit/Outcomes.scala src/test/scala/oathdigital/testkit/OutcomesSuite.scala src/test/scala/oathdigital/testkit/Look.scala src/test/scala/oathdigital/testkit/TableSuite.scala docs/superpowers/specs/2026-10-02-test-readability-ledger.md
git commit -m "test: add the testkit accessors that fail with the value they got" -m "Co-Authored-By: <your model's trailer>"
```

The root total grows by 9 (4 Unwrap, 3 Outcomes, 2 Look) and the frontend total by 4 (Unwrap). Record both in the report.

---

### Task 2: Foundation, Table steps and testkit renames

The new `Table` steps the ledger asks for (three or more suites need each), and the testkit renames from rows 00-153, 00-154, 00-157, 00-162 and 00-164. A rename touches every caller in one commit because the compiler must see them all; the call-site rewrites the rows ask for beyond the rename (stating a site a test depends on) belong to the area tasks.

**Files:**
- Modify: `src/test/scala/oathdigital/testkit/Table.scala`
- Modify: `src/test/scala/oathdigital/testkit/Look.scala`
- Modify: `src/test/scala/oathdigital/testkit/Situation.scala`
- Modify: `src/test/scala/oathdigital/testkit/TableSuite.scala`
- Modify: every caller of `Table.warbands`, `Look.warbands`, `Table.homeOf`, `Look.denizens` and `Situation.serviceRules` under `src/test`
- Modify: the ledger (Outcome cells of 00-152 to 00-165, except R10)

**Interfaces:**
- Consumes: Task 1's `Unwrap`, `Outcomes`, `Look.discardAfter`, `Look.forceKind`.
- Produces, on `Table`:
  - `def relicDeckEmptied: Table`
  - `def parkedWalker(action: ActionRef, path: String = "0", answered: Vector[Answered] = Vector.empty): Table`
  - `def visionsDrawn(n: Int): Table`
  - `def revealedVision(player: PlayerId, vision: VisionId)(using munit.Location): Table`
  - `def backInPhase(phase: Phase): Table`
  - `def peoplesFavor(holder: Option[PlayerId], favor: Int, face: PeoplesFavorFace = PeoplesFavorFace.Mob): Table`
  - `def darkestSecret(holder: Option[PlayerId], secrets: Int, face: DarkestSecretFace = DarkestSecretFace.WanderingFlame): Table`
  - renamed: `Table.boardWarbands(player, n)` (was `warbands`), `Table.quietPawnSite(player)` (was `homeOf`)
- Produces, on `Look`: `boardWarbands(p)` (was `warbands`), `cardsAt(site)` (was `denizens`)
- Produces, on `Situation`: `productionRules(...)` (was `serviceRules`)

- [ ] **Step 1: Set up the worktree** with the procedure's step 3 (task name `foundation-2`).

- [ ] **Step 2: Write the failing step tests** (append to `TableSuite.scala`)

```scala
  test("relicDeckEmptied moves the relic deck to the reliquary, losing no relic"):
    val before = Table.start.ready
    val after = Table.start.relicDeckEmptied.ready
    assertEquals(after.game.current.commonCards.relicDeck, Vector.empty)
    assertEquals(after.game.campaign.reliquary,
      before.game.campaign.reliquary ++ before.game.current.commonCards.relicDeck)

  test("parkedWalker parks the action's walker at the given step"):
    val current = Table.start.parkedWalker(ActionRef.Recover).ready.game.current
    assertEquals(current.walkerProcedure, Some(ActionRef.Recover))
    assertEquals(current.walkerPending,
      Some(PendingTree(Vector("0"), Vector.empty)))

  test("visionsDrawn sets the Visions Drawn track"):
    assertEquals(Table.start.visionsDrawn(3).ready.game.current.tracks
      .visionsDrawn, 3)

  test("revealedVision reveals the Vision faceup and takes it from the deck"):
    val current = Table.start.revealedVision(p1, VisionRules.Faith).ready
      .game.current
    assertEquals(current.players.find(_.player == p1).flatMap(_.revealedVision),
      Some(VisionState(VisionRules.Faith, Orientation.FaceUp)))
    assert(!current.commonCards.worldDeck.contains(VisionRules.Faith))

  test("backInPhase changes the phase and keeps the powers used this turn"):
    val wolves = CatalogNames.denizen("Wolves")
    val use = PowerUseRef(PowerTiming.Act, PowerSourceRef.Card(wolves),
      PowerId("denizen.wolves"))
    // The used power is the subject: no step records a use.
    val used = Table.start.update(_.updateCurrent(c => c.copy(turn =
      c.turn.copy(usedPowers = Set(use)))))
    val turn = used.backInPhase(Phase.Wake).ready.game.current.turn
    assertEquals(turn.phase, Phase.Wake)
    assertEquals(turn.usedPowers, Set(use))

  test("the quiet table's banners lie on the faces the banner steps default to"):
    val banners = Table.start.ready.game.current.banners
    assertEquals(banners.peoplesFavor.active, PeoplesFavorFace.Mob)
    assertEquals(banners.darkestSecret.active, DarkestSecretFace.WanderingFlame)

  test("the banner steps set the face they are given"):
    val banners = Table.start
      .peoplesFavor(Some(p1), 2, face = PeoplesFavorFace.GrandCouncil)
      .darkestSecret(Some(p2), 1, face = DarkestSecretFace.Festival)
      .ready.game.current.banners
    assertEquals(banners.peoplesFavor.active, PeoplesFavorFace.GrandCouncil)
    assertEquals(banners.darkestSecret.active, DarkestSecretFace.Festival)
```

Add imports for `VisionRules` (`oathdigital.gameplay.actions.VisionRules`) and any model name the compiler asks for (`oathdigital.model._` covers most). `PowerSourceRef.Card` takes the card id as `PowerUseRef`'s callers in `src/test` pass it; match them. If the quiet-table banner test fails, the defaults below take the faces the quiet table really shows, and the test asserts those.

- [ ] **Step 3: Run them to verify they fail**

Run: `./sbtw "root/testOnly oathdigital.testkit.TableSuite"`
Expected: compile error, `value relicDeckEmptied is not a member of oathdigital.testkit.Table`.

- [ ] **Step 4: Add the steps to `Table`**

Add after `bankFavor` (keep `update` last among the steps):

```scala
  /** The relic deck moved, in order, to the end of the campaign's
    * reliquary: a game whose relic deck has run out. No relic is lost. */
  def relicDeckEmptied: Table = update { r =>
    val deck = r.game.current.commonCards.relicDeck
    r.updateCurrent(c => c.copy(commonCards =
      c.commonCards.copy(relicDeck = Vector.empty)))
      .updateCampaign(c => c.copy(reliquary = c.reliquary ++ deck)) }

  /** A walker procedure for `action`, parked at step `path` of its tree
    * ("0" is the tree's first step), with `answered` already recorded. */
  def parkedWalker(action: ActionRef, path: String = "0",
      answered: Vector[Answered] = Vector.empty): Table =
    update(_.updateCurrent(_.copy(walkerProcedure = Some(action),
      walkerPending = Some(PendingTree(Vector(path), answered)))))

  /** The Visions Drawn track at `n`. */
  def visionsDrawn(n: Int): Table =
    update(_.updateCurrent(c => c.copy(tracks =
      c.tracks.copy(visionsDrawn = n))))

  /** `vision` as `player`'s revealed Vision, faceup, taken from wherever
    * it lay. */
  def revealedVision(player: PlayerId, vision: VisionId)(
      using munit.Location): Table =
    moving(vision).onPlayer(player)(_.copy(revealedVision =
      Some(VisionState(vision, Orientation.FaceUp))))

  /** The turn moved back to `phase`, keeping its active player and the
    * powers already used this turn (`turn` starts a fresh turn instead). */
  def backInPhase(phase: Phase): Table =
    update(_.updateCurrent(c => c.copy(turn = c.turn.copy(phase = phase))))
```

Replace `peoplesFavor` and `darkestSecret`:

```scala
  /** The People's Favor held by `holder` with `favor` on it, showing
    * `face`; the quiet table shows the Mob. */
  def peoplesFavor(holder: Option[PlayerId], favor: Int,
      face: PeoplesFavorFace = PeoplesFavorFace.Mob): Table =
    update(_.updateCurrent(c => c.copy(banners = c.banners.copy(peoplesFavor =
      c.banners.peoplesFavor.copy(active = face, holder = holder,
        favor = favor)))))

  /** The Darkest Secret held by `holder` with `secrets` on it, showing
    * `face`; the quiet table shows the Wandering Flame. */
  def darkestSecret(holder: Option[PlayerId], secrets: Int,
      face: DarkestSecretFace = DarkestSecretFace.WanderingFlame): Table =
    update(_.updateCurrent(c => c.copy(banners = c.banners.copy(darkestSecret =
      c.banners.darkestSecret.copy(active = face, holder = holder,
        secrets = secrets)))))
```

In the `Table` class doc, after "Nobody holds the Oathkeeper title or a banner", add: "The People's Favor shows the Mob and the Darkest Secret the Wandering Flame." Add imports for `ActionRef` and `VisionState` if the compiler asks.

- [ ] **Step 5: Run them to verify they pass**

Run: `./sbtw "root/testOnly oathdigital.testkit.TableSuite"`
Expected: PASS.

- [ ] **Step 6: Rename the testkit members and their callers**

1. `Table.warbands` and `Look.warbands` become `boardWarbands` (00-153, 00-162). Rename both definitions, then every call: `git grep -l '\.warbands(' -- src/test` lists the candidates; change only calls on a `Table` or a `Look` (the compiler confirms).
2. `Table.homeOf` becomes `quietPawnSite` (00-164). Update its doc to "Where the quiet table stands `player`'s pawn." Rename every use under `src/test` (`git grep -l homeOf -- src/test`).
3. `Look.denizens` becomes `cardsAt` (00-154), documented "The cards a site lists: its denizens and edifices." Rename the definition, compile, and fix each call the compiler reports.
4. `Situation.serviceRules` becomes `productionRules` (00-157), documented as the rules the service builds, with its power catalogs.

Run: `./sbtw root/Test/compile`
Expected: success.

- [ ] **Step 7: Gate**

Run the procedure's step 6 gate. The root total grows by 7 (the step tests), and nothing else changes. `frozen.py` lists only lines whose sole change is a rename (for example `assertEquals(look.warbands(p1), 2)` becoming `assertEquals(look.boardWarbands(p1), 2)`); list them in the report.

- [ ] **Step 8: Commit**

```bash
git add src/test
git commit -m "test: add the Table steps three suites need, and name testkit reads for what they read" -m "Ledger rows 00-153, 00-154, 00-157, 00-162, 00-164." -m "Co-Authored-By: <your model's trailer>"
```

- [ ] **Step 9: Record the outcomes** of rows 00-152 to 00-165 (procedure step 7). Keep rows (00-152, 00-155, 00-156, 00-158 to 00-161, 00-163) get `kept: <the row's reason>`; 00-165 is Keep with an R10 note: record `kept; doc line in Task 38`. This task has no mutations: it changes no rule a test guards. Write "none: no rule's tests were rewritten" in the report.

---

## Fix tasks 3 to 36

Each fix task below follows the procedure file end to end: worktree (step 3), fix its rows (step 4), mutations (step 5), gate (step 6), record (step 7), report (step 8). Its rows are every ledger row whose ID falls in its range, except R10 rows (Tasks 37 to 39). Its files are the files those rows name, and, for a task that owns shared fixtures, every caller of a helper it renames or inlines.

The Foundation added: `Unwrap.accepted`, `rejected`, `present`, `as[T]`; `Outcomes.ready`, `parked`, `finished`, `assertWireRoundTrips`; `Look.boardWarbands`, `cardsAt`, `discardAfter`, `forceKind`; `Table.relicDeckEmptied`, `parkedWalker`, `visionsDrawn`, `revealedVision`, `backInPhase`, and a `face` argument on `peoplesFavor` and `darkestSecret`; renames `Table.boardWarbands`, `Table.quietPawnSite`, `Situation.productionRules`.

Every fix task's steps:

- [ ] Step 1: Read the procedure, the spec's Rubric, the ledger's Review, and the task's rows.
- [ ] Step 2: Set up the worktree and record the base and the "before" totals (procedure step 3).
- [ ] Step 3: Fix the rows file by file, running each suite (procedure step 4).
- [ ] Step 4: Run 3 to 5 mutations and confirm each is caught (procedure step 5).
- [ ] Step 5: Run the gate, including `frozen.py` (procedure step 6), and commit the fixes.
- [ ] Step 6: Record each row's outcome and the mutations in the ledger, and commit (procedure step 7).
- [ ] Step 7: Write the report (procedure step 8).

The task entries give what differs: rows, files, owned fixtures, and notes.

### Task 3: Area 04 shared fixtures

**Rows:** 00-061 to 00-066 and 00-071 to 00-093 (NoteText, PowerFixture, TargetsFixture, MovementFixture, PaidActionHarness).

**Files:** `src/test/scala/oathdigital/gameplay/powers/NoteText.scala`, `PowerFixture.scala`, `TargetsFixture.scala`, `action/MovementFixture.scala`, `action/PaidActionHarness.scala` (all under `src/test/scala/oathdigital/gameplay/powers/`), and every caller of a helper these rows rename, inline or replace, in any area (`git grep -l -w <helper> -- src/test`).

**Notes:**
- 00-090: `wireRoundTrips` (74 callers) becomes `Outcomes.assertWireRoundTrips`. Replace `assert(wireRoundTrips(events))` with `assertWireRoundTrips(events)` at every caller and delete `wireRoundTrips`. This strengthens the assertion; `frozen.py` lists each such line.
- Do not touch the 04 suites' own rows here; Tasks 4 to 7 fix them.

### Task 4: Area 04, part 1

**Rows:** 04-001 to 04-056, and area-wide rows 04-196 to 04-203 applied to this part's files only.

**Files (16):** AlchemistSuite, AmberFlameSuite, ArcaneBrokersSuite, ArmedMobSuite, BallotBoxSuite, BanditPaymasterSuite, BarbedNetSuite, BedOfRootsSuite, BloodPactSuite, BogSuite, BoneDiceSuite, BookOfRecordsSuite, BrassHorseSuite, CharmingFriendSuite, ClayRattleSuite, CrystalVialSuite (in `src/test/scala/oathdigital/gameplay/powers/action/`).

**Notes:** R10 row 04-049 goes to Task 38. Leave the area-wide rows' Outcome cells empty; Task 7 fills them.

### Task 5: Area 04, part 2

**Rows:** 04-057 to 04-104, and the area-wide rows 04-196 to 04-203 applied to this part's files only.

**Files (16):** DarkEnforcerSuite, DemonTailSuite, DowsingSticksSuite, DreamThiefSuite, ElderSuite, EnchantressSuite, FaeMerchantSuite, FirebrandSuite, GamblingHallSuite, GreatFeastSuite, HonorGuardSuite, IvoryEyeSuite, LevelersSuite, MagicCarpetSuite, MagicWaterskinSuite, MemoryOfHomeSuite (in `src/test/scala/oathdigital/gameplay/powers/action/`).

**Notes:** 04-065 and 04-076 use `Table.relicDeckEmptied`. Leave the area-wide rows' Outcome cells empty.

### Task 6: Area 04, part 3

**Rows:** 04-105 to 04-157, and the area-wide rows 04-196 to 04-203 applied to this part's files only.

**Files (16):** MemoryOfNatureSuite, MessengerSuite, MurkyFountainSuite, OracleSuite, OracularPigSuite, PaidActionSuite, PlagueEnginesSuite, QuickExitSuite, RelicBreakerSuite, ScryerSuite, SecondChanceSuite, ShiftingMapSuite, SiegeEnginesSuite, SkeletonKeySuite, SleightOfHandSuite, SpiritSnareSuite (in `src/test/scala/oathdigital/gameplay/powers/action/`).

**Notes:** R10 row 04-122 goes to Task 38. Leave the area-wide rows' Outcome cells empty.

### Task 7: Area 04, part 4

**Rows:** 04-158 to 04-195, and the area-wide rows 04-196 to 04-203 applied to this part's files; then fill the area-wide rows' Outcome cells for all of area 04.

**Files (11):** SpoiledSuppliesSuite, StorytellerSuite, TamingCharmSuite, TavernSongsSuite, TinkersFairSuite, TutorSuite, WaysideInnSuite, WhisperingLeavesSuite, WhistleSuite, WizardSchoolSuite, WolvesSuite (in `src/test/scala/oathdigital/gameplay/powers/action/`).

**Notes:** 04-172 uses `Table.relicDeckEmptied`. Before filling the area-wide outcomes, confirm with `git grep` over `src/test/scala/oathdigital/gameplay/powers/action/` that each area-wide problem is gone from every suite (for example no `toOption.get` left for 04-196).

### Task 8: Area 06 shared fixtures

**Rows:** 00-060, 00-094 to 00-100, 00-109 to 00-117, 00-125 to 00-134, 00-145 to 00-147 (CardStaging, BannerFixture, LeagueTreatyFixture, SilverTongueFixture, TravelFixture, RiverFixture, SetupWalkDriver).

**Files:** those fixtures (`src/test/scala/oathdigital/gameplay/powers/CardStaging.scala`, `powers/banner/BannerFixture.scala`, `powers/rest/LeagueTreatyFixture.scala`, `powers/rest/SilverTongueFixture.scala`, `powers/travel/TravelFixture.scala`, `powers/wake/RiverFixture.scala`, `gameplay/setup/SetupWalkDriver.scala`) and every caller of a helper the rows inline or replace.

**Notes:** 00-097 replaces `holdingFlame` and `holdingFavor` with the `face` argument of `Table.darkestSecret` and `Table.peoplesFavor` at every caller (the callers in areas 01, 03 and 10 included), and deletes both helpers.

### Task 9: Area 06, part 1

**Rows:** 06-001 to 06-061.

**Files (10):** AdviserLimitSuite, RuledCardsSuite, SelectedModifierSuite, BannerFaceSourcesSuite, PeoplesFavorMobSuite, WanderingFlameMoveSuite, WanderingFlamePlaceSuite, RelicWorshipSuite, InsomniaSuite, LeagueTreatySuite.

**Notes:** R10 row 06-009 goes to Task 38.

### Task 10: Area 06, part 2

**Rows:** 06-062 to 06-123.

**Files (10):** SilverTongueSuite, AugurySuite, CropRotationSuite, DisciplesSuite, TruthfulHarpSuite, EdificeSetupSupportSuite, GreatForgeRulesSuite, GreatMarketRulesSuite, ProvingGroundsRulesSuite, ChaosCultSuite.

**Notes:** 06-084 is an R2 rename on a documented helper; it stays a rename (Review, decision 3). DisciplesSuite's Visions Drawn patch uses `Table.visionsDrawn`.

### Task 11: Area 06, part 3

**Rows:** 06-124 to 06-191.

**Files (11):** DragonskinDrumSuite, ForestPathsSuite, GraspingVinesSuite, RoyalStablesSuite, TentsSuite, TollRoadsSuite, HornedMaskSuite, HungerSuite, MarbleFountainsSuite, QuartermasterSuite, RiverSitePowerSuite.

**Notes:** 06-167, 06-181 and 06-184 use `Table.backInPhase(Phase.Wake)`; where the state is a `ReadyGame` mid-test, continue it with `Table.from(ready).backInPhase(Phase.Wake).unchecked`.

### Task 12: Area 10 shared fixtures

**Rows:** 00-043 to 00-056, 00-059, 00-138 to 00-144, 00-148 (EconomyFixture, PhasePowerFixture, PlacementFixture, SearchBudget, WalkerRecordedOpsReducer, FirstGameSetupFixture, ParkedDecisionAssertions).

**Files:** those fixtures, under `src/test/scala/oathdigital/gameplay/` (FirstGameSetupFixture in `gameplay/setup/`, ParkedDecisionAssertions in `gameplay/walker/`), and every caller of a helper the rows rename or reshape.

**Notes:**
- 00-141 and 00-142: `execute` becomes `setupThroughWake` returning a named `SetupResult(state, events)`; change every caller (the ledger counted six: GameEventWireSuite, MinorActionsSuite, ChallengeProcedureSuite, NegotiationProcedureSuite, WalkerReplayDriftSuite, EndWakeProcedureSuite; Task 1 added OutcomesSuite).
- 00-143: `initialReady` reads with `Outcomes.ready`.
- R10 rows 00-043, 00-044, 00-047 go to Task 38.

### Task 13: Area 10, part 1

**Rows:** 10-001 to 10-056.

**Files (9):** CardPlayHooksSuite, CardPlayProcedureSuite, CardPlayRestrictionsSuite, ChallengeProcedureSuite, EconomyWalkerSuite, EndWakeProcedureSuite, ForgeProcedureSuite, HomelandLineSuite, HomelandRuleSuite.

**Notes:** R10 rows 10-001, 10-025, 10-026 go to Task 38. CardPlayProcedureSuite's revealed-Vision patch uses `Table.revealedVision`.

### Task 14: Area 10, part 2

**Rows:** 10-057 to 10-122.

**Files (12):** MinorActionsSuite, MusterProcedureSuite, NegotiationProcedureSuite, PhasePowerSuite, PlaceBannerResourceProcedureSuite, PlacementRulesSuite, PlayerSecretSummarySuite, RecoverEligibilitySuite, RecoverProcedureSuite, RestSuite, SearchProcedureSuite, SearchSuite.

**Notes:** R10 rows 10-064, 10-114 go to Task 38. 10-121 uses `Table.visionsDrawn`; the Visions Drawn value is the test's subject, so the step replaces the patch and the comment the row mentions is not needed.

### Task 15: Area 10, part 3

**Rows:** 10-123 to 10-177.

**Files (12):** SiteDiscardFirstSuite, StateBasedEvaluationSuite, SupplyAdjustSuite, TakeWealthPowerSuite, TakeWealthProcedureSuite, TradeProcedureSuite, TravelProcedureSuite, TravelSitePowersSuite, VisionPlaySuite, WakeAutoEndSuite, CardCatalogSuite, OathkeeperProcedureSuite.

**Notes:** R10 row 10-162 goes to Task 38. 10-129, 10-167 and 10-169 use `Table.revealedVision` and `Table.visionsDrawn`.

### Task 16: Area 05, part 1, with its shared fixtures

**Rows:** 05-001 to 05-061; 00-067 to 00-070 (SearchFixture), 00-107 and 00-108 (VisionPlayFixture), 00-135 to 00-137 (WhenPlayedHarness).

**Files (10, plus the three fixtures and their callers):** BookBindersSuite, GossipSuite, SacredGroundSuite, SaddleMakersSuite, SecretPoliceSuite, VowOfObedienceSuite, WelcomingPartySuite, WildCrySuite, AnimalPlaymatesSuite, BirdsongSuite.

**Notes:** WhenPlayedHarness's `parked` and `finished` (00-136) give way to `Outcomes.parked` and `Outcomes.finished` at every caller.

### Task 17: Area 05, part 2

**Rows:** 05-062 to 05-118.

**Files (12):** CupOfPlentySuite, DowntroddenSuite, InitiationRiteSuite, KnightsErrantSuite, RowdyPubSuite, TheOldOakSuite, VillageIdiotSuite, ASmallFavorSuite, AnimalHostSuite, BanditPrinceSuite, ConspiracyTargetWindowSuite, ConspiracyWhenPlayedSuite.

**Notes:** R10 row 05-109 goes to Task 38. 05-080's `chooseOne` is `Unwrap.as[DecisionQuery.ChooseOne]`; 05-099's `forceKind` is `Look.forceKind`.

### Task 18: Area 05, part 3

**Rows:** 05-119 to 05-163.

**Files (15):** DazzleSuite, FabledFeastSuite, FaithfulFriendSuite, FamilyHeirloomSuite, GarrisonSuite, GreatHerdSuite, KeyToTheCitySuite, RiotsSuite, RoyalTaxSuite, SaladDaysSuite, ShiftingFogSuite, ThreateningRoarSuite, TownMeetingSuite, TwinBrotherSuite, WhenPlayedDecisionAfterPlaySuite.

### Task 19: Area 07 shared fixtures

**Rows:** 00-032 to 00-042 (CampaignFixture), 00-101 to 00-106 (PlanDriver), 00-118 to 00-124 (TargetingFixture).

**Files:** `src/test/scala/oathdigital/gameplay/CampaignFixture.scala`, `gameplay/powers/campaign/PlanDriver.scala`, `gameplay/powers/targeting/TargetingFixture.scala`, and every caller of a helper the rows reshape (CampaignFixture has 57 callers).

**Notes:**
- 00-040: rename `againstPlayer` to `rivalRulesOriginAndHoldsOathkeeper` at every caller (Review, decision 1).
- 00-033: `board`'s `warbands` and `supply` become required arguments, so each caller states them.
- R10 row 00-101 goes to Task 38.

### Task 20: Area 07, part 1

**Rows:** 07-001 to 07-064.

**Files (10):** CampaignBattleSuite, CampaignPlanWindowSuite, CampaignPlansSuite, CampaignPowersSuite, CampaignProcedureSuite, CampaignRaidSuite, CampaignSetupSuite, CatacombsContributionSuite, BagOfSiegeworksSuite, BanditStandardSuite.

**Notes:** R10 rows 07-004, 07-005, 07-017, 07-052, 07-057 go to Task 38. 07-058 is an R2 rename on a documented helper; it stays a rename.

### Task 21: Area 07, part 2

**Rows:** 07-065 to 07-096.

**Files (16):** BattleAxesSuite, BattleHonorsSuite, BlackSwordSuite, BookBurningSuite, CrackedSageSuite, CrackingGroundSuite, DisgracedCaptainSuite, EncirclementSuite, FearsomeShieldSuite, FieldPromotionSuite, FireTalkersSuite, GarrisonArmorySuite, GleamingArmorSuite, GreatCrusadeSuite, GreatLevySuite, HospitalSuite.

**Notes:** R10 rows 07-086, 07-092 go to Task 38. 07-072 is an R2 rename on a documented helper; it stays a rename.

### Task 22: Area 07, part 3

**Rows:** 07-097 to 07-134.

**Files (16):** InsectSwarmSuite, MercenariesSuite, MilitaryParadeSuite, NatureWorshipSuite, PledgeOfDefenseSuite, RainBootsSuite, RampartSuite, RivalKhanSuite, StickyFireSuite, TributeSpoilsSuite, VillageConstableSuite, VowOfPeaceSuite, WalledGardenSuite, WarningSignalsSuite, WrestlersSuite, CircletOfCommandSuite.

**Notes:** R10 row 07-109 goes to Task 38. 07-118 is an R2 rename on a documented helper; it stays a rename.

### Task 23: Area 07, part 4

**Rows:** 07-135 to 07-160.

**Files (5):** ForgottenVaultSuite, FortressRulesSuite, LostTongueSuite, BannerBreakersSuite, HorseArchersSuite.

### Task 24: Area 03, part 1, with its shared fixtures

**Rows:** 03-001 to 03-051; 00-057 and 00-058 (WalkerDiceFixture), 00-149 (ReadyGames), 00-150 and 00-151 (TestGameFixtures), and 08-020 (the duplicate of 00-150; record it `fixed: see 00-150`).

**Files (16, plus the three fixtures and their callers):** AutomaticRollSuite, BackendArchitectureSuite, CoOwnedDecideSuite, ContributionCollectorSuite, ContributionIgnoresSuite, CoreOperationsSuite, EnclosingProcedureSuite, GlobalRestrictionsWalkerSuite, LazyPruningSuite, OathRulesEvolveSuite, OathRulesWalkerPowerSuite, OfferHostSuite, OperationApplicationSuite, OperationExecutorSuite, OperationPipelineSuite, OperationResolutionSuite.

**Notes:** R10 rows 03-001, 03-006, 03-007, 03-009, 03-014, 03-015, 03-020, 03-030, 03-035, 03-041, 03-048 go to Task 38.

### Task 25: Area 03, part 2

**Rows:** 03-052 to 03-102.

**Files (16):** OperationRestrictionsSuite, OperationStateAdapterSuite, OperationVocabularySuite, OptionRestrictionSuite, PayCostSettlementSuite, PayCostSuite, PendingWalkerRulesSuite, PowerAccessSuite, PowerNoteHideSuite, PowerNoteWalkerSuite, PowerOperationsSuite, PowerResolverSuite, ProcedureWalkerSuite, RepeatPassSuite, RestrictionAnswersSuite, RestrictionLookAheadSuite.

**Notes:**
- 03-089 (R11): split `ProcedureWalkerSuite` (1022 lines) into the four suites the row names, each under 800 lines; its helpers move to the testkit (03-090). The test total is unchanged; record the four files and their counts.
- R10 rows 03-056, 03-063, 03-078, 03-086, 03-091, 03-092, 03-099, 03-102 go to Task 38.

### Task 26: Area 03, part 3

**Rows:** 03-103 to 03-143.

**Files (14):** RuleResolutionSuite, RuleSourceIndexSuite, SelectionPaymentsSuite, WalkerReplayDriftSuite, WalkerReplaySettlementSuite, ModifyRollOutcomeSuite, OperationMutationSuite, RecordCampaignResultSuite, ShuffleOperationSuite, InspectQuerySuite, ParkedDecisionAssertionsSuite, WalkerPreviewSuite, WalkerReplayGuardSuite, WalkerSearchSuite.

**Notes:** R10 row 03-112 goes to Task 38. 03-109 uses the banner `face` argument.

### Task 27: Area 01, part 1, with its shared fixtures

**Rows:** 01-001 to 01-023; 00-001 to 00-015 (ForgeWalkerFixture, ParkedServiceFixture).

**Files (6, plus the two fixtures and their callers):** BannerFaceProjectionSuite, CardKnowledgeSuite, ChronicleShuffleSuite, DicePowerDecisionProjectionSuite, EconomyProjectionSuite, FirstGameChronicleGeneratorSuite.

**Notes:**
- 00-012: the park helpers return the named results the row lists; update every caller (areas 01, 02 and 06).
- 01-001 uses the banner `face` argument.
- R10 rows 00-014, 01-009, 01-019 go to Task 38.

### Task 28: Area 01, part 2: GameApplicationServiceSuite

**Rows:** 01-024 to 01-065.

**Files:** `src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala` (1829 lines, 39 `@unchecked`).

**Notes:**
- 01-024 (R11): split into the six suites the row names (Recover, Forge, Actions, Stream, Projection, Reopen), each under 800 lines, sharing `execute`, `replayingSecond` and `parkedAssertions` through ParkedServiceFixture. The test total is unchanged; record the six files and their counts.
- Do the split first, in its own commit with no other change, so the review can check it moved tests without editing them; then fix the rows.
- R10 rows 01-029, 01-038, 01-049, 01-057 go to Task 38.

### Task 29: Area 01, part 3

**Rows:** 01-066 to 01-127.

**Files (20):** GameHistorySuite, GamePresentationProjectorAdviserRedactionSuite, GamePresentationProjectorDiscardSuite, GamePresentationProjectorPrintedFacesSuite, GameServerGatewaySubmitBeginSuite, GeneratedFirstGamePlanFactorySuite, ImplementedCardCatalogSuite, MembershipAuthorizationServiceSuite, NegotiationDealProjectionSuite, PendingWalkerInvariantSuite, PhasePowerProjectorSuite, PresentationLabelsSuite, PricedOptionProjectionSuite, SitePowerTextSuite, SupplyProjectionSuite, WalkerDecisionProjectionSuite, WalkerDecisionProjectorSuite, WalkerDecisionQueryPowerSuite, WalkerDiceAdapterSuite, EventReplayEngineSuite.

**Notes:** 01-099 and 01-113 use `Table.parkedWalker` (L667's recorded `Answered` passes as `answered`). R10 rows 01-071, 01-078, 01-088, 01-104, 01-126, 01-127 go to Task 38.

### Task 30: Area 09, part 1

**Rows:** 09-001 to 09-056.

**Files (16):** GameStartRulesSuite, GameStartToWakeSuite, SetupProcedureSuite, HsqldbDatabaseOwnerSuite, HsqldbEventStreamRepositorySuite, HsqldbIdentityRepositorySuite, AuthenticatedGameBootstrapRoutesSuite, AuthenticatedGameRoutesSuite, CommandRejectionMessageSuite, DesktopConsoleSuite, DesktopLaunchProfileSuite, DevelopmentIdentityShimSuite, DevelopmentTrustBoundarySuite, GameHttpWireSuite, GameRoutesSuite, GameTrustBoundaryRoutesSuite.

**Notes:** R10 rows 09-028, 09-034, 09-055 go to Task 38. Task 37 later moves the shared behavior tests out of HsqldbEventStreamRepositorySuite; fix its rows here as they stand.

### Task 31: Area 09, part 2

**Rows:** 09-057 to 09-091.

**Files (8):** HealthRoutesSuite, SeatCodeSuite, ServerConfigSuite, ServerRoutesSuite, ServerRuntimeSuite, SessionSecuritySuite, TrustedGameProvisioningSuite, TrustedSeatRoutesSuite.

**Notes:** R10 rows 09-072, 09-080, 09-081 go to Task 38.

### Task 32: Area 02 shared fixtures

**Rows:** 00-016 to 00-031 (GoldenLog, LogScripts).

**Files:** `src/test/scala/oathdigital/application/gamelog/GoldenLog.scala`, `LogScripts.scala`, and every caller of a helper the rows rename or replace.

**Notes:**
- 00-020 renames `board` to `recordedEventsBoard`; 00-023 `name` to `playerLabel`; 00-026 `usePower` to `silverTongue`; 00-027 `oathkeeper` to `oathkeeperTie`.
- 00-025 and 00-028 build on the ParkedServiceFixture results Task 27 named; read them as they now stand.
- Golden-log contents are out of scope: no golden file changes. If a golden file differs after a rewrite, the rewrite changed the play; undo it.
- R10 row 00-018 goes to Task 38.

### Task 33: Area 02

**Rows:** 02-001 to 02-065.

**Files (16):** GameLogActionLineSuite, GameLogCampaignSuite, GameLogDecisionSuite, GameLogDetailSuite, GameLogEventSuite, GameLogExchangeSuite, GameLogHeadlineSuite, GameLogPowerLinesSuite, GameLogPropertiesSuite, GameLogRareLinesSuite, GameLogRouteSuite, GameLogSetupSuite, GameLogStartLineSuite, LogWordsSuite, SituationSuite, TableSuite.

**Notes:** R10 row 02-053 goes to Task 38. Golden files do not change.

### Task 34: Area 11, with TestBrowser

**Rows:** 11-001 to 11-057; 00-167 and 00-168 (TestBrowser).

**Files (20, in `frontend/src/test/scala/oathdigital/frontend/`, plus `TestBrowser.scala`):** ActionOptionRowSuite, ActionPanePromptSuite, BannerControlsSuite, BoardSurfaceSuite, BoardTargetSelectionStateSuite, CampaignControlsSuite, CardFaceSuite, CardInspectionOverlaySuite, DieFaceSuite, DiscardPileSuite, DistributeDecisionStateSuite, DistributePanelRenderSuite, EconomyControlsSuite, GameLogOverlaySuite, GameLogPaneSuite, HttpGameClientSuite, LogMarkerSuite, MapViewStateSuite, ModifierFlowSuite, ModifierSelectionStateSuite.

**Notes:**
- Frontend suites use frontend builders, never the root `Table`. The accessors are `oathdigital.testkit.Unwrap` (11-014's `updated` is `as[BoardSelectionResult.Updated]`; 11-037's `right` is `accepted`).
- Mutations break rules in `frontend/src/main`.
- R10 rows 11-011, 11-031, 11-040, 11-041, 11-047 go to Task 39.

### Task 35: Area 12

**Rows:** 12-001 to 12-050.

**Files (18, in `frontend/src/test/scala/oathdigital/frontend/`):** NegotiationControlsSuite, NegotiationDealPanelSuite, PanelPlacementSuite, PartitionDecisionStateSuite, PartitionPanelRenderSuite, PlayerBoardSuite, RecoverPanelSuite, RulesTextRendererSuite, ServerModeUiSuite, SessionDraftsSuite, SiteFaceSuite, SnapshotPollingCoordinatorSuite, SuitGlyphSuite, TableSessionSuite, TargetSelectionExitSuite, WalkerChoicePanelRenderSuite, WalkerSelectionDraftSuite, WalkerSelectionPanelsSuite.

**Notes:**
- 12-025 (R11): split `ServerModeUiSuite` into the six suites the row lists, each under 800 lines. Split first, in its own commit; then fix the rows.
- 12-049's `chooseMany` and `chooseAmount` are `as[WalkerChooseManyDraft]` (and the amount draft's class) over `present(reconcile(...), "a draft")`.
- R10 rows 12-026, 12-042, 12-044 go to Task 39.

### Task 36: Area 08

**Rows:** every 08 row (08-002 to 08-049), except 08-020 (Task 24).

**Files (18):** ActionValuesSuite, CardIndexSuite, DecisionOptionRefSuite, DomainValidationSuite, OpaqueIdSuite, PlayerForceKindSuite, PlayerSetupStateSuite, ProcedureRefSuite, ReadyGameSuite, CampaignResultCodecSuite, GameEventWireSuite, PayCostWireSuite, ExecutableCatalogSuite (root); CommandProtocolSuite, LogPageCodecSuite, OathkeeperPresentationSuite, ProjectionProtocolSuite, TrustedGameProtocolSuite (`shared/src/test/scala/oathdigital/protocol/`).

**Notes:**
- 08-024 (R11): split `GameEventWireSuite` (931 lines) into the five suites the row names. Split first, in its own commit; then fix the rows.
- Numeric ids stay where the id is the subject (R5 exemption for wire and catalog suites); record such rows `kept: the id is the subject`.
- The shared protocol suites use `oathdigital.testkit.Unwrap` (08-037 and 08-045's `refusal` is `rejected`); they must still compile for Scala.js, so the gate's frontend run matters here.
- R10 row 08-032 goes to Task 38.

---

### Task 37: Contract suite for EventStreamRepository

**Files:**
- Create: `src/test/scala/oathdigital/application/EventStreamRepositoryContract.scala`
- Create: `src/test/scala/oathdigital/application/InMemoryEventStreamRepositorySuite.scala`
- Modify: `src/test/scala/oathdigital/persistence/HsqldbEventStreamRepositorySuite.scala`
- Modify: the ledger ("Test doubles and contracts" and the Result notes below)

**Interfaces:**
- Produces: `abstract class EventStreamRepositoryContract[R <: EventStreamRepository] extends munit.FunSuite` with `protected def make(): R` and `protected def release(repository: R): Unit = ()`.

- [ ] **Step 1: Set up the worktree** (procedure step 3, task name `contract-repository`) and record the "before" totals.

- [ ] **Step 2: Write the contract**

`src/test/scala/oathdigital/application/EventStreamRepositoryContract.scala`:

```scala
package oathdigital.application

import java.util.concurrent.CountDownLatch

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

import oathdigital.testkit.Unwrap.*

/** What every `EventStreamRepository` promises (the trait's doc): one stream
  * per game, appended atomically, with the expected position compared in
  * the same step. The in-memory repository every service test uses and the
  * Hsqldb store both run it, so the fake cannot drift from the real one. */
abstract class EventStreamRepositoryContract[R <: EventStreamRepository]
    extends munit.FunSuite:
  /** A new, empty repository. */
  protected def make(): R
  /** Releases what `make` opened. */
  protected def release(repository: R): Unit = ()

  private given ExecutionContext = ExecutionContext.global

  private val fresh = FunFixture[R](_ => make(), release)

  private def stream(repository: R, gameId: String): StoredEventStream =
    present(accepted(repository.load(gameId)), s"a stream for $gameId")

  fresh.test("an unknown game loads no stream"): repository =>
    assertEquals(repository.load("nobody"), Right(None))

  fresh.test("creates a stream and loads its records in order"): repository =>
    assertEquals(repository.append("game", ExpectedStream.MustNotExist,
      Vector("first", "second")), Right(RepositoryAppendResult.Appended(0L, 2)))
    val loaded = stream(repository, "game")
    assertEquals(loaded.gameId, "game")
    assertEquals(loaded.records, Vector("first", "second"))
    assertEquals(loaded.nextSequence, 2L)

  fresh.test("creating a stream that exists, or appending to one that does not, is refused"):
    repository =>
      repository.append("game", ExpectedStream.MustNotExist, Vector("zero"))
      assertEquals(repository.append("game", ExpectedStream.MustNotExist,
        Vector("again")), Right(RepositoryAppendResult.StreamAlreadyExists))
      assertEquals(repository.append("missing", ExpectedStream.AtNextSequence(0L),
        Vector("one")), Right(RepositoryAppendResult.StreamNotFound))
      assertEquals(stream(repository, "game").records, Vector("zero"))

  fresh.test("appends a batch at the next sequence, in order"): repository =>
    repository.append("game", ExpectedStream.MustNotExist, Vector("zero"))
    assertEquals(repository.append("game", ExpectedStream.AtNextSequence(1L),
      Vector("one", "two", "three")), Right(RepositoryAppendResult.Appended(1L, 3)))
    assertEquals(stream(repository, "game").records,
      Vector("zero", "one", "two", "three"))

  fresh.test("a sequence conflict writes no part of the batch"): repository =>
    repository.append("game", ExpectedStream.MustNotExist, Vector("zero"))
    assertEquals(repository.append("game", ExpectedStream.AtNextSequence(0L),
      Vector("stale-one", "stale-two")),
      Right(RepositoryAppendResult.SequenceConflict(0L, 1L)))
    assertEquals(stream(repository, "game").records, Vector("zero"))

  fresh.test("two racing creations have one winner and no partial stream"):
    repository =>
      val start = new CountDownLatch(1)
      val attempts = Vector("left", "right").map { record =>
        Future:
          start.await()
          repository.append("game", ExpectedStream.MustNotExist, Vector(record))
      }
      start.countDown()
      val results = Await.result(Future.sequence(attempts), 20.seconds)
      assertEquals(results.count(_.exists(
        _.isInstanceOf[RepositoryAppendResult.Appended])), 1)
      assertEquals(results.count(_ ==
        Right(RepositoryAppendResult.StreamAlreadyExists)), 1)
      assertEquals(stream(repository, "game").records.size, 1)

  fresh.test("two racing appends at one position have one winner and one conflict"):
    repository =>
      repository.append("game", ExpectedStream.MustNotExist, Vector("zero"))
      val start = new CountDownLatch(1)
      val attempts = Vector("left", "right").map { record =>
        Future:
          start.await()
          repository.append("game", ExpectedStream.AtNextSequence(1L),
            Vector(record))
      }
      start.countDown()
      val results = Await.result(Future.sequence(attempts), 20.seconds)
      assertEquals(results.count(_.exists(
        _.isInstanceOf[RepositoryAppendResult.Appended])), 1)
      assertEquals(results.count(_ ==
        Right(RepositoryAppendResult.SequenceConflict(1L, 2L))), 1)
      assertEquals(stream(repository, "game").records.size, 2)
```

`Await.result` bridges a real thread race, with a generous bound; the scala-testing skill allows it for that.

- [ ] **Step 3: Run the contract against the fake**

`src/test/scala/oathdigital/application/InMemoryEventStreamRepositorySuite.scala`:

```scala
package oathdigital.application

/** The in-memory repository, the fake every service test uses, held to
  * the repository contract. */
class InMemoryEventStreamRepositorySuite
    extends EventStreamRepositoryContract[InMemoryEventStreamRepository]:
  protected def make() = new InMemoryEventStreamRepository
```

Run: `./sbtw "root/testOnly oathdigital.application.InMemoryEventStreamRepositorySuite"`
Expected: PASS, 7 tests. A failure is a divergence of the fake: fix `InMemoryEventStreamRepository` (production, `src/main/scala/oathdigital/application/EventStreamRepository.scala`) to match what the Hsqldb store does, and record the divergence in the ledger's "Test doubles and contracts" section.

- [ ] **Step 4: Run the contract against the real store**

In `HsqldbEventStreamRepositorySuite`, extend the contract and delete the five tests it now covers ("creates a stream and loads exact records in sequence order", "appends an ordered multi-event batch atomically", "sequence conflict writes no part of a proposed batch", "concurrent creation race has one winner and no partial stream", "concurrent same-position appends have one winner and one conflict"). Keep the store's own tests (schema, URL delimiters, rollback on a database failure, close and reopen). Their assertions all live on in the contract, which asserts the same values; list each deleted test beside the contract test that carries its assertions in the report.

```scala
class HsqldbEventStreamRepositorySuite
    extends EventStreamRepositoryContract[OwnedHsqldbEventStreamRepository]:
  protected def make() = open(databasePath("contract"))
  override protected def release(repository: OwnedHsqldbEventStreamRepository)
      : Unit = repository.close()
```

Keep its `given ExecutionContext` only if a remaining test needs it.

Run: `./sbtw "root/testOnly oathdigital.persistence.HsqldbEventStreamRepositorySuite"`
Expected: PASS, 7 contract tests and the store's 5 own tests.

- [ ] **Step 5: Mutations.** Break the fake three ways, one at a time, and confirm the contract catches each in the in-memory suite: (1) `SequenceConflict` branch appends anyway; (2) `MustNotExist` on an existing stream returns `Appended`; (3) `load` returns the records reversed. Restore and record each (procedure steps 5 and 7, task name `contract-repository`).

- [ ] **Step 6: Gate** (procedure step 6). The root total grows by 9: the in-memory suite adds 7, and the Hsqldb suite trades its 5 moved tests for the 7 contract tests (the contract adds "an unknown game loads no stream" and splits the old create test's refusals into their own test). Write the before and after totals and this arithmetic in the report. `frozen.py` lists the deleted Hsqldb assertions; each maps to a contract assertion.

- [ ] **Step 7: Commit**

```bash
git add src/test/scala/oathdigital/application/EventStreamRepositoryContract.scala src/test/scala/oathdigital/application/InMemoryEventStreamRepositorySuite.scala src/test/scala/oathdigital/persistence/HsqldbEventStreamRepositorySuite.scala
git commit -m "test: hold the in-memory event stream repository to the Hsqldb store's contract" -m "Co-Authored-By: <your model's trailer>"
```

If Step 3 changed the fake, add `src/main/scala/oathdigital/application/EventStreamRepository.scala` to that commit and say so in its message.

- [ ] **Step 8: Record.** In the ledger's "Test doubles and contracts" section, end the `InMemoryEventStreamRepository` entry with "Done in <commit>: both run `EventStreamRepositoryContract`." Append the mutations. Commit the ledger. Do not edit `docs/ROADMAP.md`: the report tells the controller the ROADMAP item "Contract suite for `EventStreamRepository`" is done, and the controller tells the user, who edits the ROADMAP.

### Task 38: Root test doubles

**Rows:** every R10 row with an ID in 00 (except 00-166) or 01 to 10: 00-014, 00-018, 00-043, 00-044, 00-047, 00-101, 00-165 (its doc line), 01-009, 01-019, 01-029, 01-038, 01-049, 01-057, 01-071, 01-078, 01-088, 01-104, 01-126, 01-127, 02-053, 03-001, 03-006, 03-007, 03-009, 03-014, 03-015, 03-020, 03-030, 03-035, 03-041, 03-048, 03-056, 03-063, 03-078, 03-086, 03-091, 03-092, 03-099, 03-102, 03-112, 04-049, 04-122, 05-109, 06-009, 07-004, 07-005, 07-017, 07-052, 07-057, 07-086, 07-092, 07-109, 08-032, 09-028, 09-034, 09-055, 09-072, 09-080, 09-081, 10-001, 10-025, 10-026, 10-064, 10-114, 10-162. Before starting, confirm with the ledger that this list equals every root R10 row (a `grep` of rows whose Rule cell is `R10`); add any missed row.

**Files:** the files those rows name; new `src/test/scala/oathdigital/testkit/Dice.scala`; new `src/test/scala/oathdigital/engine/EventEvolutionContract.scala`.

**Notes:**
- Follow each row's planned outcome: most are a doc line naming the kind of double ("Stub of …", "Spy of …", "Fake of …") and the trait. Mocks that hide an interaction are replaced by the real component or the fake; a spy over a fake stays (spec).
- 00-014, 00-018 and the other CampaignDicePort stubs (01-029, 01-104, 01-126) move into one testkit object `oathdigital.testkit.Dice` holding named stubs (`blank`, `steady`, `raid`, …), each with a doc line saying what it rolls; every caller uses it.
- 01-127: write `EventEvolutionContract[S, E, V]` with `protected def evolution: EventEvolution[S, E, V]`, `protected def events: Vector[E]` (a valid sequence from the initial state) and `protected def corrupt: E` (an event the initial state refuses). Its tests: the initial state reads the same twice; evolving `events` twice from the initial state gives equal results; `evolve(initialState, corrupt)` is a Left. Run it against `EventReplayEngineSuite`'s `Totals` (moved to a testkit object so both suites see it; events `2, 3, 5`, corrupt `-1`) and against `OathRules` (`new OathRules(FirstGameSetupFixture.catalog)`, events from `FirstGameSetupFixture.setupThroughWake().events`, corrupt the last of those events, which `NoGame` must refuse; if it does not, use the first event after `GameStarted`).
- No mutations beyond the contract's: break `Totals` to accept a negative event and `OathRules.evolve` to accept an event on `NoGame`, one at a time; confirm the contract catches each.
- Gate, record and commit as the procedure says (task name `doubles-root`).

### Task 39: Frontend test doubles

**Rows:** 00-166, and every R10 row with an ID in 11 or 12: 11-011, 11-031, 11-040, 11-041, 11-047, 12-026, 12-042, 12-044. Confirm the list against the ledger first.

**Files:** the files those rows name; new `frontend/src/test/scala/oathdigital/frontend/FakeLogStore.scala`, `LogStoreContract.scala`, `FakePollClock.scala`, `PollClockContract.scala` (all in `frontend/src/test/scala/oathdigital/frontend/`).

**Notes:**
- `FakeLogStore`: one shared fake replacing the `FakeStore` classes in GameLogOverlaySuite and LogMarkerSuite (11-031, 11-041). `LogStoreContract`: a written key reads back its value; a second write replaces it; an unknown key reads `None`. Run it against `FakeLogStore` and against `LogStore.browser.get` over a working `localStorage`: in that suite, install a plain JavaScript object with `getItem` and `setItem` as `window.localStorage` (`Object.defineProperty`, configurable) before the test and remove it after. jsdom's own storage throws for this harness, which is why the trait exists.
- `FakePollClock`: one shared fake with the union of what `SnapshotPollingCoordinatorSuite.FakePollClock` and `TableSessionSuite.ManualClock` offer (the delays asked for, the pending tasks, firing the next task). `PollClockContract`: a scheduled task runs once when its time comes; a cancelled task never runs. The fake's "time comes" is its fire method; `BrowserPollClock`'s is a real wait (a `Future` completed by `dom.window.setTimeout` after the delay, with delays of a few milliseconds). Return the `Future` from the munit test.
- `FakeClient` (12-044) and `RecordingNavigation` stay spies: doc lines only. 00-166: doc line on RecordingControls per its row.
- Mutations: break `FakeLogStore` to drop writes, `FakePollClock` to run a cancelled task, and `BrowserPollClock.cancel` to do nothing; confirm the contract catches each.
- Gate, record and commit as the procedure says (task name `doubles-frontend`).

### Task 40: Guards

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/BackendArchitectureSuite.scala`

- [ ] **Step 1: Set up the worktree** (task name `guards`).

- [ ] **Step 2: Write the line-cap guard** after "all production Scala files stay bounded":

```scala
  test("all test Scala files stay bounded"):
    val roots = Vector(Paths.get("src/test/scala"),
      Paths.get("frontend/src/test/scala"), Paths.get("shared/src/test/scala"))
    val oversized = roots.flatMap(root => Files.walk(root).iterator.asScala)
      .filter(_.toString.endsWith(".scala")).flatMap { path =>
        val lines = Files.readAllLines(path).size
        Option.when(lines > 800)(s"$path:$lines")
      }.sorted
    assertEquals(oversized, Vector.empty)
```

- [ ] **Step 3: Write the numeric-id guard**

```scala
  test("tests name cards, not numeric ids, outside the wire and catalog suites"):
    // A numeric id hides which card a test means; CatalogNames names it. The
    // exempt suites test the ids themselves (spec, rubric R5).
    val exempt = Set(
      "src/test/scala/oathdigital/catalog/ExecutableCatalogSuite.scala")
    val numericId =
      "(Denizen|Relic|Edifice|Vision|Card|Site|WorldCard)Id\\(\"[0-9]+\"\\)".r
    val roots = Vector(Paths.get("src/test/scala"),
      Paths.get("frontend/src/test/scala"), Paths.get("shared/src/test/scala"))
    val offenders = roots.flatMap(root => Files.walk(root).iterator.asScala)
      .filter(_.toString.endsWith(".scala"))
      .filterNot(path => exempt(path.toString))
      .filter(path => numericId.findFirstIn(Files.readString(path)).nonEmpty)
      .map(_.toString).sorted
    assertEquals(offenders, Vector.empty)
```

- [ ] **Step 4: Run them**

Run: `./sbtw "root/testOnly oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS. If the numeric-id guard lists a file, decide per file: a wire or catalog suite whose rows were kept as "the id is the subject" joins `exempt` (one line each); any other file is a missed R5 fix, made here with `CatalogNames`, and recorded in the ledger against its row. The exempt list must name only suites the ledger kept as exempt.

- [ ] **Step 5: Mutation.** Add a 900-line dummy test file and a `DenizenId("93")` to a power suite, one at a time; confirm each guard fails naming it; remove both.

- [ ] **Step 6: Gate, commit and record** (task name `guards`): root total grows by 2.

```bash
git add src/test/scala/oathdigital/gameplay/BackendArchitectureSuite.scala
git commit -m "test: guard test file size and numeric card ids in tests" -m "Co-Authored-By: <your model's trailer>"
```

### Task 41: Result

**Files:**
- Modify: `docs/superpowers/specs/2026-10-02-test-readability-ledger.md` (Status, a "## Result" section)
- Modify: `docs/superpowers/specs/2026-10-02-test-readability-design.md` (a "## Result" section; Status line)

- [ ] **Step 1: Set up the worktree** (task name `result`).

- [ ] **Step 2: Every row has an outcome.** List rows with an empty Outcome cell:

```bash
python3 - <<'EOF'
import re
for line in open("docs/superpowers/specs/2026-10-02-test-readability-ledger.md"):
    if re.match(r"\| \d\d-\d+ \|", line):
        cells = [c.strip() for c in line.split("|")]
        if len(cells) > 8 and not cells[7]:
            print(cells[1])
EOF
```

Expected: no output. Any row listed goes back to the controller as a finding: name the task that owned it.

- [ ] **Step 3: Metrics.** Run `sh /Users/roman/projects/oathdigital/.superpowers/readability/metrics.sh`. Expected: `@unchecked: 0`, `numeric card ids:` only in the exempt suites, `test files over 800 lines: 0`. Every remaining state patch (`git grep -n -E 'updateCurrent|copy\(current *=' -- src/test`) is the test's subject or has a comment saying why no step reaches the state: list any that is neither as a finding.

- [ ] **Step 4: Brittleness re-check.** In `src/main/scala/oathdigital/gameplay/OathRules.scala`, make `wakeOptionOpen` always return true (back the file up first). Run `./sbtw root/test 2>&1 | grep -E 'Passed: Total|Failed: Total'`. Expected: at most 21 failures. Restore the file and confirm `git status --short src/main` prints nothing.

- [ ] **Step 5: Write the Result.** In the ledger, set Status to "Status: fixed; see Result." and add a "## Result" section after Status: the metrics before (Baseline) and after, rows fixed, kept and struck (none were struck), splits and their test counts, contract suites and any fake divergence found, mutations run and caught (count from the Mutations table), the brittleness count, and the root and frontend test totals before Plan 2 and after. In the spec, set the Status line to "Status: done." and add a short "## Result" section pointing at the ledger's Result with the headline numbers.

- [ ] **Step 6: Commit and report**

```bash
git add docs/superpowers/specs/2026-10-02-test-readability-ledger.md docs/superpowers/specs/2026-10-02-test-readability-design.md
git commit -m "docs: record the test readability sweep's result" -m "Co-Authored-By: <your model's trailer>"
```

The report reminds the controller to tell the user the ROADMAP item for the `EventStreamRepository` contract is done, for the user to strike.
