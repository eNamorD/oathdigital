# Walker Ownership, Phases and Triggered Procedures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a parked walker decision be owned off-turn, let the walker park and resume in any phase, let the engine start procedures itself, run the action boundary after every action, and prove all of it by porting every Oathkeeper title change onto one triggered walker procedure.

**Architecture:** A sealed `ProcedureRef` hierarchy (`ActionRef`, `PhaseTransitionRef`, `TriggeredProcedureRef`) replaces `ActionRef` as the walker's procedure identity and decides whether the boundary runs. The active player stops being stored; the owner of a parked `Decide` is recomputed from the rebuilt tree on every command and authorizes, projects and continues. `OathkeeperRules.outcome` is the single title table, and a triggered `OathkeeperProcedure` performs every change through a new `SetOathkeeper` operation.

**Tech Stack:** Scala 2.13, sbt multi-project (root engine, `shared` cross-built protocol, `frontend` Scala.js), munit, ujson.

**Spec:** `docs/superpowers/specs/2026-09-12-walker-ownership-and-phases-design.md` (approved, `cbc6596`). Read it before any task; this plan argues from it.

## Global Constraints

- Full gate green at every commit: `./sbtw "test"`, plus `./sbtw "frontend/test" "frontend/fastLinkJS"` for any task touching `shared/` or `frontend/`, plus `python3 scripts/check-architecture.py` and `git diff --check`.
- Every production Scala file stays at or under 800 lines (`BackendArchitectureSuite`, "all production Scala files stay bounded"). Watch: `serialization/WalkerEventCodec.scala` 785, `gameplay/walker/ProcedureWalker.scala` 666, `gameplay/OathRulesWalker.scala` 412.
- `BackendArchitectureSuite` ("a walker power imports no engine, and the engine never learns its name") stays green without being weakened. No source under `gameplay/walker` or `gameplay/operations` names a specific power.
- Replay applies recorded operations only. No contribution, transform, restriction, validator or `OathkeeperRules.outcome` runs at replay.
- Old-journal compatibility is not a constraint. Changing a wire spelling, and rewriting fixtures to it, is expected.
- No registry flag decides behaviour. The procedure reference's family is the only input to "does the boundary run".
- A procedure belongs to `turn.activePlayer`; only a parked `Decide`'s owner may differ.
- `ReviewedPowerCatalog.AuditedCatalogFingerprint` must be recomputed in the same commit if the catalog's handler inventory changes (no task here is expected to change it).
- Work on branch `feat/walker-ownership`, cut from `feat/engine-redesign`. Commit per task with the message shown, ending with the `Co-Authored-By` trailer the session requires.
- Run a single suite with `./sbtw "testOnly <fully.qualified.Suite>"`; frontend suites with `./sbtw "frontend/testOnly <fully.qualified.Suite>"`.

## Two corrections to the spec's delivery order

The spec lists eight commits. Implementation needs two changes to that order; both are recorded here so a reviewer does not read them as drift.

1. **The Oathkeeper port (Task 7) lands before the boundary generalization (Task 8), not after.** If Take Wealth ran the boundary first, a tie during Wake would reach the legacy `OathkeeperRecipientChosen` path, which hardcodes `ActActionSelection` as its continuation and would send a Wake player to Act. Ported first, the triggered procedure continues through the phase's own continuation, so the boundary can move safely afterwards.
2. **`startTriggered` is proven by the real `TriggeredProcedureRef.Oathkeeper`, not a synthetic triggered procedure.** `TriggeredProcedureRef` is sealed, so a test cannot declare its own member. Task 4 declares the family empty, Task 7 adds its one member together with `startTriggered`, and the synthetic tree seam (`OathRules.walkerTree`) covers the shapes the real tree cannot reach.

The spec's `SetOathkeeper` also becomes its own task (Task 6), because a reviewer can accept the operation while rejecting the procedure.

## File map

| File | Responsibility | Tasks |
|---|---|---|
| `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` (new) | Operation encode/decode, moved out of `WalkerEventCodec` | 1, 6 |
| `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala` | Walker event encode/decode, procedure family tags, `Answered.by` | 1, 3, 4 |
| `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala` | `PowerCtx.activePlayer` | 2 |
| `src/main/scala/oathdigital/model/PendingTree.scala` | `PendingTree(at, answered)`, `Answered(decisionId, answer, by)` | 3 |
| `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala` | Actor-free walker events, `ChoicePayload.by`, `procedure` field | 3, 4 |
| `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` | Owner check against `Answered.by`; `awaitedPlayer` | 2, 3, 5 |
| `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala` | Replay without stored actor | 3, 4 |
| `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` | Requester checks, phase gate removal, `startTriggered`, boundary by family | 3, 4, 5, 7, 8 |
| `src/main/scala/oathdigital/model/ProcedureRef.scala` (renamed from `ActionRef.scala`) | The three families | 4, 7 |
| `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` (renamed) | One map keyed by `ProcedureRef` | 4, 7 |
| `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` | Awaited-player projection and `waiting` | 3, 4, 5 |
| `shared/.../projection/ActionProjectionDtos.scala`, `GameProjectionDto.scala`, `GameProjectionCodec.scala` | `WalkerWaitingProjection`, deletion of `OathkeeperRecipientProjection` | 5, 7 |
| `frontend/.../WalkerPanelSupport.scala`, `ActionDecisionRenderer.scala`, `package.scala` | Waiting notice, generic choose-one panel, legacy block deletion | 5, 7 |
| `src/main/scala/oathdigital/gameplay/operations/CoreOperations.scala`, `OperationStateMutation.scala`, `OperationError.scala` | `SetOathkeeper` | 6 |
| `src/main/scala/oathdigital/gameplay/oathkeeper/OathkeeperRules.scala` (new) | `OathkeeperOutcome` and the title table | 7 |
| `src/main/scala/oathdigital/gameplay/oathkeeper/OathkeeperProcedure.scala` (new) | The triggered tree | 7 |
| `src/main/scala/oathdigital/gameplay/OathRules.scala` | `completeAction`'s Oathkeeper step, dead-guard deletion | 7, 8 |
| `src/main/scala/oathdigital/gameplay/StateBasedEvaluation.scala` | Loses `afterAction`, `chooseRecipient` and three evolve cases | 7 |
| `src/test/scala/oathdigital/gameplay/oathkeeper/` (new) | `OathkeeperFixture`, `OathkeeperRulesSuite`, `OathkeeperProcedureSuite` | 7, 8 |

---

### Task 1: Codec headroom

**Files:**
- Create: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala:164-731`

**Interfaces:**
- Produces: `private[serialization] trait WalkerOperationCodec { this: GameEventJsonSupport => protected final def encodeOperation(operation: CoreOperation): ujson.Value; protected final def decodeOperation(value: ujson.Value, path: String): Either[WireError, CoreOperation]; protected final def decodeSignedInt(value: ujson.Value, path: String): Either[WireError, Int] }`, mixed into `WalkerEventCodec`. Task 6 adds `SetOathkeeper`'s spelling here.

This is a pure move. Its acceptance criterion is the one batch-1 Task 1b used: no test file changes.

- [x] **Step 1: Record the baseline.**

Run: `wc -l src/main/scala/oathdigital/serialization/WalkerEventCodec.scala && ./sbtw "testOnly oathdigital.serialization.GameEventWireSuite"`
Expected: `785` lines; suite PASS.

- [x] **Step 2: Move the operation codec.** Create `WalkerOperationCodec.scala` holding, verbatim and in their current order, `encodeOperation` (line 164) through `decodeOrientation` (line 726), plus `decodeSignedInt` (line 738) and `decodeOptionalSignedInt` (line 562) if nothing outside the moved block still calls them. Start the file with:

```scala
package oathdigital.serialization

// imports: copy exactly the imports the moved methods need from
// WalkerEventCodec.scala's header, and remove any the event codec no longer uses.

/** Operation spellings for recorded walker steps, split out of
  * `WalkerEventCodec` to give it headroom under the 800-line bound.
  *
  * A pure move: every spelling is byte-identical to the version before the
  * split, which `GameEventWireSuite` pins without being edited.
  */
private[serialization] trait WalkerOperationCodec {
    this: GameEventJsonSupport =>
  import GameEventWire._
  import WireError._
```

Change only the visibility of the three members shared with the event codec, to `protected final def encodeOperation`, `protected final def decodeOperation`, and `protected final def decodeSignedInt`; `decodeSignedInt` is shared because event delta decoding remains outside the moved block and still calls it. Everything else stays `private`. In `WalkerEventCodec.scala`, declare:

```scala
private[serialization] trait WalkerEventCodec extends WalkerOperationCodec {
    this: GameEventJsonSupport =>
```

- [x] **Step 3: Verify it is a pure move.**

Run: `./sbtw "test" && git diff --stat -- src/test shared/src/test frontend/src/test && wc -l src/main/scala/oathdigital/serialization/Walker*Codec.scala`
Expected: all tests PASS; the `git diff --stat` prints nothing; `WalkerEventCodec.scala` is under 250 lines and `WalkerOperationCodec.scala` under 800.

- [x] **Step 4: Run the architecture check.**

Run: `python3 scripts/check-architecture.py && git diff --check`
Expected: PASS, no output from `diff --check`.

- [x] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala src/main/scala/oathdigital/serialization/WalkerEventCodec.scala
git commit -m "refactor(walker): move operation spellings out of the event codec"
```

#### What Task 1 settled

Landed as `25a8a69` (base `9e27824`; the implementer's initial commit `a3fa18b`
was amended in a fix round to address the ruling below). A follow-up,
user-requested comment-only cleanup moving the operation codec's scaladoc
beside the code it documents landed separately as `94cf42e`.

Ruling: `decodeSignedInt` stays `protected final` in `WalkerOperationCodec`
and is inherited by `WalkerEventCodec` — the plan's Step 2 named
`decodeSignedInt` as one of the three members shared with the event codec
while also saying "everything else stays `private`," a contradiction the
user resolved in favor of the shared, non-private modifier; the plan text
was corrected in the same commit. Fix round 1/5: 1 addressed, 0 left open.
No other plan defect; review otherwise clean.

---

### Task 2: Rename `actor` to `activePlayer` on the walker and power surfaces

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala:11-20` (`PowerCtx`)
- Modify: every `PowerCtx(` construction site and every `ctx.actor` reader (list with the command in Step 1)
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (`WalkCtx.actor`), `WalkerPowerGather.scala`
- Modify: the `actor` parameter of `WalkerActionRegistry.build`/`rebuild` and of `RecoverProcedure`, `ForgeProcedure`, `TravelProcedure`, `TakeWealthProcedure`, `EndWakeProcedure` `build`/`rebuild`/`candidates`
- Test: every suite the compiler names

**Interfaces:**
- Produces: `PowerCtx(state: ReadyGame, activePlayer: PlayerId, source: RuleSourceRef, window: PowerWindow, nodePath: Vector[String], operation: Operation)`; `WalkCtx.activePlayer`; `build(catalog, state, activePlayer: PlayerId, args)` on every procedure.

Mechanical, no behaviour change. Stored copies (`PendingTree.actor`, walker event `actor` fields) are **not** renamed here: Task 3 deletes them.

- [x] **Step 1: List the sites.**

Run:
```bash
grep -rn "ctx\.actor\|PowerCtx(\|WalkCtx(\|actor = ctx\.actor" --include='*.scala' src/main src/test
grep -rn "actor: PlayerId" --include='*.scala' src/main/scala/oathdigital/gameplay/walker src/main/scala/oathdigital/gameplay/powerresolver src/main/scala/oathdigital/gameplay/powers src/main/scala/oathdigital/gameplay/actions/recover src/main/scala/oathdigital/gameplay/actions/forge src/main/scala/oathdigital/gameplay/actions/travel src/main/scala/oathdigital/gameplay/phases/wake
```

- [x] **Step 2: Rename.** In `PowerCtx`, rename the field to `activePlayer` and document it:

```scala
final case class PowerCtx(
    state: ReadyGame,
    /** The player whose procedure is running -- the traveller, the recoverer,
      * the taker. Not the power's activator (only the active player selects
      * powers) and not a parked decision's owner, which a contribution hooked
      * on a `Decide` reads from `operation`.
      */
    activePlayer: PlayerId,
    source: RuleSourceRef,
    window: PowerWindow,
    nodePath: Vector[String],
    /** Exact windowed operation a contribution is being collected for. */
    operation: Operation)
```

Rename every `ctx.actor` to `ctx.activePlayer`, `WalkCtx.actor` to `WalkCtx.activePlayer`, and the listed `actor: PlayerId` parameters (and their named-argument call sites such as `actor = actor` in `WalkerActionRegistrySuite`) to `activePlayer`. Leave `PendingTree.actor`, `pending.actor`, and the `actor` fields of `WalkerStepRecorded`, `WalkerParked` and `WalkerCompleted` untouched.

- [x] **Step 3: Compile and test.**

Run: `./sbtw "test"`
Expected: PASS with the same test count as Task 1.

- [x] **Step 4: Verify nothing was missed.**

Run: `grep -rn "ctx\.actor" --include='*.scala' src/main src/test`
Expected: no output.

- [x] **Step 5: Commit**

```bash
git add -A src/main src/test
git commit -m "refactor(walker): call the procedure's player activePlayer"
```

#### What Task 2 settled

Landed as `f2c1ab3`. Mechanical rename, no ruling required and no plan
defect found; every listed site renamed and the `ctx\.actor` sweep in
Step 4 came back empty.

---

### Task 3: Stop storing the active player; record who answered

**Files:**
- Modify: `src/main/scala/oathdigital/model/PendingTree.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala:126-190, 555-600`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` (`startWalker`, `resolveWalker`, `rollWalkerPrepared`, `walkerResumeContext`, `walkerTransition`, `parkedContinue`)
- Modify: `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala`
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala:357-359`, `WalkerDecisionProjector.scala:52-100`
- Test: `src/test/scala/oathdigital/gameplay/OathRulesWalkerPowerSuite.scala`, `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`, plus every suite constructing the changed types

**Interfaces:**
- Consumes: Task 2's `WalkCtx.activePlayer`.
- Produces:
  - `final case class PendingTree(at: Vector[String], answered: Vector[Answered])`
  - `final case class Answered(decisionId: String, answer: DecisionAnswer, by: PlayerId)`
  - `final case class ChoicePayload(decisionId: String, answer: DecisionAnswer, by: PlayerId)`
  - `WalkerStepRecorded(nodeId: String, payload: WalkerStepPayload, ops: Vector[CoreOperation], contributions: Vector[PowerId])`
  - `WalkerParked(action: ActionRef, at, answered, modifiers, startArgs)`; `WalkerCompleted(action: ActionRef)` (Task 4 renames `action` to `procedure`)
  - `OathRulesWalker.startWalker(state: OathState, action: ActionRef, requester: PlayerId, modifiers: Vector[PowerId] = Vector.empty, startArgs: Vector[DecisionOptionRef] = Vector.empty)`
  - `OathRulesWalker.resolveWalker(state: OathState, requester: PlayerId, decisionId: String, answer: DecisionAnswer): Either[OathViolation, OathTransition]`
  - `OathRulesWalker.rollWalkerPrepared(state: OathState, requester: PlayerId, pool: PoolKey)(prepareFaces)`, unchanged signature

Behaviour is unchanged: every owner still equals the active player. What changes is where each identity lives.

- [x] **Step 1: Write the failing tests** in `OathRulesWalkerPowerSuite`:

```scala
  test("startWalker rejects a requester who is not the active player before " +
      "building anything") {
    val (ready, actor) = actable
    val intruder = ready.game.current.players.map(_.player).find(_ != actor).get
    // The injected tree would build and park for anyone, so a rejection here
    // is the walker's own requester check and not a procedure's gate.
    assertEquals(
      rules(actor, WalkerPowers.empty).startWalker(Ready(ready),
        ActionRef.Recover, intruder),
      Left(OathViolation.WrongPlayer(actor, intruder)))
  }

  test("a resolved answer records who answered, on the step and in pending") {
    val (ready, actor) = actable
    val started = rules(actor, WalkerPowers.empty)
      .startWalker(Ready(ready), ActionRef.Recover, actor).toOption.get
    val resumed = rules(actor, WalkerPowers.empty).resolveWalker(started.state,
      actor, RecoverProcedure.choiceDecisionId,
      DecisionAnswer.ChooseOneAnswer(ProcedureWalkerSuite.continueOption))
      .toOption.get
    assert(resumed.events.exists {
      case WalkerStepRecorded(_, ChoicePayload(_, _, by), _, _) => by == actor
      case _ => false
    })
    val Ready(after) = resumed.state: @unchecked
    assertEquals(after.game.current.walkerPending.map(_.answered.map(_.by)),
      Some(Vector(actor)))
  }
```

Update the existing C1 tests in the same suite to the new `resolveWalker(state, requester, decisionId, answer)` signature. Their expected value, `Left(OathViolation.WrongPlayer(actor, intruder))`, does not change: the owner is still the actor.

- [x] **Step 2: Run the tests to verify they fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.OathRulesWalkerPowerSuite"`
Expected: FAIL to compile (`ChoicePayload` has two fields; `resolveWalker` takes an `Answered`).

- [x] **Step 3: Change the model and events.**

`PendingTree.scala`:
```scala
/** One recorded answer to a parked walker decision.
  *
  * @param by the player who submitted it: the authorized requester, which
  *   equals the parked `Decide`'s owner. Journalled so log lines can name who
  *   chose from the payload alone; replay does not re-derive the owner.
  */
final case class Answered(decisionId: String, answer: DecisionAnswer, by: PlayerId)

/** Parked walker position. Stores only a pointer into the action: `at` and the
  * decisions already `answered`. The player the procedure belongs to is not
  * stored: it is always `turn.activePlayer`, and nothing a walk does changes it.
  */
final case class PendingTree(at: Vector[String], answered: Vector[Answered])
```

`WalkerEvents.scala`: remove `actor` from `WalkerStepRecorded`, `WalkerParked` and `WalkerCompleted`; add `by: PlayerId` as the last field of `ChoicePayload`, documented as "who answered".

- [x] **Step 4: Change the walker.** In `ProcedureWalker`:
  - `advance`, `roll`, `resolve` build `WalkCtx(base, Vector.empty, state.game.current.turn.activePlayer, pending.fold(Vector.empty[Answered])(_.answered), powers)`. There is no `pending.actor` to read.
  - `answerDecide` checks the answer's submitter against the rebuilt owner:

```scala
      _ <- Either.cond(decide.owner == answer.by, (),
        OathViolation.WrongPlayer(decide.owner, answer.by))
```

  and records `ChoicePayload(answer.decisionId, answer.answer, answer.by)` on a `WalkerStepRecorded(nodeId = …, payload = …, ops = Vector.empty, contributions = …)`.
  - `recordRoll` and the delta step construct `WalkerStepRecorded` without `actor`.
  - Wherever `WalkerOutcome.Parked` builds a `PendingTree`, build `PendingTree(at, answered)`.

- [x] **Step 5: Change replay.** In `WalkerReplay.applyRecordedReady`:
  - Delete `validateActor`. `validateStep` keeps only the node-id check.
  - `validateParkedStep` drops the `pending.actor == step.actor` check.
  - The `ChoicePayload(decisionId, payload, by)` arm appends `Answered(decisionId, payload, by)`.
  - `WalkerParked(action, at, answered, modifiers, startArgs)` restores `PendingTree(at, answered)`. `WalkerCompleted(action)` is otherwise unchanged.

- [x] **Step 6: Change the command surface.** In `OathRulesWalker`:
  - `startWalker(state, action, requester, modifiers, startArgs)`: in the `Ready(ready)` branch's `for`, before `validateModifiers`, add
    ```scala
            _ <- Either.cond(requester == ready.game.current.turn.activePlayer,
              (), WrongPlayer(ready.game.current.turn.activePlayer, requester))
    ```
    and pass `ready.game.current.turn.activePlayer` wherever `actor` was passed.
  - `resolveWalker(state, requester, decisionId, answer)` builds `Answered(decisionId, answer, by = requester)` inside `resumeWalker` and passes it to `ProcedureWalker.resolve`. The owner check now happens in `answerDecide`.
  - `rollWalkerPrepared` checks `requester == ready.game.current.turn.activePlayer`.
    - *As implemented (Task 3 review):* the check runs **before** `resumeWalker`, through a private `requireActivePlayer(ready, requester)`. A roll park is always the active player's, so the check needs no tree. Inside `resumeWalker`, a restriction or a moved position would answer an off-turn roll first.
    - `startWalker` uses the same helper as the first step of its `for`.
    - `OathRulesWalkerPowerSuite` pins the order: "a non-active player's RollWalker is rejected before the tree is rebuilt".
  - `walkerResumeContext(state)` no longer takes a requester. It deletes `pending.actor == activePlayer` and `actor == pending.actor`, and **keeps** the `Phase.Act` gate (Task 5 removes it). It rebuilds with `turn.activePlayer`.
  - `walkerTransition` writes `WalkerParked(action, pending.at, pending.answered, modifiers, startArgs)` and `WalkerCompleted(action)`.
  - `parkedContinue` passes `ready.game.current.turn.activePlayer` to `continuationFor` (Task 5 passes the awaited player instead).

In `GameApplicationService.applyUnblockedCommand`:
```scala
      case GameCommand.ResolveWalker(actor, treeDecision) =>
        rules.resolveWalker(state, actor, treeDecision.decisionId,
          treeDecision.answer)
```

In `WalkerDecisionProjector.project`, replace every `pending.actor` with `context.current.turn.activePlayer` and keep the viewer gate against it.

- [x] **Step 7: Change the codec.** In `WalkerEventCodec`:
  - `WalkerStepRecorded`, `WalkerParked` and `WalkerCompleted` stop writing and reading `"actorPlayerId"`.
  - `encodeAnswered` writes `"byPlayerId" -> answered.by.value`, and `decodeAnswered` reads it.
  - `encodeStepPayload`'s `ChoicePayload` arm writes `"byPlayerId"`, and `decodeStepPayload` reads it.

- [x] **Step 8: Update every other construction site.**

Run: `grep -rn "PendingTree(\|Answered(\|ChoicePayload(\|WalkerStepRecorded(\|WalkerParked(\|WalkerCompleted(\|resolveWalker(" --include='*.scala' src/test`

Give each `Answered`/`ChoicePayload` the player who answers (the actor in every existing test), drop the actor from `PendingTree`, `WalkerStepRecorded`, `WalkerParked` and `WalkerCompleted`, and move `resolveWalker` calls to the new signature. In `GameEventWireSuite`'s "both generic walker decision answers round trip on the step and on the park", keep `player` as the `by` of both answers so the new field round-trips.

- [x] **Step 9: Run the full suite.**

Run: `./sbtw "test"`
Expected: PASS, including the two new tests.

- [x] **Step 10: Mutation check.** Temporarily change `answerDecide`'s check to `decide.owner == decide.owner` and run `./sbtw "testOnly oathdigital.gameplay.OathRulesWalkerPowerSuite"`.
Expected: FAIL in "a seated non-active player's ResolveWalker against another player's parked decision is rejected". Revert.

- [x] **Step 11: Commit**

```bash
git add -A src/main src/test
git commit -m "feat(walker): stop storing the active player and record who answered"
```

#### What Task 3 settled

Landed as `02e1357..877d7e4` (task range `f2c1ab3..877d7e4`; main content
commit `02e1357` "feat(walker): stop storing the active player and record
who answered"). Review found an off-turn-roll ordering gap not covered by
the plan's steps as written: `resolveWalker` could rebuild the tree before
checking that the answering player was the parked decision's owner. The
fix, rejecting an off-turn roll before rebuilding the tree, closes out
this same task as its final commit, `877d7e4` (`fix(walker): reject an
off-turn roll before rebuilding the tree`). No other plan defect; Steps
9-10 passed as written.

---

### Task 4: Procedure reference families

**Files:**
- Rename: `src/main/scala/oathdigital/model/ActionRef.scala` → `src/main/scala/oathdigital/model/ProcedureRef.scala`
- Rename: `src/main/scala/oathdigital/gameplay/walker/WalkerActionRegistry.scala` → `WalkerProcedureRegistry.scala`; `src/test/scala/oathdigital/gameplay/walker/WalkerActionRegistrySuite.scala` → `WalkerProcedureRegistrySuite.scala`
- Modify: `src/main/scala/oathdigital/model/GameState.scala:149` (`walkerAction` → `walkerProcedure`)
- Modify: `WalkerEvents.scala`, `WalkerReplay.scala`, `WalkerEventCodec.scala`, `OathRules.scala:363-380` (`WalkerTreeSource`), `OathRulesWalker.scala`
- Modify: `application/GameCommands.scala:12`, `GameIntentMapper.scala:56-60`, `GameApplicationService.scala:161-162, 354-356, 378`, `WalkerDecisionProjector.scala`
- Test: `src/test/scala/oathdigital/model/ActionValuesSuite.scala`, `WalkerProcedureRegistrySuite.scala`, `GameEventWireSuite.scala`, `EndWakeProcedureSuite.scala`

**Interfaces:**
- Consumes: Task 3's events.
- Produces:

```scala
sealed trait ProcedureRef extends Product with Serializable {
  def key: String
  def family: String
}
sealed trait StartableRef extends ProcedureRef
sealed trait ActionRef extends StartableRef { final def family = "action" }
sealed trait PhaseTransitionRef extends StartableRef {
  final def family = "phase-transition"
}
sealed trait TriggeredProcedureRef extends ProcedureRef {
  final def family = "triggered"
}
```

  - `ActionRef.all: Vector[ActionRef]` (Recover, Forge, Travel, TakeWealth); `PhaseTransitionRef.all` (EndWake); `TriggeredProcedureRef.all` (empty until Task 7); `StartableRef.all`, `ProcedureRef.all`.
  - `ActionRef.fromKey`, `StartableRef.fromKey(key): Option[StartableRef]`, `ProcedureRef.fromFamilyKey(family: String, key: String): Option[ProcedureRef]`.
  - `CurrentGameState.walkerProcedure: Option[ProcedureRef]`.
  - `WalkerParked(procedure: ProcedureRef, at, answered, modifiers, startArgs)`, `WalkerCompleted(procedure: ProcedureRef)`.
  - `WalkerProcedureRegistry.Entry(fallbackKind: Option[MajorActionKind], rollDecisionId: Option[String], modifierWindow: Option[PowerWindow], continuationFor: (String, PlayerId, DecisionId) => Option[OathContinue], build: (ExecutableCatalog, ReadyGame, PlayerId, Vector[DecisionOptionRef]) => Either[OathViolation, Operation], rebuild: <same>)`.
  - `WalkerProcedureRegistry.entries: Map[ProcedureRef, Entry]`; `build`, `rebuild`, `rollDecisionId`, `modifierWindow`, `continuationFor`, `isRegistered` take a `ProcedureRef`; `fallbackKind(procedure: StartableRef): Either[OathViolation, MajorActionKind]`.
  - `OathRules.WalkerTreeSource = (ExecutableCatalog, ProcedureRef, ReadyGame, PlayerId, Vector[DecisionOptionRef], Boolean) => Either[OathViolation, Operation]`.
  - `OathRulesWalker.startWalker(state, procedure: StartableRef, requester, modifiers, startArgs)`.
  - `GameCommand.StartWalker(procedure: StartableRef, start: StartPayload)`.

Behaviour-neutral. The boundary rule is **not** changed here: completion still reads the phases (Task 8 changes it).

- [x] **Step 1: Write the failing tests.**

In `ActionValuesSuite`:
```scala
  test("procedure references form three families with keys unique across all") {
    assertEquals(ActionRef.all.map(_.key),
      Vector("recover", "forge", "travel", "take-wealth"))
    assertEquals(PhaseTransitionRef.all.map(_.key), Vector("end-wake"))
    assertEquals(ProcedureRef.all.map(_.key).distinct.size,
      ProcedureRef.all.size)
    assertEquals(StartableRef.fromKey("end-wake"),
      Some(PhaseTransitionRef.EndWake))
    assertEquals(ActionRef.fromKey("end-wake"), None)
    assertEquals(ProcedureRef.fromFamilyKey("action", "end-wake"), None)
    assertEquals(ProcedureRef.fromFamilyKey("phase-transition", "end-wake"),
      Some(PhaseTransitionRef.EndWake))
    assertEquals(ProcedureRef.fromFamilyKey("triggered", "recover"), None)
  }
```

In `WalkerProcedureRegistrySuite`, replace "the production entries register every known action" with:
```scala
  test("the production entries register every procedure reference") {
    assertEquals(WalkerProcedureRegistry.entries.keySet, ProcedureRef.all.toSet)
  }
```

In `GameEventWireSuite`, add:
```scala
  test("a procedure reference round-trips with its family, and a reference " +
      "under the wrong family or an unknown family is rejected") {
    val events = Vector[OathEvent](
      WalkerParked(PhaseTransitionRef.EndWake, Vector("0"), Vector.empty,
        Vector.empty, Vector.empty),
      WalkerCompleted(ActionRef.Travel))
    val encoded = GameEventWire.encodeStream("families", catalogRef,
      events.zipWithIndex.map { case (event, index) =>
        RecordedEvent(index, event) }).toOption.get
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event),
      events)
    assert(ujson.read(encoded).arr.head.toString.contains(
      "\"family\":\"phase-transition\""))

    Vector("action", "made-up").foreach { family =>
      val tampered = encoded.replace("\"family\":\"phase-transition\"",
        s"\"family\":\"$family\"")
      assert(GameEventWire.decodeStream(tampered).isLeft,
        s"end-wake under family '$family' must not decode")
    }
  }
```

In `EndWakeProcedureSuite`, change the completion assertion to `WalkerCompleted(PhaseTransitionRef.EndWake)`.

- [x] **Step 2: Run them to verify they fail.**

Run: `./sbtw "testOnly oathdigital.model.ActionValuesSuite"`
Expected: FAIL to compile (`PhaseTransitionRef` not found).

- [x] **Step 3: Implement the references.** `git mv` the file and write `ProcedureRef.scala` with the hierarchy above:

```scala
object ActionRef {
  case object Recover extends ActionRef { val key = "recover" }
  case object Forge extends ActionRef { val key = "forge" }
  case object Travel extends ActionRef { val key = "travel" }
  case object TakeWealth extends ActionRef { val key = "take-wealth" }
  val all: Vector[ActionRef] = Vector(Recover, Forge, Travel, TakeWealth)
  def fromKey(key: String): Option[ActionRef] = all.find(_.key == key)
}
object PhaseTransitionRef {
  case object EndWake extends PhaseTransitionRef { val key = "end-wake" }
  val all: Vector[PhaseTransitionRef] = Vector(EndWake)
}
object TriggeredProcedureRef {
  val all: Vector[TriggeredProcedureRef] = Vector.empty
}
object StartableRef {
  val all: Vector[StartableRef] = ActionRef.all ++ PhaseTransitionRef.all
  def fromKey(key: String): Option[StartableRef] = all.find(_.key == key)
}
object ProcedureRef {
  val all: Vector[ProcedureRef] = StartableRef.all ++ TriggeredProcedureRef.all
  def fromFamilyKey(family: String, key: String): Option[ProcedureRef] =
    all.find(ref => ref.family == family && ref.key == key)
}
```

Carry over the existing doc comment on `ActionRef.all` about key bridging to `MajorActionKind`, and add a class doc per family taken from the spec's "Procedure references" bullets.

- [x] **Step 4: Re-key the registry.** `git mv` both registry files. Rename the object to `WalkerProcedureRegistry`, key `entries` by `ProcedureRef`, make `fallbackKind` optional, and move End Wake's key to `PhaseTransitionRef.EndWake`:

```scala
  def fallbackKind(procedure: StartableRef)
      : Either[OathViolation, MajorActionKind] =
    lookup(procedure, entries).flatMap(_.fallbackKind.toRight(
      OathViolation.InvalidEventOrder(
        s"walker procedure ${procedure.key} declares no fallback kind")))
```

Wrap every existing `fallbackKind = MajorActionKind.X` in `Some(...)`. Keep `lookup`'s message as `s"no walker action registered for ${procedure.key}"` so the existing registry-suite messages stay valid.

- [x] **Step 5: Thread the type through.**
  - `CurrentGameState.walkerAction` → `walkerProcedure: Option[ProcedureRef]`, and every reader and writer (`OathRulesWalker.startWalker`'s pending check, `walkerResumeContext`, `WalkerReplay`, `WalkerDecisionProjector.project`).
  - `WalkerParked`/`WalkerCompleted` field `action` → `procedure`.
  - `OathRules.WalkerTreeSource` and `declaredWalkerTree` take a `ProcedureRef`; `WalkerDecisionProjector.TreeSource` and `declaredTree` likewise.
  - `OathRulesWalker.startWalker`'s parameter becomes `procedure: StartableRef`. `buildWalker`, `walkerTransition`, `parkedContinue`, `resumeWalker` and `walkerResumeContext` carry a `ProcedureRef` (Task 7's `startTriggered` passes a `TriggeredProcedureRef` to `buildWalker` and `walkerTransition`).
  - `WalkerDecisionProjection(procedure.key, …)`.
  - `GameCommand.StartWalker(procedure: StartableRef, start)`; `GameIntentMapper` resolves the intent's key with `StartableRef.fromKey`, rejecting an unknown key with the same `IntentError` it uses today.
  - `GameApplicationService`: `GameCommand.EndWake(playerId)` routes to `rules.startWalker(state, PhaseTransitionRef.EndWake, playerId)`; `walkerAction(action: MajorActionKind)` keeps returning `Option[ActionRef]` via `ActionRef.fromKey`, and `isRegistered` accepts it as a `ProcedureRef`.

- [x] **Step 6: Change the codec's spelling.** In `WalkerEventCodec`, replace `decodeAction` with:

```scala
  private def encodeProcedure(procedure: ProcedureRef): ujson.Value =
    ujson.Obj("family" -> procedure.family, "key" -> procedure.key)

  private def decodeProcedure(value: ujson.Value,
      path: String): Either[WireError, ProcedureRef] = {
    val family = value("family").str
    val key = value("key").str
    ProcedureRef.fromFamilyKey(family, key).toRight(InvalidValue(path,
      s"unknown walker procedure '$family/$key'"))
  }
```

`WalkerParked` and `WalkerCompleted` write `"procedure" -> encodeProcedure(procedure)` in place of `"action"`, and decode it with `decodeProcedure(payload("procedure"), s"$path.procedure")`.

- [x] **Step 7: Update the remaining sites.**

Run: `grep -rn "ActionRef.EndWake\|walkerAction\|WalkerActionRegistry\|decodeAction\|\"action\"" --include='*.scala' src/main src/test shared/src frontend/src`

Fix each: `ActionRef.EndWake` → `PhaseTransitionRef.EndWake`; `walkerAction` → `walkerProcedure`; `WalkerActionRegistry` → `WalkerProcedureRegistry`. The frontend and shared intent `StartWalker(action: String, …)` keep their wire field name: a key is unique across families, so the intent needs no family tag.

- [x] **Step 8: Run the gates.**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS" && python3 scripts/check-architecture.py && git diff --check`
Expected: PASS.

- [x] **Step 9: Commit**

```bash
git add -A src shared frontend
git commit -m "feat(walker): name procedures by family, with End Wake as a phase transition"
```

#### What Task 4 settled

Landed as `266f2fb`. No plan defect and no review ruling; during its own
TDD process the implementer found and fixed two bugs in its own RED-phase
test text before GREEN (a tamper-string whitespace mismatch, and a stale
raw-journal assertion expecting the deleted `"action":"recover"` shape
instead of `"procedure":{"family":"action","key":"recover"}`) — both
self-caught, neither a plan or production defect.

---

### Task 5: Off-turn ownership, any-phase resume, and the waiting projection

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (add `awaitedPlayer`)
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` (`walkerResumeContext`, `parkedContinue`)
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`, `ScopedProjectionContext.scala:30-40`, `PendingProcedureProjector.scala:14-35`, `GameProjection.scala:155-160`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala`, `GameProjectionDto.scala:43`, `GameProjectionCodec.scala:19-22, 100, 163-169`
- Modify: `frontend/src/main/scala/oathdigital/frontend/package.scala:144`, `WalkerPanelSupport.scala`, `ActionDecisionRenderer.scala:339-342`
- Test: `OathRulesWalkerPowerSuite.scala`, `application/WalkerDecisionProjectorSuite.scala`, `application/WalkerDecisionQueryPowerSuite.scala`, `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala`

**Interfaces:**
- Consumes: Task 3's requester checks, Task 4's `ProcedureRef` and registry.
- Produces:
  - `ProcedureWalker.awaitedPlayer(state: ReadyGame, action: Operation, pending: PendingTree, powers: WalkerPowers): Option[PlayerId]`: the parked `Decide`'s owner, or `turn.activePlayer` for a parked `Roll`.
  - `WalkerDecisionProjector.waiting(context: ScopedProjectionContext): Option[WalkerWaitingProjection]`.
  - `shared`: `final case class WalkerWaitingProjection(playerId: String, heading: Option[String] = None)`; `GameProjection.walkerWaiting: Option[WalkerWaitingProjection] = None`.
  - `frontend`: `type WalkerWaitingState = protocol.projection.WalkerWaitingProjection`; `WalkerPanelSupport.waitingNotice(value: GameProjection): Option[String]`.

- [x] **Step 1: Write the failing engine tests** in `OathRulesWalkerPowerSuite`:

```scala
  /** A single decision owned by `owner`, which need not be the active player. */
  private def ownedTree(owner: PlayerId): Operation = Sequence(Decide(
    decisionId = RecoverProcedure.choiceDecisionId,
    owner = owner,
    query = DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(ProcedureWalkerSuite.continueOption, "Continue")))))

  private def ownedRules(owner: PlayerId): OathRules =
    new OathRules(catalog, walkerTree = (_, _, _, _, _, _) =>
      Right(ownedTree(owner)))

  private val continue = DecisionAnswer.ChooseOneAnswer(
    ProcedureWalkerSuite.continueOption)

  test("an off-turn decision is answered by its owner, and the active player " +
      "is rejected") {
    val (ready, actor) = actable
    val owner = ready.game.current.players.map(_.player).find(_ != actor).get
    val started = ownedRules(owner).startWalker(Ready(ready), ActionRef.Recover,
      actor).toOption.get
    assertEquals(started.continue, OathContinue.AwaitingRecoverRoll(owner,
      DecisionId(RecoverProcedure.choiceDecisionId)))
    assertEquals(ownedRules(owner).resolveWalker(started.state, actor,
      RecoverProcedure.choiceDecisionId, continue),
      Left(OathViolation.WrongPlayer(owner, actor)))
    val answered = ownedRules(owner).resolveWalker(started.state, owner,
      RecoverProcedure.choiceDecisionId, continue)
    assert(answered.isRight, s"the owner's answer must be accepted, got $answered")
  }

  test("a walker parks and resumes outside the Act phase") {
    val (act, actor) = actable
    val wake = act.copy(game = act.game.copy(current = act.game.current.copy(
      turn = act.game.current.turn.copy(phase = Phase.Wake))))
    val started = rules(actor, WalkerPowers.empty).startWalker(Ready(wake),
      ActionRef.Recover, actor).toOption.get
    assert(started.events.last.isInstanceOf[WalkerParked])
    val resumed = rules(actor, WalkerPowers.empty).resolveWalker(started.state,
      actor, RecoverProcedure.choiceDecisionId, continue)
    assert(resumed.isRight, s"a Wake resume must not be phase-gated, got $resumed")
  }

  test("a procedure completing in a phase with no walker continuation is " +
      "still a typed rejection") {
    val (act, actor) = actable
    val rest = act.copy(game = act.game.copy(current = act.game.current.copy(
      turn = act.game.current.turn.copy(phase = Phase.Rest))))
    val flat = new OathRules(catalog, walkerTree = (_, _, _, _, _, _) =>
      Right(Sequence(Vector.empty)))
    assertEquals(flat.startWalker(Ready(rest), ActionRef.Recover, actor),
      Left(OathViolation.InvalidEventOrder("a walker procedure completed in " +
        "the Rest phase, which has no walker continuation")))
  }
```

- [x] **Step 2: Run them to verify they fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.OathRulesWalkerPowerSuite"`
Expected: FAIL. The off-turn test gets `WrongPlayer` at the start continuation or the answer. The Wake test gets `Left(WrongPhase(Act, Wake))`. The Rest test already passes and stays as a guard.

- [x] **Step 3: Implement the engine half.**

In `ProcedureWalker`:
```scala
  /** Who a parked position waits on: a parked `Decide`'s owner, read off the
    * rebuilt and transformed node, or the active player for a parked `Roll`.
    * Never stored -- a power that changes an owner changes this answer on the
    * next command, and authorization, projection and continuation all read it.
    */
  def awaitedPlayer(state: ReadyGame, action: Operation, pending: PendingTree,
      powers: WalkerPowers): Option[PlayerId] =
    parkedDecide(state, action, pending, powers).map(_.owner).orElse(
      parkedRoll(state, action, pending, powers).map(_ =>
        state.game.current.turn.activePlayer))
```

In `OathRulesWalker.walkerResumeContext`, delete:
```scala
      _ <- Either.cond(ready.game.current.turn.phase == Phase.Act, (),
        WrongPhase(Phase.Act, ready.game.current.turn.phase))
```
Replace its doc sentence about the phase with: "No phase gate: only resume commands are accepted while a walker is pending, so nothing else can change the phase, and the procedure's own build passed its phase gates at start."

In `parkedContinue`, compute the awaited player once and pass it to `continuationFor`:
```scala
    val awaited = ProcedureWalker.awaitedPlayer(ready, tree, pending, powers)
      .getOrElse(ready.game.current.turn.activePlayer)
    def continuationFor(decisionId: String) =
      WalkerProcedureRegistry.continuationFor(procedure, decisionId, awaited,
        DecisionId(decisionId)).flatMap(_.toRight(InvalidEventOrder(
          "no client continuation is registered for walker decision " +
            decisionId)))
```

- [x] **Step 4: Run the engine tests.**

Run: `./sbtw "testOnly oathdigital.gameplay.OathRulesWalkerPowerSuite"`
Expected: PASS.

- [x] **Step 5: Write the failing projection tests** in `WalkerDecisionProjectorSuite`. Add a helper parking a `Decide` owned by another player, using the suite's existing `TreeSource` seam:

```scala
  private def parkedOffTurn: (ReadyGame, PlayerId, PlayerId,
      WalkerDecisionProjector) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val active = base.game.current.turn.activePlayer
    val owner = base.game.current.players.map(_.player).find(_ != active).get
    val tree: Operation = Sequence(Decide("test.off-turn", owner,
      DecisionQuery.ChooseOne(Vector(DecisionOption.Button(
        DecisionOptionRef.Button("ok"), "OK")), heading = Some("Answer"))))
    val ready = base.copy(game = base.game.copy(current =
      base.game.current.copy(
        turn = base.game.current.turn.copy(phase = Phase.Act),
        walkerProcedure = Some(ActionRef.Recover),
        walkerPending = Some(PendingTree(Vector("0"), Vector.empty)))))
    val projector = new WalkerDecisionProjector(catalog,
      new GamePresentationProjector(catalog), WalkerPowers.empty,
      (_, _, _, _, _) => Right(tree))
    (ready, active, owner, projector)
  }

  test("only the awaited player sees the decision; everyone else, the active " +
      "player and spectators included, sees who is being waited on") {
    val (ready, active, owner, projector) = parkedOffTurn
    def ctx(viewer: Option[PlayerId]) = ScopedProjectionContext(ready, viewer)
    assert(projector.project(ctx(Some(owner))).nonEmpty)
    assertEquals(projector.project(ctx(Some(active))), None)
    assertEquals(projector.waiting(ctx(Some(owner))), None)
    val waiting = Some(WalkerWaitingProjection(owner.value, Some("Answer")))
    assertEquals(projector.waiting(ctx(Some(active))), waiting)
    assertEquals(projector.waiting(ctx(None)), waiting)
  }
```

Construct `GamePresentationProjector` the same way the suite's existing tests do; if its constructor differs from `new GamePresentationProjector(catalog)`, copy the existing construction.

In `WalkerDecisionQueryPowerSuite`, add a test that a synthetic `Transform` power at the test tree's window replaces the parked `Decide` with a copy owned by another player, then asserts together that: `resolveWalker` by that player is `Right`; by the active player is `Left(WrongPlayer(other, active))`; `project` for the other player is non-empty; and `waiting` for the active player names the other player. Use the suite's existing power and tree helpers, writing the transform as:

```scala
      Transform((_, ops) => ops.map {
        case decide: Decide => decide.copy(owner = other)
        case op => op
      })
```

- [x] **Step 6: Run them to verify they fail.**

Run: `./sbtw "testOnly oathdigital.application.WalkerDecisionProjectorSuite oathdigital.application.WalkerDecisionQueryPowerSuite"`
Expected: FAIL to compile (`waiting`, `WalkerWaitingProjection` not found).

- [x] **Step 7: Implement the projection.**

In `shared/.../ActionProjectionDtos.scala`:
```scala
/** Public: who a parked walker position waits on, and the question's heading
  * when it has one (`None` for a roll). Every viewer except the awaited
  * player receives this; the awaited player receives `WalkerDecisionProjection`.
  */
final case class WalkerWaitingProjection(playerId: String,
    heading: Option[String] = None)
```

Add `walkerWaiting: Option[WalkerWaitingProjection] = None` after `walkerDecision` in `GameProjectionDto.scala`. In `GameProjectionCodec`, add `"walkerWaiting"` to the known-field list, encode it as `option(value.walkerWaiting)(w => ujson.Obj("playerId" -> w.playerId, "heading" -> option(w.heading)(ujson.Str(_))))`, and decode it with `optionalAbsent(value, "walkerWaiting", path)` beside `walkerDecision`, mirroring how `walkerDecision`'s optional strings are read.

In `WalkerDecisionProjector`, factor out the parked position and gate on the awaited player:
```scala
  private final case class Parked(procedure: ProcedureRef, tree: Operation,
      pending: PendingTree, powers: WalkerPowers, awaited: PlayerId)

  private def parkedPosition(context: ScopedProjectionContext): Option[Parked] =
    for {
      pending <- context.current.walkerPending
      procedure <- context.current.walkerProcedure
      tree <- rebuild(context.ready, procedure,
        context.current.turn.activePlayer,
        context.current.walkerStartArgs).toOption
      powers = WalkerPowers.selected(walkerPowerCatalog,
        context.current.walkerModifiers)
      awaited <- ProcedureWalker.awaitedPlayer(context.ready, tree, pending,
        powers)
    } yield Parked(procedure, tree, pending, powers, awaited)

  def project(context: ScopedProjectionContext)
      : Option[WalkerDecisionProjection] = for {
    parked <- parkedPosition(context)
    if context.viewer.contains(parked.awaited)
    projection <- this.parked(parked.procedure, parked.tree, context.ready,
      parked.pending, parked.powers, parked.awaited)
  } yield projection

  def waiting(context: ScopedProjectionContext)
      : Option[WalkerWaitingProjection] = for {
    parked <- parkedPosition(context)
    if !context.viewer.contains(parked.awaited)
    // The same all-or-nothing rule as `project`: a decision the awaited
    // player could not be shown is not announced to anyone else either.
    _ <- this.parked(parked.procedure, parked.tree, context.ready,
      parked.pending, parked.powers, parked.awaited)
  } yield WalkerWaitingProjection(parked.awaited.value,
    ProcedureWalker.parkedDecide(context.ready, parked.tree, parked.pending,
      parked.powers).flatMap(decide => decide.query match {
        case DecisionQuery.ChooseOne(_, heading) => heading
        case DecisionQuery.Partition(_, _, heading, _) => heading
      }))
```

Rename the existing private `parked` method's viewer argument to `awaited` and use it both as the card-disclosure viewer and for `rollOutcome`. In `ScopedProjectionContext`'s `PendingProjection`, add `walkerWaiting: Option[WalkerWaitingProjection]`. `PendingProcedureProjector.project` fills it with `walkerDecisions.waiting(context)`, and `GameProjection` copies `walkerWaiting = pending.walkerWaiting` next to `walkerDecision`. `LegalActionProjector.walkerControls` already derives its controls from `project`, so the awaited player alone now receives them, with no edit.

In `ProjectionProtocolSuite`, add `walkerWaiting = Some(WalkerWaitingProjection("blue", Some("Choose the Oathkeeper")))` to the populated `projection` value, so the existing round-trip test covers it.

- [x] **Step 8: Write the failing frontend test** in `ServerModeUiSuite`. Build the smallest `GameProjection` the suite's existing Forge tests build, and add:

```scala
  test("a parked walker waiting on another player names them and the question") {
    val waitingOn = forgeProjection.copy(walkerDecision = None,
      walkerWaiting = Some(WalkerWaitingState(
        forgeProjection.players.head.playerId, Some("Choose the Oathkeeper"))))
    assertEquals(WalkerPanelSupport.waitingNotice(waitingOn),
      Some(s"Waiting for ${forgeProjection.players.head.displayName}: " +
        "Choose the Oathkeeper"))
    assertEquals(WalkerPanelSupport.waitingNotice(
      waitingOn.copy(walkerWaiting = None)), None)
  }
```

`forgeProjection` stands for whatever value the suite's "Forge is answered by moving projected options between projected sections" test builds; reuse it rather than building a new one.

- [x] **Step 9: Implement the frontend.** In `package.scala`, add the `WalkerWaitingState` type and value aliases beside `WalkerDecisionState`. In `WalkerPanelSupport`:

```scala
  /** The public line shown to everyone a parked walker is not waiting on. */
  private[frontend] def waitingNotice(value: GameProjection): Option[String] =
    value.walkerWaiting.map { waiting =>
      val name = value.players.find(_.playerId == waiting.playerId)
        .map(_.displayName).getOrElse(waiting.playerId)
      waiting.heading.fold(s"Waiting for $name")(heading =>
        s"Waiting for $name: $heading")
    }

  private[frontend] def renderWaitingNotice(value: GameProjection,
      panel: dom.Element): Unit =
    waitingNotice(value).foreach(notice =>
      panel.appendChild(text("p", "walker-waiting", notice)))
```

In `ActionDecisionRenderer`, call `WalkerPanelSupport.renderWaitingNotice(value, panel)` directly after `renderPartitionPanel`.

- [x] **Step 10: Run the gates.**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS" && python3 scripts/check-architecture.py && git diff --check`
Expected: PASS.

- [x] **Step 11: Mutation check.** Temporarily make `awaitedPlayer` return `Some(state.game.current.turn.activePlayer)` unconditionally. Run the two application suites and `OathRulesWalkerPowerSuite`.
Expected: FAIL in the off-turn tests of all three. Revert.

- [x] **Step 12: Commit**

```bash
git add -A src shared frontend
git commit -m "feat(walker): authorize and project a parked decision by its owner in any phase"
```

#### What Task 5 settled

Landed as `4321aae..90f4e6a` (task range `266f2fb..90f4e6a`; main content
commit `4321aae`). The plan's Files list omitted
`frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala`;
review found the off-turn owner's parked decision was not being surfaced
with working controls to that owner, and the fix landed in the same
task's closing commit,
`90f4e6a` (`fix(frontend): give a parked walker decision's owner the
controls, and name who others wait for`), which also names, for every
other seated player, whom the game is waiting on. No other plan defect.

---

### Task 6: The `SetOathkeeper` operation

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/operations/CoreOperations.scala` (after `EnterPhase`, line 492)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationStateMutation.scala:305-330`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationError.scala` (after `PhaseAlreadyEntered`)
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`
- Test: `src/test/scala/oathdigital/gameplay/operations/OperationStateMutationSuite.scala`, `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala:495`

**Interfaces:**
- Produces: `final case class SetOathkeeper(holder: Option[PlayerId]) extends PrimitiveOperation`; `OperationError.OathkeeperUnchanged(holder: Option[PlayerId])` with `code = "oathkeeper-unchanged"`.

- [x] **Step 1: Write the failing tests** in `OperationStateMutationSuite` (add the imports the file lacks):

```scala
  private def titled(holder: Option[PlayerId], side: TitleSide): ReadyGame = {
    val Ready(base) = execute(new FirstGameSetupRules(catalog))._1: @unchecked
    base.copy(game = base.game.copy(current = base.game.current.copy(
      title = OathkeeperState(holder, side))))
  }
  private def set(ready: ReadyGame, holder: Option[PlayerId]) =
    new OperationExecutor().executeAll(ready, Vector(SetOathkeeper(holder)))
      .map(_.game.current.title)

  test("SetOathkeeper moves the title and always resets it to the Oathkeeper side") {
    val p1 = PlayerId("p1"); val p2 = PlayerId("p2")
    assertEquals(set(titled(None, TitleSide.Oathkeeper), Some(p2)),
      Right(OathkeeperState(Some(p2), TitleSide.Oathkeeper)))
    assertEquals(set(titled(Some(p1), TitleSide.Usurper), Some(p2)),
      Right(OathkeeperState(Some(p2), TitleSide.Oathkeeper)))
    assertEquals(set(titled(Some(p1), TitleSide.Usurper), None),
      Right(OathkeeperState(None, TitleSide.Oathkeeper)))
  }

  test("SetOathkeeper rejects leaving the holder unchanged, whatever the side") {
    val p1 = PlayerId("p1")
    Vector(TitleSide.Oathkeeper, TitleSide.Usurper).foreach { side =>
      assertEquals(set(titled(Some(p1), side), Some(p1)).left.map(_.code),
        Left("oathkeeper-unchanged"))
    }
    assertEquals(set(titled(None, TitleSide.Oathkeeper), None).left.map(_.code),
      Left("oathkeeper-unchanged"))
  }
```

Use the player ids the setup fixture actually seats if `PlayerId("p1")`/`"p2"` fail the executor's post-state validation. `executeAll`'s error type exposes `code` (`OperationError.code`); if it returns a wrapper, map through it the way `WalkerReplay` does (`.left.map(_.toViolation)`) and compare the violation instead.

In `GameEventWireSuite`'s "every CoreOperation variant round-trips through the walker codec", add `SetOathkeeper(Some(PlayerId("red")))` and `SetOathkeeper(None)` to the operation list.

- [x] **Step 2: Run them to verify they fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.OperationStateMutationSuite"`
Expected: FAIL to compile (`SetOathkeeper` not found).

- [x] **Step 3: Implement.**

`CoreOperations.scala`, after `EnterPhase`:
```scala
/** Sets who holds the Oathkeeper title: a player, or the shared bank (`None`).
  *
  * The holder and the side change together: any change of holder puts the
  * title back on its Oathkeeper side, as every title change in the rules does.
  * Which holder the title should go to is not decided here; that is
  * `OathkeeperRules.outcome`. The one thing applying this rejects is leaving
  * the holder as it is (`OperationError.OathkeeperUnchanged`), which is a
  * doubled or reordered journal rather than a rule about the title.
  */
final case class SetOathkeeper(holder: Option[PlayerId]) extends PrimitiveOperation
```

`OperationError.scala`:
```scala
  /** A title "change" to the player (or bank) already holding it. */
  final case class OathkeeperUnchanged(holder: Option[PlayerId])
      extends OperationError {
    override val code: String = "oathkeeper-unchanged"
    override val detail: String = holder.fold(
      "the Oathkeeper title is already in the shared bank")(player =>
      s"${player.value} already holds the Oathkeeper title")
  }
```

`OperationStateMutation.scala`, in the leaf fold beside `EnterPhase`:
```scala
      case (result, SetOathkeeper(holder)) =>
        result.flatMap(setOathkeeper(_, holder))
```
and:
```scala
  private def setOathkeeper(ready: ReadyGame,
      holder: Option[PlayerId]): Either[OperationError, ReadyGame] =
    Either.cond(ready.game.current.title.holder != holder,
      updateCurrent(ready)(current => current.copy(
        title = OathkeeperState(holder, TitleSide.Oathkeeper))),
      OathkeeperUnchanged(holder))
```

`WalkerOperationCodec.scala`, beside `enter-phase`:
```scala
      case SetOathkeeper(holder) => ujson.Obj("kind" -> "set-oathkeeper",
        "holderPlayerId" -> holder.fold[ujson.Value](ujson.Null)(p =>
          ujson.Str(p.value)))
```
and in `decodeOperation`:
```scala
      case "set-oathkeeper" => Right(SetOathkeeper(value("holderPlayerId") match {
        case ujson.Null => None
        case other => Some(PlayerId(other.str))
      }))
```

If the compiler reports a non-exhaustive match on `PrimitiveOperation` elsewhere (`OperationShadow`, `OperationStateAdapter`, `OperationValidator`), add `SetOathkeeper` beside `EnterPhase` in that match.

- [x] **Step 4: Run the tests.**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.OperationStateMutationSuite oathdigital.serialization.GameEventWireSuite"`
Expected: PASS.

- [x] **Step 5: Full suite and architecture check.**

Run: `./sbtw "test" && python3 scripts/check-architecture.py && git diff --check`
Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add -A src
git commit -m "feat(operations): set the Oathkeeper title with one generic operation"
```

#### What Task 6 settled

Landed as `dae427d`. Review found 0 Critical/Important issues and no plan
defect; frontend/shared gates were not rerun for this task because the
diff touched only the root project. Open observation (not a bug, not
fixed here): `OperationStateMutationSuite`'s `titled` fixture uses
unseated `PlayerId`s — harmless, since `SetOathkeeper` performs no seat
check by spec.

---

### Task 7: The triggered Oathkeeper procedure

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/oathkeeper/OathkeeperRules.scala`
- Create: `src/main/scala/oathdigital/gameplay/oathkeeper/OathkeeperProcedure.scala`
- Create: `src/test/scala/oathdigital/gameplay/oathkeeper/OathkeeperFixture.scala`, `OathkeeperRulesSuite.scala`, `OathkeeperProcedureSuite.scala`
- Modify: `src/main/scala/oathdigital/model/ProcedureRef.scala` (`TriggeredProcedureRef.Oathkeeper`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` (entry)
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` (`startTriggered`, interim boundary exclusion)
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala:180-190, 250-275, 340-356` (Oathkeeper step, deletions)
- Modify: `src/main/scala/oathdigital/gameplay/StateBasedEvaluation.scala` (deletions)
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala`, `ActionDecisionRenderer.scala:560-574`, `package.scala:142-143`
- Delete, per Step 9's sweep: the legacy recipient command, events, codecs, projection and pending case
- Test: `StateBasedEvaluationSuite.scala`, `EconomySuite.scala`, `EndWakeProcedureSuite.scala`, `GameEventWireSuite.scala`, `WalkerReplayDriftSuite.scala`, `WalkerDecisionProjectorSuite.scala`, `frontend/.../ServerModeUiSuite.scala`

**Interfaces:**
- Consumes: Task 4's families and registry, Task 5's awaited-player continuation and projection, Task 6's `SetOathkeeper`.
- Produces:
  - `package oathdigital.gameplay.oathkeeper`: `sealed trait OathkeeperOutcome` with `NoChange`, `Transfer(holder: Option[PlayerId])`, `Choose(holder: PlayerId, candidates: Vector[PlayerId])`; `OathkeeperRules.outcome(ready: ReadyGame): OathkeeperOutcome`.
  - `OathkeeperProcedure.recipientDecisionId: String = "oathkeeper.recipient"`; `OathkeeperProcedure.build(catalog: ExecutableCatalog, state: ReadyGame, activePlayer: PlayerId, args: Vector[DecisionOptionRef]): Either[OathViolation, Operation]`.
  - `TriggeredProcedureRef.Oathkeeper` (key `"oathkeeper"`).
  - `OathRulesWalker.startTriggered(transition: OathTransition, procedure: TriggeredProcedureRef): Either[OathViolation, OathTransition]`, `private[gameplay]`.
  - `WalkerPanelSupport.chooseOneStep(decision: WalkerDecisionState): Option[DecisionQueryState]`.

- [x] **Step 1: Create the fixture.** Move `StateBasedEvaluationSuite.prepared`'s body into a reusable helper that works on any ready game, and make `StateBasedEvaluationSuite.prepared` delegate to it:

```scala
package oathdigital.gameplay.oathkeeper

import oathdigital.gameplay.ReadyGame
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.model._

/** Title and site-ruler setups for the Supremacy goal the first game uses:
  * `owners(i)` rules the i-th in-play site with one exile warband (`None` is
  * one bandit), so leaders are whoever rules the most sites.
  */
object OathkeeperFixture {
  def base: ReadyGame = {
    val Ready(ready) = execute(new FirstGameSetupRules(catalog))._1: @unchecked
    ready
  }

  def players: Vector[PlayerId] = base.game.current.players.map(_.player)

  def ruled(ready: ReadyGame, owners: Vector[Option[PlayerId]],
      holder: Option[PlayerId] = None,
      side: TitleSide = TitleSide.Oathkeeper): ReadyGame = {
    val byPlayer = ready.game.current.players.map(p => p.player -> p.lineage).toMap
    val sites = ready.game.current.map.inPlay.zipWithIndex.map { case (id, i) =>
      val force = owners.lift(i).flatten.fold[SiteForces](
        SiteForces.Occupied(ForceKind.Bandit, 1))(player =>
        SiteForces.Occupied(ForceKind.Exile(byPlayer(player)), 1))
      id -> ready.game.current.map.sites(id).copy(forces = force)
    }.toMap
    ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      map = ready.game.current.map.copy(sites = sites),
      title = OathkeeperState(holder, side))))
  }

  def inPhase(ready: ReadyGame, phase: Phase): ReadyGame =
    ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      turn = ready.game.current.turn.copy(phase = phase))))
}
```

- [x] **Step 2: Write the failing outcome tests** in `OathkeeperRulesSuite`:

```scala
package oathdigital.gameplay.oathkeeper

import oathdigital.gameplay.oathkeeper.OathkeeperFixture._
import oathdigital.gameplay.oathkeeper.OathkeeperOutcome._
import oathdigital.model._

class OathkeeperRulesSuite extends munit.FunSuite {
  private val p = players
  private def outcome(owners: Vector[Option[PlayerId]],
      holder: Option[PlayerId] = None, side: TitleSide = TitleSide.Oathkeeper) =
    OathkeeperRules.outcome(ruled(base, owners, holder, side))

  test("a holder who still leads keeps the title, even when tied") {
    assertEquals(outcome(Vector(Some(p(0))), holder = Some(p(0))), NoChange)
    // Pins row order: a tied holder who leads must not be offered a choice.
    assertEquals(outcome(Vector(Some(p(0)), Some(p(1))), holder = Some(p(0))),
      NoChange)
  }

  test("a holder tied out of the lead chooses among the leaders, in seat order") {
    assertEquals(outcome(Vector(Some(p(1)), Some(p(0))), holder = Some(p(2))),
      Choose(p(2), Vector(p(0), p(1))))
  }

  test("a single leader takes the title from a holder or from the bank") {
    assertEquals(outcome(Vector(Some(p(1))), holder = Some(p(0))),
      Transfer(Some(p(1))))
    assertEquals(outcome(Vector(Some(p(1)))), Transfer(Some(p(1))))
  }

  test("a holder with nobody leading returns the title to the bank") {
    assertEquals(outcome(Vector.empty, holder = Some(p(0))), Transfer(None))
  }

  test("with nobody holding it, no leader or a tie changes nothing") {
    assertEquals(outcome(Vector.empty), NoChange)
    assertEquals(outcome(Vector(Some(p(0)), Some(p(1)))), NoChange)
  }

  test("the Usurper side is not consulted") {
    assertEquals(outcome(Vector(Some(p(1))), holder = Some(p(0)),
      side = TitleSide.Usurper), Transfer(Some(p(1))))
  }
}
```

- [x] **Step 3: Run them to verify they fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.oathkeeper.OathkeeperRulesSuite"`
Expected: FAIL to compile (`OathkeeperRules` not found).

- [x] **Step 4: Implement the outcome.** Create `OathkeeperRules.scala`, moving `qualifyingPlayers` and `leadersWithPositiveCount` from `StateBasedEvaluation.scala:299-332` verbatim (their only caller is `afterAction`, which Step 9 deletes):

```scala
package oathdigital.gameplay.oathkeeper

import oathdigital.gameplay.ReadyGame
import oathdigital.model._

sealed trait OathkeeperOutcome extends Product with Serializable
object OathkeeperOutcome {
  case object NoChange extends OathkeeperOutcome
  final case class Transfer(holder: Option[PlayerId]) extends OathkeeperOutcome
  /** `candidates` are the tied leaders in seat order; always two or more. */
  final case class Choose(holder: PlayerId, candidates: Vector[PlayerId])
      extends OathkeeperOutcome
}

/** The only definition of what the Oathkeeper title does at an action
  * boundary. The boundary asks it whether anything happens, and
  * `OathkeeperProcedure` asks it what, so the two cannot disagree.
  */
object OathkeeperRules {
  import OathkeeperOutcome._

  def outcome(ready: ReadyGame): OathkeeperOutcome = {
    val current = ready.game.current
    val leaders = qualifyingPlayers(ready.game.campaign.oathkeeperGoal, current)
    current.title.holder match {
      case Some(holder) if leaders(holder) => NoChange
      case Some(holder) if leaders.size > 1 =>
        Choose(holder, current.players.map(_.player).filter(leaders))
      case _ if leaders.size == 1 => Transfer(leaders.headOption)
      case Some(_) => Transfer(None)
      case None => NoChange
    }
  }

  // qualifyingPlayers and leadersWithPositiveCount, moved from
  // StateBasedEvaluation unchanged, as `private` members of this object.
}
```

Run: `./sbtw "testOnly oathdigital.gameplay.oathkeeper.OathkeeperRulesSuite"`
Expected: PASS.

- [x] **Step 5: Write the failing procedure tests** in `OathkeeperProcedureSuite`:

```scala
package oathdigital.gameplay.oathkeeper

import oathdigital.gameplay._
import oathdigital.gameplay.OathEvent.BanditsRefilled
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.operations.SetOathkeeper
import oathdigital.gameplay.oathkeeper.OathkeeperFixture._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ChoicePayload, WalkerCompleted,
  WalkerParked, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer

class OathkeeperProcedureSuite extends munit.FunSuite {
  private val rules = new OathRules(catalog)

  /** A Travel by the active player: the cheapest walker action whose
    * completion runs the action boundary and moves no forces.
    */
  private def travel(ready: ReadyGame) = {
    val active = ready.game.current.turn.activePlayer
    val pawn = ready.game.current.players.find(_.player == active).get.pawnSite.get
    val destination = ready.game.current.map.inPlay.find(_ != pawn).get
    rules.startWalker(Ready(ready), ActionRef.Travel, active, Vector.empty,
      Vector(DecisionOptionRef.Site(destination)))
  }

  private def replays(start: ReadyGame, events: Vector[OathEvent],
      expected: OathState): Unit =
    assertEquals(events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(start)))((state, event) => state.flatMap(rules.evolve(_, event))),
      Right(expected))

  /** Holder is not the active player; the two leaders are everyone else. */
  private def tie: (ReadyGame, PlayerId, PlayerId, Vector[PlayerId]) = {
    val active = base.game.current.turn.activePlayer
    val holder = players.find(_ != active).get
    val leaders = players.filterNot(_ == holder).take(2)
    (inPhase(ruled(base, leaders.map(Some(_)), holder = Some(holder)),
      Phase.Act), active, holder, leaders)
  }

  test("a single new leader takes the title inside the action's own command") {
    val leader = players.last
    val ready = inPhase(ruled(base, Vector(Some(leader))), Phase.Act)
    val accepted = travel(ready).toOption.get
    assert(accepted.events.exists {
      case step: WalkerStepRecorded => step.ops == Vector(SetOathkeeper(Some(leader)))
      case _ => false
    })
    assertEquals(accepted.events.last,
      WalkerCompleted(TriggeredProcedureRef.Oathkeeper): OathEvent)
    val Ready(after) = accepted.state: @unchecked
    assertEquals(after.game.current.title,
      OathkeeperState(Some(leader), TitleSide.Oathkeeper))
    assertEquals(after.game.current.walkerPending, None)
    replays(ready, accepted.events, accepted.state)
  }

  test("a tie parks for the holder, who may be off-turn, and only the holder " +
      "may answer with a tied leader") {
    val (ready, active, holder, leaders) = tie
    val parked = travel(ready).toOption.get
    assertEquals(parked.continue, OathContinue.AwaitingOathkeeperRecipient(
      holder, DecisionId(OathkeeperProcedure.recipientDecisionId)))
    assert(parked.events.last.isInstanceOf[WalkerParked])
    val Ready(waiting) = parked.state: @unchecked
    assertEquals(waiting.game.current.walkerProcedure,
      Some(TriggeredProcedureRef.Oathkeeper))

    def answer(by: PlayerId, chosen: PlayerId) = rules.resolveWalker(parked.state,
      by, OathkeeperProcedure.recipientDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Player(chosen)))

    assertEquals(answer(active, leaders(0)),
      Left(OathViolation.WrongPlayer(holder, active)))
    assert(answer(holder, holder).isLeft,
      "a player who is not a tied leader is not a legal recipient")

    val chosen = answer(holder, leaders(1)).toOption.get
    assert(chosen.events.exists {
      case WalkerStepRecorded(_, ChoicePayload(_, _, by), _, _) => by == holder
      case _ => false
    })
    assertEquals(chosen.continue, OathContinue.ActActionSelection(active))
    val Ready(after) = chosen.state: @unchecked
    assertEquals(after.game.current.title,
      OathkeeperState(Some(leaders(1)), TitleSide.Oathkeeper))
    replays(ready, parked.events ++ chosen.events, chosen.state)
  }

  test("completing the Oathkeeper procedure runs no action boundary") {
    val (ready, _, holder, leaders) = tie
    val parked = travel(ready).toOption.get
    // An empty site with capacity makes a boundary observable: if one ran on
    // the resolving command, it would refill bandits here.
    val Ready(waiting) = parked.state: @unchecked
    val empty = waiting.game.current.map.inPlay.find(id =>
      catalog.sites.find(_.id == id).exists(_.capacity > 0) &&
        waiting.game.current.map.sites(id).forces ==
          SiteForces.Occupied(ForceKind.Bandit, 1)).get
    val emptied = waiting.copy(game = waiting.game.copy(current =
      waiting.game.current.copy(map = waiting.game.current.map.copy(sites =
        waiting.game.current.map.sites.updated(empty,
          waiting.game.current.map.sites(empty).copy(forces = SiteForces.Empty))))))
    assert(StateBasedEvaluation.banditRefill(catalog, Ready(emptied))
      .toOption.flatten.nonEmpty, "precondition: a boundary would refill here")
    val chosen = rules.resolveWalker(Ready(emptied), holder,
      OathkeeperProcedure.recipientDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Player(leaders(0)))).toOption.get
    assertEquals(chosen.events.collect { case e: BanditsRefilled => e }, Vector.empty)
  }

  test("the engine refuses to start a triggered procedure over a pending one") {
    val (ready, _, _, _) = tie
    val parked = travel(ready).toOption.get
    val result = rules.startTriggered(
      OathTransition(parked.state, Vector.empty, parked.continue),
      TriggeredProcedureRef.Oathkeeper)
    assert(result.left.toOption.exists(_.isInstanceOf[OathViolation.InvalidEventOrder]),
      s"expected a typed rejection, got $result")
  }

  test("the procedure rejects a start selection and a state with nothing to change") {
    val ready = inPhase(base, Phase.Act)
    assertEquals(OathkeeperProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty),
      Left(OathViolation.InvalidEventOrder("no Oathkeeper change to perform")))
    assert(OathkeeperProcedure.build(catalog, ruled(ready, Vector(Some(players.last))),
      ready.game.current.turn.activePlayer,
      Vector(DecisionOptionRef.Site(ready.game.current.map.inPlay.head))).isLeft)
  }
}
```

`OathTransition`'s constructor takes `(state, events, continue)`, the order `OathRulesWalker.walkerTransition` uses. `rules.startTriggered` is `private[gameplay]`, which this `gameplay.oathkeeper` suite can reach.

In `ActionValuesSuite`, extend "procedure references form three families" with the client-side rejection the spec's failure list requires:

```scala
    assertEquals(TriggeredProcedureRef.all, Vector(TriggeredProcedureRef.Oathkeeper))
    // A client names a procedure by key alone; the triggered key must not resolve.
    assertEquals(StartableRef.fromKey("oathkeeper"), None)
```

In `EconomySuite`, add a test that the legacy Muster path starts the procedure within the same command: build the ready game with the suite's `act()` fixture, apply `OathkeeperFixture.ruled(_, Vector(Some(<a non-active player>)))`, run the same `rules.handle(Ready(...), EconomyCommand.Muster(...))` call the test at `EconomySuite.scala:73` makes, and assert the transition's events end with `WalkerCompleted(TriggeredProcedureRef.Oathkeeper)` and the title moved to that player.

- [x] **Step 6: Run them to verify they fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.oathkeeper.OathkeeperProcedureSuite"`
Expected: FAIL to compile (`OathkeeperProcedure`, `TriggeredProcedureRef.Oathkeeper`, `startTriggered` not found).

- [x] **Step 7: Implement the procedure and its registration.**

`ProcedureRef.scala`:
```scala
object TriggeredProcedureRef {
  /** Every change of the Oathkeeper title holder at an action boundary. */
  case object Oathkeeper extends TriggeredProcedureRef { val key = "oathkeeper" }
  val all: Vector[TriggeredProcedureRef] = Vector(Oathkeeper)
}
```

`OathkeeperProcedure.scala`:
```scala
package oathdigital.gameplay.oathkeeper

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathViolation, ReadyGame}
import oathdigital.gameplay.operations.{BuildOps, CoreOperation, Decide,
  Operation, Sequence, SetOathkeeper}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer

/** Declared tree for every Oathkeeper title change (triggered, started by the
  * action boundary).
  *
  * {{{
  * Transfer(holder)  -> Sequence(SetOathkeeper(holder))
  * Choose(holder, c) -> Sequence(Decide(recipient, owner = holder), BuildOps(SetOathkeeper))
  * }}}
  *
  * A single leader is a forced choice, so it omits the `Decide` and applies the
  * transfer itself. `build` is also `rebuild`: only resume commands are
  * accepted while this parks, so the outcome cannot change under it. No window:
  * nothing may transform a title change until a real power needs to.
  */
object OathkeeperProcedure {
  val recipientDecisionId: String = "oathkeeper.recipient"

  def build(catalog: ExecutableCatalog, state: ReadyGame, activePlayer: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    _ <- Either.cond(args.isEmpty, (), OathViolation.InvalidEventOrder(
      "the Oathkeeper procedure selects nothing, got " +
        args.map(ref => s"${ref.kind}/${ref.wireId}").mkString(", ")))
    tree <- OathkeeperRules.outcome(state) match {
      case OathkeeperOutcome.NoChange => Left(OathViolation.InvalidEventOrder(
        "no Oathkeeper change to perform"))
      case OathkeeperOutcome.Transfer(holder) =>
        Right(Sequence(Vector(SetOathkeeper(holder))))
      case OathkeeperOutcome.Choose(holder, candidates) => Right(Sequence(Vector(
        Decide(
          decisionId = recipientDecisionId,
          owner = holder,
          query = DecisionQuery.ChooseOne(candidates.map(candidate =>
            DecisionOption.Player(DecisionOptionRef.Player(candidate))),
            heading = Some("Choose the Oathkeeper"))),
        BuildOps((_, pending) => pending.answered.lastOption match {
          case Some(Answered(`recipientDecisionId`,
              ChooseOneAnswer(DecisionOptionRef.Player(chosen)), _)) =>
            Right(Vector[CoreOperation](SetOathkeeper(Some(chosen))))
          case _ => Left(OathViolation.InvalidEventOrder(
            "no Oathkeeper recipient answer is recorded"))
        }))))
    }
  } yield tree
}
```

`WalkerProcedureRegistry.entries`:
```scala
    /** The first triggered procedure. It is started by the action boundary,
      * never by a client, so it has no fallback kind (the boundary recorded
      * its diagnostics), no modifier window and no start selection.
      */
    TriggeredProcedureRef.Oathkeeper -> Entry(
      fallbackKind = None,
      rollDecisionId = None,
      modifierWindow = None,
      continuationFor = (decisionId, awaited, decision) => decisionId match {
        case OathkeeperProcedure.recipientDecisionId =>
          Some(OathContinue.AwaitingOathkeeperRecipient(awaited, decision))
        case _ => None
      },
      build = OathkeeperProcedure.build,
      rebuild = OathkeeperProcedure.build)
```

`OathRulesWalker`:
```scala
  /** Starts a procedure the engine triggers, with no client command
    * (walker-ownership spec, Triggered procedures).
    *
    * Strictly sequential: a walker or legacy procedure already pending is a
    * typed rejection rather than a nested start. The procedure's events are
    * appended to `transition`, so the action that triggered it and the
    * procedure journal as one command.
    */
  private[gameplay] def startTriggered(transition: OathTransition,
      procedure: TriggeredProcedureRef): Either[OathViolation, OathTransition] =
    transition.state match {
      case Ready(ready) if ready.game.current.walkerPending.nonEmpty ||
          ready.game.current.walkerProcedure.nonEmpty ||
          ready.game.current.pending.nonEmpty =>
        Left(InvalidEventOrder(s"cannot start ${procedure.key}: another " +
          "procedure is already pending"))
      case Ready(ready) =>
        val activePlayer = ready.game.current.turn.activePlayer
        val powers = walkerPowers(ready, activePlayer, Vector.empty)
        for {
          tree <- buildWalker(procedure, ready, activePlayer, Vector.empty,
            starting = true)
          _ <- checkRestrictions(tree, powers, ready, activePlayer)
          outcome <- walkerCall(ProcedureWalker.advance(ready, tree, None, powers))
          started <- walkerTransition(transition.state, ready, procedure, tree,
            outcome, powers, Vector.empty, Vector.empty)
        } yield started.copy(events = transition.events ++ started.events)
      case _ => Right(transition)
    }
```

In `walkerTransition`'s `Finished` branch, until Task 8 replaces the rule, exclude triggered procedures from the boundary so completing one never runs `completeAction` again:
```scala
      completionIn(ready.game.current.turn.phase,
        treeless.game.current.turn.phase, activePlayer).flatMap { completed =>
        val runsBoundary = completed.runsActionBoundary && (procedure match {
          case _: TriggeredProcedureRef => false
          case _: StartableRef => true
        })
```
and use `runsBoundary` where `completed.runsActionBoundary` was read.

- [x] **Step 8: Replace the boundary's Oathkeeper step.** In `OathRules.completeAction`, replace `appendEvaluation(afterRefill, StateBasedEvaluation.afterAction)` with `oathkeeperStep(afterRefill)`:

```scala
  /** The boundary decides only WHETHER the title changes; the triggered
    * procedure performs the change, so every title change is one walker step.
    */
  private def oathkeeperStep(transition: OathTransition)
      : Either[OathViolation, OathTransition] = transition.state match {
    case Ready(ready) => StateBasedEvaluation.supported(transition.state)
      .flatMap(_ => OathkeeperRules.outcome(ready) match {
        case OathkeeperOutcome.NoChange => Right(transition)
        case _ => startTriggered(transition, TriggeredProcedureRef.Oathkeeper)
      })
    case _ => Right(transition)
  }
```

Make `StateBasedEvaluation.supported` `private[gameplay]` so the gate `afterAction` ran first is still run. In `appendEvaluation`, delete the `OathkeeperRecipientChoiceStarted` arm; the `UsurperVictory` arm stays until Task 8.

- [x] **Step 9: Delete the legacy path.** Delete:
  - `StateBasedEvaluation.afterAction`, `chooseRecipient`, and the evolve cases for `OathkeeperChanged`, `OathkeeperRecipientChoiceStarted` and `OathkeeperRecipientChosen`;
  - the three events in `gameplay/model/GameEventProtocol.scala` and their `OathRules.evolve` routes (lines 252-256);
  - their codec branches and wire types in `serialization/EndingEventCodec.scala` and `GameEventWire.scala`;
  - `OathRules.chooseOathkeeperRecipient` (lines 185-188);
  - `PendingProcedure.OathkeeperRecipient` in `model/PendingProcedures.scala`;
  - `GameCommand.ChooseOathkeeperRecipient`, its `Authorization` helper, its `GameIntentMapper` case, its `GameApplicationService` route, and the shared intent with its codec and decoder;
  - `OathkeeperRecipientProjection`, its `PendingProcedureProjector.oathkeeperRecipient`, its `ScopedProjectionContext` field, its `GameProjection` copy, its `LegalActionProjector` arms (lines 107-109), and its `GameProjectionDto`/`GameProjectionCodec` field;
  - the frontend block at `ActionDecisionRenderer.scala:560-574` and the `package.scala` alias at lines 142-143.

Keep `OathContinue.AwaitingOathkeeperRecipient`; the registry entry produces it.

Run: `grep -rn "OathkeeperChanged\|OathkeeperRecipientChoiceStarted\|OathkeeperRecipientChosen\|ChooseOathkeeperRecipient\|chooseOathkeeperRecipient\|OathkeeperRecipientProjection\|PendingProcedure.OathkeeperRecipient\|afterAction\|oathkeeperRecipient" --include='*.scala' src shared frontend`
Expected, after the edits: matches only in tests that Step 10 ports, and no match in `src/main`, `shared/src/main` or `frontend/src/main`. `FirstGameSetup.scala` appears in the pre-edit list; if its matches are the `AwaitingOathkeeperRecipient` continuation, keep them.

- [x] **Step 10: Port the remaining tests.**
  - `StateBasedEvaluationSuite`: make `prepared` delegate to `OathkeeperFixture.ruled`. Delete "F7 retains a highest tied holder but does not invent an initial tie winner" and "F7 records and resolves a displaced-holder tie choice"; `OathkeeperRulesSuite` and `OathkeeperProcedureSuite` now cover both. In "first-game Supremacy qualification transfers at a completed action boundary", replace `assertEquals(accepted.events.last, OathkeeperChanged(Some(PlayerId("p2"))))` with `assertEquals(accepted.events.last, WalkerCompleted(TriggeredProcedureRef.Oathkeeper): OathEvent)`.
  - `EndWakeProcedureSuite`, "ending Wake does not run the Act action boundary": replace the `OathkeeperChanged` collect with `accepted.events.collect { case WalkerCompleted(TriggeredProcedureRef.Oathkeeper) => () }` expected empty.
  - `GameEventWireSuite`: delete the legacy Oathkeeper events from "current state-based Oathkeeper and Usurper events round trip" and the fixture near line 894, keeping the Usurper events; remove the deleted imports.
  - `CampaignSuite`, `GameApplicationServiceSuite`, `HttpGameClientSuite`, `ProtocolTestCommands`: remove whichever deleted symbol each names (Step 9's grep lists them).

- [x] **Step 11: Project the recipient choice for the frontend.**

In `WalkerDecisionProjectorSuite`, add a test parking the Oathkeeper tree on a tie (`OathkeeperFixture.ruled` with `walkerProcedure = Some(TriggeredProcedureRef.Oathkeeper)`, `walkerPending = Some(PendingTree(Vector("0"), Vector.empty))`, and the production `declaredTree`) asserting that the holder's projection has `query.map(_.options.map(_.kind))` equal to `Some(Vector("player", "player"))` with the leaders' ids, and a spectator's `waiting` equals `Some(WalkerWaitingProjection(holder.value, Some("Choose the Oathkeeper")))`.

In `ServerModeUiSuite`:
```scala
  test("a choose-one decision outside Recover is answered from its projected options") {
    val decision = WalkerDecisionState("oathkeeper", "oathkeeper.recipient",
      "decide", query = Some(DecisionQueryState("choose-one", Vector(
        DecisionOptionState("player", "blue", "blue"),
        DecisionOptionState("player", "yellow", "yellow")),
        heading = Some("Choose the Oathkeeper"))))
    assertEquals(WalkerPanelSupport.chooseOneStep(decision).map(_.options.map(_.id)),
      Some(Vector("blue", "yellow")))
    assertEquals(WalkerPanelSupport.chooseOneStep(decision.copy(action = "recover")),
      None)
    assertEquals(WalkerPanelSupport.resolveChooseOneCommand(decision,
      decision.query.get.options(1)),
      GameCommand.ResolveWalker("oathkeeper.recipient",
        DecisionAnswerWire.ChooseOneWire("player", "yellow")))
  }
```

Use the frontend state aliases' actual constructor names (`DecisionQueryState`, `DecisionOptionState`) as `package.scala` declares them.

In `WalkerPanelSupport`:
```scala
  /** A choose-one decision no action-specific panel claims. Recover keeps its
    * own panel for its richer copy; everything else is answered here, from the
    * projected options alone.
    */
  private[frontend] def chooseOneStep(decision: WalkerDecisionState)
      : Option[DecisionQueryState] =
    if (decision.action == "recover" || decision.kind != "decide") None
    else chooseOneQuery(decision)

  private[frontend] def renderChooseOnePanel(value: GameProjection,
      presentation: ViewerPresentation, canControl: Boolean,
      panel: dom.Element, ui: ServerUiView): Unit =
    value.walkerDecision.filter(_ => presentation.showGameplayControls)
      .flatMap(decision => chooseOneStep(decision).map(decision -> _))
      .foreach { case (decision, query) =>
        panel.appendChild(text("h2", "", decisionHeading(query)))
        query.options.foreach { option =>
          val label = if (option.kind == "player")
            value.players.find(_.playerId == option.id).map(_.displayName)
              .getOrElse(option.label)
          else option.label
          val choose = button(label, "walker-choice")
          choose.disabled = !canControl
          choose.onclick = _ => ui.submitCommand(
            resolveChooseOneCommand(decision, option))
          panel.appendChild(choose)
        }
      }
```

In `ActionDecisionRenderer`, call `WalkerPanelSupport.renderChooseOnePanel(value, presentation, canControl, panel, ui)` after `renderRecoverPanel`.

- [x] **Step 12: Add the drift case.** In `WalkerReplayDriftSuite`, give `assertNoDrift` two parameters with defaults that keep every existing call unchanged, `procedure: ProcedureRef = ActionRef.Recover` and `tree: (ReadyGame, Boolean) => Operation = (state, starting) => if (starting) RecoverProcedure.build(catalog, state, state.game.current.turn.activePlayer).toOption.get else RecoverProcedure.rebuild(catalog, state, state.game.current.turn.activePlayer).toOption.get`. Use `tree` where it builds `liveTree`, and use `procedure` wherever it constructs a `WalkerParked` or `WalkerCompleted`. Then add:

```scala
  test("drift check: an Oathkeeper tie parks for the holder and resolves") {
    val active = OathkeeperFixture.base.game.current.turn.activePlayer
    val holder = OathkeeperFixture.players.find(_ != active).get
    val leaders = OathkeeperFixture.players.filterNot(_ == holder).take(2)
    val ready = OathkeeperFixture.inPhase(OathkeeperFixture.ruled(
      OathkeeperFixture.base, leaders.map(Some(_)), holder = Some(holder)),
      Phase.Act)
    val oathkeeperTree: (ReadyGame, Boolean) => Operation = (state, _) =>
      OathkeeperProcedure.build(catalog, state,
        state.game.current.turn.activePlayer, Vector.empty).toOption.get
    val finished = assertNoDrift(ready, active,
      Vector(StartWalk, AnswerResume(Answered(
        OathkeeperProcedure.recipientDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Player(leaders(1))), holder))),
      walkerPowers, TriggeredProcedureRef.Oathkeeper, oathkeeperTree)
    finished match {
      case WalkerOutcome.Finished(treeless, _) =>
        assertEquals(treeless.game.current.title,
          OathkeeperState(Some(leaders(1)), TitleSide.Oathkeeper))
      case other => fail(s"expected the tie to finish, got $other")
    }
  }
```

Update the suite's doc comment, which says the corpus is Recover only, to name this case.

- [x] **Step 13: Run the gates.**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS" && python3 scripts/check-architecture.py && git diff --check`
Expected: PASS.

- [x] **Step 14: Mutation checks.** Each must fail at least one test; revert after each.
  1. Swap `outcome`'s first two `case` arms. Expected FAIL: "a holder who still leads keeps the title, even when tied".
  2. In `walkerTransition`, let `TriggeredProcedureRef` run the boundary (`case _: TriggeredProcedureRef => true`). Expected FAIL: "completing the Oathkeeper procedure runs no action boundary".
  3. Give the recipient `Decide` `owner = activePlayer`. Expected FAIL: "a tie parks for the holder…".

- [x] **Step 15: Commit**

```bash
git add -A src shared frontend
git commit -m "feat(walker): change the Oathkeeper title through one triggered procedure"
```

#### What Task 7 settled

Landed as `7ccf57a`. The plan's Files list omitted
`src/main/scala/oathdigital/gameplay/FirstGameSetup.scala`, which still
pattern-matched on the deleted `OathkeeperChanged`/`OathkeeperRecipient*`
events; fixed within this same commit by matching
`case _: UsurperFlipped | _: UsurperVictory =>` instead.

Two rulings extended the plan's own test list rather than changing
production code: R-P1 — Step 11's projector test also asserts that a
`DecisionOption.Player` naming an unseated player omits the whole
decision (and the waiting projection), which the spec's Testing section
requires but no existing test pinned. R-P2 — the "refuses to start a
triggered procedure over a pending one" test also covers a legacy
`PendingProcedure` pending with no walker tree, per the spec's Failure
handling section, not only a walker-pending case as the plan's steps
named.

Open observations (not bugs, not fixed here): no test replays a legacy
action plus a triggered Oathkeeper change as one journalled command;
`EndWakeProcedureSuite`'s Oathkeeper assertion matches only
`WalkerCompleted(Oathkeeper)` and would pass even if a tie wrongly
started `WalkerParked`; `WalkerReplayDriftSuite`'s `assertNoDrift`/`go`
keep an unused `actor` parameter; `OathkeeperProcedureSuite` pins only
`isLeft` for a non-candidate recipient rather than the specific
violation.

**Task 9 Step 4 — legacy boundary call-site verification.** Every
`completeAction` call site in `OathRules.handle` that lacks its own
`pending.isEmpty` guard (Vision `PlayConspiracy`, Negotiation `Accept`,
and Campaign `Sacrifice` already guard inline and are out of this scope)
was traced to confirm the boundary never runs with `pending` set. Result:
clean — no bug found at any site.

| Site | File:line (call + evidence) | Result |
|---|---|---|
| Economy `Muster` | `OathRules.scala:55`; `Economy.scala` never references `pending` and is entered through `OathLifecycle.validateAct` (`Economy.scala:145`) | Safe — `pending` untouched, guaranteed empty by `validateAct` |
| Economy `Trade` | `OathRules.scala:58`; same as `Muster` | Safe — same reasoning |
| Search `Complete` | `OathRules.scala:85`; guarded above by `OathRules.scala:72-85`, which intercepts an awaiting-target Conspiracy and routes to `AwaitingConspiracyDecision` instead of `completeAction`; the remaining path runs `CardPlay.update` (`CardPlay.scala:328-333`), which sets `pending = None` whenever `endSearchPending` (`fromSearch(Origin.Search) = true`, `CardPlay.scala:80-82,113`) | Safe — either intercepted earlier, or cleared by `CardPlay.update` |
| Challenge `Complete` | `OathRules.scala:107`; `BannerChallengeCompleted` evolves via `applyCompletion`, which sets `pending = None` (`Challenge.scala:272,311`) | Safe — cleared before the boundary |
| Challenge `PlaceResource` | `OathRules.scala:107` (shared match arm); `BannerResourcePlaced` evolve never touches `pending`; entry gated by `validateAct` | Safe — untouched, guaranteed empty |
| MinorActions (all 5 commands) | `OathRules.scala:120`; every handler and evolve is `validateAct`-gated (`MinorActions.scala:51,60,68,76,86,116,128,138,151,165`); `pending` is never referenced in the file; the two commands using `CardPlay.resolve` pass `Origin.FacedownAdviser`, whose `endSearchPending = false` and `startConspiracy = None` always (`CardPlay.scala:80-82`) | Safe — untouched throughout |
| Vision `Reveal` | `OathRules.scala:130`; `VisionRevealed` evolve (`Visions.scala:128-145`) never touches `pending`; gated by `validateAct` (`Visions.scala:129`) | Safe — untouched, guaranteed empty |
| Negotiation `Decline` | `OathRules.scala:141`; `NegotiationDeclined` evolve explicitly sets `pending = None` (`Negotiation.scala:115-117`) | Safe — cleared before the boundary |
| Campaign `Place` | `OathRules.scala:159` (shared match arm); `CampaignConquered` evolves via `applyConquest`, which sets `pending = None` (`Campaign.scala:665,710`) | Safe — cleared before the boundary |
| Campaign `RelocateRaidPawn` | `OathRules.scala:159` (shared match arm); the transition folds two events — `CampaignRaided`'s evolve (`applyRaid`, `Campaign.scala:722,787`) sets `pending = Some(relocation)`, but the same transition's second event, `CampaignRaidPawnRelocated` (`Campaign.scala:427-444`), clears it back to `pending = None` before `completeAction` runs | Safe — set then cleared within the same folded transition |

No site can reach the action boundary with `pending` set; nothing here
requires a production-code change.

---

### Task 8: The action boundary after every action

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` (`walkerTransition`, `WalkerCompletion`, `completionIn`)
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala:266-275, 304-307, 340-356`
- Test: `src/test/scala/oathdigital/gameplay/TakeWealthProcedureSuite.scala:107-127`, `EndWakeProcedureSuite.scala:69-91`, `oathkeeper/OathkeeperProcedureSuite.scala`, `StateBasedEvaluationSuite.scala`

**Interfaces:**
- Consumes: Task 4's families, Task 7's triggered procedure.
- Produces: the rule "`completeAction` runs iff the completed procedure is an `ActionRef`", with no phase input.

- [x] **Step 1: Write the failing tests.**

In `TakeWealthProcedureSuite`, replace "a completed Wake action does not run the Act action boundary" with:

```scala
  test("a completed Take Wealth runs the action boundary and stays in Wake") {
    // An empty site with capacity makes the boundary observable; the
    // precondition proves it has something to do here.
    val base = wake()
    val empty = base.game.current.map.inPlay.find(id =>
      catalog.sites.find(_.id == id).exists(_.capacity > 0)).get
    val ready = base.copy(game = base.game.copy(current =
      base.game.current.copy(map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(empty,
          base.game.current.map.sites(empty).copy(
            forces = SiteForces.Empty))))))
    assert(StateBasedEvaluation.banditRefill(catalog, Ready(ready))
      .toOption.flatten.nonEmpty,
      "precondition: the boundary would refill bandits in this state")
    val result = accepted(ready)
    assertEquals(result.events.collect { case event: BanditsRefilled => event }.size, 1)
    assertEquals(result.continue, AwaitingWakeAction(ready.game.current.turn.activePlayer))
  }
```

In `OathkeeperProcedureSuite`, add:

```scala
  test("a tie found after Take Wealth parks in Wake and returns the player to Wake") {
    val active = base.game.current.turn.activePlayer
    val holder = players.find(_ != active).get
    val leaders = players.filterNot(_ == holder).take(2)
    val ready = TakeWealthFixture.wakeReady(ruled(base, leaders.map(Some(_)),
      holder = Some(holder)))
    val parked = TakeWealthFixture.take(rules, ready).toOption.get
    assertEquals(parked.continue, OathContinue.AwaitingOathkeeperRecipient(
      holder, DecisionId(OathkeeperProcedure.recipientDecisionId)))
    val chosen = rules.resolveWalker(parked.state, holder,
      OathkeeperProcedure.recipientDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Player(leaders(0)))).toOption.get
    assertEquals(chosen.continue, OathContinue.AwaitingWakeAction(active))
  }
```

`TakeWealthFixture` is `TakeWealthProcedureSuite`'s existing `wake()` setup and its `accepted`/`take` command, extracted into a test object in the same file so this suite can call them. Move them without changing their bodies, and give `wakeReady` a parameter for the game it starts from.

In `StateBasedEvaluationSuite`, add:

```scala
  test("once the game has a result, every command is refused at the lifecycle gate") {
    val finished = prepared(Vector.empty).copy(game = prepared(Vector.empty).game
      .copy(current = prepared(Vector.empty).game.current.copy(
        result = Some(GameResult(PlayerId("p1"), VictoryKind.Usurper)))))
    val active = finished.game.current.turn.activePlayer
    val act = finished.copy(game = finished.game.copy(current =
      finished.game.current.copy(turn = finished.game.current.turn.copy(
        phase = Phase.Act))))
    val destination = act.game.current.map.inPlay.find(_ !=
      act.game.current.players.find(_.player == active).get.pawnSite.get).get
    assertEquals(rules.startWalker(Ready(act), ActionRef.Travel, active,
      Vector.empty, Vector(DecisionOptionRef.Site(destination))), Left(GameEnded))
    val wake = act.copy(game = act.game.copy(current = act.game.current.copy(
      turn = act.game.current.turn.copy(phase = Phase.Wake))))
    assertEquals(rules.startWalker(Ready(wake), PhaseTransitionRef.EndWake,
      active), Left(GameEnded))
  }
```

Use a seated player id in `GameResult` if the fixture's players are not `p1`.

In `EndWakeProcedureSuite`, rewrite the comment on "ending Wake does not run the Act action boundary" to say the boundary runs only after an action, and End Wake is a phase transition. Its assertions do not change.

- [x] **Step 2: Run them to verify they fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.TakeWealthProcedureSuite oathdigital.gameplay.oathkeeper.OathkeeperProcedureSuite"`
Expected: FAIL: zero `BanditsRefilled` after Take Wealth, and no park after it.

- [x] **Step 3: Decide the boundary by family.** In `OathRulesWalker`, replace `WalkerCompletion` and `completionIn` with:

```scala
  /** Whether the action boundary follows a completed procedure. Only an
    * action runs it, in whatever phase it ran: Take Wealth does, End Wake (a
    * phase transition) and a triggered procedure do not. Carried by the
    * reference's family, never by a registry flag and never by the phase --
    * Take Wealth and End Wake both start in Wake.
    */
  private def runsActionBoundary(procedure: ProcedureRef): Boolean =
    procedure match {
      case _: ActionRef => true
      case _: PhaseTransitionRef | _: TriggeredProcedureRef => false
    }
```

In `walkerTransition`'s `Finished` branch:
```scala
    case WalkerOutcome.Finished(treeless, steps) =>
      val activePlayer = treeless.game.current.turn.activePlayer
      continuationIn(treeless.game.current.turn.phase, activePlayer).flatMap {
        continue => GameplayTransition(state,
          steps :+ WalkerCompleted(procedure), continue)(evolve)
          .flatMap(transition =>
            if (runsActionBoundary(procedure)) completeAction(transition)
            else Right(transition))
      }
```

Delete Task 7's interim `runsBoundary` expression and the long doc block on the old two-phase rule; keep `continuationIn` and its doc.

- [x] **Step 4: Delete the unreachable guards.** In `OathRules`:

```scala
  protected def completeAction(transition: OathTransition)
      : Either[OathViolation, OathTransition] =
    recordBoundaryFallback(transition)
      .flatMap(appendEvaluation(_, StateBasedEvaluation.banditRefill(catalog, _)))
      .flatMap(oathkeeperStep)
```

Delete `hasResult` if nothing else calls it (`grep -n "hasResult" src/main/scala/oathdigital/gameplay/OathRules.scala`). In `appendEvaluation`, keep `transition.continue` unconditionally:

```scala
  private def appendEvaluation(transition: OathTransition,
      evaluate: OathState => Either[OathViolation, Option[OathEvent]]) =
    evaluate(transition.state).flatMap {
      case None => Right(transition)
      case Some(event) => evolve(transition.state, event).map(next =>
        transition.copy(state = next, events = transition.events :+ event))
    }
```

- [x] **Step 5: Run the gates.**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS" && python3 scripts/check-architecture.py && git diff --check`
Expected: PASS.

- [x] **Step 6: Mutation checks.** Revert after each.
  1. Make `runsActionBoundary` return `true` for `TriggeredProcedureRef`. Expected FAIL: "completing the Oathkeeper procedure runs no action boundary".
  2. Make it return `true` for `PhaseTransitionRef`. Expected FAIL: "ending Wake does not run the Act action boundary".
  3. Make it return `false` for `ActionRef`. Expected FAIL: "a completed Take Wealth runs the action boundary and stays in Wake", and the Travel boundary tests.

- [x] **Step 7: Commit**

```bash
git add -A src
git commit -m "feat(walker): run the action boundary after every action, decided by procedure family"
```

#### What Task 8 settled

Landed as `d5e7e4b..4c98292` (task range `7ccf57a..4c98292`; main content
commit `d5e7e4b`; fix round `4c98292`).

Ruling R-T8a: the plan assumed every action start was already
lifecycle-gated so that `completeAction`'s `hasResult` guards were
deletable as unreachable; a preflight check found `ForgeProcedure.build`
was the one exception (`ForgeRules.validate` checked neither the game
result, phase, nor a legacy pending procedure). The task adds
`OathLifecycle.validateAct(Ready(state), activePlayer)` as the first step
of `ForgeProcedure.build`, matching Recover and Travel, and extends the
"once the game has a result, every command is refused" test to a Forge
start expecting `Left(GameEnded)`. Without this, a Forge could have
started in Wake or over a legacy pending procedure once the `hasResult`
guards were removed.

Review found stale documentation naming the deleted `completionIn`/
`OathRulesWalker.WalkerCompletion` symbols and the phase-based boundary
rule the family rule replaced, at three spots — `WalkerProcedureRegistry
.scala:~208` (EndWake registration doc), `ProcedureRef.scala:~23-31`
(`ActionRef`'s doc reworded to name `runsActionBoundary`;
`PhaseTransitionRef`'s doc dropped its stale "Act" qualifier), and
`WalkerDecisionProjector.scala:~333-338` (`Parked` case class doc's
citation of the deleted `WalkerCompletion` symbol reworded) — plus one
unrelated dead-parameter cleanup (`walkerTransition`'s unused `ready`
parameter). All fixed in the same fix round, `4c98292`.

Open observation (not fixed here, confirmed still present by direct
read on 2026-09-13): `ProcedureRef.scala`'s `PhaseTransitionRef.EndWake`
case-object doc comment (in the `object PhaseTransitionRef` block, not
the `ActionRef.scala:~23-31` trait docs the fix round corrected) still
reads "so no Act action boundary runs after it" — the same stale
phase-qualified framing the fix round removed everywhere else, left
standing at this one additional location. This is the item the ledger
records as "Task 8: minor (deferred): ProcedureRef.scala:72-75... still
says 'no Act action boundary runs after it'"; it is a distinct doc
comment from the one the fix round addressed and remains open. No other
plan defect.

---

### Task 9: Close-out

**Files:**
- Modify: this plan (settled notes, ticked boxes)
- Modify: `docs/superpowers/specs/2026-09-05-procedure-walker-design.md` (Migration status, Verification)
- Modify: `docs/superpowers/specs/2026-09-12-walker-ownership-and-phases-design.md` (status line)

- [x] **Step 1: Record what each task settled.** Under each task in this plan, add a `#### What Task N settled` note with its commit hash, any ruling taken during review, and anything the plan got wrong. Tick completed boxes.
- [x] **Step 2: Update the walker spec.** In Migration status, replace "Nine `PendingProcedure` cases remain" with the eight that remain, removing the `OathkeeperRecipient` bullet. Record that ownership, phase freedom and triggered procedures have landed, and remove "(not yet implemented)" from the in-place notes this work fulfilled. In Verification, record that `WalkerReplayDriftSuite` now covers the Oathkeeper tie. Update the per-file line counts in "Costs worth carrying forward" with measured values: `wc -l src/main/scala/oathdigital/serialization/Walker*Codec.scala src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/main/scala/oathdigital/gameplay/OathRulesWalker.scala src/main/scala/oathdigital/gameplay/actions/Campaign.scala`.
- [x] **Step 3: Mark the design spec implemented.** Add a status line under its title naming the commits.
- [x] **Step 4: Verify each legacy boundary call site.** For every `completeAction` call site in `OathRules.handle` that lacks a `pending.isEmpty` guard (Economy, Search complete, Challenge complete and place-resource, minor actions, Vision reveal, Negotiation decline, Campaign place and raid relocation), confirm the handler clears or never sets `pending` before the boundary. Record the result per site in Task 7's settled note. Any site that can reach the boundary with `pending` set is a bug to report, not to paper over.
- [x] **Step 5: Full gate.**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS" && python3 scripts/check-architecture.py && git diff --check`
Expected: PASS. Record the test counts in the settled note.

- [x] **Step 6: Commit**

```bash
git add docs/superpowers
git commit -m "docs(spec): record what walker ownership and phases settled"
```

#### What Task 9 settled

Landed as this close-out commit, committed atop `4c98292` (docs-only:
this plan, the two specs; no production code touched). Step 4's per-site
legacy boundary verification found no bug — recorded in full in Task 7's
settled note above rather than repeated here, as the brief directs. Step
5's full gate ran clean at branch head `4c98292` before this commit:
backend `./sbtw "test"` — 718 passed, 0 failed, 0 errors; frontend
`frontend/test` — 151 passed, 0 failed, 0 errors; `python3
scripts/check-architecture.py` — passed, 199 production Scala files;
`git diff --check` — clean (no whitespace errors), confirmed by the
chained command's exit code 0. These counts match Task 8's own closing
counts (718/151), as expected since no test changed between `4c98292` and
this close-out. No ruling was needed and no plan defect was found in Task
9 itself; one open observation from Task 8's review carries forward
unresolved (see Task 8's settled note): `ProcedureRef.scala`'s
`PhaseTransitionRef.EndWake` case-object doc still names "Act" in its
boundary description.
