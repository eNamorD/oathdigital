# Procedures Journal Their Own Notes, Slice 4 (Campaign) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Campaign journals its own Game Log lines, and the last two
inference files go: `CampaignLines` is deleted and `ActionLines` is folded
into `PowerLines` and `LogWords`. After this slice every procedure's line is
a note its own tree wrote.

**Architecture:**

- Campaign's twenty-three note keys and every builder live in one new file,
  `gameplay/actions/campaign/CampaignNotes.scala`. `CampaignProcedure`,
  `CampaignPlanChoice`, `CampaignOutcome`, `CampaignConquest` and
  `CampaignRaid` place the leaves; none of them words anything.
- **Every count is read from the step the note restates, never recomputed.**
  Four powers rewrite the Campaign's own loss operations in place
  (`SparedForce.spare` through `LossRewrite`, and `Keep` replaces the Raid's
  consequences outright), so arithmetic over the pre-loss board would state
  numbers that did not happen. Each closing note reads `NoteStates.previous`
  — the before and after of the leaf immediately above it.
- **Each note sits immediately after the leaf it restates, inside that
  leaf's own windowed node.** Inside, because a power hooked at the window
  appends to the node the window names (slice 3's `RecoverProcedure`
  lesson); immediately after the leaf, because a power that appends its own
  kills (Sticky Fire, Shield Wall) would otherwise become the note's
  `previous`.
- **The loss sentence becomes three.** `CampaignLines.losses` joined up to
  five clauses applied in three different nodes, which one note cannot span.
  Each node now writes its own sentence with its subject named. The one case
  whose subject is omitted — the loser is the actor — carries exactly one
  clause, so it is a flat key and the capitalisation that blocked this slice
  never arises.
- **One new argument kind**, `NoteArg.Things` over a closed `NoteThing` sum,
  serves both lines that join a variable list of mixed references: "Targets:
  …" and a won Raid's "Took … from …".
- The two totals move after their result windows and lose
  `CampaignLines.recorded`'s forward read to `segmentEnd`. `ActionLines`'
  `powerSource` is then the Game Log's only forward read, and it moves to
  `PowerLines` with the fold.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`.

**Spec:** Read these before starting:

- `docs/superpowers/specs/2026-10-16-procedures-journal-their-notes-design.md`
  — the whole design. This plan implements its slice 4. Read "Rulings",
  "Mechanism", "Campaign", "What the four application files become" and
  "Slices".
- `docs/superpowers/specs/2026-10-16-game-log-wording-ledger.md` — what this
  slice may not fix, and what it must record.
- `docs/superpowers/specs/2026-10-17-campaign-kills-at-their-moment-design.md`
  — batch 8 item 13b, which this slice starts from.
- `docs/architecture/bounded-campaign.md` — the Campaign's step order.

**Starts from:** `main` with **batch 8 item 13b merged**. That item moves the
skull and sacrifice kills into two new windows, moves `RecordCampaignResult`
after them, and reworded the attack line to count skull faces and the
warbands they kill. Every note placement and the three attack keys below are
written against its end state. **Do not start this slice before item 13b is
on `main`**: the tree it is written against does not exist until then.

Where this plan quotes code, read the file as it stands on `main` and apply
the change to what is there.

## What was settled before this slice started

Nine questions were answered at plan time. The answers bind the tasks.

1. **The loss sentence splits per node** (three keys), rather than widening
   `NoteStates` or applying every loss in one leaf.
2. **One new argument kind**, `NoteArg.Things`, serving both the targets
   line and the spoils line. This **amends ruling 7** ("three new argument
   kinds and no more"); Task 6 records the amendment.
3. **The title plan's activation line is reworded.** It reads the label its
   offer authored ("Oathkeeper title: add 1 defense die"), which a typed
   argument cannot carry. Two keys replace it with "{Blue} activated the
   Oathkeeper title" and "… the Usurper title". Recorded in the ledger.
4. **The two totals read after the lines powers in their result windows
   write**, where they read before them. Accepted and recorded; the forward
   read is deleted, not reproduced.
5. **The missing goldens land first**, in their own commit, so the
   migration's diff covers the branches no golden plays today.
6. **The synthetic-journal tests are replaced, not ported.** Six tests over
   hand-built journals exercise the inference rule, which ruling 9 deletes.
7. **The two card-naming state sets are shown to agree** for Campaign's
   cards, by test, rather than ruled on.
8. **The card-play line order** (the ledger's open question from slice 3)
   stays out of this slice; Task 6 adds a ROADMAP task for it.
9. **Six commits**, one per task.

## What is already true

Read these before writing code; three of the design's premises have moved.

- **`NegotiationLines` and `SetupLines` are already gone.** Slice 2 deleted
  them. The design's "What the four application files become" table is
  stale: only `CampaignLines` (243 lines) and `ActionLines` (109) remain, and
  `ActionLines` is already down to `campaignLines`, `usePowerLines` and four
  companion helpers.
- **`NoteArg.Clauses`, `NoteClause` and `NoteArg.Placements` exist** from
  slice 1, with codec cases, renderer cases in `PowerLines.argument` and
  round-trip tests in `PowerNoteWireSuite`. Nothing in production writes one
  yet; this slice is their first use. Two of the five `NoteClause` cases
  (`LostWarbands`, `SentTo`) are made unnecessary by the split and are
  deleted in Task 4.
- **`gameplay/actions` may import `gameplay/powers`.** No architecture rule
  forbids it; six procedures already import `gameplay.powers.NoteSupport`
  and `MinorActionProcedures` imports `VowOfWandering`. The attack note calls
  `AttackScore.skullFaces` and `AttackScore.rules` the same way item 13b's
  `CampaignLines` does.

## Global Constraints

- **Build rules.** `-Werror` with
  `-Wunused:imports,privates,locals,implicits,nowarn`: an unused import or
  an unused **private** member fails the build. An unused public companion
  member does not.
- **File size.** Production Scala files stay at or under 800 lines
  (`scripts/check-architecture.py` and `BackendArchitectureSuite`).
  `CampaignProcedure.scala` is at 247 lines, `CampaignBattle.scala` at 255,
  `PowerLines.scala` at 155, `LogWords.scala` at 169. Test files share the
  cap: **`LogScripts.scala` is at 800 exactly**, so not one line may be
  added to it.
- **Line width.** Scala lines stay at or under 80 characters.
- **Layer rules** (`BackendArchitectureSuite`): `gameplay` never imports
  `application`, `persistence`, `presentation`, `protocol`, `serialization`
  or `server`. A note's wording is `NoteKey`, `NotePart` and `NoteArg`, all
  `model`; a note's **kind** is `application`'s and is declared on the
  `ProcedureLog` entry, never beside the procedure (ruling 2).
- **Ruling 6 binds every commit.** A migration leaves every golden entry's
  spans, kind, depth and section byte-identical; only `sequence` and
  `ordinal` may move. The exceptions this slice is allowed are exactly the
  ones listed per task, and each is recorded in the ledger. No other wording
  changes, however tempting the ledger's entries are.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Stage explicit paths; never `git add -A`.
  Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Never use bare `git stash` / `git stash pop`:** the stash stack is
  shared with every worktree. Set work aside as a temporary WIP commit, or
  as a patch file in the scratchpad.
- **Worktree.** Work in a git worktree, never in the main checkout.
  `EnterWorktree` branches from `origin`, which lags local `main` by over a
  thousand commits: fast-forward the new branch to local `main` first
  (`git merge --ff-only main`), then symlink the main checkout's `.tooling`
  and `node_modules` into the worktree before the first `./sbtw`.
- **Commit trailer:** `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
  A subagent on another model uses its own model's line.
- **The full gate, green before every commit:**
  `./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py`
- **Rewriting fixtures.** `GAMELOG_GOLDEN=write ./sbtw "testOnly
  oathdigital.application.gamelog.GameLogGoldenSuite"` rewrites the goldens;
  `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly
  oathdigital.gameplay.powers.DeclaredNoteKeysSuite"` rewrites
  `src/test/resources/notes/declared-keys.txt`. **Review every rewritten
  line before committing it.** A golden diff that shows anything but a
  shifted `sequence.ordinal` on a migrated line is a defect.
- **The slice's own proof.** Before Task 2, record the goldens as they
  stand with the sequence numbers and card indices stripped:

```bash
mkdir -p "$SCRATCH/slice4-before"
for f in src/test/resources/gamelog/*.log; do
  sed -E 's/^[0-9]+\.[0-9]+ //; s/@[0-9]+//g' "$f" \
    > "$SCRATCH/slice4-before/$(basename "$f")"
done
```

  After Task 6, do the same into `slice4-after` and
  `diff -r "$SCRATCH/slice4-before" "$SCRATCH/slice4-after"`. The only
  differences allowed are the ones each task's "Done when" names.

## Lessons from slices 1 to 3

Every one of these cost a fix commit or a corrupted file. Read them.

- **Every leaf resets `NoteStates.previous`**, including a leaf that applies
  only bookkeeping. A note reads the leaf *immediately* above it, whatever
  that leaf is. Three defects in slice 2 and 3 were this.
- **A note after a windowed node reads after every leaf that node's own
  execution ran**, including a power's. Put the note inside the node, after
  the leaf it restates.
- **`OperationRewrite.ops` descends `Sequence`, `Repeat`, `Branch`,
  `BuildOps` and `RewritableComposite`**, skipping only a node with a window
  of its own. Wrapping a leaf and a note in a bare `Sequence` does not keep
  a power's splice out from between them.
- **Never slice a file on computed indices.** `ActionLines.scala` was
  corrupted twice that way. When Task 6 deletes members, assert the bounds
  (`assert start < end`) and assert each method's presence before and
  absence after.
- **A test that passes for the wrong reason is worse than none.** Slice 3's
  first ordering test matched a cost span, not the line under test. Before
  trusting a new assertion, break the production change and watch it fail.

---

## File Structure

**New:**

- `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignNotes.scala`
  — Campaign's `NoteSet`, its twenty-three keys, every note builder, and the
  readers that turn a step into a count or a card list. Nothing else words a
  Campaign line. Expect 380 to 430 lines; if it passes 800, split the
  readers into `CampaignNoteReads.scala` and keep the keys and builders
  here.
- `src/test/scala/oathdigital/application/gamelog/CampaignLogScripts.scala`
  — the three new golden scripts and their `named` vector.
- `src/test/scala/oathdigital/application/gamelog/GoldenScripts.scala` —
  the golden registry: `LogScripts.named ++ CampaignLogScripts.named`.
  `LogScripts` is at its cap and cannot hold the new entries.

**Modified:**

- `model/PowerNotes.scala` — `NoteArg.Things`, the `NoteThing` sum, and
  `NoteClause` loses two cases.
- `serialization/WalkerEventCodec.scala` — the `things` argument case and
  its decode; two clause cases go.
- `application/gamelog/PowerLines.scala` — renders `Things`; then takes
  `usePowerLines` and its helpers in Task 6.
- `application/gamelog/LogWords.scala` — takes `ActionLines`' companion
  helpers in Task 6.
- `application/gamelog/ProcedureLog.scala` — Campaign's entry becomes
  `Notes(kinds)`; `ActionLines` leaves the constructor.
- `gameplay/actions/campaign/CampaignProcedure.scala`,
  `CampaignPlanChoice.scala`, `CampaignOutcome.scala`,
  `CampaignConquest.scala`, `CampaignRaid.scala` — note leaves only.
- `gameplay/actions/ProcedureNotes.scala` — Campaign's keys.

**Deleted:** `application/gamelog/CampaignLines.scala`,
`application/gamelog/ActionLines.scala`.

---

### Task 1: The Campaign branches no golden plays

Today's two Campaign goldens play one Raid victory whose loss sentence has
three clauses, and one Conquest defeat that loses nothing. Nothing covers
the attacker's loss line, a won Raid's spoils, a Conquest victory's
placement or the bandits' loss. Those are four of the six closing lines this
slice rewrites. They land first, so the migration's diff covers them.

Two of the four are free: `CaptainsLogScripts` already builds a won and a
lost Captains Raid, exercised by `GameLogActionLineSuite`, and neither is in
the golden registry.

**Files:**
- Create: `src/test/scala/oathdigital/application/gamelog/CampaignLogScripts.scala`
- Create: `src/test/scala/oathdigital/application/gamelog/GoldenScripts.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogGoldenSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala:16`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` — **delete `all` only** (two lines; nothing else may grow, the file is at 800)
- Create: eight golden files under `src/test/resources/gamelog/`

**Interfaces:**
- Consumes: `LogScripts.atTable`, `LogScripts.named`, `Table`, `Dice`,
  `MenuPlay.start`, `CaptainsLogScripts.captainsRaidFromARuledSite`,
  `CaptainsLogScripts.captainsRaidLostWithASplit`.
- Produces: `GoldenScripts.named: Vector[(String, () => Script)]` and
  `GoldenScripts.all: Vector[Script]`, the golden registry every later task
  and both suites read; `CampaignLogScripts.raidTakingRelicAndBanner` and
  `CampaignLogScripts.conquestPlacingWarbands`.

- [ ] **Step 1: Write the two new scripts**

Create `CampaignLogScripts.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer,
  ChooseManyAnswer, ChooseOneAnswer}
import oathdigital.testkit.{Answers, CatalogNames, Dice, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import LogScripts.{atTable, raidAnswers}

/** The Campaign branches the older goldens never play: a won Raid's
  * spoils, and a Conquest victory's placement with the bandits' loss.
  * Kept beside `LogScripts`, which is at its size limit (procedures
  * journal their own notes design, slice 4).
  */
object CampaignLogScripts:
  private val relic = CatalogNames.relic("Wine of Welcome")

  /** p1 Raids p2, who shares Ancient City, holds Wine of Welcome faceup,
    * holds the People's Favor, has a facedown adviser and two favor. p1
    * targets the relic and the banner as well as the pawn, commits three
    * warbands and sacrifices them all, so the attack is six against p2's
    * three board warbands and blank defense dice: p1 wins and takes both.
    */
  def raidTakingRelicAndBanner(using munit.Location): Script =
    val (service, act) = atTable("raid-spoils", Table.start
      .pawn(p1, "Ancient City").pawn(p2, "Ancient City")
      .pawn(p3, "Buried Giant")
      .boardWarbands(p1, 3).boardWarbands(p2, 3)
      .relic(p2, relic)
      .peoplesFavor(Some(p2), favor = 2)
      .adviser(p2, "Birdsong", facedown = true)
      .favor(p2, 2)
      .banditsRefilled, Dice.raid)
    raidAnswers(act, {
      case park if park.decisionId == CampaignIds.targets =>
        ChooseManyAnswer(Vector(DecisionOptionRef.Relic(relic),
          DecisionOptionRef.Banner(Banner.PeoplesFavor)))
    }).after(MenuPlay.start(p1, ActionRef.Campaign))
    Script("raid-spoils", service, p1)

  /** p1 Conquests Ancient City, which the bandits rule with three
    * warbands, with four board warbands and no sacrifice: four swords
    * against three, so p1 wins, the bandits lose all three, and p1 places
    * two of the four survivors on the site.
    */
  def conquestPlacingWarbands(using munit.Location): Script =
    val (service, act) = atTable("conquest-placed", Table.start
      .pawn(p1, "Ancient City").pawn(p2, "Broken Peaks")
      .pawn(p3, "Buried Giant")
      .boardWarbands(p1, 4)
      .banditsRefilled, Dice.raid)
    val answers: Answers = {
      case Park(Decide(CampaignIds.force, _,
          amount: DecisionQuery.ChooseAmount, _, _, _, _), _, _, _) =>
        ChooseAmountAnswer(amount.max)
      case park if park.decisionId == CampaignIds.sacrifice =>
        ChooseAmountAnswer(0)
      case park if park.decisionId == CampaignIds.placement =>
        ChooseAmountAnswer(2)
      case park if park.decisionId == CampaignIds.attackerPlan ||
          park.decisionId == CampaignIds.defenderPlan =>
        ChooseOneAnswer(CampaignIds.finish)
    }
    act.withAnswers(answers).after(MenuPlay.start(p1, ActionRef.Campaign))
    Script("conquest-placed", service, p1)

  /** The scripts this file adds to the golden registry. */
  val named: Vector[(String, () => Script)] = Vector(
    "raid-spoils" -> (() => raidTakingRelicAndBanner),
    "conquest-placed" -> (() => conquestPlacingWarbands),
    "captains-raid" -> (() => CaptainsLogScripts.captainsRaidFromARuledSite),
    "captains-raid-lost" ->
      (() => CaptainsLogScripts.captainsRaidLostWithASplit))
```

Add the imports the two scripts need and nothing more; `-Werror` fails on an
unused one. `Park` and `Decide` come from `oathdigital.model._`.

- [ ] **Step 2: Calibrate the two new scripts against the real board**

Neither script's arithmetic is guessed at a desk: run each and read the log.

Create `GoldenScripts.scala`:

```scala
package oathdigital.application.gamelog

/** Every script with a golden file, for the suites that hold for each.
  * `LogScripts` is at its size limit, so the registry lives here and the
  * script files contribute their own vectors. */
object GoldenScripts:
  val named: Vector[(String, () => Script)] =
    LogScripts.named ++ CampaignLogScripts.named

  def all: Vector[Script] = named.map(_._2())
```

Point `GameLogGoldenSuite` at it: change `import LogScripts._` to
`import LogScripts.format` and `import GoldenScripts.named` (keep whatever
other members it uses). Point `GameLogPropertiesSuite:16` at
`GoldenScripts.all`, and delete `LogScripts.all`, whose only caller that
was.

Then write the goldens and read them:

```bash
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
cat src/test/resources/gamelog/raid-spoils.actor.log
cat src/test/resources/gamelog/conquest-placed.actor.log
```

`raid-spoils.actor.log` must contain, in this order, a line starting
`Took `, one starting with p1's label and ` wins!`, one reading
`{p2} lost 1 warband, burned 1 favor and discarded …`, and one containing
` was sent to `. `conquest-placed.actor.log` must contain a line reading
`The bandits lost 3 warbands` and one starting `Placed 2 warband`.

If a branch is missing, the arithmetic is off, not the plan. Read the log to
see where it stopped and adjust **only** the board and the answers:

- No `Took ` line: the targets answer did not take. Check the Targets line
  names the relic and the banner; if the options are not offered, p2's relic
  is facedown or the banner has no holder.
- `The bandits win!` instead of p1: the attack did not beat the defense.
  Raise `boardWarbands(p1, …)`; a Raid's attack is the force plus the
  sacrifice, and the defense is the defender's board warbands with blank
  dice.
- No `Placed ` line: the placement was not asked, so no warband survived.
  Raise p1's force.

Re-run the write command after each change. **Do not** change the dice
stubs: `Dice.raid` rolls one sword per attack die and a blank per defense
die, and every other golden depends on those two stubs reading the same way.

- [ ] **Step 3: Review all eight goldens line by line**

Eight files are new: `raid-spoils`, `conquest-placed`, `captains-raid` and
`captains-raid-lost`, each for the actor and one other seat. Read every
line of each and check:

- every line is a sentence the Game Log is meant to post, not a stray
  delta;
- the two seats differ only where visibility differs — a facedown adviser
  named for its owner and `a Denizen` for the other seat;
- `captains-raid-lost.actor.log` holds a line reading `Lost 2 warbands`,
  with no subject, which is the branch this task exists for.

A golden committed without this read is worthless: the whole slice's proof
is that these files do not move.

- [ ] **Step 4: Run the full gate**

Run: `./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py`
Expected: PASS. `GameLogPropertiesSuite`'s properties now hold for four more
scripts; if one fails, it has found a real gap in a procedure's narration
declaration. Fix the declaration, and say so in the commit message — do not
drop the script.

- [ ] **Step 5: Commit**

```bash
git add src/test/scala/oathdigital/application/gamelog/CampaignLogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GoldenScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogGoldenSuite.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/resources/gamelog/
git commit -m "test(gamelog): golden the Campaign branches no script played

The attacker's loss line, a won Raid's spoils, a Conquest victory's
placement and the bandits' loss had no golden, and they are four of the
six closing lines slice 4 rewrites. Two came free: the Captains Raids
were already built and were not in the registry, which moves out of
LogScripts, at its size limit.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

**Done when:** eight new goldens exist and were read; `GoldenScripts.named`
holds thirty-five entries; the gate is green. No production file changed.

---

### Task 2: `NoteArg.Things`, one joined list of mixed references

Two Campaign lines join a variable-length list whose items are of different
kinds. "Targets: " joins sites in a Conquest, or a pawn, relics and banners
in a Raid. A won Raid's "Took {spoils} from {player}" joins relics and
banners. `NotePart` cannot join (ruling 8) and no argument kind holds a
mixed list, so both lines need one. One kind serves both.

This **amends ruling 7**, which allowed three new argument kinds and said a
fourth is a spec amendment. Task 6 writes the amendment into the design.

**Files:**
- Modify: `src/main/scala/oathdigital/model/PowerNotes.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/PowerLines.scala`
- Test: `src/test/scala/oathdigital/serialization/PowerNoteWireSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/NoteText.scala`
- Test: `src/test/scala/oathdigital/application/gamelog/ProcedureNoteLinesSuite.scala`

**Interfaces:**
- Consumes: `LogWords.join`, `LogWords.one`, `LogWords.seen`,
  `LogWords.site`, `LogWords.player`, `LogWords.banner`.
- Produces: `NoteArg.Things(items: Vector[NoteThing])` and the closed sum
  `NoteThing = Site(id) | Pawn(player) | Card(id) | Banner(banner)`, which
  Tasks 3 and 4 build.

- [ ] **Step 1: Write the failing tests**

In `PowerNoteWireSuite`, after "the players, clauses and placements
arguments round trip":

```scala
  test("the things argument round trips, and an unknown thing is refused"):
    val event = noteEvent(PowerSourceRef.Site(SiteId("s1")),
      NoteArg.Things(Vector(NoteThing.Site(SiteId("s2")),
        NoteThing.Pawn(PlayerId("p2")),
        NoteThing.Card(RelicId("r1")),
        NoteThing.Banner(Banner.PeoplesFavor))),
      NoteArg.Things(Vector.empty))
    val encoded = accepted(GameEventWire.encodeEvent("notes", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    val broken = ujson.read(encoded)
    broken("payload")("note")("args")(0)("things")(0)("kind") = "weather"
    assertEquals(GameEventWire.decode(broken), Left(WireError.InvalidValue(
      "$.payload.note.args[0].things[0].kind",
      "unknown note thing 'weather'")))
```

In `ProcedureNoteLinesSuite`, a rendering test that needs no game state. A
card with no states behind it renders as its back, which is what this
asserts:

```scala
  test("a things argument joins sites, a pawn, a card and a banner"):
    assertEquals(spoken(ActionRef.Campaign, CampaignNotes.targets,
        NoteArg.Things(Vector(NoteThing.Site(brokenPeaks),
          NoteThing.Pawn(PlayerId("p2")), NoteThing.Card(RelicId("r1")),
          NoteThing.Banner(Banner.DarkestSecret)))).map(_.text).mkString,
      "Targets: Broken Peaks, p2's pawn, a Relic and Darkest Secret")
```

This one cannot compile until Task 3 declares `CampaignNotes.targets`.
Write it now and leave it failing to compile: Task 3's step 2 is where it
first runs. Note that in the plan's own order it is Task 3 that turns this
test green, which is why Task 2's gate run excludes it — comment the test
out with a `// TODO(slice 4 task 3): …` line if a red compile blocks the
commit, and uncomment it in Task 3.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.serialization.PowerNoteWireSuite"`
Expected: a compile error, `NoteThing` not found.

- [ ] **Step 3: Add the kind to `model`**

In `PowerNotes.scala`, after `NoteArg.Placements`:

```scala
  /** Several references of different kinds as one joined phrase, "Broken
    * Peaks, Blue's pawn, Wine of Welcome and the People's Favor": a
    * Campaign's targets, and a won Raid's spoils. The joining is the Game
    * Log's. */
  final case class Things(items: Vector[NoteThing]) extends NoteArg
```

and after the `NoteClause` sum:

```scala
/** One item of a joined list of references (procedures journal their own
  * notes design, "Campaign"). Closed, because each case is something the
  * rules name and the Game Log has one way of naming each. */
sealed trait NoteThing extends Product with Serializable
object NoteThing:
  final case class Site(id: SiteId) extends NoteThing
  /** A player's pawn, "Blue's pawn". */
  final case class Pawn(player: PlayerId) extends NoteThing
  final case class Card(id: CardId) extends NoteThing
  final case class Banner(banner: oathdigital.model.Banner) extends NoteThing
```

Then delete `NoteClause.LostWarbands` and `NoteClause.SentTo`. Task 4's
split gives each of those its own flat key, so no line builds either, and a
case of a closed sum that nothing builds is dead weight the codec and its
tests still carry. Leave `BurnedFavor`, `Discarded` and `SetAside`, which
Task 4 uses.

- [ ] **Step 4: Add the codec case**

In `encodeNoteArg`:

```scala
    case NoteArg.Things(items) => ujson.Obj("kind" -> "things",
      "things" -> ujson.Arr.from(items.map(encodeNoteThing)))
```

and beside `encodeNoteClause`:

```scala
  private def encodeNoteThing(thing: NoteThing): ujson.Value = thing match
    case NoteThing.Site(id) => ujson.Obj("kind" -> "site",
      "siteId" -> id.value)
    case NoteThing.Pawn(player) => ujson.Obj("kind" -> "pawn",
      "playerId" -> player.value)
    case NoteThing.Card(id) => ujson.Obj("kind" -> "card",
      "card" -> encodeCardId(id))
    case NoteThing.Banner(banner) => ujson.Obj("kind" -> "banner",
      "bannerKey" -> banner.key)
```

In `decodeNoteArg`:

```scala
    case "things" => traverse(value("things").arr.zipWithIndex.toVector)({
      case (thing, index) =>
        decodeNoteThing(thing, s"$path.things[$index]") })
      .map(NoteArg.Things.apply)
```

and beside `decodeNoteClause`:

```scala
  private def decodeNoteThing(value: ujson.Value, path: String)
      : Either[WireError, NoteThing] = value("kind").str match
    case "site" => Right(NoteThing.Site(SiteId(value("siteId").str)))
    case "pawn" => Right(NoteThing.Pawn(PlayerId(value("playerId").str)))
    case "card" => decodeCardId(value("card"), s"$path.card")
      .map(NoteThing.Card.apply)
    case "banner" => decodeBanner(value("bannerKey").str, s"$path.bannerKey")
      .map(NoteThing.Banner.apply)
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown note thing '$other'"))
```

Delete the `lost-warbands` and `sent-to` cases from `encodeNoteClause` and
`decodeNoteClause`, and the two assertions that build them in
`PowerNoteWireSuite` ("the players, clauses and placements arguments round
trip" loses two clause entries, and "a negative clause or placement count is
refused" uses `NoteClause.BurnedFavor` in place of `LostWarbands`).

- [ ] **Step 5: Render it**

In `PowerLines.argument`, after the `Placements` case:

```scala
    case NoteArg.Things(items) => LogWords.join(items.map(thing(_, seen,
      viewer)))
```

and beside `clause`:

```scala
  /** One item of a joined list, worded as the line that joined them by hand
    * worded it. */
  private def thing(item: NoteThing, seen: Vector[ReadyGame],
      viewer: Option[PlayerId]): Vector[LogSpan] = item match
    case NoteThing.Site(id) => Vector(words.site(id))
    case NoteThing.Pawn(player) =>
      Vector(words.player(player), Text("'s pawn"))
    case NoteThing.Card(id) => words.one(words.seen(id, seen, viewer))
    case NoteThing.Banner(banner) => Vector(words.banner(banner))
```

Delete the `LostWarbands` and `SentTo` cases from `PowerLines.clause`.

In `NoteText` (the test helper), add `case NoteArg.Things(items) =>
items.map(thing).mkString(", ")` with a `thing` helper beside `clause`, and
delete the two clause cases that no longer exist.

- [ ] **Step 6: Run the codec and renderer suites**

Run: `./sbtw "testOnly oathdigital.serialization.* oathdigital.application.gamelog.* oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`
Expected: PASS, apart from the `ProcedureNoteLinesSuite` test that waits for
Task 3.

- [ ] **Step 7: Run the full gate and commit**

Run: `./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py`

```bash
git add src/main/scala/oathdigital/model/PowerNotes.scala \
  src/main/scala/oathdigital/serialization/WalkerEventCodec.scala \
  src/main/scala/oathdigital/application/gamelog/PowerLines.scala \
  src/test/scala/oathdigital/serialization/PowerNoteWireSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/NoteText.scala
git commit -m "feat(gamelog): a note argument for a joined list of mixed references

A Campaign's targets join sites, or a pawn, relics and banners; a won
Raid's spoils join relics and banners. No argument kind held a mixed
list and a template cannot join, so both lines needed one. The two
clause cases the loss split makes unnecessary go with it.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

**Done when:** `NoteArg.Things` round trips, an unknown thing is refused,
`NoteClause` holds three cases, and no golden moved — nothing writes a
`Things` yet.

---

## A correction to the approved commit shape

Plan-time question 9 was answered **a**: six commits, with "Campaign's
decision, roll and outcome lines" and "Campaign's closing lines" as two of
them. **That split is not available**, and the reason is worth stating
before Task 3 rather than discovering it mid-task.

`ActionLineSource` is one of three things (ruling 4). While Campaign's entry
is `Rule(campaignLines)`, `ProcedureLog.noteKind` answers `LogKind.Action`
for every key, so a note placed in the tree would post at the wrong kind —
and before that it would not post at all, because a key Campaign has not
registered is **refused by the walker's own check**, which is a thrown
violation, not a missing line (slice 3 hit this). Switch the entry to
`Notes` with only half the leaves placed and the other half's lines vanish.
So Campaign's leaves, its registry entry and `CampaignLines`' deletion are
one commit.

The six commits keep their count, split at a different line: the words and
the readings are written and pinned first (Task 3), and placing them is the
atomic switch (Task 4). Task 3's deliverable is independently testable —
every key's sentence is asserted without a game — and nothing it adds is
reachable from a journal, so no golden can move.

---

### Task 3: Campaign's twenty-three keys, and what each reads

Every Campaign line's words, and the readers that turn a journaled step into
its numbers and cards, in one new file. Nothing is placed in the tree yet:
this task registers the keys, pins every sentence, and leaves the rule
posting the lines exactly as it does today.

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignNotes.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Test: `src/test/scala/oathdigital/application/gamelog/ProcedureNoteLinesSuite.scala`
- Fixture: `src/test/resources/notes/declared-keys.txt`

**Interfaces:**
- Consumes: `NoteSet`, `NoteKey`, `NotePart`, `NoteArg`, `NoteThing`,
  `NoteClause`, `ProcedureNotes.id`, `NoteSupport.Step`,
  `NoteSupport.favor`, `NoteSupport.relicsGained`, `BannerRules.holder`,
  `PlayerForceKind.of`, `CampaignSetup.setup`, `CampaignAnswers.sacrificed`,
  `CampaignAnswers.skipped`, `CampaignBattle.printedDefense`,
  `CampaignBattle.sacrificeWorth`, `CampaignBattle.attackFacesOf`,
  `CampaignPlans.cardOf`, `AttackScore.skullFaces`, `AttackScore.rules`.
- Produces: `CampaignNotes.noteKeys`, the twenty-three keys by name, and one
  builder per leaf, every one of them public so that Task 3 compiles with
  none of them called: `targetsNote`, `poolsNote`, `activationNote`,
  `banditActivationNote`, `sacrificedNote`, `attackNote`, `defenseNote`,
  `winnerNote`, `lossNote`, `spoilsNote`, `placementNote`,
  `consequenceNote`, `relocationNote`, `endedNote`.

- [ ] **Step 1: Write the file**

```scala
package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.{BannerRules, ProcedureNotes}
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.gameplay.powers.campaign.AttackScore
import oathdigital.model._

/** Every Game Log line a Campaign writes about itself, and what each one
  * reads (procedures journal their own notes design, "Campaign"). The
  * Campaign's own files place the leaves; the words and the readings are
  * all here.
  *
  * **Every count is read from the step the note restates, never computed
  * again from the board.** Four powers rewrite the Campaign's own loss
  * operations before they run — Billowing Fog, Traveling Doctor and Peace
  * Envoy through `SparedForce.spare`, and Keep replaces the Raid's
  * consequences outright — so the Campaign's own arithmetic would state
  * numbers that did not happen. `NoteStates.previous` holds the before and
  * after of the leaf immediately above the note, which is what did.
  *
  * The loss sentence the rule joined from up to five clauses is three
  * sentences here, because the clauses are applied in three different
  * nodes and one note cannot span them. The only subject the rule omitted
  * — the loser is the Campaign's own actor — carries exactly one clause,
  * so it is a flat key.
  */
object CampaignNotes:
  private val notes = NoteSet(ProcedureNotes.id(ActionRef.Campaign))
  def noteKeys: Vector[NoteKey] = notes.declared

  /** "Targets: Broken Peaks and the Mine", or a Raid's pawn, relics and
    * banners. */
  val targets: NoteKey = notes.key("targets", Vector(
    NotePart.Text("Targets: "), NotePart.Arg(0)))
  /** "Attack Pool: 3, Defense Pool: 2". */
  val pools: NoteKey = notes.key("pools", Vector(
    NotePart.Text("Attack Pool: "), NotePart.Arg(0),
    NotePart.Text(", Defense Pool: "), NotePart.Arg(1)))
  /** "Activated Careful Plans": the Campaign's own actor. */
  val activatedPlan: NoteKey = notes.key("activated-plan", Vector(
    NotePart.Text("Activated "), NotePart.Arg(0)))
  /** "{Blue} activated Careful Plans": a defender, or an off-turn user. */
  val playerActivatedPlan: NoteKey = notes.key("player-activated-plan",
    Vector(NotePart.Arg(0), NotePart.Text(" activated "), NotePart.Arg(1)))
  val banditsActivatedPlan: NoteKey = notes.key("bandits-activated-plan",
    Vector(NotePart.Text("The bandits activated "), NotePart.Arg(0)))
  /** "{Blue} activated the Oathkeeper title": the title's defense plan,
    * which no card prints. The rule read the label its offer authored,
    * which a typed argument cannot carry (wording ledger, slice 4). */
  val activatedOathkeeperTitle: NoteKey = notes.key(
    "activated-oathkeeper-title", Vector(NotePart.Arg(0),
      NotePart.Text(" activated the Oathkeeper title")))
  val activatedUsurperTitle: NoteKey = notes.key("activated-usurper-title",
    Vector(NotePart.Arg(0),
      NotePart.Text(" activated the Usurper title")))
  /** "Sacrificed 3 warbands for an attack of 6". */
  val sacrificed: NoteKey = notes.key("sacrificed", Vector(
    NotePart.Text("Sacrificed "), NotePart.Arg(0), NotePart.Text(" "),
    NotePart.Plural(0, "warband", "warbands"),
    NotePart.Text(" for an attack of "), NotePart.Arg(1)))
  /** "Attack: 5", when the skulls killed nothing. */
  val attack: NoteKey = notes.key("attack", Vector(
    NotePart.Text("Attack: "), NotePart.Arg(0)))
  /** "Attack: 5 with 1 skull", when each skull killed one warband. */
  val attackWithSkulls: NoteKey = notes.key("attack-with-skulls", Vector(
    NotePart.Text("Attack: "), NotePart.Arg(0), NotePart.Text(" with "),
    NotePart.Arg(1), NotePart.Text(" "),
    NotePart.Plural(1, "skull", "skulls")))
  /** "Attack: 6 with 2 skulls, killing 4 warbands", when the skulls killed
    * another number than they were (Weeping Banner, or the force cap). */
  val attackWithSkullKills: NoteKey = notes.key("attack-with-skull-kills",
    Vector(NotePart.Text("Attack: "), NotePart.Arg(0),
      NotePart.Text(" with "), NotePart.Arg(1), NotePart.Text(" "),
      NotePart.Plural(1, "skull", "skulls"), NotePart.Text(", killing "),
      NotePart.Arg(2), NotePart.Text(" "),
      NotePart.Plural(2, "warband", "warbands")))
  /** "Defense: 3". */
  val defense: NoteKey = notes.key("defense", Vector(
    NotePart.Text("Defense: "), NotePart.Arg(0)))
  /** "{Blue} wins!" */
  val playerWins: NoteKey = notes.key("player-wins", Vector(
    NotePart.Arg(0), NotePart.Text(" wins!")))
  val banditsWin: NoteKey = notes.key("bandits-win", Vector(
    NotePart.Text("The bandits win!")))
  /** "Took Wine of Welcome and Darkest Secret from {Blue}". */
  val took: NoteKey = notes.key("took", Vector(NotePart.Text("Took "),
    NotePart.Arg(0), NotePart.Text(" from "), NotePart.Arg(1)))
  /** "Placed 2 warbands on the Mine and 1 warband on Standing Stones". */
  val placed: NoteKey = notes.key("placed", Vector(
    NotePart.Text("Placed "), NotePart.Arg(0)))
  /** "Lost 2 warbands": the loser is the Campaign's own actor. */
  val lost: NoteKey = notes.key("lost", Vector(NotePart.Text("Lost "),
    NotePart.Arg(0), NotePart.Text(" "),
    NotePart.Plural(0, "warband", "warbands")))
  /** "{Blue} lost 2 warbands". */
  val playerLost: NoteKey = notes.key("player-lost", Vector(
    NotePart.Arg(0), NotePart.Text(" lost "), NotePart.Arg(1),
    NotePart.Text(" "), NotePart.Plural(1, "warband", "warbands")))
  val banditsLost: NoteKey = notes.key("bandits-lost", Vector(
    NotePart.Text("The bandits lost "), NotePart.Arg(0),
    NotePart.Text(" "), NotePart.Plural(0, "warband", "warbands")))
  /** "{Blue} burned 1 favor, discarded Tinker and set aside a Relic": what
    * a defeated Raid defender loses beyond their warbands, in the order
    * the rule's joined sentence named them. */
  val defenderLost: NoteKey = notes.key("defender-lost", Vector(
    NotePart.Arg(0), NotePart.Text(" "), NotePart.Arg(1)))
  /** "{Blue} was sent to the Mine". */
  val sentTo: NoteKey = notes.key("sent-to", Vector(NotePart.Arg(0),
    NotePart.Text(" was sent to "), NotePart.Arg(1)))
  val skipped: NoteKey = notes.key("skipped", Vector(
    NotePart.Text("Skipped the Campaign.")))
  val nothingToAttack: NoteKey = notes.key("nothing-to-attack", Vector(
    NotePart.Text("Nothing left to attack: the Campaign ended.")))

  /** The Campaign's setup, from the answers so far. */
  private def setupOf(states: NoteStates, actor: PlayerId)
      : Option[CampaignSetup] =
    CampaignSetup.setup(states.now, actor,
      PendingTree(Vector.empty, states.answered))

  /** What the Campaign targets, each as the Game Log names it. */
  private def targeted(setup: CampaignSetup): Vector[NoteThing] =
    setup.kind match
      case CampaignKind.Conquest =>
        setup.targetSites.map(NoteThing.Site(_))
      case CampaignKind.Raid => setup.raidTargets.map {
        case CampaignRaidTarget.Pawn(player) => NoteThing.Pawn(player)
        case CampaignRaidTarget.Relic(_, relic) => NoteThing.Card(relic)
        case CampaignRaidTarget.Banner(_, banner) =>
          NoteThing.Banner(banner)
      }

  def targetsNote(actor: PlayerId)(states: NoteStates): Option[PowerNote] =
    setupOf(states, actor).map(setup =>
      targets.sourceless(NoteArg.Things(targeted(setup))))

  def poolsNote(catalog: ExecutableCatalog, actor: PlayerId)(
      states: NoteStates): Option[PowerNote] =
    setupOf(states, actor).map(setup => pools.sourceless(
      NoteArg.Number(setup.force),
      NoteArg.Number(CampaignBattle.printedDefense(catalog, setup))))

  /** A plan a player chose, named by its card, or by the title it is
    * printed on no card for. `user` is who chose it. */
  def activationNote(source: CampaignPlanSource, user: PlayerId,
      actor: PlayerId)(states: NoteStates): Option[PowerNote] =
    CampaignPlans.cardOf(source) match
      case Some(card) => Some(
        if user == actor then activatedPlan.sourceless(NoteArg.Card(card))
        else playerActivatedPlan.sourceless(NoteArg.Player(user),
          NoteArg.Card(card)))
      case None => Some(states.now.game.current.title.side match
        case TitleSide.Oathkeeper =>
          activatedOathkeeperTitle.sourceless(NoteArg.Player(user))
        case TitleSide.Usurper =>
          activatedUsurperTitle.sourceless(NoteArg.Player(user)))

  /** A plan a bandit defender applied, which is always a card's: the title
    * is a player defender's. */
  def banditActivationNote(source: CampaignPlanSource)(
      states: NoteStates): Option[PowerNote] =
    CampaignPlans.cardOf(source).map(card =>
      banditsActivatedPlan.sourceless(NoteArg.Card(card)))

  /** The attack each sacrificed warband bought, as the new total: the
    * sacrifice is added to the recorded attack when the result is built,
    * so the pool still holds the attack before it. */
  def sacrificedNote(states: NoteStates): Option[PowerNote] =
    val count = CampaignAnswers.sacrificed(
      PendingTree(Vector.empty, states.answered))
    Option.when(count > 0)(sacrificed.sourceless(NoteArg.Number(count),
      NoteArg.Number(scoreOf(states.now, CampaignIds.attackPool) +
        count * CampaignBattle.sacrificeWorth(states.now))))

  /** The attack as it stands once every plan has scored it, with the skull
    * faces counted as the scoring counts them and, when the skulls killed
    * another number of warbands, that number (batch 8, item 13b). */
  def attackNote(actor: PlayerId)(states: NoteStates): Option[PowerNote] =
    states.now.game.current.rollOutcomes.get(CampaignIds.attackPool).map {
      outcome =>
        val kills = outcome.skulls
        val faces = AttackScore.skullFaces(
          CampaignBattle.attackFacesOf(states.now),
          AttackScore.rules(states.now,
            PendingTree(Vector.empty, states.answered), actor))
        if kills == 0 then attack.sourceless(NoteArg.Number(outcome.score))
        else if kills == faces then attackWithSkulls.sourceless(
          NoteArg.Number(outcome.score), NoteArg.Number(faces))
        else attackWithSkullKills.sourceless(
          NoteArg.Number(outcome.score), NoteArg.Number(faces),
          NoteArg.Number(kills))
    }

  /** The defense, which is written even for a pool that never rolled: it
    * is the dice plus the defender's force. */
  def defenseNote(states: NoteStates): Option[PowerNote] = Some(
    defense.sourceless(NoteArg.Number(
      scoreOf(states.now, CampaignIds.defensePool))))

  def winnerNote(states: NoteStates): Option[PowerNote] =
    resultOf(states).map(result =>
      if result.attackerWins then
        playerWins.sourceless(NoteArg.Player(result.attacker))
      else result.defender match
        case CampaignDefender.Player(player) =>
          playerWins.sourceless(NoteArg.Player(player))
        case CampaignDefender.Bandits => banditsWin.sourceless())

  /** What the battle's losses killed, read as the warbands the loser holds
    * on their board and across the map before and after the leaf. A
    * Conquest defender's returned half comes back into that total, so the
    * count is the net loss the rule's joined clause named. */
  def lossNote(states: NoteStates): Option[PowerNote] = for
    result <- resultOf(states)
    step <- states.previous
    owner = loserOf(result)
    kind <- forceKindOf(step._1, owner)
    count = forceHeld(step._1, owner, kind) - forceHeld(step._2, owner, kind)
    if count > 0
  yield owner match
    case Some(player) if player == result.attacker =>
      lost.sourceless(NoteArg.Number(count))
    case Some(player) => playerLost.sourceless(NoteArg.Player(player),
      NoteArg.Number(count))
    case None => banditsLost.sourceless(NoteArg.Number(count))

  /** What a won Raid took, read as what the attacker gained in the step:
    * the relics first, then the banners, as the takes ran. A take a power
    * refused is not named, because nothing was gained. */
  def spoilsNote(states: NoteStates): Option[PowerNote] = for
    result <- resultOf(states)
    defender <- defenderOf(result)
    step <- states.previous
    taken = NoteSupport.relicsGained(step, result.attacker)
      .map(relic => NoteThing.Card(relic): NoteThing) ++
      bannersGained(step, result.attacker).map(NoteThing.Banner(_))
    if taken.nonEmpty
  yield took.sourceless(NoteArg.Things(taken), NoteArg.Player(defender))

  /** Where a Conquest victory's survivors went, read per target site. */
  def placementNote(states: NoteStates): Option[PowerNote] = for
    result <- resultOf(states)
    step <- states.previous
    kind <- forceKindOf(step._1, Some(result.attacker))
    entries = result.targetSites.flatMap { site =>
      val added = atSite(step._2, site, kind) - atSite(step._1, site, kind)
      Option.when(added > 0)(added -> site)
    }
    if entries.nonEmpty
  yield placed.sourceless(NoteArg.Placements(entries))

  /** What a defeated Raid defender lost beyond their warbands, read from
    * the consequences step: the favor burnt, the cards that left their
    * play area for a discard pile, and the relics set aside. A facedown
    * Conspiracy goes to the box, not a pile, so no clause names it. */
  def consequenceNote(states: NoteStates): Option[PowerNote] = for
    result <- resultOf(states)
    defender <- defenderOf(result)
    step <- states.previous
    clauses = consequences(step, defender)
    if clauses.nonEmpty
  yield defenderLost.sourceless(NoteArg.Player(defender),
    NoteArg.Clauses(clauses))

  private def consequences(step: NoteSupport.Step, defender: PlayerId)
      : Vector[NoteClause] =
    val burned = -NoteSupport.favor(step, defender)
    val left = held(step._1, defender).filterNot(
      held(step._2, defender).contains)
    val discarded = left.collect { case card: WorldCardId => card }
      .filter(card => Region.all.exists(region =>
        step._2.game.current.commonCards.discard(region).contains(card)))
    val aside = step._2.game.current.setAsideRelics.filterNot(
      step._1.game.current.setAsideRelics.contains)
    Vector(
      Option.when(burned > 0)(NoteClause.BurnedFavor(burned)),
      Option.when(discarded.nonEmpty)(NoteClause.Discarded(
        discarded.map(card => card: CardId))),
      Option.when(aside.nonEmpty)(NoteClause.SetAside(
        aside.map(relic => relic: CardId)))).flatten

  /** Where a defeated Raid defender's pawn was sent, read from the move
    * itself: a power that ends the Raid instead (Keep) replaces this
    * step's operations and its own line says where the pawn stands. */
  def relocationNote(states: NoteStates): Option[PowerNote] = for
    result <- resultOf(states)
    defender <- defenderOf(result)
    step <- states.previous
    to <- pawnOf(step._2, defender)
    if !pawnOf(step._1, defender).contains(to)
  yield sentTo.sourceless(NoteArg.Player(defender), NoteArg.Site(to))

  /** Why a Campaign ended before its force was committed. */
  def endedNote(states: NoteStates): Option[PowerNote] = Some(
    if CampaignAnswers.skipped(PendingTree(Vector.empty, states.answered))
    then skipped.sourceless() else nothingToAttack.sourceless())

  private def resultOf(states: NoteStates): Option[CampaignResult] =
    states.now.game.current.lastCampaignResult

  private def defenderOf(result: CampaignResult): Option[PlayerId] =
    Some(result.defender).collect {
      case CampaignDefender.Player(player) => player }

  /** Who lost the battle: the defender when the attacker won, else the
    * attacker. `None` is the bandits. */
  private def loserOf(result: CampaignResult): Option[PlayerId] =
    if result.attackerWins then defenderOf(result)
    else Some(result.attacker)

  private def forceKindOf(ready: ReadyGame, owner: Option[PlayerId])
      : Option[ForceKind] = owner match
    case None => Some(ForceKind.Bandit)
    case Some(player) => ready.game.current.players
      .find(_.player == player).map(state =>
        PlayerForceKind.of(ready, state).getOrElse(
          ForceKind.Exile(state.lineage)))

  /** Every warband of `kind` the owner holds: their board, and every site.
    * The warband bank is not counted, so a warband returned from the
    * supply comes back out of the loss. */
  private def forceHeld(ready: ReadyGame, owner: Option[PlayerId],
      kind: ForceKind): Int =
    val onBoard = owner.flatMap(player => ready.game.current.players
      .find(_.player == player)).fold(0)(_.board.warbands)
    onBoard + ready.game.current.map.sites.keys
      .map(atSite(ready, _, kind)).sum

  private def atSite(ready: ReadyGame, site: SiteId, kind: ForceKind): Int =
    ready.game.current.map.sites.get(site).map(_.forces).collect {
      case SiteForces.Occupied(`kind`, count) => count
    }.getOrElse(0)

  private def scoreOf(ready: ReadyGame, pool: PoolKey): Int =
    ready.game.current.rollOutcomes.get(pool).fold(0)(_.score)

  private def held(ready: ReadyGame, player: PlayerId): Vector[CardId] =
    ready.game.current.players.find(_.player == player).toVector.flatMap(
      state => state.advisers.map(_.id: CardId) ++
        state.relics.map(_.id: CardId))

  private def pawnOf(ready: ReadyGame, player: PlayerId): Option[SiteId] =
    ready.game.current.players.find(_.player == player).flatMap(_.pawnSite)

  private def bannersGained(step: NoteSupport.Step, player: PlayerId)
      : Vector[Banner] = Banner.all.filter(banner =>
    BannerRules.holder(step._2.game.current, banner).contains(player) &&
      !BannerRules.holder(step._1.game.current, banner).contains(player))
```

Check as you write: `state.advisers.map(_.id)` must yield the adviser's
`WorldCardId`; if `AdviserState`'s `id` is not directly a `CardId`, widen it
the way `CampaignRaid.consequences` does. Read `AdviserState` before
assuming.

- [ ] **Step 2: Register the keys**

In `ProcedureNotes.keys`, add, beside the other `ActionRef` arms:

```scala
      case ActionRef.Campaign => CampaignNotes.noteKeys
```

with `import oathdigital.gameplay.actions.campaign.CampaignNotes`.
Campaign's entry is still `Rule`, which the design allows explicitly: a
procedure listed here that has not migrated writes no note and its line
still comes from `ProcedureLog`, and listing it means the declared-key union
covers it.

- [ ] **Step 3: Pin every sentence**

In `ProcedureNoteLinesSuite`, uncomment Task 2's `Things` test and add one
assertion per key. Keep them in the file's existing idiom: `said` for a
sentence with no card in it, `spoken` for one with. These are the whole of
this task's proof, so write every key, not a sample.

```scala
  test("Campaign's pool, roll and sacrifice lines read as the rule wrote " +
      "them"):
    assertEquals(said(ActionRef.Campaign, CampaignNotes.pools,
      NoteArg.Number(3), NoteArg.Number(2)),
      "Attack Pool: 3, Defense Pool: 2")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.attack,
      NoteArg.Number(5)), "Attack: 5")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.attackWithSkulls,
      NoteArg.Number(5), NoteArg.Number(1)), "Attack: 5 with 1 skull")
    assertEquals(said(ActionRef.Campaign,
      CampaignNotes.attackWithSkullKills, NoteArg.Number(6),
      NoteArg.Number(2), NoteArg.Number(4)),
      "Attack: 6 with 2 skulls, killing 4 warbands")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.defense,
      NoteArg.Number(3)), "Defense: 3")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.sacrificed,
      NoteArg.Number(1), NoteArg.Number(4)),
      "Sacrificed 1 warband for an attack of 4")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.sacrificed,
      NoteArg.Number(3), NoteArg.Number(6)),
      "Sacrificed 3 warbands for an attack of 6")

  test("Campaign's loss lines name the loser, except the actor"):
    assertEquals(said(ActionRef.Campaign, CampaignNotes.lost,
      NoteArg.Number(2)), "Lost 2 warbands")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.lost,
      NoteArg.Number(1)), "Lost 1 warband")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.playerLost,
      NoteArg.Player(PlayerId("p2")), NoteArg.Number(1)),
      "p2 lost 1 warband")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.banditsLost,
      NoteArg.Number(3)), "The bandits lost 3 warbands")

  test("a defeated Raid defender's clauses read in the rule's order"):
    assertEquals(said(ActionRef.Campaign, CampaignNotes.defenderLost,
      NoteArg.Player(PlayerId("p2")), NoteArg.Clauses(Vector(
        NoteClause.BurnedFavor(1),
        NoteClause.Discarded(Vector(DenizenId("176"))),
        NoteClause.SetAside(Vector(RelicId("R23")))))),
      "p2 burned 1 favor, discarded a Denizen and set aside a Relic")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.sentTo,
      NoteArg.Player(PlayerId("p2")), NoteArg.Site(brokenPeaks)),
      "p2 was sent to Broken Peaks")

  test("Campaign's winner, spoils, placement and plan lines"):
    assertEquals(said(ActionRef.Campaign, CampaignNotes.playerWins,
      NoteArg.Player(PlayerId("p1"))), "p1 wins!")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.banditsWin),
      "The bandits win!")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.took,
      NoteArg.Things(Vector(NoteThing.Banner(Banner.DarkestSecret))),
      NoteArg.Player(PlayerId("p2"))),
      "Took Darkest Secret from p2")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.placed,
      NoteArg.Placements(Vector(2 -> brokenPeaks))),
      "Placed 2 warbands on Broken Peaks")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.placed,
      NoteArg.Placements(Vector(1 -> brokenPeaks))),
      "Placed 1 warband on Broken Peaks")
    assertEquals(said(ActionRef.Campaign,
      CampaignNotes.playerActivatedPlan, NoteArg.Player(PlayerId("p2")),
      NoteArg.Card(DenizenId("176"))), "p2 activated a Denizen")
    assertEquals(said(ActionRef.Campaign,
      CampaignNotes.banditsActivatedPlan, NoteArg.Card(DenizenId("176"))),
      "The bandits activated a Denizen")
    assertEquals(said(ActionRef.Campaign,
      CampaignNotes.activatedOathkeeperTitle,
      NoteArg.Player(PlayerId("p2"))),
      "p2 activated the Oathkeeper title")
    assertEquals(said(ActionRef.Campaign,
      CampaignNotes.activatedUsurperTitle, NoteArg.Player(PlayerId("p2"))),
      "p2 activated the Usurper title")

  test("a Campaign that ended before its battle says why"):
    assertEquals(said(ActionRef.Campaign, CampaignNotes.skipped),
      "Skipped the Campaign.")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.nothingToAttack),
      "Nothing left to attack: the Campaign ended.")
```

`activatedPlan` ("Activated {card}") is asserted in Task 4 against the
Careful Plans script, which plays it; add it here too if a card-free
rendering is wanted, remembering that a card with no state behind it reads
as its back.

- [ ] **Step 4: Run them**

Run: `./sbtw "testOnly oathdigital.application.gamelog.ProcedureNoteLinesSuite oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`
Expected: the wording tests pass and `DeclaredNoteKeysSuite` fails on the
snapshot, which has twenty-three new lines.

Compare each failing assertion against the strings in `CampaignLines` as it
stands on `main` — `"Attack Pool: "`, `"Defense: "`, `" wins!"`,
`"The bandits activated "`, `"was sent to "`, `"Skipped the Campaign."` —
character for character. A wording that differs here is a golden failure in
Task 4 at best, and a silently reworded line at worst.

- [ ] **Step 5: Rewrite the key snapshot and read the diff**

```bash
NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"
git diff --stat src/test/resources/notes/declared-keys.txt
git diff src/test/resources/notes/declared-keys.txt
```

Expected: exactly twenty-three added lines, all beginning
`procedure.campaign `, and **no removed line**. A removed line means a key
was renamed or a template changed, which this task may not do.

- [ ] **Step 6: Run the full gate and commit**

Run: `./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py`
Expected: PASS, and `git status` shows no golden changed: nothing journals a
Campaign note yet.

```bash
git add src/main/scala/oathdigital/gameplay/actions/campaign/CampaignNotes.scala \
  src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala \
  src/test/scala/oathdigital/application/gamelog/ProcedureNoteLinesSuite.scala \
  src/test/resources/notes/declared-keys.txt
git commit -m "feat(gamelog): Campaign's own words, and what each line reads

Twenty-three keys and a builder each, declared and pinned before any is
placed in the tree. Every count is read from the step the note restates:
four powers rewrite the Campaign's loss operations before they run, so
its own arithmetic would state numbers that did not happen.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

**Done when:** `declared-keys.txt` gained twenty-three lines and lost none,
every sentence is asserted, the gate is green, and no golden moved.

---

### Task 4: Campaign journals its own lines, and `CampaignLines` goes

The atomic switch: the leaves are placed, the registry entry becomes
`Notes`, the rule is deleted, and the tests that exercised the rule over
hand-built journals are replaced with tests of real play and of the readers.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignNotes.scala` (one member)
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanChoice.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignOutcome.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignConquest.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignRaid.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`
- Delete: `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala` (the `ActionLines` construction)
- Create: `src/test/scala/oathdigital/gameplay/campaign/CampaignNoteReadsSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/CampaignLogScripts.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogCampaignSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogRareLinesSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala` (the `noting` vector)
- Fixture: the Campaign goldens

**Interfaces:**
- Consumes: every builder Task 3 produced.
- Produces: `CampaignNotes.leaf`, used by the five Campaign files.

- [ ] **Step 1: Add the leaf helper**

In `CampaignNotes`, beside `noteKeys`:

```scala
  /** A note leaf of the Campaign's own id, for the files that place one. */
  def leaf(build: NoteStates => Option[PowerNote]): Note =
    Note(ProcedureNotes.id(ActionRef.Campaign), build)
```

- [ ] **Step 2: Place the leaves**

Each note goes **immediately after the leaf it restates**, and **inside**
that leaf's own windowed node. Both halves matter: outside the node, a power
hooked at the window runs before the note and the note reads that power's
leaf; at the end of the node, a power that appends its own kills (Sticky
Fire, Shield Wall) becomes the note's `previous`.

In `CampaignProcedure.battle`, the children become (showing only what
changes, in tree order):

```scala
    forceStep(catalog, state, actor),
    CampaignNotes.leaf(CampaignNotes.targetsNote(actor)),
    CampaignNotes.leaf(CampaignNotes.poolsNote(catalog, actor)),
    Sequence(Vector[Operation](BuildOps((ready, pending) =>
      withSetup(ready, actor, pending)(setup => CampaignSetup.pin(setup) +:
        CampaignBattle.gatherPools(catalog, setup)))),
      Some(PowerWindow.CampaignGatherPools)),
```

The two lines post at the force answer today, before the pools are
gathered; these two leaves keep that place.

After the `CampaignAttackResult` sequence, and before the
`CampaignSkullLosses` sequence item 13b added:

```scala
    CampaignNotes.leaf(CampaignNotes.attackNote(actor)),
```

After the `CampaignDefenseResult` sequence:

```scala
    CampaignNotes.leaf(CampaignNotes.defenseNote),
```

After the `CampaignAfterOutcome` sequence, before `outcomeStep`:

```scala
    CampaignNotes.leaf(CampaignNotes.winnerNote),
```

In `sacrificeStep`, after the `Decide`:

```scala
        Vector[Operation](Decide(CampaignIds.sacrifice, actor,
          DecisionQuery.ChooseAmount(0, max,
            Some(CampaignBattle.sacrificeHeading(max,
              CampaignBattle.sacrificeWorth(ready))), "Sacrifice"),
          window = Some(PowerWindow.CampaignSacrificeSelection)),
          CampaignNotes.leaf(CampaignNotes.sacrificedNote))
```

In `endStep`, before the `EndWalk`:

```scala
        Vector(CampaignNotes.leaf(CampaignNotes.endedNote), EndWalk)
```

In `CampaignPlanChoice.userPass`, the second `Branch`'s `Some(offered)` arm:

```scala
          case Some(offered) => Vector(
            CampaignNotes.leaf(CampaignNotes.activationNote(
              offered.offer.source, user, actor)),
            applicationOf(setup, offered))
```

In `CampaignPlanChoice.banditPlans`, each application gains its line:

```scala
    val chosen = applied.find(_.offered.offer.alone).fold(applied)(Vector(_))
    chosen.flatMap(application => Vector[Operation](
      CampaignNotes.leaf(CampaignNotes.banditActivationNote(
        application.offered.offer.source)), application))
```

In `CampaignOutcome.steps`, inside the losses node:

```scala
    val losses: Operation = Sequence(
      CampaignBattle.originDeathsStep(catalog, actor, result,
        CampaignBattle.attackerSpared(ready)) ++ Vector[Operation](
          BuildOps((ready, pending) =>
            CampaignBattle.losses(ready, result, pending)),
          CampaignNotes.leaf(CampaignNotes.lossNote)),
      Some(PowerWindow.CampaignLosses))
```

In `CampaignConquest.steps`, inside the placement node, after the
`BuildOps`: `CampaignNotes.leaf(CampaignNotes.placementNote)`.

In `CampaignRaid.steps`, after the takes `BuildOps`:
`CampaignNotes.leaf(CampaignNotes.spoilsNote)`; inside the consequences
node, after its `BuildOps`:
`CampaignNotes.leaf(CampaignNotes.consequenceNote)`; inside the relocation
node, after `relocate`'s `BuildOps`:
`CampaignNotes.leaf(CampaignNotes.relocationNote)`.

The last two are also where `Keep`'s suppression comes from for free:
`KeepRule` contributes a `Transform` that replaces the consequences
children with nothing and the relocation children with its own, so both
notes go with the operations they restate, which is what `journal.covered`
did for the rule's joined sentence.

- [ ] **Step 3: Switch the registry entry and delete the rule**

In `ProcedureLog.rules`, replace Campaign's arm:

```scala
    // Every Campaign line tells its own decision. The kinds are the ones
    // the rules posted at: the targets, the plan activations and the
    // sacrifice are decisions, the two totals are rolls, and what the
    // outcome applies is a delta. The winner's line is the action's, which
    // is the default.
    case ActionRef.Campaign => ProcedureLog(tag = Some("Campaign"),
      lines = ActionLineSource.Notes(Map(
        CampaignNotes.targets -> LogKind.Decision,
        CampaignNotes.activatedPlan -> LogKind.Decision,
        CampaignNotes.playerActivatedPlan -> LogKind.Decision,
        CampaignNotes.banditsActivatedPlan -> LogKind.Decision,
        CampaignNotes.activatedOathkeeperTitle -> LogKind.Decision,
        CampaignNotes.activatedUsurperTitle -> LogKind.Decision,
        CampaignNotes.sacrificed -> LogKind.Decision,
        CampaignNotes.pools -> LogKind.Delta,
        CampaignNotes.attack -> LogKind.Roll,
        CampaignNotes.attackWithSkulls -> LogKind.Roll,
        CampaignNotes.attackWithSkullKills -> LogKind.Roll,
        CampaignNotes.defense -> LogKind.Roll,
        CampaignNotes.took -> LogKind.Delta,
        CampaignNotes.placed -> LogKind.Delta,
        CampaignNotes.lost -> LogKind.Delta,
        CampaignNotes.playerLost -> LogKind.Delta,
        CampaignNotes.banditsLost -> LogKind.Delta,
        CampaignNotes.defenderLost -> LogKind.Delta,
        CampaignNotes.sentTo -> LogKind.Delta,
        CampaignNotes.skipped -> LogKind.Delta,
        CampaignNotes.nothingToAttack -> LogKind.Delta)),
      anchor = starts.campaignSettled,
      opening = starts.campaignWords,
      narratedPrefixes = Vector("campaign."))
```

Then `git rm src/main/scala/oathdigital/application/gamelog/CampaignLines.scala`,
and in `ActionLines` delete the `campaigns` field and the `campaignLines`
method. **Do not slice the file on computed indices**; this is the file
slice 2 corrupted twice. Delete the two members by name, then assert:

```bash
grep -c "campaignLines\|new CampaignLines" src/main/scala/oathdigital/application/gamelog/ActionLines.scala
grep -c "usePowerLines\|def named\|def effect\|def powerSource" src/main/scala/oathdigital/application/gamelog/ActionLines.scala
```
Expected: `0`, then a non-zero count. `ActionLines`' constructor then takes
only `words`; drop `choices` and `catalog` and fix the one construction site
in `GameLogFormatter`. `ChoiceWords` is still built there for `DetailLines`;
check before deleting anything else.

- [ ] **Step 4: Update the `noting` vector**

In `GameLogPropertiesSuite`, add `"campaign"` to the expected vector — in
`ProcedureRef.all`'s order, which the assertion compares positionally, so
run it and read the failure rather than guessing the slot. It becomes
eighteen entries, and only `ActionRef.UsePower` is left as a `Rule`.

- [ ] **Step 5: Replace the tests that exercised the rule**

Six tests read hand-built journals through the deleted rule. Each is
replaced, not ported.

**Delete** from `GameLogCampaignSuite`: "a battle plan answer names who
activated it; a flipped plan card names itself", "a plan the bandits apply
is named like any other activation", "a total a plan rewrites in the same
segment is told once, as rewritten", "the attack line counts the skull
faces, and the kills when they differ" (its wordings are now
`ProcedureNoteLinesSuite`'s), "under Weeping Banner two skulls read as
killing four warbands", and "a sacrifice line counts what each warband
adds".

**Delete** from `GameLogRareLinesSuite`: "a won Raid tells what was taken,
then what the defender lost", "a defeated attacker's loss line has no
subject and the bandits win", and "a defeated attacker's loss line counts
only the half killed at the losses". Their branches are Task 1's goldens and
their wordings are Task 3's assertions. Check what else those tests' local
fixtures (`result`, `relic`, `lineage`) are used for before deleting them.

**Add** to `CampaignLogScripts` three scripts, none of them in `named` (no
new golden; Task 1 settled which branches get one), plus two helpers they
share. `ToweringRampart` is the plan both activation scripts use: it is free,
its source is a site edifice, which is public, so both seats name it, and it
is offered to any defender whose pawn stands at its site or whose targets
include it — which a bandit defender's do.

```scala
  private val rampart = CatalogNames.edifice("Towering Rampart")
  private val outriders = CatalogNames.denizen("Outriders")

  /** Whether the parked decision still lists `ref`. A plan already chosen
    * is filtered out of the next pass, so this answers a free plan exactly
    * once without a marker on its card. */
  private def offers(park: Park, ref: DecisionOptionRef): Boolean =
    park.decide.query match
      case one: DecisionQuery.ChooseOne => one.options.exists(_.ref == ref)
      case _ => false

  /** Attack dice that all roll a two-swords-and-skull face, defense dice
    * blank: the roll Outriders is for. */
  private val skulls: CampaignDicePort = new CampaignDicePort:
    def rollAttack(count: Int): Vector[AttackDieFace] =
      Vector.fill(count)(AttackDieFace.TwoSwordsSkull)
    def rollDefense(count: Int): Vector[DefenseDieFace] =
      Vector.fill(count)(DefenseDieFace.Blank)

  /** A Conquest of Ancient City, which the bandits rule and where an
    * intact Towering Rampart stands: a free defender's plan, which a
    * bandit defender applies without being asked. */
  def banditPlanApplied(using munit.Location): Script =
    val (service, act) = atTable("bandit-plan", Table.start
      .pawn(p1, "Ancient City").pawn(p2, "Broken Peaks")
      .pawn(p3, "Buried Giant")
      .boardWarbands(p1, 4)
      .edifice("Towering Rampart", EdificeSide.Intact, "Ancient City")
      .banditsRefilled, Dice.raid)
    val answers: Answers = {
      case Park(Decide(CampaignIds.force, _,
          amount: DecisionQuery.ChooseAmount, _, _, _, _), _, _, _) =>
        ChooseAmountAnswer(amount.max)
      case park if park.decisionId == CampaignIds.sacrifice ||
          park.decisionId == CampaignIds.placement =>
        ChooseAmountAnswer(0)
      case park if park.decisionId == CampaignIds.attackerPlan ||
          park.decisionId == CampaignIds.defenderPlan =>
        ChooseOneAnswer(CampaignIds.finish)
    }
    act.withAnswers(answers).after(MenuPlay.start(p1, ActionRef.Campaign))
    Script("bandit-plan", service, p1)

  /** The `raid` table with an intact Towering Rampart at Ancient City,
    * where p2's pawn stands: p2, the defender, chooses it. */
  def defenderChoseAPlan(using munit.Location): Script =
    val (service, act) = atTable("defender-plan", Table.start
      .pawn(p1, "Ancient City").pawn(p2, "Ancient City")
      .pawn(p3, "Buried Giant")
      .boardWarbands(p1, 3).boardWarbands(p2, 3)
      .edifice("Towering Rampart", EdificeSide.Intact, "Ancient City")
      .adviser(p2, "Birdsong", facedown = true)
      .banditsRefilled, Dice.raid)
    raidAnswers(act, {
      case park if park.decisionId == CampaignIds.defenderPlan &&
          offers(park, DecisionOptionRef.Edifice(rampart)) =>
        ChooseOneAnswer(DecisionOptionRef.Edifice(rampart))
    }).after(MenuPlay.start(p1, ActionRef.Campaign))
    Script("defender-plan", service, p1)

  /** p1 Raids p2 with Outriders faceup and rolls a skull on every attack
    * die: the plan writes the attack again without the skull cap, so one
    * total is told, after the plan's own line. */
  def raidUnderOutriders(using munit.Location): Script =
    val (service, act) = atTable("outriders", Table.start
      .pawn(p1, "Ancient City").pawn(p2, "Ancient City")
      .pawn(p3, "Buried Giant")
      .boardWarbands(p1, 3).boardWarbands(p2, 1)
      .adviser(p1, outriders)
      .adviser(p2, "Birdsong", facedown = true)
      .banditsRefilled, skulls)
    raidAnswers(act, {
      case park if park.decisionId == CampaignIds.attackerPlan &&
          offers(park, DecisionOptionRef.Denizen(outriders)) =>
        ChooseOneAnswer(DecisionOptionRef.Denizen(outriders))
    }).after(MenuPlay.start(p1, ActionRef.Campaign))
    Script("outriders", service, p1)
```

Calibrate each the way Task 1 step 2 calibrates its own: run the assertions
below and read the log when one fails. For `banditPlanApplied`, the bandits
apply a free plan only when the dry run says they can pay, which for
Towering Rampart is always; if the line is missing, check that the edifice
is intact and that Ancient City is among the Conquest's targets. For
`raidUnderOutriders`, p1 must still win: three skull faces score two swords
each when every skull is paid, and p2 brings one board warband, so the
sacrifice is what settles it — `raidAnswers` sacrifices every survivor.

**Add** to `GameLogCampaignSuite`, over those scripts:

```scala
  test("a plan a defender chose names the defender, and a bandit's names " +
      "the bandits"):
    val chosen = lines(defenderChoseAPlan)
    assert(chosen.exists(line =>
      line == s"${playerLabel(p2)} activated Towering Rampart"), chosen)
    val bandits = lines(banditPlanApplied)
    assert(bandits.contains("The bandits activated Towering Rampart"),
      bandits)

  test("a total a plan rewrites is told once, after the plan's own line"):
    val all = lines(raidUnderOutriders)
    val ignored = all.indexOf("Outriders: Skulls ignored.")
    val attacks = all.zipWithIndex.filter(_._1.startsWith("Attack: "))
    assertEquals(attacks.size, 1, all)
    assert(ignored >= 0 && attacks.head._2 > ignored, all)
```

The second assertion is the one plan-time question 4 accepted: the total now
reads **after** the line of a power in its own window, where the rule's
forward read put it before. Pin the new order so that nothing restores the
old one by accident.

**Update** `GameLogCampaignSuite`'s surviving "a Raid tells its targets,
pools, dice, totals, winner and losses in order": its last assertion expects
one joined loss line ending in " was sent to ". The sentence is three now,
so expect, after the winner's line, a line starting with p2's label and
containing " lost ", then one containing " was sent to " as the last line.
Keep the order check: the point of the test is the order.

**Update** `GameLogActionLineSuite:194`, which asserts
`all.toLowerCase.contains("lost 2 warbands")` on the lost Captains Raid.
That line is still written, by `CampaignNotes.lost`; confirm it passes
unchanged and leave it.

**Add** `CampaignNoteReadsSuite`, the readers' own tests, which is where the
deleted synthetic tests' intent belongs: these assert what a builder reads
from a step, with no formatter in the way.

```scala
  /** `result` recorded on `ready`, as the battle records it. */
  private def withResult(ready: ReadyGame, result: CampaignResult)
      : ReadyGame =
    ready.updateCurrent(_.copy(lastCampaignResult = Some(result)))

  /** A Conquest of `sites` that p1 won against `loser`. */
  private def conquestWon(loser: PlayerId, sites: Vector[SiteId])
      : CampaignResult = CampaignResult(p1, CampaignKind.Conquest,
    CampaignDefender.Player(loser), targetSites = sites,
    raidTargets = Vector.empty, force = 4, attackFaces = Vector.empty,
    attackScore = 5, skullLosses = 0, sacrificed = 0,
    defenseFaces = Vector.empty, defenseScore = 1, attackerWins = true)

  /** The attack the sacrifice line names, for `count` warbands worth
    * `worth` each, over an attack already scored at two. */
  private def sacrificeAttack(worth: Option[Int], count: Int)(
      using munit.Location): Int =
    val faces = Vector[DieFace](AttackDieFace.OneSword,
      AttackDieFace.OneSword)
    val rolled = CampaignFixture.board(warbands = 4, supply = 4).ready
      .updateCurrent(current => current.copy(
        rollOutcomes = current.rollOutcomes.updated(CampaignIds.attackPool,
          RollOutcome(CampaignIds.attackPool, faces.size, faces,
            skulls = 0, score = 2)),
        rollPools = worth.fold(current.rollPools)(value =>
          current.rollPools.updated(CampaignIds.sacrificeWorth,
            DicePoolState(value)))))
    val answered = Vector(Answered(CampaignIds.sacrifice,
      DecisionAnswer.ChooseAmountAnswer(count), p1))
    CampaignNotes.sacrificedNote(NoteStates(rolled, None, answered))
      .flatMap(_.args.lift(1)).collect { case NoteArg.Number(n) => n }
      .getOrElse(fail("no sacrifice line"))

  test("the sacrifice line counts what a plan said each warband adds"):
    // Zealots records three; each warband adds one while the pool is
    // absent.
    assertEquals(sacrificeAttack(worth = None, count = 2), 4)
    assertEquals(sacrificeAttack(worth = Some(3), count = 2), 8)

  test("a Conquest defender's loss is net of the half that came back"):
    val site = CatalogNames.site("Broken Peaks")
    val seats = Table.start.pawn(p1, "Ancient City")
      .pawn(p2, "Broken Peaks")
    val result = conquestWon(p2, Vector(site))
    // Four of p2's warbands stood at the target; all four die and half
    // come back to p2's board, which is a loss of two.
    val before = withResult(seats.warbandsAt("Broken Peaks", p2, 4)
      .boardWarbands(p2, 0).ready, result)
    val after = withResult(seats.boardWarbands(p2, 2).ready, result)
    val note = CampaignNotes.lossNote(
      NoteStates(after, Some((before, after)), Vector.empty))
    assertEquals(note.map(_.key), Some(CampaignNotes.playerLost.name))
    assertEquals(note.map(_.args), Some(Vector[NoteArg](
      NoteArg.Player(p2), NoteArg.Number(2))))

  test("a defeated Raid defender's clauses read the burn, the discards " +
      "and the relics set aside"):
    val adviser = CatalogNames.denizen("Birdsong")
    val relic = CatalogNames.relic("Wine of Welcome")
    val seats = Table.start.pawn(p1, "Ancient City")
      .pawn(p2, "Ancient City")
    val result = raidWon(p2)
    val before = withResult(seats.adviser(p2, adviser, facedown = true)
      .relic(p2, relic, facedown = true).favor(p2, 2).ready, result)
    val after = withResult(seats.favor(p2, 1)
      .discarded(Region.Provinces, adviser)
      .update(_.updateCurrent(_.copy(setAsideRelics = Vector(relic))))
      .ready, result)
    val note = CampaignNotes.consequenceNote(
      NoteStates(after, Some((before, after)), Vector.empty))
    assertEquals(note.map(_.args), Some(Vector[NoteArg](
      NoteArg.Player(p2), NoteArg.Clauses(Vector(
        NoteClause.BurnedFavor(1),
        NoteClause.Discarded(Vector(adviser)),
        NoteClause.SetAside(Vector(relic)))))))
```

Write `raidWon(loser)` beside `conquestWon`, with `kind =
CampaignKind.Raid` and `raidTargets = Vector(CampaignRaidTarget.Pawn(loser))`.
Each assertion names the number or the clause list, never the rendered
sentence: the sentences are Task 3's. If a `Table` builder named here does
not exist with that signature, read `Table` and use the one that does —
these states are ordinary quiet boards, nothing exotic.

- [ ] **Step 6: Show the two card-naming state sets agree**

Plan-time question 7. The rule named a discarded or set-aside card at the
before and after of its own `Move`; a note names it at the action's start,
the restated leaf's before-state, and the state after the note. Add to
`GameLogCampaignSuite`:

```scala
  test("a defeated Raid defender's cards read the same for every seat"):
    val script = CampaignLogScripts.raidTakingRelicAndBanner
    val forDefender = lines(script, Some(p2))
    val forAttacker = lines(script, Some(script.actor))
    val forThird = lines(script, Some(p3))
    def clause(from: Vector[String]): String =
      from.find(_.contains(" burned ")).getOrElse(fail(s"no clause: $from"))
    // The defender's own facedown adviser is named to them and to nobody
    // else, which is the one difference the visibility rule makes.
    assert(clause(forDefender).contains("Birdsong"), forDefender)
    assertEquals(clause(forAttacker), clause(forThird))
    assert(clause(forAttacker).contains("a Denizen"), forAttacker)
```

If the note's reading differs from what `raid-spoils`'s golden recorded in
Task 1 for either seat, **stop**: the two state sets disagree, and the
design requires that difference to be named in the ledger rather than
absorbed. Report it with the two strings.

- [ ] **Step 7: Run the Campaign and log suites**

Run: `./sbtw "testOnly oathdigital.application.gamelog.* oathdigital.gameplay.campaign.* oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.powers.campaign.*"`

The goldens will fail. Read each failure **before** rewriting anything: the
expected diff is a shifted `sequence.ordinal` on every migrated line, plus
the loss sentence becoming two or three lines in `raid`, `raid-spoils` and
`captains-raid-lost`. Anything else — a changed span, kind, depth, section,
or a line that moved relative to another — is a defect in the placement, not
a golden to rewrite.

Then rewrite and read:

```bash
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
git diff src/test/resources/gamelog/
```

Check, line by line:

- `raid.actor.log`: `27.0 delta … | p2 lost 1 warband, discarded a Denizen
  and was sent to Broken Peaks` becomes three delta lines — `p2 lost 1
  warband`, `p2 discarded a Denizen`, `p2 was sent to Broken Peaks` — and
  the warband line now reads **before** the spoils line, because the losses
  node runs before the Raid's transfer. Both are the accepted changes.
- every other golden: the sequence numbers move and nothing else. The
  twenty-nine scripts whose logs hold no Campaign must not move at all.

- [ ] **Step 8: Prove it from the other end**

The notes must be journaled where the plan says, not merely produce the
right text. Break each of the three riskiest placements and watch a test
fail:

1. Move the loss note from inside the `CampaignLosses` node to after it.
   `captains-raid-lost`'s loss line must change or vanish (the note's
   `previous` becomes whatever leaf the window ran last). Restore it.
2. Move the spoils note to the end of the transfer node, after the
   consequences sequence. The spoils line must change. Restore it.
3. Move the attack note inside the `CampaignAttackResult` node. The
   Outriders test must fail on the total. Restore it.

A placement that survives all three edits is not being tested.

- [ ] **Step 9: Run the full gate and commit**

Run: `./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py`

```bash
git add -u src/main/scala/oathdigital src/test/scala/oathdigital \
  src/test/resources/gamelog
git add src/test/scala/oathdigital/gameplay/campaign/CampaignNoteReadsSuite.scala
git commit -m "feat(gamelog): Campaign journals its own lines

Twenty-three note leaves placed where each line's facts complete, and
CampaignLines deleted. Three changes the rule could not keep, each
accepted at plan time: the loss sentence is three sentences, because its
clauses are applied in three nodes; the two totals read after the lines
powers in their own result windows write, and the forward read to
segmentEnd is gone; the title plan's activation line names the title
instead of the label its offer authored.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

**Done when:** `CampaignLines.scala` is gone; `ActionRef.UsePower` is the
only `Rule` left; the `noting` vector lists eighteen procedures; the gate is
green; the golden diff is sequence shifts plus the split loss sentence and
nothing else; and each of step 8's three edits fails a test.

---

### Task 5: `ActionLines` is folded away

What is left of `ActionLines` after Task 4 is the `"Used {card}"` fallback
for a power that writes no `used` note, and four shared helpers. The file is
named and documented for "the action line each procedure posts", which it no
longer holds. `usePowerLines` and its helpers go to `PowerLines`, which owns
the power-note path; the companion's helpers go to `LogWords`, where the
rest of the shared span building lives.

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/PowerLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/LogWords.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala`
- Delete: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (three references, no new lines)
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/NoteText.scala` (if it names `ActionLines.plural`)

**Interfaces:**
- Consumes: nothing new.
- Produces: `PowerLines.usePowerLines(power)(journal, run, at, viewer)`,
  and on `LogWords`' companion: `plural`, `PlacePrefix`, `subjectCard`.

- [ ] **Step 1: Move the four companion helpers to `LogWords`**

Into `private[gamelog] object LogWords`, verbatim: `plural(count, one,
many)`, `PlacePrefix`, and `subjectCard`. `LogWords` already has a
`plural(kind: String)` for a card back's plural; the two overloads differ in
arity and both are used, so keep both and say in a comment which is which.

`ActionLines.action(spans)` is a one-line `Posted(LogKind.Action, spans)`
constructor whose only remaining caller is `usedPower`; inline it there
rather than moving it.

Then rewrite every reference. There are eleven, in `ProcedureLog` (three),
`DetailLines` (six), `PowerLines` (two) and the three in `LogScripts`.
`LogWords.PlacePrefix` is shorter than `ActionLines.PlacePrefix`, so no line
grows and `LogScripts` stays at 800.

Run `grep -rn "ActionLines\." src/main src/test` and expect only
`usePowerLines`' own file to match.

- [ ] **Step 2: Move `usePowerLines` into `PowerLines`**

Move `usePowerLines`, `usedPower`, `effect`, `powerSource` and `named`
into `PowerLines`, unchanged, as private members apart from
`usePowerLines`. `PowerLines` already holds `words`, which is all they need.
Add `LogKind.Action` and the `WalkerParked`, `WalkerCompleted`,
`WalkerStepPayload` and `DecisionOptionRef` imports they bring; drop any
import that is then unused, or `-Werror` fails.

Extend `PowerLines`' class doc with a second paragraph:

```scala
  * It also holds the one action line still inferred from the journal: the
  * "Used {card}" fallback for a power that writes no `used` note of its
  * own. `powerSource` is the Game Log's last forward read to
  * `segmentEnd`; the prefix-stability property at
  * `GameLogPropertiesSuite` holds over that one rule now.
```

- [ ] **Step 3: Rewire the formatter**

`ProcedureLogs` takes `PowerLines` in place of `ActionLines`, and the two
refer to each other: `PowerLines` needs `procedures.noteKind`, and
`ProcedureLogs`' `UsePower` arm needs `notes.usePowerLines`. Break it with a
by-name parameter, which is the same hazard `ProcedureNotes`' laziness
documents:

```scala
/** `notes` is by-name because `PowerLines` is built from this table's own
  * `noteKind`. Nothing forces it during construction: `entries` is built
  * from `ProcedureRef.all`, which does not list the parameterized
  * `ActionRef.UsePower`, and that is the only arm that reads it. */
private[gamelog] final class ProcedureLogs(starts: StartLines,
    notes: => PowerLines):
```

with the arm becoming
`lines = ActionLineSource.Rule(notes.usePowerLines(power))`.

In `GameLogFormatter`, delete the `actions` field and order the two:

```scala
  private[gamelog] val procedures = new ProcedureLogs(starts, notes)
  private val notes = new PowerLines(words, wordings,
    procedures.noteKind)
```

Check `ChoiceWords` is still constructed for `DetailLines`, and that
`catalog` is still used by whatever else needs it; if either is now unused,
`-Werror` says so.

Then `git rm src/main/scala/oathdigital/application/gamelog/ActionLines.scala`.

- [ ] **Step 4: Run the gate and the golden proof**

Run: `./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py`
Expected: PASS with **no golden rewritten**. This task moves code and
changes no line, so `git status` must show no file under
`src/test/resources/gamelog`. A golden that moves here is a defect in the
move.

Check the sizes the design predicted: `wc -l
src/main/scala/oathdigital/application/gamelog/{PowerLines,LogWords}.scala`
— roughly 220 and 185, both well inside the cap.

- [ ] **Step 5: Commit**

```bash
git add -u src/main/scala/oathdigital/application/gamelog \
  src/test/scala/oathdigital
git commit -m "refactor(gamelog): fold ActionLines away

Powers' lines are in PowerLines, procedures' lines are in their own
trees, and ProcedureLog says which. The 'Used {card}' fallback and its
forward read to segmentEnd move to PowerLines, which owns the power-note
path; the shared span helpers move to LogWords. No line changed.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

**Done when:** `ActionLines.scala` and `CampaignLines.scala` are both gone,
nothing names `ActionLines`, the gate is green, and no golden moved.

---

### Task 6: The documents, and the slice's own proof

**Files:**
- Modify: `docs/superpowers/specs/2026-10-16-procedures-journal-their-notes-design.md`
- Modify: `docs/superpowers/specs/2026-10-16-game-log-wording-ledger.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Amend the design**

Five amendments, each in the section it belongs to, in the voice the
document already uses — a finding against the code, dated by its slice, not
a changelog entry.

1. **Ruling 7** gains a fourth kind. Say what forced it: two lines join a
   variable list of mixed references, "Targets: " and a won Raid's spoils,
   and `NoteArg.Things` over a closed `NoteThing` sum serves both. Say also
   that `NoteClause` lost `LostWarbands` and `SentTo`, which the loss split
   made unreachable, so the vocabulary grew by one kind and shrank by two
   cases.
2. **"Campaign"** gains a subsection, "The loss sentence could not be one
   note", beside the existing "Search's line could not be one note". The
   facts: `CampaignLines.losses` joined up to five clauses applied in three
   nodes — the warband kills in `CampaignLosses`, the burn, discards and
   set-asides in `CampaignRaidConsequences`, the pawn in
   `CampaignRaidRelocation` — and `NoteStates` reaches one leaf, so the
   sentence is three. Then why the clauses are read from the step and not
   computed: `SparedForce.spare` rewrites the kill operations through
   `LossRewrite` and `Keep` replaces the consequences outright, so the
   Campaign's own arithmetic would state numbers that did not happen. Note
   the one gain: placing each note inside its windowed node makes `Keep`'s
   `Transform` drop the note with the operations it restates, which is what
   `journal.covered` did for the joined sentence.
3. **"Two things slice 4 must settle first"** is answered, in place, rather
   than deleted. The capitalisation blocker **dissolved**: the only
   subject-omitted case is the loser being the actor, which is the
   attacker-loses branch, which carries exactly one clause, so it is a flat
   key and today's words survive. The ledger's wording question stays open
   as an improvement. The card-state-set question is answered by the test
   Task 4 step 6 added: the two readings agree for Campaign's cards for
   every seat.
4. **"What the four application files become"** is corrected: slice 2
   already deleted `NegotiationLines` and `SetupLines`, and `ActionLines`
   was down to 109 lines before this slice, not 484. State the end state
   that was reached rather than the one predicted.
5. **"Slices"** entry 4 records what actually landed, including that the
   two totals now read after the lines of a power in their result window,
   that the title plan's activation line was reworded, and that the design's
   two-commit shape became four — the goldens, the argument kind, the whole
   migration (which cannot be halved, for the reason this plan gives before
   Task 3) and the fold.

- [ ] **Step 2: Amend the ledger**

Append, in the ledger's own form — where the line is built, what it reads,
why it is worth a second look — and strike nothing that is not fixed:

- **The capitalisation entry**: edit it in place. Remove the "Blocks slice 4"
  paragraph and replace it with one sentence saying why it no longer blocks:
  the actor-loser line carries one clause and is a flat key. The wording
  question itself stays open.
- **New: "The title plan's activation line lost its label."** It read
  "{Blue} activated Oathkeeper title: add 1 defense die", the label
  `TitleDefensePlan` authored for its offer; it now reads "{Blue} activated
  the Oathkeeper title". A note's arguments are typed references, never
  text. The dice it adds are told by the Defense total, so nothing is lost
  that the log does not say twice — worth deciding whether the other plan
  activations should name what the plan does, too.
- **New: "A Campaign's totals read after the plan that rewrote them."**
  `CampaignLines.recorded` read forward to `segmentEnd` to suppress a total
  a plan overwrote, so "Attack: 8" read before "Outriders: Skulls ignored.";
  the note reads after the result window closes, so the plan's line comes
  first. Worth deciding which order a reader wants.
- **New: "A defeated Raid defender's losses read as three sentences."** One
  joined sentence became "{Blue} lost 1 warband", "{Blue} burned 1 favor and
  discarded Tinker" and "{Blue} was sent to the Mine", because the clauses
  are applied in three nodes. Worth deciding whether three sentences are
  better read than one; it is not a mechanism question any more.
- **Move the card-play order entry** out: replace its last paragraph with a
  pointer to the ROADMAP task step 3 adds. The question is unchanged; it is
  no longer waiting on this slice.
- **Repoint the five entries whose files are gone.** Three name
  `ActionLines.scala` at a line number (the Trade no-gain wording, "kept
  none", "Played {card} as an adviser") and two name `CampaignLines.scala`
  (the two end-of-Campaign lines and the capitalisation dance). Both files
  are deleted. Point each at the key that holds the wording now —
  `TradeProcedure`, `SearchProcedure`, `CardPlayProcedure`,
  `CampaignNotes.skipped` and `CampaignNotes.nothingToAttack` — with the key
  name, not a line number, so the pointer cannot rot again. This is
  bookkeeping, not a wording change: do not touch what any entry asks.

- [ ] **Step 3: Add the ROADMAP task**

One task, in the Game Log section beside the `Power*` rename:

> **A card play's line before the lines its own batch causes.**
> `WalkerSteps.recordBatch` runs `AdviserArrivalPass.after` and an executed
> trigger's follow-up inside the play's own leaf, so a power whose effect
> fires during the play writes its line first. Putting the play's line back
> in front needs it journaled inside that batch, and a `Note` is not a
> `CoreOperation`. See the wording ledger, "A card play's line now follows
> the lines its own batch caused".

- [ ] **Step 4: The slice's own proof**

Render the goldens at the tip with the sequence numbers and card indices
stripped, and diff against the recording taken before Task 2:

```bash
mkdir -p "$SCRATCH/slice4-after"
for f in src/test/resources/gamelog/*.log; do
  sed -E 's/^[0-9]+\.[0-9]+ //; s/@[0-9]+//g' "$f" \
    > "$SCRATCH/slice4-after/$(basename "$f")"
done
diff -r "$SCRATCH/slice4-before" "$SCRATCH/slice4-after"
```

The only differences allowed:

- the eight files Task 1 added, which are new rather than changed (they are
  in `before` too if the recording was taken after Task 1, which it was);
- in `raid`, `raid-spoils` and `captains-raid-lost`, the loss sentence split
  into two or three lines, and the warband line moving above the spoils
  line.

Any other difference is a defect: find it before writing the commit. Paste
the diff into the commit message — it is this slice's whole proof, the way
slice 3's was.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-10-16-procedures-journal-their-notes-design.md \
  docs/superpowers/specs/2026-10-16-game-log-wording-ledger.md \
  docs/ROADMAP.md
git commit -m "docs(gamelog): record what slice 4 found and what it changed

A fourth argument kind, which amends ruling 7; the loss sentence that
could not be one note; the capitalisation blocker that dissolved once
the split showed the actor-loser line carries one clause; the title
plan's reworded line and the totals' new order, both in the ledger; and
the card-play order question moved to the ROADMAP.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

**Done when:** the design's slice-4 entry describes what landed, the ledger
carries three new entries, one edited and five repointed, the ROADMAP has
the card-play task, and the stripped-golden diff is the two allowed differences.

---

## After the slice

The migration is finished: eighteen procedures journal their own lines, and
`ActionRef.UsePower`'s `"Used {card}"` fallback is the only line the Game
Log still infers. Two things remain, neither in scope here:

- **The `Power*` family's name** (ruling 10), already a ROADMAP task:
  `PowerNote`, `PowerNoted` and `PowerLines` now carry procedures' and game
  rules' lines as well as powers'.
- **The ledger's wording improvements**, now ten open entries, which a
  dedicated commit may take one at a time — each one a golden diff that
  means exactly one thing, which is what ruling 6 was protecting.
