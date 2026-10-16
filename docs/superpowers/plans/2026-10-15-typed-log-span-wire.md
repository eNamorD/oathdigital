# Typed Log Span Wire Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The game log's wire and the formatter's output become one set of
sealed types, so a wrong combination of span kind and fields stops being
representable and stops decoding.

**Architecture:**

- **Today.** `LogSpanWire` is `kind: String` plus four unchecked optional
  siblings shared by eight kinds. `GameLogProjector.span` maps the formatter's
  already-correct `LogSpan` family onto it and loses information doing so: a
  roll's faces travel as a space-joined string in a field named `id`.
- **The move, not a mirror.** `LogSpan`, `LogEntry`, `LogSection`,
  `LogTakenBack` and `LogKind` move from
  `src/main/scala/oathdigital/application/gamelog/LogEntry.scala` to the shared
  protocol package. The four `Wire` twins and both mapping functions are
  deleted; `LogPageWire` becomes `LogPage`. `Posted` stays in `gamelog`.
- **Three derived fields leave the wire.** `text` is a `def` on the trait,
  `depth` a `def` on `LogKind`, and a roll's die is read off its first face.
- **The codec becomes discriminated.** `LogPageCodec` gains one encoder and
  one decoder arm per span case, each with its own `exact` field set, in the
  shape `ActionProjectionCodec.decodeDecisionQuery` already uses. An unknown
  kind is a `ProtocolDecodeFailure.UnknownVariant`.
- **The faces get a vocabulary.** A shared `DieFace` enum carries each face's
  wire `key`, its display `word` and its `Die`. It replaces three of the four
  face tables in the tree; the fourth (`WalkerDecisionProjector.faceName`,
  which serves the decision wire) stays and is held to it by a set-equality
  test.
- **Strict decoding needs somewhere to fail.** `GameJson.logResponse` maps
  `UnknownVariant` to `GameClientFailure.UnsupportedProjection` and
  `TableSession.fetchLog` routes a failure to the session's existing
  unsupported branch, the stale-bundle path the typed decision form slice
  built.

**Tech Stack:** Scala 3.9.0 on the JVM and Scala.js, ujson, munit, built
through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-10-15-typed-log-span-wire-design.md`
(committed as `7a162ad3`). Read it before starting, with
`docs/superpowers/specs/2026-09-25-game-log-design.md` for the vocabulary and
`docs/superpowers/specs/2026-09-25-decision-form-tag-separation-decision.md`
for why table 2 stays apart.

**Starts from:** `main`. Check that
`shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala`
is 36 lines and still defines `LogSpanWire`.

## Global Constraints

- **Build rules.** `-Werror` with
  `-Wunused:imports,privates,locals,implicits,nowarn`: an unused import or an
  unused private member fails the build. Every import added by this plan must
  be used by the file that carries it, and every import left behind by a
  deletion must go.
- **Cross-compilation.** Everything under `shared/src/main` is compiled for
  both the JVM backend and the Scala.js frontend, and may import only
  `oathdigital.protocol` and `oathdigital.model` types that already appear
  there. It may **not** import `oathdigital.application`,
  `oathdigital.gameplay` or `oathdigital.model.DieFace`.
- **File size.** Production and test Scala files stay at or under 800 lines
  (`scripts/check-architecture.py`, `BackendArchitectureSuite`).
  `WalkerDecisionProjectorSuite.scala` is 734 lines and
  `WalkerDecisionProjector.scala` 695; both grow by under 15 in this plan.
- **Line width.** Scala lines stay at or under 80 characters.
- **Architecture tests.** `BackendArchitectureSuite` forbids `application`
  from importing `server`, `persistence` or `serialization`. It does not
  restrict `application` from importing `oathdigital.protocol`, and
  `gamelog` already does.
- **Full gate.** `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` exit 0,
  and `git diff --check` clean, before each commit.
- **Golden log files.** `src/test/resources/gamelog/*.log` must not change in
  this plan. If a task's diff touches one, the task is wrong: stop and report.
  `GAMELOG_GOLDEN=write` exists but is not authorized here.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree. Symlink the main checkout's
  `.tooling` and `node_modules` into it before the first `./sbtw`.
- **Commit trailer.** Every commit in this plan ends with
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`, including commits
  made by a subagent — the plan's trailer overrides the subagent's own.

## Three resolutions the spec left open

Record these; a reviewer will ask.

1. **`DieFace` collides with `oathdigital.model.DieFace`.** The domain already
   owns that name for the open trait `AttackDieFace` and `DefenseDieFace`
   extend. The new shared enum keeps the spec's name, and the two collisions
   are resolved locally: `LogWords` imports it renamed (`DieFace => RolledFace`),
   and the frontend's renderer object `DieFace` is renamed `DieFaceChip`
   (Task 6) so the enum owns the bare name on that side.
2. **An unknown `LogEntry.kind` must also be rejected.** The spec names five
   decode rejections and does not mention the entry kind, but moving `LogKind`
   onto the wire type makes an unknown `kind` string undecodable. It is an
   `UnknownVariant`, for the same reason an unknown span kind is: the cause is
   the vocabulary, and the client acts on it by reloading.
3. **The decision wire still sends face keys as strings.** The spec says
   "an unknown face and an unknown die are no longer values the type admits",
   but `WalkerPanelSupport.rollFeedback` feeds the renderer
   `WalkerRollOutcomeProjection.faces`, which is `Vector[String]` from table 2
   and out of this slice's scope. So the renderer keeps one string entry point
   (`rollKeys`) for that route, with its unknown-face tolerance intact, and
   gains a typed one (`roll`) for the log. The die becomes `Option[Die]` on
   both, so an unknown *die* really does stop being representable.

## Delivery

The spec plans one slice in two commits. A plan task must be independently
testable, and a Scala type move does not compile in halves, so the two
commits become two groups of committing tasks:

- **The collapse:** Tasks 1-4.
- **The faces:** Tasks 5-8.

---

### Task 1: Move the five log types to the shared protocol package

No shape change, no wire change. The `Wire` twins and
`GameLogProjector.span`/`wire` still compile against the moved types, so the
full gate must pass with no test edited beyond its import list.

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala`
- Delete: `src/main/scala/oathdigital/application/gamelog/LogEntry.scala`
- Create: `src/main/scala/oathdigital/application/gamelog/Posted.scala`
- Modify (import line only): the 15 other files under
  `src/main/scala/oathdigital/application/gamelog/` and 19 files under
  `src/test/scala/oathdigital/application/gamelog/` listed in Step 3.

**Interfaces:**
- Consumes: nothing.
- Produces: `oathdigital.protocol.projection.{LogSpan, LogEntry, LogSection,
  LogTakenBack, LogKind}` with exactly the shapes they have today, including
  `LogEntry.depth: Int` as a field and `LogSpan.Cost(value: Int, unit: String)`.
  `oathdigital.application.gamelog.Posted` keeps
  `Posted(kind, depth, spans, section)` with `Posted.headline` and
  `Posted.line`.

- [ ] **Step 1: Append the moved types to the shared DTO file**

Append to `LogProjectionDtos.scala`, below the existing `LogPageWire`. Copy
the doc comments across unchanged from `LogEntry.scala`; they are the record
of what each type means.

```scala
/** One log entry (game log design, "Output"). `(sequence, ordinal)` is its
  * stable identity: `sequence` is the journal index of the event that posted
  * it, `ordinal` its position among the entries that event posted. Depth 0
  * is a headline, depth 1 a line; only a line can carry a section.
  */
final case class LogEntry(sequence: Long, ordinal: Int, kind: LogKind,
    depth: Int, spans: Vector[LogSpan], section: Option[LogSection] = None,
    takenBack: Option[LogTakenBack] = None)

/** The taken-back group an entry belongs to (undo design, "Game Log"):
  * `group` is the sequence of the group's first entry, the same for every
  * entry of one contiguous run of voided entries, and `header` the line
  * that opens it, "p1 took back the following moves:". */
final case class LogTakenBack(group: Long, header: Vector[LogSpan])

/** The action an entry belongs to (log action sections design, "Server"):
  * `id` is the journal index of the action's first event, the same for
  * every entry of one action, and `action` its procedure key, or `minor`
  * for a minor action. A client draws one band per section. */
final case class LogSection(id: Long, action: String)

/** `key` is the kind's wire spelling. */
enum LogKind(val key: String):
  case Round extends LogKind("round")
  case Turn extends LogKind("turn")
  /** Wake, Act or Rest, under its turn's headline. */
  case Phase extends LogKind("phase")
  /** The tag that opens an action's band: its name and the Supply it spent
    * (log action sections design, "The tag entry"). */
  case Section extends LogKind("section")
  case Action extends LogKind("action")
  case Decision extends LogKind("decision")
  case Roll extends LogKind("roll")
  case Delta extends LogKind("delta")
  case Trigger extends LogKind("trigger")
  case Victory extends LogKind("victory")

/** One piece of an entry's text: plain words or a typed reference. `text` is
  * what a reader that ignores the kind shows. A card a viewer may not
  * identify is never a `Card`: it is `Text` naming its back.
  */
sealed trait LogSpan extends Product with Serializable:
  def text: String
object LogSpan:
  final case class Text(value: String) extends LogSpan:
    def text: String = value
  final case class Player(id: String, name: String) extends LogSpan:
    def text: String = name
  final case class Card(id: String, name: String) extends LogSpan:
    def text: String = name
  final case class Site(id: String, name: String) extends LogSpan:
    def text: String = name
  final case class Amount(value: Int, unit: String) extends LogSpan:
    def text: String = s"$value $unit"
  /** One roll of one die kind, drawn as dice: `faces` are the wire names the
    * client's die chips know, `names` the rulebook's ("Attack" and "Defend"). */
  final case class Dice(die: String, faces: Vector[String],
      names: Vector[String]) extends LogSpan:
    def text: String = names.mkString(", ")
  /** What an action spent, drawn apart from the sentence (game log design,
    * "Supply"). */
  final case class Cost(value: Int, unit: String) extends LogSpan:
    def text: String = s"−$value $unit"
  /** More cards than a line names inline, read as "6 cards": the list a
    * client opens, in order, each card's face if its viewer may identify it
    * and its back otherwise (catalog batch 2, N5). */
  final case class Cards(cards: Vector[CardDetailsProjection]) extends LogSpan:
    def text: String = s"${cards.size} cards"
```

- [ ] **Step 2: Replace `LogEntry.scala` with `Posted.scala`**

```bash
git rm src/main/scala/oathdigital/application/gamelog/LogEntry.scala
```

Create `src/main/scala/oathdigital/application/gamelog/Posted.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.protocol.projection.{LogKind, LogSection, LogSpan}

/** An entry before it has a key: what one event posts, in order. */
private[gamelog] final case class Posted(kind: LogKind, depth: Int,
    spans: Vector[LogSpan], section: Option[LogSection] = None)
private[gamelog] object Posted:
  def headline(kind: LogKind, spans: Vector[LogSpan]): Posted =
    Posted(kind, 0, spans)
  def line(kind: LogKind, spans: Vector[LogSpan]): Posted =
    Posted(kind, 1, spans)
```

- [ ] **Step 3: Add the import line to every file that names a moved type**

Insert `import oathdigital.protocol.projection.{...}` into each file, in the
alphabetical position the file's existing `oathdigital.*` import block uses.
A file that already has `import LogSpan.Text` keeps it unchanged: it resolves
against the newly imported `LogSpan`.

Production (`src/main/scala/oathdigital/application/gamelog/`):

| File | Import |
|---|---|
| `ActionLines.scala` | `{LogKind, LogSpan}` |
| `CampaignLines.scala` | `{LogKind, LogSpan}` |
| `ChoiceWords.scala` | `{LogSpan}` |
| `ConsentLines.scala` | `{LogKind, LogSpan}` |
| `DetailLines.scala` | `{LogKind, LogSpan}` |
| `EventLines.scala` | `{LogKind, LogSpan}` |
| `GameLogFormatter.scala` | `{LogEntry, LogKind, LogSpan, LogTakenBack}` |
| `LogSections.scala` | `{LogSection}` |
| `LogWords.scala` | `{LogSpan}` |
| `NegotiationLines.scala` | `{LogKind, LogSpan}` |
| `PowerLines.scala` | `{LogKind, LogSpan}` |
| `SetupLines.scala` | `{LogKind, LogSpan}` |
| `StartLines.scala` | `{LogKind, LogSpan}` |
| `TakenBackGroups.scala` | `{LogEntry, LogSpan, LogTakenBack}` |

`GameLogProjector.scala` already imports from the package; widen its braces to:

```scala
import oathdigital.protocol.projection.{LogEntry, LogEntryWire, LogPageWire,
  LogSectionWire, LogSpan, LogSpanWire, LogTakenBackWire}
```

Test (`src/test/scala/oathdigital/application/gamelog/`):

| File | Import |
|---|---|
| `GameLogActionLineSuite.scala` | `{LogKind, LogSpan}` |
| `GameLogConsentSuite.scala` | `{LogEntry, LogTakenBack}` |
| `GameLogDetailSuite.scala` | `{LogKind, LogSpan}` |
| `GameLogEventSuite.scala` | `{LogEntry, LogKind, LogSpan}` |
| `GameLogExchangeSuite.scala` | `{LogKind}` |
| `GameLogFollowUpSuite.scala` | `{LogKind}` |
| `GameLogHeadlineSuite.scala` | `{LogEntry, LogKind, LogSpan}` |
| `GameLogPhaseMenuSuite.scala` | `{LogKind}` |
| `GameLogPowerLinesSuite.scala` | `{LogEntry, LogKind, LogSpan}` |
| `GameLogPropertiesSuite.scala` | `{LogKind, LogSpan}` |
| `GameLogRestoredSuite.scala` | `{LogEntry}` |
| `GameLogSectionSuite.scala` | `{LogEntry}` |
| `GameLogSetupSuite.scala` | `{LogEntry, LogSpan}` |
| `GameLogSilverCharmSuite.scala` | `{LogKind}` |
| `GameLogStartLineSuite.scala` | `{LogEntry, LogKind, LogSpan}` |
| `GameLogTakenBackSuite.scala` | `{LogEntry}` |
| `GoldenLog.scala` | `{LogEntry, LogSpan}` |
| `LogScripts.scala` | `{LogEntry, LogSpan}` |
| `LogWordsSuite.scala` | add `LogSpan` to the existing `LogSpanWire` import |

- [ ] **Step 4: Compile and fix whatever the lists missed**

Run: `./sbtw "Test/compile" "frontend/Test/compile"`
Expected: PASS. A "Not found: LogSpan" (or sibling) names a file the table
above missed; add the import and run again. A `-Wunused:imports` error names
an import the table added that the file does not in fact use; remove it.

- [ ] **Step 5: Run the full gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS, every suite, with no assertion changed.

- [ ] **Step 6: Confirm the goldens did not move**

Run: `git status --porcelain src/test/resources/gamelog`
Expected: no output.

- [ ] **Step 7: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala src/main/scala/oathdigital/application/gamelog src/test/scala/oathdigital/application/gamelog
git commit -m "refactor(gamelog): move the log's types to the shared protocol

They are the types the wire should have. Moving them is the step that
lets the Wire twins go; nothing changes shape here.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: Derive depth from the kind and retire the two `Posted` constructors

The headline/line partition is total and disjoint today: `Posted.headline` is
called only with `Round`, `Turn`, `Phase` and `Victory`; `Posted.line` only
with `Section`, `Action`, `Decision`, `Delta`, `Roll` and `Trigger`
(`PowerLines.kind` returns `Action` or `Trigger`). Moving `depth` onto
`LogKind` makes a wrong depth unwritable.

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/Posted.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala:122,147,154`
- Modify: every file under `src/main/scala/oathdigital/application/gamelog/`
  that calls `Posted.line` or `Posted.headline` (31 and 5 calls, in
  `ActionLines`, `CampaignLines`, `ConsentLines`, `DetailLines`, `EventLines`,
  `GameLogFormatter`, `NegotiationLines`, `PowerLines`, `SetupLines`,
  `StartLines`)

**Interfaces:**
- Consumes: the moved types from Task 1.
- Produces: `LogKind.depth: Int`; `LogEntry(sequence, ordinal, kind, spans,
  section, takenBack)` with `def depth: Int = kind.depth`;
  `Posted(kind, spans, section)` with no companion object.

- [ ] **Step 1: Write the failing test**

Add to `src/test/scala/oathdigital/application/gamelog/GameLogHeadlineSuite.scala`:

```scala
  test("a kind carries its own depth, and headlines never carry a section"):
    assertEquals(LogKind.values.filter(_.depth == 0).toSet,
      Set(LogKind.Round, LogKind.Turn, LogKind.Phase, LogKind.Victory))
    assertEquals(LogKind.values.filter(_.depth == 1).toSet,
      Set(LogKind.Section, LogKind.Action, LogKind.Decision, LogKind.Roll,
        LogKind.Delta, LogKind.Trigger))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogHeadlineSuite"`
Expected: FAIL to compile, "value depth is not a member of
oathdigital.protocol.projection.LogKind".

- [ ] **Step 3: Give `LogKind` a depth and `LogEntry` a derived one**

In `LogProjectionDtos.scala`, keep the case order and add the second
parameter:

```scala
/** `key` is the kind's wire spelling, and `depth` where its entries sit: 0
  * is a headline, 1 a line. The kind decides it, so no caller can post a
  * headline at a line's depth and no wire can carry a disagreeing pair. */
enum LogKind(val key: String, val depth: Int):
  case Round extends LogKind("round", 0)
  case Turn extends LogKind("turn", 0)
  /** Wake, Act or Rest, under its turn's headline. */
  case Phase extends LogKind("phase", 0)
  /** The tag that opens an action's band: its name and the Supply it spent
    * (log action sections design, "The tag entry"). */
  case Section extends LogKind("section", 1)
  case Action extends LogKind("action", 1)
  case Decision extends LogKind("decision", 1)
  case Roll extends LogKind("roll", 1)
  case Delta extends LogKind("delta", 1)
  case Trigger extends LogKind("trigger", 1)
  case Victory extends LogKind("victory", 0)
```

and drop `LogEntry`'s `depth` field for a derived one:

```scala
final case class LogEntry(sequence: Long, ordinal: Int, kind: LogKind,
    spans: Vector[LogSpan], section: Option[LogSection] = None,
    takenBack: Option[LogTakenBack] = None):
  def depth: Int = kind.depth
```

- [ ] **Step 4: Collapse `Posted`**

`src/main/scala/oathdigital/application/gamelog/Posted.scala` becomes:

```scala
package oathdigital.application.gamelog

import oathdigital.protocol.projection.{LogKind, LogSection, LogSpan}

/** An entry before it has a key: what one event posts, in order. Its depth
  * is its kind's, so there is one constructor and no way to post a headline
  * as a line. */
private[gamelog] final case class Posted(kind: LogKind,
    spans: Vector[LogSpan], section: Option[LogSection] = None)
```

- [ ] **Step 5: Rewrite the 36 call sites**

```bash
cd src/main/scala/oathdigital/application/gamelog
perl -pi -e 's/Posted\.(line|headline)\(/Posted(/g' *.scala
cd -
```

Then check nothing else matched:

```bash
grep -rn 'Posted\.line\|Posted\.headline' src/main/scala src/test/scala
```
Expected: no output.

- [ ] **Step 6: Fix the three places in `GameLogFormatter` that read `depth`**

Line 122 drops the argument:

```scala
          LogEntry(journal.sequence(at), ordinal, entry.kind,
            entry.spans, entry.section)
```

`quietWake` compares on kind and spans only:

```scala
  private def quietWake(last: Option[LogEntry], posted: Vector[Posted])
      : Vector[Posted] =
    val previous = last.map(entry => (entry.kind, entry.spans))
    posted.foldLeft((previous, Vector.empty[Posted])) {
      case ((before, done), entry) =>
        val quiet = entry == phaseHeadline(Phase.Act) &&
          before.contains((LogKind.Phase, phaseWords(Phase.Wake)))
        val lines = if quiet then Vector(Posted(LogKind.Action,
          Vector(Text("Nothing happened in Wake"))), entry) else Vector(entry)
        (Some((entry.kind, entry.spans)), done ++ lines)
    }._2
```

`GameLogProjector.wire` still reads `entry.depth`; it now reads the derived
`def` and needs no change.

- [ ] **Step 7: Run the new test and the full gate**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogHeadlineSuite"`
Expected: PASS.

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS. The wire's JSON still carries `depth`
(`GameLogProjector.wire` passes `entry.depth` into `LogEntryWire`), so
`LogPageCodecSuite` and `HttpGameClientSuite` are untouched.

- [ ] **Step 8: Confirm the goldens did not move, then commit**

`GoldenLog.render` prints `entry.depth`, which still resolves.

```bash
git status --porcelain src/test/resources/gamelog
git add shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala src/main/scala/oathdigital/application/gamelog src/test/scala/oathdigital/application/gamelog
git commit -m "refactor(gamelog): a log kind carries its own depth

Posted.headline and Posted.line were the only thing keeping the two in
step. The kind decides the depth now, so a wrong one cannot be written.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: Drop `Cost.unit`

Both construction sites pass the literal `"Supply"`. A field with one possible
value is not one of the case's fields; the word moves into `text`.

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/StartLines.scala:57,186`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogProjector.scala:38`
- Modify: `src/test/scala/oathdigital/application/gamelog/GoldenLog.scala:21`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogWordsSuite.scala:37`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala:30`

**Interfaces:**
- Consumes: Task 2's types.
- Produces: `LogSpan.Cost(value: Int)` with `text = s"−$value Supply"`.

- [ ] **Step 1: Write the failing test**

Add to `src/test/scala/oathdigital/application/gamelog/LogWordsSuite.scala`:

```scala
  test("a cost names the one currency an action spends"):
    assertEquals(LogSpan.Cost(2).text, "−2 Supply")
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.LogWordsSuite"`
Expected: FAIL to compile, "missing argument for parameter unit".

- [ ] **Step 3: Narrow the case**

In `LogProjectionDtos.scala`:

```scala
  /** What an action spent, drawn apart from the sentence (game log design,
    * "Supply"). Supply is the only thing an action spends, so the word is
    * the case's, not a field: a second currency is a one-parameter change
    * the compiler routes to every site. */
  final case class Cost(value: Int) extends LogSpan:
    def text: String = s"−$value Supply"
```

- [ ] **Step 4: Update the four callers and the two assertions**

`StartLines.scala:57` and `:186` drop the argument:

```scala
          Vector(Text("Continued"), LogSpan.Cost(spent))))
```
```scala
    if spent > 0 then Vector(LogSpan.Cost(spent)) else Vector.empty
```

`GameLogProjector.scala:38` keeps the wire's `unit` field, which is still
`LogSpanWire`'s, and supplies the word:

```scala
    case cost @ LogSpan.Cost(count) =>
      LogSpanWire("cost", cost.text, value = Some(count),
        unit = Some("Supply"))
```

`GoldenLog.scala:21` renders the same string without a second table:

```scala
    case cost: LogSpan.Cost => s"[cost:${cost.text.stripPrefix("−")}]"
```

`LogWordsSuite.scala:37`:

```scala
    assertEquals(GameLogProjector.span(LogSpan.Cost(2)),
      LogSpanWire("cost", "−2 Supply", value = Some(2), unit = Some("Supply")))
```

`GameLogStartLineSuite.scala:30` loses the `unit` assertion; delete this line
and nothing else:

```scala
    assertEquals(cost.head.unit, "Supply")
```

- [ ] **Step 5: Run the test and the full gate**

Run: `./sbtw "testOnly oathdigital.application.gamelog.LogWordsSuite"`
Expected: PASS.

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS.

- [ ] **Step 6: Confirm the goldens did not move, then commit**

`GoldenLog` prints `[cost:1 Supply]` exactly as before, from
`text.stripPrefix("−")`.

```bash
git status --porcelain src/test/resources/gamelog
git add shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala src/main/scala/oathdigital/application/gamelog src/test/scala/oathdigital/application/gamelog
git commit -m "refactor(gamelog): a cost's currency is the case's, not a field

Both sites pass \"Supply\" and always have.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: Replace the wire with the sealed family

The one atomic step: the `Wire` twins, the mapping functions and the flat
codec go together, and the frontend, the server routes and the suites move
onto the moved types in the same compile.

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/LogPageCodec.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogProjector.scala`
- Modify: `src/main/scala/oathdigital/application/GameProjection.scala:32`
- Modify: `src/main/scala/oathdigital/server/GameRoutes.scala:23,76,143`
- Modify: `src/main/scala/oathdigital/server/TrustedGameGateway.scala:6,32`
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameClient.scala:10,115,205`
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableSession.scala:4,42,305`
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableScreen.scala:22`
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala:165`
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameLogOverlay.scala:4,44`
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala`
- Test: `shared/src/test/scala/oathdigital/protocol/LogPageCodecSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/TestLog.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala`,
  `GameLogTakenBackPaneSuite.scala`, `GameLogWithdrawnSuite.scala`,
  `GameLogOverlaySuite.scala`, `TableSessionSuite.scala`,
  `HttpGameClientSuite.scala`
- Test: `src/test/scala/oathdigital/application/gamelog/LogWordsSuite.scala`

**Interfaces:**
- Consumes: Task 3's `LogSpan`, `LogEntry`, `LogKind`, `LogSection`,
  `LogTakenBack`.
- Produces: `LogPage(gameId: String, after: Long, nextSequence: Long,
  entries: Vector[LogEntry])`; `LogPageCodec.encode(LogPage): String` and
  `LogPageCodec.decode(String): Either[ProtocolDecodeFailure, LogPage]`.
  `GameLogProjector.page` returns `Option[LogPage]`.
  `GameClient.loadLog` returns `Future[Either[GameClientFailure, LogPage]]`.
  `TableSession.viewedLog: Vector[LogEntry]`.

- [ ] **Step 1: Write the failing codec tests**

Replace the whole of
`shared/src/test/scala/oathdigital/protocol/LogPageCodecSuite.scala`:

```scala
package oathdigital.protocol

import oathdigital.protocol.projection._

class LogPageCodecSuite extends munit.FunSuite:
  private val page = LogPage("game", 4L, 9L, Vector(
    LogEntry(4L, 0, LogKind.Turn, Vector(
      LogSpan.Player("red", "Red"), LogSpan.Text("'s turn"))),
    LogEntry(6L, 1, LogKind.Action, Vector(
      LogSpan.Text("Started Travel"), LogSpan.Cost(2)))))

  test("a page round-trips"):
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(page)), Right(page))

  test("every span case round-trips"):
    val spans = Vector[LogSpan](LogSpan.Text("words"),
      LogSpan.Player("red", "Red"), LogSpan.Card("c1", "Old Oak"),
      LogSpan.Site("s1", "Buried Giant"), LogSpan.Amount(3, "favor"),
      LogSpan.Cost(2),
      LogSpan.Dice("attack", Vector("one-sword"), Vector("sword")),
      LogSpan.Cards(Vector(
        CardDetailsProjection("19", "denizen", "Scryer", Some("discord")))))
    val all = LogPage("g", 0, 9,
      Vector(LogEntry(7, 0, LogKind.Action, spans)))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(all)), Right(all))

  test("a text span is the whole of its object: a kind and its words"):
    val json = ujson.read(LogPageCodec.encode(LogPage("g", 0, 9,
      Vector(LogEntry(7, 0, LogKind.Turn, Vector(LogSpan.Text("'s turn")))))))
    assertEquals(json("entries")(0)("spans")(0).obj.keySet.toSet,
      Set("kind", "text"))

  test("an entry carries no depth: its kind decides it"):
    val json = ujson.read(LogPageCodec.encode(page))
    assertEquals(json("entries")(0).obj.keySet.toSet,
      Set("sequence", "ordinal", "kind", "spans", "section", "takenBack"))

  test("a span kind this build does not know is an unknown variant"):
    val json = LogPageCodec.encode(page).replace("\"kind\":\"player\"",
      "\"kind\":\"omen\"")
    assertEquals(LogPageCodec.decode(json),
      Left(ProtocolDecodeFailure.UnknownVariant("$.entries[0].spans[0].kind",
        "unknown log span kind 'omen'")))

  test("an entry kind this build does not know is an unknown variant"):
    val json = LogPageCodec.encode(page).replace("\"kind\":\"turn\"",
      "\"kind\":\"interlude\"")
    assertEquals(LogPageCodec.decode(json),
      Left(ProtocolDecodeFailure.UnknownVariant("$.entries[0].kind",
        "unknown log entry kind 'interlude'")))

  test("a field another span case declares is rejected at its path"):
    val json = LogPageCodec.encode(page).replace("\"text\":\"'s turn\"",
      "\"text\":\"'s turn\",\"value\":1")
    assertEquals(LogPageCodec.decode(json).left.map(_.path),
      Left("$.entries[0].spans[1].value"))

  test("an empty card list is rejected: no line builds one"):
    val empty = LogPage("g", 0, 9, Vector(LogEntry(7, 0, LogKind.Action,
      Vector(LogSpan.Cards(Vector.empty)))))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(empty)).left.map(
      _.path), Left("$.entries[0].spans[0].cards"))

  test("a section on a headline kind is rejected"):
    val json = LogPageCodec.encode(LogPage("g", 0, 9, Vector(
      LogEntry(6, 0, LogKind.Section, Vector(LogSpan.Text("Search")),
        Some(LogSection(6, "search"))))))
      .replace("\"kind\":\"section\"", "\"kind\":\"turn\"")
    assertEquals(LogPageCodec.decode(json),
      Left(ProtocolDecodeFailure.InvalidValue("$.entries[0].section",
        "only a line can carry a section")))

  test("an entry's section round-trips, and an entry without one writes null"):
    val banded = LogPage("g", 0, 9, Vector(
      LogEntry(6, 0, LogKind.Section, Vector(LogSpan.Text("Search")),
        Some(LogSection(6, "search"))),
      LogEntry(7, 0, LogKind.Turn, Vector(LogSpan.Text("Red's turn")))))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(banded)),
      Right(banded))
    val json = ujson.read(LogPageCodec.encode(banded))
    assertEquals(json("entries")(0)("section"),
      ujson.Obj("id" -> ujson.Num(6), "action" -> ujson.Str("search")))
    assertEquals(json("entries")(1)("section"), ujson.Null)

  test("an entry written before sections or groups decodes with none"):
    val json = """{"gameId":"g","after":0,"nextSequence":9,"entries":""" +
      """[{"sequence":7,"ordinal":0,"kind":"turn","spans":[]}]}"""
    assertEquals(LogPageCodec.decode(json).map(_.entries.map(entry =>
      entry.section -> entry.takenBack)), Right(Vector(None -> None)))

  test("an entry's taken-back group round-trips with its header"):
    val voided = LogPage("g", 0, 9, Vector(LogEntry(6, 0, LogKind.Action,
      Vector(LogSpan.Text("Travelled")),
      takenBack = Some(LogTakenBack(4L, Vector(
        LogSpan.Player("p1", "p1"),
        LogSpan.Text(" took back the following moves:")))))))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(voided)),
      Right(voided))

  test("a section with an unexpected field is rejected at its path"):
    val banded = page.copy(entries = Vector(page.entries.head.copy(
      kind = LogKind.Action, section = Some(LogSection(4, "travel")))))
    val json = LogPageCodec.encode(banded).replace("\"action\":\"travel\"",
      "\"action\":\"travel\",\"extra\":1")
    assertEquals(LogPageCodec.decode(json).left.map(_.path),
      Left("$.entries[0].section.extra"))

  test("malformed JSON is a decode failure that names the parser's clue"):
    // ujson wraps its parse error in a trace whose own message is only the
    // JSON path; the clue is the wrapped error's.
    assertEquals(LogPageCodec.decode("{"),
      Left(ProtocolDecodeFailure.MalformedJson("$", "exhausted input")))

  test("a card list span round-trips its faces and backs"):
    val list = LogPage("g", 0, 9, Vector(LogEntry(7, 0, LogKind.Action,
      Vector(LogSpan.Cards(Vector(
        CardDetailsProjection("19", "denizen", "Scryer", Some("discord")),
        CardDetailsProjection("hidden", "vision", "Facedown vision",
          orientation = Some("face-down"), hidden = true)))))))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(list)), Right(list))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.protocol.LogPageCodecSuite"`
Expected: FAIL to compile, "Not found: type LogPage".

- [ ] **Step 3: Delete the Wire twins and rename the page**

In `LogProjectionDtos.scala`, delete `LogSpanWire`, `LogSectionWire`,
`LogEntryWire` and `LogTakenBackWire` with their doc comments, and rename the
page:

```scala
/** Every entry with `sequence >= after`, formatted for one seat, and the
  * cursor to ask with next time. */
final case class LogPage(gameId: String, after: Long, nextSequence: Long,
    entries: Vector[LogEntry])
```

- [ ] **Step 4: Rewrite `LogPageCodec`**

Replace the whole file:

```scala
package oathdigital.protocol.projection

import scala.util.control.NonFatal

import oathdigital.protocol.ProtocolDecodeFailure
import oathdigital.protocol.ProtocolDecodeFailure.{InvalidValue, MalformedJson,
  UnknownVariant}
import ProjectionCodecSupport._

/** The log page's wire form. One encoder and one decoder arm per span case,
  * each with its own `exact` set, so a field another case declares is an
  * unexpected field here rather than an ignored one.
  *
  * A kind this build does not know is an `UnknownVariant`, not a span
  * rendered as its text. The frontend ships inside the server's own
  * resources (`build.sbt` copies `fullLinkJS`'s output into
  * `resourceManaged`), so "a newer server's span reaching an older page" is
  * not a state this system reaches; what remains is a browser holding a
  * stale cached bundle, which the client reports and offers to reload.
  * Tolerating the kind instead would cost an `Unknown` case that every
  * downstream match carries forever
  * (`docs/superpowers/specs/2026-10-15-typed-log-span-wire-design.md`).
  */
object LogPageCodec:
  private val PageFields = Set("gameId", "after", "nextSequence", "entries")
  private val EntryFields = Set("sequence", "ordinal", "kind", "spans",
    "section", "takenBack")
  private val SectionFields = Set("id", "action")
  private val TakenBackFields = Set("group", "header")

  def encode(value: LogPage): String = ujson.write(ujson.Obj(
    "gameId" -> value.gameId,
    "after" -> ujson.Num(value.after.toDouble),
    "nextSequence" -> ujson.Num(value.nextSequence.toDouble),
    "entries" -> encoded(value.entries)(encodeEntry)))

  def decode(json: String): Either[ProtocolDecodeFailure, LogPage] =
    try decodePage(ujson.read(json), "$")
    catch { case NonFatal(error) => Left(MalformedJson.of(error)) }

  private def encodeEntry(entry: LogEntry): ujson.Value = ujson.Obj(
    "sequence" -> ujson.Num(entry.sequence.toDouble),
    "ordinal" -> entry.ordinal, "kind" -> entry.kind.key,
    "spans" -> encoded(entry.spans)(encodeSpan),
    "section" -> option(entry.section)(section => ujson.Obj(
      "id" -> ujson.Num(section.id.toDouble), "action" -> section.action)),
    "takenBack" -> option(entry.takenBack)(group => ujson.Obj(
      "group" -> ujson.Num(group.group.toDouble),
      "header" -> encoded(group.header)(encodeSpan))))

  /** No `text`: every case computes it, on both sides of the wire. */
  private def encodeSpan(span: LogSpan): ujson.Value = span match
    case LogSpan.Text(value) => ujson.Obj("kind" -> "text", "text" -> value)
    case LogSpan.Player(id, name) =>
      ujson.Obj("kind" -> "player", "id" -> id, "name" -> name)
    case LogSpan.Card(id, name) =>
      ujson.Obj("kind" -> "card", "id" -> id, "name" -> name)
    case LogSpan.Site(id, name) =>
      ujson.Obj("kind" -> "site", "id" -> id, "name" -> name)
    case LogSpan.Amount(value, unit) =>
      ujson.Obj("kind" -> "amount", "value" -> value, "unit" -> unit)
    case LogSpan.Cost(value) => ujson.Obj("kind" -> "cost", "value" -> value)
    case LogSpan.Dice(die, faces, names) => ujson.Obj("kind" -> "dice",
      "die" -> die, "faces" -> encoded(faces)(ujson.Str(_)),
      "names" -> encoded(names)(ujson.Str(_)))
    case LogSpan.Cards(cards) => ujson.Obj("kind" -> "cards",
      "cards" -> encoded(cards)(WorldProjectionCodec.encodeCard))

  private def decodePage(raw: ujson.Value, path: String): Result[LogPage] =
    for
      value <- obj(raw, path)
      _ <- exact(value, PageFields, path)
      gameId <- string(value, "gameId", path)
      after <- long(value, "after", path)
      next <- long(value, "nextSequence", path)
      rawEntries <- array(value, "entries", path)
      entries <- traverse(rawEntries, s"$path.entries")(decodeEntry)
    yield LogPage(gameId, after, next, entries)

  private def decodeEntry(raw: ujson.Value, path: String): Result[LogEntry] =
    for
      value <- obj(raw, path)
      _ <- exact(value, EntryFields, path)
      sequence <- long(value, "sequence", path)
      ordinal <- int(value, "ordinal", path)
      kind <- decodeKind(value, path)
      rawSpans <- array(value, "spans", path)
      spans <- traverse(rawSpans, s"$path.spans")(decodeSpan)
      section <- optionalAbsent(value, "section", path)(decodeSection)
      _ <- Either.cond(section.isEmpty || kind.depth == 1, (),
        InvalidValue(s"$path.section", "only a line can carry a section"))
      takenBack <- optionalAbsent(value, "takenBack", path)(decodeTakenBack)
    yield LogEntry(sequence, ordinal, kind, spans, section, takenBack)

  private def decodeKind(value: ujson.Obj, path: String): Result[LogKind] =
    string(value, "kind", path).flatMap(key =>
      LogKind.values.find(_.key == key).toRight(UnknownVariant(
        s"$path.kind", s"unknown log entry kind '$key'")))

  private def decodeTakenBack(raw: ujson.Value, path: String)
      : Result[LogTakenBack] =
    for
      value <- obj(raw, path)
      _ <- exact(value, TakenBackFields, path)
      group <- long(value, "group", path)
      rawHeader <- array(value, "header", path)
      header <- traverse(rawHeader, s"$path.header")(decodeSpan)
    yield LogTakenBack(group, header)

  private def decodeSection(raw: ujson.Value, path: String)
      : Result[LogSection] =
    for
      value <- obj(raw, path)
      _ <- exact(value, SectionFields, path)
      id <- long(value, "id", path)
      action <- string(value, "action", path)
    yield LogSection(id, action)

  /** Reads the discriminator, then hands the object to the one decoder that
    * knows that case's fields. */
  private def decodeSpan(raw: ujson.Value, path: String): Result[LogSpan] =
    for
      value <- obj(raw, path)
      kind <- string(value, "kind", path)
      span <- kind match
        case "text" => decodeText(value, path)
        case "player" => decodeNamed(value, path, LogSpan.Player.apply)
        case "card" => decodeNamed(value, path, LogSpan.Card.apply)
        case "site" => decodeNamed(value, path, LogSpan.Site.apply)
        case "amount" => decodeAmount(value, path)
        case "cost" => decodeCost(value, path)
        case "dice" => decodeDice(value, path)
        case "cards" => decodeCards(value, path)
        case other => Left(UnknownVariant(s"$path.kind",
          s"unknown log span kind '$other'"))
    yield span

  private def decodeText(value: ujson.Obj, path: String): Result[LogSpan] =
    for
      _ <- exact(value, Set("kind", "text"), path)
      text <- string(value, "text", path)
    yield LogSpan.Text(text)

  private def decodeNamed(value: ujson.Obj, path: String,
      make: (String, String) => LogSpan): Result[LogSpan] =
    for
      _ <- exact(value, Set("kind", "id", "name"), path)
      id <- string(value, "id", path)
      name <- string(value, "name", path)
    yield make(id, name)

  private def decodeAmount(value: ujson.Obj, path: String): Result[LogSpan] =
    for
      _ <- exact(value, Set("kind", "value", "unit"), path)
      amount <- int(value, "value", path)
      unit <- string(value, "unit", path)
    yield LogSpan.Amount(amount, unit)

  private def decodeCost(value: ujson.Obj, path: String): Result[LogSpan] =
    for
      _ <- exact(value, Set("kind", "value"), path)
      amount <- int(value, "value", path)
    yield LogSpan.Cost(amount)

  private def decodeDice(value: ujson.Obj, path: String): Result[LogSpan] =
    for
      _ <- exact(value, Set("kind", "die", "faces", "names"), path)
      die <- string(value, "die", path)
      faces <- strings(value, "faces", path)
      names <- strings(value, "names", path)
    yield LogSpan.Dice(die, faces, names)

  /** `LogWords.listed` builds a `Cards` span only past the inline threshold
    * and writes a `Text` span when the viewer may identify none of them, so
    * an empty list is not a span this server can send. */
  private def decodeCards(value: ujson.Obj, path: String): Result[LogSpan] =
    for
      _ <- exact(value, Set("kind", "cards"), path)
      raw <- array(value, "cards", path)
      cards <- traverse(raw, s"$path.cards")(WorldProjectionCodec.decodeCard)
      _ <- Either.cond(cards.nonEmpty, (),
        InvalidValue(s"$path.cards", "expected at least one card"))
    yield LogSpan.Cards(cards)
```

- [ ] **Step 5: Strip `GameLogProjector` to its page assembly**

```scala
package oathdigital.application.gamelog

import oathdigital.application.{GameHistory, GamePresentationProjector}
import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.PlayerId
import oathdigital.protocol.projection.LogPage

/** The log page one seat reads: the whole prefix formatted for that seat,
  * then every entry at or after `after` (game log design, "Routes" and
  * "Cost"). The formatter's entries are the wire's, so this projector maps
  * nothing. */
private[application] final class GameLogProjector(catalog: ExecutableCatalog,
    presentation: GamePresentationProjector):
  private val formatter = new GameLogFormatter(catalog, presentation)

  def page(gameId: String, history: GameHistory, after: Long,
      viewer: PlayerId): Option[LogPage] =
    Option.when(after >= 0 && after <= history.nextSequence)(LogPage(
      gameId, after, history.nextSequence,
      formatter.format(history.steps, Some(viewer))
        .filter(_.sequence >= after)))
```

- [ ] **Step 6: Rename the type at every remaining `LogPageWire` site**

```bash
grep -rln 'LogPageWire' src frontend shared | xargs perl -pi -e 's/\bLogPageWire\b/LogPage/g'
grep -rn 'LogPageWire\|LogSpanWire\|LogEntryWire\|LogSectionWire\|LogTakenBackWire' src frontend shared
```
Expected from the second command: only the frontend and test files Steps 7-9
rewrite.

- [ ] **Step 7: Point `GameLogPane` at the sealed family**

Change the import to
`import oathdigital.protocol.projection.{LogEntry, LogSpan, LogTakenBack}`
(not `LogKind`: the pane reads the kind's `key` and never names the type, and
`-Wunused:imports` would fail the build),
replace `LogEntryWire` with `LogEntry`, `LogSpanWire` with `LogSpan` and
`LogTakenBackWire` with `LogTakenBack` throughout, read the kind's key where a
string is wanted, and rewrite the span renderer. `entry.depth` still resolves,
as the derived `def`.

Line 200 and lines 229-236 read the key:

```scala
    if entry.depth == 0 then headline(node, entry.kind.key) else node
```
```scala
    val depth = if entry.depth == 0 then "log-headline" else "log-line"
    val classes = Vector("log-entry", depth, s"log-${entry.kind.key}") ++
      band(entry, start, end)
```

The renderer loses its fallback, so a new span case fails compilation here:

```scala
  private def spanNode(span: LogSpan, colors: Map[String, String])
      : dom.Node = span match
    case LogSpan.Player(id, name) => text("span", "log-player player-ref " +
      colors.getOrElse(id, PlayerColorCss.neutral), name)
    case card: LogSpan.Card => text("span", "log-card", card.text)
    case site: LogSpan.Site => text("span", "log-site", site.text)
    case amount: LogSpan.Amount => text("span", "log-amount", amount.text)
    case cost: LogSpan.Cost => text("span", "log-cost", cost.text)
    // The same die chips the table draws.
    case dice: LogSpan.Dice =>
      val row = DieFace.roll(dice.faces, Some(dice.die))
      row.setAttribute("class", s"die-faces log-dice log-dice-${dice.die}")
      row
    // More cards than a line names: a control that opens them as a card
    // list (catalog batch 2, N5).
    case list: LogSpan.Cards =>
      val open = button(list.text, "log-cards")
      open.onclick = _ => CardInspection.openCards(list.text, list.cards, open)
      open
    case LogSpan.Text(value) => dom.document.createTextNode(value)
```

The `case "cards" if span.cards.nonEmpty` guard is gone: the decoder rejects
an empty list, so the branch it guarded was unreachable.

- [ ] **Step 8: Retype the frontend's log plumbing**

In each of `GameClient.scala`, `TableSession.scala`, `TableScreen.scala`,
`GameTableShell.scala` and `GameLogOverlay.scala`, replace `LogEntryWire` with
`LogEntry` (and `LogPageWire` with `LogPage`, already done by Step 6) and fix
the import braces. `GameClient.scala:10` becomes:

```scala
import oathdigital.protocol.projection.{GameProjection, GameProjectionCodec,
  LogPage, LogPageCodec}
```

- [ ] **Step 9: Retype the frontend suites**

`TestLog.scala` loses its `depth` parameter and takes a typed kind:

```scala
package oathdigital.frontend

import oathdigital.protocol.projection.{LogEntry, LogKind, LogSection,
  LogSpan, LogTakenBack}
import org.scalajs.dom
import scala.scalajs.js

/** Builders for the log lines and the content box the log suites draw. */
private[frontend] object TestLog:
  /** A log entry with every field named at the call. Its depth is its
    * kind's. */
  def entry(sequence: Long, kind: LogKind, spans: Vector[LogSpan],
      section: Option[LogSection] = None,
      takenBack: Option[LogTakenBack] = None): LogEntry =
    LogEntry(sequence = sequence, ordinal = 0, kind = kind, spans = spans,
      section = section, takenBack = takenBack)

  /** A log entry that reads `text` as one plain span. */
  def textEntry(sequence: Long, kind: LogKind, text: String,
      section: Option[LogSection] = None,
      takenBack: Option[LogTakenBack] = None): LogEntry =
    entry(sequence, kind, Vector(LogSpan.Text(text)), section, takenBack)
```

(`sizedBox` is unchanged; keep it below.)

Then update the callers. Every `depth` argument goes; every kind string
becomes a `LogKind`; every `LogSpanWire(...)` becomes its case. The
substitutions, in full:

- `GameLogOverlaySuite.scala:11-12`:
  ```scala
    private def entry(sequence: Long, kind: LogKind, text: String) =
      TestLog.textEntry(sequence, kind, text)
  ```
  and its three uses become `entry(0, LogKind.Round, "Setup")`,
  `entry(3, LogKind.Turn, "Red's turn")`,
  `entry(5, LogKind.Action, "Started Travel")`,
  `entry(8, LogKind.Action, "Started Search")`.
- `GameLogPaneSuite.scala`: the local `entry` helper drops `depth`; `setup`,
  `turn`, `travel`, `sectioned` and the `roll`, `listed`, `plain`, `few`,
  `search` and victory entries take `LogKind` values and `LogSpan` cases, for
  example
  ```scala
    private def entry(sequence: Long, kind: LogKind, spans: LogSpan*) =
      TestLog.entry(sequence, kind, spans.toVector)

    private val setup = entry(0, LogKind.Round, LogSpan.Text("Setup"))
    private val turn = entry(3, LogKind.Turn,
      LogSpan.Player("red", "Red"), LogSpan.Text("'s turn"))
    private val travel = entry(5, LogKind.Action,
      LogSpan.Text("Started Travel"), LogSpan.Cost(2))
  ```
  and the dice test's entry
  ```scala
      val roll = entry(7, LogKind.Roll, LogSpan.Text("Rolled "),
        LogSpan.Dice("attack", Vector("one-sword", "blank"),
          Vector("sword", "blank")), LogSpan.Text(" for the attack"))
  ```
  Its assertions do not move in this task: `faces(0)` still reads
  `aria-label` "one sword", because the frontend's own table is still in
  place until Task 7.
- `GameLogTakenBackPaneSuite.scala`, `GameLogWithdrawnSuite.scala`: the same
  substitution, with `LogKind.Action`, `LogKind.Decision`, `LogKind.Round`
  and `LogSection`/`LogTakenBack`/`LogSpan` in place of the `Wire` names.
- `TableSessionSuite.scala:395-397`:
  ```scala
    private def page(after: Long, next: Long): LogPage =
      LogPage("g", after, next, (after until next).toVector.map(sequence =>
        TestLog.textEntry(sequence, LogKind.Action, s"line $sequence")))
  ```
  and `pageTakenBackFrom` builds `LogTakenBack(from,
  Vector(LogSpan.Text("Red took back the following moves:")))`. Add
  `LogKind`, `LogSpan`, `LogTakenBack` to its projection import.
- `HttpGameClientSuite.scala:259`: drop `"depth":0` from the fixture string.

- [ ] **Step 10: Drop the dead assertion in `LogWordsSuite`**

`GameLogProjector.span` no longer exists. Delete the whole test "every span
kind has its wire form; only typed spans carry ids and amounts" (lines 27-38)
and the now-unused `import oathdigital.protocol.projection.LogSpanWire`. The
span shapes are covered by `LogPageCodecSuite`'s round trips.

- [ ] **Step 11: Run the codec suite, then the full gate**

Run: `./sbtw "testOnly oathdigital.protocol.LogPageCodecSuite"`
Expected: PASS, 15 tests.

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS.

- [ ] **Step 12: Confirm the goldens did not move, then commit**

```bash
git status --porcelain src/test/resources/gamelog
git add shared src frontend
git commit -m "refactor(gamelog): the log's wire is the formatter's types

Deletes the four Wire twins and both mapping functions, renames
LogPageWire to LogPage, and gives LogPageCodec one arm per span case with
its own exact field set. text and depth leave the wire; a kind this build
does not know is now an UnknownVariant rather than a span rendered as its
text.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: Add the die face vocabulary and guard the table that stays apart

Seven faces are spelled in four tables. This task adds the one that will
replace three of them, and pins the fourth
(`WalkerDecisionProjector.faceName`, which serves the decision wire and stays
out of scope) to it with a set-equality test.

**Files:**
- Create: `shared/src/main/scala/oathdigital/protocol/projection/DieFaces.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala:634,640-654`
- Test: `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `oathdigital.protocol.projection.Die` with
  `Die.Attack`/`Die.Defense` and `val key: String`;
  `oathdigital.protocol.projection.DieFace` with seven cases and
  `val key: String`, `val word: String`, `val die: Die`.
  `WalkerDecisionProjector.faceName(value: oathdigital.model.DieFace):
  Vector[String]` moves to the companion object as `private[application]`.

- [ ] **Step 1: Write the failing test**

Add to `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala`:

```scala
  /** The decision wire keeps its own face table
    * (`WalkerDecisionProjector.faceName`), because the application layer may
    * not import the serialization layer and widening the typed log slice to
    * a second wire was not in it. A test holds the two together instead of a
    * shared constant: a failure names the divergence and leaves the fix to a
    * human (2026-09-25-decision-form-tag-separation-decision.md).
    */
  test("the decision wire spells every face exactly as the shared vocabulary"):
    val projected = (AttackDieFace.values ++ DefenseDieFace.values)
      .flatMap(WalkerDecisionProjector.faceName).toSet
    assertEquals(projected,
      oathdigital.protocol.projection.DieFace.values.map(_.key).toSet)
```

Add `import oathdigital.model.{AttackDieFace, DefenseDieFace}` if the suite's
`oathdigital.model._` import does not already cover it.

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: FAIL to compile, "value faceName is not a member of object
WalkerDecisionProjector".

- [ ] **Step 3: Create the shared vocabulary**

`shared/src/main/scala/oathdigital/protocol/projection/DieFaces.scala`:

```scala
package oathdigital.protocol.projection

/** The two dice a roll is made on. */
enum Die(val key: String):
  case Attack extends Die("attack")
  case Defense extends Die("defense")

/** The seven faces, with the one spelling each side needs: `key` on the
  * wire, `word` on the page, and the `die` it is printed on.
  *
  * One table for all three. The log used to carry a face's wire name and its
  * display name as two parallel arrays, and the client kept a third table of
  * display names which had drifted from the server's: the log sentence said
  * "sword" where the die chip's label said "one sword". These words are the
  * server's, which are the internally consistent set -- "sword" is parallel
  * with "two swords and a skull", "shield" with "two shields".
  *
  * The decision wire (`WalkerDecisionProjector.faceName`) still spells its
  * faces itself, because the application layer may not import the
  * serialization layer; a set-equality test in
  * `WalkerDecisionProjectorSuite` holds it to this enum's keys.
  */
enum DieFace(val key: String, val word: String, val die: Die):
  case HollowSword extends DieFace("hollow-sword", "hollow sword", Die.Attack)
  case OneSword extends DieFace("one-sword", "sword", Die.Attack)
  case TwoSwordsSkull
      extends DieFace("two-swords-skull", "two swords and a skull", Die.Attack)
  case Blank extends DieFace("blank", "blank", Die.Defense)
  case OneShield extends DieFace("one-shield", "shield", Die.Defense)
  case TwoShields extends DieFace("two-shields", "two shields", Die.Defense)
  case Doubler extends DieFace("doubler", "doubler", Die.Defense)
```

- [ ] **Step 4: Move `faceName` to the companion**

Delete the private method from the class body of `WalkerDecisionProjector`
(lines 640-654, comment included) and add it to
`private[application] object WalkerDecisionProjector`, unchanged except for
its visibility:

```scala
  /** Local duplicate of `WalkerEventCodec`'s (serialization-layer) face
    * vocabulary: the application layer may not import the serialization
    * layer (`BackendArchitectureSuite`). `oathdigital.model.DieFace` is
    * open, so a face family neither pool rolls spells as nothing rather
    * than as a guess. Held to
    * `oathdigital.protocol.projection.DieFace`'s keys by a test in
    * `WalkerDecisionProjectorSuite`.
    */
  private[application] def faceName(value: DieFace): Vector[String] =
    value match
      case DefenseDieFace.Blank => Vector("blank")
      case DefenseDieFace.OneShield => Vector("one-shield")
      case DefenseDieFace.TwoShields => Vector("two-shields")
      case DefenseDieFace.Doubler => Vector("doubler")
      case AttackDieFace.HollowSword => Vector("hollow-sword")
      case AttackDieFace.OneSword => Vector("one-sword")
      case AttackDieFace.TwoSwordsSkull => Vector("two-swords-skull")
      case _ => Vector.empty
```

The one caller, line 634, becomes
`.flatMap(WalkerDecisionProjector.faceName)`.

- [ ] **Step 5: Run the test and the full gate**

Run: `./sbtw "testOnly oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: PASS.

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/projection/DieFaces.scala src/main/scala/oathdigital/application/WalkerDecisionProjector.scala src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala
git commit -m "feat(protocol): one die face vocabulary, shared by both runtimes

Seven faces were spelled in four tables and two of them had drifted. This
is the one that replaces three. The fourth serves the decision wire and
stays, held to this one by a set-equality test.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: Rename the frontend's renderer to `DieFaceChip`

A pure rename, so the shared `DieFace` enum can own the bare name on the
frontend side. No behavior change.

**Files:**
- Delete: `frontend/src/main/scala/oathdigital/frontend/DieFace.scala`
- Create: `frontend/src/main/scala/oathdigital/frontend/DieFaceChip.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala:249`
- Delete: `frontend/src/test/scala/oathdigital/frontend/DieFaceSuite.scala`
- Create: `frontend/src/test/scala/oathdigital/frontend/DieFaceChipSuite.scala`

**Interfaces:**
- Consumes: Task 5's enums (not used yet).
- Produces: `private[frontend] object DieFaceChip` with exactly the members
  `DieFace` had: `name(face: String)`, `face(value: String, die:
  Option[String])`, `roll(faces: Vector[String], die: Option[String])`.

- [ ] **Step 1: Rename the three files and the references**

```bash
git mv frontend/src/main/scala/oathdigital/frontend/DieFace.scala \
  frontend/src/main/scala/oathdigital/frontend/DieFaceChip.scala
git mv frontend/src/test/scala/oathdigital/frontend/DieFaceSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/DieFaceChipSuite.scala
perl -pi -e 's/\bDieFaceSuite\b/DieFaceChipSuite/g; s/\bDieFace\b/DieFaceChip/g' \
  frontend/src/main/scala/oathdigital/frontend/DieFaceChip.scala \
  frontend/src/test/scala/oathdigital/frontend/DieFaceChipSuite.scala \
  frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala \
  frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala
```

- [ ] **Step 2: Reword the object's doc comment**

The scaladoc on `DieFaceChip` says the engine names faces with seven strings;
after Task 5 there is a named vocabulary to point at. Replace its first
paragraph:

```scala
/** The faces of the two dice, drawn as the symbols printed on them.
  *
  * One renderer serves every panel that shows a roll. Five faces compose
  * from sprite glyphs; a blank face has nothing printed on it, and the
  * doubler multiplies rather than adding, so it says so in figures.
  *
  * Named `DieFaceChip`, not `DieFace`: the face itself is
  * `oathdigital.protocol.projection.DieFace`, and this is what the page
  * draws for one.
  */
```

- [ ] **Step 3: Check nothing else named it**

```bash
grep -rn '\bDieFace\b' frontend/src
```
Expected: no output (Task 7 reintroduces the name as the imported enum).

- [ ] **Step 4: Run the frontend suites and the full gate**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.DieFaceChipSuite"`
Expected: PASS, with every assertion unchanged.

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src
git commit -m "refactor(frontend): rename DieFace to DieFaceChip

The face is a protocol type now; this object is what the page draws for
one. Rename only.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: Type the roll end to end

`LogSpan.Dice` stops carrying a die name and two parallel string arrays and
carries typed faces, head first, so the die is read off them. Three of the
four face tables collapse onto `DieFace.word` and `DieFace.key`.

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/LogPageCodec.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/LogWords.scala:129-156`
- Modify: `frontend/src/main/scala/oathdigital/frontend/DieFaceChip.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala:65-68,249`
- Test: `shared/src/test/scala/oathdigital/protocol/LogPageCodecSuite.scala`
- Test: `src/test/scala/oathdigital/application/gamelog/GoldenLog.scala:22-23`
- Test: `frontend/src/test/scala/oathdigital/frontend/DieFaceChipSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala:72-83`
- Test: `frontend/src/test/scala/oathdigital/frontend/WalkerDecisionDraftSuite.scala:80-84`

**Interfaces:**
- Consumes: Task 5's `Die` and `DieFace`; Task 6's `DieFaceChip`.
- Produces: `LogSpan.Dice(first: DieFace, rest: Vector[DieFace])` with
  `def die: Die = first.die` and
  `def text: String = (first +: rest).map(_.word).mkString(", ")`;
  `LogWords.dice(faces: Vector[oathdigital.model.DieFace]):
  Option[LogSpan.Dice]`; `DieFaceChip.roll(faces: Vector[DieFace])`,
  `DieFaceChip.rollKeys(keys: Vector[String], die: Option[Die])`,
  `DieFaceChip.face(key: String, die: Option[Die])`;
  `WalkerPanelSupport.poolDie(pool: String): Option[Die]`.

- [ ] **Step 1: Write the failing tests**

In `shared/src/test/scala/oathdigital/protocol/LogPageCodecSuite.scala`,
replace the `LogSpan.Dice(...)` in "every span case round-trips" with
`LogSpan.Dice(DieFace.OneSword, Vector(DieFace.TwoSwordsSkull))` and add:

```scala
  test("a dice span is its faces: the die and the words come off them"):
    val roll = LogPage("g", 0, 9, Vector(LogEntry(7, 0, LogKind.Roll,
      Vector(LogSpan.Dice(DieFace.OneSword,
        Vector(DieFace.OneSword, DieFace.TwoSwordsSkull))))))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(roll)), Right(roll))
    val json = ujson.read(LogPageCodec.encode(roll))
    assertEquals(json("entries")(0)("spans")(0),
      ujson.Obj("kind" -> "dice", "faces" -> ujson.Arr("one-sword",
        "one-sword", "two-swords-skull")))

  test("an empty roll is rejected: no line builds one"):
    val json = """{"gameId":"g","after":0,"nextSequence":9,"entries":[""" +
      """{"sequence":7,"ordinal":0,"kind":"roll","spans":[""" +
      """{"kind":"dice","faces":[]}]}]}"""
    assertEquals(LogPageCodec.decode(json).left.map(_.path),
      Left("$.entries[0].spans[0].faces"))

  test("a roll mixing the two dice is rejected"):
    val json = """{"gameId":"g","after":0,"nextSequence":9,"entries":[""" +
      """{"sequence":7,"ordinal":0,"kind":"roll","spans":[""" +
      """{"kind":"dice","faces":["one-sword","blank"]}]}]}"""
    assertEquals(LogPageCodec.decode(json),
      Left(ProtocolDecodeFailure.InvalidValue("$.entries[0].spans[0].faces",
        "expected one die's faces")))

  test("a face this build does not know is an unknown variant"):
    val json = """{"gameId":"g","after":0,"nextSequence":9,"entries":[""" +
      """{"sequence":7,"ordinal":0,"kind":"roll","spans":[""" +
      """{"kind":"dice","faces":["three-moons"]}]}]}"""
    assertEquals(LogPageCodec.decode(json),
      Left(ProtocolDecodeFailure.UnknownVariant(
        "$.entries[0].spans[0].faces[0]", "unknown die face 'three-moons'")))
```

In `src/test/scala/oathdigital/application/gamelog/LogWordsSuite.scala`:

```scala
  test("a roll is its faces; a mixed or empty pool is no span"):
    assertEquals(LogWords.dice(Vector(AttackDieFace.OneSword,
      AttackDieFace.TwoSwordsSkull)).map(_.text),
      Some("sword, two swords and a skull"))
    assertEquals(LogWords.dice(Vector(DefenseDieFace.OneShield))
      .map(_.die), Some(oathdigital.protocol.projection.Die.Defense))
    assertEquals(LogWords.dice(Vector(AttackDieFace.OneSword,
      DefenseDieFace.Blank)), None)
    assertEquals(LogWords.dice(Vector.empty), None)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.protocol.LogPageCodecSuite oathdigital.application.gamelog.LogWordsSuite"`
Expected: FAIL to compile, "None of the overloaded alternatives of method
apply ... LogSpan.Dice".

- [ ] **Step 3: Retype the case**

In `LogProjectionDtos.scala`:

```scala
  /** One roll of one die, drawn as dice. Head first rather than a `Vector`,
    * so reading the die off the faces is total: there is no empty roll and
    * no roll whose die must be sent beside it. */
  final case class Dice(first: DieFace, rest: Vector[DieFace])
      extends LogSpan:
    def faces: Vector[DieFace] = first +: rest
    def die: Die = first.die
    def text: String = faces.map(_.word).mkString(", ")
```

- [ ] **Step 4: Retype the codec's dice arm**

Encode:

```scala
    case dice: LogSpan.Dice => ujson.Obj("kind" -> "dice",
      "faces" -> encoded(dice.faces)(face => ujson.Str(face.key)))
```

Decode:

```scala
  /** `LogWords.dice` builds a span only from a non-empty pool of one die's
    * faces, so neither an empty roll nor a mixed one is a span this server
    * can send. */
  private def decodeDice(value: ujson.Obj, path: String): Result[LogSpan] =
    for
      _ <- exact(value, Set("kind", "faces"), path)
      keys <- strings(value, "faces", path)
      faces <- traverse(keys, s"$path.faces")((key, child) =>
        DieFace.values.find(_.key == key).toRight(UnknownVariant(child,
          s"unknown die face '$key'")))
      first <- faces.headOption.toRight(
        InvalidValue(s"$path.faces", "expected at least one face"))
      _ <- Either.cond(faces.forall(_.die == first.die), (),
        InvalidValue(s"$path.faces", "expected one die's faces"))
    yield LogSpan.Dice(first, faces.tail)
```

- [ ] **Step 5: Collapse `LogWords`'s four naming functions into one map**

Add the renamed import to `LogWords.scala` — `oathdigital.model._` already
binds `DieFace` to the domain's open trait, so the enum comes in under a
second name:

```scala
import oathdigital.protocol.projection.{DieFace => RolledFace, LogSpan}
```

Replace lines 128-156 (`dice` and the four private functions) with:

```scala
  /** A roll as a dice span, or nothing for no dice, a mixed pool or a face
    * family neither die prints. */
  def dice(faces: Vector[DieFace]): Option[LogSpan.Dice] =
    val rolled = faces.flatMap(rolledFace)
    Option.when(rolled.size == faces.size && rolled.nonEmpty &&
      rolled.forall(_.die == rolled.head.die))(
      LogSpan.Dice(rolled.head, rolled.tail))

  /** `oathdigital.model.DieFace` is open, so a face family neither pool
    * rolls maps to nothing rather than to a guess. */
  private def rolledFace(value: DieFace): Option[RolledFace] = value match
    case AttackDieFace.HollowSword => Some(RolledFace.HollowSword)
    case AttackDieFace.OneSword => Some(RolledFace.OneSword)
    case AttackDieFace.TwoSwordsSkull => Some(RolledFace.TwoSwordsSkull)
    case DefenseDieFace.Blank => Some(RolledFace.Blank)
    case DefenseDieFace.OneShield => Some(RolledFace.OneShield)
    case DefenseDieFace.TwoShields => Some(RolledFace.TwoShields)
    case DefenseDieFace.Doubler => Some(RolledFace.Doubler)
    case _ => None
```

- [ ] **Step 6: Keep `GoldenLog`'s rendering byte-identical**

`src/test/scala/oathdigital/application/gamelog/GoldenLog.scala:22-23`:

```scala
    case dice: LogSpan.Dice =>
      s"[dice:${dice.die.key} ${dice.faces.map(_.key).mkString(" ")}" +
        s"|${dice.text}]"
```

The goldens read `[dice:attack one-sword one-sword|sword, sword]`. `key` is
what `LogWords.attackWire` produced and `word` is what `attackName` produced,
so every character is the one already on disk.

- [ ] **Step 7: Retype the renderer**

`DieFaceChip.scala`: delete the `words` map and the `dice` set, import the
vocabulary, and split the entry points.

```scala
package oathdigital.frontend

import org.scalajs.dom
import oathdigital.protocol.projection.{Die, DieFace}

/** The faces of the two dice, drawn as the symbols printed on them.
  *
  * One renderer serves every panel that shows a roll. Five faces compose
  * from sprite glyphs; a blank face has nothing printed on it, and the
  * doubler multiplies rather than adding, so it says so in figures.
  *
  * Named `DieFaceChip`, not `DieFace`: the face itself is
  * `oathdigital.protocol.projection.DieFace`, and this is what the page
  * draws for one. The words a face is read by are that enum's; this object
  * keeps only the glyph composition, which is the client's own drawing
  * concern and has no server twin.
  */
private[frontend] object DieFaceChip:
  private val symbols = Map(
    "hollow-sword" -> Vector("hollow-sword"),
    "one-sword" -> Vector("sword"),
    "two-swords-skull" -> Vector("sword", "sword", "skull"),
    "one-shield" -> Vector("shield"),
    "two-shields" -> Vector("shield", "shield"),
    "blank" -> Vector.empty[String],
    "doubler" -> Vector.empty[String])

  def name(key: String): String =
    DieFace.values.find(_.key == key).fold(key)(_.word)

  def face(key: String, die: Option[Die] = None): dom.Element =
    val classes = Vector("die-face", s"die-face-$key") ++
      die.map(kind => s"die-face-${kind.key}")
    val node = ServerUiSupport.element("span", classes.mkString(" "))
    node.setAttribute("role", "img")
    node.setAttribute("aria-label", name(key))
    symbols.get(key) match
      case Some(glyphs) =>
        glyphs.foreach(glyph => node.appendChild(RulesTextRenderer.glyph(glyph)))
        if key == "doubler" then
          node.appendChild(dom.document.createTextNode("×2"))
      // A face this client does not know is printed as itself rather than
      // dropped, the same way an unknown rules token stays literal. Only the
      // decision wire can still send one; the log's faces are typed.
      case None => node.appendChild(dom.document.createTextNode(key))
    node

  /** The log's roll: every face typed, so the die comes off the faces. */
  def roll(faces: Vector[DieFace]): dom.Element =
    row(faces.map(_.key), faces.headOption.map(_.die))

  /** The decision wire's roll. Its faces are still keys
    * (`WalkerDecisionProjector.faceName`), held to `DieFace` by a test in
    * `WalkerDecisionProjectorSuite`. */
  def rollKeys(keys: Vector[String], die: Option[Die]): dom.Element =
    row(keys, die)

  private def row(keys: Vector[String], die: Option[Die]): dom.Element =
    val node = ServerUiSupport.element("span", "die-faces")
    node.setAttribute("aria-label", keys.map(name).mkString(", "))
    keys.foreach(key => node.appendChild(face(key, die)))
    node
```

- [ ] **Step 8: Retype the two callers**

`GameLogPane.scala`'s dice arm:

```scala
    case dice: LogSpan.Dice =>
      val row = DieFaceChip.roll(dice.faces)
      row.setAttribute("class",
        s"die-faces log-dice log-dice-${dice.die.key}")
      row
```

`WalkerPanelSupport.scala:65-68` and `:249`:

```scala
  private[frontend] def poolDie(pool: String): Option[Die] = pool match
    case "campaign.attack" => Some(Die.Attack)
    case "campaign.defense" | "recover" => Some(Die.Defense)
    case _ => None
```
```scala
        row.appendChild(DieFaceChip.rollKeys(outcome.faces,
          poolDie(outcome.pool)))
```

Add `import oathdigital.protocol.projection.Die` to `WalkerPanelSupport.scala`
and `oathdigital.protocol.projection.LogSpan` is already on `GameLogPane`.

- [ ] **Step 9: Update the three frontend suites**

`DieFaceChipSuite.scala`:
- "a face rolled on a known die carries that die; any other stays neutral":
  the die arguments become `Some(Die.Attack)` and `Some(Die.Defense)`, and
  the last assertion — `DieFaceChip.face("one-shield", Some("round"))` — is
  deleted: an unknown die is no longer a value the type admits.
- "each face's accessible name is its printed words": `"one-sword"` now
  reads `"sword"` and `"one-shield"` reads `"shield"`. **This is the
  behavior change the spec records.** The other five are unchanged.
- "a roll gives every face its die" becomes the typed entry point:
  ```scala
    val row = DieFaceChip.roll(Vector(DieFace.OneSword, DieFace.HollowSword))
    assertEquals(all(row, ".die-face-attack").size, 2)
  ```
- "a roll is a row of faces, and an empty roll draws nothing" keeps the
  string entry point, which is the one that can still be empty:
  ```scala
    val row = DieFaceChip.rollKeys(
      Vector("one-shield", "blank", "doubler"), None)
    assertEquals(all(row, ".die-face").size, 3)
    assertEquals(row.getAttribute("aria-label"), "shield, blank, doubler")
    assertEquals(all(DieFaceChip.rollKeys(Vector.empty, None), ".die-face"),
      Vector.empty)
  ```
- "a face this client does not know is printed as its own name" is unchanged:
  `face("three-moons")` still takes a key.

`GameLogPaneSuite.scala:72-83`. Both faces must be attack faces: the die
comes off the first one now, and every chip in the row is drawn in that die's
colour, so the old `blank` (a defense face) would make the row self-
contradictory rather than exercise anything.

```scala
  test("a dice span draws the die-face chips, one per face"):
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    val roll = entry(7, LogKind.Roll, LogSpan.Text("Rolled "),
      LogSpan.Dice(DieFace.OneSword, Vector(DieFace.HollowSword)),
      LogSpan.Text(" for the attack"))
    new GameLogPane(content).show("g|red", Vector(roll), Map.empty)
    val faces = content.querySelectorAll(".die-faces .die-face")
    assertEquals(faces.length, 2)
    assertEquals(faces(0).getAttribute("aria-label"), "sword")
    assertEquals(content.querySelector(".die-faces").getAttribute("class"),
      "die-faces log-dice log-dice-attack")
    assertEquals(content.querySelectorAll(".die-face-attack").length, 2)
```

Add `DieFace` to the suite's `oathdigital.protocol.projection` import.

`WalkerDecisionDraftSuite.scala:80-84`:

```scala
    assertEquals(WalkerPanelSupport.poolDie("campaign.attack"),
      Some(Die.Attack))
    assertEquals(WalkerPanelSupport.poolDie("campaign.defense"),
      Some(Die.Defense))
    assertEquals(WalkerPanelSupport.poolDie("recover"), Some(Die.Defense))
    assertEquals(WalkerPanelSupport.poolDie("gambling-hall"), None)
```

- [ ] **Step 10: Run the tests, then the full gate**

Run: `./sbtw "testOnly oathdigital.protocol.LogPageCodecSuite oathdigital.application.gamelog.LogWordsSuite"`
Expected: PASS.

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS.

- [ ] **Step 11: Confirm the goldens did not move, then commit**

```bash
git status --porcelain src/test/resources/gamelog
```
Expected: no output. If a golden moved, the collapsed word table disagrees
with `LogWords.attackName`/`defenseName`; compare the diff against
`DieFace.word` before changing anything.

```bash
git add shared src frontend
git commit -m "feat(gamelog): a roll's faces are typed, and there is one word for each

LogSpan.Dice carried a die name and two parallel string arrays. It carries
faces now, head first, so the die is read off them. Three of the four face
tables collapse onto DieFace.

Two die chips change their accessible name: one-sword and one-shield now
read \"sword\" and \"shield\", the words the log sentence has always used.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 8: Route an unparseable log page to the stale-bundle path

Strict decoding moved the failure and on this route it currently has nowhere
to go: `fetchLog` consumes the result with `result.foreach`, so a `Left` is
dropped and the pane would silently stop updating forever.

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameClient.scala:204-209`
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableSession.scala:77-99`
- Test: `frontend/src/test/scala/oathdigital/frontend/TableSessionSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/HttpGameClientSuite.scala`

**Interfaces:**
- Consumes: Task 7's codec, which produces `UnknownVariant` for an unknown
  span kind, entry kind or die face.
- Produces: no new type. `GameJson.logResponse` maps `UnknownVariant` to
  `GameClientFailure.UnsupportedProjection`; `TableSession.clientOutOfDate`
  becomes true for a log failure as it already is for a projection one.

- [ ] **Step 1: Write the failing tests**

In `frontend/src/test/scala/oathdigital/frontend/HttpGameClientSuite.scala`:

```scala
  test("a log page naming a span kind this build lacks is unsupported"):
    val unknown = """{"gameId":"g","after":0,"nextSequence":1,"entries":[""" +
      """{"sequence":0,"ordinal":0,"kind":"turn","spans":[""" +
      """{"kind":"omen","text":"x"}]}]}"""
    val transport = new RecordingTransport(
      Vector(Right(TransportResponse(200, unknown))))
    new HttpGameClient(transport).loadLog("g", "red", 0L).map { result =>
      assertEquals(result, Left(GameClientFailure.UnsupportedProjection(
        "$.entries[0].spans[0].kind", "unknown log span kind 'omen'")))
    }
```

(Match `RecordingTransport`'s actual name and constructor in that file; the
suite already builds one at line 263.)

In `frontend/src/test/scala/oathdigital/frontend/TableSessionSuite.scala`:

```scala
  /** The log route's half of the stale-bundle path: strict decoding gives
    * the failure somewhere to go, instead of a pane that stops updating in
    * silence. */
  test("a log page this build cannot parse stops polling and asks for a reload"):
    displayed(3).flatMap { fixture =>
      val unsupported = GameClientFailure.UnsupportedProjection(
        "$.entries[0].spans[0].kind", "unknown log span kind 'omen'")
      fixture.client.answerLog(Left(unsupported))
      settle().map { _ =>
        assertEquals(fixture.session.shownFailure, Some(unsupported))
        assert(fixture.session.clientOutOfDate)
        assert(!fixture.clock.hasPendingTask)
      }
    }

  test("a log page that fails to decode for any other reason changes nothing"):
    displayed(3).flatMap { fixture =>
      fixture.client.answerLog(
        Left(GameClientFailure.DecodeFailure("$.entries", "expected array")))
      settle().map { _ =>
        assertEquals(fixture.session.shownFailure, None)
        assert(!fixture.session.clientOutOfDate)
        assert(fixture.clock.hasPendingTask)
      }
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.TableSessionSuite oathdigital.frontend.HttpGameClientSuite"`
Expected: FAIL. The client test reports a `DecodeFailure` where an
`UnsupportedProjection` was expected; the session test reports
`shownFailure` as `None`.

- [ ] **Step 3: Map the failure in `logResponse`**

`GameClient.scala:204-209`:

```scala
  /** As `decodeProjection`: a variant this build does not know means the
    * bundle in this browser is stale, which the session can act on, and any
    * other decode failure means the payload is wrong, which it cannot. */
  def logResponse(response: TransportResponse)
      : Either[GameClientFailure, LogPage] =
    if response.status >= 200 && response.status < 300 then
      LogPageCodec.decode(response.body).left.map:
        case unknown: ProtocolDecodeFailure.UnknownVariant =>
          GameClientFailure.UnsupportedProjection(unknown.path,
            unknown.message)
        case error => GameClientFailure.DecodeFailure(error.path,
          error.message)
    else Left(responseFailure(response))
```

- [ ] **Step 4: Route it in `fetchLog`**

`TableSession.scala:81-99` — the success body is unchanged; the `Left` branch
is new:

```scala
      client.loadLog(gameId, selectedPlayer, logSequence).foreach { result =>
        if epoch == logEpoch then
          logLoading = false
          result match
            case Right(page) =>
              val advanced = page.nextSequence > logSequence
              if logReplacing then
                log = page.entries
                logReplacing = false
              else
                val last = log.lastOption.map(entry =>
                  entry.sequence -> entry.ordinal)
                log ++= page.entries.filter(entry => last.forall(key =>
                  Ordering[(Long, Int)].gt(entry.sequence -> entry.ordinal,
                    key)))
              logSequence = math.max(logSequence, page.nextSequence)
              redraw()
              if advanced then projection.foreach(current =>
                fetchLog(current.nextSequence))
            // A log page this build cannot parse is the same stale bundle
            // the projection route reports: polling cannot recover from it
            // and only a reload can. Every other failure keeps the posture
            // the spec sets -- it changes nothing, and the next advancing
            // position asks again.
            case Left(error) if TableSession.unsupported(error) =>
              polling.stop()
              failure = Some(error)
              redraw()
            case Left(_) => ()
      }
```

Update `fetchLog`'s scaladoc to name the new branch:

```scala
  /** Asks for the entries after the log's cursor when `target`, a displayed
    * position, is past it. One request at a time; a failure changes nothing
    * and the next advancing position asks again (spec, "Fetching"), except
    * a page this build cannot parse, which stops the session as an
    * unsupported projection does. */
```

- [ ] **Step 5: Run the tests and the full gate**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.TableSessionSuite oathdigital.frontend.HttpGameClientSuite"`
Expected: PASS.

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src
git commit -m "fix(frontend): an unparseable log page reports the stale bundle

fetchLog consumed its result with foreach, so a decode failure was
dropped. Under strict decoding that would have been a log pane that stops
updating forever with no message. It now takes the same unsupported path
the projection route takes.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Self-review

**Spec coverage.**

| Spec section | Task |
|---|---|
| The types move, they are not duplicated | 1, 4 |
| The span family (`Text`…`Cards`) | 1, 3, 7 |
| `Cost` has one unit | 3 |
| `Card` and `Site` keep their ids | 1 (kept, unconsumed by design) |
| Die faces, tables 1/3/4 collapsed | 5, 7 |
| Table 2 stays, guarded by a set-equality test | 5 |
| `text` leaves the wire | 4 |
| `depth` leaves the wire, onto `LogKind` | 2, 4 |
| A roll's die is derived | 7 |
| Decoder: unknown span kind | 4 |
| Decoder: a field a case does not declare | 4 |
| Decoder: empty `cards` | 4 |
| Decoder: mixed-die `faces` | 7 |
| Decoder: `section` on a headline kind | 4 |
| The codec, discriminated with per-case sets | 4 |
| `Text`'s payload keyed `"text"` | 4 |
| `LogKind` still encodes as its key | 4 |
| A log page this client cannot parse | 8 |
| Retiring the unknown-kind tolerance | 4 (header comment replaced) |
| The formatter and the thirteen line files | 1 (imports only) |
| `GameLogPane` matches the sealed family | 4 |
| `DieFace.roll` typed; `words` and the die set deleted | 6, 7 |
| `DieFace.symbols` stays | 7 |
| `Amount.unit` and `LogSection.action` stay `String` | untouched, by design |
| Verification: `LogPageCodecSuite` per case + rejections | 4, 7 |
| Verification: the formatter's assertions do not move | 1-3, 7 (golden check each task) |
| Verification: the pane and `TestLog` | 4, 7 |
| Verification: the stale-bundle path | 8 |

Nothing in the spec is unassigned. Two items the spec leaves open are resolved
above under "Three resolutions the spec left open" and are not silent.

**Placeholder scan.** No "TBD", no "similar to Task N", no "add error
handling". Every code step carries the code. The two places that say "match
the file's actual helper" (`RecordingTransport` in Task 8 Step 1, the
alphabetical import position in Task 1 Step 3) name a fact the executor reads
off the file rather than a decision left open.

**Type consistency.** `LogPage`, `LogEntry`, `LogSpan`, `LogKind`,
`LogSection`, `LogTakenBack`, `Die`, `DieFace`, `DieFaceChip`, `Posted`,
`LogWords.dice`, `WalkerDecisionProjector.faceName`,
`WalkerPanelSupport.poolDie`, `DieFaceChip.roll`/`rollKeys`/`face`/`name` are
spelled the same in every task that names them. `LogSpan.Dice` is
`(die, faces, names)` through Task 6 and `(first, rest)` from Task 7, which
is the spec's own staging and is stated at both ends.
