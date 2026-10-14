# Operation Algebra Depth: Generic Walker Vocabulary Apart from Oath's Operations

**Status:** designed 2026-10-14. Not built.

## Goal

No new operation can be added to the game. Two production files sit against
the repo's 800-line cap, and both walls must come down before the next
catalog batch adds a case:

- `serialization/WalkerOperationCodec.scala` is 795 lines. The smallest
  measured codec cost of one new operation is +9 lines.
- `model/CoreOperations.scala` is 784 lines with 49 operations, and cannot
  be split while `CoreOperation` is sealed: Scala confines a sealed type's
  direct subclasses to one source file.

The fix is not a line-budget split. `CoreOperation` and `PrimitiveOperation`
each carry two unrelated jobs, and separating them removes more lines than a
split would while drawing a boundary the codebase already assumes:

| Trait | Generic job | Game-specific job |
| --- | --- | --- |
| `CoreOperation` | the delta-batch vocabulary the executor, policies and journal accept | "is an operation of Oath" |
| `PrimitiveOperation` | leaf marker, `children = Vector(this)` | sealed member of that family |

Lifting the generic jobs into the unsealed `Operation` layer leaves
`CoreOperation` holding only Oath's operations, and makes the walker's
control vocabulary reusable vocabulary rather than a special case the
serializer must defend against.

## Background

### The boundary is already drawn in the signatures

Tree position is typed `Operation`; delta position is typed `CoreOperation`.
`OperationRewrite.ops` takes `Vector[Operation]` and returns
`Vector[Operation]` (`gameplay/powerresolver/OperationRewrite.scala:38-39`),
while a power body that produces deltas returns
`Either[OathViolation, Vector[CoreOperation]]`
(`gameplay/powers/whenplayed/FabledFeast.scala:82`).

Across all of production, ten lines name both a `CoreOperation` type and one
of the control operations; six are import lists. The four substantive ones
are the same shape --
`BuildOps((_, _) => Right(Vector[CoreOperation](Move(...))))` -- a control
operation *wrapping* a delta vector, never sitting inside one. `Defer` is
constructed at three production sites, each with a bare game operation. No
control operation is placed in a `Vector[CoreOperation]` anywhere in the
repository, so none of the 289 `Vector[CoreOperation]` sites is affected by
moving the control operations out of that family.

### What the codec spends its lines defending

`encodeOperation` is total over `CoreOperation` by design: a bounded
operation set is what let Catacombs' recorded `Move` out of `Location.Deck`
reach an append-time throw with no test to catch it. Six of its arms
(`Decide`, `BuildOps`, `Repeat`, `Branch`, `Sequence`, and the two
`CardPlayed` hooks) exist only to throw `UnencodableOperation`, because those
cases are members of the family but can never legally be recorded:
`ProcedureWalker` records an already-applied delta batch, never one of its
own control nodes. Those arms plus the 24-line class doc that explains them
are 34 lines of defence against a state the type system could forbid
outright.

### Settled ground

The replay-posture, delta-meaning-retention and decision-form-tag-separation
decisions
(`docs/superpowers/specs/2026-09-24-replay-posture-decision.md`,
`2026-09-24-delta-meaning-retention-decision.md`,
`2026-09-25-decision-form-tag-separation-decision.md`) are settled and this
design does not reopen them. Nothing here changes what is recorded, what a
recorded delta means, or how a decision's form is tagged. The wire format is
unchanged in every slice.

## Measurements

A throwaway compile probe on 3dcbfd9a built the whole change and was
reverted. Full backend and test compile, Scala 3.9.0 under `-Werror`:

- Production cost: 8 tracked files, +31 / -159 lines. 104 of those deleted
  lines reappear as the moved control operations, so the change is close to
  line-neutral overall -- about 25 production lines net. Its value is the
  redistribution and the deleted defensive code, not net shrinkage.
- `CoreOperations.scala` 784 to 681. `WalkerOperationCodec.scala` 795 to
  761 before slice 1's split, near 522 after it. `Operation.scala` 48 to
  roughly 160 once the control operations land there.
- `scripts/check-architecture.py` passes.
- Every intermediate compile failure was an `E030 Unreachable case` warning
  promoted by `-Werror`. No logic broke anywhere. The compiler's whole
  complaint was that defensive code had become provably dead.
- Test cost: six errors. Five are in `WalkerOperationWireSuite.scala:448-458`,
  the suite that exists only to pin that the codec's throw is reachable; it
  is deleted with the arms. The sixth is one type annotation in a file
  untracked at probe time and not part of this change.
- Mechanical call-site cost: four files for the `PrimitiveOperation` to
  `LeafOperation` retarget.

The probe also surfaced a dead guard nobody predicted, at
`gameplay/operations/OperationResolution.scala:63-64`: the restriction
screener special-cases containers, and stops needing to once it can no
longer be handed one.

## Design

### The generic layer

`model/Operation.scala` becomes the reusable walker vocabulary and holds, at
roughly 160 lines:

- `trait Operation` -- unsealed root. Gains `required`, the batch posture
  that generic code reads off an `Operation`-typed node, beside the `window`
  hook that is already there for the same reason.
- `trait LeafOperation extends Operation` -- new, unsealed. Defines
  `final val children: Vector[Operation] = Vector(this)`. This is
  `PrimitiveOperation`'s leaf-marker job, lifted.
- `trait RewritableComposite`, `case object EndWalk` -- unchanged.
- `Decide` and `BuildOps`, each extending `LeafOperation`.
- `Repeat`, `Branch`, `Sequence` and `Sequence`'s companion, each extending
  `Operation`.

One file rather than a sibling: it makes the boundary legible as two files
opposite each other, and leaves roughly 640 lines of headroom. A second
generic file is worth adding when the vocabulary grows, not before.

### The game-specific layer

`model/CoreOperations.scala` keeps Oath's operations, at 681 lines:

- `sealed trait CoreOperation extends Operation` -- keeps `simultaneous`,
  which nothing generic reads. `required` moves up.
- `sealed trait PrimitiveOperation extends CoreOperation, LeafOperation` --
  no body; its `children` definition comes from `LeafOperation`.
- The 44 game operations, unchanged.
- `CardPlayedFaceup` and `CardPlayedFacedown` extend `Operation` rather than
  `CoreOperation`. They are game-specific in vocabulary -- they name a card
  play -- but window-only composites in behaviour, never deltas. Leaving the
  family is what makes `encodeOperation` total: the type the codec
  dispatches on is the right place to carry "never a recorded delta", and a
  codec that is total beats one that throws defensively.

### Leaf dispatch

Four files retarget `PrimitiveOperation` to `LeafOperation`:
`gameplay/walker/ProcedureWalker.scala`,
`gameplay/walker/WalkerPowerGather.scala`,
`gameplay/powerresolver/OperationRewrite.scala`,
`application/gamelog/DetailLines.scala`, along with `Operation.flatten`
itself. Each of these asks "is this node a leaf", not "is this node a game
operation", so the retarget corrects the predicate rather than widening it.

### The walker's leaf step

`ProcedureWalker.scala:641-644` peels `Decide`/`Roll` and `BuildOps` off a
leaf and falls through `case delta => WalkerSteps.record(delta, ...)`. Once
control leaves are not `CoreOperation`, that fallthrough cannot type-check,
because the remaining leaf is not provably a game operation.

**Amended 2026-10-14, while writing the plan.** The design originally called
for a third slice that pushed control-leaf handling up into `walkNode` so
that no catch-all existed anywhere. That is not achievable, and the reason is
the design's own premise: `LeafOperation` is unsealed, because an extensible
generic layer is the point. No match over an unsealed type is ever
exhaustive, so the compiler cannot be shown that a leaf which is neither
`Decide` nor `BuildOps` must be a `CoreOperation` -- and
`WalkerStubs.WindowedNode` is standing proof that leaves are authored outside
the family. A catch-all at the leaf tier is structural, not defensive.

So the resolution is the arm itself, in `runNarrowed`:

```scala
case build: BuildOps => WalkerSteps.runBuildOps(build, ctx, path, contributions)
case delta: CoreOperation =>
  WalkerSteps.record(delta, ctx, path, contributions, strict).map(Done(_))
case other => contractViolation(
  s"leaf ${WalkerSteps.leafLabel(other)} is neither a control leaf nor a " +
    "recordable operation")
```

`WalkerSteps.record` already takes `CoreOperation`, so the typed boundary is
reached by narrowing this one arm; nothing moves. `contractViolation` is
already the walker's idiom for an unreachable contract breach, used eleven
times in this file, including the neighbouring resume arms for "not a
Decide/Roll park". This is three lines in the same edit as the rest of the
hierarchy move, not a separate reviewable deliverable, so the former slice 3
is folded into slice 2 and the plan has two slices.

What the change still buys at this boundary is real but narrower than first
claimed: the codec's six throws and `OperationResolution`'s container guard
go away entirely, and the walker keeps one arm whose impossibility is
asserted in the walker, where the walker's own contract lives, rather than in
the serializer, which had no business knowing it.

### Codec

Slice 1 is independent of everything above and is the actual unblock. The
value codecs run from `encodeGive` (near line 557) to end of file, 239
lines, and move to a sibling trait mixed into `WalkerOperationCodec` beside
the four that are already there (`CampaignResultCodec`, `FollowUpWire`,
`OathkeeperWire`, `PositionWire`). That takes the codec to roughly 556 with
no model change at all.

The split widens private helpers to `protected`. One of them collides:
`decodeOrientation` exists twice today, `protected final` taking
`ujson.Value` at `serialization/GameEventJsonSupport.scala:102` and
`private` taking `String` at `serialization/WalkerOperationCodec.scala:766`.
They are an overload, not an override, and resolve today only because
callers pass `.str`; widening merges them into one overload set. This is the
configuration recorded at `WalkerOperationCodec.scala:760-762`, where a
same-named private method was dispatched in place of
`GameEventJsonSupport`'s and silently broke `Flip` and `Play` encoding. The
encode half of that pair was already renamed to `orientationKey`; the decode
half was missed. Rename the `String`-taking method `orientationFromKey`, per
that convention, as part of slice 1.

Four helpers in the moved block have callers outside the file
(`decodeOrientation` 3, `decodeSignedInt` 3, `encodeDieFace` 2,
`decodeDieFace` 2). A trait-to-trait move keeps them visible through the
mixin, so this costs nothing beyond the widening above.

Slice 2 then deletes the six throwing arms and the class doc, and slice 2
also retires `UnencodableOperation`: `serialization/WireError.scala:42`, the
special case at `serialization/GameEventWire.scala:99`, and the paragraph
above it. Nothing constructs it once `encodeOperation` is total.
`-Wunused` does not flag an unused case class, so the build will not force
this; it is removed because its justification was that a control node might
reach the encoder, and this design makes that unrepresentable.

### Stale comments

Two comments cite a Scala 2.13 constraint on a 3.9.0 build:
`model/Operation.scala:10-13` and
`src/test/scala/oathdigital/testkit/WalkerStubs.scala:14-19`. The rule they
describe is unchanged in Scala 3 -- only the version is wrong -- but after
this change their conclusion changes too. Both are rewritten to state the
Scala 3 rule and to say what unsealed now buys: generic vocabulary authored
outside the game's family, which `WalkerStubs.WindowedNode` already
demonstrates.

## Slices

1. **Value-codec split.** Move the value codecs out of
   `WalkerOperationCodec` into a sibling trait; rename
   `decodeOrientation(String)` to `orientationFromKey`. No model change. A
   pure move: every spelling stays byte-identical, which
   `WalkerOperationWireSuite` pins. Unblocks new operations on the codec
   side by itself.
2. **Hierarchy move.** Add `LeafOperation`, lift `required`, move the five
   control operations into `Operation.scala`, take the two `CardPlayed`
   hooks out of `CoreOperation`, retarget the four leaf-dispatch files,
   narrow `runNarrowed`'s fallthrough, delete the codec's six arms and class
   doc, delete the dead guard in `OperationResolution`, and delete the suite
   that pins the codec's throw. `-Werror` promotes every one of those
   deletions to a build break, so they are one atomic commit and cannot be
   split further. Retiring `UnencodableOperation` and rewriting the two
   stale comments are separable and follow as their own commits.

Slice 1 stands alone and can land on its own. Slice 2 depends on nothing in
slice 1 and could land first; the plan runs slice 1 first because it is the
unblock and the cheaper review.

## Testing

- `WalkerOperationWireSuite` pins slice 1 as a pure move: the encoded
  spellings do not change, so the suite passes untouched.
- Slice 2 deletes `WalkerOperationWireSuite.scala:448-458`, which asserts
  the codec's throw is reachable. That assertion becomes unwritable, which
  is the proof the throw was never a real path. No replacement test: the
  property it guarded is now the compiler's.
- `encodeOperation` stays total over `CoreOperation`, so a genuinely new
  game operation still fails to compile until it is given a real arm. What
  is given up is that a new *control* operation forces a codec arm; it no
  longer needs one, because it is no longer in the encoded family.
- Walker suites cover the narrowed `runNarrowed` arm unchanged: the arm
  order is preserved, so which leaf each case catches does not change. The
  new `contractViolation` arm is unreachable by construction and gets no
  test, for the same reason the codec's throws are being deleted rather than
  tested.
- `scripts/check-architecture.py` and `BackendArchitectureSuite` gate the
  line counts at each slice.

## Consequences

- Headroom goes from 16 lines to roughly 120 in `CoreOperations.scala`, and
  from 5 to roughly 280 in the codec -- at the measured +9 lines per
  operation, room for the next several batches rather than none.
- `Operation` is the reusable walker algebra; `CoreOperation` is Oath's
  vocabulary. A future walker-level control node is authored in the generic
  layer and needs no serializer arm, no restriction-screener guard and no
  explanatory comment.
- `Operation.flatten`, `OperationRewrite` and the log's detail lines
  dispatch on what a node *is* -- a leaf -- rather than on which family it
  belongs to.
