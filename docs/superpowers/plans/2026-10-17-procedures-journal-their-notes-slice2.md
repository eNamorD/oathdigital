# Procedures Journal Their Own Notes — Slice 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the twelve straightforward procedures so each journals its
own Game Log line, and delete the twelve inference rules in `ActionLines`,
`NegotiationLines` and `SetupLines` that guessed those lines from recorded
operations.

**Architecture:** Each procedure declares its own `NoteSet` beside itself in
`gameplay`, puts a `Note` leaf in the tree where the effect happens, and its
`ProcedureLog` entry becomes `ActionLineSource.Notes`. Slice 1 built that
mechanism and proved it on Muster. This slice adds one thing to it: a note's
`LogKind`. Slice 1 renders every procedure note at `LogKind.Action`, which is
right for eight of these twelve and wrong for four — Oathkeeper posts at
`trigger`, Finish Rest at `delta`, Setup at `action` and `decision`,
Negotiation at `action` and `delta`. The kind is layout, so ruling 2 keeps it
out of `gameplay`: the registry entry declares it, keyed by note key, and
`PowerLines` reads it through the note's own procedure.

**Tech Stack:** Scala 3.9.0 on the JVM, munit, ujson, built through `./sbtw`.

**Spec:** [docs/superpowers/specs/2026-10-16-procedures-journal-their-notes-design.md](../specs/2026-10-16-procedures-journal-their-notes-design.md)

**Slice 1 plan:** [2026-10-16-procedures-journal-their-notes-slice1.md](2026-10-16-procedures-journal-their-notes-slice1.md)

**Scope:** Slice 2 of four. Slice 3 (Search, Trade, Recover, the played
adviser) and slice 4 (Campaign, then the deletion) each get their own plan.
`ActionLines` is not deleted here: this slice empties it of twelve of its
eighteen procedure rules and leaves the other six plus `usePowerLines`.

## Global Constraints

- **Scala 3.9.0 with `-Werror`** (`build.sbt:61-68`). A non-exhaustive match
  or an unused import fails the build. Deleting a rule usually orphans an
  import at the top of `ActionLines.scala`; remove it in the same step.
- **800-line production-file cap**, enforced by `scripts/check-architecture.py`
  and `BackendArchitectureSuite`. `ActionLines` shrinks throughout; no
  `gameplay` file here is within 300 lines of the cap.
- **`gameplay` may not import `application`, `persistence`, `presentation`,
  `protocol`, `serialization` or `server`** (`scripts/check-architecture.py`).
  `model` is free. This is why a procedure's wording is `NoteKey` /
  `NotePart` / `NoteArg` and never `LogSpan` or `LogKind`.
- **`model` may not import `application`, `gameplay`, `persistence`,
  `serialization` or `server`.**
- **Golden logs: the rendered line never changes, the sequence may.** Run
  `./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`.
  Each migration task moves its line onto the `PowerNoted` event, so the
  entry's `sequence` and `ordinal` shift and nothing else may. Set
  `GAMELOG_GOLDEN=write` only in a migration task, and read the `git diff`
  line by line: a changed span, a changed `kind`, a changed depth, a changed
  section, a line that moved relative to another line, or a shift on a line
  the task did not touch is a defect, not a rewrite.
- **A template must not introduce a typed span where the line writes plain
  text today.** `GoldenLog.render` prints `LogSpan.Text` as its bare string
  and `LogSpan.Amount` as `[amount:2 warbands]`. Counts in these twelve lines
  are plain text, so every count is `NoteArg.Number` plus `NotePart.Plural`,
  **never** `NoteArg.Amount`. Several adjacent `Text` spans concatenate to
  the same golden string, so splitting a sentence across parts is safe;
  changing a span's *kind* is not.
- **`NoteArg.Cards` renders through `LogWords.listed`, which switches to a
  `LogSpan.Cards` span above `LogWords.Inline` (5) cards.** Every card list
  in this slice is bounded below that — a Forge pays at most three, a site
  holds at most two relics — so each stays the inline phrase `LogWords.cards`
  writes today. A list that could exceed five would need a different
  argument.
- **A note's cards are judged at a wider state set than the rule's were.**
  `LogWords.card(id, before, after, viewer)` *is*
  `seen(Vector(before, after), viewer)`, and a note's `seen` is the action's
  start, the restated step's before-state and the state after the note. The
  note set is wider in time, so it can only name a card the rule left as a
  back, never the reverse. The goldens prove the covered paths.
- **`NoteStates.previous` is the last *leaf* the command ran, and a `Note` is
  not one.** `ProcedureWalker.noted` appends the event and copies nothing
  else, so several `Note` leaves in a row all read the same `previous`. Place
  a note that reads a delta directly after the leaf whose delta it reads, and
  say so in a comment, as `EconomyTree.afterSource` does.
- **`Told.by` names a key only when exactly one key tells that decision.**
  Where a line has several variants — Challenge's four, Forge's three — no
  single key tells the decision, and naming one of them would be false. Leave
  `by` unset there with a one-line comment; `GameLogPropertiesSuite` checks
  only that a named key is one the procedure declares.
- **`src/test/resources/notes/declared-keys.txt` is expected to change** in
  every migration task. Rewrite it with
  `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`
  and read the diff: it must add exactly the task's keys and change nothing
  else.
- **`GameLogPropertiesSuite`'s `noting` assertion rises by one per task.** It
  reads `assertEquals(noting.map(_.key), Vector("muster"))` today. Each task
  appends its own key, in `ProcedureRef.all` order.
- **Every line number in this plan is as of the slice's first commit.**
  Tasks 2 to 13 each delete a block from `ActionLines.scala` and from
  `ProcedureLog.scala`, so the numbers below a deletion drift. Find the named
  method, not the line.
- **A tagged procedure holds its notes until its tag posts.**
  `PowerLines.own` returns nothing while `run.started.isEmpty` and the entry
  has a tag, and `PowerLines.held` posts the held notes right after the start
  line. Three of these twelve have tags — Travel, Forge and Challenge — and
  in each the anchor fires before the note's event, so the note posts at its
  own event as usual. A note that appears directly under the `section` line
  instead of where its effect happened means the anchor moved behind it.
- **Worktree setup.** A fresh worktree has no `.tooling`. Symlink the main
  checkout's `.tooling` (and `node_modules`, if absent) into it before the
  first `./sbtw`.

### The full gate

Run before every commit:

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
```

---

### Task 1: A procedure note's kind is declared in the registry

No procedure migrates here. Slice 1's `PowerLines.kind` answers
`LogKind.Action` for every procedure note, and four of this slice's twelve
post at another kind. Ruling 2 forbids `gameplay` naming a kind, so the
registry entry — which is already in `application/gamelog` — declares it.

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/PowerLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Create: `src/test/scala/oathdigital/application/gamelog/ProcedureNoteLinesSuite.scala`
- Modify: `docs/superpowers/specs/2026-10-16-procedures-journal-their-notes-design.md`
- Modify: `docs/superpowers/specs/2026-10-16-game-log-wording-ledger.md`

**Interfaces:**
- Consumes: `ActionLineSource`, `ProcedureNotes.writes`, `PowerLines.kind`,
  all from slice 1.
- Produces: `ActionLineSource.Notes(kinds: Map[NoteKey, LogKind])`,
  `ProcedureLog.noteKind(key: String): LogKind`,
  `ProcedureNotes.procedureOf(power: PowerId): Option[ProcedureRef]`, and the
  `ProcedureNoteLinesSuite` file every later task appends to.

- [ ] **Step 1: Write the failing kind tests**

Create `src/test/scala/oathdigital/application/gamelog/ProcedureNoteLinesSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection.{LogKind, LogSpan}
import LogSpan.Text

/** What a migrated procedure's line reads, on the branches no golden script
  * exercises, and at which kind it posts (procedures journal their own notes
  * design, slice 2). A golden file proves the branch its script plays; these
  * are the branches it does not play, written down before the rule that
  * worded them was deleted.
  *
  * Rendering takes no game state: every assertion here is over plain text,
  * players, sites, banners and numbers, and a card with no states behind it
  * renders as its back, which is the part these branches leave untested
  * elsewhere.
  */
class ProcedureNoteLinesSuite extends munit.FunSuite:
  private val words = new LogWords(catalog, LogScripts.presentation)
  private val lines = new PowerLines(words, NoteWordings.default(catalog),
    LogScripts.formatter.procedures)

  /** `key`'s sentence, as the Game Log renders it for a procedure's note. */
  private def spoken(procedure: ProcedureRef, key: NoteKey, args: NoteArg*)(
      using munit.Location): Vector[LogSpan] =
    lines.spoken(ProcedureNotes.id(procedure), key.sourceless(args*),
      Vector.empty, None).getOrElse(
        fail(s"no template for ${procedure.key}/${key.name}"))

  /** The kind `procedure`'s entry declares for `key`. */
  private def kindOf(procedure: ProcedureRef, key: NoteKey): LogKind =
    LogScripts.formatter.procedures.of(procedure).noteKind(key.name)

  test("a procedure's note posts at Action unless its entry says otherwise"):
    assertEquals(kindOf(ActionRef.Muster, MusterProcedure.mustered),
      LogKind.Action)

  test("a procedure whose line is still a rule declares no kind"):
    assertEquals(LogScripts.formatter.procedures.of(ActionRef.Travel)
      .noteKind("anything"), LogKind.Action)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.ProcedureNoteLinesSuite"`
Expected: FAIL to compile, `value noteKind is not a member of ProcedureLog`.

- [ ] **Step 3: Give `ActionLineSource.Notes` its kinds**

In `ProcedureLog.scala`, add `LogKind` to the projection import:

```scala
import oathdigital.protocol.projection.{LogKind, LogSpan}
```

Replace the `Notes` case of `ActionLineSource`:

```scala
  /** The procedure journals its own notes, which `PowerLines` posts. Each
    * posts at `LogKind.Action` unless `kinds` names another for its key: a
    * kind is layout, so it is declared here and never beside the procedure
    * (procedures journal their own notes design, ruling 2). */
  case Notes(kinds: Map[NoteKey, LogKind] = Map.empty)
```

Add to the body of `ProcedureLog`, after `narrates`:

```scala
  /** The kind the note named `key` posts at. A procedure whose line is still
    * a rule answers `Action`, which nothing asks it for. */
  def noteKind(key: String): LogKind = lines match
    case ActionLineSource.Notes(kinds) => kinds.collectFirst {
      case (declared, kind) if declared.name == key => kind
    }.getOrElse(LogKind.Action)
    case _ => LogKind.Action
```

Change Muster's entry to `lines = ActionLineSource.Notes()`.

- [ ] **Step 4: Let `ProcedureNotes` name the procedure behind an id**

In `ProcedureNotes.scala`, replace the `ids` set and `writes`:

```scala
  /** Every procedure by the id `id` builds for it, so a note's id names the
    * procedure that wrote it. The Game Log needs the procedure and not just
    * the fact that one wrote the note, because the kind is declared on the
    * procedure's registry entry.
    *
    * `lazy` for the same reason `all` is. */
  private lazy val byId: Map[PowerId, ProcedureRef] =
    ProcedureRef.all.map(procedure => id(procedure) -> procedure).toMap

  /** Whether `power` is a procedure's id rather than a power's or a game
    * rule's. The Game Log asks this, and not whether a note has a source:
    * the source is optional on a type powers also use. */
  def writes(power: PowerId): Boolean = byId.contains(power)

  /** The procedure `power` is the id of, if it is one. */
  def procedureOf(power: PowerId): Option[ProcedureRef] = byId.get(power)
```

- [ ] **Step 5: Read the kind off the note's own procedure**

In `PowerLines.scala`, give the class the registry:

```scala
private[gamelog] final class PowerLines(words: LogWords,
    wordings: NoteWordings, procedures: ProcedureLogs):
```

and replace `kind`:

```scala
  /** A procedure's own note is its action's line at the kind its registry
    * entry declares, and a phase power's `used` note, or a variant of it, is
    * its action's line.
    *
    * The procedure is read from the note's id, never from `run.procedure`: a
    * queued follow-up can run one procedure's note under another's run. */
  private def kind(run: Run, power: PowerId, note: PowerNote): LogKind =
    ProcedureNotes.procedureOf(power) match
      case Some(procedure) => procedures.of(procedure).noteKind(note.key)
      case None =>
        if NoteKey.isUse(note.key) && run.procedure == ActionRef.UsePower(power)
        then LogKind.Action
        else LogKind.Trigger
```

In `GameLogFormatter.scala`, pass it — `procedures` is already built on the
line above `notes`:

```scala
  private val notes = new PowerLines(words, wordings, procedures)
```

and widen the one pattern that names `Notes` without arguments:

```scala
            case ActionLineSource.Notes(_) | ActionLineSource.Silent(_) =>
              Vector.empty) ++
```

- [ ] **Step 6: Fix the properties suite's equality check**

In `GameLogPropertiesSuite.scala`, `Notes` is no longer a singleton:

```scala
    val noting = ProcedureRef.all.filter(
      logs.of(_).lines.isInstanceOf[ActionLineSource.Notes])
```

- [ ] **Step 7: Run the tests**

Run: `./sbtw "testOnly oathdigital.application.gamelog.ProcedureNoteLinesSuite oathdigital.application.gamelog.GameLogPropertiesSuite oathdigital.application.gamelog.GameLogGoldenSuite"`
Expected: PASS, and the golden files untouched — no note changed kind here.

- [ ] **Step 8: Record the two amendments in the design**

In `2026-10-16-procedures-journal-their-notes-design.md`, under
"Telling a procedure note from a power's", replace the closing paragraph —
"A procedure note renders at `LogKind.Action`. `PowerLines.kind` grows one
arm beside the `NoteKey.isUse` arm it already has." — with:

```
A procedure note renders at the kind its `ProcedureLog` entry declares,
`LogKind.Action` by default. **Amended during slice 2**, which found that
four of its twelve procedures post at another kind — Oathkeeper at
`Trigger`, Finish Rest at `Delta`, Setup at `Action` and `Decision`,
Negotiation at `Action` and `Delta` — so a single hardcoded `Action` could
not satisfy ruling 6. `ActionLineSource.Notes` carries a
`Map[NoteKey, LogKind]`, which keeps the kind in `application` exactly where
ruling 2 puts it. `PowerLines` looks the entry up through
`ProcedureNotes.procedureOf(power)`, not `run.procedure`.
```

Append to the "Campaign" section's "Two things slice 4 must settle first",
as a third subsection:

```
**A card in a slotted row reads as its back, not its slot.**
`LogWords.slotted` names a card the viewer cannot identify by its 1-based
slot — "facedown adviser (slot 2)" — where every other card word says "a
Denizen". Two callers have it: Negotiation's disclosure and gift lines, and
`ChoiceWords`' description of a parked adviser choice, which stays.
`NoteArg.Card` renders through `seen`, so Negotiation's two lines lose the
slot for a viewer who is not a party to the deal. **Accepted during slice
2** rather than paid for with a fifth argument kind: both negotiation
goldens name the card for both viewers, so no golden moves, and the ledger
carries the question of whether the slot should be every card line's
fallback or none's.
```

- [ ] **Step 9: Record the slot change in the wording ledger**

Append an entry to `2026-10-16-game-log-wording-ledger.md`:

```
## A facedown card's slot, in a negotiation only

`LogWords.slotted` (`LogWords.scala:62-76`) names a card the viewer cannot
identify by its row and 1-based slot, "facedown adviser (slot 2)". Nothing
else in the Game Log does: every other card word falls back to "a Denizen".
Slice 2 migrated Negotiation's two card lines to `NoteArg.Card` and so
dropped the slot for a viewer outside the deal. `slotted` keeps its other
caller, `ChoiceWords.scala:34`, which describes a parked adviser choice, so
the slot survives in one place and not the other.

The question is which fallback is right everywhere. A slot tells a reader
*which* of a player's facedown advisers moved, which a count does not; it
also leaks a position the deal's parties may not have shown. Decide it once
and apply it to every card line, not to one procedure and one choice.
```

- [ ] **Step 10: Run the full gate and commit**

```bash
git add -A && git commit -m "$(cat <<'MSGEOF'
feat(gamelog): let a procedure's registry entry declare its notes' kinds

Slice 1 rendered every procedure note at LogKind.Action. Four of slice 2's
twelve procedures post at another kind, so the entry declares it, keyed by
note key, and PowerLines reads it through the note's own procedure rather
than the run's.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

### Task 2: Travel journals its own line

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/travel/TravelProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala:47-50`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala:142-145`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala:28-35`
- Modify: `src/test/scala/oathdigital/gameplay/walker/UndeclaredNoteSuite.scala:81-88`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Rewrite: `src/test/resources/gamelog/round.{actor,other}.log`,
  `src/test/resources/gamelog/oathkeeper.{actor,other}.log`
- Rewrite: `src/test/resources/notes/declared-keys.txt`

**Interfaces:**
- Consumes: `ProcedureNotes.id`, `ActionLineSource.Notes` from Task 1.
- Produces: `TravelProcedure.travelled: NoteKey`,
  `TravelProcedure.noteKeys: Vector[NoteKey]`.

- [ ] **Step 1: Declare Travel's key**

In `TravelProcedure.scala`, add the import and the set. The object already
imports `oathdigital.model._`, so `NoteSet`, `NoteKey`, `NotePart`,
`NoteArg`, `Note` and `ActionRef` are in scope.

```scala
import oathdigital.gameplay.actions.ProcedureNotes
```

After `private val startLabel = "Start Travel"`:

```scala
  private val notes = NoteSet(ProcedureNotes.id(ActionRef.Travel))
  /** "Travelled to Broken Peaks". */
  val travelled: NoteKey = notes.key("travelled", Vector(
    NotePart.Text("Travelled to "), NotePart.Arg(0)))
  def noteKeys: Vector[NoteKey] = notes.declared
```

- [ ] **Step 2: Place the leaf and build the note**

Replace `routeTree`:

```scala
  /** Closes only over command-stable actor, route, and printed base. */
  private def routeTree(actor: PlayerId, source: SiteId, destination: SiteId,
      base: Int): Operation =
    Sequence(Vector(
      Sequence(Vector(
        SpendSupply(actor, base),
        Move(Piece.Pawn(actor),
          PositionedLocation(Location.Site(source)),
          PositionedLocation(Location.Site(destination)))),
        Some(PowerWindow.TravelCost)),
      // Outside the cost window and after it. Every Travel power finds the
      // route by flattening that window to its `Move`, so a note inside it
      // would be a child those powers rewrite; and reading the arrival off
      // the step means a power that rerouted the pawn is named correctly
      // rather than by the destination this tree closed over.
      Note(ProcedureNotes.id(ActionRef.Travel), travelledNote(actor))),
      Some(PowerWindow.TravelActionEligibility))

  /** Where the pawn arrived in the step before the note. A Travel whose
    * `Move` a power removed writes no line, as the rule this replaced wrote
    * none without a recorded pawn move. */
  private def travelledNote(actor: PlayerId)(states: NoteStates)
      : Option[PowerNote] = states.previous.flatMap: (before, after) =>
    actorSite(after, actor).filterNot(actorSite(before, actor).contains)
      .map(arrived => travelled.sourceless(NoteArg.Site(arrived)))
```

- [ ] **Step 3: Register the keys and switch the entry**

In `ProcedureNotes.scala`, add an arm above the wildcard and the import:

```scala
import oathdigital.gameplay.actions.travel.TravelProcedure
```

```scala
      case ActionRef.Travel => TravelProcedure.noteKeys
```

In `ProcedureLog.scala`:

```scala
    // The Travel line names the destination, and a travel power's own line
    // names what it was asked.
    case ActionRef.Travel => ProcedureLog(tag = Some("Travel"),
      lines = ActionLineSource.Notes(),
      anchor = starts.picksSettled,
      narratedPrefixes = Vector("travel."))
```

- [ ] **Step 4: Delete the rule**

In `ActionLines.scala`, delete `travelLines` entirely (lines 28-35).
`Piece.Pawn` is still matched by `setupLines`' helper and `Move` by several
others, so no import is orphaned; compile to be sure.

- [ ] **Step 5: Move the unmigrated-procedure test off Travel**

`UndeclaredNoteSuite`'s last test uses `ActionRef.Travel` as the procedure
that has not migrated. Travel just did. Point it at Campaign, which migrates
in slice 4:

```scala
  test("a procedure that has not migrated writes no note at all"):
    val campaign = ProcedureNotes.id(ActionRef.Campaign)
    assert(DeclaredNotes.of(catalog).knows(campaign),
      "an unmigrated procedure is covered by the union, so it is refused " +
        "rather than unrecognised")
    val thrown = intercept[AssertionError](NoteCheck.journal(declared, campaign,
      PowerNote(None, "campaigned", Vector.empty), covers = false))
    assert(thrown.getMessage.contains("procedure.campaign"), thrown.getMessage)
```

- [ ] **Step 6: Raise the properties suite's count**

```scala
    assertEquals(noting.map(_.key), Vector("travel", "muster"))
```

The order is `ProcedureRef.all`'s: `Travel` precedes `Muster` in
`ActionRef.all`.

- [ ] **Step 7: Rewrite the snapshot and the goldens, then read both diffs**

```bash
NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"
git diff src/test/resources/notes/declared-keys.txt
```

Expected: exactly one added line,
`procedure.travel travelled = Travelled to {0}`.

```bash
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
git diff src/test/resources/gamelog/
```

Expected: in `round.*` and `oathkeeper.*`, the `Travelled to …` entry's
sequence rises and its spans, `action`, depth `1` and `travel@…` section are
unchanged. No other file and no other line moves. Anything else is a defect:
revert the goldens and find it.

- [ ] **Step 8: Full gate and commit**

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
git add -A && git commit -m "$(cat <<'MSGEOF'
feat(gamelog): Travel journals its own line

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

### Task 3: Take Wealth journals its own line

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/phases/wake/TakeWealthProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala:165-166`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala:89-97`
- Modify: `src/test/scala/oathdigital/application/gamelog/ProcedureNoteLinesSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Rewrite: `src/test/resources/gamelog/take-wealth.{actor,other}.log`,
  `src/test/resources/gamelog/menu-wealth.{actor,other}.log`
- Rewrite: `src/test/resources/notes/declared-keys.txt`

**Interfaces:**
- Consumes: Task 1's mechanism; `NoteSupport.favor`, `NoteSupport.secrets`.
- Produces: `TakeWealthProcedure.tookFavor`, `.tookSecret`, `.noteKeys`.

- [ ] **Step 1: Write the failing test for the branch no script plays**

Both Take Wealth goldens take favor. Append to
`ProcedureNoteLinesSuite.scala`, inside the class:

```scala
  private val brokenPeaks = SiteId("site:broken-peaks")

  test("Take Wealth of a secret reads as the rule wrote it"):
    assertEquals(spoken(ActionRef.TakeWealth, TakeWealthProcedure.tookSecret,
        NoteArg.Site(brokenPeaks)),
      Vector[LogSpan](Text("Took 1 secret from "), words.site(brokenPeaks)))
```

and the import:

```scala
import oathdigital.gameplay.phases.wake.TakeWealthProcedure
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.ProcedureNoteLinesSuite"`
Expected: FAIL to compile, `value tookSecret is not a member of TakeWealthProcedure`.

- [ ] **Step 3: Declare the two keys**

In `TakeWealthProcedure.scala`:

```scala
import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.gameplay.powers.NoteSupport
```

After `object TakeWealthProcedure:`:

```scala
  private val notes = NoteSet(ProcedureNotes.id(ActionRef.TakeWealth))
  /** "Took 1 favor from Broken Peaks". A Take Wealth takes exactly one, so
    * the count is printed rather than carried. */
  val tookFavor: NoteKey = notes.key("took-favor", Vector(
    NotePart.Text("Took 1 favor from "), NotePart.Arg(0)))
  val tookSecret: NoteKey = notes.key("took-secret", Vector(
    NotePart.Text("Took 1 secret from "), NotePart.Arg(0)))
  def noteKeys: Vector[NoteKey] = notes.declared
```

- [ ] **Step 4: Place the leaf and build the note**

Replace `tree`:

```scala
  private def tree(actor: PlayerId, site: SiteId,
      resource: WakeResource): Operation = Sequence(Vector(
    Take(piece(resource), actor, Location.Site(site),
      Location.PlayArea(actor)),
    // Directly after the take, so `previous` is the take: a take a power
    // emptied moves nothing and writes no line, as the rule this replaced
    // wrote none without a recorded `Move`. The use record follows, and a
    // `Note` is not a leaf, so placing it here changes nothing else.
    Note(ProcedureNotes.id(ActionRef.TakeWealth),
      tookNote(actor, resource, site)),
    RecordPowerUse(TakeWealthLimit.useRef(site))),
    Some(PowerWindow.WakeTakeWealth))

  private def tookNote(actor: PlayerId, resource: WakeResource, site: SiteId)(
      states: NoteStates): Option[PowerNote] =
    states.previous.map(step => resource match
      case WakeResource.Favor => NoteSupport.favor(step, actor)
      case WakeResource.Secret => NoteSupport.secrets(step, actor))
      .filter(_ > 0).map(_ => key(resource).sourceless(NoteArg.Site(site)))

  private def key(resource: WakeResource): NoteKey = resource match
    case WakeResource.Favor => tookFavor
    case WakeResource.Secret => tookSecret
```

- [ ] **Step 5: Register, switch the entry, delete the rule**

In `ProcedureNotes.scala`, with
`import oathdigital.gameplay.phases.wake.TakeWealthProcedure`:

```scala
      case ActionRef.TakeWealth => TakeWealthProcedure.noteKeys
```

In `ProcedureLog.scala`:

```scala
    // Wake's own action: its lines stand outside any band.
    case ActionRef.TakeWealth => ProcedureLog(tag = None,
      lines = ActionLineSource.Notes(), isSection = false)
```

In `ActionLines.scala`, delete `takeWealthLines` (lines 89-97). The
companion's `resource` still serves `challengeLines`, `bannerLines` and
`isTradeGain`; leave it.

- [ ] **Step 6: Raise the count**

```scala
    assertEquals(noting.map(_.key), Vector("travel", "take-wealth", "muster"))
```

- [ ] **Step 7: Rewrite the snapshot and the goldens, then read both diffs**

```bash
NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
git diff src/test/resources/
```

Expected: two added snapshot lines; in `take-wealth.*` and `menu-wealth.*`
the `Took 1 favor from …` entry's sequence rises and nothing else changes.
The entry carries no section in either file, and must still carry none.

- [ ] **Step 8: Full gate and commit**

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
git add -A && git commit -m "$(cat <<'MSGEOF'
feat(gamelog): Take Wealth journals its own line

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

### Task 4: Forge journals its own two lines

Forge posts two entries at one event today — `6.0` "Placed favor on …" and
`6.1` "Forged …". As notes they become two `PowerNoted` events at
consecutive sequences, each at ordinal `0`. The order between them must not
change.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/forge/ForgeProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala:137-139`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala:122-145`
- Modify: `src/test/scala/oathdigital/application/gamelog/ProcedureNoteLinesSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Rewrite: `src/test/resources/gamelog/forge.{actor,other}.log`
- Rewrite: `src/test/resources/notes/declared-keys.txt`

**Interfaces:**
- Consumes: Task 1's mechanism; `NoteSupport.relicsGained`.
- Produces: `ForgeProcedure.paidFavor`, `.paidSecrets`, `.paidBoth`,
  `.forged`, `.noteKeys`.

- [ ] **Step 1: Write the failing tests for the branches no script plays**

The Forge script pays three favor at a site printing three of one resource.
Four of the seven forgeable sites print a single resource, so the
mixed-cost payment — the only case where the line joins two halves — is
played by no script, and neither is an all-secrets payment. Append to
`ProcedureNoteLinesSuite.scala`:

```scala
  private val roar: CardId = DenizenId("179")
  private val mushrooms: CardId = DenizenId("183")
  private val mercenaries: CardId = DenizenId("12")

  test("a Forge paying secrets only reads as the rule wrote it"):
    assertEquals(spoken(ActionRef.Forge, ForgeProcedure.paidSecrets,
        NoteArg.Cards(Vector(roar, mushrooms, mercenaries))),
      Vector[LogSpan](Text("Placed secrets on "), Text("3 Denizens")))

  test("a Forge paying both joins the halves as the rule joined them"):
    assertEquals(spoken(ActionRef.Forge, ForgeProcedure.paidBoth,
        NoteArg.Cards(Vector(roar, mushrooms)),
        NoteArg.Cards(Vector(mercenaries))),
      Vector[LogSpan](Text("Placed favor on "), Text("2 Denizens"),
        Text(" and secrets on "), Text("a Denizen")))
```

With no states behind them the cards render as their backs, which is what
makes the join readable in a literal; the named rendering is what the
golden proves.

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.ProcedureNoteLinesSuite"`
Expected: FAIL to compile, `value paidSecrets is not a member of ForgeProcedure`.

- [ ] **Step 3: Declare the four keys**

In `ForgeProcedure.scala`, add `import oathdigital.gameplay.actions.ProcedureNotes`
and `import oathdigital.gameplay.powers.NoteSupport`, and after
`val assignmentDecisionId`:

```scala
  private val notes = NoteSet(ProcedureNotes.id(ActionRef.Forge))
  /** "Placed favor on Threatening Roar, Mushrooms and Mercenaries". */
  val paidFavor: NoteKey = notes.key("paid-favor", Vector(
    NotePart.Text("Placed favor on "), NotePart.Arg(0)))
  val paidSecrets: NoteKey = notes.key("paid-secrets", Vector(
    NotePart.Text("Placed secrets on "), NotePart.Arg(0)))
  /** A site printing both resources: the two halves as `LogWords.join`
    * joined them, "Placed favor on A and B and secrets on C". */
  val paidBoth: NoteKey = notes.key("paid-both", Vector(
    NotePart.Text("Placed favor on "), NotePart.Arg(0),
    NotePart.Text(" and secrets on "), NotePart.Arg(1)))
  val forged: NoteKey = notes.key("forged", Vector(
    NotePart.Text("Forged "), NotePart.Arg(0)))
  def noteKeys: Vector[NoteKey] = notes.declared
```

- [ ] **Step 4: Place the two leaves and build the notes**

In `forging`, replace the `nodes` value:

```scala
    // Both notes follow the node that paid and forged, and a `Note` is not
    // a leaf, so both read that node as `previous`. The payment line comes
    // first, as it does today.
    val paid = Note(ProcedureNotes.id(ActionRef.Forge), paidNote(actor, cost))
    val made = Note(ProcedureNotes.id(ActionRef.Forge), forgedNote(actor))

    val nodes: Vector[Operation] =
      if parks(cost) then
        Vector(paySupply, assignmentDecide, forgeRelic, paid, made)
      else Vector(paySupply, forgeRelic, paid, made)
```

and add, as members of the object beside `forging`:

```scala
  /** What the Forge placed, read the way the node that placed it read it:
    * the answered split for a site printing both resources, and every
    * eligible target at the state before the payment for a site printing
    * one. Nothing placed writes nothing. */
  private def paidNote(actor: PlayerId, cost: Tokens)(states: NoteStates)
      : Option[PowerNote] = states.previous.flatMap { (before, _) =>
    val (favor, secrets) =
      if parks(cost) then answeredSplit(states.answered)
      else
        val targets = eligibleTargets(before, actor).map(_.denizenId: CardId)
        if cost.favor > 0 then (targets, Vector.empty) else (Vector.empty, targets)
    if favor.nonEmpty && secrets.nonEmpty then
      Some(paidBoth.sourceless(NoteArg.Cards(favor), NoteArg.Cards(secrets)))
    else if favor.nonEmpty then Some(paidFavor.sourceless(NoteArg.Cards(favor)))
    else if secrets.nonEmpty then
      Some(paidSecrets.sourceless(NoteArg.Cards(secrets)))
    else None
  }

  /** The assignment answer's denizens, by section. */
  private def answeredSplit(answered: Vector[Answered])
      : (Vector[CardId], Vector[CardId]) =
    val placements = answered.collectFirst {
      case Answered(`assignmentDecisionId`, PartitionAnswer(values), _) => values
    }.getOrElse(Vector.empty)
    def section(key: String): Vector[CardId] = placements.collect {
      case DecisionPlacement(DecisionOptionRef.Denizen(id), `key`) => id
    }
    (section(favorSectionKey), section(secretSectionKey))

  /** The relic that reached the actor in the step before the note. A Forge
    * whose `Play` a power removed writes no line, as the rule this replaced
    * wrote none without a recorded play off the relic deck. */
  private def forgedNote(actor: PlayerId)(states: NoteStates)
      : Option[PowerNote] = states.previous
    .flatMap(NoteSupport.relicsGained(_, actor).headOption)
    .map(relic => forged.sourceless(NoteArg.Card(relic)))
```

- [ ] **Step 5: Register, switch the entry, delete the rule**

In `ProcedureNotes.scala`, with
`import oathdigital.gameplay.actions.forge.ForgeProcedure`:

```scala
      case ActionRef.Forge => ForgeProcedure.noteKeys
```

In `ProcedureLog.scala`:

```scala
    // The payment line tells the assignment, in one of three wordings, so
    // no single key names it.
    case ActionRef.Forge => ProcedureLog(tag = Some("Forge"),
      lines = ActionLineSource.Notes(),
      narrated = Vector(Told(ForgeProcedure.assignmentDecisionId)))
```

In `ActionLines.scala`, delete `forgeLines` (lines 122-145). Compile and
remove whatever import that orphans — `Play` and `CardDeck.Relic` are
matched nowhere else in the file, but both arrive through
`oathdigital.model._`, so expect no import change.

- [ ] **Step 6: Raise the count**

```scala
    assertEquals(noting.map(_.key),
      Vector("forge", "travel", "take-wealth", "muster"))
```

- [ ] **Step 7: Rewrite the snapshot and the goldens, then read both diffs**

```bash
NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
git diff src/test/resources/
```

Expected: four added snapshot lines; in `forge.*` the two entries that were
`6.0` and `6.1` are now at two consecutive sequences, both at ordinal `0`,
**in the same order**, with the same spans, `action`, depth and `forge@4`
section. The payment line reading second is a defect.

- [ ] **Step 8: Full gate and commit**

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
git add -A && git commit -m "$(cat <<'MSGEOF'
feat(gamelog): Forge journals its own lines

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

### Task 5: Challenge journals its own line

The line names the banner, its holder and what was paid, and the holder is
a player or the bank while the payment is favor or secrets. That is four
wordings of one sentence, so Challenge declares four keys and its entry
names none of them in `Told`.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/challenge/ChallengeProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala:154-157`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala:151-163,354-362`
- Modify: `src/test/scala/oathdigital/application/gamelog/ProcedureNoteLinesSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Rewrite: `src/test/resources/gamelog/banners.{actor,other}.log`
- Rewrite: `src/test/resources/notes/declared-keys.txt`

**Interfaces:**
- Consumes: Task 1's mechanism; `NoteSupport.favor`, `NoteSupport.secrets`,
  `BannerRules.holder`.
- Produces: `ChallengeProcedure.tookFromPlayerWithFavor`,
  `.tookFromBankWithFavor`, `.tookFromPlayerWithSecrets`,
  `.tookFromBankWithSecrets`, `.noteKeys`.

- [ ] **Step 1: Write the failing tests for the three branches no script plays**

The banners script takes People's Favor from the bank. Append to
`ProcedureNoteLinesSuite.scala`:

```scala
  private val rival = PlayerId("p2")

  test("a Challenge against a holder reads as the rule wrote it"):
    assertEquals(spoken(ActionRef.Challenge,
        ChallengeProcedure.tookFromPlayerWithFavor,
        NoteArg.Banner(Banner.PeoplesFavor), NoteArg.Player(rival),
        NoteArg.Number(2)),
      Vector[LogSpan](Text("Took "), words.banner(Banner.PeoplesFavor),
        Text(" from "), words.player(rival), Text(" with "), Text("2"),
        Text(" favor")))

  test("a Challenge for Darkest Secret counts secrets"):
    assertEquals(spoken(ActionRef.Challenge,
        ChallengeProcedure.tookFromBankWithSecrets,
        NoteArg.Banner(Banner.DarkestSecret), NoteArg.Number(2)),
      Vector[LogSpan](Text("Took "), words.banner(Banner.DarkestSecret),
        Text(" from the bank with "), Text("2"), Text(" "), Text("secrets")))

  test("a Challenge for one secret says secret"):
    assertEquals(spoken(ActionRef.Challenge,
        ChallengeProcedure.tookFromPlayerWithSecrets,
        NoteArg.Banner(Banner.DarkestSecret), NoteArg.Player(rival),
        NoteArg.Number(1)),
      Vector[LogSpan](Text("Took "), words.banner(Banner.DarkestSecret),
        Text(" from "), words.player(rival), Text(" with "), Text("1"),
        Text(" "), Text("secret")))
```

Several adjacent `Text` spans are what the template produces and what the
golden concatenates; assert them as they come rather than merged.

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.ProcedureNoteLinesSuite"`
Expected: FAIL to compile, `value tookFromPlayerWithFavor is not a member of ChallengeProcedure`.

- [ ] **Step 3: Declare the four keys**

In `ChallengeProcedure.scala`, add `import oathdigital.gameplay.actions.ProcedureNotes`
and `import oathdigital.gameplay.powers.NoteSupport`, and after
`val startLabel`:

```scala
  private val notes = NoteSet(ProcedureNotes.id(ActionRef.Challenge))
  /** "Took People's Favor from p2 with 2 favor". Favor never pluralises;
    * secrets do, which is why the unit is in the template and not an
    * argument. */
  val tookFromPlayerWithFavor: NoteKey = notes.key("took-from-player-favor",
    Vector(NotePart.Text("Took "), NotePart.Arg(0), NotePart.Text(" from "),
      NotePart.Arg(1), NotePart.Text(" with "), NotePart.Arg(2),
      NotePart.Text(" favor")))
  val tookFromBankWithFavor: NoteKey = notes.key("took-from-bank-favor",
    Vector(NotePart.Text("Took "), NotePart.Arg(0),
      NotePart.Text(" from the bank with "), NotePart.Arg(1),
      NotePart.Text(" favor")))
  val tookFromPlayerWithSecrets: NoteKey = notes.key(
    "took-from-player-secrets",
    Vector(NotePart.Text("Took "), NotePart.Arg(0), NotePart.Text(" from "),
      NotePart.Arg(1), NotePart.Text(" with "), NotePart.Arg(2),
      NotePart.Text(" "), NotePart.Plural(2, "secret", "secrets")))
  val tookFromBankWithSecrets: NoteKey = notes.key("took-from-bank-secrets",
    Vector(NotePart.Text("Took "), NotePart.Arg(0),
      NotePart.Text(" from the bank with "), NotePart.Arg(1),
      NotePart.Text(" "), NotePart.Plural(1, "secret", "secrets")))
  def noteKeys: Vector[NoteKey] = notes.declared
```

- [ ] **Step 4: Place the leaf and build the note**

In `effects`, replace the placement sequence:

```scala
    yield Vector[Operation](
      Sequence(ChallengeRibbon.steps(actor, banner),
        Some(PowerWindow.ChallengeRibbon)),
      Sequence(Vector[Operation](payment(actor, banner, amount),
        // Directly after the payment, so `previous` is the payment and the
        // note reads what actually left the play area rather than the
        // amount answered. The holder is read here, before the take moves
        // the banner, which is the holder the deleted rule read off the
        // recorded `Take`.
        Note(ProcedureNotes.id(ActionRef.Challenge), tookNote(actor, banner,
          BannerRules.holder(ready.game.current, banner))),
        custody(ready, actor, banner)), Some(PowerWindow.ChallengePlacement))))
```

and add beside `payment`:

```scala
  /** What the challenger put on the banner, in the step before the note,
    * and who held it. Nothing paid writes nothing. */
  private def tookNote(actor: PlayerId, banner: Banner,
      holder: Option[PlayerId])(states: NoteStates): Option[PowerNote] =
    states.previous.map(step => banner match
      case Banner.PeoplesFavor => -NoteSupport.favor(step, actor)
      case Banner.DarkestSecret => -NoteSupport.secrets(step, actor))
      .filter(_ > 0).map(paid => holder match
        case Some(rival) => fromPlayer(banner).sourceless(
          NoteArg.Banner(banner), NoteArg.Player(rival), NoteArg.Number(paid))
        case None => fromBank(banner).sourceless(
          NoteArg.Banner(banner), NoteArg.Number(paid)))

  private def fromPlayer(banner: Banner): NoteKey = banner match
    case Banner.PeoplesFavor => tookFromPlayerWithFavor
    case Banner.DarkestSecret => tookFromPlayerWithSecrets

  private def fromBank(banner: Banner): NoteKey = banner match
    case Banner.PeoplesFavor => tookFromBankWithFavor
    case Banner.DarkestSecret => tookFromBankWithSecrets
```

- [ ] **Step 5: Register, switch the entry, delete the rule**

In `ProcedureNotes.scala`, with
`import oathdigital.gameplay.actions.challenge.ChallengeProcedure`:

```scala
      case ActionRef.Challenge => ChallengeProcedure.noteKeys
```

In `ProcedureLog.scala`:

```scala
    // The take line tells both decisions, in one of four wordings, so no
    // single key names them.
    case ActionRef.Challenge => ProcedureLog(tag = Some("Challenge"),
      lines = ActionLineSource.Notes(),
      narrated = Vector(Told(ChallengeProcedure.bannerDecisionId),
        Told(ChallengeProcedure.amountDecisionId)))
```

In `ActionLines.scala`, delete `challengeLines` (lines 151-163) and its only
caller-less helper `paidOnto` (lines 354-362).

- [ ] **Step 6: Raise the count**

```scala
    assertEquals(noting.map(_.key),
      Vector("forge", "travel", "take-wealth", "muster", "challenge"))
```

- [ ] **Step 7: Rewrite the snapshot and the goldens, then read both diffs**

```bash
NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
git diff src/test/resources/
```

Expected: four added snapshot lines; in `banners.*` the
`Took People's Favor from the bank with 2 favor` entry's sequence **falls**,
because the note now posts at the payment rather than at the take, and its
spans, `action`, depth and `challenge@4` section are unchanged. The
`place-banner-resource@22` line below it must not move at all.

- [ ] **Step 8: Full gate and commit**

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
git add -A && git commit -m "$(cat <<'MSGEOF'
feat(gamelog): Challenge journals its own line

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

### Task 6: Place Banner Resource journals its own line

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/challenge/PlaceBannerResourceProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala:168-171`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala:165-174`
- Modify: `src/test/scala/oathdigital/application/gamelog/ProcedureNoteLinesSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Rewrite: `src/test/resources/gamelog/banners.{actor,other}.log`
- Rewrite: `src/test/resources/notes/declared-keys.txt`

**Interfaces:**
- Consumes: Task 1's mechanism; `NoteSupport.favor`, `NoteSupport.secrets`.
- Produces: `PlaceBannerResourceProcedure.placedFavor`, `.placedSecrets`,
  `.noteKeys`.

- [ ] **Step 1: Write the failing test for the branch no script plays**

The banners script places favor. Append to `ProcedureNoteLinesSuite.scala`:

```scala
  test("placing secrets on a banner reads as the rule wrote it"):
    assertEquals(spoken(ActionRef.PlaceBannerResource,
        PlaceBannerResourceProcedure.placedSecrets, NoteArg.Number(2),
        NoteArg.Banner(Banner.DarkestSecret)),
      Vector[LogSpan](Text("Placed "), Text("2"), Text(" "), Text("secrets"),
        Text(" on "), words.banner(Banner.DarkestSecret)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.ProcedureNoteLinesSuite"`
Expected: FAIL to compile, `value placedSecrets is not a member of PlaceBannerResourceProcedure`.

- [ ] **Step 3: Declare the two keys**

In `PlaceBannerResourceProcedure.scala`, add
`import oathdigital.gameplay.actions.ProcedureNotes` and
`import oathdigital.gameplay.powers.NoteSupport`, and after
`val amountDecisionId`:

```scala
  private val notes = NoteSet(ProcedureNotes.id(ActionRef.PlaceBannerResource))
  /** "Placed 1 favor on People's Favor". */
  val placedFavor: NoteKey = notes.key("placed-favor", Vector(
    NotePart.Text("Placed "), NotePart.Arg(0), NotePart.Text(" favor on "),
    NotePart.Arg(1)))
  val placedSecrets: NoteKey = notes.key("placed-secrets", Vector(
    NotePart.Text("Placed "), NotePart.Arg(0), NotePart.Text(" "),
    NotePart.Plural(0, "secret", "secrets"), NotePart.Text(" on "),
    NotePart.Arg(1)))
  def noteKeys: Vector[NoteKey] = notes.declared
```

- [ ] **Step 4: Place the leaf and build the note**

Replace `placement`:

```scala
  private def placement(actor: PlayerId, pending: PendingTree): Vector[Operation] =
    (for
      banner <- bannerOf(pending)
      amount <- amountOf(pending)
    yield Vector[Operation](Sequence(Vector[Operation](Move(banner match {
      case Banner.PeoplesFavor => Piece.Favor(amount)
      case Banner.DarkestSecret => Piece.Secrets(amount)
    }, PositionedLocation(Location.PlayArea(actor)),
      PositionedLocation(Location.OnBanner(banner))),
      // Directly after the move, so `previous` is the move and the note
      // reads what actually left the play area rather than the amount
      // answered.
      Note(ProcedureNotes.id(ActionRef.PlaceBannerResource),
        placedNote(actor, banner))),
      Some(PowerWindow.PlaceBannerResourcePlacement)))).getOrElse(
      Vector[Operation](BuildOps((_, _) => Left(OathViolation.InvalidEventOrder(
        "Place Banner Resource reached its move without a banner and an amount")))))

  /** What reached the banner in the step before the note. Nothing placed
    * writes nothing, as the rule this replaced wrote none without a
    * recorded `Move` onto the banner. */
  private def placedNote(actor: PlayerId, banner: Banner)(states: NoteStates)
      : Option[PowerNote] =
    states.previous.map(step => banner match
      case Banner.PeoplesFavor => -NoteSupport.favor(step, actor)
      case Banner.DarkestSecret => -NoteSupport.secrets(step, actor))
      .filter(_ > 0).map(placed => key(banner).sourceless(
        NoteArg.Number(placed), NoteArg.Banner(banner)))

  private def key(banner: Banner): NoteKey = banner match
    case Banner.PeoplesFavor => placedFavor
    case Banner.DarkestSecret => placedSecrets
```

- [ ] **Step 5: Register, switch the entry, delete the rule**

In `ProcedureNotes.scala`, with
`import oathdigital.gameplay.actions.challenge.PlaceBannerResourceProcedure`:

```scala
      case ActionRef.PlaceBannerResource =>
        PlaceBannerResourceProcedure.noteKeys
```

In `ProcedureLog.scala`:

```scala
    // No modifier window, so no tag. The placement line tells both
    // decisions, in one of two wordings, so no single key names them.
    case ActionRef.PlaceBannerResource => ProcedureLog(tag = None,
      lines = ActionLineSource.Notes(),
      narrated = Vector(Told(PlaceBannerResourceProcedure.bannerDecisionId),
        Told(PlaceBannerResourceProcedure.amountDecisionId)))
```

In `ActionLines.scala`, delete `bannerLines` (lines 165-174).

- [ ] **Step 6: Raise the count**

```scala
    assertEquals(noting.map(_.key), Vector("forge", "travel", "take-wealth",
      "muster", "challenge", "place-banner-resource"))
```

- [ ] **Step 7: Rewrite the snapshot and the goldens, then read both diffs**

```bash
NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
git diff src/test/resources/
```

Expected: two added snapshot lines; in `banners.*` the
`Placed 1 favor on People's Favor` entry's sequence rises by one and its
spans, `action`, depth and `place-banner-resource@22` section are unchanged.
The Challenge line above it must not move.

- [ ] **Step 8: Full gate and commit**

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
git add -A && git commit -m "$(cat <<'MSGEOF'
feat(gamelog): Place Banner Resource journals its own line

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

### Task 7: Peek Site Relics journals its own line

`MinorActionProcedures` builds three procedures in one object, so it gets
three `NoteSet`s — one per procedure id — and three `noteKeys` defs. This
task adds the first; Tasks 8 and 9 add the others to the same object.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/minor/MinorActionProcedures.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala:176-177`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala:176-185`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Rewrite: `src/test/resources/gamelog/peek-relics.{actor,other}.log`
- Rewrite: `src/test/resources/notes/declared-keys.txt`

**Interfaces:**
- Consumes: Task 1's mechanism.
- Produces: `MinorActionProcedures.peeked: NoteKey`,
  `MinorActionProcedures.peekKeys: Vector[NoteKey]`.

- [ ] **Step 1: Declare the key**

In `MinorActionProcedures.scala`, add
`import oathdigital.gameplay.actions.ProcedureNotes`, and after the
`toBoard` value:

```scala
  /** One set per procedure, because each is held to its own declared keys.
    * `NoteSet` is read through a `def`, so these are plain `val`s. */
  private val peekNotes = NoteSet(ProcedureNotes.id(ActionRef.PeekSiteRelics))
  /** "Peeked at Sticky Fire at Broken Peaks". A site holds at most two
    * relics, so the list stays the inline phrase and never becomes a
    * `Cards` span. */
  val peeked: NoteKey = peekNotes.key("peeked", Vector(
    NotePart.Text("Peeked at "), NotePart.Arg(0), NotePart.Text(" at "),
    NotePart.Arg(1)))
  def peekKeys: Vector[NoteKey] = peekNotes.declared
```

- [ ] **Step 2: Place the leaf and build the note**

Replace `peekTree`:

```scala
  /** Every relic at the pawn's site, peeked at. */
  private def peekTree(actor: PlayerId): Operation =
    Sequence(Vector(
      BuildOps((live, _) =>
        Right(sitedPawn(live, actor).toVector.flatMap { (siteId, site) =>
          site.relics.map(relic => Peek(actor, relic.id, Location.Site(siteId)))
        }), required = true),
      // After the peeks, so `previous` is the step that peeked; the relics
      // are read at the state before it, which is where the deleted rule
      // read them off the recorded `Peek` operations.
      Note(ProcedureNotes.id(ActionRef.PeekSiteRelics), peekedNote(actor))))

  /** The relics at the pawn's site before the peek. A site with none writes
    * no line, as the rule this replaced posted none without a recorded
    * `Peek`. */
  private def peekedNote(actor: PlayerId)(states: NoteStates)
      : Option[PowerNote] = states.previous.flatMap((before, _) =>
    sitedPawn(before, actor).filter(_._2.relics.nonEmpty).map:
      (siteId, site) => peeked.sourceless(
        NoteArg.Cards(site.relics.map(_.id: CardId)), NoteArg.Site(siteId)))
```

- [ ] **Step 3: Register, switch the entry, delete the rule**

In `ProcedureNotes.scala`, with
`import oathdigital.gameplay.actions.minor.MinorActionProcedures`:

```scala
      case ActionRef.PeekSiteRelics => MinorActionProcedures.peekKeys
```

In `ProcedureLog.scala`:

```scala
    case ActionRef.PeekSiteRelics => ProcedureLog(tag = None,
      lines = ActionLineSource.Notes())
```

In `ActionLines.scala`, delete `peekLines` (lines 176-185) and the
`/** The minor actions post their legacy lines, now as action lines. */`
comment that opens it, which belongs to the three rules Tasks 7 to 9
remove.

- [ ] **Step 4: Raise the count**

```scala
    assertEquals(noting.map(_.key), Vector("forge", "travel", "take-wealth",
      "muster", "challenge", "place-banner-resource", "peek-site-relics"))
```

- [ ] **Step 5: Rewrite the snapshot and the golden, then read both diffs**

```bash
NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
git diff src/test/resources/
```

Expected: one added snapshot line; in `peek-relics.*` the
`Peeked at [card:R01|Sticky Fire] at …` entry's sequence rises and the card
is still a `[card:…]` span, not a back. A back here would mean the note's
wider state set reads the relic differently than the rule did, which is a
defect.

- [ ] **Step 6: Full gate and commit**

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
git add -A && git commit -m "$(cat <<'MSGEOF'
feat(gamelog): Peek Site Relics journals its own line

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

### Task 8: Reveal Owned Relic journals its own line

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/minor/MinorActionProcedures.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala:178-179`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala:187-194`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Rewrite: `src/test/resources/gamelog/reveal-relic.{actor,other}.log`
- Rewrite: `src/test/resources/notes/declared-keys.txt`

**Interfaces:**
- Consumes: Task 1's mechanism.
- Produces: `MinorActionProcedures.revealed: NoteKey`,
  `MinorActionProcedures.revealKeys: Vector[NoteKey]`.

- [ ] **Step 1: Declare the key**

In `MinorActionProcedures.scala`, beside the peek set:

```scala
  private val revealNotes =
    NoteSet(ProcedureNotes.id(ActionRef.RevealOwnedRelic))
  /** "Revealed Sticky Fire". */
  val revealed: NoteKey = revealNotes.key("revealed", Vector(
    NotePart.Text("Revealed "), NotePart.Arg(0)))
  def revealKeys: Vector[NoteKey] = revealNotes.declared
```

- [ ] **Step 2: Place the leaf and build the note**

Replace `revealTree`:

```scala
  /** The relic to reveal, then its reveal. */
  private def revealTree(ready: ReadyGame, actor: PlayerId): Operation =
    Sequence(Vector(
      Decide(relicDecisionId, actor, DecisionQuery.ChooseOne(
        facedownRelics(ready, actor).map(id =>
          DecisionOption.Relic(DecisionOptionRef.Relic(id))),
        heading = Some("Choose a relic to reveal")),
        presentation = DecisionPresentation.Panel),
      BuildOps((_, pending) => Right(pending.answered.collectFirst {
        case Answered(`relicDecisionId`, DecisionAnswer.ChooseOneAnswer(
            DecisionOptionRef.Relic(id)), _) =>
          Reveal(id, Location.PlayArea(actor))
      }.toVector), required = true),
      // After the reveal, so `previous` is the step that turned the relic:
      // a reveal a power removed turns nothing and writes no line, as the
      // rule this replaced posted none without a recorded `Reveal`.
      Note(ProcedureNotes.id(ActionRef.RevealOwnedRelic),
        revealedNote(actor))))

  private def revealedNote(actor: PlayerId)(states: NoteStates)
      : Option[PowerNote] = for
    (before, after) <- states.previous
    relic <- states.answered.collectFirst {
      case Answered(`relicDecisionId`, DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.Relic(id)), _) => id
    }
    if faceup(after, actor, relic) && !faceup(before, actor, relic)
  yield revealed.sourceless(NoteArg.Card(relic))

  private def faceup(ready: ReadyGame, actor: PlayerId,
      relic: RelicId): Boolean =
    ready.game.current.players.find(_.player == actor).exists(
      _.relics.exists(held => held.id == relic &&
        held.orientation == Orientation.FaceUp))
```

- [ ] **Step 3: Register, switch the entry, delete the rule**

In `ProcedureNotes.scala`:

```scala
      case ActionRef.RevealOwnedRelic => MinorActionProcedures.revealKeys
```

In `ProcedureLog.scala`:

```scala
    case ActionRef.RevealOwnedRelic => ProcedureLog(tag = None,
      lines = ActionLineSource.Notes())
```

The relic decision stays narrated by nothing: `reveal-relic.*` shows a
`Chose [card:R01|Sticky Fire]` line above the reveal line, and that line
remains. Do not add a `Told` here.

In `ActionLines.scala`, delete `revealLines` (lines 187-194).

- [ ] **Step 4: Raise the count**

```scala
    assertEquals(noting.map(_.key), Vector("forge", "travel", "take-wealth",
      "muster", "challenge", "place-banner-resource", "peek-site-relics",
      "reveal-owned-relic"))
```

- [ ] **Step 5: Rewrite the snapshot and the golden, then read both diffs**

```bash
NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
git diff src/test/resources/
```

Expected: one added snapshot line; in `reveal-relic.*` the
`Revealed [card:R01|Sticky Fire]` entry's sequence rises by one, the
`Chose …` line above it does not move, and the Recover lines in the same
file do not move.

- [ ] **Step 6: Full gate and commit**

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
git add -A && git commit -m "$(cat <<'MSGEOF'
feat(gamelog): Reveal Owned Relic journals its own line

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

### Task 9: Move Warbands journals its own line

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/minor/MinorActionProcedures.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala:180-181`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala:196-210`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Rewrite: `src/test/resources/gamelog/warbands-moved.{actor,other}.log`
- Rewrite: `src/test/resources/notes/declared-keys.txt`

**Interfaces:**
- Consumes: Task 1's mechanism; `NoteSupport.warbands`.
- Produces: `MinorActionProcedures.movedToSite`, `.movedFromSite`,
  `.moveKeys`.

Both directions are played by `warbands-moved.*`, so this task needs no
uncovered-branch test.

- [ ] **Step 1: Declare the two keys**

In `MinorActionProcedures.scala`, add
`import oathdigital.gameplay.powers.NoteSupport`, and beside the other two
sets:

```scala
  private val moveNotes = NoteSet(ProcedureNotes.id(ActionRef.MoveWarbands))
  /** "Moved 2 warbands to Broken Peaks". */
  val movedToSite: NoteKey = moveNotes.key("moved-to-site", Vector(
    NotePart.Text("Moved "), NotePart.Arg(0), NotePart.Text(" "),
    NotePart.Plural(0, "warband", "warbands"), NotePart.Text(" to "),
    NotePart.Arg(1)))
  /** "Moved 1 warband from Broken Peaks". */
  val movedFromSite: NoteKey = moveNotes.key("moved-from-site", Vector(
    NotePart.Text("Moved "), NotePart.Arg(0), NotePart.Text(" "),
    NotePart.Plural(0, "warband", "warbands"), NotePart.Text(" from "),
    NotePart.Arg(1)))
  def moveKeys: Vector[NoteKey] = moveNotes.declared
```

- [ ] **Step 2: Place the leaf and build the note**

In `moveTree`, append the note after the move's `BuildOps`:

```scala
      BuildOps((live, pending) => Right((for
        picked <- chosenWay(pending.answered)
        amount <- pending.answered.collectFirst {
          case Answered(`amountDecisionId`,
              DecisionAnswer.ChooseAmountAnswer(value), _) => value
        }
        operation <- move(live, actor, picked, amount)
      yield operation).toVector), required = true),
      // After the move, so `previous` is the move and the note reads how
      // many warbands actually travelled rather than the amount answered.
      Note(ProcedureNotes.id(ActionRef.MoveWarbands), movedNote(actor))))
```

and add beside `move`:

```scala
  /** How many warbands moved, and the site they moved to or from. The count
    * is the change in the actor's own board bank: moving to the site empties
    * it, moving to the board fills it. Nothing moved writes nothing. */
  private def movedNote(actor: PlayerId)(states: NoteStates)
      : Option[PowerNote] = for
    step <- states.previous
    way <- chosenWay(states.answered)
    change = NoteSupport.warbands(step, actor)
    moved = if way == toSite then -change else change
    if moved > 0
    site <- sitedPawn(step._1, actor).map(_._1)
  yield (if way == toSite then movedToSite else movedFromSite)
    .sourceless(NoteArg.Number(moved), NoteArg.Site(site))
```

- [ ] **Step 3: Register, switch the entry, delete the rule**

In `ProcedureNotes.scala`:

```scala
      case ActionRef.MoveWarbands => MinorActionProcedures.moveKeys
```

In `ProcedureLog.scala`:

```scala
    case ActionRef.MoveWarbands => ProcedureLog(tag = None,
      lines = ActionLineSource.Notes())
```

The direction decision stays narrated by nothing: `warbands-moved.*` shows
`Chose To your site` above each move line, and those lines remain.

In `ActionLines.scala`, delete `warbandLines` (lines 196-210). That removes
the file's last use of `plural` from within the class; the companion's
`plural` is still called by `PowerLines.argument` and
`PowerLines.clause`, so leave it.

- [ ] **Step 4: Raise the count**

```scala
    assertEquals(noting.map(_.key), Vector("forge", "travel", "take-wealth",
      "muster", "challenge", "place-banner-resource", "peek-site-relics",
      "reveal-owned-relic", "move-warbands"))
```

- [ ] **Step 5: Rewrite the snapshot and the golden, then read both diffs**

```bash
NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
git diff src/test/resources/
```

Expected: two added snapshot lines; in `warbands-moved.*` both move entries'
sequences rise, the `Moved 2 warbands to …` / `Moved 1 warband from …`
wordings are unchanged, the two `Chose …` lines do not move, and the
`Oathkeeper passed to …` line between them does not move.

- [ ] **Step 6: Full gate and commit**

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
git add -A && git commit -m "$(cat <<'MSGEOF'
feat(gamelog): Move Warbands journals its own line

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

### Task 10: Oathkeeper journals its own line, at `Trigger`

The first task to use Task 1's kind declaration. `oathkeeper.*` and
`warbands-moved.*` both show `trigger 1 | Oathkeeper passed to …`, and that
`trigger` must survive.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/oathkeeper/OathkeeperProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala:194-197`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala:37-43`
- Modify: `src/test/scala/oathdigital/application/gamelog/ProcedureNoteLinesSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Rewrite: `src/test/resources/gamelog/oathkeeper.{actor,other}.log`,
  `src/test/resources/gamelog/warbands-moved.{actor,other}.log`
- Rewrite: `src/test/resources/notes/declared-keys.txt`

**Interfaces:**
- Consumes: Task 1's `ActionLineSource.Notes(kinds)` and
  `ProcedureLog.noteKind`.
- Produces: `OathkeeperProcedure.passedToPlayer`, `.passedToBank`,
  `.noteKeys`.

- [ ] **Step 1: Write the failing tests — the bank branch, and the kind**

No script passes the title to the bank. Append to
`ProcedureNoteLinesSuite.scala`:

```scala
  test("the Oathkeeper passing to the bank reads as the rule wrote it"):
    assertEquals(spoken(TriggeredProcedureRef.Oathkeeper,
        OathkeeperProcedure.passedToBank),
      Vector[LogSpan](Text("Oathkeeper passed to the bank")))

  test("an Oathkeeper line posts at Trigger, not Action"):
    assertEquals(kindOf(TriggeredProcedureRef.Oathkeeper,
      OathkeeperProcedure.passedToPlayer), LogKind.Trigger)
    assertEquals(kindOf(TriggeredProcedureRef.Oathkeeper,
      OathkeeperProcedure.passedToBank), LogKind.Trigger)
```

with `import oathdigital.gameplay.oathkeeper.OathkeeperProcedure`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.ProcedureNoteLinesSuite"`
Expected: FAIL to compile, `value passedToBank is not a member of OathkeeperProcedure`.

- [ ] **Step 3: Declare the two keys**

In `OathkeeperProcedure.scala`, add
`import oathdigital.gameplay.actions.ProcedureNotes`, and after
`val recipientDecisionId`:

```scala
  private val notes =
    NoteSet(ProcedureNotes.id(TriggeredProcedureRef.Oathkeeper))
  /** "Oathkeeper passed to p1". */
  val passedToPlayer: NoteKey = notes.key("passed-to-player", Vector(
    NotePart.Text("Oathkeeper passed to "), NotePart.Arg(0)))
  /** No leader qualifies, so the title returns to the bank. */
  val passedToBank: NoteKey = notes.key("passed-to-bank", Vector(
    NotePart.Text("Oathkeeper passed to the bank")))
  def noteKeys: Vector[NoteKey] = notes.declared
```

- [ ] **Step 4: Place the leaf in both branches and build the note**

Replace the two `Right(Sequence(...))` arms of `build`:

```scala
      case OathkeeperOutcome.Transfer(holder) =>
        Right(Sequence(Vector(SetOathkeeper(holder),
          Note(ProcedureNotes.id(TriggeredProcedureRef.Oathkeeper),
            passedNote)),
          Some(PowerWindow.OathkeeperTitleChange)))
      case OathkeeperOutcome.Choose(holder, candidates) => Right(Sequence(Vector(
        Decide(
          decisionId = recipientDecisionId,
          owner = holder,
          query = DecisionQuery.ChooseOne(candidates.map(candidate =>
            DecisionOption.Player(DecisionOptionRef.Player(candidate))),
            heading = Some("Choose the Oathkeeper"))),
        BuildOps((_, pending) => pending.answered.lastOption match {
          case Some(Answered(`recipientDecisionId`,
              ChooseOneAnswer(DecisionOptionRef.Player(chosen)), _)) =>
            Right(Vector[CoreOperation](SetOathkeeper(Some(chosen))))
          case _ => Left(OathViolation.InvalidEventOrder(
            "no Oathkeeper recipient answer is recorded"))
        }),
        Note(ProcedureNotes.id(TriggeredProcedureRef.Oathkeeper),
          passedNote)), Some(PowerWindow.OathkeeperTitleChange)))
```

and add to the object:

```scala
  /** Who holds the title after the step before the note, when that step
    * changed it. `SetOathkeeper` rejects an unchanged holder, so the rule
    * this replaced posted nothing without a recorded change; the note reads
    * the same fact off the state rather than off the operation. */
  private def passedNote(states: NoteStates): Option[PowerNote] =
    states.previous.filter((before, after) => holder(before) != holder(after))
      .map((_, after) => holder(after).fold(passedToBank.sourceless())(
        taker => passedToPlayer.sourceless(NoteArg.Player(taker))))

  private def holder(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.title.holder
```

- [ ] **Step 5: Register, switch the entry, delete the rule**

In `ProcedureNotes.scala`, with
`import oathdigital.gameplay.oathkeeper.OathkeeperProcedure`:

```scala
      case TriggeredProcedureRef.Oathkeeper => OathkeeperProcedure.noteKeys
```

In `ProcedureLog.scala` — the file already imports `OathkeeperProcedure`,
and Task 1 added `LogKind` to its projection import:

```scala
    // A title change is a trigger, not an action: the lines stand outside
    // any band and read at the kind the rule they replaced posted at.
    case TriggeredProcedureRef.Oathkeeper => ProcedureLog(tag = None,
      lines = ActionLineSource.Notes(Map(
        OathkeeperProcedure.passedToPlayer -> LogKind.Trigger,
        OathkeeperProcedure.passedToBank -> LogKind.Trigger)),
      narrated = Vector(Told(OathkeeperProcedure.recipientDecisionId,
        Some(OathkeeperProcedure.passedToPlayer))),
      isSection = false)
```

The recipient decision is asked only in the `Choose` branch, which always
sets a player, so `passedToPlayer` is the one key that tells it.

In `ActionLines.scala`, delete `oathkeeperLines` (lines 37-43). That removes
the file's only `Posted(LogKind.Trigger, …)`; `LogKind` is still used by
`action` in the companion, so the import stays.

- [ ] **Step 6: Raise the count**

```scala
    assertEquals(noting.map(_.key), Vector("forge", "travel", "take-wealth",
      "muster", "challenge", "place-banner-resource", "peek-site-relics",
      "reveal-owned-relic", "move-warbands", "oathkeeper"))
```

- [ ] **Step 7: Rewrite the snapshot and the goldens, then read both diffs**

```bash
NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
git diff src/test/resources/
```

Expected: two added snapshot lines; in `oathkeeper.*` and
`warbands-moved.*` the `Oathkeeper passed to [player:p1|p1]` entry's
sequence rises by one and the entry still reads `trigger 1` with no
section. An `action 1` there means Task 1's kind map is not reaching
`PowerLines`; fix that rather than the golden.

- [ ] **Step 8: Full gate and commit**

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
git add -A && git commit -m "$(cat <<'MSGEOF'
feat(gamelog): Oathkeeper journals its own line

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

### Task 11: Finish Rest journals its own line, at `Delta`

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/phases/rest/FinishRestProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala:192-193`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala:45-52`
- Modify: `src/test/scala/oathdigital/application/gamelog/ProcedureNoteLinesSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Rewrite: `src/test/resources/gamelog/round.{actor,other}.log`
- Rewrite: `src/test/resources/notes/declared-keys.txt`

**Interfaces:**
- Consumes: Task 1's kind declaration; `NoteSupport.board`.
- Produces: `FinishRestProcedure.supplyIncreased`, `.noteKeys`.

- [ ] **Step 1: Write the failing kind test**

The wording is played by `round.*`, so only the kind needs its own test.
Append to `ProcedureNoteLinesSuite.scala`:

```scala
  test("a Finish Rest line posts at Delta, not Action"):
    assertEquals(kindOf(PhaseTransitionRef.FinishRest,
      FinishRestProcedure.supplyIncreased), LogKind.Delta)
```

with `import oathdigital.gameplay.phases.rest.FinishRestProcedure`.

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.ProcedureNoteLinesSuite"`
Expected: FAIL to compile, `value supplyIncreased is not a member of FinishRestProcedure`.

- [ ] **Step 3: Declare the key**

In `FinishRestProcedure.scala`, add
`import oathdigital.gameplay.actions.ProcedureNotes` and
`import oathdigital.gameplay.powers.NoteSupport`, and after
`val ExileSupply`:

```scala
  private val notes = NoteSet(ProcedureNotes.id(PhaseTransitionRef.FinishRest))
  /** "Increased supply from 5 to 7". Both numbers are plain, as the rule
    * this replaced wrote them into one `Text` span. */
  val supplyIncreased: NoteKey = notes.key("supply-increased", Vector(
    NotePart.Text("Increased supply from "), NotePart.Arg(0),
    NotePart.Text(" to "), NotePart.Arg(1)))
  def noteKeys: Vector[NoteKey] = notes.declared
```

- [ ] **Step 4: Place the leaf and build the note**

Replace the `yield` of `build`:

```scala
  yield Sequence(Vector(
    BuildOps((state, _) => RestCleanup.operations(catalog, state, player),
      window = Some(PowerWindow.RestReturnFavor)),
    BuildOps((state, _) => supplyRefresh(state, player)),
    // Between the refresh and the hand-over, so `previous` is the refresh.
    // The rule this replaced matched only a `GainSupply`, so a refresh that
    // spent Supply posted nothing; requiring a rise reads the same.
    Note(ProcedureNotes.id(PhaseTransitionRef.FinishRest),
      supplyNote(player)),
    BuildOps((state, _) => beginNextTurn(state, player))))

  private def supplyNote(resting: PlayerId)(states: NoteStates)
      : Option[PowerNote] = states.previous.flatMap { (before, after) =>
    val was = supplyOf(before, resting)
    val now = supplyOf(after, resting)
    Option.when(now > was)(supplyIncreased.sourceless(
      NoteArg.Number(was), NoteArg.Number(now)))
  }

  private def supplyOf(ready: ReadyGame, player: PlayerId): Int =
    NoteSupport.board(ready, player).fold(0)(_.supply.supply)
```

- [ ] **Step 5: Register, switch the entry, delete the rule**

In `ProcedureNotes.scala`, with
`import oathdigital.gameplay.phases.rest.FinishRestProcedure`:

```scala
      case PhaseTransitionRef.FinishRest => FinishRestProcedure.noteKeys
```

In `ProcedureLog.scala`, add
`import oathdigital.gameplay.phases.rest.FinishRestProcedure` and:

```scala
    // A Supply refresh is a delta, not an action, and stands outside any
    // band as the rule it replaced did.
    case PhaseTransitionRef.FinishRest => ProcedureLog(tag = None,
      lines = ActionLineSource.Notes(Map(
        FinishRestProcedure.supplyIncreased -> LogKind.Delta)),
      isSection = false)
```

In `ActionLines.scala`, delete `finishRestLines` (lines 45-52). That removes
the file's last use of `LogJournal.supply`; the method is still called by
`LogJournal.supplySpent`, so leave it.

- [ ] **Step 6: Raise the count**

`FinishRest` sits in `PhaseTransitionRef.all`, which `ProcedureRef.all`
places after every `ActionRef` and before every `TriggeredProcedureRef`:

```scala
    assertEquals(noting.map(_.key), Vector("forge", "travel", "take-wealth",
      "muster", "challenge", "place-banner-resource", "peek-site-relics",
      "reveal-owned-relic", "move-warbands", "finish-rest", "oathkeeper"))
```

- [ ] **Step 7: Rewrite the snapshot and the goldens, then read both diffs**

```bash
NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
git diff src/test/resources/
```

Expected: one added snapshot line; in `round.*` the
`28.0 delta 1 | Increased supply from 5 to 7` entry's sequence rises by one
and it still reads `delta 1` with no section. The `[player:p2|p2]'s turn`
and `Wake` headlines that follow it come from the hand-over step after the
note, so they must still come after it and keep their own sequence.

- [ ] **Step 8: Full gate and commit**

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
git add -A && git commit -m "$(cat <<'MSGEOF'
feat(gamelog): Finish Rest journals its own line

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

### Task 12: Negotiation journals its own lines, and `NegotiationLines` goes

Negotiation posts an opening line and one `delta` per disclosure and
transfer. Ruling 9 deletes the rule outright, so all of them become notes:
six keys, four of them at `LogKind.Delta`. The settlement notes are built
from the same `DealState` the settling node is built from, in the same
order — `NegotiationDeal.settle` is `knowledge(deal) ++ transfers(deal)`,
so disclosures come first.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/negotiation/NegotiationProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/negotiation/NegotiationDeal.scala:39-51`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala:172-175`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala:21-26,212-214`
- Delete: `src/main/scala/oathdigital/application/gamelog/NegotiationLines.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/ProcedureNoteLinesSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Rewrite: `src/test/resources/gamelog/negotiation-agreed.{actor,other}.log`,
  `negotiation-declined.{actor,other}.log`,
  `negotiation-disclosed.{actor,other}.log`
- Rewrite: `src/test/resources/notes/declared-keys.txt`

**Interfaces:**
- Consumes: Task 1's kind declaration; `NegotiationDeal.deal`,
  `NegotiationDeal.settle`, `DealState`.
- Produces: `NegotiationProcedure.negotiated`, `.ended`, `.gaveFavor`,
  `.gaveCard`, `.showed`, `.wasShown`, `.noteKeys`; and
  `NegotiationDeal.chosen`.

- [ ] **Step 1: Write the failing tests for the branches no script plays**

The three negotiation scripts play a favor gift, a decline and an adviser
disclosure. A relic gift, a site-relic disclosure, and a disclosed card the
viewer cannot identify are all unplayed. Append to
`ProcedureNoteLinesSuite.scala`:

```scala
  private val stickyFire: CardId = RelicId("R01")

  test("a relic given in a negotiation reads as the rule wrote it"):
    assertEquals(spoken(ActionRef.Negotiation, NegotiationProcedure.gaveCard,
        NoteArg.Player(PlayerId("p1")), NoteArg.Card(stickyFire),
        NoteArg.Player(rival)),
      Vector[LogSpan](words.player(PlayerId("p1")), Text(" gave "),
        Text("a Relic"), Text(" to "), words.player(rival)))

  test("a site relic shown in a negotiation reads as the rule wrote it"):
    assertEquals(spoken(ActionRef.Negotiation, NegotiationProcedure.wasShown,
        NoteArg.Player(rival), NoteArg.Card(stickyFire),
        NoteArg.Site(brokenPeaks)),
      Vector[LogSpan](words.player(rival), Text(" was shown "),
        Text("a Relic"), Text(" at "), words.site(brokenPeaks)))

  // Accepted in slice 2 (design, "Two things slice 4 must settle first"):
  // the deleted rule read this card through `LogWords.slotted` and wrote
  // "facedown adviser (slot 1)". A note's card word has no slot fallback.
  test("a disclosed card a viewer cannot identify reads as its back"):
    assertEquals(spoken(ActionRef.Negotiation, NegotiationProcedure.showed,
        NoteArg.Player(rival), NoteArg.Player(PlayerId("p1")),
        NoteArg.Card(DenizenId("176"))),
      Vector[LogSpan](words.player(rival), Text(" showed "),
        words.player(PlayerId("p1")), Text(" "), Text("a Denizen")))

  test("every Negotiation settlement line posts at Delta"):
    Vector(NegotiationProcedure.gaveFavor, NegotiationProcedure.gaveCard,
      NegotiationProcedure.showed, NegotiationProcedure.wasShown).foreach(key =>
        assertEquals(kindOf(ActionRef.Negotiation, key), LogKind.Delta,
          s"${key.name}"))

  test("a Negotiation's opening lines post at Action"):
    assertEquals(kindOf(ActionRef.Negotiation,
      NegotiationProcedure.negotiated), LogKind.Action)
    assertEquals(kindOf(ActionRef.Negotiation, NegotiationProcedure.ended),
      LogKind.Action)
```

with `import oathdigital.gameplay.actions.negotiation.NegotiationProcedure`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.ProcedureNoteLinesSuite"`
Expected: FAIL to compile, `value gaveCard is not a member of NegotiationProcedure`.

- [ ] **Step 3: Give `NegotiationDeal` the negotiator list in answered order**

The opening line names the players in the order the answer named them, not
in table order, and `participants` reorders. Split the read out so both use
one definition. Replace `participants` in `NegotiationDeal.scala`:

```scala
  /** The players the actor chose, in the order the answer named them, or
    * the one eligible candidate when the choice was omitted. The Game Log's
    * opening line names them in this order. */
  def chosen(state: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[PlayerId] =
    pending.answered.reverse.collectFirst {
      case Answered(`negotiatorsDecisionId`, ChooseManyAnswer(selected), _) =>
        selected.collect { case DecisionOptionRef.Player(id) => id }
    }.getOrElse(eligible(state, actor))

  /** Actor first, then the chosen players in table order. With no recorded
    * negotiator answer the one eligible candidate is the negotiator: the
    * tree omits the choice when there is only one.
    */
  def participants(state: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[PlayerId] =
    val picked = chosen(state, actor, pending).toSet
    actor +: state.game.current.players.map(_.player).filter(picked)
```

- [ ] **Step 4: Declare the six keys**

In `NegotiationProcedure.scala`, add
`import oathdigital.gameplay.actions.ProcedureNotes` and
`import oathdigital.model.DecisionAnswer.DeclineDeal`, and after
`object NegotiationProcedure:`:

```scala
  private val notes = NoteSet(ProcedureNotes.id(ActionRef.Negotiation))
  /** "Negotiated with p2", or "Negotiated with p2 and p3". */
  val negotiated: NoteKey = notes.key("negotiated", Vector(
    NotePart.Text("Negotiated with "), NotePart.Arg(0)))
  val ended: NoteKey = notes.key("ended", Vector(
    NotePart.Text("Negotiation ended by "), NotePart.Arg(0)))
  /** "p1 gave 1 favor to p2". */
  val gaveFavor: NoteKey = notes.key("gave-favor", Vector(
    NotePart.Arg(0), NotePart.Text(" gave "), NotePart.Arg(1),
    NotePart.Text(" favor to "), NotePart.Arg(2)))
  val gaveCard: NoteKey = notes.key("gave-card", Vector(
    NotePart.Arg(0), NotePart.Text(" gave "), NotePart.Arg(1),
    NotePart.Text(" to "), NotePart.Arg(2)))
  /** "p2 showed p1 Birdsong". */
  val showed: NoteKey = notes.key("showed", Vector(
    NotePart.Arg(0), NotePart.Text(" showed "), NotePart.Arg(1),
    NotePart.Text(" "), NotePart.Arg(2)))
  /** "p1 was shown Sticky Fire at Broken Peaks". */
  val wasShown: NoteKey = notes.key("was-shown", Vector(
    NotePart.Arg(0), NotePart.Text(" was shown "), NotePart.Arg(1),
    NotePart.Text(" at "), NotePart.Arg(2)))
  def noteKeys: Vector[NoteKey] = notes.declared
```

- [ ] **Step 5: Place the leaves and build the notes**

Replace the closing `Branch` of `tree`:

```scala
      Branch((ready, pending) => {
        val deal = NegotiationDeal.deal(ready, actor, pending)
        // The opening line, as the rule posted it: the decline names who
        // ended the deal, and anything else names who was negotiated with,
        // whether or not the deal had substance to settle.
        val opening: Vector[Operation] = Vector(
          Note(ProcedureNotes.id(ActionRef.Negotiation),
            if deal.declined then endedNote
            else _ => Some(negotiated.sourceless(NoteArg.Players(
              NegotiationDeal.chosen(ready, actor, pending))))))
        // The settlement's lines, in the order `settle` applies them:
        // disclosures first, then transfers. A refused settlement fails the
        // command, so nothing is journaled and no note is reached.
        val settlement: Vector[Operation] =
          if !deal.agreed then Vector.empty
          else BuildOps((state, _) => NegotiationDeal.settle(state, deal),
            window = Some(PowerWindow.NegotiationSettlement)) +:
            (disclosed(deal) ++ transferred(deal)).map(note =>
              Note(ProcedureNotes.id(ActionRef.Negotiation), _ => Some(note)))
        opening ++ settlement
      })))
```

and add to the object:

```scala
  private def endedNote(states: NoteStates): Option[PowerNote] =
    states.answered.reverse.collectFirst {
      case Answered(NegotiationDeal.dealDecisionId, DeclineDeal, by) =>
        ended.sourceless(NoteArg.Player(by))
    }

  /** One line per disclosure, in `NegotiationDeal.knowledge`'s order. */
  private def disclosed(deal: DealState): Vector[PowerNote] =
    deal.participants.flatMap(deal.terms(_).disclosures).map:
      case NegotiationDisclosure(recipient,
          NegotiationDisclosureRef.Adviser(owner, card)) =>
        showed.sourceless(NoteArg.Player(owner), NoteArg.Player(recipient),
          NoteArg.Card(card))
      case NegotiationDisclosure(recipient,
          NegotiationDisclosureRef.HeldRelic(owner, relic)) =>
        showed.sourceless(NoteArg.Player(owner), NoteArg.Player(recipient),
          NoteArg.Card(relic))
      case NegotiationDisclosure(recipient,
          NegotiationDisclosureRef.SiteRelic(site, relic)) =>
        wasShown.sourceless(NoteArg.Player(recipient), NoteArg.Card(relic),
          NoteArg.Site(site))

  /** One line per transfer, in `NegotiationDeal.transfers`' order: each
    * author's favor before their relics. */
  private def transferred(deal: DealState): Vector[PowerNote] =
    deal.participants.flatMap(author =>
      deal.terms(author).transfers.flatMap(transfer =>
        Option.when(transfer.favor > 0)(gaveFavor.sourceless(
          NoteArg.Player(author), NoteArg.Number(transfer.favor),
          NoteArg.Player(transfer.recipient))).toVector ++
          transfer.relics.map(relic => gaveCard.sourceless(
            NoteArg.Player(author), NoteArg.Card(relic),
            NoteArg.Player(transfer.recipient)))))
```

- [ ] **Step 6: Register, switch the entry, delete the rule and its file**

In `ProcedureNotes.scala`, with
`import oathdigital.gameplay.actions.negotiation.NegotiationProcedure`:

```scala
      case ActionRef.Negotiation => NegotiationProcedure.noteKeys
```

In `ProcedureLog.scala`, add
`import oathdigital.gameplay.actions.negotiation.NegotiationProcedure` and:

```scala
    // Every Negotiation line tells its own decision. The settlement lines
    // are deltas, as the rules they replaced were; the opening line is the
    // action's.
    case ActionRef.Negotiation => ProcedureLog(tag = None,
      lines = ActionLineSource.Notes(Map(
        NegotiationProcedure.gaveFavor -> LogKind.Delta,
        NegotiationProcedure.gaveCard -> LogKind.Delta,
        NegotiationProcedure.showed -> LogKind.Delta,
        NegotiationProcedure.wasShown -> LogKind.Delta)),
      narratedPrefixes = Vector("negotiation."))
```

In `ActionLines.scala`, delete `negotiationLines` (lines 212-214) and the
field `private val negotiations = new NegotiationLines(words)` (line 26).
Then delete `src/main/scala/oathdigital/application/gamelog/NegotiationLines.scala`.

`LogWords.slotted` keeps its other caller, `ChoiceWords.scala:34`. Leave it.

- [ ] **Step 7: Raise the count**

```scala
    assertEquals(noting.map(_.key), Vector("forge", "travel", "take-wealth",
      "muster", "challenge", "place-banner-resource", "negotiation",
      "peek-site-relics", "reveal-owned-relic", "move-warbands",
      "finish-rest", "oathkeeper"))
```

- [ ] **Step 8: Rewrite the snapshot and the goldens, then read all three diffs**

```bash
NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
git diff src/test/resources/
```

Expected: six added snapshot lines. In `negotiation-agreed.*` and
`negotiation-disclosed.*` the two entries that shared sequence 14 at
ordinals `0` and `1` are now at two different sequences, both at ordinal
`0`, **the opening line first**, with the same spans, the same `action` and
`delta` kinds, the same depth and the same `negotiation@4` section. In
`negotiation-declined.*` the `Negotiation ended by …` entry's sequence
rises. A `delta` before an `action`, or either line now reading `action`,
is a defect.

- [ ] **Step 9: Full gate and commit**

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
git add -A && git commit -m "$(cat <<'MSGEOF'
feat(gamelog): Negotiation journals its own lines, and NegotiationLines goes

Every Negotiation line becomes a note built from the DealState the settling
node is built from, in the order settle applies its operations. The
disclosure and gift lines lose LogWords.slotted's slot fallback for a
viewer outside the deal, which the wording ledger records.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

### Task 13: Setup journals its own lines, and `SetupLines` goes

Setup has no turn, so every line names its player, and the kept and
revealed lines post at `LogKind.Decision`. The reveal step asks for any
number of cards and posts one line each, and the answer does not exist when
the branch that asks is walked, so the notes come from a second `Branch`
after the reveal.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/setup/SetupProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala:198-201`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala:21-26,54-56`
- Delete: `src/main/scala/oathdigital/application/gamelog/SetupLines.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/ProcedureNoteLinesSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Rewrite: `src/test/resources/gamelog/woken.{actor,other}.log`
- Rewrite: `src/test/resources/notes/declared-keys.txt`

**Interfaces:**
- Consumes: Task 1's kind declaration.
- Produces: `SetupProcedure.placedPawn`, `.keptAdviser`, `.revealedCard`,
  `.noteKeys`.

- [ ] **Step 1: Write the failing tests for the branch no script plays**

No participant in the `woken` script has a card to reveal, so the revealed
line is unplayed. Append to `ProcedureNoteLinesSuite.scala`:

```scala
  test("a card revealed in Setup reads as the rule wrote it"):
    assertEquals(spoken(TriggeredProcedureRef.Setup,
        SetupProcedure.revealedCard, NoteArg.Player(rival),
        NoteArg.Card(DenizenId("205"))),
      Vector[LogSpan](words.player(rival), Text(" revealed "),
        Text("a Denizen")))

  test("Setup's kept and revealed lines post at Decision, its pawn at Action"):
    assertEquals(kindOf(TriggeredProcedureRef.Setup,
      SetupProcedure.placedPawn), LogKind.Action)
    assertEquals(kindOf(TriggeredProcedureRef.Setup,
      SetupProcedure.keptAdviser), LogKind.Decision)
    assertEquals(kindOf(TriggeredProcedureRef.Setup,
      SetupProcedure.revealedCard), LogKind.Decision)
```

with `import oathdigital.gameplay.setup.SetupProcedure`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.ProcedureNoteLinesSuite"`
Expected: FAIL to compile, `value revealedCard is not a member of SetupProcedure`.

- [ ] **Step 3: Declare the three keys**

In `SetupProcedure.scala`, add
`import oathdigital.gameplay.actions.ProcedureNotes`, and after
`val adviserDiscardKey`:

```scala
  private val notes = NoteSet(ProcedureNotes.id(TriggeredProcedureRef.Setup))
  /** "p2 placed pawn at Ancient City". Setup has no turn yet, so every line
    * names its player. */
  val placedPawn: NoteKey = notes.key("placed-pawn", Vector(
    NotePart.Arg(0), NotePart.Text(" placed pawn at "), NotePart.Arg(1)))
  val keptAdviser: NoteKey = notes.key("kept-adviser", Vector(
    NotePart.Arg(0), NotePart.Text(" kept "), NotePart.Arg(1)))
  val revealedCard: NoteKey = notes.key("revealed-card", Vector(
    NotePart.Arg(0), NotePart.Text(" revealed "), NotePart.Arg(1)))
  def noteKeys: Vector[NoteKey] = notes.declared
```

- [ ] **Step 4: Place the pawn and adviser leaves**

Replace `playerStep`'s `Sequence`:

```scala
    Sequence(Vector(
      Decide(pawnId, participant.playerId,
        DecisionQuery.ChooseOne(siteOptions,
          heading = Some("Choose your starting site"),
          confirmVerb = Some("Start at"))),
      BuildOps(placePawn(participant.playerId, pawnId),
        window = Some(PowerWindow.SetupPawnPlaced)),
      // Each note follows the node whose effect it states, so `previous`
      // is that node and the pawn's arrival is read off the state rather
      // than off the answer.
      Note(ProcedureNotes.id(TriggeredProcedureRef.Setup),
        placedPawnNote(participant.playerId)),
      Decide(adviserId, participant.playerId,
        DecisionQuery.Partition(
          Vector(DecisionSection(adviserKeepKey, "Keep", 1, Some(1)),
            DecisionSection(adviserDiscardKey, "Discard", 0)),
          handOptions, heading = Some("Choose your starting adviser"),
          confirmLabel = Some("Confirm Adviser"))),
      BuildOps(chooseAdviser(participant.playerId, adviserId)),
      Note(ProcedureNotes.id(TriggeredProcedureRef.Setup),
        keptAdviserNote(participant.playerId, adviserId)),
      revealCards(catalog, participant.playerId)))
```

and add:

```scala
  /** Where the pawn arrived in the step before the note. */
  private def placedPawnNote(player: PlayerId)(states: NoteStates)
      : Option[PowerNote] = states.previous.flatMap((before, after) =>
    pawnSiteOf(after, player).filterNot(pawnSiteOf(before, player).contains)
      .map(site => placedPawn.sourceless(NoteArg.Player(player),
        NoteArg.Site(site))))

  private def pawnSiteOf(ready: ReadyGame, player: PlayerId): Option[SiteId] =
    ready.game.current.players.find(_.player == player).flatMap(_.pawnSite)

  /** The adviser the answer kept. `adviserSelection` takes a `PendingTree`
    * and a note has only `states.answered`, so this reads the placements
    * directly rather than rebuilding a tree to borrow it. */
  private def keptAdviserNote(player: PlayerId, decisionId: String)(
      states: NoteStates): Option[PowerNote] =
    states.answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.PartitionAnswer(placements), _) =>
        placements.collect {
          case DecisionPlacement(DecisionOptionRef.Denizen(id),
              `adviserKeepKey`) => id
        }
    }.flatMap(_.headOption).map(kept =>
      keptAdviser.sourceless(NoteArg.Player(player), NoteArg.Card(kept)))
```

- [ ] **Step 5: Place one reveal leaf per revealed card**

The slot-to-id resolution is already inside `reveal`; lift it out so the
note and the operation read it one way. Replace `revealCards` and `reveal`:

```scala
  private def revealCards(catalog: ExecutableCatalog, player: PlayerId)
      : Operation =
    val decisionId = revealDecisionId(player)
    Branch((state, _) =>
      val options = revealable(catalog, state, player)
      if options.isEmpty then Vector.empty
      else Vector(
        Decide(decisionId, player, DecisionQuery.ChooseMany(0, options.size,
          options, heading = Some("Reveal cards"),
          confirm = Some(CountedConfirm("Reveal", "card", "cards",
            Some("Reveal nothing"))))),
        BuildOps(reveal(player, decisionId)),
        // One line per card, as the rule posted one per recorded `Reveal`.
        // A second `Branch`, because the answer this reads does not exist
        // when the branch above is walked.
        Branch((live, pending) =>
          revealed(live, player, decisionId, pending.answered).map(card =>
            Note(ProcedureNotes.id(TriggeredProcedureRef.Setup),
              _ => Some(revealedCard.sourceless(NoteArg.Player(player),
                NoteArg.Card(card))))))))

  /** The cards `player`'s reveal answer names, by slot. The one definition
    * the operations and the lines both read. */
  private def revealed(ready: ReadyGame, player: PlayerId, decisionId: String,
      answered: Vector[Answered]): Vector[CardId] =
    val held = ready.game.current.players.find(_.player == player)
    answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseManyAnswer(selected), _) =>
        selected.flatMap {
          case DecisionOptionRef.AdviserSlot(_, slot) =>
            held.flatMap(_.advisers.lift(slot)).map(_.id: CardId)
          case DecisionOptionRef.RelicSlot(_, slot) =>
            held.flatMap(_.relics.lift(slot)).map(_.id: CardId)
          case _ => None
        }
    }.getOrElse(Vector.empty)

  private def reveal(player: PlayerId, decisionId: String)
      : (ReadyGame, PendingTree) => Either[OathViolation, Vector[CoreOperation]] =
    (ready, pending) =>
      if pending.answered.exists(_.decisionId == decisionId) then
        Right(revealed(ready, player, decisionId, pending.answered)
          .map(card => Reveal(card, Location.PlayArea(player))))
      else Left(InvalidEventOrder(
        s"no reveal answer is recorded for $decisionId"))
```

- [ ] **Step 6: Register, switch the entry, delete the rule and its file**

In `ProcedureNotes.scala`, with
`import oathdigital.gameplay.setup.SetupProcedure`:

```scala
      case TriggeredProcedureRef.Setup => SetupProcedure.noteKeys
```

In `ProcedureLog.scala`, add `import oathdigital.gameplay.setup.SetupProcedure`
and:

```scala
    // Setup's own lines tell every decision it asks. Setup has no turn, so
    // the kept and revealed lines post as decisions, which is where the
    // rules they replaced posted them.
    case TriggeredProcedureRef.Setup => ProcedureLog(tag = None,
      lines = ActionLineSource.Notes(Map(
        SetupProcedure.keptAdviser -> LogKind.Decision,
        SetupProcedure.revealedCard -> LogKind.Decision)),
      narratedPrefixes = Vector("setup."), isSection = false)
```

In `ActionLines.scala`, delete `setupLines` (lines 54-56) and the field
`private val setup = new SetupLines(words)` (line 24). Then delete
`src/main/scala/oathdigital/application/gamelog/SetupLines.scala`.

- [ ] **Step 7: Raise the count for the last time**

```scala
    assertEquals(noting.map(_.key), Vector("forge", "travel", "take-wealth",
      "muster", "challenge", "place-banner-resource", "negotiation",
      "peek-site-relics", "reveal-owned-relic", "move-warbands",
      "finish-rest", "oathkeeper", "setup"))
```

- [ ] **Step 8: Rewrite the snapshot and the golden, then read both diffs**

```bash
NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
git diff src/test/resources/
```

Expected: three added snapshot lines; in `woken.*` the six Setup entries
rise in sequence, keep their order — each player's `placed pawn at` before
their `kept` — and keep their `action` and `decision` kinds, depth `1` and
absent section. The `Setup` round headline at `0.0` and the `Round 1` /
turn / `Wake` headlines after them must not move relative to the Setup
lines. In `woken.other.log` the `kept a Denizen` entries must still read as
backs.

- [ ] **Step 9: Full gate and commit**

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
git add -A && git commit -m "$(cat <<'MSGEOF'
feat(gamelog): Setup journals its own lines, and SetupLines goes

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

## Done when

- Twelve procedures carry their own `NoteSet` and a `Note` leaf, and their
  `ProcedureLog` entries read `ActionLineSource.Notes`.
  `GameLogPropertiesSuite` lists thirteen, Muster included.
- `NegotiationLines.scala` and `SetupLines.scala` are gone.
  `ActionLines.scala` has lost `travelLines`, `oathkeeperLines`,
  `finishRestLines`, `setupLines`, `takeWealthLines`, `forgeLines`,
  `challengeLines`, `paidOnto`, `bannerLines`, `peekLines`, `revealLines`,
  `warbandLines` and `negotiationLines`, and keeps `searchLines`,
  `tradeLines`, `recoverLines`, `campaignLines`, `playedAdviser`,
  `usePowerLines` and their helpers.
- Every golden file's diff across the slice is sequence and ordinal
  movement only. No span, kind, depth or section changed, and no two lines
  swapped order.
- `ProcedureNoteLinesSuite` holds one assertion per branch no script plays,
  and one per non-`Action` kind.
- `declared-keys.txt` has gained exactly the keys these twelve declare.
- The design records the kind declaration and the accepted loss of
  `slotted`'s slot in Negotiation's two card lines; the wording ledger
  carries the question of which card fallback is right everywhere.
- The full gate is green.

## What slice 3 inherits

- Six procedure rules in `ActionLines`: Search, Trade, Recover, the played
  adviser, Campaign and `usePowerLines`. Slice 3 takes the first four,
  slice 4 Campaign and the deletion.
- `ActionLines`' companion helpers (`action`, `plural`, `resource`,
  `PlacePrefix`, `subjectCard`, `worldCard`, `isDelta`, `sourceName`) are
  still shared with `PowerLines` and `CampaignLines`; slice 4 moves them to
  `LogWords`.
- One open wording question that blocks slice 4 and now has a second
  caller's worth of evidence: whether a card a viewer cannot identify
  should fall back to its slot, as `ChoiceWords` still does, or to its
  back, as every note now does.
