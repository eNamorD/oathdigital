# Service and game-log start states

Date: 2026-09-28. Status: implemented (see Result).

This is the third of five test-suite improvement projects from the
2026-09-27 test audit. The [Table builder](2026-09-27-test-table-builder-design.md)
and the [low-value prune](2026-09-27-prune-low-value-tests-design.md) are
merged. Still queued after this one: a readability sweep, and speed.

## Problem

Rule tests start from a built `Table`. Service and game-log tests still reach
their start by driving the whole first-game setup through
`GameApplicationService`, about 25 events, and then patching the result:

- `ParkedServiceFixture.setUp` runs setup through the journaled adapter. Its
  callers fix facts by reordering the deal (`withWorldDeckTop`), reordering the
  Chronicle's atlas, and journaling `Arrange` steps (`Move`, `Kill` and
  `SetOathkeeper` operations). An `Arrange` step cannot set the phase or the
  turn.
- `ForgeWalkerFixture` plays real commands to reach a Forge: a Campaign
  conquest with fixed dice, three Searches and a Rest.
- `LogScripts` builds 26 scripts this way, for 13 game-log suites (about 95
  tests).
- `GameApplicationServiceSuite` calls `execute` (setup through the service) in
  24 of its 36 tests.

These tests inherit the setup's facts without stating them: p2 goes first, the
pawns sit at Ancient City, Broken Peaks and Buried Giant, each player keeps the
first card dealt, the first Wake ends by itself when it has nothing to decide,
and the journal is about 25 events long before play begins. Service tests
assert absolute sequences (29L, 32L). Every one of the 52 golden logs opens
with the setup lines.

The table-builder project's brittleness check (disabling the Wake auto-end)
still fails 8 service, log or `Situation` tests for this reason.

Setup replay is also the slowest part of these suites. Every journaled command
reloads and replays the whole stream, so the setup is re-folded on every step.
The game-log suites take about 27 s of CPU and `GameApplicationServiceSuite`
about 9 s.

A built start cannot reach the service today. A journal must begin with
`GameStarted` (replay folds from `NoGame`, and every other event is rejected
there), and no codec exists for a whole `ReadyGame`.

## Decision

Give `GameApplicationService` a test-only replay origin. A production event
that carries a whole arranged position was considered and deferred: it needs a
`ReadyGame` wire codec, a log line, projection handling and a decision on who
may start one. It is recorded in `docs/ROADMAP.md` as the arranged-start event
for game save and load, and it would grow from this seam. Keeping setup replay
and pinning each fact was also considered; it cuts brittleness but not cost,
and it keeps the patching indirection.

## Design

### The seam

`GameApplicationService` gains one parameter,
`genesis: OathState = OathRules.initialState` (`NoGame`).

- `reconstruct` and `scan` fold from `genesis`. A small `EventEvolution`
  wrapper overrides `initialState`, so `EventReplayEngine` is unchanged.
- With no stream and `genesis` equal to `NoGame`, `handle` accepts only
  `Begin`, as today.
- With no stream and a `Ready` genesis, `handle` runs any non-`Begin` command
  at sequence 0 against `genesis`, expecting the stream not to exist. `Begin`
  is rejected: `GameStarted` on a started game is already `GameAlreadyExists`.
- With no stream and a `Ready` genesis, `load` returns the genesis at
  sequence 0, so a test can read or project the start before its first
  command. `history` of a missing stream stays `None`.

A `BackendArchitectureSuite` check asserts that no code under `src/main`
passes `genesis`. The seam is for tests only, until the arranged-start event
replaces it with a journaled one.

### The game log's opening

`GameLogFormatter` writes the "Setup" headline for `GameStarted`, and the
Round, Turn and Phase headlines for `RoundEnded`. A stream that starts from a
built state has neither. When the journal's first event is not `GameStarted`,
the formatter opens with the Round, Turn and Phase headlines read from the
state before event 0, through the same helpers `RoundEnded` uses. The rule is
production code; it is harmless, and it is what the arranged-start event will
need.

### Testkit

`table.situation(driver)` stops rejecting the journaled adapter. A journaled
situation from a table builds a fresh in-memory service with
`genesis = Ready(table)`, the given ports and the event wire codec; its events
start empty and its sequence at 0. `after` and `parkedAfter` work unchanged,
and every command goes through the service, the wire codec and replay. The
plan settles the exact names.

`SituationSuite` gains a case showing that the rules adapter and the journaled
adapter agree when both start from the same table.

`Table` gains a `relicDeckTop` step (the Forge and Family Heirloom scripts
need the relic deck's top). Its docstring's claim that Ancient City's River
gives a Wake option is corrected: River needs another River in play, and the
fixture map has none.

## Scope

**Moves to a table start:**

- `LogScripts`: 25 of the 26 scripts. Each script states its board as `Table`
  steps (advisers, pawns, the world-deck top, the turn), replacing
  `withWorldDeckTop`, `topOfWorldDeck` moves, `arrangedForNext` and `Arrange`
  steps. The 13 game-log suites that consume them follow.
- `ParkedServiceFixture`: the League Treaty, Recover choice, Oathkeeper tie
  and Silver Tongue parks are rebuilt on tables. `setUp` and `execute` go.
- `ForgeWalkerFixture`: the walk to a Forge becomes a table with the Forge
  site and the needed cards placed. `mixedForgeCostCatalog` stays.
- `GameApplicationServiceSuite`: the 24 tests that call `execute`. Absolute
  sequences become small local numbers the test can explain.
- `PhasePowerProjectorSuite`, `WalkerDecisionProjectionSuite` and
  `PendingWalkerInvariantSuite`: the tests that use those parks.
- 50 goldens (25 scripts, actor and other views) are rewritten. They open with
  the genesis headlines and have no setup lines.

**Stays on replay, because it tests the real start:**

- The `woken` script and its two goldens: the one end-to-end golden, from the
  real setup lines through Round 1.
- `GameLogSetupSuite` and `SituationSuite`'s replay checks.
  (`GameLogStartLineSuite` tests procedure start lines such as "Started
  Travel", not `GameStarted`; it follows the scripts it reads.)
- HungerSuite's "a Hunger revealed in Setup's Reveal Cards runs at the first
  Wake", which keeps `withWorldDeckTop`.
- `GameApplicationServiceSuite`'s `Begin` and bootstrap tests, its
  all-Exile game to round 8 (the full-game smoke test), its setup-length
  check, and its Salt Flats Recover (Salt Flats is not in play on the fixture
  map, and no Table step changes the map).
- The route, bootstrap and provisioning suites. They issue only `Begin`.

**Deleted once empty:** whatever of `ParkedServiceFixture` and
`ForgeWalkerFixture` nothing uses any more, and the `Arrange` step if no
remaining test uses it.

## Verification

- **Brittleness.** Rerun the table-builder check: disable the Wake auto-end.
  The only service or log failures left must be tests about the auto-end
  itself or tests that stay on replay (`woken`, `SituationSuite`, the Hunger
  Setup test). Record the counts.
- **Sequence independence.** Add one extra event to setup (for example a
  no-op walker step). Only the tests that stay on replay may fail. A migrated
  test that fails still depends on the setup's length.
- **Goldens keep their meaning.** Rewrite with `GAMELOG_GOLDEN=write`, then
  review each diff by hand. Every change must be a dropped setup line, a
  renumbered sequence, the new opening headlines, or a board fact the script
  now states differently. Any other changed line is a regression. The Result
  lists each golden whose post-setup lines changed, and why.
- **Speed.** Record per-suite CPU from `target/test-reports` before and after
  for the game-log suites, `GameApplicationServiceSuite`, and the three
  projector and invariant suites.
- **DAMP.** Each migrated script or test states its start as `Table` steps in
  its own body, with no deal reordering or arrange indirection.
- **Gate.** `./sbtw root/test` and `./sbtw frontend/test` are green at the end
  of every task.

## Tasks

The [plan](../plans/2026-09-28-service-log-start-states.md) refines these
into seven tasks: the log scripts move in two batches, and the two log
scripts that reuse the parks move with the parks. Each task ends green and is
its own commit.

1. **Seam.** The `genesis` parameter, the architecture guard, the formatter's
   opening headlines, and the journaled `table.situation`. Tests: the
   adapters agree from a table, and a table-started log opens with its
   headlines. No migration yet.
2. **Parks.** Rebuild the `ParkedServiceFixture` parks on tables, moving
   `PhasePowerProjectorSuite`, `WalkerDecisionProjectionSuite` and
   `PendingWalkerInvariantSuite`.
3. **Service suite.** Move `GameApplicationServiceSuite`'s `execute` tests,
   including the sequence tests.
4. **Forge.** Rebuild `ForgeWalkerFixture` on a table; its service and
   projection users follow.
5. **Log scripts.** Move `LogScripts` in two or three batches by family
   (actions, powers, negotiation). Each batch rewrites and reviews its goldens.
6. **Clean-up and Result.** Delete emptied fixture code, run the brittleness,
   sequence and speed checks, record the Result here, and update the
   **Situation** entry in `CONTEXT.md` to say a journaled situation may also
   start at a Table.

## Result

One commit per task moved the tests, with two small follow-ups from the
reviews. The root suite has 2114 tests and the frontend 466, all green after
every commit.

**What changed beyond the design.**
- `Table.banditsRefilled`. The quiet table has empty sites, so a journaled
  first command also journals the bandit refill (`BanditsRefilled`). A test
  that counts a command's events, or a script whose log is read, starts from a
  table that ends with this step. It fills only empty in-play sites that have
  a printed capacity, as `StateBasedEvaluation.banditRefill` does.
- `Table.relicDeckTop`, for the Forge and Family Heirloom scripts.
- `LogScripts.atTable(name, table, dice)`: a fresh service begun at a table,
  and the journaled situation there.
- `LogScripts.board`: a short table journal (a relic at Broken Peaks, a
  homeland edifice at Deep Woods, a facedown adviser each with p2 and p3, p1
  in Act). `GameLogRareLinesSuite`, `GameLogEventSuite`,
  `GameLogCampaignSuite`, three `GameLogHeadlineSuite` tests and the banner
  test in `GameLogExchangeSuite` appended synthetic events to the replayed
  `woken` game and leaned on its setup facts. They read `board` now.
  `PresentationLabelsSuite` reads its own small table.
- `SituationDriver`'s `Arrange` appends with `MustNotExist` when it is a
  table journal's first record.
- A table journal's opening headlines (`GameLogFormatter.opening`) and
  `Table#service` are the design's seam, as specified. `GameLogStartLineSuite`
  tests procedure start lines, not `GameStarted`, so it moved with its
  scripts instead of staying on replay.

**Brittleness.** Disabling the Wake auto-end (`wakeOptionOpen` always true)
fails 21 tests, down from 24 before the table-builder project's service and
log tests moved. Every one is about the auto-end itself or stays on replay:
- about the auto-end: `WakeAutoEndSuite` (2), `TakeWealthProcedureSuite` (2),
  `RestSuite` (1), `PhasePowerSuite`, `HornedMaskSuite`, `HungerSuite`'s
  facedown case; in this project's scope, `GameApplicationServiceSuite`'s Take
  Wealth test (Wake ends in the same command), `GameLogHeadlineSuite` (3, the
  Wake headlines and "Nothing happened in Wake") and the round, take-wealth and
  hunger goldens;
- on replay by design: `SituationSuite` (3), `HungerSuite`'s Setup test, the
  `woken` golden and `GameApplicationServiceSuite`'s setup-length test.

None is a service or log test failing on an incidental setup fact.

**Sequence independence.** Adding one event to `beginGame` (a no-op walker
step after `GameStarted`) fails 78 tests, all in suites that keep the real
start: the route, provisioning and bootstrap suites (27), `SituationSuite`
(7), `EndWakeProcedureSuite` (7), the wire suite (5), `SetupProcedureSuite`
and `GameStartToWakeSuite` (5), `WalkerReplayDriftSuite` (3), the three
replay-from-journal tests, `GameHistorySuite`, `GameLogSetupSuite` (3),
`GameLogPropertiesSuite` (5, which runs every script including `woken`),
`GameLogRouteSuite`, and one test each in `GameLogGoldenSuite`,
`GameLogDecisionSuite`, `GameLogHeadlineSuite` and `GameLogPowerLinesSuite`
that read `woken`'s setup lines. In `GameApplicationServiceSuite` it fails six:
the setup-length check, Salt Flats, the all-Exile game and the three `Begin`
tests. Before the `board` script, the check also failed about twenty log and
presentation tests that used `woken` only as a base state; they are why
`board` exists.

**Speed** (CPU seconds summed over munit's per-test times, one parallel run
each, on the same machine):

| Suite | Before | After |
|---|---|---|
| The 14 game-log suites | 38.8 | 7.8 |
| `GameApplicationServiceSuite` | 6.9 | 4.5 |
| `WalkerDecisionProjectionSuite` | 2.0 | 0.5 |
| `PendingWalkerInvariantSuite` | 1.7 | 0.1 |
| `PhasePowerProjectorSuite` | 0.4 | 0.1 |

The largest drops are the ones that replayed Setup on every command:
`GameLogPropertiesSuite` (11.3 to 2.1), `GameLogGoldenSuite` (9.5 to 2.2) and
`GameLogActionLineSuite` (6.7 to 0.5).

**Kept on the replayed setup**, and why:
- the `woken` script and its goldens: the one end-to-end log through Setup;
- `GameLogSetupSuite`, `SituationSuite`'s replay checks, HungerSuite's Setup
  test, the route, bootstrap and provisioning suites, the wire, End Wake and
  drift suites, and the replay-from-journal tests, which test the real start;
- in `GameApplicationServiceSuite`: the all-Exile game, the setup-length
  check, the three `Begin` tests, and Salt Flats (not in play on a Table's
  map, and no Table step changes the map).

`ParkedServiceFixture.setUp`, `endingWake` and `withWorldDeckTop`, and
`GameApplicationServiceSuite.execute`, remain for those tests only.

**Goldens.** Every golden changed by dropping its Setup lines, renumbering,
and opening with its round, turn and phase. Beyond that, these changed for a
fact the table now states differently (the action lines read as before):
- `oathkeeper`, `use-power`: the actor is p1, and the Oathkeeper passes to P1,
  the default first leader under the new seats;
- `forge`: the conquest, Searches and rounds are gone; p1 forges;
- `muster`, `trade`: the pawn stands at Deep Woods, so the Travel line is
  gone;
- `banners`, `take-wealth`: the favor is in the table, not an arranged gain;
  take-wealth starts at p2's Wake, so the first player's Rest is gone;
- `round`: the turn order is p1, p2, p3, and p1's first Wake ends at once;
- `augury`: the Search draws Threatening Roar, Fae Merchant, Second Chance and
  Pied Piper, since the moved card no longer leaves a gap at the deck's top;
- `barbed-net`: it peeks at two relics at Ancient City, not three at Broken
  Peaks;
- `hunger`: p2 buries their own adviser (slot 1), as before;
- `raid`: the defender is p2 and loses Birdsong;
- `negotiation-*`: the partner is p2;
- `recover-*`, `reveal-relic`: p1 recovers at Broken Peaks and p2 stands at
  Deep Woods, since a player at a site sees the relics lying there.

**For the readability and speed projects.**
- Log scripts and service tests now read as facts. The remaining coupling is
  the real-start tests above, on purpose.
- `trade` still uses `Step.Arrange` for its gain line, which the detail suite
  tests, through `arrangedForNext`.
- Each script's table ends with `banditsRefilled`, and each script says so
  once, on `atTable`. A reader who sees it in a table elsewhere can find why.
- `GameApplicationServiceSuite` is 1900 lines; its tests read a table and a
  service in their own bodies, and could be split by area.
