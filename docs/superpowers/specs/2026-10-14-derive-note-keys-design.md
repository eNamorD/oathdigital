# Derive Note Keys From Their Declarations

> Status: design confirmed 2026-10-14, from architecture review candidate C
> ("Derive noteKeys", rated Strong) against the code as it stands at
> `3dcbfd9a`. It settles how a power's note keys stop being a hand-written
> list and start being derived from the keys themselves, what happens when a
> key still escapes, and the order the change lands in. It was implemented on
> 2026-10-14. It amends neither the
> 2026-09-25 game log design nor the 2026-09-26 power log lines design: the
> wire format, the wording rules and the placement rules are unchanged.

## Why now

The 2026-09-26 power log lines design says "Each power declares a template
for each key it emits, beside its own code". That is a convention, and
`NoteWordings.scala:8-14` states the posture it leaves behind:

> A note whose power or key is missing here renders nothing.

Warning Signals was fixed three times. Its arithmetic was never touched. One
of the three fixes, `a0d2d0c1` ("Warning Signals writes the end-of-Campaign
discard line"), added a single identifier to a list. The card was discarding
correctly the whole time; the player's log simply had a line missing, and
nothing in the build, the tests or the server said so.

## What is actually broken

The candidate's premise needs narrowing before it can be designed against.

A wording already travels with its key. `NoteKey(name, template)`
(`model/PowerNotes.scala:58`) holds both, and `NoteKey.apply` is how a note
is built, so a key cannot exist without a template. "A note with no wording"
is already unreachable.

The gap is one step along. A key must *also* appear in the power's
`noteKeys` (`powerresolver/ContributingPower.scala:260`, base
`Vector.empty`), the list `NoteWordings.default` folds into its lookup
table. Emit a key that is not in that list and
`NoteWordings.template` returns `None`, `PowerLines.spoken` returns `None`,
`PowerLines.line`'s for-comprehension yields nothing, and that one entry is
omitted from the log. The rest of the log renders.

So the defect is a **duplicated declaration**: a key is defined in one place
and relisted in another, and the two can disagree silently. There are 206
`noteKeys` overrides. Roughly 75 of them relist a key owned by another
object; `PlanDiscard.discarded` alone is relisted by 12 powers, seven of
which also carry a byte-identical `later` body.

### Why this cannot be a compile error under `-Werror`

A note is emitted by a closure: `Note(power, NoteStates => Option[PowerNote],
covers)` (`model/PowerNotes.scala:83`), built inside `Branch` or `BuildOps`
from live game state. Of the 368 `Note(` construction sites, about 64 branch
between keys inside the closure. Nothing static says which key a given
closure will return, so no compiler flag and no exhaustivity check reaches
this. Compile-time safety is available only by making an unregistered key
*unobtainable* — a change at the construction site, not a warning setting.
This design takes that route for keys a power owns, and accepts an explicit,
checked declaration for keys it borrows.

### What the tests already catch

`NoteText.sentence` (test helper) renders `<no template for {key}>`, so a card
suite that asserts the text of the missing line does fail. Warning Signals
escaped because no suite asserted its discard line, not because the mechanism
was unobservable. `PowerNoteCatalogSuite` validates properties of the keys a
power *does* declare and cannot see one it omits.

## Decisions

| # | Decision | Choice |
|---|----------|--------|
| 1 | Failure posture | Derive the registry, so the own-key failure mode stops existing. A loud check backs up the residue. Not a compile error for borrowed keys. |
| 2 | How a key is created | Through the power's own set: `NoteSet`, named in `model/PowerNotes.scala` beside `NoteKey`. Declaring and defining become one act. |
| 3 | Borrowed keys | Explicit `include` of the owner's set, plus evidence in the signature of the five helper families that build the note themselves. |
| 4 | Registration mechanism | A buffer filled as the owning object initialises, read through a `def`. Guarded by the snapshot in decision 7, which fails loudly on a partial set. |
| 5 | Production behaviour on an undeclared key | The entry stays omitted and the server logs an error. The formatter never throws and never shows internal text to a player. |
| 6 | Test behaviour on an undeclared key | The walker rejects it where the note is journaled, so any scripted journal that reaches the path fails. |
| 7 | Proof the rewrite is inert | A committed snapshot of `power id -> sorted key names`, taken before anything moves, byte-identical after every slice. |
| 8 | Scope | Note keys only. `noteSource` and `narratedDecisions` keep their own overrides. |
| 9 | Second idiom | Closed: the bare `NoteKey` constructor is restricted once the rewrite lands. |
| 10 | The 12 `PlanDiscard` relists | Fixed first, by a trait supplying the key to all 12 and the shared `later` body to the seven that discard unconditionally. One section here, no spec of its own. |
| 11 | Vocabulary | "Note", "note key", "wording" and "declared set" are added to CONTEXT.md's `## Language`. |

### Why not a compile error everywhere (decision 1)

Making every key unobtainable unless registered *for the emitting power*
would close the hole completely. It also rewrites the signature of every
shared helper and every bare borrowing, against one observed escape in the
history. The derivation removes the duplication that caused the escape for
the roughly two thirds of declarations that own their keys, and leaves a
smaller, explicitly written residue that decision 6 checks. A compile error
claimed for two thirds of cases is worse than a check that covers all of
them, because the claim is what stops people looking.

### Why the formatter does not throw (decision 5)

The Log pane is player-facing and the formatter runs per viewer. Throwing
turns one missing line into a dead pane for that game. That is a worse bug
than the one being fixed, and it contradicts the game log design's rule that
every viewer receives the same entries. The player keeps seeing a complete,
if slightly shorter, log; we see an error.

## Mechanism

### `NoteSet`

`NoteSet` lives in `model/PowerNotes.scala` (85 lines today, well under the
800-line cap) beside the types it registers.

```scala
final class NoteSet private (power: PowerId):
  def key(name: String, template: Vector[NotePart]): NoteKey
  def include(other: NoteSet): Unit
  /** Every key this power owns or includes. The snapshot sorts by name, so
    * where an `include` sits in the object body does not matter. */
  def declared: Vector[NoteKey]
object NoteSet:
  def apply(power: PowerId): NoteSet
```

A power's companion holds one `NoteSet` and creates its keys through it:

```scala
object WarningSignals:
  private val notes = NoteSet(id)
  /** "{Blue} redistributed their warbands." */
  val redistributed: NoteKey = notes.key("redistributed", Vector(
    NotePart.Arg(0), NotePart.Text(" redistributed their warbands.")))
  notes.include(PlanDiscard.notes)
  def noteKeys: Vector[NoteKey] = notes.declared
```

The key's `name` is unchanged, so every journaled spelling survives. That
matters: `PowerNoted` records the key, and the formatter looks a saved game's
wording up by it.

### Initialisation order

`key` appends to a buffer as the owning object's initialiser runs, so
`declared` is complete only once that initialiser finishes. Three rules keep
that honest, and the snapshot enforces all three:

- `noteKeys` is a `def`, never a `val`. It is called at catalog build time,
  after the companion is initialised.
- Keys are plain `val`s in the object that owns the `NoteSet`. A `lazy val`
  key would never register.
- A key declared in a trait body initialises per mixin and is therefore not
  allowed; such a key belongs to a shared object and is included.

A partial set is a snapshot mismatch, which fails the build. It is not a
blank line in anyone's log.

### Borrowed keys

Two kinds, treated differently.

**Helper-owned**, where no power owns the key: `PlanDiscard.discarded` (12
powers), `NoteSupport.took` (5), `RelicDraws.drew` (4), `RulerToll.keys` (3),
`RegionDiscard.none` and `RegionDiscard.discarded` (3 each),
`SparedForce.spared`, `EdificeSetupSupport.discarded`, `RollResults.rolled`,
`VisionPlay.noFaceup`, `CardPlayRules.discardFirst`,
`BannerFacePowers.printed`. Each helper owns a `NoteSet` with no power behind
it, and a borrowing power calls `include`. For the five helpers that build
the note themselves — `PlanDiscard`, `RelicDraws`, `RegionDiscard`,
`EdificeSetupSupport`, `NoteSupport` — the builder's signature takes evidence
of that inclusion, so forgetting it does not compile. This is exactly where
the 12-relist bug lived.

**Power-owned and reused**, where another power already owns the key:
`Outriders.ignored` (4), `VowOfWandering.forbidden` (4), `WildMounts.swapped`,
`TollRoads.*`, `WarTortoise.*`. These are borrowed as bare keys with no call
to attach evidence to, so they stay an explicit `include`. There are about
ten such sites and decision 6 covers them.

### The end-of-Campaign discard

The 12 powers whose card says "At end, discard" relist
`PlanDiscard.discarded` and, seven of them, an identical `later` body. A
`DiscardedAtEnd` trait over `BattlePlan` supplies both, and leaves each power
expressing only what differs from the standard: its own keys, a condition on
the discard, or a second window. This is slice 2 and depends on nothing
else.

### One declared-set union

The union of walker powers, phase powers and `RuleNotes.all` is re-derived
by hand in two places today: `NoteWordings.default`
(`application/gamelog/NoteWordings.scala:33-40`) and
`PowerNoteCatalogSuite:11-15`. The walker's check needs the same union, and
`application` sits downstream of `gameplay`, so the union must live in
`gameplay`. A single `DeclaredNotes` there becomes the one owner, consumed by
`NoteWordings`, by the walker check and by the catalog suite.

### The walker check

`PowerNoted` is constructed at three points: `ProcedureWalker.scala:303`,
`AdviserArrivalPass.scala:53` and `BurnTakes.scala:35`. All three hold the
powers already — `WalkCtx.powers` for the first two, `replace`'s own
parameter for the third — so the check goes in one helper that all three
call, not three copies of it.

The helper rejects a note whose `(power, key)` is absent from
`DeclaredNotes`. In tests and development that is a failure at the point of
emission, so any scripted journal reaching the path fails whether or not the
suite asserts the line's text. In production it logs an error and omits the
entry, per decision 5.

## Delivery

Six slices. Each is independently revertible, and slices 2 to 6 carry the
same one-line proof, which is why the snapshot is taken first.

1. **Snapshot and vocabulary.** Commit `power id -> sorted key names` as a
   fixture and generalise `PowerNoteCatalogSuite`'s persisted-spelling check
   from two hand-picked powers to every power. Add "note", "note key",
   "wording" and "declared set" to CONTEXT.md's `## Language`, pointing at
   the 2026-09-26 design. *Proves:* green against untouched code, so the
   baseline predates every later slice.
2. **`PlanDiscard` trait.** One trait supplies `PlanDiscard.discarded` to
   all 12 powers and the end-of-Campaign `later` entry to the seven that
   discard unconditionally. The other five keep only what differs: a
   condition on the discard (Hearts and Minds, Traveling Doctor, Mountain
   Giant) or a second window (Rain Boots, Lancers). Deletes 12 relists and
   seven identical bodies. *Proves:* snapshot byte-identical.
3. **Mechanism.** `NoteSet`, `DeclaredNotes`, the walker helper and the
   production error log, applied to two powers only. *Proves:* a deliberately
   undeclared key fails a test at emission; snapshot byte-identical.
4. **Own keys.** The roughly 131 declarations that borrow nothing, one commit
   per power directory. *Proves:* snapshot byte-identical at every commit.
5. **Borrowed keys.** `include` at the roughly 75 borrowing sites, and
   evidence in the five helper families. *Proves:* snapshot byte-identical,
   and deleting an `include` fails to compile for those five.
6. **Close the door.** Restrict the bare `NoteKey` constructor so a second
   idiom is unreachable. *Proves:* `-Werror` build green.

The plan may merge slices when it sizes them, but slice 1 stays first and
slice 6 stays last.

## Out of scope

- `noteSource` and `narratedDecisions`. Neither has a missing-declaration
  failure mode: a power with no source falls back correctly, and the
  2026-09-27 prune ledger already deleted the tautological test that asserted
  `narratedDecisions`. Folding them in widens the rewrite for tidiness.
- Any wording, placement or wire change. The two game log designs stand.
- Old journal compatibility beyond key spellings. Spellings are preserved
  because the formatter reads them from saved games; nothing else about the
  journal moves.

## Risks

- **A partial declared set from initialisation order.** Mitigated by the
  three rules above and caught by the snapshot, which turns it into a build
  failure.
- **Slice 4's size.** 131 files of mechanical edits. Mitigated by one commit
  per directory with the snapshot proving each inert, so a bad commit is
  found at the commit that caused it.
- **The roughly ten bare power-to-power borrowings stay unchecked at compile
  time.** This is the design's accepted residue; the walker check is what
  covers it, and its reach is the reach of the scripted journals.
