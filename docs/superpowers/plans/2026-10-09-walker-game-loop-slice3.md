# Walker-Driven Game Loop, Slice 3 (The Minor Actions) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Peek Site Relics, Reveal Owned Relic and Move Warbands become walker procedures. The legacy `MinorActions` handler, its operation policy, its three events and codecs, and its three commands and intents go. The Act panel's buttons start the new procedures with `StartWalker`.

**Architecture:**

- **The procedures.** A new object, `MinorActionProcedures`, builds three action trees: `ActionRef.PeekSiteRelics`, `RevealOwnedRelic` and `MoveWarbands`.
  - Each start keeps the legacy handler's gates: the Act phase check and the first-game rules gate.
  - Nothing is chosen at the start. Reveal asks which facedown relic. Move Warbands asks which way, then how many. Peek asks nothing.
  - Every operation is required, so a restriction that refuses one refuses the action.
- **The Game Log.** Each procedure posts one action line in today's wording, in a section of its own. The detail lines for its peek, reveal or move are not repeated.
- **The legacy path goes.** `MinorActions`, `MinorActionOperationPolicy`, the events `SiteRelicsPeeked`, `OwnedRelicRevealed` and `WarbandsMoved`, and their codec, Game Log arms and undo barrier arm all go. `GameCommand.PeekSiteRelics`, `RevealOwnedRelic` and `MoveWarbands` go with their intents and routes. The driver's `Ended.MinorAction` goes, so the driver is handed the finished procedure.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md`, including its plan-time rulings for slices 1 and 2, before starting. Task 1 adds this plan's rulings to it.

**Starts from:** `main`, after walker game loop slice 2 has merged. Check that `git log --oneline main` shows slice 2's last commit, "docs: record walker game loop slice 2". If slice 2 has not merged, stop and report. Slice 2 changes several files this plan also changes (`ActionDecisionRenderer`, the command and intent files, `SituationDriver`, `OathDriver`). Every edit below is anchored on minor-action code that slice 2 does not touch, but read each file before editing it.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`.
  - An unused import or private member fails the build. Remove every import a deletion leaves unused.
  - A non-exhaustive match fails the build. A new `ActionRef` case needs an arm in every match over `ProcedureRef` without a catch-all: `ActionLines.lines` and `StartLines.name`.
  - A refutable pattern in a `val` also fails it, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker or `gameplay/operations` sources. `gameplay/actions` may import a power, as `TravelRules` does.
- **Line width.** Scala lines this plan writes stay at or under 80 characters, as in the files around them. Re-wrap any line that comes out longer.
- **Indentation in lists.** A code block inside a list item is indented two extra spaces for the list. Drop those two when you copy it.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
  - Run `ExitWorktree` with `keep` before merging into `main`. Do not push.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **No compatibility** (spec ruling 14). Stored journals with the deleted commands or events stop replaying. Do not bump the journal version.
- **Changed tests.** This slice changes behavior, so existing tests change. Each task names the expected changes. When a test fails for a reason the task does not name, stop and report it.
- **Texts.** Docs and commit messages use plain English. Game text (headings, labels, reasons, log lines) is given in the steps; use it exactly.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end. Report every test added and deleted.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

Task 1 writes these into the spec.

1. **Each minor action is an action.** `ActionRef.PeekSiteRelics` (key `"peek-site-relics"`), `RevealOwnedRelic` (`"reveal-owned-relic"`) and `MoveWarbands` (`"move-warbands"`) are registered like any action. The action boundary follows each one, as it followed the legacy handler. They have no modifier window and record no fallback diagnostics, as before.
2. **The choices move into the walk.** No minor action takes a start selection, as Search and Travel take none.
   - Reveal Owned Relic asks which facedown relic, in the panel.
   - Move Warbands asks which way, then how many.
   - Peek Site Relics asks nothing. The peek is recorded in the player's knowledge, as before; no `Inspect` decision is added.
3. **Move Warbands shows both ways.** A way that cannot be taken is shown unavailable with its reason. Vow of Wandering's reason is one of them: the procedure reads the Vow's holder itself, as the Vow's doc says each placing power does. When neither way is open, the start is refused with both reasons.
4. **Every operation is required.** Any operation restriction that refuses a peek, a reveal or a move refuses the action with that restriction's reason.
5. **The starts keep the legacy gates** until slice 4: the Act phase check (spec ruling 9) and the first-game rules gate.
6. **One action line each, in a section of its own.** The wording is the legacy lines': "Peeked at {relics} at {site}", "Revealed {relic}", "Moved {n} warbands to {site}" and "Moved {n} warbands from {site}". They become action lines in a section keyed by the procedure, and the shared "minor" section goes. The peek, reveal and move detail lines are not repeated for these runs. A peek and a reveal stay undo barriers through their walker step's hidden operations.
7. **The commands go in slice 3**, not slice 5. The Act panel starts each procedure with `StartWalker`. Its buttons are shown by today's legal controls, `peekSiteRelics`, `revealOwnedRelic` and `moveWarbands`, until slice 4 replaces the panel.
8. **The driver is handed the finished procedure.** With `Ended.MinorAction` gone, `OathDriver.next` takes the `ProcedureRef` whose walk ended, and the `Ended` enum goes.

## What exists before this plan

Check each of these in the code before Task 1. If a name or signature differs, read slices 1 and 2's commits and adapt. Stop and report if the difference is more than a name.

- `model/ProcedureRef.scala`: `ActionRef.all` lists 12 actions, from `Search` to `Campaign`.
- `gameplay/walker/WalkerProcedureRegistry.scala`: `Entry(fallbackKind, rollDecisionId, modifierWindow, build, rebuild, ...)` and the `entries` map.
- `gameplay/actions/MinorActions.scala`: `MinorActionCommand`, `MinorActions.handle` and `evolve`, and the private `validateAct` with the first-game rules gate.
- `gameplay/OathDriver.scala`: `private[gameplay] enum Ended { case Walk(procedure: ProcedureRef); case MinorAction }`, and `next(transition, ended)`.
- `gameplay/OathRules.scala`: `handle(state, command: MinorActionCommand)` and three `MinorActions.evolve` arms.
- `gameplay/powers/economy/VowOfWandering.scala`: `VowOfWandering.holds(ready, player)` and `VowOfWandering.Reason`.
- `application/LegalActionProjector.scala`: the Act controls `"peekSiteRelics"`, `"revealOwnedRelic"` and `"moveWarbands"`.
- `application/gamelog/DetailLines.scala`: the `Peek`, `Reveal` and warbands-to-site `Move` arms.
- `frontend/.../ActionDecisionRenderer.scala`: the `value.minorActions.foreach` block with the peek, reveal and warband-move buttons.
- Tests: `gameplay/MinorActionsSuite`, `testkit/ParkedNode.of`, `testkit/PowerStubs.OperationRestrictionStub`, `testkit/PowerRules.withWalkerPowers`, and `gamelog/LogScripts.atTable`, `revealRelic` and `named`.

## File map

| File | Change | Task |
|---|---|---|
| `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md` | modify | 1, 3 |
| `src/main/scala/oathdigital/model/ProcedureRef.scala` | modify | 1 |
| `src/main/scala/oathdigital/gameplay/actions/minor/MinorActionProcedures.scala` | create | 1 |
| `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` | modify | 1 |
| `src/main/scala/oathdigital/application/gamelog/{ActionLines,StartLines,DetailLines}.scala` | modify | 1 |
| `src/main/scala/oathdigital/gameplay/actions/{MinorActions,MinorActionOperationPolicy}.scala` | delete | 2 |
| `src/main/scala/oathdigital/gameplay/{OathRules,OathDriver,OathRulesWalker}.scala` | modify | 2 |
| `src/main/scala/oathdigital/gameplay/powers/economy/VowOfWandering.scala` | modify (doc) | 2 |
| `src/main/scala/oathdigital/model/{GameEventProtocol,GameViolation}.scala` | modify | 2 |
| `src/main/scala/oathdigital/serialization/ActionEventCodec.scala` | delete | 2 |
| `src/main/scala/oathdigital/serialization/GameEventWire.scala` | modify | 2 |
| `src/main/scala/oathdigital/application/{GameCommands,Authorization,GameIntentMapper,GameApplicationService,UndoBarrier}.scala` | modify | 2 |
| `src/main/scala/oathdigital/application/gamelog/{EventLines,GameLogFormatter,LogSections}.scala` | modify | 2 |
| `shared/src/main/scala/oathdigital/protocol/{CommandIntents,CommandIntentCodec,CommandIntentDecoders}.scala` | modify | 2 |
| `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala` | modify | 2 |
| `src/test/scala/oathdigital/gameplay/MinorActionsSuite.scala` | modify | 1, 2 |
| `src/test/scala/oathdigital/application/gamelog/{LogScripts,GameLogEventSuite,GameLogSectionSuite}.scala` | modify | 1, 2 |
| `src/test/scala/oathdigital/model/ProcedureRefSuite.scala` | modify | 1 |
| other test suites (listed in Task 2) | modify | 2 |
| `docs/ROADMAP.md` | modify | 3 |

---

### Task 1: The three procedures

The procedures exist, run, replay and post their lines. The legacy commands still work beside them; Task 2 deletes them.

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md`
- Modify: `src/main/scala/oathdigital/model/ProcedureRef.scala`
- Create: `src/main/scala/oathdigital/gameplay/actions/minor/MinorActionProcedures.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`, `StartLines.scala`, `DetailLines.scala`
- Modify tests: `src/test/scala/oathdigital/gameplay/MinorActionsSuite.scala`, `src/test/scala/oathdigital/model/ProcedureRefSuite.scala`, `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`, `src/test/scala/oathdigital/application/gamelog/GameLogEventSuite.scala`

**Interfaces:**
- Produces, in `oathdigital.model.ActionRef`: `PeekSiteRelics` (key `"peek-site-relics"`), `RevealOwnedRelic` (`"reveal-owned-relic"`) and `MoveWarbands` (`"move-warbands"`), appended to `ActionRef.all` in that order.
- Produces, in `oathdigital.gameplay.actions.minor`:

  ```scala
  object MinorActionProcedures:
    val relicDecisionId: String            // "minor.reveal-relic"
    val directionDecisionId: String        // "minor.warbands-direction"
    val amountDecisionId: String           // "minor.warbands-amount"
    val toSite: DecisionOptionRef.Button   // Button("to-site")
    val toBoard: DecisionOptionRef.Button  // Button("to-board")
    type Build = (ExecutableCatalog, ReadyGame, PlayerId,
      Vector[DecisionOptionRef]) => Either[OathViolation, Operation]
    val buildPeek, rebuildPeek, buildReveal, rebuildReveal,
      buildMove, rebuildMove: Build
  ```

- Produces, in tests: `LogScripts.peekRelics` and `LogScripts.warbandsMoved`.

- [ ] **Step 1: Set up and record the baseline**

Follow Global Constraints, "Worktree". Check "What exists before this plan". Run `./sbtw "test" "frontend/test"` and note both counts.

- [ ] **Step 2: Write the plan-time rulings into the spec**

In `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md`, add a section after "Plan-time rulings (slice 2)":

```markdown
## Plan-time rulings (slice 3)

1. **Each minor action is an action.** `ActionRef.PeekSiteRelics`,
   `RevealOwnedRelic` and `MoveWarbands` are registered like any action.
   The action boundary follows each one, as it followed the legacy handler.
   They have no modifier window and record no fallback diagnostics.
2. **The choices move into the walk.** No minor action takes a start
   selection. Reveal Owned Relic asks which facedown relic. Move Warbands
   asks which way, then how many. Peek Site Relics asks nothing: the peek is
   recorded in the player's knowledge, and no `Inspect` decision is added.
3. **Move Warbands shows both ways.** A way that cannot be taken is shown
   unavailable with its reason, Vow of Wandering's included: the procedure
   reads the Vow's holder itself, as the Vow's other placing powers do.
   When neither way is open, the start is refused with both reasons.
4. **Every operation is required.** An operation restriction that refuses
   a peek, a reveal or a move refuses the action with its own reason.
5. **The starts keep the legacy gates** until slice 4: the Act phase check
   (ruling 9) and the first-game rules gate.
6. **One action line each, in a section of its own,** in the legacy lines'
   wording. The shared "minor" section goes, and the peek, reveal and move
   detail lines are not repeated for these runs. A peek and a reveal stay
   undo barriers through their walker step's hidden operations.
7. **The commands go in slice 3.** The Act panel starts each procedure with
   `StartWalker`, shown by today's legal controls until slice 4 replaces the
   panel.
8. **The driver is handed the finished procedure.** With the legacy minor
   action gone, `OathDriver.next` takes the procedure whose walk ended.
```

In the "Slices" section, append to item 3 ("**The minor actions.** ..."): " Their commands, intents, events and codecs go with them."

- [ ] **Step 3: Write the failing rules tests**

In `src/test/scala/oathdigital/gameplay/MinorActionsSuite.scala`, keep every existing test for now. Add these imports:

```scala
import oathdigital.gameplay.actions.minor.MinorActionProcedures
import oathdigital.gameplay.actions.minor.MinorActionProcedures.{toBoard,
  toSite}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.economy.VowOfWandering
import oathdigital.gameplay.walker.ActionBoundaryRan
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer, ChooseOneAnswer}
import oathdigital.testkit.{ParkedNode, PowerStubs}
```

Add these helpers after `base`:

```scala
  private val dowsingSticks = CatalogNames.relic("Dowsing Sticks")

  /** p1's warbands on the board, and the forces at Broken Peaks. The rest
    * of the state is not compared: the action boundary may refill bandits
    * elsewhere. */
  private def warbandCounts(state: OathState): (Int, SiteForces) =
    val ready = Outcomes.ready(state)
    (Look(ready).boardWarbands(p1),
      ready.game.current.map.sites(brokenPeaks).forces)

  /** The query of the decision `state` is parked on, rebuilt as the
    * projector rebuilds it. */
  private def parkedDecisionQuery(state: OathState,
      powers: WalkerPowers = WalkerPowers.empty)(
      using munit.Location): DecisionQuery =
    ParkedNode.of(state, catalog, powers) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide.query
      case other => fail(s"expected a parked decision, got $other")

  /** `events` evolved one by one from `start`. */
  private def replayedFrom(start: ReadyGame, events: Vector[OathEvent])
      : Either[OathViolation, OathState] =
    events.foldLeft[Either[OathViolation, OathState]](Right(Ready(start))):
      case (Right(state), event) => rules.evolve(state, event)
      case (failure @ Left(_), _) => failure

  private def wayButton(ref: DecisionOptionRef.Button, label: String) =
    DecisionOption.Button(ref, label)

  private def blockedWay(ref: DecisionOptionRef.Button, label: String,
      reason: String) = DecisionOption.Unavailable(wayButton(ref, label),
    OptionReason.Written(reason))
```

Then add the tests:

```scala
  test("Peek Site Relics peeks at every relic at the pawn's site, runs the " +
      "boundary and replays"):
    val peeked = accepted(rules.startWalker(Ready(base),
      ActionRef.PeekSiteRelics, p1))
    val after = Outcomes.ready(peeked.state)
    assert(after.knowledge.siteRelics(p1)(brokenPeaks).contains(stickyFire))
    parked.assertResumed(peeked.state, Phase.Act, p1)
    assertEquals(peeked.events.count(_ == ActionBoundaryRan), 1)
    assertEquals(replayedFrom(base, peeked.events), Right(peeked.state))

  test("Peek Site Relics refuses a site with no relics"):
    val bare = Table.start.pawn(p1, at = brokenPeaks).ready
    // The precondition: nothing lies at Broken Peaks.
    assertEquals(Look(bare).relicsAt(brokenPeaks), Vector.empty)
    assertEquals(rules.startWalker(Ready(bare), ActionRef.PeekSiteRelics, p1)
      .left.toOption, Some(OathViolation.MinorActionUnavailable(
        "the pawn's site has no relics")))

  test("Reveal Owned Relic asks which facedown relic, then reveals it"):
    val twoRelics = minorBoard.relic(p1, dowsingSticks, facedown = true).ready
    val asked = accepted(rules.startWalker(Ready(twoRelics),
      ActionRef.RevealOwnedRelic, p1))
    parked.assertParked(asked.state, ActionRef.RevealOwnedRelic,
      MinorActionProcedures.relicDecisionId, p1)
    assertEquals(parkedDecisionQuery(asked.state),
      DecisionQuery.ChooseOne(Vector(cursedCauldron, dowsingSticks).map(id =>
        DecisionOption.Relic(DecisionOptionRef.Relic(id))),
        heading = Some("Choose a relic to reveal")))
    val revealed = accepted(rules.resolveWalker(asked.state, p1,
      MinorActionProcedures.relicDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Relic(cursedCauldron))))
    assertEquals(Look(revealed.state).player(p1).relics
      .map(relic => relic.id -> relic.orientation), Vector(
        cursedCauldron -> Orientation.FaceUp,
        dowsingSticks -> Orientation.FaceDown))
    parked.assertResumed(revealed.state, Phase.Act, p1)
    assertEquals(replayedFrom(twoRelics, asked.events ++ revealed.events),
      Right(revealed.state))

  test("Reveal Owned Relic refuses when no relic is facedown"):
    val faceUp = minorBoard.relic(p1, cursedCauldron, facedown = false).state
    assertEquals(rules.startWalker(faceUp, ActionRef.RevealOwnedRelic, p1)
      .left.toOption, Some(OathViolation.MinorActionUnavailable(
        "no facedown relic to reveal")))

  test("Move Warbands asks the way, then how many, in both directions"):
    val exiles = ForceKind.Exile(Look(base).player(p1).lineage)
    val asked = accepted(rules.startWalker(Ready(base),
      ActionRef.MoveWarbands, p1))
    parked.assertParked(asked.state, ActionRef.MoveWarbands,
      MinorActionProcedures.directionDecisionId, p1)
    assertEquals(parkedDecisionQuery(asked.state), DecisionQuery.ChooseOne(
      Vector(wayButton(toSite, "To your site"),
        wayButton(toBoard, "To your board")),
      heading = Some("Move warbands")))

    // To the site: up to all 4 on the board.
    val towardSite = accepted(rules.resolveWalker(asked.state, p1,
      MinorActionProcedures.directionDecisionId, ChooseOneAnswer(toSite)))
    assertEquals(parkedDecisionQuery(towardSite.state),
      DecisionQuery.ChooseAmount(1, 4,
        Some("Move how many warbands to your site?"), "Move"))
    val atSite = accepted(rules.resolveWalker(towardSite.state, p1,
      MinorActionProcedures.amountDecisionId, ChooseAmountAnswer(2)))
    assertEquals(warbandCounts(atSite.state),
      (2, SiteForces.Occupied(exiles, 5)))
    assertEquals(replayedFrom(base,
      asked.events ++ towardSite.events ++ atSite.events),
      Right(atSite.state))

    // To the board: one warband must stay, so up to 2 of the 3.
    val towardBoard = accepted(rules.resolveWalker(asked.state, p1,
      MinorActionProcedures.directionDecisionId, ChooseOneAnswer(toBoard)))
    assertEquals(parkedDecisionQuery(towardBoard.state),
      DecisionQuery.ChooseAmount(1, 2,
        Some("Move how many warbands to your board?"), "Move"))
    val onBoard = accepted(rules.resolveWalker(towardBoard.state, p1,
      MinorActionProcedures.amountDecisionId, ChooseAmountAnswer(2)))
    assertEquals(warbandCounts(onBoard.state),
      (6, SiteForces.Occupied(exiles, 1)))

  test("Move Warbands shows a way it cannot take with its reason"):
    // No warbands on the board: only the way back is open.
    val emptyBoard = minorBoard.boardWarbands(p1, 0).state
    val asked = accepted(rules.startWalker(emptyBoard,
      ActionRef.MoveWarbands, p1))
    assertEquals(parkedDecisionQuery(asked.state), DecisionQuery.ChooseOne(
      Vector(blockedWay(toSite, "To your site", "No warbands on your board"),
        wayButton(toBoard, "To your board")),
      heading = Some("Move warbands")))
    assert(rules.resolveWalker(asked.state, p1,
      MinorActionProcedures.directionDecisionId,
      ChooseOneAnswer(toSite)).isLeft)

  test("Vow of Wandering blocks the way to the site with its reason, and " +
      "leaves the way back open"):
    val holding = minorBoard.adviser(p1,
      CatalogNames.denizen("Vow of Wandering")).state
    val withPowers = PowerRules.withWalkerPowers
    val asked = accepted(withPowers.startWalker(holding,
      ActionRef.MoveWarbands, p1))
    assertEquals(parkedDecisionQuery(asked.state,
      WalkerPowerCatalog.default(catalog)), DecisionQuery.ChooseOne(
      Vector(blockedWay(toSite, "To your site", VowOfWandering.Reason),
        wayButton(toBoard, "To your board")),
      heading = Some("Move warbands")))
    val towardBoard = accepted(withPowers.resolveWalker(asked.state, p1,
      MinorActionProcedures.directionDecisionId, ChooseOneAnswer(toBoard)))
    assert(withPowers.resolveWalker(towardBoard.state, p1,
      MinorActionProcedures.amountDecisionId, ChooseAmountAnswer(2)).isRight)

  test("Move Warbands refuses to start when neither way is open"):
    // Nothing on the board, and only one warband at the site.
    val stuck = minorBoard.boardWarbands(p1, 0)
      .warbandsAt(brokenPeaks, p1, 1).state
    assertEquals(rules.startWalker(stuck, ActionRef.MoveWarbands, p1)
      .left.toOption, Some(OathViolation.MinorActionUnavailable(
        "No warbands on your board; One warband must stay at your site")))

  test("a minor action refuses what a global restriction refuses"):
    // Stub of OperationRestriction: refuses every operation.
    val refuseAllStub = new OperationRestriction:
      override def reason(state: ReadyGame, operation: CoreOperation)
          : Option[OperationReason] = Some(OperationReason("refused",
        "every operation is refused", OperationReasonKind.Impossible))
    val refusing = new OathRules(catalog, walkerPowerCatalog = WalkerPowers(
      Vector(PowerStubs.OperationRestrictionStub(PowerId("test.refuse-all"),
        refuseAllStub))))
    assertEquals(refusing.startWalker(Ready(base), ActionRef.PeekSiteRelics,
      p1).left.toOption, Some(OathViolation.CoreOperationRejected("refused",
        "every operation is refused")))
```

If `Table.relic` places relics in a different order than added, read `Look(...).player(p1).relics` and order both expected vectors the same way the table holds them; do not sort in the procedure.

In `src/test/scala/oathdigital/model/ProcedureRefSuite.scala`, extend the hand list of `ActionRef.all` keys:

```scala
      Vector("search", "play-facedown-adviser", "recover", "forge",
        "travel", "take-wealth", "muster", "trade", "challenge",
        "place-banner-resource", "negotiation", "campaign",
        "peek-site-relics", "reveal-owned-relic", "move-warbands"))
```

- [ ] **Step 4: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.MinorActionsSuite oathdigital.model.ProcedureRefSuite"`
Expected: compile errors: `PeekSiteRelics` is not a member of `ActionRef`, and `MinorActionProcedures` is not found.

- [ ] **Step 5: The procedure references**

In `src/main/scala/oathdigital/model/ProcedureRef.scala`, after `case object Campaign extends ActionRef { val key = "campaign" }`, add:

```scala
  /** The three minor actions that were legacy commands (walker-driven game
    * loop design, slice 3). */
  case object PeekSiteRelics extends ActionRef:
    val key = "peek-site-relics"
  case object RevealOwnedRelic extends ActionRef:
    val key = "reveal-owned-relic"
  case object MoveWarbands extends ActionRef { val key = "move-warbands" }
```

Append them to `ActionRef.all`:

```scala
  val all: Vector[ActionRef] = Vector(Search, PlayFacedownAdviser,
    Recover, Forge, Travel, TakeWealth, Muster, Trade, Challenge,
    PlaceBannerResource, Negotiation, Campaign, PeekSiteRelics,
    RevealOwnedRelic, MoveWarbands)
```

- [ ] **Step 6: The procedures**

Create `src/main/scala/oathdigital/gameplay/actions/minor/MinorActionProcedures.scala`:

```scala
package oathdigital.gameplay.actions.minor

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.OathLifecycle
import oathdigital.gameplay.powers.economy.VowOfWandering
import oathdigital.model._
import oathdigital.model.OathViolation.{InvalidEventOrder,
  MinorActionUnavailable, PawnSiteMissing}

/** The three minor actions that were legacy commands (walker-driven game
  * loop design, "The minor actions"): Peek Site Relics, Reveal Owned Relic
  * and Move Warbands. Their rules are the legacy handler's, moved into
  * walker trees.
  *
  * A start checks the Act phase and the first-game rules gate, as the
  * handler did, and takes no start selection: what the player picks is
  * asked inside the walk. Every operation is required, so a restriction
  * that refuses one refuses the action with its own reason. A resume
  * rebuilds the same tree without the start's gates.
  */
object MinorActionProcedures:
  type Build = (ExecutableCatalog, ReadyGame, PlayerId,
    Vector[DecisionOptionRef]) => Either[OathViolation, Operation]

  val relicDecisionId: String = "minor.reveal-relic"
  val directionDecisionId: String = "minor.warbands-direction"
  val amountDecisionId: String = "minor.warbands-amount"
  val toSite: DecisionOptionRef.Button = DecisionOptionRef.Button("to-site")
  val toBoard: DecisionOptionRef.Button = DecisionOptionRef.Button("to-board")

  val buildPeek: Build = (_, ready, actor, args) =>
    for
      _ <- started(ready, actor, args, ActionRef.PeekSiteRelics)
      site <- pawnSite(ready, actor)
      _ <- Either.cond(site.relics.nonEmpty, (),
        MinorActionUnavailable("the pawn's site has no relics"))
    yield peekTree(actor)

  val rebuildPeek: Build = (_, _, actor, _) => Right(peekTree(actor))

  val buildReveal: Build = (_, ready, actor, args) =>
    for
      _ <- started(ready, actor, args, ActionRef.RevealOwnedRelic)
      _ <- Either.cond(facedownRelics(ready, actor).nonEmpty, (),
        MinorActionUnavailable("no facedown relic to reveal"))
    yield revealTree(ready, actor)

  val rebuildReveal: Build = (_, ready, actor, _) =>
    Right(revealTree(ready, actor))

  val buildMove: Build = (_, ready, actor, args) =>
    for
      _ <- started(ready, actor, args, ActionRef.MoveWarbands)
      _ <- pawnSite(ready, actor)
      blocked = Vector(toSite, toBoard).flatMap(way =>
        most(ready, actor, way).left.toOption)
      _ <- Either.cond(blocked.size < 2, (),
        MinorActionUnavailable(blocked.mkString("; ")))
    yield moveTree(ready, actor)

  val rebuildMove: Build = (_, ready, actor, _) =>
    Right(moveTree(ready, actor))

  /** Every relic at the pawn's site, peeked at. */
  private def peekTree(actor: PlayerId): Operation =
    BuildOps((live, _) => Right(sitedPawn(live, actor).toVector.flatMap {
      (siteId, site) => site.relics.map(relic =>
        Peek(actor, relic.id, Location.Site(siteId)))
    }), required = true)

  /** The relic to reveal, then its reveal. */
  private def revealTree(ready: ReadyGame, actor: PlayerId): Operation =
    Sequence(Vector(
      Decide(relicDecisionId, actor, DecisionQuery.ChooseOne(
        facedownRelics(ready, actor).map(id =>
          DecisionOption.Relic(DecisionOptionRef.Relic(id))),
        heading = Some("Choose a relic to reveal")),
        presentation = DecisionPresentation.Panel),
      BuildOps((_, pending) => Right(pending.answered.collectFirst {
        case Answered(`relicDecisionId`, DecisionAnswer.ChooseOneAnswer(
            DecisionOptionRef.Relic(id)), _) =>
          Reveal(id, Location.PlayArea(actor))
      }.toVector), required = true)))

  /** The way, then how many, then the move. */
  private def moveTree(ready: ReadyGame, actor: PlayerId): Operation =
    Sequence(Vector(
      Decide(directionDecisionId, actor, DecisionQuery.ChooseOne(
        Vector(way(ready, actor, toSite, "To your site"),
          way(ready, actor, toBoard, "To your board")),
        heading = Some("Move warbands")),
        presentation = DecisionPresentation.Panel),
      Branch((live, pending) => chosenWay(pending.answered).toVector
        .flatMap(picked => most(live, actor, picked).toOption.toVector
          .map(max => Decide(amountDecisionId, actor,
            DecisionQuery.ChooseAmount(1, max, Some(
              if picked == toSite then "Move how many warbands to your site?"
              else "Move how many warbands to your board?"), "Move"))))),
      BuildOps((live, pending) => Right((for
        picked <- chosenWay(pending.answered)
        amount <- pending.answered.collectFirst {
          case Answered(`amountDecisionId`,
              DecisionAnswer.ChooseAmountAnswer(value), _) => value
        }
        operation <- move(live, actor, picked, amount)
      yield operation).toVector), required = true)))

  private def way(ready: ReadyGame, actor: PlayerId,
      ref: DecisionOptionRef.Button, label: String): DecisionOption =
    val option = DecisionOption.Button(ref, label)
    most(ready, actor, ref).fold(reason => DecisionOption.Unavailable(option,
      OptionReason.Written(reason)), _ => option)

  private def chosenWay(answered: Vector[Answered])
      : Option[DecisionOptionRef.Button] = answered.collectFirst {
    case Answered(`directionDecisionId`,
        DecisionAnswer.ChooseOneAnswer(picked), _)
        if picked == toSite || picked == toBoard => picked
  }.collect { case button: DecisionOptionRef.Button => button }

  /** The most warbands that may move `way`, or why none may. To the site,
    * the actor must rule it, hold warbands on the board and not be bound by
    * Vow of Wandering; to the board, one of their warbands must stay. */
  private def most(ready: ReadyGame, actor: PlayerId,
      way: DecisionOptionRef.Button): Either[String, Int] =
    val current = ready.game.current
    current.players.find(_.player == actor).zip(sitedPawn(ready, actor)) match
      case None => Left("Your pawn is not at a site")
      case Some((player, (_, site))) =>
        if way == toSite then
          if !SiteRule.ruledBy(site.forces, current.players, actor)
              .getOrElse(false) then Left("You do not rule your site")
          else if player.board.warbands == 0 then
            Left("No warbands on your board")
          else if VowOfWandering.holds(ready, actor) then
            Left(VowOfWandering.Reason)
          else Right(player.board.warbands)
        else site.forces match
          case SiteForces.Occupied(ForceKind.Exile(lineage), count)
              if lineage == player.lineage =>
            if count > 1 then Right(count - 1)
            else Left("One warband must stay at your site")
          case _ => Left("None of your warbands are at your site")

  private def move(ready: ReadyGame, actor: PlayerId,
      way: DecisionOptionRef.Button, amount: Int): Option[CoreOperation] =
    for
      player <- ready.game.current.players.find(_.player == actor)
      siteId <- player.pawnSite
    yield
      val board = PositionedLocation(Location.PlayArea(actor))
      val site = PositionedLocation(Location.Site(siteId))
      Move(Piece.Warbands(ForceKind.Exile(player.lineage), amount),
        if way == toSite then board else site,
        if way == toSite then site else board)

  private def facedownRelics(ready: ReadyGame, actor: PlayerId)
      : Vector[RelicId] =
    ready.game.current.players.find(_.player == actor).toVector
      .flatMap(_.relics.filter(_.orientation == Orientation.FaceDown)
        .map(_.id))

  /** The actor's pawn site and its state, when the pawn stands on one. */
  private def sitedPawn(ready: ReadyGame, actor: PlayerId)
      : Option[(SiteId, SiteState)] =
    ready.game.current.players.find(_.player == actor)
      .flatMap(_.pawnSite).flatMap(id =>
        ready.game.current.map.sites.get(id).map(id -> _))

  private def pawnSite(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, SiteState] =
    ready.game.current.players.find(_.player == actor)
      .flatMap(_.pawnSite).toRight(PawnSiteMissing(actor))
      .flatMap(_ => sitedPawn(ready, actor).map(_._2).toRight(
        MinorActionUnavailable("pawn site is not in play")))

  /** The legacy handler's gates: no start selection, the Act phase, and
    * the first-game rules. */
  private def started(ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef], action: ActionRef)
      : Either[OathViolation, Unit] =
    for
      _ <- Either.cond(args.isEmpty, (), InvalidEventOrder(
        s"walker procedure ${action.key} takes no start selection, got " +
          args.map(_.kind).mkString(", ")))
      _ <- OathLifecycle.validateAct(OathState.Ready(ready), actor)
      _ <- Either.cond(supported(ready), (), MinorActionUnavailable(
        "minor actions are limited to fixed unaltered all-Exile " +
          "first-game rules"))
    yield ()

  private def supported(ready: ReadyGame): Boolean =
    ready.setup.foundationProfile ==
      FirstGameFoundationProfile.FixedUnaltered &&
      ready.game.campaign.lineages.values.forall(_.role == Role.Exile) &&
      ready.game.campaign.foundations.values.forall(foundation =>
        foundation.face == FoundationFace.Normal &&
          foundation.alterationSources.isEmpty)
```

Notes:

- The type of a site's state may not be called `SiteState`. Use the type of `ready.game.current.map.sites` values.
- If `site.relics` holds `RelicId`s directly rather than states with `.id`, drop the `.id`. The legacy handler's `site.relics.map(_.id)` shows which.
- Check `MinorActions.validateAct` for the exact gate and copy it if it differs from `supported` above.

- [ ] **Step 7: Register them**

In `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`, import `oathdigital.gameplay.actions.minor.MinorActionProcedures`. After the `ActionRef.Campaign` entry, add:

```scala
    /** The legacy minor actions (walker-driven game loop design, slice 3).
      * They are actions, so the boundary follows each, but like the legacy
      * handler they take no modifiers and record no fallback diagnostics.
      */
    ActionRef.PeekSiteRelics -> Entry(
      fallbackKind = None,
      rollDecisionId = None,
      modifierWindow = None,
      build = MinorActionProcedures.buildPeek,
      rebuild = MinorActionProcedures.rebuildPeek),
    ActionRef.RevealOwnedRelic -> Entry(
      fallbackKind = None,
      rollDecisionId = None,
      modifierWindow = None,
      build = MinorActionProcedures.buildReveal,
      rebuild = MinorActionProcedures.rebuildReveal),
    ActionRef.MoveWarbands -> Entry(
      fallbackKind = None,
      rollDecisionId = None,
      modifierWindow = None,
      build = MinorActionProcedures.buildMove,
      rebuild = MinorActionProcedures.rebuildMove),
```

- [ ] **Step 8: The Game Log lines**

In `src/main/scala/oathdigital/application/gamelog/StartLines.scala`, in `name`, extend the no-tag arm:

```scala
      // No modifier window, so no tag.
      case ActionRef.TakeWealth | ActionRef.PlaceBannerResource |
          ActionRef.Negotiation | ActionRef.PeekSiteRelics |
          ActionRef.RevealOwnedRelic | ActionRef.MoveWarbands |
          _: ActionRef.UsePower => None
```

In `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`, in `lines`, before `case ActionRef.Negotiation =>`, add:

```scala
      // The minor actions post their legacy lines, now as action lines.
      case ActionRef.PeekSiteRelics =>
        val peeked = ops.collect {
          case OpStep(Peek(_, relic, Location.Site(site)), before, after) =>
            site -> words.card(relic, before, after, viewer)
        }
        peeked.headOption.toVector.map((site, _) => action(
          Vector(Text("Peeked at ")) ++ words.cards(peeked.map(_._2)) ++
            Vector(Text(" at "), words.site(site))))
      case ActionRef.RevealOwnedRelic => ops.collect {
        case OpStep(Reveal(relic, Location.PlayArea(owner)), before, after)
            if owner == actor =>
          action(Text("Revealed ") +:
            words.one(words.card(relic, before, after, viewer)))
      }
      case ActionRef.MoveWarbands => ops.collect {
        case OpStep(Move(Piece.Warbands(_, count),
            PositionedLocation(Location.PlayArea(_), _),
            PositionedLocation(Location.Site(site), _), _), _, _) =>
          action(Vector(Text(s"Moved $count " +
            s"${plural(count, "warband", "warbands")} to "), words.site(site)))
        case OpStep(Move(Piece.Warbands(_, count),
            PositionedLocation(Location.Site(site), _),
            PositionedLocation(Location.PlayArea(_), _), _), _, _) =>
          action(Vector(Text(s"Moved $count " +
            s"${plural(count, "warband", "warbands")} from "),
            words.site(site)))
      }
```

`words.card` returns the type `words.cards` takes; check `LogWords` and adjust the names if they differ.

In `src/main/scala/oathdigital/application/gamelog/DetailLines.scala`, keep these ops from repeating as detail lines:

- the warbands-to-site `Move` arm's guard becomes `if run.procedure != ActionRef.Campaign && run.procedure != ActionRef.MoveWarbands`;
- the `Peek` arm's guard becomes `if run.procedure != ActionRef.Negotiation && run.procedure != ActionRef.PeekSiteRelics`;
- the `Reveal` arm gets the guard `if run.procedure != ActionRef.RevealOwnedRelic`.

- [ ] **Step 9: Run the rules tests**

Run: `./sbtw "testOnly oathdigital.gameplay.MinorActionsSuite oathdigital.model.ProcedureRefSuite oathdigital.gameplay.walker.WalkerProcedureRegistrySuite"`
Expected: PASS.

- [ ] **Step 10: The Game Log scripts and tests**

In `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`, import `oathdigital.gameplay.actions.minor.MinorActionProcedures`. After `revealRelic`, add:

```scala
  /** p1 peeks at Broken Peaks, where Sticky Fire lies. p2 and p3 stand
    * elsewhere, so only p1 learns the relic. */
  def peekRelics(using munit.Location): Script =
    val (service, act) = atTable("peek-relics", Table.start
      .pawn(p1, at = "Broken Peaks").pawn(p2, at = "Ancient City")
      .pawn(p3, at = "Buried Giant").relicAt("Sticky Fire", "Broken Peaks")
      .banditsRefilled)
    act.after(GameCommand.StartWalker(ActionRef.PeekSiteRelics,
      StartPayload(p1)))
    Script("peek-relics", service, p1)

  /** p1 rules Broken Peaks with 3 warbands and holds 4 more. Two minor
    * actions: 2 warbands to the site, then 1 back to the board. */
  def warbandsMoved(using munit.Location): Script =
    val (service, act) = atTable("warbands-moved", Table.start
      .pawn(p1, at = "Broken Peaks").boardWarbands(p1, 4)
      .warbandsAt("Broken Peaks", p1, 3).banditsRefilled)
    def answer(decisionId: String, value: DecisionAnswer) =
      GameCommand.ResolveWalker(p1, TreeDecision(decisionId, value))
    def moved(from: Situation, way: DecisionOptionRef.Button,
        amount: Int): Situation =
      from.parkedAfter(GameCommand.StartWalker(ActionRef.MoveWarbands,
          StartPayload(p1)))
        .parkedAfter(answer(MinorActionProcedures.directionDecisionId,
          ChooseOneAnswer(way)))
        .after(answer(MinorActionProcedures.amountDecisionId,
          ChooseAmountAnswer(amount)))
    moved(moved(act, MinorActionProcedures.toSite, 2),
      MinorActionProcedures.toBoard, 1)
    Script("warbands-moved", service, p1)
```

Import `ChooseOneAnswer` and `Situation` if the file does not yet. Add both scripts to `named`, after `"reveal-relic"`:

```scala
    "peek-relics" -> (() => peekRelics),
    "warbands-moved" -> (() => warbandsMoved),
```

In `src/test/scala/oathdigital/application/gamelog/GameLogEventSuite.scala`, add:

```scala
  test("Peek Site Relics names the relics for the peeker only"):
    val script = peekRelics
    val site = CatalogNames.site("Broken Peaks")
    val mine = entryStarting(format(script, Some(script.actor)), "Peeked at ")
    assertEquals(mine.kind, LogKind.Action)
    assert(mine.spans.exists(_.isInstanceOf[LogSpan.Card]), mine.spans)
    assertEquals(mine.section.map(_.action), Some("peek-site-relics"))
    // p3 stands at Buried Giant, so p3 reads only that a relic was peeked
    // at. The peeker is the active player, so the subject is left out.
    val theirs = entryStarting(format(script, Some(p3)), "Peeked at ")
    assertEquals(text(theirs),
      s"Peeked at a Relic at ${presentation.siteLabel(site)}")

  test("each Move Warbands posts one line, in a section of its own"):
    val peaks = presentation.siteLabel(CatalogNames.site("Broken Peaks"))
    val moves = format(warbandsMoved, None)
      .filter(entry => text(entry).startsWith("Moved "))
    assertEquals(texts(moves), Vector(s"Moved 2 warbands to $peaks",
      s"Moved 1 warband from $peaks"))
    assert(moves.forall(_.kind == LogKind.Action), moves)
    assertEquals(moves.map(_.section.map(_.action)),
      Vector.fill(2)(Some("move-warbands")))
    assertEquals(moves.map(_.section).distinct.size, 2)
```

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS. If a new script fails a `GameLogPropertiesSuite` property, the failure names the property; fix the procedure or the line, not the property.

- [ ] **Step 11: Run everything**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS, with the new tests added to the baseline count.

- [ ] **Step 12: Commit**

```bash
git add docs/superpowers/specs/2026-10-09-walker-game-loop-design.md \
  src/main/scala/oathdigital/model/ProcedureRef.scala \
  src/main/scala/oathdigital/gameplay/actions/minor/MinorActionProcedures.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala \
  src/main/scala/oathdigital/application/gamelog/ActionLines.scala \
  src/main/scala/oathdigital/application/gamelog/StartLines.scala \
  src/main/scala/oathdigital/application/gamelog/DetailLines.scala \
  src/test/scala/oathdigital/gameplay/MinorActionsSuite.scala \
  src/test/scala/oathdigital/model/ProcedureRefSuite.scala \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogEventSuite.scala
git status --short
git commit -m "feat(walker): the three legacy minor actions as walker procedures"
```

---

### Task 2: The legacy minor actions go

**Files:**
- Delete: `src/main/scala/oathdigital/gameplay/actions/MinorActions.scala`, `src/main/scala/oathdigital/gameplay/actions/MinorActionOperationPolicy.scala`, `src/main/scala/oathdigital/serialization/ActionEventCodec.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala`, `OathDriver.scala`, `OathRulesWalker.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/VowOfWandering.scala` (doc)
- Modify: `src/main/scala/oathdigital/model/GameEventProtocol.scala`, `GameViolation.scala`
- Modify: `src/main/scala/oathdigital/serialization/GameEventWire.scala`
- Modify: `src/main/scala/oathdigital/application/GameCommands.scala`, `Authorization.scala`, `GameIntentMapper.scala`, `GameApplicationService.scala`, `UndoBarrier.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/EventLines.scala`, `GameLogFormatter.scala`, `LogSections.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala`, `CommandIntentCodec.scala`, `CommandIntentDecoders.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala`
- Modify tests: listed in Steps 1, 5 and 6

**Interfaces:**
- Consumes: Task 1's `ActionRef.PeekSiteRelics`, `RevealOwnedRelic`, `MoveWarbands` and `MinorActionProcedures`.
- Produces: `OathDriver.next(transition: OathTransition, finished: ProcedureRef)`. The `Ended` enum is gone.

- [ ] **Step 1: Write the failing frontend test**

In `frontend/src/test/scala/oathdigital/frontend/ActionOptionRowSuite.scala`:

- Give `panel` a `controls` parameter and use it:

  ```scala
  private def panel(powers: Vector[PhasePowerState] = Vector.empty,
      legalControls: Vector[String] = Vector("beginRecover",
        "peekSiteRelics", "moveWarbands"),
      controls: RecordingControls = new RecordingControls()): dom.Element =
  ```

  and pass `controls` where it passed `new RecordingControls()`.
- Its doc becomes: "The Act pane for Red. Unless `legalControls` says otherwise she may Recover (the one major row), peek at the relics at her site and move warbands (the minor rows)."
- The suite's doc loses the sentence "A warband move is one option, so its count and its button share a row."
- In "each action option sits on its own row", the minor rows become `Vector("Peek at relics at your site", "Move warbands")`.
- In "a power's action is listed as a minor action, not beside End Act", the `legalControls` it passes keeps `"beginRecover"` and the power's control; nothing else changes.
- Replace "a warband move keeps its count and its button on one row" with:

  ```scala
  test("a minor action's button starts its procedure, which asks the rest"):
    val controls = new RecordingControls()
    val acting = panel(controls = controls)
    acting.querySelector(".minor-move-warband")
      .asInstanceOf[dom.html.Button].click()
    assertEquals(controls.submitted,
      Vector(GameCommand.StartWalker("move-warbands")))
  ```

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ActionOptionRowSuite"`
Expected: FAIL: the minor rows still show the warband count rows, and the click submits `MoveWarbands`.

- [ ] **Step 2: The frontend buttons start the procedures**

In `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala`, inside `value.minorActions.foreach { minor => ... }`, delete the `if minor.canPeekSiteRelics then` block, the `minor.facedownRelics.foreach` block and the `Vector(true -> minor.maxBoardToSite, ...)` block. Keep the facedown-adviser button. After the `value.minorActions.foreach` block's closing brace, add:

```scala
     // The legacy minor actions are walker procedures (walker-driven game
     // loop design, slice 3): a button starts one, and the walk asks the
     // rest. Slice 4's Act menu replaces these buttons.
     Vector(
       ("peekSiteRelics", "Peek at relics at your site", "minor-peek-relics",
         "peek-site-relics"),
       ("revealOwnedRelic", "Reveal a relic", "minor-reveal-relic",
         "reveal-owned-relic"),
       ("moveWarbands", "Move warbands", "minor-move-warband",
         "move-warbands"))
       .filter((control, _, _, _) => value.legalControls.contains(control))
       .foreach { (_, label, style, action) =>
         val start = button(label, style)
         start.disabled = !canControl
         start.onclick = _ => submitCommand(GameCommand.StartWalker(action))
         groups.appendKind(action, start)
       }
```

`MinorActionsProjection` keeps its fields; slice 4 deletes it with the panel.

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ActionOptionRowSuite"`
Expected: PASS.

- [ ] **Step 3: Delete the commands and intents**

- `shared/.../CommandIntents.scala`: delete `PeekSiteRelics`, `RevealOwnedRelic` and `MoveWarbands`.
- `shared/.../CommandIntentCodec.scala`: delete their three `tagged` arms.
- `shared/.../CommandIntentDecoders.scala`: delete the `"peekSiteRelics"`, `"revealOwnedRelic"` and `"moveWarbands"` cases.
- `application/GameCommands.scala`: delete the three case classes and their `actor` arms.
- `application/Authorization.scala`: delete `peekSiteRelics`, `revealOwnedRelic` and `moveWarbands`.
- `application/GameIntentMapper.scala`: delete the three `Intent` cases.
- `application/GameApplicationService.scala`: delete the three command arms and the `MinorActionCommand` import.

- [ ] **Step 4: Delete the legacy handler, its events and its log arms**

- Delete `gameplay/actions/MinorActions.scala` and `gameplay/actions/MinorActionOperationPolicy.scala`.
- `gameplay/OathRules.scala`:
  - delete `handle(state, command: MinorActionCommand)` and its import;
  - delete the three `MinorActions.evolve` arms in `evolve`;
  - `outsideActions`' doc ends "State-based effects run under them." in place of "Minor actions and state-based effects run under them."
- `gameplay/OathDriver.scala`: delete the `Ended` enum. `next` takes the finished procedure:

  ```scala
  private[gameplay] def next(transition: OathTransition,
      finished: ProcedureRef): Either[OathViolation, OathTransition] =
  ```

  - the boundary arm becomes `case Ready(ready) if ready.game.current.boundaryDue => boundary(transition, finished)`;
  - `boundary`, `oathkeeper` and `turnStart` take `finished: ProcedureRef`;
  - `turnStart` matches `case PhaseTransitionRef.FinishRest | TriggeredProcedureRef.Setup =>`.

  Then run `grep -rn "Ended" src/main/scala src/test/scala | grep -v "RoundEnded\|GameEnded"`. Replace each remaining `Ended.Walk(x)` with `x`, and each `driven(ended: Ended)` parameter with `finished: ProcedureRef`. `OathRulesWalker` (`next(_, Ended.Walk(procedure))`), `FollowUpPlay` and `OathDriverSuite` have uses.
- `model/GameEventProtocol.scala`: delete `SiteRelicsPeeked`, `OwnedRelicRevealed` and `WarbandsMoved`.
- `model/GameViolation.scala`: delete `MinorActionOutcomeMismatch`. Keep `MinorActionUnavailable`; the new procedures and `CardPlay` use it.
- Delete `serialization/ActionEventCodec.scala`. In `serialization/GameEventWire.scala`:
  - drop `with ActionEventCodec`;
  - delete `SiteRelicsPeekedType`, `OwnedRelicRevealedType` and `WarbandsMovedType`;
  - drop `.orElse(actionDiscriminator)`, `.orElse(actionEncoder)` and `.orElse(actionDecode(eventType, payload, path))` from the three dispatch chains.
- `application/UndoBarrier.scala`: delete the `SiteRelicsPeeked | OwnedRelicRevealed` arm and its import. A peek and a reveal stay barriers through `HiddenOutcomes`.
- `application/gamelog/EventLines.scala`: delete the three minor-action arms. The class doc becomes: "The events that happen outside any walker run (spec, "Detail lines"): two state-based changes at Wake."
- `application/gamelog/GameLogFormatter.scala`: delete the minor-action arm ("A minor action, outside any walker run, is a section of its own.").
- `application/gamelog/LogSections.scala`: delete `Minor` and its doc.
- `gameplay/powers/economy/VowOfWandering.scala`: in the doc, "The minor action refuses with the restriction's reason." becomes "Move Warbands shows its way to the site unavailable with the restriction's reason."

- [ ] **Step 5: Convert the tests the deletions break**

- `gameplay/MinorActionsSuite`:
  - delete "site relic peek uses core operations and preserves knowledge", "owned relic reveal uses core operation and preserves replay", "owned relic reveal rejects unheld and already-faceup relics", "minor-action operation policy permits roots only in validated context", "warband moves use core operations in both directions and replay", "minor actions run under the global restrictions" and "Vow of Wandering refuses a move to the site with its reason, and allows one back to the board". Task 1's tests replace them;
  - remove the imports this leaves unused (`MinorActionCommand`, `MinorActionOperationPolicy`, `MinorActions`, `OathEvent._` if unused).
- `gameplay/OathDriverSuite`, "a minor action runs the boundary":

  ```scala
  test("a minor action runs the boundary"):
    val start = table.relicAt("Brass Horse", at = ancientCity).state
    val peeked = accepted(rules.startWalker(start, ActionRef.PeekSiteRelics,
      p1))
    assertEquals(boundaries(peeked.events), 1)
  ```

- `gameplay/PendingWalkerRulesSuite`: delete the `Vector("minor action" -> ...)` block and its import. The test name becomes `s"no walker start runs over a parked $name"`. `StartableRef.all` now holds the three minor actions, so the loop above covers them.
- `application/PendingWalkerInvariantSuite`: delete the three minor commands from `everyCommand`.
- `application/MembershipAuthorizationServiceSuite`: delete the `revealOwnedRelic` assertion. If no assertion of a seat's command is left, assert `actor.beginRest` is `GameCommand.BeginRest(PlayerId("p1"))`.
- `application/GameApplicationServiceReopenSuite`, "HSQL reopen preserves private minor-action relic knowledge": the command becomes `GameCommand.StartWalker(ActionRef.PeekSiteRelics, StartPayload(p2))`.
- `application/GameApplicationServiceRecoverSuite`: the command the walker park blocks becomes `GameCommand.BeginRest(actor)`; the failure message says "a start command should be blocked by walker park".
- `application/UndoBarrierPlaySuite`, "peeking at a site's relics cannot be taken back": `act.after(GameCommand.StartWalker(ActionRef.PeekSiteRelics, StartPayload(p1)))`.
- `application/UndoBarrierSuite`: delete "a legacy peek or reveal is a barrier", the `relic` value and the event import.
- `serialization/GameEventFactsWireSuite`, "minor actions preserve private identities ...": rename it "state-based events are written at format version 1" and use `Vector[OathEvent](OathEvent.BanditsRefilled(Vector(SiteId("site:a") -> 1)), OathEvent.UsurperFlipped(PlayerId("red")))`, with the stream name `"state-based"`.
- `server/AuthenticatedGameRoutesSuite`: delete `actorDerivedMinor` and `spoofedMinorActor` with their assertions. In the comment above, "may not Forge or peek yet" becomes "may not Forge yet".
- `testkit/SituationDriver`: delete the three minor-command arms and the `MinorActionCommand` import.
- `gamelog/LogScripts.revealRelic`: the reveal becomes a walker start. The relic choice is answered by default:

  ```scala
    recovered.after(GameCommand.StartWalker(ActionRef.RevealOwnedRelic,
      StartPayload(actor)))
  ```

  Its doc becomes "A successful Recover, then the recovered relic revealed with Reveal Owned Relic." Delete the `relic` value and its match if nothing else reads them.
- `gamelog/GameLogEventSuite`:
  - "a revealed relic is named for everyone": read the line with `entryStarting(format(script, viewer), "Revealed ")`, and its kind is `LogKind.Action`;
  - delete "a site peek names the relics for the peeker only" (Task 1's "Peek Site Relics names the relics for the peeker only" replaces it);
  - in "warbands moved, bandits returned and a new Usurper each post one line", delete the two `WarbandsMoved` cases and rename it "bandits returned and a new Usurper each post one line".
- `gamelog/GameLogSectionSuite`:
  - "a minor action is a section of its own, apart from the action around it": `val revealed = entryStarting(entries, "Revealed ")`, and `assertEquals(revealed.section.map(_.action), Some("reveal-owned-relic"))`;
  - "moved warbands are a minor section; refilled bandits and a new Usurper are in none": delete the `moved` case, keep the other two, and rename it "refilled bandits and a new Usurper are in no section".
- `shared/.../CommandProtocolSuite`: delete `PeekSiteRelics`, `RevealOwnedRelic("r1")` and `MoveWarbands(toSite = true, 2)` from `examples`, and add `StartWalker("move-warbands")`.
- `frontend/.../HttpGameClientSuite`: each `GameIntent.RevealOwnedRelic("relic:a")` becomes `GameIntent.StartWalker("reveal-owned-relic")`.

Then run `grep -rn "PeekSiteRelics\|RevealOwnedRelic\|MoveWarbands\|SiteRelicsPeeked\|OwnedRelicRevealed\|WarbandsMoved\|MinorActionCommand\|peekSiteRelics\|revealOwnedRelic\|moveWarbands" src shared/src frontend/src`. Expected: only `ActionRef.PeekSiteRelics`, `ActionRef.RevealOwnedRelic` and `ActionRef.MoveWarbands`, the legal-control strings in `LegalActionProjector`, `ActionDecisionRenderer` and their tests, and `GameApplicationServiceProjectionSuite`'s control list.

- [ ] **Step 6: Run everything**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS. Compare the counts with Task 1's: the deleted and renamed tests above are the only changes.

- [ ] **Step 7: Commit**

Stage every file this task changed or deleted by path (`git add <path>` for changes, `git rm <path>` for the three deleted files), then:

```bash
git status --short
git commit -m "refactor: the legacy minor-action commands and events go"
```

---

### Task 3: Docs, and the gates

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: The roadmap and the spec's status**

In `docs/ROADMAP.md`, under "### Phase - Walker-driven game loop", change "- [ ] Slice 3: the three legacy minor actions on the walker." to "- [x] Slice 3: the three legacy minor actions on the walker.", as slice 1's line is recorded.

In the spec, replace the status paragraph with:

```markdown
**Status:** designed 2026-10-09. Slices 1 to 3 have landed; slice 4 is
ready to plan.
```

- [ ] **Step 2: The gates**

Run, in order:

1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. Compare the test counts with the baseline and report every test added and deleted.

- [ ] **Step 3: Commit, and merge**

```bash
git add docs/ROADMAP.md \
  docs/superpowers/specs/2026-10-09-walker-game-loop-design.md
git status --short
git commit -m "docs: record walker game loop slice 3"
```

Then run `ExitWorktree` with `keep`. In the main checkout:
1. Check `git log --oneline -1`.
2. Merge the branch into local `main` (`git merge --ff-only <branch>`, or a merge commit when `main` moved).
3. Run gate 1 again on the merge.

Do not push.
