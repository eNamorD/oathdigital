# Walker Step Payload Operations: Operations Belong to the Payload That Carries Them

**Status:** designed 2026-10-19 from an architecture review of the game log's
second operation pass. Implemented 2026-10-19 by
[the plan](../plans/2026-10-19-walker-step-payload-operations.md), which
records its execution and the two expectations it got wrong.

## Goal

`WalkerStepRecorded` carries `ops` beside a `WalkerStepPayload`, and only one
of the three payloads may have them. "A roll step carries no operations" is a
doc comment enforced by three runtime checks in `WalkerReplay`. Move the
operations into the payload that owns them, so a roll or choice step with
operations stops being a shape the code must defend against and becomes a
shape it cannot express.

This change exists because a second candidate needs it. The architecture
review of 2026-10-13 found `LogJournal.opSteps` applying recorded operations
guard-free, with `.getOrElse(state)` where the engine's fold short-circuits.
That candidate's design makes `opSteps` strict, and its match today reaches
for any `WalkerStepRecorded` because `ops` sits on the event rather than on
the one payload that can have them. This document is sequenced first so that
`opSteps` gets its honest match and its honest error handling together.

## Background

### The constraint is a comment, enforced three times at runtime

`WalkerStepRecorded` is `(nodeId, payload, ops, contributions)`
(`gameplay/walker/WalkerEvents.scala:57-62`). `ChoicePayload` and
`RollPayload` both document that "the step's `ops` stay empty", and
`WalkerReplay` enforces it:

- `gameplay/walker/WalkerReplay.scala:74` — a recorded `RollPayload` must not
  contain operations.
- `:83` — a recorded `ChoicePayload` must not contain operations.
- `:94` — a recorded delta step must contain operations.

The first two defend against a shape the type permits and nothing legitimately
produces. Both construction sites for delta steps already wrap themselves in
`Option.when(updated.executed.nonEmpty)`
(`gameplay/walker/WalkerSteps.scala:69`,
`gameplay/walker/AdviserArrivalPass.scala:45`), and the two non-delta sites
pass `ops = Vector.empty` explicitly
(`gameplay/walker/WalkerSteps.scala:108,128`). So in memory the invariant
already holds by construction; the checks exist for decoded input.

### The open family's rationale is spent

`WalkerStepPayload` is deliberately unsealed, and its comment says why:
"concrete per-node payloads specialize in later tasks (DecisionStarted/
Resolved, RollRecorded, ...) and may be declared wherever the owning action
lives" (`gameplay/walker/WalkerEvents.scala:5-11`). Both halves of that
rationale are spent:

- The comment landed in `de52be5b` on 2026-09-06. `ChoicePayload`
  (`6b065377`) and `RollPayload` (`4a4e0b8f`) landed the same day. The
  specializations arrived immediately, under different names.
- `DecisionStarted`, `DecisionResolved` and `RollRecorded` appear nowhere in
  the codebase or the docs. Their closest ancestors —
  `RestPowerDecisionStarted`, `LeagueTreatyResolved` — were pre-walker event
  types that the rest-walker plan deleted.
- All three payloads live in `WalkerEvents.scala`. Nothing is "declared
  wherever the owning action lives".

The three payloads are the three categories of durable walk fact: the step
applied operations, it recorded an answer, or it recorded dice. The walker's
node vocabulary adds no fourth: the composites and control nodes
(`model/Operation.scala:55,140-186` — `EndWalk`, `Repeat`, `Branch`,
`AutomaticUse`, `Sequence`) record nothing durable of their own and resolve
into those three, and the two nodes that do record a fact of their own are
`Decide` (`model/Operation.scala:102`) and `Roll`
(`model/CoreOperations.scala:634`), which already have payloads. The one plausible fourth
category, recorded randomness beyond dice, is already covered as operations:
`Draw` names its cards, so deck order lives in the batch. No remaining
roadmap phase mentions payloads. And when a candidate for a fourth arrived —
automatic-power consent, which asks rulers through a hidden step — it shipped
its own `ConsentEvent` family rather than a step payload.

The openness does have one live consumer, and it is a test:
`serialization/WalkerOperationWireSuite.scala:248-262` declares its own
`UnknownStepPayload` to assert that the encoder returns a typed `WireError`
rather than throwing.

## Measurements

| Thing | Count |
| --- | --- |
| `WalkerStepRecorded(` sites | 52, across 28 files; 17 in `src/main` |
| `DeltaRecorded` references | 28 |
| Construction sites in `src/main` | 5 (`WalkerSteps` ×3, `AdviserArrivalPass`, codec decode) |
| Readers of `ops` | 5 (`LogJournal.opSteps`, `NoteLines:193`, `OathRulesWalker:354,422`, `WalkerSimulation:307`) |
| Guards removed | 2 of 3 |
| Production `throw`s removed | 1 of 16 |

Every match site changes whether or not it reads operations, because the
event's arity drops from four to three. File sizes leave room:
`WalkerEvents.scala` 115 lines, `WalkerEventCodec.scala` 305,
`WalkerReplay.scala` 196, against the project's 800-line cap.

## Design

### The type

`WalkerStepPayload` becomes `sealed`. `WalkerStepPayload.DeltaRecorded` — a
`case object` nested in the companion — becomes top-level
`final case class DeltaPayload(ops: Vector[CoreOperation])`, matching
`ChoicePayload` and `RollPayload` in both naming and placement. All three
stay in `WalkerEvents.scala`, which sealing now requires. `ops` leaves
`WalkerStepRecorded`, which keeps `nodeId`, `payload` and `contributions`.

No mixin trait. An `OperationPayload` trait that any operation-carrying
payload extends was considered and rejected: see below.

### The wire

Operations move under the payload object. `encodeStepPayload`
(`serialization/WalkerEventCodec.scala:86-101`) writes
`{"kind": "delta", "ops": [...]}`; `decodeStepPayload` (`:102-114`) reads
them; the event's top-level `"ops"` key goes from both the encoder arm
(`:26-31`) and `decodeStepRecorded` (`:269-279`). Operation decoding stays
inside `decodeStepRecorded`'s `try`, so a missing key still returns
`InvalidValue` rather than escaping as an exception.

`GameEventWire.FormatVersion` stays at `1`.

### Journals already written

They stop loading. The standing position is that a wire change need not keep
old journals decodable. An old walker step now fails decode on its missing
`ops` key; `decodeRecords` maps that to `CodecFailure` before the fold runs
(`application/GameApplicationService.scala:253-259,292-304`), so the load
fails cleanly and nothing silently skips an event. The development database
in `var/` does not survive this change.

The version was deliberately left at `1` rather than bumped. A bump would
reject an old journal at its envelope, by name, through
`UnsupportedFormatVersion` (`serialization/GameEventWire.scala:25,166-175`);
leaving it reports the break as a field error at the first walker step
instead. Both fail the load; the choice is diagnostics, and the version field
stays available for a change that needs it.

### Guards

`WalkerReplay:74` and `:83` are deleted: the shapes they reject are no longer
representable. `:94` stays — an empty `DeltaPayload` is still representable
and the wire is untrusted input, so the check belongs where decoded facts are
applied rather than in a constructor.

`encodeStepPayload`'s `case other => throw new IllegalArgumentException` is
deleted. Sealing makes it unreachable, and `-Werror` (`build.sbt:69-76`)
fails the build on an unreachable case.

### Readers

Five sites match `DeltaPayload(ops)` instead of reading `step.ops`:

- `application/gamelog/LogJournal.scala:119-128` (`opSteps`).
- `application/gamelog/NoteLines.scala:193`, which already names the payload.
- `gameplay/OathRulesWalker.scala:354` and `:422`.
- `gameplay/walker/WalkerSimulation.scala:307`.

`OathRulesWalker:354` keeps `.nonEmpty` on the payload rather than becoming a
bare type test. It asks whether a procedure runs operations before its first
decision, and an empty delta must not answer yes. `:422` and
`WalkerSimulation:307` are collects, so matching the payload preserves them
exactly: choice and roll steps contribute nothing today and cannot contribute
anything after.

The two non-delta construction sites drop `ops = Vector.empty` entirely.

### Stale comments

Three comments are wrong today and all three sit on lines this change
rewrites:

- `WalkerStepPayload`'s rationale names three payloads that were never built
  and a placement rule never used. It is replaced by the sealed family's own
  reason: the three payloads are the three categories of durable walk fact,
  and a fourth becomes a compile error at every match.
- `DeltaRecorded`'s "It carries nothing" is falsified directly.
- `WalkerStepRecorded`'s doc calls `contributions` "the powers whose gather
  produced this step's `ops`", but every construction site passes it,
  choices and rolls included. The field is not operations-scoped; only the
  comment said so.

## Considered and rejected

- **An `OperationPayload` mixin.** A trait any operation-carrying payload
  extends, so readers match the trait and keep working as the family grows.
  Rejected together with the decision to seal: the two are answers to the same
  question and sealing is the stronger one. A mixin would let a second
  operation-carrying payload through every reader unexamined, where sealing
  turns it into a compile error naming each site. Shipping both would mean a
  one-implementor trait with no growth path.
- **A top-level `ops` key on the wire.** Keeps the serialized shape stable.
  Rejected: the decoder would then have to decide which payload receives the
  operations, which is the same type-and-data mismatch this change deletes,
  relocated to the boundary.
- **Reading both wire shapes.** Decode operations from the payload, falling
  back to the old top-level key, so existing journals keep loading. Rejected:
  a compatibility fallback with no retirement date, which is the pattern the
  `opSteps` candidate exists to remove.
- **A non-empty collection type for `ops`.** Would make an empty delta
  unrepresentable too. Rejected: the repo has no such type, construction
  already guarantees non-emptiness, and the only source of an empty delta is
  the wire — which needs a runtime check whatever the type says.
- **`require` in `DeltaPayload`'s constructor.** Rejected: routes a wire
  validation failure through exception plumbing to reach the same `Left` the
  existing guard returns directly.
- **Sealing in a separate change.** Rejected once the growth rationale was
  found spent: the comment stating it is being rewritten here anyway, and
  leaving the family open would mean shipping the mixin instead.

## Testing

`WalkerOperationWireSuite:248-262` is deleted. Sealing makes its
`UnknownStepPayload` uncompilable from another file.

It is replaced rather than dropped. That test is currently the only one in
the repo asserting that the encode path returns a typed `WireError` instead of
throwing — every wire-error test in `GameEventWireEnvelopeSuite` exercises the
decode side (`UnsupportedFormatVersion`, `UnknownEventType`, `MalformedJson`,
`WrongType`, `InvalidSequence`), none of which reaches
`encodePayloadSafe`'s catch (`serialization/GameEventWire.scala:85-96`). That
method's own doc comment states the guarantee, so deleting its only test
would leave the claim unchecked.

The replacement asserts the same guarantee one layer out, where it stays
reachable: `OathEvent` and `WalkerEvent` are open traits and the encoder
dispatch is a composed `PartialFunction`
(`serialization/GameEventWire.scala:250-262`), so an unknown *event* still
throws into that catch. The new test lives in
`GameEventWireEnvelopeSuite`, declares its own `case object` extending
`WalkerEvent`, and asserts `encodeEvent` returns a `Left`.
`OathRulesEvolveSuite.scala:14` already declares an unregistered walker event
for `evolve`, so the shape has a precedent.

Beyond that the existing suites carry the change, and the plan states which:
the walker wire suites round-trip the moved payload shape, and the walker
replay suites already cover the `ops.nonEmpty` rejection that survives.

## Consequences

- A roll or choice step carrying operations is unrepresentable. Two runtime
  guards and one production `throw` are gone.
- A future operation-carrying payload is a compile error at every payload
  match, which is the review it deserves.
- Sealing adopts the one-file constraint that forced the
  [operation algebra depth design](2026-10-14-operation-algebra-depth-design.md)
  when `model/CoreOperations.scala` reached 784 lines with a sealed
  `CoreOperation`. Three cases in a 115-line file makes that remote, but it is
  the same trap and this document is where it was accepted.
- Journals written before this change do not load, and the `var/` development
  database dies with it.
- `LogJournal.opSteps` can then match the one payload that has operations.
  The candidate that makes it strict is sequenced after this one and is
  recorded separately; it also carries three findings this change does not
  touch — the log's per-marker re-application cost, `DetailLines` building
  `OpStep`s whose states do not bracket their operation, and
  `LogScripts.formatAppended`'s unvalidated state hooks.
