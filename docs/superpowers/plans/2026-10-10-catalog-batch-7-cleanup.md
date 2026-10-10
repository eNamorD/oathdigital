# Catalog Batch 7 Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the two test-only cleanup items that catalog batch 7 left in `docs/ROADMAP.md`: the Game Log under Careful Plans, and two slice 4 rulings that no test pins.

**Architecture:** This plan adds tests only. No production code changes. A spike on 2026-10-10 ran each new test against `main` at `b4cf692d`, so the expected values below are what the code produces now:

- **Careful Plans in the Game Log.** A raid played through `GameApplicationService` with Careful Plans chosen logs the defense roll and its total before the attack roll, and the sacrifice line still adds the sacrifice to the attack. `CampaignLines` needs no change. The new test fixes that order.
- **Hearts and Minds and the People's Favor.** The card's discard check is a `later` hook at `CampaignActionEligibility`. It reads the People's Favor holder when the walk reaches the end of the Campaign, not when the plan was chosen. No rule in the engine gives a defender the banner while it wins with Hearts and Minds. So the test changes the holder in the parked state between the plan pick and the end of the Campaign, and says why. A mirror test takes the banner away in the same place.
- **Signal Trees before it is chosen.** The old test "until it is applied, no player knows the cards" cannot fail through a dry run. A dry run records no event and leaves no state behind, so a dry-run reveal can never show up in the state the test reads. The spike also tried making the plan read the pile when the offer is built rather than when the walk reaches it. Every test still passed, because the plan window is folded again from the live state on every resume. The leak a test can catch is a reveal wired outside the chosen path, such as a rewrite or a window hook that runs whether or not the plan was chosen. The replacement test leaves Signal Trees unchosen, plays the Campaign to its end, and checks that the cards stayed hidden and unmoved.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`.

**Spec:** Read these before starting:

- `docs/ROADMAP.md`, "Cleanup tasks", the block "Left behind by catalog batch 7". This plan closes its second and third items. The first, the browser play-test, stays.
- `docs/superpowers/specs/2026-10-09-catalog-batch-7-rulings.md`, the entries "254 Careful Plans", "217 Signal Trees" and "138 Hearts and Minds".

**Starts from:** `main` at or after `b4cf692d`. Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails it.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Test helper names** state what they build. A doc comment does not excuse a vague name.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Baseline.** Record the server test count from your first full `./sbtw "test"` run in the worktree, and compare it at the end.
  - This plan adds 3 server tests: 1 in Task 1 and 2 in Task 2. Task 3 replaces a test, so the count does not change there.
  - It changes no frontend code, so the frontend suite and `fullOptJS` are not gates.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test"`
  2. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
- **Mutation checks.** These tests pass on the first run, because the behavior already exists. Each task therefore breaks the test on purpose, watches it fail, and reverts the change. Never commit a mutation. Run `git diff` before each commit and check that only the listed files changed.

---

### Task 1: The Game Log under Careful Plans

**Files:**
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (the `raid` script group, around lines 478–545)
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogCampaignSuite.scala`
- Modify: `docs/ROADMAP.md` (the item "Cover the Game Log under Careful Plans")

**Interfaces:**
- Produces: `LogScripts.raidUnderCarefulPlans(using munit.Location): Script`. It is not added to `LogScripts.named`, because every named script has a golden file that `GameLogGoldenSuite` compares against.
- Changes: the private `LogScripts.raidAnswers(act: Situation)` gains a second parameter, `plans: Answers = PartialFunction.empty`. It is tried before the raid's own answers. Existing callers pass nothing and behave as before.

- [ ] **Step 1: Let `raidAnswers` take extra plan answers**

In `LogScripts.scala`, replace the head of `raidAnswers`:

```scala
  /** A Raid's answers: all force committed and sacrificed, no battle plans. */
  private def raidAnswers(act: Situation): Situation =
    act.withAnswers {
```

with:

```scala
  /** A Raid's answers: all force committed and sacrificed, and Finish at
    * every battle plan window unless `plans` answers it first. */
  private def raidAnswers(act: Situation,
      plans: Answers = PartialFunction.empty): Situation =
    act.withAnswers(plans.orElse {
```

Then close the call: the block's final `}` becomes `})`. The block ends with the plan-window case:

```scala
      case park if park.decisionId == CampaignIds.attackerPlan ||
          park.decisionId == CampaignIds.defenderPlan =>
        ChooseOneAnswer(CampaignIds.finish)
    })
```

`Answers`, `Look`, `Tokens` and `CatalogNames` are already imported in this file.

- [ ] **Step 2: Add the script**

In `LogScripts.scala`, directly after `def raid`, add:

```scala
  /** The `raid` table with Careful Plans faceup as p1's adviser and one
    * favor to pay for it. p1 chooses it, so p2's defense dice roll before
    * p1's attack dice. Everything else is answered as in `raid`. */
  def raidUnderCarefulPlans(using munit.Location): Script =
    val careful = CatalogNames.denizen("Careful Plans")
    val (service, act) = atTable("careful-raid", tableWithOnePawnPerSite
      .pawn(p2, "Ancient City")
      .adviser(p2, "Birdsong", facedown = true)
      .adviser(p1, careful).favor(p1, 1).banditsRefilled, Dice.raid)
    // Chosen once: the favor it places marks it used.
    raidAnswers(act, {
      case park if park.decisionId == CampaignIds.attackerPlan &&
          Look(park.ready).adviserTokens(p1, careful) == Tokens(0, 0) =>
        ChooseOneAnswer(DecisionOptionRef.Denizen(careful))
    }).after(MenuPlay.start(p1, ActionRef.Campaign))
    Script("careful-raid", service, p1)
```

- [ ] **Step 3: Write the test**

Append to `GameLogCampaignSuite`:

```scala
  test("under Careful Plans the defense is rolled and told before the " +
      "attack"):
    val script = raidUnderCarefulPlans
    val all = lines(script)
    val from = all.indexOf("Activated Careful Plans")
    assert(from >= 0, all)
    // Dice.raid rolls a sword on every attack die and a blank on every
    // defense die. The three warbands sacrificed are added to the attack
    // after it is rolled, as in the printed order.
    assertEquals(all.slice(from, from + 7), Vector(
      "Activated Careful Plans",
      "Rolled blank, blank for the defense",
      "Defense: 3",
      "Rolled sword, sword, sword for the attack",
      "Attack: 3",
      "Sacrificed 3 warbands for an attack of 6",
      s"${playerLabel(script.actor)} wins!"))
```

- [ ] **Step 4: Run the test**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogCampaignSuite"`
Expected: PASS, 6 tests.

- [ ] **Step 5: Mutation check**

In `raidUnderCarefulPlans`, delete `.favor(p1, 1)`. Careful Plans can no longer be paid for, so it is not chosen and the dice roll in the printed order.

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogCampaignSuite"`
Expected: FAIL in the new test. The assertion `from >= 0` fails, because no line reads "Activated Careful Plans".

Restore `.favor(p1, 1)` and run the suite again. Expected: PASS.

- [ ] **Step 6: Remove the roadmap item**

In `docs/ROADMAP.md`, under "Left behind by catalog batch 7", delete the whole item that begins `- [ ] **Cover the Game Log under Careful Plans.**`. That is four lines, ending with "were written for the printed order.". The roadmap deletes items once they are done.

- [ ] **Step 7: Commit**

```bash
git diff --stat
git add src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogCampaignSuite.scala \
  docs/ROADMAP.md
git commit -m "test(batch7): pin the Game Log's order under Careful Plans"
```

---

### Task 2: Hearts and Minds reads the People's Favor at the end

**Files:**
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/HeartsAndMindsSuite.scala`

**Interfaces:**
- Consumes: from `PlanDriver`, `Run(game, transition, events, powers)` (a case class), `commit`, `Run.pick`, `Run.finishPlanWindows`, `awaits` and `said`. From `CampaignFixture`, `ready(state: OathState): ReadyGame`. From `testkit`, `Table.from(ready: ReadyGame): Table`, `Table.unchecked: ReadyGame` and `Table.peoplesFavor(holder: Option[PlayerId], favor: Int)`.
- Produces: a private suite helper, `runWithTableChanged(run: Run)(steps: Table => Table): Run`.

- [ ] **Step 1: Write the helper and the two tests**

In `HeartsAndMindsSuite.scala`, change the testkit import to:

```scala
import oathdigital.testkit.{CatalogNames, Look, Table}
```

Add this helper after `defender`:

```scala
  /** `run` with its parked state changed by Table steps, as if something
    * earlier in the Campaign had changed the board. */
  private def runWithTableChanged(run: Run)(steps: Table => Table): Run =
    run.copy(transition = run.transition.copy(state =
      OathState.Ready(steps(Table.from(ready(run.state))).unchecked)))
```

Append these two tests:

```scala
  // No rule yet gives a defender the People's Favor while it wins with
  // Hearts and Minds, so these tests move the banner in the parked state,
  // after the pick and before the end of the Campaign.
  test("a People's Favor won during the Campaign keeps the card"):
    val b = defender(favor = 3, favored = false)
    val picked = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(awaits(picked, b.rival, CampaignIds.defenderPlan))
    val done = runWithTableChanged(picked)(
      _.peoplesFavor(Some(b.rival), favor = 1)).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assert(Look(done.state).cardsAt(b.origin).contains(card))
    assertEquals(said(power, done.events), Vector.empty)

  test("a People's Favor lost during the Campaign discards the card"):
    val b = defender(favor = 3, favored = true)
    val picked = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(awaits(picked, b.rival, CampaignIds.defenderPlan))
    val done = runWithTableChanged(picked)(
      _.peoplesFavor(None, favor = 1)).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(said(power, done.events), Vector(NoteText.Said("discarded",
      "Discarded after the Campaign.", covers = false)))
```

The `awaits` assertion matters: it shows the Campaign is still running when the banner moves, so the end-of-Campaign check comes after the change.

- [ ] **Step 2: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.HeartsAndMindsSuite"`
Expected: PASS, 5 tests.

- [ ] **Step 3: Mutation check**

In the first new test, replace the line `_.peoplesFavor(Some(b.rival), favor = 1)).finishPlanWindows` with `identity).finishPlanWindows`, so the banner never moves.

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.HeartsAndMindsSuite"`
Expected: FAIL in "a People's Favor won during the Campaign keeps the card": the card is no longer at the origin. This shows the banner change, and nothing else, is what keeps the card.

Restore the line and run the suite again. Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git diff --stat
git add src/test/scala/oathdigital/gameplay/powers/campaign/HeartsAndMindsSuite.scala
git commit -m "test(batch7): pin Hearts and Minds' People's Favor at the end"
```

---

### Task 3: An unchosen Signal Trees reveals nothing

**Files:**
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/SignalTreesSuite.scala`
- Modify: `docs/ROADMAP.md` (the item "Pin two slice 4 rulings in tests")

**Interfaces:**
- Consumes: from `PlanDriver`, `commit`, `Run.offers(actor, who, id, ref)`, `Run.finishPlanWindows`, `Run.winner` and `said`. The suite's own `attacker`, `everyone`, `ref`, `power` and card values.

- [ ] **Step 1: Replace the test**

In `SignalTreesSuite.scala`, delete the whole test `test("until it is applied, no player knows the cards"):` and put this in its place:

```scala
  // A dry run records no event and leaves no state, so no test can see a
  // reveal in one. A reveal wired outside the chosen plan, as a rewrite or
  // a window hook, would run unchosen; this test catches that.
  test("unchosen, it reveals and moves nothing up to the Campaign's end"):
    val b = attacker(oracle, swarm, songs, wolves)
    def untouched(state: OathState): Unit =
      val now = Look(state)
      for hidden <- Vector(wolves, songs, swarm); viewer <- everyone(b) do
        assert(!now.knowsAdviser(viewer, hidden), s"$viewer knows $hidden")
      assertEquals(now.discardPile(Region.Cradle),
        Vector(oracle, swarm, songs, wolves))
    val run = commit(rules(winningDice), b, 2)
    assert(run.offers(b.actor, b.actor, CampaignIds.attackerPlan, ref))
    untouched(run.state)
    val done = run.finishPlanWindows
    assert(done.winner.nonEmpty)
    untouched(done.state)
    assertEquals(said(power, done.events), Vector.empty)
```

- [ ] **Step 2: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.SignalTreesSuite"`
Expected: PASS, 6 tests.

- [ ] **Step 3: Mutation check**

Wire Signal Trees' effect as a rewrite, which runs whether or not the plan was chosen. In `SignalTrees.scala`, add to the imports:

```scala
import oathdigital.gameplay.powerresolver.Transform
```

and add this member to the class, after `plan`:

```scala
  override def rewrites: Map[PowerWindow, Transform] = Map(
    PowerWindow.CampaignAttackRoll -> Transform((ctx, children) =>
      signal(ctx.activePlayer, CampaignPlanSide.Attacker) +: children))
```

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.SignalTreesSuite"`
Expected: FAIL in "unchosen, it reveals and moves nothing up to the Campaign's end". A card is known, the pile has moved, or the walk fails. Any of these counts.

Revert `SignalTrees.scala` (`git checkout src/main/scala/oathdigital/gameplay/powers/campaign/SignalTrees.scala`) and run the suite again. Expected: PASS.

- [ ] **Step 4: Remove the roadmap item**

In `docs/ROADMAP.md`, under "Left behind by catalog batch 7", delete the whole item that begins `- [ ] **Pin two slice 4 rulings in tests.**`. That is five lines, ending with "would pass even if the plan window's dry run revealed them.". The play-test item stays, and so does the block's heading line.

- [ ] **Step 5: Run the gates**

1. Run: `./sbtw "test"`
   Expected: PASS. The count is the baseline plus 3.
2. Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
   Expected: both pass.

- [ ] **Step 6: Commit**

```bash
git diff --stat
git add src/test/scala/oathdigital/gameplay/powers/campaign/SignalTreesSuite.scala \
  docs/ROADMAP.md
git commit -m "test(batch7): pin that an unchosen Signal Trees reveals nothing"
```
