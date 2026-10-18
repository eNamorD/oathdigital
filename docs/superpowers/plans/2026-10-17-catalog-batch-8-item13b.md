# Catalog Batch 8, Item 13b (Campaign Kills at Their Moment) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Campaign kills the attacker's skull losses at the attack result and the sacrifices when they are chosen, before the defense roll. `CampaignLosses` kills only the defeat's half and the defender's dead. No card's outcome changes, and the log follows the new moments.

**Architecture:**

- **Two new windows.** `PowerWindow.CampaignSkullLosses` follows the attack result window, and `PowerWindow.CampaignSacrificeLosses` follows the sacrifice decision. Each is a root-level `Sequence` whose children come from `CampaignBattle.earlyKill`: a `Branch` that may ask the origin split question, then a `BuildOps` that kills.
- **The split reads the live board.** Both children read `CampaignSetup.originForce(ready, actor)`, the board and origin warbands as they stand. Nothing runs between the question and the kill. A resume skips the earlier siblings of the parked node (`ProcedureWalker.walkChildren`), so once a window's kill has run its question is never selected again.
- **The losses keep the durable result.** `RecordCampaignResult` now runs after the two early kills, so `CampaignResult.originForce` records the board and site as the losses find them. `attackerDeaths` becomes the defeat's half alone, and the half's split keeps the id `campaign.origin-deaths`.
- **Each split has its own id:** `campaign.skull-origin-deaths`, `campaign.sacrifice-origin-deaths` and `campaign.origin-deaths`.
- **Careful Plans** moves five attack steps, not three: the attack roll, the attack result, the skull losses, the sacrifice and the sacrifice losses.
- **Hospital and Cursed Cauldron** wrap the two new windows with the shared loss counter (`LossRewrite.counted`, `returns = false`). Their placement and gain still come once, at the end.
- **The attack line** counts the skull faces as the scoring counts them (`AttackScore.skullFaces`). It adds ", killing K warbands" when the kills differ from the faces. The closing loss line already reads only the operations after `RecordCampaignResult`, so it counts only the half with no change.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`.

**Spec:** Read these before starting:

- `docs/superpowers/specs/2026-10-17-campaign-kills-at-their-moment-design.md`: the whole design. This plan implements it.
- `docs/architecture/bounded-campaign.md`: the step order and the losses.
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: slice 13, "Deaths" and "Loss powers".
- These hold what this item changes: `gameplay/actions/campaign/CampaignProcedure.scala`, `CampaignBattle.scala`, `CampaignSetup.scala` and `CampaignOutcome.scala`; `gameplay/powers/campaign/CarefulPlans.scala`, `Hospital.scala`, `CursedCauldron.scala`, `SparedForce.scala` and `AttackScore.scala`; `application/gamelog/CampaignLines.scala`.

**Starts from:** `main` at `545e0c08` or later, with this plan committed. Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Departures from the design

1. **The force cap.** The design's test "Three skull faces against a force of two kill two" cannot be rolled: the attack pool holds one die per committed warband. The cap shows only when the skulls kill more than one warband each (Weeping Banner) or count twice (Lancers). Task 1 tests Weeping Banner with an odd force, and Task 3 tests the line's wording for a capped kill directly.
2. **`originDeathRange` keeps its signature.** It takes an `OriginForce`, which already carries B (`onBoard`) and S (`atSite`). The early kills build that value from the live board with `CampaignSetup.originForce`, and the losses read it from the result.
3. **`CampaignResult.originForce` is recorded after the early kills.** The field is unchanged. Its meaning is now "the board and the origin as the losses find them". When the early kills empty the origin, the field is `None`, and the half dies from the board.

Task 4 records these in the design.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). `CampaignBattle.scala` is at 255 lines, `CampaignProcedure.scala` at 247 and `CampaignLines.scala` at 243.
- **Import rules** (`BackendArchitectureSuite`):
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
  - Engine files under `gameplay/walker` and `gameplay/operations` never name a power, in code or comments. This plan changes none of them.
- **Line width.** Scala lines stay at or under 80 characters.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase. Leave the untracked `HiddenScopeProbeSuite.scala` alone.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
  - Run `ExitWorktree` with `keep` before merging into `main`.
- **Commit trailer:** `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. A subagent with another model uses its own model's line.
- **Test helper names** say what they build. A doc comment does not excuse a vague name.
- **Old replays need no support.** The tree's shape changes, and old journals need not replay.
- **Log lines omit the actor.** The attack and sacrifice lines name nobody.
- **Lessons from the Campaign suites:**
  - `CampaignFixture.board(warbands, supply)` has p1 (the actor) at Ancient City, which the bandits rule with 2 warbands. Ancient City prints 2 defense dice.
  - `WalkerDiceFixture.dice(attack, defense)` (exported by `CampaignFixture`) needs exactly one face per die in the pool. A Conquest pool holds one attack die per committed warband and the targets' printed defense dice. A Raid's pawn adds 2 defense dice.
  - `PlanDriver.winningDice` rolls a sword on every attack die and `losingDice` a hollow sword; both roll blank defense dice.
  - `TwoSwordsSkull` scores 2 when its skull is paid; a hollow sword scores half, rounded down over the roll; `TwoShields` scores 2. A defender adds the warbands at the targets (Conquest) or on their board (Raid).
  - `PlanDriver.Run.finishPlanWindows` finishes the plan windows and answers the sacrifice and the placement with 0. It stops at any other question, so it stops at the new split questions.
  - `rivalAloneAtASiteTheActorHolds(board, atSite, rivalBoard = 3)` and `campaignQueuedFrom(game, b, away, source)` start a Wild Allies Raid from a site where p1 has `atSite` warbands.
- **Names, exactly:**
  - `PowerWindow.CampaignSkullLosses` (key `campaign.skull-losses`) and `PowerWindow.CampaignSacrificeLosses` (key `campaign.sacrifice-losses`).
  - `CampaignIds.skullOriginDeaths = "campaign.skull-origin-deaths"` and `CampaignIds.sacrificeOriginDeaths = "campaign.sacrifice-origin-deaths"`. `CampaignIds.originDeaths` stays `"campaign.origin-deaths"`, for the half.
  - `CampaignAnswers.originDeaths(pending: PendingTree, id: String): Option[Int]`.
  - In `CampaignBattle`: `skullDeaths(ready): Int`, `attackerDeaths(result, spared): Int`, `originSplit(catalog, actor, id, origin: Option[OriginForce], deaths): Vector[Operation]`, `attackerKills(ready, attacker, id, origin, deaths, pending): Either[OathViolation, Vector[CoreOperation]]` and `earlyKill(catalog, actor, id, deaths: (ReadyGame, PendingTree) => Int): Vector[Operation]`.
  - `AttackScore.skullFaces(faces: Vector[AttackDieFace], rules: AttackRules): Int`.
  - `CampaignLines.attack(score: Int, skulls: Int, kills: Int): String`, in a new `private[gamelog] object CampaignLines` in `CampaignLines.scala`.
  - `PlanDriver.killedBeforeTheDefenseRoll(events: Vector[OathEvent], kill: CoreOperation): Boolean`.

---

### Task 1: The three kills at their moments

**Files:**
- Modify: `src/main/scala/oathdigital/model/PowerWindow.scala` (after `CampaignAttackResult`)
- Modify: `src/main/scala/oathdigital/model/CampaignTypes.scala:177-178` (the `originForce` doc)
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala` (`CampaignIds`, `CampaignSetup.originForce` doc, `CampaignAnswers.originDeaths`)
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignBattle.scala:104-225`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala:8-22, 81-115`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/CarefulPlans.scala:15-35`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/SparedForce.scala:12-16` (doc only)
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala` (one helper)
- Test: `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/campaign/CampaignOriginSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/CarefulPlansSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/WeepingBannerSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/BillowingFogSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/TravelingDoctorSuite.scala`

**Interfaces:**
- Consumes: `CampaignSetup.originForce(ready, actor): Option[OriginForce]`, `CampaignAnswers.sacrificed(pending): Int`, `OriginForce(site, atSite, onBoard)`.
- Produces: the two windows, the two ids, `CampaignAnswers.originDeaths(pending, id)`, and the `CampaignBattle` functions under "Names, exactly". Task 2 wraps the windows. `PlanDriver.killedBeforeTheDefenseRoll` is used again in Task 2's suites only if a reviewer asks for it.

- [ ] **Step 1: Add the event-order helper to `PlanDriver`**

Add `RollPayload` to the `oathdigital.gameplay.walker` import of `PlanDriver.scala`, then add inside `object PlanDriver`, after `awaits`:

```scala
  /** Whether `kill` was recorded in `events` before the defense pool was
    * rolled there. */
  def killedBeforeTheDefenseRoll(events: Vector[OathEvent],
      kill: CoreOperation): Boolean =
    val steps = events.collect { case step: WalkerStepRecorded => step }
    val killed = steps.indexWhere(_.ops.contains(kill))
    val rolled = steps.indexWhere(_.payload match
      case RollPayload(pool, _, _) => pool == CampaignIds.defensePool
      case _ => false)
    killed >= 0 && rolled > killed
```

- [ ] **Step 2: Write the failing moment tests in `CampaignProcedureSuite`**

Add `import oathdigital.gameplay.powers.campaign.PlanDriver.killedBeforeTheDefenseRoll`. In the "battle" section, after the `committed` helper, add:

```scala
  /** The size of each kill of `player`'s board warbands in `events`. */
  private def boardKills(events: Vector[OathEvent], player: PlayerId)
      : Vector[Int] = ops(events).collect {
    case Kill(Piece.Warbands(_, count),
        PositionedLocation(Location.PlayArea(`player`), _, _)) => count
  }

  /** `count` of `b`'s actor's board warbands, killed. */
  private def actorBoardKill(b: Board, count: Int): CoreOperation =
    Kill(Piece.Warbands(ForceKind.Exile(b.player(b.actor).lineage), count),
      PositionedLocation(Location.PlayArea(b.actor)))
```

Then add these tests after "the sacrifice decision is bounded by the force the skulls left, and shows the roll":

```scala
  test("a skull's warband dies at the attack result, before the sacrifice " +
      "is asked"):
    val b = board(warbands = 5, supply = 7)
    val faces = Vector(AttackDieFace.TwoSwordsSkull, AttackDieFace.OneSword)
    val game = rules(CampaignFixture.dice(faces, blanks(b)))
    val start = committed(game, b, 2)
    parked.assertParked(start.state, ActionRef.Campaign, CampaignIds.sacrifice,
      b.actor)
    assertEquals(boardKills(start.events, b.actor), Vector(1))
    assertEquals(Look(start.state).boardWarbands(b.actor), 4)

  test("a sacrificed warband dies before the defense is rolled"):
    val b = board(warbands = 5, supply = 7)
    val game = rules(CampaignFixture.dice(sword(2), blanks(b)))
    val start = committed(game, b, 2)
    val won = resolved(game, start, CampaignIds.sacrifice,
      ChooseAmountAnswer(1))
    assert(killedBeforeTheDefenseRoll(won.events, actorBoardKill(b, 1)),
      won.events)

  test("a defeat kills only half the survivors at the losses"):
    val b = board(warbands = 5, supply = 7)
    // A paid skull and three hollow swords make 3, against two double
    // shields and the origin's two bandits.
    val faces = AttackDieFace.TwoSwordsSkull +:
      Vector.fill(3)(AttackDieFace.HollowSword)
    val game = rules(CampaignFixture.dice(faces,
      Vector.fill(printed(b))(DefenseDieFace.TwoShields)))
    val start = committed(game, b, 4)
    assertEquals(Look(start.state).boardWarbands(b.actor), 4)
    val done = resolved(game, start, CampaignIds.sacrifice,
      ChooseAmountAnswer(0))
    assertEquals(result(done.state).attackerWins, false)
    // Three survive the skull; half of them, rounded down, die.
    assertEquals(boardKills(done.events, b.actor), Vector(1))
    assertEquals(Look(done.state).boardWarbands(b.actor), 3)

  test("a victory kills only the skull and the sacrifice"):
    val b = board(warbands = 5, supply = 7)
    val faces = Vector(AttackDieFace.TwoSwordsSkull, AttackDieFace.OneSword,
      AttackDieFace.OneSword)
    val game = rules(CampaignFixture.dice(faces, blanks(b)))
    val start = committed(game, b, 3)
    val won = resolved(game, start, CampaignIds.sacrifice,
      ChooseAmountAnswer(1))
    assertEquals(result(won.state).attackerWins, true)
    parked.assertParked(won.state, ActionRef.Campaign, CampaignIds.placement,
      b.actor)
    assertEquals(boardKills(start.events, b.actor), Vector(1))
    assertEquals(boardKills(won.events, b.actor), Vector(1))
    assertEquals(Look(won.state).boardWarbands(b.actor), 3)
```

In "a Conquest victory rolls both dice by itself…", reword the comment "The board is untouched until the losses" to "With no skull and no sacrifice, the board is untouched until the losses".

- [ ] **Step 3: Write the failing Careful Plans and Weeping Banner tests**

In `CarefulPlansSuite`, after "the defense rolls first, and the sacrifice is asked with both rolls known", add:

```scala
  test("chosen, the skull's warband dies once the attack is rolled, and the " +
      "sacrifice before the losses"):
    val b = attacker(favor = 1)
    val game = rules(scripted(defenseFaces, attackFaces))
    val asked = commit(game, b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .finishAttackerPlans(b.actor)
    assert(awaits(asked, b.actor, CampaignIds.sacrifice))
    assertEquals(Look(asked.state).boardWarbands(b.actor), 4)
    val done = asked.answer(b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(1))
    assertEquals(Look(done.state).boardWarbands(b.actor), 3)
```

In `WeepingBannerSuite`, after the first test, add:

```scala
  test("the four warbands die at the attack result, before the defense roll"):
    val b = defender
    val done = commit(rules(dice(skulls(4), shields)), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    val killed = Kill(Piece.Warbands(
      ForceKind.Exile(b.player(b.actor).lineage), 4),
      PositionedLocation(Location.PlayArea(b.actor)))
    assert(killedBeforeTheDefenseRoll(done.events, killed), done.events)

  test("with an odd force the cap kills every warband at the attack result"):
    val b = defender
    val done = commit(rules(dice(skulls(3), shields)), b, 3)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    val killed = Kill(Piece.Warbands(
      ForceKind.Exile(b.player(b.actor).lineage), 3),
      PositionedLocation(Location.PlayArea(b.actor)))
    assert(killedBeforeTheDefenseRoll(done.events, killed), done.events)
```

- [ ] **Step 4: Write the failing origin tests**

In `CampaignOriginSuite`, add after "a split with one possible value asks nothing and kills from the site":

```scala
  test("each kill asks its own split, over the board and site the earlier " +
      "kills left"):
    // Two on the board and four at the site commit six. A paid skull and
    // five hollow swords make 4, and a sacrifice 5, against two double
    // shields and the rival's three board warbands: a defeat.
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 2, atSite = 4)
    val attack = AttackDieFace.TwoSwordsSkull +:
      Vector.fill(5)(AttackDieFace.HollowSword)
    val game = rules(dice(attack, Vector.fill(2)(DefenseDieFace.TwoShields)))
    val name = catalog.site(away).get.name
    def asks(run: Run, id: String, least: Int, most: Int, dead: Int): Unit =
      assertEquals(parkedDecision(run), Some(id))
      run.query(b.actor) match
        case amount: DecisionQuery.ChooseAmount =>
          assertEquals((amount.min, amount.max, amount.suggested),
            (least, most, Some(least)))
          val noun = if dead == 1 then "warband" else "warbands"
          assertEquals(amount.heading, Some(s"Choose how many of the " +
            s"$dead dead $noun come from $name"))
        case other => fail(s"expected the $id question, got $other")
    val skulled = committed(game, campaignQueuedFrom(game, b, away, source),
      b, force = 6, raid = true).finishPlanWindows
    // The skull's one warband: from the board (2) or the site (4).
    asks(skulled, CampaignIds.skullOriginDeaths, 0, 1, 1)
    val sacrificing = skulled.answer(b.actor, CampaignIds.skullOriginDeaths,
      ChooseAmountAnswer(1))
    assertEquals(warbandsAt(sacrificing, away), 3)
    val sacrificed = sacrificing.answer(b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(1))
    // The sacrifice's one warband, over the board's 2 and the site's 3.
    asks(sacrificed, CampaignIds.sacrificeOriginDeaths, 0, 1, 1)
    val fought = sacrificed.answer(b.actor, CampaignIds.sacrificeOriginDeaths,
      ChooseAmountAnswer(0))
    assertEquals(fought.winner, Some(false))
    assertEquals(fought.result.originForce,
      Some(OriginForce(away, atSite = 3, onBoard = 1)))
    // Half the four survivors: the board holds only one, so one or two
    // come from the site.
    asks(fought, CampaignIds.originDeaths, 1, 2, 2)
    val done = fought.answer(b.actor, CampaignIds.originDeaths,
      ChooseAmountAnswer(2))
    assertEquals(Look(done.state).boardWarbands(b.actor), 1)
    assertEquals(warbandsAt(done, away), 1)
```

Rename "the result records the origin's warbands and the board's before any dies" to "the result records the origin's warbands and the board's as the losses find them". Its body is unchanged: no skull or sacrifice dies there.

- [ ] **Step 5: Write the Billowing Fog and Traveling Doctor tests, and rewrite the Fog split test**

In `BillowingFogSuite`, add after "a defeated attacker still loses its sacrifice":

```scala
  test("a defeated attacker loses its skull and its sacrifice, and only " +
      "the half is spared"):
    val base = board(warbands = 5, supply = 7)
    val b = withFogFor(base, base.actor)
    // A paid skull and three hollow swords make 3, and a sacrifice 4,
    // against two double shields and the origin's two bandits.
    val game = rules(dice(AttackDieFace.TwoSwordsSkull +:
      Vector.fill(3)(AttackDieFace.HollowSword),
      Vector.fill(2)(DefenseDieFace.TwoShields)))
    val done = commit(game, b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .finishAttackerPlans(b.actor)
      .answer(b.actor, CampaignIds.sacrifice, ChooseAmountAnswer(1))
      .finishPlanWindows
    assertEquals(done.winner, Some(false))
    assertEquals(Look(done.state).boardWarbands(b.actor), 3)
    assertEquals(said(BillowingFog, done.events), Vector(spared(b.actor)))
```

Rewrite "under Billowing Fog the origin split asks over the sacrifices only" as:

```scala
  test("under Billowing Fog only the sacrifice's split is asked, at the " +
      "sacrifice"):
    val (base, away) = rivalAloneAtASiteTheActorHolds(board = 1,
      atSite = 3, rivalBoard = 5)
    val b = withFogFor(base, base.actor)
    val game = rules(losingDice)
    val fought = committed(game, campaignQueuedFrom(game, b, away,
        PowerSourceRef.Card(CatalogNames.denizen("Wild Allies"))), b,
        force = 4, raid = true)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .answer(b.actor, CampaignIds.sacrifice, ChooseAmountAnswer(2))
    // The two sacrifices die now, before the defense roll: the board
    // holds one, so one or two come from the site.
    assert(awaits(fought, b.actor, CampaignIds.sacrificeOriginDeaths))
    fought.query(b.actor) match
      case amount: DecisionQuery.ChooseAmount =>
        assertEquals((amount.min, amount.max), (1, 2))
        val name = catalog.site(away).get.name
        assertEquals(amount.heading, Some("Choose how many of the 2 dead " +
          s"warbands come from $name"))
      case other => fail(s"expected the split question, got $other")
    // 2 from the dice and 2 sacrificed do not beat the pawn's two blank
    // dice and five board warbands. The spared half asks nothing.
    val done = fought.answer(b.actor, CampaignIds.sacrificeOriginDeaths,
      ChooseAmountAnswer(1)).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assert(!awaits(done, b.actor, CampaignIds.originDeaths))
    assertEquals(Look(done.state).boardWarbands(b.actor), 0)
    assertEquals(Look(done.state).forces(away), SiteForces.Occupied(
      Look(done.state).forceKind(b.actor), 2))
```

In `TravelingDoctorSuite`, add `import oathdigital.model.DecisionAnswer.ChooseAmountAnswer` and, after the first test:

```scala
  test("a defeated attacker loses its skull and its sacrifice, and only " +
      "the half is spared"):
    val base = board(warbands = 5, supply = 7)
    val b = withAdviser(base, card, Orientation.FaceUp)
    val game = rules(dice(AttackDieFace.TwoSwordsSkull +:
      Vector.fill(3)(AttackDieFace.HollowSword),
      Vector.fill(2)(DefenseDieFace.TwoShields)))
    val done = commit(game, b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .finishAttackerPlans(b.actor)
      .answer(b.actor, CampaignIds.sacrifice, ChooseAmountAnswer(1))
      .finishPlanWindows
    assertEquals(done.winner, Some(false))
    assertEquals(Look(done.state).player(b.actor).board.warbands, 3)
    assertEquals(lines(done), Vector(NoteText.Said("spared",
      s"${b.actor.value}'s force was spared.", covers = false), discarded))
```

The two "only the half is spared" tests pass before and after this task, since the batched kill also killed the skull and the sacrifice. They pin the ruling across the move.

- [ ] **Step 6: Run the new tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.campaign.CampaignOriginSuite oathdigital.gameplay.powers.campaign.CarefulPlansSuite oathdigital.gameplay.powers.campaign.WeepingBannerSuite oathdigital.gameplay.powers.campaign.BillowingFogSuite oathdigital.gameplay.powers.campaign.TravelingDoctorSuite"`
Expected: compile errors for `CampaignIds.skullOriginDeaths` and `CampaignIds.sacrificeOriginDeaths`. Comment those two tests out for one run to confirm the others fail on their boards: the skull test sees 5 warbands, the sacrifice and Weeping Banner tests find the kill after the defense roll, and the Careful Plans test sees 5. Restore them.

- [ ] **Step 7: Add the two windows**

In `PowerWindow.scala`, after `CampaignAttackResult`:

```scala
  /** The attack's skull losses die, right after the attack result has
    * its final count (catalog batch 8, item 13b). */
  case object CampaignSkullLosses extends CampaignWindow:
    val key = "campaign.skull-losses"
```

After `CampaignSacrificeSelection`:

```scala
  /** The sacrificed warbands die, before the defense roll (item 13b). */
  case object CampaignSacrificeLosses extends CampaignWindow:
    val key = "campaign.sacrifice-losses"
```

Neither joins `BattlePlan.afterOutcome`: both run before `RecordCampaignResult`.

- [ ] **Step 8: Add the ids and the answer reader**

In `CampaignIds`, replace the `originDeaths` entry and `all` with:

```scala
  /** How many of the skull losses die at a queued origin. */
  val skullOriginDeaths = "campaign.skull-origin-deaths"
  /** How many of the sacrificed warbands die at a queued origin. */
  val sacrificeOriginDeaths = "campaign.sacrifice-origin-deaths"
  /** How many of the defeat's half die at a queued origin. */
  val originDeaths = "campaign.origin-deaths"
  val all: Set[String] = Set(kind, defender, targets, force, attackerPlan,
    defenderPlan, sacrifice, placement, relocation, planSacrifice,
    skullOriginDeaths, sacrificeOriginDeaths, originDeaths)
```

In `CampaignAnswers`, replace `originDeaths(pending)` with:

```scala
  /** The answer to one of the origin split questions, `id`. */
  def originDeaths(pending: PendingTree, id: String): Option[Int] =
    latest(pending, id).collect:
      case DecisionAnswer.ChooseAmountAnswer(amount) => amount
```

Reword the doc of `CampaignSetup.originForce` to: "The origin's warbands and the board's as they stand, when a queued origin's warbands joined the force: what each early kill splits over, and what the result records for the losses."

In `CampaignTypes.scala`, replace the `originForce` sentence of the `CampaignResult` doc with: "`originForce` records the board and the queued origin as the losses find them, after the skull and sacrifice losses died, so the losses split the defeat's half between them. It is `None` once no warband of the force is left at the origin."

- [ ] **Step 9: Build each kill in `CampaignBattle`**

Replace lines 157-225 (from `attackerSpared` through `losses`) with the following. `conquestLosses` and `raidBoardLosses` stay as they are.

```scala
  /** Whether an attacker's plan spared a defeated force. */
  def attackerSpared(ready: ReadyGame): Boolean =
    ready.game.current.rollPools.contains(CampaignIds.attackerSpared)

  /** The skull losses, which die at the attack result: the attack's final
    * count, after every plan in the attack result window rewrote it. */
  def skullDeaths(ready: ReadyGame): Int =
    ready.game.current.rollOutcomes.get(CampaignIds.attackPool)
      .fold(0)(_.skulls)

  /** The attacker's dead at the losses: half the survivors, rounded down,
    * on a defeat, unless a plan spared them. The skull and sacrifice
    * losses died in their own windows (item 13b). */
  def attackerDeaths(result: CampaignResult, spared: Boolean): Int =
    val survivors = result.force - result.skullLosses - result.sacrificed
    if result.attackerWins || spared then 0 else survivors / 2

  /** How many of `deaths` may come from the origin: at least what the
    * board cannot hold, at most what the site holds. A toll beyond both
    * takes all the site holds. */
  def originDeathRange(origin: OriginForce, deaths: Int): (Int, Int) =
    val most = math.min(deaths, origin.atSite)
    (math.min(math.max(0, deaths - origin.onBoard), most), most)

  /** The question `id` that splits `deaths` of the attacker's dead between
    * their board and a queued origin standing as `origin`, asked only when
    * the split is a real choice. */
  def originSplit(catalog: ExecutableCatalog, actor: PlayerId, id: String,
      origin: Option[OriginForce], deaths: Int): Vector[Operation] =
    origin.toVector.flatMap { at =>
      val (least, most) = originDeathRange(at, deaths)
      val name = catalog.site(at.site).fold(at.site.value)(_.name)
      val dead = if deaths == 1 then "warband" else "warbands"
      Option.when(least < most)(Decide(id, actor,
        DecisionQuery.ChooseAmount(least, most, Some(s"Choose how many of " +
          s"the $deaths dead $dead come from $name"), "Choose",
          suggested = Some(least)))).toVector
    }

  /** The kill of `deaths` of the attacker's warbands: as many at the origin
    * as the answer to `id` says, within the range, else the least, and the
    * rest from the board. */
  def attackerKills(ready: ReadyGame, attacker: PlayerId, id: String,
      origin: Option[OriginForce], deaths: Int, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    ready.game.current.players.find(_.player == attacker).toRight(
      OathViolation.InvalidEventOrder(
        "the Campaign's attacker is not in the game")).map { player =>
      val kind = PlayerForceKind.of(ready, player)
        .getOrElse(ForceKind.Exile(player.lineage))
      val fromSite = origin.fold(0) { at =>
        val (least, most) = originDeathRange(at, deaths)
        CampaignAnswers.originDeaths(pending, id)
          .filter(n => n >= least && n <= most).getOrElse(least)
      }
      val board: Vector[CoreOperation] =
        Option.when(deaths - fromSite > 0)(Kill(
          Piece.Warbands(kind, deaths - fromSite),
          PositionedLocation(Location.PlayArea(attacker)))).toVector
      val site: Vector[CoreOperation] = origin.filter(_ => fromSite > 0)
        .toVector.map(at => Kill(Piece.Warbands(kind, fromSite),
          PositionedLocation(Location.Site(at.site))))
      board ++ site
    }

  /** A kill of the attacker's force in its own window, before the outcome:
    * the split question `id` over the board and the origin as they stand,
    * then the kill. Nothing runs between the two, so both read the same
    * board, and a resume never selects the question again once the kill
    * has run. `deaths` reads the walk. */
  def earlyKill(catalog: ExecutableCatalog, actor: PlayerId, id: String,
      deaths: (ReadyGame, PendingTree) => Int): Vector[Operation] = Vector(
    Branch((ready, pending) => originSplit(catalog, actor, id,
      CampaignSetup.originForce(ready, actor), deaths(ready, pending))),
    BuildOps((ready, pending) => attackerKills(ready, actor, id,
      CampaignSetup.originForce(ready, actor), deaths(ready, pending),
      pending)))

  /** The split of the defeat's half, read from the durable result, so the
    * step keeps its place when the losses have changed the board. */
  def originDeathsStep(catalog: ExecutableCatalog, actor: PlayerId,
      result: CampaignResult, spared: Boolean): Vector[Operation] =
    originSplit(catalog, actor, CampaignIds.originDeaths, result.originForce,
      attackerDeaths(result, spared))

  /** Step 8: kill the defeated warbands. The attacker loses half the
    * survivors on a defeat unless a plan spared them, split by the answer
    * to `originDeaths` when a queued origin's warbands are in the force.
    * On a victory every warband at every target dies, a player defender's
    * survivors return from the supply to their board, and a Raid defender
    * loses half its board.
    */
  def losses(ready: ReadyGame, result: CampaignResult, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    attackerKills(ready, result.attacker, CampaignIds.originDeaths,
      result.originForce, attackerDeaths(result, attackerSpared(ready)),
      pending).map(_ ++ (if !result.attackerWins then Vector.empty
        else result.kind match {
          case CampaignKind.Conquest => conquestLosses(ready, result)
          case CampaignKind.Raid => raidBoardLosses(ready, result)
        }))
```

Also reword two docs that no longer hold:
- `defenseResultOps`: "It is written here, before any defender warband dies, so the victor never reads the board."
- `result`: "The durable record of this battle, built from the recorded outcomes and the answers, after the skull and sacrifice losses and before the losses."

- [ ] **Step 10: Walk the two windows in `CampaignProcedure`**

In `battle`, replace the `sacrificeStep(actor),` line with:

```scala
    Sequence(CampaignBattle.earlyKill(catalog, actor,
      CampaignIds.skullOriginDeaths,
      (ready, _) => CampaignBattle.skullDeaths(ready)),
      Some(PowerWindow.CampaignSkullLosses)),
    sacrificeStep(actor),
    Sequence(CampaignBattle.earlyKill(catalog, actor,
      CampaignIds.sacrificeOriginDeaths,
      (_, pending) => CampaignAnswers.sacrificed(pending)),
      Some(PowerWindow.CampaignSacrificeLosses)),
```

In the object's doc, replace "roll the attack, sacrifice, roll the defense, declare the victor, kill the defeated warbands, resolve the victory." with "roll the attack and kill its skull losses, sacrifice and kill the sacrificed, roll the defense, declare the victor, kill the defeated warbands, resolve the victory." Add after the paragraph that ends "because the losses change the board.":

```scala
  * The skull and sacrifice losses die in their own windows, before the
  * defense roll (catalog batch 8, item 13b). Each asks its origin split
  * over the board as that kill finds it.
```

- [ ] **Step 11: Move five attack steps in Careful Plans**

In `CarefulPlans.scala`, set `private val AttackSteps = 5` and reword its doc: "The attack roll, the attack result, the skull losses, the sacrifice and the sacrifice losses, which come before the defense roll and its result." In the object's doc, replace "the attack roll, the attack result and the sacrifice, then the defense roll and the defense result" with "the attack roll, the attack result, the skull losses, the sacrifice and the sacrifice losses, then the defense roll and the defense result", and add after "so the sacrifice is decided with both rolls known.": "The skull losses still die right after the attack result, and the sacrifice right after it is chosen."

- [ ] **Step 12: Reword Spared Force's doc**

In `SparedForce.scala`, replace the first bullet with:

```scala
  *  - A defeated attacker still loses its skulls and its sacrifice, which
  *    die in their own windows before the losses (item 13b), but not half
  *    of what survived. Choosing the plan records the spare
  *    (`CampaignIds.attackerSpared`), which the losses read, so the split
  *    of the half between board and a queued origin is never asked.
```

In `CampaignSetup.scala`, reword the `attackerSpared` doc's last sentence to "the losses then kill none of the attacker's force. It is never rolled, and it is cleared with the others when the Campaign ends."

- [ ] **Step 13: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.campaign.CampaignOriginSuite oathdigital.gameplay.powers.campaign.*"`
Expected: PASS.

Run: `./sbtw "test"`
Expected: PASS. A suite that fails here pins the batched kill: it reads the attacker's board at a park between the attack result and the losses, or answers `campaign.origin-deaths` for a skull or sacrifice kill. Change its expectation to the moment rule (the skull dies at the attack result, the sacrifice when chosen, the half at the losses) and name each such suite in the commit message. The game log suites should pass unchanged: no script there loses a skull or a sacrifice on a defeat.

- [ ] **Step 14: Commit**

```bash
git add src/main/scala/oathdigital/model/PowerWindow.scala \
  src/main/scala/oathdigital/model/CampaignTypes.scala \
  src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala \
  src/main/scala/oathdigital/gameplay/actions/campaign/CampaignBattle.scala \
  src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/CarefulPlans.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/SparedForce.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala \
  src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala \
  src/test/scala/oathdigital/gameplay/campaign/CampaignOriginSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/CarefulPlansSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/WeepingBannerSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/BillowingFogSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/TravelingDoctorSuite.scala
git commit -m "feat(batch8): a Campaign kills its skulls and sacrifices at their moment

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Hospital and Cursed Cauldron count the early kills

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/Hospital.scala:23-34, 83-85`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/CursedCauldron.scala:15-22, 62-71`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/HospitalSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/CursedCauldronSuite.scala`

**Interfaces:**
- Consumes: `PowerWindow.CampaignSkullLosses`, `PowerWindow.CampaignSacrificeLosses` (Task 1); `LossRewrite.counted(pool, kind, owner, returns)`.
- Produces: nothing new for later tasks.

- [ ] **Step 1: Write the failing Hospital test**

In `HospitalSuite`, add `import oathdigital.model.DecisionAnswer.ChooseAmountAnswer`, and after "a defeated attacker's dead are placed on Hospital's site at the end":

```scala
  test("a defeated attacker's skull and sacrifice are saved with the half"):
    val base = board(warbands = 5, supply = 7)
    val b = hospitalFor(base, base.actor, hospitalSite)
    // A paid skull and three hollow swords make 3, and a sacrifice 4,
    // against two double shields and the origin's two bandits.
    val game = rules(dice(AttackDieFace.TwoSwordsSkull +:
      Vector.fill(3)(AttackDieFace.HollowSword),
      Vector.fill(2)(DefenseDieFace.TwoShields)))
    val done = commit(game, b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .finishAttackerPlans(b.actor)
      .answer(b.actor, CampaignIds.sacrifice, ChooseAmountAnswer(1))
      .finishPlanWindows
    assertEquals(done.winner, Some(false))
    // The skull, the sacrifice and half the two survivors die, and all
    // three come back to Hospital's site.
    assertEquals(Look(done.state).player(b.actor).board.warbands, 2)
    assertEquals(Look(done.state).forces(hospitalSite),
      SiteForces.Occupied(kind(b, b.actor), 5))
    assertEquals(lines(done), Vector(placed(3, b.actor, hospitalSite)))
```

- [ ] **Step 2: Write the failing Cursed Cauldron test**

In `CursedCauldronSuite`, add `import oathdigital.model.DecisionAnswer.ChooseAmountAnswer`, and after "a victorious defender gains a warband per attacker warband killed":

```scala
  test("a victorious defender gains for the attacker's skull and sacrifice " +
      "too"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withRelicFor(base, base.rival, relic)
    val before = b.player(b.rival).board.warbands
    // A paid skull and three hollow swords make 3, and a sacrifice 4,
    // against two double shields and the rival's two warbands at the
    // origin.
    val game = rules(dice(AttackDieFace.TwoSwordsSkull +:
      Vector.fill(3)(AttackDieFace.HollowSword),
      Vector.fill(2)(DefenseDieFace.TwoShields)))
    val picked = commit(game, b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    val planned =
      if awaits(picked, b.rival, CampaignIds.defenderPlan) then
        picked.finishDefenderPlans(b.rival)
      else picked
    val done = planned.answer(b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(1)).finishPlanWindows
    // The skull, the sacrifice and half the two survivors died.
    assertEquals(done.winner, Some(false))
    assertEquals(Look(done.state).boardWarbands(b.rival), before + 3)
    assertEquals(said(CursedCauldron, done.events),
      Vector(gained(b.rival, 3)))
```

- [ ] **Step 3: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.HospitalSuite oathdigital.gameplay.powers.campaign.CursedCauldronSuite"`
Expected: the two new tests FAIL. Hospital places 1 warband, not 3; the Cauldron gains 1, not 3. Every other test passes.

- [ ] **Step 4: Wrap the new windows**

In `Hospital.scala`, replace `wrapping` with:

```scala
  override def wrapping: Map[PowerWindow, BattlePlan.Wrap] = Map(
    PowerWindow.CampaignPlanApplication -> counted(returns = false),
    PowerWindow.CampaignSkullLosses -> counted(returns = false),
    PowerWindow.CampaignSacrificeLosses -> counted(returns = false),
    PowerWindow.CampaignLosses -> counted(returns = true))
```

In its doc, add a bullet after the plan application one:

```scala
  *  - the skull and sacrifice losses, which die in their own windows
  *    (`CampaignSkullLosses`, `CampaignSacrificeLosses`) before the
  *    outcome (item 13b);
```

and reword the losses bullet's opening to "every kill in the losses (`CampaignLosses`): the defeat's half and the defender's dead, including the kills Sticky Fire and Shield Wall add there…".

In `CursedCauldron.scala`, add to `wrapping`, before the `CampaignLosses` entry:

```scala
    PowerWindow.CampaignSkullLosses -> ((ctx, use, children) =>
      counted(ctx, use, children, returns = false)),
    PowerWindow.CampaignSacrificeLosses -> ((ctx, use, children) =>
      counted(ctx, use, children, returns = false)),
```

In its doc, replace "in the plans applied after it and in the losses" with "in the plans applied after it, in the skull and sacrifice losses (item 13b), whether or not its user wins, and in the losses".

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/Hospital.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/CursedCauldron.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/HospitalSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/CursedCauldronSuite.scala
git commit -m "feat(batch8): Hospital and Cursed Cauldron count the early Campaign kills

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: The attack line counts skull faces and kills

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/AttackScore.scala:44-67`
- Modify: `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala:1-21, 71-107`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/AttackScoreSuite.scala`
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogCampaignSuite.scala`
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogRareLinesSuite.scala`

**Interfaces:**
- Consumes: `AttackScore.rules(ready, pending, actor)`, `CampaignBattle.attackFacesOf(ready)`, `LogJournal.answers(run, at)`.
- Produces: `AttackScore.skullFaces` and `CampaignLines.attack`, used only here.

- [ ] **Step 1: Write the failing tests**

In `AttackScoreSuite`, add:

```scala
  test("the skull faces count each skull rolled, twice when doubled"):
    val faces = Vector(TwoSwordsSkull, OneSword, TwoSwordsSkull)
    assertEquals(AttackScore.skullFaces(faces, AttackRules()), 2)
    assertEquals(AttackScore.skullFaces(faces, AttackRules(doubled = true)),
      4)
    assertEquals(AttackScore.skullFaces(faces, AttackRules(skullKills = 2)),
      2)
```

In `GameLogCampaignSuite`, add after "a total a plan rewrites in the same segment is told once, as rewritten":

```scala
  test("the attack line counts the skull faces, and the kills when they " +
      "differ"):
    assertEquals(CampaignLines.attack(5, skulls = 0, kills = 0), "Attack: 5")
    assertEquals(CampaignLines.attack(5, skulls = 1, kills = 1),
      "Attack: 5 with 1 skull")
    assertEquals(CampaignLines.attack(6, skulls = 2, kills = 4),
      "Attack: 6 with 2 skulls, killing 4 warbands")
    assertEquals(CampaignLines.attack(4, skulls = 4, kills = 2),
      "Attack: 4 with 4 skulls, killing 2 warbands")
    // Ignored skulls kill nothing, and the line names none.
    assertEquals(CampaignLines.attack(8, skulls = 2, kills = 0), "Attack: 8")

  test("under Weeping Banner two skulls read as killing four warbands"):
    val script = recordedEventsBoard
    val steps = script.history.steps
    val faces = Vector[AttackDieFace](AttackDieFace.TwoSwordsSkull,
      AttackDieFace.TwoSwordsSkull, AttackDieFace.OneSword,
      AttackDieFace.OneSword)
    /** The state the attack result starts from, with `faces` rolled. */
    def attackRolled(state: OathState): OathState = state match
      case OathState.Ready(ready) => OathState.Ready(
        ready.updateCurrent(current => current.copy(rollOutcomes =
          current.rollOutcomes.updated(CampaignIds.attackPool, RollOutcome(
            CampaignIds.attackPool, faces.size, faces, skulls = 0,
            score = 0)))))
      case other => other
    val tail = Vector[OathEvent](
      WalkerStepRecorded("attack-result", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.OperationApplied("attack")), Vector(
          ModifyRollOutcome(CampaignIds.attackPool, Some(4), Some(6))),
        Vector.empty),
      WalkerCompleted(ActionRef.Campaign))
    val attacks = texts(formatAppended(script, tail,
        stateBefore = attackRolled).filter(_.sequence >= steps.size))
      .filter(_.startsWith("Attack: "))
    assertEquals(attacks, Vector("Attack: 6 with 2 skulls, killing 4 warbands"))
```

In `GameLogRareLinesSuite`, add after "a defeated attacker's loss line has no subject and the bandits win":

```scala
  test("a defeated attacker's loss line counts only the half killed at the " +
      "losses"):
    val result = CampaignResult(actor, CampaignKind.Conquest,
      CampaignDefender.Bandits, targetSites = Vector(site),
      raidTargets = Vector.empty, force = 4, attackFaces = Vector.empty,
      attackScore = 3, skullLosses = 1, sacrificed = 1,
      defenseFaces = Vector.empty, defenseScore = 6, attackerWins = false)
    val one = Kill(Piece.Warbands(ForceKind.Exile(lineage(actor)), 1),
      PositionedLocation(Location.PlayArea(actor)))
    // The skull and the sacrifice died before the result was recorded.
    val lines = posted(ActionRef.Campaign, one, one,
      RecordCampaignResult(result), one)
    assert(lines.contains("Lost 1 warband"), lines)
    val spared = posted(ActionRef.Campaign, one, one,
      RecordCampaignResult(result))
    assert(!spared.exists(_.startsWith("Lost ")), spared)
```

The loss-line test passes at once: the closing line already reads only the operations after `RecordCampaignResult`. It pins that the line counts the half alone.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.AttackScoreSuite oathdigital.application.gamelog.GameLogCampaignSuite oathdigital.application.gamelog.GameLogRareLinesSuite"`
Expected: compile errors for `AttackScore.skullFaces` and `CampaignLines.attack`.

- [ ] **Step 3: Add `skullFaces`**

In `AttackScore`, before `of`:

```scala
  /** The skull faces the attack counts: each two-swords-and-skull face
    * rolled, twice with Lancers. */
  def skullFaces(faces: Vector[AttackDieFace], rules: AttackRules): Int =
    faces.count(_ == AttackDieFace.TwoSwordsSkull) *
      (if rules.doubled then 2 else 1)
```

In `of`, replace `val skulls = count(AttackDieFace.TwoSwordsSkull)` with `val skulls = skullFaces(faces, rules)`.

- [ ] **Step 4: Word the attack line**

In `CampaignLines.scala`, add `import oathdigital.gameplay.powers.campaign.AttackScore`. Pass the run to `recorded`: in `lines`, call `recorded(journal, run, at, viewer)`, and change its signature to `private def recorded(journal: LogJournal, run: Run, at: Int, viewer: Option[PlayerId]): Vector[Posted]`. Replace the attack case with:

```scala
      case (OpStep(ModifyRollOutcome(CampaignIds.attackPool, _,
          Some(score)), _, after), index)
          if !rewritten(index, CampaignIds.attackPool) =>
        val kills = after.game.current.rollOutcomes
          .get(CampaignIds.attackPool).fold(0)(_.skulls)
        val skulls = AttackScore.skullFaces(
          CampaignBattle.attackFacesOf(after), AttackScore.rules(after,
            PendingTree(Vector.empty, journal.answers(run, at)), run.actor))
        Posted(LogKind.Roll, Vector(Text(
          CampaignLines.attack(score, skulls, kills))))
```

At the end of the file, add the companion:

```scala
private[gamelog] object CampaignLines:
  /** "Attack: 5", "Attack: 5 with 2 skulls" or, when the skulls kill
    * another number of warbands, "Attack: 5 with 2 skulls, killing 4
    * warbands". A roll whose skulls kill nothing names none. */
  def attack(score: Int, skulls: Int, kills: Int): String =
    if kills == 0 then s"Attack: $score"
    else
      val killing =
        if kills == skulls then ""
        else s", killing $kills ${plural(kills, "warband", "warbands")}"
      s"Attack: $score with $skulls ${plural(skulls, "skull", "skulls")}" +
        killing
```

Reword the class doc's first sentence to "Every Campaign line after the start line (spec, "Campaign"), each posted where its facts complete. The rolls themselves are detail lines, and the attack line counts the skull faces and, when they differ, the warbands they kill (item 13b)."

- [ ] **Step 5: Run the log and scoring suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.AttackScoreSuite oathdigital.application.gamelog.*"`
Expected: PASS. The `raid.actor.log` and `raid.other.log` fixtures and the Captains scripts roll no skull, so they stay as they are. If a fixture differs, regenerate it the way its suite's failure message says and check that the only change is an attack line's skull count.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/AttackScore.scala \
  src/main/scala/oathdigital/application/gamelog/CampaignLines.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/AttackScoreSuite.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogCampaignSuite.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogRareLinesSuite.scala
git commit -m "feat(batch8): the attack line counts skull faces and the warbands they kill

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Docs, gates and status

**Files:**
- Modify: `docs/architecture/bounded-campaign.md` (the step diagram, "Losses and resolution")
- Modify: `docs/superpowers/specs/2026-10-17-campaign-kills-at-their-moment-design.md` (Status, Testing, a "Departures" section)
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md` (slice 13, "Deaths")
- Modify: `docs/superpowers/specs/2026-10-05-catalog-batch-4-rulings.md` ("Losses")
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` (Status)
- Modify: `docs/ROADMAP.md` ("Now", item 13b, the line under the list)

- [ ] **Step 1: Update the bounded Campaign doc**

In the diagram, replace the lines from `CampaignAttackResult` to `Roll(campaign.defense, Automatic)` with:

```
  CampaignAttackResult                    -- capped attack written as a roll outcome
  Sequence(CampaignSkullLosses,           -- the skull losses die
    Decide(campaign.skull-origin-deaths)  -- only with a queued origin and a real split
    BuildOps(kill))
  Decide(campaign.sacrifice)              -- omitted when nothing survives the skulls
  Sequence(CampaignSacrificeLosses,       -- the sacrificed warbands die
    Decide(campaign.sacrifice-origin-deaths)
    BuildOps(kill))
  Roll(campaign.defense, Automatic)       -- CampaignDefenseRoll
```

Replace the first paragraph of "Losses and resolution" with:

```markdown
The attacker's dead die at three moments. The skull losses die in
`CampaignSkullLosses`, right after the attack result has its final count.
The sacrificed warbands die in `CampaignSacrificeLosses`, right after the
sacrifice and before the defense roll. `CampaignLosses` kills half the
survivors, rounded down, on a defeat, and the defender's dead. With a queued
origin's warbands in the force, each kill asks its own split question over
the board and the site as that kill finds them ([catalog batch 8, item
13b](../superpowers/specs/2026-10-17-campaign-kills-at-their-moment-design.md)).
On a victory every warband at every target dies. A player defender keeps
half the killed force, rounded up, returned from the supply to their board.
Losses are plain operations in named windows, not a policy registry.
```

Then search the doc for "before any warband dies" and "Nothing leaves the board", and reword any other sentence that says no warband dies before `CampaignLosses`.

- [ ] **Step 2: Update the item 13b design**

- Set its Status to "designed 2026-10-17; done 2026-10-17".
- Add a section "Departures" after "Docs", holding this plan's "Departures from the design" list in the design's own style.
- In "Testing", replace "**The force cap.** Three skull faces against a force of two kill two, and the line says so." with "**The force cap.** Under Weeping Banner, two skulls against a force of three kill three at the attack result, and the line wording for a capped kill is tested directly."

- [ ] **Step 3: Update the rulings**

In the batch 8 rulings, slice 13, "Deaths", replace:

```
  - The question is asked once, at the kill step. When ROADMAP item 13b
    moves each kill to its moment, this question moves with it.
```

with:

```
  - Each kill asks its own question, over the board and the site as that
    kill finds them: the skull losses at the attack result, the sacrifices
    when chosen, and the defeat's half at the kill step
    ([item 13b](2026-10-17-campaign-kills-at-their-moment-design.md)).
```

In the batch 4 rulings, "Losses", after "On a defeat today, the attacker loses skulls, sacrifice and half the surviving force.", add:

```markdown
Since catalog batch 8 item 13b, each dies at its moment: the skulls at
the attack result, the sacrifice when chosen and the half at the losses
([design](2026-10-17-campaign-kills-at-their-moment-design.md)).
```

- [ ] **Step 4: Run the full gate**

Run: `./sbtw "test"`
Expected: everything passes. The server count rises by 10 (Task 1) + 2 (Task 2) + 4 (Task 3) = 16 new tests.

Run: `python3 scripts/check-architecture.py` and `python3 scripts/check-markdown-links.py`.
Expected: both pass.

- [ ] **Step 5: Record the status**

- In the batch 8 design's Status line, write "Slices 1 to 15 and items 10b and 13b are done."
- In the ROADMAP's "Now" paragraph, write "slices 1 to 15 and items 10b and 13b are done".
- Replace item 13b with:

```
- [x] **13b. Campaign kills at their moment.** A Campaign kills the
  attacker's skull losses at the attack result, the sacrifices when they
  are chosen, and the defeat's half at the kill step, each with its own
  origin split question. Hospital and Cursed Cauldron count every kill, and
  the attack line counts the skull faces and the warbands they kill.
  Design: [Campaign kills at their moment](superpowers/specs/2026-10-17-campaign-kills-at-their-moment-design.md).
  Bear Traps applies at plan time, before any roll, so the move never
  changed what it sees; the claim that it would was mistaken.
```

- Change the line under the list to "Only items 13c and 14b of catalog batch 8 remain open."

```bash
git add docs/architecture/bounded-campaign.md \
  docs/superpowers/specs/2026-10-17-campaign-kills-at-their-moment-design.md \
  docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md \
  docs/superpowers/specs/2026-10-05-catalog-batch-4-rulings.md \
  docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md \
  docs/ROADMAP.md
git commit -m "docs(batch8): item 13b done

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
