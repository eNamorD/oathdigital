# The three power harnesses share their core, and keep their names

> Status: designed and implemented 2026-10-23. This is the follow-up the
> [card driver decision](2026-10-22-card-driver-decision.md) named as "the
> consolidation of `PhasePowerHarness`, `ActPowerHarness` and
> `WhenPlayedHarness` — a separate candidate with its own numbers". It
> records the numbers, the two shapes considered, and why the three
> harnesses keep their names.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **table** and
**situation**. The testing doctrine is the `scala-testing` skill: DAMP over
DRY, every test earns its place, a test states every fact it relies on.

## The numbers, at `62f9a117`

| measure | value |
|---|---|
| suites using `PhasePowerHarness` | 131 |
| suites using `ActPowerHarness` | 95 |
| suites using `WhenPlayedHarness` | 28 |
| suites using two or more | 63 (PPH+APH 37, PPH+WPH 26, APH+WPH 0) |
| files referencing any of the three | 197 |
| lines in the three harness files | 443 |

Two numbers the decision record did not have decided the shape:

- All 26 suites that import both `PhasePowerHarness` and `WhenPlayedHarness`
  import the first for one member, `ops`, a one-line alias of
  `RecordedOps.all`.
- Of the 58 suites that use only `ActPowerHarness`, 44 use one member,
  `replayed`, with rules they build elsewhere. That harness's main job today
  is "replay these events through these rules", not driving an act power.

The one measured cost of three copies: `ce866c9a` (the undeclared-note check)
added the same `declared = Some(DeclaredNotes.of(catalog))` line to all three.

## What is the same, and what is not

Accidents of three authors on one day (2026-09-20): `pick` and `chooseOne`
have identical bodies; `ops` and `said` are aliases of testkit members that
164 suites already call directly; the rules-path replay fold is written twice
with two return shapes; `parkedWithWalkerPowers` is
`PowerRules.parkedUnderWalkerAndPhasePowers` by another name; `act` has no
callers; `withoutAdvisers` bypasses the `Table` with one caller.

Essential differences, which no shared verb could hide without a `match`
inside the helper: the phase and act harnesses go through `OathRules`
(journaled walk, `OathState`, projection reads, a parked decision rebuilt by
`ParkedDecisionAssertions`), while the when-played harness walks a bare hook
on a `ReadyGame` and replays operations, because a when-played power fires
inside a card play and the suite isolates the hook. The act harness threads
`rules` per call so a suite can script dice; the phase harness has one fixed
`rules` and the projection reads.

So the split is two-way — rules path and bare-hook path — and the three
objects are two conventions plus a dice variant, not three copies of one
thing.

## Two shapes

**One `PowerHarness` with three start verbs.** Import rename across 197
files; one object carrying two actor conventions, two state types and two
replay mechanisms under shared verbs. `replayed` would need three overloads,
and a name that covers both the rules fold and the operation pipeline no
longer says what it builds. The package dependency inverts: when-played types
land in `testkit`. Under the deletion test it is co-location, not
consolidation: the extra complexity (overload families, qualified doc
comments) exists only because of the merge.

**Keep the three names, move every accidental duplicate to the testkit
object that already owns it.** `Journal.evolved` holds the rules-path fold;
`DecisionAnswers.chooseOne` the answer shortcut; `PowerRules.declared` the
declared-note set; `RecordedOps.all` and `NoteText.said` are reached by
`export` or a one-line delegation; `Table.withoutAdvisers` replaces the
bypass; `act` goes. Zero call sites move. Under the deletion test, deleting
`Journal.evolved` makes two folds reappear, so it earns its place; deleting
the exports changes 26 import lines, which is a rename, not complexity.

The second shape is the one built.

## What landed

Six commits, each green under `-Werror`, none touching production code:

1. `ActPowerHarness.act` deleted (0 callers). `Table.withoutAdvisers(owner)`
   added on the existing `worldDeckBottom` step, so the Table's own card
   check stays whole (decision 6 of the
   [table-from-checks design](2026-10-21-table-from-checks-design.md));
   `PhasePowerHarness.withoutAdvisers` deleted; one `TableSuite` test.
2. `Journal.evolved(rules, from, events)` in the testkit's `Journal`; both
   harnesses' `replayed` delegate to it, each keeping its return shape. A
   `JournalSuite` test proves `evolved` from a midway state agrees with
   `replayed` from no game.
3. `DecisionAnswers.chooseOne`, exported as `pick` and `chooseOne`;
   `WhenPlayedHarness` exports `RecordedOps.all as ops` and no longer imports
   `PhasePowerHarness`; `parkedWithWalkerPowers` is
   `PowerRules.parkedUnderWalkerAndPhasePowers`.
4. `PowerRules.declared`, used by all three harnesses: the `ce866c9a` line
   lives once.
5. 25 when-played suites read `ops` from their own harness; `-Werror`'s
   unused-import check proved the scripted edit. `ConspiracyWhenPlayedSuite`
   stays as it was: it imports by name and holds a local `val ops`.
6. `ActPowerHarness.usableIds` says in its doc comment that it ignores the
   printed restriction set, unlike `PhasePowerHarness.usableNow`. No
   behaviour change.

The three harness files went from 443 lines to 417; 26 cross-package imports
went. Test count 4386 → 4388.

## What is deliberately not done

- `said` stays a one-line delegation in `WhenPlayedHarness` and `PlanDriver`
  rather than an `export`: `NoteText.said` is overloaded on `PhasePower` and
  `(PowerId, keys)`, the harness form takes `ContributingPower`, and powers
  that extend both would make an added overload ambiguous.
- `replayed`'s two return shapes stay. 66 call lines compare against
  `Right(done.state)` and 81 files compare a `ReadyGame`; changing either is
  a sweep for taste.
- `ActPowerHarness` is not folded into `PhasePowerHarness`: its reason to
  exist is `rules` as a parameter.
- No caller-facing verb is renamed (`use`/`play`/`fire`, `answer`/`resume`,
  `awaits`/`question`). Each is the vocabulary of its path.
- `WhenPlayedHarness` stays in `gameplay.powers.whenplayed`: it depends on
  `CardPlayedFaceup`, `ContributingPower` and `PowerFixture`, and its users
  sit in that directory.
- `usableIds` keeps ignoring the printed restrictions. Giving it
  `usableNow`'s argument would change the meaning for up to 15 suites, each
  to be read by hand; that is a separate change if anyone wants it.
