# Procedures Journal Their Own Notes — Slice 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the mechanism that lets a procedure journal its own Game Log
line, and prove it by migrating Muster so that `ActionLines.musterLines` is
deleted and the golden logs do not move.

**Architecture:** A procedure puts a `Note` leaf in the tree it already
builds, at the point its effect happens. The walker journals `PowerNoted`.
The formatter reads the wording off the `NoteKey` the procedure declared in
`model` vocabulary. `PowerNote.source` becomes optional so a procedure's line
renders without the `"{source}: "` prefix a power's line carries, and
`PowerLines` renders a procedure note at `LogKind.Action` rather than
`Trigger`. `ProcedureLog`'s `lines` becomes a sealed source so a migrated
procedure is distinguishable from a silent one.

**Tech Stack:** Scala 3.9.0 on the JVM, munit, ujson, built through `./sbtw`.

**Spec:** [docs/superpowers/specs/2026-10-16-procedures-journal-their-notes-design.md](../specs/2026-10-16-procedures-journal-their-notes-design.md)

**Scope:** Slice 1 of four. Slices 2 (twelve straightforward procedures),
3 (Search, Trade, Recover, played adviser) and 4 (Campaign, then the
deletion) each get their own plan, written once this one lands and the exact
names it produces are fixed.

## Global Constraints

- **Scala 3.9.0 with `-Werror`** (`build.sbt:61-68`). A non-exhaustive match
  or an unused import fails the build.
- **800-line production-file cap**, enforced by `scripts/check-architecture.py`
  and `BackendArchitectureSuite`.
- **`gameplay` may not import `application`, `persistence`, `presentation`,
  `protocol`, `serialization` or `server`** (`scripts/check-architecture.py`).
  `model` is free. This is why a procedure's wording is `NoteKey` /
  `NotePart` / `NoteArg` and never `LogSpan` or `LogKind`.
- **`model` may not import `application`, `gameplay`, `persistence`,
  `serialization` or `server`.**
- **Golden logs must be byte-identical at every commit in this plan.** Run
  `./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`.
  Never set `GAMELOG_GOLDEN=write` in this slice: a rewritten golden is the
  failure this plan is designed to detect.
- **A template must not introduce a typed span where the line writes plain
  text today.** `GoldenLog.render` prints `LogSpan.Text` as its bare string
  and `LogSpan.Amount` as `[amount:2 warbands]`. Today's Muster line is one
  `Text`, so its template uses `NotePart.Arg` over `NoteArg.Number` plus
  `NotePart.Plural` — **not** `NoteArg.Amount`. Several adjacent `Text` spans
  concatenate to the same golden string, so splitting a sentence across parts
  is safe; changing a span's *kind* is not.
- **`src/test/resources/notes/declared-keys.txt` is expected to change** in
  this plan, unlike in the derive-note-keys work it was written for. It is the
  record that a key landed, not an inertness proof. The inertness proof here
  is the golden log.
- **Worktree setup.** A fresh worktree has no `.tooling`. Symlink the main
  checkout's `.tooling` (and `node_modules`, if absent) into it before the
  first `./sbtw`.

---

### Task 1: Amend the designs and the vocabulary

No code. Four documents record that the mechanism moved, so a later reader
does not find four descriptions of the same lines and only two of them true.

**Files:**
- Modify: `CONTEXT.md:77-100`
- Modify: `docs/superpowers/specs/2026-10-16-game-log-procedure-registry-design.md`
- Modify: `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`
- Modify: `docs/superpowers/specs/2026-09-25-game-log-design.md`

**Interfaces:**
- Consumes: nothing.
- Produces: the vocabulary later tasks' doc comments cite.

- [ ] **Step 1: Widen the three `CONTEXT.md` note entries**

In `CONTEXT.md`, the **Note**, **Note key** and **Wording** entries each say
"a power". Replace that phrase with "a power, a game rule or a procedure" in
all three. In **Note**, replace the example sentence's lead-in so it reads:

```
**Note**:
One line of the Game Log in which a power, a game rule or a procedure says
what its own effect did: "Warning Signals: Blue redistributed their
warbands.", or "Mustered 2 warbands with Hearth" from the Muster procedure,
which writes no source. It is never text. It is an optional source, a key
and typed arguments, so a card it names still passes the log's knowledge
rule.
_Avoid_: log message, power text, log entry
```

- [ ] **Step 2: Add the boundary entry**

Append to the same `### Note` block of `CONTEXT.md`, after **Declared set**:

```
**Note boundary**:
The line between what a procedure may declare and what the Game Log keeps. A
procedure declares what it did — a note key, its wording and typed arguments,
all `model` types. It never names how the line is laid out: the kind, the
depth, the section, the tag's anchor, the placement order, covering, or
whether a decision is narrated. Those live in `application/gamelog`.
_Avoid_: layer rule, log boundary, gameplay/application split
```

- [ ] **Step 3: Point the three designs at the new one**

In `2026-10-16-game-log-procedure-registry-design.md`, append to ruling 3:

```
   Amended within its own scope by the
   [procedures journal their own notes design](2026-10-16-procedures-journal-their-notes-design.md),
   whose ruling 2 states the line it draws instead. That design is the
   "separate candidate" this document's "Out of scope" section names.
```

In the same file, append the same pointer sentence to the resolved decision
beginning "Narration is derived from the entries".

In `2026-09-26-power-log-lines-design.md`, append to ruling 2:

```
   Amended by the
   [procedures journal their own notes design](2026-10-16-procedures-journal-their-notes-design.md):
   a note's source is optional, and a note with none renders its sentence
   alone, at `LogKind.Action`.
```

In `2026-09-25-game-log-design.md`, add a line to the head of the "Action
lines" section:

```
> The rules below describe where a note is placed, not how a line is
> inferred, for every procedure migrated by the
> [procedures journal their own notes design](2026-10-16-procedures-journal-their-notes-design.md).
```

- [ ] **Step 4: Commit**

```bash
git add CONTEXT.md docs/superpowers/specs
git commit -m "docs: amend the note designs for procedure notes"
```

---

### Task 2: A note's source becomes optional

**Files:**
- Modify: `src/main/scala/oathdigital/model/PowerNotes.scala:7-8`
- Modify: `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala:121-123,155-159`
- Modify: `src/main/scala/oathdigital/application/gamelog/PowerLines.scala:46-54`
- Test: `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`

**Interfaces:**
- Consumes: nothing.
- Produces: `PowerNote(source: Option[PowerSourceRef], key: String, args: Vector[NoteArg])`;
  `NoteKey.apply(source: PowerSourceRef, args: NoteArg*): PowerNote` unchanged
  in signature; a new `NoteKey.sourceless(args: NoteArg*): PowerNote`.

- [ ] **Step 1: Write the failing codec tests**

Add to `GameEventWireSuite`:

```scala
test("a note with no source round-trips, and an old payload keeps its source"):
  val note = PowerNote(None, "mustered", Vector(NoteArg.Number(2)))
  val event = PowerNoted(PowerId("procedure.muster"), note, covers = false)
  val encoded = WalkerEventCodec.encode(event)
  assertEquals(WalkerEventCodec.decode(encoded, "event"), Right(event))
  assert(!encoded("note").obj.contains("source"),
    s"a sourceless note writes no source field: $encoded")

  val withSource = PowerNote(Some(PowerSourceRef.Card(DenizenId("39"))),
    "used", Vector.empty)
  val old = WalkerEventCodec.encode(
    PowerNoted(PowerId("denizen.wolves"), withSource, covers = false))
  assertEquals(WalkerEventCodec.decode(old, "event").map {
    case PowerNoted(_, decoded, _) => decoded.source
    case other => fail(s"not a note: $other")
  }, Right(Some(PowerSourceRef.Card(DenizenId("39")))))
```

Match the suite's existing `encode`/`decode` call shape and its imports; if
the suite wraps them in a helper, use the helper.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.serialization.GameEventWireSuite"`
Expected: FAIL to compile — `PowerNote` does not take an `Option`.

- [ ] **Step 3: Make the source optional in the model**

In `model/PowerNotes.scala`, change the case class and add the sourceless
constructor beside the existing one:

```scala
/** What one power, game rule or procedure says about its own effect, for the
  * Game Log (power log lines design, section 1; procedures journal their own
  * notes design, ruling 1). `source` is the card, site or banner the line
  * starts with, and is absent for a procedure's own line, which starts with
  * its sentence. `key` picks the template. `args` are typed references,
  * never text, so a card still passes the log's knowledge rule.
  */
final case class PowerNote(source: Option[PowerSourceRef], key: String,
    args: Vector[NoteArg])
```

In `NoteKey`, keep `apply` and add:

```scala
  def apply(source: PowerSourceRef, args: NoteArg*): PowerNote =
    PowerNote(Some(source), name, args.toVector)
  /** A line that starts with its sentence: a procedure's own. */
  def sourceless(args: NoteArg*): PowerNote =
    PowerNote(None, name, args.toVector)
```

- [ ] **Step 4: Make the codec write and read an optional source**

In `WalkerEventCodec.encodeNote`, build the object conditionally:

```scala
  private def encodeNote(note: PowerNote): ujson.Value =
    val fields = ujson.Obj("key" -> note.key,
      "args" -> ujson.Arr.from(note.args.map(encodeNoteArg)))
    note.source.foreach(source =>
      fields("source") = encodePowerSource(source))
    fields
```

In `decodePowerNote`, read it only when present:

```scala
    source <- value.obj.get("source") match
      case None => Right(None)
      case Some(field) =>
        decodePowerSource(field, s"$path.source").map(Some.apply)
```

- [ ] **Step 5: Render a sourceless note without a prefix**

In `PowerLines.spoken`, split the prefix out so an absent source contributes
nothing:

```scala
  def spoken(power: PowerId, note: PowerNote, seen: Vector[ReadyGame],
      viewer: Option[PlayerId]): Option[Vector[LogSpan]] =
    wordings.template(power, note.key).map(template =>
      prefix(power, note, seen, viewer) ++
        sentence(template, note.args, seen, viewer))

  /** "{source}: " for a power's or a rule's line, nothing for a
    * procedure's. */
  private def prefix(power: PowerId, note: PowerNote, seen: Vector[ReadyGame],
      viewer: Option[PlayerId]): Vector[LogSpan] =
    note.source.toVector.flatMap(source =>
      wordings.source(power).fold(words.source(source, seen, viewer))(
        named => Vector(Text(named))) :+ Text(": "))
```

- [ ] **Step 6: Fix every construction site the compiler names**

`./sbtw compile` lists each `PowerNote(...)` built positionally. Every one in
production is a power's or a rule's, so each takes `Some(...)`. Do not change
any `NoteKey.apply(source, args*)` call — its signature is unchanged. Test
fixtures that build a `PowerNote` directly take `Some(...)` too.

- [ ] **Step 7: Run the tests**

Run: `./sbtw "testOnly oathdigital.serialization.GameEventWireSuite"`
Expected: PASS

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`
Expected: PASS, every golden unchanged.

- [ ] **Step 8: Commit**

```bash
git add src/main src/test
git commit -m "feat(gamelog): a note's source is optional"
```

---

### Task 3: Three argument kinds

Ruling 7 closes the argument vocabulary this migration needs. `Players`
lands for Negotiation in slice 2; `Clauses` and `Placements` for Campaign in
slice 4. They land together here so the wire is reviewed once.

**Risk to carry forward:** Campaign may find `NoteClause` needs a case this
plan did not foresee. That would be a `model` and codec change in slice 4,
and a spec amendment under ruling 7.

**Files:**
- Modify: `src/main/scala/oathdigital/model/PowerNotes.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala:125-192`
- Modify: `src/main/scala/oathdigital/application/gamelog/PowerLines.scala:88-103`
- Test: `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`

**Interfaces:**
- Consumes: Task 2's `PowerNote`.
- Produces: `NoteArg.Players(ids: Vector[PlayerId])`,
  `NoteArg.Clauses(parts: Vector[NoteClause])`,
  `NoteArg.Placements(entries: Vector[(Int, SiteId)])`, and the sealed
  `NoteClause` with cases `LostWarbands(count: Int)`, `BurnedFavor(count: Int)`,
  `Discarded(ids: Vector[CardId])`, `SetAside(ids: Vector[CardId])`,
  `SentTo(site: SiteId)`.

- [ ] **Step 1: Write the failing round-trip and rejection tests**

Add to `GameEventWireSuite`:

```scala
test("the three new note argument kinds round-trip"):
  val args = Vector[NoteArg](
    NoteArg.Players(Vector(PlayerId("p1"), PlayerId("p2"))),
    NoteArg.Clauses(Vector(NoteClause.LostWarbands(2),
      NoteClause.BurnedFavor(1), NoteClause.Discarded(Vector(DenizenId("39"))),
      NoteClause.SetAside(Vector(RelicId("r1"))),
      NoteClause.SentTo(SiteId("s1")))),
    NoteArg.Placements(Vector(2 -> SiteId("s1"), 1 -> SiteId("s2"))))
  val event = PowerNoted(PowerId("procedure.campaign"),
    PowerNote(None, "lost", args), covers = false)
  assertEquals(WalkerEventCodec.decode(WalkerEventCodec.encode(event),
    "event"), Right(event))

test("an unknown note clause is a decode error"):
  val payload = WalkerEventCodec.encode(PowerNoted(PowerId("procedure.campaign"),
    PowerNote(None, "lost", Vector(NoteArg.Clauses(
      Vector(NoteClause.BurnedFavor(1))))), covers = false))
  payload("note")("args")(0)("clauses")(0)("kind") = "invented"
  assert(WalkerEventCodec.decode(payload, "event").isLeft,
    "an unknown clause kind must not decode")
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.serialization.GameEventWireSuite"`
Expected: FAIL to compile — `NoteArg.Players` does not exist.

- [ ] **Step 3: Add the model types**

In `model/PowerNotes.scala`, inside `object NoteArg`:

```scala
  /** Several players as one joined phrase, "Blue and Red". */
  final case class Players(ids: Vector[PlayerId]) extends NoteArg
  /** Several clauses as one joined phrase, "lost 2 warbands, burned 1 favor
    * and was sent to the Mine". The joining is the log's. */
  final case class Clauses(parts: Vector[NoteClause]) extends NoteArg
  /** Warbands placed by site, "2 warbands on the Mine and 1 warband on the
    * Standing Stones". */
  final case class Placements(entries: Vector[(Int, SiteId)]) extends NoteArg
```

Beside `NoteArg`, add the clause family:

```scala
/** One clause of a joined note sentence (procedures journal their own notes
  * design, "Campaign"). Closed, because each case is a thing the rules do
  * and the log has one way of saying each. */
sealed trait NoteClause extends Product with Serializable
object NoteClause:
  final case class LostWarbands(count: Int) extends NoteClause
  final case class BurnedFavor(count: Int) extends NoteClause
  final case class Discarded(ids: Vector[CardId]) extends NoteClause
  final case class SetAside(ids: Vector[CardId]) extends NoteClause
  final case class SentTo(site: SiteId) extends NoteClause
```

- [ ] **Step 4: Add the codec arms**

In `WalkerEventCodec.encodeNoteArg`:

```scala
    case NoteArg.Players(ids) => ujson.Obj("kind" -> "players",
      "playerIds" -> ujson.Arr.from(ids.map(_.value)))
    case NoteArg.Clauses(parts) => ujson.Obj("kind" -> "clauses",
      "clauses" -> ujson.Arr.from(parts.map(encodeNoteClause)))
    case NoteArg.Placements(entries) => ujson.Obj("kind" -> "placements",
      "placements" -> ujson.Arr.from(entries.map((count, site) =>
        ujson.Obj("count" -> count, "siteId" -> site.value))))

  private def encodeNoteClause(clause: NoteClause): ujson.Value = clause match
    case NoteClause.LostWarbands(count) =>
      ujson.Obj("kind" -> "lost-warbands", "count" -> count)
    case NoteClause.BurnedFavor(count) =>
      ujson.Obj("kind" -> "burned-favor", "count" -> count)
    case NoteClause.Discarded(ids) => ujson.Obj("kind" -> "discarded",
      "cards" -> ujson.Arr.from(ids.map(encodeCardId)))
    case NoteClause.SetAside(ids) => ujson.Obj("kind" -> "set-aside",
      "cards" -> ujson.Arr.from(ids.map(encodeCardId)))
    case NoteClause.SentTo(site) =>
      ujson.Obj("kind" -> "sent-to", "siteId" -> site.value)
```

In `decodeNoteArg`, beside the existing arms:

```scala
    case "players" => Right(NoteArg.Players(
      value("playerIds").arr.toVector.map(id => PlayerId(id.str))))
    case "clauses" => value("clauses").arr.toVector.zipWithIndex
      .traverseEither((clause, index) =>
        decodeNoteClause(clause, s"$path.clauses[$index]"))
      .map(NoteArg.Clauses.apply)
    case "placements" => Right(NoteArg.Placements(
      value("placements").arr.toVector.map(entry =>
        entry("count").num.toInt -> SiteId(entry("siteId").str))))
```

and the clause decoder, whose default arm is the rejection the second test
asserts:

```scala
  private def decodeNoteClause(value: ujson.Value, path: String)
      : Either[WireError, NoteClause] = value("kind").str match
    case "lost-warbands" => Right(NoteClause.LostWarbands(value("count").num.toInt))
    case "burned-favor" => Right(NoteClause.BurnedFavor(value("count").num.toInt))
    case "discarded" => value("cards").arr.toVector.zipWithIndex
      .traverseEither((card, index) => decodeCardId(card, s"$path.cards[$index]"))
      .map(NoteClause.Discarded.apply)
    case "set-aside" => value("cards").arr.toVector.zipWithIndex
      .traverseEither((card, index) => decodeCardId(card, s"$path.cards[$index]"))
      .map(NoteClause.SetAside.apply)
    case "sent-to" => Right(NoteClause.SentTo(SiteId(value("siteId").str)))
    case other => Left(WireError(path, s"unknown note clause kind $other"))
```

Use whichever traverse helper and `WireError` constructor the file already
uses for `decodeNoteArg`'s list cases; copy that shape rather than the names
above if they differ.

- [ ] **Step 5: Render the three kinds**

In `PowerLines.argument`:

```scala
    case NoteArg.Players(ids) =>
      LogWords.join(ids.map(id => Vector(words.player(id))))
    case NoteArg.Clauses(parts) => LogWords.join(parts.map(clause(_, seen, viewer)))
    case NoteArg.Placements(entries) => LogWords.join(entries.map((count, site) =>
      Vector(Text(s"$count ${ActionLines.plural(count, "warband", "warbands")} on "),
        words.site(site))))
```

and the clause renderer, whose wording matches `CampaignLines.losses` exactly
so slice 4's golden diff stays empty:

```scala
  private def clause(part: NoteClause, seen: Vector[ReadyGame],
      viewer: Option[PlayerId]): Vector[LogSpan] = part match
    case NoteClause.LostWarbands(count) => Vector(Text(
      s"lost $count ${ActionLines.plural(count, "warband", "warbands")}"))
    case NoteClause.BurnedFavor(count) => Vector(Text(s"burned $count favor"))
    case NoteClause.Discarded(ids) => Text("discarded ") +:
      words.listed(ids, seen, viewer)
    case NoteClause.SetAside(ids) => Text("set aside ") +:
      words.listed(ids, seen, viewer)
    case NoteClause.SentTo(site) => Vector(Text("was sent to "), words.site(site))
```

- [ ] **Step 6: Run the tests**

Run: `./sbtw "testOnly oathdigital.serialization.GameEventWireSuite"`
Expected: PASS

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`
Expected: PASS, unchanged.

- [ ] **Step 7: Commit**

```bash
git add src/main src/test
git commit -m "feat(gamelog): note arguments for players, clauses and placements"
```

---

### Task 4: `ProcedureNotes` and the declared-set union

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/DeclaredNotes.scala:36-40`
- Test: `src/test/scala/oathdigital/gameplay/powers/DeclaredNoteKeysSuite.scala`

**Interfaces:**
- Consumes: Task 2's `PowerNote`.
- Produces: `ProcedureNotes.id(procedure: ProcedureRef): PowerId`,
  `ProcedureNotes.all: Vector[(PowerId, Vector[NoteKey])]`, and
  `ProcedureNotes.writes(power: PowerId): Boolean`, which `PowerLines` uses in
  Task 5 to tell a procedure note from a power's.

- [ ] **Step 1: Write the failing test**

Add to `DeclaredNoteKeysSuite`:

```scala
test("a procedure's keys reach the declared union under its derived id"):
  val declared = DeclaredNotes.of(FirstGameSetupFixture.catalog)
  val muster = ProcedureNotes.id(ActionRef.Muster)
  assertEquals(muster, PowerId("procedure.muster"))
  assert(ProcedureNotes.writes(muster), "the registry knows its own id")
  assert(!ProcedureNotes.writes(PowerId("denizen.wolves")),
    "a power is not a procedure")
  assert(declared.knows(muster),
    s"the union covers ${muster.value}: ${declared.keys.keySet}")
```

`ProcedureNotes.all` is empty until Task 8, so `knows` must hold for an id
that declares no keys yet. Register every id the table names, with its
(possibly empty) key vector, so the union covers it.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`
Expected: FAIL to compile — `ProcedureNotes` does not exist.

- [ ] **Step 3: Write `ProcedureNotes`**

```scala
package oathdigital.gameplay.actions

import oathdigital.model.{NoteKey, PowerId, ProcedureRef}

/** The procedures that write their own Game Log lines (procedures journal
  * their own notes design, "ProcedureNotes"): each procedure's id and the
  * keys it writes. A procedure is not a power, so the formatter reads this
  * beside the two power catalogs and `RuleNotes`, in the shape
  * `PrintedRule` set for the restrictions that are not powers either.
  *
  * The id is derived from `ProcedureRef.key` and never spelled by hand, so
  * it cannot drift from the key `ProcedureLog` writes into a section.
  */
object ProcedureNotes:
  def id(procedure: ProcedureRef): PowerId =
    PowerId(s"procedure.${procedure.key}")

  /** Every procedure that journals its own notes, with the keys it writes.
    * A procedure joins this table in the slice that migrates it. */
  val all: Vector[(PowerId, Vector[NoteKey])] = Vector.empty

  private val ids: Set[PowerId] = all.map(_._1).toSet

  /** Whether `power` is a procedure's id rather than a power's or a rule's.
    * `PowerLines` asks this, not `note.source.isEmpty`: the source is
    * optional on a type powers also use. */
  def writes(power: PowerId): Boolean = ids.contains(power)
```

- [ ] **Step 4: Union it into `DeclaredNotes`**

In `DeclaredNotes.of`, add `ProcedureNotes.all` to the concatenation beside
`RuleNotes.all`, and extend the doc comment's first line to "the union over
`walkers` and `catalog`'s phase powers, game rules and procedures".

```scala
      PhasePowerCatalog.default(catalog).powers.map(power =>
        power.id -> power.noteKeys) ++ RuleNotes.all ++ ProcedureNotes.all)
```

- [ ] **Step 5: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`
Expected: FAIL — `declared.knows(muster)` is false, because `all` is empty.

- [ ] **Step 6: Register the ids with no keys**

Change `all` so every procedure that will migrate is present from the start,
and the per-procedure slices only fill in keys:

```scala
  val all: Vector[(PowerId, Vector[NoteKey])] =
    ProcedureRef.all.map(procedure => id(procedure) -> keys(procedure))

  /** The keys a procedure writes. A procedure joins this match with its
    * own `NoteSet` in the slice that migrates it; until then it writes
    * none, and the Game Log reads its line from `ProcedureLog`. */
  private def keys(procedure: ProcedureRef): Vector[NoteKey] =
    procedure match
      case _ => Vector.empty
```

Leave the wildcard with the comment above it: an exhaustive match here would
duplicate `ProcedureLog`'s, which is the registry that already fails
compilation for a new procedure, and `ActionRef.UsePower` is parameterized
and absent from `ProcedureRef.all`. Task 8 replaces the wildcard's body for
Muster.

- [ ] **Step 7: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`
Expected: PASS

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`
Expected: PASS, unchanged.

- [ ] **Step 8: Commit**

```bash
git add src/main src/test
git commit -m "feat(gamelog): procedures join the declared note union"
```

---

### Task 5: Render a procedure note, and fix the two collisions

`covered` and `repeated` were written when every note came from a power.

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/PowerLines.scala:59-82`
- Modify: `src/main/scala/oathdigital/application/gamelog/LogJournal.scala:71-80`
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`

**Interfaces:**
- Consumes: Task 4's `ProcedureNotes.writes`.
- Produces: `PowerLines` rendering a procedure note at `LogKind.Action` with
  no prefix, exempt from the identical-note drop; `LogJournal.covered`
  answering for a `PowerNoted`.

- [ ] **Step 1: Write the failing tests**

Add to `GameLogPowerLinesSuite`, using whatever fixture that suite already
uses to build a journal of hand-made events:

```scala
test("a procedure note is an action line with no source prefix"):
  val note = PowerNote(None, "mustered", Vector(NoteArg.Number(2)))
  val posted = lines.own(journalOf(PowerNoted(
    ProcedureNotes.id(ActionRef.Muster), note, covers = false)),
    run(ActionRef.Muster), at = 0, opens = false, viewer = None)
  assertEquals(posted.map(_.kind), Vector(LogKind.Action))
  assert(!text(posted.head).contains(": "),
    s"a procedure note writes no source prefix: ${text(posted.head)}")

test("two identical procedure notes in one run both post"):
  val note = PowerNote(None, "moved", Vector(NoteArg.Number(1)))
  val event = PowerNoted(ProcedureNotes.id(ActionRef.MoveWarbands), note,
    covers = false)
  val journal = journalOf(event, event)
  val run0 = run(ActionRef.MoveWarbands)
  assertEquals(
    (0 to 1).toVector.flatMap(lines.own(journal, run0, _, false, None)).size, 2)

test("two identical power notes in one run post once"):
  val note = PowerNote(Some(PowerSourceRef.Card(DenizenId("39"))), "used",
    Vector.empty)
  val event = PowerNoted(PowerId("denizen.wolves"), note, covers = false)
  val journal = journalOf(event, event)
  val run0 = run(ActionRef.UsePower(PowerId("denizen.wolves")))
  assertEquals(
    (0 to 1).toVector.flatMap(lines.own(journal, run0, _, false, None)).size, 1)

test("a covering power note suppresses a procedure note"):
  val procedure = PowerNoted(ProcedureNotes.id(ActionRef.Muster),
    PowerNote(None, "mustered", Vector(NoteArg.Number(2))), covers = false)
  val covering = PowerNoted(PowerId("denizen.vow-of-union"),
    PowerNote(Some(PowerSourceRef.Card(DenizenId("v"))), "used", Vector.empty),
    covers = true)
  assert(journalOf(procedure, covering).covered(0),
    "a covering note that follows a note covers it")

test("a non-covering note after a note does not cover it"):
  val procedure = PowerNoted(ProcedureNotes.id(ActionRef.Muster),
    PowerNote(None, "mustered", Vector(NoteArg.Number(2))), covers = false)
  val plain = PowerNoted(PowerId("denizen.wolves"),
    PowerNote(Some(PowerSourceRef.Card(DenizenId("39"))), "used", Vector.empty),
    covers = false)
  assert(!journalOf(procedure, plain).covered(0),
    "only a covering note covers")
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogPowerLinesSuite"`
Expected: FAIL — the kind is `Trigger`, the duplicate is dropped, and
`covered(0)` is false. `journalOf` is the suite's existing helper for a
journal of hand-made events; it supplies the closing event itself.

- [ ] **Step 3: Render a procedure note as an action line**

In `PowerLines.kind`:

```scala
  /** A procedure's own note is its action's line, as is a phase power's
    * `used` note or a variant of it. */
  private def kind(run: Run, power: PowerId, note: PowerNote): LogKind =
    if ProcedureNotes.writes(power) then LogKind.Action
    else if NoteKey.isUse(note.key) && run.procedure == ActionRef.UsePower(power)
    then LogKind.Action
    else LogKind.Trigger
```

- [ ] **Step 4: Exempt procedure notes from the identical-note drop**

In `PowerLines.line`, pass the exemption through the existing guard:

```scala
    case PowerNoted(power, note, _)
        if ProcedureNotes.writes(power) ||
          !repeated(journal, run, at, power, note) =>
```

and extend `repeated`'s doc comment to say why: the drop exists for the hide
hook, which fires once per hidden option, and procedures have no hide hook,
so two identical procedure notes in one run are two real events.

- [ ] **Step 5: Let a covering note cover a note**

In `LogJournal.covered`, answer for a `PowerNoted` as well as a
`WalkerStepRecorded`. A note is covered by a covering note that follows it in
its segment before the next step or note:

```scala
  /** Whether a covering note restates the step or note at `index`: one
    * follows it in its segment before the next step or note of its own. */
  def covered(index: Int): Boolean = event(index) match
    case _: WalkerStepRecorded | _: PowerNoted =>
      (index + 1 to segmentEnd(index)).iterator.map(event)
        .takeWhile {
          case _: WalkerStepRecorded => false
          case noted: PowerNoted => noted.covers
          case _ => true
        }
        .exists {
          case noted: PowerNoted => noted.covers
          case _ => false
        }
    case _ => false
```

Read the `takeWhile` carefully: it must stop at the next step and at a
non-covering note, and let a covering note through to `exists`.

- [ ] **Step 6: Run the tests**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogPowerLinesSuite"`
Expected: PASS

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS, goldens unchanged — no procedure writes a note yet, and the
`covered` change must not move any existing line.

- [ ] **Step 7: Commit**

```bash
git add src/main src/test
git commit -m "feat(gamelog): render a procedure note, and cover and repeat it correctly"
```

---

### Task 6: The walker journals a procedure note exactly once

The two guarantees the formatter cannot see: a resumed command must not
journal a note twice, and a note in a branch that does not run must not be
journaled at all. Both already hold for a power's note; this task pins them
for a procedure's before any procedure relies on them.

**Files:**
- Test: `src/test/scala/oathdigital/gameplay/walker/WalkerNotesSuite.scala`

**Interfaces:**
- Consumes: Task 4's `ProcedureNotes.id`.
- Produces: nothing; these are guard tests over existing walker behaviour.

- [ ] **Step 1: Write the failing tests**

Add to the walker suite that already drives a hand-built `Operation` tree
(the one covering `Note` for powers; if it has no name of its own, create
`WalkerNotesSuite` and copy that suite's fixture imports):

```scala
private val id = ProcedureNotes.id(ActionRef.Muster)

private def noted(events: Vector[OathEvent]): Int =
  events.count {
    case PowerNoted(`id`, _, _) => true
    case _ => false
  }

test("a resumed command journals a procedure note once"):
  val tree = Sequence(Vector[Operation](
    Decide("test.park", actor, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(DecisionOptionRef.Button("go"), "Go")))),
    Note(id, _ => Some(PowerNote(None, "mustered",
      Vector(NoteArg.Number(1)))))))
  val parked = walk(tree)
  assertEquals(noted(parked.events), 0, "nothing is journaled before the park")
  val resumed = answer(parked, "test.park", DecisionOptionRef.Button("go"))
  assertEquals(noted(parked.events ++ resumed.events), 1,
    "the resume journals the note once, not once per fold")

test("a procedure note in a branch that does not run journals nothing"):
  val tree = Branch((_, _) => Vector.empty[Operation])
  assertEquals(noted(walk(tree).events), 0)
```

`walk` and `answer` are the suite's existing drivers; use its names and its
`actor`. If the suite drives through `GameApplicationService` instead, build
the same two trees through whatever entry point it uses — the assertions do
not change.

- [ ] **Step 2: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.WalkerNotesSuite"`
Expected: PASS. These guarantees come from the power log lines design's
"Once per effect" rule and are expected to hold already. **If either fails,
stop and report it** — a procedure note is journaled where a power's is, so
a failure here is a defect in the existing mechanism, not in this plan, and
the remaining tasks would build on it.

- [ ] **Step 3: Commit**

```bash
git add src/test
git commit -m "test(walker): a procedure note is journaled once, and not in a dead branch"
```

---

### Task 7: `ActionLineSource` and `Told`

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala:28-68`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala:49`
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `ActionLineSource` with cases `Rule(lines: ActionLine)`, `Notes`
  and `Silent(reason: String)`; `Told(decision: String, by: NoteKey)`;
  `ProcedureLog.narrates(decisionId: String): Boolean` unchanged in
  signature.

- [ ] **Step 1: Write the failing test**

Add to `GameLogPropertiesSuite`:

```scala
test("every procedure states whether its line is a rule, its notes, or absent"):
  val sources = ProcedureRef.all.map(procedure =>
    procedure -> logs.of(procedure).lines)
  assert(sources.forall {
    case (_, ActionLineSource.Silent(reason)) => reason.nonEmpty
    case _ => true
  }, s"a silent procedure states why: $sources")
  assertEquals(sources.count(_._2 == ActionLineSource.Notes), 0,
    "no procedure journals its own notes yet")
```

The second assertion's expected count rises by one in Task 8 and in every
later slice; it is the plan's running tally that a migration landed.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogPropertiesSuite"`
Expected: FAIL to compile — `ActionLineSource` does not exist.

- [ ] **Step 3: Replace `ActionLine.none` with the sealed source**

In `ProcedureLog.scala`, keep the `ActionLine` type alias and add:

```scala
/** Where a procedure's action line comes from (procedures journal their own
  * notes design, ruling 4). `none` meant two things — "posts nothing" and
  * "posts from its notes" — so a procedure whose notes were dropped read as
  * correct. */
private[gamelog] enum ActionLineSource:
  /** A rule in `ActionLines` that reads the journal. */
  case Rule(lines: ActionLine)
  /** The procedure journals its own notes; the formatter posts them. */
  case Notes
  /** The procedure posts no action line, and says why. */
  case Silent(reason: String)
```

Change the field and delete `object ActionLine`'s `none`:

```scala
    lines: ActionLineSource,
```

- [ ] **Step 4: Update every entry in the `rules` match**

Each `lines = actions.xLines` becomes `lines = ActionLineSource.Rule(actions.xLines)`.
Each `lines = ActionLine.none` becomes `ActionLineSource.Silent(reason)` with
the reason the entry's comment already carries, for example:

```scala
    case PhaseTransitionRef.EndWake | PhaseTransitionRef.BeginRest =>
      ProcedureLog(tag = None, isSection = false,
        lines = ActionLineSource.Silent(
          "the phase changes show in the lines around them"))
    case TriggeredProcedureRef.ForcedWake =>
      ProcedureLog(tag = None, isSection = false,
        lines = ActionLineSource.Silent("each forced power writes its own lines"))
    case TriggeredProcedureRef.WakeMenu | TriggeredProcedureRef.RestMenu |
        TriggeredProcedureRef.ActMenu =>
      ProcedureLog(tag = None, isSection = false,
        narrated = Set(PhaseMenu.decisionId),
        lines = ActionLineSource.Silent("a pick starts its own walk, which writes the lines"))
    case TriggeredProcedureRef.FollowUpOffer =>
      ProcedureLog(tag = None, isSection = false,
        lines = ActionLineSource.Silent(
          "the queued action's tag, or the Skip line, tells the offer's answer"))
```

`narrated` is still `Set[String]` at this step and changes in Step 6, so
every entry's `narrated` stays exactly as it is today.

- [ ] **Step 5: Teach the formatter the three cases**

In `GameLogFormatter`, where it calls `entry.lines(journal, run, at, viewer)`:

```scala
    val own = entry.lines match
      case ActionLineSource.Rule(lines) => lines(journal, run, at, viewer)
      case ActionLineSource.Notes | ActionLineSource.Silent(_) => Vector.empty
```

`Notes` posts nothing of its own: a procedure's note arrives through the
`PowerNoted` path `PowerLines` already handles.

- [ ] **Step 6: Pair narration with the key that tells it**

In `ProcedureLog.scala`:

```scala
/** A decision whose answer another line already tells, and the note key that
  * tells it (procedures journal their own notes design, ruling 5). Pairing
  * them means deleting the key fails compilation here, rather than leaving a
  * narration that suppresses a "Chose …" line with nothing in its place. */
private[gamelog] final case class Told(decision: String,
    by: Option[NoteKey] = None)
```

`by` is an `Option` because an unmigrated procedure has no key to name. The
compile-time guarantee ruling 5 claims holds for an entry that has filled it
in; `GameLogPropertiesSuite`'s narration coverage stays the guard for the
rest, and every slice that migrates a procedure fills its `by` in.

Change the field to `narrated: Vector[Told] = Vector.empty` and `narrates`:

```scala
  def narrates(decisionId: String): Boolean =
    narrated.exists(_.decision == decisionId) ||
      narratedPrefixes.exists(decisionId.startsWith)
```

Rewrite each entry's `narrated = Set(a, b)` as
`narrated = Vector(Told(a), Told(b))`. `by` stays `None` for a procedure
whose line is still a `Rule`; a migrating slice fills it in.

`DetailLines:49` calls `log.narrates(id)` and does not change.

- [ ] **Step 7: Run the tests**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS, goldens unchanged.

- [ ] **Step 8: Commit**

```bash
git add src/main src/test
git commit -m "refactor(gamelog): a procedure states whether its line is a rule, notes or absent"
```

---

### Task 8: Muster journals its own line

The proof. `ActionLines.musterLines` is deleted and the golden logs do not
move.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/economy/MusterProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala:40-53,112-129`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala:63-78`
- Modify: `src/test/resources/notes/declared-keys.txt`
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala`

**Interfaces:**
- Consumes: Tasks 2–6.
- Produces: `MusterProcedure.notes: NoteSet`, `MusterProcedure.mustered: NoteKey`,
  and `EconomyTree.Kind.note: Option[(NoteStates, MusterSource) => Option[PowerNote]]`.

- [ ] **Step 1: Write the failing test**

`GameLogActionLineSuite:63` already asserts
`mine.contains("Mustered 2 warbands with Golem Legions")`. Add beside it:

```scala
test("the Muster line comes from the procedure's own note"):
  val script = LogScripts.muster()
  val events = script.events
  assert(events.exists {
    case PowerNoted(power, note, _) =>
      power == ProcedureNotes.id(ActionRef.Muster) && note.key == "mustered"
    case _ => false
  }, "the Muster journals its own note")
  assert(LogScripts.text(script, script.actor)
    .contains("Mustered 2 warbands with Golem Legions"),
    "and the line reads exactly as before")
```

Use the script name and accessors `GameLogActionLineSuite:63`'s test already
uses; do not invent a new script.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogActionLineSuite"`
Expected: FAIL — no `PowerNoted` for the Muster.

- [ ] **Step 3: Declare Muster's key**

In `MusterProcedure.scala`:

```scala
object MusterProcedure:
  val decisionId: String = "muster.source"

  private val notes = NoteSet(ProcedureNotes.id(ActionRef.Muster))
  /** "Mustered 2 warbands with Hearth". The count is the warbands that
    * actually arrived, never the printed number, and the parts are several
    * `Text` spans that concatenate to the one span the line had before. */
  val mustered: NoteKey = notes.key("mustered", Vector(
    NotePart.Text("Mustered "), NotePart.Arg(0), NotePart.Text(" "),
    NotePart.Plural(0, "warband", "warbands"), NotePart.Text(" with "),
    NotePart.Arg(1)))
  def noteKeys: Vector[NoteKey] = notes.declared
```

`noteKeys` is a `def`, the key is a plain `val`, and neither is in a trait
body — the three rules `NoteSet`'s doc comment sets.

- [ ] **Step 4: Give `EconomyTree.Kind` a note, and place the leaf**

`Kind` is built per call, so it carries the whole leaf rather than a builder
plus an id. Add one field to `Kind`, after `yields`:

```scala
      /** The line this action writes at its gain, if it writes one: the
        * note's id and a builder that reads the resolved source. */
      note: Option[(PowerId, (NoteStates, MusterSource) => Option[PowerNote])] =
        None,
```

In `afterSource`, append the leaf to the gain's own sequence, so the note's
`previous` is the gain and nothing else:

```scala
    Sequence(kind.yields(actor, source,
      MusterSource.matching(catalog, ready, actor, source.suit), force).toVector ++
      kind.note.toVector.map((id, build) => Note(id, build(_, source))),
      Some(kind.gain)))
```

`EconomyTree` places the leaf and owns none of the words, which is what
ruling 2 and the spec's "Where a shared builder's keys live" require.
`TradeProcedure` passes nothing and keeps the default until slice 3 migrates
it.

- [ ] **Step 5: Build Muster's note**

`NoteStates` carries no actor, so `kind` becomes a `def` over the actor that
`build` and `rebuild` both already hold. Replace the `private val kind` in
`MusterProcedure.scala` with:

```scala
  private def kind(actor: PlayerId) = EconomyTree.Kind(decisionId,
    "Choose a card to Muster from", PowerWindow.MusterModifierSelection,
    "Start Muster", PowerWindow.MusterActionEligibility,
    PowerWindow.MusterSourceSelection, PowerWindow.MusterCost,
    PowerWindow.MusterGain, Cost(favor = 1),
    (player, _, matching, force) =>
      Some(Gain.Warbands(player, force, 1 + matching)),
    note = Some((ProcedureNotes.id(ActionRef.Muster),
      musteredNote(actor, _, _))))

  /** The warbands that reached the player's play area, read as the
    * difference across the gain rather than as the printed number: a gain
    * the bank clamped reads what arrived. A gain a power removed reads zero
    * and writes no line, exactly as the deleted rule wrote none without a
    * `Move`. */
  private def musteredNote(actor: PlayerId, states: NoteStates,
      source: MusterSource): Option[PowerNote] =
    states.previous.flatMap: (before, after) =>
      val gained = warbands(after, actor) - warbands(before, actor)
      Option.when(gained > 0)(mustered.sourceless(
        NoteArg.Number(gained), NoteArg.Card(source.card)))

  private def warbands(ready: ReadyGame, actor: PlayerId): Int =
    ready.game.current.players.find(_.player == actor)
      .fold(0)(_.board.warbands)
```

`build` and `rebuild` pass `kind(actor)` where they passed `kind`.


- [ ] **Step 6: Register the key and mark the entry**

In `ProcedureNotes.keys`:

```scala
  private def keys(procedure: ProcedureRef): Vector[NoteKey] =
    procedure match
      case ActionRef.Muster => MusterProcedure.noteKeys
      case _ => Vector.empty
```

In `ProcedureLog.rules`:

```scala
    case ActionRef.Muster => ProcedureLog(tag = Some("Muster"),
      lines = ActionLineSource.Notes, anchor = starts.paysCost,
      narrated = Vector(Told(MusterProcedure.decisionId,
        Some(MusterProcedure.mustered))))
```

- [ ] **Step 7: Delete the rule**

Remove `ActionLines.musterLines` entirely, with its doc comment. Remove the
`MusterProcedure` import if nothing else in the file uses it, and remove
`source` and `plural` only if `tradeLines` no longer needs them — it does
need both, so leave them.

- [ ] **Step 8: Run the tests**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogActionLineSuite"`
Expected: PASS

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`
Expected: PASS. **If a golden differs, the migration changed a line — fix the
template, never the golden.** Read the diff: a `[amount:…]` where text was
means the template used `NoteArg.Amount` instead of `Number` plus `Plural`.

- [ ] **Step 9: Update the declared-keys snapshot**

`DeclaredNoteKeysSuite` now fails: the snapshot gains Muster's key. This is
the one fixture this plan expects to change.

Run: `./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`
Read the failure, and add exactly one line to
`src/test/resources/notes/declared-keys.txt`, in the file's sort order:

```
procedure.muster mustered = Mustered {0} {1} with {2}.
```

Match the renderer's exact spelling from the failure message rather than the
line above, which is written from the template by hand.

- [ ] **Step 10: Run the full gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: exit 0.

Run: `python3 scripts/check-architecture.py`
Expected: exit 0.

- [ ] **Step 11: Commit**

```bash
git add src/main src/test
git commit -m "feat(gamelog): Muster journals its own line"
```

---

## Done when

- `ActionLines.musterLines` does not exist.
- `ProcedureLog.of(ActionRef.Muster).lines == ActionLineSource.Notes`.
- Every golden log under `src/test/resources/gamelog` is byte-identical to
  its state at the start of this plan.
- `src/test/resources/notes/declared-keys.txt` has exactly one new line.
- `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` exits 0 and
  `python3 scripts/check-architecture.py` exits 0.
