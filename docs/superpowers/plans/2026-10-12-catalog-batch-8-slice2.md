# Catalog Batch 8, Slice 2 (Oathkeeper Goal) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the two cards that change the Oathkeeper goal: True Oath (209) and The Red Seer (244).

**Architecture:**

- **A Set Oathkeeper Goal operation.** `SetOathkeeperGoal(goal)` writes the campaign state's `oathkeeperGoal`, beside `SetOathkeeper`. Applying it rejects an unchanged goal, as `SetOathkeeper` rejects an unchanged holder. The codec records it and replay applies it.
- **One goal question.** A helper in `gameplay/powers`, `OathkeeperGoalChoice`, builds the `ChooseOne` of the four goals, reads the answer, and writes the line. Both cards use it.
- **The title follows by itself.** `OathkeeperRules` reads the goal at every action boundary, so a changed goal can pass the title when the action ends. No title code changes.
- **The Red Seer** is a `PaidAction`. The engine pays its printed cost. Its `usable` checks that the user holds the Darkest Secret.
- **True Oath** is a `WhenPlayedPower`. It counts the other Beast cards its player rules with `RuledCards.of`. With three or more it asks, and the player may keep the current goal.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`: "Goal and scope", "The bar" (the Set Oathkeeper Goal bullet), "Catalog and roadmap corrections", "Slice 2: Oathkeeper goal" and "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: "Rules that apply to the whole batch" and the two slice 2 entries.

Task 1 amends both with this plan's rulings. Read them again after Task 1.

**Starts from:** `main` at `335150ef` or later. Slice 1 is merged.

Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it.
  - Each new file below lists the imports it needs.
  - When a card moves out of a holding catalog file, remove any import that file no longer uses.
  - A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker sources.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Names, exactly:**
  - `oathdigital.model.SetOathkeeperGoal(goal: OathkeeperGoal)`, a `PrimitiveOperation`. Wire kind `set-oathkeeper-goal`, field `goal` holding `OathkeeperGoal.key`.
  - `OperationError.OathkeeperGoalUnchanged(goal)`, code `oathkeeper-goal-unchanged`.
  - `TurnStateOperations.setOathkeeperGoal`.
  - `oathdigital.gameplay.powers.OathkeeperGoalChoice` (object).
  - `TheRedSeer` (case object) and `TheRedSeerCard`. Decision `power.the-red-seer.goal`.
  - `TrueOath` (final case class with `forCatalog`) and `TrueOathCard`. Decision `cardplay.true-oath.goal`.
  - Test helper `Table.oathkeeperGoal(goal)`.
- **Texts, exactly:**
  - Goal buttons: "Supremacy", "Protection", "The People", "Devotion", in that order (`OathkeeperGoal.all`). Each is keyed by `DecisionOptionRef.Button(goal.key)`.
  - The current goal is shown unavailable with "The current goal". The Red Seer's Devotion is shown unavailable with "The Red Seer cannot choose Devotion". When Devotion is the current goal, "The current goal" is its reason.
  - True Oath's extra last option: `DecisionOptionRef.Button("keep")`, "Keep the current goal".
  - Headings: "The Red Seer: change the Oathkeeper goal to anything but Devotion" and "True Oath: you may change the Oathkeeper goal".
  - The change line, for both cards: "Changed the Oathkeeper goal to Protection." Inside a sentence The People reads "the People": "Changed the Oathkeeper goal to the People."
  - True Oath's other lines: "Kept the Oathkeeper goal." and "Ruled fewer than three other Beast cards."
  - Neither card's question posts a "Chose" line.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 10 server tests and no frontend test.
  - It changes one existing test's data: `WalkerOperationWireSuite`'s operation list gains `SetOathkeeperGoal`.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

The product owner reviews these with the plan. Task 1 writes all of them into the spec.

1. **One goal question.** Both cards ask a `ChooseOne` that lists all four goals in printed order. The current goal is shown unavailable with "The current goal". The Red Seer also shows Devotion unavailable, with "The Red Seer cannot choose Devotion". True Oath adds a last option, "Keep the current goal", which declines.
2. **The title follows at the action's end.** The Oathkeeper check at every action boundary reads the new goal. So the title may pass in the same action, and the log's existing "Oathkeeper passed to {Blue}" line tells it. True Oath's boundary is the end of the Search that played it.
3. **The Red Seer without the Darkest Secret.** Its `usable` is false, so the Act menu shows it unavailable with the engine's "Nothing to do now", and its cost is not paid. A reason of its own would need a change to `PhasePower`, which is out of scope, as for Ward of Silence.
4. **True Oath counts live.** The other Beast cards are counted when the power fires, with True Oath already among its player's advisers. A facedown card has no suit, and a card at a site its player does not rule does not count. With fewer than three, nothing is asked.
5. **Lines.** Both cards write "Changed the Oathkeeper goal to Protection." True Oath writes "Kept the Oathkeeper goal." when declined, and "Ruled fewer than three other Beast cards." when it cannot ask. Neither question posts a "Chose" line.
6. **An unchanged goal is rejected.** Applying `SetOathkeeperGoal` with the goal already set fails with `oathkeeper-goal-unchanged`, a doubled or reordered journal, as `SetOathkeeper` treats an unchanged holder.

## File Structure

- **Create:**
  - `src/main/scala/oathdigital/gameplay/powers/OathkeeperGoalChoice.scala`: the shared question, answer and line.
  - `src/main/scala/oathdigital/gameplay/powers/action/TheRedSeer.scala`: the card and its power.
  - `src/main/scala/oathdigital/gameplay/powers/whenplayed/TrueOath.scala`: the card and its power.
  - `src/test/scala/oathdigital/gameplay/powers/action/TheRedSeerSuite.scala`
  - `src/test/scala/oathdigital/application/gamelog/TheRedSeerLogSuite.scala`
  - `src/test/scala/oathdigital/gameplay/powers/whenplayed/TrueOathSuite.scala`
- **Modify:**
  - `src/main/scala/oathdigital/model/CoreOperations.scala`: the operation.
  - `src/main/scala/oathdigital/model/OperationError.scala`: the error.
  - `src/main/scala/oathdigital/gameplay/operations/TurnStateOperations.scala` and `OperationApplication.scala`: applying it.
  - `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`: the wire form.
  - `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`: registers The Red Seer.
  - `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`: registers True Oath.
  - `src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala`: True Oath's reviewed handler becomes `playedDone`.
  - `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala` and `HoldingNomadDenizens.scala`: the cards move out.
  - `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`: imports.
  - `src/test/scala/oathdigital/testkit/Table.scala`: `oathkeeperGoal`.
  - `src/test/scala/oathdigital/gameplay/operations/OperationMutationSuite.scala` and `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala`.
  - The design, the rulings appendix and `docs/ROADMAP.md`.

---

### Task 1: Record the plan-time rulings

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` ("Slice 2: Oathkeeper goal")
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md` ("Slice 2: Oathkeeper goal")

- [ ] **Step 1: Add the design's plan-time rulings.** Under "## Slice 2: Oathkeeper goal", after its paragraph, add:

```markdown
### Plan-time rulings

- **One goal question.** Both cards list the four goals in printed order.
  The current goal is shown unavailable with "The current goal". The Red
  Seer also shows Devotion unavailable, with "The Red Seer cannot choose
  Devotion". True Oath adds "Keep the current goal", which declines.
- **The title follows at the action's end.** The Oathkeeper check at every
  action boundary reads the new goal, so the title may pass in the same
  action. The log's "Oathkeeper passed to {Blue}" line tells it.
- **The Red Seer without the Darkest Secret** is shown unavailable with the
  engine's "Nothing to do now", and its cost is not paid.
- **True Oath counts live,** when the power fires, with True Oath already
  among its player's advisers. With fewer than three other Beast cards it
  asks nothing.
- **Lines.** Both cards write "Changed the Oathkeeper goal to Protection."
  True Oath writes "Kept the Oathkeeper goal." when declined, and "Ruled
  fewer than three other Beast cards." when it cannot ask. Neither question
  posts a "Chose" line.
- **An unchanged goal is rejected.** `SetOathkeeperGoal` with the goal
  already set fails with `oathkeeper-goal-unchanged`, as `SetOathkeeper`
  treats an unchanged holder.
```

- [ ] **Step 2: Extend the rulings appendix.** Replace the slice 2 section's two entries with:

```markdown
- **209 True Oath.** "WHEN PLAYED, if you rule at least three other
  [suit-beast] cards, you may change the Oathkeeper goal."
  - The three Beast cards are counted as `RuledCards.of` counts ruled
    cards, without True Oath itself, when the power fires (plan-time
    ruling).
  - With three or more, it lists the four goals, the current one shown
    unavailable with "The current goal", and "Keep the current goal". The
    new goal must differ from the current one. The player may decline.
  - Lines: "True Oath: Changed the Oathkeeper goal to Protection.", "True
    Oath: Kept the Oathkeeper goal." and, with fewer than three, "True
    Oath: Ruled fewer than three other Beast cards." (plan-time ruling).
- **244 The Red Seer.** "[secret] [secret-burnt] [secret-burnt] ACTION: If
  you hold the Darkest Secret, change the Oathkeeper goal to anything but
  Devotion."
  - The new goal is neither the current goal nor Devotion. Both are listed
    unavailable: "The current goal", and "The Red Seer cannot choose
    Devotion" (plan-time ruling).
  - Without the Darkest Secret the action is shown unavailable, with
    "Nothing to do now" (Yew Staff satisfies it once slice 9 lands).
  - Line: "The Red Seer: Changed the Oathkeeper goal to Protection."
- **Both.** The title follows at the action's end: the Oathkeeper check
  reads the new goal, and "Oathkeeper passed to {Blue}" tells a pass
  (plan-time ruling).
```

- [ ] **Step 3: Check links and commit.**

Run: `python3 scripts/check-markdown-links.py`
Expected: no broken link.

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md
git commit -m "docs(batch8): record slice 2 plan-time rulings"
```

---

### Task 2: The Set Oathkeeper Goal operation

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala` (after `SetOathkeeper`)
- Modify: `src/main/scala/oathdigital/model/OperationError.scala` (after `OathkeeperUnchanged`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/TurnStateOperations.scala` (after `setOathkeeper`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala` (after the `SetOathkeeper` case)
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`
- Modify: `src/test/scala/oathdigital/testkit/Table.scala` (after `oathkeeper`)
- Test: `src/test/scala/oathdigital/gameplay/operations/OperationMutationSuite.scala`, `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala`

**Interfaces:**
- Produces: `SetOathkeeperGoal(goal: OathkeeperGoal)`, applied by the executor and replay, encoded as `set-oathkeeper-goal`. `Table.oathkeeperGoal(goal: OathkeeperGoal): Table`.

- [ ] **Step 1: Add the test helper.** In `Table.scala`, after `def oathkeeper(...)`:

```scala
  /** The campaign's Oathkeeper goal set to `goal`. */
  def oathkeeperGoal(goal: OathkeeperGoal): Table =
    update(_.updateCampaign(_.copy(oathkeeperGoal = goal)))
```

- [ ] **Step 2: Write the failing tests.** In `OperationMutationSuite.scala`, after the two `SetOathkeeper` tests:

```scala
  private def setGoal(from: OathkeeperGoal, to: OathkeeperGoal) =
    new OperationExecutor().executeAll(
      Table.start.oathkeeperGoal(from).ready, Vector(SetOathkeeperGoal(to)))
      .map(_.game.campaign.oathkeeperGoal)

  test("SetOathkeeperGoal changes the campaign's Oathkeeper goal"):
    assertEquals(setGoal(OathkeeperGoal.Supremacy, OathkeeperGoal.Devotion),
      Right(OathkeeperGoal.Devotion))

  test("SetOathkeeperGoal rejects leaving the goal unchanged"):
    assertEquals(setGoal(OathkeeperGoal.Protection, OathkeeperGoal.Protection)
      .left.map(_.code), Left("oathkeeper-goal-unchanged"))
```

In `WalkerOperationWireSuite.scala`, after `SetOathkeeper(None),` in the operation list:

```scala
      SetOathkeeperGoal(OathkeeperGoal.ThePeople),
```

- [ ] **Step 3: Run them to see them fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.OperationMutationSuite oathdigital.serialization.WalkerOperationWireSuite"`
Expected: compile failure, `SetOathkeeperGoal` not found.

- [ ] **Step 4: Add the operation.** In `CoreOperations.scala`, after `SetOathkeeper`:

```scala
/** Sets the campaign's Oathkeeper goal (catalog batch 8, slice 2).
  *
  * Which goal a power may choose is the power's rule. The title follows at
  * the next action boundary, where `OathkeeperRules.outcome` reads the goal.
  * The one thing applying this rejects is leaving the goal as it is
  * (`OperationError.OathkeeperGoalUnchanged`), which is a doubled or
  * reordered journal, as for [[SetOathkeeper]].
  */
final case class SetOathkeeperGoal(goal: OathkeeperGoal)
    extends PrimitiveOperation
```

In `OperationError.scala`, after `OathkeeperUnchanged`:

```scala
  /** A goal "change" to the goal already set. */
  final case class OathkeeperGoalUnchanged(goal: OathkeeperGoal)
      extends OperationError:
    override val code: String = "oathkeeper-goal-unchanged"
    override val detail: String =
      s"the Oathkeeper goal is already ${goal.key}"
```

In `TurnStateOperations.scala`, after `setOathkeeper` (import `OathkeeperGoalUnchanged` the way the file imports `OathkeeperUnchanged`):

```scala
  def setOathkeeperGoal(ready: ReadyGame,
      goal: OathkeeperGoal): Either[OperationError, ReadyGame] =
    Either.cond(ready.game.campaign.oathkeeperGoal != goal,
      ready.updateCampaign(_.copy(oathkeeperGoal = goal)),
      OathkeeperGoalUnchanged(goal))
```

In `OperationApplication.scala`, after the `SetOathkeeper` case:

```scala
      case (result, SetOathkeeperGoal(goal)) =>
        result.flatMap(TurnStateOperations.setOathkeeperGoal(_, goal))
```

- [ ] **Step 5: Add the wire form.** In `WalkerOperationCodec.scala`:
  - Add `SetOathkeeperGoal` to `TurnOperation`, after `SetOathkeeper`.
  - In `encodeTurnOperation`, after the `SetOathkeeper` case:

```scala
      case SetOathkeeperGoal(goal) => ujson.Obj(
        "kind" -> "set-oathkeeper-goal", "goal" -> goal.key)
```

  - In the decoder, after the `"set-oathkeeper"` case:

```scala
    case "set-oathkeeper-goal" =>
      val key = value("goal").str
      OathkeeperGoal.all.find(_.key == key).toRight(
        InvalidValue(s"$path.goal", s"unknown Oathkeeper goal '$key'"))
        .map(SetOathkeeperGoal.apply)
```

If the compiler names another exhaustive match over `CoreOperation`, add the case there beside `SetOathkeeper`'s and name the file in the report.

- [ ] **Step 6: Run the tests to see them pass.**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.OperationMutationSuite oathdigital.serialization.WalkerOperationWireSuite"`
Expected: PASS.

- [ ] **Step 7: Commit.**

```bash
git add src/main/scala/oathdigital/model/CoreOperations.scala src/main/scala/oathdigital/model/OperationError.scala src/main/scala/oathdigital/gameplay/operations/TurnStateOperations.scala src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala src/test/scala/oathdigital/testkit/Table.scala src/test/scala/oathdigital/gameplay/operations/OperationMutationSuite.scala src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala
git commit -m "feat(batch8): a Set Oathkeeper Goal operation"
```

---

### Task 3: The goal question and The Red Seer

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/OathkeeperGoalChoice.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/TheRedSeer.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala` (remove `TheRedSeerCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `TheRedSeerCard` from `gameplay.powers.action`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/TheRedSeerSuite.scala`, `src/test/scala/oathdigital/application/gamelog/TheRedSeerLogSuite.scala`

**Interfaces:**
- Consumes: `SetOathkeeperGoal`, `Table.oathkeeperGoal` (Task 2).
- Produces, used by Task 4:
  - `OathkeeperGoalChoice.options(ready: ReadyGame, barred: Map[OathkeeperGoal, String]): Vector[DecisionOption]`
  - `OathkeeperGoalChoice.set(ready: ReadyGame, pending: PendingTree, decisionId: String, barred: Set[OathkeeperGoal]): Either[OathViolation, Vector[CoreOperation]]`
  - `OathkeeperGoalChoice.changedKeys(prefix: String): Vector[NoteKey]`
  - `OathkeeperGoalChoice.changedNote(states: NoteStates, prefix: String, source: PowerSourceRef): Option[PowerNote]`
  - `OathkeeperGoalChoice.ref(goal: OathkeeperGoal): DecisionOptionRef.Button`, `.label(goal)`, `.currentGoal`

- [ ] **Step 1: Write the failing tests.** Create `TheRedSeerSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.powers.{NoteText, OathkeeperGoalChoice,
  PhasePowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, ParkedNode, PhasePowerHarness,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** The Red Seer: with the Darkest Secret, change the Oathkeeper goal to
  * anything but Devotion. */
class TheRedSeerSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val seer = CatalogNames.denizen("The Red Seer")
  private val source = DecisionOptionRef.Denizen(seer)

  /** p1's Act holding The Red Seer as an adviser, 3 faceup secrets and,
    * when `holds`, the Darkest Secret, under the goal `goal`. */
  private def seerHeldUnder(goal: OathkeeperGoal,
      holds: Boolean = true): ReadyGame =
    Table.start.turn(p1, Phase.Act).adviser(p1, seer).secrets(p1, 3)
      .darkestSecret(Option.when(holds)(p1), 1).oathkeeperGoal(goal).ready

  private def button(goal: OathkeeperGoal): DecisionOption.Button =
    DecisionOption.Button(OathkeeperGoalChoice.ref(goal),
      OathkeeperGoalChoice.label(goal))

  private def unavailable(goal: OathkeeperGoal, reason: String)
      : DecisionOption = DecisionOption.Unavailable(button(goal),
    OptionReason.Written(reason))

  /** The options of the decision the use is parked on. */
  private def parkedOptions(t: OathTransition): Vector[DecisionOption] =
    ParkedNode.of(t.state, catalog,
      phasePowers = PhasePowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) =>
        decide.query match
          case DecisionQuery.ChooseOne(options, _, _) => options
          case other => fail(s"expected a choose-one, got $other")
      case other => fail(s"expected a parked decision, got $other")

  /** Why the Act menu shows The Red Seer unavailable, if it does. */
  private def menuReason(ready: ReadyGame): Option[OptionReason] =
    PhaseMenu.offers(PhaseMenu.Menu.Act, catalog, ready, p1,
      PhasePowerCatalog.default(catalog), WalkerPowers.empty)
      .map(_.option).collectFirst {
        case DecisionOption.Unavailable(shown, reason)
            if shown.ref == source => reason
      }

  test("it pays its cost, offers every goal but the current one and " +
      "Devotion, and sets the one chosen"):
    val ready = seerHeldUnder(OathkeeperGoal.Supremacy)
    val started = accepted(use(ready, TheRedSeer, source))
    assert(awaits(started, TheRedSeer.decisionId))
    assertEquals(parkedOptions(started), Vector[DecisionOption](
      unavailable(OathkeeperGoal.Supremacy, OathkeeperGoalChoice.currentGoal),
      button(OathkeeperGoal.Protection), button(OathkeeperGoal.ThePeople),
      unavailable(OathkeeperGoal.Devotion, TheRedSeer.notDevotion)))
    // 1 secret placed on the card and 2 burnt.
    assertEquals(Look(after(started)).tokensOn(seer), Tokens(0, 1))
    assertEquals(Look(after(started)).faceUpSecrets(p1), 0)
    val done = accepted(answer(started, p1, TheRedSeer.decisionId,
      pick(OathkeeperGoalChoice.ref(OathkeeperGoal.ThePeople))))
    assertEquals(after(done).game.campaign.oathkeeperGoal,
      OathkeeperGoal.ThePeople)
    val events = started.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assertWireRoundTrips(events)
    assertEquals(NoteText.said(TheRedSeer, done.events), Vector(
      NoteText.Said(s"${NoteKey.Used}.the-people",
        "Changed the Oathkeeper goal to the People.", covers = false)))

  test("under Devotion, Devotion is the current goal and the other three " +
      "are offered"):
    val started = accepted(use(seerHeldUnder(OathkeeperGoal.Devotion),
      TheRedSeer, source))
    assertEquals(parkedOptions(started), Vector[DecisionOption](
      button(OathkeeperGoal.Supremacy), button(OathkeeperGoal.Protection),
      button(OathkeeperGoal.ThePeople),
      unavailable(OathkeeperGoal.Devotion, OathkeeperGoalChoice.currentGoal)))

  test("an unavailable goal is refused"):
    val started = accepted(use(seerHeldUnder(OathkeeperGoal.Supremacy),
      TheRedSeer, source))
    Vector(OathkeeperGoal.Supremacy, OathkeeperGoal.Devotion).foreach { goal =>
      assert(answer(started, p1, TheRedSeer.decisionId,
        pick(OathkeeperGoalChoice.ref(goal))).isLeft, goal.key)
    }

  test("without the Darkest Secret it is unavailable"):
    val ready = seerHeldUnder(OathkeeperGoal.Supremacy, holds = false)
    assert(!usableNow(ready).exists(_.power.id == TheRedSeer.id))
    assertEquals(menuReason(ready),
      Some(OptionReason.Written("Nothing to do now")))
```

Create `TheRedSeerLogSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.gameplay.powers.OathkeeperGoalChoice
import oathdigital.gameplay.powers.action.TheRedSeer
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import LogScripts.{atTable, format, playerLabel, texts}

/** How The Red Seer reads in the Game Log: its line names the new goal, its
  * question posts no "Chose" line, and the title follows the new goal when
  * the action ends. */
class TheRedSeerLogSuite extends munit.FunSuite:
  private val seer = CatalogNames.denizen("The Red Seer")

  /** p1 holds The Red Seer, the Darkest Secret, 3 faceup secrets and the
    * Oathkeeper title, and rules Ancient City under Supremacy; p2 holds
    * Lost Tapestry. p1 changes the goal to Protection, which p2 leads. The
    * log as p3 sees it. */
  private def seerChangedTheGoalToProtection: Vector[String] =
    val table = Table.start.pawn(p1, "Ancient City")
      .pawn(p2, "Buried Giant").pawn(p3, "Buried Giant")
      .adviser(p1, seer).secrets(p1, faceUp = 3)
      .darkestSecret(Some(p1), 1).oathkeeper(Some(p1))
      .oathkeeperGoal(OathkeeperGoal.Supremacy)
      .warbandsAt("Ancient City", p1, 1).relic(p2, "Lost Tapestry")
      .banditsRefilled
    val (service, act) = atTable("the-red-seer", table)
    act.withAnswers {
      case park if park.decisionId == TheRedSeer.decisionId =>
        ChooseOneAnswer(OathkeeperGoalChoice.ref(OathkeeperGoal.Protection))
    }.after(MenuPlay.start(p1, ActionRef.UsePower(TheRedSeer.id),
      Vector(DecisionOptionRef.Denizen(seer))))
    texts(format(Script("the-red-seer", service, p1), Some(p3)))

  test("its line names the new goal, with no Chose line, and the title " +
      "passes to the new goal's leader"):
    val log = seerChangedTheGoalToProtection
    assert(log.contains("The Red Seer: Changed the Oathkeeper goal to " +
      "Protection."), log.mkString("\n"))
    assert(!log.exists(_.startsWith("Chose ")), log.mkString("\n"))
    assert(log.contains(s"Oathkeeper passed to ${playerLabel(p2)}"),
      log.mkString("\n"))
```

If `Table.start` already gives p1 the Oathkeeper title under a different goal leader, or another player rules a site, adjust the staging so that p1 leads Supremacy and p2 leads Protection, and say so in the doc comment.

- [ ] **Step 2: Run them to see them fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.TheRedSeerSuite oathdigital.application.gamelog.TheRedSeerLogSuite"`
Expected: compile failure, `TheRedSeer` and `OathkeeperGoalChoice` not found.

- [ ] **Step 3: Write the shared goal question.** Create `OathkeeperGoalChoice.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.model._

/** The question a power asks to change the Oathkeeper goal, and the line
  * that tells the change (catalog batch 8, slice 2). The Red Seer and True
  * Oath ask it.
  *
  * Every goal is listed in printed order, as a button keyed by the goal's
  * own key. The current goal is shown unavailable, and so is each goal the
  * power bars, the current goal's reason first.
  */
object OathkeeperGoalChoice:
  val currentGoal: String = "The current goal"

  def label(goal: OathkeeperGoal): String = goal match
    case OathkeeperGoal.Supremacy => "Supremacy"
    case OathkeeperGoal.Protection => "Protection"
    case OathkeeperGoal.ThePeople => "The People"
    case OathkeeperGoal.Devotion => "Devotion"

  /** The goal's name inside a sentence, "the People". */
  private def named(goal: OathkeeperGoal): String = goal match
    case OathkeeperGoal.ThePeople => "the People"
    case other => label(other)

  def ref(goal: OathkeeperGoal): DecisionOptionRef.Button =
    DecisionOptionRef.Button(goal.key)

  def options(ready: ReadyGame, barred: Map[OathkeeperGoal, String])
      : Vector[DecisionOption] =
    val current = ready.game.campaign.oathkeeperGoal
    OathkeeperGoal.all.map { goal =>
      val shown = DecisionOption.Button(ref(goal), label(goal))
      val reason = if goal == current then Some(currentGoal)
        else barred.get(goal)
      reason.fold[DecisionOption](shown)(written =>
        DecisionOption.Unavailable(shown, OptionReason.Written(written)))
    }

  /** The goal answered to `decisionId`, when the answer names one. */
  def chosen(pending: PendingTree, decisionId: String)
      : Option[OathkeeperGoal] =
    PowerAnswers.one(pending, decisionId).flatMap {
      case DecisionOptionRef.Button(key) => OathkeeperGoal.all.find(_.key == key)
      case _ => None
    }

  /** The answered goal set, while it is neither the current goal nor
    * barred. */
  def set(ready: ReadyGame, pending: PendingTree, decisionId: String,
      barred: Set[OathkeeperGoal])
      : Either[OathViolation, Vector[CoreOperation]] =
    chosen(pending, decisionId).toRight(PowerAnswers.missing(decisionId))
      .flatMap(goal => Either.cond(
        goal != ready.game.campaign.oathkeeperGoal && !barred(goal),
        Vector[CoreOperation](SetOathkeeperGoal(goal)),
        OathViolation.InvalidEventOrder(
          s"the Oathkeeper goal cannot change to ${goal.key}")))

  /** "Changed the Oathkeeper goal to Protection.", named
    * `{prefix}.{goal key}`. */
  def changed(prefix: String, goal: OathkeeperGoal): NoteKey =
    NoteKey(s"$prefix.${goal.key}", Vector(NotePart.Text(
      s"Changed the Oathkeeper goal to ${named(goal)}.")))

  /** One change line per goal. */
  def changedKeys(prefix: String): Vector[NoteKey] =
    OathkeeperGoal.all.map(changed(prefix, _))

  /** The line for the goal the step before set, if it changed one. */
  def changedNote(states: NoteStates, prefix: String,
      source: PowerSourceRef): Option[PowerNote] = for
    (before, after) <- states.previous
    goal = after.game.campaign.oathkeeperGoal
    if goal != before.game.campaign.oathkeeperGoal
  yield changed(prefix, goal)(source)
```

- [ ] **Step 4: Write The Red Seer.** Remove `TheRedSeerCard` from `HoldingNomadDenizens.scala`, and drop any import that file no longer uses. Create `TheRedSeer.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powers.OathkeeperGoalChoice
import oathdigital.model._

object TheRedSeerCard extends Denizen(DenizenId("244"), "The Red Seer",
    Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.the-red-seer"),
    persistent = false, cost = Cost(secret = 1, secretBurnt = 2),
    text = "**ACTION:** If you hold the Darkest Secret, change the " +
      "Oathkeeper goal to anything but Devotion.")
  val powers: Vector[PrintedPower] = Vector(power)

/** The Red Seer (card 244), ACTION: "If you hold the Darkest Secret, change
  * the Oathkeeper goal to anything but Devotion."
  *
  * The engine pays the printed cost. It is usable only by a player who
  * holds the Darkest Secret; otherwise the Act menu shows it unavailable
  * with "Nothing to do now". The user chooses a new goal from the four,
  * with the current goal and Devotion shown unavailable. The title follows
  * at the action's end, where the Oathkeeper check reads the new goal.
  *
  * Its line is the action's: "Changed the Oathkeeper goal to Protection."
  * It narrates its question.
  */
case object TheRedSeer extends PaidAction(TheRedSeerCard.power):
  val decisionId: String = "power.the-red-seer.goal"
  val notDevotion: String = "The Red Seer cannot choose Devotion"
  private val barred: Map[OathkeeperGoal, String] =
    Map(OathkeeperGoal.Devotion -> notDevotion)

  override def noteKeys: Vector[NoteKey] =
    OathkeeperGoalChoice.changedKeys(NoteKey.Used)
  override def narratedDecisions: Set[String] = Set(decisionId)

  override def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean =
    BannerRules.holder(ready.game.current, Banner.DarkestSecret)
      .contains(player)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => Vector(Decide(decisionId, player,
      DecisionQuery.ChooseOne(OathkeeperGoalChoice.options(live, barred),
        heading = Some("The Red Seer: change the Oathkeeper goal to " +
          "anything but Devotion"))))),
    BuildOps((live, pending) =>
      OathkeeperGoalChoice.set(live, pending, decisionId, barred.keySet)),
    Note(id, states => PowerSourceRef.of(source).flatMap(card =>
      OathkeeperGoalChoice.changedNote(states, NoteKey.Used, card))))))
```

Register it: in `WorldActionPowers.forCatalog`, add `TheRedSeer` to the first `Vector[PhasePower](...)` after `Convoys`. In `NewFoundations.scala`, add `TheRedSeerCard` to the `gameplay.powers.action` import list in alphabetical order.

- [ ] **Step 5: Run the tests to see them pass.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.TheRedSeerSuite oathdigital.application.gamelog.TheRedSeerLogSuite"`
Expected: PASS, 5 tests.

- [ ] **Step 6: Run the whole server suite** to catch catalog and campaign fixtures that relied on The Red Seer having no power.

Run: `./sbtw test`
Expected: PASS. If a fixture used The Red Seer as a planless or powerless filler, swap in another card with no implemented power and name it in the report.

- [ ] **Step 7: Commit.**

```bash
git add src/main/scala/oathdigital/gameplay/powers/OathkeeperGoalChoice.scala src/main/scala/oathdigital/gameplay/powers/action/TheRedSeer.scala src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/test/scala/oathdigital/gameplay/powers/action/TheRedSeerSuite.scala src/test/scala/oathdigital/application/gamelog/TheRedSeerLogSuite.scala
git commit -m "feat(batch8): The Red Seer changes the Oathkeeper goal"
```

---

### Task 4: True Oath

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/TrueOath.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala` (remove `TrueOathCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `TrueOathCard` from `gameplay.powers.whenplayed`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala` (`TrueOath`'s handler becomes `playedDone`)
- Test: `src/test/scala/oathdigital/gameplay/powers/whenplayed/TrueOathSuite.scala`

**Interfaces:**
- Consumes: `OathkeeperGoalChoice` (Task 3), `Table.oathkeeperGoal` (Task 2).

- [ ] **Step 1: Write the failing tests.** Create `TrueOathSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, OathkeeperGoalChoice}
import oathdigital.model._
import oathdigital.testkit.{Outcomes, Table}
import oathdigital.testkit.Table.p1

/** True Oath: with three other Beast cards ruled, its player may change the
  * Oathkeeper goal. */
class TrueOathSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[TrueOath]
  private val oath = power.cardId
  private val played = hook(oath)

  /** True Oath and the Beast advisers Animal Host and Threatening Roar,
    * p1's, under Supremacy. Wolves, the third other Beast card, stands at
    * p1's site, which p1 rules, or, when `wolvesRuled` is false, at a site
    * nobody rules. */
  private def oathBesideBeasts(wolvesRuled: Boolean = true): ReadyGame =
    Table.start.adviser(p1, oath).adviser(p1, "Animal Host")
      .adviser(p1, "Threatening Roar").warbandsAt(homeSite, p1, 1)
      .denizen("Wolves", at = if wolvesRuled then homeSite else nearSite)
      .oathkeeperGoal(OathkeeperGoal.Supremacy).ready

  private def goal(ready: ReadyGame): OathkeeperGoal =
    ready.game.campaign.oathkeeperGoal

  test("with three other Beast cards it offers the other goals and Keep, " +
      "and sets the one chosen"):
    val ready = oathBesideBeasts()
    val first = Outcomes.parked(play(ready, power, oath))
    val decide = question(ready, power, played, first.tree)
    assertEquals(decide.decisionId, TrueOath.decisionId)
    assertEquals(chooseOneOptions(decide), OathkeeperGoal.all.map { g =>
      val shown = DecisionOption.Button(OathkeeperGoalChoice.ref(g),
        OathkeeperGoalChoice.label(g))
      if g == OathkeeperGoal.Supremacy then DecisionOption.Unavailable(shown,
        OptionReason.Written(OathkeeperGoalChoice.currentGoal))
      else shown
    } :+ DecisionOption.Button(TrueOath.keep, "Keep the current goal"))
    val done = Outcomes.finished(resume(ready, power, played, first.tree,
      TrueOath.decisionId,
      chooseOne(OathkeeperGoalChoice.ref(OathkeeperGoal.Devotion))))
    assertEquals(goal(done.treeless), OathkeeperGoal.Devotion)
    assertJournalRebuilds(ready, first.events ++ done.events, done.treeless)
    assertEquals(said(power, done.events), Vector(NoteText.Said(
      "changed.devotion", "Changed the Oathkeeper goal to Devotion.",
      covers = false)))

  test("Keep leaves the goal, and the line says so"):
    val ready = oathBesideBeasts()
    val first = Outcomes.parked(play(ready, power, oath))
    val done = Outcomes.finished(resume(ready, power, played, first.tree,
      TrueOath.decisionId, chooseOne(TrueOath.keep)))
    assertEquals(goal(done.treeless), OathkeeperGoal.Supremacy)
    assertEquals(said(power, done.events), Vector(NoteText.Said("kept",
      "Kept the Oathkeeper goal.", covers = false)))

  test("with two other Beast cards, True Oath itself not counted, it asks " +
      "nothing"):
    val done = Outcomes.finished(play(oathBesideBeasts(wolvesRuled = false),
      power, oath))
    assertEquals(goal(done.treeless), OathkeeperGoal.Supremacy)
    assertEquals(said(power, done.events), Vector(NoteText.Said("unmet",
      "Ruled fewer than three other Beast cards.", covers = false)))
```

- [ ] **Step 2: Run them to see them fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.TrueOathSuite"`
Expected: compile failure, `TrueOath` not found.

- [ ] **Step 3: Write True Oath.** Remove `TrueOathCard` from `HoldingBeastDenizens.scala`; `Locked` and `AdviserOnly` stay imported there while other cards use them. Create `TrueOath.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{NoteSupport, OathkeeperGoalChoice,
  PowerAnswers, RuledCards}
import oathdigital.model._

object TrueOathCard extends Denizen(DenizenId("209"), "True Oath",
    Suit.Beast) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.true-oath"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** if you rule at least three other " +
      "[suit-beast] cards, you may change the Oathkeeper goal.")
  val powers: Vector[PrintedPower] = Vector(power)

/** True Oath (card 209), WHEN PLAYED: "if you rule at least three other
  * Beast cards, you may change the Oathkeeper goal."
  *
  * The other Beast cards its player rules are read live, as
  * [[RuledCards]] reads ruled cards, without True Oath itself. With three
  * or more, the player chooses a new goal or keeps the current one, which
  * is shown unavailable among the four. With fewer, nothing is asked. The
  * title follows at the end of the action, where the Oathkeeper check reads
  * the new goal.
  *
  * Lines: "Changed the Oathkeeper goal to Protection.", "Kept the
  * Oathkeeper goal." and "Ruled fewer than three other Beast cards." It
  * narrates its question.
  */
final case class TrueOath private (catalog: ExecutableCatalog)
    extends WhenPlayedPower:
  val cardId: DenizenId = TrueOathCard.id
  import TrueOath._
  def id: PowerId = TrueOath.id

  override def noteKeys: Vector[NoteKey] =
    OathkeeperGoalChoice.changedKeys(Changed) ++ Vector(kept, unmet)
  override def narratedDecisions: Set[String] = Set(decisionId)

  private def cardSource: PowerSourceRef = PowerSourceRef.Card(cardId)

  /** The other Beast cards `actor` rules. */
  private def others(ready: ReadyGame, actor: PlayerId): Int =
    RuledCards.of(catalog, ready, SiteRuler.Player(actor), Suit.Beast)
      .count(_ != cardId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(Branch((live, _) =>
      if others(live, actor) < Others then
        Vector(Note(id, _ => Some(unmet(cardSource))))
      else Vector(
        Decide(decisionId, actor, DecisionQuery.ChooseOne(
          OathkeeperGoalChoice.options(live, Map.empty) :+
            DecisionOption.Button(keep, "Keep the current goal"),
          heading = Some("True Oath: you may change the Oathkeeper goal"))),
        BuildOps((ready, pending) => settle(ready, pending)),
        Note(id, note))))

  private def settle(ready: ReadyGame, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    if PowerAnswers.one(pending, decisionId).contains(keep) then
      Right(Vector.empty)
    else OathkeeperGoalChoice.set(ready, pending, decisionId, Set.empty)

  private def note(states: NoteStates): Option[PowerNote] =
    OathkeeperGoalChoice.changedNote(states, Changed, cardSource).orElse(
      Option.when(NoteSupport.answer(states, decisionId).contains(keep))(
        kept(cardSource)))

object TrueOath:
  val id: PowerId = TrueOathCard.power.id
  val decisionId: String = "cardplay.true-oath.goal"
  val keep: DecisionOptionRef.Button = DecisionOptionRef.Button("keep")
  /** The other Beast cards it needs. */
  val Others: Int = 3
  /** The prefix of its change lines. */
  val Changed: String = "changed"
  /** "Kept the Oathkeeper goal." */
  val kept: NoteKey = NoteKey("kept", Vector(
    NotePart.Text("Kept the Oathkeeper goal.")))
  /** "Ruled fewer than three other Beast cards." */
  val unmet: NoteKey = NoteKey("unmet", Vector(
    NotePart.Text("Ruled fewer than three other Beast cards.")))

  def forCatalog(catalog: ExecutableCatalog): TrueOath = new TrueOath(catalog)
```

Register it: in `WhenPlayedPowers.forCatalog`, append `++ Vector(TrueOath.forCatalog(catalog))`. In `ActionPowers.scala`, change `object TrueOath extends ReviewedPower("denizen.true-oath", None, played)` to use `playedDone`. In `NewFoundations.scala`, add `TrueOathCard` to the `gameplay.powers.whenplayed` import list in alphabetical order.

- [ ] **Step 4: Run the tests to see them pass.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.TrueOathSuite"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Run the whole server suite.**

Run: `./sbtw test`
Expected: PASS. If a fixture used True Oath as a powerless filler, swap in another card with no implemented power and name it in the report.

- [ ] **Step 6: Commit.**

```bash
git add src/main/scala/oathdigital/gameplay/powers/whenplayed/TrueOath.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/test/scala/oathdigital/gameplay/powers/whenplayed/TrueOathSuite.scala
git commit -m "feat(batch8): True Oath may change the Oathkeeper goal"
```

---

### Task 5: Close the slice

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` (Status)
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Run the gates**, in order:

```bash
./sbtw "test" "frontend/test"
```

```bash
./sbtw frontend/fullOptJS
```

```bash
python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py
```

Expected: all pass. The server count is the baseline plus 10; the frontend count is unchanged.

- [ ] **Step 2: Update the docs.**
  - In the design's Status, "Slice 1 is done." becomes "Slices 1 and 2 are done."
  - In `docs/ROADMAP.md`, check `- [ ] **2. Oathkeeper goal:** True Oath, The Red Seer.` and change the Now line to "**Phase - Catalog batch 8** is under way: slices 1 and 2 are done, and slice 3 is next."

- [ ] **Step 3: Commit.**

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/ROADMAP.md
git commit -m "docs(batch8): slice 2 done"
```

- [ ] **Step 4: Report** the baseline and final test counts, any fixture swapped, and any exhaustive match the compiler named in Task 2.
