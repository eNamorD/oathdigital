# Power Log Lines, Slice 5 (Setup) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each implemented setup rule its own Game Log line, so the Setup section of the log says what the edifices did.

**Architecture:**

- The six setup rules are the two faces of three edifices: E02 (Great Market, Bandit Market), E06 (Great Forge, Broken Forge) and E22 (Proving Grounds, Empty Grounds). They live in `gameplay/powers/setup/`.
- Each rule adds its effect through a `Transform` that appends a `BuildOps` to the `SetupPawnPlaced` or `SetupEnd` window. Each gains a `Note` node right after that `BuildOps`, and declares the `NoteKey` that words it. This is the slice-1 emission path 1 (`Note`).
- Each window's expansion has no decision after the `BuildOps`, so a note there moves no parked decision.
- The formatter already posts a note in the Setup run among the setup lines. The Setup procedure has no start line, so nothing holds its notes. Task 2 pins this with a test.
- A note's `source` is the edifice card, `PowerSourceRef.Card(edifice)`. The spec says the setup rules, "whose own `source` is a game rule, pass their edifice card".

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change. Impeccable is not needed.

**Spec:** `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

- Before starting, read the Rulings and section 1, all of it. Then read section 2's "Setup" table, "Placement", and "Settled in slice 1" through "Settled in slice 4".
- The slice 4 plan, `docs/superpowers/plans/2026-09-26-power-log-lines-slice4.md`, shows how a contributing power adds its note after its `BuildOps`. It also shows how the power's suite reads that note. See Dazzle in its Task 3 and League Treaty in its Task 5.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build. Each file edited below lists the imports it needs.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`). Every file in this slice is under 150 lines.
- Import rules:
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - Test code may import `gameplay.walker`.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree:
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first.
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
  - To merge at the end, leave the worktree with `ExitWorktree` (`keep`) first. The worktree guard refuses `git -C` on the main checkout.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- Line wording follows the spec's "Setup" table, except where "Decisions this plan makes" below changes it. A template starts with an argument or a capital letter. `PowerNoteCatalogSuite` already checks this.
- Players are named by chip, never "you".
- **A note never comes before a decision already in the tree** (settled in slice 4). Every note in this plan goes last in its window's expansion.
- Baselines: record the server test count from your first full `./sbtw test` run in the worktree, and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.
- If a golden log under `src/test/resources/gamelog/` changes, stop and report which line changed. Do not regenerate it. No golden game stages E02, E06 or E22, so none should change.

### Decisions this plan makes that the spec left open

These were settled while planning (2026-09-26). Task 2 records each one in the spec.

1. **Amounts are what happened.** Every setup note reads the step before it, the rule's own `BuildOps` batch, so it states what that batch did. A rule whose batch changed nothing writes nothing. Examples: an empty relic deck, a Great Market in a region with no denizens, or a bank already empty.
2. **Bandit Market has three keys**, as Sticky Fire does. The spec's printed "1 favor on each bandit site … 1 favor from each bank" is not what happens when a bank runs dry or no site is bandit-ruled.
   - `placed-and-burned`: "Placed {2 favor} on the bandit sites and burned {6 favor} from the banks."
   - `placed`: "Placed {2 favor} on the bandit sites." when the placing leaves no favor in the banks to burn.
   - `burned`: "Burned {6 favor} from the banks." when no site is bandit-ruled.
   - Each site takes one favor, so the placed amount is also the number of sites. "site" or "sites" follows it through a `Plural`.
   - The burned amount is the favor the banks lost minus the favor the sites gained.
3. **Broken Forge lists its relics as cards:** "Broken Forge: Discarded {cards}." There is no list-of-sites argument. The relics at sites are facedown, so most viewers read "2 Relics" by the knowledge rule. The line's source is in the region, which already says where.
4. **Broken Forge and Empty Grounds share one sentence,** `EdificeSetupSupport.discarded`: "Discarded {cards}."
5. **Empty Grounds covers the generic Discard line.** Its `Discard.Denizen` operations write "Discarded {card} to the {Region} discard" today, as Dazzle's did. None of the other five rules has a generic line, so none of them covers one.
6. **Empty Grounds reads the state its transform was folded with**, as Dazzle does (settled in slice 4). The discard restrictions may refuse some of its discards. The note lists the cards it would discard at the fold that no site holds when the note runs.
7. **Great Forge reuses the relic draw sentence,** `RelicDraws.drew`: "{Red} drew {relic} facedown." `RelicDraws` gains `drewNote(card: PowerSourceRef, player)`, and `drawNote` delegates to it.
8. **Proving Grounds uses the shared gain sentence,** `NoteSupport.gainedKey("gained")` read with `NoteSupport.gainedNote` over warbands: "{Red} gained {3 warbands}."

### The lines

| Rule | Window | Key | Line | Covers |
|---|---|---|---|---|
| Great Market | SetupEnd | `placed` | Great Market: Placed {3 favor} on {site}. | |
| Bandit Market | SetupEnd | `placed-and-burned`, `placed`, `burned` | Bandit Market: Placed {1 favor} on the bandit site and burned {6 favor} from the banks. | |
| Great Forge | SetupPawnPlaced | `used` | Great Forge: {Red} drew {relic} facedown. | |
| Broken Forge | SetupPawnPlaced | `discarded` | Broken Forge: Discarded {cards}. | |
| Proving Grounds | SetupPawnPlaced | `gained` | Proving Grounds: {Red} gained {3 warbands}. | |
| Empty Grounds | SetupEnd | `discarded` | Empty Grounds: Discarded {cards}. | the Discard line |

## File Structure

| File | Change |
|---|---|
| `src/test/scala/oathdigital/gameplay/setup/SetupWalkDriver.scala` | `driveWithEvents` also returns the walk's events |
| `src/main/scala/oathdigital/gameplay/powers/RelicDraws.scala` | `drewNote` for a card source |
| `src/main/scala/oathdigital/gameplay/powers/setup/EdificeSetupSupport.scala` | the shared `discarded` sentence |
| `src/main/scala/oathdigital/gameplay/powers/setup/GreatMarketRules.scala` | Great Market and Bandit Market notes |
| `src/main/scala/oathdigital/gameplay/powers/setup/GreatForgeRules.scala` | Great Forge and Broken Forge notes |
| `src/main/scala/oathdigital/gameplay/powers/setup/ProvingGroundsRules.scala` | Proving Grounds and Empty Grounds notes |
| `src/test/scala/oathdigital/gameplay/powers/setup/*RulesSuite.scala` | each line's exact sentence |
| `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala` | a setup note posts among the setup lines |
| `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`, `docs/ROADMAP.md` | record the slice |

---

### Task 1: The Markets and the Forges say what they did

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/setup/SetupWalkDriver.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/RelicDraws.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/setup/EdificeSetupSupport.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/setup/GreatMarketRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/setup/GreatForgeRules.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/setup/GreatMarketRulesSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/setup/GreatForgeRulesSuite.scala`

**Interfaces:**
- Test setup: `SetupWalkDriver` answers every decision with its first option, so every player places a pawn at `FirstGameSetupFixture.sites.head`, where the suites stage the edifice. A pawn-placed rule (Great Forge, Proving Grounds) therefore fires once per player. Broken Forge also fires once per player, but only the first time finds relics to discard.
- Consumes (existing):
  - `NoteText.said(id: PowerId, keys: Vector[NoteKey], events: Vector[OathEvent]): Vector[NoteText.Said]`
  - `NoteText.Said(key: String, text: String, covers: Boolean)`
  - `NoteSupport.relicsGained(step, player): Vector[RelicId]`
  - `Note(power, build: NoteStates => Option[PowerNote], covers = false)`
  - `NoteArg.Cards(ids: Vector[CardId])`
- Produces:
  - `SetupWalkDriver.driveWithEvents(ready: ReadyGame, tree: Operation, powers: WalkerPowers): (ReadyGame, Vector[OathEvent])`
  - `RelicDraws.drewNote(card: PowerSourceRef, player: PlayerId)(states: NoteStates): Option[PowerNote]`
  - `EdificeSetupSupport.discarded: NoteKey` (name `"discarded"`, "Discarded {cards}.")
  - `GreatMarket.placed`, `BanditMarket.placedAndBurned`, `BanditMarket.placed`, `BanditMarket.burned`, `BrokenForge` declares `EdificeSetupSupport.discarded`, and `GreatForge` declares `RelicDraws.drew`

- [ ] **Step 1: Let the setup driver return the walk's events**

In `SetupWalkDriver.scala`, replace `driveToCompletion` with the two methods below. The imports stay as they are.

```scala
  def driveToCompletion(ready: ReadyGame, tree: Operation, powers: WalkerPowers)
      : ReadyGame = driveWithEvents(ready, tree, powers)._1

  /** `driveToCompletion`, also returning every event the walk journaled, in
    * walk order, for the suites that read a rule's notes. */
  def driveWithEvents(ready: ReadyGame, tree: Operation, powers: WalkerPowers)
      : (ReadyGame, Vector[OathEvent]) =
    var state = ready
    var journaled = Vector.empty[OathEvent]
    var outcome = ProcedureWalker.advance(state, tree, None, powers).toOption.get
    while outcome.isInstanceOf[WalkerOutcome.Parked] do
      val WalkerOutcome.Parked(pending, events) = outcome: @unchecked
      journaled = journaled ++ events
      state = foldRecordedOps(state, events, "setup walk failed")
      val decide = ProcedureWalker.parkedDecide(state, tree, pending, powers).get
      val park = Park(decide, state, decide.owner,
        TriggeredProcedureRef.Setup)
      val answer = Answered(decide.decisionId, Situation.defaultAnswer
        .applyOrElse(park, _ => fail(
          s"SetupWalkDriver cannot auto-answer ${decide.query}")),
        decide.owner)
      outcome = ProcedureWalker.resolve(state, tree, pending, answer, powers)
        .toOption.get
    val WalkerOutcome.Finished(finished, events) = outcome: @unchecked
    (finished, journaled ++ events)
```

- [ ] **Step 2: Write the failing Market tests**

In `GreatMarketRulesSuite.scala`, add `NoteText` to the powers import:

```scala
import oathdigital.gameplay.powers.{CardStaging, NoteText, WalkerPowerCatalog}
```

Then append the following at the end of the class. The `withBandits` staging repeats the existing Bandit Market test's.

```scala
  // ---- Lines ----

  private def events(ready: ReadyGame): Vector[OathEvent] =
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    SetupWalkDriver.driveWithEvents(ready, tree, powers)._2

  private val great = GreatMarket.forCatalog(catalog).get
  private val bandit = BanditMarket.forCatalog(catalog).get

  /** Exactly `bandited` is bandit-ruled; every other site is empty. */
  private def banditsOnly(ready: ReadyGame, bandited: Set[SiteId]): ReadyGame =
    ready.updateCurrent(c => c.copy(map = c.map.copy(
      sites = c.map.sites.map { case (s, state) =>
        s -> (if bandited(s) then
          state.copy(forces = SiteForces.Occupied(ForceKind.Bandit, 1))
        else state.copy(forces = SiteForces.Empty))
      })))

  test("Great Market writes the favor it placed, and where"):
    val staged = stagedAt(EdificeSide.Intact)
    val region = staged.game.current.map.regionOf(site).get
    val count = staged.game.current.map.inPlay
      .filter(s => staged.game.current.map.regionOf(s).contains(region))
      .flatMap(s => staged.game.current.map.sites(s).denizens).size
    assertEquals(NoteText.said(great.id, great.noteKeys, events(staged)),
      Vector(NoteText.Said("placed",
        s"Placed $count favor on ${site.value}.", covers = false)))

  test("Bandit Market writes the favor it placed and the favor it burned"):
    val staged = banditsOnly(stagedAt(EdificeSide.Ruined),
      Set(FirstGameSetupFixture.sites(1)))
    assertEquals(NoteText.said(bandit.id, bandit.noteKeys, events(staged)),
      Vector(NoteText.Said("placed-and-burned",
        s"Placed 1 favor on the bandit site and burned ${Suit.all.size} " +
          "favor from the banks.", covers = false)))

  test("Bandit Market with no bandit site writes only the burn"):
    val staged = banditsOnly(stagedAt(EdificeSide.Ruined), Set.empty)
    assertEquals(NoteText.said(bandit.id, bandit.noteKeys, events(staged)),
      Vector(NoteText.Said("burned",
        s"Burned ${Suit.all.size} favor from the banks.", covers = false)))
```

- [ ] **Step 3: Write the failing Forge tests**

In `GreatForgeRulesSuite.scala`, add `NoteText` to the powers import:

```scala
import oathdigital.gameplay.powers.{CardStaging, NoteText, WalkerPowerCatalog}
```

Then append the following at the end of the class.

```scala
  // ---- Lines ----

  private def events(ready: ReadyGame): Vector[OathEvent] =
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    SetupWalkDriver.driveWithEvents(ready, tree, powers)._2

  private val great = GreatForge.forCatalog(catalog).get
  private val broken = BrokenForge.forCatalog(catalog).get

  test("Great Forge writes the relic its player drew"):
    val staged = stagedAt(EdificeSide.Intact)
    val topRelic = staged.game.current.commonCards.relicDeck.head
    val said = NoteText.said(great.id, great.noteKeys, events(staged))
    assertEquals(said.head, NoteText.Said(NoteKey.Used,
      s"${firstPlayer.value} drew ${topRelic.value} facedown.", covers = false))
    // The driver places every pawn at the first site, so each player draws.
    assertEquals(said.size, staged.game.current.players.size)

  test("Great Forge with an empty relic deck writes nothing"):
    val staged = stagedAt(EdificeSide.Intact).updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(relicDeck = Vector.empty)))
    assertEquals(NoteText.said(great.id, great.noteKeys, events(staged)),
      Vector.empty)

  test("Broken Forge writes the relics it discarded"):
    val staged = stagedAt(EdificeSide.Ruined)
    val current = staged.game.current
    val region = current.map.regionOf(site).get
    val relics = current.map.inPlay
      .filter(s => current.map.regionOf(s).contains(region))
      .flatMap(s => current.map.sites(s).relics.map(_.id.value))
    assert(relics.nonEmpty)
    assertEquals(NoteText.said(broken.id, broken.noteKeys, events(staged)),
      Vector(NoteText.Said("discarded",
        s"Discarded ${relics.mkString(", ")}.", covers = false)))
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.setup.GreatMarketRulesSuite oathdigital.gameplay.powers.setup.GreatForgeRulesSuite"`

Expected: FAIL. The new tests see no notes, because every rule's `noteKeys` is empty and no rule writes a `Note`. The existing tests still pass.

- [ ] **Step 5: Add `drewNote` to `RelicDraws`**

In `RelicDraws.scala`, replace `drawNote` with:

```scala
  /** The relic the step before the note drew, restated. Nothing when that
    * step drew none. */
  def drawNote(source: DecisionOptionRef, player: PlayerId)(
      states: NoteStates): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(drewNote(_, player)(states))

  /** `drawNote` for a power whose card is `card`. */
  def drewNote(card: PowerSourceRef, player: PlayerId)(
      states: NoteStates): Option[PowerNote] = for
    step <- states.previous
    relic <- NoteSupport.relicsGained(step, player).headOption
  yield drew(card, NoteArg.Player(player), NoteArg.Card(relic))
```

- [ ] **Step 6: Add the shared sentence to `EdificeSetupSupport`**

In `EdificeSetupSupport.scala`, append inside `object EdificeSetupSupport`:

```scala

  /** "Discarded {cards}.": Broken Forge's relics and Empty Grounds'
    * denizens. */
  val discarded: NoteKey = NoteKey("discarded", Vector(
    NotePart.Text("Discarded "), NotePart.Arg(0), NotePart.Text(".")))
```

Its imports (`oathdigital.model._`) already cover `NoteKey` and `NotePart`.

- [ ] **Step 7: Write the Market notes**

In `GreatMarketRules.scala`, make these changes:

1. In `MarketRule`, add an abstract `note` after the abstract `build`:

```scala
  /** The line this face writes after its effect. */
  protected def note(at: SiteId): Note
```

2. In `MarketRule.contributions`, append the note after the `BuildOps`:

```scala
      case Some(site) =>
        ops :+ BuildOps((ready, _) => build(ready, site)) :+ note(site)
```

3. Add a companion `object MarketRule` directly after the class, before `GreatMarket`:

```scala
object MarketRule:
  /** The favor on `site` in `ready`. */
  def siteFavor(ready: ReadyGame, site: SiteId): Int =
    ready.game.current.map.sites.get(site).fold(0)(_.tokens.favor)

  def favor(amount: Int): NoteArg = NoteArg.Amount(amount, NoteUnit.Favor)
```

4. In `GreatMarket`, add:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(GreatMarket.placed)

  protected def note(at: SiteId): Note = Note(id, states => for
    step <- states.previous
    placed = MarketRule.siteFavor(step._2, at) - MarketRule.siteFavor(step._1, at)
    if placed > 0
  yield GreatMarket.placed(PowerSourceRef.Card(edifice), MarketRule.favor(placed),
    NoteArg.Site(at)))
```

and in `object GreatMarket`:

```scala
  /** "Placed {3 favor} on {site}." */
  val placed: NoteKey = NoteKey("placed", Vector(NotePart.Text("Placed "),
    NotePart.Arg(0), NotePart.Text(" on "), NotePart.Arg(1), NotePart.Text(".")))
```

5. In `BanditMarket`, add:

```scala
  override def noteKeys: Vector[NoteKey] =
    Vector(BanditMarket.placedAndBurned, BanditMarket.placed, BanditMarket.burned)

  /** Each bandit site took one favor; the banks lost that and the burn. */
  protected def note(at: SiteId): Note = Note(id, states =>
    states.previous.flatMap { case (before, after) =>
      val placed = after.game.current.map.sites.keysIterator.map(site =>
        MarketRule.siteFavor(after, site) - MarketRule.siteFavor(before, site))
        .filter(_ > 0).sum
      val drained = Suit.all.map(suit => before.banks.favor.getOrElse(suit, 0) -
        after.banks.favor.getOrElse(suit, 0)).sum
      val burned = drained - placed
      val card = PowerSourceRef.Card(edifice)
      if placed > 0 && burned > 0 then Some(BanditMarket.placedAndBurned(card,
        MarketRule.favor(placed), MarketRule.favor(burned)))
      else if placed > 0 then Some(BanditMarket.placed(card, MarketRule.favor(placed)))
      else if burned > 0 then Some(BanditMarket.burned(card, MarketRule.favor(burned)))
      else None
    })
```

and in `object BanditMarket`:

```scala
  /** "Placed {2 favor} on the bandit sites and burned {6 favor} from the
    * banks." */
  val placedAndBurned: NoteKey = NoteKey("placed-and-burned", Vector(
    NotePart.Text("Placed "), NotePart.Arg(0),
    NotePart.Plural(0, " on the bandit site", " on the bandit sites"),
    NotePart.Text(" and burned "), NotePart.Arg(1),
    NotePart.Text(" from the banks.")))
  /** "Placed {2 favor} on the bandit sites." */
  val placed: NoteKey = NoteKey("placed", Vector(NotePart.Text("Placed "),
    NotePart.Arg(0),
    NotePart.Plural(0, " on the bandit site.", " on the bandit sites.")))
  /** "Burned {6 favor} from the banks." */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Text("Burned "),
    NotePart.Arg(0), NotePart.Text(" from the banks.")))
```

`BanditMarket.note` does not read `at`. The build's `-Wunused` set does not include parameters, so an unread parameter compiles.

The file's imports stay as they are: `oathdigital.model._` covers `Note`, `NoteKey`, `NotePart`, `NoteArg`, `NoteUnit` and `PowerSourceRef`.

- [ ] **Step 8: Write the Forge notes**

In `GreatForgeRules.scala`, make these changes:

1. In `ForgeRule`, add an abstract `note` after the abstract `build`:

```scala
  /** The line this face writes after its effect. */
  protected def note(actor: PlayerId, at: SiteId): Note
```

2. In `ForgeRule.contributions`, append the note after the `BuildOps`:

```scala
      case Some((actor, site)) => ops :+ BuildOps((ready, _) =>
        build(ready, actor, site)) :+ note(actor, site)
```

3. In `GreatForge`, add:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(RelicDraws.drew)

  protected def note(actor: PlayerId, at: SiteId): Note =
    Note(id, RelicDraws.drewNote(PowerSourceRef.Card(edifice), actor))
```

4. In `BrokenForge`, add:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(EdificeSetupSupport.discarded)

  /** The relics at sites before the step that no site holds after it. */
  protected def note(actor: PlayerId, at: SiteId): Note = Note(id, states => for
    step <- states.previous
    gone = BrokenForge.siteRelics(step._1)
      .filterNot(BrokenForge.siteRelics(step._2).contains)
    if gone.nonEmpty
  yield EdificeSetupSupport.discarded(PowerSourceRef.Card(edifice),
    NoteArg.Cards(gone)))
```

and in `object BrokenForge`:

```scala
  /** Every relic at a site in play, in map order. */
  private def siteRelics(ready: ReadyGame): Vector[CardId] =
    val current = ready.game.current
    current.map.inPlay.flatMap(site => current.map.sites(site).relics.map(_.id))
```

The imports stay as they are. `RelicDraws` is already imported from `oathdigital.gameplay.powers`.

- [ ] **Step 9: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.setup.* oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.gameplay.setup.*"`

Expected: PASS, including the existing effect tests.

If "Broken Forge writes the relics it discarded" fails because the list order differs, check the order `BrokenForge.build` discards in. Both the test and `siteRelics` read `inPlay` order, so they should agree.

- [ ] **Step 10: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/setup/SetupWalkDriver.scala \
  src/main/scala/oathdigital/gameplay/powers/RelicDraws.scala \
  src/main/scala/oathdigital/gameplay/powers/setup/EdificeSetupSupport.scala \
  src/main/scala/oathdigital/gameplay/powers/setup/GreatMarketRules.scala \
  src/main/scala/oathdigital/gameplay/powers/setup/GreatForgeRules.scala \
  src/test/scala/oathdigital/gameplay/powers/setup/GreatMarketRulesSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/setup/GreatForgeRulesSuite.scala
git commit -m "feat(powers): the Markets and the Forges say what they did at setup"
```

Add the trailer line to the message.

---

### Task 2: The Grounds say what they did; setup lines post among setup; record the slice

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/setup/ProvingGroundsRules.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/setup/ProvingGroundsRulesSuite.scala`
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`
- Modify: `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`
- Modify: `docs/ROADMAP.md`

**Interfaces:**
- Consumes:
  - `SetupWalkDriver.driveWithEvents`
  - `EdificeSetupSupport.discarded`
  - `NoteSupport.gainedKey(name): NoteKey`
  - `NoteSupport.gainedNote(key, card, player, unit, read)(states)`
  - `NoteSupport.warbands(step, player): Int`
  - `LogScripts.inserted`, `usePower`, `withoutNotes`, `texts`, `name` (all existing)
- Produces: `ProvingGrounds.gained: NoteKey`, and `EmptyGrounds` declares `EdificeSetupSupport.discarded`

- [ ] **Step 1: Write the failing Grounds tests**

In `ProvingGroundsRulesSuite.scala`, add `NoteText` to the powers import:

```scala
import oathdigital.gameplay.powers.{CardStaging, NoteText, PlayerFacts, WalkerPowerCatalog}
```

Then append the following at the end of the class.

```scala
  // ---- Lines ----

  private def events(ready: ReadyGame): Vector[OathEvent] =
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    SetupWalkDriver.driveWithEvents(ready, tree, powers)._2

  private val proving = ProvingGrounds.forCatalog(catalog).get
  private val empty = EmptyGrounds.forCatalog(catalog).get

  test("Proving Grounds writes the warbands its player gained"):
    val staged = stagedAt(EdificeSide.Intact)
    val said = NoteText.said(proving.id, proving.noteKeys, events(staged))
    assertEquals(said.head, NoteText.Said("gained",
      s"${firstPlayer.value} gained 3 warbands.", covers = false))
    // The driver places every pawn at the first site, so each player gains.
    assertEquals(said.size, staged.game.current.players.size)

  test("Empty Grounds writes the cards it discarded, in place of the Discard line"):
    val staged = stagedAt(EdificeSide.Ruined)
    val current = staged.game.current
    val region = current.map.regionOf(site).get
    val others = current.map.inPlay
      .filter(s => current.map.regionOf(s).contains(region))
      .flatMap(s => current.map.sites(s).denizens.map(_.id.value))
      .filterNot(_ == edifice.value)
    assert(others.nonEmpty)
    assertEquals(NoteText.said(empty.id, empty.noteKeys, events(staged)),
      Vector(NoteText.Said("discarded",
        s"Discarded ${others.mkString(", ")}.", covers = true)))
```

- [ ] **Step 2: Write the failing placement test**

In `GameLogPowerLinesSuite.scala`, append this test at the end of the class. The imports already cover everything it uses.

```scala
  test("a setup note posts under the Setup headline, among the setup lines"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val placed = steps.indexWhere(_.event.event match
      case step: WalkerStepRecorded => step.ops.exists {
        case Move(Piece.Pawn(_), _, _, _) => true
        case _ => false
      }
      case _ => false)
    val noted = inserted(steps, placed,
      saying(script.actor, NoteArg.Amount(2, NoteUnit.Favor)))
    val all = texts(noting.format(noted, None))
    val line = all.indexWhere(_.endsWith(s"${name(script.actor)} said 2 favor."))
    assert(all.indexOf("Setup") < line && line < all.indexOf("Round 1"), all)
    assert(all.take(line).exists(_.contains(" placed pawn at ")), all)
    assertPrefixStable(noted)
```

This test pins behavior the formatter already has. It is expected to pass at once. It passes because Setup has no start line, so `StartLines.opens` is false for it and its notes post where they fall. If it fails, stop and report. Do not change the formatter to make it pass.

- [ ] **Step 3: Run the tests to verify the Grounds tests fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.setup.ProvingGroundsRulesSuite oathdigital.application.gamelog.GameLogPowerLinesSuite"`

Expected:
- The two new Grounds tests FAIL; they see no notes.
- The placement test PASSES.
- Every existing test passes.

- [ ] **Step 4: Write the Grounds notes**

In `ProvingGroundsRules.scala`, make these changes:

1. Add `NoteSupport` to the powers import:

```scala
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport, PlayerFacts}
```

2. In `ProvingGrounds`, add:

```scala
  override def noteKeys: Vector[NoteKey] = Vector(ProvingGrounds.gained)
```

and change its `contributions` case to append the note:

```scala
      case Some((actor, _)) => ops :+ BuildOps((ready, _) => build(ready, actor)) :+
        Note(id, NoteSupport.gainedNote(ProvingGrounds.gained,
          PowerSourceRef.Card(edifice), actor, NoteUnit.Warband,
          NoteSupport.warbands))
```

In `object ProvingGrounds`, add:

```scala
  /** "{player} gained {3 warbands}." */
  val gained: NoteKey = NoteSupport.gainedKey("gained")
```

3. In `EmptyGrounds`, move the candidate read out of `build` into its own method. Then `build` and the note share it.

```scala
  override def noteKeys: Vector[NoteKey] = Vector(EdificeSetupSupport.discarded)

  /** Every card at a site in `site`'s region except this edifice, in map
    * order. An intact edifice among them is locked and stays. */
  private def candidates(ready: ReadyGame, site: SiteId)
      : Vector[(SiteId, SiteDenizenState)] =
    val current = ready.game.current
    current.map.regionOf(site).toVector.flatMap(region =>
      current.map.inPlay.filter(s => current.map.regionOf(s).contains(region)))
      .flatMap(s => current.map.sites(s).denizens.map(s -> _))
      .filterNot { case (s, card) => s == site && card.id.value == edifice.value }

  /** The cards Empty Grounds would discard in `before` that no site holds
    * now. */
  private def discardNote(before: ReadyGame, site: SiteId)(
      states: NoteStates): Option[PowerNote] =
    val sites = states.now.game.current.map.sites.values
    val gone = candidates(before, site).map(_._2.id).filterNot(id =>
      sites.exists(_.denizens.exists(_.id == id)))
    Option.when(gone.nonEmpty)(EdificeSetupSupport.discarded(
      PowerSourceRef.Card(edifice), NoteArg.Cards(gone)))
```

In `build`, keep the `regionOf` check, which reports a site not in play. Replace the inline `destination`/`candidates` read with:

```scala
    current.map.regionOf(site).toRight(OathViolation.InvalidEventOrder(
      s"${site.value} is not in play")).flatMap { region =>
      val destination = CardPlay.nextRegion(region)
      candidates(ready, site).foldLeft[Either[OathViolation, Vector[CoreOperation]]](
          Right(Vector.empty)):
        // the fold's body is unchanged
```

The `val current` in `build` is still needed only if the fold body reads it. Remove it if `-Wunused` reports it.

4. In `EmptyGrounds.contributions`, append the covering note after the `BuildOps`:

```scala
      case Some(site) => ops :+ BuildOps((ready, _) => build(ready, site),
        restrictions = (ready, _) =>
          Vector(new DiscardRestrictions(catalog, ready.setup.firstPlayer))) :+
        Note(id, discardNote(ctx.state, site), covers = true)
```

`SiteDenizenState`, `NoteStates`, `PowerNote`, `Note`, `NoteArg`, `NoteUnit` and `PowerSourceRef` come from `oathdigital.model._`, which is already imported.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.setup.* oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.application.gamelog.*"`

Expected: PASS. No golden log changes.

- [ ] **Step 6: Record the slice in the spec**

In `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`:

1. After the status paragraph that begins "**Slice 4 (2026-09-26):**", add:

```markdown
**Slice 5 (2026-09-26):** setup. "Settled in slice 5" records what it
decided.
```

2. Replace the whole table under "### Setup" with:

```markdown
| Card | Line | Covers |
|---|---|---|
| Great Market | Great Market: Placed {3 favor} on {site}. | |
| Bandit Market, key `placed-and-burned` | Bandit Market: Placed {1 favor} on the bandit site and burned {6 favor} from the banks. | |
| Bandit Market, key `placed`, when no favor is left to burn | Bandit Market: Placed {1 favor} on the bandit site. | |
| Bandit Market, key `burned`, when no site is bandit-ruled | Bandit Market: Burned {6 favor} from the banks. | |
| Great Forge | Great Forge: {Red} drew {relic} facedown. | |
| Broken Forge | Broken Forge: Discarded {cards}. | |
| Proving Grounds | Proving Grounds: {Red} gained {3 warbands}. | |
| Empty Grounds | Empty Grounds: Discarded {cards}. | the Discard line |
```

3. Directly before "## Slices", add:

```markdown
## Settled in slice 5

The fifth slice settled these:

- Each setup rule's note goes last in its window's expansion, after the
  rule's own `BuildOps`, and reads that step. A rule whose step changed
  nothing writes nothing: an empty relic deck, an empty bank, or a region
  with no denizens.
- Bandit Market writes what it did, in three keys, as Sticky Fire does:
  the favor placed on the bandit sites, the favor burned from the banks,
  or both. One favor goes to each site, so the placed amount names the
  number of sites.
- Broken Forge lists the relics it discarded as cards, "Discarded {cards}",
  and Empty Grounds shares that sentence. Facedown relics read by their
  back.
- Empty Grounds covers the generic Discard line, and reads the state its
  transform was folded with, as Dazzle does.
- Great Forge shares the relic draw sentence, and Proving Grounds the gain
  sentence.
- The Setup procedure has no start line, so its notes post where they
  fall, among the setup lines.
```

- [ ] **Step 7: Update the roadmap**

In `docs/ROADMAP.md`, replace:

```markdown
and altered procedure has its line. Slice 5 remains: setup.
```

with:

```markdown
and altered procedure has its line. Slice 5 gives every setup rule its
line.
```

- [ ] **Step 8: Run the gates**

Run: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. The server test count is the baseline plus 9.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/setup/ProvingGroundsRules.scala \
  src/test/scala/oathdigital/gameplay/powers/setup/ProvingGroundsRulesSuite.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala \
  docs/superpowers/specs/2026-09-26-power-log-lines-design.md docs/ROADMAP.md
git commit -m "feat(powers): the Grounds say what they did at setup; record slice 5"
```

Add the trailer line to the message.

---

### Checkpoint A

After Task 2, a reviewer checks the whole slice before the branch finishes:

- Every row of the spec's new Setup table has a note and an exact-sentence test.
- Every note goes last in its window's expansion, after its rule's `BuildOps`, and no `Decide` follows it there.
- Only Empty Grounds covers, and the line it covers is the generic Discard line.
- `ProvingGroundsRules.scala`'s `build` still reports a site that is not in play.
- The spec's tables and "Settled in slice 5" match the code.
- The gates pass, and no golden log changed.
