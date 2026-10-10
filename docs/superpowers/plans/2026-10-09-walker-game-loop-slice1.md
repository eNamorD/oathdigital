# Walker-Driven Game Loop, Slice 1 (The Queue and the Driver) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The walker keeps a queue of procedures. One driver, `OathDriver.next`, runs every finished walk's continuation: it starts the queue's head, or, when the queue is empty, it runs the action boundary, the turn-start hooks, and the automatic End Wake and Finish Rest. A follow-up becomes a queued offer that the player answers in a walker decision. The glue in `OathRules` and the follow-up commands, projection and panel go.

**Architecture:**

- **The queue.** `FollowUp` becomes `QueuedProcedure(actor, procedure, startArgs, source, kind)`. `followUps` becomes `queue`, and `walkerFollowUp` becomes `walkerEntry`. The op `QueueProcedure` appends an entry, or puts it in front. The event `ProcedureDequeued` takes the head and holds it in `walkerEntry` for the walk it starts.
- **The driver.** A new trait, `OathDriver`, mixed into `OathRules`. `walkerTransition` hands every finished walk to `next`. A flag, `boundaryDue`, set when an action's walk completes and cleared by a new event, `ActionBoundaryRan`, makes the boundary wait until the queue is empty.
- **The offer.** A new triggered procedure, `FollowUpOffer`, asks the entry's actor: the action, or Skip. The action's option is priced by `QueuedStart.refusal`, which runs the same checks the driver's start runs. Taking it puts a `Run` entry in front of the queue; the driver starts it in the same command.
- **Deletions.** `completeAction`, `oathkeeperStep`, `turnBoundary`, `enterWake`, `forcedWake`, `autoEndWake`, `autoFinishRest`, `startFollowUp` and `skipFollowUp`. The service's follow-up gate, `FollowUpPending`, the `SkipFollowUp` command and intent, `FollowUpProjection`, the projector's follow-up controls and `FollowUpControls`.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md` before starting. Task 1 writes this plan's rulings into it. Also read `docs/superpowers/specs/2026-10-08-follow-up-actions-design.md` and `docs/architecture/follow-up-actions.md`: they describe the mechanism this plan replaces.

**Starts from:** `main`. Operation rewrite slice 2 (`docs/superpowers/plans/2026-10-09-operation-rewrite-slice2.md`) may be running at the same time. It rewrites `FollowUpSupplyWaiver.scala` and `SupplyStrip.scala`. Check `git log --oneline main -- src/main/scala/oathdigital/gameplay/walker/FollowUpSupplyWaiver.scala` before Task 1 and again before merging. Where both changed that file, keep slice 2's body and apply this plan's renames to it.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`.
  - An unused import or private member fails the build. Remove every import a deletion leaves unused.
  - A non-exhaustive match fails the build. A new enum case needs an arm in every match over its enum.
  - A refutable pattern in a `val` also fails it, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker or `gameplay/operations` sources.
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
- **No compatibility** (spec ruling 14). Stored journals with the old events stop replaying. Do not bump the journal version.
- **Changed tests.** This slice changes behavior, so existing tests change. Each task names the expected changes. When a test fails for a reason the task does not name, stop and report it.
- **Texts.** Docs and commit messages use plain English. Game text (headings, labels, log lines) is given in the steps; use it exactly.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end. Report every test added and deleted.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

Task 1 writes these into the spec, under a new heading "Plan-time rulings (slice 1)" after "Slices".

1. **An offer's pick goes to the front.** `QueueProcedure(entry, atFront)` appends by default. The follow-up offer's pick puts its `Run` entry in front. Appending would put Second Wind's Travel behind its own queued Campaign offer.
2. **The driver starts a phase-ending procedure directly.** When End Wake or Finish Rest is the only option left (ruling 3), the driver starts it without queueing it. An entry queued and dequeued in the same driver step would only add a `ProcedureDequeued` event.
3. **`ActionBoundaryRan` clears `boundaryDue`.** The boundary may write nothing else: no bandits to refill, no title change, no ignored rule. A marker event keeps replay exact. It writes no Game Log line.
4. **The offer's option runs the start's own checks.** `QueuedStart.refusal` builds the entry's tree on the state its start would see, then runs the restriction check, the first walk and the playable-option gate, as `startNow` does. An available option is a start the driver accepts. This is exact for the start. The look-ahead through the modifier decision stays slice 4's.
5. **Skip is an answer.** The offer's Skip ends its walk. `walkerTransition` writes `FollowUpSkipped(entry)` before `WalkerCompleted`, for the Game Log line. Replay checks that it names `walkerEntry` and changes nothing.
6. **Recover gets a cost window**, `PowerWindow.RecoverCost`, around the Supply it pays on each roll. The follow-up waiver reaches it.
7. **The automatic Finish Rest follows any walk.** Until now it followed only Begin Rest. Ruling 3 applies it whenever the queue is empty in Rest and no REST power is usable, so Rest also ends after the last REST power is used, and after a queued action that ran in Rest.
8. **The turn-start hooks are keyed on the finished walk.** They run after a Finish Rest walk and after the Setup walk. Neither queues an entry, and neither leaves the boundary due, so nothing between the walk and the hooks can stop them.
9. **The follow-up client surface goes in this slice.** The offer is a walker decision, so the follow-up projection, controls, panel and Skip command have nothing left to show. They are deleted here, not in slice 5.

## What exists before this plan

Check each of these in the code before Task 1. If a name or signature differs, stop and report.

- `model/GameState.scala`: `final case class FollowUp(player: PlayerId, action: ActionRef, source: PowerSourceRef)`; `CurrentGameState.followUps: Vector[FollowUp]` and `walkerFollowUp: Option[FollowUp]`.
- `model/CoreOperations.scala`: `final case class QueueFollowUp(followUp: FollowUp) extends PrimitiveOperation`.
- `gameplay/walker/WalkerEvents.scala`: `FollowUpStarted(followUp: FollowUp)` and `FollowUpSkipped(followUp: FollowUp)`.
- `gameplay/OathRulesWalker.scala`: `startWalker`, `startNow`, `startFollowUp`, `skipFollowUp`, `startTriggered`, `walkerTransition`, `autoFinishRest`, `autoEndWake`, `runsActionBoundary`, `runsTurnBoundary`, `buildWalker`.
- `gameplay/OathRules.scala`: `completeAction`, `turnBoundary`, `restPowerUsable`, `wakeOptionOpen`, `oathkeeperStep`, `recordBoundaryFallback`, `withFallback`, `enterWake`, `forcedWake`, `appendEvaluation`, and `private lazy val outsideActions`.
- `model/ProcedureRef.scala`: `enum TriggeredProcedureRef` with `Oathkeeper`, `Setup`, `ForcedWake`, and `TriggeredProcedureRef.all`.
- `gameplay/walker/WalkerProcedureRegistry.scala`: `build` and `rebuild` take `(procedure, catalog, state, activePlayer, args, phasePowers, registrations)`; `lookup` special-cases `ActionRef.UsePower` and `TriggeredProcedureRef.ForcedWake`.

## File map

| File | Change | Task |
|---|---|---|
| `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md` | modify | 1, 6 |
| `src/main/scala/oathdigital/model/GameState.scala` | modify | 1, 3 |
| `src/main/scala/oathdigital/model/CoreOperations.scala` | modify | 1 |
| `src/main/scala/oathdigital/model/GameViolation.scala` | modify | 1, 4 |
| `src/main/scala/oathdigital/model/ProcedureRef.scala` | modify | 4 |
| `src/main/scala/oathdigital/model/PowerWindow.scala` | modify | 2 |
| `src/main/scala/oathdigital/gameplay/operations/{OperationApplication,TurnStateOperations}.scala` | modify | 1 |
| `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala` | modify | 1, 3 |
| `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala` | modify | 1, 3, 4 |
| `src/main/scala/oathdigital/gameplay/walker/FollowUpSupplyWaiver.scala` | modify | 1, 2 |
| `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` | modify | 4 |
| `src/main/scala/oathdigital/gameplay/walker/FollowUpOffer.scala` | create | 4 |
| `src/main/scala/oathdigital/gameplay/walker/QueuedStart.scala` | create | 4 |
| `src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala` | modify | 2 |
| `src/main/scala/oathdigital/gameplay/OathDriver.scala` | create | 3, 4 |
| `src/main/scala/oathdigital/gameplay/OathRules.scala` | modify | 1, 3 |
| `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` | modify | 1, 3, 4 |
| `src/main/scala/oathdigital/gameplay/OathLifecycle.scala` | modify | 1 |
| `src/main/scala/oathdigital/gameplay/powers/{economy/KnightsErrant,search/HuntingParty,campaign/SecondWind}.scala` | modify | 1 |
| `src/main/scala/oathdigital/serialization/{FollowUpWire,WalkerEventCodec,WalkerOperationCodec,GameEventWire}.scala` | modify | 1, 3 |
| `src/main/scala/oathdigital/application/GameApplicationService.scala` | modify | 1, 4 |
| `src/main/scala/oathdigital/application/{GameCommands,Authorization,GameIntentMapper}.scala` | modify | 4 |
| `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` | modify | 4 |
| `src/main/scala/oathdigital/application/{LegalActionProjector,PendingProjector,GameProjection,ScopedProjectionContext}.scala` | modify | 1, 5 |
| `src/main/scala/oathdigital/application/gamelog/{GameLogFormatter,StartLines,ActionLines}.scala` | modify | 1, 4 |
| `src/main/scala/oathdigital/server/GameRoutes.scala` | modify | 1, 4 |
| `shared/src/main/scala/oathdigital/protocol/{CommandIntents,CommandIntentCodec,CommandIntentDecoders}.scala` | modify | 4 |
| `shared/src/main/scala/oathdigital/protocol/projection/{GameProjectionDto,GameProjectionCodec}.scala` | modify | 5 |
| `frontend/src/main/scala/oathdigital/frontend/FollowUpControls.scala` | delete | 4 |
| `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala` | modify | 4, 5 |
| `src/test/scala/oathdigital/gameplay/OathDriverSuite.scala` | create | 3, 4 |
| `src/test/scala/oathdigital/gameplay/FollowUpPlay.scala` | create | 4 |
| `src/test/scala/oathdigital/testkit/{Table,ParkedNode,SituationDriver}.scala` | modify | 4 |
| follow-up test suites (listed per task) | modify or delete | 1-5 |
| `docs/superpowers/specs/2026-10-08-follow-up-actions-design.md` | modify | 6 |
| `docs/superpowers/specs/2026-09-12-walker-ownership-and-phases-design.md` | modify | 6 |
| `docs/superpowers/specs/2026-10-09-operation-rewrite-design.md` | modify | 6 |
| `docs/architecture/follow-up-actions.md` | modify | 6 |
| `docs/ROADMAP.md` | modify | 6 |

---

### Task 1: The rulings, and `QueuedProcedure` in place of `FollowUp`

A rename and retype. Behavior does not change: the queue still waits for `StartWalker` or `SkipFollowUp`, and `ProcedureDequeued` and `FollowUpSkipped` replay as `FollowUpStarted` and `FollowUpSkipped` did.

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md`
- Modify: `src/main/scala/oathdigital/model/GameState.scala`, `CoreOperations.scala`, `GameViolation.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala`, `TurnStateOperations.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala`, `WalkerReplay.scala`, `FollowUpSupplyWaiver.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala`, `OathRulesWalker.scala`, `OathLifecycle.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/KnightsErrant.scala`, `powers/search/HuntingParty.scala`, `powers/campaign/SecondWind.scala`
- Modify: `src/main/scala/oathdigital/serialization/FollowUpWire.scala`, `WalkerEventCodec.scala`, `WalkerOperationCodec.scala`, `GameEventWire.scala`
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala`, `LegalActionProjector.scala`, `PendingProjector.scala`, `GameProjection.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala`, `StartLines.scala`, `ActionLines.scala`
- Modify: `src/main/scala/oathdigital/server/GameRoutes.scala`
- Modify tests: `FollowUpRulesSuite`, `FollowUpSupplyWaiverSuite`, `FollowUpReplaySuite`, `KnightsErrantSuite`, `HuntingPartySuite`, `SecondWindSuite`, `FollowUpProjectionSuite`, `FollowUpServiceSuite`, `GameLogFollowUpSuite`, `LogScripts`, `WalkerDecisionWireSuite`, `WalkerOperationWireSuite`, `CommandRejectionMessageSuite`

**Interfaces:**
- Produces, in `oathdigital.model`:

  ```scala
  final case class QueuedProcedure(actor: PlayerId, procedure: StartableRef,
      startArgs: Vector[DecisionOptionRef], source: Option[PowerSourceRef],
      kind: QueueKind)
  enum QueueKind:
    case Run, Offer
  final case class QueueProcedure(entry: QueuedProcedure,
      atFront: Boolean = false) extends PrimitiveOperation
  ```

  `CurrentGameState.queue: Vector[QueuedProcedure]` and `walkerEntry: Option[QueuedProcedure]`. `OathViolation.FollowUpPending(entry: QueuedProcedure)` until Task 4 deletes it.
- Produces, in `oathdigital.gameplay.walker`: `ProcedureDequeued(entry: QueuedProcedure)` and `FollowUpSkipped(entry: QueuedProcedure)`, both `WalkerEvent`s.
- Produces, in `TurnStateOperations`: `def queueProcedure(ready: ReadyGame, entry: QueuedProcedure, atFront: Boolean): ReadyGame`.
- Produces, in `FollowUpSupplyWaiver`: `def costWindow(procedure: StartableRef): Option[PowerWindow]`.
- Wire: event type `walker.procedure-dequeued` with key `entry`; `walker.follow-up-skipped` with key `entry`; op kind `queue-procedure` with keys `entry` and `atFront`. An entry is `{"actorId", "procedure", "startArgs", "source"?, "kind"}`, `kind` being `"run"` or `"offer"`.

- [ ] **Step 1: Set up and record the baseline**

Follow Global Constraints, "Worktree". Run `./sbtw "test" "frontend/test"` and note both counts.

- [ ] **Step 2: Write the plan-time rulings into the spec**

In `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md`, add a section after "Slices" and before "Testing":

```markdown
## Plan-time rulings (slice 1)

1. **An offer's pick goes to the front.** `QueueProcedure(entry, atFront)`
   appends by default. The follow-up offer's pick puts its `Run` entry in
   front, or Second Wind's Travel would wait behind its own queued Campaign
   offer.
2. **The driver starts a phase-ending procedure directly.** When End Wake or
   Finish Rest is the only option left (ruling 3), the driver starts it
   without queueing it.
3. **`ActionBoundaryRan` clears `boundaryDue`.** The boundary may write
   nothing else, so a marker event keeps replay exact. It writes no Game Log
   line.
4. **The offer's option runs the start's own checks.** `QueuedStart.refusal`
   builds the entry's tree on the state its start would see, then runs the
   restriction check, the first walk and the playable-option gate, as a
   start does. The look-ahead through the modifier decision stays slice 4's.
5. **Skip is an answer.** The offer's Skip ends its walk, and
   `FollowUpSkipped(entry)` is written before `WalkerCompleted` for the Game
   Log line. Replay checks that it names `walkerEntry` and changes nothing.
6. **Recover gets a cost window**, `RecoverCost`, around the Supply it pays
   on each roll, so the follow-up waiver reaches it.
7. **The automatic Finish Rest follows any walk.** Rest also ends after the
   last REST power is used, and after a queued action that ran in Rest.
8. **The turn-start hooks are keyed on the finished walk**: Finish Rest and
   Setup. Neither queues an entry or leaves the boundary due.
9. **The follow-up client surface goes in slice 1.** The offer is a walker
   decision, so the follow-up projection, controls, panel and Skip command
   are deleted here, not in slice 5.
```

In the "The queue" section, replace the bullet that begins "The op `QueueFollowUp` becomes `QueueProcedure(entry)`. It appends" with:

```markdown
- The op `QueueFollowUp` becomes `QueueProcedure(entry, atFront)`. It
  appends unless `atFront` is set, so follow-up ruling 11 (new entries go
  to the back) holds; only an offer's pick goes in front.
```

- [ ] **Step 3: Replace the model types**

In `src/main/scala/oathdigital/model/GameState.scala`, replace the `FollowUp` class and its doc with:

```scala
/** A procedure the walker runs as its own walk once the current one ends
  * (walker-driven game loop design, "The queue"). `actor` owns the walk: the
  * one place an off-turn actor (Sneak Attack) will differ from the active
  * player. `startArgs` are what a start would carry. `source` is the power
  * that queued it, which the Game Log credits; an entry with a source is a
  * follow-up, whose walk spends no Supply. A `Run` starts at once; an
  * `Offer` asks its actor first, with Skip. */
final case class QueuedProcedure(actor: PlayerId, procedure: StartableRef,
    startArgs: Vector[DecisionOptionRef], source: Option[PowerSourceRef],
    kind: QueueKind)

enum QueueKind:
  case Run, Offer
```

In `CurrentGameState`, replace the two follow-up fields and their comments with:

```scala
    // Procedures to run once the current walk ends, in order (walker-driven
    // game loop design, "The queue"). Not walker scratch: it outlives walks
    // and phases, and only `ProcedureDequeued` takes from it.
    queue: Vector[QueuedProcedure] = Vector.empty,
    // The entry the current walk was started from. Walker scratch: replay
    // sets it from `ProcedureDequeued` and `WalkerCompleted` clears it, so
    // the cost waiver and the phase exemption hold on every resume.
    walkerEntry: Option[QueuedProcedure] = None,
```

In `src/main/scala/oathdigital/model/CoreOperations.scala`, replace `QueueFollowUp` with:

```scala
/** Puts `entry` on the queue (walker-driven game loop design, "The
  * queue"): at the back, or in front when `atFront`. A source power emits
  * it inside the origin action's tree. */
final case class QueueProcedure(entry: QueuedProcedure,
    atFront: Boolean = false) extends PrimitiveOperation
```

In `src/main/scala/oathdigital/model/GameViolation.scala`, change `FollowUpPending(followUp: FollowUp)` to `FollowUpPending(entry: QueuedProcedure)`.

- [ ] **Step 4: Apply the op**

In `TurnStateOperations.scala`, replace `queueFollowUp` with:

```scala
  def queueProcedure(ready: ReadyGame, entry: QueuedProcedure,
      atFront: Boolean): ReadyGame =
    ready.updateCurrent(current => current.copy(queue =
      if atFront then entry +: current.queue else current.queue :+ entry))
```

In `OperationApplication.scala`, replace the `QueueFollowUp` arm with:

```scala
      case (result, QueueProcedure(entry, atFront)) =>
        result.map(TurnStateOperations.queueProcedure(_, entry, atFront))
```

- [ ] **Step 5: Rename the events and their replay**

In `WalkerEvents.scala`, replace `FollowUpStarted` and `FollowUpSkipped` with:

```scala
/** The first event of a walk started from the queue's head: replay takes
  * the head and holds it as `walkerEntry` until `WalkerCompleted`. An event,
  * not a `WalkerParked` field, because a walk can finish in its starting
  * command without parking, and the cost waiver and the Game Log tag still
  * need the entry (walker-driven game loop design, "The queue"). */
final case class ProcedureDequeued(entry: QueuedProcedure) extends WalkerEvent

/** The entry's actor declined it, and the Game Log writes the Skip line. */
final case class FollowUpSkipped(entry: QueuedProcedure) extends WalkerEvent
```

Fix the file's `model` import: `FollowUp` out, `QueuedProcedure` in.

In `WalkerReplay.scala`:

- the `WalkerCompleted` arm clears `walkerEntry = None` in place of `walkerFollowUp = None`;
- the `FollowUpStarted(followUp)` arm becomes `ProcedureDequeued(entry)`, with `current.queue` in place of `current.followUps`, `walkerEntry` in place of `walkerFollowUp`, and the messages "dequeued entry does not match the queue head" and "an entry was dequeued while another walk is open";
- the `FollowUpSkipped` arm reads `current.queue` and keeps taking the head (Task 4 changes it).

In `OathRules.evolve`, replace the `FollowUpStarted` arm with `case event: ProcedureDequeued => ProcedureWalker.applyRecorded(state, event)` and fix the import.

- [ ] **Step 6: Rename the readers**

- `OathLifecycle.validateAct`: `current.walkerEntry.isEmpty` in place of `current.walkerFollowUp.isEmpty`; the comment says "its start has set `walkerEntry`".
- `OathRulesWalker`:
  - `startWalker` reads `ready.game.current.queue.headOption`;
  - `startFollowUp(state, head: QueuedProcedure, …)` compares `requester == head.actor` (`WrongPlayer(head.actor, requester)`) and `procedure == head.procedure`, and emits `ProcedureDequeued(head)`;
  - `skipFollowUp` reads `queue` and `head.actor`;
  - `autoFinishRest` and `autoEndWake` read `queue.isEmpty`.
- `FollowUpSupplyWaiver`: `costWindow` takes `procedure: StartableRef` (same arms, `case _ => None`). The `Transform` reads `walkerEntry` and strips for `entry.actor`. `applicable` becomes:

  ```scala
  override def applicable(ctx: PowerCtx): Boolean =
    ctx.state.game.current.walkerEntry.exists(entry =>
      entry.source.nonEmpty &&
        costWindow(entry.procedure).contains(ctx.window))
  ```

  Update its doc: "the action `walkerEntry` names", and "an entry with a source is a follow-up".
- `GameApplicationService`: the gate reads `queue.headOption` and `FollowUpPending(ready.game.current.queue.head)`. `answersFollowUp(head: QueuedProcedure, …)` compares `procedure == head.procedure && start.actor == head.actor` and `player == head.actor`.
- `GameRoutes`: its `FollowUpPending(followUp)` message reads the entry's `procedure.key` where it read `action.key`. Keep the wording.
- `LegalActionProjector`, `PendingProjector`, `GameProjection`: `queue` in place of `followUps`. In `followUpProjection`, apply `ProcedureDequeued(head)`, read `head.actor` and `head.procedure`, and label the source with `head.source.fold("")(sourceLabel(context.ready, _))`. `followUpStartable`, `actionLabel` and `followUpReason` take a `StartableRef`. Task 5 deletes all of it.
- Game Log:
  - `StartLines.queuedBy` collects `ProcedureDequeued(entry)` and credits `entry.source.toVector.flatMap(...)`.
  - `GameLogFormatter` matches `ProcedureDequeued` where it matched `FollowUpStarted`. Its `FollowUpSkipped(entry)` line uses `entry.source.toVector.flatMap(words.source(_, …))` and `starts.name(entry.procedure).getOrElse(entry.procedure.key)`.
  - `ActionLines` matches `QueueProcedure(_, _)` where it matched `QueueFollowUp(_)`.

- [ ] **Step 7: The powers queue offers**

Each of the three powers builds an `Offer` entry with no start arguments. Knights Errant:

```scala
    PowerWindow.MusterActionEligibility -> Vector(Transform((ctx, children) =>
      children :+ QueueProcedure(QueuedProcedure(ctx.activePlayer,
        ActionRef.Campaign, Vector.empty, Some(PowerSourceRef.Card(cardId)),
        QueueKind.Offer)))))
```

Hunting Party and Second Wind change the same way: `QueueProcedure(QueuedProcedure(actor, action, Vector.empty, Some(source), QueueKind.Offer))`, where they built `QueueFollowUp(FollowUp(actor, action, source))`.

- [ ] **Step 8: The wire**

In `FollowUpWire.scala`, replace `encodeFollowUp` and `decodeFollowUp` with:

```scala
  protected final def encodeQueued(entry: QueuedProcedure): ujson.Value =
    val fields = Vector[(String, ujson.Value)](
      "actorId" -> entry.actor.value,
      "procedure" -> encodeProcedure(entry.procedure),
      "startArgs" -> ujson.Arr.from(entry.startArgs.map(
        DecisionAnswerCodec.encodeRef)),
      "kind" -> (entry.kind match
        case QueueKind.Run => "run"
        case QueueKind.Offer => "offer")) ++
      entry.source.map(source => "source" -> encodePowerSource(source))
    ujson.Obj.from(fields)

  protected final def decodeQueued(value: ujson.Value,
      path: String): Either[WireError, QueuedProcedure] = for
    procedure <- decodeProcedure(value("procedure"), s"$path.procedure")
    startable <- procedure match
      case startable: StartableRef => Right(startable)
      case other => Left(InvalidValue(s"$path.procedure",
        s"a queued procedure is startable, not ${other.key}"))
    startArgs <- traverse(value("startArgs").arr.zipWithIndex.toVector):
      case (ref, index) =>
        DecisionAnswerCodec.decodeRef(ref, s"$path.startArgs[$index]")
    source <- value.obj.get("source") match
      case None => Right(None)
      case Some(source) =>
        decodePowerSource(source, s"$path.source").map(Some(_))
    kind <- value("kind").str match
      case "run" => Right(QueueKind.Run)
      case "offer" => Right(QueueKind.Offer)
      case other => Left(InvalidValue(s"$path.kind",
        s"unknown queue kind '$other'"))
  yield QueuedProcedure(PlayerId(value("actorId").str), startable,
    startArgs, source, kind)
```

`traverse` is the helper `WalkerEventCodec.decodeRefs` uses. If it is not visible in `FollowUpWire`, use the same import or self-type that file uses. Update the trait's doc: "a queued entry".

In `GameEventWire.scala`, replace `FollowUpStartedType` with `val ProcedureDequeuedType = "walker.procedure-dequeued"`. In `WalkerEventCodec.scala`, rename the discriminator, encoder and decoder arms, with payload `ujson.Obj("entry" -> encodeQueued(entry))` and decoding `decodeQueued(payload("entry"), s"$path.entry")`, for both events. In `WalkerOperationCodec.scala`, the op becomes:

```scala
      case QueueProcedure(entry, atFront) => ujson.Obj(
        "kind" -> "queue-procedure",
        "entry" -> encodeQueued(entry),
        "atFront" -> ujson.Bool(atFront))
```

and its decoder `case "queue-procedure" => decodeQueued(value("entry"), s"$path.entry").map(QueueProcedure(_, value("atFront").bool))`. Rename `QueueFollowUp` to `QueueProcedure` in the codec's op union type too.

- [ ] **Step 9: Update the tests mechanically**

In every test listed under "Files", apply:

- `FollowUp(player, action, source)` becomes `QueuedProcedure(player, action, Vector.empty, Some(source), QueueKind.Offer)`;
- `followUps` becomes `queue`, and `walkerFollowUp` becomes `walkerEntry`;
- `FollowUpStarted` becomes `ProcedureDequeued`;
- `QueueFollowUp(x)` becomes `QueueProcedure(x)`;
- the wire suites' expected JSON uses the spellings of Step 8.

Add one round trip to `WalkerOperationWireSuite`: `QueueProcedure(QueuedProcedure(p1, ActionRef.Trade, Vector(DecisionOptionRef.Button("favor")), None, QueueKind.Run), atFront = true)` decodes to itself. Use the suite's own round-trip helper and player id.

Add one test to `FollowUpReplaySuite`:

```scala
  test("an entry queued in front goes before the waiting ones"):
    val waiting = QueuedProcedure(p1, ActionRef.Campaign, Vector.empty,
      Some(knights), QueueKind.Offer)
    val first = waiting.copy(procedure = ActionRef.Travel,
      kind = QueueKind.Run)
    val ready = Table.start.update(_.updateCurrent(_.copy(
      queue = Vector(waiting)))).ready
    assertEquals(TurnStateOperations.queueProcedure(ready, first,
      atFront = true).game.current.queue, Vector(first, waiting))
```

Use the suite's own `knights` source and imports; add `oathdigital.gameplay.operations.TurnStateOperations` if it is not imported.

- [ ] **Step 10: Run the follow-up suites**

Run: `./sbtw "testOnly oathdigital.gameplay.FollowUp* oathdigital.gameplay.walker.FollowUp* oathdigital.gameplay.powers.economy.KnightsErrantSuite oathdigital.gameplay.powers.search.HuntingPartySuite oathdigital.gameplay.powers.campaign.SecondWindSuite oathdigital.application.FollowUp* oathdigital.application.gamelog.GameLogFollowUpSuite oathdigital.serialization.*"`
Expected: PASS.

- [ ] **Step 11: Run everything**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS, with the baseline counts plus the 2 new tests.

- [ ] **Step 12: Commit**

```bash
git add docs/superpowers/specs/2026-10-09-walker-game-loop-design.md \
  src/main/scala/oathdigital/model src/main/scala/oathdigital/gameplay \
  src/main/scala/oathdigital/serialization \
  src/main/scala/oathdigital/application src/main/scala/oathdigital/server \
  src/test/scala
git status --short
git commit -m "refactor(walker): QueuedProcedure replaces FollowUp"
```

Check `git status --short` before committing: only this task's files are staged.

---

### Task 2: Recover's cost window

**Files:**
- Modify: `src/main/scala/oathdigital/model/PowerWindow.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/FollowUpSupplyWaiver.scala`
- Test: `src/test/scala/oathdigital/gameplay/FollowUpSupplyWaiverSuite.scala`

**Interfaces:**
- Produces: `PowerWindow.RecoverCost`, key `"recover.cost"`, a `RecoverWindow`. `FollowUpSupplyWaiver.costWindow(ActionRef.Recover) == Some(PowerWindow.RecoverCost)`.

- [ ] **Step 1: Write the failing tests**

In `FollowUpSupplyWaiverSuite`, change the test "each action pays at its own cost window, and Recover at none" to expect `Some(PowerWindow.RecoverCost)` for Recover, and rename it "each action pays at its own cost window". Add:

```scala
  test("a queued Recover spends no Supply on its roll"):
    // p1 has no Supply. A Recover started normally cannot pay its roll.
    val owed = QueuedProcedure(p1, ActionRef.Recover, Vector.empty,
      Some(knights), QueueKind.Offer)
    val base = Table.start.pawn(p1, at = "Dunes")
      .relicAt("Brass Horse", at = "Dunes").supply(p1, 0)
    assert(rules.startWalker(base.state, ActionRef.Recover, p1).isLeft)
    val queued = base.update(_.updateCurrent(_.copy(queue = Vector(owed))))
    val started = accepted(rules.startWalker(queued.state, ActionRef.Recover,
      p1))
    assertEquals(Look(Outcomes.ready(started.state)).supply(p1), 0)
```

Use the suite's `rules`, `knights` and imports; add `Look`, `Outcomes`, `Table` and `QueueKind` imports if missing. If the Recover roll needs dice the suite's rules do not supply, build the rules with `walkerDice = WalkerDiceFixture.blanks`, as `FollowUpRulesSuite` does.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.FollowUpSupplyWaiverSuite"`
Expected: FAIL: `RecoverCost` does not exist.

- [ ] **Step 3: Add the window and wrap the payment**

In `PowerWindow.scala`, after `RecoverBeforeFirstRoll`:

```scala
  /** The Supply each Recover roll costs (walker-driven game loop design,
    * plan-time ruling 6): the follow-up waiver strips it here. */
  case object RecoverCost extends RecoverWindow { val key = "recover.cost" }
```

In `RecoverProcedure.scala`, the body's first child becomes a windowed `Sequence`:

```scala
    val body = Sequence(
      Sequence(Vector(SpendSupply(actor, supplyCost)),
        Some(PowerWindow.RecoverCost)),
      Roll(recoverPool, DiceSpec(DiceKind.Defense), RollMode.Automatic),
      Branch((ready, _) =>
        if succeeded(ready) then Vector.empty else Vector(choiceDecide)))
```

Check `Sequence`'s constructor in `CoreOperations.scala` (line ~698). If its second parameter is not the window, pass the window by its name. Update the tree sketch in the object's doc: `SpendSupply(actor, 1), // window = RecoverCost`.

In `FollowUpSupplyWaiver.scala`, add `case ActionRef.Recover => Some(PowerWindow.RecoverCost)` to `costWindow`, add `PowerWindow.RecoverCost` to `windows`, and delete the doc sentence "Recover pays inside its repeat with no window, and every other procedure spends none." in favor of "Every other procedure spends none."

- [ ] **Step 4: Run the Recover and waiver suites**

Run: `./sbtw "testOnly oathdigital.gameplay.FollowUpSupplyWaiverSuite oathdigital.gameplay.RecoverProcedureSuite oathdigital.gameplay.powers.recover.*"`
Expected: PASS. A `RecoverProcedureSuite` test that lists the windows a Recover visits now also lists `RecoverCost`; update its expected list and report it. A test that reads a Recover step's `nodeId` may see the payment one level deeper; update it and report it.

- [ ] **Step 5: Run everything and commit**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS.

```bash
git add src/main/scala/oathdigital/model/PowerWindow.scala \
  src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala \
  src/main/scala/oathdigital/gameplay/walker/FollowUpSupplyWaiver.scala \
  src/test/scala/oathdigital/gameplay
git commit -m "feat(recover): a cost window the follow-up waiver reaches"
```

---

### Task 3: The driver

`OathDriver` replaces the glue. While the queue is not empty, the driver does nothing: the head still waits for `StartWalker` or `SkipFollowUp`, which Task 4 replaces.

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/OathDriver.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala`, `OathRulesWalker.scala`
- Modify: `src/main/scala/oathdigital/model/GameState.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala`, `WalkerReplay.scala`
- Modify: `src/main/scala/oathdigital/serialization/GameEventWire.scala`, `WalkerEventCodec.scala`
- Create: `src/test/scala/oathdigital/gameplay/OathDriverSuite.scala`
- Modify: `src/test/scala/oathdigital/serialization/WalkerDecisionWireSuite.scala`

**Interfaces:**
- Consumes: Task 1's `queue`, `walkerEntry`, `ProcedureDequeued`.
- Produces, in `oathdigital.gameplay`:

  ```scala
  private[gameplay] enum Ended:
    case Walk(procedure: ProcedureRef)
    case MinorAction
    case Skip
  private[gameplay] def next(transition: OathTransition, ended: Ended)
      : Either[OathViolation, OathTransition]   // on OathDriver
  ```

- Produces: `CurrentGameState.boundaryDue: Boolean = false`; `case object ActionBoundaryRan extends WalkerEvent`, wire type `walker.action-boundary-ran` with an empty payload.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/OathDriverSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ActionBoundaryRan, FollowUpSkipped,
  WalkerCompleted}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Outcomes, TakeWealthFixture, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** What runs between walks (walker-driven game loop design, "The
  * driver"). */
class OathDriverSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = WalkerDiceFixture.blanks)
  private val ancientCity = CatalogNames.site("Ancient City")
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  private val knights =
    PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))

  private def offer(action: StartableRef): QueuedProcedure =
    QueuedProcedure(p1, action, Vector.empty, Some(knights), QueueKind.Offer)

  private def current(state: OathState): CurrentGameState =
    Outcomes.ready(state).game.current

  private def boundaries(events: Vector[OathEvent]): Int =
    events.count(_ == ActionBoundaryRan)

  /** p1 at Ancient City, which two bandits rule; p2 at Broken Peaks and p3
    * at Buried Giant. It is p1's Act. */
  private def table: Table = Table.start
    .pawn(p1, at = ancientCity).pawn(p2, at = brokenPeaks)
    .pawn(p3, at = "Buried Giant").bandits(ancientCity, 2)

  private def replayed(from: OathState, events: Vector[OathEvent])
      : OathState =
    accepted(events.foldLeft[Either[OathViolation, OathState]](Right(from))(
      (state, event) => state.flatMap(rules.evolve(_, event))))

  test("an action's walk ends with the boundary, once"):
    val start = table.state
    val traveled = accepted(TravelPlay.travelled(rules, start, p1,
      brokenPeaks))
    val completed = traveled.events.indexOf(WalkerCompleted(ActionRef.Travel))
    assertEquals(traveled.events.lift(completed + 1),
      Some(ActionBoundaryRan: OathEvent))
    assertEquals(boundaries(traveled.events), 1)
    assertEquals(current(traveled.state).boundaryDue, false)
    assertEquals(replayed(start, traveled.events), traveled.state)

  test("Begin Rest, Finish Rest and End Wake run no boundary"):
    // p1 has no REST power, so Rest finishes in the same command, and p2's
    // Wake, with nothing to take, ends in it too.
    val rested = accepted(rules.startWalker(table.state,
      PhaseTransitionRef.BeginRest, p1))
    assert(rested.events.contains(
      WalkerCompleted(PhaseTransitionRef.FinishRest)), rested.events)
    assertEquals(boundaries(rested.events), 0)

  test("a walk that leaves an entry queued holds the boundary until Skip"):
    val start = table.update(_.updateCurrent(_.copy(queue =
      Vector(offer(ActionRef.Travel), offer(ActionRef.Campaign))))).state
    val started = accepted(rules.startWalker(start, ActionRef.Travel, p1))
    val traveled = accepted(rules.resolveWalker(started.state, p1,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(brokenPeaks))))
    assertEquals(boundaries(traveled.events), 0)
    assertEquals(current(traveled.state).boundaryDue, true)
    val skipped = accepted(rules.skipFollowUp(traveled.state, p1))
    assertEquals(skipped.events.take(2), Vector[OathEvent](
      FollowUpSkipped(offer(ActionRef.Campaign)), ActionBoundaryRan))
    assertEquals(current(skipped.state).boundaryDue, false)

  test("a minor action runs the boundary"):
    val start = table.relicAt("Brass Horse", at = ancientCity).state
    val peeked = accepted(rules.handle(start,
      oathdigital.gameplay.actions.MinorActionCommand.PeekSiteRelics(p1)))
    assertEquals(boundaries(peeked.events), 1)

  test("the title walk hands back to the driver: Wake ends once the holder " +
      "answers"):
    // p2 holds the title while p1 and p3 each rule one site, a tie p2
    // breaks. p1 takes Ancient City's only favor, which leaves no Wake
    // option.
    val ready = Table.start
      .pawn(p1, at = "Ancient City").pawn(p2, at = "Broken Peaks")
      .pawn(p3, at = "Buried Giant").oathkeeper(Some(p2))
      .warbandsAt("Dunes", p1, 1).warbandsAt("Fair Isle", p3, 1)
      .turn(p1, Phase.Wake).siteTokens("Ancient City", favor = 1, secrets = 0)
      .ready
    val parked = accepted(TakeWealthFixture.take(rules, ready))
    assertEquals(current(parked.state).walkerProcedure,
      Some(TriggeredProcedureRef.Oathkeeper))
    val chosen = accepted(rules.resolveWalker(parked.state, p2,
      OathkeeperProcedure.recipientDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Player(p1))))
    assert(chosen.events.contains(
      WalkerCompleted(PhaseTransitionRef.EndWake)), chosen.events)
    assertEquals(current(chosen.state).turn.phase, Phase.Act)

  test("Setup's end runs the first Wake's checks"):
    // The first player's ignored WAKE rules, recorded right after Setup.
    val setup = FirstGameSetupFixture.setupThroughWake()
    val events = setup.events
    val end = events.indexOf(WalkerCompleted(TriggeredProcedureRef.Setup))
    val afterSetup = Outcomes.ready(replayed(OathState.NoGame,
      events.take(end + 1)))
    val first = afterSetup.game.current.turn.activePlayer
    val expected = accepted(PowerRuntime.ignored(catalog, afterSetup, first,
      ActionKind.Wake))
    val recorded = events.drop(end + 1).collectFirst {
      case OathEvent.IgnoredRulesRecorded(player, ActionKind.Wake, found)
          if player == first => found
    }
    assertEquals(recorded, Option.when(expected.nonEmpty)(expected))
```

The minor-action test needs a relic at p1's site: use `relicAt` as `MinorActionsSuite` does. `TakeWealthFixture.take` takes a favor by default; if it takes something else, set the site's tokens so the take leaves no Wake option.

In "Setup's end runs the first Wake's checks", `replayed` starts from `NoGame`. If `setupThroughWake`'s events do not begin with `GameStarted`, fold them from the state the fixture began with instead; read `FirstGameSetupFixture` for it. If `expected` comes out empty for the fixture, the test proves nothing. Then add, before Setup ends, an adviser for the first player whose WAKE power the fallback registry ignores: find one by filtering the catalog's denizens with `PowerRuntime.ignored(..., ActionKind.Wake)` on a `Table.start.adviser(p1, id)`. Report which card you used.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.OathDriverSuite"`
Expected: FAIL to compile: `ActionBoundaryRan` and `boundaryDue` do not exist.

- [ ] **Step 3: The flag and its event**

In `CurrentGameState`, after `walkerEntry`:

```scala
    // Whether an action's walk has finished since the last action boundary
    // (walker-driven game loop design, ruling 5). Not walker scratch:
    // `WalkerCompleted` of an action sets it, and only `ActionBoundaryRan`
    // clears it, once the queue is empty.
    boundaryDue: Boolean = false,
```

In `WalkerEvents.scala`:

```scala
/** The action boundary ran: replay clears `boundaryDue` (walker-driven game
  * loop design, plan-time ruling 3). The boundary may write nothing else.
  * No Game Log line. */
case object ActionBoundaryRan extends WalkerEvent
```

In `WalkerReplay.scala`:

- the `WalkerCompleted(procedure)` arm also sets `boundaryDue = procedure match { case _: ActionRef => true; case _ => ready.game.current.boundaryDue }`;
- a new arm: `case ActionBoundaryRan => Right(ready.updateCurrent(_.copy(boundaryDue = false)))`.

In `OathRules.evolve`, add `case ActionBoundaryRan => ProcedureWalker.applyRecorded(state, ActionBoundaryRan)` beside the other walker events.

Wire: in `GameEventWire.scala`, `val ActionBoundaryRanType = "walker.action-boundary-ran"`. In `WalkerEventCodec.scala`, the discriminator `case ActionBoundaryRan => ActionBoundaryRanType`, the encoder `case ActionBoundaryRan => ujson.Obj()`, and the decoder `case ActionBoundaryRanType => Right(ActionBoundaryRan)`. Add its round trip to `WalkerDecisionWireSuite`, beside the other walker events.

- [ ] **Step 4: Write the driver**

Create `src/main/scala/oathdigital/gameplay/OathDriver.scala`:

```scala
package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.EventEvolution
import oathdigital.gameplay.oathkeeper.{OathkeeperOutcome, OathkeeperRules}
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.phases.rest.{TurnBoundary,
  WarExhaustionRandomPort}
import oathdigital.gameplay.phases.wake.{ForcedWakeProcedure,
  TakeWealthProcedure}
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.walker.ActionBoundaryRan
import oathdigital.model._
import oathdigital.model.OathEvent._
import oathdigital.model.OathState._
import oathdigital.model.OathViolation._

/** What the driver was handed. */
private[gameplay] enum Ended:
  /** A walk of `procedure` finished. */
  case Walk(procedure: ProcedureRef)
  /** A legacy minor action finished. Slice 3 moves them onto the walker. */
  case MinorAction
  /** The queue's head was skipped. Task 4 makes Skip an answer of the
    * offer's walk. */
  case Skip

/** What runs between walks (walker-driven game loop design, "The driver").
  * Every finished walk is handed to [[next]], which starts nothing while a
  * result is set and leaves a queued head waiting. With an empty queue it
  * runs, in order:
  *
  *  1. the action boundary, when an action has finished since the last one
  *     (`boundaryDue`, ruling 5): the ignored-rule diagnostics, the bandit
  *     refill and the Oathkeeper check. A title walk the check starts hands
  *     back here when it ends.
  *  2. the turn's start, after a Finish Rest or Setup walk: the round's end
  *     when the last player has rested, then Wake's entry and the Forced
  *     Wake walk when one is due, which hands back here when it ends.
  *  3. the phase's end, when its ending is the only option left (ruling 3):
  *     End Wake in Wake, Finish Rest in Rest. Act parks nothing until its
  *     menu exists (slice 4).
  *
  * Each start runs on the state the steps before it left, so a turn that
  * changes inside one command gives the next walk the new active player.
  */
private[gameplay] trait OathDriver:
  self: OathRulesWalker & EventEvolution[OathState, OathEvent,
    OathViolation] =>

  protected def catalog: ExecutableCatalog
  protected def phasePowerCatalog: PhasePowers
  protected def warExhaustionRandomPort: WarExhaustionRandomPort
  protected def outsideActions: Vector[OperationRestriction]

  private[gameplay] def next(transition: OathTransition, ended: Ended)
      : Either[OathViolation, OathTransition] = transition.state match
    case Ready(ready) if ready.game.current.result.nonEmpty =>
      Right(transition)
    // The head waits for its player's start or Skip.
    case Ready(ready) if ready.game.current.queue.nonEmpty => Right(transition)
    case Ready(ready) if ready.game.current.boundaryDue ||
        ended == Ended.MinorAction => boundary(transition, ended)
    case Ready(_) => turnStart(transition, ended)
    case _ => Right(transition)

  /** Step 1. Its order is the old `completeAction`'s, after the marker. */
  private def boundary(transition: OathTransition, ended: Ended) =
    for
      marked <- append(transition, ActionBoundaryRan)
      noted <- boundaryDiagnostics(marked)
      refilled <- appendEvaluation(noted,
        StateBasedEvaluation.banditRefill(catalog, _))
      checked <- oathkeeper(refilled, ended)
    yield checked

  /** The boundary decides only WHETHER the title changes; the triggered
    * procedure performs the change, so every title change is one walker
    * step. */
  private def oathkeeper(transition: OathTransition, ended: Ended) =
    transition.state match
      case Ready(ready) => StateBasedEvaluation.supported(transition.state)
        .flatMap(_ => OathkeeperRules.outcome(ready) match {
          case OathkeeperOutcome.NoChange => turnStart(transition, ended)
          case _ => startTriggered(transition,
            TriggeredProcedureRef.Oathkeeper)
        })
      case _ => Right(transition)

  private def boundaryDiagnostics(transition: OathTransition) =
    transition.state match
      case Ready(ready) =>
        val actor = ready.game.current.turn.activePlayer
        PowerRuntime.ignored(catalog, ready, actor,
          ActionKind.ActionBoundary).map { diagnostics =>
          if diagnostics.isEmpty then transition else transition.copy(
            events = transition.events :+ IgnoredRulesRecorded(actor,
              ActionKind.ActionBoundary, diagnostics))
        }
      case _ => Right(transition)

  /** Step 2 (plan-time ruling 8). Setup ends as Finish Rest does, so the
    * first Wake runs these hooks too. */
  private def turnStart(transition: OathTransition, ended: Ended)
      : Either[OathViolation, OathTransition] = ended match
    case Ended.Walk(PhaseTransitionRef.FinishRest |
        TriggeredProcedureRef.Setup) =>
      val rounded = transition.state match
        case Ready(ready) if ready.game.current.turn.phase ==
            Phase.RoundEnd =>
          TurnBoundary.finishRound(catalog, transition,
            warExhaustionRandomPort, outsideActions)
        case _ => Right(transition)
      rounded.flatMap(after => after.state match {
        case Ready(ready) if ready.game.current.result.nonEmpty => Right(after)
        case _ => enterWake(after)
      })
    case _ => phaseEnd(transition)

  private def enterWake(transition: OathTransition) =
    val prepared = transition.state match
      case Ready(ready) => PowerRuntime.ignored(catalog, ready,
          ready.game.current.turn.activePlayer, ActionKind.Wake)
        .map { diagnostics =>
          if diagnostics.isEmpty then transition else transition.copy(
            events = transition.events :+ IgnoredRulesRecorded(
              ready.game.current.turn.activePlayer, ActionKind.Wake,
              diagnostics))
        }
      case _ => Right(transition)
    prepared.flatMap(current =>
      StateBasedEvaluation.atWake(current.state).flatMap {
        case Some(win: UsurperVictory) => append(current, win)
        case Some(flip: UsurperFlipped) => append(current, flip)
          .flatMap(vision)
        case None => vision(current)
        case Some(other) => Left(InvalidEventOrder(
          s"unexpected Wake evaluation event: $other"))
      }).flatMap(forcedWake)

  private def vision(transition: OathTransition) =
    StateBasedEvaluation.visionAtWake(transition.state).flatMap {
      case Some(won) => append(transition, won)
      case None => Right(transition)
    }

  /** The forced Wake powers of the player whose Wake begins (catalog batch
    * 2, N8), while the game goes on. */
  private def forcedWake(transition: OathTransition) =
    transition.state match
      case Ready(ready) if ready.game.current.result.isEmpty &&
          ready.game.current.turn.phase == Phase.Wake &&
          ForcedWakeProcedure.due(catalog, ready,
            ready.game.current.turn.activePlayer,
            phasePowerCatalog).nonEmpty =>
        startTriggered(transition, TriggeredProcedureRef.ForcedWake)
      case _ => phaseEnd(transition)

  /** Step 3, until the menus exist. */
  private def phaseEnd(transition: OathTransition)
      : Either[OathViolation, OathTransition] = transition.state match
    case Ready(ready) if ready.game.current.result.isEmpty =>
      val player = ready.game.current.turn.activePlayer
      ready.game.current.turn.phase match
        case Phase.Wake if !wakeOptionOpen(ready, player) =>
          joined(transition, startWalker(transition.state,
            PhaseTransitionRef.EndWake, player))
        case Phase.Rest if !phasePowerUsable(ready, player) =>
          joined(transition, startWalker(transition.state,
            PhaseTransitionRef.FinishRest, player))
        case _ => Right(transition)
    case _ => Right(transition)

  /** Whether `player` could use a power of the phase they are in now: a
    * WAKE power in Wake, a REST power in Rest. */
  private def phasePowerUsable(ready: ReadyGame, player: PlayerId): Boolean =
    PhasePowerProcedure.usable(catalog, ready, player, phasePowerCatalog,
      walkerPowers).nonEmpty

  /** Whether `player` has a Wake option open now: wealth to take or a WAKE
    * power to use. */
  private def wakeOptionOpen(ready: ReadyGame, player: PlayerId): Boolean =
    phasePowerUsable(ready, player) || TakeWealthProcedure.candidates(catalog,
      ready, player, walkerPowers).nonEmpty

  private def joined(before: OathTransition,
      started: Either[OathViolation, OathTransition]) =
    started.map(after => after.copy(events = before.events ++ after.events))

  private def append(transition: OathTransition, event: OathEvent) =
    evolve(transition.state, event).map(state =>
      transition.copy(state = state, events = transition.events :+ event))

  private def appendEvaluation(transition: OathTransition,
      evaluate: OathState => Either[OathViolation, Option[OathEvent]]) =
    evaluate(transition.state).flatMap:
      case None => Right(transition)
      case Some(event) => append(transition, event)
```

`phasePowerUsable` is `OathRules.restPowerUsable`'s body, renamed because Wake asks it too, and `wakeOptionOpen` keeps its body. Both rest on `PhasePowerProcedure.usable` answering for the phase the player is in; check that before deleting the originals in `OathRules`. `ended == Ended.MinorAction` compares enum values, which is fine for a parameterless case.

- [ ] **Step 5: Hand every finish to the driver, and delete the glue**

In `OathRules.scala`:

- the class extends `EventEvolution[OathState, OathEvent, OathViolation] with OathRulesWalker with OathDriver`;
- `outsideActions` becomes `protected lazy val outsideActions`;
- `handle(state, command: MinorActionCommand)` ends `.flatMap(next(_, Ended.MinorAction))` in place of `.flatMap(completeAction)`;
- delete `completeAction`, `turnBoundary`, `restPowerUsable`, `wakeOptionOpen`, `oathkeeperStep`, `recordBoundaryFallback`, `enterWake`, `forcedWake` and `appendEvaluation`, and every import only they used.

In `OathRulesWalker.scala`:

- delete the abstract members `completeAction`, `turnBoundary`, `forcedWake`, `restPowerUsable` and `wakeOptionOpen`, and add:

  ```scala
  /** Runs what follows a finished walk (`OathDriver`). */
  private[gameplay] def next(transition: OathTransition, ended: Ended)
      : Either[OathViolation, OathTransition]
  ```

- `walkerTransition`'s `Finished` arm becomes:

  ```scala
    case WalkerOutcome.Finished(_, steps) =>
      GameplayTransition(state, steps :+ WalkerCompleted(procedure))(evolve)
        .flatMap(recordCardPlayFallback(_, procedure, steps))
        .flatMap(next(_, Ended.Walk(procedure)))
  ```

- `skipFollowUp`'s last line becomes `GameplayTransition(state, Vector(FollowUpSkipped(head)))(evolve).flatMap(next(_, Ended.Skip))`;
- delete `runsActionBoundary`, `runsTurnBoundary`, `autoFinishRest` and `autoEndWake`;
- `startTriggered`'s doc: "The procedure's events are appended to `transition`, so whatever handed it on and the procedure journal as one command." Delete the sentence about the action that triggered it.

- [ ] **Step 6: Run the driver suite**

Run: `./sbtw "testOnly oathdigital.gameplay.OathDriverSuite"`
Expected: PASS.

- [ ] **Step 7: Run everything and update the expected changes**

Run: `./sbtw "test" "frontend/test"`

These failures are expected. Update each test and list it in your report:

- a test that asserts an action's exact `events` now sees `ActionBoundaryRan` right after the action's `WalkerCompleted`;
- a test that compares a whole `CurrentGameState` built by hand sees `boundaryDue`;
- after Knights Errant's Muster or Hunting Party's Search, the boundary (bandit refill, title change) now waits until the queued Campaign ends or is skipped (ruling 4);
- in Rest, Finish Rest now runs by itself after the last usable REST power is used, and after a queued action that ran in Rest (plan-time ruling 7). `FollowUpSupplyWaiverSuite`'s Special Envoy test is one;
- the first Wake after Setup now records its Wake diagnostics and runs the Usurper and Vision checks.

Any other failure: stop and report.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/OathDriver.scala \
  src/main/scala/oathdigital/gameplay/OathRules.scala \
  src/main/scala/oathdigital/gameplay/OathRulesWalker.scala \
  src/main/scala/oathdigital/model/GameState.scala \
  src/main/scala/oathdigital/gameplay/walker \
  src/main/scala/oathdigital/serialization src/test/scala
git status --short
git commit -m "feat(walker): one driver runs what follows every walk"
```

---

### Task 4: Follow-ups become walker offers

The driver dequeues the head. An `Offer` parks a `FollowUpOffer` decision: the action, priced by its start's own checks, or Skip. `startFollowUp`, `skipFollowUp`, the service's gate and the Skip command go.

**Files:**
- Modify: `src/main/scala/oathdigital/model/ProcedureRef.scala`, `GameViolation.scala`
- Create: `src/main/scala/oathdigital/gameplay/walker/FollowUpOffer.scala`, `QueuedStart.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`, `WalkerReplay.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathDriver.scala`, `OathRulesWalker.scala`
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala`, `GameCommands.scala`, `Authorization.scala`, `GameIntentMapper.scala`, `WalkerDecisionProjector.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/*.scala` where a match over `TriggeredProcedureRef` needs the new case
- Modify: `src/main/scala/oathdigital/server/GameRoutes.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala`, `CommandIntentCodec.scala`, `CommandIntentDecoders.scala`
- Delete: `frontend/src/main/scala/oathdigital/frontend/FollowUpControls.scala`, `frontend/src/test/scala/oathdigital/frontend/FollowUpControlsSuite.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala`
- Create: `src/test/scala/oathdigital/gameplay/FollowUpPlay.scala`
- Modify: `src/test/scala/oathdigital/testkit/Table.scala`, `ParkedNode.scala`, `SituationDriver.scala`
- Modify tests: `OathDriverSuite`, `FollowUpRulesSuite`, `FollowUpReplaySuite`, `FollowUpSupplyWaiverSuite`, `KnightsErrantSuite`, `HuntingPartySuite`, `SecondWindSuite`, `FollowUpServiceSuite`, `GameLogFollowUpSuite`, `LogScripts`, `PendingWalkerInvariantSuite`, `CommandRejectionMessageSuite`, `CommandProtocolSuite`, `WalkerProcedureRegistrySuite`

**Interfaces:**
- Consumes: Tasks 1 and 3.
- Produces, in `oathdigital.model`: `TriggeredProcedureRef.FollowUpOffer`, key `"follow-up-offer"`.
- Produces, in `oathdigital.gameplay.walker`:

  ```scala
  object FollowUpOffer:
    val decisionId: String = "follow-up.offer"
    val take: DecisionOptionRef.Button
    val skip: DecisionOptionRef.Button
    def build(phasePowers: PhasePowers, powers: WalkerPowers)(
        catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
        args: Vector[DecisionOptionRef]): Either[OathViolation, Operation]
    def skips(event: OathEvent): Boolean
  object QueuedStart:
    def refusal(catalog: ExecutableCatalog, ready: ReadyGame,
        entry: QueuedProcedure, powers: WalkerPowers,
        phasePowers: PhasePowers): Option[OathViolation]
    def startingFrom(ready: ReadyGame, entry: QueuedProcedure): ReadyGame
  ```

- Produces: `WalkerProcedureRegistry.build` and `rebuild` gain a last parameter `walkerPowers: WalkerPowers = WalkerPowers.empty`.
- Produces, in tests: `FollowUpPlay.take`, `skip`, `taken`, `skipped`, `offered` and `dequeued` (Step 1); `Table.offering(entry)`.

- [ ] **Step 1: The test helpers**

Create `src/test/scala/oathdigital/gameplay/FollowUpPlay.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.walker.FollowUpOffer
import oathdigital.model._

/** Answers a follow-up offer as its player does (walker-driven game loop
  * design, ruling 6). */
object FollowUpPlay:
  val take: DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(FollowUpOffer.take)
  val skip: DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(FollowUpOffer.skip)

  def taken(rules: OathRules, state: OathState, actor: PlayerId)
      : Either[OathViolation, OathTransition] =
    rules.resolveWalker(state, actor, FollowUpOffer.decisionId, take)

  def skipped(rules: OathRules, state: OathState, actor: PlayerId)
      : Either[OathViolation, OathTransition] =
    rules.resolveWalker(state, actor, FollowUpOffer.decisionId, skip)

  /** The entry the parked offer asks about, when an offer is parked. */
  def offered(state: OathState): Option[QueuedProcedure] = state match
    case OathState.Ready(ready) if ready.game.current.walkerProcedure
        .contains(TriggeredProcedureRef.FollowUpOffer) =>
      ready.game.current.walkerEntry
    case _ => None

  /** The driver on a table that holds a queue, as when the walk that
    * queued it ends: it dequeues the head. */
  def dequeued(rules: OathRules, state: OathState)
      : Either[OathViolation, OathTransition] =
    rules.next(OathTransition(state, Vector.empty),
      Ended.Walk(ActionRef.Travel))
```

In `src/test/scala/oathdigital/testkit/Table.scala`, after `parkedWalker`:

```scala
  /** A follow-up offer of `entry` parked, as the driver leaves it once it
    * dequeues the entry (walker-driven game loop design, "The driver"). */
  def offering(entry: QueuedProcedure): Table =
    update(_.updateCurrent(_.copy(walkerEntry = Some(entry))))
      .parkedWalker(TriggeredProcedureRef.FollowUpOffer)
```

In `src/test/scala/oathdigital/testkit/ParkedNode.scala`, the `rebuild` call passes the powers too, so an offer's park can be read:

```scala
              tree <- WalkerProcedureRegistry.rebuild(procedure, catalog,
                  ready, current.turn.activePlayer, current.walkerStartArgs,
                  phasePowers, walkerPowers =
                    WalkerPowers.selected(walkerPowers, Vector.empty))
```

- [ ] **Step 2: Write the failing rules tests**

Rewrite `src/test/scala/oathdigital/gameplay/FollowUpRulesSuite.scala`. Keep its class doc's first sentence's subject, its `rules`, `ancientCity`, `brokenPeaks`, `knights`, `table`, `current` and `replayed`, with `table` taking `QueuedProcedure*` and setting `queue`. Replace `owed` and every test with:

```scala
  private def offer(action: StartableRef): QueuedProcedure =
    QueuedProcedure(p1, action, Vector.empty, Some(knights), QueueKind.Offer)

  private def run(action: StartableRef): QueuedProcedure =
    offer(action).copy(kind = QueueKind.Run)

  /** The parked offer's options, as the walker shows them. */
  private def options(state: OathState): Vector[DecisionOption] =
    ParkedNode.of(state, catalog, WalkerPowerCatalog.default(catalog))
      .toOption.flatten.collect {
        case ParkedNode.Decision(_, decide, _) => decide.query
      }.collect { case one: DecisionQuery.ChooseOne => one.options }
      .getOrElse(Vector.empty)

  test("the driver dequeues an offer at once, and the offer parks"):
    val start = table(offer(ActionRef.Campaign)).state
    val parked = accepted(FollowUpPlay.dequeued(rules, start))
    assertEquals(parked.events.head,
      ProcedureDequeued(offer(ActionRef.Campaign)): OathEvent)
    assertEquals(FollowUpPlay.offered(parked.state),
      Some(offer(ActionRef.Campaign)))
    assertEquals(current(parked.state).queue, Vector.empty)
    assertEquals(parked.state,
      table().offering(offer(ActionRef.Campaign)).state)
    assertEquals(replayed(start, parked.events), parked.state)

  test("the offer shows the action for no Supply, then Skip"):
    val parked = table().offering(offer(ActionRef.Campaign)).state
    assertEquals(options(parked).map(_.ref),
      Vector[DecisionOptionRef](FollowUpOffer.take, FollowUpOffer.skip))
    assert(options(parked).forall(_.available), options(parked))

  test("taking it starts the action in the same command"):
    val start = table().offering(offer(ActionRef.Campaign)).state
    val started = accepted(FollowUpPlay.taken(rules, start, p1))
    assert(started.events.contains(
      WalkerCompleted(TriggeredProcedureRef.FollowUpOffer)), started.events)
    assert(started.events.contains(
      ProcedureDequeued(run(ActionRef.Campaign))), started.events)
    assertEquals(current(started.state).walkerProcedure,
      Some(ActionRef.Campaign))
    assertEquals(current(started.state).walkerEntry,
      Some(run(ActionRef.Campaign)))
    assertEquals(replayed(start, started.events), started.state)

  test("an action that cannot start is shown with its reason, and Skip " +
      "still works"):
    // No bandits and no enemy pawn at Ancient City: nothing to campaign
    // against.
    val start = table().bandits(ancientCity, 0)
      .offering(offer(ActionRef.Campaign)).state
    assertEquals(options(start).headOption, Some(DecisionOption.Unavailable(
      DecisionOption.Button(FollowUpOffer.take, "Campaign for no Supply"),
      OptionReason.Written("No site to conquer and no enemy pawn to raid"))))
    assert(FollowUpPlay.taken(rules, start, p1).isLeft)
    val skipped = accepted(FollowUpPlay.skipped(rules, start, p1))
    assert(skipped.events.contains(
      FollowUpSkipped(offer(ActionRef.Campaign))), skipped.events)
    assertEquals(current(skipped.state).walkerProcedure, None)
    assertEquals(replayed(start, skipped.events), skipped.state)

  test("skipping one entry offers the next"):
    val start = table(offer(ActionRef.Travel), offer(ActionRef.Campaign))
      .state
    val travel = accepted(FollowUpPlay.dequeued(rules, start))
    val next = accepted(FollowUpPlay.skipped(rules, travel.state, p1))
    assertEquals(FollowUpPlay.offered(next.state),
      Some(offer(ActionRef.Campaign)))

  test("a taken action goes before the entries already waiting"):
    val start = table(offer(ActionRef.Travel), offer(ActionRef.Campaign))
      .state
    val travel = accepted(FollowUpPlay.dequeued(rules, start))
    val started = accepted(FollowUpPlay.taken(rules, travel.state, p1))
    assertEquals(current(started.state).walkerProcedure,
      Some(ActionRef.Travel))
    assertEquals(current(started.state).queue,
      Vector(offer(ActionRef.Campaign)))

  test("a queued action starts outside the Act phase"):
    val resting = table().turn(p1, Phase.Rest)
      .offering(offer(ActionRef.Campaign)).state
    assertEquals(current(accepted(FollowUpPlay.taken(rules, resting, p1))
      .state).walkerProcedure, Some(ActionRef.Campaign))

  test("without an entry, a Campaign still needs the Act phase"):
    assertEquals(rules.startWalker(table().turn(p1, Phase.Rest).state,
      ActionRef.Campaign, p1),
      Left(OathViolation.WrongPhase(Phase.Act, Phase.Rest)))

  test("only the entry's actor answers the offer"):
    val start = table().offering(offer(ActionRef.Campaign)).state
    assertEquals(FollowUpPlay.skipped(rules, start, p2),
      Left(OathViolation.WrongPlayer(p1, p2)))
```

Imports: `oathdigital.gameplay.walker.{FollowUpOffer, FollowUpSkipped, ProcedureDequeued, WalkerCompleted}` and `oathdigital.testkit.ParkedNode`; drop `TravelProcedure` and `ChooseOneAnswer` if nothing else uses them.

Add to `OathDriverSuite`, replacing the test "a walk that leaves an entry queued holds the boundary until Skip":

```scala
  test("a queued chain gets one boundary, after its last action"):
    val start = table.update(_.updateCurrent(_.copy(boundaryDue = true,
      queue = Vector(offer(ActionRef.Campaign))))).state
    val parked = accepted(FollowUpPlay.dequeued(rules, start))
    assertEquals(boundaries(parked.events), 0)
    val skipped = accepted(FollowUpPlay.skipped(rules, parked.state, p1))
    assertEquals(boundaries(skipped.events), 1)

  test("a Skip with no action before it runs no boundary"):
    val start = table.offering(offer(ActionRef.Campaign)).state
    val skipped = accepted(FollowUpPlay.skipped(rules, start, p1))
    assertEquals(boundaries(skipped.events), 0)

  test("a result stops the driver"):
    val over = table.update(_.updateCurrent(_.copy(
      result = Some(GameResult(p2)),
      queue = Vector(offer(ActionRef.Campaign))))).state
    assertEquals(accepted(FollowUpPlay.dequeued(rules, over)).events,
      Vector.empty)
```

Its imports drop `FollowUpSkipped` if unused.

- [ ] **Step 3: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.FollowUpRulesSuite oathdigital.gameplay.OathDriverSuite"`
Expected: FAIL to compile: `FollowUpOffer` does not exist.

- [ ] **Step 4: The procedure reference**

In `model/ProcedureRef.scala`, add to `TriggeredProcedureRef`:

```scala
  /** Asks a follow-up entry's actor: the action, or Skip (walker-driven
    * game loop design, ruling 6). The driver starts it when it dequeues an
    * `Offer` entry. */
  case FollowUpOffer extends TriggeredProcedureRef("follow-up-offer")
```

and add `FollowUpOffer` to `TriggeredProcedureRef.all`.

- [ ] **Step 5: The start check**

Create `src/main/scala/oathdigital/gameplay/walker/QueuedStart.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.model._

/** Whether a queued entry would start now (walker-driven game loop design,
  * plan-time ruling 4): the checks the driver's start of it runs, on the
  * state that start would see, with nothing persisted. An option the
  * follow-up offer shows available is a start the driver accepts.
  *
  * It builds through the production registry, so a suite that injects its
  * own tree source into `OathRules` does not reach it.
  */
object QueuedStart:
  def refusal(catalog: ExecutableCatalog, ready: ReadyGame,
      entry: QueuedProcedure, powers: WalkerPowers,
      phasePowers: PhasePowers): Option[OathViolation] =
    val state = startingFrom(ready, entry)
    WalkerProcedureRegistry.build(entry.procedure, catalog, state,
        entry.actor, entry.startArgs, phasePowers)
      .fold(Some(_), tree => WalkerSimulation.refusal(tree, state, powers)
        .orElse(unplayable(entry.procedure, tree, state, powers)))

  /** The state the entry's start runs on: no walk open, and the entry
    * dequeued, so the cost waiver and the phase exemption see it. */
  def startingFrom(ready: ReadyGame, entry: QueuedProcedure): ReadyGame =
    ready.updateCurrent(_.copy(walkerPending = None, walkerProcedure = None,
      walkerStartArgs = Vector.empty, walkerStartFacts = Vector.empty,
      walkerEntry = Some(entry)))

  /** The playable-option gate a start runs for a procedure that opts in. */
  private def unplayable(procedure: StartableRef, tree: Operation,
      state: ReadyGame, powers: WalkerPowers): Option[OathViolation] =
    if !WalkerProcedureRegistry.requiresPlayableOption(procedure) then None
    else ProcedureWalker.advance(state, tree, None, powers,
        WalkerDice.placeholder).toOption.collect {
        case WalkerOutcome.Parked(pending, _) => pending
      }.flatMap(pending =>
        WalkerSimulation.previewParked(state, tree, pending, powers) match
          case Right(options) if options.exists(_.outcome.isRight) => None
          case Right(_) => Some(OathViolation.NoPlayableOption(
            procedure.key))
          case Left(violation) => Some(violation))
```

Check `WalkerSimulation.previewParked`'s parameter list; pass only the four arguments `OathRulesWalker.requirePlayableOption` passes.

- [ ] **Step 6: The offer**

Create `src/main/scala/oathdigital/gameplay/walker/FollowUpOffer.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.model._

/** A follow-up entry's question (walker-driven game loop design, ruling 6):
  * the action, for no Supply, or Skip. The driver starts it once it has
  * dequeued an `Offer` entry, which `walkerEntry` then holds.
  *
  * The action is unavailable, with its reason, when its start would be
  * refused (`QueuedStart`). Taking it puts the entry, as a `Run`, in front
  * of the queue (plan-time ruling 1), and the driver starts it when this
  * walk ends. Skip ends the walk; `OathRulesWalker` then writes
  * `FollowUpSkipped` for the Game Log. The tree runs nothing before its
  * decision, so a resume rebuilds the same question.
  */
object FollowUpOffer:
  val decisionId: String = "follow-up.offer"
  val take: DecisionOptionRef.Button = DecisionOptionRef.Button("follow-up.take")
  val skip: DecisionOptionRef.Button = DecisionOptionRef.Button("follow-up.skip")

  def build(phasePowers: PhasePowers, powers: WalkerPowers)(
      catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    for
      _ <- Either.cond(args.isEmpty, (), OathViolation.InvalidEventOrder(
        "the follow-up offer takes no start selection"))
      entry <- ready.game.current.walkerEntry
        .filter(_.kind == QueueKind.Offer)
        .toRight(OathViolation.InvalidEventOrder(
          "no follow-up offer was dequeued"))
    yield
      val run = entry.copy(kind = QueueKind.Run)
      val named = action(entry.procedure)
      val shown = DecisionOption.Button(take, s"$named for no Supply")
      val offered = QueuedStart.refusal(catalog, ready, run, powers,
          phasePowers)
        .fold[DecisionOption](shown)(violation => DecisionOption.Unavailable(
          shown, OptionReason.Written(reason(entry.procedure, violation))))
      Sequence(
        Decide(decisionId, entry.actor, DecisionQuery.ChooseOne(
          Vector(offered, DecisionOption.Button(skip, "Skip")),
          heading = Some(sourceName(catalog, entry.source)
            .fold(named)(name => s"$named · $name")))),
        Branch((_, pending) =>
          if picked(pending, take) then
            Vector(QueueProcedure(run, atFront = true))
          else Vector.empty))

  /** Whether `event` records the Skip answer. */
  def skips(event: OathEvent): Boolean = event match
    case WalkerStepRecorded(_, ChoicePayload(`decisionId`,
        DecisionAnswer.ChooseOneAnswer(`skip`), _), _, _) => true
    case _ => false

  private def picked(pending: PendingTree, ref: DecisionOptionRef): Boolean =
    pending.answered.exists:
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(answer), _) =>
        answer == ref
      case _ => false

  /** "Campaign", "Travel": the action as the option names it. */
  private def action(procedure: StartableRef): String =
    procedure.key.capitalize

  /** Why the entry cannot start, as each action words it. A Campaign shows
    * its refusal's own text, or names the missing target. */
  private def reason(procedure: StartableRef, violation: OathViolation)
      : String = (procedure, violation) match
    case (ActionRef.Campaign, OathViolation.CampaignUnavailable(detail))
        if detail == CampaignProcedure.NothingToAttack =>
      "No site to conquer and no enemy pawn to raid"
    case (ActionRef.Campaign, OathViolation.CampaignUnavailable(detail)) =>
      detail
    case (ActionRef.Travel, _) => "No site to travel to"
    case _ => "Cannot start now"

  private def sourceName(catalog: ExecutableCatalog,
      source: Option[PowerSourceRef]): Option[String] = source.flatMap:
    case PowerSourceRef.Card(id: DenizenId) => catalog.denizen(id).map(_.name)
    case PowerSourceRef.Card(id: RelicId) => catalog.relic(id).map(_.name)
    case PowerSourceRef.Site(id) => catalog.site(id).map(_.name)
    case _ => None
```

Wrap the two long `val` lines at 80 characters. `CampaignProcedure.NothingToAttack` is what `LegalActionProjector.followUpReason` compares today; check its name there.

- [ ] **Step 7: Register it**

In `WalkerProcedureRegistry.scala`:

- `build` and `rebuild` take a last parameter `walkerPowers: WalkerPowers = WalkerPowers.empty` and pass it to `lookup`;
- `lookup` takes `walkerPowers: WalkerPowers = WalkerPowers.empty` and gains the arm `case TriggeredProcedureRef.FollowUpOffer => Right(followUpOfferEntry(powers, walkerPowers))`;
- add:

  ```scala
  /** The follow-up offer prices its action by a dry run of the action's
    * start, which needs the walk's powers. It is started by the driver, so
    * it has no fallback kind, no modifier window and no start selection. */
  private def followUpOfferEntry(phasePowers: PhasePowers,
      powers: WalkerPowers): Entry = Entry(
    fallbackKind = None,
    rollDecisionId = None,
    modifierWindow = None,
    build = FollowUpOffer.build(phasePowers, powers),
    rebuild = FollowUpOffer.build(phasePowers, powers))
  ```

- `isRegistered` also accepts `TriggeredProcedureRef.FollowUpOffer`.

In `WalkerProcedureRegistrySuite`, wherever `ForcedWake` is listed as built outside `entries`, list `FollowUpOffer` beside it.

Route the builds that need it:

- `OathRulesWalker.buildWalker`: add `TriggeredProcedureRef.FollowUpOffer` to both `UsePower | ForcedWake` patterns, and pass `walkerPowers = walkerPowers` to both registry calls.
- `WalkerDecisionProjector.tree`: add `TriggeredProcedureRef.FollowUpOffer` to its `UsePower | ForcedWake` pattern, and pass `walkerPowers = WalkerPowers.selected(walkerPowerCatalog, Vector.empty)`.

- [ ] **Step 8: Skip's replay and line**

In `WalkerReplay.scala`, the `FollowUpSkipped(entry)` arm becomes:

```scala
      // The offer's walk already took the entry; Skip only anchors the
      // Game Log line.
      case FollowUpSkipped(entry) =>
        Either.cond(ready.game.current.walkerEntry.contains(entry), ready,
          OathViolation.InvalidEventOrder(
            "a skip does not name the offered entry"))
```

In `OathRulesWalker.walkerTransition`, the `Finished` arm becomes:

```scala
    case WalkerOutcome.Finished(_, steps) =>
      GameplayTransition(state, steps ++ offerSkipped(state, procedure,
          steps) :+ WalkerCompleted(procedure))(evolve)
        .flatMap(recordCardPlayFallback(_, procedure, steps))
        .flatMap(next(_, Ended.Walk(procedure)))
```

and add:

```scala
  /** The Skip line's anchor when a follow-up offer ends on Skip
    * (walker-driven game loop design, plan-time ruling 5). */
  private def offerSkipped(state: OathState, procedure: ProcedureRef,
      steps: Vector[OathEvent]): Vector[OathEvent] = state match
    case Ready(ready) if procedure == TriggeredProcedureRef.FollowUpOffer &&
        steps.exists(FollowUpOffer.skips) =>
      ready.game.current.walkerEntry.map(FollowUpSkipped(_)).toVector
    case _ => Vector.empty
```

- [ ] **Step 9: The driver dequeues**

In `OathRulesWalker.scala`:

- `startWalker`'s `Ready` arm becomes `case Ready(ready) => startNow(state, ready, procedure, requester, startArgs)`, and its doc drops the sentence about the queue;
- delete `startFollowUp` and `skipFollowUp`;
- add:

  ```scala
  /** Starts a dequeued `Run` entry for its actor, on the state its
    * `ProcedureDequeued` left, so the cost waiver and the phase exemption
    * see it. A start that fails refuses the whole command. */
  private[gameplay] def startQueued(transition: OathTransition,
      entry: QueuedProcedure): Either[OathViolation, OathTransition] =
    transition.state match
      case Ready(ready) => startNow(transition.state, ready, entry.procedure,
          entry.actor, entry.startArgs)
        .map(started => started.copy(
          events = transition.events ++ started.events))
      case _ => Left(GameNotStarted)
  ```

In `OathDriver.scala`:

- delete `Ended.Skip` and its doc;
- the queue arm of `next` becomes `case Ready(ready) if ready.game.current.queue.nonEmpty => dequeue(transition, ready.game.current.queue.head)`;
- add:

  ```scala
  /** A queued head starts at once: a `Run` as its procedure, an `Offer` as
    * the question whose answer may queue it as a `Run`. */
  private def dequeue(transition: OathTransition, entry: QueuedProcedure) =
    append(transition, ProcedureDequeued(entry)).flatMap(taken =>
      entry.kind match
        case QueueKind.Run => startQueued(taken, entry)
        case QueueKind.Offer => startTriggered(taken,
          TriggeredProcedureRef.FollowUpOffer))
  ```

- import `ProcedureDequeued` from `gameplay.walker`;
- the class doc's first paragraph: "which starts nothing while a result is set, and otherwise starts the queue's head. With an empty queue it runs, in order:".

In `OathLifecycle.validateAct`, the comment above the phase check becomes "A queued action may run in any phase (walker-driven game loop design, ruling 9); its start has set `walkerEntry`."

Delete `OathViolation.FollowUpPending` from `GameViolation.scala` and its message from `GameRoutes.scala`.

- [ ] **Step 10: Run the rules tests**

Run: `./sbtw "testOnly oathdigital.gameplay.FollowUpRulesSuite oathdigital.gameplay.OathDriverSuite"`
Expected: the build fails in `application`, `shared` and `frontend` until Step 11. Do Step 11, then rerun: PASS.

- [ ] **Step 11: The service, the commands and the panel**

- `GameApplicationService`: delete the follow-up gate (the `case` that returns `FollowUpPending`), the `SkipFollowUp` route and `answersFollowUp`. The walker-pending gate now covers a waiting offer.
- `GameCommands.scala`: delete `SkipFollowUp` and its `playerOf` arm. `Authorization.scala`: delete `skipFollowUp`. `GameIntentMapper.scala`: delete the `Intent.SkipFollowUp` arm.
- `shared/.../CommandIntents.scala`: delete `case object SkipFollowUp`. `CommandIntentCodec.scala` and `CommandIntentDecoders.scala`: delete its arms.
- Delete `frontend/.../FollowUpControls.scala` and `FollowUpControlsSuite.scala`. In `ActionDecisionRenderer.scala`, delete the `followUpPending` value and the `if followUpPending then FollowUpControls.render(...)` it guards. The offer is drawn as any parked choose-one is.
- `src/test/scala/oathdigital/testkit/SituationDriver.scala`: delete the `SkipFollowUp` arm.
- `PendingWalkerInvariantSuite`: delete `GameCommand.SkipFollowUp(actor)` from its command list. `CommandRejectionMessageSuite`: delete its `FollowUpPending` case. `CommandProtocolSuite`: delete the `skipFollowUp` round trip.
- Any match over `TriggeredProcedureRef` the compiler now reports as non-exhaustive gets `FollowUpOffer` in the arm that holds `ForcedWake`. In the Game Log, that means no name, no section and no line.

- [ ] **Step 12: Rewrite the remaining follow-up suites**

Each of these now sees the offer parked where it saw an entry waiting.

`KnightsErrantSuite`: `owed` is the `Offer` entry. `followUps(transition)` becomes `offered(transition) = FollowUpPlay.offered(transition.state)`.

- "the Muster offers a Campaign owed to its player": `offered(mustered) == Some(owed)`, and the warbands line stays.
- "without the selection the Muster offers nothing": `offered(...) == None`, and `walkerPending == None`.
- "the offer is unconditional: nothing to campaign against still offers it": `offered(...) == Some(owed)`.
- "the taken Campaign sees the warbands the Muster gained and spends no Supply": `FollowUpPlay.taken(rules, mustered.state, p1)` in place of `rules.startWalker(…)`; the assertions stay.
- "Vow of Peace shows on the offer, and Skip clears it": the take is refused (`isLeft`); the parked take option is `Unavailable` with `OptionReason.Written("Vow of Peace prevents its ruler from campaigning")` (read it as `FollowUpRulesSuite.options` does); then `FollowUpPlay.skipped` leaves `walkerPending == None` and an empty `queue`.

`HuntingPartySuite` and `SecondWindSuite`: where a test reads `queue` after the origin, read `FollowUpPlay.offered` (the head) and `queue` (the entries behind it). For Second Wind, "a won Campaign offers a Travel, then a Campaign": `offered == Some(travel)` and `queue == Vector(campaign)`. "skipping the Travel still offers the Campaign" uses `FollowUpPlay.skipped`. "the queued Campaign offers it again" takes the Campaign through `FollowUpPlay.taken`.

`FollowUpReplaySuite`:

- the "skip takes the head" test becomes "a skip names the offered entry and changes nothing": from `Table.start.offering(entry)`, `FollowUpSkipped(entry)` leaves the state unchanged, and `FollowUpSkipped(other)` is refused with "a skip does not name the offered entry";
- the "start or skip that names another entry" test keeps only the dequeue half.

`FollowUpSupplyWaiverSuite`: the queued starts go through `Table.offering(entry)` and `FollowUpPlay.taken`. The Recover test of Task 2 becomes: from `base.offering(owed)`, `FollowUpPlay.taken` is accepted and the supply stays 0.

`FollowUpServiceSuite`: replace its tests with these three:

```scala
  /** A fresh service at p1's Act with a Campaign offer parked for p1: two
    * bandits rule Ancient City, so it can start. */
  private def offered(using munit.Location)
      : (GameApplicationService, InMemoryEventStreamRepository) =
    Table.start.pawn(p1, at = ancientCity).pawn(p2, at = "Broken Peaks")
      .pawn(p3, at = "Buried Giant").bandits(ancientCity, 2)
      .offering(campaign).service()

  private def answer(chosen: DecisionAnswer, by: PlayerId = p1) =
    GameCommand.ResolveWalker(by, TreeDecision(FollowUpOffer.decisionId,
      chosen))

  test("other commands wait for the offer"):
    assertEquals(offered._1.handle("game", 0L, GameCommand.StartWalker(
      ActionRef.Travel, StartPayload(p1))).left.toOption.collect {
        case CommandRejected(violation) => violation
      }, Some(OathViolation.InvalidEventOrder("a walker procedure is " +
        "pending; only walker resume commands are legal")))

  test("Skip is an answer, and writes FollowUpSkipped"):
    val skipped = offered._1.handle("game", 0L, answer(FollowUpPlay.skip))
    assertEquals(skipped.map(_.events.collect {
      case skip: FollowUpSkipped => skip }),
      Right(Vector(FollowUpSkipped(campaign))))

  test("Undo after taking the offer returns to the offer"):
    val (service, _) = offered
    val taken = accepted(service.handle("game", 0L, answer(FollowUpPlay.take)))
    val undone = accepted(service.handle("game", taken.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(FollowUpPlay.offered(undone.state), Some(campaign))
```

`campaign` is the `Offer` entry. Imports: `FollowUpPlay`, `FollowUpOffer`, `InMemoryEventStreamRepository` (where `Table.service` gets it), `TreeDecision`, `DecisionAnswer` and `Unwrap.accepted`. If the service names the stream differently from `"game"`, keep what the suite used. If the take command's state is not the Undo's target, read what `UndoServiceSuite` asserts and match it.

`LogScripts`:

- `queuedRaid` builds its table with `.offering(QueuedProcedure(p1, ActionRef.Campaign, Vector.empty, Some(PowerSourceRef.Card(knights)), QueueKind.Offer))` in place of the `followUps` update. Its first command is `GameCommand.ResolveWalker(p1, TreeDecision(FollowUpOffer.decisionId, FollowUpPlay.take))` in place of `StartWalker(Campaign)`.
- add `queuedRaidThenSkip`: the same table with a second identical `Offer` entry in `queue`, and an answer for the offer that takes while `park.ready.game.current.queue` is not empty and skips otherwise. Its first command is the same `ResolveWalker`.

`GameLogFollowUpSuite`:

- the two `queuedRaid` tests stay;
- "Skip writes its line in the latest section" uses `format(queuedRaidThenSkip, None)`: its last entry reads "Knights Errant: Campaign skipped." and its section is the Campaign's;
- add:

  ```scala
  test("taking the offer writes nothing"):
    val entries = format(queuedRaid, None)
    assertEquals(entries.takeWhile(entry => !text(entry)
      .startsWith("Campaign")).filter(_.kind == LogKind.Action), Vector.empty)
  ```

  If `LogKind` has no `Action` member, filter out the headline kinds instead.

- [ ] **Step 13: Run everything**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS. Expected count changes: `FollowUpControlsSuite` deleted, `FollowUpRulesSuite` and `FollowUpServiceSuite` rewritten, three tests added to `OathDriverSuite`, one to `GameLogFollowUpSuite`; the protocol and rejection suites lose their follow-up cases. Report the counts.

- [ ] **Step 14: Commit**

```bash
git add src/main/scala/oathdigital src/test/scala \
  shared/src/main/scala/oathdigital/protocol \
  shared/src/test/scala/oathdigital/protocol \
  frontend/src/main/scala/oathdigital/frontend \
  frontend/src/test/scala/oathdigital/frontend
git status --short
git commit -m "feat(walker): a follow-up is a queued offer the walker asks"
```

`git status --short` must show the deleted `FollowUpControls` files as staged deletions.

---

### Task 5: The follow-up projection goes

Between commands the queue is now always empty, so the projection's follow-up parts never show. They go.

**Files:**
- Modify: `src/main/scala/oathdigital/application/LegalActionProjector.scala`, `PendingProjector.scala`, `GameProjection.scala`, `ScopedProjectionContext.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/GameProjectionDto.scala`, `GameProjectionCodec.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala`
- Delete: `src/test/scala/oathdigital/application/FollowUpProjectionSuite.scala`
- Modify: `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/ViewerPresentationSuite.scala`

**Interfaces:**
- Consumes: Task 4 (no reader of `FollowUpProjection` is left in the frontend's panel).
- Produces: `GameProjection` without `followUp`; `LegalProjection(controls, minorActions)`.

- [ ] **Step 1: Delete the projection**

- `LegalActionProjector`: delete `followUpProjection`, `followUpStartable`, `actionLabel`, `sourceLabel`, the companion's `followUpReason`, the `followUps`/`queue` branch in `controls`, the `queue.isEmpty` term in `minor`, and the `followUp` parameter. Delete imports only they used (`ProcedureDequeued`, `ProcedureWalker`, `BannerRules`, and the `Ready` import if nothing else uses it).
- `ScopedProjectionContext`: `LegalProjection` loses `followUp`.
- `PendingProjector`: delete the `follow-up-pending` branch.
- `GameProjection`: `actionSelectionOpen` loses its `queue.isEmpty` term; delete `followUp = legal.followUp`.
- `GameProjectionDto.scala`: delete `FollowUpProjection` and the `followUp` field. `GameProjectionCodec.scala`: delete `"followUp"` from the key list, its encoder, decoder and the constructor argument.
- `ActionDecisionRenderer.status`: delete the `case None if value.followUp.nonEmpty` arm and the comment above it.

- [ ] **Step 2: Delete the tests**

Delete `FollowUpProjectionSuite.scala`. In `ProjectionProtocolSuite`, delete the follow-up round trips and the missing-key test. In `ViewerPresentationSuite`, delete the test that builds a `FollowUpProjection`, and its import.

- [ ] **Step 3: Run everything**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS, with the deleted tests fewer. Report the counts.

- [ ] **Step 4: Commit**

```bash
git add src/main/scala/oathdigital/application \
  shared/src/main/scala/oathdigital/protocol/projection \
  shared/src/test/scala/oathdigital/protocol \
  frontend/src/main/scala/oathdigital/frontend \
  frontend/src/test/scala/oathdigital/frontend \
  src/test/scala/oathdigital/application/FollowUpProjectionSuite.scala
git status --short
git commit -m "refactor(projection): drop the follow-up projection"
```

---

### Task 6: Docs, and the gates

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md`
- Modify: `docs/superpowers/specs/2026-10-08-follow-up-actions-design.md`
- Modify: `docs/superpowers/specs/2026-09-12-walker-ownership-and-phases-design.md`
- Modify: `docs/superpowers/specs/2026-10-09-operation-rewrite-design.md`
- Modify: `docs/architecture/follow-up-actions.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: The follow-up design**

In `docs/superpowers/specs/2026-10-08-follow-up-actions-design.md`, add after the **Status** paragraph:

```markdown
**Amended 2026-10-09** by the
[walker-driven game loop](2026-10-09-walker-game-loop-design.md), slice 1:
a follow-up is a queued offer the walker asks. Rulings 2, 3 and 6 and the
sections "Start and skip", "Cost waiver" and "Projection and UI" below are
rewritten to match. The rest stands.
```

Replace rulings 2, 3 and 6 with:

```markdown
2. **Opt-in after the origin ends.** The origin action completes. The walker
   then asks the player: the queued action, for no Supply, or Skip. Taking
   it starts the action at once, with its own modifier decision. No question
   is asked inside the origin action.
3. **Nothing in between.** The offer is a parked walker decision, so no
   other command runs until it is answered. "After X, you may Y" means Y
   right away.
```

```markdown
6. **The Act phase ending leaves the queue.** Nothing in the rules stops a
   Campaign after the Act phase (Sneak Attack campaigns off turn). When a run
   enters Rest (Special Envoy on a queued Travel, for example), the queue
   drains first, and a queued action's start skips the phase check.
```

Replace the section "Start and skip" with:

```markdown
### Start and skip

When a walk ends, the driver (`OathDriver.next`) takes the queue's head with
`ProcedureDequeued(entry)`, which holds it in `walkerEntry`, and starts the
follow-up offer (`FollowUpOffer`). The offer asks the entry's actor: the
action, or Skip.

- The action's option is priced by `QueuedStart.refusal`: the checks the
  start runs, on the state it would see. When it would be refused, the
  option is unavailable with its reason, and Skip stays available.
- Taking it puts the entry, as a `Run`, in front of the queue. The driver
  dequeues it and starts the action in the same command.
- Skip ends the offer's walk. `FollowUpSkipped(entry)` anchors its Game Log
  line.

A queued run completes like any action. The action boundary runs once the
queue is empty, so a chain gets one boundary, after its last action.
```

Replace the section "Cost waiver"'s first paragraph with:

```markdown
`FollowUpSupplyWaiver` is always offered and reads `walkerEntry`. When the
entry has a source, its `Transform` at the action's cost window removes every
`SpendSupply` by the entry's actor. Every walker path builds its powers
through `WalkerPowers`, so the waiver reaches the walk on start and on every
resume, the offer's dry run and any preview. Recover pays at `RecoverCost`.
```

Replace the section "Projection and UI" with:

```markdown
### Projection and UI

The offer is a parked walker decision: the frontend draws it as any
choose-one, with the reason on the unavailable option's hover. There is no
follow-up projection, control or panel.
```

- [ ] **Step 2: The ownership design**

In `docs/superpowers/specs/2026-09-12-walker-ownership-and-phases-design.md`:

Add at the start of "Completion and the action boundary":

```markdown
**Amended 2026-10-09** by the
[walker-driven game loop](2026-10-09-walker-game-loop-design.md): every
finished walk is handed to `OathDriver.next`. The boundary runs only when the
queue is empty, once an action has finished since the last one
(`boundaryDue`), and `completeAction` is gone. The text below describes the
boundary's steps, which are unchanged.
```

In "Triggered procedures", step 1, replace "Nothing nests; a procedure that needs sub-steps changes its own operations mid-walk through `Branch` and `BuildOps`." with:

```markdown
   Nest or queue (walker-driven game loop design, ruling 15): one
   procedure's steps are built inside another's tree, through `Branch`,
   `BuildOps` or a shared builder, when the enclosing procedure has steps
   after the inner part, or when the text says "as if". A nested part has
   no modifier decision and no boundary. An action is queued when the text
   says "after X, you may Y"; it is a full action with its own walk.
```

Add at the start of "Triggered procedures":

```markdown
**Amended 2026-10-09:** the driver, not the boundary, starts the Oathkeeper,
Forced Wake and follow-up offer walks (walker-driven game loop design, "The
driver").
```

- [ ] **Step 3: The operation rewrite design**

In `docs/superpowers/specs/2026-10-09-operation-rewrite-design.md`, under "Extension points (not built)", after the "Walker-level reach" bullet:

```markdown
- **Rewrites never reach a queued walk** (walker-driven game loop design,
  ruling 17). A deferral is resolved within its action and never crosses
  into a queued walk; walker-level reach covers nested subtrees only. A
  power that changes a later action uses a persistent rule, or queues its
  own entry.
```

- [ ] **Step 4: The architecture note**

Rewrite `docs/architecture/follow-up-actions.md`'s sections "The queue and its replay", "Start, skip and the service gate", "The Supply waiver" and "Projection and the panel", and its first paragraph's last-but-one sentence, to describe what Tasks 1-5 built:

- the queue: `QueuedProcedure(actor, procedure, startArgs, source, kind)`, `queue`, `walkerEntry`, `QueueProcedure(entry, atFront)`, `ProcedureDequeued`, `FollowUpSkipped`, and the wire spellings `queue-procedure`, `walker.procedure-dequeued` and `walker.follow-up-skipped`;
- start and skip: the driver dequeues the head; `FollowUpOffer` asks, priced by `QueuedStart.refusal`; taking puts a `Run` in front; Skip is an answer; no service gate;
- the waiver: reads `walkerEntry`, needs a source; Recover pays at `RecoverCost`;
- the panel: none; the offer is a parked choose-one.

In "Game Log": `ProcedureDequeued` opens the queued run's segment; the offer's run writes nothing; `FollowUpSkipped` is written inside the offer's walk and filed in the latest section.

In "Queueing another action": delete the bullets about `followUpStartable`, `FollowUpControls` and Recover's cost window. Keep the Muster and Trade tag bullet. Add: "give the entry its `startArgs` (a Trade's resource) where the power queues it."

In "Sneak Attack hook": `QueuedProcedure.actor` and the offer's owner are the hooks.

In "Decisions beyond the design": replace "Recover has no cost window." with "Recover pays at `RecoverCost`."; replace "`queuedAfter` holds labels, not a count." with "The offer is a walker decision, not a panel (walker-driven game loop, slice 1)."

Add the walker-driven game loop design to the note's design links.

- [ ] **Step 5: The roadmap and the spec's status**

In `docs/ROADMAP.md`, under "Phase - Walker-driven game loop", delete the line `- [ ] Slice 1: the queue and the driver, with every empty-queue hook.`

In the walker-driven game loop design, the **Status** line becomes: "designed 2026-10-09. Slice 1 landed: the queue, the driver and follow-up offers. Slice 2 is ready to plan."

- [ ] **Step 6: The gates**

Run, in order:

1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. Compare the test counts with the baseline and report every test added and deleted.

- [ ] **Step 7: Commit**

```bash
git add docs/superpowers/specs/2026-10-09-walker-game-loop-design.md \
  docs/superpowers/specs/2026-10-08-follow-up-actions-design.md \
  docs/superpowers/specs/2026-09-12-walker-ownership-and-phases-design.md \
  docs/superpowers/specs/2026-10-09-operation-rewrite-design.md \
  docs/architecture/follow-up-actions.md docs/ROADMAP.md
git commit -m "docs: record walker game loop slice 1"
```

Then run `ExitWorktree` with `keep`, merge into `main` from the main checkout (`git merge --ff-only <branch>`, or a merge commit when `main` moved), and do not push.
