# Procedures Journal Their Own Notes

> Status: designed 2026-10-16, from the follow-up candidate the
> [game log procedure registry design](2026-10-16-game-log-procedure-registry-design.md)
> records under "Out of scope" as "where this module's ambition lives". It
> amends that design's ruling 3 within its own scope, and amends the
> [power log lines design](2026-09-26-power-log-lines-design.md)'s ruling 2
> and its `PowerNote` definition. It stands on the
> [replay posture decision](2026-09-24-replay-posture-decision.md) without
> changing it. No implementation is authorized by this document alone; a plan
> follows it.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **note**, **note key**,
**wording** and **declared set**. This design widens the first three from
"a power" to "a power, a game rule or a procedure", and adds one entry for
the boundary ruling 2 below draws.

## Why now

The registry design co-located every procedure's log knowledge in
`ProcedureLog` and stopped there, on purpose: "Nothing absorbs behaviour, no
interface gets smaller, and nothing is deleted." It names this candidate as
the one that can delete instead of relocate, and the deletion is real.
`CampaignLines` (243 lines), `NegotiationLines` (75) and `SetupLines` (27)
go entirely, and `ActionLines` (484) keeps only the power fallback that was
never a procedure's line.

What is deleted is not wording. It is *inference*. "Mustered {n} warbands
with {card}" is reconstructed from a `Move` out of a warband bank plus a card
read back out of an earlier answer. The procedure knew both at the moment it
acted and said nothing. Every procedure added since has paid that cost again.

The mechanism to stop paying it exists and is in use by something that is not
a power. `CardPlayRules` puts a `Note(RuleNotes.homelandDiscard, …)` into its
own tree; `PrintedRule` carries `id`, `source` and `noteKeys` for the
restrictions that are not powers either, and `RuleNotes` folds both into the
union the formatter reads. A procedure is the third such thing.

## The two prices, corrected

The registry design states this candidate's price as "a sourceless note
variant and a wire case for it; and the migration touches about fifteen
`gameplay` procedure files". Both need adjusting before anything is designed
against them.

**The sourceless variant is two fields, not one.** A note renders
`"{source}: {sentence}"` at `LogKind.Trigger`. An action line has no source
*and* is `LogKind.Action`. `PowerLines.kind` already carries the second half
for a phase power's `used` note; a procedure needs its own answer in the same
place. Stating only the prefix understates it.

**"Wording cannot live beside the procedure" is not a constraint.**
`scripts/check-architecture.py` forbids `gameplay` importing `application`
and `protocol`, which is why `LogSpan`, `LogKind` and `LogJournal` cannot go
there. `NoteKey`, `NotePart` and `NoteArg` live in `model`, which `gameplay`
imports freely and which `RuleNotes` already uses for exactly this. The
registry design's own out-of-scope paragraph says "declares its wording in
model vocabulary" and is right; the constraint it cites elsewhere does not
reach this case.

**"About fifteen" is the file count, and the procedure count is
eighteen.** `ActionLines` holds nineteen public line-builders, of which
`usePowerLines` is the power fallback and not a procedure's line. The
remaining eighteen live across fifteen `gameplay` files, because
`EconomyTree` builds Muster and Trade, `MinorActionProcedures` builds Peek,
Reveal and Move Warbands, and `CardPlayProcedure` builds the facedown
adviser's. No file is near the 800-line cap: the largest is
`CardPlayProcedure` at 414.

## Rulings

These were settled while designing and bind the rest of this document.

1. **One event, widened.** `PowerNote.source` becomes
   `Option[PowerSourceRef]`. No second note event, no second codec, no second
   ordering path. A sibling `ProcedureNoted` would duplicate `held`, `own`,
   `repeated` and `covered`, which is where the subtle bugs are.
2. **What may cross into `gameplay`.** A procedure may declare **what it
   did**: a `NoteKey`, its `Vector[NotePart]` wording, and typed `NoteArg`s —
   all `model`. A procedure may **not** name how the log lays it out: kind,
   depth, section, anchor, placement order, covering, or narration. Those
   stay in `application/gamelog`. An English sentence with typed slots is a
   statement of fact; a kind is a layout decision.
3. **The registry stays total.** `ProcedureLog`'s entries are built from
   `ProcedureRef.all` over an exhaustive match, so an entry cannot leave. A
   migrated procedure's entry changes value, and the value says which of
   three things it is (ruling 4).
4. **`lines` becomes a sealed source.** `ActionLineSource = Rule(f) | Notes |
   Silent(reason)`. `ActionLine.none` today means "posts nothing"; reusing it
   for "posts from its notes" would make a procedure whose notes were dropped
   read as correct.
5. **Narration pairs a decision with the key that tells it.**
   `narrated: Vector[Told]`, `Told(decision: String, by: NoteKey)`, on the
   entry. Deleting the key fails compilation. The policy stays in
   `application`, because "no Chose line for this decision" is layout.
6. **Byte-identical, no exceptions.** Every migration commit leaves the
   golden files unchanged. A wording improvement inside a migration makes the
   golden diff mean two things at once.
7. **Three new argument kinds and no more.** `Players`, `Clauses` over a
   closed `NoteClause` sum, and Conquest's placements. A fourth is a spec
   amendment, not a judgement call made mid-plan.
8. **`NotePart` stays flat.** No conditionals, no joins. A conditional
   template language is a second rendering engine every power author would
   have to learn.
9. **No fallback path.** When a procedure migrates, its inference rule is
   deleted outright. A per-procedure fallback for noteless runs would keep
   both paths alive forever.
10. **The `Power*` names stay.** The family has been misnamed since
    `RuleNotes` landed. Renaming 368 construction sites inside a plan whose
    proof is an empty golden diff would make every later diff harder to read.
    A ROADMAP task records it.

## Mechanism

### The optional source

```scala
final case class PowerNote(source: Option[PowerSourceRef], key: String,
    args: Vector[NoteArg])
```

`PowerLines.spoken` renders the prefix only when a source is present:

- a source, and `NoteWordings.sources` names the power → the declared name;
- a source, and no name → `words.source(ref, seen, viewer)`, unchanged;
- no source → the sentence alone, capitalised as it already is.

On the wire the field becomes optional. An old journal's `PowerNoted` carries
a source and decodes as `Some`, so nothing recorded before this change stops
loading. `FormatVersion` does not move.

### `ProcedureNotes`

A new object in `gameplay`, beside `RuleNotes`, in the shape `PrintedRule`
set: one list, read by every registry that keys a note source by id.

```scala
object ProcedureNotes:
  def id(procedure: ProcedureRef): PowerId =
    PowerId(s"procedure.${procedure.key}")
  val all: Vector[(PowerId, Vector[NoteKey])]
```

`DeclaredNotes` unions it as it already unions `RuleNotes.all`. Three things
follow with no further code: `NoteWordings.default` finds every procedure
template, the derive-note-keys walker check rejects a procedure note whose
key is undeclared, and `PowerNoteCatalogSuite`'s persisted-spelling check
covers procedure keys.

The id is derived from `ProcedureRef.key`, never spelled by hand. `key` is
the same string `ProcedureLog` already uses for a section's `action`.

### Telling a procedure note from a power's

`PowerLines` asks `ProcedureNotes` whether the note's id is a procedure's.
Not `source.isEmpty`: ruling 1 makes the source optional on a type powers
also use, and the first power that wants a sourceless note would otherwise
silently become an action line. Not `run.procedure` either: a queued
follow-up can run one procedure's note under another's run.

A procedure note renders at `LogKind.Action`. `PowerLines.kind` grows one
arm beside the `NoteKey.isUse` arm it already has.

### Three argument kinds

| Kind | Renders as | Needed by |
|---|---|---|
| `Players(ids)` | one joined phrase of player chips | Negotiation's "Negotiated with Blue and Red" |
| `Clauses(parts)` | one joined phrase of clauses | Campaign's losses |
| `Placements(entries)` | "2 warbands on Mine and 1 warband on Standing Stones" | a Conquest's gains |

`NoteClause` is a closed sum in `model`: `LostWarbands(n)`,
`BurnedFavor(n)`, `Discarded(cards)`, `SetAside(cards)`, `SentTo(site)`.
`PowerLines.argument` renders each through the `LogWords.join` the line uses
today, so the joining stays in `application` where ruling 2 puts it.

Each kind is a codec case in the walker event wire and a rejection case in
the decode test, per the typed log span wire design's posture that an unknown
argument kind is a decode error.

### `covered` and `repeated`

`LogJournal.covered(index)` answers only for a `WalkerStepRecorded`: it asks
whether a covering note follows the step. `ActionLines.musterLines` opens
with `if journal.covered(at)` so that a vow's warband note suppresses the
Muster line. Once the Muster line is itself a note, that is note-covers-note,
which `covered` cannot express. It is extended to answer for a `PowerNoted`
as well.

`PowerLines.repeated` drops a note identical to an earlier one in the same
run. Its reason is the hide hook, which fires once per hidden option — the
Circlet of Command hiding three relics reads once. Procedures have no hide
hook, and two identical procedure notes in one run are a real event. Procedure
notes are exempt.

### Where a shared builder's keys live

`EconomyTree` builds Muster's and Trade's trees from a `Kind` record that
already parameterises the decision id, the windows, the cost and the gain. A
note key is one more parameter of the same sort: `MusterProcedure` owns its
`NoteSet` and declares `mustered`; `TradeProcedure` declares `traded` and
`tradedNothing`; `EconomyTree` places the leaf and owns none of the words.
`MinorActionProcedures` does the same for its three.

## Campaign

Campaign is the only procedure whose line the mechanism could not express.

`CampaignLines.losses` builds one sentence from up to five optional clauses
joined by `LogWords.join`: "Blue lost 2 warbands, burned 1 favor, discarded
Tinker and was sent to Mine". `NotePart` is `Text | Arg | Plural` — a flat
sentence with fixed slots, which cannot drop a clause or join a list.
`NoteArg.Clauses` solves it without touching `NotePart`, in the shape
`NoteArg.Cards` already set for "a variable-length thing rendered as one
joined phrase". `gains` for a Conquest takes `Placements` for the same
reason.

Two things that looked like blockers are not.

`CampaignLines.recorded` reads **forward** to `journal.segmentEnd(at)` to
suppress an attack or defense total that a later plan overwrites, so that
only the final total is told. A note placed after the plan window closes
needs no look-ahead. The forward read is deleted, not reproduced.

`closing` aggregates over `journal.runOps(run, at)` — kills minus returns,
burns, discards, set-asides, the pawn sent. `NoteStates` has no run-wide
state and is not widened (see "Out of scope"). It does not need to be: the
Campaign tree applies those losses and knows their counts at the point it
applies them, so the procedure carries the numbers into the clauses rather
than re-reading the journal for them.

## Journals

The guarantee, stated exactly, because the slogan around it is looser than
the fact.

`PowerNoted` journals a **power id, a key name and typed arguments**. The
wording is read from the key's declaration when the log is formatted. So
changing a template changes that line **in every journal that already holds
the note**, including games recorded before the change. That is the same
property `derive-note-keys` relies on when it preserves key spellings.

It is **not** a guarantee that a game recorded before the note existed gains
the line. There is no note there to render.

The replay posture is barely engaged. Adding a note to a procedure adds a
`PowerNoted` to new journals; old ones lack it and still replay, because
`WalkerReplay` no-ops the event and never reads a note. The optional source
decodes old bytes as `Some`. Nothing here needs the standing position that
wire changes may break old journals, and nothing here breaks one.

What it does cost, accepted under ruling 9: a game already in progress when a
procedure migrates loses that line for its past actions and gains it for its
future ones. One log, inconsistent across the deploy, on a cosmetic field, in
alpha, self-healing as the game continues. The alternatives are a fallback no
one will ever retire, or a load failure spent on a missing sentence — which
the replay posture decision refuses on principle.

## What the four application files become

| File | Now | After |
|---|---|---|
| `CampaignLines` | 243 | deleted |
| `NegotiationLines` | 75 | deleted |
| `SetupLines` | 27 | deleted |
| `ActionLines` | 484 | deleted; see below |

Three files and 345 lines go outright. `ActionLines`' 484 reduce to roughly
120 that move rather than vanish, so the module loses about 710 lines net.

`ActionLines` would keep only `usePowerLines` and its helpers — the
`"Used {card}"` fallback for a power that wrote no `used` note — plus a
companion of shared span helpers other files import. That is not "the action
line each procedure posts", which is what the file is named and documented
for. `usePowerLines`, `powerSource`, `effect` and `named` fold into
`PowerLines`, which already owns the power-note path and is 100 lines; the
companion's `action`, `plural`, `resource`, `PlacePrefix`, `subjectCard`,
`worldCard`, `isDelta` and `sourceName` move to `LogWords`, where the rest of
the shared span building lives.

The end state is one sentence: powers' lines in `PowerLines`, procedures'
lines in their own trees, and `ProcedureLog` saying which.

Against the 800-line cap: `PowerLines` goes from 100 to roughly 220,
`LogWords` from 169 to roughly 210, and every `gameplay` file gains its own
`NoteSet` and leaves — `CardPlayProcedure`, the largest at 414, takes the
facedown adviser's keys and stays well inside.

`ActionLines.powerSource` is the module's last forward read to
`segmentEnd` once Campaign's is gone, and it moves with the fold. The
prefix-stability property at `GameLogPropertiesSuite:20` therefore holds over
a strictly smaller surface after slice 4.

## Tests

**Golden files are the per-migration proof.** `LogScripts` builds every
journal by real play through `GameApplicationService` on every run — "no
stored game is a fixture" — so a migrated procedure's script produces a
journal containing the note. `GoldenLog.render` records sequence, ordinal,
kind, depth, section and every span with its identity. A byte-identical
golden file therefore proves the wording, the kind, the depth, the section
and the placement all survived. `GameLogPropertiesSuite` already asserts that
every `ProcedureRef` key the formatter handles appears in some script, so
every migrated procedure is exercised.

**Six mechanism tests land in slice 1**, before any procedure migrates,
because each is a property of the mechanism and the eleven migrations of
slice 2 would otherwise run against an unproven base:

- a procedure note under resume journals once, and a new `Repeat` pass
  journals again;
- a procedure note in a branch that does not run journals nothing;
- a power's covering note suppresses a procedure note;
- two identical procedure notes in one run both post;
- the three new argument kinds round-trip through `WalkerEventCodec`, and an
  unknown kind is rejected;
- `source = None` encodes and decodes, and an old payload with a source
  decodes as `Some`.

None of these is a new script. `LogScripts.scala` is at the 800-line test cap
exactly; they are walker and codec unit tests, which is where they belong.

**Narration coverage** is unchanged in reach and stronger in kind: ruling 5
makes a deleted key a compile error, and `GameLogPropertiesSuite`'s "every
decision a script asks is told by some line" remains the guard for a key that
survives while its `Note` leaf is removed.

## Slices

1. **Mechanism, proven on Muster.** Optional `source`, `ProcedureNotes`,
   `ActionLineSource`, `Told`, the three argument kinds, `covered` extended,
   `repeated` exempted, the six unit tests, and `MusterProcedure` migrated.
   *Proves:* golden files byte-identical; the six tests pass.
2. **The twelve straightforward procedures**, one commit each: Travel,
   Take Wealth, Forge, Challenge, Place Banner Resource, Peek, Reveal, Move
   Warbands, Oathkeeper, Finish Rest, Negotiation and Setup. Each is a
   per-operation line whose facts the procedure holds where it acts.
   Negotiation is the only one needing an argument kind, `Players`, which
   slice 1 added. Setup lands in `SetupProcedure`, not a new file.
   *Proves:* golden byte-identical at every commit.
3. **The four harder ones:** Search, Trade, Recover, the played adviser.
   These are where the formatter guesses — Search's three fallback shapes,
   Trade's completion fallback, Recover's failure branch, the played
   adviser's "no delta intervened" guard. Each guess disappears rather than
   moving. The played adviser's note lands in `CardPlayProcedure`.
   *Proves:* golden byte-identical.
4. **Campaign, then the deletion.** Campaign alone in its own commit, because
   it is the only one carrying an argument kind nothing else uses and its
   golden diff must not be confusable. Then one commit deleting
   `CampaignLines`, `NegotiationLines`, `SetupLines` and `ActionLines`, and
   folding what is left into `PowerLines` and `LogWords`. *Proves:* golden
   byte-identical across both.

One, twelve, four and one: eighteen procedures, and `usePowerLines` moving
file in slice 4 without changing.

## Documents amended

Each amendment lands in slice 1, with the mechanism, not in this design's own
commit — the precedent the derive-note-keys design set for its vocabulary
entries.

- **This design amends the registry design's ruling 3** ("the log stays in
  `application`") and its resolved decision that narration is derived rather
  than moved. Both are correct within that design's scope; ruling 2 here
  states the line this one draws instead, and that document gains a pointer.
- **The power log lines design's ruling 2** ("Every line starts with the
  source's name") and its `PowerNote` definition gain a pointer: a source is
  now optional, and three argument kinds are added to its table.
- **The game log design's "Action lines" section** gains a pointer: the
  rules it describes are now where a note is placed, not how a line is
  inferred.
- **`CONTEXT.md`**: **note**, **note key** and **wording** widen from "a
  power" to "a power, a game rule or a procedure", and one entry is added for
  ruling 2's boundary.
- `docs/architecture/gameplay-modules.md` was checked and says nothing about
  where log knowledge may live; it is not amended.

## Out of scope

- **The tag entry and its anchors.** `ProcedureLog`'s `tag`, `anchor`,
  `opening` and `continued` are the registry design's, and they answer a
  different question: not "what happened here" but "where in this run are
  the band header's facts complete". The anchor reads forward within a
  segment and the cost span sums Supply across the run; neither is a
  point-in-time fact. Untouched.
- **Widening `NoteStates`.** It carries `now`, `previous` (the last leaf of
  the current command) and `answered` (the whole action). All eighteen
  procedures were checked against it and none needs more; adding a run-start
  state would mean threading it through park and resume for no consumer.
  `WalkCtx` is command-local on purpose.
- **Renaming the `Power*` note family** (ruling 10). A ROADMAP task.
- **Wording improvements**, per ruling 6. The ledger at
  `2026-10-16-game-log-wording-ledger.md` collects them and a ROADMAP task
  points at it.
- **`usePowerLines`.** It is the fallback for a power with no `used` note,
  not a procedure's line. It moves file in slice 4 and changes in no other
  way.

## Considered and rejected

- **A sibling `ProcedureNoted` event.** Rejected: `PowerNote`'s rendering
  differs by two fields, while a second event duplicates `held`, `own`,
  `repeated` and `covered` — the ordering machinery, which is where this
  module's hard cases are.
- **Deciding a procedure note by `source.isEmpty`.** Rejected: it welds two
  unrelated facts, and breaks for the first power that legitimately wants a
  sourceless note.
- **Narration moved onto the note key** (`NoteSet.key(…, tells = …)`).
  Rejected: suppressing another line is a layout policy, and ruling 2 keeps
  layout out of `gameplay`. The reference stays compiler-checked either way.
- **Growing `NotePart` with conditionals and a join** for Campaign's
  clauses. Rejected: it makes every power's template language bigger to serve
  one line, and a conditional template is a second rendering engine.
- **One note per Campaign clause.** Rejected: five lines where the log shows
  one is a player-visible change, and it forfeits ruling 6's empty golden
  diff on the slice that most needs it.
- **Keeping an inference fallback for noteless runs.** Rejected: it preserves
  both paths for every procedure, which is the deletion this design exists to
  make, and the cutover that retires it will never be scheduled.
- **Bumping `FormatVersion` to refuse old journals.** Rejected: a load
  failure spent on a missing sentence, against a decision that refuses to
  brick an openable game.
- **Improving wording during a migration.** Rejected under ruling 6; the
  ledger exists so the improvements are not lost.
- **Renaming the wire tag `"walker.power-noted"`.** Rejected: it is the one
  change here that would make an openable game fail to load.

## Consequences

- A new procedure costs a `NoteSet` beside itself and one entry in
  `ProcedureLog` saying `Notes`. No rule is written in `application`.
- Eighteen procedures stop being inferred. Three files go outright and
  `application/gamelog` loses about 710 lines net. The module's remaining
  per-procedure knowledge is the registry's declarations and nothing else.
- Two forward reads to `segmentEnd` are deleted, so prefix stability holds
  over a smaller surface.
- The note mechanism now serves three kinds of source — powers, printed
  rules and procedures — behind types named for the first. Ruling 10 accepts
  that and records where it is paid.
- `ProcedureLog.lines` states which of three things a procedure is, so a
  procedure that silently stopped writing its line is distinguishable from
  one that never wrote one.
