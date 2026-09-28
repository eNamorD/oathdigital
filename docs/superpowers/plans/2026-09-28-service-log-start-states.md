# Service and Game-Log Start States Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Service and game-log tests start from a built `Table` instead of a replayed first-game setup.

**Architecture:** `GameApplicationService` gains a test-only `genesis` state that replay folds from. A `Table` builds a service seeded with itself, and a journaled `Situation` then drives that service from an empty stream at sequence 0. The game-log formatter opens a journal that does not begin with `GameStarted` with the Round, Turn and Phase headlines of its start. Fixtures, suites and log scripts then move onto tables one family at a time.

**Tech Stack:** Scala 3, munit, sbt via `./sbtw` (projects `root` and `frontend`), `-Werror`.

**Spec:** `docs/superpowers/specs/2026-09-28-service-log-start-states-design.md`

## Global Constraints

- Never use `preview_start` (it hits the live DB).
- Never stage `docs/ROADMAP.md`.
- Every task ends with `./sbtw root/test` and `./sbtw frontend/test` green, and is its own commit ending with the session's `Co-Authored-By` trailer.
- Commit messages, comments and docs are normal prose.
- No code under `src/main` passes `genesis`; only tests do.
- A migrated test states its start as `Table` steps in its own body or in a named fixture helper whose steps are visible there. No deal reordering (`withWorldDeckTop`), no `Arrange` placing the board, no `endingWake` to absorb the Wake auto-end.
- A migrated test keeps its name and what it asserts. If a start cannot be stated with `Table` steps without changing what the test asserts, leave that test on replay, add a one-line comment saying why, and list it in the Result (Task 7).
- Golden files change only by `GAMELOG_GOLDEN=write ./sbtw "root/testOnly *GameLogGoldenSuite"`, and every golden diff is reviewed. An allowed change is a dropped setup line, a renumbered sequence, the new opening headlines, a different "other" viewer, or a board fact the script now states differently. Write each golden whose non-setup lines changed, and why, into the task's commit message.
- If HEAD moved (concurrent sessions), re-check before any amend, reset or rebase.

## Facts every task needs

**The quiet table** (`src/test/scala/oathdigital/testkit/Table.scala`, `Table.start`): p1, p2 and p3 sit in that turn order. `setup.firstPlayer` is p1. It is p1's Act. Pawns stand at Ancient City (p1), Broken Peaks (p2) and Buried Giant (p3). No site holds a card, bandit or wealth. Nobody holds an adviser. Every board has 1 favor, 1 faceup secret, 3 warbands and 7 Supply. Nobody holds the Oathkeeper title.

**The replayed setup these tests leave behind** (`FirstGameSetupFixture`, `Situation.wake`): p2 goes first, so the order is p2, p3, p1. Pawns stand at Ancient City (p2), Broken Peaks (p3) and Buried Giant (p1). p2 keeps Wizard's Conclave facedown, and the others keep a facedown denizen. Sites carry bandits, dealt relics (Sticky Fire and Cursed Cauldron at Broken Peaks) and ruined homeland edifices (Hiding Place, the ruined face of E26, at Deep Woods). Broken Peaks has 2 secrets and Fair Isle 3 favor. p2's first Wake at Ancient City has no option and ends by itself, so setup rests in p2's Act at sequence 29.

**Wake options on a table.** Ancient City's River needs another River in play, and there is none, so a player in Wake at Ancient City has no Wake option. A player whose site holds wealth has one (Take Wealth). A next player's Wake "waits" after a Rest only if it has an option: put wealth on that player's site (`siteTokens("Broken Peaks", secrets = 2)` for p2). `EndWake` is accepted in a Wake with no option.

**Phase-entry effects.** `turn(player, phase)` only sets `TurnState`. It runs no Wake auto-end and no forced Wake step (Hunger). To get either, enter the phase by play: for example p1 in Act, then `BeginRest(p1)`, which finishes Rest and enters p2's Wake.

**Speed baseline.** Task 1 records it before any change. Task 7 compares against it.

---

### Task 1: The seam

**Files:**
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala` (constructor at :79-87, `replay` at :99, `load` at :124-131, `handle` at :240-278)
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala` (`format` at :35-45, headline helpers at :137-149)
- Modify: `src/test/scala/oathdigital/testkit/Table.scala` (class docstring :85-104, `situation` at :290-298)
- Modify: `src/test/scala/oathdigital/gameplay/BackendArchitectureSuite.scala`
- Modify: `src/test/scala/oathdigital/testkit/SituationSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogHeadlineSuite.scala`
- Create: `src/test/scala/oathdigital/application/GenesisServiceSuite.scala`
- Modify: `CONTEXT.md` (the **Situation** entry)

**Interfaces:**
- Produces:
  - `GameApplicationService(..., val genesis: OathState = OathState.NoGame)`
  - `Table#service(catalog: ExecutableCatalog = FirstGameSetupFixture.catalog, campaignDice: CampaignDicePort = CampaignDicePort.random, defenseDice: DefenseDicePort = DefenseDicePort.random, warExhaustion: WarExhaustionRandomPort = WarExhaustionRandomPort.random)(using munit.Location): (GameApplicationService, InMemoryEventStreamRepository)`
  - `Table#situation(driver)`, which now accepts a journaled driver whose service's `genesis` is this table's state.

- [ ] **Step 0: Record the speed baseline**

Run: `./sbtw root/test`, then:

```bash
python3 - <<'EOF' > /private/tmp/start-states-baseline.txt
import glob, xml.etree.ElementTree as ET
names = ["GameApplicationServiceSuite", "PhasePowerProjectorSuite",
  "WalkerDecisionProjectionSuite", "PendingWalkerInvariantSuite", "GameLog"]
for f in sorted(glob.glob("target/test-reports/*.xml")):
    r = ET.parse(f).getroot()
    if any(n in r.get("name") for n in names):
        print(f'{r.get("name")} {float(r.get("time")):.2f}s {r.get("tests")}')
EOF
cat /private/tmp/start-states-baseline.txt
```

Keep the file. Task 7 reads it.

- [ ] **Step 1: Write the failing service tests**

Create `src/test/scala/oathdigital/application/GenesisServiceSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.p1

/** A service seeded with a start state: its streams begin there instead of
  * at `GameStarted`. Tests only; see the service and log start-states
  * design. */
class GenesisServiceSuite extends munit.FunSuite:
  test("a missing stream loads as the genesis at sequence 0"):
    val table = Table.start
    val (service, _) = table.service()
    assertEquals(service.load("g"), Right(Some(LoadedGame(table.state, 0L))))

  test("the first command runs against the genesis, and replay starts there"):
    val table = Table.start
    val (service, repository) = table.service()
    val accepted = service.handle("g", 0L, GameCommand.BeginRest(p1))
      .fold(error => fail(s"rejected: $error"), identity)
    assert(accepted.events.nonEmpty)
    assertEquals(accepted.nextSequence, accepted.events.size.toLong)
    // A second service over the same journal replays from the same genesis.
    val reloaded = new GameApplicationService(catalog, repository,
      genesis = table.state)
    assertEquals(reloaded.load("g").map(_.map(_.state)),
      Right(Some(accepted.state)))

  test("a started genesis refuses Begin"):
    val (service, _) = Table.start.service()
    assert(service.handle("g", 0L, GameCommand.Begin(
      oathdigital.gameplay.setup.FirstGameSetupFixture.chronicle,
      oathdigital.gameplay.setup.FirstGameSetupFixture.orders)).isLeft)

  test("without a genesis, a missing stream still accepts only Begin"):
    val service = new GameApplicationService(catalog,
      new InMemoryEventStreamRepository)
    assertEquals(service.load("g"), Right(None))
    assertEquals(service.handle("g", 0L, GameCommand.BeginRest(p1)),
      Left(GameApplicationError.StreamNotFound("g")))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "root/testOnly *GenesisServiceSuite"`
Expected: compile error. `genesis` is not a parameter of `GameApplicationService`, and `Table#service` does not exist.

- [ ] **Step 3: Add `genesis` to the service**

In `GameApplicationService.scala`:
- Add the constructor parameter after `eventCodec`:

```scala
    eventCodec: GameEventCodec = GameEventCodec.default,
    /** Where every stream of this service begins: `NoGame`, so a stream's
      * first event is `GameStarted`, unless a test seeds a start state. Only
      * tests pass it (`BackendArchitectureSuite`), until a journaled
      * arranged-start event replaces it (`docs/ROADMAP.md`). */
    val genesis: OathState = OathState.NoGame
):
```

- Replace `private val replay = new EventReplayEngine(rules)` with:

```scala
  private val replay = new EventReplayEngine(
    new EventEvolution[OathState, OathEvent, OathViolation]:
      def initialState: OathState = genesis
      def evolve(state: OathState, event: OathEvent)
          : Either[OathViolation, OathState] = rules.evolve(state, event))
```

  Add `EventEvolution` to the existing `oathdigital.engine` import.

- In `load`, replace `case None => Right(None)` with:

```scala
      case None => Right(genesis match
        case OathState.NoGame => None
        case started => Some(LoadedGame(started, 0L)))
```

- In `handle`, replace the whole `case None =>` branch with:

```scala
      case None =>
        if expectedNextSequence != 0L then
          Left(StaleClientPosition(expectedNextSequence, 0L))
        else if genesis == rules.initialState &&
            !command.isInstanceOf[GameCommand.Begin] then
          Left(GameApplicationError.StreamNotFound(gameId))
        else
          handleAgainst(gameId, genesis, command, ExpectedStream.MustNotExist,
            0L)
```

`prepareBootstrap` keeps `rules.initialState`: bootstrap always begins a real game.

- [ ] **Step 4: Add `Table#service` and the journaled `Table#situation`**

In `Table.scala`, add these imports:

```scala
import oathdigital.application.{CampaignDicePort, DefenseDicePort,
  GameApplicationService, InMemoryEventStreamRepository}
import oathdigital.gameplay.phases.rest.WarExhaustionRandomPort
```

Then replace `situation` with:

```scala
  /** A fresh in-memory service whose streams begin at this table, and its
    * repository. `catalog` may be an edited copy of the fixture's with the
    * same ids (a changed Forge cost, say). */
  def service(catalog: ExecutableCatalog = FirstGameSetupFixture.catalog,
      campaignDice: CampaignDicePort = CampaignDicePort.random,
      defenseDice: DefenseDicePort = DefenseDicePort.random,
      warExhaustion: WarExhaustionRandomPort = WarExhaustionRandomPort.random)(
      using munit.Location): (GameApplicationService, InMemoryEventStreamRepository) =
    val repository = new InMemoryEventStreamRepository
    (new GameApplicationService(catalog, repository,
      defenseDicePort = defenseDice, campaignDicePort = campaignDice,
      warExhaustionRandomPort = warExhaustion, genesis = state), repository)

  /** A situation at this table, with no events and the sequence at 0. A
    * rules adapter starts here directly. A journaled adapter must drive a
    * service from [[service]] (its `genesis` is this table), on a stream
    * that does not exist yet. */
  def situation(driver: SituationDriver)(using munit.Location): Situation =
    driver match
      case journaled: SituationDriver.Journaled
          if journaled.service.genesis != state => munit.Assertions.fail(
        "a journaled situation at a Table drives a service begun at that " +
          "table; build it with table.service")
      case driver => Situation(state, Vector.empty, 0L, driver)
```

In the class docstring, replace the sentence about River:

```
  * Ancient City carries the River site power, a Wake option: a test that
  * puts p1 in Wake there has something to decide.
```

with:

```
  * Ancient City's River moves a pawn only to another River, and none is in
  * play, so a Wake at Ancient City has no option unless the test adds one.
```

- [ ] **Step 5: Run the service tests**

Run: `./sbtw "root/testOnly *GenesisServiceSuite"`
Expected: 4 passed.

- [ ] **Step 6: Write the failing formatter test**

In `GameLogHeadlineSuite.scala`, add these imports:

```scala
import oathdigital.application.GameCommand
import oathdigital.testkit.{Situation, Table}
import oathdigital.testkit.Table.p1
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
```

Then add:

```scala
  test("a journal that starts at a table opens with its round, turn and " +
      "phase"):
    val table = Table.start.turn(p1, Phase.Wake)
    val (service, repository) = table.service()
    table.situation(Situation.journaled(service, catalog, repository, "t"))
      .after(GameCommand.EndWake(p1))
    val entries = formatter.format(service.history("t").toOption.flatten.get
      .steps, Some(p1))
    assertEquals(texts(entries).take(5), Vector("Round 1",
      s"${name(p1)}'s turn", "Wake", "Nothing happened in Wake", "Act"))
    assertEquals(entries.take(3).map(entry => (entry.sequence, entry.ordinal)),
      Vector((0L, 0), (0L, 1), (0L, 2)))
```

Run: `./sbtw "root/testOnly *GameLogHeadlineSuite"`
Expected: FAIL. The log starts at "Act", with no opening headlines.

- [ ] **Step 7: Open a table-started log in the formatter**

In `GameLogFormatter.format`, change the fold body:

```scala
      case ((run, entries), at) =>
        val (lines, next) = eventLines(journal, run, at, viewer)
        val opened = if at == 0 then opening(journal) ++ lines else lines
        val posted = quietWake(entries.lastOption, opened)
```

Add this beside `quietWake`:

```scala
  /** A journal that does not begin with `GameStarted` began at a position
    * already in play (a test's Table): it opens with that position's round,
    * turn and phase, the headlines a `RoundEnded` would have posted. */
  private def opening(journal: LogJournal): Vector[Posted] =
    journal.event(0) match
      case OathEvent.GameStarted(_, _) => Vector.empty
      case _ => journal.readyBefore(0).toVector.flatMap { ready =>
        val current = ready.game.current
        Vector(roundHeadline(current.tracks.round),
          turnHeadline(current.turn.activePlayer),
          phaseHeadline(current.turn.phase))
      }
```

Run: `./sbtw "root/testOnly *GameLogHeadlineSuite"`
Expected: PASS.

- [ ] **Step 8: Adapters agree from a table; a mismatched service is refused**

In `SituationSuite.scala`, add `import oathdigital.testkit.Table.p1` and these tests:

```scala
  test("the rules and journaled adapters agree from a table"):
    val table = Table.start
    val (service, repository) = table.service(campaignDice = blankDice)
    val steps = Vector[GameCommand | Step](GameCommand.StartWalker(
      ActionRef.Search, StartPayload(p1, Vector.empty,
        Vector(DecisionOptionRef.Button("search:world")))))
    val byRules = table.situation(Situation.serviceRules(catalog, blankDice))
      .after(steps*)
    val byJournal = table.situation(Situation.journaled(service, catalog,
      repository, "table")).after(steps*)
    assertEquals(byJournal.state, byRules.state)
    assertEquals(byJournal.events, byRules.events)
    assertEquals(byJournal.nextSequence, byRules.nextSequence)
    assertEquals(recordCount(repository, "table").toLong,
      byJournal.nextSequence)

  test("a journaled situation at a table needs a service begun there"):
    val (service, repository) = journaledService()
    val failure = intercept[AssertionError](Table.start.situation(
      Situation.journaled(service, catalog, repository, "elsewhere")))
    assert(failure.getMessage.contains("table.service"), failure.getMessage)
```

`journaledService()` is the service half of the suite's existing `journaled(gameId)` helper. Split it:

```scala
  private def journaledService()
      : (GameApplicationService, InMemoryEventStreamRepository) =
    val repository = new InMemoryEventStreamRepository
    (new GameApplicationService(catalog, repository,
      campaignDicePort = blankDice), repository)

  private def journaled(gameId: String)
      : (SituationDriver, InMemoryEventStreamRepository) =
    val (service, repository) = journaledService()
    (Situation.journaled(service, catalog, repository, gameId), repository)
```

Run: `./sbtw "root/testOnly *SituationSuite"`
Expected: PASS.

- [ ] **Step 9: Guard the seam**

In `BackendArchitectureSuite.scala`, add:

```scala
  test("only tests seed a service's genesis"):
    // The seam lets a test's Table start a stream. Production streams begin
    // at GameStarted until an arranged-start event exists (docs/ROADMAP.md).
    val root = Paths.get("src/main/scala")
    val owner = Paths.get(
      "src/main/scala/oathdigital/application/GameApplicationService.scala")
    val offenders = Files.walk(root).iterator.asScala.filter(path =>
      path.toString.endsWith(".scala") && path != owner &&
        Files.readString(path).contains("genesis")).map(_.toString).toVector
    assertEquals(offenders, Vector.empty)
```

Run: `./sbtw "root/testOnly *BackendArchitectureSuite"`
Expected: PASS. If an unrelated file already uses the word, narrow the match to `genesis =` and say so in a comment.

- [ ] **Step 10: CONTEXT.md**

In the **Situation** entry, replace `Driven by the rules adapter, a situation may also start at a Table.` with `A situation may also start at a Table: the rules adapter directly, the journaled adapter through a service begun at that Table.`

- [ ] **Step 11: Full gate and commit**

Run: `./sbtw root/test` and `./sbtw frontend/test`. Both are green.

```bash
git add src/main/scala/oathdigital/application/GameApplicationService.scala \
  src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala \
  src/test/scala/oathdigital/testkit/Table.scala \
  src/test/scala/oathdigital/testkit/SituationSuite.scala \
  src/test/scala/oathdigital/gameplay/BackendArchitectureSuite.scala \
  src/test/scala/oathdigital/application/GenesisServiceSuite.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogHeadlineSuite.scala \
  CONTEXT.md
git commit -m "test: let a service stream begin at a Table"
```

---

### Task 2: The parked fixtures on tables

This moves `ParkedServiceFixture`'s four parks and `WalkerDecisionProjectionSuite.startedRolling`. They go onto tables, and so does every caller of those five builders. The `oathkeeper` and `use-power` log scripts reuse two of the parks, so their goldens change here.

**Files:**
- Modify: `src/test/scala/oathdigital/application/ParkedServiceFixture.scala`
- Modify: `PhasePowerProjectorSuite.scala`, `WalkerDecisionProjectionSuite.scala` and `PendingWalkerInvariantSuite.scala` (all in `src/test/scala/oathdigital/application/`)
- Modify: `GameApplicationServiceSuite.scala`, the callers of the parks only: `:59-80` (League Treaty) and `:1127-1160` (Oathkeeper tie)
- Modify: `src/test/scala/oathdigital/testkit/SituationSuite.scala:114-127`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (`oathkeeper` :139-147, `usePower` :404-412, a new `atTable` helper)
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala` (:255-264, :348-363)
- Modify: `src/test/resources/gamelog/{oathkeeper,use-power}.{actor,other}.log`

**Interfaces:**
- Consumes: `Table#service`, `Table#situation` (Task 1).
- Produces (all in `ParkedServiceFixture`; each park builds its own service, because the table is its genesis):
  - `final case class ParkedGame(service: GameApplicationService, repository: InMemoryEventStreamRepository, accepted: GameAccepted)`
  - `def leagueTreatyPark(gameId: String): (ParkedGame, PlayerId, PlayerId)`, which returns (game, active p1, ruler p2)
  - `def recoverChoicePark(gameId: String, dice: CampaignDicePort = failingDice): (ParkedGame, PlayerId, Vector[PlayerId])`
  - `def oathkeeperTiePark(gameId: String): (ParkedGame, PlayerId, PlayerId, PlayerId)`
  - `def silverTonguePark(gameId: String, dice: CampaignDicePort = CampaignDicePort.random): (ParkedGame, PlayerId, Suit)`
  - `LogScripts.atTable(name: String, table: Table, dice: CampaignDicePort = steadyDice)(using munit.Location): (GameApplicationService, Situation)`

- [ ] **Step 1: Rebuild the parks**

In `ParkedServiceFixture.scala`, keep `treatyCard`, `silverTongueCard`, `failingDice`, `accepted(situation, before)` and the `parkedAssertions` helper. Replace the four park builders with the table versions below. Each one keeps its current docstring, adjusted to the new seats, and its current `parkedAssertions.assertParked` check.

```scala
  final case class ParkedGame(service: GameApplicationService,
      repository: InMemoryEventStreamRepository, accepted: GameAccepted)

  private def journaled(table: Table, gameId: String,
      dice: CampaignDicePort = CampaignDicePort.random)(
      using munit.Location): (GameApplicationService,
        InMemoryEventStreamRepository, Situation) =
    val (service, repository) = table.service(campaignDice = dice)
    (service, repository,
      table.situation(Situation.journaled(service, catalog, repository, gameId)))

  /** League Treaty at Ancient City, ruled by p2's warband and holding 2
    * favor. p1 begins Rest, which finishes Rest and parks on p2's
    * destination decision. Broken Peaks' secrets give p2's coming Wake an
    * option, so that Wake waits after the decline. */
  def leagueTreatyPark(gameId: String)(using munit.Location)
      : (ParkedGame, PlayerId, PlayerId) =
    val table = Table.start
      .denizen("League Treaty", "Ancient City")
      .tokens("League Treaty", favor = 2)
      .warbandsAt("Ancient City", p2, 1)
      .warbands(p2, 2)
      .siteTokens("Broken Peaks", secrets = 2)
    val (service, repository, act) = journaled(table, gameId)
    val parked = act.parkedAfter(GameCommand.BeginRest(p1))
    parkedAssertions.assertParked(parked.state, PhaseTransitionRef.FinishRest,
      LeagueTreatyContribution.destinationDecisionId(table.ready, p1,
        Table.homeOf(p1), treatyCard), p2)
    (ParkedGame(service, repository, accepted(parked, act)), p1, p2)
```

For the other three parks, use these tables and commands. Keep each park's current return roles; the survey values are in brackets.

- **`recoverChoicePark`**: `Table.start.pawn(p1, "Broken Peaks").relicAt("Sticky Fire", "Broken Peaks").relicAt("Cursed Cauldron", "Broken Peaks")`. Build it with `dice`. Then `parkedAfter(GameCommand.StartWalker(ActionRef.Recover, StartPayload(p1)))`. Assert that it parks on `RecoverProcedure.choiceDecisionId` for p1. Keep returning the same roles as today, with p1 as actor.
- **`oathkeeperTiePark`**: `Table.start.oathkeeper(Some(p3)).warbandsAt("Ancient City", p1, 1).warbandsAt("Broken Peaks", p2, 1).warbands(p1, 2).warbands(p2, 2)`. Then `parkedAfter(GameCommand.StartWalker(ActionRef.Travel, StartPayload(p1, Vector.empty, Vector(DecisionOptionRef.Site(Table.homeOf(p3))))))`. Assert that the last event is the Oathkeeper `WalkerParked` awaiting the holder p3. Return `(game, traveller p1, holder p3, other p2)` in the tuple order today's callers read (mover, holder, other). Check the current body's return expression and keep its order.
- **`silverTonguePark`**: `Table.start.adviser(p1, "Silver Tongue").denizen("Wrestlers", "Ancient City").denizen("Bandit Chief", "Ancient City")`. Then `after(GameCommand.BeginRest(p1))` and `parkedAfter(GameCommand.UsePower(p1, SilverTongue.id, DecisionOptionRef.Denizen(silverTongueCard)))`. Return `Suit.Order` as today.
  - `GameLogPowerLinesSuite` also reads another player's facedown denizen adviser from the `use-power` script (:137-153, :155-176, :235-253, :310-331). Add `.adviser(p2, "Birdsong", facedown = true)` to this table, and say why in the docstring.

Delete `setUp`, `woken`, `endingWake` (after Task 3 removes its last callers; if it still has callers, keep it until Task 3), `arrange`, `cleared`, `recoverChronicle` and `recoverSites`, but only once `grep -rn` shows no callers. `withWorldDeckTop` and `topOfWorldDeck` stay until Task 6. `HungerSuite:134` keeps `withWorldDeckTop` for good.

- [ ] **Step 2: Move the callers**

- `PhasePowerProjectorSuite:108-135`: `val (game, actor, ruler) = ParkedServiceFixture.leagueTreatyPark("treaty")`. Read `game.accepted` where it read the tuple's `GameAccepted`, and `game.service` where it built its own. Same for `silverTonguePark`.
- `PendingWalkerInvariantSuite:78-118`: the four parks as above. The Begin sample (:30) keeps its own no-genesis service.
- `WalkerDecisionProjectionSuite.startedRolling :89-105`: replace the inlined `recoverChronicle`, `setUp` and `endingWake` with the `recoverChoicePark` table, under `FixedRecoverDice`. Build it through `ParkedServiceFixture.recoverChoicePark(gameId, dice)` if its park point matches, or through the same table and `Table#service` otherwise. The relic test (:225) needs both facedown relics, and the table has them.
- `GameApplicationServiceSuite:59-80` and `:1127-1160`: the League Treaty and Oathkeeper tie tests. Where a test reloads through a second service on the same repository (:64, :1139-1141), build that one with `genesis = game.service.genesis`.
- `SituationSuite:114-127` ("the rules adapter parks a Recover on its continue-or-stop choice the way ParkedServiceFixture does"): drive the recover table through `table.situation(Situation.serviceRules(catalog, blankDice)).parkedAfter(GameCommand.StartWalker(ActionRef.Recover, StartPayload(p1)))`, and keep the assertion.

- [ ] **Step 3: Move the two log scripts**

In `LogScripts.scala`, add:

```scala
  /** A fresh service whose stream `name` begins at `table`, and the
    * journaled situation there. */
  def atTable(name: String, table: Table, dice: CampaignDicePort = steadyDice)(
      using munit.Location): (GameApplicationService, Situation) =
    val (service, repository) = table.service(campaignDice = dice)
    (service, table.situation(Situation.journaled(service, catalog, repository,
      name)))
```

Rewrite `oathkeeper` and `usePower` on the new parks: `Script(name, game.service, actor)`, then the same commands after the park as today. `GameLogPowerLinesSuite:348-363` reads a setup note through `usePower`; point it at `woken`, which keeps the setup. At `:255-264` the first `WalkerStepRecorded` was a Setup step. Read the test's intent (the first step of the power's own run) and select that step by procedure, not by position.

- [ ] **Step 4: Rewrite and review the two goldens**

Run: `GAMELOG_GOLDEN=write ./sbtw "root/testOnly *GameLogGoldenSuite"`, then `git diff src/test/resources/gamelog/`. Only `oathkeeper.*` and `use-power.*` may change. Check each line against the Global Constraints list.

- [ ] **Step 5: Gate and commit**

Run: `./sbtw root/test` and `./sbtw frontend/test`. Commit every file listed above with the message `test: build the parked service fixtures on tables`. Name any golden whose non-setup lines changed in the body.

---

### Task 3: GameApplicationServiceSuite on tables

**Files:**
- Modify: `src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala`
- Modify: `src/test/scala/oathdigital/application/ParkedServiceFixture.scala` (delete `setUp`, `woken`, `endingWake` once unused)

**Interfaces:**
- Consumes: `Table#service` and `Table#situation` (Task 1), `ParkedGame` (Task 2).

Each moved test builds its table in its own body. It drives the table through `table.situation(Situation.journaled(service, catalog, repository, gameId))`. It reads `situation.nextSequence` where it read `repository.load(...).get.nextSequence`. `orders.firstPlayer` becomes the literal seat the table uses.

**Stays on replay** (leave them, and add nothing): 582 (all-Exile game), 996 (the 29-event setup-length check), 1446, 1595 and 1694 (Begin), and 1473, 1487, 1527 and 1571 (raw seeded records, no setup). **318 (Salt Flats)** stays too: Salt Flats is not in play on the fixture map, and no Table step changes the map. Add a one-line comment saying so.

**Moves** (line numbers as of the plan; the tables come from the survey):

| Test (line) | Table | Then |
|---|---|---|
| 131, 217, 257, 388 Recover | `Table.start.turn(p2, Phase.Act).relicAt("Sticky Fire", "Broken Peaks")` (257 needs no relic) | Their Recover commands as p2. `withSiteInPlay` and `endingWake` go. |
| 460, 533 Catacombs | `Table.start.pawn(p1, "Dunes").denizen("Catacombs", "Dunes")`. Add a held facedown relic and `supply` only if the test reads them. | Recover as p1. 533 still expects exactly `[denizen.catacombs]`. `catacombsSetup` and `prepareCatacombs` go. |
| 654 preview, 686 minor adviser, 1667 facedown preview | `Table.start.adviser(p1, "Wizard's Conclave", facedown = true)` | As today, as p1. For 654, which reads `act.nextSequence - 1`: start at `.turn(p1, Phase.Wake)` and issue `EndWake(p1)` first, so a stream exists. |
| 722 Challenge | `Table.start.favor(p1, 2).pawn(p1, "Fair Isle")` | Challenge with amount 2. Drop the Take Wealth. |
| 1017 absolute position | `Table.start.turn(p2, Phase.Wake).siteTokens("Broken Peaks", secrets = 2)` | Same commands. Assert the local sequences the new start gives: the stream ends at the number of events the commands made, and an EndWake at 0 is `StaleClientPosition(0L, n)`. Replace 32L, 27L and `take(27)` with values the test derives and explains in a comment. |
| 1069 Travel | `Table.start` | Travel to Broken Peaks. Still expects +3 events. |
| 1168 Muster | `Table.start.pawn(p1, "Deep Woods").edifice("Hiding Place", EdificeSide.Ruined, "Deep Woods")` | Muster as today. |
| 1197 Search tamper, 1215 and 1633 Search | `Table.start.worldDeckTop(<three denizens the test's assertions name, or any three>)` | As today, as p1. 1197 reads the sequence from the situation. |
| 1249 Wake projection | `Table.start.turn(p2, Phase.Wake).siteTokens("Broken Peaks", secrets = 2).relicAt("Sticky Fire", "Broken Peaks").bandits("Broken Peaks", 1).adviser(p2, "Birdsong", facedown = true)` | It still asserts exactly `beginRest, beginRecover, beginCampaign, facedownAdviserMinorAction, peekSiteRelics`. If the set differs, compare it with the replay board and add the missing fact. Do not change the expected set. |
| 1305 site projection | `Table.start` steps for the cradle and province sites. Imperial warbands need `update`, with a comment. Use a second card instead of placing one denizen twice. | If the test cannot be stated without changing what it asserts, keep it as it is and record why. |
| 1778 HSQL Rest | `Table.start.siteTokens("Broken Peaks", secrets = 2)` | BeginRest(p1). The next player p2's Wake waits, so the test's `Phase.Wake` holds. |
| 1810 minor relic knowledge | `Table.start.turn(p2, Phase.Act).relicAt("Sticky Fire", "Broken Peaks")` | As p2. |
| 1876, 1904 Negotiation | `Table.start.adviser(p1, "Wizard's Conclave", facedown = true)` | p1 travels to Broken Peaks and negotiates with p2. The disclosure still relies on the facedown adviser. |

- [ ] **Step 1:** Move the tests in table order, running `./sbtw "root/testOnly *GameApplicationServiceSuite"` after each group of rows.
- [ ] **Step 2:** Delete `execute`, `withSiteInPlay`, `catacombsSetup` and `prepareCatacombs`, and any import left unused. `-Werror` flags unused imports. In `ParkedServiceFixture`, delete `setUp`, `woken` and `endingWake` if `grep -rn` shows no callers.
- [ ] **Step 3:** Run the mutation check on this suite. In `OathRules.scala:141`, make `wakeOptionOpen` return `true`. Then run `./sbtw "root/testOnly *GameApplicationServiceSuite"`. Only 582, 996, 318 and the Begin tests may fail. Restore the file with `git checkout src/main/scala/oathdigital/gameplay/OathRules.scala`.
- [ ] **Step 4:** Run the gate and commit with the message `test: start the service suite's tests from tables`.

---

### Task 4: The Forge on a table

**Files:**
- Modify: `src/test/scala/oathdigital/testkit/Table.scala` (a new `relicDeckTop` step)
- Modify: `src/test/scala/oathdigital/testkit/TableSuite.scala` (a test for the step)
- Modify: `src/test/scala/oathdigital/application/ForgeWalkerFixture.scala`
- Modify: `GameApplicationServiceSuite.scala:776-800, :937-960`, `WalkerDecisionProjectionSuite.scala:352`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (`forge` :293-301), `GameLogActionLineSuite.scala:81-88`
- Modify: `src/test/resources/gamelog/forge.{actor,other}.log`

**Interfaces:**
- Produces:
  - `Table#relicDeckTop(cards: (String | RelicId)*)(using munit.Location): Table`
  - `ForgeWalkerFixture.forgeTable: Table`
  - `ForgeWalkerFixture.parkedForge(gameId: String): (ExecutableCatalog, GameAccepted, PlayerId, SiteId)` (same signature)
  - `ForgeWalkerFixture.forgeReadyGame(gameId: String, cat: ExecutableCatalog = catalog): (GameApplicationService, GameAccepted, PlayerId, SiteId)`

- [ ] **Step 1: Write the failing `relicDeckTop` test.** Add it to `TableSuite`:

```scala
  test("relicDeckTop puts relics on top of the relic deck, first named on top"):
    val ready = Table.start.relicDeckTop("Dowsing Sticks", "Sticky Fire").ready
    assertEquals(ready.game.current.commonCards.relicDeck.take(2),
      Vector(CatalogNames.relic("Dowsing Sticks"),
        CatalogNames.relic("Sticky Fire")))
```

Run: `./sbtw "root/testOnly *TableSuite"`. Expected: a compile error.

- [ ] **Step 2: Add the step** beside `worldDeckTop`:

```scala
  /** These relics on top of the relic deck, first named on top. */
  def relicDeckTop(cards: (String | RelicId)*)(using munit.Location): Table =
    val ids = cards.toVector.map(CatalogNames.relic)
    ids.foldLeft(this)(_.moving(_)).update(_.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(relicDeck = ids ++
        c.commonCards.relicDeck))))
```

Run the `TableSuite` again. Expected: PASS.

- [ ] **Step 3: Rebuild the fixture.** Replace the real-command walk (`forgeReady`, the atlas and deck reordering, the conquest, the Searches and the Rest) with:

```scala
  /** p1 rules Ancient City with one warband, and the site holds three faceup
    * denizens with no tokens: the Forge's precondition. p1 has the 3 favor
    * the printed cost needs, or 2 favor and a secret for the mixed-cost
    * catalog. Dowsing Sticks tops the relic deck, so the forged relic is
    * known. */
  val forgeTable: Table = Table.start
    .warbandsAt("Ancient City", p1, 1).warbands(p1, 2)
    .denizen("Threatening Roar", "Ancient City")
    .denizen("Mushrooms", "Ancient City")
    .denizen("Mercenaries", "Ancient City")
    .favor(p1, 4)
    .relicDeckTop("Dowsing Sticks")
```

`forgeReadyGame(gameId, cat)` does `forgeTable.service(catalog = cat)`, then a journaled situation at the table, and returns `(service, accepted(situation), p1, Table.homeOf(p1))`. `parkedForge` builds on it as today. Keep `mixedForgeCostCatalog`, which rewrites Ancient City's cost. Delete `blankCampaignDice` if nothing else uses it.

- [ ] **Step 4: Move the callers.** At `GameApplicationServiceSuite:776` and `:937`, the tests used to build the service and pass it in. They now take it from `forgeReadyGame`. Every assertion stays. The relic-head check now reads Dowsing Sticks. `WalkerDecisionProjectionSuite:352` changes only its call. `LogScripts.forge` becomes `Script("forge", service, p1)` over `forgeReadyGame("forge", catalog)`, then the Forge commands as today, under `steadyDice`.
- [ ] **Step 5: Keep the Campaign check.** `GameLogActionLineSuite:81-88` read the Campaign start and winner lines from `forge`, which no longer campaigns. Point it at `raid`, whose Campaign is the script's subject. Keep the assertions. If `raid` phrases them differently, stop and report; do not loosen the assertion.
- [ ] **Step 6: Rewrite and review the goldens.** Run `GAMELOG_GOLDEN=write ./sbtw "root/testOnly *GameLogGoldenSuite"`. Only `forge.*` may change. The whole prefix (the conquest, Searches and Rest) drops out. Review the Forge lines.
- [ ] **Step 7: Gate and commit** with the message `test: build the Forge fixture on a table`.

---

### Task 5: Log scripts, actions batch

**Scripts:** `round`, `search`, `facedown-adviser`, `muster`, `trade`, `take-wealth`, `recover-failed`, `recover-succeeded`, `reveal-relic`, `banners`.

**Files:**
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`
- Modify: `GameLogHeadlineSuite.scala`, `GameLogStartLineSuite.scala`, `GameLogDetailSuite.scala`, and any other suite whose assertion names a setup fact these scripts dropped
- Modify: the goldens of these ten scripts

**Interfaces:**
- Consumes: `LogScripts.atTable` (Task 2).

Each script becomes `val (service, situation) = atTable(name, <table>, dice)`, followed by the script's own commands, unchanged except for seats. `Script(name, service, actor)`.

| Script | Table | Notes |
|---|---|---|
| round | `Table.start.turn(p1, Phase.Wake)` | EndWake(p1), Travel to "Broken Peaks", then Rest p1, then p2 and p3 each EndWake (their Wakes have no option, so they end by themselves: use `endingWake` on the situation), BeginRest and FinishRest. The log must open in Wake (HeadlineSuite :37-54), and `StartLineSuite:15-33` reads `steps(spentAt - 1)`, which needs an event before Travel. |
| search | `Table.start.worldDeckTop("Threatening Roar", "Fae Merchant", "Second Chance")` | Search `search:world`. |
| facedown-adviser | `Table.start.adviser(p1, "Wizard's Conclave", facedown = true)` | Keep the `facedownAdviser(name, placement)` variant signature (ActionLineSuite :99-109, DetailSuite :52). |
| muster | `Table.start.pawn(p1, "Deep Woods").edifice("Hiding Place", EdificeSide.Ruined, "Deep Woods")` | Muster. Drop the Travel. |
| trade | the muster table plus `.favor(p1, 2)` | Trade `secret`. DetailSuite :28-36 asserts the arranged "gained 1 favor from the Discord bank" line. Keep that one `Step.Arrange` after the first command, where the stream exists and the journaled Arrange works as today. |
| take-wealth | `Table.start.turn(p2, Phase.Wake).siteTokens("Broken Peaks", favor = 1)` | Actor p2. Take Wealth `favor`. HeadlineSuite :56-62 wants "Took" then "Act". |
| recover-failed / recover-succeeded / reveal-relic | `Table.start.pawn(p1, "Broken Peaks").relicAt("Sticky Fire", "Broken Peaks").relicAt("Cursed Cauldron", "Broken Peaks")` | `failingDice` / `steadyDice` as today. reveal-relic adds `RevealOwnedRelic`. |
| banners | `Table.start.favor(p1, 3)` | Challenge and PlaceBannerResource as today. |

Suite fixes:
- `GameLogHeadlineSuite:24` expected `"Setup", "Round 1"` from `round`. It now expects the opening `"Round 1", "<p1>'s turn", "Wake"`.
- `GameLogStartLineSuite` follows `round` and `augury` unchanged. Only its expectations of seats change.

- [ ] **Step 1:** Move the scripts one row at a time. After each one, run `./sbtw "root/testOnly oathdigital.application.gamelog.*"`. The golden suite fails until Step 2.
- [ ] **Step 2:** Rewrite and review the ten scripts' goldens.
- [ ] **Step 3:** Run the gate and commit with the message `test: start the action log scripts from tables`.

---

### Task 6: Log scripts, powers and negotiation batch

**Scripts:** `augury`, `gambling-hall`, `wolves`, `oracle`, `barbed-net`, `hunger`, `heirloom-kept`, `heirloom-returned`, `raid`, `negotiation-declined`, `negotiation-agreed`, `negotiation-disclosed`.

**Files:**
- Modify: `LogScripts.scala`, `GameLogPowerLinesSuite.scala`, `GameLogExchangeSuite.scala`, `GameLogCampaignSuite.scala` as needed
- Modify: the goldens of these twelve scripts
- Modify: `ParkedServiceFixture.scala` (delete `withWorldDeckTop`'s users here; keep it for `HungerSuite:134`; delete `topOfWorldDeck` once unused)

| Script | Table | Notes |
|---|---|---|
| augury | `Table.start.denizen("Augury", "Ancient City")` | Search with modifier `Augury.id`. |
| gambling-hall | `Table.start.denizen("Gambling Hall", "Ancient City").favor(p1, 2)` | Pin the richest bank with `bankFavor` if the answer depends on it. |
| wolves | `Table.start.denizen("Wolves", "Ancient City").secrets(p1, 2).warbands(p2, 4)` | The victim is p2, with the most warbands. |
| oracle | `Table.start.denizen("Oracle", "Ancient City").secrets(p1, 3)` | Pin the world deck with `worldDeckTop` so that the first Vision's depth is the one today's placement answer expects. Keep the `oracle(name, placement)` variant signature. |
| barbed-net | `Table.start.relic(p1, "Barbed Net").relicAt("Dowsing Sticks", "Ancient City").secrets(p1, 4)` | Add `relicAt` for the other relics if the peek line should list them as today. |
| hunger | `Table.start.adviser(p2, "Hunger").adviser(p2, "Birdsong", facedown = true)` | BeginRest(p1). p2's Wake is entered by play, so the forced Hunger step runs. Answer by default as today. The ProcedureRef coverage property relies on this script for `forced-wake`. |
| heirloom-kept / heirloom-returned | `Table.start.adviser(p1, "Family Heirloom", facedown = true).relicDeckTop("Dowsing Sticks")` | PlayFacedownAdviser with `adviser-faceup` and the choice. |
| raid | `Table.start.pawn(p2, "Ancient City").adviser(p2, "Birdsong", facedown = true)` | `raidDice`, the same answers. The Campaign lines that Task 4 pointed here stay. |
| negotiation-declined / -agreed | `Table.start.pawn(p2, "Ancient City")` | Negotiation parked, then the deals as today. |
| negotiation-disclosed | the above plus `.adviser(p2, "Birdsong", facedown = true)` | ExchangeSuite:45 reads the partner's facedown adviser in slot 0 now, not slot 1. Update the index and say why. |

- [ ] **Step 1:** Move the scripts one row at a time, running the gamelog suites after each.
- [ ] **Step 2:** Rewrite and review the twelve scripts' goldens.
- [ ] **Step 3:** Delete `arrangedForNext`, `topOfWorldDeck`, the `spread` parameter of `journaled`, and `journaled` itself, if only `woken` uses it. Keep whatever `woken` still needs.
- [ ] **Step 4:** Run the gate and commit with the message `test: start the power and negotiation log scripts from tables`.

---

### Task 7: Clean-up, checks and Result

**Files:**
- Modify: `ParkedServiceFixture.scala`, `ForgeWalkerFixture.scala`, `testkit/Situation.scala` (delete what `grep -rn` shows unused)
- Modify: `docs/superpowers/specs/2026-09-28-service-log-start-states-design.md` (the Result section)

- [ ] **Step 1: Delete unused code.** For each of `Step.Arrange`, `SituationDriver.arranging`, `ParkedServiceFixture.withWorldDeckTop`, `ForgeWalkerFixture.blankCampaignDice`, `Situation.pawnsAt` and `LogScripts.steadyDice`, run `grep -rn "<name>" src/test`. Delete it if it has no users. `Step.Arrange` stays if `trade` still uses it.
- [ ] **Step 2: Run the brittleness check.** Make `OathRules.wakeOptionOpen` (`OathRules.scala:141`) return `true`, run `./sbtw root/test`, and record the failing tests. Every failure must be a test about the auto-end itself, or one that stays on replay (`woken`, `SituationSuite`'s replay checks, HungerSuite's Setup test, `GameApplicationServiceSuite` 582, 996 and 318). Restore with `git checkout src/main/scala/oathdigital/gameplay/OathRules.scala`.
- [ ] **Step 3: Run the sequence check.** In `OathRules.beginGame` (`OathRules.scala:111-119`), append one extra `IgnoredRulesRecorded` diagnostic event after `GameStarted`. Use the smallest well-formed one the codec accepts: find its constructor in `model/GameEventProtocol.scala`. Run `./sbtw root/test`, and record the failures. Only tests that stay on replay may fail. Restore the file.
- [ ] **Step 4: Record speed.** Rerun the Step 0 script from Task 1 into `/private/tmp/start-states-after.txt`, and put both columns in the Result.
- [ ] **Step 5: Write the Result.** Append a `## Result` section to the spec with:
  - the counts from Steps 2 and 3;
  - the speed table;
  - each test kept on replay and why;
  - each golden whose non-setup lines changed and why;
  - notes for the readability and speed projects.

  Change the spec's status line to `Status: implemented (see Result).`
- [ ] **Step 6: Gate and commit** with the message `test: record the start-states result and remove the emptied fixtures`. Do not stage `docs/ROADMAP.md`.
