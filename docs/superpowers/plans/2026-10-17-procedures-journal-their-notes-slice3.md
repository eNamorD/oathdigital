# Procedures Journal Their Own Notes — Slice 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the four harder procedures — Trade, Recover, the played
adviser and Search — so each journals its own Game Log line, and delete the
six inference rules in `ActionLines` that guessed those lines from recorded
operations.

**Architecture:** Each procedure declares its own `NoteSet` beside itself in
`gameplay`, puts a `Note` leaf in the tree where the effect happens, and its
`ProcedureLog` entry becomes `ActionLineSource.Notes`. Slices 1 and 2 built
and proved that mechanism on thirteen procedures. These four are the ones
where the formatter guesses, and three of the four guesses simply disappear.
The fourth does not: **Search's line cannot be one note**, and this slice
splits it in two and moves three phrases into the powers that cause them.
Nothing in the mechanism changes.

**Tech Stack:** Scala 3.9.0 on the JVM, munit, ujson, built through `./sbtw`.

**Spec:** [docs/superpowers/specs/2026-10-16-procedures-journal-their-notes-design.md](../specs/2026-10-16-procedures-journal-their-notes-design.md)

**Slice 1 plan:** [2026-10-16-procedures-journal-their-notes-slice1.md](2026-10-16-procedures-journal-their-notes-slice1.md)
**Slice 2 plan:** [2026-10-17-procedures-journal-their-notes-slice2.md](2026-10-17-procedures-journal-their-notes-slice2.md)

**Scope:** Slice 3 of four. Slice 4 (Campaign, then the deletion) gets its own
plan and is still blocked on the three gaps the design records under "Two
things slice 4 must settle first". `ActionLines` is not deleted here: this
slice empties it of its last five procedure rules and leaves `campaignLines`
and `usePowerLines`.

---

## Why Search is different, and what this slice does about it

Trade, Recover and the played adviser each hold their facts where they act,
exactly as slice 2's twelve did. Search does not. Its one line,

```
Drew Threatening Roar, Fae Merchant and Second Chance from the World Deck and kept Threatening Roar
```

needs three facts that never coexist anywhere a note can read them:

1. **The kept set** lives in `answered`, which is action-scoped and survives
   the park.
2. **The drawn set** lives in the temporary hand, which is state.
3. **The `from {pile}` phrase** lives in the `Draw` operation's `source` and
   `position` **as powers rewrote them** — `WineOfWelcome` and `Mushrooms`
   make it `the bottom of …`, `PaintedTrumpet` makes it `each discard pile`.
   A note cannot read an operation.

The keep answer arrives in a **different walker command** than the draw, so
`NoteStates.previous` is long gone by then and only `states.now` survives.
`SpinningBee`'s no-match draw discards the whole hand *inside*
`PowerWindow.SearchBeforeDraw`, so even `states.now` is empty there. The rule
works solely because it reads the journal, which `NoteStates` deliberately
does not expose.

Four plan-time rulings settle it. They are the user's decisions, recorded here
because the design did not anticipate them.

- **Ruling 1 — the line splits.** `Drew {cards} from {pile}` posts at the
  draw, where the cards and the pile are live; `Kept {cards}` posts at the
  keep answer. Both at `LogKind.Action`, depth 1, in the Search section. This
  is a deliberate exception to the design's ruling 6: the goldens
  `search.{actor,other}` and `augury.{actor,other}` change, in Task 4 and
  nowhere else, and every other task in this slice stays byte-identical.
- **Ruling 2 — the played adviser's keys are owned by
  `ActionRef.PlayFacedownAdviser`.** Card play is an embedded subtree, not a
  `ProcedureRef`, so it has no registry entry of its own and no kind. The
  keys are declared in `CardPlayProcedure` and written under that one id
  wherever a card play runs — a Search, the facedown-adviser action, or a
  power's own play (Oracle). All three enclosures then resolve the kind from
  that single entry, which is `Action`, which is what all three post at
  today. The id is never rendered; only `declared-keys.txt` carries it.
- **Ruling 3 — Trade's empty-bank line is corrected.** `ActionLines.tradeLines`
  hardcodes the literal `"no secrets"`, so a Trade **for favor** whose suit
  bank is empty prints `… for no secrets` today. Nothing covers the branch.
  The note writes `… for no favor`, and the ledger records the correction.
- **Ruling 4 — a Trade posts one line.** `tradeLines` matches every
  bank-to-player move in the formatted event, so a Trade that a power gains
  again on (`TheOldOak`'s bonus secret, `MountedLibrary`'s doubling) appears
  to post a second `Traded with …`. One line per Trade, naming what the Trade
  itself gained, is the correct reading. Task 1 probes today's behaviour
  first and records what it found.
- **Ruling 5 — the three variant phrases move into the powers that cause
  them.** `WineOfWelcome`, `Mushrooms` and `PaintedTrumpet` each gain a line
  of their own, before the draw, the way `SpinningBee` already has one;
  `SpinningBee` gains a second line for its no-match draw, which is the only
  case where Search's own two notes write nothing. Each of those three card
  docs currently ends *"It writes no line: … the Search line says the cards
  came from the bottom"* — that sentence was true while the line was a rule
  that could read the `Draw`. Task 4 amends all three.

---

## Global Constraints

- **Scala 3.9.0 with `-Werror`** and `-Wunused:imports,privates,locals,implicits,nowarn`
  (`build.sbt:71-77`). An unused import **or an unused private method** fails
  the build. Deleting a rule from `ActionLines.scala` usually orphans an
  import and sometimes a private helper; remove both in the same step.
  `-Wunused` does **not** see an unused *public* member, so the companion
  helpers that lose their last caller survive compilation and are deleted by
  hand in Task 5.
- **800-line production-file cap**, enforced by `scripts/check-architecture.py`
  and `BackendArchitectureSuite`. The four `gameplay` files grow:
  `CardPlayProcedure` 414 → about 475, `SearchProcedure` 241 → about 290,
  `RecoverProcedure` 249 → about 285, `TradeProcedure` 58 → about 100. None is
  within 300 lines of the cap. `ActionLines` only shrinks.
- **`gameplay` may not import `application`, `persistence`, `presentation`,
  `protocol`, `serialization` or `server`** (`scripts/check-architecture.py`).
  `model` is free. This is why a procedure's wording is `NoteKey` /
  `NotePart` / `NoteArg` and never `LogSpan` or `LogKind`.
- **Golden logs: the rendered line never changes, the sequence may — except
  in Task 4.** Run
  `./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`.
  Tasks 1, 2 and 3 move their lines onto the `PowerNoted` event, so the
  entry's `sequence` and `ordinal` shift and nothing else may. Set
  `GAMELOG_GOLDEN=write` only in a migration task and read the `git diff`
  line by line: a changed span, a changed `kind`, a changed depth, a changed
  section, a line that moved relative to another line, or a shift on a line
  the task did not touch is a defect, not a rewrite. Task 4 is the one place
  a span changes, and its step says exactly which four files and which lines.
- **No new typed spans.** `GoldenLog.render` prints `LogSpan.Text` as its bare
  string and `LogSpan.Amount` as `[amount:2 warbands]`. Every count in these
  lines is plain text today, so every count is `NoteArg.Number` plus
  `NotePart.Plural`, **never** `NoteArg.Amount`. Several adjacent `Text` spans
  concatenate to the same golden string, so splitting a sentence across parts
  is safe; changing a span's *kind* is not.
- **`NoteArg.Cards` renders through `LogWords.listed`, which switches to a
  `LogSpan.Cards` span above `LogWords.Inline` (5) cards**, where the deleted
  rule used `LogWords.cards` unconditionally. Every card list in Tasks 1 to 3
  is one card. Search's is not bounded below six: 3 printed + `Augury.More`
  (1) + `TruthfulHarp.More` (2) reaches six, and a six-card draw renders as a
  `Cards` span where the rule wrote the inline phrase. This is a real
  divergence on a branch nothing covers; Task 4 records it in the ledger and
  pins the shape with a unit test rather than pretending it cannot happen.
- **A note's cards are judged at a wider state set than the rule's were.**
  `LogWords.card(id, before, after, viewer)` *is*
  `seen(Vector(before, after), viewer)`, and a note's `seen` is the action's
  start, the restated step's before-state, and the state after the note. The
  note set is wider in time, so it can only name a card the rule left as a
  back, never the reverse. The `.other.log` goldens prove the covered paths:
  `search.other.log` must still read `3 Denizens`, not three names.
- **`NoteStates.previous` is the last *leaf* the command ran, and a `Note` is
  not one.** This is the slice's one recurring defect class and it has already
  caused two regressions (slice 2: Travel, Setup). Before placing any note in
  this slice, answer both questions in a comment beside it:
  1. *Does this note read `previous`?* If it does, name every power that can
     append a leaf into the same window between the step it means to read and
     the note. Prefer reading `states.now` and `states.answered`, which no
     appended leaf can move. Trade is the only note in this slice that reads
     `previous`, and `EconomyTree` already documents why.
  2. *Is this note inside the windowed node, or a sibling after it?*
     `WalkerPowerGather.applyWindow` calls a contributor with
     `ops = Vector(leaf)` for a windowed **leaf** and replaces that leaf in
     its parent, so a power's appended operations run **before** any sibling
     that follows. A line that must precede a power's line therefore belongs
     *inside* the windowed node. Recover's `RecoverAfterRelic` is exactly this
     case and Task 2 hangs the window on a `Sequence`, as slice 2's Setup fix
     did.
- **`Told.by` names a key only when exactly one key tells that decision.**
  Where a line has several variants — Trade's four, the played adviser's five,
  Search's two draw keys — no single key tells the decision, and naming one
  would be false. Leave `by` unset there with a one-line comment;
  `GameLogPropertiesSuite` checks only that a named key is one the procedure
  declares.
- **`src/test/resources/notes/declared-keys.txt` is expected to change** in
  every task. Rewrite it with
  `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`
  and read the diff: it must add exactly the task's keys and change nothing
  else. The slice adds **18** keys in all: 4 Trade, 2 Recover, 5 card play,
  3 Search, 4 power lines.
- **`GameLogPropertiesSuite`'s `noting` assertion grows by one procedure per
  migration task**, in `ProcedureRef.all` order — which is `ActionRef.all`
  then `PhaseTransitionRef.all` then `TriggeredProcedureRef.all`. It reads

  ```scala
      Vector("forge", "travel", "take-wealth",
        "muster", "challenge", "place-banner-resource", "negotiation",
        "peek-site-relics", "reveal-owned-relic", "move-warbands",
        "finish-rest", "oathkeeper", "setup")
  ```

  today, and must read

  ```scala
      Vector("search", "play-facedown-adviser", "recover", "forge", "travel",
        "take-wealth", "muster", "trade", "challenge",
        "place-banner-resource", "negotiation", "peek-site-relics",
        "reveal-owned-relic", "move-warbands", "finish-rest", "oathkeeper",
        "setup")
  ```

  when the slice is done. `Search`, `PlayFacedownAdviser` and `Recover` are the
  first three entries of `ActionRef.all`, so each task inserts at the front
  rather than appending.
- **`BackendArchitectureSuite`: a test outside the catalog suite names a
  catalog card through `CatalogNames`**, never as `DenizenId("179")` or
  `RelicId("R01")`. `CatalogNames` is at
  `src/test/scala/oathdigital/testkit/Table.scala:14` and offers `site`,
  `denizen`, `worldCard`, `relic` and `edifice`.
- **A `Note` holds a closure, so case-class equality fails on it.** Any
  whole-tree `assertEquals` over a tree that gains a `Note` must become a
  shape match. `TakeWealthProcedureSuite` is the precedent.
- **Every line number in this plan is as of the slice's first commit.** Each
  task deletes a block from `ActionLines.scala` and edits
  `ProcedureLog.scala`, so the numbers below a deletion drift. **Find the
  named method, never the line**, and delete a named block by matching its
  exact text — slice 2 corrupted `ActionLines.scala` twice by slicing on
  computed indices.
- **Worktree setup.** A fresh worktree has no `.tooling`. Symlink the main
  checkout's `.tooling` (and `node_modules`, if absent) into it before the
  first `./sbtw`.

### The full gate

Run before every commit:

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
```

### The slice's own proof

At the slice's first commit and again at its tip, render every golden with the
leading `sequence.ordinal` and the section's `@index` stripped, and diff the
two snapshots. Tasks 1 to 3 must contribute **no** change. Task 4's change must
be exactly the four Search goldens, one line becoming two in each. Slice 2 ran
this and it is what caught both of its regressions.

---

### Task 1: Trade journals its own line

Trade is the easiest of the four: `EconomyTree.Kind` already carries a `note`
parameter that `MusterProcedure` fills, and the leaf is already placed last in
the gain window. Only the words and the reading are new.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/economy/TradeProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala`
  (the `ActionRef.Trade` arm)
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`
  (delete `tradeLines`, `traded`, `source`)
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/ProcedureNoteLinesSuite.scala`
- Modify: `docs/superpowers/specs/2026-10-16-game-log-wording-ledger.md`
- Rewrite: `src/test/resources/gamelog/trade.{actor,other}.log`
- Rewrite: `src/test/resources/notes/declared-keys.txt`

**Interfaces:**
- Consumes: `ProcedureNotes.id`, `ActionLineSource.Notes`,
  `EconomyTree.Kind.note` (slices 1 and 2);
  `NoteSupport.favor`, `NoteSupport.secrets`;
  `MusterSource(card: CardId, suit: Suit, origin: RuleSourceRef)`.
- Produces: `TradeProcedure.tradedForFavor`, `TradeProcedure.tradedForNoFavor`,
  `TradeProcedure.tradedForSecrets`, `TradeProcedure.tradedForNoSecrets`, all
  `NoteKey`; `TradeProcedure.noteKeys: Vector[NoteKey]`.

- [ ] **Step 1: Probe ruling 4's premise before changing anything**

Copy `LogScripts.trade` (`LogScripts.scala:367-376`) into a throwaway suite
under `src/test/scala/oathdigital/application/gamelog/`, change its table so
The Old Oak is the Trade's source — it is a denizen, so place it at the pawn's
site the way `LogScripts.source` places the existing one — and count the lines
the **current rule** posts. Run it, read the number, then delete the suite.

```scala
package oathdigital.application.gamelog

import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.powers.economy.TheOldOak
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, MenuPlay, Table}
import oathdigital.testkit.Table.p1
import LogScripts._

/** PROBE for slice 3 plan-time ruling 4. Delete once the count is in Task
  * 1's commit message. */
class TradeBonusProbeSuite extends munit.FunSuite:
  test("how many Traded-with lines a bonus gain posts today"):
    val (service, act) = atTable("old-oak-probe", Table.start
      .pawn(p1, "Ancient City")
      .denizen("The Old Oak", "Ancient City")
      .favor(p1, 2).supply(p1, 7).banditsRefilled)
    act.withAnswers(ModifierPlay.answers(
      PowerWindow.TradeModifierSelection, Vector(TheOldOak.id)))
      .after(MenuPlay.start(p1, ActionRef.Trade,
        DecisionOptionRef.Button("secret")))
    val all = texts(format(Script("old-oak-probe", service, p1), Some(p1))
      .filter(_.depth == 1))
    println(all.filter(_.startsWith("Traded with ")))
```

`MenuPlay.start`'s start-selection parameter and `Table`'s `favor` helper may
be spelled differently — read `MenuPlay` and `Table` and match them; this is a
probe, so the shortest thing that runs is right. A Trade for secrets gains
only with a matching adviser, so if the run gains nothing, give p1 an adviser
of The Old Oak's suit before trading, or the probe measures the fallback
instead of the double line.

Record the count in Task 1's commit message either way. If it is **two**,
ruling 4 applies as written and the note's single line is the intended change.
If it is **one**, say so: ruling 4 is moot and the ledger entry in Step 7 says
"no change" instead.

- [ ] **Step 2: Declare Trade's four keys**

In `TradeProcedure.scala`, add the imports:

```scala
import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.gameplay.powers.NoteSupport
```

and, after `val decisionId: String = "trade.source"`:

```scala
  private val notes = NoteSet(ProcedureNotes.id(ActionRef.Trade))
  /** "Traded with Hiding Place for 3 favor". The count is a `Number` and
    * not an `Amount`, so it renders as the plain text the rule wrote. */
  val tradedForFavor: NoteKey = notes.key("traded-favor", Vector(
    NotePart.Text("Traded with "), NotePart.Arg(0), NotePart.Text(" for "),
    NotePart.Arg(1), NotePart.Text(" favor")))
  /** "Traded with Hiding Place for no favor": the suit's bank was empty, or
    * a power removed the gain. The rule this replaced wrote "for no
    * secrets" here whichever resource the Trade was for, which was wrong
    * and which no test covered (slice 3 plan-time ruling 3). */
  val tradedForNoFavor: NoteKey = notes.key("traded-no-favor", Vector(
    NotePart.Text("Traded with "), NotePart.Arg(0),
    NotePart.Text(" for no favor")))
  /** "Traded with Hiding Place for 2 secrets". */
  val tradedForSecrets: NoteKey = notes.key("traded-secrets", Vector(
    NotePart.Text("Traded with "), NotePart.Arg(0), NotePart.Text(" for "),
    NotePart.Arg(1), NotePart.Text(" "),
    NotePart.Plural(1, "secret", "secrets")))
  /** "Traded with Hiding Place for no secrets": no adviser matched, so the
    * Trade requested no gain at all. */
  val tradedForNoSecrets: NoteKey = notes.key("traded-no-secrets", Vector(
    NotePart.Text("Traded with "), NotePart.Arg(0),
    NotePart.Text(" for no secrets")))
  def noteKeys: Vector[NoteKey] = notes.declared
```

- [ ] **Step 3: Build the note and hand it to the tree**

`kind` currently takes only the resource and must also take the actor, the way
`MusterProcedure.kind(actor)` does. Replace `kind` and add the builder:

```scala
  private def kind(resource: TradeResource, actor: PlayerId)
      : EconomyTree.Kind = resource match
    case TradeResource.Favor => EconomyTree.Kind(decisionId,
      "Choose a card to Trade for favor", PowerWindow.TradeModifierSelection,
      "Start Trade", PowerWindow.TradeActionEligibility,
      PowerWindow.TradeSourceSelection, PowerWindow.TradeCost,
      PowerWindow.TradeGain, Cost(secret = 1),
      (player, source, matching, _) =>
        Some(Gain.Favor(player, source.suit, 1 + matching)),
      selection = selection(resource),
      note = Some((ProcedureNotes.id(ActionRef.Trade),
        tradedNote(resource, actor, _, _))))
    case TradeResource.Secret => EconomyTree.Kind(decisionId,
      "Choose a card to Trade for secrets", PowerWindow.TradeModifierSelection,
      "Start Trade", PowerWindow.TradeActionEligibility,
      PowerWindow.TradeSourceSelection, PowerWindow.TradeCost,
      PowerWindow.TradeGain, Cost(favor = 1, favorBurnt = 1),
      (player, _, matching, _) =>
        Option.when(matching > 0)(Gain.Secrets(player, matching)),
      selection = selection(resource),
      note = Some((ProcedureNotes.id(ActionRef.Trade),
        tradedNote(resource, actor, _, _))))

  /** What the Trade's own gain put in the actor's play area, read as the
    * difference across the step before the note rather than as the
    * requested amount: a gain the bank clamped reads what arrived, and a
    * gain a power removed reads zero.
    *
    * `EconomyTree.afterSource` places this leaf last in the gain window, so
    * `previous` is the gain. Every `TradeGain` power appends — The Old Oak's
    * bonus secret, Mounted Library's doubling, Secret Signal's extra favor —
    * so each lands after this note and the note still reads the Trade's own
    * gain. A power that prepended would make the note read another
    * operation's delta, exactly as `EconomyTree` says of Muster.
    *
    * A Trade for secrets with no matching adviser requests no gain at all,
    * so the window holds only this note and `previous` is the cost: the
    * difference is zero and the line is "for no secrets", which is what the
    * rule wrote at completion.
    */
  private def tradedNote(resource: TradeResource, actor: PlayerId,
      states: NoteStates, source: MusterSource): Option[PowerNote] =
    val gained = states.previous.fold(0)(step => resource match
      case TradeResource.Favor => NoteSupport.favor(step, actor)
      case TradeResource.Secret => NoteSupport.secrets(step, actor))
    val card = NoteArg.Card(source.card)
    Some(resource match
      case TradeResource.Favor =>
        if gained > 0 then
          tradedForFavor.sourceless(card, NoteArg.Number(gained))
        else tradedForNoFavor.sourceless(card)
      case TradeResource.Secret =>
        if gained > 0 then
          tradedForSecrets.sourceless(card, NoteArg.Number(gained))
        else tradedForNoSecrets.sourceless(card))
```

Then thread the actor through both callers:

```scala
  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    resourceOf(args).flatMap(resource =>
      EconomyTree.build(catalog, state, actor, kind(resource, actor)))

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    resourceOf(args).map(resource =>
      EconomyTree.tree(catalog, state, actor, kind(resource, actor)))
```

- [ ] **Step 4: Register the keys and switch the entry**

In `ProcedureNotes.scala`, add an arm above the wildcard — `TradeProcedure` is
in `oathdigital.gameplay.actions.economy`, which the file already imports for
`MusterProcedure`, so widen that import rather than adding one:

```scala
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
```

```scala
      case ActionRef.Trade => TradeProcedure.noteKeys
```

In `ProcedureLog.scala`, replace the `ActionRef.Trade` arm:

```scala
    // Four keys word this line — favor or secrets, some or none — so no
    // single key tells the source decision.
    case ActionRef.Trade => ProcedureLog(tag = Some("Trade"),
      lines = ActionLineSource.Notes(),
      anchor = starts.paysCost,
      narrated = Vector(Told(TradeProcedure.decisionId)))
```

- [ ] **Step 5: Delete the rule**

In `ActionLines.scala`, delete three blocks by their exact text:

- `def tradeLines(...)` entirely, including the two-line comment inside it
  about trading favor for secrets with no matching adviser.
- `private def traded(...)`, its doc comment included.
- `private def source(...)`, its doc comment included — `traded` was its only
  caller, and `-Wunused:privates` will say so if anything is missed.

Then remove the now-orphaned import `oathdigital.gameplay.actions.economy.TradeProcedure`.
`DecisionAnswer.ChooseOneAnswer` is still used by `stopped` and
`playedAdviser`, and `WalkerCompleted` by `recoverLines` and `usePowerLines`,
so neither import goes yet. Compile to be sure.

`ActionLines.resource` and `ActionLines.isTradeGain` lose their last callers
here. They are public companion members, so `-Wunused` will not flag them;
Task 5 deletes them. Leave them for now so this task's diff stays one
procedure wide.

- [ ] **Step 6: Raise the properties suite and add the uncovered-branch tests**

`GameLogPropertiesSuite`:

```scala
      Vector("forge", "travel", "take-wealth",
        "muster", "trade", "challenge", "place-banner-resource",
        "negotiation", "peek-site-relics", "reveal-owned-relic",
        "move-warbands", "finish-rest", "oathkeeper", "setup"))
```

`Trade` follows `Muster` in `ActionRef.all`.

The `trade` golden covers only `for no secrets`. Add three tests to
`ProcedureNoteLinesSuite` for the three keys no golden exercises:

```scala
  test("a Trade for favor reads as the rule wrote it"):
    assertEquals(spoken(ActionRef.Trade, TradeProcedure.tradedForFavor,
        NoteArg.Card(roar), NoteArg.Number(3)),
      Vector[LogSpan](Text("Traded with "), Text("a Denizen"),
        Text(" for "), Text("3"), Text(" favor")))

  test("a Trade for one secret says secret"):
    assertEquals(said(ActionRef.Trade, TradeProcedure.tradedForSecrets,
      NoteArg.Card(roar), NoteArg.Number(1)),
      "Traded with a Denizen for 1 secret")

  // The rule this replaced wrote "for no secrets" here whichever resource
  // the Trade was for; nothing covered it (slice 3 plan-time ruling 3, and
  // the wording ledger).
  test("a Trade for favor that gained none says no favor"):
    assertEquals(said(ActionRef.Trade, TradeProcedure.tradedForNoFavor,
      NoteArg.Card(roar)), "Traded with a Denizen for no favor")
```

A card with no state behind it renders as its back, which is why these read
`a Denizen` — the suite's class doc already says so, and `roar` is already
declared in it. Do **not** reach for an edifice here: `LogWords.backOf` gives
`"Edifice"` and `LogWords.cards` writes `s"a $kind"` with no article logic, so
a lone edifice back reads `a Edifice`, and pinning that in a new test would
freeze a wording bug. The Trade source can be a denizen, so use one.

- [ ] **Step 7: Record ruling 3 and ruling 4 in the wording ledger**

In `docs/superpowers/specs/2026-10-16-game-log-wording-ledger.md`, add a
section:

```markdown
## A Trade that gained nothing names the right resource

`ActionLines.tradeLines` posted its completion fallback with the literal
`"no secrets"`, so a Trade **for favor** whose suit bank was empty read
`Traded with Hiding Place for no secrets`. No test or golden covered the
branch. `TradeProcedure.tradedForNoFavor` now reads `… for no favor`
(procedures journal their own notes, slice 3 plan-time ruling 3).

The same rule matched every bank-to-player move in the event it formatted, so
a Trade a power gained again on — The Old Oak's bonus secret, Mounted
Library's doubling — posted a second `Traded with …` line. The note posts
once, naming what the Trade itself gained.
```

Write Step 1's measured count into that last sentence before committing: "The
rule posted N here" if the probe saw a second line, or "The rule posted one
line here, so the note changes nothing" if it did not. The section must state
what was observed, not what was expected.

- [ ] **Step 8: Rewrite the snapshot and the goldens, then read both diffs**

```bash
NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"
git diff src/test/resources/notes/declared-keys.txt
```

Expected: exactly four added lines and none removed —
`procedure.trade traded-favor`, `traded-no-favor`, `traded-secrets`,
`traded-no-secrets`, each with its rendered template.

```bash
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
git diff src/test/resources/gamelog/
```

Expected: in `trade.actor.log` and `trade.other.log` only, the
`Traded with [card:E26|Hiding Place] for no secrets` entry's sequence falls
from `24.0` — the line used to post at `WalkerCompleted` and now posts in the
gain window — and its spans, `action`, depth `1` and `trade@17` section are
unchanged. It is the last line in both files, so no line moves relative to
another. No other file changes. Anything else is a defect: revert the goldens
and find it.

- [ ] **Step 9: Full gate and commit**

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
git add -A && git commit -m "$(cat <<'MSGEOF'
feat(gamelog): Trade journals its own line

The rule's completion fallback wrote "for no secrets" whichever resource the
Trade was for; the note names the right one. Records what the rule did when a
power gained again in the same window.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

### Task 2: Recover journals its own two lines

Recover's two lines are both point-in-time facts the tree holds: the relic the
player answered, and the site the tree closed over. Neither reads `previous`.
The one hazard is ordering: `RelicWorship` appends a Supply gain into
`PowerWindow.RecoverAfterRelic`, and that window hangs on a `BuildOps` **leaf**
today, so a sibling note after it would post *after* `Gained 2 Supply` and the
recovered line would come second. The window moves onto a `Sequence` holding
the move and the note, exactly as slice 2's Setup fix did.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala`
  (the `ActionRef.Recover` arm)
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`
  (delete `recoverLines`, `stopped`)
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/ProcedureNoteLinesSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/recover/RelicWorshipLogSuite.scala`
- Rewrite: `src/test/resources/gamelog/recover-succeeded.{actor,other}.log`,
  `src/test/resources/gamelog/recover-failed.{actor,other}.log`,
  `src/test/resources/gamelog/reveal-relic.{actor,other}.log`
- Rewrite: `src/test/resources/notes/declared-keys.txt`

**Interfaces:**
- Consumes: `ProcedureNotes.id`, `ActionLineSource.Notes`, `Told`.
- Produces: `RecoverProcedure.recovered: NoteKey`,
  `RecoverProcedure.failed: NoteKey`,
  `RecoverProcedure.noteKeys: Vector[NoteKey]`.

- [ ] **Step 1: Write the failing ordering test first**

This is the test that would have caught slice 2's second regression. Create
`src/test/scala/oathdigital/gameplay/powers/recover/RelicWorshipLogSuite.scala`:

```scala
package oathdigital.gameplay.powers.recover

import oathdigital.application.gamelog.LogScripts
import oathdigital.application.gamelog.LogScripts._
import oathdigital.testkit.Table.p1

/** Relic Worship appends its Supply gain to the node `RecoverAfterRelic`
  * names, so the recovered line must sit inside that node: outside it, the
  * generic "Gained 2 Supply" delta would come first (procedures journal
  * their own notes design; slice 2 regression 2). */
class RelicWorshipLogSuite extends munit.FunSuite:
  test("the recovered line comes before Relic Worship's Supply gain"):
    val all = texts(format(recoverWorship, Some(p1)).filter(_.depth == 1))
    val recovered = all.indexWhere(_.startsWith("Recovered "))
    val supply = all.indexWhere(_.contains("Supply"))
    assert(recovered >= 0, all)
    assert(supply < 0 || supply > recovered, all)
```

`LogScripts.recoverWorship` (`LogScripts.scala:454-461`) already builds it and
`GameLogStartLineSuite` already uses it. It rolls `Dice.steady` at
`ParkedServiceFixture.recoverTable`, which holds a facedown relic, so confirm
the script actually reaches `Recovered …`; if it stops at the relic decision
unanswered, answer that decision the way `recoverSucceeded`
(`LogScripts.scala:408`) does and keep the new script beside it.

Run this test and confirm it passes against the **rule**, before touching the
tree. That is what makes it a regression guard rather than a new assertion
about new behaviour — and if it fails now, the rule already has the ordering
bug and Task 2 fixes it rather than preserving it. Say which it was.

- [ ] **Step 2: Declare Recover's two keys**

In `RecoverProcedure.scala`, widen the existing actions import:

```scala
import oathdigital.gameplay.actions.{ProcedureNotes, RecoverRules}
```

and, after `private val supplyCost: Int = 1`:

```scala
  private val notes = NoteSet(ProcedureNotes.id(ActionRef.Recover))
  /** "Recovered Sticky Fire at Broken Peaks". */
  val recovered: NoteKey = notes.key("recovered", Vector(
    NotePart.Text("Recovered "), NotePart.Arg(0), NotePart.Text(" at "),
    NotePart.Arg(1)))
  /** "Failed to recover at Broken Peaks": the player answered Stop. A
    * Recover that succeeded with no relic to take writes nothing, as the
    * rule this replaced wrote nothing. */
  val failed: NoteKey = notes.key("failed", Vector(
    NotePart.Text("Failed to recover at "), NotePart.Arg(0)))
  def noteKeys: Vector[NoteKey] = notes.declared
```

- [ ] **Step 3: Move the window and place both leaves**

Inside `recovery`, drop the window from `moveRelic`:

```scala
    val moveRelic = BuildOps((ready, pending) =>
      pending.answered.lastOption match {
        case Some(Answered(_, ChooseOneAnswer(
            DecisionOptionRef.Relic(relicId)), _)) =>
          Right(Vector[CoreOperation](relicMove(relicId)))
        case _ => Left(OathViolation.InvalidEventOrder(
          "no recovered relic answer is recorded"))
      })
```

and add the two notes beside it:

```scala
    /** The relic taken and the site it came from. Reads the relic decision's
      * answer and the site this tree closed over, never `previous`, so a
      * leaf a power appends into the window cannot move what it reads. */
    val recoveredNote = Note(ProcedureNotes.id(ActionRef.Recover), states =>
      states.answered.reverse.collectFirst {
        case Answered(`relicDecisionId`,
            ChooseOneAnswer(DecisionOptionRef.Relic(relicId)), _) => relicId
      }.map(relic => recovered.sourceless(NoteArg.Card(relic),
        NoteArg.Site(siteId))))

    /** "Failed to recover at Broken Peaks", read from the same `stopped`
      * the `Repeat` guard reads, so the line and the loop's own exit agree
      * on what ended the Recover. */
    val failedNote = Note(ProcedureNotes.id(ActionRef.Recover),
      _ => Some(failed.sourceless(NoteArg.Site(siteId))))
```

Then rewrite `afterLoop`. It needs `pending` now, so its first parameter list
changes from `(ready, _)` to `(ready, pending)`:

```scala
    val afterLoop = Branch((ready, pending) => {
      val relics = actorFacedownRelics(ready, actor)
      if succeeded(ready) && relics.nonEmpty then
        Vector(Decide(
          decisionId = relicDecisionId,
          owner = actor,
          query = DecisionQuery.ChooseOne(relics.flatMap(relic =>
            CardSlots.judge(catalog, ready, actor,
              DecisionOption.Relic(DecisionOptionRef.Relic(relic.id)),
              Right(Vector(relicMove(relic.id))))),
            heading = Some("Take a relic"), confirmVerb = Some("Take"))),
          // The move and its line in one windowed node: a power hooked at
          // `RecoverAfterRelic` appends to the node the window names, so a
          // note outside it would read after that power's leaves and
          // "Gained 2 Supply" would precede the line saying what was
          // recovered (`RelicWorshipLogSuite`).
          Sequence(Vector(moveRelic, recoveredNote),
            Some(PowerWindow.RecoverAfterRelic)))
      else if stopped(pending) then Vector(failedNote)
      else Vector.empty
    })
```

Keep the existing block comment above `afterLoop` about the live relic query;
it is still true. Add one sentence to it saying the third branch is the
abandoned Recover's line.

- [ ] **Step 4: Register the keys and switch the entry**

`ProcedureNotes.scala` — the file does not import the recover package yet:

```scala
import oathdigital.gameplay.actions.recover.RecoverProcedure
```

```scala
      case ActionRef.Recover => RecoverProcedure.noteKeys
```

`ProcedureLog.scala`:

```scala
    // The relic pick is told by the recovered line, which names the relic.
    // The continue-or-stop answer is told by the band's own "Continued"
    // line and, for a stop, by the failed line, so no single key tells it.
    case ActionRef.Recover => ProcedureLog(tag = Some("Recover"),
      lines = ActionLineSource.Notes(),
      anchor = starts.opensDicePool, continued = starts.continuedRecover,
      narrated = Vector(Told(RecoverProcedure.choiceDecisionId),
        Told(RecoverProcedure.relicDecisionId,
          Some(RecoverProcedure.recovered))))
```

- [ ] **Step 5: Delete the rule**

In `ActionLines.scala`, delete by exact text:

- `def recoverLines(...)` entirely, its inner comment about a Recover that
  succeeds with no relic included.
- `private def stopped(...)` with its doc comment.

Remove the orphaned import
`oathdigital.gameplay.actions.recover.RecoverProcedure`. `LogJournal.pawnSite`
loses its last caller in this file — check whether it keeps one elsewhere
before touching it, and if it does not, leave it: it is a public member of
`LogJournal` and out of this task's scope.

- [ ] **Step 6: Raise the properties suite and add the uncovered-branch test**

`GameLogPropertiesSuite` — `Recover` is third in `ActionRef.all`:

```scala
      Vector("recover", "forge", "travel", "take-wealth",
        "muster", "trade", "challenge", "place-banner-resource",
        "negotiation", "peek-site-relics", "reveal-owned-relic",
        "move-warbands", "finish-rest", "oathkeeper", "setup"))
```

Both Recover lines are covered by goldens — `recover-succeeded`,
`reveal-relic` and `recover-failed` — so no wording test is strictly owed.
Add one anyway for the kind, which no golden asserts in isolation:

```scala
  test("both Recover lines post at Action"):
    assertEquals(kindOf(ActionRef.Recover, RecoverProcedure.recovered),
      LogKind.Action)
    assertEquals(kindOf(ActionRef.Recover, RecoverProcedure.failed),
      LogKind.Action)
```

- [ ] **Step 7: Rewrite the snapshot and the goldens, then read both diffs**

```bash
NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"
git diff src/test/resources/notes/declared-keys.txt
```

Expected: exactly two added lines, `procedure.recover recovered` and
`procedure.recover failed`.

```bash
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
git diff src/test/resources/gamelog/
```

Expected: six files. `Recovered … at Broken Peaks` shifts from `11.0` in
`recover-succeeded.*` and `reveal-relic.*`; `Failed to recover at …` shifts
from `16.0` in `recover-failed.*`. Each is the last line of its section, so
nothing moves relative to anything. Spans, `action`, depth `1` and the
`recover@4` section are unchanged, and `reveal-relic`'s later
`reveal-owned-relic@19` line does not move. No other file changes.

- [ ] **Step 8: Full gate and commit**

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
git add -A && git commit -m "$(cat <<'MSGEOF'
feat(gamelog): Recover journals its own two lines

The window moves from the relic move onto the move and its line together, so
a power that appends into it cannot post ahead of the line that says what was
recovered.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

### Task 3: The played adviser's line lands in `CardPlayProcedure`

`ActionLines.playedAdviser` is the one rule three registry entries share:
Search's, Play Facedown Adviser's and Use Power's. It reads backwards from a
`DeltaRecorded` event to the last placement answer and refuses if any delta
intervened — a guess about which batch belongs to the play. Card play knows
the slot the player answered, so the guess disappears.

Ruling 2 puts the keys in `CardPlayProcedure`, owned by
`ProcedureNotes.id(ActionRef.PlayFacedownAdviser)` wherever the play runs.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala`
  (the `ActionRef.PlayFacedownAdviser` arm)
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`
  (delete `playedAdviser`, `played`; drop the call from `searchLines` and
  `usePowerLines`)
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/ProcedureNoteLinesSuite.scala`
- Rewrite: `src/test/resources/gamelog/facedown-adviser.{actor,other}.log`,
  `heirloom-kept.{actor,other}.log`, `heirloom-returned.{actor,other}.log`,
  `oracle.{actor,other}.log`, `search.{actor,other}.log`,
  `augury.{actor,other}.log`
- Rewrite: `src/test/resources/notes/declared-keys.txt`

**Interfaces:**
- Consumes: `ProcedureNotes.id`, `ActionLineSource.Notes`;
  `CardPlayProcedure.playedAs`, `CardPlayProcedure.siteOf`,
  `CardSlot.ref` (the answered placement option).
- Produces: `CardPlayProcedure.discardedTo`, `.discarded`, `.playedAtSite`,
  `.revealedVision`, `.playedAsAdviser`, all `NoteKey`;
  `CardPlayProcedure.noteKeys: Vector[NoteKey]`.

- [ ] **Step 1: Declare card play's five keys**

In `CardPlayProcedure.scala`, widen the actions import:

```scala
import oathdigital.gameplay.actions.{CardPlay, CardPlayRules, ProcedureNotes}
```

and, after `val facedownAdviserDecisionId: String = "cardplay.facedown-adviser"`:

```scala
  /** The lines a card play writes about itself. A card play is an embedded
    * subtree, not a `ProcedureRef`, so it has no registry entry of its own
    * and no kind: these keys are owned by the facedown-adviser action's id
    * wherever the play runs — a Search, that action, or a power's own play —
    * and all three resolve their kind from that one entry, which is
    * `Action` (procedures journal their own notes design, slice 3 plan-time
    * ruling 2). The id is never rendered. */
  private val notes = NoteSet(ProcedureNotes.id(ActionRef.PlayFacedownAdviser))
  /** "Discarded Tinker to the Provinces discard": named with its pile, as
    * every other discard line is. */
  val discardedTo: NoteKey = notes.key("discarded-to", Vector(
    NotePart.Text("Discarded "), NotePart.Arg(0), NotePart.Text(" to the "),
    NotePart.Arg(1), NotePart.Text(" discard")))
  /** "Discarded Tinker", when the card came to rest outside any regional
    * pile. The rule this replaced appended nothing in that case. */
  val discarded: NoteKey = notes.key("discarded", Vector(
    NotePart.Text("Discarded "), NotePart.Arg(0)))
  /** "Played Fae Merchant to Ancient City". */
  val playedAtSite: NoteKey = notes.key("played-at-site", Vector(
    NotePart.Text("Played "), NotePart.Arg(0), NotePart.Text(" to "),
    NotePart.Arg(1)))
  /** "Revealed Vision of Sanctuary": a Vision played faceup takes the
    * revealed Vision slot, not an adviser's. */
  val revealedVision: NoteKey = notes.key("revealed-vision", Vector(
    NotePart.Text("Revealed "), NotePart.Arg(0)))
  /** "Played Family Heirloom as an adviser". */
  val playedAsAdviser: NoteKey = notes.key("played-as-adviser", Vector(
    NotePart.Text("Played "), NotePart.Arg(0),
    NotePart.Text(" as an adviser")))
  def noteKeys: Vector[NoteKey] = notes.declared
```

- [ ] **Step 2: Build the note from the answered slot and the live state**

Add beside `playedAs`:

```scala
  /** Where the card went. The placement is the slot the player answered, so
    * this says what the play did rather than reading back from a recorded
    * batch and guessing which one was the play's. The pile comes from the
    * live state after the play, never from `previous`: the credits node and
    * the play's own operations both run before this leaf. */
  private def playedNote(card: WorldCardId, ref: DecisionOptionRef)(
      states: NoteStates): Option[PowerNote] = playedAs(ref).flatMap {
    case PlayedAs.Discarded => Some(discardPile(states.now, card).fold(
      discarded.sourceless(NoteArg.Card(card)))(region =>
        discardedTo.sourceless(NoteArg.Card(card),
          NoteArg.RegionName(region))))
    case PlayedAs.AtSite => siteOf(ref).map(site =>
      playedAtSite.sourceless(NoteArg.Card(card), NoteArg.Site(site)))
    case PlayedAs.Faceup if card.isInstanceOf[VisionId] =>
      Some(revealedVision.sourceless(NoteArg.Card(card)))
    case _ => Some(playedAsAdviser.sourceless(NoteArg.Card(card)))
  }

  /** The regional pile `card` lies in, when it lies in one. */
  private def discardPile(ready: ReadyGame, card: WorldCardId)
      : Option[Region] =
    ready.game.cardIndex.toOption.flatMap(_.get(card))
      .map(_.location.container).collect {
        case CardContainer.RegionalDiscard(region) => region
      }
```

`NoteArg.RegionName` renders `region.toString`, and the rule wrote
`region.key.capitalize`. Every `Region` is one word — Cradle, Provinces,
Hinterland — so the two agree, and `SpinningBee`'s existing line already
proves the rendering reads `the Cradle discard`. Confirm it in the golden
diff rather than on trust.

- [ ] **Step 3: Place the leaf after the play**

In `played`, the `play` branch returns the credits node then the play. Add the
note as its third child:

```scala
      credited match
        case Left(error) => Vector(Sequence(Vector.empty),
          BuildOps((_, _) => Left(error)))
        case Right(notes) => Vector(Sequence(notes), apply,
          Note(ProcedureNotes.id(ActionRef.PlayFacedownAdviser),
            playedNote(card, slot.ref)))
```

Update the comment above `play`: the play is still the node's second child,
which is what `settled` mirrors and what a follow-up path addresses into, and
the line follows it.

Leave `settled` as it is, and add one sentence to its doc comment saying why:
a resumed walk starts past the play, so neither the play nor its line runs
again, and the line is already journaled. The two shapes differ only by a
trailing child, so no path into the follow-ups shifts.

- [ ] **Step 4: Register the keys and switch the entry**

`ProcedureNotes.scala` already imports
`oathdigital.gameplay.actions.cardplay` in no form — add it:

```scala
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
```

```scala
      case ActionRef.PlayFacedownAdviser => CardPlayProcedure.noteKeys
```

`ProcedureLog.scala`:

```scala
    // The played card's line names the facedown pick. Five keys word it —
    // a discard with or without a pile, a play to a site, a revealed
    // Vision, an adviser — so no single key tells the decision.
    case ActionRef.PlayFacedownAdviser =>
      ProcedureLog(tag = Some("Play facedown adviser"),
        lines = ActionLineSource.Notes(),
        anchor = starts.adviserPicked,
        narrated = Vector(Told(CardPlayProcedure.facedownAdviserDecisionId)),
        narratedPrefixes = Vector(ActionLines.PlacePrefix,
          "cardplay.discard."))
```

Search's and Use Power's entries keep `ActionLineSource.Rule`; only what the
rule returns changes, in the next step.

- [ ] **Step 5: Delete the rule and its two call sites**

In `ActionLines.scala`:

```scala
  def searchLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    search(journal, run, at, viewer)
```

```scala
  /** Only a run that answered a placement decision has a played-card line. */
  def usePowerLines(power: PowerId)(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    usedPower(journal, run, at, power,
      journal.event(at).isInstanceOf[WalkerCompleted], viewer)
```

Then delete `def playedAdviser(...)` and `private def played(...)` by exact
text, doc comments included, and fix `usePowerLines`' doc comment, which now
describes a line it no longer posts — it is the `"Used {card}"` fallback and
nothing else.

Orphaned imports to remove: `CardPlayProcedure`, `CardPlayProcedure.PlayedAs`.
`ActionLines.isDelta`, `ActionLines.subjectCard` and
`ActionLines.worldCard` lose callers here — `subjectCard` and `PlacePrefix`
keep one in `DetailLines`, so only `isDelta` and `worldCard` become orphans,
and Task 5 removes them. Compile and let `-Wunused:privates` find anything
missed.

- [ ] **Step 6: Raise the properties suite and add the uncovered-branch test**

`GameLogPropertiesSuite` — `PlayFacedownAdviser` is second in `ActionRef.all`:

```scala
      Vector("play-facedown-adviser", "recover", "forge", "travel",
        "take-wealth", "muster", "trade", "challenge",
        "place-banner-resource", "negotiation", "peek-site-relics",
        "reveal-owned-relic", "move-warbands", "finish-rest", "oathkeeper",
        "setup"))
```

Four of the five keys are covered: `discardedTo` by `facedown-adviser` and
`search`, `playedAsAdviser` by `heirloom-kept` and `oracle`, `playedAtSite` by
`GameLogSearchKeepSuite`'s Land Warden test, `revealedVision` by
`GameLogActionLineSuite`'s Vision test. `discarded` — a discard that reached no
regional pile — is covered by nothing. Add one test:

```scala
  test("a discard that reached no regional pile names no pile"):
    assertEquals(said(ActionRef.PlayFacedownAdviser,
      CardPlayProcedure.discarded, NoteArg.Card(birdsong)),
      "Discarded a Denizen")

  test("every card-play line posts at Action, under whichever procedure"):
    Vector(CardPlayProcedure.discardedTo, CardPlayProcedure.discarded,
      CardPlayProcedure.playedAtSite, CardPlayProcedure.revealedVision,
      CardPlayProcedure.playedAsAdviser).foreach(key =>
        assertEquals(kindOf(ActionRef.PlayFacedownAdviser, key),
          LogKind.Action, key.name))
```

- [ ] **Step 7: Rewrite the snapshot and the goldens, then read both diffs**

```bash
NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"
git diff src/test/resources/notes/declared-keys.txt
```

Expected: exactly five added lines, all under
`procedure.play-facedown-adviser`.

```bash
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
git diff src/test/resources/gamelog/
```

Expected, in twelve files and nowhere else:

- `facedown-adviser.*`: `Discarded … to the Provinces discard` shifts from
  `11.0`.
- `heirloom-kept.*`, `heirloom-returned.*`: `Played Family Heirloom as an
  adviser` shifts from `11.0`.
- `oracle.*`: `Played a Vision as an adviser` / the named Vision shifts from
  `11.0`, still under `use-power:denizen.oracle@4`.
- `search.*`: `Discarded Threatening Roar to the Provinces discard` shifts
  from `17.0` and **stays after** the `13.0 delta` line for the two cards not
  kept. If it moves ahead of that delta line, the note is in the wrong place:
  it must follow `apply`, not the credits node.
- `augury.*`: the same line shifts from `20.0`, after the `16.0 delta`.

The `Drew …` lines must not move in this task — they are Task 4's. Spans,
`action`, depth `1` and every section are unchanged.

- [ ] **Step 8: Full gate and commit**

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
git add -A && git commit -m "$(cat <<'MSGEOF'
feat(gamelog): a card play journals where the card went

The rule read backwards from a recorded batch to the last placement answer and
refused if a delta intervened. Card play knows the slot the player answered,
so the guess goes. The keys are owned by the facedown-adviser action's id
wherever the play runs, which is the one registry entry that can declare their
kind.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

### Task 4: Search journals its own two lines, and its draw powers say how they draw

The largest task, and the only one whose rendered lines change. Read "Why
Search is different" above before starting: rulings 1 and 5 are what this task
implements, and they are the user's decisions, not suggestions.

The whole Search change lands in one commit because the two halves are one
wording decision: Search's line stops naming how the draw was reshaped, and
the four powers that reshape it start naming it themselves. Split across two
commits, the first would print both phrasings at once.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/search/WineOfWelcome.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/search/Mushrooms.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/search/PaintedTrumpet.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/search/SpinningBee.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ProcedureNotes.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ProcedureLog.scala`
  (the `ActionRef.Search` arm)
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`
  (delete `searchLines`, `search`, `drawnFrom`, `inDiscardPile`)
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogSearchDrawSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogSearchKeepSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/ProcedureNoteLinesSuite.scala`
- Modify: `docs/superpowers/specs/2026-10-16-procedures-journal-their-notes-design.md`
- Modify: `docs/superpowers/specs/2026-10-16-game-log-wording-ledger.md`
- Rewrite: `src/test/resources/gamelog/search.{actor,other}.log`,
  `augury.{actor,other}.log`
- Rewrite: `src/test/resources/notes/declared-keys.txt`

**Interfaces:**
- Consumes: `ProcedureNotes.id`, `ActionLineSource.Notes`, `Told`;
  `SearchProcedure.kept(answered)`, `SearchSource`,
  `SpinningBee.declared(answered)`.
- Produces: `SearchProcedure.drewFromDeck`, `.drewFromDiscard`, `.keptCards`,
  `SearchProcedure.noteKeys`; `WineOfWelcome.noteKeys`,
  `Mushrooms.noteKeys`, `PaintedTrumpet.noteKeys` and one key each;
  `SpinningBee.drewNone`.

- [ ] **Step 1: Declare Search's three keys and the hand helper**

In `SearchProcedure.scala`, add the import:

```scala
import oathdigital.gameplay.actions.ProcedureNotes
```

and, after `private val startLabel = "Start Search"`:

```scala
  private val notes = NoteSet(ProcedureNotes.id(ActionRef.Search))
  /** "Drew Threatening Roar, Fae Merchant and Second Chance from the World
    * Deck". */
  val drewFromDeck: NoteKey = notes.key("drew-from-deck", Vector(
    NotePart.Text("Drew "), NotePart.Arg(0),
    NotePart.Text(" from the World Deck")))
  /** "Drew Threatening Roar from the Cradle discard". A separate key rather
    * than a `Pile` argument: `SearchSource.name` words a pile as "Cradle
    * discard pile", and this line says "the Cradle discard". */
  val drewFromDiscard: NoteKey = notes.key("drew-from-discard", Vector(
    NotePart.Text("Drew "), NotePart.Arg(0), NotePart.Text(" from the "),
    NotePart.Arg(1), NotePart.Text(" discard")))
  /** "Kept Threatening Roar", or both cards when Land Warden kept two. */
  val keptCards: NoteKey = notes.key("kept", Vector(
    NotePart.Text("Kept "), NotePart.Arg(0)))
  def noteKeys: Vector[NoteKey] = notes.declared

  /** The actor's temporary hand: the cards a Search has drawn and not yet
    * placed. */
  private def hand(ready: ReadyGame, actor: PlayerId): Vector[WorldCardId] =
    ready.game.current.temporaryHands.getOrElse(actor, Vector.empty)
```

Use `hand` in `selectedCards` too, replacing its inline `getOrElse`.

- [ ] **Step 2: Build the two notes**

Add to `SearchProcedure`:

```scala
  /** "Drew … from the World Deck", at the draw. The cards are read from the
    * live temporary hand and not from `previous`: Truthful Harp and
    * Spinning Bee each append a leaf inside `SearchBeforeDraw`, and every
    * leaf resets `previous`, which is what broke Travel and Setup in
    * slice 2. The pile is the one the Search picked; a power that drew from
    * the other end of it, or from every pile, says so in its own line
    * (slice 3 plan-time ruling 5).
    *
    * Spinning Bee's no-match draw discards the whole hand inside that same
    * window, so the hand is empty here and this writes nothing: Bee's own
    * line names what it drew and discarded.
    */
  private def drewNote(actor: PlayerId, source: SearchSource)(
      states: NoteStates): Option[PowerNote] =
    Option(hand(states.now, actor)).filter(_.nonEmpty).map(drawn =>
      source match
        case SearchSource.WorldDeck =>
          drewFromDeck.sourceless(NoteArg.Cards(drawn))
        case SearchSource.RegionalDiscard(region) =>
          drewFromDiscard.sourceless(NoteArg.Cards(drawn),
            NoteArg.RegionName(region)))

  /** "Kept Threatening Roar", after the keep question and before the
    * placement, where the hand still holds every drawn card. The kept cards
    * come from the keep answer when one was asked, and are the one card
    * drawn when nothing was asked. Nothing held means Spinning Bee drew no
    * match and discarded them all, and Bee's own line says so. */
  private def keptNote(actor: PlayerId)(states: NoteStates)
      : Option[PowerNote] =
    val held = hand(states.now, actor)
    val keeps = kept(states.answered)
    val chosen: Vector[WorldCardId] =
      if keeps.nonEmpty then keeps
      else if held.sizeIs == 1 then held
      else Vector.empty
    Option.when(chosen.nonEmpty)(keptCards.sourceless(
      NoteArg.Cards(chosen)))
```

`Vector` is covariant and `WorldCardId <: CardId`, so `NoteArg.Cards(drawn)`
needs no mapping.

- [ ] **Step 3: Place both leaves in the body**

Replace the last expression of `body`:

```scala
    Sequence(Vector(cost, draw,
      Note(ProcedureNotes.id(ActionRef.Search), drewNote(actor, source)),
      query,
      Note(ProcedureNotes.id(ActionRef.Search), keptNote(actor)),
      placement),
      Some(PowerWindow.SearchActionEligibility))
```

Both notes are siblings in the eligibility node, which is right for each:

- The draw line follows the draw node, so Truthful Harp's reveal line and
  Spinning Bee's declaration line — both inserted at the draw's position —
  still precede it, as they do today.
- The keep line precedes `placement`, so the discard delta for the cards not
  kept and the kept card's own line still follow it.
- `HuntingParty` is the only `SearchActionEligibility` contributor and it
  appends after `placement`, so neither note moves.

Add a short comment to `body`'s doc block saying the two notes sit here and
why, with a pointer to `drewNote`.

- [ ] **Step 4: Give the three reshaping powers their own line**

`WineOfWelcome.scala` — the pile is always the world deck, so the line takes
no argument:

```scala
object WineOfWelcome:
  val id: PowerId = WineOfWelcomeCard.power.id
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Wine of Welcome: Drawing from the bottom of the World Deck", before
    * the draw, as Spinning Bee's line is. The Search line names the pile
    * but cannot know which end the draw took, because that is this power's
    * rewrite of the draw operation (slice 3 plan-time ruling 5). */
  val drawing: NoteKey = notes.key("drawing", Vector(
    NotePart.Text("Drawing from the bottom of the World Deck")))
```

and on the class, beside `effects`:

```scala
  override def noteKeys: Vector[NoteKey] = WineOfWelcome.noteKeys

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchBeforeDraw -> Vector(Transform((_, operations) =>
      Note(id, _ => Some(WineOfWelcome.drawing.sourceless())) +:
        BottomDraw.reshaped(operations, SearchRules.DrawSize,
          _ == SearchSource.WorldDeck))))
```

`Mushrooms.scala` — the pile is whichever discard the source decision picked,
which `Observatory` and `Errand Boy` can widen, so read it from the answer:

```scala
object Mushrooms:
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Mushrooms: Drawing one card from the bottom of the Cradle discard". */
  val drawing: NoteKey = notes.key("drawing", Vector(
    NotePart.Text("Drawing one card from the bottom of the "),
    NotePart.Arg(0), NotePart.Text(" discard")))
```

```scala
  /** The pile the source decision picked, when it picked a discard pile. */
  private def line: Operation = Note(id, states =>
    states.answered.collectFirst {
      case Answered(SearchProcedure.sourceDecisionId,
          DecisionAnswer.ChooseOneAnswer(ref), _) => ref
    }.flatMap(SearchSource.fromRef).collect {
      case SearchSource.RegionalDiscard(region) =>
        Mushrooms.drawing.sourceless(NoteArg.RegionName(region))
    })
```

and prepend `line` to the `SearchBeforeDraw` transform the way Wine of
Welcome does. A pattern match on a non-stable identifier needs the string
compared rather than bound — write the guard as
`if answered.decisionId == SearchProcedure.sourceDecisionId` if the
backtick-free form will not compile.

`PaintedTrumpet.scala` — one card from each pile, so no argument:

```scala
object PaintedTrumpet:
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Painted Trumpet: Drawing the top card of each discard pile instead".
    * The Search line names the pile the player picked, which is the pile
    * the Search was priced at; this line says the cards came from all of
    * them. */
  val drawing: NoteKey = notes.key("drawing", Vector(
    NotePart.Text("Drawing the top card of each discard pile instead")))
```

Prepend its note to its own `SearchBeforeDraw` transform, before the `map`
that rewrites the draw node. The note must be conditional on the pile being a
discard pile, because a Search of the world deck is unchanged: build it from
the same `picked(pending)` the transform already uses, so the line appears
exactly when the rewrite does.

Each of the three powers folds at its own priority — `BottomDraw.Priority` is
−1, the draw extensions 0, `PaintedTrumpet.Priority` +1 — and prepending to
the children vector puts the line first whatever the fold order, because every
contributor sees the previous one's output.

- [ ] **Step 5: Give Spinning Bee its no-match line**

`SpinningBee` already writes `Drawing from the Cradle discard until Order card
is drawn` before the draw. It needs a second line for the case where no card
matched, because that is the only case where Search's own two notes write
nothing. Add to the companion:

```scala
  /** "Spinning Bee: Drew Homesteaders, Wrestlers and Defame, and discarded
    * them all". The Search's own lines cannot write this: this power
    * discards the whole hand inside `SearchBeforeDraw`, so by the time
    * Search's draw line runs the hand is empty (slice 3 plan-time
    * ruling 5). */
  val drewNone: NoteKey = notes.key("drew-none", Vector(
    NotePart.Text("Drew "), NotePart.Arg(0),
    NotePart.Text(", and discarded them all")))
```

and in the class, after `discardedUnlessMatched`:

```scala
  /** The actor's temporary hand in `ready`. */
  private def hand(ready: ReadyGame, actor: PlayerId): Vector[WorldCardId] =
    ready.game.current.temporaryHands.getOrElse(actor, Vector.empty)

  /** What the no-match draw discarded, read as the hand the discard step
    * emptied. A matched draw leaves the hand untouched, so
    * `discardedUnlessMatched` runs and changes nothing, the step gives the
    * same non-empty hand twice, and this writes nothing; only a hand that
    * went from full to empty is the no-match case.
    *
    * This is the one note in slice 3 that reads `previous` by design: the
    * leaf it restates is the one this power placed immediately before it,
    * in a vector this power controls, so nothing can come between them. */
  private def discardedLine(actor: PlayerId): Note = Note(id, states =>
    states.previous
      .collect { case (before, after) if hand(after, actor).isEmpty =>
        hand(before, actor) }
      .filter(_.nonEmpty)
      .map(drawn => SpinningBee.drewNone(PowerSourceRef.Card(cardId),
        NoteArg.Cards(drawn))))
```

The actor comes from `ctx.activePlayer` at the contribution, as `line` and
`discardedUnlessMatched` already take it — do not close over a different one.
Append `discardedLine(ctx.activePlayer)` after
`discardedUnlessMatched(ctx.activePlayer)` in Bee's own `SearchBeforeDraw`
vector, so the Transform returns four nodes where it returns three today.

- [ ] **Step 6: Register every key and switch Search's entry**

`ProcedureNotes.scala`:

```scala
import oathdigital.gameplay.actions.search.SearchProcedure
```

```scala
      case ActionRef.Search => SearchProcedure.noteKeys
```

The four powers' keys reach `DeclaredNotes` through the power catalogs'
`noteKeys`, not through `ProcedureNotes`, so nothing else registers them —
but each power must override `noteKeys`, or `NoteCheck` refuses the note and
the line vanishes silently. `SpinningBee` already overrides it; the other
three do not, and that override is the whole reason their keys are declared on
their companions.

`ProcedureLog.scala`:

```scala
    // Two keys word the draw line, one per pile kind, so no single key
    // tells the source decision. Card Play's placement line names the card
    // it placed; the follow-up discard of a Replace site slot is told by
    // the note of the power that permitted it.
    case ActionRef.Search => ProcedureLog(tag = Some("Search"),
      lines = ActionLineSource.Notes(),
      anchor = starts.picksSettled,
      narrated = Vector(Told(SearchProcedure.sourceDecisionId),
        Told(SearchProcedure.cardDecisionId,
          Some(SearchProcedure.keptCards))),
      narratedPrefixes = Vector(ActionLines.PlacePrefix,
        "cardplay.discard."))
```

- [ ] **Step 7: Delete the rule**

In `ActionLines.scala`, delete by exact text: `def searchLines(...)`,
`private def search(...)` with its long doc comment, `private def
drawnFrom(...)`, and `private def inDiscardPile(...)`. Remove the orphaned
imports `SearchProcedure` and `DecisionAnswer.PartitionAnswer`; keep
`ChooseOneAnswer`, which `named` still uses.

`ActionLines.sourceName` and `ActionLines.worldCard` lose their last callers.
Task 5 removes them.

After this step `ActionLines` holds exactly `campaignLines`, `usePowerLines`,
the private `usedPower`, `effect`, `powerSource` and `named`, the `campaigns`
field, and the companion. Its class doc still says "The action line each
procedure posts"; correct it to say what is left — Campaign's lines and the
`"Used {card}"` fallback — and point at the design for where the rest went.

- [ ] **Step 8: Update the three Search log suites and the action-line suite**

`GameLogActionLineSuite`'s Search test asserts one joined line. Split it:

```scala
    val drew = lineStarting(mine, "Drew ")
    assert(drew.endsWith(" from the World Deck"), drew)
    assert(mine.exists(_.startsWith("Kept ")), mine)
```

Keep the `theirs` assertions as they are: the other seat must still read backs
on the `Drew` line, and `entryStarting(entries, "Drew ")` must still carry no
`LogSpan.Card`.

`GameLogSearchDrawSuite` — each of its three tests now asserts the power's own
line and the plain Search line:

```scala
  test("Wine of Welcome: its line says the draw came from the bottom"):
    val lines = searchLines("wine-of-welcome", …)
    assert(lines.contains(
      "Wine of Welcome: Drawing from the bottom of the World Deck"), lines)
    assert(lineStarting(lines, "Drew ").endsWith(" from the World Deck"),
      lines)
```

and the same shape for Mushrooms (`Mushrooms: Drawing one card from the
bottom of the Cradle discard`, then `Drew Threatening Roar from the Cradle
discard`) and Painted Trumpet (`Painted Trumpet: Drawing the top card of each
discard pile instead`, then a `Drew …` line naming the picked pile). Assert
the power's line comes **before** the `Drew` line in each. Update the class
doc: the draws these pin are now each told by the power that reshapes them.

`GameLogSearchKeepSuite`:

- The Spinning Bee match test keeps its ordering assertion and changes
  `" from the Cradle discard and kept "` to `" from the Cradle discard"`, plus
  a `Kept ` assertion.
- The no-match test becomes Bee's own line:

```scala
  test("Spinning Bee with no match: its line names what it discarded"):
    val lines = searchLines("spinning-bee-none", besideBee,
      Vector(SpinningBee.id), declaring(Suit.Nomad))
    assert(lines.exists(line => line.startsWith("Spinning Bee: Drew ") &&
      line.endsWith(", and discarded them all")), lines)
    assert(!lines.exists(_.startsWith("Drew ")), lines)
    assert(!lines.exists(_.startsWith("Kept ")), lines)
```

- Land Warden's test changes `" and kept Threatening Roar and Fae Merchant"`
  to a `Kept Threatening Roar and Fae Merchant` line, and keeps both
  placement assertions.

`GameLogDetailSuite`'s two Search tests pass unchanged — the kept card's
placement still follows the draw line, and the cards not kept still go to a
regional pile. Run them and confirm rather than assume.

- [ ] **Step 9: Raise the properties suite and pin the oversized draw**

`GameLogPropertiesSuite` — `Search` is first in `ActionRef.all`:

```scala
      Vector("search", "play-facedown-adviser", "recover", "forge", "travel",
        "take-wealth", "muster", "trade", "challenge",
        "place-banner-resource", "negotiation", "peek-site-relics",
        "reveal-owned-relic", "move-warbands", "finish-rest", "oathkeeper",
        "setup"))
```

Add to `ProcedureNoteLinesSuite` a test for each new key, and one that pins
the `LogWords.listed` switch the global constraints name:

```scala
  test("a Search of a discard pile names the pile"):
    assertEquals(said(ActionRef.Search, SearchProcedure.drewFromDiscard,
      NoteArg.Cards(Vector(roar)), NoteArg.RegionName(Region.Cradle)),
      "Drew a Denizen from the Cradle discard")

  // Six drawn cards is reachable: 3 printed, +1 Augury, +2 Truthful Harp.
  // The rule this replaced called `LogWords.cards` at any length and wrote
  // "6 Denizens"; a note's `Cards` argument goes through `LogWords.listed`,
  // which above `LogWords.Inline` (5) writes "6 cards" when the viewer
  // identifies none of them and a `LogSpan.Cards` span when it identifies
  // some. Recorded in the wording ledger.
  private val faeMerchant: CardId = CatalogNames.denizen("Fae Merchant")
  private val secondChance: CardId = CatalogNames.denizen("Second Chance")
  private val piedPiper: CardId = CatalogNames.denizen("Pied Piper")

  test("a draw above five cards is counted, not listed by its backs"):
    val six = Vector(roar, faeMerchant, secondChance, piedPiper, mushrooms,
      mercenaries)
    assertEquals(said(ActionRef.Search, SearchProcedure.drewFromDeck,
      NoteArg.Cards(six)), "Drew 6 cards from the World Deck")
```

Every card in this suite renders as a back — it passes no states — so this
test pins the all-backs branch. The actor's own six-card view takes
`listed`'s third branch and renders a `LogSpan.Cards` span; that needs a real
Search script with Augury and Truthful Harp both picked, which is more table
than the divergence is worth. Say so in the ledger entry instead.

- [ ] **Step 10: Amend the design, the ledger and the three card docs**

In `docs/superpowers/specs/2026-10-16-procedures-journal-their-notes-design.md`,
amend slice 3's entry in the slices list. It says Search's guesses "disappear
rather than moving"; that is true of three of the four and false of Search.
Add a subsection beside the existing "`covered` and `repeated`" amendment:

```markdown
### Search's line could not be one note

**Amended during slice 3, against this document.** The slices list said
Search's three fallback shapes disappear like the others'. They do, but the
line itself cannot be a note: it needs the kept set (in `answered`), the
drawn set (in state) and the `from {pile}` phrase (in the `Draw` operation as
powers rewrote it), and the keep answer arrives in a different walker command
than the draw, so `previous` is gone and only `states.now` survives. Spinning
Bee empties the hand inside `SearchBeforeDraw`, so even that is empty there.

Slice 3 splits the line — `Drew {cards} from {pile}` at the draw, `Kept
{cards}` at the keep answer — and moves the bottom-draw, each-pile and
no-match phrases into Wine of Welcome, Mushrooms, Painted Trumpet and
Spinning Bee, each of which now writes its own line where it previously
relied on Search's. This is the one deliberate exception to ruling 6 in the
whole migration, and it is confined to one commit.
```

In the wording ledger, add a section recording the split, the three powers'
new lines, and the `listed` switch above five cards.

In `WineOfWelcome.scala`, `Mushrooms.scala` and `PaintedTrumpet.scala`, replace
the class doc sentence *"It writes no line: … the Search line says …"* with
what is now true: it writes its own line before the draw, because the Search
line names the pile but cannot know how the draw was reshaped.

- [ ] **Step 11: Rewrite the snapshot and the goldens, then read both diffs**

```bash
NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"
git diff src/test/resources/notes/declared-keys.txt
```

Expected: seven added lines — three under `procedure.search`, one each under
`relic.wine-of-welcome`, `denizen.mushrooms`, `relic.painted-trumpet`, and one
more under `denizen.spinning-bee` beside its existing two.

```bash
GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"
git diff src/test/resources/gamelog/
```

Expected, in four files and nowhere else:

```
-12.0 action 1 search@4 | Drew […] from the World Deck and kept [card:179|Threatening Roar]
+<n> action 1 search@4 | Drew […] from the World Deck
+<m> action 1 search@4 | Kept [card:179|Threatening Roar]
```

with the `Drew` line before the `13.0 delta` discard line, the `Kept` line
after `Drew` and before that delta, and `search.other.log` reading
`Drew 3 Denizens from the World Deck` / `Kept a Denizen`. `augury.*` the same
with four cards. Everything else in those four files, and every other golden,
is untouched. If the `Drew` line lands under the `section` header instead of
where the draw happened, `starts.picksSettled` fires after the draw and the
note is being held — say so and stop rather than moving the note.

- [ ] **Step 12: Full gate and commit**

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
git add -A && git commit -m "$(cat <<'MSGEOF'
feat(gamelog): Search journals its own two lines

Search's single line needed the kept set, the drawn set and the pile phrase at
once, and they never coexist where a note can read them: the keep answer
arrives in a different command than the draw, and Spinning Bee empties the
hand inside the draw's window. The line splits in two, and the four powers
that reshape the draw now say so in their own lines instead of relying on
Search's.

This is the migration's one deliberate change to a rendered line. The design
and the wording ledger record it.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

### Task 5: `ActionLines` loses its orphaned companion helpers

`-Wunused` sees unused *private* members only, so the five companion helpers
whose last callers Tasks 1 to 4 deleted compile cleanly and would sit there
until slice 4 moved them. Remove them now, while it is obvious which rule each
belonged to.

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`
- Modify: `src/test/scala/oathdigital/gameplay/BackendArchitectureSuite.scala`
  — only if it pins `ActionLines`' member list; check first.

**Interfaces:**
- Consumes: nothing new.
- Produces: an `ActionLines` whose companion holds exactly `action`, `plural`,
  `PlacePrefix` and `subjectCard`.

- [ ] **Step 1: Confirm each helper is unreferenced**

```bash
for name in resource isTradeGain sourceName worldCard isDelta; do
  echo "== $name"
  grep -rn "$name" src/main src/test | grep -v "gamelog/ActionLines.scala"
done
```

Expected: `resource`, `isTradeGain`, `sourceName`, `worldCard` and `isDelta`
match nothing outside `ActionLines.scala`. `BannerRules.resourceName`,
`FollowUpOffer.sourceName` and `CardPlayProcedure.worldCard` are different
members with similar names — read each hit before deleting anything.

`plural`, `PlacePrefix` and `subjectCard` must still match: `DetailLines` uses
all three, `CampaignLines` and `PowerLines` use `plural`, and
`ProcedureLog` uses `PlacePrefix`. `action` is used by `usedPower` and
`CampaignLines`.

- [ ] **Step 2: Delete the five**

From `ActionLines`' companion, delete `resource`, `isTradeGain`, `sourceName`,
`worldCard` and `isDelta`, each with its doc comment. Remove any import they
were the last user of — `Piece.Favor` and `Piece.Secrets` are still matched by
`effect`, so `Piece` stays.

- [ ] **Step 3: Check what the file is now, and say so**

```bash
grep -n "def \|val " src/main/scala/oathdigital/application/gamelog/ActionLines.scala
wc -l src/main/scala/oathdigital/application/gamelog/ActionLines.scala
```

Expected members, and nothing else: `campaigns`, `campaignLines`,
`usePowerLines`, `usedPower`, `effect`, `powerSource`, `named`, and the
companion's `action`, `plural`, `PlacePrefix`, `subjectCard`. The file should
be roughly 120 lines, down from 325 at the slice's start and 484 before
slice 2.

- [ ] **Step 4: Full gate and commit**

```bash
./sbtw test && ./sbtw frontend/test && ./sbtw frontend/fastLinkJS && python3 scripts/check-architecture.py
git add -A && git commit -m "$(cat <<'MSGEOF'
refactor(gamelog): drop ActionLines' orphaned span helpers

The five whose last callers went with the five procedure rules this slice
migrated. -Wunused does not see an unused public member, so they had to go by
hand.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSGEOF
)"
```

---

## Done when

- `ProcedureLog`'s only `ActionLineSource.Rule` entries are
  `ActionRef.Campaign` and `ActionRef.UsePower`.
- `ActionLines` holds `campaignLines`, `usePowerLines` and their helpers, and
  nothing else; about 120 lines.
- `GameLogPropertiesSuite`'s `noting` vector lists seventeen procedures.
- `declared-keys.txt` has gained exactly eighteen lines and lost none.
- Tasks 1, 2, 3 and 5 changed no rendered golden line — only sequence
  numbers. Task 4 changed exactly four files, one line becoming two in each.
- The slice-wide proof (render every golden with the sequence and section
  index stripped, at the first commit and at the tip) differs only in the four
  Search goldens.
- The design and the wording ledger record the split, the three powers' new
  lines, Trade's corrected empty-bank wording, the single Trade line, and the
  `LogWords.listed` switch above five drawn cards.
- The full gate is green on each of the five commits.

## Out of scope

- **Campaign and the deletion.** Slice 4, still blocked on the three gaps the
  design records: `NoteArg.Clauses` and the capitalisation dance, the state
  set clause cards are judged at, and whether a card a viewer cannot identify
  falls back to its slot or its back.
- **`usePowerLines`.** It is the fallback for a power with no `used` note, not
  a procedure's line. It loses its `playedAdviser` call in Task 3 and moves
  file in slice 4; it changes in no other way.
- **The tag entry and its anchors.** `ProcedureLog`'s `tag`, `anchor`,
  `opening` and `continued` are the registry design's and answer a different
  question. Search's `picksSettled` and Recover's `opensDicePool` /
  `continuedRecover` are carried over untouched.
- **Widening `NoteStates`.** Search is the one procedure that would have used
  more, and no widening would have helped: the facts it needs are in two
  different walker commands. The split is the answer, not a wider `NoteStates`.
- **`LogWords.slotted`.** Still used by `ChoiceWords` alone, and whether a
  note should fall back to a slot is slice 4's third gap.
