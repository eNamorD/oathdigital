# A table continued from a state still checks its cards

Date: 2026-10-21. Status: approved design.

This is candidate K of the 2026-10-13 architecture review ("one driver for
'run this card to its ruling'"), re-inventoried at `7b6b2659` and narrowed to
the defect the evidence supports.

## Problem

`Table` validates what it builds. [`Table.ready`](../../../src/test/scala/oathdigital/testkit/Table.scala)
checks that no card is lost or in two places and that no force outnumbers its
printed supply, and it fails the test naming what is wrong. `Table.unchecked`
returns the same state with nothing checked.

`unchecked` is used 195 times across 89 files. It grew by 7 in the week before
this design. 182 of the 195 (93%) read a table entered through `Table.from`,
13 do not: 5 economy suites read the site map off `Table.start`, 6 chain off a
local helper that returned a `Table`, and 1 is `TableSuite` proving that
`ready` refuses a broken table.

That distribution has one cause. `Table` holds the cards that joined it since
`Table.start`, and checks the state against the first game's inventory plus
those. A `Table` entered through `from` holds a `ReadyGame`, which carries no
record of what joined it earlier, so `from` seeds that set empty and `ready`
would report every earlier arrival as a missing card. `from`'s own doc says
so, and sends its callers to `unchecked`.

So `unchecked` is not an escape hatch that grew into an interface by neglect.
It is the only exit `from` offers, and 182 states are assembled with no
card-wholeness check at all because of it. 130 of the 195 sites sit directly
in a test body; the other 65 sit in 57 helper definitions.

The review proposed one driver that takes a card and runs it to its ruling, so
that the common case stops needing a fixture. That does not touch this defect:
what the 182 sites need is to continue from a state someone else built and
still be checked, which no driver provides. It is also already built twice —
`PhasePowerHarness` is imported by 137 files, `ActPowerHarness` by 96, and 38
suites import both. Consolidating those two is real work with its own
evidence, and it is a separate candidate.

One thing has changed since the review that makes this cheap. The
[test speed design](2026-10-07-test-speed-design.md) shipped: `OathGame`
carries `lazy val cardIndex`, and `CardIndex.from` reads it. Indexing a state
the testkit already holds now costs no scan.

## Goal

Every table state is checked, whoever assembled it. `unchecked` stops being an
interface.

## Scope

In scope: the card set `Table` checks against, how `from` seeds it, the sweep
of the 195 call sites, the `munit.Location` clauses that sweep needs, two
geography accessors for the five map-reading suites, and `TableSuite`
coverage.

Out of scope:

- consolidating `PhasePowerHarness` and `ActPowerHarness` (its own candidate);
- the per-card setup repetition the review called for a driver to solve (the
  other half of candidate K, judged on its own evidence afterwards);
- any change to `src/main`;
- test speed.

## Decisions

1. **`Table` carries the cards it expects, not the cards that joined it.**
   The private `joined: Set[CardId]` becomes `expected: Set[CardId]`, and the
   fixture-wide `inventory` stops being a second input to the check.

   | | today | after |
   |---|---|---|
   | `ready` | `CardIndex.from(game.game, inventory ++ joined)` | `CardIndex.from(game.game, expected)` |
   | `moving` | `if inventory(card) then joined else joined + card` | `expected + card` |
   | `start` | `Table(quiet(fresh), Set.empty)` | `Table(quiet(fresh), <its own index's ids>)` |
   | `from` | `Table(ready, Set.empty)` | `Table(ready, <the incoming index's ids>)` |

   `ready` then means: whole relative to where this table started. For a table
   built from `Table.start` that is exactly today's check, since `start`'s own
   ids are the first game's inventory. For a table entered through `from` it
   is the check that was missing.

2. **`from` seeds from the cached index.** It reads `ready.game.cardIndex`,
   the `lazy val` on the state it was handed, so seeding costs no scan. When
   that index is already `Left` — the incoming state has duplicates — the
   expected set seeds empty. Duplicates need no expected set to be reported,
   so `ready` still names them, and no special case is needed.

3. **Both of `ready`'s checks run on a `from` state.** The warband-supply
   check is absolute, not relative to where the table started, so it carries
   over unchanged. It has never run on these 182 states. If a state reached by
   real play fails it, that is a production bug — more warbands in play than
   are printed — and it is reported as a finding, not exempted.

4. **`unchecked` becomes `private[testkit]`.** One caller survives on purpose:
   the `TableSuite` test that reads a deliberately broken table to prove
   `ready` refuses it. Its doc says that is what it is for. It stops being an
   interface the moment no suite outside the testkit can reach it.

5. **The 56 helper definitions gain `(using munit.Location)`.** Of the 57
   helper definitions holding an `unchecked`, 56 lack the clause. Without it
   `.ready` still compiles — munit derives a `Location` at the helper's own
   line — but a failure points at the fixture instead of at the test that
   built the bad state. A card-wholeness failure names a card, and the reader
   needs the test that placed it. Every caller resolves the clause
   automatically, so no call site changes for it.

6. **A site that fails the new check is fixed, never re-exempted.** Two shapes
   are expected. A state that is simply wrong gets corrected. A helper that
   removes something no `Table` step can remove gets the step:
   `PhasePowerHarness.withoutAdvisers` bypasses `Table` entirely today with
   the comment "No Table step removes advisers", and an adviser-removing step
   serves it and any suite like it. The count of sites that failed, and what
   each turned out to be, is recorded with the work.

7. **Two geography accessors replace the five `Table.start.unchecked` sites.**
   Five economy suites read the site map purely as catalog geography, each
   repeating the same two `find` calls to derive "another site in the pawn's
   region" and "a site in another region". They become named accessors beside
   `CatalogNames`, named for what the suites already call the values. A fact
   five suites need is a step.

8. **Nothing is added to `src/main`.** The alternative was to expose the
   comparison as a `CardIndex` API the testkit calls. It would have no
   production caller, which the project's testing doctrine calls dead code to
   delete, and the testkit version is less state than today rather than more.

9. **Two commits.** The `Table` change with its `TableSuite` coverage, then
   the sweep. The sweep changes no assertion: `.unchecked` becomes `.ready`,
   56 definitions gain a clause, and the five map readers move to the
   accessors.

## Behavior proof

- The gate (`./sbtw root/test frontend/test`) is green before the sweep and
  after it. No assertion changes in the sweep, so a failure is either a state
  the new check caught or a mistake in the sweep, and the two are told apart
  by reading the failure.
- `TableSuite` gains tests that the new check actually catches what it claims:
  a table entered through `from` that loses a card fails and names it; one
  that takes a card out and puts it back passes; a card that joined before the
  `from` is not reported missing after it; and a table entered through `from`
  whose state already holds a duplicate is named as a duplicate.
- The sites that failed the new check are listed with what each turned out to
  be.

## Measurement

- `.unchecked` occurrences: 195 before, 1 after (the `TableSuite` test), none
  reachable outside the testkit.
- Gate wall-clock and CPU before and after. The check reads a cached index, so
  the expectation is no change; the number is recorded to confirm it.

## Process

The work runs inline in a worktree branched from local `main` at `7b6b2659`,
as the two commits of decision 9, and merges into local `main`. No separate
implementation plan: the reasoning is this document, the `Table` change is
four lines, and the sweep's correctness is proved by the gate rather than by
review judgment.
