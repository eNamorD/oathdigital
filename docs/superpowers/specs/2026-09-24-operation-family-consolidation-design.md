# Operation Family Consolidation

> Status: approved in conversation on 2026-09-24. This is a behavior-preserving
> ownership refactor of `gameplay/operations`. Its one deliberate replay change
> is recorded in the [replay posture decision](2026-09-24-replay-posture-decision.md).
> No production implementation is authorized by this document alone.

## Purpose

Legality for one core operation is spelled twice. `OperationShape`
(`OperationValidator.scala:63-770`) is a pure copy of the guards that
`OperationStateMutation` and `OperationCardMutation` run while mutating, kept
byte-identical by hand. The copies have drifted in both directions:
`InvalidStackPosition` exists at four sites in the shape layer and nowhere in
the mutation; `InvalidDestination` at nine sites against three. The
`WalkerReplay` path executes recorded operations through `OperationExecutor`
alone and so never reaches the shape layer. A recorded card move into a
regional discard with no stack position replays to a state that has silently
lost the card, because `insertStack` treats an unspecified position as a no-op.

Put each guard beside the mutation it defends, once, and make the mutation run
the guard as its precondition. Both entries into operation application, the
live pipeline and replay, then cross the same guard set.

## Ownership and interface

`gameplay/operations` is split by operation family. Each family file holds the
shape guard and the state mutation for the operations it owns, exposed to the
package only.

| File | Owns |
| --- | --- |
| `CardMovementOperations.scala` | `Move` of a card, `Bury`, `Play`, `Swap`, `Draw`, card `Take`/`Give`, discard storage; stack position conventions; card source, order and destination legality; card removal and insertion. Renamed from `OperationCardMutation.scala`. |
| `CardFaceOperations.scala` | `Flip`, `Reveal`, `Peek`: orientation and knowledge. |
| `ResourceOperations.scala` | `Move` of favor, secrets and warbands; `FlipSecrets`; discard resource descriptions; counted source sufficiency and destination compatibility; the six-pass counted fold. |
| `BoardControlOperations.scala` | `Move` of a pawn or banner. |
| `TurnStateOperations.scala` | `GainSupply`, `SpendSupply`, `AdvanceVisionsDrawn`, `ModifyDicePool`, `ModifyRollOutcome`, `RecordPowerUse`, `EnterPhase`, `SetOathkeeper`, `RecordCampaignResult`, `BeginTurn`. |
| `OperationStateWrites.scala` | Shared state writers (`updatePlayer`, `updateSite`, `updateCommonCards`, `updateCardTokens`, `updateCardState`, `updateAtlasCardState`, `semanticLocation`, `sequence`) and the single `CardTransfer` collector. |
| `OperationApplication.scala` | The two dispatchers. `validate(ready, operation): Vector[OperationReason]` aggregates every family's guard against the initial state, in today's order, for the resolver. `mutate(ready, operation): Either[OperationError, ReadyGame]` runs the guard as its precondition and then the family mutations in today's order. Also the running-board simulation the guard fold threads through secrets and supply, and the reason classification. |

`OperationValidator.scala` and `OperationStateMutation.scala` cease to exist.

The two entries stay where they are and keep their contracts.
`OperationPipeline.run` settles, resolves through `OperationResolution` (which
now takes the allowlist and restrictions directly), executes, applies the
owning update and runs the post-state invariant. `OperationExecutor.execute`
calls `OperationApplication.mutate`; `WalkerReplay.executeRecorded` is
unchanged and reaches the guards through it. The live path runs the shape
guard twice, once for reasons and once as the precondition; the guard is pure.

`OperationPolicy` stays. It has three production adapters (`Permissive`,
`StateBasedOperationPolicy`, `MinorActionOperationPolicy`) and its
allowlist-before-shape precedence is observable on the two legacy-event paths.
Only `OperationPolicy.all`, which has no caller, is removed. The 45-line
`OperationValidator` class, whose three methods concatenate allowlist, shape
and restriction reasons, is removed; `OperationResolution` owns that
concatenation.

## Removed with no replacement

- `OperationShape.validateBatch`, `OperationShape.first`,
  `OperationPipeline.report` and the cross-operation conflict detector. Their
  only route in was `report`, whose one caller is a test. The pipeline's own
  doc explains why aggregated initial-state validation cannot be authoritative.
- `ClearDicePool`. Encodable and recorded, never applied by any mutation and
  never emitted by any production code. Its two codec arms and its wire-suite
  fixture line go with it.
- `OperationStateAdapter.applyOperation`, a four-line forwarder.
- The duplicated helper pairs `finiteSufficiency`/`requireFinite` and
  `Transfer`/`CardTransfer`: one copy of each survives.
- Every explicit mutation-side re-check of a fact the shape guard already
  establishes. After `mutate` runs the guard first these are unreachable. The
  seven sites are listed in the plan. Total-match fallbacks (`case _ =>
  Left(...)`) are not re-checks and stay.

## Left alone, deliberately

- The eight operations with no shape guard (`ModifyDicePool`,
  `ModifyRollOutcome`, `RecordPowerUse`, `EnterPhase`, `SetOathkeeper`,
  `RecordCampaignResult`, `BeginTurn`, and the site force-kind conflict in
  warband credits) keep their mutation-time guards only. Their legality is a
  property of current state, not of the operation's shape; a shape guard for
  them would read the same state the mutation reads.
- `adjustDicePool` guards underflow with `require`. Converting that to a typed
  rejection is a behavior change and gets its own change.
- `OperationShadow`, `OathContinue.Awaiting*` and `DeltaMeaning` are the
  separate deletion candidate from the same review.
- Migrating `StateBasedOperationPolicy` and `MinorActionOperationPolicy` onto
  `OperationRestriction`, so one seam answers "who may veto an operation", is
  deferred and recorded in `ROADMAP.md`. It reorders precedence on the
  legacy-event path and needs its own preservation argument.

## Preservation boundary

No rejection code or detail string, operation order, executed or skipped
result, wire shape, projected control or walker event changes. Every existing
`OperationPipeline.run` call site keeps its arguments. Any rule defect
discovered during the move is a separate fix.

The one behavior change is on replay: a recorded operation that fails a shape
guard now fails replay with the same `CoreOperationRejected` the live pipeline
would have produced, instead of applying with the guard skipped. This is the
posture `docs/architecture/core-operations-migration.md` asks for ("replay
must reject invalid event facts with `OathViolation`"). No journal written by
this codebase carries such an operation, because the pipeline rejected it
before it could be recorded.

## Verification

Bracket every structural task with the operation suites
(`OperationExecutorSuite`, `OperationPipelineSuite`, `OperationResolutionSuite`,
`OperationVocabularySuite`, `PowerOperationsSuite`, `SupplyAdjustSuite`, the
`gameplay/operations` suites), the walker suites (`ProcedureWalkerSuite`,
`WalkerReplayDriftSuite`) and `BackendArchitectureSuite`, then the full
`./sbtw test`, `./sbtw frontend/test` and `python3 scripts/check-architecture.py`
gates.

`OperationValidatorSuite` loses the tests that reached `validateBatch`,
`report` and `first`; the rest move to `OperationApplication.validate` and
`OperationPipeline.run` and become `OperationApplicationSuite`.

One new suite, `WalkerReplayGuardSuite`, proves the replay change: a hand-built
`WalkerStepRecorded` carrying a card move into a regional discard replays when
the position is `Top` and is rejected with `invalid-stack-position` when it is
`Unspecified`. Before this change the second case replays to a state with the
card gone.

Every production Scala file under `gameplay/operations` stays under 500 lines;
the architecture gate's 800-line bound is not approached.
