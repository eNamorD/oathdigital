# Power Log Lines, Slice 2 (Phase Powers) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every implemented phase power its own Game Log line, in place of the generic "Used {card}", using the slice-1 mechanism. Slice 1 already did Gambling Hall.

**Architecture:**

- Each phase power declares its `NoteKey`s and puts `Note` nodes in the tree its `build` returns, right after the step each note restates. No walker change.
- A new `NoteSupport` object in `gameplay/powers` holds the reads every note needs, such as "the favor this player gained in the step before". It also holds the sentences several powers share. The helper objects `RollResults`, `RelicDraws` and `PawnMoves` gain the note each of their families shares.
- The formatter learns two things:
  - A phase power may have several `used` lines, named `used` or `used.{variant}`. An example is Wolves' "{Blue} had no warband to kill.".
  - A note judges its cards at three states: the action's start, the step the note restates, and the note itself. This keeps a buried or returned card named to a viewer who saw it.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change: the client already renders the `action` and `trigger` kinds. Impeccable is not needed.

**Spec:** `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

- Before starting, read the Rulings, section 2's "Phase powers" table and "Settled in slice 1".
- The previous plan, `docs/superpowers/plans/2026-09-26-power-log-lines-slice1.md`, shows how the mechanism was built.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build. Each file edited below lists the imports it needs.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`). This slice touches no file near the limit, and does not touch `ProcedureWalker.scala`.
- Import rules:
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - `application` never imports `serialization`.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree:
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first.
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- Line wording follows the spec's phase power table, except where "Decisions this plan makes" below changes it. A template starts with an argument or a capital letter. A catalog test already checks this.
- A note reads amounts from `NoteStates.previous`, the step before it, never from the printed number.
- `FaeMerchant`, `CrystalVial`, `BrassHorse` and `HornedMask` open their class body with `import Companion._`, and each companion defines `id`. In those four classes a bare `id` is ambiguous between the inherited member and the import, so their notes write `this.id`. `given` is a Scala 3 keyword, so Magic Carpet's key value is `givenAway`.
- Baselines: record the server test count from your first full `./sbtw test` run in the worktree, and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

### Decisions this plan makes that the spec left open

These were settled while planning (2026-09-26). Task 8 records each one in the spec.

1. **Variants of the `used` line.**
   - Several rows of the table are a power's `used` line for another outcome, such as "Wolves, no warband" or "Dowsing Sticks, empty deck". A key name must be unique per power, so a variant is named `used.{variant}`: `used.none`, `used.empty`, `used.away`.
   - `NoteKey.isUse(name)` is true for `used` and for every `used.{variant}`. Both formatter checks read it: "does a `used` note replace Used {card}?" and "is this line the action line?".
   - Every phase power must still declare a key named exactly `used`.
2. **Brass Horse writes two lines.** Its reveal is its first effect and runs in the command that starts it. The destination question can park, so the placement can land in a later command. Slice 1 settled that a `used` note goes in the same command as the first effect. So Brass Horse writes two lines:
   - `used`: "Brass Horse: {Red} revealed {card}.", covering the Revealed line.
   - `placed`: "Brass Horse: {Red} placed at {site}."

   With an empty pile nothing is revealed. Then "Used Brass Horse" stays, followed by the `placed` line.
3. **Horned Mask after a discard question.**
   - When the adviser area is full, the discard question parks after the denizen answer. The take then lands in a third command, after "Used Horned Mask" has posted. In that case the note uses the key `taken`, with the same sentence, and posts as a trigger line.
   - Otherwise it uses `used`.
4. **Cards in a note are judged at three states.** These are the action's start, the step the note restates (the last step before it in the run), and the note itself. A viewer who could identify the card at any of them reads its name.
   - A generic line judges a card before and after its own operation, so a covered line never named less than this.
   - Without this, Magic Waterskin's own line would open with "a Relic", because the relic is buried by then. A relic Fae Merchant draws and puts back would also read as its back to its owner.
5. **Ivory Eye's wording.** The spec wrote "{Red} peeked at {Blue}'s {card}.". To a viewer who may not identify the card, that reads "Blue's a Denizen". The line is "Ivory Eye: {Red} peeked at {card} in {Blue}'s advisers." instead.
6. **Banner sources.** Wandering Flame's powers are printed on the Darkest Secret banner. `PowerSourceRef.of` learns the banner option, and the log names a banner source by its banner, as "Used Darkest Secret" does today. The lines therefore read "Darkest Secret: {Red} placed at {site}.". The spec's "Wandering Flame:" is corrected to match.
7. **Wandering Flame's place line keeps "a secret".** It moves exactly one secret or none, and writes nothing when none moved. The spec's wording stays: "{Red} placed a secret at {site}.".
8. **Magic Waterskin's Buried line stays.** The bury comes before the gain on the card. The Buried line therefore posts before "Magic Waterskin: {Red} gained 4 Supply.", which covers nothing, as the spec's table says.
9. **Silver Tongue's script in the log suites.** `GameLogPowerLinesSuite`, `GameLogDecisionSuite` and `GameLogExchangeSuite` use Silver Tongue's REST as "a power with no line of its own". Once Silver Tongue writes its line, those tests read its journal through `LogScripts.withoutNotes`. That is the journal with its notes removed and renumbered. The `use-power` golden log changes to show the new line.

### Facts this plan relies on (verified against the code on 2026-09-26, at `a8a2bcde`)

- **Mechanism (slice 1):**
  - `model/PowerNotes.scala` defines:
    - `PowerNote(source, key, args)`;
    - `NoteArg.{Player, Card, Site, Amount, Number, Bank, Dice}`;
    - `NoteUnit.{Favor, Secret, Supply, Warband}`;
    - `NotePart.{Text, Arg, Plural}`;
    - `NoteKey(name, template)`, with `apply(source, args*)` and `NoteKey.Used = "used"`;
    - `NoteStates(now, previous, answered)`;
    - `Note(power, build, covers = false)`.
  - `PhasePower extends NotingPower` with `def noteKeys: Vector[NoteKey] = Vector.empty`.
  - `PowerSourceRef.of(ref: DecisionOptionRef)` handles Denizen, Relic, Vision, Edifice and Site, not Banner.
  - The walker journals `PowerNoted(power, note, covers)` when it reaches a `Note` fresh. `NoteStates.previous` is the states around the last step the walk journaled, a decision answer included.
  - Each leaf (a primitive operation or a `BuildOps`) is one journaled step. An empty `BuildOps` batch journals nothing.
- **Engine:** the engine prepends the cost payment to `build`'s tree and appends `RecordPowerUse` after it. The cost step is never an "effect" for "Used {card}" (`ActionLines.effect`).
- **Game Log:**
  - `PowerLines.line` renders a note with `words.source(note.source, state, viewer)` and `words.card(id, state, state, viewer)`, where `state` is the state after the note.
  - `PowerLines.kind` and `LogJournal.notedUse` both compare `note.key == NoteKey.Used`.
  - `LogWords.card(id, before, after, viewer)` names a card when `presentation.identifiesAt` holds at `before` or at `after`.
  - `ActionLines.powerSource` is the only other caller of `words.source`.
  - `DetailLines.lines` drops a step's roll and delta lines when `journal.covered(at)`. Its decision line always stays.
  - Generic lines that notes cover:
    - `Gain.Favor`, and a `Move` of favor from a bank to a play area: "Gained {n} favor from the {suit} bank".
    - `Gain.Secrets`: "Gained {n} secret".
    - `Bury`: "Buried {card}".
    - `Peek`: "Peeked at {card}".
    - `Reveal`: "Revealed {card}".
  - `GainSupply`, `Kill`, pawn `Move`s, `Play` of a relic, `Take`, `Give`, `Discard.Relic` and `EnterPhase` post no detail line.
- **Phase powers:** `PhasePowerCatalog.default` registers 22 powers: SilverTongue, VowOfObedience, WaysideInn, Elders, MagicWaterskin, MarbleFountains, GamblingHall, BoneDice, MurkyFountain, DowsingSticks, FaeMerchant, Wolves, Alchemist, SleightOfHand, CrystalVial, IvoryEye, HornedMask, Whistle, MagicCarpet, BrassHorse, WanderingFlameMove, WanderingFlamePlace.
- **State:**
  - `PlayerBoardState(favor, faceUpSecrets, faceDownSecrets, warbands, supply: SupplyTrack)`, where `supply.supply` is the value.
  - `ready.banks.favor: Map[Suit, Int]`.
  - `PlayerState.pawnSite: Option[SiteId]` and `PlayerState.relics` (each has `.id`).
  - `TurnStateOperations.enterPhase` sets `turn.phase`.
- **Tests:**
  - `PowerFixture.actor` is the active player.
  - `TargetsFixture` offers `use(ready, power: PhasePower, source)`, `answer(t, by, decisionId, answer)`, `pick(ref)` and `after(t)`.
  - `PaidActionHarness` offers `use(rules, ready, id, source)` and `answer(rules, state, decisionId, ref)`.
  - `MovementFixture` offers `use(ready, powerId, relic)` and `choose(state, decisionId, ref)`.
  - `LogScripts` offers `usePower` (Silver Tongue's REST), `gamblingHall`, `inserted`, `format`, `text`, `texts`, `name`, `presentation`, `formatter` and `named`.
  - `GameLogGoldenSuite` compares every script in `named` against `src/test/resources/gamelog/{name}.{actor,other}.log`. `GAMELOG_GOLDEN=write` rewrites them.

---

### Task 1: The formatter reads `used` variants and judges a note's cards at three states

**Files:**
- Modify: `src/main/scala/oathdigital/model/PowerNotes.scala` (the `NoteKey` companion)
- Modify: `src/main/scala/oathdigital/model/GameState.scala` (`PowerSourceRef.of`)
- Modify: `src/main/scala/oathdigital/application/gamelog/LogWords.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/PowerLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/LogJournal.scala` (`notedUse`)
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala:302`
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/PowerNoteCatalogSuite.scala`

**Interfaces:**
- Produces:
  - `NoteKey.isUse(name: String): Boolean`
  - `LogWords.seen(id: CardId, states: Vector[ReadyGame], viewer: Option[PlayerId]): CardWord`
  - `LogWords.source(ref: PowerSourceRef, states: Vector[ReadyGame], viewer: Option[PlayerId]): Vector[LogSpan]`, which replaces the single-state form
  - `PowerSourceRef.of(DecisionOptionRef.Banner(b)) == Some(PowerSourceRef.Banner(b))`

- [ ] **Step 1: Write the failing tests**

In `PowerNoteCatalogSuite.scala`, add:

```scala
  test("a used line's variants are named used.{variant}"):
    assert(NoteKey.isUse("used"))
    assert(NoteKey.isUse("used.none"))
    assert(!NoteKey.isUse("usedx"))
    assert(!NoteKey.isUse("gained"))

  test("a banner option names its banner as a note's source"):
    assertEquals(PowerSourceRef.of(DecisionOptionRef.Banner(Banner.DarkestSecret)),
      Some(PowerSourceRef.Banner(Banner.DarkestSecret)))
```

In `GameLogPowerLinesSuite.scala`:

- Add a variant key beside `counted`:

  ```scala
    private val none = NoteKey("used.none", Vector(NotePart.Text("Nothing to take.")))
  ```

- Change the `noting` formatter's wordings to `NoteWordings.of(power, Vector(said, took, counted, none))`.
- Add:

```scala
  test("a variant of a phase power's used note also replaces Used {card}"):
    val script = usePower
    val steps = script.history.steps
    val noted = inserted(steps, take(steps),
      PowerNoted(power, none(card), covers = false))
    assert(!lines(noted).exists(_.startsWith("Used ")), lines(noted))
    val entry = ours(noted).head
    assertEquals(text(entry), "Silver Tongue: Nothing to take.")
    assertEquals(entry.kind, LogKind.Action)

  test("a card is named to a viewer who identified it at any state its line reads"):
    val script = usePower
    val last = script.history.steps.last.after match
      case OathState.Ready(ready) => ready
      case other => fail(s"expected a ready game, got $other")
    val (owner, hidden) = last.game.current.players
      .filter(_.player != script.actor).flatMap(held => held.advisers.collect {
        case DenizenState(id, Orientation.FaceDown, _) => held.player -> id
      }).head
    val shown = last.updateCurrent(current => current.copy(players =
      current.players.map(held => if held.player != owner then held
        else held.copy(advisers = held.advisers.map {
          case DenizenState(`hidden`, _, tokens) =>
            DenizenState(hidden, Orientation.FaceUp, tokens)
          case other => other
        }))))
    val words = new LogWords(catalog, presentation)
    val viewer = Some(script.actor)
    assertEquals(words.seen(hidden, Vector(last), viewer), CardWord.Back("Denizen"))
    assertEquals(words.seen(hidden, Vector(shown, last), viewer),
      CardWord.Named(LogSpan.Card(hidden.value,
        presentation.cardLabel(last, hidden))))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogPowerLinesSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite"`
Expected: compile errors: `isUse` and `seen` are not members, and `PowerSourceRef.of` returns `None` for a banner.

- [ ] **Step 3: Implement**

In `PowerNotes.scala`, extend the `NoteKey` companion:

```scala
object NoteKey:
  /** A phase power's own line, which replaces "Used {card}". */
  val Used: String = "used"
  /** A phase power's own line, or a variant of it for another outcome, such
    * as nothing to target: `used`, or `used.{variant}`. Either replaces
    * "Used {card}" and is its action's line. */
  def isUse(name: String): Boolean = name == Used || name.startsWith(s"$Used.")
```

In `GameState.scala`, add a case to `PowerSourceRef.of`, before `case _ => None`:

```scala
    case DecisionOptionRef.Banner(banner) => Some(Banner(banner))
```

In `LogWords.scala`, replace `source` and `card`, and add `seen`:

```scala
  /** The card, banner or site a power belongs to, judged at `states`. */
  def source(ref: PowerSourceRef, states: Vector[ReadyGame],
      viewer: Option[PlayerId]): Vector[LogSpan] = ref match
    case PowerSourceRef.Card(id) => one(seen(id, states, viewer))
    case PowerSourceRef.Banner(held) => Vector(banner(held))
    case PowerSourceRef.Site(at) => Vector(site(at))
```

```scala
  /** Named when the viewer identifies the card where it lies before the
    * operation, or where it lies after it; otherwise its back. */
  def card(id: CardId, before: ReadyGame, after: ReadyGame,
      viewer: Option[PlayerId]): CardWord = seen(id, Vector(before, after), viewer)

  /** Named, by its label in the last of `states`, when the viewer identifies
    * the card in any of them; otherwise its back. */
  def seen(id: CardId, states: Vector[ReadyGame],
      viewer: Option[PlayerId]): CardWord =
    states.lastOption.filter(_ =>
      states.exists(presentation.identifiesAt(_, viewer, id))) match
      case Some(last) =>
        CardWord.Named(LogSpan.Card(id.value, presentation.cardLabel(last, id)))
      case None => CardWord.Back(LogWords.backOf(id))
```

In `ActionLines.scala:302`, pass one state:

```scala
    }.flatMap(source => ready.map(state => words.source(source, Vector(state), viewer)))
```

In `LogJournal.scala`, change `notedUse`'s match arm to:

```scala
      case PowerNoted(`power`, note, _) => NoteKey.isUse(note.key)
```

In `PowerLines.scala`:

- Import `WalkerStepRecorded` beside `PowerNoted`: `import oathdigital.gameplay.walker.{PowerNoted, WalkerStepRecorded}`.
- Replace `line`, `kind`, `sentence` and `argument` with:

```scala
  private def line(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Option[Posted] = journal.event(at) match
    case PowerNoted(power, note, _) if !repeated(journal, run, at, power, note) =>
      for
        template <- wordings.template(power, note.key)
        after <- journal.readyAfter(at)
        seen = states(journal, run, at) :+ after
      yield Posted.line(kind(run, power, note),
        words.source(note.source, seen, viewer) ++
          (Text(": ") +: sentence(template, note.args, seen, viewer)))
    case _ => None

  /** The states before `at` that a note's cards are judged at: its action's
    * start, and the step it restates, the last step before it. A generic
    * line judges a card before and after its own operation, so a note
    * restating it never names less. */
  private def states(journal: LogJournal, run: Run, at: Int): Vector[ReadyGame] =
    val restated = (run.first until at).reverseIterator.find(index =>
      journal.event(index).isInstanceOf[WalkerStepRecorded])
    journal.readyBefore(run.first).toVector ++
      restated.toVector.flatMap(journal.readyBefore)

  /** A phase power's own `used` note, or a variant of it, is its action's
    * line. */
  private def kind(run: Run, power: PowerId, note: PowerNote): LogKind =
    if NoteKey.isUse(note.key) && run.procedure == ActionRef.UsePower(power)
    then LogKind.Action
    else LogKind.Trigger
```

```scala
  private def sentence(template: Vector[NotePart], args: Vector[NoteArg],
      seen: Vector[ReadyGame], viewer: Option[PlayerId]): Vector[LogSpan] =
    template.flatMap:
      case NotePart.Text(written) => Vector(Text(written))
      case NotePart.Arg(index) =>
        args.lift(index).toVector.flatMap(argument(_, seen, viewer))
      case NotePart.Plural(index, one, many) => Vector(Text(args.lift(index) match
        case Some(NoteArg.Amount(1, _)) | Some(NoteArg.Number(1)) => one
        case _ => many))

  private def argument(arg: NoteArg, seen: Vector[ReadyGame],
      viewer: Option[PlayerId]): Vector[LogSpan] = arg match
    case NoteArg.Player(id) => Vector(words.player(id))
    case NoteArg.Card(id) => words.one(words.seen(id, seen, viewer))
    case NoteArg.Site(id) => Vector(words.site(id))
    case NoteArg.Amount(value, unit) =>
      Vector(LogSpan.Amount(value, unit.word(value)))
    case NoteArg.Number(value) => Vector(Text(value.toString))
    case NoteArg.Bank(suit) => Vector(Text(s"the $suit bank"))
    case NoteArg.Dice(faces) => LogWords.dice(faces).toVector
```

Update the class doc of `PowerLines` with one sentence: "A note names a card its viewer identified at its action's start, at the step it restates, or after it."

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.application.gamelog.* oathdigital.gameplay.powers.PowerNoteCatalogSuite"`
Expected: PASS, the golden logs included: no journal has a variant or a banner note yet.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/model/PowerNotes.scala src/main/scala/oathdigital/model/GameState.scala src/main/scala/oathdigital/application/gamelog/LogWords.scala src/main/scala/oathdigital/application/gamelog/PowerLines.scala src/main/scala/oathdigital/application/gamelog/LogJournal.scala src/main/scala/oathdigital/application/gamelog/ActionLines.scala src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala src/test/scala/oathdigital/gameplay/powers/PowerNoteCatalogSuite.scala
git commit -m "feat(log): used-line variants and cards judged at the restated step"
```

---

### Task 2: Note reads, and the powers that gain

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/NoteSupport.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/NoteText.scala`
- Modify:
  - `src/main/scala/oathdigital/gameplay/powers/action/Elders.scala`
  - `src/main/scala/oathdigital/gameplay/powers/action/WaysideInn.scala`
  - `src/main/scala/oathdigital/gameplay/powers/action/MagicWaterskin.scala`
  - `src/main/scala/oathdigital/gameplay/powers/action/Alchemist.scala`
  - `src/main/scala/oathdigital/gameplay/powers/wake/MarbleFountains.scala`
  - `src/main/scala/oathdigital/gameplay/powers/rest/SilverTongue.scala`
  - `src/main/scala/oathdigital/gameplay/powers/cardplay/VowOfObedience.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (`withoutNotes`, `formatWithoutNotes`)
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`, `GameLogDecisionSuite.scala`, `GameLogExchangeSuite.scala`
- Modify: `src/test/resources/gamelog/use-power.actor.log`, `use-power.other.log` (regenerated)
- Test:
  - `src/test/scala/oathdigital/gameplay/powers/action/ElderSuite.scala`
  - `src/test/scala/oathdigital/gameplay/powers/action/WaysideInnSuite.scala`
  - `src/test/scala/oathdigital/gameplay/powers/action/MagicWaterskinSuite.scala`
  - `src/test/scala/oathdigital/gameplay/powers/action/AlchemistSuite.scala`
  - `src/test/scala/oathdigital/gameplay/powers/wake/MarbleFountainsSuite.scala`
  - `src/test/scala/oathdigital/gameplay/powers/rest/SilverTongueSuite.scala`
  - `src/test/scala/oathdigital/gameplay/powers/cardplay/VowOfObedienceSuite.scala`

**Interfaces:**
- Consumes: `NoteKey.isUse` (Task 1).
- Produces:
  - `NoteSupport.Step = (ReadyGame, ReadyGame)`
  - `NoteSupport.board(ready, player): Option[PlayerBoardState]`
  - `NoteSupport.favor | supply | secrets | warbands (step: Step, player: PlayerId): Int`, each the change over the step
  - `NoteSupport.bankPaid(step): Option[Suit]`
  - `NoteSupport.relicsGained | relicsLost (step, player): Vector[RelicId]`
  - `NoteSupport.answer(states: NoteStates, decisionId: String): Option[DecisionOptionRef]`
  - `NoteSupport.gainedKey(name: String): NoteKey`: "{0} gained {1}."
  - `NoteSupport.gainNote(key, source: DecisionOptionRef, player, unit: NoteUnit, read: (Step, PlayerId) => Int)(states): Option[PowerNote]`
  - `NoteSupport.took: NoteKey`: `used`, "{0} took {1} from {2}."
  - `NoteSupport.tookNote(source, player)(states): Option[PowerNote]`: favor from one bank
  - test: `NoteText.Said(key: String, text: String, covers: Boolean)`, `NoteText.said(power: PhasePower, events: Vector[OathEvent]): Vector[Said]`
  - test: `LogScripts.withoutNotes(steps)`, `LogScripts.formatWithoutNotes(script, viewer)`

- [ ] **Step 1: Create the test helper**

Create `src/test/scala/oathdigital/gameplay/powers/NoteText.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.gameplay.walker.PowerNoted
import oathdigital.model._

/** A phase power's notes as plain sentences, for the card suites (power log
  * lines design, "Tests"). Arguments read plainly: a player, card or site by
  * id, an amount with its unit, a bank by suit, dice by their count. The
  * formatter's own rendering is tested in `GameLogPowerLinesSuite`. */
object NoteText:
  final case class Said(key: String, text: String, covers: Boolean)

  /** The notes `power` journaled in `events`, in order. */
  def said(power: PhasePower, events: Vector[OathEvent]): Vector[Said] =
    events.collect { case PowerNoted(id, note, covers) if id == power.id =>
      Said(note.key, sentence(power.noteKeys, note), covers) }

  private def sentence(keys: Vector[NoteKey], note: PowerNote): String =
    keys.find(_.name == note.key).fold(s"<no template for ${note.key}>")(
      _.template.map {
        case NotePart.Text(words) => words
        case NotePart.Arg(index) =>
          note.args.lift(index).fold("<missing>")(plain)
        case NotePart.Plural(index, one, many) => note.args.lift(index) match
          case Some(NoteArg.Amount(1, _)) | Some(NoteArg.Number(1)) => one
          case _ => many
      }.mkString)

  private def plain(arg: NoteArg): String = arg match
    case NoteArg.Player(id) => id.value
    case NoteArg.Card(id) => id.value
    case NoteArg.Site(id) => id.value
    case NoteArg.Amount(value, unit) => s"$value ${unit.word(value)}"
    case NoteArg.Number(value) => value.toString
    case NoteArg.Bank(suit) => s"the $suit bank"
    case NoteArg.Dice(faces) => s"${faces.size} dice"
```

- [ ] **Step 2: Write the failing card tests**

In each suite, add `NoteText` to the existing `oathdigital.gameplay.powers.{...}` import. The suites import:

- ElderSuite and WaysideInnSuite: `{NoteText, PhasePowerCatalog, PowerFixture}`
- MagicWaterskinSuite: `{NoteText, PhasePowerCatalog, PowerFixture}`
- AlchemistSuite: `{NoteText, PhasePowerCatalog, PowerFixture, TargetsFixture}`
- MarbleFountainsSuite: `{NoteText, PhasePowerCatalog, PlayerFacts, PowerFixture}`
- SilverTongueSuite: `{NoteText, PhasePowerCatalog, WalkerPowerCatalog}`

VowOfObedienceSuite imports `oathdigital.gameplay.powers.{CardStaging, PhasePowerCatalog, PowerFixture, ...}` over two lines. Add `NoteText` to that list.

`ElderSuite`:

```scala
  test("it writes its gain as its own line, covering the generic one"):
    val done = use(staged(favor = 3)).toOption.get
    assertEquals(NoteText.said(Elders, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 1 secret.", covers = true)))
```

`WaysideInnSuite`:

```scala
  test("it writes the Supply it gained as its own line"):
    val done = use(staged(favor = 3, supply = 2)).toOption.get
    assertEquals(NoteText.said(WaysideInn, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 2 Supply.", covers = false)))

  test("a gain the track caps is written as what it gained, and a full track writes nothing"):
    assertEquals(NoteText.said(WaysideInn,
      use(staged(favor = 1, supply = 6)).toOption.get.events).map(_.text),
      Vector(s"${actor.value} gained 1 Supply."))
    assertEquals(NoteText.said(WaysideInn,
      use(staged(favor = 1, supply = 7)).toOption.get.events), Vector.empty)
```

`MagicWaterskinSuite`:

```scala
  test("it writes the Supply it gained, as the track allowed"):
    assertEquals(NoteText.said(MagicWaterskin, use(staged()).toOption.get.events),
      Vector(NoteText.Said(NoteKey.Used, s"${actor.value} gained 4 Supply.",
        covers = false)))
    assertEquals(NoteText.said(MagicWaterskin,
      use(staged(supply = 5)).toOption.get.events).map(_.text),
      Vector(s"${actor.value} gained 2 Supply."))
```

`AlchemistSuite`:

```scala
  test("it writes the favor it gained, beside each bank's own line"):
    val t = use(staged(Suit.Nomad -> 9), Alchemist, source).toOption.get
    assertEquals(NoteText.said(Alchemist, t.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 4 favor.", covers = false)))

  test("a distribution writes its total once the player answers"):
    val ready = staged(Suit.Arcane -> 3, Suit.Discord -> 3, Suit.Nomad -> 2)
    val t = use(ready, Alchemist, source).toOption.get
    assertEquals(NoteText.said(Alchemist, t.events), Vector.empty)
    val done = answer(t, actor, Alchemist.decisionId,
      rows(Suit.Arcane -> 3, Suit.Discord -> 1, Suit.Nomad -> 0)).toOption.get
    assertEquals(NoteText.said(Alchemist, done.events).map(_.text),
      Vector(s"${actor.value} gained 4 favor."))

  test("empty banks give nothing, so nothing is written"):
    assertEquals(NoteText.said(Alchemist,
      use(staged(), Alchemist, source).toOption.get.events), Vector.empty)
```

`MarbleFountainsSuite`:

```scala
  test("it writes the Supply it refreshed to"):
    val done = use(staged()).toOption.get
    assertEquals(NoteText.said(MarbleFountains, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value}'s Supply refreshed to 7.",
        covers = false)))
```

`SilverTongueSuite`:

```scala
  test("its take is written as its own line, covering the generic one"):
    val (ready, actor) = arranged(Vector(Suit.Arcane, Suit.Nomad), Set(Suit.Arcane))
    val used = rules.startWalker(Ready(ready), use, actor, Vector.empty,
      Vector(source)).toOption.get
    assertEquals(NoteText.said(SilverTongue.forCatalog(catalog).get, used.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"${actor.value} took 1 favor from the Arcane bank.", covers = true)))
```

`VowOfObedienceSuite`, after the REST tests:

```scala
  test("REST: the take is written as its own line, covering the generic one"):
    val done = rest(resting(Map(Suit.Hearth -> 2)))
    assertEquals(NoteText.said(VowOfObedience.forCatalog(catalog).get, done.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"${actor.value} took 1 favor from the Hearth bank.", covers = true)))
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.ElderSuite oathdigital.gameplay.powers.action.WaysideInnSuite oathdigital.gameplay.powers.action.MagicWaterskinSuite oathdigital.gameplay.powers.action.AlchemistSuite oathdigital.gameplay.powers.wake.MarbleFountainsSuite oathdigital.gameplay.powers.rest.SilverTongueSuite oathdigital.gameplay.powers.cardplay.VowOfObedienceSuite"`
Expected: the new tests fail with `Vector()` against the expected notes. None of these powers journals a note yet.

- [ ] **Step 4: Create `NoteSupport`**

Create `src/main/scala/oathdigital/gameplay/powers/NoteSupport.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.model._

/** What a power's note reads (power log lines design, "Amounts are what
  * happened"), and the sentences several powers share. A read compares the
  * states around the step the note restates, so a note never repeats the
  * cap logic of the operation it describes.
  */
object NoteSupport:
  /** The states before and after one journaled step. */
  type Step = (ReadyGame, ReadyGame)

  def board(ready: ReadyGame, player: PlayerId): Option[PlayerBoardState] =
    ready.game.current.players.find(_.player == player).map(_.board)

  def favor(step: Step, player: PlayerId): Int = change(step, player)(_.favor)
  def supply(step: Step, player: PlayerId): Int =
    change(step, player)(_.supply.supply)
  def secrets(step: Step, player: PlayerId): Int =
    change(step, player)(held => held.faceUpSecrets + held.faceDownSecrets)
  def warbands(step: Step, player: PlayerId): Int =
    change(step, player)(_.warbands)

  /** The bank whose favor fell in the step, if one did. */
  def bankPaid(step: Step): Option[Suit] = Suit.all.find(suit =>
    step._2.banks.favor.getOrElse(suit, 0) < step._1.banks.favor.getOrElse(suit, 0))

  def relicsGained(step: Step, player: PlayerId): Vector[RelicId] =
    relics(step._2, player).filterNot(relics(step._1, player).contains)
  def relicsLost(step: Step, player: PlayerId): Vector[RelicId] =
    relics(step._1, player).filterNot(relics(step._2, player).contains)

  /** The option `decisionId` was answered with in this action, if it was
    * asked. */
  def answer(states: NoteStates, decisionId: String): Option[DecisionOptionRef] =
    states.answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
    }

  /** "{player} gained {amount}." */
  def gainedKey(name: String): NoteKey = NoteKey(name, Vector(NotePart.Arg(0),
    NotePart.Text(" gained "), NotePart.Arg(1), NotePart.Text(".")))

  /** What `player` gained in the step before the note, by `read`. Nothing
    * gained writes nothing. */
  def gainNote(key: NoteKey, source: DecisionOptionRef, player: PlayerId,
      unit: NoteUnit, read: (Step, PlayerId) => Int)(states: NoteStates)
      : Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    amount = read(step, player)
    if amount > 0
  yield key(card, NoteArg.Player(player), NoteArg.Amount(amount, unit))

  /** "{player} took {amount} from {whom}.": a REST power's take, or a secret
    * taken from a player. */
  val took: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text(".")))

  /** The favor `player` took from one bank in the step before the note. */
  def tookNote(source: DecisionOptionRef, player: PlayerId)(states: NoteStates)
      : Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    amount = favor(step, player)
    if amount > 0
    bank <- bankPaid(step)
  yield took(card, NoteArg.Player(player),
    NoteArg.Amount(amount, NoteUnit.Favor), NoteArg.Bank(bank))

  private def change(step: Step, player: PlayerId)(
      read: PlayerBoardState => Int): Int =
    board(step._2, player).fold(0)(read) - board(step._1, player).fold(0)(read)

  private def relics(ready: ReadyGame, player: PlayerId): Vector[RelicId] =
    ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.relics.map(_.id))
```

- [ ] **Step 5: Give the seven powers their notes**

`Elders.scala`, whole file:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Elders (card 26), ACTION: place 2 favor on this card, then gain 1 secret
  * from the shared bank, which holds an unlimited supply. Its own line
  * restates the gain in place of the generic Gain line.
  */
case object Elders extends PaidAction("denizen.elders", Cost(favor = 2)):
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  override def noteKeys: Vector[NoteKey] = Vector(gained)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    Gain.Secrets(player, 1),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Secret,
      NoteSupport.secrets), covers = true))))
```

`WaysideInn.scala`, whole file:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Wayside Inn (card 47), ACTION: place 1 favor on this card, then gain
  * 2 Supply. Its own line reads the Supply the track allowed.
  */
case object WaysideInn extends PaidAction("denizen.wayside-inn",
    Cost(favor = 1)):
  val Supply: Int = 2
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  override def noteKeys: Vector[NoteKey] = Vector(gained)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    GainSupply(player, Supply),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Supply,
      NoteSupport.supply)))))
```

`MagicWaterskin.scala`:

- Change the import to `import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts}`.
- Add `val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)` and `override def noteKeys: Vector[NoteKey] = Vector(gained)` under `val Supply`.
- Change the `yield` to:

```scala
      yield Sequence(Bury.standard(BuryableCard.Relic(id),
        PositionedLocation(Location.PlayArea(player)), None, 0,
        relic.tokens.secrets, player) :+ GainSupply(player, Supply) :+
        Note(this.id, NoteSupport.gainNote(gained, source, player,
          NoteUnit.Supply, NoteSupport.supply)))
```

  `id` there is the relic, from `case DecisionOptionRef.Relic(id)`, so the power's id is `this.id`.
- Add a doc sentence: "Its own line reads the Supply the track allowed. The Buried line posts before it, since the bury comes first."

`Alchemist.scala`:

- Change the import to `import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}`.
- Add under `val decisionId`:

```scala
  /** Its own line: the whole favor it gained. Each bank's Gain line stays. */
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  override def noteKeys: Vector[NoteKey] = Vector(gained)
```

- Change `build` to:

```scala
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => gain(live, player, pending)),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Favor,
      NoteSupport.favor)))))
```

`MarbleFountains.scala`:

- Add `import oathdigital.gameplay.powers.NoteSupport`.
- Add under `def timing`:

```scala
  /** Its own line: the Supply it refreshed to. */
  val refreshed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text("'s Supply refreshed to "), NotePart.Arg(1),
    NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = Vector(refreshed)
```

- Change `build` to:

```scala
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    GainSupply(player, SupplyTrack.Maximum),
    Note(id, states => for
      card <- PowerSourceRef.of(source)
      board <- NoteSupport.board(states.now, player)
    yield refreshed(card, NoteArg.Player(player),
      NoteArg.Number(board.supply.supply))))))
```

`SilverTongue.scala`:

- Add `import oathdigital.gameplay.powers.NoteSupport`.
- Add `override def noteKeys: Vector[NoteKey] = Vector(NoteSupport.took)` under `def source`.
- Wrap `build`'s `Branch` in a `Sequence` that ends in the note:

```scala
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    val choice = choiceDecisionId(ready, player)
    Right(Sequence(Vector(Branch((state, _) => stocked(state, player) match {
      case Vector(only) => Vector(take(player, _ => Right(only)))
      case several => Vector(
        Decide(choice, player, DecisionQuery.ChooseOne(several.map(suit =>
          DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
          heading = Some("Silver Tongue: take a favor from a bank"))),
        take(player, pending => pending.answered.collectFirst {
          case Answered(`choice`, DecisionAnswer.ChooseOneAnswer(
            DecisionOptionRef.FavorBank(suit)), _) => suit
        }.toRight(OathViolation.InvalidEventOrder(
          s"no Silver Tongue bank is recorded for $choice"))))
    }),
      Note(id, NoteSupport.tookNote(source, player), covers = true))))
```

  Inside `build`, `source` is the parameter, which shadows the class's `def source: RuleSourceRef`.

`VowOfObedience.scala`:

- Change the import to `import oathdigital.gameplay.powers.{CatalogCards, NoteSupport}`.
- Add `override def noteKeys: Vector[NoteKey] = Vector(NoteSupport.took)` under `def source`.
- Change `build` to:

```scala
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    val choice = VowOfObedience.choiceDecisionId(ready, player)
    Right(Sequence(Vector(
      Branch((state, _) => FavorBankChoice.take(state, player, 1, choice,
        "Vow of Obedience: take a favor from a bank")),
      Note(id, NoteSupport.tookNote(source, player), covers = true))))
```

- [ ] **Step 6: Run the card tests to verify they pass**

Run the Step 3 command.
Expected: PASS.

- [ ] **Step 7: Keep Silver Tongue's log tests on a journal without notes**

Silver Tongue now writes its own line. The log suites use its script as a power that has none. Give them the journal without its notes.

In `LogScripts.scala`, add below `inserted`:

```scala
  /** `steps` without their power notes, renumbered: the journal of a power
    * that writes no line of its own. Notes change no state, so every other
    * step is unchanged. */
  def withoutNotes(steps: Vector[ReplayStep[OathState, OathEvent]])
      : Vector[ReplayStep[OathState, OathEvent]] =
    steps.filterNot(_.event.event.isInstanceOf[PowerNoted]).zipWithIndex.map {
      case (step, index) => step.copy(event = step.event.copy(index = index.toLong))
    }

  def formatWithoutNotes(script: Script, viewer: Option[PlayerId])(using
      munit.Location): Vector[LogEntry] =
    formatter.format(withoutNotes(script.history.steps), viewer)
```

- Import `oathdigital.gameplay.walker.PowerNoted` in `LogScripts.scala`.
- If `RecordedEvent.index` is not a `Long`, match its type, as `inserted` does.
- In `GameLogPowerLinesSuite.scala`, replace every `script.history.steps` with `withoutNotes(script.history.steps)`. Do not change the Gambling Hall test, which uses `format(script, None)`.

In `GameLogDecisionSuite.scala`, change the first test to read both journals:

```scala
  test("a power's own decision posts one Chose line naming what was shown"):
    val all = texts(formatWithoutNotes(usePower, None).filter(_.depth == 1))
    val chose = all.filter(_.startsWith("Chose "))
    assertEquals(chose.size, 1, all)
    assert(!chose.head.contains("Button"), chose.head)
    // The line follows the power's own "Used" line.
    assert(all.indexWhere(_.startsWith("Used ")) < all.indexOf(chose.head), all)

  test("a power's own line follows the Chose line of the choice it restates"):
    val all = lines(usePower)
    val chose = all.indexWhere(_.startsWith("Chose "))
    assert(chose >= 0, all)
    assert(all(chose + 1).startsWith("Silver Tongue: "), all)
    assert(!all.exists(_.startsWith("Used ")), all)
```

In `GameLogExchangeSuite.scala`, change `val all = lines(usePower)` in "a used power is named by its source card, once" to:

```scala
    val all = texts(formatWithoutNotes(usePower, None).filter(_.depth == 1))
```

- [ ] **Step 8: Regenerate and review the `use-power` golden logs**

Run: `GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`
Then run: `git status --short src/test/resources/gamelog`
Expected: only `use-power.actor.log` and `use-power.other.log` changed. In each, the lines:

```
27.0 action 1 | Used [card:92|Silver Tongue]
27.1 decision 1 | Chose the Discord bank
28.0 delta 1 | Gained 1 favor from the Discord bank
```

become a Chose line followed by one `action` line reading `[card:92|Silver Tongue]: [player:p2|P2] took [amount:1 favor] from the Discord bank.` The sequence numbers after it move up by one. Read both files in full. If any other golden changed, stop and find out why before going on.

- [ ] **Step 9: Run the server suite**

Run: `./sbtw test`
Expected: PASS. A suite that asserts one of these seven powers' exact event list may now also see `walker.power-noted` events. Update only that expectation, to include the new events.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/NoteSupport.scala src/main/scala/oathdigital/gameplay/powers/action/Elders.scala src/main/scala/oathdigital/gameplay/powers/action/WaysideInn.scala src/main/scala/oathdigital/gameplay/powers/action/MagicWaterskin.scala src/main/scala/oathdigital/gameplay/powers/action/Alchemist.scala src/main/scala/oathdigital/gameplay/powers/wake/MarbleFountains.scala src/main/scala/oathdigital/gameplay/powers/rest/SilverTongue.scala src/main/scala/oathdigital/gameplay/powers/cardplay/VowOfObedience.scala src/test/scala/oathdigital/gameplay/powers/NoteText.scala src/test/scala/oathdigital/gameplay/powers/action/ElderSuite.scala src/test/scala/oathdigital/gameplay/powers/action/WaysideInnSuite.scala src/test/scala/oathdigital/gameplay/powers/action/MagicWaterskinSuite.scala src/test/scala/oathdigital/gameplay/powers/action/AlchemistSuite.scala src/test/scala/oathdigital/gameplay/powers/wake/MarbleFountainsSuite.scala src/test/scala/oathdigital/gameplay/powers/rest/SilverTongueSuite.scala src/test/scala/oathdigital/gameplay/powers/cardplay/VowOfObedienceSuite.scala src/test/scala/oathdigital/application/gamelog/LogScripts.scala src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala src/test/scala/oathdigital/application/gamelog/GameLogDecisionSuite.scala src/test/scala/oathdigital/application/gamelog/GameLogExchangeSuite.scala src/test/resources/gamelog/use-power.actor.log src/test/resources/gamelog/use-power.other.log
git commit -m "feat(powers): gain and take powers write their own log lines"
```

Add to that list any suite Step 9 made you update.

### Checkpoint A

Dispatch a review subagent on Sonnet or lower. Point it at Tasks 1 and 2, the spec's section 2 and "Settled in slice 1", and this plan's decisions 1, 4 and 9. Ask it to check:

- that `PowerLines.states` cannot read past the note's own event, so prefix stability holds;
- that a note's `previous` is the step it restates in each of the seven powers;
- that no power's `noteKeys` misses a key it builds;
- that the `use-power` golden changed only as Step 8 describes.

Fix what it finds before Task 3.

---

### Task 3: The dice powers

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/RollResults.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/GamblingHall.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/BoneDice.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/MurkyFountain.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/BoneDiceSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/MurkyFountainSuite.scala`

**Interfaces:**
- Consumes: `NoteSupport.gainedKey`, `NoteSupport.gainNote`, `NoteSupport.supply`, `NoteSupport.favor`, `NoteSupport.relicsLost` (Task 2).
- Produces:
  - `RollResults.rolled: NoteKey`: `used`, "{0} rolled {1}, Total: {2}"
  - `RollResults.rollNote(source, player, pool)(states): Option[PowerNote]`
  - `GamblingHall.rolled` stays, equal to `RollResults.rolled`.

- [ ] **Step 1: Write the failing tests**

Add `NoteText` to the `oathdigital.gameplay.powers.{...}` import of both suites.

`BoneDiceSuite`:

```scala
  test("it writes its roll and its gain, and no skull writes no bury"):
    val done = use(rules(attackDice(AttackDieFace.OneSword,
      AttackDieFace.HollowSword)), staged(), BoneDice.id, source).toOption.get
    assertEquals(NoteText.said(BoneDice, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} rolled 2 dice, Total: 1",
        covers = true),
      NoteText.Said("gained", s"${actor.value} gained 1 Supply.", covers = false)))

  test("a skull writes the bury in place of the generic line"):
    val done = use(rules(attackDice(AttackDieFace.OneSword,
      AttackDieFace.TwoSwordsSkull)), staged(), BoneDice.id, source).toOption.get
    assertEquals(NoteText.said(BoneDice, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} rolled 2 dice, Total: 3",
        covers = true),
      NoteText.Said("gained", s"${actor.value} gained 3 Supply.", covers = false),
      NoteText.Said("buried", "Buried after a skull.", covers = true)))
```

`MurkyFountainSuite`:

```scala
  test("it writes its roll and the Supply it gained"):
    val done = use(rules(defenseDice(DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields)), staged(), MurkyFountain.id, source).toOption.get
    assertEquals(NoteText.said(MurkyFountain, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} rolled 2 dice, Total: 3",
        covers = true),
      NoteText.Said("gained", s"${actor.value} gained 3 Supply.", covers = false)))

  test("a total of zero writes that the Act phase ended"):
    val done = use(rules(defenseDice(DefenseDieFace.Blank, DefenseDieFace.Blank)),
      staged(), MurkyFountain.id, source).toOption.get
    assertEquals(NoteText.said(MurkyFountain, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} rolled 2 dice, Total: 0",
        covers = true),
      NoteText.Said("ended", s"${actor.value}'s Act phase ended.", covers = false)))

  test("with the pawn elsewhere it writes only that"):
    val done = use(rules(), staged(pawnAtEdifice = false), MurkyFountain.id,
      source).toOption.get
    assertEquals(NoteText.said(MurkyFountain, done.events), Vector(
      NoteText.Said("used.away", s"${actor.value} was not at its site.",
        covers = false)))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BoneDiceSuite oathdigital.gameplay.powers.action.MurkyFountainSuite"`
Expected: the new tests fail with `Vector()`.

- [ ] **Step 3: Implement**

`RollResults.scala`: change the import to `import oathdigital.model._`, and add to the object:

```scala
  /** "{player} rolled {dice}, Total: {n}": a dice power's own line, in place
    * of the generic "Rolled" line it covers. */
  val rolled: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" rolled "), NotePart.Arg(1), NotePart.Text(", Total: "),
    NotePart.Arg(2)))

  /** The roll `pool` recorded, restated with its total. */
  def rollNote(source: DecisionOptionRef, player: PlayerId, pool: PoolKey)(
      states: NoteStates): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    outcome <- states.now.game.current.rollOutcomes.get(pool)
  yield rolled(card, NoteArg.Player(player), NoteArg.Dice(outcome.faces),
    NoteArg.Number(score(states.now, pool)))
```

`GamblingHall.scala`:

- Add `import oathdigital.gameplay.powers.NoteSupport`.
- Replace the `rolled` definition with `val rolled: NoteKey = RollResults.rolled`, keeping its doc comment.
- In `build`, change the first note to `Note(id, RollResults.rollNote(source, player, pool), covers = true)`.
- Delete the private `rollNote` and `favor`.
- In `gainNote`, change `amount = favor(step._2, player) - favor(step._1, player)` to `amount = NoteSupport.favor(step, player)`.

`BoneDice.scala`:

- Change the import to `import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts}`.
- Add under `val pool`:

```scala
  val gained: NoteKey = NoteSupport.gainedKey("gained")
  /** The bury a skull forces, in place of the generic Buried line. */
  val buried: NoteKey = NoteKey("buried", Vector(
    NotePart.Text("Buried after a skull.")))
  override def noteKeys: Vector[NoteKey] = Vector(RollResults.rolled, gained,
    buried)
```

- Change `build`, renaming the relic pattern variable so it no longer shadows the power's `id`:

```scala
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match
    case DecisionOptionRef.Relic(relic) => Right(Sequence(Vector(
      ModifyDicePool(pool, Dice),
      Roll(pool, DiceSpec(DiceKind.Attack), RollMode.Automatic),
      Note(id, RollResults.rollNote(source, player, pool), covers = true),
      BuildOps((state, _) => settle(state, player, relic)),
      Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Supply,
        NoteSupport.supply)),
      Note(id, buryNote(_, player, source), covers = true))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not a relic source"))

  /** The settle step buried the relic. It covers that step, whose only
    * generic line is the Buried line: `GainSupply` posts none. */
  private def buryNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    if NoteSupport.relicsLost(step, player).nonEmpty
  yield buried(card)
```

`MurkyFountain.scala`:

- Add `import oathdigital.gameplay.powers.NoteSupport`.
- Add under `val pool`:

```scala
  val gained: NoteKey = NoteSupport.gainedKey("gained")
  val ended: NoteKey = NoteKey("ended", Vector(NotePart.Arg(0),
    NotePart.Text("'s Act phase ended.")))
  /** Its line when the pawn is elsewhere: the cost is paid, nothing else. */
  val away: NoteKey = NoteKey("used.away", Vector(NotePart.Arg(0),
    NotePart.Text(" was not at its site.")))
  override def noteKeys: Vector[NoteKey] = Vector(RollResults.rolled, gained,
    ended, away)
```

- Change `build`, renaming the edifice pattern variable:

```scala
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match
    case DecisionOptionRef.Edifice(edifice) =>
      if !atPawnSite(ready, player, edifice) then Right(Sequence(Vector(
        Note(id, _ => PowerSourceRef.of(source).map(away(_,
          NoteArg.Player(player)))))))
      else Right(Sequence(Vector(
        ModifyDicePool(pool, Dice),
        Roll(pool, DiceSpec(DiceKind.Defense), RollMode.Automatic),
        Note(id, RollResults.rollNote(source, player, pool), covers = true),
        BuildOps((state, _) => Right(outcome(state, player))),
        Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Supply,
          NoteSupport.supply)),
        Note(id, endNote(_, player, source)))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not an edifice source"))

  /** The outcome step moved the turn out of the Act phase. */
  private def endNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    if step._1.game.current.turn.phase != step._2.game.current.turn.phase
  yield ended(card, NoteArg.Player(player))
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.* oathdigital.application.gamelog.*"`
Expected: PASS. `GamblingHallSuite` and the `gambling-hall` golden are unchanged.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/RollResults.scala src/main/scala/oathdigital/gameplay/powers/action/GamblingHall.scala src/main/scala/oathdigital/gameplay/powers/action/BoneDice.scala src/main/scala/oathdigital/gameplay/powers/action/MurkyFountain.scala src/test/scala/oathdigital/gameplay/powers/action/BoneDiceSuite.scala src/test/scala/oathdigital/gameplay/powers/action/MurkyFountainSuite.scala
git commit -m "feat(powers): dice powers write their roll and its outcome"
```

---

### Task 4: Relic draws, burials and peeks

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/RelicDraws.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/DowsingSticks.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/FaeMerchant.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/CrystalVial.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/IvoryEye.scala`
- Test:
  - `src/test/scala/oathdigital/gameplay/powers/action/DowsingSticksSuite.scala`
  - `src/test/scala/oathdigital/gameplay/powers/action/FaeMerchantSuite.scala`
  - `src/test/scala/oathdigital/gameplay/powers/action/CrystalVialSuite.scala`
  - `src/test/scala/oathdigital/gameplay/powers/action/IvoryEyeSuite.scala`

**Interfaces:**
- Consumes: `NoteSupport.relicsGained`, `relicsLost`, `answer` (Task 2).
- Produces:
  - `RelicDraws.drew: NoteKey`: `used`, "{0} drew {1} facedown."
  - `RelicDraws.drawNote(source, player)(states): Option[PowerNote]`

- [ ] **Step 1: Write the failing tests**

Add `NoteText` to each suite's `oathdigital.gameplay.powers.{...}` import.

`DowsingSticksSuite`:

```scala
  test("it writes the relic it drew"):
    val ready0 = staged()
    val top = ready0.game.current.commonCards.relicDeck.head
    val done = use(rules(), ready0, DowsingSticks.id, source).toOption.get
    assertEquals(NoteText.said(DowsingSticks, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} drew ${top.value} facedown.",
        covers = false)))

  test("an empty relic deck is written as such"):
    val ready0 = staged()
    val current = ready0.game.current
    val emptied = ready0.updateCurrent(_.copy(commonCards =
      current.commonCards.copy(relicDeck = Vector.empty)))
      .updateCampaign(c => c.copy(reliquary = c.reliquary ++
        current.commonCards.relicDeck))
    val done = use(rules(), emptied, DowsingSticks.id, source).toOption.get
    assertEquals(NoteText.said(DowsingSticks, done.events), Vector(
      NoteText.Said("used.empty", "The relic deck was empty.", covers = false)))
```

`FaeMerchantSuite`:

```scala
  test("it writes the relic it drew, then the relic it put back in place of the generic line"):
    val ready0 = staged()
    val top = ready0.game.current.commonCards.relicDeck.head
    val merchant = FaeMerchant.forCatalog(catalog)
    val parked = use(rules(), ready0, FaeMerchant.id, source).toOption.get
    assertEquals(NoteText.said(merchant, parked.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} drew ${top.value} facedown.",
        covers = false)))
    val done = answer(rules(), parked.state, FaeMerchant.decisionId,
      relicRef(held1)).toOption.get
    assertEquals(NoteText.said(merchant, done.events), Vector(
      NoteText.Said("returned",
        s"${actor.value} put ${held1.value} on the bottom of the relic deck.",
        covers = true)))
```

`CrystalVialSuite`:

```scala
  test("it writes the card it buried in place of the generic line"):
    val t = use(staged, power, source).toOption.get
    val done = answer(t, actor, CrystalVial.decisionId,
      pick(DecisionOptionRef.Denizen(held))).toOption.get
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} buried ${held.value}.", covers = true)))

  test("with no candidate nothing is written"):
    assertEquals(NoteText.said(power, use(bare, power, source).toOption.get.events),
      Vector.empty)
```

`IvoryEyeSuite`:

```scala
  test("it writes the adviser it peeked at in place of the generic line"):
    val t = use(staged, IvoryEye, source).toOption.get
    val card = firstAdviser(staged, target)
    val done = answer(t, actor, IvoryEye.decisionId, peekAt(target, 0))
      .toOption.get
    assertEquals(NoteText.said(IvoryEye, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${actor.value} peeked at ${card.value} in ${target.value}'s advisers.",
      covers = true)))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.DowsingSticksSuite oathdigital.gameplay.powers.action.FaeMerchantSuite oathdigital.gameplay.powers.action.CrystalVialSuite oathdigital.gameplay.powers.action.IvoryEyeSuite"`
Expected: the new tests fail with `Vector()`, except "with no candidate nothing is written", which already passes.

- [ ] **Step 3: Implement**

`RelicDraws.scala`, add to the object:

```scala
  /** "{player} drew {relic} facedown.": a relic draw's own line. */
  val drew: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" drew "), NotePart.Arg(1), NotePart.Text(" facedown.")))

  /** The relic the step before the note drew, restated. Nothing when that
    * step drew none. */
  def drawNote(source: DecisionOptionRef, player: PlayerId)(
      states: NoteStates): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    relic <- NoteSupport.relicsGained(step, player).headOption
  yield drew(card, NoteArg.Player(player), NoteArg.Card(relic))
```

`DowsingSticks.scala`:

- Change the import to `import oathdigital.gameplay.powers.RelicDraws`. It is unchanged.
- Add:

```scala
  /** Its line when the deck had no relic to draw. */
  val emptyDeck: NoteKey = NoteKey("used.empty", Vector(
    NotePart.Text("The relic deck was empty.")))
  override def noteKeys: Vector[NoteKey] = Vector(RelicDraws.drew, emptyDeck)
```

- Change `build` to:

```scala
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    BuildOps((state, _) => Right(RelicDraws.takeTop(state, player))),
    Note(id, states => RelicDraws.drawNote(source, player)(states)
      .orElse(PowerSourceRef.of(source).map(emptyDeck(_)))))))
```

With an empty deck, the draw journals nothing. The step before the note is then the cost payment, which draws no relic.

`FaeMerchant.scala`:

- Change the import to `import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, RelicDraws}`.
- In the class, add `override def noteKeys: Vector[NoteKey] = Vector(RelicDraws.drew, returned)`.
- Change `build` to:

```scala
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((state, _) => Right(RelicDraws.takeTop(state, player))),
    Note(this.id, RelicDraws.drawNote(source, player)),
    Branch((state, _) => candidates(state, player) match {
      case several if several.size > 1 => Vector(Decide(decisionId, player,
        DecisionQuery.ChooseOne(several.map(id =>
          DecisionOption.Relic(DecisionOptionRef.Relic(id))),
          heading = Some("Fae Merchant: put a relic on the bottom of the " +
            "relic deck"))))
      case _ => Vector.empty
    }),
    BuildOps((state, pending) => putBack(state, player, pending)),
    Note(this.id, returnNote(_, player, source), covers = true))))

  /** The relic the bury took from the player, in place of its Buried line. */
  private def returnNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    relic <- NoteSupport.relicsLost(step, player).headOption
  yield returned(card, NoteArg.Player(player), NoteArg.Card(relic))
```

- In the companion, add:

```scala
  val returned: NoteKey = NoteKey("returned", Vector(NotePart.Arg(0),
    NotePart.Text(" put "), NotePart.Arg(1),
    NotePart.Text(" on the bottom of the relic deck.")))
```

`CrystalVial.scala`:

- Change the import to `import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}`.
- In the class, add `override def noteKeys: Vector[NoteKey] = Vector(buried)`.
- Change `build` to:

```scala
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => bury(live, player, pending)),
    Note(this.id, buriedNote(_, player, source), covers = true))))

  /** The chosen card, read where it stood before the bury step. */
  private def buriedNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    ref <- NoteSupport.answer(states, decisionId)
    chosen <- candidates(step._1, actor).find(_.ref == ref)
  yield buried(card, NoteArg.Player(actor), NoteArg.Card(chosen.card.id))
```

- In the companion, add:

```scala
  val buried: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" buried "), NotePart.Arg(1), NotePart.Text(".")))
```

`IvoryEye.scala`:

- Change the import to `import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}`.
- Add under `private val Prefix`:

```scala
  /** Its own line, in place of the generic Peeked line. The card reads as
    * its back to anyone but the peeker, so the owner is named apart from
    * it. */
  val peeked: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" peeked at "), NotePart.Arg(1), NotePart.Text(" in "),
    NotePart.Arg(2), NotePart.Text("'s advisers.")))
  override def noteKeys: Vector[NoteKey] = Vector(peeked)
```

- Change `build` to:

```scala
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => peek(live, player, pending)),
    Note(id, peekNote(_, player, source), covers = true))))

  /** A peek leaves the adviser where it was, so the answered slot still
    * names it. */
  private def peekNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    ref <- NoteSupport.answer(states, decisionId)
    target <- targets(states.now).find(_.ref == ref)
  yield peeked(card, NoteArg.Player(actor), NoteArg.Card(target.card),
    NoteArg.Player(target.owner))
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.* oathdigital.gameplay.powers.PowerNoteCatalogSuite"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/RelicDraws.scala src/main/scala/oathdigital/gameplay/powers/action/DowsingSticks.scala src/main/scala/oathdigital/gameplay/powers/action/FaeMerchant.scala src/main/scala/oathdigital/gameplay/powers/action/CrystalVial.scala src/main/scala/oathdigital/gameplay/powers/action/IvoryEye.scala src/test/scala/oathdigital/gameplay/powers/action/DowsingSticksSuite.scala src/test/scala/oathdigital/gameplay/powers/action/FaeMerchantSuite.scala src/test/scala/oathdigital/gameplay/powers/action/CrystalVialSuite.scala src/test/scala/oathdigital/gameplay/powers/action/IvoryEyeSuite.scala
git commit -m "feat(powers): relic draws, burials and peeks write their own lines"
```

---

### Task 5: The powers that target a player

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/SleightOfHand.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/Whistle.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/Wolves.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/SleightOfHandSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/WhistleSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/WolvesSuite.scala`

**Interfaces:**
- Consumes: `NoteSupport.took`, `secrets`, `warbands` and `answer` (Task 2), and `NoteKey.isUse` for the `used.none` variants (Task 1).

- [ ] **Step 1: Write the failing tests**

Add `NoteText` to each suite's `oathdigital.gameplay.powers.{...}` import.

`SleightOfHandSuite`:

```scala
  test("it writes the secret it took"):
    val t = use(staged(2, 0), SleightOfHand, source).toOption.get
    val done = answer(t, actor, SleightOfHand.decisionId, choose(victim))
      .toOption.get
    assertEquals(NoteText.said(SleightOfHand, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} took 1 secret from ${victim.value}.",
      covers = false)))

  test("with nobody to rob it writes that"):
    val t = use(staged(1, 0), SleightOfHand, source).toOption.get
    assertEquals(NoteText.said(SleightOfHand, t.events), Vector(NoteText.Said(
      "used.none", "No player could be robbed.", covers = false)))
```

`WhistleSuite`:

```scala
  test("it writes the pawn it pulled and the secret it gave"):
    val parked = use(staged(), Whistle.id, whistle).toOption.get
    val done = choose(parked.state, Whistle.decisionId, target).toOption.get
    assertEquals(NoteText.said(Whistle, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Placed ${p3.value} at ${ancientCity.value} and gave " +
        s"${p3.value} the Whistle's secret.", covers = false)))

  test("with nobody to pull it writes that"):
    val start = withPawn(withPawn(staged(), p1, ancientCity), p3, ancientCity)
    val done = use(start, Whistle.id, whistle).toOption.get
    assertEquals(NoteText.said(Whistle, done.events), Vector(NoteText.Said(
      "used.none", "No pawn could be pulled.", covers = false)))
```

`WolvesSuite`:

```scala
  test("it writes the warband it killed"):
    val done = answer(parked, actor, Wolves.decisionId, choose(victim)).toOption.get
    assertEquals(NoteText.said(Wolves, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Killed 1 ${victim.value} warband.", covers = false)))

  test("a board with no warband is written as such"):
    val empty = updatePlayer(staged(), victim)(p =>
      p.copy(board = p.board.copy(warbands = 0)))
    val t = use(empty, Wolves, source).toOption.get
    val done = answer(t, actor, Wolves.decisionId, choose(victim)).toOption.get
    assertEquals(NoteText.said(Wolves, done.events), Vector(NoteText.Said(
      "used.none", s"${victim.value} had no warband to kill.", covers = false)))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.SleightOfHandSuite oathdigital.gameplay.powers.action.WhistleSuite oathdigital.gameplay.powers.action.WolvesSuite"`
Expected: the new tests fail with `Vector()`.

- [ ] **Step 3: Implement**

`SleightOfHand.scala`:

- Change the import to `import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}`.
- Add under `val MinimumSecrets`:

```scala
  /** Its line when no player at the site holds two secrets. */
  val nobody: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No player could be robbed.")))
  override def noteKeys: Vector[NoteKey] = Vector(NoteSupport.took, nobody)
```

- Change `build` to:

```scala
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => steal(live, player, pending)),
    Note(id, stealNote(_, player, source)))))

  /** The question is asked whenever there is a target, so no answer means
    * nobody could be robbed. */
  private def stealNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      NoteSupport.answer(states, decisionId) match
        case Some(DecisionOptionRef.Player(target)) => for
            step <- states.previous
            amount = NoteSupport.secrets(step, actor)
            if amount > 0
          yield NoteSupport.took(card, NoteArg.Player(actor),
            NoteArg.Amount(amount, NoteUnit.Secret), NoteArg.Player(target))
        case _ => Some(nobody(card)))
```

`Whistle.scala`:

- Change the import to `import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}`.
- Add under `val decisionId`:

```scala
  val pulled: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Placed "),
    NotePart.Arg(0), NotePart.Text(" at "), NotePart.Arg(1),
    NotePart.Text(" and gave "), NotePart.Arg(0),
    NotePart.Text(" the Whistle's secret.")))
  /** Its line when no other player stands at another site. */
  val nobody: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No pawn could be pulled.")))
  override def noteKeys: Vector[NoteKey] = Vector(pulled, nobody)
```

- Change `build`'s relic case to add the note:

```scala
    case DecisionOptionRef.Relic(whistle) => Right(Sequence(Vector[Operation](
      Branch((state, _) => ask(state, player)),
      BuildOps((state, pending) => pull(state, player, whistle, pending)),
      Note(id, pullNote(_, source)))))
```

- Add:

```scala
  private def pullNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = PowerSourceRef.of(source).flatMap(card =>
    NoteSupport.answer(states, decisionId) match
      case Some(DecisionOptionRef.Player(target)) =>
        PawnMoves.pawnSite(states.now, target).toOption.map(site =>
          pulled(card, NoteArg.Player(target), NoteArg.Site(site)))
      case _ => Some(nobody(card)))
```

`Wolves.scala`:

- Change the import to `import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, PowerAnswers}`.
- Add under `val decisionId`:

```scala
  val killed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Text(" "), NotePart.Arg(1),
    NotePart.Plural(0, " warband.", " warbands.")))
  /** Its line when the chosen board had no warband: the kill is best-effort. */
  val spared: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" had no warband to kill.")))
  override def noteKeys: Vector[NoteKey] = Vector(killed, spared)
```

- Change `build` to:

```scala
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Decide(decisionId, player, DecisionQuery.ChooseOne(
      ready.game.current.players.map(p => DecisionOption.Player(
        DecisionOptionRef.Player(p.player))),
      heading = Some("Wolves: kill one warband on a player board"))),
    BuildOps((live, pending) => kill(live, pending)),
    Note(id, killNote(_, source)))))

  /** The warbands the chosen board lost in the kill step. */
  private def killNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    target <- NoteSupport.answer(states, decisionId).collect {
      case DecisionOptionRef.Player(board) => board }
    step <- states.previous
    lost = -NoteSupport.warbands(step, target)
  yield
    if lost > 0 then killed(card, NoteArg.Number(lost), NoteArg.Player(target))
    else spared(card, NoteArg.Player(target))
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/SleightOfHand.scala src/main/scala/oathdigital/gameplay/powers/action/Whistle.scala src/main/scala/oathdigital/gameplay/powers/action/Wolves.scala src/test/scala/oathdigital/gameplay/powers/action/SleightOfHandSuite.scala src/test/scala/oathdigital/gameplay/powers/action/WhistleSuite.scala src/test/scala/oathdigital/gameplay/powers/action/WolvesSuite.scala
git commit -m "feat(powers): targeting powers write what they did, or that nothing could"
```

---

### Task 6: The powers that place a pawn or take a card

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/PawnMoves.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/BrassHorse.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/MagicCarpet.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/banner/WanderingFlameMove.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/banner/WanderingFlamePlace.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala`
- Test:
  - `src/test/scala/oathdigital/gameplay/powers/action/BrassHorseSuite.scala`
  - `src/test/scala/oathdigital/gameplay/powers/action/MagicCarpetSuite.scala`
  - `src/test/scala/oathdigital/gameplay/powers/banner/WanderingFlameMoveSuite.scala`
  - `src/test/scala/oathdigital/gameplay/powers/banner/WanderingFlamePlaceSuite.scala`
  - `src/test/scala/oathdigital/gameplay/powers/wake/HornedMaskSuite.scala`

**Interfaces:**
- Consumes: `PowerSourceRef.of` for banners (Task 1), `NoteSupport.answer` and `secrets` (Task 2).
- Produces:
  - `PawnMoves.placedKey(name: String): NoteKey`: "{0} placed at {1}."
  - `PawnMoves.placedNote(key, source, player)(states): Option[PowerNote]`

- [ ] **Step 1: Write the failing tests**

Add `NoteText` to each suite's `oathdigital.gameplay.powers.{...}` import.

`BrassHorseSuite`:

```scala
  test("it writes the card it revealed in place of the generic line, then where the pawn went"):
    val done = use(cradleTopped(beastTop), BrassHorse.id, horse).toOption.get
    assertEquals(NoteText.said(new BrassHorse(catalog), done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} revealed ${beastTop.value}.",
        covers = true),
      NoteText.Said("placed", s"${actor.value} placed at ${deepWoods.value}.",
        covers = false)))

  test("an empty pile writes only where the pawn went"):
    val start = withDiscard(staged, Region.Cradle, Vector.empty)
    val parked = use(start, BrassHorse.id, horse).toOption.get
    val done = choose(parked.state, BrassHorse.decisionId, site(dunes)).toOption.get
    assertEquals(NoteText.said(new BrassHorse(catalog), parked.events ++ done.events),
      Vector(NoteText.Said("placed", s"${actor.value} placed at ${dunes.value}.",
        covers = false)))
```

`MagicCarpetSuite`:

```scala
  test("it writes where the pawn went, then that it was discarded"):
    val (first, placed) = placedAt(staged, deepWoods)
    val done = choose(placed.state, MagicCarpet.fateDecisionId,
      MagicCarpet.discard).toOption.get
    assertEquals(NoteText.said(MagicCarpet,
      first.events ++ placed.events ++ done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} placed at ${deepWoods.value}.",
        covers = false),
      NoteText.Said("discarded", "Discarded.", covers = false)))

  test("a Carpet given away is written as given"):
    val (_, placed) = placedAt(staged, deepWoods)
    val done = choose(placed.state, MagicCarpet.fateDecisionId,
      DecisionOptionRef.Player(p3)).toOption.get
    assertEquals(NoteText.said(MagicCarpet, done.events), Vector(
      NoteText.Said("given", s"Given to ${p3.value}.", covers = false)))
```

`WanderingFlameMoveSuite`:

```scala
  test("it writes where the pawn went"):
    val done = use(staged(brokenPeaks), power, darkestSecret).toOption.get
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} placed at ${brokenPeaks.value}.",
      covers = false)))
```

`WanderingFlamePlaceSuite`:

```scala
  test("it writes the site it placed a secret at"):
    val done = use(staged(faceUp = 2), power, darkestSecret).toOption.get
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} placed a secret at ${ancientCity.value}.",
      covers = false)))
```

`HornedMaskSuite`:

```scala
  test("it writes the denizen it took"):
    val t = use(atHome(staged, inn), power, source).toOption.get
    val done = answer(t, actor, HornedMask.denizenDecisionId, choose(inn))
      .toOption.get
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} took ${inn.value} as a facedown adviser.",
      covers = false)))

  test("after a discard question the take is written apart from its action line"):
    val t = use(holding(elders, fresh, wolves), power, source).toOption.get
    val asked = answer(t, actor, HornedMask.denizenDecisionId, choose(inn))
      .toOption.get
    val done = answer(asked, actor, HornedMask.discardDecisionId,
      choose(elders)).toOption.get
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "taken", s"${actor.value} took ${inn.value} as a facedown adviser.",
      covers = false)))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.BrassHorseSuite oathdigital.gameplay.powers.action.MagicCarpetSuite oathdigital.gameplay.powers.banner.* oathdigital.gameplay.powers.wake.HornedMaskSuite"`
Expected: the new tests fail with `Vector()`.

- [ ] **Step 3: Implement**

`PawnMoves.scala`, add to the object:

```scala
  /** "{player} placed at {site}.": a pawn a power places does not travel or
    * move. */
  def placedKey(name: String): NoteKey = NoteKey(name, Vector(NotePart.Arg(0),
    NotePart.Text(" placed at "), NotePart.Arg(1), NotePart.Text(".")))

  /** Where `player`'s pawn stands after the power placed it. */
  def placedNote(key: NoteKey, source: DecisionOptionRef, player: PlayerId)(
      states: NoteStates): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    site <- PowerAccess.pawnSite(states.now, player)
  yield key(card, NoteArg.Player(player), NoteArg.Site(site))
```

`BrassHorse.scala`:

- In the class, add `override def noteKeys: Vector[NoteKey] = Vector(revealed, placed)`.
- Change `build` to:

```scala
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((state, _) => reveal(state, player)),
    Note(this.id, revealNote(_, player, source), covers = true),
    Branch((state, _) => ask(state, player)),
    BuildOps((state, pending) => place(state, player, pending)),
    Note(this.id, PawnMoves.placedNote(placed, source, player)))))

  /** The reveal changes nothing, so the pile's top card is still the one it
    * revealed. */
  private def revealNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    found <- region(states.now, player).toOption
    shown <- top(states.now, found)
  yield revealed(card, NoteArg.Player(player), NoteArg.Card(shown))
```

- In the companion, add:

```scala
  /** Its own line: the reveal is its first effect, so the line sits there,
    * in the command that starts it. The placement may follow a question in
    * a later command, so it writes a line of its own. */
  val revealed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" revealed "), NotePart.Arg(1), NotePart.Text(".")))
  val placed: NoteKey = PawnMoves.placedKey("placed")
```

`MagicCarpet.scala`:

- Change the imports to `import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts}`. Keep `import oathdigital.gameplay.powers.PowerAnswers`, or merge all three into one.
- Add under `val discard`:

```scala
  val placed: NoteKey = PawnMoves.placedKey(NoteKey.Used)
  val givenAway: NoteKey = NoteKey("given", Vector(NotePart.Text("Given to "),
    NotePart.Arg(0), NotePart.Text(".")))
  val discarded: NoteKey = NoteKey("discarded", Vector(
    NotePart.Text("Discarded.")))
  override def noteKeys: Vector[NoteKey] = Vector(placed, givenAway, discarded)
```

- Change `build`'s relic case to:

```scala
    case DecisionOptionRef.Relic(carpet) => Right(Sequence(Vector[Operation](
      PawnMoves.siteChoice(siteDecisionId, player,
        ready.game.current.map.inPlay,
        "Magic Carpet: choose the site to place your pawn at"),
      BuildOps((state, pending) => PawnMoves.chosenSite(pending,
        siteDecisionId).flatMap(PawnMoves.relocate(state, player, _))),
      Note(id, PawnMoves.placedNote(placed, source, player)),
      Branch((state, _) => ask(state, player)),
      BuildOps((state, pending) => settle(state, player, carpet, pending)),
      Note(id, fateNote(_, source)))))
```

- Add:

```scala
  /** The fate question is not asked when nobody may take the Carpet, and it
    * is then discarded. */
  private def fateNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = PowerSourceRef.of(source).map(card =>
    NoteSupport.answer(states, fateDecisionId) match
      case Some(DecisionOptionRef.Player(taker)) =>
        givenAway(card, NoteArg.Player(taker))
      case _ => discarded(card))
```

`WanderingFlameMove.scala`:

- Add under `val decisionId`:

```scala
  val placed: NoteKey = PawnMoves.placedKey(NoteKey.Used)
  override def noteKeys: Vector[NoteKey] = Vector(placed)
```

- Change `build` to:

```scala
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((state, _) => ask(state, player)),
    BuildOps((state, pending) => move(state, player, pending)),
    Note(id, PawnMoves.placedNote(placed, source, player)))))
```

`WanderingFlamePlace.scala`:

- Change the imports to `import oathdigital.gameplay.powers.NoteSupport` and `import oathdigital.gameplay.powers.action.{PaidAction, PawnMoves}`.
- Add:

```scala
  /** It moves one secret or none, so the line names no amount. */
  val placed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" placed a secret at "), NotePart.Arg(1),
    NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = Vector(placed)
```

- Change `build` to:

```scala
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((state, _) => place(state, player)),
    Note(id, placeNote(_, player, source)))))

  private def placeNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    if NoteSupport.secrets(step, player) < 0
    site <- PawnMoves.pawnSite(states.now, player).toOption
  yield placed(card, NoteArg.Player(player), NoteArg.Site(site))
```

`HornedMask.scala`:

- Change the import to `import oathdigital.gameplay.powers.{AdviserLimit, NoteSupport, PlayerFacts, PowerAnswers}`.
- In the class, add `override def noteKeys: Vector[NoteKey] = Vector(took, taken)` under `def timing`.
- Change `build` to:

```scala
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askDenizen(live, player)),
    Branch((live, pending) => askDiscard(live, player, pending)),
    BuildOps((live, pending) => take(live, player, pending),
      restrictions = (_, _) => Vector(
        new DiscardRestrictions(catalog, player))),
    Note(this.id, takeNote(_, player, source)))))

  /** After a discard question the take lands in a later command than the
    * power's first effect, where "Used Horned Mask" has already posted, so
    * the line is then `taken`, a trigger. */
  private def takeNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    denizen <- NoteSupport.answer(states, denizenDecisionId).collect {
      case DecisionOptionRef.Denizen(chosen) => chosen }
    if advisers(states.now, actor).exists(_.id == denizen)
  yield
    val key =
      if NoteSupport.answer(states, discardDecisionId).isDefined then taken
      else took
    key(card, NoteArg.Player(actor), NoteArg.Card(denizen))
```

- In the companion, add:

```scala
  val took: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1),
    NotePart.Text(" as a facedown adviser.")))
  val taken: NoteKey = took.copy(name = "taken")
```

- [ ] **Step 4: Run the tests to verify they pass**

Run the Step 2 command, then `./sbtw "testOnly oathdigital.gameplay.powers.* oathdigital.application.gamelog.*"`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/PawnMoves.scala src/main/scala/oathdigital/gameplay/powers/action/BrassHorse.scala src/main/scala/oathdigital/gameplay/powers/action/MagicCarpet.scala src/main/scala/oathdigital/gameplay/powers/banner/WanderingFlameMove.scala src/main/scala/oathdigital/gameplay/powers/banner/WanderingFlamePlace.scala src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala src/test/scala/oathdigital/gameplay/powers/action/BrassHorseSuite.scala src/test/scala/oathdigital/gameplay/powers/action/MagicCarpetSuite.scala src/test/scala/oathdigital/gameplay/powers/banner/WanderingFlameMoveSuite.scala src/test/scala/oathdigital/gameplay/powers/banner/WanderingFlamePlaceSuite.scala src/test/scala/oathdigital/gameplay/powers/wake/HornedMaskSuite.scala
git commit -m "feat(powers): placements and takes write their own lines"
```

### Checkpoint B

Dispatch a review subagent on Sonnet or lower. Point it at Tasks 3 to 6, the spec's phase power table, and this plan's decisions 1 to 8. Ask it to check, for each of the 21 powers:

- Is its `used` note in the same command as its first effect? Brass Horse and Horned Mask are the planned exceptions.
- Does each covering note follow the step whose generic line it replaces, and only that step?
- Does no note read a printed amount?
- Does each `noteKeys` hold every key the power builds, and a key named `used`?

Fix what it finds before Task 7.

---

### Task 7: Every phase power has a line, shown through a real log

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/powers/PowerNoteCatalogSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (the `wolves` script, added to `named`)
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`
- Create: `src/test/resources/gamelog/wolves.actor.log`, `src/test/resources/gamelog/wolves.other.log`

**Interfaces:**
- Consumes: every phase power's `noteKeys` (Tasks 2 to 6).
- Produces: `LogScripts.wolves: Script`

- [ ] **Step 1: Write the tests**

In `PowerNoteCatalogSuite.scala`, add:

```scala
  test("every phase power declares its own used line"):
    PhasePowerCatalog.default(catalog).powers.foreach(power =>
      assert(power.noteKeys.exists(_.name == NoteKey.Used), power.id.value))
```

In `LogScripts.scala`:

- Import `Wolves` beside `GamblingHall`: `import oathdigital.gameplay.powers.action.{GamblingHall, Wolves}`.
- Add below `gamblingHall`:

```scala
  /** Wolves at the actor's site, used in Act with a secret arranged. The
    * board question parks; the answer kills one of the other player's
    * warbands with the most of them. */
  def wolves(using munit.Location): Script =
    val card = DenizenId("39")
    val (chronicle, orders) = ParkedServiceFixture.withWorldDeckTop(
      FirstGameSetupFixture.chronicle, FirstGameSetupFixture.orders, Vector(card))
    val (service, _, driver) = journaled("wolves")
    val woken = Situation.wake(driver, chronicle, orders)
    val actor = active(woken)
    val victim = woken.ready.game.current.players.filter(_.player != actor)
      .maxBy(_.board.warbands).player
    woken.withAnswers {
      case park if park.decisionId == Wolves.decisionId =>
        ChooseOneAnswer(DecisionOptionRef.Player(victim))
    }.after(Step.Arrange(Vector(
        ParkedServiceFixture.topOfWorldDeck(card, Location.Site(pawn(woken, actor))),
        Move(Piece.Secrets(1), PositionedLocation(Location.SharedBank),
          PositionedLocation(Location.PlayArea(actor))))),
      GameCommand.EndWake(actor),
      GameCommand.UsePower(actor, Wolves.id, DecisionOptionRef.Denizen(card)))
    Script("wolves", service, actor)
```

- Append `"wolves" -> (() => wolves)` to `named`, after `"gambling-hall"`.

In `GameLogPowerLinesSuite.scala`, add:

```scala
  test("Wolves writes its kill as the action line, after the choice it answers"):
    val script = wolves
    val entries = format(script, None).filter(_.depth == 1)
    val all = texts(entries)
    val kill = all.indexWhere(_.startsWith("Wolves: Killed 1 "))
    assert(kill > 0, all)
    assert(all(kill - 1).startsWith("Chose "), all)
    assert(all(kill).endsWith(" warband."), all(kill))
    assert(!all.exists(_.startsWith("Used ")), all)
    assertEquals(entries(kill).kind, LogKind.Action)
```

- [ ] **Step 2: Run the catalog test**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.PowerNoteCatalogSuite"`
Expected: PASS. Tasks 2 to 6 gave every phase power a `used` key. If a power fails, its task missed it.

- [ ] **Step 3: Write and review the Wolves golden logs**

Run: `GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`
Then run: `git status --short src/test/resources/gamelog`
Expected: only the two new `wolves.*.log` files. Read both. After the turn headline, the actor's log should hold:

- the arranged "Gained 1 secret" line;
- no line for the cost or the park;
- a `decision` line "Chose {victim}";
- an `action` line "[card:39|Wolves]: Killed 1 [player:…] warband.".

The other seat's log should show the same lines.

- [ ] **Step 4: Run the log suites**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS. `GameLogPropertiesSuite` now also holds the Wolves script to prefix stability, identical keys and no leak.

- [ ] **Step 5: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/powers/PowerNoteCatalogSuite.scala src/test/scala/oathdigital/application/gamelog/LogScripts.scala src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala src/test/resources/gamelog/wolves.actor.log src/test/resources/gamelog/wolves.other.log
git commit -m "test(log): every phase power has its line; Wolves through a real log"
```

---

### Task 8: Record the slice and run the gates

**Files:**
- Modify: `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Amend the spec**

Add a line under the slice 1 status line:

```markdown
**Slice 2 (2026-09-26):** every phase power's own line. "Settled in slice 2"
records what it decided.
```

In section 2's phase power table, replace these rows:

```markdown
| Ivory Eye | Ivory Eye: {Red} peeked at {card} in {Blue}'s advisers. | the Peeked line |
| Brass Horse | Brass Horse: {Red} revealed {card}. | the Revealed line |
| Brass Horse, key `placed` | Brass Horse: {Red} placed at {site}. | |
| Wandering Flame (move) | Darkest Secret: {Red} placed at {site}. | |
| Wandering Flame (place) | Darkest Secret: {Red} placed a secret at {site}. | |
| Horned Mask | Horned Mask: {Red} took {card} as a facedown adviser. | |
| Horned Mask, key `taken`, after a discard question | Horned Mask: {Red} took {card} as a facedown adviser. | |
```

Add this section after "Settled in slice 1":

```markdown
## Settled in slice 2

The second slice settled these:

- A row the table marks as another outcome of a power's `used` line, such
  as "Wolves, no warband", is a variant named `used.{variant}`: `used.none`,
  `used.empty`, `used.away`. A variant also replaces "Used {card}" and is its
  action's line. Every phase power declares a key named exactly `used`,
  which a catalog test checks.
- Brass Horse's reveal is its first effect, and its destination question can
  park. Its `used` line is therefore the reveal, and the placement is a
  `placed` line of its own. With an empty pile, "Used Brass Horse" stays.
- Horned Mask's take lands after "Used Horned Mask" has posted when the
  adviser area was full and a discard question parked. Its line then uses
  the key `taken`, a trigger with the same sentence.
- A note's cards, its source included, are judged at its action's start, at
  the step it restates, and after it. A viewer who identified a card at any
  of them reads its name. Magic Waterskin's source is buried by the time its
  line posts, and still reads as Magic Waterskin.
- Ivory Eye names the adviser apart from its owner, so a viewer who may not
  identify it reads "a Denizen in Blue's advisers".
- A banner's power names its banner, as "Used Darkest Secret" does:
  "Darkest Secret: Red placed at {site}."
- Magic Waterskin's Buried line posts before its own line, since the bury
  comes first on the card.
- The reads a note needs live in `NoteSupport`. Shared sentences live beside
  the helpers of their family: `RollResults.rolled`, `RelicDraws.drew`,
  `PawnMoves.placedKey`, `NoteSupport.took` and `NoteSupport.gainedKey`.
```

In `docs/ROADMAP.md`, replace the paragraph starting "Slice 1 merged on 2026-09-26" with:

```markdown
Slice 1 merged on 2026-09-26: the mechanism, with Vow of Peace and Gambling
Hall as its first powers. Slice 2 gives every phase power its own line.
Slices 3 to 5 remain: removed and hidden options, added effects and altered
procedures, then setup.
```

- [ ] **Step 2: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: all pass. The server count is your baseline plus the new tests.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both clean.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-09-26-power-log-lines-design.md docs/ROADMAP.md
git commit -m "docs: record what power log lines slice 2 settled"
```

### Checkpoint C

Dispatch a final review subagent on Sonnet or lower over the whole branch diff, the spec and this plan. Ask it:

- to confirm that every row of the spec's phase power table, as amended, has a test asserting its exact sentence;
- to confirm that the Global Constraints hold;
- to read the `use-power` and `wolves` golden logs as a player would.

Fix what it finds, then run the gates once more.
