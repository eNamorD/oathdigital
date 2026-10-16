# Game Log Procedure Registry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every per-procedure fact the game log knows is declared once, in one
table whose totality the compiler enforces, so a new procedure cannot post a
stray line by being forgotten.

**Architecture:**

- **Today.** Five places under `application/gamelog` each learn every
  procedure: `StartLines.name`, `StartLines.opening`, `ActionLines.lines`,
  `LogSections.of` (by family, so a new procedure inherits an answer) and
  `DetailLines.NarratedIds` (a `Set[String]`, so a new procedure is forgiven
  for being forgotten and the log posts a stray "Chose …" line).
- **The registry.** A new `ProcedureLog` case class holds one procedure's
  declarations. A `ProcedureLogs` class builds the table from an **exhaustive
  match**, `rules(procedure: ProcedureRef)`, and exposes it as a map built
  from `ProcedureRef.all`. Totality is by construction: a new `ProcedureRef`
  member fails compilation in `rules`, and no lookup can miss.
- **Bodies stay put.** Each entry holds a *reference* to the rule body that
  already exists in `StartLines`, `ActionLines`, `CampaignLines`,
  `NegotiationLines` or `SetupLines` — the shape
  `WalkerProcedureRegistry` uses when it writes `build = SearchProcedure.build`.
  The match moves; the rules do not.
- **One consumer.** `GameLogFormatter.walker` looks the entry up once per
  event and calls through it. It already reached around `StartLines` for
  `name` and `opens` and called `LogSections.forRun` directly, so three of
  the absorbed members had the formatter as their consumer already.
- **`LogSections` goes.** `of` becomes the `isSection` member; `forRun`'s one
  expression moves into the formatter, its only caller.
- **Narration becomes entry data.** `DetailLines` receives the run's entry and
  asks it; the global `NarratedIds` and `NarratedPrefixes` are deleted. The
  two cross-cutting predicates (`ModifierPicks.isDecision`,
  `RerollChoice.isDecision`) stay as the two calls `DetailLines` already
  makes, under the name `DetailLines.crossCutting`.
- **Campaign's two reads get one name.** `CampaignSetup.declaredAt` composes
  `kindOf` and `defenderOf`; its three callers (the log, `CampaignSetup.setup`
  and `CampaignProcedure.targetsStep`) stop composing them by hand.

**Tech Stack:** Scala 3.9.0 on the JVM, munit, built through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-10-16-game-log-procedure-registry-design.md`
(committed as `8dc95f2b`). Read it before starting, with
`docs/superpowers/specs/2026-09-25-game-log-design.md` for the vocabulary and
`docs/superpowers/specs/2026-09-24-replay-posture-decision.md`, which is
settled and which this plan does not touch.

**Starts from:** `main`. Check that
`src/main/scala/oathdigital/application/gamelog/LogSections.scala` is 18 lines
and still defines `of` and `forRun`, and that
`src/main/scala/oathdigital/application/gamelog/DetailLines.scala` is 236
lines and still defines `NarratedIds`.

## Global Constraints

- **Build rules.** `-Werror` with
  `-Wunused:imports,privates,locals,implicits,nowarn` (build.sbt:70-76). An
  unused import or an unused **private** member fails the build. Every rule
  body the registry references must therefore lose its `private` qualifier
  (the enclosing classes are already `private[gamelog]`, so an unqualified
  member is visible to the registry and to nothing outside the package), and
  every import left behind by a deletion must go.
- **File size.** Production and test Scala files stay at or under 800 lines
  (`scripts/check-architecture.py:19`,
  `src/test/scala/oathdigital/gameplay/BackendArchitectureSuite.scala`).
  Current sizes: `ActionLines.scala` 452, `DetailLines.scala` 236,
  `GameLogFormatter.scala` 271, `StartLines.scala` 187, `LogSections.scala`
  18. `LogScripts.scala` is **exactly 800** — the test cap. No task in this
  plan may add a line to it.
- **Line width.** Scala lines stay at or under 80 characters.
- **Layering.** `scripts/check-architecture.py` forbids `gameplay` importing
  `application`. No log vocabulary (`LogSpan`, `LogKind`, `LogJournal`,
  `ProcedureLog`) may appear in a `gameplay` file. Task 5 adds a `gameplay`
  function that returns model types only.
- **Nothing new is journaled.** No new event, no new durable field, no
  replay-time validation anywhere in this plan.
- **Golden log files.** `src/test/resources/gamelog/*.log` must not change in
  this plan. Every task ends with `git diff --exit-code --
  src/test/resources/gamelog` clean. If a task's diff touches a golden file,
  the task is wrong: stop and report. `GAMELOG_GOLDEN=write` exists and is
  **not** authorized here.
- **Full gate.** `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` exit 0,
  and `git diff --check` clean, before each commit. While iterating, the
  faster `./sbtw "testOnly oathdigital.application.gamelog.*"` is enough.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree. Symlink the main checkout's
  `.tooling` and `node_modules` into it before the first `./sbtw`.
- **Commit trailer.** Every commit in this plan ends with
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`, including commits
  made by a subagent — the plan's trailer overrides the subagent's own.

## Six resolutions the spec left open

Record these; a reviewer will ask.

1. **The registry is a class, not an object.** The rule bodies are methods on
   `StartLines`, `ActionLines` and their helpers, which carry constructor
   dependencies (`LogWords`, `ChoiceWords`, `ExecutableCatalog`). A table of
   references to them is therefore built per formatter:
   `new ProcedureLogs(starts, actions)`, constructed in `GameLogFormatter`
   after both. The spec's `val entries` becomes a private field of that class.
2. **`ActionRef.UsePower` is not in `ProcedureRef.all`.** The map covers the
   25 finite members; `of` falls back to `rules(procedure)` for a use-power
   reference, which builds its entry on demand. This is a fallback to the same
   exhaustive match, not a lookup that can miss — the match has a
   `case ActionRef.UsePower(power)` arm, so the fallback is total too. It
   allocates one entry per formatted event of a use-power run; the entry is
   five references and a `Set`, and the formatter already allocates per event.
3. **Parameter order differs from the spec's listing.** The spec shows `lines`
   after the defaulted members. Scala admits a non-defaulted parameter after
   defaulted ones only for named-argument calls, and every construction here
   uses named arguments, but the plan puts the two non-defaulted members
   (`tag`, then `lines`) first to remove the question. The member set is the
   spec's, unchanged.
4. **The spec's size forecast for `ActionLines` was wrong, in the direction
   this plan's ruling chose.** It predicted 452 → about 330, which assumed the
   rule bodies travel into the table. The settled ruling is that entries hold
   function references and bodies stay in place, so `ActionLines` does not
   shrink: the 19 branch bodies become 19 named members, which **adds** about
   40 lines of signature and removes about 35 of match scaffolding.
   Expect `ActionLines` near 455 and `ProcedureLog.scala` near 190 after
   Task 3. Nothing approaches the 800-line cap, and no file is split.

5. **An or-pattern of named members is not a family wildcard.** Ruling 2
   forbids `case _: PhaseTransitionRef => bare`, which answers for members
   that do not exist yet. `case ActionRef.PeekSiteRelics |
   ActionRef.RevealOwnedRelic => …` names every member it answers for, so a
   new member still fails compilation — which is the ruling's whole purpose.
   The table groups rows that declare exactly the same thing and splits them
   the moment they differ, as Task 3 splits the phase menus out of the
   triggered row. `case ActionRef.UsePower(power)` is the one type-level arm,
   because the reference is parameterized.
6. **Several rule bodies ignore `run` or `viewer`.** The four function
   aliases give every rule one signature, so a body that needs neither still
   takes both. `-Wunused` is configured for
   `imports,privates,locals,implicits,nowarn` and not for `params`
   (build.sbt:75), so an unused parameter does not fail the build. Do not add
   `@nowarn` and do not rename a parameter to `_`: the name documents the
   position.

## Delivery

The spec plans two slices. Slice 1 is pure co-location, proven by
byte-identical golden logs; slice 2 is the only part that touches
behaviour-adjacent code.

- **Slice 1 — co-location:** Tasks 1 and 2.
- **Slice 2 — narration and Campaign:** Tasks 3, 4 and 5.

## File Structure

| File | Responsibility after this plan |
|---|---|
| `gamelog/ProcedureLog.scala` (new) | The entry type, its four function aliases, and `ProcedureLogs`: the exhaustive match and the map built from it. ~190 lines. |
| `gamelog/StartLines.scala` | Tag assembly, the cost and queued-by spans, modifier naming, and the six named anchors the table references. No per-procedure match. |
| `gamelog/ActionLines.scala` | The 19 named action-line bodies and the shared helpers. No per-procedure match. |
| `gamelog/DetailLines.scala` | Delta, roll and decision rules, which name no procedure, and `crossCutting`. No narration list. |
| `gamelog/GameLogFormatter.scala` | Looks the entry up once per event and calls through it; builds the section itself. |
| `gamelog/LogSections.scala` | **Deleted.** |
| `campaign/CampaignSetup.scala` | Gains `declaredAt`, the one named read of a Campaign's kind and defender. |
| `gamelog/GameLogPropertiesSuite.scala` | Gains the narration-coverage property. |

---

### Task 1: The entry type, the table, and the start lines

`ProcedureLog` is introduced with every member except `lines`, which Task 2
adds. `StartLines` loses `name`, `opening`, `opens` and the `continued`
match; `LogSections` is deleted. The formatter consults the registry for the
tag, the anchor, the opening, the continued line, the section and
`PowerLines.own`'s `opens` flag, and still calls `actions.lines` for the
action line.

**Files:**
- Create: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/StartLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala:45-47,196-200,207-230`
- Delete: `src/main/scala/oathdigital/application/gamelog/LogSections.scala`
- Test: no new test. `GameLogGoldenSuite`, `GameLogStartLineSuite`,
  `GameLogSectionSuite` and `GameLogPropertiesSuite` stand unchanged and are
  the proof.

**Interfaces:**
- Consumes: nothing.
- Produces, all `private[gamelog]`:
  - `type Anchor = (LogJournal, Run, Int) => Boolean`
  - `type Opening = (LogJournal, Run, Int, Option[PlayerId]) => Vector[LogSpan]`
  - `type Continued = (LogJournal, Run, Int, Option[PlayerId]) => Option[Posted]`
  - `type ActionLine = (LogJournal, Run, Int, Option[PlayerId]) => Vector[Posted]`
  - `object Anchor { val AtFirstEvent: Anchor }`,
    `object Opening { val none: Opening }`,
    `object Continued { val none: Continued }`,
    `object ActionLine { val none: ActionLine }`
  - `final case class ProcedureLog(tag: Option[String], anchor: Anchor =
    Anchor.AtFirstEvent, opening: Opening = Opening.none, continued: Continued
    = Continued.none, isSection: Boolean = true)`
  - `final class ProcedureLogs(starts: StartLines)` with
    `def of(procedure: ProcedureRef): ProcedureLog`. Task 2 adds the second
    constructor parameter, `actions: ActionLines`, when the table starts
    naming action lines.
  - On `StartLines`: `val paysCost: Anchor`, `val opensDicePool: Anchor`,
    `val picksSettled: Anchor`, `val adviserPicked: Anchor`,
    `val campaignSettled: Anchor`, `val campaignWords: Opening`,
    `def continuedRecover(journal: LogJournal, run: Run, at: Int, viewer:
    Option[PlayerId]): Option[Posted]`, and
    `def start(journal: LogJournal, run: Run, at: Int, viewer:
    Option[PlayerId], log: ProcedureLog): Vector[Posted]`
  - On `GameLogFormatter`: `private[gamelog] val procedures: ProcedureLogs`

- [ ] **Step 1: Write `ProcedureLog.scala`**

The table names no action line yet, so `ProcedureLogs` takes only `starts`;
Task 2 adds `actions`.

```scala
package oathdigital.application.gamelog

import oathdigital.model._
import oathdigital.protocol.projection.LogSpan

/** Whether the tag posts at this event: the anchor each procedure declares
  * (log action sections design, "The tag entry"). */
private[gamelog] type Anchor = (LogJournal, Run, Int) => Boolean

/** What the line after the tag says beyond the modifiers. */
private[gamelog] type Opening =
  (LogJournal, Run, Int, Option[PlayerId]) => Vector[LogSpan]

/** The repeated line a procedure posts when its player pays again. */
private[gamelog] type Continued =
  (LogJournal, Run, Int, Option[PlayerId]) => Option[Posted]

/** The action line a procedure posts, at the event its facts complete. */
private[gamelog] type ActionLine =
  (LogJournal, Run, Int, Option[PlayerId]) => Vector[Posted]

private[gamelog] object Anchor:
  /** The run's first event, where a procedure declares no later anchor. */
  val AtFirstEvent: Anchor = (_, run, at) => at == run.first

private[gamelog] object Opening:
  val none: Opening = (_, _, _, _) => Vector.empty

private[gamelog] object Continued:
  val none: Continued = (_, _, _, _) => None

private[gamelog] object ActionLine:
  val none: ActionLine = (_, _, _, _) => Vector.empty

/** What one procedure declares to the game log: its tag and where the tag
  * posts, what its opening line says, the line it repeats when the player
  * pays again, and whether its run draws a section band. Every member is a
  * declaration, not a wording: the rules themselves stay in `StartLines`,
  * `ActionLines` and their helpers, and an entry names them.
  */
private[gamelog] final case class ProcedureLog(
    tag: Option[String],
    anchor: Anchor = Anchor.AtFirstEvent,
    opening: Opening = Opening.none,
    continued: Continued = Continued.none,
    isSection: Boolean = true)

/** Every procedure's log lines, in one table.
  *
  * `rules` is an exhaustive match over the sealed `ProcedureRef` hierarchy,
  * with every finite member named and no family wildcard, so a new procedure
  * fails compilation here. `entries` is built from that match over
  * `ProcedureRef.all`, so the table is total by construction and no lookup
  * can miss — unlike `WalkerProcedureRegistry`, whose literal map is guarded
  * by a test and answers a miss at runtime.
  *
  * `ActionRef.UsePower` is parameterized, so `ProcedureRef.all` does not list
  * it and `of` builds its entry from the same match on demand.
  */
private[gamelog] final class ProcedureLogs(starts: StartLines):
  private val entries: Map[ProcedureRef, ProcedureLog] =
    ProcedureRef.all.map(procedure => procedure -> rules(procedure)).toMap

  def of(procedure: ProcedureRef): ProcedureLog =
    entries.getOrElse(procedure, rules(procedure))

  private def rules(procedure: ProcedureRef): ProcedureLog = procedure match
    case ActionRef.Search => ProcedureLog(tag = Some("Search"),
      anchor = starts.picksSettled)
    case ActionRef.PlayFacedownAdviser =>
      ProcedureLog(tag = Some("Play facedown adviser"),
        anchor = starts.adviserPicked)
    case ActionRef.Recover => ProcedureLog(tag = Some("Recover"),
      anchor = starts.opensDicePool, continued = starts.continuedRecover)
    case ActionRef.Forge => ProcedureLog(tag = Some("Forge"))
    case ActionRef.Travel => ProcedureLog(tag = Some("Travel"),
      anchor = starts.picksSettled)
    case ActionRef.Muster => ProcedureLog(tag = Some("Muster"),
      anchor = starts.paysCost)
    case ActionRef.Trade => ProcedureLog(tag = Some("Trade"),
      anchor = starts.paysCost)
    case ActionRef.Challenge => ProcedureLog(tag = Some("Challenge"))
    case ActionRef.Campaign => ProcedureLog(tag = Some("Campaign"),
      anchor = starts.campaignSettled, opening = starts.campaignWords)
    // Wake's own action: its lines stand outside any band.
    case ActionRef.TakeWealth => ProcedureLog(tag = None, isSection = false)
    // No modifier window, so no tag.
    case ActionRef.PlaceBannerResource | ActionRef.Negotiation |
        ActionRef.PeekSiteRelics | ActionRef.RevealOwnedRelic |
        ActionRef.MoveWarbands => ProcedureLog(tag = None)
    case ActionRef.UsePower(_) => ProcedureLog(tag = None)
    // A phase change shows in the lines around it, and neither it nor a
    // triggered procedure is an action a reader looks for.
    case PhaseTransitionRef.EndWake | PhaseTransitionRef.BeginRest |
        PhaseTransitionRef.FinishRest =>
      ProcedureLog(tag = None, isSection = false)
    case TriggeredProcedureRef.Oathkeeper | TriggeredProcedureRef.Setup |
        TriggeredProcedureRef.ForcedWake |
        TriggeredProcedureRef.FollowUpOffer |
        TriggeredProcedureRef.WakeMenu | TriggeredProcedureRef.RestMenu |
        TriggeredProcedureRef.ActMenu =>
      ProcedureLog(tag = None, isSection = false)
```

- [ ] **Step 2: Run the build to see it fail**

Run: `./sbtw compile`
Expected: FAIL. `ProcedureLogs` references `starts.picksSettled`,
`starts.adviserPicked`, `starts.opensDicePool`, `starts.paysCost`,
`starts.campaignSettled`, `starts.campaignWords` and
`starts.continuedRecover`, none of which exists yet.

- [ ] **Step 3: Replace `StartLines`' per-procedure match with named anchors**

In `StartLines.scala`, delete `opening` (lines 69-110 of the current file),
`opens` (112-113) and `name` (115-130), delete the `continued` match
(49-57), and rename the private `campaignWords(kind, defender)` helper to
`campaignLine` so the new `Opening` may take its name. Replace `start`
(27-45) and add the anchors. The six anchors keep each case's own
`|| completing` disjunct exactly where the deleted match had it: the default
anchor has none, which is why `Anchor.AtFirstEvent` is a bare
`at == run.first`.

```scala
  def start(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId], log: ProcedureLog): Vector[Posted] =
    if run.started.nonEmpty then Vector.empty
    else log.tag.filter(_ => log.anchor(journal, run, at)).toVector
      .flatMap { named =>
        val said = log.opening(journal, run, at, viewer)
        val chosen = modifiers(journal, run, at, viewer)
        val line =
          if said.isEmpty then
            Option.when(chosen.nonEmpty)(Text("With ") +: chosen)
          else Some(said ++ (if chosen.isEmpty then Vector.empty
            else Text(" with ") +: chosen))
        Posted(LogKind.Section, Text(named) +:
          (queuedBy(journal, run, at, viewer) ++ cost(journal, run, at))) +:
          line.toVector.map(Posted(LogKind.Action, _))
      }

  /** Recover spends Supply again each time the player continues rolling. */
  def continuedRecover(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Option[Posted] =
    if !run.started.exists(_ != journal.segmentEnd(at)) then None
    else
      val spent = journal.supplySpent(at, run.actor)
      Option.when(spent > 0)(Posted(LogKind.Action,
        Vector(Text("Continued"), LogSpan.Cost(spent))))

  private def completing(journal: LogJournal, at: Int): Boolean =
    journal.event(at).isInstanceOf[WalkerCompleted]

  /** Muster and Trade: their cost step, after the source decision. */
  val paysCost: Anchor = (journal, run, at) =>
    paysCostAt(journal, run, at) || completing(journal, at)

  /** Recover: the step that opens its dice pool, after its modifier
    * decision. */
  val opensDicePool: Anchor = (journal, run, at) =>
    opensDicePoolAt(journal, at) || completing(journal, at)

  /** Travel and Search: the first step that does anything after the source
    * or destination is picked — a toll, a modifier's payment, the Supply,
    * the draw or the move. A pick's payment does not count: the tag waits
    * until no more picks can follow, so that it names them all. */
  val picksSettled: Anchor = (journal, run, at) =>
    (journal.ops(at).nonEmpty &&
      ModifierPicks.settled(journal.answers(run, at))) ||
      completing(journal, at)

  /** The facedown adviser's pick: every modifier is picked by then. */
  val adviserPicked: Anchor = (journal, run, at) =>
    completing(journal, at) || (journal.event(at) match
      case WalkerStepRecorded(_, ChoicePayload(id, _, _), _, _) =>
        id == CardPlayProcedure.facedownAdviserDecisionId
      case _ => false)

  /** Campaign: the first event by which kind and defender are both settled.
    * A Campaign that ended before its kind was settled is tagged where it
    * completes, with an empty opening. */
  val campaignSettled: Anchor = (journal, run, at) =>
    campaign(journal, run, at).nonEmpty || completing(journal, at)

  /** Campaign's kind and defender, or nothing when the Campaign completed
    * before they were settled. Read twice per tagged event, once by
    * [[campaignSettled]] and once here; both reads are pure. */
  val campaignWords: Opening = (journal, run, at, _) =>
    campaign(journal, run, at).getOrElse(Vector.empty)
```

Rename the two surviving private predicates to free the anchor names:
`paysCost(journal, run, at)` becomes `paysCostAt`, and
`opensDicePool(journal, at)` becomes `opensDicePoolAt`. Their bodies do not
change. `campaign` and `modifiers` do not change, beyond `campaignWords`
inside `campaign` becoming `campaignLine`.

Drop the now-unused imports from `StartLines.scala`:
`oathdigital.gameplay.walker.WalkerCompleted` **stays** (used by
`completing`), `ChoicePayload` and `WalkerStepRecorded` **stay** (used by
`adviserPicked` and `queuedBy`), `ProcedureDequeued` **stays**. Nothing is
dropped; check with `./sbtw compile` rather than by eye.

- [ ] **Step 4: Point the formatter at the registry and delete `LogSections`**

In `GameLogFormatter.scala`:

1. Add `LogSection` to the projection import on line 12-13.
2. After `private val actions = new ActionLines(words, choices, catalog)`,
   add:

```scala
  // Built after `starts`: the table holds references to its rules, and
  // `entries` is built eagerly. Task 2 adds `actions` here.
  private[gamelog] val procedures = new ProcedureLogs(starts)
```

3. In `eventLines`, the `FollowUpSkipped` arm reads `starts.name`. Replace
   `starts.name(entry.procedure).getOrElse(entry.procedure.key)` with
   `procedures.of(entry.procedure).tag.getOrElse(entry.procedure.key)`.
4. Replace the body of the `case Some(run)` arm of `walker`:

```scala
      case Some(run) =>
        val log = procedures.of(run.procedure)
        val start = starts.start(journal, run, at, viewer, log)
        val begun = if start.isEmpty then run
          else run.copy(started = Some(journal.segmentEnd(at)))
        // Which runs are sections (log action sections design, "Where a
        // section comes from"): every top-level action is one. Wake's Take
        // Wealth, phase transitions and triggered procedures are not actions
        // a reader looks for, so their lines stand outside any band.
        val section = Option.when(log.isSection)(
          LogSection(journal.sequence(begun.first), begun.procedure.key))
        val lines = start ++
          (if start.isEmpty then Vector.empty
            else notes.held(journal, begun, at, viewer)) ++
          log.continued(journal, begun, at, viewer).toVector ++
          actions.lines(journal, begun, at, viewer) ++
          details.lines(journal, begun, at, viewer) ++
          notes.own(journal, begun, at, log.tag.nonEmpty, viewer)
        // A headline the run posts (a turn or phase change) is in no section.
        val posted = lines.map(_.copy(section = section)) ++
          headlines(journal, at)
        val next = journal.event(at) match
          case _: WalkerCompleted => None
          case _ => Some(begun)
        (posted, next)
```

5. Delete `src/main/scala/oathdigital/application/gamelog/LogSections.scala`.

Run: `git rm src/main/scala/oathdigital/application/gamelog/LogSections.scala`

- [ ] **Step 5: Run the gamelog suites**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS, every suite, with no test file edited.

- [ ] **Step 6: Prove the golden logs did not move**

Run: `git diff --exit-code -- src/test/resources/gamelog`
Expected: exit 0, no output. A diff here means the co-location changed a
line: stop and report which script and which line.

- [ ] **Step 7: Run the full gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: exit 0. `BackendArchitectureSuite` passes, which is the file-size
and layering check.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala src/main/scala/oathdigital/application/gamelog/StartLines.scala src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala src/main/scala/oathdigital/application/gamelog/LogSections.scala
git commit -m "$(cat <<'EOF'
refactor(gamelog): declare each procedure's tag and section in one table

A new ProcedureLog entry holds what one procedure declares to the log, and
ProcedureLogs builds the table from an exhaustive match over ProcedureRef, so
a new procedure fails compilation rather than inheriting an answer. The tag's
name and anchor, the opening line, Recover's continued line and the section
flag move out of StartLines' and LogSections' per-procedure matches; the rules
themselves stay where they are and the table names them. LogSections is gone:
its one section expression now sits in the formatter, its only caller.

Golden logs are byte-identical, which is the proof that this only relocates.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: The action line joins the table

`ActionLines.lines`' 19-branch match over `run.procedure` becomes 19 named
members, and the entry gains `lines`. Nothing about any rule changes: each
branch body moves verbatim into a def whose signature is `ActionLine`, with
only the locals it uses declared at the top.

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala:28-206`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala`
- Test: no new test. The golden logs are the proof.

**Interfaces:**
- Consumes: `ProcedureLog`, `ActionLine`, `ProcedureLogs` from Task 1.
- Produces: `ProcedureLogs` gains its second constructor parameter,
  `actions: ActionLines`, and `GameLogFormatter` passes it:
  `new ProcedureLogs(starts, actions)`. On `ActionLines`, 19 members each of
  type `ActionLine`, named
  `travelLines`, `oathkeeperLines`, `finishRestLines`, `setupLines`,
  `searchLines`, `playedAdviser` (which already has that shape),
  `musterLines`, `tradeLines`, `takeWealthLines`, `recoverLines`,
  `forgeLines`, `campaignLines`, `challengeLines`, `bannerLines`,
  `peekLines`, `revealLines`, `warbandLines`, `negotiationLines`, and
  `usePowerLines(power: PowerId)`, which is curried and eta-expands to
  `ActionLine`. `ActionLines.lines` is deleted. `ProcedureLog` gains
  `lines: ActionLine` as its second non-defaulted member.

- [ ] **Step 1: Add `lines` to the entry and to every table row**

In `ProcedureLog.scala`, give `ProcedureLogs` its second constructor
parameter:

```scala
private[gamelog] final class ProcedureLogs(starts: StartLines,
    actions: ActionLines):
```

Then insert `lines` second in the entry, with no default so that every row
must state it:

```scala
private[gamelog] final case class ProcedureLog(
    tag: Option[String],
    lines: ActionLine,
    anchor: Anchor = Anchor.AtFirstEvent,
    opening: Opening = Opening.none,
    continued: Continued = Continued.none,
    isSection: Boolean = true)
```

Then give every row its `lines`. The five rows that post nothing say so once,
which is the point of saying it in the table:

```scala
  private def rules(procedure: ProcedureRef): ProcedureLog = procedure match
    case ActionRef.Search => ProcedureLog(tag = Some("Search"),
      lines = actions.searchLines, anchor = starts.picksSettled)
    case ActionRef.PlayFacedownAdviser =>
      ProcedureLog(tag = Some("Play facedown adviser"),
        lines = actions.playedAdviser, anchor = starts.adviserPicked)
    case ActionRef.Recover => ProcedureLog(tag = Some("Recover"),
      lines = actions.recoverLines, anchor = starts.opensDicePool,
      continued = starts.continuedRecover)
    case ActionRef.Forge => ProcedureLog(tag = Some("Forge"),
      lines = actions.forgeLines)
    case ActionRef.Travel => ProcedureLog(tag = Some("Travel"),
      lines = actions.travelLines, anchor = starts.picksSettled)
    case ActionRef.Muster => ProcedureLog(tag = Some("Muster"),
      lines = actions.musterLines, anchor = starts.paysCost)
    case ActionRef.Trade => ProcedureLog(tag = Some("Trade"),
      lines = actions.tradeLines, anchor = starts.paysCost)
    case ActionRef.Challenge => ProcedureLog(tag = Some("Challenge"),
      lines = actions.challengeLines)
    case ActionRef.Campaign => ProcedureLog(tag = Some("Campaign"),
      lines = actions.campaignLines, anchor = starts.campaignSettled,
      opening = starts.campaignWords)
    // Wake's own action: its lines stand outside any band.
    case ActionRef.TakeWealth => ProcedureLog(tag = None,
      lines = actions.takeWealthLines, isSection = false)
    // No modifier window, so no tag.
    case ActionRef.PlaceBannerResource => ProcedureLog(tag = None,
      lines = actions.bannerLines)
    case ActionRef.Negotiation => ProcedureLog(tag = None,
      lines = actions.negotiationLines)
    case ActionRef.PeekSiteRelics => ProcedureLog(tag = None,
      lines = actions.peekLines)
    case ActionRef.RevealOwnedRelic => ProcedureLog(tag = None,
      lines = actions.revealLines)
    case ActionRef.MoveWarbands => ProcedureLog(tag = None,
      lines = actions.warbandLines)
    // A power that plays a card as if searched (Oracle) posts where the card
    // went, as a Search does.
    case ActionRef.UsePower(power) => ProcedureLog(tag = None,
      lines = actions.usePowerLines(power))
    // The phase changes show in the lines around them.
    case PhaseTransitionRef.EndWake | PhaseTransitionRef.BeginRest =>
      ProcedureLog(tag = None, lines = ActionLine.none, isSection = false)
    case PhaseTransitionRef.FinishRest => ProcedureLog(tag = None,
      lines = actions.finishRestLines, isSection = false)
    case TriggeredProcedureRef.Oathkeeper => ProcedureLog(tag = None,
      lines = actions.oathkeeperLines, isSection = false)
    case TriggeredProcedureRef.Setup => ProcedureLog(tag = None,
      lines = actions.setupLines, isSection = false)
    // Each forced power writes its own lines. The follow-up offer and the
    // phase menus write none: a pick starts its own walk, which writes them,
    // and a Skip is a line of its own (`FollowUpSkipped`).
    case TriggeredProcedureRef.ForcedWake |
        TriggeredProcedureRef.FollowUpOffer |
        TriggeredProcedureRef.WakeMenu | TriggeredProcedureRef.RestMenu |
        TriggeredProcedureRef.ActMenu =>
      ProcedureLog(tag = None, lines = ActionLine.none, isSection = false)
```

- [ ] **Step 2: Run the build to see it fail**

Run: `./sbtw compile`
Expected: FAIL, one error per member the table names and `ActionLines` does
not have yet.

- [ ] **Step 3: Replace the match with the 19 named bodies**

Delete `ActionLines.lines` (lines 28-206) and put these in its place. Every
body is the branch's own, verbatim; the only edits are the locals each one
declares for itself, the two inner `val`s renamed where they would have
shadowed their def (`recovered` to `taken`, `forged` to `made`), and
`private` dropped from `playedAdviser`.

```scala
  def travelLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    journal.ops(at).collect {
      case OpStep(Move(Piece.Pawn(mover), _,
          PositionedLocation(Location.Site(to), _, _), _, _), _, _)
          if mover == run.actor =>
        action(Vector(Text("Travelled to "), words.site(to)))
    }

  def oathkeeperLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    journal.ops(at).collect {
      case OpStep(SetOathkeeper(holder), _, _) =>
        Posted(LogKind.Trigger, Vector(Text("Oathkeeper passed to "),
          holder.fold[LogSpan](Text("the bank"))(words.player)))
    }

  def finishRestLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    journal.ops(at).collect {
      case OpStep(GainSupply(player, _), before, after) =>
        Posted(LogKind.Delta, Vector(Text(
          s"Increased supply from ${LogJournal.supply(before, player)} " +
            s"to ${LogJournal.supply(after, player)}")))
    }

  def setupLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    setup.lines(journal, at, viewer)

  def searchLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    search(journal, run, at, viewer) ++
      playedAdviser(journal, run, at, viewer)

  /** A power's covering line restates a gain of its own, such as a vow's
    * warbands after the Muster's. */
  def musterLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    if journal.covered(at) then Vector.empty
    else journal.ops(at).collect {
      case OpStep(Move(Piece.Warbands(_, count),
          PositionedLocation(Location.WarbandBank(_), _, _),
          PositionedLocation(Location.PlayArea(taker), _, _),
          _, _), before, after)
          if taker == run.actor =>
        action(Vector(Text(
          s"Mustered $count ${plural(count, "warband", "warbands")} with ")) ++
          source(journal, run, at, MusterProcedure.decisionId, before, after,
            viewer))
    }

  def tradeLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    val actor = run.actor
    val gained = journal.ops(at).collect {
      case OpStep(Move(piece @ Piece.Favor(_),
          PositionedLocation(Location.FavorBank(_), _, _),
          PositionedLocation(Location.PlayArea(taker), _, _),
          _, _), before, after)
          if taker == actor =>
        traded(journal, run, at, resource(piece), before, after, viewer)
      case OpStep(Move(piece @ Piece.Secrets(_),
          PositionedLocation(Location.SharedBank, _, _),
          PositionedLocation(Location.PlayArea(taker), _, _),
          _, _), before, after)
          if taker == actor =>
        traded(journal, run, at, resource(piece), before, after, viewer)
    }
    // Trading favor for secrets with no matching adviser gains nothing: the
    // line is posted at completion instead.
    val gainedEarlier = (run.first until at).exists(index =>
      journal.ops(index).exists(isTradeGain(actor)))
    if gained.nonEmpty || gainedEarlier ||
        !journal.event(at).isInstanceOf[WalkerCompleted] then gained
    else journal.readyBefore(at).toVector.map(ready =>
      traded(journal, run, at, "no secrets", ready, ready, viewer))

  def takeWealthLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    journal.ops(at).collect {
      case OpStep(Move(piece, PositionedLocation(Location.Site(site), _, _),
          PositionedLocation(Location.PlayArea(taker), _, _), _, _), _, _)
          if taker == run.actor && resource(piece).nonEmpty =>
        action(Vector(Text(s"Took ${resource(piece)} from "),
          words.site(site)))
    }

  def recoverLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    val actor = run.actor
    val taken = journal.ops(at).collect {
      case OpStep(Move(Piece.Card(relic: RelicId),
          PositionedLocation(Location.Site(site), _, _),
          PositionedLocation(Location.PlayArea(taker), _, _),
          _, _), before, after)
          if taker == actor =>
        action(Vector(Text("Recovered ")) ++
          words.one(words.card(relic, before, after, viewer)) ++
          Vector(Text(" at "), words.site(site)))
    }
    // A Recover that succeeds with no relic to take posts nothing.
    val failed =
      if journal.event(at).isInstanceOf[WalkerCompleted] &&
          stopped(journal, run, at) then
        journal.readyBefore(run.first).flatMap(LogJournal.pawnSite(_, actor))
          .toVector.map(site =>
            action(Vector(Text("Failed to recover at "), words.site(site))))
      else Vector.empty
    taken ++ failed

  def forgeLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    val paid = journal.ops(at).collect {
      case OpStep(PayCost(_, Location.OnCard(card), cost, _, _, _),
          before, after) => (card, cost, before, after)
    }
    val favor = paid.collect { case (card, cost, before, after)
      if cost.favor > 0 => words.card(card, before, after, viewer) }
    val secret = paid.collect { case (card, cost, before, after)
      if cost.secret > 0 => words.card(card, before, after, viewer) }
    val halves = Vector(
      Option.when(favor.nonEmpty)(Text("favor on ") +: words.cards(favor)),
      Option.when(secret.nonEmpty)(Text("secrets on ") +: words.cards(secret))
    ).flatten
    val payment = if halves.isEmpty then Vector.empty
      else Vector(action(Text("Placed ") +: LogWords.join(halves)))
    val made = journal.ops(at).collect {
      case OpStep(Play(relic: RelicId,
          PositionedLocation(Location.Deck(CardDeck.Relic), _, _), _, _, _),
          before, after) =>
        action(Vector(Text("Forged ")) ++
          words.one(words.card(relic, before, after, viewer)))
    }
    payment ++ made

  def campaignLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    campaigns.lines(journal, run, at, viewer)

  def challengeLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    journal.ops(at).collect {
      case OpStep(Move(Piece.Banner(banner), PositionedLocation(from, _, _),
          PositionedLocation(Location.PlayArea(taker), _, _), _, _), _, _)
          if taker == run.actor =>
        val holder = from match
          case Location.PlayArea(player) => words.player(player)
          case _ => Text("the bank")
        action(Vector(Text("Took "), words.banner(banner), Text(" from "),
          holder) ++ paidOnto(journal, run, at, banner).toVector.map(paid =>
            Text(s" with $paid")))
    }

  def bannerLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    journal.ops(at).collect {
      case OpStep(Move(piece,
          PositionedLocation(Location.PlayArea(giver), _, _),
          PositionedLocation(Location.OnBanner(banner), _, _), _, _), _, _)
          if giver == run.actor && resource(piece).nonEmpty =>
        action(Vector(Text(s"Placed ${resource(piece)} on "),
          words.banner(banner)))
    }

  /** The minor actions post their legacy lines, now as action lines. */
  def peekLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    val peeked = journal.ops(at).collect {
      case OpStep(Peek(_, relic, Location.Site(site)), before, after) =>
        site -> words.card(relic, before, after, viewer)
    }
    peeked.headOption.toVector.map((site, _) => action(
      Vector(Text("Peeked at ")) ++ words.cards(peeked.map(_._2)) ++
        Vector(Text(" at "), words.site(site))))

  def revealLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    journal.ops(at).collect {
      case OpStep(Reveal(relic, Location.PlayArea(owner)), before, after)
          if owner == run.actor =>
        action(Text("Revealed ") +:
          words.one(words.card(relic, before, after, viewer)))
    }

  def warbandLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    journal.ops(at).collect {
      case OpStep(Move(Piece.Warbands(_, count),
          PositionedLocation(Location.PlayArea(_), _, _),
          PositionedLocation(Location.Site(site), _, _), _, _), _, _) =>
        action(Vector(Text(s"Moved $count " +
          s"${plural(count, "warband", "warbands")} to "), words.site(site)))
      case OpStep(Move(Piece.Warbands(_, count),
          PositionedLocation(Location.Site(site), _, _),
          PositionedLocation(Location.PlayArea(_), _, _), _, _), _, _) =>
        action(Vector(Text(s"Moved $count " +
          s"${plural(count, "warband", "warbands")} from "),
          words.site(site)))
    }

  def negotiationLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    negotiations.lines(journal, run, at, viewer)

  /** Only a run that answered a placement decision has a played-card line. */
  def usePowerLines(power: PowerId)(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    usedPower(journal, run, at, power,
      journal.event(at).isInstanceOf[WalkerCompleted], viewer) ++
      playedAdviser(journal, run, at, viewer)
```

- [ ] **Step 4: Point the formatter at the entry's action line**

In `GameLogFormatter`, pass `actions` to the registry:
`new ProcedureLogs(starts, actions)`. In `walker`, replace
`actions.lines(journal, begun, at, viewer)` with
`log.lines(journal, begun, at, viewer)`.

`actions` is still read, by the registry's construction, so it stays a
field.

- [ ] **Step 5: Run the gamelog suites**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS, every suite, with no test file edited.

- [ ] **Step 6: Prove the golden logs did not move**

Run: `git diff --exit-code -- src/test/resources/gamelog`
Expected: exit 0, no output.

- [ ] **Step 7: Run the full gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: exit 0.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/ActionLines.scala src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala
git commit -m "$(cat <<'EOF'
refactor(gamelog): read each procedure's action line from the table

ActionLines' match over run.procedure becomes nineteen named rules, and the
registry entry names the one its procedure posts. The five procedures that
post no action line now say so in the table, once, which is where a reader
looks for it.

Golden logs are byte-identical.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Narration becomes entry data

`DetailLines.NarratedIds` and `NarratedPrefixes` are deleted. Each entry
declares its own narrated decision ids and prefixes, and `DetailLines` asks
the run's entry. The two cross-cutting predicates stay where they are.

This is the only task whose assignment is not mechanical: the global set did
not record which procedure asks which decision. Step 1 derives that from the
scripts before anything moves, and the table below is the starting point the
derivation corrects.

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala:29-34,53-56,204-236`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala`
- Test (temporary, deleted in Step 6):
  `src/test/scala/oathdigital/application/gamelog/NarrationDerivationSuite.scala`

**Interfaces:**
- Consumes: `ProcedureLog`, `ProcedureLogs` from Tasks 1 and 2.
- Produces:
  - `ProcedureLog` gains `narrated: Set[String] = Set.empty` and
    `narratedPrefixes: Vector[String] = Vector.empty`, and
    `def narrates(decisionId: String): Boolean`, which is
    `narrated(decisionId) || narratedPrefixes.exists(decisionId.startsWith)`
    and nothing else.
  - `DetailLines.crossCutting(decisionId: String): Boolean`, which is
    `ModifierPicks.isDecision(decisionId) ||
    RerollChoice.isDecision(decisionId)` and nothing else.
    `DetailLines.narrated` is gone.
  - `DetailLines.lines` gains a fifth parameter, `log: ProcedureLog`.

- [ ] **Step 1: Derive which procedure asks each narrated decision**

Write this temporary suite. It prints, for every scripted journal, each
decision id that today's global list suppresses, beside the procedure whose
run asked it. It asserts nothing.

```scala
package oathdigital.application.gamelog

import oathdigital.gameplay.walker.{ChoicePayload, WalkerCompleted,
  WalkerStepRecorded}
import oathdigital.model._
import LogScripts._

/** Temporary: prints the procedure that asks each decision today's global
  * narration list suppresses, so the registry's entries can be written from
  * the journals rather than from memory. Deleted in the same task. */
class NarrationDerivationSuite extends munit.FunSuite:
  test("print every (procedure, narrated decision) pair the scripts reach"):
    val pairs = LogScripts.all.flatMap { script =>
      val journal = new LogJournal(script.history.steps)
      (0 until journal.size).foldLeft(
          (Option.empty[ProcedureRef], Vector.empty[(String, String)])) {
        case ((current, found), at) =>
          val procedure = current.orElse(journal.procedureAt(at))
          val more = (journal.event(at), procedure) match
            case (WalkerStepRecorded(_, ChoicePayload(id, _, _), _, _),
                Some(ref)) if DetailLines.narrated(id) =>
              Vector(ref.key -> id)
            case _ => Vector.empty
          val next = journal.event(at) match
            case _: WalkerCompleted => None
            case _ => procedure
          (next, found ++ more)
      }._2
    }.distinct.sorted
    println(pairs.map((key, id) => s"[narration] $key asks $id")
      .mkString("\n"))
```

Run: `./sbtw "testOnly oathdigital.application.gamelog.NarrationDerivationSuite"`
Expected: PASS, with one `[narration]` line per pair. Keep the output: it is
the input to Step 2.

- [ ] **Step 2: Write the narration onto the entries**

Add the two members and `narrates` to `ProcedureLog`:

```scala
private[gamelog] final case class ProcedureLog(
    tag: Option[String],
    lines: ActionLine,
    anchor: Anchor = Anchor.AtFirstEvent,
    opening: Opening = Opening.none,
    continued: Continued = Continued.none,
    narrated: Set[String] = Set.empty,
    narratedPrefixes: Vector[String] = Vector.empty,
    isSection: Boolean = true):

  /** Whether another of this procedure's lines already tells the answer to
    * `decisionId`, so no "Chose …" line is posted for it. Two procedures
    * that ask the same decision each declare it: the compiler holds every
    * entry to stating its own narration, so nothing can drift. */
  def narrates(decisionId: String): Boolean =
    narrated(decisionId) || narratedPrefixes.exists(decisionId.startsWith)
```

Then add `narrated`/`narratedPrefixes` to the rows below. **This table is the
starting point, not the answer.** Every pair Step 1 printed must be narrated
by its procedure's entry after this step: add any pair the table lacks, and
do not remove a declaration because no script reached it — an unexercised
declaration is reported by Task 4, not an error.

| Row | `narrated` | `narratedPrefixes` |
|---|---|---|
| `Search` | `SearchProcedure.sourceDecisionId`, `SearchProcedure.cardDecisionId` | `ActionLines.PlacePrefix` |
| `PlayFacedownAdviser` | `CardPlayProcedure.facedownAdviserDecisionId` | `ActionLines.PlacePrefix`, `"cardplay.discard."` |
| `Recover` | `RecoverProcedure.choiceDecisionId`, `RecoverProcedure.relicDecisionId` | — |
| `Forge` | `ForgeProcedure.assignmentDecisionId` | — |
| `Travel` | — | `"travel."` |
| `Muster` | `MusterProcedure.decisionId` | — |
| `Trade` | `TradeProcedure.decisionId` | — |
| `Challenge` | `ChallengeProcedure.bannerDecisionId`, `ChallengeProcedure.amountDecisionId` | — |
| `PlaceBannerResource` | `PlaceBannerResourceProcedure.bannerDecisionId`, `PlaceBannerResourceProcedure.amountDecisionId` | — |
| `Campaign` | — | `"campaign."` |
| `Negotiation` | — | `"negotiation."` |
| `UsePower(_)` | — | `ActionLines.PlacePrefix`, `"cardplay.discard."` |
| `TriggeredProcedureRef.Setup` | — | `"setup."` |
| `TriggeredProcedureRef.Oathkeeper` | `OathkeeperProcedure.recipientDecisionId` | — |
| `TriggeredProcedureRef.FollowUpOffer` | `FollowUpOffer.decisionId` | — |
| `WakeMenu`, `RestMenu`, `ActMenu` | `PhaseMenu.decisionId` | — |

The three phase menus can no longer share one row with `ForcedWake`, which
narrates nothing: split them out. For example:

```scala
    // One decision id serves the Wake, Rest and Act menus, so all three
    // declare it. A pick starts its own walk, which tells it.
    case TriggeredProcedureRef.WakeMenu | TriggeredProcedureRef.RestMenu |
        TriggeredProcedureRef.ActMenu =>
      ProcedureLog(tag = None, lines = ActionLine.none,
        narrated = Set(PhaseMenu.decisionId), isSection = false)
    case TriggeredProcedureRef.ForcedWake =>
      ProcedureLog(tag = None, lines = ActionLine.none, isSection = false)
    // The queued action's tag, or the Skip line, tells the offer's answer.
    case TriggeredProcedureRef.FollowUpOffer =>
      ProcedureLog(tag = None, lines = ActionLine.none,
        narrated = Set(FollowUpOffer.decisionId), isSection = false)
```

Carry the doc comment that `DetailLines.NarratedIds` held (lines 204-211)
onto the rows it explains, one clause per row. It is the record of *why* each
decision is silent; it must not be deleted with the list.

`ProcedureLog.scala` gains these imports:

```scala
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.challenge.{ChallengeProcedure,
  PlaceBannerResourceProcedure}
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.walker.FollowUpOffer
```

- [ ] **Step 3: Make `DetailLines` ask the entry**

In `DetailLines.scala`:

1. Rename the constructor parameter `narrated` to `narratedByPowers`, so the
   power narration and the procedure's own are told apart at every use site:

```scala
private[gamelog] final class DetailLines(words: LogWords,
    choices: ChoiceWords, narratedByPowers: Set[String]):
  def lines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId], log: ProcedureLog): Vector[Posted] =
    decision(journal, run, at, viewer, log) ++ (if journal.covered(at) then
      Vector.empty else roll(journal, at) ++ deltas(journal, run, at, viewer))
```

2. `decision` takes the entry and asks it:

```scala
  private def decision(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId], log: ProcedureLog): Vector[Posted] =
    journal.event(at) match
      case WalkerStepRecorded(_, ChoicePayload(id, answer, by), _, _)
          if !log.narrates(id) && !DetailLines.crossCutting(id) &&
            !narratedByPowers(id) =>
```

   The rest of that arm is unchanged.

3. Delete `NarratedPrefixes` and `NarratedIds` with their doc comment (lines
   204-225, the comment having moved to the rows in Step 2), and replace
   `narrated` with:

```scala
  /** The decisions any procedure may ask, told by the lines of whatever
    * asked them: a modifier pick, which the start line names, and a reroll
    * choice, which the reroll's own lines tell. Both are computed from the
    * id by the layer that owns the decision, so neither can silently miss
    * one and neither keys on a procedure. */
  def crossCutting(decisionId: String): Boolean =
    ModifierPicks.isDecision(decisionId) || RerollChoice.isDecision(decisionId)
```

4. Drop these now-unused imports from `DetailLines.scala`:
   `ChallengeProcedure`, `PlaceBannerResourceProcedure`, `MusterProcedure`,
   `TradeProcedure`, `ForgeProcedure`, `RecoverProcedure`, `SearchProcedure`,
   `OathkeeperProcedure`, `PhaseMenu`, and `FollowUpOffer` from the walker
   import. `CampaignIds`, `CardPlayProcedure`, `PlayedAs`, `DiceThrow`,
   `RerollChoice`, `ModifierPicks`, `ChoicePayload`, `RollPayload` and
   `WalkerStepRecorded` all stay. `-Werror` reports any mistake here.

5. In `GameLogFormatter.walker`, pass the entry:
   `details.lines(journal, begun, at, viewer, log)`.

- [ ] **Step 4: Run the gamelog suites**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS. A failure here names the script and the line: a stray
"Chose …" means a pair Step 1 printed is missing from the table; a missing
line means a declaration is on the wrong row.

- [ ] **Step 5: Prove the golden logs did not move**

Run: `git diff --exit-code -- src/test/resources/gamelog`
Expected: exit 0, no output. This is the real gate on the assignment: a
narration on the wrong row shows up here as a line gained or lost.

- [ ] **Step 6: Delete the derivation suite**

Run: `git rm src/test/scala/oathdigital/application/gamelog/NarrationDerivationSuite.scala`

It referenced `DetailLines.narrated`, which no longer exists; it has served
its purpose and Task 4 replaces it with a standing property.

- [ ] **Step 7: Run the full gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: exit 0.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala src/main/scala/oathdigital/application/gamelog/DetailLines.scala src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala
git commit -m "$(cat <<'EOF'
refactor(gamelog): each procedure declares the decisions its lines tell

The global NarratedIds set and its prefix list are gone. Each registry entry
declares the decisions of its own procedure whose answers another of its lines
already tells, so a new procedure that forgets to declare one fails to
compile instead of posting a stray "Chose ..." line. Two procedures that ask
the same decision each declare it, and the compiler holds them to it.

The two cross-cutting predicates stay the two calls DetailLines already made:
they match under any procedure with a modifier window or a roll, they are
computed rather than enumerated, and they are owned by the layer that owns the
decisions. Nothing in the narration path is hand-maintained now.

Golden logs are byte-identical.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: The narration-coverage property

One standing property replaces the derivation suite: across the scripted
journals, every decision a run asks is accounted for. Its value is the
diagnostic — it names the procedure and the decision id, where a golden diff
names a line in a log — and the report of declarations no script exercises.

**Files:**
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
  (141 lines, grows by about 55; the 800-line cap is far off)

**Interfaces:**
- Consumes: `GameLogFormatter.procedures`, `ProcedureLog.narrates`,
  `ProcedureLog.narrated`, `DetailLines.crossCutting`.
- Produces: nothing.

- [ ] **Step 1: Write the property**

Append to `GameLogPropertiesSuite`, beside its sibling "every `ProcedureRef`
key the formatter handles appears in some script". Add
`import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog`,
`import oathdigital.gameplay.walker.{ChoicePayload, WalkerStepRecorded}` to
the existing walker import, and
`import oathdigital.model.DecisionAnswer.{ChooseManyAnswer, ChooseOneAnswer}`.

```scala
  /** Every decision a script asks, with the procedure whose run asked it and
    * the sequence of the step that answered it. A run begins at the first
    * walker event after the previous completion, as the formatter reads it.
    * A step voided by an undo still belongs to its procedure, so taking it
    * in only widens the check. Answers that name no option post no line
    * either way, so they are left out. */
  private def asked(script: Script): Vector[(ProcedureRef, String, Long)] =
    val journal = new LogJournal(script.history.steps)
    (0 until journal.size).foldLeft((Option.empty[ProcedureRef],
        Vector.empty[(ProcedureRef, String, Long)])) {
      case ((current, found), at) =>
        val procedure = current.orElse(journal.procedureAt(at))
        val more = (journal.event(at), procedure) match
          case (WalkerStepRecorded(_, ChoicePayload(id, answer, _), _, _),
              Some(ref)) if namesAnOption(answer) =>
            Vector((ref, id, journal.sequence(at)))
          case _ => Vector.empty
        val next = journal.event(at) match
          case _: WalkerCompleted => None
          case _ => procedure
        (next, found ++ more)
    }._2

  /** Whether an answer names an option, as `DetailLines.decision` reads it:
    * Done and Continue are not choices, and an empty choose-many is not one
    * either. */
  private def namesAnOption(answer: DecisionAnswer): Boolean = answer match
    case ChooseOneAnswer(DecisionQuery.Inspect.Done) => false
    case ChooseOneAnswer(Decide.Pass) => false
    case ChooseOneAnswer(_) => true
    case ChooseManyAnswer(selected) => selected.nonEmpty
    case _ => false

  /** Nothing a run asks goes untold. A decision either posts its own
    * "Chose ..." line, or something else tells its answer: the procedure's
    * own entry, one of the two cross-cutting predicates, or a power's note.
    * A new procedure whose entry forgets a decision fails here by name,
    * before any golden log has to be read. */
  test("every decision a script asks is told by some line"):
    val byPowers = NoteWordings.default(catalog).narrated
    val unaccounted = scripts.flatMap { script =>
      val decided = formatter.format(script.history.steps, None)
        .filter(_.kind == LogKind.Decision).map(_.sequence).toSet
      asked(script).collect {
        case (procedure, id, sequence) if !decided(sequence) &&
            !formatter.procedures.of(procedure).narrates(id) &&
            !DetailLines.crossCutting(id) && !byPowers(id) =>
          s"${script.name}: ${procedure.key} asks $id, and no line tells it"
      }
    }.distinct
    assertEquals(unaccounted, Vector.empty[String])

  /** A declaration no script exercises is reported, not asserted: an exact
    * set would make every new script a two-file edit, which is the cost the
    * registry exists to cut. */
  test("declared narrations no script exercises are reported"):
    val exercised = scripts.flatMap(asked).map((procedure, id, _) =>
      procedure -> id).toSet
    val idle = ProcedureRef.all.flatMap(procedure =>
      formatter.procedures.of(procedure).narrated.toVector.sorted
        .filterNot(id => exercised((procedure, id)))
        .map(id => s"[narration] ${procedure.key} declares $id, " +
          "and no script asks it"))
    if idle.nonEmpty then println(idle.mkString("\n"))
```

- [ ] **Step 2: Run it to see it pass, and read the report**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogPropertiesSuite"`
Expected: PASS. Read the `[narration]` lines: each is a declaration no script
reaches. Do **not** delete a declaration to silence one — the list is a note
about script coverage, not about the table. If a line names a decision that
no longer exists anywhere in the tree (`grep -rn` it under `src/main/scala`),
that *is* a dead declaration: delete it and say so in the commit body.

- [ ] **Step 3: Verify it fails when a narration is missing**

Temporarily remove `MusterProcedure.decisionId` from the Muster row of
`ProcedureLog.rules`.

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogPropertiesSuite"`
Expected: FAIL, naming `muster asks muster.source` (or the decision's actual
id). Restore the declaration and re-run; expected PASS. A property that
cannot fail is not a test.

- [ ] **Step 4: Run the full gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: exit 0.

- [ ] **Step 5: Commit**

```bash
git add src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala
git commit -m "$(cat <<'EOF'
test(gamelog): every decision a script asks is told by some line

A standing property over the scripted journals: a decision either posts its
own line, or its procedure's entry, a cross-cutting predicate or a power's
note tells its answer. A procedure that forgets a narration now fails by
name, instead of as a diff in a golden log. Declarations no script exercises
are reported rather than asserted, so a new script stays a one-file edit.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: One named read of a Campaign's kind and defender

`StartLines.campaign` composes `CampaignSetup.kindOf` and
`CampaignSetup.defenderOf` by hand, and it is the third place to do so.
`CampaignSetup.declaredAt` becomes the one name for that read, and its three
callers use it. The journal fallback stays in the log: it reads a recorded
operation, and `gameplay` may not see `LogJournal`.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala:137-168,196-203`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala:193-200`
- Modify: `src/main/scala/oathdigital/application/gamelog/StartLines.scala`
- Test: `src/test/scala/oathdigital/gameplay/CampaignSetupSuite.scala`
  (76 lines)

**Interfaces:**
- Consumes: `CampaignSetup.kindOf`, `CampaignSetup.defenderOf`, unchanged.
- Produces: `CampaignSetup.declaredAt(ready: ReadyGame, actor: PlayerId,
  pending: PendingTree): Option[(CampaignKind, CampaignDefender)]`.

- [ ] **Step 1: Write the failing test**

Append to `CampaignSetupSuite`. The fixture `board` and the local `pending`
helper are already there.

```scala
  test("the declared pair is the kind and defender read together"):
    // One legal kind and one defender, so both settle with no answer; the
    // pair is what the two reads give separately.
    val b = board(warbands = 5, supply = 7)
    val empty = pending()
    val kind = CampaignSetup.kindOf(b.ready, b.actor, empty)
    assertEquals(CampaignSetup.declaredAt(b.ready, b.actor, empty),
      kind.flatMap(only =>
        CampaignSetup.defenderOf(b.ready, b.actor, empty, only)
          .map(only -> _)))
    assert(CampaignSetup.declaredAt(b.ready, b.actor, empty).nonEmpty)

  test("no pair is declared once the attacker skipped"):
    val b = board(warbands = 5, supply = 7)
    val skipped = pending(CampaignIds.kind ->
      ChooseOneAnswer(CampaignIds.skip))
    assertEquals(CampaignSetup.declaredAt(b.ready, b.actor, skipped), None)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignSetupSuite"`
Expected: FAIL with "value declaredAt is not a member of object
CampaignSetup".

- [ ] **Step 3: Add `declaredAt` and read it from its three callers**

In `CampaignSetup.scala`, after `defenderOf`:

```scala
  /** This Campaign's kind and defender at one point of its run: the pair
    * pinned when the pools were gathered, else the pair the answers so far
    * and the board settle. `None` until both are settled, and once the
    * attacker skipped. One name for a read that three callers used to
    * compose by hand.
    */
  def declaredAt(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Option[(CampaignKind, CampaignDefender)] = for
    kind <- kindOf(ready, actor, pending)
    defender <- defenderOf(ready, actor, pending, kind)
  yield (kind, defender)
```

Then read it from `setup`, in the same file. Every step of the comprehension
is a pure read of the same inputs, so moving `origin` after the pair changes
nothing:

```scala
  /** The complete setup, once the force is answered. */
  def setup(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Option[CampaignSetup] = for
    (kind, defender) <- declaredAt(ready, actor, pending)
    origin <- originOf(ready, actor)
    force <- CampaignAnswers.force(pending)
  yield
```

The `yield` block below is unchanged.

In `CampaignProcedure.targetsStep`, replace the first two steps of its
comprehension:

```scala
    (for
      (kind, defender) <- CampaignSetup.declaredAt(ready, actor, pending)
      origin <- CampaignSetup.originOf(ready, actor)
      options = CampaignSetup.targetOptions(ready, actor, kind, defender)
      if options.nonEmpty
    yield
```

In `StartLines.campaign`, replace the two reads with the one:

```scala
  /** Kind and defender from the answers so far and the state after `at`,
    * read exactly as the procedure reads them, through the one function that
    * owns that read; failing that, from the recorded result, which is a
    * journal read and so stays here. */
  private def campaign(journal: LogJournal, run: Run, at: Int)
      : Option[Vector[LogSpan]] =
    val pending = PendingTree(Vector.empty, journal.answers(run, at))
    journal.readyAfter(at).flatMap(ready =>
      CampaignSetup.declaredAt(ready, run.actor, pending))
      .map(campaignLine(_, _))
      .orElse(journal.ops(at).collectFirst {
        case OpStep(RecordCampaignResult(result), _, _) =>
          campaignLine(result.kind, result.defender)
      })
```

If `.map(campaignLine(_, _))` on an `Option[(CampaignKind,
CampaignDefender)]` does not infer, write it as
`.map((kind, defender) => campaignLine(kind, defender))`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignSetupSuite oathdigital.gameplay.Campaign* oathdigital.application.gamelog.*"`
Expected: PASS.

- [ ] **Step 5: Prove the golden logs did not move**

Run: `git diff --exit-code -- src/test/resources/gamelog`
Expected: exit 0, no output.

- [ ] **Step 6: Run the full gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: exit 0.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala src/main/scala/oathdigital/application/gamelog/StartLines.scala src/test/scala/oathdigital/gameplay/CampaignSetupSuite.scala
git commit -m "$(cat <<'EOF'
refactor(campaign): read a Campaign's declared kind and defender by name

CampaignSetup.declaredAt composes kindOf and defenderOf once, and its three
callers stop composing them: the procedure's target step, the full setup, and
the Game Log's tag line. The log keeps the fallback that reads the recorded
result, because that is a journal read and gameplay cannot see the journal.

Nothing is journaled and nothing is validated. Golden logs are
byte-identical.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## What this plan does not do

Named here so a reviewer does not look for them, each with the spec's reason:

- **A procedure journaling its own notes.** The depth change, and a candidate
  of its own: it needs a sourceless note variant, a wire case for it, and a
  migration across about fifteen `gameplay` procedure files. Nothing here
  prepares for it or blocks it.
- **A typed decision-id vocabulary** (candidate H). This plan invents no ids
  and is not blocked on it. Task 4's property catches a missing narration
  whether or not ids are ever typed.
- **The formatter's second pass over the journal** (candidate I) and
  **`DeltaMeaning`** (candidate G). Untouched.
- **A registry totality test.** `ProcedureRef.all` builds the map and `rules`
  is exhaustive under `-Werror`, so a coverage test would assert what the
  compiler already guarantees. This is the one place this plan deliberately
  departs from `WalkerProcedureRegistrySuite`'s posture, and the reason is in
  the spec's first ruling.
