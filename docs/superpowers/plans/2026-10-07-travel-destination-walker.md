# Travel Destination in the Walker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Travel starts with no selection and parks on a board decision over destinations, each badged with its cost or shown unavailable with why. Way Station asks its non-ruler whether to pay favor instead of Supply. The older board targets retire, and every hidden option explains itself on hover instead of in the Game Log.

**Architecture:**

- **Start facts.** A registry entry may declare `startFacts`, which the engine derives at start, stores as `walkerStartFacts`, journals on `WalkerParked`, and clears on `WalkerCompleted`. A procedure's `rebuild` reads them from the state it is handed. Travel's source site is its one start fact.
- **Tree.** Travel's tree is `Sequence(Decide(travel.destination), Branch(eligibility ⊃ cost ⊃ [SpendSupply, Move]))`. The entry opts in to `requiresPlayableOption`, so a start with no reachable destination is refused.
- **Pricing.** `WalkerSimulation.previewParked` gains a follow-up mode that previews a further choose-one an answer parks on. A registry entry may declare `optionPricing`, which the projector uses in place of the playable-option filter. Travel's pricing badges each accepted destination and marks each rejected one `Unavailable` with its shortfall.
- **Way Station** is a persistent `Transform` at `TravelCost` (priority 3) that removes its ruler's Supply payment, or asks a non-ruler a Panel question.
- **Hidden notes.** The walker stops journaling hidden-option notes; each note stays the option's `Unavailable` reason. Narrow Pass gains a Travel note.
- **Frontend.** A `beginTravel` control starts the walker through the modifier flow. Site badges draw on the site box. The board targets and the modifier flow's targets stage are deleted.

**Tech Stack:** Scala 3 on the JVM (server, munit) and Scala.js 3 (frontend, scalajs-dom, munit on jsdom), built through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-10-07-travel-destination-walker-design.md`. Read the whole spec before starting. The undo slice 4 plan (`docs/superpowers/plans/2026-10-07-undo-slice4.md`) moved the facedown-adviser pick into the walker the same way; its commits `7d7c254b` and `79de94af` are the closest template for Tasks 2 and 6.

## Global Constraints

- **Build rules.** `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn` on every project. An unused import, private member or local fails the build: delete whatever the compiler names.
- **File size.** Every Scala file stays at or under 800 lines (`scripts/check-architecture.py`).
- **Ids and wording, exactly:**
  - Destination decision id `travel.destination`, spelled once as `TravelProcedure.destinationDecisionId`; heading `Choose a Travel destination`.
  - Way Station decision id `travel.way-station`, spelled once as `WayStation.decisionId`; heading `Way Station: spend no Supply?`; buttons `Spend {n} Supply` (ref `DecisionOptionRef.Button("supply")`) then `Give 1 favor to {ruler id}` or, under bandits, `Burn 1 favor` (ref `DecisionOptionRef.Button("favor")`).
  - Badges: `2 Supply`, `0 Supply`, `2 Supply · 1 favor`, `1 favor`, `2 Supply or 1 favor`.
  - Shortfalls: `Needs 3 Supply; you have 2`, `Needs 1 favor; you have 0`, fallback `Cannot travel there now`.
  - Narrow Pass Travel reason: `Narrow Pass: Cannot travel to other sites in the region.` Campaign reason: `Narrow Pass: Cannot target other sites in the region.` Vision reason ends `Cannot play a Vision faceup.`
  - Start refusals: `travel takes no start selection`; a resume without its fact: `a resumed Travel needs its source site as its one start fact`.
  - Replay mismatch: `durable walker park start facts do not match the recorded start`.
  - Legal control `beginTravel`; button label `Travel`, class `act-action travel-action`, section kind `travel`.
- **Rulings** (spec, "Rulings"): modifiers stay before the start; a selected modifier's card payment is not in the badge; when a restriction and a shortfall both apply, the restriction's reason shows; Way Station asks even when one answer is left; every `travel.` decision is narrated.
- **No CSS change.** The site badge reuses `.target-detail-badge`, which `frontend/styles.css` already places on a `.site` box. Impeccable is not needed.
- **Never touch the live database `var/oathdigital`.** Never run `preview_start`.
- **Concurrent sessions.** Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree. `EnterWorktree` branches from `origin`, which lags local `main`: fast-forward the new branch to local `main` first (`git merge --ff-only main`). Symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`. Copy `.superpowers/` from the main checkout if a ledger is in use.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Verification commands:**
  - One server suite: `./sbtw "testOnly oathdigital.gameplay.TravelProcedureSuite"`.
  - One frontend suite: `./sbtw "frontend/testOnly oathdigital.frontend.ModifierFlowSuite"`.
  - Whole server or frontend: `./sbtw test` or `./sbtw frontend/test`.
  - Full gate (Task 7): `./sbtw "test" "frontend/test"`, then `./sbtw frontend/fullOptJS`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`, then `git diff --check`.

---

### Task 1: Start facts

Persistence and threading for engine-derived start facts. No procedure declares any yet; Task 2's Travel is the first.

**Files:**
- Modify: `src/main/scala/oathdigital/model/GameState.scala:161-174`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala:95-111`
- Modify: `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala:30-40,262-284`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala:99-155`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` (`Entry`, accessors)
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` (`startWalker`, `startTriggered`, the three `resumeWalker` callers, `walkerTransition`)
- Test: `src/test/scala/oathdigital/serialization/WalkerDecisionWireSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/walker/WalkerStartFactsReplaySuite.scala`

**Interfaces:**
- Produces:
  - `CurrentGameState.walkerStartFacts: Vector[DecisionOptionRef] = Vector.empty`.
  - `WalkerParked(procedure, at, answered, modifiers, startArgs, startFacts: Vector[DecisionOptionRef] = Vector.empty)`.
  - `WalkerProcedureRegistry.Entry.startFacts: (ExecutableCatalog, ReadyGame, PlayerId) => Vector[DecisionOptionRef]`, default empty.
  - `WalkerProcedureRegistry.startFacts(procedure: ProcedureRef, catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId): Vector[DecisionOptionRef]`.

- [ ] **Step 1: Write the failing tests**

Append to `WalkerDecisionWireSuite` after the test "a parked Travel round trips its start selection":

```scala
  test("a park round trips its start facts, and a park without them reads " +
      "as having none"):
    val park = WalkerParked(ActionRef.Travel, Vector("0"), Vector.empty,
      Vector.empty, Vector.empty)
    assert(encodedStreamThatRoundTrips("facts", Vector[OathEvent](
      park.copy(startFacts = Vector(DecisionOptionRef.Site(
        SiteId("site:source")))))).contains("\"startFacts\""))
    val withoutFacts = encodedStream("no-facts", Vector[OathEvent](park))
      .replace(",\"startFacts\":[]", "")
    assert(!withoutFacts.contains("startFacts"), withoutFacts)
    assertEquals(accepted(GameEventWire.decodeStream(withoutFacts))
      .map(_.event), Vector[OathEvent](park))
```

Import `oathdigital.testkit.Unwrap.accepted` if the suite lacks it.

Create `src/test/scala/oathdigital/gameplay/walker/WalkerStartFactsReplaySuite.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}

/** Start facts are restored by replay, checked against the recorded start on
  * every later park, and cleared when the walk completes (travel destination
  * design, ruling 6). */
class WalkerStartFactsReplaySuite extends munit.FunSuite:
  private val source = DecisionOptionRef.Site(CatalogNames.site("Buried Giant"))
  private val elsewhere = DecisionOptionRef.Site(CatalogNames.site("Dunes"))

  private def park(at: String, facts: Vector[DecisionOptionRef]): WalkerParked =
    WalkerParked(ActionRef.Travel, Vector(at), Vector.empty, Vector.empty,
      Vector.empty, facts)

  private def facts(state: OathState): Vector[DecisionOptionRef] = state match
    case OathState.Ready(ready) => ready.game.current.walkerStartFacts
    case other => fail(s"not ready: $other")

  test("a park restores its start facts and a completion clears them"):
    val parked = ProcedureWalker.applyRecorded(
      OathState.Ready(Table.start.ready), park("0", Vector(source)))
    assertEquals(parked.map(facts), Right(Vector(source)))
    val completed = parked.flatMap(ProcedureWalker.applyRecorded(_,
      WalkerCompleted(ActionRef.Travel)))
    assertEquals(completed.map(facts), Right(Vector.empty))

  test("a later park naming other start facts is refused"):
    val refused = ProcedureWalker.applyRecorded(
      OathState.Ready(Table.start.ready), park("0", Vector(source)))
      .flatMap(ProcedureWalker.applyRecorded(_, park("1", Vector(elsewhere))))
    assertEquals(refused, Left(OathViolation.InvalidEventOrder(
      "durable walker park start facts do not match the recorded start")))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.WalkerStartFactsReplaySuite oathdigital.serialization.WalkerDecisionWireSuite"`
Expected: compile failure, `WalkerParked` takes 5 arguments and `walkerStartFacts` is not a member.

- [ ] **Step 3: Add the state field and the event field**

In `GameState.scala`, after `walkerStartArgs` (line 174), add:

```scala
    // What the engine derived when the walker procedure started and cannot
    // read back once the walk changes it (travel destination design, ruling
    // 6): Travel's source site, after the pawn has moved. Engine-owned, where
    // `walkerStartArgs` is what the player selected. Restored by replay from
    // the `WalkerParked` fact and cleared by `WalkerCompleted`.
    walkerStartFacts: Vector[DecisionOptionRef] = Vector.empty,
```

Also change the `walkerStartArgs` comment's "Travel's destination is the only one today" to "Search's source is one".

In `WalkerEvents.scala`, add the parameter to `WalkerParked` and one sentence to its doc:

```scala
  * `startFacts` is what the engine derived at the start, carried for the same
  * reason: a fact the walk itself changes (Travel's source site) cannot be
  * read back from state on a resume.
  */
final case class WalkerParked(
    procedure: ProcedureRef,
    at: Vector[String],
    answered: Vector[Answered],
    modifiers: Vector[PowerId],
    startArgs: Vector[DecisionOptionRef],
    startFacts: Vector[DecisionOptionRef] = Vector.empty
) extends WalkerEvent
```

- [ ] **Step 4: Encode, decode and replay the facts**

In `WalkerEventCodec.scala`, the encoder arm becomes:

```scala
    case WalkerParked(procedure, at, answered, modifiers, startArgs,
        startFacts) =>
      ujson.Obj(
        "procedure" -> encodeProcedure(procedure),
        "at" -> stringArray(at),
        "answered" -> ujson.Arr.from(answered.map(encodeAnswered)),
        "modifiers" -> stringArray(modifiers.map(_.value)),
        // Always written, empty for an action that selects nothing, exactly
        // as `modifiers` is. A park written before batch-1 Task 5 has no such
        // key at all, and `decodeParked` reads a missing key as empty.
        "startArgs" -> ujson.Arr.from(startArgs.map(
          DecisionAnswerCodec.encodeRef)),
        // Read the same way: a park written before start facts has none.
        "startFacts" -> ujson.Arr.from(startFacts.map(
          DecisionAnswerCodec.encodeRef)))
```

and the decoder reads both keys through one helper:

```scala
    startArgs <- decodeRefs(value, "startArgs", s"$path.startArgs")
    startFacts <- decodeRefs(value, "startFacts", s"$path.startFacts")
  yield WalkerParked(procedure,
    value("at").arr.toVector.map(_.str), answered, modifiers, startArgs,
    startFacts)
```

Rename `decodeStartArgs(value, path)` to `decodeRefs(value: ujson.Value, key: String, path: String)` and read `value.obj.get(key)`. Keep its doc, saying it serves start selections and start facts.

In `WalkerReplay.scala`, bind `startFacts` in the `WalkerParked` pattern. Inside the `Some(existing)` branch, after the start-args check, add:

```scala
                _ <- Either.cond(
                  ready.game.current.walkerStartFacts == startFacts, (),
                  OathViolation.InvalidEventOrder(
                    "durable walker park start facts do not match the " +
                      "recorded start"))
```

Then add `walkerStartFacts = startFacts` to the restored copy, and `walkerStartFacts = Vector.empty` to the `WalkerCompleted` clear.

- [ ] **Step 5: Declare and thread start facts**

In `WalkerProcedureRegistry.Entry`, after `requiresPlayableOption`, add:

```scala
      /** What the engine derives at a fresh start and stores as
        * `walkerStartFacts` (travel destination design, ruling 6). A
        * procedure's `rebuild` reads them from the state it is handed. None
        * by default. */
      startFacts: (ExecutableCatalog, ReadyGame, PlayerId) =>
        Vector[DecisionOptionRef] = (_, _, _) => Vector.empty,
```

and beside `requiresPlayableOption(procedure)` add:

```scala
  /** The start facts `procedure` derives at a fresh start, empty for one that
    * declares none. */
  def startFacts(procedure: ProcedureRef, catalog: ExecutableCatalog,
      state: ReadyGame, actor: PlayerId): Vector[DecisionOptionRef] =
    lookup(procedure, entries).toOption.fold(Vector.empty)(
      _.startFacts(catalog, state, actor))
```

In `OathRulesWalker.scala`:
- `walkerTransition` gains a last parameter `startFacts: Vector[DecisionOptionRef]`, and its park becomes `WalkerParked(procedure, pending.at, pending.answered, modifiers, startArgs, startFacts)`.
- In `startWalker`'s `run`, pass `WalkerProcedureRegistry.startFacts(procedure, catalog, ready, activePlayer)` as the new argument.
- In `startTriggered`, pass `Vector.empty`.
- In `resolveWalker`, `rollWalkerPrepared` and the third `resumeWalker` caller, pass `ready.game.current.walkerStartFacts`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.WalkerStartFactsReplaySuite oathdigital.serialization.WalkerDecisionWireSuite oathdigital.gameplay.WalkerReplayDriftSuite"`
Expected: PASS.

Then run `./sbtw test`. Expected: PASS. Any other 5-field `WalkerParked` pattern the compiler names gains a sixth binding `_`.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/model/GameState.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala \
  src/main/scala/oathdigital/serialization/WalkerEventCodec.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala \
  src/main/scala/oathdigital/gameplay/OathRulesWalker.scala \
  src/test/scala/oathdigital/serialization/WalkerDecisionWireSuite.scala \
  src/test/scala/oathdigital/gameplay/walker/WalkerStartFactsReplaySuite.scala
git commit -m "feat(walker): keep engine-derived start facts across a resume"
```

---

### Task 2: Travel parks on its destination

The engine change breaks every test that starts a Travel with a site, so this task updates all of them. It ends with the whole server suite green. The frontend still sends the old start until Task 6; that is expected inside the branch.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/travel/TravelProcedure.scala` (rewrite)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala:179-191`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala:24-28` (doc only)
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala:188-189`
- Create: `src/test/scala/oathdigital/gameplay/TravelPlay.scala`
- Create: `src/test/scala/oathdigital/application/TravelDestinationServiceSuite.scala`
- Modify: `src/test/scala/oathdigital/application/ParkedServiceFixture.scala` (add `startTravel`, `pickTravelDestination`)
- Modify: `src/test/scala/oathdigital/gameplay/powers/travel/TravelFixture.scala:73-81`
- Modify: `src/test/scala/oathdigital/gameplay/TravelProcedureSuite.scala`
- Modify: every other test that starts a Travel with a site selection (Step 6 lists them)

**Interfaces:**
- Consumes (Task 1): `Entry.startFacts`, `CurrentGameState.walkerStartFacts`.
- Produces:
  - `TravelProcedure.destinationDecisionId: String = "travel.destination"`.
  - `TravelProcedure.build` and `TravelProcedure.rebuild`: `(ExecutableCatalog, ReadyGame, PlayerId, Vector[DecisionOptionRef]) => Either[OathViolation, Operation]`.
  - `TravelProcedure.startFacts(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId): Vector[DecisionOptionRef]`.
  - `TravelProcedure.candidates` keeps its signature for now (Task 3 deletes it), reading the preview.
  - Test helper `oathdigital.gameplay.TravelPlay.travelled(rules: OathRules, state: OathState, actor: PlayerId, destination: SiteId, modifiers: Vector[PowerId] = Vector.empty): Either[OathViolation, OathTransition]` and `TravelPlay.destination(site: SiteId): DecisionAnswer`.

- [ ] **Step 1: Write the test helper and the failing engine tests**

Create `src/test/scala/oathdigital/gameplay/TravelPlay.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.model._

/** A Travel driven past its destination pick, for the suites that test what
  * a Travel does rather than the pick. */
object TravelPlay:
  /** The answer that picks `site` as the destination. */
  def destination(site: SiteId): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(site))

  /** `actor` starts a Travel with `modifiers` and picks `destination`. The
    * events are both commands'. */
  def travelled(rules: OathRules, state: OathState, actor: PlayerId,
      destination: SiteId, modifiers: Vector[PowerId] = Vector.empty)
      : Either[OathViolation, OathTransition] = for
    started <- rules.startWalker(state, ActionRef.Travel, actor, modifiers)
    picked <- rules.resolveWalker(started.state, actor,
      TravelProcedure.destinationDecisionId, this.destination(destination))
  yield picked.copy(events = started.events ++ picked.events)
```

In `ParkedServiceFixture`, add (with `TravelProcedure` and `TravelPlay` imported):

```scala
  /** `actor` starts a Travel; the walk parks on the destination. */
  def startTravel(actor: PlayerId): GameCommand =
    GameCommand.StartWalker(ActionRef.Travel,
      StartPayload(actor, Vector.empty, Vector.empty))

  /** `actor` answers the destination decision with `site`. */
  def pickTravelDestination(actor: PlayerId, site: SiteId): GameCommand =
    GameCommand.ResolveWalker(actor, TreeDecision(
      TravelProcedure.destinationDecisionId, TravelPlay.destination(site)))
```

Create `src/test/scala/oathdigital/application/TravelDestinationServiceSuite.scala`:

```scala
package oathdigital.application

import oathdigital.model._
import oathdigital.testkit.{Outcomes, Table}
import oathdigital.testkit.Table.p1

/** The destination decision through the service: Undo in place of the old
  * Cancel (travel destination design, ruling 8). */
class TravelDestinationServiceSuite extends munit.FunSuite:
  private val table = Table.start.banditsRefilled
  private def accept(result: Either[GameApplicationError, GameAccepted])
      : GameAccepted = result.fold(error => fail(s"rejected: $error"), identity)

  /** A site in p1's own region, which any Supply at the start reaches. */
  private def nearby(state: OathState): SiteId =
    val current = Outcomes.ready(state).game.current
    val here = current.players.find(_.player == p1).flatMap(_.pawnSite).get
    current.map.inPlay.find(site => site != here &&
      current.map.regionOf(site) == current.map.regionOf(here)).get

  test("Undo takes back the start, leaving the table as it was"):
    val (service, _) = table.service()
    val started = accept(service.handle("g", 0L,
      ParkedServiceFixture.startTravel(p1)))
    val undone = accept(service.handle("g", started.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, table.state)

  test("Undo after the pick returns to the destination decision"):
    val (service, _) = table.service()
    val started = accept(service.handle("g", 0L,
      ParkedServiceFixture.startTravel(p1)))
    val picked = accept(service.handle("g", started.nextSequence,
      ParkedServiceFixture.pickTravelDestination(p1, nearby(started.state))))
    val undone = accept(service.handle("g", picked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, started.state)
    assert(undone.undo.nonEmpty, "the start can still be taken back")
```

In `TravelProcedureSuite`, replace the `simulate` helper with:

```scala
  /** What a Travel to `destination` would spend, from the same preview the
    * parked decision reads; `None` when the preview refuses it. */
  private def simulate(ready: ReadyGame, destination: SiteId,
      using: WalkerPowers = powers): Option[Int] =
    TravelProcedure.candidates(catalog, ready, actor, using)
      .collectFirst { case (site, cost) if site == destination => cost }

  private def travelled(ready: ReadyGame, destination: SiteId)
      : Either[OathViolation, OathTransition] =
    TravelPlay.travelled(new OathRules(catalog, walkerPowerCatalog = powers),
      Ready(ready), actor, destination)
```

Replace tests (f), "the declared tree…" and "build rejects…" with:

```scala
  test("a Travel parks on its destination and completes on the answer"):
    val ready = passRuled(travelBoard(supply = 7))
    val rules = new OathRules(catalog, walkerPowerCatalog = powers)
    val started = accepted(rules.startWalker(Ready(ready), ActionRef.Travel,
      actor, Vector.empty))
    val parked = Outcomes.ready(started.state)
    assertEquals(parked.game.current.walkerProcedure, Some(ActionRef.Travel))
    assertEquals(parked.game.current.walkerStartFacts,
      Vector(DecisionOptionRef.Site(cradlePlains)))
    assertEquals(Look(parked).supply(actor), 7)
    val done = accepted(rules.resolveWalker(started.state, actor,
      TravelProcedure.destinationDecisionId, TravelPlay.destination(coast)))
    val after = Outcomes.ready(done.state)
    assertEquals(Look(after).pawn(actor), coast)
    assertEquals(after.game.current.walkerProcedure, None)
    assertEquals(after.game.current.walkerStartFacts, Vector.empty)

  test("the destination offers every in-play site but the pawn's"):
    val ready = travelBoard(supply = 7)
    val tree = accepted(TravelProcedure.build(catalog, ready, actor,
      Vector.empty))
    val offered = tree.children.head match
      case Decide(TravelProcedure.destinationDecisionId, `actor`,
          DecisionQuery.ChooseOne(options, Some("Choose a Travel destination")),
          _, _, _, _) => options.map(_.ref)
      case other => fail(s"not the destination decision: $other")
    assertEquals(offered, ready.game.current.map.inPlay
      .filter(_ != cradlePlains).map(DecisionOptionRef.Site(_)))

  test("a resume reads the source from the start fact, not the pawn"):
    val ready = travelBoard(supply = 7)
    val moved = ready.updateCurrent(current => current.copy(
      walkerStartFacts = Vector(DecisionOptionRef.Site(cradlePlains)),
      players = current.players.map(player =>
        if player.player == actor then player.copy(pawnSite = Some(coast))
        else player)))
    val rebuilt = accepted(TravelProcedure.rebuild(catalog, moved, actor,
      Vector.empty))
    val pending = PendingTree(Vector("1"), Vector(Answered(
      TravelProcedure.destinationDecisionId, TravelPlay.destination(coast),
      actor)))
    val selected = rebuilt.children(1) match
      case branch: Branch => branch.select(moved, pending)
      case other => fail(s"not the route branch: $other")
    assertEquals(selected.flatMap(Operation.flatten).collect {
      case Move(Piece.Pawn(_), PositionedLocation(Location.Site(from), _),
          _, _) => from }, Vector(cradlePlains))

  test("a start that names a selection is refused, and so is a resume " +
      "without its start fact"):
    val ready = travelBoard(supply = 7)
    assertEquals(rejected(TravelProcedure.build(catalog, ready, actor,
      Vector(DecisionOptionRef.Site(coast)))),
      InvalidEventOrder("travel takes no start selection"))
    assertEquals(rejected(TravelProcedure.rebuild(catalog, ready, actor,
      Vector.empty)), InvalidEventOrder(
      "a resumed Travel needs its source site as its one start fact"))

  test("a start with no reachable destination is refused"):
    val broke = passRuled(travelBoard(supply = 0))
    val rules = new OathRules(catalog, walkerPowerCatalog = powers)
    assertEquals(rejected(rules.startWalker(Ready(broke), ActionRef.Travel,
      actor, Vector.empty)), NoPlayableOption(ActionRef.Travel.key))
```

The `Decide` pattern lists seven fields (`decisionId, owner, query, window, coOwners, passWhenEmpty, presentation`); match the case class as it is.

In the remaining tests, turn each `rules.startWalker(Ready(ready), ActionRef.Travel, actor, Vector.empty, Vector(DecisionOptionRef.Site(destination)))` into `travelled(ready, destination)`. In "zero Supply passes the semantic build…", the build call becomes `TravelProcedure.build(catalog, ready, actor, Vector.empty)`. The `CoreOperationRejected("insufficient-supply", …)` assertion becomes `assertEquals(rejected(travelled(ready, destination)), NoPlayableOption(ActionRef.Travel.key))`, because the start itself is now refused. In "role and Foundation changes…", the build call passes `Vector.empty`.

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.TravelProcedureSuite oathdigital.application.TravelDestinationServiceSuite"`
Expected: compile failure, `destinationDecisionId` and `rebuild` are not members of `TravelProcedure`.

- [ ] **Step 3: Rewrite `TravelProcedure`**

Replace the object (keep the file's package) with:

```scala
package oathdigital.gameplay.actions.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.TravelRules
import oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}
import oathdigital.gameplay.walker.WalkerSimulation.PreviewedOption
import oathdigital.gameplay.OathLifecycle
import oathdigital.model._

/** Travel on the walker (travel destination design).
  *
  * {{{
  * Sequence(
  *   Decide(travel.destination),              // a board choose-one over sites
  *   Branch { after the answer:
  *     Sequence(                              // window = TravelActionEligibility
  *       Sequence(                            // window = TravelCost
  *         SpendSupply(actor, printedBase),
  *         Move(Pawn, source -> destination))) })
  * }}}
  *
  * **The destination is a decision.** A power that cares which site was
  * picked acts inside the walk and may ask its own question (Way Station).
  * The start carries no selection.
  *
  * **The windows sit inside the `Branch`.** Every Travel power finds the
  * route by flattening its windowed node to the `Move`, and a `Branch`
  * flattens to nothing, so a window above it would see no route. A selected
  * modifier's payment, prepended at the eligibility window, is therefore paid
  * after the pick, and nothing runs before the first decision.
  *
  * **The source is a start fact.** A resume after the pawn moved could not
  * read it from the pawn, so a fresh build reads the pawn and the start
  * stores it; a resume reads it back from `walkerStartFacts`.
  *
  * **What does not gate a start.** Supply, terrain, role and Foundation faces
  * are not build gates: the registry's playable-option gate previews every
  * destination and refuses a start none of them can complete.
  */
object TravelProcedure:
  val destinationDecisionId: String = "travel.destination"
  private val heading = "Choose a Travel destination"

  def build(catalog: ExecutableCatalog, state: ReadyGame,
      activePlayer: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for
    _ <- OathLifecycle.validateAct(OathState.Ready(state), activePlayer)
    _ <- noSelection(args)
    source <- actorSite(state, activePlayer).toRight(
      OathViolation.PawnSiteMissing(activePlayer))
  yield tree(catalog, state, activePlayer, source)

  /** Resume rebuilds what the start built, from the source the start
    * stored. Once the pawn has moved the destination decision lists other
    * sites, but the walk is past it by then. */
  def rebuild(catalog: ExecutableCatalog, state: ReadyGame,
      activePlayer: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for
    _ <- noSelection(args)
    source <- sourceOf(state.game.current.walkerStartFacts)
  yield tree(catalog, state, activePlayer, source)

  /** The source site, the one fact a resumed Travel cannot read back. */
  def startFacts(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Vector[DecisionOptionRef] =
    actorSite(state, actor).map(DecisionOptionRef.Site(_)).toVector

  def actorSite(state: ReadyGame, actor: PlayerId): Option[SiteId] =
    state.game.current.players.find(_.player == actor).flatMap(_.pawnSite)

  private def noSelection(args: Vector[DecisionOptionRef])
      : Either[OathViolation, Unit] = Either.cond(args.isEmpty, (),
    OathViolation.InvalidEventOrder("travel takes no start selection"))

  private def sourceOf(facts: Vector[DecisionOptionRef])
      : Either[OathViolation, SiteId] = facts match
    case Vector(DecisionOptionRef.Site(source)) => Right(source)
    case _ => Left(OathViolation.InvalidEventOrder(
      "a resumed Travel needs its source site as its one start fact"))

  private def tree(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, source: SiteId): Operation =
    val pick = Decide(destinationDecisionId, actor, DecisionQuery.ChooseOne(
      state.game.current.map.inPlay.filter(_ != source).map(site =>
        DecisionOption.Site(DecisionOptionRef.Site(site))),
      heading = Some(heading)))
    val route = Branch((current, pending) =>
      pending.answered.collectFirst {
        case Answered(`destinationDecisionId`,
            DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(site)), _) =>
          site
      }.toVector.map(destination =>
        TravelRules.cost(catalog, current, source, destination).fold(
          error => BuildOps((_, _) => Left(error)),
          base => routeTree(actor, source, destination, base))))
    Sequence(Vector(pick, route))

  /** Closes only over command-stable actor, route, and printed base. */
  private def routeTree(actor: PlayerId, source: SiteId, destination: SiteId,
      base: Int): Operation =
    Sequence(Vector(
      Sequence(Vector(
        SpendSupply(actor, base),
        Move(Piece.Pawn(actor),
          PositionedLocation(Location.Site(source)),
          PositionedLocation(Location.Site(destination)))),
        Some(PowerWindow.TravelCost))),
      Some(PowerWindow.TravelActionEligibility))

  /** Every destination the preview accepts, with the Supply it spends.
    * Retires with the board targets (Task 3). */
  def candidates(catalog: ExecutableCatalog, state: ReadyGame,
      activePlayer: PlayerId, powers: WalkerPowers): Vector[(SiteId, Int)] =
    build(catalog, state, activePlayer, Vector.empty)
      .flatMap(WalkerSimulation.preview(_, state, powers))
      .getOrElse(Vector.empty).collect {
        case PreviewedOption(DecisionOption.Site(DecisionOptionRef.Site(site)),
            Right(outcome)) => site -> outcome.operations.collect {
          case SpendSupply(player, amount, _) if player == activePlayer =>
            amount }.sum
      }
```

If the compiler reports an unused import, remove it.

- [ ] **Step 4: Register the new entry and narrate the pick**

Replace the Travel entry in `WalkerProcedureRegistry.entries` and its doc:

```scala
    /** Travel parks on its destination first (travel destination design).
      * Nothing runs before that decision, so the start is previewed through
      * the playable-option gate. Its source is a start fact: a resume after
      * the pawn moved reads it back rather than from the pawn.
      */
    ActionRef.Travel -> Entry(
      fallbackKind = Some(ActionKind.Travel),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.TravelModifierSelection),
      build = TravelProcedure.build,
      rebuild = TravelProcedure.rebuild,
      requiresPlayableOption = true,
      startFacts = TravelProcedure.startFacts),
```

Also update the `Entry` doc paragraph that says "Travel cannot name a route without one": Search's source is now the start selection that example names.

In `WalkerSimulation.scala`'s doc, replace "Every registered action whose tree can park (Recover, Forge) therefore has nothing to ask this." with "An action whose tree parks asks `preview` instead."

In `DetailLines.scala`, add `"travel."` to `NarratedPrefixes`.

- [ ] **Step 5: Point the Travel power fixture at the walk**

In `TravelFixture.scala`, replace `candidates` and `travel` with:

```scala
  /** The destinations Travel offers with `modifiers` selected, and the
    * Supply each spends, from the preview the parked decision reads. */
  def candidates(ready: ReadyGame, modifiers: Vector[PowerId] = Vector.empty)
      : Map[SiteId, Int] = TravelProcedure.candidates(catalog, ready, actor,
    powers(modifiers)).toMap

  /** `actor` starts a Travel with `modifiers` and picks `destination`. */
  def travel(ready: ReadyGame, destination: SiteId,
      modifiers: Vector[PowerId] = Vector.empty)
      : Either[OathViolation, OathTransition] =
    TravelPlay.travelled(rules, Ready(ready), actor, destination, modifiers)
```

with `import oathdigital.gameplay.TravelPlay`. Task 3 deletes `TravelProcedure.candidates` and gives this helper its own preview.

- [ ] **Step 6: Run the server suite and move the other Travel starts**

Run: `./sbtw test`
Expected: failures in suites that start a Travel with a site selection, plus game-log fixtures.

Fix each with one rule: a Travel start no longer names a site; the site is the answer to `travel.destination`.
- **Engine suites** (`rules.startWalker(…, ActionRef.Travel, …, Vector(DecisionOptionRef.Site(site)))`) call `TravelPlay.travelled(rules, state, actor, site, modifiers)` instead.
- **Service suites** (`GameCommand.StartWalker(ActionRef.Travel, StartPayload(actor, modifiers, Vector(Site(site))))`) send `StartPayload(actor, modifiers, Vector.empty)` and then `GameCommand.ResolveWalker(actor, TreeDecision(TravelProcedure.destinationDecisionId, TravelPlay.destination(site)))` at the next sequence. Where a fixture builds the start command (`ParkedServiceFixture`, `LogScripts`), add a sibling builder named for what it sends, as slice 4 added `pickFacedownAdviser`: `pickTravelDestination(actor: PlayerId, site: SiteId): GameCommand`.
- **Wire suites** (`server/GameHttpWireSuite`, `server/AuthenticatedGameRoutesSuite`, `shared/…/CommandProtocolSuite`) that only round-trip a `StartWalker` intent with start args keep their wire shape. Change only those that expect the command to be accepted.
- **Undo, Redo and Game Log suites** that take back or log a Travel now see two command batches. Undo takes back the destination answer first; a test that expected one Undo to restore the pre-Travel table needs a second Undo, or asserts the parked destination decision after the first. Regenerate a changed golden log only when the only difference is the extra parked step and its narrated (absent) "Chose" line. Inspect each diff before accepting it.

Files the grep found (spot-check; some only use the `"travel"` key and need no change): `application/{GameApplicationServiceActionsSuite, GameApplicationServiceProjectionSuite, GameApplicationServiceReopenSuite, JournalFoldSuite, JournalFoldRedoSuite, ParkedServiceFixture, RedoServiceSuite, UndoProjectionSuite, UndoServiceSuite}`, `application/gamelog/{GameLogPropertiesSuite, GameLogRareLinesSuite, GameLogRestoredSuite, GameLogRouteSuite, GameLogSectionSuite, GameLogTakenBackSuite, LogScripts}`, `gameplay/{CatacombsContributionSuite, NegotiationProcedureSuite, StateBasedEvaluationSuite}`, `gameplay/oathkeeper/OathkeeperProcedureSuite`, `gameplay/powers/cardplay/WildCrySuite`, `gameplay/powers/search/{AugurySuite, NewsFromAfarSuite}`, `gameplay/powers/title/ChaosCultSuite`, `gameplay/walker/WalkerProcedureRegistrySuite`, `serialization/WalkerDecisionWireSuite`, `server/{AuthenticatedGameRoutesSuite, GameHttpWireSuite, GameTrustBoundaryRoutesSuite, TrustedSeatRoutesSuite}`, `testkit/TableSuite`, all under `src/test/scala/oathdigital/`.

Run `./sbtw test` again. Expected: PASS.

- [ ] **Step 7: Commit**

Stage the main files above, `TravelPlay.scala`, `TravelFixture.scala`, `TravelProcedureSuite.scala`, and every test file and golden log Step 6 changed, by explicit path.

```bash
git commit -m "feat(travel): ask the destination inside the walker"
```

---

### Task 3: Badged destinations and the Travel control

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala` (`PreviewOutcome`, `previewParked`, `answer`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` (`OptionPricing`, `Entry.optionPricing`, accessor, Travel entry)
- Create: `src/main/scala/oathdigital/gameplay/actions/travel/TravelOptions.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/travel/TravelProcedure.scala` (add `startable`, delete `candidates`)
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` (`playable`)
- Modify: `src/main/scala/oathdigital/application/LegalActionProjector.scala`
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala:189-263` (drop `walkerTargets`)
- Modify: `src/test/scala/oathdigital/gameplay/powers/travel/TravelFixture.scala` (`candidates`)
- Modify: `src/test/scala/oathdigital/gameplay/TravelProcedureSuite.scala` (`simulate`)
- Create: `src/test/scala/oathdigital/application/TravelDestinationRowsSuite.scala`

**Interfaces:**
- Consumes (Task 2): `TravelProcedure.build`, `destinationDecisionId`.
- Produces:
  - `WalkerSimulation.PreviewOutcome(operations, complete, next: Vector[PreviewedOption] = Vector.empty)`.
  - `WalkerSimulation.previewParked(state, tree, pending, powers, followUp: Boolean = false)`.
  - `WalkerProcedureRegistry.OptionPricing = (ReadyGame, Operation, PendingTree, WalkerPowers, Decide) => Vector[DecisionOption]`, `Entry.optionPricing: Option[OptionPricing] = None`, `WalkerProcedureRegistry.optionPricing(procedure: ProcedureRef): Option[OptionPricing]`.
  - `TravelOptions.priced: OptionPricing`, `TravelOptions.badge(outcome: PreviewOutcome, actor: PlayerId): String`.
  - `TravelProcedure.startable(catalog, state, actor, powers): Boolean`.
  - The legal control `beginTravel`. `LegalProjection`'s board targets are always empty from here on; Task 6 deletes the field.

- [ ] **Step 1: Write the failing projection tests**

Create `src/test/scala/oathdigital/application/TravelDestinationRowsSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.TravelPlay
import oathdigital.gameplay.powers.travel.{Tents, TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.DecisionOptionProjection
import oathdigital.testkit.{CatalogNames, Outcomes, Table}
import oathdigital.testkit.Unwrap.{accepted, present}

/** What the parked destination decision shows each site as: its cost under
  * the selected modifiers, or why it cannot be reached (travel destination
  * design, rulings 3 and 4). */
class TravelDestinationRowsSuite extends munit.FunSuite:
  import TravelFixture._
  import oathdigital.gameplay.powers.PowerFixture.actor

  private val projector = new WalkerDecisionProjector(catalog,
    new GamePresentationProjector(catalog))

  /** The destination rows `actor` is shown once a Travel with `modifiers`
    * starts at `ready`. */
  private def rows(ready: ReadyGame, modifiers: Vector[PowerId] = Vector.empty)
      (using munit.Location): Map[String, DecisionOptionProjection] =
    val started = accepted(rules.startWalker(Ready(ready), ActionRef.Travel,
      actor, modifiers))
    present(projector.project(ScopedProjectionContext(
      Outcomes.ready(started.state), Some(actor))), "the destination decision")
      .query.toVector.flatMap(_.offeredOptions).map(row => row.id -> row).toMap

  private def row(ready: ReadyGame, site: SiteId,
      modifiers: Vector[PowerId] = Vector.empty)(using munit.Location)
      : DecisionOptionProjection =
    present(rows(ready, modifiers).get(site.value), site.value)

  /** The pass ruled by the actor, so no route here is blocked by it. */
  private def open(supply: Int): ReadyGame =
    Table.from(board(supply = supply)).warbandsAt(pass, actor, 1).unchecked

  test("a destination is badged with the Supply its Travel spends"):
    val ready = open(supply = 7)
    assertEquals(row(ready, coast).badge, Some("1 Supply"))
    assertEquals(row(ready, provincePlains).badge, Some("2 Supply"))
    assertEquals(row(ready, island).badge, Some("6 Supply"))

  test("a toll adds its favor to the badge"):
    val ready = Table.from(open(supply = 7))
      .denizen(CatalogNames.denizen("Toll Roads"), at = provincePlains)
      .favor(actor, 1).warbandsAt(provincePlains, Table.p2, 1)
      .warbandsAt(coast, Table.p2, 1).unchecked
    assertEquals(row(ready, coast).badge, Some("1 Supply · 1 favor"))

  test("a selected modifier that makes the Travel free reads 0 Supply, and " +
      "its own card payment is not in the badge"):
    val ready = Table.from(open(supply = 7))
      .adviser(actor, CatalogNames.denizen("Tents")).favor(actor, 1).unchecked
    assertEquals(row(ready, coast, Vector(Tents.id)).badge, Some("0 Supply"))

  test("a destination the actor cannot afford says how much it needs"):
    val shown = row(open(supply = 1), provincePlains)
    assertEquals(shown.state, "unavailable")
    assertEquals(shown.reason, Some("Needs 2 Supply; you have 1"))
    assertEquals(row(open(supply = 1), coast).state, "available")

  test("a toll the actor cannot pay says how much favor it needs"):
    val ready = Table.from(open(supply = 7))
      .denizen(CatalogNames.denizen("Toll Roads"), at = provincePlains)
      .favor(actor, 0).warbandsAt(provincePlains, Table.p2, 1)
      .warbandsAt(coast, Table.p2, 1).unchecked
    val shown = row(ready, coast)
    assertEquals(shown.state, "unavailable")
    assertEquals(shown.reason, Some("Needs 1 favor; you have 0"))
```

Before relying on them, check that the badge numbers match the fixture's printed table (`TravelFixture`'s doc). The island route from the Cradle to the Hinterland costs 4, plus the island's 2. Check also that `PowerFixture.actor` is the travelling player. Correct a number only from what the walk spends (`TravelProcedureSuite`'s terrain tests show each).

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.TravelDestinationRowsSuite"`
Expected: FAIL. The rows have no badge, and the unaffordable site is missing, because the playable filter drops it.

- [ ] **Step 3: Follow-up previews**

In `WalkerSimulation.scala`:

```scala
  /** What answering one option would record. `complete` is true when the tree
    * then finished and false when it parked at a further decision. `next`
    * previews that further decision's options, when it was asked for and the
    * decision is a choose-one.
    */
  final case class PreviewOutcome(operations: Vector[CoreOperation],
      complete: Boolean, next: Vector[PreviewedOption] = Vector.empty)
```

`previewParked` gains `followUp: Boolean = false` and passes it to `answer`. `answer` becomes:

```scala
  private def answer(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, decide: Decide, option: DecisionOption,
      followUp: Boolean): Either[OathViolation, PreviewOutcome] = guarded:
    ProcedureWalker.resolve(state, tree, pending,
      Answered(decide.decisionId, DecisionAnswer.ChooseOneAnswer(option.ref),
        decide.owner), powers, WalkerDice.placeholder).map:
      case WalkerOutcome.Finished(_, events) =>
        PreviewOutcome(recordedOperations(events), complete = true)
      case WalkerOutcome.Parked(next, events) =>
        PreviewOutcome(recordedOperations(events), complete = false,
          next = if !followUp then Vector.empty
            else after(state, pending, next, events)
              .flatMap(previewParked(_, tree, next, powers))
              .getOrElse(Vector.empty))

  /** The parked position an answer leads to: its recorded steps applied to
    * `state` as replay applies them, parked at `next`. The steps start from
    * the position the answer was given at, which a start's preview has not
    * recorded yet. */
  private def after(state: ReadyGame, pending: PendingTree, next: PendingTree,
      events: Vector[OathEvent]): Either[OathViolation, ReadyGame] =
    events.collect { case step: WalkerStepRecorded => step }
      .foldLeft[Either[OathViolation, OathState]](Right(OathState.Ready(
        state.updateCurrent(_.copy(walkerPending = Some(pending)))))) {
        (folded, step) => folded.flatMap(ProcedureWalker.applyRecorded(_, step))
      }.flatMap:
        case OathState.Ready(ready) =>
          Right(ready.updateCurrent(_.copy(walkerPending = Some(next))))
        case _ => Left(OathViolation.GameNotStarted)
```

The follow-up is one level deep: the nested `previewParked` call does not follow up. Add `OathState` to the model import.

- [ ] **Step 4: Option pricing in the registry and the projector**

In `WalkerProcedureRegistry`, above `Entry`:

```scala
  /** The options a parked decision shows, from its position: see
    * `Entry.optionPricing`. */
  type OptionPricing = (ReadyGame, Operation, PendingTree, WalkerPowers,
    Decide) => Vector[DecisionOption]
```

Add `optionPricing: Option[OptionPricing] = None` as `Entry`'s last field, documented: "Replaces the playable-option filter in the projector: an entry that prices its options shows every option, each priced or explained. Travel's destinations." Add the accessor:

```scala
  def optionPricing(procedure: ProcedureRef): Option[OptionPricing] =
    lookup(procedure, entries).toOption.flatMap(_.optionPricing)
```

and `optionPricing = Some(TravelOptions.priced)` to the Travel entry, importing `TravelOptions`.

In `WalkerDecisionProjector.playable`, check pricing first:

```scala
    WalkerProcedureRegistry.optionPricing(procedure) match
      case Some(price) => (decide.query match
        case one: DecisionQuery.ChooseOne =>
          one.copy(options = price(ready, tree, pending, powers, decide))
        case other => other, Map.empty)
      case None =>
        if !WalkerProcedureRegistry.requiresPlayableOption(procedure) then
          (decide.query, Map.empty)
        else … // the existing body, unchanged
```

Update `playable`'s doc with one sentence: "A procedure that prices its options shows them all, as its pricing words them."

- [ ] **Step 5: Travel's pricing**

Create `src/main/scala/oathdigital/gameplay/actions/travel/TravelOptions.scala`:

```scala
package oathdigital.gameplay.actions.travel

import oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}
import oathdigital.gameplay.walker.WalkerSimulation.{PreviewOutcome,
  PreviewedOption}
import oathdigital.model._

/** The destination decision as its player is shown it (travel destination
  * design, rulings 3 and 4): each destination the preview accepts badged with
  * what its Travel costs, each it refuses unavailable with why. A destination
  * the look-ahead already hid keeps its reason.
  */
object TravelOptions:
  private val Fallback = "Cannot travel there now"
  /** Enough favor for any toll a Travel can owe, to learn what a refused one
    * needs. */
  private val AmpleFavor = 10

  def priced(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, decide: Decide): Vector[DecisionOption] =
    val actor = decide.owner
    val previewed = previews(state, tree, pending, powers, followUp = true)
    decide.query match
      case DecisionQuery.ChooseOne(options, _) => options.map(option =>
        previewed.get(option.ref) match
          case Some(Right(outcome)) =>
            DecisionOption.Badged(option, badge(outcome, actor))
          case Some(Left(_)) => DecisionOption.Unavailable(option,
            OptionReason.Written(shortfall(state, tree, pending, powers,
              actor, option.ref)))
          case None => option)
      case _ => Vector.empty

  /** "2 Supply", "2 Supply · 1 favor", "1 favor", or each answer of a
    * further question joined with " or ": "2 Supply or 1 favor". */
  def badge(outcome: PreviewOutcome, actor: PlayerId): String =
    val answers = outcome.next.collect { case PreviewedOption(_, Right(then)) =>
      outcome.operations ++ then.operations }
    if answers.isEmpty then words(outcome.operations, actor)
    else answers.map(words(_, actor)).distinct.mkString(" or ")

  private def words(operations: Vector[CoreOperation], actor: PlayerId)
      : String =
    val (supply, favor) = spent(operations, actor)
    Vector(Option.when(supply > 0 || favor == 0)(s"$supply Supply"),
      Option.when(favor > 0)(s"$favor favor")).flatten.mkString(" · ")

  /** The Supply and favor a Travel takes from `actor`. Favor counts when it
    * leaves their play area for anywhere but a card: a selected modifier's
    * payment onto its card is the same for every destination. A `Give` and a
    * `PayCost` are walked as their moves, so the moves are read. */
  private def spent(operations: Vector[CoreOperation], actor: PlayerId)
      : (Int, Int) =
    val supply = operations.collect {
      case SpendSupply(player, amount, _) if player == actor => amount }.sum
    val favor = operations.collect {
      case Move(Piece.Favor(amount),
          PositionedLocation(Location.PlayArea(player), _),
          PositionedLocation(to, _), _)
          if player == actor && !to.isInstanceOf[Location.OnCard] => amount
    }.sum
    (supply, favor)

  /** Why `ref` cannot be reached. The pipeline's refusal carries no amount,
    * so the amount is read from the same preview with the shortfall lifted:
    * Supply at the track's maximum, then favor as well. */
  private def shortfall(state: ReadyGame, tree: Operation,
      pending: PendingTree, powers: WalkerPowers, actor: PlayerId,
      ref: DecisionOptionRef): String =
    val board = state.game.current.players.find(_.player == actor).map(_.board)
    def lifted(favor: Boolean): Option[PreviewOutcome] =
      previews(lift(state, actor, favor), tree, pending, powers,
        followUp = false).get(ref).flatMap(_.toOption)
    (board, lifted(favor = false).orElse(lifted(favor = true))) match
      case (Some(board), Some(outcome)) =>
        val (supply, favor) = spent(outcome.operations, actor)
        if supply > board.supply.supply then
          s"Needs $supply Supply; you have ${board.supply.supply}"
        else if favor > board.favor then
          s"Needs $favor favor; you have ${board.favor}"
        else Fallback
      case _ => Fallback

  private def lift(state: ReadyGame, actor: PlayerId, favor: Boolean)
      : ReadyGame = state.updateCurrent(current => current.copy(
    players = current.players.map(player =>
      if player.player != actor then player
      else player.copy(board = player.board.copy(
        supply = SupplyTrack(SupplyTrack.Maximum),
        favor = if favor then player.board.favor + AmpleFavor
          else player.board.favor)))))

  private def previews(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, followUp: Boolean)
      : Map[DecisionOptionRef, Either[OathViolation, PreviewOutcome]] =
    WalkerSimulation.previewParked(state, tree, pending, powers, followUp)
      .fold(_ => Map.empty, _.map(previewed =>
        previewed.option.ref -> previewed.outcome).toMap)
```

`then` is a soft keyword in Scala 3; if the compiler rejects it as a binder, name it `after`.

In `TravelProcedure`, delete `candidates` and its now-unused imports, and add:

```scala
  /** Whether a Travel could start now: some destination survives the same
    * preview a start runs. */
  def startable(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Boolean =
    build(catalog, state, actor, Vector.empty)
      .flatMap(WalkerSimulation.preview(_, state, powers))
      .exists(_.exists(_.outcome.isRight))
```

- [ ] **Step 6: The Travel control, and the retired targets**

In `LegalActionProjector`:
- Replace `travelCandidates` with:

```scala
  /** Whether a Travel could start now, asked of the procedure that owns it. */
  private def travelStartable(context: ScopedProjectionContext): Boolean =
    TravelProcedure.startable(catalog, context.ready, context.active.player,
      WalkerPowers.selected(walkerPowerCatalog, Vector.empty))
```

- Add `Option.when(travelStartable(context))("beginTravel"),` after the `beginForge` line in `controls`.
- In `project`, pass `Vector.empty` for the board targets; delete `travelFacts`, `boardTargetActions` and `selection`. Delete the doc paragraph on `walkerPowerCatalog` that mentions Travel candidates and the preview re-costing them; keep one sentence saying the projection is costed against automatic powers alone.

In `GameApplicationService`, delete `walkerTargets` and its doc, and call `acceptPreview(loaded, options, selected, Vector.empty)`.

In `TravelFixture.candidates` and `TravelProcedureSuite.simulate`, read the preview directly, since `candidates` is gone:

```scala
  def candidates(ready: ReadyGame, modifiers: Vector[PowerId] = Vector.empty)
      : Map[SiteId, Int] =
    TravelProcedure.build(catalog, ready, actor, Vector.empty)
      .flatMap(WalkerSimulation.preview(_, ready, powers(modifiers)))
      .getOrElse(Vector.empty).collect {
        case PreviewedOption(DecisionOption.Site(DecisionOptionRef.Site(site)),
            Right(outcome)) => site -> outcome.operations.collect {
          case SpendSupply(player, amount, _) if player == actor => amount }.sum
      }.toMap
```

In `TravelProcedureSuite`, `simulate(ready, destination, using)` becomes the same expression with `using`, returning `.get(destination)`. Rewrite the test "simulation omits every destination the actor cannot afford" to read that map.

- [ ] **Step 7: Run the tests**

Run: `./sbtw "testOnly oathdigital.application.TravelDestinationRowsSuite oathdigital.gameplay.TravelProcedureSuite oathdigital.gameplay.powers.travel.*"`
Expected: PASS.

Run: `./sbtw test`
Expected: PASS. A projection test that read Travel's board targets now asserts the `beginTravel` control instead.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala \
  src/main/scala/oathdigital/gameplay/actions/travel/TravelOptions.scala \
  src/main/scala/oathdigital/gameplay/actions/travel/TravelProcedure.scala \
  src/main/scala/oathdigital/application/WalkerDecisionProjector.scala \
  src/main/scala/oathdigital/application/LegalActionProjector.scala \
  src/main/scala/oathdigital/application/GameApplicationService.scala \
  src/test/scala/oathdigital/application/TravelDestinationRowsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/TravelFixture.scala \
  src/test/scala/oathdigital/gameplay/TravelProcedureSuite.scala
git commit -m "feat(travel): badge each destination with its cost, or say why not"
```

Stage any other test the step changed by explicit path too.

---

### Task 4: Way Station

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/travel/WayStation.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala:72-78` (remove `WayStationCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala:61-64` (import `WayStationCard` from `travel`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TravelModifiers.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala:22-28`
- Create: `src/test/scala/oathdigital/gameplay/powers/travel/WayStationSuite.scala`
- Modify: `src/test/scala/oathdigital/application/TravelDestinationRowsSuite.scala`

**Interfaces:**
- Consumes: `TravelPlay`, `TravelFixture`, `TravelOptions.badge` (Task 3), `RulerToll` (`payment`, `note`, `keys`).
- Produces: `WayStation.id`, `WayStation.decisionId = "travel.way-station"`, `WayStation.supplyOption = DecisionOptionRef.Button("supply")`, `WayStation.favorOption = DecisionOptionRef.Button("favor")`.

- [ ] **Step 1: Write the failing tests**

Create `WayStationSuite.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class WayStationSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val wayStation = CatalogNames.denizen("Way Station")
  private val rival = Table.p2

  /** Way Station stands at the coast, which `rival` rules. The actor, at the
    * Cradle's plains, holds `favor` favor and `supply` Supply; a Travel to
    * the coast costs 1. */
  private def rivalRules(favor: Int = 1, supply: Int = 7): ReadyGame =
    Table.from(board(supply = supply)).denizen(wayStation, at = coast)
      .warbandsAt(coast, rival, 1).favor(actor, favor).unchecked

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(WayStation.id, WayStation.noteKeys, events)

  private val parked = new ParkedDecisionAssertions(catalog, powers(Vector.empty))

  private def assertAsked(state: OathState)(using munit.Location): Unit =
    parked.assertParked(state, ActionRef.Travel, WayStation.decisionId, actor)

  private def answer(state: OathState, choice: DecisionOptionRef.Button)
      : OathTransition = accepted(rules.resolveWalker(state, actor,
    WayStation.decisionId, DecisionAnswer.ChooseOneAnswer(choice)))

  test("its ruler travels there free, unasked"):
    val ready = Table.from(board()).denizen(wayStation, at = coast)
      .warbandsAt(coast, actor, 1).unchecked
    val done = accepted(travel(ready, coast))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).pawn(actor), coast)
    assertEquals(Look(result).supply(actor), 7)
    assertEquals(result.game.current.walkerProcedure, None)

  test("a non-ruler with favor is asked, and the favor goes to the ruler"):
    val ready = rivalRules()
    val asked = accepted(travel(ready, coast))
    assertAsked(asked.state)
    val done = answer(asked.state, WayStation.favorOption)
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).pawn(actor), coast)
    assertEquals(Look(result).supply(actor), 7)
    assertEquals(Look(result).favor(actor), 0)
    assertEquals(Look(result).favor(rival), Look(ready).favor(rival) + 1)
    assertEquals(said(done.events), Vector(NoteText.Said("gave",
      s"Gave 1 favor to ${rival.value}.", covers = false)))
    val all = asked.events ++ done.events
    assertEquals(ActPowerHarness.replayed(rules, ready, all), result)
    assertWireRoundTrips(all)

  test("spending the Supply instead posts no Way Station line"):
    val asked = accepted(travel(rivalRules(), coast))
    val done = answer(asked.state, WayStation.supplyOption)
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).supply(actor), 6)
    assertEquals(Look(result).favor(actor), 1)
    assertEquals(said(done.events), Vector.empty)

  test("a non-ruler with no favor is not asked"):
    val done = accepted(travel(rivalRules(favor = 0), coast))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).pawn(actor), coast)
    assertEquals(Look(result).supply(actor), 6)

  test("a Travel a selected modifier already made free is not asked"):
    val ready = Table.from(rivalRules(favor = 2))
      .adviser(actor, CatalogNames.denizen("Tents")).unchecked
    val done = accepted(travel(ready, coast, Vector(Tents.id)))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).pawn(actor), coast)
    assertEquals(Look(result).supply(actor), 7)

  test("under bandit rule the favor is burnt"):
    val ready = Table.from(board()).denizen(wayStation, at = coast)
      .bandits(coast, 1).favor(actor, 1).unchecked
    val done = answer(accepted(travel(ready, coast)).state,
      WayStation.favorOption)
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).favor(actor), 0)
    assertEquals(said(done.events), Vector(NoteText.Said("burned",
      "Burned 1 favor.", covers = false)))

  test("a player short of Supply can still go there by paying the favor"):
    val asked = accepted(travel(rivalRules(supply = 0), coast))
    assertAsked(asked.state)
    assert(rules.resolveWalker(asked.state, actor, WayStation.decisionId,
      DecisionAnswer.ChooseOneAnswer(WayStation.supplyOption)).isLeft)
    val result = Outcomes.ready(answer(asked.state,
      WayStation.favorOption).state)
    assertEquals(Look(result).pawn(actor), coast)
```

`ParkedDecisionAssertions.assertParked` is the check `KnightsErrantSuite` uses for its own power question.

Append to `TravelDestinationRowsSuite`:

```scala
  test("Way Station's site offers the favor beside the Supply"):
    val ready = Table.from(open(supply = 7))
      .denizen(CatalogNames.denizen("Way Station"), at = coast)
      .warbandsAt(coast, Table.p2, 1).favor(actor, 1).unchecked
    assertEquals(row(ready, coast).badge, Some("1 Supply or 1 favor"))
    val broke = Table.from(ready).supply(actor, 0).unchecked
    assertEquals(row(broke, coast).badge, Some("1 favor"))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.WayStationSuite"`
Expected: compile failure, `WayStation` is not defined.

- [ ] **Step 3: Write the power**

Move `WayStationCard` out of `HoldingNomadDenizens.scala` into the new file. Import it in `NewFoundations.scala` from `oathdigital.gameplay.powers.travel` beside `TollRoadsCard`. Create `WayStation.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogResolution, PowerAnswers, RulerToll}
import oathdigital.model._

object WayStationCard extends Denizen(DenizenId("169"), "Way Station", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.way-station"),
    persistent = true, cost = Cost.free,
    text = "Spend no Supply if you're traveling to this site and either " +
      "rule Way Station or choose to give [favor] to its ruler. " +
      "_(Give it to Chancellor if Empire, burn it if bandits.)_")
  val powers: Vector[PrintedPower] = Vector(power)

/** Way Station (card 169), a persistent rule of a faceup site card: a Travel
  * to its site spends no Supply for Way Station's ruler, and for anyone else
  * who chooses to give 1 favor to that ruler, or burn it when bandits rule.
  * Empire rulers are not supported.
  *
  * It rewrites the Travel's cost node after every other Travel power (priority
  * 3), so it sees the Supply they leave. Its ruler's payment is removed. A
  * non-ruler with favor, on a Travel that still costs Supply, is asked in the
  * Action panel; the Supply answer is offered only when affordable. The
  * pawn's move stays outside the question, so later windows still see the
  * route. The question parks before the move, and Travel's source is a start
  * fact, so a resume rebuilds the same tree.
  *
  * The favor writes the toll lines Curfew and Forced Labor write: "Gave
  * {1 favor} to {Blue}." or "Burned {1 favor}."
  */
final case class WayStation private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = WayStationCard.id
  def id: PowerId = WayStation.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def priority: Int = 3
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
  override def noteKeys: Vector[NoteKey] = RulerToll.keys

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      arriving(ctx).fold(operations)((traveller, ruler) =>
        rewrite(ctx.state, traveller, ruler, operations)))))

  override def applicable(ctx: PowerCtx): Boolean = arriving(ctx).nonEmpty

  /** The traveller and Way Station's ruler, on a Travel to its site. */
  private def arriving(ctx: PowerCtx): Option[(PlayerId, SiteRuler)] = for
    route <- TravelRoute.pawnMove(ctx.operation)
    site <- SiteRulers.siteOf(ctx.state, cardId)
    if route.destination == site
    ruler <- SiteRulers.rulerOfCard(ctx.state, cardId)
  yield (route.player, ruler)

  private def rewrite(ready: ReadyGame, traveller: PlayerId, ruler: SiteRuler,
      operations: Vector[Operation]): Vector[Operation] =
    val payment = operations.collect {
      case spend @ SpendSupply(player, _, _) if player == traveller => spend }
    val supply = payment.map(_.amount).sum
    val board = ready.game.current.players.find(_.player == traveller)
      .map(_.board)
    if supply == 0 then operations
    else if ruler == SiteRuler.Player(traveller) then
      TravelPayments.withoutSupply(operations, traveller)
    else if !board.exists(_.favor >= WayStation.Favor) then operations
    else
      val options = Vector(
        Option.when(board.exists(_.supply.supply >= supply))(
          DecisionOption.Button(WayStation.supplyOption,
            s"Spend $supply Supply")),
        Some(DecisionOption.Button(WayStation.favorOption, ruler match
          case SiteRuler.Player(owner) => s"Give 1 favor to ${owner.value}"
          case _ => "Burn 1 favor"))).flatten
      Vector[Operation](
        Decide(WayStation.decisionId, traveller, DecisionQuery.ChooseOne(
          options, heading = Some("Way Station: spend no Supply?")),
          presentation = DecisionPresentation.Panel),
        Branch((_, pending) =>
          PowerAnswers.one(pending, WayStation.decisionId) match
            case Some(WayStation.favorOption) => Vector(
              RulerToll.payment(traveller, ruler),
              Note(id, RulerToll.note(cardId, traveller, ruler)))
            case Some(_) => payment
            case None => Vector.empty)) ++
        TravelPayments.withoutSupply(operations, traveller)

object WayStation:
  val id: PowerId = WayStationCard.power.id
  val Favor: Int = 1
  /** Under the `travel.` prefix, so the Game Log narrates it. */
  val decisionId: String = "travel.way-station"
  val supplyOption: DecisionOptionRef.Button = DecisionOptionRef.Button("supply")
  val favorOption: DecisionOptionRef.Button = DecisionOptionRef.Button("favor")

  def forCatalog(catalog: ExecutableCatalog): WayStation =
    new WayStation(catalog)
```

Register it: append `Vector(WayStation.forCatalog(catalog)) ++` in `TravelModifiers.forCatalog`, before `Vector(Tyrant)`. Add `"denizen.way-station"` to `persistentRules` in `PowerKindsCatalogSuite`. If `RulerToll` is `private[powers]` and the compiler refuses access from `powers.travel`, widen nothing: `private[powers]` covers subpackages. If it still refuses, report it rather than widen the modifier.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.* oathdigital.application.TravelDestinationRowsSuite oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: PASS. The card catalog suite also counts implemented cards; run `./sbtw test` and update any count it names by one.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/travel/WayStation.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/main/scala/oathdigital/gameplay/powers/travel/TravelModifiers.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/WayStationSuite.scala \
  src/test/scala/oathdigital/application/TravelDestinationRowsSuite.scala
git commit -m "feat(travel): Way Station spends no Supply for its ruler, or for a favor"
```

---

### Task 5: Hidden options explain themselves on hover only

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala:300-315,426-462`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala:38-83,95-120`
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala:144-219`
- Modify: `src/main/scala/oathdigital/gameplay/powers/cardplay/VisionPlay.scala:31-38`
- Modify: `src/test/scala/oathdigital/application/TravelDestinationRowsSuite.scala`
- Modify: the suites that assert a hidden option's line: `application/CardPlayReasonsSuite`, `gameplay/CampaignPowersSuite`, `gameplay/powers/campaign/BagOfSiegeworksSuite`, `gameplay/powers/cardplay/{SacredGroundSuite, SecretPoliceSuite, VowOfObedienceSuite}`, `gameplay/powers/targeting/{CircletOfCommandSuite, ForgottenVaultSuite, FortressRulesSuite, LostTongueSuite, TargetingFixture}`, all under `src/test/scala/oathdigital/`
- Modify: `docs/superpowers/specs/2026-09-26-power-log-lines-design.md` (status note)

**Interfaces:**
- Produces: `NarrowPassSitePower.noTravel: NoteKey`. `WalkerPowerGather.applyWindow` loses nothing; `applyWindowNoted` and `hiddenNotes` are deleted.

- [ ] **Step 1: Write the failing tests**

Append to `TravelDestinationRowsSuite`:

```scala
  test("a destination Narrow Pass blocks says so on hover and logs nothing"):
    val ready = board(supply = 7)
    val shown = row(ready, provincePlains)
    assertEquals(shown.state, "unavailable")
    assertEquals(shown.reason, Some(
      "Narrow Pass: Cannot travel to other sites in the region."))
    val started = accepted(rules.startWalker(Ready(ready), ActionRef.Travel,
      actor, Vector.empty))
    assert(!started.events.exists(_.isInstanceOf[
      oathdigital.gameplay.walker.PowerNoted]))

  test("a destination both blocked and unaffordable gives the block"):
    // Dunes costs 2 from the Cradle, and Narrow Pass blocks it.
    assertEquals(row(board(supply = 1), provincePlains).reason, Some(
      "Narrow Pass: Cannot travel to other sites in the region."))
```

In `CardPlayReasonsSuite.assertSpotRefused`, the check becomes `shown.reason.exists(_.endsWith("Cannot play a Vision faceup."))`. In `CampaignPowersSuite`, the test "each site the Pass hides writes the same line" becomes "each site the Pass hides keeps its reason and writes no line". It asserts `passNotes(withPawnAt(ancientCity)) == Vector.empty`, and that the parked decision holds two options `DecisionOption.Unavailable(_, OptionReason.Noted(passPower.id, note))` with `note.key == "no-target"`. Read the parked decision as `PlacementPicks.parkedOptions` does. Every other hidden-option line that the other listed suites assert through `NoteText.said` now expects no line: replace the expected `Vector(NoteText.Said(…))` with `Vector.empty`, and keep the test's name truthful. "writes a line" becomes "explains the hidden option on hover"; add one row assertion where the suite can project, as `CardPlayReasonsSuite` does. A note that is not a hidden option's (a `covers = true` line, Vow of Peace's `no-sacrifice`) keeps its line.

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.TravelDestinationRowsSuite oathdigital.application.CardPlayReasonsSuite"`
Expected: FAIL. The Narrow Pass row is missing (no note keeps it), and the Vision reason still names the actor.

- [ ] **Step 3: Stop journaling hidden-option notes**

In `ProcedureWalker`:
- `Narrowed` loses `notes`, keeping `Narrowed(decide: Decide)`, or is replaced by `Decide` throughout `narrowAt`. In `narrowAt` the `noted` vector is still read for `reasons`.
- In `runLeaf`, `case Some(narrowed) => runNarrowed(narrowed.decide, ctx, path, cursor, resume, contributions, strict)`. Delete the `hidden` local and its comment.
- In `walkFolded`, call `WalkerPowerGather.applyWindow(window, operation, ctx.state, ctx.activePlayer, …, children, ctx.procedure, ctx.answered, cursor.isDefined)` and walk with `ctx` unchanged.
- Fix the `runLeaf` doc that says a decision's notes are journaled when reached fresh: the look-ahead now keeps each note as the hidden option's reason only.

In `WalkerPowerGather`, fold `applyWindowNoted` into `applyWindow` without the `noting` parameter and the third tuple member, and delete `hiddenNotes`. `restrictOptions` keeps using `firstNote` for reasons. In `lookAheadNoted`'s doc, "The notes the look-ahead writes" becomes "The notes that explain the options `narrowed` no longer offers".

- [ ] **Step 4: Narrow Pass's Travel note and the actor-free wording**

In `TravelSitePowers.scala`, `NarrowPassSitePower`:

```scala
  override def noteKeys: Vector[NoteKey] =
    Vector(NarrowPassSitePower.noTarget, NarrowPassSitePower.noTravel)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelActionEligibility ->
      Vector(Restriction((ctx, _) => blocked(ctx), travelNote)),
    PowerWindow.CampaignTargetSelection ->
      Vector(OptionRestriction(campaignBlocked, campaignNote)))

  /** A blocked destination's hover reason (travel destination design,
    * ruling 4). */
  private def travelNote(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[PowerNote] =
    Some(NarrowPassSitePower.noTravel(PowerSourceRef.Site(site)))

  /** A hidden target site's hover reason. */
  private def campaignNote(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[PowerNote] =
    Some(NarrowPassSitePower.noTarget(PowerSourceRef.Site(site)))
```

and in its companion:

```scala
  /** "Cannot target other sites in the region." */
  val noTarget: NoteKey = NoteKey("no-target", Vector(
    NotePart.Text("Cannot target other sites in the region.")))
  /** "Cannot travel to other sites in the region." */
  val noTravel: NoteKey = NoteKey("no-travel", Vector(
    NotePart.Text("Cannot travel to other sites in the region.")))
```

If `ctx` and `ref` are reported unused, name them `_`.

In `VisionPlay.scala`:

```scala
  val noFaceup: NoteKey = NoteKey("no-faceup", Vector(
    NotePart.Text("Cannot play a Vision faceup.")))
```

and `note(card)` returns `(_, _) => Some(noFaceup(PowerSourceRef.Card(card)))`.

- [ ] **Step 5: Run the tests**

Run: `./sbtw test`
Expected: PASS once Step 1's suite edits are in. A golden game-log fixture that held a hidden-option line drops it. Inspect each diff, and accept it only when the removed line is a hidden-option note.

- [ ] **Step 6: Record the change in the power log lines design**

Under the status lines at the top of `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`, add:

```markdown
**Superseded in part (2026-10-07):** hidden options no longer write lines.
Each hide hook's note is the hidden option's hover reason only, and the two
notes that named the acting player dropped the name
([travel destination design](2026-10-07-travel-destination-walker-design.md),
ruling 10).
```

- [ ] **Step 7: Commit**

Stage the main files, the design doc, and every test file and fixture Steps 1 and 5 changed, by explicit path.

```bash
git commit -m "feat(walker): explain a hidden option on hover only, and Narrow Pass blocks Travel there"
```

---

### Task 6: Frontend — start Travel, badge sites, retire the board targets

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala:166-190,256-272`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala:165-220`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ModifierFlow.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ModifierFlowDraft.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/SessionDrafts.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableControls.scala:19-29`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala` (`commandForSelection`, `candidateDetailBadge`, `candidateDetailText`, `actionLabel` and what only they use)
- Delete: `frontend/src/main/scala/oathdigital/frontend/BoardTargetSelectionState.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala:3-30` and its codec, the `GameProjection` DTO's `boardTargetActions`
- Modify: `shared/src/main/scala/oathdigital/protocol/MajorActionPreviewProtocol.scala` (drop `PreviewTarget` and `targets`)
- Modify: `src/main/scala/oathdigital/application/GameProjection.scala:96`, `LegalActionProjector.scala` (`LegalProjection.boardTargets`), `GameApplicationService.scala` (`MajorActionPreviewAccepted.targets`, `acceptPreview`'s `targets`), `server/GameRoutes.scala:97-128` (`MajorActionPreviewTargets.from`), `server/AuthenticatedGameRoutes.scala:98-102`, `server/TrustedGameGateway.scala:61-66`
- Modify and delete: the frontend and shared tests that exercise what is removed (Step 5 lists them)
- Test: `frontend/src/test/scala/oathdigital/frontend/TravelLaunchSuite.scala` (create)

**Interfaces:**
- Consumes (Task 3): the `beginTravel` legal control; site options with `badge` and `reason`.
- Produces: no new API. The deleted names are listed in Steps 3 and 4.

- [ ] **Step 1: Write the failing frontend tests**

Create `frontend/src/test/scala/oathdigital/frontend/TravelLaunchSuite.scala`. It follows `FacedownAdviserPickSuite` (the launch) and `BoardOptionsRenderSuite` (the board):

```scala
package oathdigital.frontend

import oathdigital.frontend.Dom.one
import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => Intent}
import oathdigital.testkit.Unwrap.as
import org.scalajs.dom

/** Travel starts the walker with no destination, and the parked destination
  * decision draws each site's cost or reason on its site box (travel
  * destination design, rulings 2 to 4). */
class TravelLaunchSuite extends munit.FunSuite:
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private def site(id: String, name: String) = GameSite(id, name,
    looseFavor = 0, looseSecrets = 0, denizenCapacity = 3, relicCapacity = 0,
    denizens = Vector.empty, relics = GameSiteRelics(0), defense = 1)
  private val table = TestProjections.actingProjection(players = Vector(red),
    world = Vector(GameRegion("cradle", Vector(site("woods", "Deep Woods"),
      site("shore", "Desolate Shore")))),
    legalControls = Vector("beginTravel"))

  test("the Travel control starts the walker and names no destination"):
    val ui = new RecordingControls()
    val pane = ActionDecisionRenderer.actionsPanel(table,
      ActionPanels.actingViewer("red"), ParkedDecision.Routed(None, None),
      canControl = true, SessionDrafts.empty, ui).element
    as[dom.html.Button](one(pane, ".travel-action")).click()
    assertEquals(ui.submitted, Vector(Intent.StartWalker("travel",
      Vector.empty)))

  test("a destination's badge is drawn on its site box, and a refused one " +
      "says why on hover"):
    val reason = "Needs 3 Supply; you have 2"
    val badged = OptionView(DecisionOptionState("site", "woods", "Deep Woods",
      badge = Some("2 Supply")), OptionState.Available, None)
    val refused = OptionView(DecisionOptionState("site", "shore",
      "Desolate Shore", state = "unavailable", reason = Some(reason)),
      OptionState.Unavailable, Some(reason))
    val options = new BoardOptions(Vector(badged, refused).map(view =>
      BoardOptionViews.home(view.option) -> view).toMap, _ => ())
    val world = WorldBoardRenderer.world(table, options, SessionDrafts.empty,
      new RecordingControls())
    assertEquals(one(one(world, """article[aria-label="Deep Woods"]"""),
      ".target-detail-badge").textContent, "2 Supply")
    val shore = one(world, """article[aria-label="Desolate Shore"]""")
    assertEquals(shore.getAttribute("title"), reason)
    assertEquals(shore.getAttribute("aria-disabled"), "true")
```

If `WorldBoardRenderer.world` loses its `drafts` parameter in Step 4, drop the argument here.

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.TravelLaunchSuite"`
Expected: FAIL. No "Travel" button is drawn for `beginTravel`, and no badge is drawn for a site option.

- [ ] **Step 3: Draw the control and the badge**

In `ActionDecisionRenderer`, after the Forge block:

```scala
       if value.legalControls.contains("beginTravel") then
         val travel = button("Travel", "act-action travel-action")
         travel.disabled = !canControl
         travel.onclick = _ => submitCommand(
           GameCommand.StartWalker("travel", Vector.empty))
         groups.appendKind("travel", travel)
```

`submitCommand` routes a walker start through the modifier flow, which previews `travel` and orders any modifiers first.

In `WorldBoardRenderer`'s site loop, after `options.decorate(control, …)`, add:

```scala
       options.view(Vector(BoardOptionViews.key("site", site.siteId)))
         .flatMap(_.option.badge)
         .foreach(badge => control.appendChild(
           text("span", "target-detail-badge", badge)))
```

- [ ] **Step 4: Delete the board targets and the targets stage**

Delete, following the compiler:
- **Board targets.** `BoardTargetSelectionState.scala`; `SessionDrafts.boardTargets` and `Draft.BoardTargets`; `BoardSelectionResult` and `TableControls.handleSelection` if nothing else uses them; and the site loop's `candidate`, `pickable`, `siteTargetClasses(candidate…)` and `candidateDetailBadge` uses (the site box keeps its plain classes). In `ActionDecisionRenderer`, the `selection.nonEmpty` branch and the `value.boardTargetActions` loop go. In `ServerUiSupport`, `commandForSelection`, `candidateDetailBadge`, `candidateDetailText` and `actionLabel` go, as does anything only they used.
- **Targets stage.** `ModifierFlowStage` (leaving the draft without a stage), `ModifierFlowDraft.actionKind`, `showTargets`, `backFromTargets`, `cancel`, `targetedActions`, `targeted` and `targetAction`. In `ModifierFlow`: `enterTargets`, `startTargetedFlow`, `completeTargetCommand`, `restoredTargets`, `beginTargetedMajorAction`, `backFromTargets`, `cancelTargetAction`, and `confirmModifierSelection`'s `None` branch, which leaves `command` non-optional. Also `FlowStep.Targets`, `TargetsEntry`, `FlowExit.Restarted`, `FlowExit.Completed` and `FlowExit.TargetsLeft`; `FlowExit.Cancelled` loses its argument. In `ActionControls`: `beginTargetedMajorAction`, `backFromTargets` and `cancelTargetAction`.
- **Protocol.** `GameProjection.boardTargetActions` and its DTO types (`BoardTargetActionProjection`, `BoardTargetCandidateProjection`, `BoardTargetRefProjection`) with their codec arms; `LegalProjection.boardTargets`. Also `MajorActionPreviewResponse.targets`, `PreviewTarget` and their codec; `MajorActionPreviewAccepted.targets`, `acceptPreview`'s `targets` parameter, and `MajorActionPreviewTargets.from`. `validate` stays. Its three callers send no targets.

Update the docs these files carry where they name Travel or targets: `ModifierFlowDraft.submission`'s doc, the `SessionDrafts` doc, `ModifierFlow`'s "and picking its targets", `ActionProjectionDtos`' "sites are the only target kind", and `SessionDrafts.leave`'s "six ways".

- [ ] **Step 5: Move or delete the tests of what is gone**

Run: `./sbtw frontend/test` and `./sbtw test`.

- Delete the suites that test only removed code: `BoardTargetSelectionStateSuite` and `TargetSelectionExitSuite`.
- In `ModifierFlowSuite`, `ModifierFlowDraftSuite`, `ModifierFlowDrafts`, `ModifierSelectionStateSuite`, `SessionDraftsSuite`, `RecordingControls`, `ServerUiSupportSuite`, `ActionPanePromptSuite`, `BoardSurfaceSuite`, `SiteBoxLayoutSuite`, `WalkerDecisionDraftSuite`, `GameLogTakenBackPaneSuite` and `HttpGameClientSuite`, delete the tests of the targets stage and the board targets. Keep the tests of the ordering stage, rewriting a Travel example to Search where the test is about ordering.
- Drop the board targets and preview targets from projection and preview fixtures in `shared/src/test` (`ProjectionProtocolSuite`, `CommandProtocolSuite`, and the preview protocol suite) and in the server route suites.

Each removal must leave a suite whose remaining tests still say what they test. Run both suites again. Expected: PASS.

- [ ] **Step 6: Commit**

Stage every changed and deleted path explicitly (`git add` for changes, `git rm` for deletions).

```bash
git commit -m "feat(travel): start Travel from its control and badge its sites; retire the board targets"
```

---

### Task 7: Docs and the full gate

**Files:**
- Modify: `docs/ROADMAP.md`
- Modify: `docs/superpowers/specs/2026-10-07-travel-destination-walker-design.md` (status)
- Modify: `docs/superpowers/specs/2026-10-05-catalog-batch-4-rulings.md:169`
- Modify: `CONTEXT.md` (Walker, Table session)

- [ ] **Step 1: Update the docs**

- **Roadmap.**
  - Under **Phase - Board decision follow-ups**, delete the Travel bullet.
  - Under **Catalog batch - engine-blocked**, "A Travel that can pause on a decision" becomes: "**Hospitality** is unblocked: start facts keep Travel's source across a resume (travel destination design). Way Station is implemented."
  - Under **Engine deferred items**, add: "- [ ] **Move Campaign's pinned facts onto start facts.** `CampaignSetup.pinned` stores the Campaign's kind and defender as sentinel `ModifyDicePool` operations; start facts (travel destination design, ruling 6) are the durable place for them."
  - In the **Walker follow-ups** item, delete "At an action's start command, window folds and the restriction look-ahead see no walker procedure…" only if `PowerCtx.procedure` now reads the start's procedure. Otherwise leave it.
- **Spec status.** Add "Implemented 2026-10-07." to the status paragraph.
- **Catalog batch 4 rulings, row 169.** Append "Implemented with the travel destination design."
- **CONTEXT.md.** Under Walker, add:

```markdown
**Start fact**:
Something the engine works out when a procedure starts and keeps until it
ends, because the walk itself changes it: Travel's starting site, once the
pawn has moved. The player never chooses it; a start selection is what the
player chose.
_Avoid_: start argument (that is the player's selection), pinned value
```

  Under Table session, drop "the picked board targets" from **Draft set** and "and picking its targets" from **Modifier flow**.

- [ ] **Step 2: Run the full gate**

Run: `./sbtw "test" "frontend/test"`, then `./sbtw frontend/fullOptJS`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`, then `git diff --check`.
Expected: every command passes.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md CONTEXT.md \
  docs/superpowers/specs/2026-10-07-travel-destination-walker-design.md \
  docs/superpowers/specs/2026-10-05-catalog-batch-4-rulings.md
git commit -m "docs: record the Travel destination in the walker"
```
