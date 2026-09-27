# Power Log Lines, Slice 1 (Mechanism) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give powers one generic way to write their own Game Log lines. A power journals a note where its effect happens, and the log renders it as "{Card}: {Sentence}". Vow of Peace's removed sacrifice decision and Gambling Hall's roll and gain are the first two powers to use it.

**Architecture:**

- A new model vocabulary (`PowerNote`, `NoteArg`, `NoteKey`, `NotePart`, `NoteStates`) and a new tree node, `Note`. `Note` is an `Operation`, not a `CoreOperation`. It changes no state and is never part of a recorded batch.
- The walker journals a `PowerNoted` audit event when it reaches a `Note`. The note reads the state around the previous step. It also journals one when an `OptionRestriction` or the restriction look-ahead hides an option at a decision it reaches fresh. Replay ignores the event.
- The Game Log renders each note from the template its power declares (`noteKeys`). A power's `used` note replaces "Used {card}". A covering note drops the generic detail lines of the step before it. Notes wait for their action's start line, and an identical note in the same action posts once.

**Tech Stack:** Scala 3 on the JVM, munit. Build through `./sbtw`. There is no frontend or UI change in this slice: the client already renders the `trigger` and `action` kinds. Impeccable is therefore not needed.

**Spec:** `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`. Read the Rulings and sections 1, 2 and 4 before starting. Section 3's tables are later slices, except the Vow of Peace and Gambling Hall rows.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`). `ProcedureWalker.scala` is at 716. Everything this slice adds to it must fit, and larger helpers go to `WalkerPowerGather.scala` (358).
- `gameplay` never imports `application`, `serialization` or `server`. A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. `application` never imports `serialization`.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree. `EnterWorktree` branches from `origin`, which lags local `main`, so fast-forward the new branch to local `main` first. Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- Line wording follows the spec exactly:
  - "Gambling Hall: {Red} rolled {dice}, Total: {n}", with no final period.
  - "Gambling Hall: {Red} gained {n} favor from {the Order bank}."
  - "Vow of Peace: The attacker cannot sacrifice against {Blue}."
- Baselines: record the server test count from your first full `./sbtw test` run in the worktree, and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

### Decisions this plan makes that the spec left open

These were settled while planning (2026-09-26). Task 6 records each one in the spec.

1. **A note carries its source.** `PowerNote(source: PowerSourceRef, key, args)`. `PowerSourceRef` (card, site or banner) is what the log already names sources by, and the hide hook returns a whole note. So the source goes on the note rather than on the leaf. It is not the `RuleSourceRef` the spec named: several powers, Vow of Peace among them, have only a game-rule `RuleSourceRef`.
2. **`Note` is a plain `Operation`.** It is not a `PrimitiveOperation`, because that type is sealed and is a `CoreOperation`. Its `build: NoteStates => Option[PowerNote]` may return `None`, and then nothing is journaled. That is how "gained 0" writes no line.
3. **`NoteStates(now, previous, answered)`.** The `answered` field was added so a note can name what the player chose, such as Gambling Hall's bank.
4. **`PowerNoted(power, note, covers)`.** The event carries `covers`, because the formatter needs it.
5. **A template lives on its `NoteKey`.** A power declares `noteKeys: Vector[NoteKey]`, on both `ContributingPower` and `PhasePower`, and builds notes through those same keys. A key and its template therefore cannot drift apart. A missing template can happen only for an old journal.
6. **`NoteArg.Number`.** A unitless number, for "Total: 8".
7. **The hide hook covers `ChooseOne` and `ChooseMany` options only.** A `ChooseAmount` narrowed by the look-ahead writes no note, because there is no option to name.
8. **Merging compares journal facts.** Two notes merge when their power and their whole note (source, key and args) are equal. Rendered text differs between viewers, so it is never compared.
9. **Held notes post right after the start line,** at the event that posts it. A procedure "opens" when it has a start line: Campaign, or any procedure with a start title.
10. **Capitals are the template's job.** A catalog test holds every template to starting with an argument or a capital letter. The formatter does not change case.
11. **The "every phase power declares `used`" test waits for slice 2.** Slice 1 gives only Gambling Hall a `used` line.
12. **Placing a `used` note.** A phase power puts its `used` note in the same command as its first effect. "Used {card}" is decided when the first effect step posts, and it looks ahead only to the end of that segment.

### Facts this plan relies on (verified against the code on 2026-09-26, at `b536dbb7`)

- **Walker.**
  - `ProcedureWalker.walk` dispatches on `Repeat`, `Branch` and `PrimitiveOperation`, and walks anything else as a composite of its `children`.
  - `WalkCtx(state, events, activePlayer, answered, powers, dice, procedure, root)` threads events forward.
  - A resumed walk skips the children before its cursor (`walkChildren`), so a leaf before a park never runs twice.
  - `walkFolded(window, operation, children, ctx, path, cursor, resume, hooks)` calls `WalkerPowerGather.applyWindow(..., resuming = cursor.isDefined)`.
  - `runLeaf` narrows a `Decide` with `WalkerPowerGather.probe` when `asked(cursor, resume)`. That happens when the decision is reached fresh (`cursor.isEmpty`) or at the end of an answer's cursor.
  - `walkRepeat.pass` stops when a pass recorded no event and asked nothing (`next.events.size == current.events.size`).
- **Gather.**
  - `WalkerPowerGather.applyWindow` restricts options only when the windowed operation is itself a `Decide` (`restrictOptions`).
  - `restrictionViolations` gathers `Restriction`s over every window of the tree against the root.
  - `probe` removes an option when answering it adds a violation not already in the baseline.
- **Contributions.**
  - `Restriction(fn)` and `OptionRestriction(fn)` are case classes in `gameplay/powerresolver/ContributingPower.scala`.
  - No production code destructures them. `ContributionCollector` matches them by type.
- **Events and replay.**
  - `WalkerEvent` is open.
  - `OathRules.evolve` routes the three walker events to `ProcedureWalker.applyRecorded` and rejects any other `WalkerEvent`.
  - `WalkerReplay.applyRecordedReady` ends with `case step: WalkerStepRecorded => invalid(...)` and `case other => invalid(...)`.
- **Codec.**
  - `WalkerEventCodec` holds `walkerDiscriminator`, `walkerEncoder` and `walkerDecode`.
  - `GameEventWire` holds the type strings.
  - `GameEventJsonSupport` offers `encodeCardId`/`decodeCardId`, `decodeSuit`, `decodeBanner(value: String, path)`, `decodeSignedInt` and `traverse`.
  - `WalkerEventCodec` has `decodeDieFace(value: String, path)` and encodes faces inline in `encodeStepPayload`.
- **Game Log.**
  - `GameLogFormatter.walker` posts, in order: start, continued, actions, details and turn headlines.
  - A `Run(procedure, actor, first, started)` has `started` set once the start line has posted.
  - `LogJournal.segmentEnd(index)` lets a line read ahead to the end of its segment.
  - `ActionLines.usedPower` posts "Used {card}" at the first effect step. A `ChoicePayload` counts as an effect.
  - `ActionLines.powerSource` renders a `PowerSourceRef` inline.
  - `DetailLines.lines = decision ++ roll ++ deltas`.
  - `StartLines.title(procedure)` is private.
  - `LogKind.Trigger` exists, and the client renders it.
- **Cards.**
  - Gambling Hall's tree is `ModifyDicePool`, an automatic `Roll`, a `Branch` holding the bank `Decide` (only when the score is above 0), and a `BuildOps` that gains `Gain.Favor(player, suit, total)`.
  - Vow of Peace's `Transform` at `CampaignSacrificeSelection` returns `Vector.empty` when the defender holds it faceup. That window sits on the sacrifice `Decide` itself.
- **Tests.**
  - `PaidActionHarness` (`use`, `answer`, `replayed`, `wireRoundTrips`) drives phase powers.
  - `PlanDriver.Run.finish` answers a Campaign to the end, and `Run.events` holds every event.
  - `LogScripts` builds journals by real play. `LogScripts.named` feeds `GameLogPropertiesSuite`'s prefix-stability, same-keys and no-leak properties.
  - `ParkedServiceFixture.silverTongueCard` is `DenizenId("92")`.

---

### Task 1: Note vocabulary and the walker's `Note` node

**Files:**
- Create: `src/main/scala/oathdigital/model/PowerNotes.scala`
- Modify: `src/main/scala/oathdigital/model/GameState.scala` (the `PowerSourceRef` companion)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala`
- Test: `src/test/scala/oathdigital/gameplay/PowerNoteWalkerSuite.scala`

**Interfaces:**
- Produces:
  - model: `PowerNote(source: PowerSourceRef, key: String, args: Vector[NoteArg])`
  - model: `NoteArg.{Player(PlayerId), Card(CardId), Site(SiteId), Amount(Int, NoteUnit), Number(Int), Bank(Suit), Dice(Vector[DieFace])}`
  - model: `NoteUnit.{Favor, Secret, Supply, Warband}`, each with `key` and `word(n)`, and `NoteUnit.fromKey`
  - model: `NotePart.{Text(String), Arg(Int), Plural(Int, String, String)}`
  - model: `NoteKey(name, template)` with `apply(source, args*): PowerNote`, and `NoteKey.Used = "used"`
  - model: `NoteStates(now: ReadyGame, previous: Option[(ReadyGame, ReadyGame)], answered: Vector[Answered])`
  - model: `Note(power: PowerId, build: NoteStates => Option[PowerNote], covers: Boolean = false) extends Operation`
  - model: `PowerSourceRef.of(ref: DecisionOptionRef): Option[PowerSourceRef]`
  - walker: `PowerNoted(power: PowerId, note: PowerNote, covers: Boolean) extends WalkerEvent`

- [ ] **Step 1: Write the failing test**

Create `src/test/scala/oathdigital/gameplay/PowerNoteWalkerSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.walker.{PowerNoted, ProcedureWalker, WalkerOutcome,
  WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

/** The `Note` node (power log lines design, section 1). */
class PowerNoteWalkerSuite extends munit.FunSuite:
  private val actor: PlayerId = playerId
  private val power = PowerId("test.noting")
  private val source = PowerSourceRef.Site(SiteId("test-site"))
  private val pool = PoolKey("test-pool")
  private def said(word: String) = PowerNote(source, word, Vector.empty)
  private def note(word: String): Note = Note(power, _ => Some(said(word)))
  private def noted(word: String) = PowerNoted(power, said(word), covers = false)
  private def button(key: String) =
    DecisionOption.Button(DecisionOptionRef.Button(key), key)
  private val ask = Decide("ask", actor,
    DecisionQuery.ChooseOne(Vector(button("yes"))))
  private val yes = Answered("ask",
    DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("yes")), actor)
  private def notes(events: Vector[OathEvent]): Vector[OathEvent] =
    events.filter(_.isInstanceOf[PowerNoted])

  test("a note is journaled where the walk reaches it, between the steps"):
    val tree = Sequence(Vector[Operation](note("first"),
      ModifyDicePool(pool, 1), note("second")))
    ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty) match
      case Right(WalkerOutcome.Finished(_, events)) =>
        assertEquals(events.map {
          case noted: PowerNoted => noted.note.key
          case _: WalkerStepRecorded => "step"
          case other => other.toString
        }, Vector("first", "step", "second"))
      case other => fail(s"expected a finished walk, got $other")

  test("a note reads the states around the step before it"):
    var seen = Option.empty[NoteStates]
    val reading = Note(power, states => { seen = Some(states); Some(said("read")) })
    val tree = Sequence(Vector[Operation](ModifyDicePool(pool, 1), reading))
    assert(ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty).isRight)
    val states = seen.get
    val (before, after) = states.previous.get
    assertEquals(after, states.now)
    assertEquals(before.game.current.rollPools.get(pool), None)
    assert(after.game.current.rollPools.contains(pool))

  test("a note before any step has no previous step"):
    var seen = Option.empty[NoteStates]
    val reading = Note(power, states => { seen = Some(states); None })
    assert(ProcedureWalker.advance(ready, Sequence(Vector[Operation](reading)),
      None, WalkerPowers.empty).isRight)
    assertEquals(seen.map(_.previous), Some(None))

  test("a note whose build says nothing journals nothing"):
    val tree = Sequence(Vector[Operation](Note(power, _ => None),
      ModifyDicePool(pool, 1)))
    val Right(WalkerOutcome.Finished(_, events)) =
      ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty): @unchecked
    assertEquals(notes(events), Vector.empty)

  test("a note in a branch that does not select it is not journaled"):
    val tree = Branch((state, _) =>
      if state.game.current.rollPools.contains(pool) then Vector(note("chosen"))
      else Vector(ModifyDicePool(pool, 1)))
    val Right(WalkerOutcome.Finished(_, events)) =
      ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty): @unchecked
    assertEquals(notes(events), Vector.empty)

  test("a resumed command journals only the notes after the park"):
    val tree = Sequence(Vector[Operation](note("before"), ask, note("after")))
    val Right(WalkerOutcome.Parked(pending, first)) =
      ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty): @unchecked
    assertEquals(notes(first), Vector(noted("before")))
    val Right(WalkerOutcome.Finished(_, second)) = ProcedureWalker.resolve(ready,
      tree, pending, yes, WalkerPowers.empty): @unchecked
    assertEquals(notes(second), Vector(noted("after")))

  test("each new pass of a Repeat journals its note again"):
    val tree = Repeat((_, pending) => pending.answered.size < 2,
      Sequence(Vector[Operation](note("pass"), ask)))
    val Right(WalkerOutcome.Parked(first, e1)) =
      ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty): @unchecked
    assertEquals(notes(e1), Vector(noted("pass")))
    val Right(WalkerOutcome.Parked(second, e2)) = ProcedureWalker.resolve(ready,
      tree, first, yes, WalkerPowers.empty): @unchecked
    assertEquals(notes(e2), Vector(noted("pass")))
    val Right(WalkerOutcome.Finished(_, e3)) = ProcedureWalker.resolve(ready,
      tree, second, yes, WalkerPowers.empty): @unchecked
    assertEquals(notes(e3), Vector.empty)

  test("a Repeat whose pass only notes stops after one pass"):
    ProcedureWalker.advance(ready, Repeat((_, _) => true, note("only")), None,
        WalkerPowers.empty) match
      case Right(WalkerOutcome.Finished(_, events)) =>
        assertEquals(events, Vector[OathEvent](noted("only")))
      case other => fail(s"expected one pass, got $other")

  test("replay applies a note as nothing"):
    val state = OathState.Ready(ready)
    assertEquals(ProcedureWalker.applyRecorded(state, noted("x")), Right(state))

  test("a phase power's selected card, relic, edifice or site is its note source"):
    assertEquals(PowerSourceRef.of(DecisionOptionRef.Denizen(DenizenId("93"))),
      Some(PowerSourceRef.Card(DenizenId("93"))))
    assertEquals(PowerSourceRef.of(DecisionOptionRef.Site(SiteId("s"))),
      Some(PowerSourceRef.Site(SiteId("s"))))
    assertEquals(PowerSourceRef.of(DecisionOptionRef.Button("b")), None)
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.PowerNoteWalkerSuite"`
Expected: compile failure, because `Note`, `NoteStates`, `PowerNote` and `PowerNoted` do not exist.

- [ ] **Step 3: Write the model vocabulary**

Create `src/main/scala/oathdigital/model/PowerNotes.scala`:

```scala
package oathdigital.model

/** What one power says about its own effect, for the Game Log (power log
  * lines design, section 1). `source` is the card, site or banner the line
  * starts with. `key` picks the power's template. `args` are typed
  * references, never text, so a card still passes the log's knowledge rule.
  */
final case class PowerNote(source: PowerSourceRef, key: String,
    args: Vector[NoteArg])

sealed trait NoteArg extends Product with Serializable
object NoteArg:
  final case class Player(id: PlayerId) extends NoteArg
  final case class Card(id: CardId) extends NoteArg
  final case class Site(id: SiteId) extends NoteArg
  /** An amount with its unit, "3 favor". It is always what happened, never
    * the number the card prints. */
  final case class Amount(value: Int, unit: NoteUnit) extends NoteArg
  /** A bare number, "Total: 8". */
  final case class Number(value: Int) extends NoteArg
  /** "the Order bank". */
  final case class Bank(suit: Suit) extends NoteArg
  final case class Dice(faces: Vector[DieFace]) extends NoteArg

/** `key` is the wire spelling. */
enum NoteUnit(val key: String, val one: String, val many: String):
  case Favor extends NoteUnit("favor", "favor", "favor")
  case Secret extends NoteUnit("secret", "secret", "secrets")
  case Supply extends NoteUnit("supply", "Supply", "Supply")
  case Warband extends NoteUnit("warband", "warband", "warbands")
  def word(value: Int): String = if value == 1 then one else many
object NoteUnit:
  def fromKey(key: String): Option[NoteUnit] = values.find(_.key == key)

/** One piece of a note's sentence. `Plural` picks `one` when the argument at
  * `index` is an amount or number of exactly 1, and `many` otherwise. */
enum NotePart:
  case Text(words: String)
  case Arg(index: Int)
  case Plural(index: Int, one: String, many: String)

/** One line a power can write: its key and the sentence the log renders
  * after "{source}: ". A power declares its keys and builds its notes
  * through them, so a key never lacks a template. */
final case class NoteKey(name: String, template: Vector[NotePart]):
  def apply(source: PowerSourceRef, args: NoteArg*): PowerNote =
    PowerNote(source, name, args.toVector)
object NoteKey:
  /** A phase power's own line, which replaces "Used {card}". */
  val Used: String = "used"

/** What a note may read when the walker reaches it. `previous` is the states
  * before and after the step this command journaled last, if any, so a note
  * that restates a step reads the applied amount instead of repeating the
  * operation's cap logic. `answered` holds the action's decisions so far. */
final case class NoteStates(now: ReadyGame,
    previous: Option[(ReadyGame, ReadyGame)], answered: Vector[Answered])

/** A power's line, placed in the tree where its effect happens. The walker
  * journals it as `PowerNoted` when it reaches this node, unless `build`
  * returns `None`. It changes no state and is never part of a recorded
  * batch. `covers` drops the generic detail lines of the step before it
  * (power log lines design, "Covering"). */
final case class Note(power: PowerId, build: NoteStates => Option[PowerNote],
    covers: Boolean = false) extends Operation:
  val children: Vector[Operation] = Vector.empty
```

In `src/main/scala/oathdigital/model/GameState.scala`, add this at the end of `object PowerSourceRef` (after `final case class Banner`):

```scala

  /** The source a phase power's start selection names, for its notes. */
  def of(ref: DecisionOptionRef): Option[PowerSourceRef] = ref match
    case DecisionOptionRef.Denizen(id) => Some(Card(id))
    case DecisionOptionRef.Relic(id) => Some(Card(id))
    case DecisionOptionRef.Vision(id) => Some(Card(id))
    case DecisionOptionRef.Edifice(id) => Some(Card(id))
    case DecisionOptionRef.Site(id) => Some(Site(id))
    case _ => None
```

- [ ] **Step 4: Add the event**

In `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala`, add this after `WalkerCompleted`. Add `PowerNote` to the file's model import, or add `import oathdigital.model.PowerNote` if the file imports names one by one.

```scala

/** Audit fact: a power's effect happened here and says so in the Game Log
  * (power log lines design). Replay applies nothing for it, as it never
  * reads `WalkerStepRecorded.contributions`. `covers` tells the log to drop
  * the generic detail lines of the step before it.
  */
final case class PowerNoted(power: PowerId, note: PowerNote, covers: Boolean)
    extends WalkerEvent
```

- [ ] **Step 5: Walk the `Note` node**

In `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`:

1. Add `Note, NoteStates` to the `oathdigital.model.{...}` import.
2. Give `WalkCtx` a last field, after `root: Operation`:

```scala
      root: Operation,
      /** The states before and after the step this command journaled last,
        * which a note reads. */
      previous: Option[(ReadyGame, ReadyGame)] = None
```

3. In `walk`, add the case before `case composite =>`:

```scala
      case note: Note => Right(Done(noted(note, ctx)))
```

4. Add after `walk`:

```scala
  /** Journals a power's note. A resumed walk skips the children before its
    * cursor, so a note runs only on the walk that first reaches it. */
  private def noted(note: Note, ctx: WalkCtx): WalkCtx =
    note.build(NoteStates(ctx.state, ctx.previous, ctx.answered)).fold(ctx)(
      built => ctx.copy(events = ctx.events :+
        PowerNoted(note.power, built, note.covers)))

  /** Events other than notes: a note changes nothing a guard reads. */
  private def recorded(ctx: WalkCtx): Int =
    ctx.events.count(!_.isInstanceOf[PowerNoted])
```

5. In `walkRepeat.pass`, replace the guard of the first `Done` case:

```scala
          case Done(next) if recorded(next) == recorded(current) &&
              next.answered.size == current.answered.size => Right(Done(next))
```

6. In `recordBatch`, replace the final `ctx.copy(...)`:

```scala
      ctx.copy(state = updated.state, events = events,
        previous = if updated.executed.isEmpty then ctx.previous
          else Some((ctx.state, updated.state)))
```

7. In `answerDecide`, add `previous = Some((ctx.state, ctx.state)),` to the `ctx.copy(`.
8. In `recordRoll`, replace the body of the `map`:

```scala
      val nodeId =
        if path.isEmpty then leafLabel(roll) else path.mkString(".")
      val written = WalkerRolls.write(ctx.state, outcome)
      ctx.copy(
        state = written,
        previous = Some((ctx.state, written)),
        events = ctx.events :+ WalkerStepRecorded(
          nodeId = nodeId, payload = RollPayload(roll.pool, faces, automatic),
          ops = Vector.empty, contributions = contributions))
```

- [ ] **Step 6: Replay applies a note as nothing**

In `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala`, add this case immediately before `case step: WalkerStepRecorded =>` (the unsupported-payload catch-all):

```scala
      // An audit fact for the Game Log; replay has nothing to apply.
      case _: PowerNoted => Right(ready)
```

In `src/main/scala/oathdigital/gameplay/OathRules.scala`, add `PowerNoted` to the `oathdigital.gameplay.walker.{...}` import. Add this line after `case event: WalkerCompleted => ProcedureWalker.applyRecorded(state, event)`:

```scala
      case event: PowerNoted => ProcedureWalker.applyRecorded(state, event)
```

- [ ] **Step 7: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.PowerNoteWalkerSuite oathdigital.gameplay.ProcedureWalkerSuite oathdigital.gameplay.RepeatPassSuite"`
Expected: all pass. Then run `wc -l src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`. Expected: 800 or fewer.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/model/PowerNotes.scala \
  src/main/scala/oathdigital/model/GameState.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala \
  src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala \
  src/main/scala/oathdigital/gameplay/OathRules.scala \
  src/test/scala/oathdigital/gameplay/PowerNoteWalkerSuite.scala
git commit -m "feat(walker): journal a power's note where the walk reaches it"
```

---

### Task 2: The hide hook

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/PhasePower.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
- Test: `src/test/scala/oathdigital/gameplay/PowerNoteHideSuite.scala`

**Interfaces:**
- Consumes: `PowerNote`, `PowerNoted`, `NoteKey` (Task 1).
- Produces:
  - `Restriction(fn, note: (PowerCtx, DecisionOptionRef) => Option[PowerNote] = Contribution.silent)`
  - `OptionRestriction(fn, note = Contribution.silent)`, with the same `note` shape
  - `ContributingPower.noteKeys: Vector[NoteKey]` and `PhasePower.noteKeys: Vector[NoteKey]`, both defaulting to empty
  - `WalkerPowerGather.applyWindowNoted(..., noting: Boolean): (Vector[Operation], Vector[PowerId], Vector[PowerNoted])`
  - `WalkerPowerGather.lookAheadNotes(root, decide, narrowed, state, activePlayer, powers, answered, procedure): Vector[PowerNoted]`

- [ ] **Step 1: Write the failing test**

Create `src/test/scala/oathdigital/gameplay/PowerNoteHideSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction, Restriction}
import oathdigital.gameplay.setup.FirstGameSetupFixture.initialReady
import oathdigital.gameplay.walker.{PowerNoted, ProcedureWalker, WalkerOutcome,
  WalkerPowers}
import oathdigital.model._

object PowerNoteHideSuite:
  val noteSource: PowerSourceRef = PowerSourceRef.Site(SiteId("test-site"))
  val hidden: OathViolation = OathViolation.InvalidEventOrder("hidden by test")

  def hid(ref: DecisionOptionRef): PowerNote = PowerNote(noteSource, "hid",
    ref match
      case DecisionOptionRef.Site(site) => Vector(NoteArg.Site(site))
      case _ => Vector.empty)

  /** Hides `forbidden` at `hook`, and notes each one it hid when `says`. */
  final case class HidingPower(id: PowerId, hook: PowerWindow,
      forbidden: Set[DecisionOptionRef], says: Boolean = true)
      extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(hook -> Vector(OptionRestriction(
        (_, ref) => Option.when(forbidden(ref))(hidden),
        (_, ref) => Option.when(says)(hid(ref)))))

  /** Rejects any action whose tree opens `hook`, and says so. */
  final case class ForbiddingPower(id: PowerId, hook: PowerWindow)
      extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(hook -> Vector(Restriction((_, _) => Some(hidden),
        (_, _) => Some(PowerNote(noteSource, "forbade", Vector.empty)))))

class PowerNoteHideSuite extends munit.FunSuite:
  import PowerNoteHideSuite._

  private val window = PowerWindow.ChallengeAmountSelection
  private def site(id: String) = DecisionOptionRef.Site(SiteId(id))
  private def pick(ready: ReadyGame) = Sequence(Vector[Operation](Decide("pick",
    ready.game.current.turn.activePlayer, DecisionQuery.ChooseOne(
      Vector("a", "b", "c").map(id => DecisionOption.Site(site(id)))),
    window = Some(window))))
  private def notes(events: Vector[OathEvent]): Vector[OathEvent] =
    events.filter(_.isInstanceOf[PowerNoted])

  private val ready = initialReady
  private val actor = ready.game.current.turn.activePlayer
  private val hider = PowerId("test.hider")

  test("each option a power hides is noted before the decision parks"):
    val powers = WalkerPowers(Vector(HidingPower(hider, window,
      Set(site("b"), site("c")))))
    val Right(WalkerOutcome.Parked(_, events)) =
      ProcedureWalker.advance(ready, pick(ready), None, powers): @unchecked
    assertEquals(notes(events), Vector[OathEvent](
      PowerNoted(hider, hid(site("b")), covers = false),
      PowerNoted(hider, hid(site("c")), covers = false)))

  test("answering the decision does not note the hidden options again"):
    val powers = WalkerPowers(Vector(HidingPower(hider, window, Set(site("b")))))
    val tree = pick(ready)
    val Right(WalkerOutcome.Parked(pending, _)) =
      ProcedureWalker.advance(ready, tree, None, powers): @unchecked
    val Right(WalkerOutcome.Finished(_, events)) = ProcedureWalker.resolve(ready,
      tree, pending, Answered("pick", DecisionAnswer.ChooseOneAnswer(site("a")),
        actor), powers): @unchecked
    assertEquals(notes(events), Vector.empty)

  test("a power that says nothing hides silently"):
    val powers = WalkerPowers(Vector(HidingPower(hider, window, Set(site("b")),
      says = false)))
    val Right(WalkerOutcome.Parked(_, events)) =
      ProcedureWalker.advance(ready, pick(ready), None, powers): @unchecked
    assertEquals(notes(events), Vector.empty)

  test("an option the look-ahead hides is noted by the restriction it would break"):
    val nested = PowerWindow.CampaignActionEligibility
    val forbidder = PowerId("test.forbidder")
    val powers = WalkerPowers(Vector(ForbiddingPower(forbidder, nested)))
    def button(key: String) =
      DecisionOption.Button(DecisionOptionRef.Button(key), key)
    val tree = Sequence(Vector[Operation](Decide("ask", actor,
        DecisionQuery.ChooseOne(Vector(button("yes"), button("no")))),
      Branch((_, pending) => if pending.answered.exists {
        case Answered("ask", DecisionAnswer.ChooseOneAnswer(ref), _) =>
          ref == DecisionOptionRef.Button("yes")
        case _ => false
      } then Vector(Sequence(Vector.empty, Some(nested))) else Vector.empty)))
    val Right(WalkerOutcome.Parked(pending, events)) =
      ProcedureWalker.advance(ready, tree, None, powers): @unchecked
    assertEquals(notes(events), Vector[OathEvent](PowerNoted(forbidder,
      PowerNote(noteSource, "forbade", Vector.empty), covers = false)))
    val Right(WalkerOutcome.Finished(_, answered)) = ProcedureWalker.resolve(
      ready, tree, pending, Answered("ask", DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Button("no")), actor), powers): @unchecked
    assertEquals(notes(answered), Vector.empty)

  test("a restriction on a tree with no decision to narrow writes no note"):
    // Rejecting a whole action is OathRules' check before the walk, which
    // journals nothing; the walker notes only options it hid.
    val powers = WalkerPowers(Vector(ForbiddingPower(PowerId("test.forbidder"),
      PowerWindow.CampaignActionEligibility)))
    val tree = Sequence(Vector[Operation](ModifyDicePool(PoolKey("p"), 1)),
      Some(PowerWindow.CampaignActionEligibility))
    val Right(WalkerOutcome.Finished(_, events)) =
      ProcedureWalker.advance(ready, tree, None, powers): @unchecked
    assertEquals(notes(events), Vector.empty)
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.PowerNoteHideSuite"`
Expected: compile failure, because `OptionRestriction` and `Restriction` take one argument.

- [ ] **Step 3: Give contributions their hook and powers their keys**

In `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`:

1. Add `NoteKey, PowerNote` to the model import.
2. Add a companion after `sealed trait Contribution extends Product with Serializable`:

```scala
object Contribution:
  /** The hide hook of a restriction that writes no Game Log line. */
  val silent: (PowerCtx, DecisionOptionRef) => Option[PowerNote] = (_, _) => None
```

3. Replace `Restriction` and `OptionRestriction` with these (keep their doc comments, and add the sentence shown):

```scala
/** Validates the whole action tree root, returning a violation to reject the
  * action wholesale. Covers cannot-effects. `note` is what the Game Log says
  * when the restriction look-ahead hides an option because of it (power log
  * lines design, section 1).
  */
final case class Restriction(
    fn: (PowerCtx, Operation) => Option[OathViolation],
    note: (PowerCtx, DecisionOptionRef) => Option[PowerNote] = Contribution.silent
) extends Contribution
```

```scala
final case class OptionRestriction(
    fn: (PowerCtx, DecisionOptionRef) => Option[OathViolation],
    note: (PowerCtx, DecisionOptionRef) => Option[PowerNote] = Contribution.silent
) extends Contribution
```

4. In `trait ContributingPower`, after `def resolution`, add:

```scala
  /** The Game Log lines this power can write (power log lines design). A
    * note it builds must come from one of these keys. */
  def noteKeys: Vector[NoteKey] = Vector.empty
```

In `src/main/scala/oathdigital/gameplay/powerresolver/PhasePower.scala`, add `NoteKey` to the model import and add the same member to `trait PhasePower`, after `build`:

```scala
  /** The Game Log lines this power can write. The key `NoteKey.Used` names
    * its own line, which replaces "Used {card}". */
  def noteKeys: Vector[NoteKey] = Vector.empty
```

- [ ] **Step 4: Note what option restrictions hide**

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, rename the body of `applyWindow` into `applyWindowNoted`, and keep `applyWindow` as a wrapper:

```scala
  def applyWindow(window: Option[PowerWindow], operation: Operation,
      state: ReadyGame,
      activePlayer: PlayerId, powers: WalkerPowers, path: Vector[String],
      ops: Vector[Operation], procedure: Option[oathdigital.model.ProcedureRef],
      answered: Vector[Answered] = Vector.empty, resuming: Boolean = false)
      : (Vector[Operation], Vector[PowerId]) =
    val (folded, order, _) = applyWindowNoted(window, operation, state,
      activePlayer, powers, path, ops, procedure, answered, resuming,
      noting = false)
    (folded, order)

  /** As [[applyWindow]], and, when `noting`, the notes of the options each
    * `OptionRestriction` hid from a `Decide`. The walker notes only a window
    * it enters fresh, so a resume never says it twice.
    */
  def applyWindowNoted(window: Option[PowerWindow], operation: Operation,
      state: ReadyGame,
      activePlayer: PlayerId, powers: WalkerPowers, path: Vector[String],
      ops: Vector[Operation], procedure: Option[oathdigital.model.ProcedureRef],
      answered: Vector[Answered], resuming: Boolean, noting: Boolean)
      : (Vector[Operation], Vector[PowerId], Vector[PowerNoted]) =
    window match
      case None => (ops, Vector.empty, Vector.empty)
      case Some(w) =>
        val byId: Map[PowerId, ContributingPower] =
          powers.powers.map(power => power.id -> power).toMap
        def ctxFor(power: ContributingPower): PowerCtx =
          PowerCtx(state, activePlayer, power.source, w, path, operation,
            procedure, answered)
        val gathered = ContributionCollector.gather(w, powers.powers, ctxFor)
        val folded = gathered.transforms.foldLeft(ops):
          case (acc, (powerId, transform)) =>
            transform.fn(ctxFor(byId(powerId)), acc)
        operation match
          case _: Decide =>
            (restrictOptions(folded, gathered.optionRestrictions, ctxFor, byId),
              gathered.order,
              if noting then hiddenNotes(folded, gathered.optionRestrictions,
                ctxFor, byId) else Vector.empty)
          case host: OfferHost =>
            val offered = gathered.offers.flatMap { case (powerId, offer) =>
              offer.plan(ctxFor(byId(powerId))).map(OfferedPlan(powerId, _))
            }
            (folded ++ host.expand(offered, OfferHost.Pass(state, answered,
              resuming, WalkerSimulation.applies(_, state, powers))),
              gathered.order, Vector.empty)
          case _ => (folded, gathered.order, Vector.empty)

  /** The options of a choose-one or choose-many decision, in order. */
  private def optionRefs(decide: Decide): Vector[DecisionOptionRef] =
    decide.query match
      case one: DecisionQuery.ChooseOne => one.options.map(_.ref)
      case many: DecisionQuery.ChooseMany => many.options.map(_.ref)
      case _ => Vector.empty

  /** For each option a restriction forbids, the note of the first power
    * that forbids it. */
  private def hiddenNotes(ops: Vector[Operation],
      restrictions: Vector[(PowerId, OptionRestriction)],
      ctxFor: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower]): Vector[PowerNoted] =
    if restrictions.isEmpty then Vector.empty
    else ops.collect { case decide: Decide => decide }.flatMap(decide =>
      optionRefs(decide).flatMap { ref =>
        restrictions.find { case (id, restriction) =>
          restriction.fn(ctxFor(byId(id)), ref).nonEmpty
        }.flatMap { case (id, restriction) =>
          restriction.note(ctxFor(byId(id)), ref)
            .map(PowerNoted(id, _, covers = false))
        }
      })
```

- [ ] **Step 5: Note what the look-ahead hides**

Still in `WalkerPowerGather.scala`:

1. Add `Restriction` to the `oathdigital.gameplay.powerresolver.{...}` import.
2. Add this case class inside the object, near the top:

```scala
  /** A restriction's violation with the power and context that produced it,
    * so the look-ahead can ask that restriction for its note. */
  private final case class Attributed(power: PowerId, ctx: PowerCtx,
      restriction: Restriction, violation: OathViolation)
```

3. Rename `restrictionViolations`' body into `attributed`, which returns both halves. Keep `restrictionViolations`' doc comment and put a one-line doc on `attributed`:

```scala
  def restrictionViolations(tree: Operation, powers: WalkerPowers,
      state: ReadyGame, activePlayer: PlayerId,
      answered: Vector[Answered] = Vector.empty): Vector[OathViolation] =
    val (rejected, emptied) = attributed(tree, powers, state, activePlayer,
      answered)
    rejected.map(_.violation) ++ emptied

  /** [[restrictionViolations]], keeping who rejected: the restrictions'
    * violations with their power, and the emptied decisions. */
  private def attributed(tree: Operation, powers: WalkerPowers,
      state: ReadyGame, activePlayer: PlayerId, answered: Vector[Answered])
      : (Vector[Attributed], Vector[OathViolation]) =
```

In that body, change only `rejected`, and make the last line `(rejected, emptied)`:

```scala
    val rejected = windows.flatMap:
      case (window, path, operation) =>
        val ctx = ctxFor(window, path, operation)
        ContributionCollector.gather(window, powers.powers, ctx).restrictions
          .flatMap { case (powerId, restriction) =>
            val at = ctx(byId(powerId))
            restriction.fn(at, tree).map(Attributed(powerId, at, restriction, _))
          }
```

4. Add after `probe`:

```scala
  /** The notes the look-ahead writes for the options `narrowed` no longer
    * offers: for each, the note of the restriction whose violation answering
    * it would add. The walker asks only at a decision reached fresh.
    */
  def lookAheadNotes(root: Operation, decide: Decide, narrowed: Decide,
      state: ReadyGame, activePlayer: PlayerId, powers: WalkerPowers,
      answered: Vector[Answered], procedure: Option[ProcedureRef])
      : Vector[PowerNoted] =
    val offered = optionRefs(narrowed).toSet
    val hidden = optionRefs(decide).filterNot(offered)
    if hidden.isEmpty then Vector.empty
    else
      val quiet = powers.copy(probing = false)
      val seen = state.updateCurrent(_.copy(walkerPending = None,
        walkerProcedure = procedure))
      def found(answers: Vector[Answered]): Vector[Attributed] =
        attributed(root, quiet, seen, activePlayer, answers)._1
      val baseline = found(answered).map(_.violation).toSet
      hidden.flatMap { ref =>
        val answer = decide.query match
          case _: DecisionQuery.ChooseMany =>
            DecisionAnswer.ChooseManyAnswer(Vector(ref))
          case _ => DecisionAnswer.ChooseOneAnswer(ref)
        found(answered :+ Answered(decide.decisionId, answer, decide.owner))
          .find(breach => !baseline(breach.violation))
          .flatMap(breach => breach.restriction.note(breach.ctx, ref)
            .map(PowerNoted(breach.power, _, covers = false)))
      }
```

- [ ] **Step 6: Journal the hidden options in the walker**

In `ProcedureWalker.scala`, replace the body of `walkFolded`:

```scala
    val (folded, order, hidden) = WalkerPowerGather.applyWindowNoted(window,
      operation, ctx.state, ctx.activePlayer, ctx.powers, path, children,
      ctx.procedure, ctx.answered, cursor.isDefined, noting = cursor.isEmpty)
    walkChildren(folded, ctx.copy(events = ctx.events ++ hidden), path, cursor,
      resume, hooks.withOrder(order))
```

In `runLeaf`, replace `case Some(narrowed) => runNarrowed(narrowed, ctx, path, cursor, resume, contributions, strict)` with:

```scala
          case Some(narrowed) =>
            // Reached fresh, so this is the only time it is asked this pass.
            val hidden = if cursor.nonEmpty then Vector.empty
              else WalkerPowerGather.lookAheadNotes(ctx.root, decide, narrowed,
                ctx.state, ctx.activePlayer, ctx.powers, ctx.answered,
                ctx.procedure)
            runNarrowed(narrowed, ctx.copy(events = ctx.events ++ hidden), path,
              cursor, resume, contributions, strict)
```

- [ ] **Step 7: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.PowerNoteHideSuite oathdigital.gameplay.OptionRestrictionSuite oathdigital.gameplay.RestrictionLookAheadSuite oathdigital.gameplay.ProcedureWalkerSuite oathdigital.gameplay.PowerNoteWalkerSuite"`
Expected: all pass. Then run `wc -l src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`. Expected: each 800 or fewer.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala \
  src/main/scala/oathdigital/gameplay/powerresolver/PhasePower.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala \
  src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala \
  src/test/scala/oathdigital/gameplay/PowerNoteHideSuite.scala
git commit -m "feat(walker): note the options a restriction hides at a fresh decision"
```

### Checkpoint A

Dispatch a review subagent on Sonnet or lower. Point it at Tasks 1 and 2 of this plan, the spec's section 1, and `git diff <base>..HEAD`. Ask it to check:

- A resumed command never journals a note twice. This covers the `Note` node, `walkFolded` and `runLeaf`.
- Replay stays note-blind.
- The `Repeat` guard change cannot loop.
- No `gameplay/powers` file imports `gameplay.walker`.
- The line bounds hold.

Fix what it finds before Task 3.

---

### Task 3: Wire format

**Files:**
- Modify: `src/main/scala/oathdigital/serialization/GameEventWire.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala`
- Test: `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`

**Interfaces:**
- Consumes: `PowerNoted`, `PowerNote`, `NoteArg`, `NoteUnit` (Task 1).
- Produces: the type string `"walker.power-noted"`. The payload is `{powerId, covers, note: {source, key, args}}`. Each argument and each source is an object tagged with `kind`.

- [ ] **Step 1: Write the failing tests**

Append to `GameEventWireSuite` (add `PowerNoted` to its `oathdigital.gameplay.walker.{...}` import):

```scala
  private def noteEvent(source: PowerSourceRef, args: NoteArg*): PowerNoted =
    PowerNoted(PowerId("test.power"), PowerNote(source, "used", args.toVector),
      covers = true)

  test("a power note round trips with every argument kind"):
    val event = noteEvent(PowerSourceRef.Card(DenizenId("93")),
      NoteArg.Player(PlayerId("p1")), NoteArg.Card(RelicId("r1")),
      NoteArg.Site(SiteId("s1")), NoteArg.Amount(3, NoteUnit.Favor),
      NoteArg.Amount(1, NoteUnit.Warband), NoteArg.Number(8),
      NoteArg.Bank(Suit.all.head),
      NoteArg.Dice(Vector(DefenseDieFace.TwoShields, DefenseDieFace.Blank)),
      NoteArg.Dice(Vector(AttackDieFace.OneSword)))
    val encoded = GameEventWire.encodeEvent("notes", catalog.ref, 0, event)
      .toOption.get
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))

  test("a power note's site and banner sources round trip"):
    Vector(noteEvent(PowerSourceRef.Site(SiteId("s1"))),
        noteEvent(PowerSourceRef.Banner(Banner.PeoplesFavor))).foreach { event =>
      val encoded = GameEventWire.encodeEvent("notes", catalog.ref, 0, event)
        .toOption.get
      assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    }

  test("an unknown note argument is refused"):
    val event = noteEvent(PowerSourceRef.Site(SiteId("s1")), NoteArg.Number(1))
    val encoded = ujson.read(GameEventWire.encodeEvent("notes", catalog.ref, 0,
      event).toOption.get)
    encoded("payload")("note")("args")(0)("kind") = "colour"
    assert(GameEventWire.decode(encoded).isLeft)
```

If `GameEventWire.decode` takes a `String` rather than a `ujson.Value`, pass `ujson.write(encoded)` in the last test and keep the rest as is. The first test in the suite shows which one it takes.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.serialization.GameEventWireSuite"`
Expected: an encode failure (`MatchError` or "no discriminator") for the new tests.

- [ ] **Step 3: Implement the codec**

In `GameEventWire.scala`, add after `val WalkerCompletedType = "walker.completed"`:

```scala
  val PowerNotedType = "walker.power-noted"
```

In `WalkerEventCodec.scala`, add `PowerNoted` to the walker import, then:

1. `walkerDiscriminator`: add `case _: PowerNoted => PowerNotedType`.
2. `walkerEncoder`: add

```scala
    case PowerNoted(power, note, covers) => ujson.Obj(
      "powerId" -> power.value, "covers" -> ujson.Bool(covers),
      "note" -> encodeNote(note))
```

3. `walkerDecode`'s `decoder`: add `case PowerNotedType => decodePowerNoted(payload, path)`.
4. In `encodeStepPayload`, replace the inline face match with a call to a new helper, and reuse that helper for notes:

```scala
          "faces" -> ujson.Arr.from(faces.map(encodeDieFace)))
```

```scala
  private def encodeDieFace(face: DieFace): ujson.Value = face match
    case face: DefenseDieFace => ujson.Str(encodeDefenseFace(face))
    case face: AttackDieFace => ujson.Str(encodeAttackFace(face))
    case other => throw new IllegalArgumentException(
      s"unsupported walker die face $other")
```

5. Add the note codec:

```scala
  private def encodeNote(note: PowerNote): ujson.Value = ujson.Obj(
    "source" -> encodeNoteSource(note.source), "key" -> note.key,
    "args" -> ujson.Arr.from(note.args.map(encodeNoteArg)))

  private def encodeNoteSource(source: PowerSourceRef): ujson.Value =
    source match
      case PowerSourceRef.Site(site) =>
        ujson.Obj("kind" -> "site", "siteId" -> site.value)
      case PowerSourceRef.Card(card) =>
        ujson.Obj("kind" -> "card", "card" -> encodeCardId(card))
      case PowerSourceRef.Banner(banner) =>
        ujson.Obj("kind" -> "banner", "bannerKey" -> banner.key)

  private def encodeNoteArg(arg: NoteArg): ujson.Value = arg match
    case NoteArg.Player(id) => ujson.Obj("kind" -> "player", "playerId" -> id.value)
    case NoteArg.Card(id) => ujson.Obj("kind" -> "card", "card" -> encodeCardId(id))
    case NoteArg.Site(id) => ujson.Obj("kind" -> "site", "siteId" -> id.value)
    case NoteArg.Amount(value, unit) => ujson.Obj("kind" -> "amount",
      "value" -> value, "unit" -> unit.key)
    case NoteArg.Number(value) => ujson.Obj("kind" -> "number", "value" -> value)
    case NoteArg.Bank(suit) => ujson.Obj("kind" -> "bank", "suit" -> suit.key)
    case NoteArg.Dice(faces) => ujson.Obj("kind" -> "dice",
      "faces" -> ujson.Arr.from(faces.map(encodeDieFace)))

  private def decodePowerNoted(value: ujson.Value,
      path: String): Either[WireError, OathEvent] = try for
    note <- decodeNote(value("note"), s"$path.note")
  yield PowerNoted(PowerId(value("powerId").str), note, value("covers").bool)
  catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid power note"))) }

  private def decodeNote(value: ujson.Value,
      path: String): Either[WireError, PowerNote] = for
    source <- decodeNoteSource(value("source"), s"$path.source")
    args <- traverse(value("args").arr.zipWithIndex.toVector):
      case (arg, index) => decodeNoteArg(arg, s"$path.args[$index]")
  yield PowerNote(source, value("key").str, args)

  private def decodeNoteSource(value: ujson.Value,
      path: String): Either[WireError, PowerSourceRef] = value("kind").str match
    case "site" => Right(PowerSourceRef.Site(SiteId(value("siteId").str)))
    case "card" => decodeCardId(value("card"), s"$path.card")
      .map(PowerSourceRef.Card.apply)
    case "banner" => decodeBanner(value("bannerKey").str, s"$path.bannerKey")
      .map(PowerSourceRef.Banner.apply)
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown note source '$other'"))

  private def decodeNoteArg(value: ujson.Value,
      path: String): Either[WireError, NoteArg] = value("kind").str match
    case "player" => Right(NoteArg.Player(PlayerId(value("playerId").str)))
    case "card" => decodeCardId(value("card"), s"$path.card")
      .map(NoteArg.Card.apply)
    case "site" => Right(NoteArg.Site(SiteId(value("siteId").str)))
    case "amount" => for
      amount <- decodeCount(value("value"), s"$path.value")
      unit <- NoteUnit.fromKey(value("unit").str).toRight(
        InvalidValue(s"$path.unit", "unknown note unit"))
    yield NoteArg.Amount(amount, unit)
    case "number" => decodeCount(value("value"), s"$path.value")
      .map(NoteArg.Number.apply)
    case "bank" => decodeSuit(value("suit").str, s"$path.suit")
      .map(NoteArg.Bank.apply)
    case "dice" => traverse(value("faces").arr.toVector)(face =>
      decodeDieFace(face.str, s"$path.faces")).map(NoteArg.Dice.apply)
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown note argument '$other'"))

  private def decodeCount(value: ujson.Value, path: String)
      : Either[WireError, Int] = decodeSignedInt(value, path).flatMap(count =>
    Either.cond(count >= 0, count, InvalidValue(path, "must not be negative")))
```

- [ ] **Step 4: Run the tests**

Run: `./sbtw "testOnly oathdigital.serialization.GameEventWireSuite"`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/serialization/GameEventWire.scala \
  src/main/scala/oathdigital/serialization/WalkerEventCodec.scala \
  src/test/scala/oathdigital/serialization/GameEventWireSuite.scala
git commit -m "feat(serialization): journal power notes as walker.power-noted"
```

---

### Task 4: Power lines in the Game Log

**Files:**
- Create: `src/main/scala/oathdigital/application/gamelog/NoteWordings.scala`
- Create: `src/main/scala/oathdigital/application/gamelog/PowerLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/LogJournal.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/LogWords.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/StartLines.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`

**Interfaces:**
- Consumes: `PowerNoted` (Task 1), `noteKeys` (Task 2).
- Produces:
  - `NoteWordings(templates)` with `template(power, key)`, `++`, `NoteWordings.of(power, keys)` and `NoteWordings.default(catalog)`
  - a third constructor parameter on `GameLogFormatter`, `wordings: NoteWordings`, with an auxiliary two-argument constructor that uses `NoteWordings.default(catalog)`
  - `LogScripts.inserted(steps, after, events*)`

- [ ] **Step 1: Add the journal-editing test helper**

In `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`, add `import oathdigital.engine.{RecordedEvent, ReplayStep}`, and add to `object LogScripts`:

```scala
  /** `steps` with `events` journaled right after position `after`. Each
    * changes no state, and every later sequence moves up to make room. */
  def inserted(steps: Vector[ReplayStep[OathState, OathEvent]], after: Int,
      events: OathEvent*): Vector[ReplayStep[OathState, OathEvent]] =
    val state = steps(after).after
    val at = steps(after).event.index + 1
    val added = events.toVector.zipWithIndex.map { case (event, offset) =>
      ReplayStep(RecordedEvent(at + offset, event), state, state) }
    steps.take(after + 1) ++ added ++ steps.drop(after + 1).map(step =>
      step.copy(event = step.event.copy(index = step.event.index + events.size)))
```

- [ ] **Step 2: Write the failing test**

Create `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.application.ParkedServiceFixture
import oathdigital.engine.ReplayStep
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{PowerNoted, WalkerCompleted, WalkerParked,
  WalkerStepRecorded}
import oathdigital.model._
import LogScripts._

/** Lines powers write about themselves (power log lines design, section 2).
  * The notes are journaled into real scripts, as Silver Tongue's, whose
  * wording this suite supplies. */
class GameLogPowerLinesSuite extends munit.FunSuite:
  private type Steps = Vector[ReplayStep[OathState, OathEvent]]
  private val power = SilverTongue.id
  private val card = PowerSourceRef.Card(ParkedServiceFixture.silverTongueCard)
  private val said = NoteKey("said", Vector(NotePart.Arg(0),
    NotePart.Text(" said "), NotePart.Arg(1), NotePart.Text(".")))
  private val took = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1), NotePart.Text(".")))
  private val counted = NoteKey("counted", Vector(NotePart.Arg(0),
    NotePart.Text(" lost "), NotePart.Arg(1), NotePart.Text(" "),
    NotePart.Plural(1, "warband", "warbands"), NotePart.Text(", then "),
    NotePart.Arg(2), NotePart.Text(".")))
  private val noting = new GameLogFormatter(catalog, presentation,
    NoteWordings.default(catalog) ++
      NoteWordings.of(power, Vector(said, took, counted)))

  private def entries(steps: Steps, viewer: Option[PlayerId] = None) =
    noting.format(steps, viewer).filter(_.depth == 1)
  private def lines(steps: Steps, viewer: Option[PlayerId] = None) =
    texts(entries(steps, viewer))
  private def ours(steps: Steps, viewer: Option[PlayerId] = None) =
    entries(steps, viewer).filter(entry => text(entry).startsWith("Silver Tongue: "))

  /** Silver Tongue's take: the step that moves favor out of a bank. */
  private def take(steps: Steps): Int = steps.indexWhere(_.event.event match
    case step: WalkerStepRecorded => step.ops.exists {
      case Move(Piece.Favor(_), PositionedLocation(Location.FavorBank(_), _),
          _, _) => true
      case _ => false
    }
    case _ => false)

  private def saying(actor: PlayerId, arg: NoteArg): PowerNoted =
    PowerNoted(power, said(card, NoteArg.Player(actor), arg), covers = false)

  private def closes(event: OathEvent): Boolean = event match
    case _: WalkerParked | _: WalkerCompleted => true
    case _ => false

  private def assertPrefixStable(steps: Steps): Unit =
    val whole = noting.format(steps, None)
    steps.indices.filter(index => closes(steps(index).event.event))
      .map(_ + 1).foreach { end =>
        assertEquals(noting.format(steps.take(end), None),
          whole.takeWhile(_.sequence < end), s"at $end")
      }

  test("a note reads as its source, a colon and its sentence"):
    val script = usePower
    val steps = script.history.steps
    val noted = inserted(steps, take(steps),
      saying(script.actor, NoteArg.Amount(2, NoteUnit.Favor)))
    val entry = ours(noted).head
    assertEquals(text(entry), s"Silver Tongue: ${name(script.actor)} said 2 favor.")
    assertEquals(entry.kind, LogKind.Trigger)

  test("a phase power's used note replaces Used {card} as the action line"):
    val script = usePower
    val steps = script.history.steps
    assert(lines(steps).contains("Used Silver Tongue"), lines(steps))
    val noted = inserted(steps, take(steps), PowerNoted(power, took(card,
      NoteArg.Player(script.actor), NoteArg.Amount(1, NoteUnit.Favor)),
      covers = false))
    assert(!lines(noted).exists(_.startsWith("Used ")), lines(noted))
    val entry = ours(noted).head
    assertEquals(text(entry), s"Silver Tongue: ${name(script.actor)} took 1 favor.")
    assertEquals(entry.kind, LogKind.Action)

  test("a covering note drops the generic lines of the step before it, but no decision line"):
    val script = usePower
    val steps = script.history.steps
    val before = lines(steps)
    assert(before.exists(_.startsWith("Gained 1 favor from the ")), before)
    val chose = before.filter(_.startsWith("Chose "))
    assert(chose.nonEmpty, before)
    val noted = inserted(steps, take(steps),
      saying(script.actor, NoteArg.Amount(1, NoteUnit.Favor)).copy(covers = true))
    val after = lines(noted)
    assert(!after.exists(_.startsWith("Gained 1 favor from the ")), after)
    assertEquals(after.filter(_.startsWith("Chose ")), chose)
    assertPrefixStable(noted)

  test("a note identical to an earlier one in the action posts nothing"):
    val script = usePower
    val steps = script.history.steps
    val twice = saying(script.actor, NoteArg.Amount(2, NoteUnit.Favor))
    val noted = inserted(steps, take(steps), twice, twice,
      saying(script.actor, NoteArg.Amount(3, NoteUnit.Favor)))
    assertEquals(ours(noted).map(text), Vector(
      s"Silver Tongue: ${name(script.actor)} said 2 favor.",
      s"Silver Tongue: ${name(script.actor)} said 3 favor."))

  test("plurals follow their amount"):
    val script = usePower
    val steps = script.history.steps
    def counting(count: Int) = PowerNoted(power, counted(card,
      NoteArg.Player(script.actor), NoteArg.Number(count),
      NoteArg.Amount(count, NoteUnit.Warband)), covers = false)
    val noted = inserted(steps, take(steps), counting(1), counting(2))
    assertEquals(ours(noted).map(text), Vector(
      s"Silver Tongue: ${name(script.actor)} lost 1 warband, then 1 warband.",
      s"Silver Tongue: ${name(script.actor)} lost 2 warbands, then 2 warbands."))

  test("a note no power words posts nothing"):
    val script = usePower
    val steps = script.history.steps
    val unknown = PowerNoted(PowerId("test.unknown"),
      PowerNote(card, "said", Vector.empty), covers = false)
    assertEquals(lines(inserted(steps, take(steps), unknown)), lines(steps))

  test("a card its viewer may not identify is not named to them"):
    val script = usePower
    val steps = script.history.steps
    val last = steps.last.after match
      case OathState.Ready(ready) => ready
      case other => fail(s"expected a ready game, got $other")
    val (owner, hidden) = last.game.current.players
      .filter(_.player != script.actor).flatMap(held => held.advisers.collect {
        case DenizenState(id, Orientation.FaceDown, _) => held.player -> (id: CardId)
        case VisionState(id, Orientation.FaceDown) => held.player -> (id: CardId)
      }).head
    val noted = inserted(steps, take(steps),
      saying(script.actor, NoteArg.Card(hidden)))
    def named(viewer: PlayerId) = ours(noted, Some(viewer)).head.spans.collect {
      case shown: LogSpan.Card => shown.id }
    assert(!named(script.actor).contains(hidden.value), named(script.actor))
    assert(named(owner).contains(hidden.value), named(owner))

  test("a note waits for its action's start line"):
    val script = raid
    val steps = script.history.steps
    val opened = steps.indexWhere(_.event.event match
      case parked: WalkerParked => parked.procedure == ActionRef.Campaign
      case _ => false)
    val noted = inserted(steps, opened,
      saying(script.actor, NoteArg.Amount(2, NoteUnit.Favor)))
    val all = lines(noted)
    val start = all.indexWhere(_.startsWith("Started Campaign"))
    assert(start >= 0, all)
    assertEquals(all.indexWhere(_.startsWith("Silver Tongue: ")), start + 1, all)
    assertPrefixStable(noted)
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogPowerLinesSuite"`
Expected: compile failure, because `NoteWordings` and the three-argument `GameLogFormatter` do not exist.

- [ ] **Step 4: Wordings**

Create `src/main/scala/oathdigital/application/gamelog/NoteWordings.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.model.{NoteKey, NotePart, PowerId}

/** Every power's note templates, by power and key (power log lines design,
  * "Wording"). A note whose power or key is missing here renders nothing. */
private[application] final case class NoteWordings(
    templates: Map[(PowerId, String), Vector[NotePart]]):
  def template(power: PowerId, key: String): Option[Vector[NotePart]] =
    templates.get((power, key))
  /** `other`'s templates win where both have one. */
  def ++(other: NoteWordings): NoteWordings =
    NoteWordings(templates ++ other.templates)

private[application] object NoteWordings:
  def of(power: PowerId, keys: Vector[NoteKey]): NoteWordings =
    NoteWordings(keys.map(key => (power, key.name) -> key.template).toMap)

  /** The walker powers' and the phase powers' keys. */
  def default(catalog: ExecutableCatalog): NoteWordings =
    (WalkerPowerCatalog.default(catalog).powers.map(power =>
      of(power.id, power.noteKeys)) ++
      PhasePowerCatalog.default(catalog).powers.map(power =>
        of(power.id, power.noteKeys)))
      .foldLeft(NoteWordings(Map.empty))(_ ++ _)
```

- [ ] **Step 5: The source chip, shared with "Used {card}"**

In `LogWords.scala`, add after `banner`:

```scala
  /** The card, banner or site a power belongs to. */
  def source(ref: PowerSourceRef, state: ReadyGame,
      viewer: Option[PlayerId]): Vector[LogSpan] = ref match
    case PowerSourceRef.Card(id) => one(card(id, state, state, viewer))
    case PowerSourceRef.Banner(held) => Vector(banner(held))
    case PowerSourceRef.Site(at) => Vector(site(at))
```

Add `PowerSourceRef` to the file's model import if it imports names one by one. In `ActionLines.powerSource`, replace the `used` value's inner match with the helper:

```scala
    val used = through.flatMap(journal.ops).collectFirst {
      case OpStep(RecordPowerUse(PowerUseRef(_, source, _)), _, _) => source
    }.flatMap(source => ready.map(state => words.source(source, state, viewer)))
```

- [ ] **Step 6: Journal queries and the start test**

In `LogJournal.scala`, add `PowerNoted` to the walker import and add:

```scala
  /** Whether `power` journals its own `used` note in `run`, up to the end of
    * `at`'s segment. That line then replaces "Used {card}". */
  def notedUse(run: Run, power: PowerId, at: Int): Boolean =
    (run.first to segmentEnd(at)).exists(index => event(index) match
      case PowerNoted(`power`, note, _) => note.key == NoteKey.Used
      case _ => false)

  /** Whether a covering note restates the step at `index`: one follows it
    * in its segment before the next step. */
  def covered(index: Int): Boolean = event(index) match
    case _: WalkerStepRecorded =>
      (index + 1 to segmentEnd(index)).iterator.map(event)
        .takeWhile(!_.isInstanceOf[WalkerStepRecorded])
        .exists {
          case noted: PowerNoted => noted.covers
          case _ => false
        }
    case _ => false
```

In `StartLines.scala`, add:

```scala
  /** Whether `procedure` opens with a start line, so its power lines wait
    * for it. */
  def opens(procedure: ProcedureRef): Boolean =
    procedure == ActionRef.Campaign || title(procedure).nonEmpty
```

In `DetailLines.lines`, keep the decision line and drop only a covered step's other lines:

```scala
    decision(journal, run, at, viewer) ++ (if journal.covered(at) then
      Vector.empty else roll(journal, at) ++ deltas(journal, run, at, viewer))
```

In `ActionLines.usedPower`, add `|| journal.notedUse(run, power, at)` to the condition that returns `Vector.empty`:

```scala
    if postedEarlier || !(effect(journal, run, at) || completing) ||
        journal.notedUse(run, power, at) then Vector.empty
```

- [ ] **Step 7: Render the lines**

Create `src/main/scala/oathdigital/application/gamelog/PowerLines.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.gameplay.walker.PowerNoted
import oathdigital.model._
import LogSpan.Text

/** The lines powers write about themselves (power log lines design,
  * section 2): "{source}: {sentence}", from the template the note's power
  * declares.
  *
  * An action whose start line has not posted yet holds its notes, and they
  * post right after it. A note identical to an earlier one in the same action
  * posts nothing. Both rules compare journal facts, never rendered text, so
  * every viewer receives the same entries.
  */
private[gamelog] final class PowerLines(words: LogWords,
    wordings: NoteWordings):

  /** The notes of `run` before `at`, held for the start line `at` posts. */
  def held(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    (run.first until at).toVector.flatMap(line(journal, run, _, viewer))

  /** `at`'s own note, unless `run` still waits for its start line. */
  def own(journal: LogJournal, run: Run, at: Int, opens: Boolean,
      viewer: Option[PlayerId]): Vector[Posted] =
    if opens && run.started.isEmpty then Vector.empty
    else line(journal, run, at, viewer).toVector

  private def line(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Option[Posted] = journal.event(at) match
    case PowerNoted(power, note, _) if !repeated(journal, run, at, power, note) =>
      for
        template <- wordings.template(power, note.key)
        state <- journal.readyAfter(at)
      yield Posted.line(kind(run, power, note),
        words.source(note.source, state, viewer) ++
          (Text(": ") +: sentence(template, note.args, state, viewer)))
    case _ => None

  /** A phase power's own `used` note is its action's line. */
  private def kind(run: Run, power: PowerId, note: PowerNote): LogKind =
    if note.key == NoteKey.Used && run.procedure == ActionRef.UsePower(power)
    then LogKind.Action
    else LogKind.Trigger

  private def repeated(journal: LogJournal, run: Run, at: Int, power: PowerId,
      note: PowerNote): Boolean =
    (run.first until at).exists(index => journal.event(index) match
      case PowerNoted(`power`, `note`, _) => true
      case _ => false)

  private def sentence(template: Vector[NotePart], args: Vector[NoteArg],
      state: ReadyGame, viewer: Option[PlayerId]): Vector[LogSpan] =
    template.flatMap:
      case NotePart.Text(written) => Vector(Text(written))
      case NotePart.Arg(index) =>
        args.lift(index).toVector.flatMap(argument(_, state, viewer))
      case NotePart.Plural(index, one, many) => Vector(Text(args.lift(index) match
        case Some(NoteArg.Amount(1, _)) | Some(NoteArg.Number(1)) => one
        case _ => many))

  private def argument(arg: NoteArg, state: ReadyGame,
      viewer: Option[PlayerId]): Vector[LogSpan] = arg match
    case NoteArg.Player(id) => Vector(words.player(id))
    case NoteArg.Card(id) => words.one(words.card(id, state, state, viewer))
    case NoteArg.Site(id) => Vector(words.site(id))
    case NoteArg.Amount(value, unit) =>
      Vector(LogSpan.Amount(value, unit.word(value)))
    case NoteArg.Number(value) => Vector(Text(value.toString))
    case NoteArg.Bank(suit) => Vector(Text(s"the $suit bank"))
    case NoteArg.Dice(faces) => LogWords.dice(faces).toVector
```

- [ ] **Step 8: Wire the formatter**

In `GameLogFormatter.scala`:

1. Add `PowerNoted` to the walker import.
2. Change the class header and add the auxiliary constructor:

```scala
private[application] final class GameLogFormatter(catalog: ExecutableCatalog,
    presentation: GamePresentationProjector, wordings: NoteWordings):
  def this(catalog: ExecutableCatalog, presentation: GamePresentationProjector) =
    this(catalog, presentation, NoteWordings.default(catalog))
```

3. Add `private val notes = new PowerLines(words, wordings)` after `events`.
4. In `eventLines`, route notes to the walker: `case _: WalkerStepRecorded | _: WalkerParked | _: WalkerCompleted | _: PowerNoted =>`.
5. In `walker`, replace `posted`:

```scala
        val posted = start.toVector ++
          (if start.isEmpty then Vector.empty
            else notes.held(journal, begun, at, viewer)) ++
          starts.continued(journal, begun, at).toVector ++
          actions.lines(journal, begun, at, viewer) ++
          details.lines(journal, begun, at, viewer) ++
          notes.own(journal, begun, at, starts.opens(begun.procedure), viewer) ++
          turnHeadlines(journal, at)
```

- [ ] **Step 9: Run the tests**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: all pass, the existing suites included.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/NoteWordings.scala \
  src/main/scala/oathdigital/application/gamelog/PowerLines.scala \
  src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala \
  src/main/scala/oathdigital/application/gamelog/LogJournal.scala \
  src/main/scala/oathdigital/application/gamelog/LogWords.scala \
  src/main/scala/oathdigital/application/gamelog/ActionLines.scala \
  src/main/scala/oathdigital/application/gamelog/DetailLines.scala \
  src/main/scala/oathdigital/application/gamelog/StartLines.scala \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala
git commit -m "feat(log): render power notes as {card}: {sentence} lines"
```

### Checkpoint B

Dispatch a review subagent on Sonnet or lower. Point it at Tasks 3 and 4, the spec's sections 2 and 4, and the diff since Checkpoint A. Ask it to check:

- Prefix stability. This covers held notes, covering and the `used` look-ahead.
- Every viewer receives the same entries with the same keys, including merged and held notes.
- Card arguments and sources pass `LogWords.card`.
- The codec rejects malformed notes.

Fix what it finds before Task 5.

---

### Task 5: Vow of Peace and Gambling Hall

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/VowOfPeaceContribution.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/GamblingHall.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/campaign/VowOfPeaceSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/action/GamblingHallSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/PowerNoteCatalogSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`

**Interfaces:**
- Consumes: everything above.
- Produces: `VowOfPeaceContribution.noSacrifice: NoteKey`, `GamblingHall.rolled: NoteKey` (key `used`), `GamblingHall.gained: NoteKey`, and `LogScripts.gamblingHall`.

- [ ] **Step 1: Write the failing card tests**

Append to `VowOfPeaceSuite` (add `import oathdigital.gameplay.walker.PowerNoted`):

```scala
  private def notes(run: Run): Vector[PowerNoted] =
    run.finish.events.collect { case noted: PowerNoted => noted }

  test("the removed sacrifice decision leaves a note naming the defender"):
    val b = defendedBy(againstPlayer(board()), Orientation.FaceUp)
    assertEquals(notes(commit(rules(losing), b, 2)), Vector(PowerNoted(
      VowOfPeaceContribution.id, VowOfPeaceContribution.noSacrifice(
        PowerSourceRef.Card(DenizenId(vow)), NoteArg.Player(b.other)),
      covers = false)))

  test("an attacker against anyone else writes no Vow of Peace note"):
    assertEquals(notes(commit(rules(losing), againstPlayer(board()), 2)),
      Vector.empty)
```

Append to `GamblingHallSuite` (add `import oathdigital.gameplay.walker.PowerNoted`):

```scala
  private val card = PowerSourceRef.Card(hall)
  private def notes(events: Vector[OathEvent]): Vector[PowerNoted] =
    events.collect { case noted: PowerNoted => noted }

  test("it notes its roll, then what it gained, each covering the generic line"):
    val rules0 = rules(total4)
    val parked = use(rules0, staged(), GamblingHall.id, source).toOption.get
    val done = answer(rules0, parked.state, GamblingHall.decisionId,
      bank(Suit.Beast)).toOption.get
    assertEquals(notes(parked.events ++ done.events), Vector(
      PowerNoted(GamblingHall.id, GamblingHall.rolled(card, NoteArg.Player(actor),
        NoteArg.Dice(Vector(DefenseDieFace.OneShield, DefenseDieFace.OneShield,
          DefenseDieFace.TwoShields, DefenseDieFace.Blank)),
        NoteArg.Number(4)), covers = true),
      PowerNoted(GamblingHall.id, GamblingHall.gained(card, NoteArg.Player(actor),
        NoteArg.Amount(4, NoteUnit.Favor), NoteArg.Bank(Suit.Beast)),
        covers = true)))

  test("a gain the bank caps is noted as what the bank paid"):
    val doubled = defenseDice(DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields, DefenseDieFace.Doubler, DefenseDieFace.Blank)
    val ready0 = staged()
    val parked = use(rules(doubled), ready0, GamblingHall.id, source).toOption.get
    val done = answer(rules(doubled), parked.state, GamblingHall.decisionId,
      bank(Suit.Nomad)).toOption.get
    assertEquals(notes(done.events).map(_.note.args), Vector(Vector(
      NoteArg.Player(actor),
      NoteArg.Amount(ready0.banks.favor(Suit.Nomad), NoteUnit.Favor),
      NoteArg.Bank(Suit.Nomad))))

  test("a total of zero notes only the roll"):
    val rules0 = rules(defenseDice(DefenseDieFace.Blank, DefenseDieFace.Blank,
      DefenseDieFace.Blank, DefenseDieFace.Blank))
    val done = use(rules0, staged(), GamblingHall.id, source).toOption.get
    assertEquals(notes(done.events).map(_.note.key), Vector(NoteKey.Used))

  test("an empty bank gives nothing, so no gain is noted"):
    val drained = staged().copy(banks = staged().banks.copy(
      favor = staged().banks.favor.updated(Suit.Discord, 0)))
    val parked = use(rules(total4), drained, GamblingHall.id, source).toOption.get
    val done = answer(rules(total4), parked.state, GamblingHall.decisionId,
      bank(Suit.Discord)).toOption.get
    assertEquals(notes(done.events), Vector.empty)
```

In the capped-gain test, `ready0.banks.favor(Suit.Nomad)` must be below 6 for the cap to show. The existing Doubler test already relies on this, by expecting the whole bank.

Create `src/test/scala/oathdigital/gameplay/powers/PowerNoteCatalogSuite.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.gameplay.powers.action.GamblingHall
import oathdigital.gameplay.powers.campaign.VowOfPeaceContribution
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Every power's declared notes (power log lines design, "Wording"). */
class PowerNoteCatalogSuite extends munit.FunSuite:
  private val declared: Vector[(PowerId, Vector[NoteKey])] =
    WalkerPowerCatalog.default(catalog).powers.map(power =>
      power.id -> power.noteKeys) ++
    PhasePowerCatalog.default(catalog).powers.map(power =>
      power.id -> power.noteKeys)

  test("a power names each of its notes once"):
    declared.foreach { case (id, keys) =>
      assertEquals(keys.map(_.name).distinct, keys.map(_.name), id.value) }

  test("every sentence starts with an argument or a capital letter"):
    declared.foreach { case (id, keys) => keys.foreach { key =>
      val where = s"${id.value}.${key.name}"
      key.template.headOption match
        case Some(NotePart.Text(words)) =>
          assert(words.headOption.exists(_.isUpper), where)
        case Some(_) => ()
        case None => fail(s"$where has no sentence")
    } }

  test("Gambling Hall and Vow of Peace declare their notes"):
    val keys = declared.toMap.view.mapValues(_.map(_.name)).toMap
    assertEquals(keys.get(GamblingHall.id), Some(Vector(NoteKey.Used, "gained")))
    assertEquals(keys.get(VowOfPeaceContribution.id), Some(Vector("no-sacrifice")))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.VowOfPeaceSuite oathdigital.gameplay.powers.action.GamblingHallSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite"`
Expected: compile failure, because `noSacrifice`, `rolled` and `gained` do not exist.

- [ ] **Step 3: Vow of Peace**

In `VowOfPeaceContribution.scala`:

1. Add after `def source`:

```scala
  /** Where its second sentence removed the attacker's sacrifice decision. */
  val noSacrifice: NoteKey = NoteKey("no-sacrifice", Vector(
    NotePart.Text("The attacker cannot sacrifice against "), NotePart.Arg(0),
    NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = Vector(noSacrifice)
```

2. Replace the `CampaignSacrificeSelection` entry:

```scala
    PowerWindow.CampaignSacrificeSelection ->
      Vector(Transform((ctx, children) => protectedDefender(ctx) match
        case Some(defender) if children.nonEmpty => Vector(Note(id, _ =>
          Some(noSacrifice(PowerSourceRef.Card(cardId),
            NoteArg.Player(defender))))))
        case Some(_) => Vector.empty
        case None => children)))
```

3. Replace `defenderHolds` with:

```scala
  /** This Campaign's defender, when that defender holds the Vow faceup. */
  private def protectedDefender(ctx: PowerCtx): Option[PlayerId] =
    CampaignSetup.setup(ctx.state, ctx.activePlayer,
      PendingTree(ctx.nodePath, ctx.answered)).flatMap(_.defender match
        case CampaignDefender.Player(player) if holds(ctx.state, player) =>
          Some(player)
        case _ => None)
```

4. Add one sentence to the class doc comment's second paragraph: "The removed decision leaves a note naming the defender, so the Game Log says why nothing was asked."

- [ ] **Step 4: Gambling Hall**

In `GamblingHall.scala`:

1. Add the keys after `decisionId`:

```scala
  /** Its own line: the roll and its total, in place of "Used Gambling Hall"
    * and the generic "Rolled" line. */
  val rolled: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" rolled "), NotePart.Arg(1), NotePart.Text(", Total: "),
    NotePart.Arg(2)))
  /** What the total took from the chosen bank, which a thin bank caps. */
  val gained: NoteKey = NoteKey("gained", Vector(NotePart.Arg(0),
    NotePart.Text(" gained "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = Vector(rolled, gained)
```

2. Replace `build`'s sequence:

```scala
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    ModifyDicePool(pool, Dice),
    Roll(pool, DiceSpec(DiceKind.Defense), RollMode.Automatic),
    Note(id, rollNote(_, player, source), covers = true),
    Branch((state, _) => {
      val total = RollResults.score(state, pool)
      if total > 0 then Vector(choice(player, total)) else Vector.empty
    }),
    BuildOps((state, pending) => take(state, player, pending)),
    Note(id, gainNote(_, player, source), covers = true))))
```

3. Add the builders:

```scala
  private def rollNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    for
      card <- PowerSourceRef.of(source)
      outcome <- states.now.game.current.rollOutcomes.get(pool)
    yield rolled(card, NoteArg.Player(player), NoteArg.Dice(outcome.faces),
      NoteArg.Number(RollResults.score(states.now, pool)))

  /** The favor the take actually moved, read from the step before it. */
  private def gainNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    for
      card <- PowerSourceRef.of(source)
      step <- states.previous
      suit <- states.answered.collectFirst {
        case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(
            DecisionOptionRef.FavorBank(suit)), _) => suit
      }
      amount = favor(step._2, player) - favor(step._1, player)
      if amount > 0
    yield gained(card, NoteArg.Player(player),
      NoteArg.Amount(amount, NoteUnit.Favor), NoteArg.Bank(suit))

  private def favor(ready: ReadyGame, player: PlayerId): Int =
    ready.game.current.players.find(_.player == player).fold(0)(_.board.favor)
```

4. Add one sentence to the class doc comment: "Two notes restate the roll and the gain for the Game Log, each covering the generic line of the step before it."

- [ ] **Step 5: Run the card tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.VowOfPeaceSuite oathdigital.gameplay.powers.action.GamblingHallSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.gameplay.CampaignPowersSuite"`
Expected: all pass. The existing `replayed(...)` and `wireRoundTrips(...)` assertions now carry `PowerNoted` events through replay and the codec.

- [ ] **Step 6: A Gambling Hall log script**

In `LogScripts.scala`, add these imports: `oathdigital.gameplay.powers.action.GamblingHall` and `oathdigital.model.DecisionAnswer.ChooseOneAnswer` (the latter is already imported). Add:

```scala
  /** Gambling Hall at the actor's site, used in Act with a second favor
    * arranged. The steady dice total 8, and the richest bank is chosen so
    * the gain is never empty. */
  def gamblingHall(using munit.Location): Script =
    val card = DenizenId("93")
    val (chronicle, orders) = ParkedServiceFixture.withWorldDeckTop(
      FirstGameSetupFixture.chronicle, FirstGameSetupFixture.orders, Vector(card))
    val (service, _, driver) = journaled("gambling-hall")
    val woken = Situation.wake(driver, chronicle, orders)
    val actor = active(woken)
    val richest = Suit.all.maxBy(suit => woken.ready.banks.favor(suit))
    val spare = Suit.all.find(_ != richest).get
    woken.withAnswers {
      case park if park.decisionId == GamblingHall.decisionId =>
        ChooseOneAnswer(DecisionOptionRef.FavorBank(richest))
    }.after(Step.Arrange(Vector(
        ParkedServiceFixture.topOfWorldDeck(card, Location.Site(pawn(woken, actor))),
        Move(Piece.Favor(1), PositionedLocation(Location.FavorBank(spare)),
          PositionedLocation(Location.PlayArea(actor))))),
      GameCommand.EndWake(actor),
      GameCommand.UsePower(actor, GamblingHall.id, DecisionOptionRef.Denizen(card)))
    Script("gambling-hall", service, actor)
```

Add `"gambling-hall" -> (() => gamblingHall)` to `named`, so `GameLogPropertiesSuite` holds it to prefix stability, same keys and no leaks.

If `spare` has no favor on this board, the `Move` is refused. Pick instead the first suit other than `richest` whose bank holds favor: `Suit.all.find(suit => suit != richest && woken.ready.banks.favor(suit) > 0).get`.

- [ ] **Step 7: The log test**

Append to `GameLogPowerLinesSuite`:

```scala
  test("Gambling Hall writes its roll and its gain in place of the generic lines"):
    val script = gamblingHall
    val all = texts(format(script, None).filter(_.depth == 1))
    val actor = name(script.actor)
    val rolled = all.filter(_.startsWith(s"Gambling Hall: $actor rolled "))
    assertEquals(rolled.size, 1, all)
    assert(rolled.head.endsWith(", Total: 8"), rolled.head)
    val gained = all.filter(_.startsWith(s"Gambling Hall: $actor gained "))
    assertEquals(gained.size, 1, all)
    assert(gained.head.endsWith(" bank."), gained.head)
    assert(!all.exists(line => line.startsWith("Used ") ||
      line.startsWith("Rolled ") || line.startsWith("Gained ")), all)
    val entry = format(script, None).find(entry =>
      text(entry).startsWith(s"Gambling Hall: $actor rolled ")).get
    assertEquals(entry.kind, LogKind.Action)
```

- [ ] **Step 8: Run the tests**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: all pass, `GameLogPropertiesSuite` over the new script included.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/VowOfPeaceContribution.scala \
  src/main/scala/oathdigital/gameplay/powers/action/GamblingHall.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/VowOfPeaceSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/action/GamblingHallSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/PowerNoteCatalogSuite.scala \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala
git commit -m "feat(powers): Vow of Peace and Gambling Hall write their own log lines"
```

---

### Task 6: Record the slice and run the gates

**Files:**
- Modify: `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`

- [ ] **Step 1: Amend the spec**

Add a `**Slice 1 (2026-09-26)**` line under Status. Then add a section before "## Slices":

```markdown
## Settled in slice 1

The first slice settled these:

- A note carries its source: `PowerNote(source: PowerSourceRef, key, args)`.
  `PowerSourceRef` is what the log already names sources by. It is not the
  `RuleSourceRef` this document first named: Vow of Peace and other powers
  have only a game-rule `RuleSourceRef`.
- `Note(power, build, covers)` is a plain `Operation`, not a
  `PrimitiveOperation`. `build` may return nothing, and then nothing is
  journaled, which is how an amount of zero writes no line.
- `NoteStates` also carries the action's answers, so a note can name what
  the player chose.
- `PowerNoted(power, note, covers)` carries `covers` for the formatter.
- A template lives on its `NoteKey`. A power declares `noteKeys`, on both
  `ContributingPower` and `PhasePower`, and builds its notes through them, so
  a key never lacks a template. A missing template can happen only for an
  old journal.
- `NoteArg.Number` is a bare number, for "Total: 8".
- The hide hook covers choose-one and choose-many options. A choose-amount
  decision the look-ahead narrows writes no note.
- Merging compares the journaled notes, never the rendered text, so every
  viewer receives the same entries.
- Held notes post right after the start line, at the event that posts it.
- A template starts with an argument or a capital letter, which a catalog
  test checks. The formatter never changes case.
- A phase power puts its `used` note in the same command as its first
  effect, because "Used {card}" is decided at that step and reads ahead only
  to the end of its segment.
- The test that every phase power declares `used` arrives with slice 2.
```

Also change section 1's `Note(power: PowerId, source: RuleSourceRef, build: NoteStates => PowerNote, covers: Boolean = false)` to `Note(power: PowerId, build: NoteStates => Option[PowerNote], covers: Boolean = false)`. Change its "`source` names the card…" bullet to say that the note's `source` names the card.

- [ ] **Step 2: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: all pass. The server count is your baseline plus the new tests.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both clean.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-09-26-power-log-lines-design.md
git commit -m "docs: record what power log lines slice 1 settled"
```

### Checkpoint C

Dispatch a final review subagent on Sonnet or lower over the whole branch diff, the spec and this plan. Ask it to confirm each "Tests" bullet of the spec's slice-1 scope has a test. Ask it also to confirm the Global Constraints hold. Fix what it finds, then run the gates once more.
