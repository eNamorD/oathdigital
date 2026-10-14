# Operation Algebra Depth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clear both 800-line walls blocking new game operations, by splitting the walker's value codecs into a sibling trait and separating the walker's generic control vocabulary from Oath's own operations.

**Architecture:** `CoreOperation` and `PrimitiveOperation` each carry one generic job and one game-specific job. The generic jobs -- the delta-batch posture `required`, and `PrimitiveOperation`'s leaf marker -- move up into the unsealed `Operation` layer as `Operation.required` and a new `trait LeafOperation`. The five walker control operations move into `Operation.scala` and extend the generic traits; the two `CardPlayed` window hooks leave `CoreOperation` for `Operation`. `CoreOperation` is then only Oath's vocabulary, `encodeOperation` becomes total with no throwing arms, and `CoreOperations.scala` sheds 103 lines.

**Tech Stack:** Scala 3.9.0, sbt via `./sbtw`, munit, ujson. `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn` (build.sbt:63-68) -- every warning fails the build.

**Spec:** `docs/superpowers/specs/2026-10-14-operation-algebra-depth-design.md`

## Global Constraints

- Scala 3.9.0. `-Werror` is on: an unreachable match case, an unused import or an unused private member is a build failure, not a warning.
- Production Scala files are capped at 800 lines, enforced by `scripts/check-architecture.py:29-30` and `BackendArchitectureSuite`. Run `python3 scripts/check-architecture.py` before every commit.
- `src/main/scala/oathdigital/model` must not import `application`, `gameplay`, `persistence`, `serialization` or `server` (`scripts/check-architecture.py:32-37`).
- The wire format does not change in this plan. Every encoded spelling stays byte-identical; `WalkerOperationWireSuite` is the pin.
- Do not touch `src/test/scala/oathdigital/gameplay/HiddenScopeProbeSuite.scala` beyond the single annotation Task 2 Step 9 specifies. It was untracked when this plan was written -- another session owns it.
- Measurements here were taken at `3dcbfd9a`. If `CoreOperations.scala` or `WalkerOperationCodec.scala` has changed since, re-run the line counts in Task 1 Step 1 and Task 2 Step 1 and adjust the expected numbers rather than assuming them.
- Commit messages end with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.

## File Structure

| File | Responsibility after this plan |
| --- | --- |
| `src/main/scala/oathdigital/serialization/WalkerValueCodec.scala` | **New.** Wire spellings for the values operations are made of: pieces, locations, positions, costs, gives, dice kinds, die faces, orientations, integers. No operation arms. |
| `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` | Operation arms only: `encodeOperation`/`decodeOperation` and the four group helpers. 795 to roughly 522. |
| `src/main/scala/oathdigital/model/Operation.scala` | The reusable walker algebra: `Operation`, `LeafOperation`, `RewritableComposite`, `EndWalk`, `Operation.flatten`, and the five control operations. 48 to roughly 160. |
| `src/main/scala/oathdigital/model/CoreOperations.scala` | Oath's operations only. 784 to 681. |
| `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` | Leaf dispatch retargeted to `LeafOperation`; `runNarrowed`'s fallthrough narrowed to `CoreOperation` with one `contractViolation` arm. |
| `src/main/scala/oathdigital/gameplay/operations/OperationResolution.scala` | Restriction screening, minus the container guard that becomes unreachable. |

---

### Task 1: Split the value codecs out of `WalkerOperationCodec`

A pure move. Nothing about operations or the model changes, so every byte of
encoded output must be identical afterwards. This task alone takes the codec
under the cap and unblocks new operations on the serialization side.

**Files:**
- Create: `src/main/scala/oathdigital/serialization/WalkerValueCodec.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`
- Test: `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala` (run unchanged; it is the pin)

**Interfaces:**
- Consumes: `GameEventJsonSupport`'s `traverse`, `safeIntField`, `requiredField`, `stringField` (via self-type), and `encodeCardId`/`decodeCardId`-style helpers it already uses.
- Produces: `trait WalkerValueCodec`, mixed into `WalkerOperationCodec`. Every moved helper becomes `protected final`, so names are visible to `WalkerOperationCodec` and to the other sibling traits: `encodeGive`, `decodeGive`, `asWarbands`, `encodeBuryableCard`, `decodeBuryableCard`, `encodeDiceKind`, `decodeDiceKind`, `decodeOptionalSignedInt`, `encodePiece`, `decodePiece`, `encodeStackPosition`, `decodeStackPosition`, `encodePositionedLocation`, `decodePositionedLocation`, `encodeLocation`, `decodeLocation`, `encodeCardDeck`, `decodeCardDeck`, `encodeCost`, `decodeCost`, `orientationKey`, `orientationFromKey`, `encodeDieFace`, `decodeDieFace`, `decodeSignedInt`, `decodePositiveInt`.

- [ ] **Step 1: Confirm the starting state**

```bash
cd /Users/roman/projects/oathdigital
wc -l src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala
grep -n 'private def encodeGive' src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala
./sbtw -batch 'root/Test/compile'
```

Expected: 795 lines; `encodeGive` at line 557; compile succeeds. If the line
numbers differ, re-derive the cut point from the `encodeGive` anchor and
adjust every number below.

- [ ] **Step 2: Capture a wire baseline to compare against**

The suite is the real pin, but a recorded baseline catches a silent spelling
change that no assertion happens to cover.

```bash
./sbtw -batch 'testOnly oathdigital.serialization.WalkerOperationWireSuite' 2>&1 | tail -5
```

Expected: PASS. Note the test count; it must be identical at Step 8.

- [ ] **Step 3: Create the new trait with the moved block**

The block runs from `private def encodeGive` (line 557) to end of file.
Build the new file, then delete the block from the old one.

```bash
cd /Users/roman/projects/oathdigital
F=src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala
N=src/main/scala/oathdigital/serialization/WalkerValueCodec.scala
{
  cat <<'HDR'
package oathdigital.serialization

import oathdigital.model._

/** Wire spellings for the values a recorded operation is made of -- pieces,
  * locations, positions, costs, gives, dice kinds, die faces, orientations
  * and bounded integers -- split out of `WalkerOperationCodec` to keep it
  * under the 800-line bound. A pure move: every spelling is byte-identical
  * to the version before the split, which `WalkerOperationWireSuite` pins.
  *
  * Mixed into `WalkerOperationCodec`, which keeps the operation arms. The
  * helpers here are `protected final` rather than `private` because the
  * operation arms and the other sibling traits call them; see the naming
  * note on `orientationKey`/`orientationFromKey` below before adding a
  * method whose name also exists in `GameEventJsonSupport`.
  */
private[serialization] trait WalkerValueCodec:
  this: GameEventJsonSupport =>
  import WireError._

HDR
  sed -n '557,795p' $F
} > $N
sed -i '' '556,795d' $F
wc -l $F $N
```

Expected: `WalkerOperationCodec.scala` at exactly 555 lines, and
`WalkerValueCodec.scala` at roughly 258 -- the 239 moved lines plus the
header above. Only the 555 is exact; if it differs, the cut point was wrong.

- [ ] **Step 4: Widen the moved helpers and mix the trait in**

Every moved helper except the three already `protected final` is `private`,
and 24 of the 26 are called from the retained operation arms. Widen them all
for consistency.

```bash
cd /Users/roman/projects/oathdigital
N=src/main/scala/oathdigital/serialization/WalkerValueCodec.scala
perl -pi -e 's/^  private def /  protected final def /' $N
perl -0pi -e 's/private\[serialization\] trait WalkerOperationCodec extends CampaignResultCodec\n    with FollowUpWire with OathkeeperWire with PositionWire:/private[serialization] trait WalkerOperationCodec extends CampaignResultCodec\n    with FollowUpWire with OathkeeperWire with PositionWire with WalkerValueCodec:/s' src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala
grep -c '^  protected final def' $N
grep -n 'with WalkerValueCodec' src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala
```

Expected: 26 `protected final def`; the mixin line matches.

- [ ] **Step 5: Rename the colliding `decodeOrientation`**

This is the one name in the moved block that also exists in
`GameEventJsonSupport` (`:102`, `protected final`, taking `ujson.Value`; the
moved one takes `String`). They are an overload, not an override, and resolve
today only because callers pass `.str`. Widening to `protected` puts them in
one overload set -- the configuration recorded at the hazard comment, where a
same-named private method was dispatched in place of
`GameEventJsonSupport`'s and silently broke `Flip` and `Play` encoding. The
encode half was already renamed to `orientationKey`; follow that convention.

```bash
cd /Users/roman/projects/oathdigital
perl -pi -e 's/\bdecodeOrientation\b/orientationFromKey/g' src/main/scala/oathdigital/serialization/WalkerValueCodec.scala
perl -pi -e 's/\bdecodeOrientation\b/orientationFromKey/g' src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala
grep -n 'orientationFromKey\|decodeOrientation' src/main/scala/oathdigital/serialization/WalkerValueCodec.scala src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala
```

Expected: four `orientationFromKey` -- one definition in `WalkerValueCodec`,
three calls in `WalkerOperationCodec` (at the former lines 313, 367 and 414).
No `decodeOrientation` remains in either file.
`GameEventJsonSupport.decodeOrientation` is untouched.

- [ ] **Step 6: Move the hazard comment to the renamed pair**

The comment now sits beside `orientationKey` in the new file. Extend it so it
covers the decode half too, which is the half that was missed last time. In
`WalkerValueCodec.scala`, replace:

```scala
  // Must not be named encodeOrientation: under Scala 3 a same-named private
  // method here was dispatched in place of GameEventJsonSupport's, which
  // broke encoding of Flip and Play operations.
  protected final def orientationKey(value: Orientation): String = value match
```

with:

```scala
  // Neither of these may be named encode/decodeOrientation: under Scala 3 a
  // same-named private method here was dispatched in place of
  // GameEventJsonSupport's, which broke encoding of Flip and Play
  // operations. These take a String key where GameEventJsonSupport's pair
  // takes a ujson.Value, so the two were an overload that resolved only by
  // argument type; the split widened these to protected and merged the
  // overload sets, so they carry distinct names instead.
  protected final def orientationKey(value: Orientation): String = value match
```

- [ ] **Step 7: Compile**

```bash
./sbtw -batch 'root/Test/compile'
```

Expected: success, no warnings. A `-Wunused:privates` failure here means a
moved helper has no caller and should be deleted rather than widened; an
ambiguous-reference error means another name collides and needs the Step 5
treatment.

- [ ] **Step 8: Prove the move is pure**

```bash
./sbtw -batch 'testOnly oathdigital.serialization.WalkerOperationWireSuite'
python3 scripts/check-architecture.py
wc -l src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala src/main/scala/oathdigital/serialization/WalkerValueCodec.scala
```

Expected: PASS with the same test count as Step 2; architecture check passes;
codec at 555, new file at 258. Both under 800.

- [ ] **Step 9: Run the full suite**

```bash
./sbtw -batch 'root/Test/test' 2>&1 | tail -20
```

Expected: all green. Any serialization failure means the move was not pure --
compare the failing spelling against `git show HEAD:<path>` rather than
adjusting the test.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/serialization/WalkerValueCodec.scala src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala
git commit -F - <<'MSG'
refactor(serialization): split walker value codecs into their own trait

A pure move: the value spellings leave WalkerOperationCodec for a sibling
trait, taking it from 795 lines to 555 and off the 800-line bound. The
moved helpers widen from private to protected, which merges
decodeOrientation into GameEventJsonSupport's overload set, so the String
half is renamed orientationFromKey per the orientationKey convention.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

### Task 2: Separate the generic walker vocabulary from Oath's operations

One atomic commit. `-Werror` promotes each consequence of the type change to
a build break, so the hierarchy move, the codec's dead arms, the screener's
dead guard and the suite that pins the dead throw cannot be committed
separately.

**Files:**
- Modify: `src/main/scala/oathdigital/model/Operation.scala`
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala:15-20,667-784`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/OperationRewrite.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala:1-91`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationResolution.scala:55-70`
- Modify: `src/test/scala/oathdigital/gameplay/HiddenScopeProbeSuite.scala:73` (one annotation)
- Test: `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala` (delete one test)

**Interfaces:**
- Produces: `trait LeafOperation extends Operation` with `final val children: Vector[Operation] = Vector(this)`; `Operation.required: Boolean = false`; `Decide` and `BuildOps` as `LeafOperation`; `Repeat`, `Branch`, `Sequence`, `CardPlayedFaceup`, `CardPlayedFacedown` as `Operation`; `sealed trait PrimitiveOperation extends CoreOperation, LeafOperation`.
- Consumes: `WalkerSteps.record(delta: CoreOperation, ...)` and `WalkerSteps.leafLabel(node: Operation)`, both already at these types.

- [ ] **Step 1: Confirm the starting state**

```bash
cd /Users/roman/projects/oathdigital
wc -l src/main/scala/oathdigital/model/CoreOperations.scala src/main/scala/oathdigital/model/Operation.scala
grep -n 'Parks a walker until\|^object Sequence:\|Semantic window for a card played faceup' src/main/scala/oathdigital/model/CoreOperations.scala
./sbtw -batch 'root/Test/compile'
```

Expected: `CoreOperations.scala` 784, `Operation.scala` 48; the `Decide` doc
at 667, the card-played doc at 762, `object Sequence` at 779; compile
succeeds. These three line numbers define the move; re-derive them from the
anchors if they differ.

- [ ] **Step 2: Add `LeafOperation` and lift `required`**

In `src/main/scala/oathdigital/model/Operation.scala`, replace the whole
root-trait declaration and its doc. Replace:

```scala
/** Root of the unified operation tree.
  *
  * An operation exposes its immediate children; a tree's leaves are
  * [[PrimitiveOperation]]s, which expose themselves as their only child so the
  * whole action tree shares one accessor. `window` is the hook point for powers
  * to wire their effects on the tree; `None` means engine-internal.
  *
  * NOTE: this root trait is deliberately not `sealed`. Scala 2.13 requires
  * every subclass of a sealed type to live in the same source file, and the
  * concrete [[CoreOperation]]/[[PrimitiveOperation]] cases stay in
  * `CoreOperations.scala` with their sealed parents.
  */
trait Operation:
  /** Hook point for power windows; None = engine-internal. */
  def window: Option[PowerWindow] = None
  /** Immediate children of this node. Primitives expose `Vector(this)`. */
  def children: Vector[Operation]
```

with:

```scala
/** Root of the reusable operation algebra: the vocabulary the walker itself
  * speaks, apart from the operations of Oath.
  *
  * An operation exposes its immediate children; a tree's leaves are
  * [[LeafOperation]]s, which expose themselves as their only child so the
  * whole action tree shares one accessor. `window` is the hook point for
  * powers to wire their effects on the tree; `None` means engine-internal.
  *
  * This root trait is deliberately not `sealed`, and that is now a feature
  * rather than a workaround. Scala confines a sealed type's direct
  * subclasses to one source file, so an unsealed root is what lets generic
  * vocabulary be authored outside Oath's own family: the control operations
  * below, and a test's own windowed node (`WalkerStubs.WindowedNode`).
  * Oath's operations live under the sealed [[CoreOperation]] in
  * `CoreOperations.scala`, where exhaustive matching over the game's
  * vocabulary is what the serializer and the restriction screener rely on.
  */
trait Operation:
  /** Hook point for power windows; None = engine-internal. */
  def window: Option[PowerWindow] = None
  /** Batch posture: a required operation rejects its batch rather than being
    * skipped when a restriction refuses it. Generic, because generic walker
    * code reads it off an `Operation`-typed node, exactly as it reads
    * `window`. */
  def required: Boolean = false
  /** Immediate children of this node. Leaves expose `Vector(this)`. */
  def children: Vector[Operation]

/** A leaf of the operation tree: it exposes itself as its only child, so the
  * whole tree shares one `children` accessor and [[Operation.flatten]] needs
  * one leaf test. Unsealed, so a leaf may be authored outside
  * `CoreOperations.scala`; [[PrimitiveOperation]] is Oath's sealed half of
  * this.
  */
trait LeafOperation extends Operation:
  final val children: Vector[Operation] = Vector(this)
```

- [ ] **Step 3: Retarget `Operation.flatten`**

Still in `Operation.scala`, in the `object Operation` body, replace:

```scala
    * A [[PrimitiveOperation]] is a leaf and flattens to itself; composites
    * flatten to the leaves of each child, in child order.
    */
  def flatten(operation: Operation): Vector[Operation] =
    operation match
      case _: PrimitiveOperation => Vector(operation)
      case _ => operation.children.flatMap(flatten)
```

with:

```scala
    * A [[LeafOperation]] is a leaf and flattens to itself; composites
    * flatten to the leaves of each child, in child order.
    */
  def flatten(operation: Operation): Vector[Operation] =
    operation match
      case _: LeafOperation => Vector(operation)
      case _ => operation.children.flatMap(flatten)
```

- [ ] **Step 4: Move the five control operations into `Operation.scala`**

Lines 667-761 hold `Decide`, `BuildOps`, `Repeat`, `Branch` and `Sequence`
with their docs; 779-784 hold `Sequence`'s companion. Lines 762-778 are the
two `CardPlayed` hooks, which stay. Append the two blocks to `Operation.scala`
and delete them from `CoreOperations.scala`, highest range first so the
earlier line numbers stay valid.

```bash
cd /Users/roman/projects/oathdigital
M=src/main/scala/oathdigital/model
{ echo; sed -n '667,761p' $M/CoreOperations.scala; echo; sed -n '779,784p' $M/CoreOperations.scala; } >> $M/Operation.scala
sed -i '' '779,784d' $M/CoreOperations.scala
sed -i '' '667,761d' $M/CoreOperations.scala
wc -l $M/Operation.scala $M/CoreOperations.scala
```

Expected: `Operation.scala` roughly 160, `CoreOperations.scala` 681.

- [ ] **Step 5: Reparent the moved operations**

In `Operation.scala`, change five `extends` clauses. `Decide` and `BuildOps`
are leaves; the other three are composites.

```bash
cd /Users/roman/projects/oathdigital
M=src/main/scala/oathdigital/model
perl -0pi -e 's/^    extends PrimitiveOperation:$/    extends LeafOperation:/m' $M/Operation.scala
perl -0pi -e 's/^    extends PrimitiveOperation$/    extends LeafOperation/m' $M/Operation.scala
perl -0pi -e 's/^    body: Operation\) extends CoreOperation:$/    body: Operation) extends Operation:/m' $M/Operation.scala
perl -0pi -e 's/^    extends CoreOperation:$/    extends Operation:/m' $M/Operation.scala
perl -0pi -e 's/^    override val window: Option\[PowerWindow\] = None\) extends CoreOperation$/    override val window: Option[PowerWindow] = None) extends Operation/m' $M/Operation.scala
grep -n 'extends LeafOperation\|extends Operation' $M/Operation.scala
```

Expected five hits among the moved classes: `Decide` and `BuildOps` on
`LeafOperation`; `Repeat`, `Branch` and `Sequence` on `Operation`. No
`PrimitiveOperation` or `CoreOperation` remains in `Operation.scala`.

- [ ] **Step 6: Reparent the family roots and the card hooks**

In `src/main/scala/oathdigital/model/CoreOperations.scala`, replace:

```scala
sealed trait CoreOperation extends Operation:
  def simultaneous: Boolean = false
  def required: Boolean = false

sealed trait PrimitiveOperation extends CoreOperation:
  final override val children: Vector[Operation] = Vector(this)
```

with:

```scala
sealed trait CoreOperation extends Operation:
  def simultaneous: Boolean = false

sealed trait PrimitiveOperation extends CoreOperation, LeafOperation
```

Then change both card hooks from `extends CoreOperation:` to
`extends Operation:` -- `CardPlayedFaceup` and `CardPlayedFacedown`. They are
game-specific in vocabulary, so they stay in this file, but they are
window-only composites and never deltas, so leaving the family is what makes
`encodeOperation` total.

Also update this file's header doc. Replace:

```scala
  * Re-rooted under [[Operation]]: composites expose their constituent
  * operations through `children` (the vectors they previously exposed as
  * `primitives`, now widened to `Vector[Operation]`); read the leaves with
  * [[Operation.flatten]]. `PrimitiveOperation` stays a subtype so the
  * existing operation-batch vocabulary (`Vector[CoreOperation]`, policies,
  * executor) keeps accepting both leaves and composites.
```

with:

```scala
  * Oath's own vocabulary, sealed so that the serializer and the restriction
  * screener can match it exhaustively. The walker's generic control
  * vocabulary is not here: it lives under the unsealed [[Operation]] in
  * `Operation.scala`, and a control node is therefore not a
  * `Vector[CoreOperation]` member and never a recorded delta.
  *
  * Composites expose their constituent operations through `children`; read
  * the leaves with [[Operation.flatten]]. `PrimitiveOperation` is Oath's
  * sealed half of [[LeafOperation]].
```

- [ ] **Step 7: Retarget leaf dispatch in the four consumers**

Each of these asks whether a node is a leaf, not whether it is a game
operation, so the rename corrects the predicate.

```bash
cd /Users/roman/projects/oathdigital
for f in src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala \
         src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala \
         src/main/scala/oathdigital/gameplay/powerresolver/OperationRewrite.scala \
         src/main/scala/oathdigital/application/gamelog/DetailLines.scala; do
  perl -pi -e 's/\bPrimitiveOperation\b/LeafOperation/g' $f
done
grep -rn 'PrimitiveOperation' src/main/scala | grep -v 'model/CoreOperations.scala'
```

Expected: the only remaining production mentions are the two prose comments
in `gameplay/actions/forge/ForgeProcedure.scala:28` and
`gameplay/walker/WalkerEvents.scala:71`. Update both to say `LeafOperation`,
since the trait they name no longer carries the leaf role.

- [ ] **Step 8: Compile to see the first real failure**

```bash
./sbtw -batch 'root/Compile/compile' 2>&1 | grep -A 6 'error'
```

Expected: one `E007 Type Mismatch` at
`ProcedureWalker.scala`, on `WalkerSteps.record(delta, ...)` --
`Found: LeafOperation, Required: CoreOperation`. This is the one real design
point in the task and Step 9 handles it.

- [ ] **Step 9: Narrow `runNarrowed`'s fallthrough**

In `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`,
replace:

```scala
            case build: BuildOps => WalkerSteps.runBuildOps(build, ctx, path, contributions)
            case delta =>
              WalkerSteps.record(delta, ctx, path, contributions, strict).map(Done(_))
```

with:

```scala
            case build: BuildOps => WalkerSteps.runBuildOps(build, ctx, path, contributions)
            case delta: CoreOperation =>
              WalkerSteps.record(delta, ctx, path, contributions, strict).map(Done(_))
            // `LeafOperation` is unsealed, so no match over it is
            // exhaustive: the compiler cannot be shown that a leaf which is
            // neither control nor core is impossible. In the walker it is --
            // every leaf an action tree can hold is one or the other -- so
            // this is the walker's own contract, asserted where the
            // walker's contract lives rather than in the serializer.
            case other => contractViolation(
              s"leaf ${WalkerSteps.leafLabel(other)} is neither a control " +
                "leaf nor a recordable operation")
```

`WalkerSteps.record` already takes `CoreOperation` and
`WalkerSteps.leafLabel` already takes `Operation`, so no signature moves.

- [ ] **Step 10: Compile to surface the dead code**

```bash
./sbtw -batch 'root/Compile/compile' 2>&1 | grep -E '^\[(warn|error)\] -- ' | sed 's#.*/src/#src/#'
```

Expected twelve `E030 Match case Unreachable` reports, promoted to errors by
`-Werror`: seven in `WalkerOperationCodec.scala` at lines 70, 74, 78, 81, 84
and 87 twice -- six case arms, but the last arm matches two patterns -- and
five at the columns of the single case spanning
`OperationResolution.scala:63-64`. Every one is defensive code the type
change has made provably dead. If anything other than `E030` appears, stop
and read it -- this task should break nothing logical.

- [ ] **Step 11: Delete the codec's throwing arms and class doc**

In `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`,
delete lines 58-91 -- the comment block beginning
`// The five arms below are the walker's own tree-control vocabulary` through
the `CardPlayed` throw -- so that `encodeOperation` ends at its four group
arms:

```scala
  protected final def encodeOperation(operation: CoreOperation): ujson.Value =
    operation match
      case operation: CardOperation => encodeCardOperation(operation)
      case operation: PieceOperation => encodePieceOperation(operation)
      case operation: ResourceOperation => encodeResourceOperation(operation)
      case operation: TurnOperation => encodeTurnOperation(operation)
```

Then replace the trait's class doc, which exists to explain those arms.
Replace the paragraph beginning `` `encodeOperation`/`encodePiece` (I8) are
total `` and running to the end of that doc comment with:

```scala
  * `encodeOperation`/`encodePiece` (I8) are total over `CoreOperation`/
  * `Piece`, exactly like [[encodeLocation]] in [[WalkerValueCodec]] (added
  * for the same reason: a bounded operation set is what let Catacombs'
  * recorded `Move` out of `Location.Deck` reach an append-time throw with no
  * test to catch it -- that lesson generalises to every recorded shape, not
  * just `Location`). Totality is now exceptionless: the walker's control
  * vocabulary lives under the unsealed `Operation`, not under
  * `CoreOperation`, so a control node is not a member of the encoded family
  * and cannot reach this method. A NEW `CoreOperation` case still fails to
  * compile here ("match may not be exhaustive") until it is given a real
  * arm.
```

- [ ] **Step 12: Delete the restriction screener's container guard**

In `src/main/scala/oathdigital/gameplay/operations/OperationResolution.scala`,
delete the arm:

```scala
    case _: Sequence | _: Repeat | _: Branch | _: CardPlayedFaceup |
        _: CardPlayedFacedown => Right(true)
```

and the doc paragraph above `screen` that explains it:

```scala
    * A container (`Sequence`, `Repeat`, `Branch` and the card-played windows)
    * only holds other operations, which are screened when the walker reaches
    * them, so it always runs: refusing it would skip its unrelated children.
```

`screen` takes `CoreOperation`, which can no longer be a container, so the
guard is unreachable. Remove any import of `Sequence`, `Repeat`, `Branch`,
`CardPlayedFaceup` or `CardPlayedFacedown` that this leaves unused --
`-Wunused:imports` will fail the build otherwise.

- [ ] **Step 13: Compile clean**

```bash
./sbtw -batch 'root/Compile/compile'
python3 scripts/check-architecture.py
wc -l src/main/scala/oathdigital/model/CoreOperations.scala src/main/scala/oathdigital/model/Operation.scala src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala
```

Expected: success with no warnings; architecture check passes;
`CoreOperations.scala` 681, `Operation.scala` roughly 160,
`WalkerOperationCodec.scala` roughly 522.

- [ ] **Step 14: Compile the tests and delete the suite that pins the throw**

```bash
./sbtw -batch 'root/Test/compile' 2>&1 | grep -E '^\[(warn|error)\] -- ' | sed 's#.*/src/#src/#'
```

Expected six errors: five at
`src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala:453-458`
and one at `src/test/scala/oathdigital/gameplay/HiddenScopeProbeSuite.scala:73`.

Delete the whole test at `WalkerOperationWireSuite.scala:441-465`, from
`test("a walker tree-control node can never be recorded, and surfaces a " +`
through the `}` that closes its `nodes.foreach`. Its assertion -- that a
control node reaching the encoder yields a typed `WireError` -- has become
unwritable, because a control node is no longer a `CoreOperation` and cannot
be put in the `Vector[CoreOperation]` the test builds. That is the proof the
throw was never a reachable path, so there is no replacement test. Remove any
import the deletion leaves unused.

In `HiddenScopeProbeSuite.scala:73`, widen the one annotation:

```scala
    assertEquals(offered(built(Vector[Operation](Sequence(Vector(failingPeek))))),
```

Change nothing else in that file; another session owns it.

- [ ] **Step 15: Run the full suite**

```bash
./sbtw -batch 'root/Test/test' 2>&1 | tail -20
```

Expected: all green. A walker failure means Step 9's arm order changed
something -- the three arms must stay in the order `BuildOps`, then
`CoreOperation`, then the catch-all.

- [ ] **Step 16: Commit**

```bash
git add -A src/
git commit -F - <<'MSG'
refactor(model): separate the walker's control vocabulary from Oath's

CoreOperation and PrimitiveOperation each carried one generic job and one
game-specific one. The generic halves move up: Operation gains `required`,
and a new unsealed LeafOperation carries the leaf marker. The five control
operations move to Operation.scala and the two CardPlayed hooks leave the
family, so CoreOperations.scala drops to 681 lines and encodeOperation is
total with no throwing arms.

Every deletion here is code the type change made provably dead, which
-Werror reports as an unreachable case: the codec's six arms and the doc
explaining them, OperationResolution's container guard, and the suite that
pinned the codec's throw. That last assertion is now unwritable, which is
the proof the throw was never a reachable path.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

### Task 3: Retire `UnencodableOperation`

Separable from Task 2 because nothing fails to compile either way:
`-Wunused` does not flag an unused case class. It is removed because its
justification was that a control node might reach the encoder, and Task 2
makes that unrepresentable.

**Files:**
- Modify: `src/main/scala/oathdigital/serialization/WireError.scala:30-43`
- Modify: `src/main/scala/oathdigital/serialization/GameEventWire.scala:78-101`

**Interfaces:**
- Consumes: nothing. Produces: nothing. This task only removes.

- [ ] **Step 1: Confirm nothing constructs it**

```bash
cd /Users/roman/projects/oathdigital
grep -rn 'UnencodableOperation' src/main/scala src/test/scala
```

Expected: exactly three sites, all of them the definition or the handler --
`WireError.scala:42`, `GameEventWire.scala:88` (prose) and
`GameEventWire.scala:99` (the catch arm). No `throw UnencodableOperation`
anywhere. If one remains, Task 2 is incomplete; stop.

- [ ] **Step 2: Delete the catch arm**

In `src/main/scala/oathdigital/serialization/GameEventWire.scala`, remove:

```scala
      case UnencodableOperation(error) => Left(error)
```

leaving `encodePayloadSafe`'s `catch` with only its `NonFatal` arm.

- [ ] **Step 3: Rewrite the doc above it**

Replace the paragraph that explains the special case -- from
`` * are now total over `CoreOperation`/`Piece` too, matching `` through
`` * "$.payload" / stringified-message fallback below. `` -- with:

```scala
    * are now total over `CoreOperation`/`Piece` too, matching
    * `encodeLocation`), so an unencodable value reaching this append path
    * must surface as a typed [[WireError]] rather than escape as a raw
    * exception (matches the decode side's `NonFatal` boundary in
    * `decodePayload`). There is no longer a special case ahead of the
    * generic `NonFatal` fallback: the walker's control vocabulary is not a
    * `CoreOperation`, so the shapes that once needed one cannot reach the
    * encoder at all.
```

- [ ] **Step 4: Delete the case class and its doc**

In `src/main/scala/oathdigital/serialization/WireError.scala`, remove:

```scala
private[serialization] final case class UnencodableOperation(error: WireError)
    extends RuntimeException(error.message)
```

together with the doc comment above it that ends
`` * exception message. ``.

- [ ] **Step 5: Compile and test**

```bash
./sbtw -batch 'root/Test/compile'
./sbtw -batch 'testOnly oathdigital.serialization.*'
python3 scripts/check-architecture.py
```

Expected: compile succeeds with no unused-import warning; serialization
suites pass; architecture check passes.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/serialization/WireError.scala src/main/scala/oathdigital/serialization/GameEventWire.scala
git commit -F - <<'MSG'
refactor(serialization): retire UnencodableOperation

Its justification was that a walker control node might reach the encoder.
A control node is no longer a CoreOperation, so nothing constructs it and
encodePayloadSafe needs no special case ahead of its NonFatal fallback.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

### Task 4: Rewrite the two stale sealed-hierarchy comments

Both cite a Scala 2.13 constraint on a 3.9.0 build. The rule they describe is
unchanged in Scala 3 -- only the version is wrong -- but after Task 2 their
conclusion changes too: unsealed stops being a consequence of where the cases
are stuck and becomes the extension point of the generic layer.
`Operation.scala`'s copy is already rewritten in Task 2 Step 2; this task
handles the test-side copy and verifies no others remain.

**Files:**
- Modify: `src/test/scala/oathdigital/testkit/WalkerStubs.scala:14-19`

**Interfaces:** none. Comments only.

- [ ] **Step 1: Find every remaining citation**

```bash
cd /Users/roman/projects/oathdigital
grep -rn '2\.13' src/main/scala src/test/scala
```

Expected: one hit, `src/test/scala/oathdigital/testkit/WalkerStubs.scala:16`.
Rewrite any other hit this finds the same way.

- [ ] **Step 2: Rewrite the stub's doc**

In `src/test/scala/oathdigital/testkit/WalkerStubs.scala`, replace:

```scala
  /** Stub of Operation: a test-only composite that hooks a `PowerWindow` on
    * an arbitrary children vector. `Operation` is deliberately unsealed
    * (unlike `CoreOperation`/`PrimitiveOperation`, which Scala 2.13 pins to
    * `CoreOperations.scala`) precisely so a windowed node can be authored
    * outside that file, so a suite can prove the walker's generic wiring
    * with a tree it builds itself.
    */
```

with:

```scala
  /** Stub of Operation: a test-only composite that hooks a `PowerWindow` on
    * an arbitrary children vector. `Operation` is the unsealed generic
    * layer, so a windowed node can be authored here and a suite can prove
    * the walker's generic wiring with a tree it builds itself. Oath's own
    * operations are sealed under `CoreOperation`, which Scala pins to
    * `CoreOperations.scala`; this node is deliberately not one of them.
    */
```

- [ ] **Step 3: Compile and test**

```bash
./sbtw -batch 'root/Test/compile'
./sbtw -batch 'root/Test/test' 2>&1 | tail -10
```

Expected: compile succeeds, all suites green.

- [ ] **Step 4: Commit**

```bash
git add src/test/scala/oathdigital/testkit/WalkerStubs.scala
git commit -F - <<'MSG'
docs: correct the stale sealed-hierarchy note in WalkerStubs

It cited a Scala 2.13 constraint on a 3.9.0 build, and its conclusion is
now different: Operation is unsealed because it is the generic layer, not
because the cases are stuck elsewhere.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

## Verification

After Task 4:

```bash
cd /Users/roman/projects/oathdigital
python3 scripts/check-architecture.py
./sbtw -batch 'root/Test/test' 2>&1 | tail -20
find src/main/scala shared/src/main/scala frontend/src/main/scala -name '*.scala' | xargs wc -l | sort -rn | head -5
```

Expected: architecture check passes, all suites green, and no production file
above roughly 700 lines. `CoreOperations.scala` at 681 with 119 lines of
headroom and `WalkerOperationCodec.scala` near 522 with 278 -- at the
measured +9 lines per operation, room for the next several catalog batches
rather than none.
