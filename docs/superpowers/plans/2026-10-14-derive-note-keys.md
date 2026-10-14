# Derive Note Keys Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a power's note keys derive from the keys themselves, so a key cannot be defined without being declared, and an undeclared key fails a test instead of silently dropping a line from the player's log.

**Architecture:** A key is created through a `NoteSet` owned by the object that defines it, and `noteKeys` reads that set instead of relisting its contents. Keys borrowed from another object are included explicitly, with compile-time evidence where a helper builds the note itself. A committed snapshot of every power's declared key names proves each step changed nothing, and a check at the three points where `PowerNoted` is built rejects a key no power declared.

**Tech Stack:** Scala 3.9.0, sbt via `./sbtw`, munit 1.0.4, `-Werror`.

**Spec:** `docs/superpowers/specs/2026-10-14-derive-note-keys-design.md`

## Global Constraints

- Scala 3.9.0 with `-Werror` (`build.sbt:61-68`): every compiler warning fails the build. `-Wunused:imports,privates,locals,implicits,nowarn` is on, so an unused import or private member is a build failure.
- 800-line cap on production files. `model/PowerNotes.scala` is 85 lines today.
- A note key's `name` is the wire spelling: `PowerNoted` journals it and the formatter looks a saved game's wording up by it. **No key's `name` string may change in any task of this plan.**
- Scoverage statement ratchet is pinned in `build.sbt`; do not lower it.
- Run the build with `./sbtw`, never a bare `sbt`. A whole-suite run is `./sbtw test`; one suite is `./sbtw 'testOnly <fully.qualified.SuiteName>'`.
- Commit messages are normal prose, Conventional Commits, and end with the attribution trailer used by the repo's recent commits.

---

### Task 1: Snapshot the declared keys, and name the vocabulary

The baseline every later task is measured against. It must be committed before anything else moves, so that a mistake in Task 2 onward shows up as a snapshot diff rather than as a blank line in someone's log.

**Files:**
- Create: `src/test/scala/oathdigital/gameplay/powers/DeclaredNoteKeysSuite.scala`
- Create: `src/test/resources/notes/declared-keys.txt` (generated, then reviewed)
- Modify: `src/test/scala/oathdigital/gameplay/powers/PowerNoteCatalogSuite.scala:31-36`
- Modify: `CONTEXT.md` (the `## Language` section, which starts at line 8)

**Interfaces:**
- Consumes: nothing.
- Produces: `src/test/resources/notes/declared-keys.txt`, the fixture every later task must leave byte-identical. `DeclaredNoteKeysSuite` reads it and is the proof step for Tasks 2 through 6.

- [ ] **Step 1: Write the snapshot suite**

It follows `GameLogGoldenSuite`'s established pattern exactly: an env var rewrites the fixture, and a rewritten fixture is reviewed before it is committed. Create `src/test/scala/oathdigital/gameplay/powers/DeclaredNoteKeysSuite.scala`:

```scala
package oathdigital.gameplay.powers

import java.nio.file.{Files, Path, Paths}
import oathdigital.gameplay.actions.RuleNotes
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.{NoteKey, PowerId}

/** Every power's declared note keys, exactly. Note keys are journaled in
  * `PowerNoted` events and the log looks a saved game's wording up by key,
  * so a renamed or dropped key orphans the notes of saved games. This
  * fixture is the one place that says what the whole catalog declares.
  *
  * `NOTE_KEYS_SNAPSHOT=write` rewrites the file instead of comparing; a
  * rewritten snapshot is reviewed line by line before it is committed.
  */
class DeclaredNoteKeysSuite extends munit.FunSuite:
  private val file: Path = Paths.get("src/test/resources/notes/declared-keys.txt")
  private val writing = sys.env.get("NOTE_KEYS_SNAPSHOT").contains("write")

  private val declared: Vector[(PowerId, Vector[NoteKey])] =
    WalkerPowerCatalog.default(catalog).powers.map(power =>
      power.id -> power.noteKeys) ++
    PhasePowerCatalog.default(catalog).powers.map(power =>
      power.id -> power.noteKeys) ++ RuleNotes.all

  /** One line per power: its id, then its key names, both sorted, so where
    * a key sits in its object's body cannot change the snapshot. */
  private def rendered: String =
    declared.map((id, keys) => s"${id.value}: ${keys.map(_.name).sorted
      .mkString(", ")}").sorted.mkString("", "\n", "\n")

  test("every power declares exactly the keys the snapshot records"):
    if writing then
      Files.createDirectories(file.getParent)
      Files.writeString(file, rendered)
    else
      assert(Files.exists(file), s"missing snapshot $file; run with " +
        "NOTE_KEYS_SNAPSHOT=write and review it")
      assertNoDiff(rendered, Files.readString(file))

  test("no power is listed twice"):
    val ids = declared.map(_._1.value)
    assertEquals(ids.distinct.size, ids.size,
      ids.diff(ids.distinct).distinct.mkString(", "))
```

- [ ] **Step 2: Run it to verify it fails for the right reason**

Run: `./sbtw 'testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite'`

Expected: FAIL on the first test with "missing snapshot src/test/resources/notes/declared-keys.txt; run with NOTE_KEYS_SNAPSHOT=write and review it". The second test must PASS. If the second test fails, a power id is duplicated across the two catalogs — stop and report it, because it means two powers share a wordings slot today.

- [ ] **Step 3: Generate the snapshot**

Run: `NOTE_KEYS_SNAPSHOT=write ./sbtw 'testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite'`

- [ ] **Step 4: Review the generated snapshot before trusting it**

Run: `wc -l src/test/resources/notes/declared-keys.txt && head -5 src/test/resources/notes/declared-keys.txt`

Expected: on the order of 200 lines, each `power.id: key, key, key`. Check three things by eye and report any that fail rather than committing:
- `denizen.warning-signals: discarded, redistributed` — both keys present.
- `denizen.gambling-hall: gained, used` — the two keys `PowerNoteCatalogSuite` pins today.
- No line ends in `: ` with nothing after it. A power with no keys should not be in the catalog's noting set at all; if one is, note its id in the commit body.

- [ ] **Step 5: Replace the two hand-picked pins with the snapshot**

The snapshot now covers every power, so the two-power version is redundant. In `src/test/scala/oathdigital/gameplay/powers/PowerNoteCatalogSuite.scala`, delete lines 31-36 — the comment and the `"Gambling Hall and Vow of Peace keep their persisted note keys"` test. The `GamblingHall` and `VowOfPeaceContribution` imports at lines 4-5 become unused, and `-Werror` with `-Wunused:imports` will fail the build if you leave them, so delete those two import lines too. Leave the suite's other four tests alone.

- [ ] **Step 6: Run the full suite**

Run: `./sbtw test`

Expected: PASS. `DeclaredNoteKeysSuite` now compares against the committed file, and `PowerNoteCatalogSuite` has four tests.

- [ ] **Step 7: Add the vocabulary to CONTEXT.md**

`CONTEXT.md` has no entry for "note" today, although `PowerNote`, `NoteKey`, `NoteArg`, `NotePart`, `noteKeys` and `NoteWordings` all depend on the term. Add a `### Note` subsection to `## Language`, placed after `### Walker` (which ends at line 73) and before `### Consent` (line 74), matching the surrounding subsections' voice:

```markdown
### Note

A **note** is what one power says about its own effect, as one line of the
Game Log: "Warning Signals: Blue redistributed their warbands." A note is
never text. It is a `PowerNote` — a source, a key and typed arguments — so a
card it names still passes the log's knowledge rule.

A **note key** is one line a power can write: its name and the **wording**
that renders it. The name is the wire spelling, journaled in `PowerNoted`,
so a saved game's notes are only readable while their keys keep their names.

A power's **declared set** is every key it may write. A key the power defines
itself it **owns**; a key defined in another object it **borrows**, and it
borrows the key by including the owner's set. A note whose key is not in the
writing power's declared set has no wording to render.

See `docs/superpowers/specs/2026-09-26-power-log-lines-design.md` for the
wording rules and `docs/superpowers/specs/2026-10-14-derive-note-keys-design.md`
for how a declared set is built.
```

- [ ] **Step 8: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/powers/DeclaredNoteKeysSuite.scala \
        src/test/resources/notes/declared-keys.txt \
        src/test/scala/oathdigital/gameplay/powers/PowerNoteCatalogSuite.scala \
        CONTEXT.md
git commit -m "$(cat <<'MSG'
test(notes): snapshot every power's declared note keys

A note key is journaled and a saved game's wording is looked up by it, so
the catalog's declared keys are a contract. One fixture now records all of
them, which replaces the two powers the catalog suite pinned by hand and
gives the coming derivation of `noteKeys` a baseline to prove itself
inert against. CONTEXT.md gains the vocabulary the fixture is written in.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

---

### Task 2: One trait for the end-of-Campaign discard

Independent of every later task. All 12 powers whose card says "At end, discard" relist `PlanDiscard.discarded`; seven also carry a byte-identical `later` body. This is where the Warning Signals bug lived.

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/DiscardedAtEnd.scala`
- Modify, dropping the relist and the shared body: `campaign/GreatCrusade.scala:30,39-41`, `campaign/MountedPatrol.scala:34,46-48`, `campaign/RivalKhan.scala:28,37-39`, `campaign/PledgeOfDefense.scala:28,38-40`, `campaign/WarningSignals.scala:42-43,50-52`, `campaign/HorseArchers.scala:27,34-36`, `campaign/StormCaller.scala:25,32-34`
- Modify, dropping the relist and narrowing the discard: `campaign/HeartsAndMinds.scala:35,45-48`, `campaign/TravelingDoctor.scala:34-35,46-50`, `campaign/MountainGiant.scala:35-36,56-62`
- Modify, dropping the relist and keeping a second window: `campaign/RainBoots.scala:30-31,37-41`, `campaign/Lancers.scala:30-31,37-44`
- Test: `src/test/scala/oathdigital/gameplay/powers/DeclaredNoteKeysSuite.scala` (unchanged; it is the proof)

**Interfaces:**
- Consumes: `src/test/resources/notes/declared-keys.txt` from Task 1.
- Produces: `trait DiscardedAtEnd extends BattlePlan`, with overridable `def planNoteKeys: Vector[NoteKey]`, `def otherWindows: Map[PowerWindow, PlanUse => Vector[Operation]]` and `def endDiscard: PlanUse => Vector[Operation]`. Task 5 attaches `PlanDiscard`'s inclusion evidence to this trait.

- [ ] **Step 1: Run the snapshot test to confirm a clean start**

Run: `./sbtw 'testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite'`

Expected: PASS. If it fails here, Task 1 is not committed — stop.

- [ ] **Step 2: Write the trait**

`BattlePlan` (`campaign/BattlePlan.scala:47`) already defaults `later` to `Map.empty`. Create `src/main/scala/oathdigital/gameplay/powers/campaign/DiscardedAtEnd.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.{DenizenId, NoteKey, Operation, PowerWindow}

/** A battle plan whose card says "At end, discard": the card goes to a
  * discard pile once the Campaign has resolved, whoever won, and the plan
  * writes `PlanDiscard.discarded` for it. Both the discard and its line come
  * from here, so a plan cannot carry one without the other.
  *
  * A plan states only what differs from the standard: its own keys in
  * `planNoteKeys`, any window other than the end discard in `otherWindows`,
  * and a narrower discard in `endDiscard`.
  */
private[campaign] trait DiscardedAtEnd extends BattlePlan:
  def catalog: ExecutableCatalog
  def cardId: DenizenId

  /** This plan's own keys, beside the end-of-Campaign discard line. */
  def planNoteKeys: Vector[NoteKey] = Vector.empty

  final override def noteKeys: Vector[NoteKey] =
    planNoteKeys :+ PlanDiscard.discarded

  /** The windows this plan acts in other than its end discard. */
  def otherWindows: Map[PowerWindow, PlanUse => Vector[Operation]] = Map.empty

  /** The end discard. A plan that discards only on some outcome narrows
    * this; it never drops the line, because the line is the discard's. */
  def endDiscard: PlanUse => Vector[Operation] = use =>
    PlanDiscard.afterCampaign(catalog, id, use, cardId)

  final override def later: Map[PowerWindow, PlanUse => Vector[Operation]] =
    otherWindows + (PowerWindow.CampaignActionEligibility -> endDiscard)
```

- [ ] **Step 3: Compile the trait alone**

Run: `./sbtw compile`

Expected: PASS. If `PlanUse` or `PowerWindow` is reported unresolved, read `campaign/BattlePlan.scala:40-55` for the import the file needs and add it; do not widen the imports beyond what compiles, because `-Wunused:imports` is fatal.

- [ ] **Step 4: Convert the seven powers with an identical body**

For each of GreatCrusade, MountedPatrol, RivalKhan, PledgeOfDefense, WarningSignals, HorseArchers and StormCaller: add `with DiscardedAtEnd` to the `extends BattlePlan` clause, delete the whole `override def later` block, and either delete `override def noteKeys` (when its only entry was `PlanDiscard.discarded`) or rename it to `override def planNoteKeys` with `PlanDiscard.discarded` removed from the vector.

`WarningSignals.scala` is the worked example. Before:

```scala
final case class WarningSignals private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = WarningSignalsCard.id
  def id: PowerId = WarningSignals.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] =
    Vector(WarningSignals.redistributed, PlanDiscard.discarded)
```

After, with lines 50-52's `override def later` deleted entirely:

```scala
final case class WarningSignals private (catalog: ExecutableCatalog)
    extends BattlePlan with DiscardedAtEnd:
  val cardId: DenizenId = WarningSignalsCard.id
  def id: PowerId = WarningSignals.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def planNoteKeys: Vector[NoteKey] =
    Vector(WarningSignals.redistributed)
```

`GreatCrusade`, `RivalKhan`, `PledgeOfDefense`, `HorseArchers` and `StormCaller` declared only `Vector(PlanDiscard.discarded)`, so their `noteKeys` override is deleted outright with no `planNoteKeys` to replace it. `MountedPatrol` keeps `override def planNoteKeys: Vector[NoteKey] = Vector(MountedPatrol.halved)`.

Watch the imports: a file that no longer names `PlanDiscard`, `NoteKey`, `PowerWindow` or `Operation` anywhere must drop those imports, or `-Werror` fails the build.

- [ ] **Step 5: Run the snapshot and the campaign suites**

Run: `./sbtw 'testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite oathdigital.gameplay.CampaignPowersSuite'`

Expected: PASS, with the snapshot byte-identical. A snapshot diff here means a key was dropped or reordered into a different sort position — the sort makes order irrelevant, so any diff is a real dropped or added key. Fix it before going on.

- [ ] **Step 6: Commit the seven**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/
git commit -m "$(cat <<'MSG'
refactor(powers): one trait holds the end-of-Campaign discard and its line

A plan whose card says "At end, discard" relisted the discard's note key
beside a copy of the discard itself, and Warning Signals once carried the
discard without the key, so it discarded in silence. `DiscardedAtEnd`
supplies both, and the seven plans that discard unconditionally now state
only their own keys.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

- [ ] **Step 7: Convert the three that discard conditionally**

These keep their condition and nothing else. `HeartsAndMinds.scala` — add `with DiscardedAtEnd`, delete `override def noteKeys` (its only entry was `PlanDiscard.discarded`), and replace lines 45-48 with:

```scala
  override def endDiscard: PlanUse => Vector[Operation] = use =>
    if use.user.exists(favored(use.ready, _)) then Vector.empty
    else PlanDiscard.afterCampaign(catalog, id, use, cardId)
```

`TravelingDoctor.scala` — add `with DiscardedAtEnd`, change `override def noteKeys: Vector[NoteKey] = Vector(SparedForce.spared, PlanDiscard.discarded)` to `override def planNoteKeys: Vector[NoteKey] = Vector(SparedForce.spared)`, and replace lines 46-50 with:

```scala
  override def endDiscard: PlanUse => Vector[Operation] = use =>
    if use.won.contains(false) then
      PlanDiscard.afterCampaign(catalog, id, use, cardId)
    else Vector.empty
```

`MountainGiant.scala` — add `with DiscardedAtEnd`, change `override def noteKeys` to `override def planNoteKeys` with `PlanDiscard.discarded` removed, and move the existing `Branch` body from lines 56-62 into `override def endDiscard: PlanUse => Vector[Operation] = use => Vector(Branch(...))`, keeping the `Branch`'s own condition on `PowerAnswers.one(pending, MountainGiant.decisionId)` exactly as it reads today. Read the whole of lines 56-70 before moving it; the `Branch` has an `else` arm that must travel with it.

- [ ] **Step 8: Convert the two with a second window**

`RainBoots.scala` — add `with DiscardedAtEnd`, change `noteKeys` to `planNoteKeys` with `PlanDiscard.discarded` removed, and replace lines 37-41 with the second window only:

```scala
  override def otherWindows
      : Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignDefenseResult -> (use =>
      SingleShields.ignore(use, id, PowerSourceRef.Card(cardId))))
```

`Lancers.scala` — the same shape, keeping its attack-result window:

```scala
  override def otherWindows
      : Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use => Vector(
      AttackScore.rescore(use.actor),
      Note(id, states => Option.when(
        CampaignBattle.attackFacesOf(states.now).nonEmpty)(
        Lancers.doubled(PowerSourceRef.Card(cardId)))))))
```

and `override def planNoteKeys: Vector[NoteKey] = Vector(Lancers.doubled)`.

- [ ] **Step 9: Run the snapshot and the campaign suites again**

Run: `./sbtw 'testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite oathdigital.gameplay.CampaignPowersSuite'`

Expected: PASS, snapshot byte-identical.

- [ ] **Step 10: Run everything**

Run: `./sbtw test`

Expected: PASS. The golden logs in `src/test/resources/gamelog/` are the real check that the conditional discards still fire in the same places; any diff there means a condition was transcribed wrongly. Do not rewrite a golden to make it pass.

- [ ] **Step 11: Commit the five**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/
git commit -m "$(cat <<'MSG'
refactor(powers): the conditional end discards keep only their condition

Hearts and Minds, Traveling Doctor and Mountain Giant discard on some
outcomes only, and Rain Boots and Lancers act in a second window. Each
now states that difference alone and takes the discard, its note key and
the standard window from `DiscardedAtEnd`.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

---

### Task 3: The mechanism, proven on two powers

`NoteSet`, the one declared-set union, and the check where notes are journaled. Two powers are converted as proof; the other ~199 wait for Tasks 4 and 5.

**Files:**
- Modify: `src/main/scala/oathdigital/model/PowerNotes.scala:58-66` (add `NoteSet`, 85 lines today)
- Create: `src/main/scala/oathdigital/gameplay/powers/DeclaredNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/NoteWordings.scala:33-40`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala:300-304`
- Modify: `src/main/scala/oathdigital/gameplay/walker/AdviserArrivalPass.scala:51-53`
- Modify: `src/main/scala/oathdigital/gameplay/walker/BurnTakes.scala:28-37`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/Defame.scala:38` and its companion at `:120`-ish (the `burned` key)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/WarningSignals.scala` (the `redistributed` key and `planNoteKeys`)
- Modify: `src/test/scala/oathdigital/gameplay/powers/DeclaredNoteKeysSuite.scala` (read the union from `DeclaredNotes`)
- Create: `src/test/scala/oathdigital/model/NoteSetSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/walker/UndeclaredNoteSuite.scala`

**Interfaces:**
- Consumes: `DiscardedAtEnd.planNoteKeys` from Task 2.
- Produces:
  - `NoteSet.apply(power: PowerId): NoteSet`, with `key(name: String, template: Vector[NotePart]): NoteKey`, `include(other: NoteSet): Unit`, `declared: Vector[NoteKey]`, and `owner: PowerId`.
  - `DeclaredNotes.of(catalog: ExecutableCatalog): DeclaredNotes`, with `keys: Map[PowerId, Vector[NoteKey]]` and `declares(power: PowerId, key: String): Boolean`.
  - `NoteSet.unowned(label: String): NoteSet` for a helper object that owns keys no power owns.

- [ ] **Step 1: Write the failing `NoteSet` test**

Create `src/test/scala/oathdigital/model/NoteSetSuite.scala`:

```scala
package oathdigital.model

class NoteSetSuite extends munit.FunSuite:
  private val sentence = Vector(NotePart.Text("Took it."))

  test("a key is declared by being created"):
    val notes = NoteSet(PowerId("test.one"))
    val took = notes.key("took", sentence)
    assertEquals(notes.declared, Vector(took))
    assertEquals(took.name, "took")
    assertEquals(took.template, sentence)

  test("an included set's keys are declared too, after the set's own"):
    val shared = NoteSet.unowned("shared")
    val discarded = shared.key("discarded", Vector(NotePart.Text("Gone.")))
    val notes = NoteSet(PowerId("test.two"))
    val took = notes.key("took", sentence)
    notes.include(shared)
    assertEquals(notes.declared, Vector(took, discarded))

  test("including a set twice declares its keys once"):
    val shared = NoteSet.unowned("shared")
    shared.key("discarded", Vector(NotePart.Text("Gone.")))
    val notes = NoteSet(PowerId("test.three"))
    notes.include(shared)
    notes.include(shared)
    assertEquals(notes.declared.map(_.name), Vector("discarded"))

  test("a set rejects two keys of the same name"):
    val notes = NoteSet(PowerId("test.four"))
    notes.key("took", sentence)
    val thrown = intercept[IllegalArgumentException](notes.key("took", sentence))
    assert(thrown.getMessage.contains("test.four"), thrown.getMessage)
    assert(thrown.getMessage.contains("took"), thrown.getMessage)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw 'testOnly oathdigital.model.NoteSetSuite'`

Expected: FAIL to compile, "Not found: NoteSet".

- [ ] **Step 3: Write `NoteSet`**

Add to `src/main/scala/oathdigital/model/PowerNotes.scala`, directly after `object NoteKey` (which ends at line 66). The mutable buffer is filled as the owning object's initialiser runs; the three rules in the scaladoc are what keep that honest, and `DeclaredNoteKeysSuite` is what enforces them.

```scala
/** The keys one object declares (derive note keys design, "Mechanism"). A
  * key is created through the set that declares it, so defining a key and
  * declaring it are one act and the two cannot disagree.
  *
  * `declared` is complete only once the owning object's initialiser has run,
  * so: read it through a `def`, never a `val`; declare keys as plain `val`s
  * in the object that owns the set, never `lazy val`s, which would never
  * register; and declare no key in a trait body, which initialises once per
  * mixin. A set left partly filled is a `DeclaredNoteKeysSuite` failure, not
  * a missing line in a player's log.
  */
final class NoteSet private (val owner: Option[PowerId], label: String):
  private val own = scala.collection.mutable.ArrayBuffer.empty[NoteKey]
  private val included = scala.collection.mutable.ArrayBuffer.empty[NoteSet]

  /** Declares a key and returns it. */
  def key(name: String, template: Vector[NotePart]): NoteKey =
    require(!own.exists(_.name == name), s"$label declares $name twice")
    val declaredKey = NoteKey(name, template)
    own += declaredKey
    declaredKey

  /** Declares every key `other` owns, for a key this object borrows. */
  def include(other: NoteSet): Unit =
    if !included.contains(other) then included += other

  /** Every key this object owns or borrows, its own first. */
  def declared: Vector[NoteKey] =
    (own.toVector ++ included.toVector.flatMap(_.declared)).distinctBy(_.name)

object NoteSet:
  def apply(power: PowerId): NoteSet = new NoteSet(Some(power), power.value)
  /** A set for a helper that owns keys no single power owns, such as the
    * end-of-Campaign discard line. `label` names it in failures only. */
  def unowned(label: String): NoteSet = new NoteSet(None, label)
```

`NoteKey`'s own constructor stays public until Task 6 closes it; closing it now would break all ~199 unconverted powers at once.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./sbtw 'testOnly oathdigital.model.NoteSetSuite'`

Expected: PASS, four tests.

- [ ] **Step 5: Commit the mechanism**

```bash
git add src/main/scala/oathdigital/model/PowerNotes.scala \
        src/test/scala/oathdigital/model/NoteSetSuite.scala
git commit -m "$(cat <<'MSG'
feat(notes): a key is declared by being created

`NoteSet` makes defining a note key and declaring it one act, so the two
cannot disagree. A helper that owns keys no power owns takes an unowned
set, and a power that borrows such a key includes it. Nothing uses this
yet; the catalog moves over power by power.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

- [ ] **Step 6: Write the failing test for the one declared-set union**

The union of walker powers, phase powers and `RuleNotes.all` is derived by hand in `NoteWordings.default` (`application/gamelog/NoteWordings.scala:33-40`) and again in two test suites. `application` sits downstream of `gameplay`, so the walker cannot reach `NoteWordings`; the union moves to `gameplay`. Create `src/test/scala/oathdigital/gameplay/powers/DeclaredNotesSuite.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.gameplay.actions.RuleNotes
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.PowerId

class DeclaredNotesSuite extends munit.FunSuite:
  private val declared = DeclaredNotes.of(catalog)

  test("it declares a walker power's key"):
    assert(declared.declares(PowerId("denizen.warning-signals"), "redistributed"))

  test("it declares a phase power's used line"):
    assert(declared.declares(PowerId("denizen.gambling-hall"), "used"))

  test("it declares a game rule's key, which no power owns"):
    val (rule, keys) = RuleNotes.all.head
    assert(declared.declares(rule, keys.head.name))

  test("it declares nothing for an unknown power or an unknown key"):
    assert(!declared.declares(PowerId("denizen.nonesuch"), "took"))
    assert(!declared.declares(PowerId("denizen.warning-signals"), "nonesuch"))
```

- [ ] **Step 7: Run it to verify it fails**

Run: `./sbtw 'testOnly oathdigital.gameplay.powers.DeclaredNotesSuite'`

Expected: FAIL to compile, "Not found: DeclaredNotes".

- [ ] **Step 8: Write `DeclaredNotes`**

Create `src/main/scala/oathdigital/gameplay/powers/DeclaredNotes.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.RuleNotes
import oathdigital.model.{NoteKey, PowerId}

/** Every note key the catalog declares, by power (derive note keys design,
  * "One declared set union"). The walker reads it to reject a note no power
  * declared, and the Game Log's `NoteWordings` builds its templates from it,
  * so both read one union instead of each rebuilding its own.
  */
final class DeclaredNotes private (val keys: Map[PowerId, Vector[NoteKey]]):
  def declares(power: PowerId, key: String): Boolean =
    keys.get(power).exists(_.exists(_.name == key))

object DeclaredNotes:
  def of(catalog: ExecutableCatalog): DeclaredNotes = new DeclaredNotes(
    (WalkerPowerCatalog.default(catalog).powers.map(power =>
      power.id -> power.noteKeys) ++
      PhasePowerCatalog.default(catalog).powers.map(power =>
        power.id -> power.noteKeys) ++ RuleNotes.all)
      .groupMapReduce(_._1)(_._2)(_ ++ _)
      .view.mapValues(_.distinctBy(_.name)).toMap)
```

`groupMapReduce` concatenates rather than replacing, so a power that appears in both catalogs keeps the keys of both. Six powers are listed in both — Silver Tongue, Insomnia, Vow of Obedience, Vow of Poverty, Pied Piper and the Assassin — and each declares the same keys in each, so concatenation alone would hold every one of their key names twice. `distinctBy(_.name)` is therefore load-bearing, not tidying: `PowerNoteCatalogSuite`'s "a power names each of its notes once" reads this union and would fail on all six without it, and so would the declared-keys snapshot. `NoteWordings.++` has no such problem to solve, because it is keyed by `(PowerId, String)` and replaces at that granularity rather than concatenating.

- [ ] **Step 9: Run the test to verify it passes**

Run: `./sbtw 'testOnly oathdigital.gameplay.powers.DeclaredNotesSuite'`

Expected: PASS, four tests.

- [ ] **Step 10: Have `NoteWordings` and the snapshot suite read the union**

In `src/main/scala/oathdigital/application/gamelog/NoteWordings.scala`, replace the body of `default` (lines 33-40) so it folds `DeclaredNotes` instead of walking the two catalogs again. The `sources` and `narrated` maps still come from the powers, which `default` already visits, so keep those two traversals and take only the keys from `DeclaredNotes`:

```scala
  /** The walker powers', the phase powers' and the game rules' keys. */
  def default(catalog: ExecutableCatalog): NoteWordings =
    val declared = DeclaredNotes.of(catalog)
    val templates = declared.keys.toVector.flatMap((power, keys) =>
      keys.map(key => (power, key.name) -> key.template)).toMap
    val powers = WalkerPowerCatalog.default(catalog).powers ++
      PhasePowerCatalog.default(catalog).powers
    NoteWordings(templates,
      powers.flatMap(power => power.noteSource.map(power.id -> _)).toMap,
      powers.flatMap(_.narratedDecisions).toSet)
```

Then in `DeclaredNoteKeysSuite`, replace only the suite's own merge of `listings` with the union, keeping the rendering and both tests as they are:

```scala
  private val declared: Vector[(PowerId, Vector[NoteKey])] =
    DeclaredNotes.of(catalog).keys.toVector
```

Keep `listings` and the cross-catalog test that reads it. That test is about catalog membership, not about declared sets, so the union does not make it trivial: it is what pins the six powers listed in both catalogs and what fails if a seventh appears. Keep the `RuleNotes` import and the two catalog references with it; they are still used by `listings`, and deleting the import as a tidy-up would fail to compile. The one thing that changes is where `declared` comes from, and the snapshot must stay byte-identical across the change — which is what `distinctBy` in Step 8 is for.

- [ ] **Step 11: Run the snapshot and the log suites**

Run: `./sbtw 'testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite oathdigital.application.gamelog.GameLogGoldenSuite'`

Expected: PASS, snapshot byte-identical, every golden log unchanged. A golden diff means `NoteWordings.default` lost a source or a narrated decision — compare the three maps, not the keys.

- [ ] **Step 12: Commit the union**

```bash
git add src/main/scala/oathdigital/gameplay/powers/DeclaredNotes.scala \
        src/main/scala/oathdigital/application/gamelog/NoteWordings.scala \
        src/test/scala/oathdigital/gameplay/powers/DeclaredNotesSuite.scala \
        src/test/scala/oathdigital/gameplay/powers/DeclaredNoteKeysSuite.scala
git commit -m "$(cat <<'MSG'
refactor(notes): one union of every declared note key

The walker powers, the phase powers and the game rules were folded into a
keys-by-power map in the Game Log's wordings and again in its tests.
`DeclaredNotes` owns that union once, in gameplay, where the walker can
also read it. A power in both catalogs keeps the keys of both, as the
wordings' own merge did. The snapshot suite's duplicate-power test goes:
the union is a map, so it cannot fail.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

- [ ] **Step 13: Write the failing test for the undeclared-key check**

Create `src/test/scala/oathdigital/gameplay/walker/UndeclaredNoteSuite.scala`. It asserts the check directly, because reaching it through a real walk needs a power that is deliberately broken:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.powers.DeclaredNotes
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.{NoteArg, PowerId, PowerNote, PowerSourceRef}

/** A note whose key no power declares has no wording, so the Game Log would
  * render nothing for it. The walker refuses to journal it instead (derive
  * note keys design, "The walker check"). */
class UndeclaredNoteSuite extends munit.FunSuite:
  private val declared = DeclaredNotes.of(catalog)
  private val power = PowerId("denizen.warning-signals")
  private val source = PowerSourceRef.Card(oathdigital.model.DenizenId("25"))

  test("a declared key is journaled"):
    val note = PowerNote(source, "redistributed",
      Vector(NoteArg.Player(oathdigital.model.PlayerId("red"))))
    assertEquals(
      NoteCheck.journal(declared, power, note, covers = false).map(_.note),
      Some(note))

  test("an undeclared key is refused, and the failure names both"):
    val note = PowerNote(source, "nonesuch", Vector.empty)
    val thrown = intercept[AssertionError](
      NoteCheck.journal(declared, power, note, covers = false))
    assert(thrown.getMessage.contains("denizen.warning-signals"),
      thrown.getMessage)
    assert(thrown.getMessage.contains("nonesuch"), thrown.getMessage)
```

- [ ] **Step 14: Run it to verify it fails**

Run: `./sbtw 'testOnly oathdigital.gameplay.walker.UndeclaredNoteSuite'`

Expected: FAIL to compile, "Not found: NoteCheck".

- [ ] **Step 15: Write the check**

`PowerNoted` is built at three places — `ProcedureWalker.scala:303`, `AdviserArrivalPass.scala:53` and `BurnTakes.scala:35` — so the check goes in one helper the three call. Add `src/main/scala/oathdigital/gameplay/walker/NoteCheck.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.powers.DeclaredNotes
import oathdigital.model.{PowerId, PowerNote}

/** Journals a note only if its power declares its key (derive note keys
  * design, decisions 5 and 6). A note with no declared key has no wording,
  * so the Game Log renders nothing for it and the player's log is quietly
  * short of a line.
  *
  * In tests and development that is a programming error and fails here, at
  * the note, so any scripted journal that reaches the note fails whether or
  * not its suite asserts the line's text. In production the note is dropped
  * and the error is logged: the Log pane is player-facing, and one missing
  * line must not take the whole pane down with it.
  */
private[walker] object NoteCheck:
  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def journal(declared: DeclaredNotes, power: PowerId, note: PowerNote,
      covers: Boolean): Option[PowerNoted] =
    if declared.declares(power, note.key) then
      Some(PowerNoted(power, note, covers))
    else
      val message = s"${power.value} wrote the undeclared note ${note.key}"
      assert(false, message)
      logger.error(message)
      None
```

`assert` is elided by `-Xdisable-assertions`, which the release build sets and the test build does not, so the same code fails loudly under test and logs in production. Check `build.sbt` for that flag before relying on it: if it is absent, add `-Xdisable-assertions` to the release task's `scalacOptions` only, never to the shared block, and say so in the commit body.

- [ ] **Step 16: Run the test to verify it passes**

Run: `./sbtw 'testOnly oathdigital.gameplay.walker.UndeclaredNoteSuite'`

Expected: PASS, two tests.

- [ ] **Step 17: Route the three journaling sites through the check**

Each of the three already holds the powers it needs. In `ProcedureWalker.scala`, `noted` (lines 300-304) becomes:

```scala
  /** Journals a power's note. A resumed walk skips the children before its
    * cursor, so a note runs only on the walk that first reaches it. */
  private def noted(note: Note, ctx: WalkCtx): WalkCtx =
    note.build(NoteStates(ctx.state, ctx.previous, ctx.answered)).fold(ctx)(
      built => NoteCheck.journal(ctx.declared, note.power, built, note.covers)
        .fold(ctx)(event => ctx.copy(events = ctx.events :+ event)))
```

`WalkCtx` (`WalkState.scala:9-23`) holds `powers: WalkerPowers` but not the union, so add a `declared: DeclaredNotes` field to it and thread it from the four `WalkCtx(...)` construction sites (`ProcedureWalker.scala:91`, `:117`, `:143`, `:565`). Read each of those four to see what it already has in scope; if a catalog is not reachable at one of them, pass `DeclaredNotes` down from the caller rather than rebuilding it per walk, because `DeclaredNotes.of` walks both catalogs.

`AdviserArrivalPass.scala:51-53` becomes:

```scala
      val note = followUp.note.build(NoteStates(updated.state,
        Some((ctx.state, updated.state)), ctx.answered)).flatMap(built =>
        NoteCheck.journal(ctx.declared, followUp.note.power, built,
          followUp.note.covers))
```

`BurnTakes.scala:28-37`: `Swapped.lines` builds `PowerNoted` directly and has no `ctx`. Give `lines` a `declared: DeclaredNotes` parameter and have its caller — `BurnTakes.replace`, which already takes `powers: Vector[ContributingPower]` — pass it through. Replace the `Option.when(amount > 0)(PowerNoted(...))` with:

```scala
        Option.when(amount > 0)(claims.head.take.line(key._3, amount, actor))
          .flatMap(NoteCheck.journal(declared, key._1, _, covers = false))
```

- [ ] **Step 18: Run everything**

Run: `./sbtw test`

Expected: PASS. If a suite now fails with "wrote the undeclared note", that is a real pre-existing bug of exactly the Warning Signals kind: a power emits a key it never declared. **Do not silence it.** Record the power and key in the commit body, add the key to that power's `noteKeys`, and note it in the plan's own progress.

- [ ] **Step 19: Convert Defame and Warning Signals to `NoteSet`**

Two powers as proof of the pattern Tasks 4 and 5 repeat. In `economy/Defame.scala`'s companion, replace the bare `NoteKey(...)` with a set:

```scala
object Defame:
  private val notes = NoteSet(id)
  /** "Burned {1 favor} from {Blue}'s board." */
  val burned: NoteKey = notes.key("burned", /* the existing template, unchanged */)
  def noteKeys: Vector[NoteKey] = notes.declared
```

and in the class replace `override def noteKeys: Vector[NoteKey] = Vector(burned)` with `override def noteKeys: Vector[NoteKey] = Defame.noteKeys`. Read the existing `burned` template out of the file and paste it verbatim; do not retype it, and do not change the `"burned"` string.

`WarningSignals` is the borrowing case. Its companion gains the same pattern for `redistributed`, and because Task 2 already gave it `PlanDiscard.discarded` through `DiscardedAtEnd`, its class keeps `override def planNoteKeys: Vector[NoteKey] = WarningSignals.noteKeys` with no mention of the discard.

- [ ] **Step 20: Run the snapshot and both card suites**

Run: `./sbtw 'testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite oathdigital.gameplay.powers.economy.DefameSuite oathdigital.gameplay.CampaignPowersSuite'`

Expected: PASS, snapshot byte-identical.

- [ ] **Step 21: Commit the check and the two converted powers**

```bash
git add src/main/scala/oathdigital/gameplay/walker/ \
        src/main/scala/oathdigital/gameplay/powers/economy/Defame.scala \
        src/main/scala/oathdigital/gameplay/powers/campaign/WarningSignals.scala \
        src/test/scala/oathdigital/gameplay/walker/UndeclaredNoteSuite.scala
git commit -m "$(cat <<'MSG'
feat(notes): the walker refuses a note no power declared

A note whose key its power never declared has no wording, so the Game Log
rendered nothing for it and the player's log was quietly a line short.
The three places a note is journaled now share one check: under test it
fails at the note, so any scripted journal that reaches it fails; in
production the note is dropped and the error logged, because one missing
line must not take the Log pane down. Defame and Warning Signals declare
their keys through a `NoteSet` as the first two of the catalog.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

---

### Task 4: Convert the powers that own all their keys

The mechanical bulk: every power that owns its keys outright and has an id of its own. 112 of them, converted in two commits rather than one per directory, because the snapshot proves each batch inert and a scripted conversion either applies cleanly or refuses the file by name.

**Files:** every file under `src/main/scala/oathdigital/gameplay/powers/` that contains `def noteKeys` and whose `noteKeys` vector names no key from another object. Counts of files containing `def noteKeys` by directory, for sizing: `action` 72, `campaign` 51, `whenplayed` 25, `economy` 10, `travel` 9, `cardplay` 7, `wake` 5, `targeting` 4, `search` 4, `banner` 4, `setup` 3, `rest` 3, `title` 1, `recover` 1, `dice` 1, `burn` 1.

**Interfaces:**
- Consumes: `NoteSet.apply`, `NoteSet.unowned` and `DeclaredNotes.of` from Task 3.
- Produces: nothing new. Every converted companion exposes `def noteKeys: Vector[NoteKey] = notes.declared`, which Task 5 and Task 6 rely on.

- [ ] **Step 1: List the files that borrow nothing**

Run:

```bash
cd /Users/roman/projects/oathdigital
grep -rln 'def noteKeys' --include='*.scala' src/main/scala/oathdigital/gameplay/powers \
  | while read -r f; do
      if ! grep -A4 'def noteKeys' "$f" | grep -qE '[A-Z][A-Za-z]+\.[a-z][A-Za-z]*'; then
        echo "$f"
      fi
    done | tee /tmp/own-keys.txt | wc -l
```

Expected: on the order of 131 paths. The filter is a heuristic — it looks for a qualified reference in the `noteKeys` body — so before converting a file, read its `noteKeys` and confirm every key in it is defined in that same file. A file the heuristic misclassifies belongs to Task 5; move it there rather than forcing it.

- [ ] **Step 2: Convert one directory**

Work one directory at a time, smallest first (`burn`, `dice`, `recover`, `title`, then upward), so the pattern is settled before `action`'s 72 files. For each file, in its companion object:

```scala
object MovingMarket:
  private val notes = NoteSet(id)
  /** "Moved {card} to {site}." */
  val moved: NoteKey = notes.key("moved", /* existing template, verbatim */)
  /** "Nothing could move." */
  val movedOnly: NoteKey = notes.key("moved-only", /* existing template */)
  def noteKeys: Vector[NoteKey] = notes.declared
```

and in the class, `override def noteKeys: Vector[NoteKey] = MovingMarket.noteKeys` — or `override def planNoteKeys` for a `DiscardedAtEnd` plan from Task 2.

Four rules, each of which the snapshot will catch if broken:
- Copy every template verbatim. Never retype one, and never change a key's name string.
- `notes` must be declared **above** the keys that use it, and `noteKeys` must be a `def`.
- A key must stay a plain `val`. A `lazy val` never registers.
- `private val notes` is read by `noteKeys` in the same object, so `-Wunused:privates` is satisfied; if a companion ends up with `notes` unused, the conversion is incomplete.

- [ ] **Step 3: Run the snapshot after each directory**

Run: `./sbtw 'testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite'`

Expected: PASS, byte-identical. A diff names the power and the key: either a key was dropped (`notes` declared below it, or a `lazy val`) or a name string was changed.

- [ ] **Step 4: Run the directory's own suites**

Run: `./sbtw 'testOnly oathdigital.gameplay.powers.<directory>.*'`

Expected: PASS.

- [ ] **Step 5: Commit the directory**

```bash
git add src/main/scala/oathdigital/gameplay/powers/<directory>/
git commit -m "$(cat <<'MSG'
refactor(powers): <directory> powers declare their note keys by creating them

The keys these powers own are now created through their own `NoteSet`, so
`noteKeys` reads the set instead of relisting it and a key cannot be
defined without being declared. Every key keeps its name and its wording,
so the declared-keys snapshot is unchanged.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

- [ ] **Step 6: Repeat Steps 2 to 5 for every remaining directory**

Order: `burn`, `dice`, `recover`, `title`, `rest`, `setup`, `banner`, `search`, `targeting`, `wake`, `cardplay`, `travel`, `economy`, `whenplayed`, `campaign`, `action`. Sixteen commits.

- [ ] **Step 7: Run everything**

Run: `./sbtw test`

Expected: PASS, every golden log unchanged.

---

### Task 5: Convert the borrowed keys

80 files, counted rather than estimated once Task 4 had taken every power that owns its keys outright. Explicit `include` where a key is relisted, plus compile-time evidence where a helper builds the key or the note itself.

Task 4 drew its boundary at the shape it could convert safely: a power with an id of its own that defines each of its keys as `val k: NoteKey = NoteKey("name", template)`. 112 powers had it. The 80 that remain do not, in four shapes, and the counts are exact:

- **34 relist a key another object owns.** `include` the owner's set. The owners are `VowOfWandering`, `RollResults`, `NoteSupport`, `RelicDraws`, `CardPlayRules`, `SparedForce`, `PlanDiscard`, `Outriders`, `SingleShields`, `VisionPlay`, `RulerToll`, `ExtraWarbands`, `EdificeSetupSupport` and `RegionDiscard` — fourteen, not the eleven the design guessed.
- **24 take their key from a factory.** `Alchemist`'s only key is `val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)`: the name is the caller's, the wording the helper's. `include` cannot declare it, because the key does not exist until the helper is called. The factory takes the caller's set instead and creates the key in it, which declares it and is the evidence in one move: `NoteSupport.gainedKey(notes, NoteKey.Used)`.
- **17 name a key that is neither a literal nor a plain borrow** — an alias, a `def`, or a key whose name differs from the field holding it (`HornedMask`'s `noteKeys` names `taken` where the file defines `took`). Read each before deciding; they are few.
- **5 own shared keys but have no power id**: `ActingSite`, `SuitOnlyAdvisers`, `NarrowPassSitePower` in `TravelSitePowers`, `FortressRule` in `FortressRules`, and `GreatMarketRules`, whose keys are split between `GreatMarket` and `BanditMarket`. Each takes `NoteSet.unowned`, and the trait or powers that carry its keys include it. `BurnVows` belongs here too: `BurnVow.taken(one, many)` is a factory two vow objects call with different words, so it takes each caller's set as the 24 above do.

Task 4's conversion was scripted, and the script refused any file outside the one shape it handled, naming the file and the reason rather than forcing it. The four shapes above are that refusal list, grouped.

**Files:**
- Modify, to own an unowned set: `powers/RelicDraws.scala:6`, `powers/NoteSupport.scala:10`, `powers/RulerToll.scala:17`, `powers/whenplayed/RegionDiscard.scala:86`, `powers/setup/EdificeSetupSupport.scala:9`, `powers/campaign/PlanDiscard.scala:20,25-26`, plus the owners of `SparedForce.spared`, `RollResults.rolled`, `VisionPlay.noFaceup`, `CardPlayRules.discardFirst` and `BannerFacePowers.printed`
- Modify, to include rather than relist: every file whose `noteKeys` names a key from another object — the complement of Task 4's list
- Modify: `powers/campaign/DiscardedAtEnd.scala` (take `PlanDiscard`'s evidence)

**Interfaces:**
- Consumes: `NoteSet.unowned`, `NoteSet.include` from Task 3; `DiscardedAtEnd` from Task 2.
- Produces: `PlanDiscard.notes`, `RelicDraws.notes`, `RegionDiscard.notes`, `EdificeSetupSupport.notes`, `NoteSupport.notes`, each an unowned `NoteSet`; and `final case class Borrowed[A] private (set: NoteSet)` as the evidence the five helpers' builders require.

- [ ] **Step 1: List the files that borrow**

Run:

```bash
cd /Users/roman/projects/oathdigital
comm -13 <(sort /tmp/own-keys.txt) \
  <(grep -rln 'def noteKeys' --include='*.scala' \
      src/main/scala/oathdigital/gameplay/powers | sort) \
  | tee /tmp/borrowed-keys.txt | wc -l
```

Expected: on the order of 70 paths, the complement of Task 4's list.

- [ ] **Step 2: Give each helper an unowned set**

For each helper that owns a key no power owns, replace the bare `NoteKey(...)` with a set. `PlanDiscard` is the worked example — `campaign/PlanDiscard.scala:25-26` becomes:

```scala
object PlanDiscard:
  val notes: NoteSet = NoteSet.unowned("PlanDiscard")
  /** "Discarded after the Campaign." */
  val discarded: NoteKey = notes.key("discarded",
    Vector(NotePart.Text("Discarded after the Campaign.")))
```

Do the same for `RelicDraws`, `NoteSupport`, `RulerToll`, `RegionDiscard`, `EdificeSetupSupport`, and the owners of `SparedForce.spared`, `RollResults.rolled`, `VisionPlay.noFaceup`, `CardPlayRules.discardFirst` and `BannerFacePowers.printed`. `RulerToll.keys` is a vector of keys rather than one key; it becomes `def keys: Vector[NoteKey] = notes.declared`, which is the same vector derived instead of relisted.

- [ ] **Step 3: Replace each relist with an include**

In each borrowing power's companion:

```scala
object Outriders:
  private val notes = NoteSet(id)
  val skulls: NoteKey = notes.key("skulls", /* existing template */)
  notes.include(VowOfWandering.notes)
  def noteKeys: Vector[NoteKey] = notes.declared
```

A power that borrowed from another **power** includes that power's set, so the owning power's companion needs its `notes` visible — change `private val notes` to `val notes` on the ~8 powers that are borrowed from (`Outriders`, `VowOfWandering`, `WildMounts`, `TollRoads`, `WarTortoise` and any the Step 1 list adds), and leave it private on the rest.

Watch for the case where a power borrowed a key *and* declared the owner's whole vector, such as `Curfew.scala:40`'s `RulerToll.keys ++ ...` — that becomes `notes.include(RulerToll.notes)` plus its own keys.

- [ ] **Step 4: Run the snapshot after each batch**

Run: `./sbtw 'testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite'`

Expected: PASS, byte-identical. Convert in batches of one directory and run this between batches; a diff here most often means an `include` was forgotten, which drops every borrowed key at once and is loud.

- [ ] **Step 5: Commit the includes**

```bash
git add src/main/scala/oathdigital/gameplay/powers/
git commit -m "$(cat <<'MSG'
refactor(powers): a borrowed note key is included, not relisted

A key a power borrows from a helper or from another power was copied into
the borrower's `noteKeys`, where it could be forgotten. The borrower now
includes the owner's set, so the key travels with its owner and a helper
that gains a key gives it to every borrower at once.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

- [ ] **Step 6: Write the failing test for the evidence**

The five helpers that build the note themselves can demand the inclusion in their signature. Create `src/test/scala/oathdigital/gameplay/powers/campaign/PlanDiscardEvidenceSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.model.NoteSet

class PlanDiscardEvidenceSuite extends munit.FunSuite:
  test("a set that includes PlanDiscard's notes can prove it"):
    val notes = NoteSet(oathdigital.model.PowerId("test.plan"))
    notes.include(PlanDiscard.notes)
    assert(PlanDiscard.borrowedBy(notes).isDefined)

  test("a set that does not include them cannot"):
    val notes = NoteSet(oathdigital.model.PowerId("test.other"))
    assertEquals(PlanDiscard.borrowedBy(notes), None)
```

- [ ] **Step 7: Run it to verify it fails**

Run: `./sbtw 'testOnly oathdigital.gameplay.powers.campaign.PlanDiscardEvidenceSuite'`

Expected: FAIL to compile, "value borrowedBy is not a member".

- [ ] **Step 8: Add the evidence type and the five builders' parameter**

In `model/PowerNotes.scala`, beside `NoteSet`:

```scala
/** Evidence that a declared set includes the keys `A`'s builder writes, so a
  * power cannot call that builder without declaring the key it writes
  * (derive note keys design, "Borrowed keys"). */
final case class Borrowed[A] private[model] (set: NoteSet)
object Borrowed:
  /** `Some` when `set` includes every key of `owned`. */
  def of[A](set: NoteSet, owned: NoteSet): Option[Borrowed[A]] =
    Option.when(owned.declared.forall(key =>
      set.declared.exists(_.name == key.name)))(new Borrowed[A](set))
```

`NoteSet` needs `declared` to be reachable from `Borrowed`, which it is: both are in `oathdigital.model`.

In `PlanDiscard`, add the accessor and require the evidence:

```scala
  /** Evidence that `set` declares this discard's line. */
  def borrowedBy(set: NoteSet): Option[Borrowed[PlanDiscard.type]] =
    Borrowed.of(set, notes)

  def afterCampaign(catalog: ExecutableCatalog, power: PowerId, use: PlanUse,
      card: DenizenId)(using Borrowed[PlanDiscard.type])
      : Vector[Operation] = Vector(
    denizen(catalog, use.user.getOrElse(use.actor), card),
    discardedNote(power, card))
```

`DiscardedAtEnd` is the one caller for all 12 plans, so it supplies the evidence once:

```scala
  private given Borrowed[PlanDiscard.type] =
    PlanDiscard.borrowedBy(planNotes).getOrElse(throw new AssertionError(
      s"${id.value} discards at end without declaring the discard's line"))
```

where `planNotes` is a new abstract `def planNotes: NoteSet` on the trait that each plan's companion supplies. Do the same for `RelicDraws`, `RegionDiscard`, `EdificeSetupSupport` and `NoteSupport`: each gains `borrowedBy`, and each of its note-building functions gains a `(using Borrowed[Owner.type])`.

- [ ] **Step 9: Run the evidence test to verify it passes**

Run: `./sbtw 'testOnly oathdigital.gameplay.powers.campaign.PlanDiscardEvidenceSuite'`

Expected: PASS, two tests.

- [ ] **Step 10: Prove the evidence bites**

Temporarily delete `notes.include(PlanDiscard.notes)` from one plan's companion — `StormCaller` is the smallest — and run `./sbtw compile`.

Expected: FAIL, either at compile time for no given instance, or at catalog construction with "storm-caller discards at end without declaring the discard's line". Either is acceptable; a build that **passes** means the evidence is not wired and must be fixed before committing. Restore the `include` afterwards and confirm `./sbtw compile` passes again.

- [ ] **Step 11: Run everything**

Run: `./sbtw test`

Expected: PASS, snapshot byte-identical, every golden log unchanged.

- [ ] **Step 12: Commit the evidence**

```bash
git add src/main/scala/oathdigital/model/PowerNotes.scala \
        src/main/scala/oathdigital/gameplay/powers/ \
        src/test/scala/oathdigital/gameplay/powers/campaign/PlanDiscardEvidenceSuite.scala
git commit -m "$(cat <<'MSG'
feat(notes): a helper that writes a note demands the caller declare it

The five helpers that build a note for a power -- the end-of-Campaign
discard, relic draws, the regional discard, the edifice setup discard and
the shared take line -- now take evidence that the calling power declared
the key they write. Forgetting the declaration stops the build instead of
dropping a line from the player's log, which is how Warning Signals
discarded in silence.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

---

### Task 6: Close the door

With every key created through a set, the bare constructor is the only way back to the old idiom.

**Files:**
- Modify: `src/main/scala/oathdigital/model/PowerNotes.scala:58` (`NoteKey`'s constructor)
- Modify: whatever the compiler then reports

**Interfaces:**
- Consumes: every companion's `notes.key(...)` from Tasks 3 to 5.
- Produces: nothing. `NoteKey.apply` is no longer callable outside `oathdigital.model`.

- [ ] **Step 1: Confirm nothing outside the model builds a key directly**

Run:

```bash
cd /Users/roman/projects/oathdigital
grep -rn 'NoteKey(' --include='*.scala' src/main | grep -v 'src/main/scala/oathdigital/model/PowerNotes.scala'
```

Expected: no output. Any hit is a power Tasks 4 and 5 missed — convert it before going on, and say which in the commit body.

- [ ] **Step 2: Close the constructor**

In `src/main/scala/oathdigital/model/PowerNotes.scala`, change the case class so only the model can construct it:

```scala
final case class NoteKey private[model] (name: String,
    template: Vector[NotePart]):
```

A `private[model]` constructor on a case class leaves its synthesised `apply` and `copy` equally restricted in Scala 3, so no caller outside the package can route around it.

- [ ] **Step 3: Compile and fix what the compiler reports**

Run: `./sbtw compile`

Expected: PASS. If a test source fails instead, that is fine and expected: a test that builds a `NoteKey` directly should take one from the power it is testing. Fix each by reading the key off its owner (`Defame.burned`, not a fresh `NoteKey("burned", ...)`).

- [ ] **Step 4: Run everything**

Run: `./sbtw test`

Expected: PASS, snapshot byte-identical, every golden log unchanged.

- [ ] **Step 5: Record the posture the mechanism replaced**

`application/gamelog/NoteWordings.scala:8-14`'s scaladoc still says "A note whose power or key is missing here renders nothing." That is no longer the posture. Replace that sentence with:

```scala
  * one, and the decisions whose answer a power's notes tell. Every key here
  * comes from the power's own `NoteSet`, so a key cannot reach a note
  * without a wording; a note the walker could not match to a declared key
  * never arrives (`NoteCheck`).
```

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/model/PowerNotes.scala \
        src/main/scala/oathdigital/application/gamelog/NoteWordings.scala
git commit -m "$(cat <<'MSG'
refactor(notes): a note key can only be made by declaring it

Every key in the catalog now comes from its owner's `NoteSet`, so the bare
constructor is the only way back to a key that is defined without being
declared. Closing it to the model leaves one way to make a key. The
wordings' scaladoc no longer promises that a missing key renders nothing:
it cannot be missing, and a note the walker cannot match never arrives.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

---

## Self-Review

**Spec coverage.** Decision 1 (derive, loud backstop) is Tasks 3 to 6. Decision 2 (`NoteSet` in `model/PowerNotes.scala`) is Task 3 Step 3. Decision 3 (include plus evidence for five helpers) is Task 5. Decision 4 (buffer, `def`, snapshot-guarded) is Task 3 Step 3's scaladoc and Task 4 Step 2's four rules. Decisions 5 and 6 (log in production, fail under test) are Task 3 Step 15. Decision 7 (snapshot first) is Task 1. Decision 8 (keys only) is honoured: no task touches `noteSource` or `narratedDecisions` except Task 3 Step 10, which preserves both. Decision 9 (close the idiom) is Task 6. Decision 10 (`PlanDiscard` first) is Task 2. Decision 11 (CONTEXT.md) is Task 1 Step 7. The spec's "One declared set union" section is Task 3 Steps 6 to 12. No spec section is unimplemented.

**Known soft spots, flagged rather than hidden.**
- Task 3 Step 15 depends on `-Xdisable-assertions` being set for the release build and not the test build. The step says to check `build.sbt` and what to do if it is absent, rather than assuming.
- Task 3 Step 17 adds a field to `WalkCtx` and threads it through four construction sites. The step says to read all four rather than guessing, because one of them (`ProcedureWalker.scala:565`) is in a different code path from the other three.
- Tasks 4 and 5 are split by a `grep` heuristic for a qualified reference in the `noteKeys` body. Both steps say to confirm by reading the file and to move a misclassified file to the other task.
- Task 5 Step 8's `planNotes` is a new abstract member on `DiscardedAtEnd`, which means Task 2's 12 plans are all touched again in Task 5. That is deliberate: Task 2 must not depend on a mechanism Task 3 has not built yet.

**Type consistency.** `NoteSet.apply(power: PowerId)`, `NoteSet.unowned(label: String)`, `key(name, template)`, `include(other)`, `declared`, `owner` are used with those names and types in Tasks 3, 4 and 5. `DeclaredNotes.of(catalog)`, `.keys`, `.declares(power, key)` likewise in Tasks 3 and 5. `NoteCheck.journal(declared, power, note, covers)` returns `Option[PowerNoted]` and is called that way at all three sites. `DiscardedAtEnd`'s `planNoteKeys`, `otherWindows`, `endDiscard` are used with those names in Task 2 Steps 4, 7 and 8, and `planNotes` is added in Task 5 Step 8 only. `Borrowed.of[A](set, owned)` and `borrowedBy(set)` match between Task 5 Steps 6 and 8.
