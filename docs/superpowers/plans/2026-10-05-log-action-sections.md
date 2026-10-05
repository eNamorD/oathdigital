# Game Log Action Sections Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A reader of the Game Log can tell where each action starts and ends, which action it was, and what its dice rolled: every top-level action sits on a colored band opened by a tag ("SEARCH −2 Supply"), attack and defense dice are red and blue chips, and the hollow sword is an outlined sword.

**Architecture:** The formatter already formats every walker event inside a `Run` that knows its procedure, so the run stamps a `LogSection(id, action)` on every entry it posts, and the start line becomes a `section` entry (the tag) plus, when there is more to say, one line. The section rides the wire as `LogEntryWire.section`. `GameLogPane` keeps its one flat list and turns sections into band classes and edge classes; CSS draws the bands from one custom property per action. `DieFace` takes the die kind, and both callers pass it.

**Tech Stack:** Scala 3, Scala.js with jsdom render suites, ujson with hand-written `exact` codecs, munit, plain CSS. Build through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-10-05-log-action-sections-design.md`. Read it whole before Task 1. The decisions below settle what it left to the plan and the browser check.

## Global Constraints

- Never touch the live database `var/oathdigital`. The browser check uses a fresh scratch database on port 8081, started through Bash, never through `preview_start`.
- Work in a git worktree: from the main checkout run `git worktree add -b log-action-sections .claude/worktrees/log-action-sections main`, then `ln -s /Users/roman/projects/oathdigital/.tooling .claude/worktrees/log-action-sections/.tooling`, then `EnterWorktree`. Inside the worktree run `git merge --ff-only main` first. Before merging from the main checkout, `ExitWorktree(keep)`.
- Inside the worktree keep Bash plain: no `cd` chained with git, no heredocs, no `git -C`. Write any helper script into the session scratchpad and run it with `python3`. Use `sed -i ''` on macOS.
- Other sessions commit to `main` while you work, and HEAD can move. Check `git log -1` before any amend or merge. Stage explicit paths only; never `git add -A`. Never commit `node_modules`, `.tooling` or `target`.
- Commit trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` unless the committing model is another one, in which case use that model's own line.
- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn` on both projects. An unused import or private member fails the build, and so does a non-exhaustive match.
- Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). `shared/src/main` may import only `oathdigital.protocol`.
- Codec rule: a new wire field is written always and decoded with a default, so a payload from before this plan still decodes; every `exact` field set gains the new name.
- Golden rule: `GAMELOG_GOLDEN=write` rewrites `src/test/resources/gamelog/*.log`. Run it through `env GAMELOG_GOLDEN=write ./sbtw ...`, then read every changed golden with `git diff` and check each change is one this task meant. Never rewrite goldens to silence a failure you have not explained.
- Copy rule: player text is plain sentences, no em dashes, numbers as digits. The cost span's "−" is the existing `LogSpan.Cost` text and stays.
- No animation, no shadow and no size change on hover anywhere in this plan (DESIGN.md, No-Reflow and Flat Board).
- Gates, in order, before the final commit of each task: the task's own `testOnly` runs, then `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`. The last task also runs `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.

### Decisions this plan makes that the spec left open or worded differently

1. **Campaign's band color is `#d47c74`, not `#c9706a`.** The spec's value gives its tag 4.21:1 on its own band, under the 4.5:1 the spec requires. `#d47c74` gives 4.80:1 and stays in the red family. The other eight colors clear 4.5:1 as given (lowest: Recover 4.91:1).
2. **A spent cost inside a band is Ink Dim (`#a99f8c`), not Ink Spent.** Ink Spent measures 4.00 to 4.25:1 on the band fills, under 4.5:1. Ink Dim measures 5.34:1 or more on every band. The tag row's cost follows the same rule.
3. **The client folds two actions onto one band class.** `log-band-<action>` is `log-band-search` for both `search` and `play-facedown-adviser`, and `log-band-use-power` for every `use-power:<id>`. The CSS then holds one rule per color, and a jsdom test can see the facedown adviser take Search's band.
4. **A Campaign's modifiers join its kind line.** The line after a Campaign tag reads "Raid against Blue with Fox"; any other action's reads "With Fox". Either way the tag is followed by at most one line.
5. **A minor action in the middle of an action splits its band.** A minor action is its own section, so the action's lines after it start a second band piece with no tag. Nothing in this plan joins the two pieces.
6. **Goldens show the section.** `GoldenLog.render` writes `sequence.ordinal kind depth action@id | spans`, with `action@id` left out for an entry with no section.
7. **The browser check starts from an empty scratch database.** No copy of `var/oathdigital` is made.

### File map

- Create `src/main/scala/oathdigital/application/gamelog/LogSections.scala`: which runs are sections, and a run's section.
- Modify `src/main/scala/oathdigital/application/gamelog/LogEntry.scala`: `LogSection`, `LogEntry.section`, `LogKind.Section`, `Posted.section`.
- Modify `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala`: stamp sections; minor actions; the tag's `Vector`.
- Modify `src/main/scala/oathdigital/application/gamelog/StartLines.scala`: tag plus one line, in place of the start line.
- Modify `src/main/scala/oathdigital/application/gamelog/GameLogProjector.scala`: wire the section.
- Modify `shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala` and `LogPageCodec.scala`: `LogSectionWire`.
- Modify `frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala`: band and edge classes; dice kind.
- Modify `frontend/src/main/scala/oathdigital/frontend/DieFace.scala`, `WalkerPanelSupport.scala`, `TokenSprite.scala`.
- Modify `frontend/styles.css`: bands, tag row, filled dice.
- Modify `DESIGN.md`, `docs/ROADMAP.md`.
- Tests: create `src/test/scala/oathdigital/application/gamelog/GameLogSectionSuite.scala`; modify `GoldenLog.scala`, `GameLogPropertiesSuite.scala`, `GameLogStartLineSuite.scala`, `GameLogActionLineSuite.scala`, `GameLogCampaignSuite.scala`, `GameLogDetailSuite.scala`, `GameLogPowerLinesSuite.scala`, `GameLogRouteSuite.scala`, the goldens, `shared/src/test/scala/oathdigital/protocol/LogPageCodecSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala`, `DieFaceSuite.scala`, `WalkerDecisionDraftSuite.scala`, `WalkerChoicePanelRenderSuite.scala`.

---

### Task 1: Sections on log entries

**Files:**
- Create: `src/main/scala/oathdigital/application/gamelog/LogSections.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/LogEntry.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala:38-48` (`format`), `:89-95` (minor actions), `:104-126` (`walker`)
- Modify: `src/test/scala/oathdigital/application/gamelog/GoldenLog.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Create: `src/test/scala/oathdigital/application/gamelog/GameLogSectionSuite.scala`
- Modify: every `src/test/resources/gamelog/*.log` (rewritten)

**Interfaces:**
- Consumes: `Run(procedure, actor, first, started)` and `LogJournal.sequence(index): Long` from `LogJournal.scala`.
- Produces: `final case class LogSection(id: Long, action: String)` (public, in package `oathdigital.application.gamelog`); `LogEntry(sequence, ordinal, kind, depth, spans, section: Option[LogSection] = None)`; `Posted(kind, depth, spans, section: Option[LogSection] = None)`; `LogSections.Minor: String = "minor"`; `LogSections.of(procedure: ProcedureRef): Boolean`; `LogSections.forRun(journal: LogJournal, run: Run): Option[LogSection]`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/application/gamelog/GameLogSectionSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.model._
import oathdigital.testkit.CatalogNames
import LogScripts._

/** Which entries share a band (log action sections design, "Server"): every
  * line of one top-level action carries that action's section; headlines,
  * Wake's Take Wealth, phase transitions, triggered procedures and
  * state-based changes carry none; a minor action is a section of its own. */
class GameLogSectionSuite extends munit.FunSuite:
  private def withAction(entries: Vector[LogEntry], action: String)
      : Vector[LogEntry] =
    entries.filter(_.section.exists(_.action == action))

  test("the lines of one action share one section, keyed by its first event"):
    val travel = withAction(format(round, None), "travel")
    // The tag (or start line) and "Travelled to".
    assertEquals(travel.size, 2, travel)
    assertEquals(travel.map(_.section).distinct.size, 1, travel)
    // Travel's opening line posts at its run's first event.
    assertEquals(travel.head.section.map(_.id), Some(travel.head.sequence))

  test("headlines, a quiet Wake and Rest's supply carry no section"):
    val entries = format(round, None)
    assert(entries.filter(_.depth == 0).forall(_.section.isEmpty), entries)
    assertEquals(entryStarting(entries, "Increased supply from ").section,
      None)
    assertEquals(entryStarting(entries, "Nothing happened in Wake").section,
      None)

  test("Wake's Take Wealth, the Oathkeeper and Setup carry no section"):
    assertEquals(entryStarting(format(takeWealth, None), "Took 1 favor from ")
      .section, None)
    assertEquals(entryStarting(format(oathkeeperTie, None),
      "Oathkeeper passed to ").section, None)
    val placed = format(woken, None).filter(entry =>
      text(entry).contains(" placed pawn at "))
    assert(placed.nonEmpty)
    assert(placed.forall(_.section.isEmpty), placed)

  test("a minor action is a section of its own, apart from the action " +
      "around it"):
    val entries = format(revealRelic, None)
    val revealed = entries.last
    assertEquals(revealed.section,
      Some(LogSection(revealed.sequence, LogSections.Minor)))
    val recovered = entryStarting(entries, "Recovered ")
    assertEquals(recovered.section.map(_.action), Some("recover"))

  test("moved warbands are a minor section; refilled bandits and a new " +
      "Usurper are in none"):
    val script = recordedEventsBoard
    val site = CatalogNames.site("Ancient City")
    val moved = formatAppended(script, Vector(OathEvent.WarbandsMoved(
      script.actor, site, toSite = true, amount = 2, priorBoardWarbands = 3,
      priorSiteWarbands = 1))).last
    assertEquals(moved.section, Some(LogSection(
      script.history.steps.size.toLong, LogSections.Minor)))
    assertEquals(formatAppended(script, Vector(OathEvent.BanditsRefilled(
      Vector(site -> 1)))).last.section, None)
    assertEquals(formatAppended(script, Vector(OathEvent.UsurperFlipped(
      script.otherSeat))).last.section, None)

  test("a power used as an action is one section named by its power"):
    val lines = format(silverTongue, None).filter(_.depth == 1)
    assert(lines.nonEmpty)
    assert(lines.forall(_.section.exists(_.action.startsWith("use-power:"))),
      lines)
    assertEquals(lines.map(_.section).distinct.size, 1, lines)

  test("playing a facedown adviser is its own section"):
    val lines = format(facedownAdviser, None).filter(_.depth == 1)
    assert(lines.nonEmpty)
    assert(lines.forall(_.section.exists(_.action == "play-facedown-adviser")),
      lines)
```

In `GameLogPropertiesSuite.scala`, change the shape in "every viewer receives the same entries with the same keys" to include the section:

```scala
        .map(entry => (entry.sequence, entry.ordinal, entry.kind, entry.depth,
          entry.section)))
```

and add, after that test:

```scala
  test("no headline carries a section"):
    scripts.foreach { script =>
      val headlines = formatter.format(script.history.steps, None)
        .filter(_.depth == 0)
      assert(headlines.forall(_.section.isEmpty), script.name)
    }
```

In `GoldenLog.scala`, write the section after the depth:

```scala
  def render(entries: Vector[LogEntry]): String =
    entries.map(entry => s"${entry.sequence}.${entry.ordinal} " +
      s"${entry.kind.key} ${entry.depth}" +
      entry.section.fold("")(section => s" ${section.action}@${section.id}") +
      " | " + entry.spans.map(span).mkString)
      .mkString("", "\n", "\n")
```

and add to its doc comment, after "Nothing a `LogEntry` holds is lost.": "An entry in a section shows it as `action@id` after its depth."

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogSectionSuite"`
Expected: compilation fails: `section` is not a member of `LogEntry`; `LogSection` and `LogSections` are not found.

- [ ] **Step 3: Add the section to the entry model**

In `LogEntry.scala`, replace the `LogEntry` case class with:

```scala
final case class LogEntry(sequence: Long, ordinal: Int, kind: LogKind,
    depth: Int, spans: Vector[LogSpan], section: Option[LogSection] = None)

/** The action an entry belongs to (log action sections design, "Server"):
  * `id` is the journal index of the action's first event, the same for
  * every entry of one action, and `action` its procedure key, or `minor`
  * for a minor action. A client draws one band per section. */
final case class LogSection(id: Long, action: String)
```

Extend the `LogEntry` doc comment's last sentence to: "Depth 0 is a headline, depth 1 a line; only a line can carry a section."

Replace `Posted` with:

```scala
/** An entry before it has a key: what one event posts, in order. */
private[gamelog] final case class Posted(kind: LogKind, depth: Int,
    spans: Vector[LogSpan], section: Option[LogSection] = None)
```

Create `LogSections.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.model._

/** Which runs are sections (log action sections design, "Where a section
  * comes from"): every top-level action is one. Wake's Take Wealth, phase
  * transitions and triggered procedures are not actions a reader looks for,
  * so their lines stand outside any band. */
private[gamelog] object LogSections:
  /** A minor action is not a walker run, so it has no procedure key. */
  val Minor = "minor"

  def of(procedure: ProcedureRef): Boolean = procedure match
    case ActionRef.TakeWealth => false
    case _: ActionRef => true
    case _: PhaseTransitionRef | _: TriggeredProcedureRef => false

  def forRun(journal: LogJournal, run: Run): Option[LogSection] =
    Option.when(of(run.procedure))(
      LogSection(journal.sequence(run.first), run.procedure.key))
```

- [ ] **Step 4: Stamp the sections in the formatter**

In `GameLogFormatter.format`, pass the section on:

```scala
        (next, entries ++ posted.zipWithIndex.map { case (entry, ordinal) =>
          LogEntry(journal.sequence(at), ordinal, entry.kind, entry.depth,
            entry.spans, entry.section) })
```

In `eventLines`, replace the case that starts with the comment "// Minor actions and state-based changes, outside any walker run." with two cases:

```scala
      // A minor action, outside any walker run, is a section of its own.
      case _: OathEvent.SiteRelicsPeeked | _: OathEvent.OwnedRelicRevealed |
          _: OathEvent.WarbandsMoved =>
        val section = LogSection(journal.sequence(at), LogSections.Minor)
        (events.lines(journal, at, viewer).map(_.copy(section = Some(section))),
          run)
      // State-based changes, outside any section.
      case _: OathEvent.BanditsRefilled | _: OathEvent.UsurperFlipped =>
        (events.lines(journal, at, viewer), run)
```

In `walker`, stamp every line of the run, but not its headlines. Replace the `val posted = ...` expression with:

```scala
        val section = LogSections.forRun(journal, begun)
        val lines = start.toVector ++
          (if start.isEmpty then Vector.empty
            else notes.held(journal, begun, at, viewer)) ++
          starts.continued(journal, begun, at).toVector ++
          actions.lines(journal, begun, at, viewer) ++
          details.lines(journal, begun, at, viewer) ++
          notes.own(journal, begun, at, starts.opens(begun.procedure), viewer)
        // A headline the run posts (a turn or phase change) is in no section.
        val posted = lines.map(_.copy(section = section)) ++
          headlines(journal, at)
```

- [ ] **Step 5: Run the new tests**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogSectionSuite oathdigital.application.gamelog.GameLogPropertiesSuite"`
Expected: PASS.

- [ ] **Step 6: Rewrite and review the goldens**

Run: `env GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`, then `git diff --stat src/test/resources/gamelog` and `git diff src/test/resources/gamelog/raid.actor.log src/test/resources/gamelog/reveal-relic.actor.log src/test/resources/gamelog/round.actor.log`.
Expected: every golden changes, and every changed line only gains ` action@id` after its depth. In `round.actor.log`, `2.0` and `3.0` gain ` travel@2`, and the headlines, "Nothing happened in Wake" and "Increased supply" gain nothing. In `reveal-relic.actor.log`, `7.0` gains ` minor@7`. No line's spans, kind or order change. Then run `./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`; expected PASS.

- [ ] **Step 7: Run the gates**

Run: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/LogSections.scala src/main/scala/oathdigital/application/gamelog/LogEntry.scala src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala src/test/scala/oathdigital/application/gamelog/GameLogSectionSuite.scala src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala src/test/scala/oathdigital/application/gamelog/GoldenLog.scala src/test/resources/gamelog
git commit -m "feat(log): every line of a top-level action carries its section"
```

(End the message with the trailer from Global Constraints.)

---

### Task 2: The tag replaces the start line

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/LogEntry.scala` (`LogKind`)
- Modify: `src/main/scala/oathdigital/application/gamelog/StartLines.scala` (whole file below)
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala` (`walker`)
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala`, `GameLogPropertiesSuite.scala`, `GameLogActionLineSuite.scala`, `GameLogCampaignSuite.scala`, `GameLogDetailSuite.scala`, `GameLogPowerLinesSuite.scala`
- Modify: the goldens (rewritten)

**Interfaces:**
- Consumes: Task 1's `LogSection`, `LogEntry.section`, `Posted.section`.
- Produces: `LogKind.Section` with wire key `"section"`. `StartLines.start(journal, run, at, viewer): Vector[Posted]` (was `Option[Posted]`): the tag, then at most one `Action` line. A tag's spans are `Text(name)` then, when Supply was spent, `LogSpan.Cost(spent, "Supply")`. Names: Search, Play facedown adviser, Recover, Forge, Travel, Muster, Trade, Challenge, Campaign.

- [ ] **Step 1: Write the failing tests**

In `GameLogStartLineSuite.scala`, replace the first test ("Travel opens with its start line and cost, then says where it went") with:

```scala
  test("Travel opens with its tag and cost, then says where it went"):
    val script = round
    val entries = format(script, Some(script.actor))
    val tag = entries.find(_.kind == LogKind.Section).getOrElse(
      fail(s"no tag in ${texts(entries)}"))
    assertEquals(tag.spans.head, LogSpan.Text("Travel"))
    // A tag with no modifiers has no line of its own: the action's line
    // follows it.
    val travelled = entries(entries.indexOf(tag) + 1)
    assert(text(travelled).startsWith("Travelled to "), text(travelled))
    assertEquals(travelled.section, tag.section)
    val cost = tag.spans.collect { case cost: LogSpan.Cost => cost }
    assertEquals(cost.size, 1)
    val spentAt = tag.sequence.toInt
    def supplyAfter(index: Int) =
      Look(script.history.steps(index).after).supply(script.actor)
    assertEquals(cost.head.value,
      supplyAfter(spentAt - 1) - supplyAfter(spentAt))
    assertEquals(cost.head.unit, "Supply")
    val destination = travelled.spans.collect { case site: LogSpan.Site => site }
    assertEquals(destination.size, 1)
```

Replace the last two tests ("a start line without modifiers has no 'with' clause" and "a chosen modifier is named on the start line by its card") with:

```scala
  test("a chosen modifier follows the tag as a line of its own"):
    val entries = format(augury, None)
    val tag = entryStarting(entries, "Search")
    assertEquals(tag.kind, LogKind.Section)
    // Search's printed cost; Augury, a free modifier, adds nothing.
    assertEquals(text(tag), "Search −2 Supply")
    val chosen = entries(entries.indexOf(tag) + 1)
    assertEquals(text(chosen), "With Augury")
    assertEquals(chosen.kind, LogKind.Action)
    assertEquals(chosen.section, tag.section)

  test("a Campaign's kind and defender follow its tag"):
    val entries = format(raid, None)
    val tag = entryStarting(entries, "Campaign")
    assertEquals(tag.kind, LogKind.Section)
    assertEquals(text(tag), "Campaign −2 Supply")
    // The raid script's p1 raids p2.
    assertEquals(text(entries(entries.indexOf(tag) + 1)),
      s"Raid against ${playerLabel(p2)}")

  test("playing a facedown adviser costs nothing, so its tag is its name"):
    val tag = entryStarting(format(facedownAdviser, None), "Play facedown")
    assertEquals(tag.kind, LogKind.Section)
    assertEquals(tag.spans, Vector[LogSpan](LogSpan.Text("Play facedown adviser")))
```

The suite already imports `p2` from `oathdigital.testkit.Table`.

In `GameLogPropertiesSuite.scala`, add after "no headline carries a section":

```scala
  private val tagged = Set("search", "play-facedown-adviser", "recover",
    "forge", "travel", "muster", "trade", "challenge", "campaign")

  test("an action with a tag opens with it, once; no other action has one"):
    scripts.foreach { script =>
      val entries = formatter.format(script.history.steps, None)
      entries.flatMap(_.section).distinct.foreach { section =>
        val own = entries.filter(_.section.contains(section))
        val tags = own.filter(_.kind == LogKind.Section)
        if tagged(section.action) then
          // A run that opens with no tag yet (a Muster still choosing its
          // source) has none; a run that has one shows it first.
          assert(tags.size <= 1, s"${script.name}: $tags")
          tags.headOption.foreach(tag => assertEquals(own.head, tag,
            s"${script.name}: a line came before the ${section.action} tag"))
        else assertEquals(tags, Vector.empty, script.name)
      }
    }

  test("no line still reads as a start line"):
    scripts.foreach { script =>
      val all = texts(formatter.format(script.history.steps, None))
      assert(!all.exists(line => line.startsWith("Started ") ||
        line.startsWith("Playing Facedown") ||
        line.startsWith("Continued Recover")), s"${script.name}: $all")
    }
```

Update the tests that read the old start line:

`GameLogActionLineSuite.scala`:
- "Search: ...": `assert(mine.exists(_.startsWith("Search −")), mine)`.
- "Muster: the start line waits ...": rename to "Muster: the tag waits for the cost, then names the card"; `val start = mine.indexOf("Muster −1 Supply")`; delete the `assert(mine(start).endsWith("−1 Supply"), mine(start))` line and its comment, since `indexOf` already checks the cost.
- "Muster under Initiation Rite: ...": rename to "Muster under Initiation Rite: the tag still waits for the cost, which places a secret, and shows only the Supply"; same two edits.
- "Trade: ...": rename to "Trade: the tag waits for the cost, then says what it bought"; `assert(mine.exists(_.startsWith("Trade −")), mine)`.
- "Take Wealth: one line naming the site, and no start line": rename to "Take Wealth: one line naming the site, and no tag"; replace `assert(!mine.exists(_.startsWith("Started")), mine)` with `assert(!format(script, None).exists(_.kind == LogKind.Section))`.
- "a failed Recover: ...": rename to "a failed Recover: tag, one continued spend, then the failure"; `assert(mine.contains("Recover −1 Supply"), mine)` in place of the `startsWith("Started Recover")` assert, and `assertEquals(mine.count(_ == "Continued −1 Supply"), 1, mine)`.
- "Forge: ...": `val start = mine.lastIndexWhere(_.startsWith("Forge −"))`.
- "Campaign: a start line naming kind and defender, and the winner": rename to "Campaign: a tag, a line naming kind and defender, and the winner"; then:

```scala
    val all = lines(raid, None)
    val start = all.indexWhere(_.startsWith("Campaign −"))
    val wins = all.indexWhere(line => line.endsWith(" wins!") ||
      line == "The bandits win!")
    assert(start >= 0 && wins > start, all)
    assert("^(Raid|Conquest) against .+$".r.matches(all(start + 1)),
      all(start + 1))
```

- "Challenge takes the banner ...": `assert(all.exists(_.startsWith("Challenge")), all)`.

`GameLogCampaignSuite.scala`, the Raid test's `expected`: replace its first element with the two elements `"Campaign −"` and `s"Raid against ${playerLabel(defender)}"`.

`GameLogDetailSuite.scala`, "a Recover roll posts after its start line": rename to "a Recover roll posts after its tag", and replace `"Started Recover"` with `"Recover −"`.

`GameLogPowerLinesSuite.scala`, the test that inserts a note at the Campaign's park (around line 293): replace `all.indexWhere(_.startsWith("Started Campaign"))` with `all.indexWhere(_.startsWith("Raid against "))`. A held note posts right after the tag's line, so `start + 1` stays.

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: compilation fails: `Section` is not a member of `LogKind`.

- [ ] **Step 3: Add the kind**

In `LogEntry.scala`, add to `LogKind`, after `Phase`:

```scala
  /** The tag that opens an action's band: its name and the Supply it spent
    * (log action sections design, "The tag entry"). */
  case Section extends LogKind("section")
```

- [ ] **Step 4: Rewrite StartLines**

Replace the body of `StartLines.scala` from the class doc comment through `opens` and `title` with the following; `paysCost`, `campaign`, `modifiers` and `cost` stay as they are except where shown.

```scala
/** The tag that opens every action that can take modifiers (log action
  * sections design, "The tag entry"): the action's name and the Supply it
  * has spent by the end of the tag's segment, then, when there is more to
  * say, one line with a Campaign's kind and defender and the modifiers the
  * player chose.
  *
  * `resolutions` is each walker power's resolution: a power that resolves
  * automatically is not a choice, so it is not named.
  */
private[gamelog] final class StartLines(words: LogWords,
    resolutions: Map[PowerId, PowerResolution]):

  def start(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    if run.started.nonEmpty then Vector.empty
    else opening(journal, run, at).toVector.flatMap { (named, said) =>
      val chosen = modifiers(journal, run, at, viewer)
      val line =
        if said.isEmpty then Option.when(chosen.nonEmpty)(Text("With ") +: chosen)
        else Some(said ++ (if chosen.isEmpty then Vector.empty
          else Text(" with ") +: chosen))
      Posted.line(LogKind.Section, Text(named) +: cost(journal, run, at)) +:
        line.toVector.map(Posted.line(LogKind.Action, _))
    }

  /** Recover spends Supply again each time the player continues rolling. */
  def continued(journal: LogJournal, run: Run, at: Int): Option[Posted] =
    run.procedure match
      case ActionRef.Recover
          if run.started.exists(_ != journal.segmentEnd(at)) =>
        val spent = journal.supplySpent(at, run.actor)
        Option.when(spent > 0)(Posted.line(LogKind.Action,
          Vector(Text("Continued"), LogSpan.Cost(spent, "Supply"))))
      case _ => None

  /** The tag's name and what its line says beyond the modifiers, but only at
    * the tag's anchor: the run's first event, except Muster and Trade (their
    * cost step, after the source decision) and Campaign (the first event by
    * which kind and defender are both settled). */
  private def opening(journal: LogJournal, run: Run, at: Int)
      : Option[(String, Vector[LogSpan])] =
    val completing = journal.event(at).isInstanceOf[WalkerCompleted]
    run.procedure match
      case ActionRef.Campaign =>
        campaign(journal, run, at).map(said => ("Campaign", said))
      case ActionRef.Muster | ActionRef.Trade =>
        name(run.procedure).filter(_ =>
          paysCost(journal, run, at) || completing)
          .map(named => (named, Vector.empty))
      case other =>
        name(other).filter(_ => at == run.first)
          .map(named => (named, Vector.empty))

  /** Whether `procedure` opens with a tag, so its power lines wait for it. */
  def opens(procedure: ProcedureRef): Boolean = name(procedure).nonEmpty

  private def name(procedure: ProcedureRef): Option[String] =
    procedure match
      case ActionRef.Search => Some("Search")
      case ActionRef.PlayFacedownAdviser => Some("Play facedown adviser")
      case ActionRef.Recover => Some("Recover")
      case ActionRef.Forge => Some("Forge")
      case ActionRef.Travel => Some("Travel")
      case ActionRef.Muster => Some("Muster")
      case ActionRef.Trade => Some("Trade")
      case ActionRef.Challenge => Some("Challenge")
      case ActionRef.Campaign => Some("Campaign")
      // No modifier window, so no tag.
      case ActionRef.TakeWealth | ActionRef.PlaceBannerResource |
          ActionRef.Negotiation | _: ActionRef.UsePower => None
      case _: PhaseTransitionRef | _: TriggeredProcedureRef => None
```

In `campaignWords`, drop the "Started Campaign: " prefix:

```scala
    defender match
      case CampaignDefender.Bandits =>
        Vector(Text(s"$named against the bandits"))
      case CampaignDefender.Player(player) =>
        Vector(Text(s"$named against "), words.player(player))
```

In `modifiers`, return the joined names alone, since `start` now writes "With " or " with ":

```scala
    journal.readyBefore(run.first).filter(_ => shown.nonEmpty)
      .fold(Vector.empty[LogSpan])(ready => LogWords.join(
        shown.map(id => words.power(ready, run.actor, id, viewer))))
```

- [ ] **Step 5: Take the tag's vector in the formatter**

In `GameLogFormatter.walker`, `start` is now a `Vector[Posted]`. Change `val lines = start.toVector ++` to `val lines = start ++`. Nothing else there changes: `start.isEmpty` reads the same.

- [ ] **Step 6: Run the gamelog tests**

Run: `./sbtw "testOnly oathdigital.application.gamelog.* oathdigital.application.GameServerGatewaySubmitBeginSuite"`
Expected: everything passes except `GameLogGoldenSuite`. If another test fails because it reads "Started ...", "Playing Facedown Adviser" or "Continued Recover", update it the same way as Step 1. If the property "an action with a tag opens with it" fails, a line source posts inside a Muster, Trade or Campaign run before its tag's anchor: stop and report which script and which line, since the spec requires holding that source back the way `PowerLines.held` does and this plan found no such source.

- [ ] **Step 7: Rewrite and review the goldens**

Run: `env GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`, then `git diff src/test/resources/gamelog`.
Expected, and nothing else:
- every `action 1 ... | Started X[cost:...]` becomes `section 1 ... | X[cost:...]` with X one of the nine names;
- `augury.*.log`: `0.3 section 1 search@0 | Search[cost:2 Supply]`, then `0.4 action 1 search@0 | With [card:56|Augury]` (the other seat reads the card as its viewer may), and the later entries at sequence 0 move up one ordinal;
- `raid.*.log`: `2.0 section 1 campaign@... | Campaign[cost:2 Supply]`, then `2.1 action 1 campaign@... | Raid against [player:p2|p2]`;
- `facedown-adviser.*.log`: `0.3 section 1 play-facedown-adviser@0 | Play facedown adviser`;
- `recover-failed.*.log`: `5.0 action 1 recover@0 | Continued[cost:1 Supply]`.
Then run `./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`; expected PASS.

- [ ] **Step 8: Run the gates**

Run: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/LogEntry.scala src/main/scala/oathdigital/application/gamelog/StartLines.scala src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala src/test/scala/oathdigital/application/gamelog/GameLogCampaignSuite.scala src/test/scala/oathdigital/application/gamelog/GameLogDetailSuite.scala src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala src/test/resources/gamelog
git commit -m "feat(log): a tag with the action's name and cost replaces the start line"
```

(Add any other test file Step 6 made you change. End the message with the trailer.)

---

### Task 3: Sections on the wire

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/LogPageCodec.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogProjector.scala:22-23`
- Test: `shared/src/test/scala/oathdigital/protocol/LogPageCodecSuite.scala`
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogRouteSuite.scala`

**Interfaces:**
- Consumes: Task 1's `LogEntry.section: Option[LogSection]`; Task 2's `section` kind.
- Produces: `final case class LogSectionWire(id: Long, action: String)`; `LogEntryWire(sequence, ordinal, kind, depth, spans, section: Option[LogSectionWire] = None)`. On the wire an entry always has `"section"`: `{"id": <number>, "action": <string>}` or `null`.

- [ ] **Step 1: Write the failing tests**

Append to `LogPageCodecSuite`:

```scala
  test("an entry's section round-trips, and an entry without one writes null"):
    val banded = LogPageWire("g", 0, 9, Vector(
      LogEntryWire(6, 0, "section", 1, Vector(LogSpanWire("text", "Search")),
        Some(LogSectionWire(6, "search"))),
      LogEntryWire(7, 0, "turn", 0, Vector(LogSpanWire("text", "Red's turn")))))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(banded)), Right(banded))
    val json = ujson.read(LogPageCodec.encode(banded))
    assertEquals(json("entries")(0)("section"),
      ujson.Obj("id" -> ujson.Num(6), "action" -> ujson.Str("search")))
    assertEquals(json("entries")(1)("section"), ujson.Null)

  test("an entry written before sections decodes with none"):
    val json = """{"gameId":"g","after":0,"nextSequence":9,"entries":""" +
      """[{"sequence":7,"ordinal":0,"kind":"turn","depth":0,"spans":[]}]}"""
    assertEquals(LogPageCodec.decode(json).map(_.entries.map(_.section)),
      Right(Vector(None)))

  test("a section with an unexpected field is rejected at its path"):
    val banded = page.copy(entries = Vector(page.entries.head.copy(
      section = Some(LogSectionWire(4, "travel")))))
    val json = LogPageCodec.encode(banded).replace("\"action\":\"travel\"",
      "\"action\":\"travel\",\"extra\":1")
    assertEquals(LogPageCodec.decode(json).left.map(_.path),
      Left("$.entries[0].section.extra"))
```

Append to `GameLogRouteSuite` (add `LogSectionWire` to its `oathdigital.protocol.projection` import):

```scala
  test("the log page carries each line's section, and no headline has one"):
    val script = LogScripts.round
    val page = present(new GameProjector(catalog).logPage("round",
      script.history, 0L, script.actor), "the whole log page")
    val tag = page.entries.find(_.kind == "section").getOrElse(
      fail(s"no tag in ${page.entries}"))
    assertEquals(tag.section, Some(LogSectionWire(tag.sequence, "travel")))
    assert(page.entries.filter(_.depth == 0).forall(_.section.isEmpty),
      page.entries)
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.protocol.LogPageCodecSuite oathdigital.application.gamelog.GameLogRouteSuite"`
Expected: compilation fails: `LogSectionWire` not found.

- [ ] **Step 3: Add the wire type**

In `LogProjectionDtos.scala`, leave `LogSpanWire` as it is and replace `LogEntryWire` with:

```scala
/** The action an entry belongs to: `id` is the same for every entry of one
  * action, `action` its procedure key (`search`, `use-power:<id>`) or
  * `minor`. A client draws one band per run of entries sharing an `id`. */
final case class LogSectionWire(id: Long, action: String)

/** One log entry; `(sequence, ordinal)` is its stable key. A `section`
  * entry is the tag that opens its section's band. */
final case class LogEntryWire(sequence: Long, ordinal: Int, kind: String,
    depth: Int, spans: Vector[LogSpanWire],
    section: Option[LogSectionWire] = None)
```

- [ ] **Step 4: Encode and decode it**

In `LogPageCodec.scala`:

```scala
  private val EntryFields = Set("sequence", "ordinal", "kind", "depth", "spans",
    "section")
  private val SectionFields = Set("id", "action")
```

```scala
  private def encodeEntry(entry: LogEntryWire): ujson.Value = ujson.Obj(
    "sequence" -> ujson.Num(entry.sequence.toDouble),
    "ordinal" -> entry.ordinal, "kind" -> entry.kind, "depth" -> entry.depth,
    "spans" -> encoded(entry.spans)(encodeSpan),
    "section" -> option(entry.section)(section => ujson.Obj(
      "id" -> ujson.Num(section.id.toDouble), "action" -> section.action)))
```

In `decodeEntry`, after `spans <- ...`:

```scala
      section <- optionalAbsent(value, "section", path)(decodeSection)
    yield LogEntryWire(sequence, ordinal, kind, depth, spans, section)
```

and add:

```scala
  private def decodeSection(raw: ujson.Value, path: String)
      : Result[LogSectionWire] =
    for
      value <- obj(raw, path)
      _ <- exact(value, SectionFields, path)
      id <- long(value, "id", path)
      action <- string(value, "action", path)
    yield LogSectionWire(id, action)
```

- [ ] **Step 5: Project it**

In `GameLogProjector.scala`, import `LogSectionWire` with the other wire types and replace `wire`:

```scala
  def wire(entry: LogEntry): LogEntryWire = LogEntryWire(entry.sequence,
    entry.ordinal, entry.kind.key, entry.depth, entry.spans.map(span),
    entry.section.map(section => LogSectionWire(section.id, section.action)))
```

- [ ] **Step 6: Run the tests**

Run: `./sbtw "testOnly oathdigital.protocol.LogPageCodecSuite oathdigital.application.gamelog.GameLogRouteSuite"`
Expected: PASS.

- [ ] **Step 7: Run the gates**

Run: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala shared/src/main/scala/oathdigital/protocol/projection/LogPageCodec.scala src/main/scala/oathdigital/application/gamelog/GameLogProjector.scala shared/src/test/scala/oathdigital/protocol/LogPageCodecSuite.scala src/test/scala/oathdigital/application/gamelog/GameLogRouteSuite.scala
git commit -m "feat(protocol): a log entry carries its section"
```

---

### Task 4: Bands in the Game Log

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala` (`show`, `redraw`, `item`, object `item`)
- Modify: `frontend/styles.css` (after the `.game-log .log-dice` rule, near line 1051)
- Test: `frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala`

**Interfaces:**
- Consumes: Task 3's `LogEntryWire.section: Option[LogSectionWire]` and the `section` entry kind.
- Produces: `GameLogPane.item(entry, colors, start: Boolean = true, end: Boolean = true): dom.Element`; `GameLogPane.band(entry: LogEntryWire, start: Boolean, end: Boolean): Vector[String]`; `GameLogPane.bandName(action: String): String`; `GameLogPane.sameSection(a: LogEntryWire, b: LogEntryWire): Boolean`. CSS classes `log-band`, `log-band-<name>`, `log-band-start`, `log-band-end`, and the custom property `--band`.

- [ ] **Step 1: Write the failing tests**

In `GameLogPaneSuite.scala`, add `LogSectionWire` to the protocol import and add after the "a dice span draws the die-face chips" test:

```scala
  /** A line of the section `id` for `action`, reading `text`. */
  private def sectioned(sequence: Long, kind: String, action: String, id: Long,
      text: String) = LogEntryWire(sequence, 0, kind, 1,
    Vector(LogSpanWire("text", text)), Some(LogSectionWire(id, action)))

  private val searchTag = sectioned(6, "section", "search", 6, "Search")
  private val drew = sectioned(7, "action", "search", 6, "Drew Fox")
  private val played = sectioned(8, "action", "search", 6,
    "Played Fox to Ruined Keep")
  private val moved = sectioned(9, "delta", "minor", 9, "Moved 2 warbands")

  /** Each shown item's band classes, in order; "" for an item with none. */
  private def bands(content: dom.Element): Vector[String] =
    items(content).map(_.getAttribute("class").split(" ").toVector
      .filter(_.startsWith("log-band")).mkString(" "))

  test("a redraw bands each section and marks its first and last entry"):
    val content = box(total = 0, height = 0, scrolled = 0)
    new GameLogPane(content).show("g|red",
      Vector(setup, turn, searchTag, drew, played, moved), Map.empty)
    assertEquals(bands(content), Vector("", "",
      "log-band log-band-search log-band-start",
      "log-band log-band-search",
      "log-band log-band-search log-band-end",
      "log-band log-band-minor log-band-start log-band-end"))
    assertEquals(items(content)(2).getAttribute("class"),
      "log-entry log-line log-section log-band log-band-search log-band-start")

  test("an appended line of the same section takes over the band's end"):
    val content = box(total = 0, height = 0, scrolled = 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, turn, searchTag, drew), Map.empty)
    pane.show("g|red", Vector(setup, turn, searchTag, drew, played), Map.empty)
    assertEquals(bands(content).drop(2), Vector(
      "log-band log-band-search log-band-start",
      "log-band log-band-search",
      "log-band log-band-search log-band-end"))

  test("an appended line of a new section starts a band of its own"):
    val content = box(total = 0, height = 0, scrolled = 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, turn, searchTag, drew), Map.empty)
    pane.show("g|red", Vector(setup, turn, searchTag, drew, moved), Map.empty)
    assertEquals(bands(content).drop(3), Vector(
      "log-band log-band-search log-band-end",
      "log-band log-band-minor log-band-start log-band-end"))

  test("the divider splits a band into two pieces"):
    val content = box(total = 500, height = 100, scrolled = 0)
    new GameLogPane(content).show("g|red",
      Vector(setup, turn, searchTag, drew, played), Map.empty, since = Some(6))
    assertEquals(bands(content), Vector("", "",
      "log-band log-band-search log-band-start log-band-end",
      "",
      "log-band log-band-search log-band-start",
      "log-band log-band-search log-band-end"))

  test("a facedown adviser takes Search's band; a power's band drops its id"):
    val content = box(total = 0, height = 0, scrolled = 0)
    new GameLogPane(content).show("g|red", Vector(
      sectioned(6, "section", "play-facedown-adviser", 6,
        "Play facedown adviser"),
      sectioned(8, "action", "use-power:silver-tongue", 8, "Silver Tongue")),
      Map.empty)
    assertEquals(bands(content), Vector(
      "log-band log-band-search log-band-start log-band-end",
      "log-band log-band-use-power log-band-start log-band-end"))
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.GameLogPaneSuite"`
Expected: the five new tests fail; the band lists are all "".

- [ ] **Step 3: Give entries their band classes**

In the `GameLogPane` object, replace `item` and add the helpers after it:

```scala
  def item(entry: LogEntryWire, colors: Map[String, String],
      start: Boolean = true, end: Boolean = true): dom.Element =
    val depth = if entry.depth == 0 then "log-headline" else "log-line"
    val classes = Vector("log-entry", depth, s"log-${entry.kind}") ++
      band(entry, start, end)
    val node = element("li", classes.mkString(" "))
    node.setAttribute("title", s"${entry.sequence}.${entry.ordinal}")
    entry.spans.foreach(span => node.appendChild(spanNode(span, colors)))
    node

  /** An entry's band (DESIGN.md, The Action Band Rule): its section's band
    * class, and an edge where the band starts or ends in this list. */
  def band(entry: LogEntryWire, start: Boolean, end: Boolean): Vector[String] =
    entry.section.toVector.flatMap(section =>
      Vector("log-band", s"log-band-${bandName(section.action)}") ++
        Option.when(start)("log-band-start") ++ Option.when(end)("log-band-end"))

  /** Playing a facedown adviser continues a Search, so it takes Search's
    * band; every power shares one band whatever its id. */
  def bandName(action: String): String =
    if action == "play-facedown-adviser" then "search"
    else if action.startsWith("use-power:") then "use-power"
    else action

  def sameSection(earlier: LogEntryWire, later: LogEntryWire): Boolean =
    earlier.section.nonEmpty &&
      earlier.section.map(_.id) == later.section.map(_.id)
```

In the class, replace the private `item` with:

```scala
  private def item(entry: LogEntryWire, colors: Map[String, String],
      start: Boolean, end: Boolean): dom.Element =
    val node = GameLogPane.item(entry, colors, start, end)
    if entry.depth == 0 then headline(node, entry.kind) else node
```

In `show`, replace the appending loop `entries.drop(shown.size).foreach(...)` with:

```scala
        // A line that continues the last entry's section moves the band's
        // end down to itself; anything else starts a band of its own.
        entries.drop(shown.size).foldLeft(shown.last) { (previous, entry) =>
          val continues = GameLogPane.sameSection(previous, entry)
          if continues then Option(list.lastElementChild)
            .foreach(_.classList.remove("log-band-end"))
          list.appendChild(item(entry, colors, start = !continues, end = true))
          entry
        }
```

In `redraw`, replace the `entries.zipWithIndex.foreach` block with:

```scala
    // The divider ends the band above it and starts the band below it.
    entries.zipWithIndex.foreach { (entry, index) =>
      val divided = divideAt.contains(index)
      if divided then list.appendChild(GameLogPane.divider)
      val start = divided ||
        !entries.lift(index - 1).exists(GameLogPane.sameSection(_, entry))
      val end = divideAt.contains(index + 1) ||
        !entries.lift(index + 1).exists(GameLogPane.sameSection(entry, _))
      list.appendChild(item(entry, colors, start, end))
    }
```

Add one sentence to the class doc comment, after "...offered the "New" chip.": "The lines of one action share a band; an append moves the band's end, never anything above it."

- [ ] **Step 4: Run the tests**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.GameLogPaneSuite oathdigital.frontend.GameLogOverlaySuite"`
Expected: PASS.

- [ ] **Step 5: Draw the bands**

In `frontend/styles.css`, after the `.game-log .log-dice { vertical-align: top; }` rule, add:

```css
/* The Action Band Rule (DESIGN.md): the lines of one action share a band in
   that action's color, warm for the aggressive ones, mixed into the Pane
   fill. The top edge sits on the band's first entry and the bottom edge on
   its last, so an appended line only moves the bottom edge down. Inside a
   band a spent cost is Ink Dim: Ink Spent falls under 4.5:1 on a fill. */
.game-log .log-band { --band: #b8ab92; --band-fill: 6%; --band-edge: 20%;
  padding-right: 6px;
  background: color-mix(in srgb, var(--band) var(--band-fill), #211f1b);
  border: 0 solid color-mix(in srgb, var(--band) var(--band-edge), #211f1b);
  border-width: 0 1px; }
.game-log .log-band-campaign, .game-log .log-band-muster,
.game-log .log-band-challenge, .game-log .log-band-travel,
.game-log .log-band-search, .game-log .log-band-trade,
.game-log .log-band-recover, .game-log .log-band-forge {
  --band-fill: 9%; --band-edge: 28%; }
.game-log .log-band-campaign { --band: #d47c74; }
.game-log .log-band-muster { --band: #c2b25c; }
.game-log .log-band-challenge { --band: #c99a62; }
.game-log .log-band-travel { --band: #7fb0a8; }
.game-log .log-band-search { --band: #b88aae; }
.game-log .log-band-trade { --band: #94b58a; }
.game-log .log-band-recover { --band: #9f8fc0; }
.game-log .log-band-forge { --band: #8fa3bd; }
.game-log .log-band-start { margin-top: 4px; padding-top: 2px;
  border-top-width: 1px; border-top-left-radius: 4px;
  border-top-right-radius: 4px; }
.game-log .log-band-end { margin-bottom: 4px; padding-bottom: 2px;
  border-bottom-width: 1px; border-bottom-left-radius: 4px;
  border-bottom-right-radius: 4px; }
.game-log .log-band .log-cost { color: #a99f8c; }
/* A band's tag: the action's name in its color, then what it spent. */
.game-log .log-section { color: var(--band, #b8ab92); font-size: 11px;
  font-weight: 700; letter-spacing: .12em; text-transform: uppercase; }
.game-log .log-section .log-cost { margin-left: .6em; font-size: 12px;
  font-weight: 400; letter-spacing: normal; text-transform: none; }
```

Nothing to run for CSS here; Task 6's browser check looks at it.

- [ ] **Step 6: Run the gates**

Run: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala frontend/styles.css frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala
git commit -m "feat(log): each action's lines sit on a band in the action's color"
```

---

### Task 5: Colored dice and the redrawn swords

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/DieFace.scala` (`face`, `roll`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala` (the `"dice"` case of `spanNode`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala` (`poolDie`, `rollFeedback`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/TokenSprite.scala:65-69` (the two swords)
- Modify: `frontend/styles.css` (after `.die-face-blank`, near line 72)
- Test: `frontend/src/test/scala/oathdigital/frontend/DieFaceSuite.scala`, `GameLogPaneSuite.scala`, `WalkerDecisionDraftSuite.scala`, `WalkerChoicePanelRenderSuite.scala`

**Interfaces:**
- Consumes: the dice span's `unit` (`attack` or `defense`), unchanged since before this plan.
- Produces: `DieFace.face(value: String, die: Option[String] = None)`, `DieFace.roll(faces: Vector[String], die: Option[String] = None)`; chip classes `die-face-attack`, `die-face-defense`; `WalkerPanelSupport.poolDie(pool: String): Option[String]`.

- [ ] **Step 1: Write the failing tests**

Append to `DieFaceSuite`:

```scala
  test("a face rolled on a known die carries that die; any other stays neutral"):
    assertEquals(DieFace.face("one-sword", Some("attack")).getAttribute("class"),
      "die-face die-face-one-sword die-face-attack")
    assertEquals(DieFace.face("blank", Some("defense")).getAttribute("class"),
      "die-face die-face-blank die-face-defense")
    assertEquals(DieFace.face("one-shield").getAttribute("class"),
      "die-face die-face-one-shield")
    // A die this client does not know is drawn neutral, not guessed at.
    assertEquals(DieFace.face("one-shield", Some("round")).getAttribute("class"),
      "die-face die-face-one-shield")

  test("a roll gives every face its die"):
    val row = DieFace.roll(Vector("one-sword", "blank"), Some("attack"))
    assertEquals(all(row, ".die-face-attack").size, 2)
```

In `GameLogPaneSuite`, at the end of "a dice span draws the die-face chips, one per face", add:

```scala
    assertEquals(content.querySelectorAll(".die-face-attack").length, 2)
```

Append to `WalkerDecisionDraftSuite`:

```scala
  test("a pool rolls the die its action rolls"):
    assertEquals(WalkerPanelSupport.poolDie("campaign.attack"), Some("attack"))
    assertEquals(WalkerPanelSupport.poolDie("campaign.defense"),
      Some("defense"))
    assertEquals(WalkerPanelSupport.poolDie("recover"), Some("defense"))
    assertEquals(WalkerPanelSupport.poolDie("gambling-hall"), None)
```

In `WalkerChoicePanelRenderSuite`, at the end of "a choose-one asked after a roll draws the roll before the question", add:

```scala
    // A Campaign's defense rolls the defense die.
    assertEquals(all(panel, ".die-face-defense").size, 2)
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.DieFaceSuite oathdigital.frontend.GameLogPaneSuite oathdigital.frontend.WalkerDecisionDraftSuite oathdigital.frontend.WalkerChoicePanelRenderSuite"`
Expected: compilation fails: `face` takes one argument; `poolDie` is not a member.

- [ ] **Step 3: Give the chips their die**

In `DieFace.scala`, add after `symbols`:

```scala
  /** The dice a roll can name. A roll on any other die keeps the neutral
    * chip. */
  private val dice = Set("attack", "defense")
```

Replace `face` and `roll`'s headers and their first lines:

```scala
  /** `die` is the die the face was rolled on; a face on a known die is
    * drawn in that die's color. */
  def face(value: String, die: Option[String] = None): dom.Element =
    val classes = Vector("die-face", s"die-face-$value") ++
      die.filter(dice).map(kind => s"die-face-$kind")
    val node = ServerUiSupport.element("span", classes.mkString(" "))
```

(the rest of `face` is unchanged)

```scala
  def roll(faces: Vector[String], die: Option[String] = None): dom.Element =
    val node = ServerUiSupport.element("span", "die-faces")
    node.setAttribute("aria-label", faces.map(name).mkString(", "))
    faces.foreach(value => node.appendChild(face(value, die)))
    node
```

In `GameLogPane.spanNode`, pass the span's die:

```scala
        val dice = DieFace.roll(faces.split(" ").toVector.filter(_.nonEmpty),
          span.unit)
```

In `WalkerPanelSupport.scala`, add after `poolLabel`:

```scala
  /** The die a pool rolls: a Campaign's attack rolls the attack die, its
    * defense and Recover the defense die. An unknown pool keeps the neutral
    * chip rather than a guessed color. */
  private[frontend] def poolDie(pool: String): Option[String] = pool match
    case "campaign.attack" => Some("attack")
    case "campaign.defense" | "recover" => Some("defense")
    case _ => None
```

and in `rollFeedback` replace `row.appendChild(DieFace.roll(outcome.faces))` with `row.appendChild(DieFace.roll(outcome.faces, poolDie(outcome.pool)))`.

- [ ] **Step 4: Redraw the swords**

In `TokenSprite.scala`, replace the `sword` and `hollow-sword` entries with:

```scala
    ("sword", "sword",
      "M12 1 16 6v8H8V6zM5 14h14v3H5zm5.5 3h3v6h-3z"),
    // The same sword in outline: blade, guard and grip are each a ring, so
    // the hollow reads at log size where a sliver of a hollow did not.
    ("hollow-sword", "hollow sword",
      "M12 1 16 6v8H8V6zm0 3.4L10 7v5h4V7zM4.5 14h15v4h-15z" +
        "m1.8 1.4v1.2h11.4v-1.2zM10 18h4v5h-4zm1.4 1.2v2.6h1.2v-2.6z")
```

- [ ] **Step 5: Color the chips**

In `frontend/styles.css`, after `.die-face-blank { border-style: dashed; border-color: #5d5144; }`, add:

```css
/* A face rolled on a known die is that die: filled in its color, symbols in
   Bright Cream, so attack and defense rolls part at a glance. A blank face
   on a known die stays an empty dashed chip, tinted toward its die. */
.die-face-attack { --die: var(--die-attack); }
.die-face-defense { --die: var(--die-defense); }
.die-face-attack, .die-face-defense { color: #fff2d6; background: var(--die);
  border-color: color-mix(in srgb, var(--die) 70%, #fff2d6); }
.die-face-attack .token-glyph, .die-face-defense .token-glyph {
  color: #fff2d6; }
.die-face-blank.die-face-attack, .die-face-blank.die-face-defense {
  background: color-mix(in srgb, var(--die) 25%, #17140f);
  border-color: var(--die); }
```

- [ ] **Step 6: Run the tests**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.DieFaceSuite oathdigital.frontend.GameLogPaneSuite oathdigital.frontend.WalkerDecisionDraftSuite oathdigital.frontend.WalkerChoicePanelRenderSuite oathdigital.frontend.RecoverPanelSuite"`
Expected: PASS.

- [ ] **Step 7: Run the gates**

Run: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/DieFace.scala frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala frontend/src/main/scala/oathdigital/frontend/TokenSprite.scala frontend/styles.css frontend/src/test/scala/oathdigital/frontend/DieFaceSuite.scala frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala frontend/src/test/scala/oathdigital/frontend/WalkerDecisionDraftSuite.scala frontend/src/test/scala/oathdigital/frontend/WalkerChoicePanelRenderSuite.scala
git commit -m "feat(dice): attack and defense chips in their dice colors, and an outlined hollow sword"
```

---

### Task 6: DESIGN.md, the roadmap and the browser check

**Files:**
- Modify: `DESIGN.md` (front matter `colors`; Colors; Named Rules; Dice; Panes)
- Modify: `docs/ROADMAP.md` (`### Phase - Campaign UI`)
- Modify (only if the browser check finds a defect): `frontend/styles.css`, `frontend/src/main/scala/oathdigital/frontend/TokenSprite.scala`

**Interfaces:**
- Consumes: everything above.
- Produces: nothing code reads.

- [ ] **Step 1: Record the design in DESIGN.md**

1. In the front matter, after `force-bandit: "#d7ed72"`, add:

```yaml
  band-campaign: "#d47c74"
  band-muster: "#c2b25c"
  band-challenge: "#c99a62"
  band-travel: "#7fb0a8"
  band-search: "#b88aae"
  band-trade: "#94b58a"
  band-recover: "#9f8fc0"
  band-forge: "#8fa3bd"
  band-neutral: "#b8ab92"
```

2. In `## Colors`, add a subsection right before `### Named Rules`:

```markdown
### Log action bands
UI colors, used only in the Game Log (The Action Band Rule). The aggressive
actions are warm: **Campaign** (`band-campaign`) rose, **Muster**
(`band-muster`) ochre, **Challenge** (`band-challenge`) amber. The others are
cool: **Travel** (`band-travel`) sea green, **Search** (`band-search`) mauve,
also for playing a facedown adviser, **Trade** (`band-trade`) sage,
**Recover** (`band-recover`) lavender, **Forge** (`band-forge`) steel. Every
other action sits on the **Neutral band** (`band-neutral`).
```

3. In `### Named Rules` under Colors, replace the last sentence of The Pieces Carry the Color Rule ("The three map regions are the one exception, and they carry the board's colors, not new ones.") with: "Two exceptions: the three map regions carry the board's colors, not new ones, and the Game Log's action bands follow The Action Band Rule." Then add after that rule:

```markdown
**The Action Band Rule.** In the Game Log the lines of one action share a
band: its color mixed 9% into the Pane fill, edged with the color mixed 28%
(the neutral band 6% and 20%), opened by a tag naming the action in its
color. The eight action colors are warm for aggressive actions, live only in
the log, and never stand for a suit, a player or a die. Every tag clears
4.5:1 on its band.
```

4. In `### Dice`, replace "A blank face is a dashed `#5d5144` chip with nothing on it." with:

```markdown
A face rolled on a known die is that die: filled with Attack red or Defense
blue, with a border of the die color mixed 30% toward Bright Cream and its
symbols in Bright Cream. A blank face is a dashed chip with nothing on it:
`#5d5144` on Base when its die is unknown, and on a known die a border in the
die color on the die color mixed 25% into Base. The sword is solid; the
hollow sword is the same sword with blade, guard and grip each drawn as a
ring, so the two part by shape at log size.
```

5. In `### Panes`, after "start of every entry sits on one edge." and before "Three reading aids sit on that list.", insert:

```markdown
The lines of one action sit on its band (The Action Band Rule): 1px edges,
4px radius and 4px of space at the band's two ends, 6px of right padding,
the hanging indent kept inside. A band opens with its tag: the action's name
in 11px uppercase, 700, 0.12em tracking, in the band's color, then the Supply
it spent in 12px, 0.6em after it. Inside a band a spent cost is Ink Dim,
because Ink Spent falls under 4.5:1 on a band's fill. A Wake's Take Wealth,
phase changes, triggered procedures, state-based changes and headlines stand
outside any band, and the "Since you last looked" rule splits a band in two.
```

- [ ] **Step 2: Close the phase in the roadmap**

Replace the whole `### Phase - Campaign UI` section (its heading through the line before `### Catalog - to verify`) with:

```markdown
### Phase - Campaign battle summary

Left out of the Game Log action sections
([spec](superpowers/specs/2026-10-05-log-action-sections-design.md)), which
answered the 2026-10-01 playtest's "make it clear what a Campaign's battle
did":

- [ ] A summary of a Campaign's battle, opened by clicking one of its log
  lines: attack and defense dice, battle plans, sacrificed warbands, the
  totals and the winner. It is not always visible.
- [ ] The same summary filling in while a Campaign runs: the dice on each
  side as battle plans are chosen, and the sacrificed warbands.
```

Run: `python3 scripts/check-markdown-links.py`
Expected: PASS. Then `grep -n "Campaign UI" docs/ROADMAP.md` prints nothing.

- [ ] **Step 3: Check the log in the browser**

From the worktree root, with `S` the session scratchpad plus `/db-log-sections` (a new, empty directory; nothing is copied from `var/`):

```bash
mkdir -p $S
```

```bash
./sbtw "frontend/fastLinkJS" "runMain oathdigital.server.OathServer --port 8081 --database-path $S/oathdigital"
```

Run the second command through Bash with `run_in_background` and wait for "Start completed" in its log. Open `http://127.0.0.1:8081/?mode=server` in the built-in browser at 1440×900, press "New game", and play Setup through with the default choices. Play until the log holds, in the acting player's turns: a Search that keeps a card as a facedown adviser, a later Play facedown adviser, a minor action (move warbands), a Trade and a Campaign with a roll on both sides. Then, with `read_page` for each check, a `wait` of 1 second before each screenshot, and one screenshot per check:

1. Each of those actions sits on its own band; Search and Play facedown adviser share the mauve; the minor action's band is neutral and has no tag; headlines, the Wake line and "Increased supply" sit outside any band.
2. Each tag reads its name in uppercase in its band's color and its cost after it. `javascript_tool` on one tag: `getComputedStyle(document.querySelector('.log-band-campaign')).backgroundColor` is a mixed color, not `rgba(0, 0, 0, 0)` (so `color-mix` is supported).
3. In the Campaign band, the attack chips are red and the defense chips blue, with Bright Cream symbols; a blank face is a dashed chip in its die's color. The roll panel during the Campaign shows the same colors.
4. Zoom a screenshot on a roll with both swords and hollow swords. The hollow sword reads as an outline at 13px. If its outline blurs, thicken the rings in `TokenSprite` without changing the outer shape, rebuild and look again once.
5. Open the log overlay: the same bands, tags and dice.
6. Scroll up, append a line from another action, and confirm the New chip appears and the last band's bottom edge moved, with nothing above it shifting.
7. Repeat checks 1 and 3 at 1024×768.
8. `read_console_messages` with `onlyErrors` shows nothing new.

Fix every failure in one batch, reload, confirm once, and stop the server with `lsof -ti :8081 | xargs kill`. Reset the window with `resize_window` preset `desktop`.

- [ ] **Step 4: Run the final gates**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add DESIGN.md docs/ROADMAP.md
git commit -m "docs: the Action Band Rule, filled dice, and the Campaign UI phase is done"
```

(Add `frontend/styles.css` or `TokenSprite.scala` if Step 3 changed them, and say what changed in the message body.)
