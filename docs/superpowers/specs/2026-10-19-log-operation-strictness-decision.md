# The Log's Second Pass Over the Journal: Strict, Not Lenient

> Status: decided 2026-10-19, from the architecture review of 2026-10-13, which
> raised this as candidate I. It records what the log's operation-application
> path is, what its leniency was actually covering, and why the
> [replay posture decision](2026-09-24-replay-posture-decision.md) does not
> reach it. That posture is untouched here. No implementation is authorized by
> this document alone.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **command batch**, **void
marker** and **taken-back group**.

## What the review found, and what it got wrong

`LogJournal.opSteps` steps through a recorded batch one operation at a time, so
a log line is judged against the state immediately around the operation it
describes. It does so without guards, falling back with `.getOrElse(state)`
where the engine's own fold short-circuits a `Left`.

The review read this as a second implementation of operation application, and
therefore as a new operation-application path that the replay posture never
ruled on. Two parts of that are wrong.

The application is not duplicated. `opSteps` folds
`ProcedureWalker.applyRecordedOperation`, which is
`WalkerReplay.applyOperation` — the same `PayCostSettlement.prepare` followed
by `OperationExecutor.execute` that `WalkerReplay.executeRecorded` folds, in
the same order, from the same starting state. What is duplicated is the
traversal, not the application: the engine walks a batch for its end state and
the log walks it again for the intermediate states. The only divergence in
behaviour is error handling.

And the posture question is malformed for this path. `GameApplicationService.history`
and `load` share one `reconstruct`, so a journal that fails to decode or replay
yields no `GameHistory` and the formatter never runs. Every step `opSteps` sees
is one the engine already applied successfully. There is no load outcome to
make stricter and no whole-state invariant to run; this code is downstream of
a load that has already succeeded.

## What the leniency was covering

The fallback's own comment claimed a failure "cannot happen on a journal that
loaded". That is true of production and false of the code as written, because
the formatter is also reachable from hand-built steps.

`LogScripts.formatAppended` and `LogScripts.inserted` build `ReplayStep`s
directly, with states a test patches itself, and pass them to
`GameLogFormatter.format`. Replacing the fallback with a throw and running the
whole suite found it firing in exactly two tests of 4374, both in
`GameLogRareLinesSuite`, and five rejected operations between them:

- **"gains, draws, buries, peeks and reveals each post one line"** — `Bury.to`
  is hardwired to the card's own deck, so burying the relic first takes it off
  the site. The `Peek` and the `Reveal` that follow both fail `missing-piece`.
  Two asserted lines narrate operations that never ran.
- **"warbands name their owner unless they are the actor's"** — every site
  already holds Bandits, because the script's board ends with
  `Table.banditsRefilled`, and a site may not hold two force kinds. Both Exile
  moves fail `conflicting-deltas`; the Imperial move fails
  `unknown-warband-supply`, there being no Imperial supply on any board a
  `Table` can reach. Three of the test's four asserted lines narrate operations
  the engine rejected.

Both tests pass today because the lines they assert are rendered from the
operation value rather than from a state difference. The silence is therefore
invisible: the state chain is broken, every later operation in the batch
continues from a stale state, and the day one of those lines is made to read
`before` and `after` — as `supplySpent` already does — it will narrate from a
state the batch never reached, still green.

So the leniency is not a lenient-rendering feature. It is accidental, and its
only beneficiaries were two tests staging states the rules forbid. Both are
fixable with legal staging.

## Decision

1. **`opSteps` is strict.** A `Left` from operation application throws
   `IllegalStateException` at `LogJournal` construction, joining the impossible-state
   throws already in `src/main`. No flag, no system property, no second door.
2. **One written fold.** `WalkerReplay` gains
   `applyBatch(ready, ops): Either[OathViolation, Vector[ReadyGame]]`, exposed
   on `ProcedureWalker`. `executeRecorded` becomes its last state; `opSteps`
   zips the operations with the starting state and that vector. `OpStep` stays
   in `application`, so ruling 3 of the
   [procedure registry design](2026-10-16-game-log-procedure-registry-design.md)
   holds.
3. **Both tests are rebuilt on legal staging**, stating their own facts rather
   than inheriting a board. The first reorders to Peek, Reveal, Bury. The
   second uses four sites it explicitly empties, and reaches the Imperial
   wording through a private citizen helper of the kind `VowOfWanderingSuite`
   already keeps, moving from the citizen's play area rather than the empty
   Imperial bank.
4. **One focused test pins the throw**, building a step whose recorded
   operation cannot apply. The production path is unreachable, so without it
   the decision has nothing guarding it.
5. **Moving `ops` into `DeltaRecorded` is a prerequisite**, taken as its own
   candidate and sequenced first. While `ops` sits on `WalkerStepRecorded`
   beside its payload, "a roll step carries no operations" is a comment
   enforced by runtime guards, and `opSteps` must match every
   `WalkerStepRecorded` to reach the only case that has operations. Carrying
   them in the payload makes the illegal shape unrepresentable and lets
   `opSteps` name its case honestly.

## Why not the alternatives

- **Keep the fallback, document it.** It would be a branch with no production
  caller and no honest test caller, which is the "fallback no one will ever
  retire" that the
  [procedures journal their own notes design](2026-10-16-procedures-journal-their-notes-design.md)
  already names as the thing to refuse.
- **A named test-only leniency**, opted into per call site. Considered at
  length and dropped once the two tests were costed: both have legal
  equivalents, so the affordance would ship with zero callers. It is also
  production logic that only tests exercise, which the testing philosophy
  treats as code to delete rather than code to name.
- **`NoteCheck`'s strict-by-property pattern.** The house pattern for this
  pane, and wrong here: the property is scope-wide, so setting it for the test
  scope would make all log formatting lenient and re-hide the other 264
  suites. `NoteCheck` is also answering a different question — an undeclared
  note can genuinely occur in production, where a non-applying recorded
  operation cannot.
- **A typed failure carried to the route.** A new error code and a new page
  outcome for a case that cannot occur. The replay posture decision rejected
  exactly this trade for its quarantine option.
- **The engine emitting the intermediate states** on `ReplayStep`. One fold and
  nothing to drift, but every load would pay memory for something only the log
  reads, and it widens an interface replay depends on.

## Consequences

- An architecture review should not re-raise `opSteps` as an unguarded
  operation-application path. It shares the engine's application function; the
  posture governs the load that precedes it.
- A test that stages an illegal board now fails at formatting rather than
  rendering lines from a state its operations never reached.
- The decision is pinned by a test, so re-introducing a fallback turns it red.

## Separate candidates this review surfaced

- **Eager re-application cost.** `LogJournal` applies every operation of every
  step in its constructor, and `GameLogFormatter.format` builds one
  `LogJournal` per standing void marker plus one. A log page therefore costs
  (markers + 1) × whole-journal re-application, per request, per seat.
- **`OpStep`s that do not bracket their operation.** `DetailLines.deltas`
  splits a composite operation into parts and wraps each in an `OpStep` holding
  the whole step's `before` and `after`, so card visibility is judged from
  states that do not surround the operation being described. `OpStep`'s own
  doc comment is false on that path, and this may be a latent wrong-visibility
  bug. It also blocks making `OpStep` correct by construction.
- **Unvalidated staging hooks.** `formatAppended`'s `stateBefore` and
  `lastStateAfter` remain unchecked. Strict `opSteps` catches an operation that
  cannot apply; it does not catch a staged state that play could never produce.
