# Test Table builder

Date: 2026-09-27. Status: implemented (see Result).

This is the first of five test-suite improvement projects that came out of the
2026-09-27 test audit. The other four are queued behind it:

- prune low-value tests;
- give service and log tests a built start state instead of a replayed setup;
- a readability sweep;
- speed.

## Problem

Most rule tests reach their starting state by replaying the whole first-game
setup (`FirstGameSetupFixture.execute()` / `initialReady`) and then patching
the result with `copy` / `updateCurrent`. The audit found:

- **Coupling.** 225 of 299 suites depend on the replayed setup, through seven
  different state-building mechanisms. The setup walk decides:
  - the first player (p2);
  - pawn order and positions;
  - which adviser each player keeps;
  - which cards and bandits sit at each site;
  - which phase the game rests in.

  Tests inherit all of these facts without stating them. When the Wake
  auto-end changed where real play rests, 158 tests failed.
- **A state play never reaches.** `initialReady` is now a Wake with nothing to
  decide, cut off just before the automatic End Wake that real play would
  apply.
- **Duplication.**
  - Five fixtures build near-identical "actor in Act" boards: Campaign,
    Economy, Challenge, Negotiation and Placement.
  - `inPhase` is defined three times, with about 22 more inline
    `turn.copy(phase = Phase.Act)`.
  - "Update a player", "active player" and "take a card out of every zone"
    each exist in 4 to 8 copies.
  - About 13 suites define their own `supplyOf`, `withSupply` or `ready`.
- **Readability (DAMP).**
  - Helper chains hide the state: `val Ready(base) = execute()._1: @unchecked`
    in 23 files, 5-tuples such as `val (base, actor, _, _, _) = recoverable`,
    and 57 references that borrow fixtures from another suite.
  - 91 numeric card ids such as `DenizenId("93")`.
- **Speed.** Each `execute()` replays setup, about 30 ms. Fixtures such as
  `CampaignFixture.board()` call it once per test, so the campaign suites are
  the slowest cluster.

## Goal

One named, readable way for a rule test to say "p1 in Act at Broken Peaks,
advised by Mercenaries". It is built from the real game start without the
setup walk. The test states every fact that matters and inherits nothing
incidental. The duplicate fixtures migrate onto it and are deleted.

Success criteria:

- `execute()` and `initialReady` remain only in suites that test the setup
  walk, replay or real play from the start (listed under "Staying on replay").
- No hand-written `phase = Phase.Act` patches or `inPhase` copies remain.
- The fixtures listed under Migration are deleted, or reduced to domain
  helpers that build no board.
- **Brittleness check.** Revert the Wake auto-end (5bf7d1a3) locally. No suite
  migrated onto `Table` fails. Then restore it without committing.
- The full suite stays green after every task. The campaign suites' CPU time
  drops measurably.

Out of scope:

- service-level and journaled tests;
- golden logs;
- `ParkedServiceFixture`, `ForgeWalkerFixture` and `LogScripts`;
- pruning tests;
- the readability sweep beyond the setup each migrated test touches.

## Design

### `Table.start`: the quiet table

`Table` lives in `src/test/scala/oathdigital/testkit/Table.scala`.
`Table.start` is built from `GameStartRules.evolve` over the fixture's
Chronicle and orders, with the orders' first player set to p1. It takes about
1 ms. Then:

- **Turn.** It is p1's Act, with no powers used. The turn order is p1, p2, p3,
  since `FinishRestProcedure.turnOrder` reads `setup.firstPlayer`.
- **Pawns.** Each pawn stands on its own site. The three sites are named in
  `Table`'s doc comment and exposed as constants.
- **Quiet sites.** No site holds a denizen, edifice, relic or bandit. Cards
  return to the zone setup drew them from (world deck, relic deck or edifice
  deck), and bandits return to the bank.
- **Players.** Nobody holds an adviser or a relic. Boards keep their printed
  start values: 1 favor, 1 faceup secret, 3 warbands and 7 Supply.
- **Players, catalog and banks.** The players are p1, p2 and p3. The catalog
  is `FirstGameSetupFixture.catalog`. The banks are as `GameStartRules`
  stocks them.

### Steps

`Table` is an immutable value. Each step states one fact and keeps the card
inventory whole: a placed card leaves whatever zone held it.

```scala
Table.start
  .turn(p2, Phase.Wake)
  .pawn(p1, at = "Broken Peaks")
  .adviser(p1, "Mercenaries")               // faceup; facedown = true
  .relic(p1, "Circlet of Command")
  .denizen("Watchdog", at = "Broken Peaks")
  .relic("Brass Army", at = "Broken Peaks")
  .edifice("Hallowed Spring", EdificeSide.Intact, at = "Ancient City")
  .bandits("Broken Peaks", 2)
  .favor(p1, 4).secrets(p1, faceUp = 2).supply(p1, 5).warbands(p1, 3)
  .tokens("Watchdog", favor = 1)
  .ready        // ReadyGame; .state gives OathState
```

- **Names.** Cards and sites are named as the catalog prints them. The
  audit found the names unique: 255 denizens, 48 relics and 24 sites, with no
  denizen and relic sharing a name. An overload takes ids, for power suites
  that already hold `X.cardId`. An unknown name fails the test and lists
  close matches.
- **Knowledge.** An owner's knowledge of their own cards is implicit (setup
  records none for kept advisers), so placing a card records nothing. A
  moved card is forgotten by every viewer, since it left the place they saw
  it.
- **Escape hatch.** `.update(f: ReadyGame => ReadyGame)` is a documented last
  resort for a fact no step states. A fact needed by three or more suites
  becomes a step.
- **Validation.** `.ready` fails if any card sits in two zones, naming the
  card and the zones. It checks this through `CardIndex.from`.
- **Step list.** The final list is settled in the plan by what the migrated
  fixtures need. Each step's name is the game's own term.

### `Look`: the read side

`Look(state)` gives the reads the suites currently copy:

- `supply(p)`, `favor(p)`, `secrets(p)`, `warbands(p)`;
- `advisers(p)`, `relics(p)`, `pawn(p)`;
- `denizens(site)`, `forces(site)`;
- `phase`, `active`, `tokensOn(card)`.

It accepts either `OathState` or `ReadyGame`, and names sites and cards the
same way `Table` does.

### Driving from a table

`table.situation(driver)` starts a `Situation` at the table's state with the
rules adapter (`Situation.rules` or `Situation.serviceRules`). The events are
empty and the sequence starts at 0. From there, `after` and `parkedAfter`
issue real commands and answer parks with the usual policy.

The journaled adapter is rejected: a stream must begin with `GameStarted`.
Service-level start states are a later project.

`CONTEXT.md` gains a **Table** term, defined as "a game state assembled
directly for a rule test, not reached by play". The **Situation** entry
changes to say that a rules-adapter situation may also start at a Table.

## Migration

The migration runs one task per fixture, or per fixture family where
fixtures build on each other. Each task moves the suites onto `Table`,
deletes the board-building it emptied, and ends green. The implementation
plan orders the tasks by dependency, and may differ from the list below:
it takes the Campaign fixtures before the `PowerFixture` family and
splits that family by power folder.

1. `Table`, `Look` and `situation`, with `TableSuite`.
2. Pilot: `EconomyFixture` (13 users).
3. `ChallengeFixture`, `NegotiationFixture` and `PlacementFixture`.
4. `OathkeeperFixture` and `PhasePowerFixture`, which hold the `inPhase`
   copies.
5. `TargetsFixture` (38), `SearchFixture` and `CardStaging`.
6. `PowerFixture` (85). It may split by power folder.
7. `CampaignFixture` (34) and the board-building in `PlanDriver`. Dice and
   campaign-driving helpers stay as small named helpers.
8. `PaidActionHarness`. Its state base and fixed `actor` go; its drive
   helpers take the actor from the state's active player. The event fold
   (`replayed`) and `wireRoundTrips` stay.
9. The remaining inline cases: suites that call `execute()`, patch the phase,
   or borrow setup from another suite. These include `RecoverProcedureSuite`,
   `TravelProcedureSuite`, `ForgeRulesSuite`, `ForgeProcedureSuite`,
   `RestSuite`, `RecoverEligibilitySuite`, `StateBasedEvaluationSuite`,
   `OathRulesWalkerPowerSuite`, `CampaignPowersSuite`, `WakeAutoEndSuite`,
   and the application projector suites that build from `execute()`.

### Rules for migrating a test

- **Keep the assertion; rewrite only the setup.** Each suite keeps its test
  count.
- **State incidental content explicitly.** A test that relied on a dealt card,
  a bandit, a pawn position or the first player gets that fact as an explicit
  step. Dealt content is never restored wholesale.
- **Private builders.** A suite may keep a private builder, such as
  `attackerHolds`, only when all of these hold:
  - it carries a one-line "who holds what" doc comment;
  - three or more tests use it;
  - it is built from `Table` steps.

  Otherwise the test writes its `Table` chain inline.
- **Card ids.** A card id the migrated setup touches is replaced by the
  card's name.
- **Weak tests.** A test that asserts nothing once the incidental content is
  gone goes on a list for the pruning project. It is not deleted here.

### Staying on replay

These suites test the setup walk, replay or real play from the start, so they
keep `initialReady`, `execute()` or `Situation.wake`:

- `SetupProcedureSuite`, `GameStartToWakeSuite`, `GameStartRulesSuite`,
  `SituationSuite`;
- `WalkerReplayDriftSuite`, `GameEventWireSuite`, `EndWakeProcedureSuite`;
- `GameApplicationServiceSuite` and the other service and route suites;
- the `gamelog` suites, `ParkedServiceFixture` and `ForgeWalkerFixture`.

`execute()` and `initialReady` stay in `FirstGameSetupFixture` for them.

## Testing

`TableSuite` shows that:

- `Table.start` is quiet: no site cards, bandits or advisers. It is p1's Act,
  with turn order p1, p2, p3.
- Its card inventory equals `GameStartRules`' inventory. No card is lost or
  copied.
- Each placing step moves the card from every zone it can come from, and
  `.ready` rejects a card placed in two zones.
- An unknown name fails and lists close matches.
- `OathRules` accepts real commands from a `Table` state:
  - a Travel, a Muster and a Begin Rest from `Table.start`.

  A phase power staged with `.adviser` is covered by the migrated power
  suites.
- `situation(Situation.rules(catalog)).after(...)` answers parks and lands
  where real play would.

After each migration task, the full root suite runs green, and the task
records the suite's CPU time from the munit timings. The last task runs the
brittleness check from the success criteria.

## Result

Implemented on `test/table-builder`, 2026-09-27.

**`Table`**
- `Table`, `Look` and `CatalogNames` are in `src/test/scala/oathdigital/testkit`, with `TableSuite` (21 tests).
- Steps added beyond the design, each needed by three or more suites: `hand`, `discarded`, `knowsRelicAt`, `oathkeeper`.
- Two ways to continue from a state a test already holds:
  - `Table.from(ready)` continues building from that state.
  - `unchecked` reads a state that a fixture helper is still assembling by hand.

**Migration.** The user chose the approach mid-way:
- Rewritten test by test, with the fixture deleted or reduced:
  - Economy: reduced to its test powers.
  - Negotiation, Challenge and Oathkeeper: deleted.
  - Placement: five of its seven suites.
  - PhasePower: reduced to its test power and `holding(phase)`.
- Rebased, keeping their helpers as thin `Table` wrappers:
  - `CampaignFixture` and `PlanDriver`;
  - the `PowerFixture` family: `PowerFixture`, `TargetsFixture`, `SearchFixture`, `MovementFixture`, `BannerFixture`, `WhenPlayedHarness`;
  - the Catacombs fixture that four suites borrow.

  The audit named the Campaign and Power suites as DAMP models, so their domain phrases stay.
- Every other rule suite that used `initialReady` or `execute()` as a base now starts from the quiet table. Where a test relied on a setup fact, it now states that fact as a step.

**Kept on replay, as planned:**
- setup, `SituationSuite`, replay drift, the wire suite, End Wake;
- the service and route suites, the game-log suites, and their fixtures (`ParkedServiceFixture`, `ForgeWalkerFixture`);
- the three replay-from-journal tests in the Minor Action, Negotiation and Challenge suites.

**Brittleness check.** Disabling the Wake auto-end makes `wakeOptionOpen` always true. This change, the same one that failed 158 tests before this work, now fails 24:

- **16 are about the auto-end itself.** Examples: Wake auto-end, Take Wealth ending Wake, a facedown Hunger, Rest waking the first player, the Game Start to Wake suite, and the log headlines and goldens that record "Nothing happened in Wake".
- **8 are service-level, log-level or `Situation`-level.** Examples: three service sequence-number tests, three golden logs, two `SituationSuite` tests, and one test of a Hunger revealed in Setup. These are the next project's: a built start state for service and log tests.
- **None is a rule suite failing on an incidental setup fact.**

**CPU** (sum of munit per-test times; wall time stays about 19 s, parallel):

| Scope | Before | After |
|---|---|---|
| Whole root suite | 156.9 s over 2308 tests | 93.3 s over 2329 |
| Campaign and targeting | 60.6 s over 281 | 24.2 s over 281 |
| Power suites | 64.1 s over 805 | 31.2 s over 805 |

**For the pruning and readability projects:**
- `CardStaging.without` is still used by about twenty power suites to take a card out before placing it by hand. A `Table` step does both.
- `PlacementFixture.staged` remains for People's Favor (Mob) and the card-play procedure suite.
- About 20 hand-written `TurnState` / `phase =` patches remain. Their subject is the phase: Rest, RoundEnd, putting the turn back in Wake, or asserting the turn.
- One weak assertion was found and fixed. A projector test checked that an adviser's id did not appear in a row. The id was "2", which appears in "p2:0", so the check could not pass. The test now uses a card with a three-digit id.
