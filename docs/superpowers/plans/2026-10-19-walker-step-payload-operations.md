# Walker Step Payload Operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A recorded walker step's operations move onto the one payload that
can have them, so a roll or choice step carrying operations stops being a
shape `WalkerReplay` defends against and becomes one the type cannot express.

**Architecture:**

- **Today.** `WalkerStepRecorded(nodeId, payload, ops, contributions)` carries
  `ops` beside a `WalkerStepPayload`, and only `DeltaRecorded` may have them.
  "A roll step carries no operations" is a doc comment enforced by two runtime
  checks in `WalkerReplay`.
- **The type.** `WalkerStepPayload.DeltaRecorded` becomes top-level
  `final case class DeltaPayload(ops: Vector[CoreOperation])`, matching
  `ChoicePayload` and `RollPayload` in naming and placement. The family is
  sealed, which requires all three to stay in `WalkerEvents.scala`. `ops`
  leaves the event.
- **The wire.** Operations move under the payload object:
  `{"kind": "delta", "ops": [...]}`. The event's top-level `"ops"` key goes.
  `GameEventWire.FormatVersion` stays at `1`, so an old journal breaks as a
  field error at its first walker step rather than at the envelope.
- **Three commits, each green.** The rename lands first on its own (pure
  mechanics, no behaviour), then sealing (which deletes the encoder's throw
  and relocates its only test), then the operations move (the type, the wire,
  the guards and every call site in one atomic change, because Scala gives no
  smaller compiling unit).

**Tech Stack:** Scala 3.9.0 on the JVM, ujson, munit, built through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-10-19-walker-step-payload-operations-design.md`
(committed as `69402731`). Read it before starting, with
`docs/superpowers/specs/2026-09-24-replay-posture-decision.md` for why replay
applies recorded operations and nothing else.

**Starts from:** `main`. Check that
`src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala` is 115 lines
and still defines `object WalkerStepPayload` with `case object DeltaRecorded`
inside it.

## Corrections to the spec's measurements

The spec's **Measurements** table counted `src/main` only. Verified at
`69402731`, the real surface is:

| Thing | Spec said | Measured |
| --- | --- | --- |
| `WalkerStepRecorded(` sites | 52 across 28 files | 52 across 28 files (17 in `src/main`) — correct |
| Readers of `ops` in `src/main` | 5 | **6** — the spec's list omits `application/UndoBarrier.scala:23` |
| Readers of `ops` in `src/test` | not counted | **68 reads across 34 files** |
| Wire-path assertions reading `payload.ops` | not counted | **30 across 3 suites** (`WalkerOperationWireSuite` 24, `CampaignResultCodecSuite` 5, `PayCostWireSuite` 1) |
| Stale comments | 3 | **7** — three more in `ProcedureWalker.scala` and one in `model/Operation.scala` |
| Tests covering the guard that survives | "the walker replay suites already cover" it | **none** — no test in the repository asserts any of `WalkerReplay`'s three guard messages |

None of this changes the design. It changes the size: roughly 50 files, not
the handful the table implied. Task 3 absorbs the whole sweep.

The last row does change the work. The spec's **Testing** section keeps
`WalkerReplay:94` on the argument that the wire is untrusted input, and says
the replay suites already cover it. They do not: `grep` for
`"recorded delta step must contain operations"` finds the string only in
`src/main`, and the same holds for both guards being deleted. A surviving
guard whose two siblings are being removed must not go untested, so Task 3
adds the test — the one piece of new coverage in this plan.

Four of the 68 test reads are inside aggregating helpers
(`PhasePowerHarness.ops`, `CampaignProcedureSuite.ops`,
`CampaignPlanWindowSuite.ops`, `PlanDriver.Run.ops`) that together serve about
95 call sites. Those 95 sites do not change — the four helpers do, and Task 3
collapses all four onto one shared testkit function.

## Global Constraints

- **Build rules.** `-Werror` with
  `-Wunused:imports,privates,locals,implicits,nowarn` (`build.sbt:69-76`): an
  unused import, an unused private member or an unreachable match case fails
  the build. Every import this plan adds must be used by the file carrying it,
  and every import a deletion leaves behind must go. This is load-bearing in
  Task 2: sealing the family is what makes `encodeStepPayload`'s
  `case other => throw` unreachable, and `-Werror` is what forces it out.
- **File size.** Production files cap at 800 lines. The files this plan
  touches are far below it (`WalkerEvents.scala` 115, `WalkerEventCodec.scala`
  305, `WalkerReplay.scala` 196) and all three shrink.
- **Vocabulary.** `CONTEXT.md` defines "note", "void marker", "taken-back
  group" and "command batch". A step's operations are a *batch*; the thing
  that carries them is a *payload*.
- **Coverage ratchet.** `coverageMinimumStmtTotal := 92.0`,
  `coverageMinimumBranchTotal := 84.4`, `coverageFailOnMinimum := true`
  (`build.sbt:88-90`). Task 4 runs `coverageReport` and the plan does not
  lower either number.
- **Full gate.** `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` exit 0.
  Nothing in this plan touches `frontend/` or `shared/`, but the gate runs
  whole.
- **No old-journal support.** The standing position
  (`docs/superpowers/specs/` and the repo's practice) is that a wire change
  need not keep old journals decodable. Do not add a compatibility fallback.

## File structure

**`src/main` — 19 files.**

| File | What changes |
| --- | --- |
| `gameplay/walker/WalkerEvents.scala` | The type: `DeltaPayload`, `sealed`, `ops` leaves the event, five doc comments |
| `gameplay/walker/WalkerReplay.scala` | Two guards deleted, three arms re-shaped |
| `gameplay/walker/WalkerSteps.scala` | Three construction sites |
| `gameplay/walker/AdviserArrivalPass.scala` | One construction site |
| `gameplay/walker/FollowUpOffer.scala` | One pattern loses an `_` |
| `gameplay/walker/WalkerSimulation.scala` | One reader |
| `gameplay/walker/ProcedureWalker.scala` | Three stale doc comments |
| `gameplay/walker/WalkerPowerGather.scala` | One doc comment mentions the type; no change needed |
| `gameplay/OathRulesWalker.scala` | Two readers, one import |
| `gameplay/OathRules.scala` | Dispatch only; no change needed |
| `serialization/WalkerEventCodec.scala` | The wire: encoder arm, both payload codecs, `decodeStepRecorded`, the deleted throw |
| `serialization/GameEventWire.scala` | No change; its `encodePayloadSafe` doc is the claim Task 2's new test protects |
| `application/UndoBarrier.scala` | One reader, one import |
| `application/gamelog/LogJournal.scala` | `opSteps` and `answers` |
| `application/gamelog/NoteLines.scala` | Two patterns, one import |
| `application/gamelog/DetailLines.scala` | Two patterns |
| `application/gamelog/StartLines.scala` | One pattern |
| `application/gamelog/GameLogFormatter.scala` | Type-only match; no change needed |
| `model/Operation.scala` | One stale doc comment |

**`src/test` — one new file, about 48 modified.**

| File | What changes |
| --- | --- |
| `testkit/RecordedOps.scala` (new) | The one place a test reads a step's batch |
| `testkit/PhasePowerHarness.scala`, `gameplay/CampaignProcedureSuite.scala`, `gameplay/CampaignPlanWindowSuite.scala`, `gameplay/powers/campaign/PlanDriver.scala` | Four duplicated aggregators delegate to it |
| `serialization/WalkerOperationWireSuite.scala` | 24 wire paths, 7 constructions, the deleted open-family test |
| `serialization/GameEventWireEnvelopeSuite.scala` | Gains the relocated encode-failure test |
| `serialization/CampaignResultCodecSuite.scala`, `serialization/PayCostWireSuite.scala` | 6 wire paths |
| `serialization/WalkerDecisionWireSuite.scala`, `serialization/WalkerWireEvents.scala` | 9 constructions |
| ~33 suites under `gameplay/`, `application/`, `testkit/` | Constructions, patterns and `.ops` reads |

---

### Task 1: Rename `WalkerStepPayload.DeltaRecorded` to the top-level `DeltaPayload`

Pure mechanics with no behaviour change, landed on its own so Task 3's diff
is the operations move and nothing else. After this task `DeltaPayload` is
still a `case object` carrying nothing; Task 3 turns it into a case class.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala:11-18`
- Modify (mechanical, 23 references): `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala`, `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala`, `src/main/scala/oathdigital/gameplay/walker/WalkerSteps.scala`, `src/main/scala/oathdigital/gameplay/walker/AdviserArrivalPass.scala`, `src/main/scala/oathdigital/application/gamelog/NoteLines.scala`, and 12 test files
- Test: no new tests. The existing suites are the regression net; a rename that compiles and leaves every suite green is the whole verification.

**Interfaces:**
- Produces: `oathdigital.gameplay.walker.DeltaPayload`, a top-level
  `case object` extending `WalkerStepPayload`. `object WalkerStepPayload` no
  longer exists.

- [ ] **Step 1: Move the case object out of the companion**

In `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala`, replace
lines 13-18 (the whole `object WalkerStepPayload:` block) with a top-level
declaration. The trait at line 11 and its comment are untouched in this task
— Task 2 rewrites them.

```scala
/** Marks a step that executed a batch of operations, as opposed to a
  * resolved choice or a submitted roll. It carries nothing: the step's
  * `ops` are both the replay authority and the only description of what
  * happened, and every reader works from them. */
case object DeltaPayload extends WalkerStepPayload
```

- [ ] **Step 2: Rewrite every reference**

Run this from the repository root. The three expressions are ordered so the
import form is rewritten first and the later two find nothing left on that
line.

```bash
grep -rl 'DeltaRecorded' src --include='*.scala' | xargs sed -i '' -e 's/import oathdigital\.gameplay\.walker\.WalkerStepPayload\.DeltaRecorded/import oathdigital.gameplay.walker.DeltaPayload/' -e 's/WalkerStepPayload\.DeltaRecorded/DeltaPayload/g' -e 's/\bDeltaRecorded\b/DeltaPayload/g'
```

- [ ] **Step 3: Compile and read the import errors**

Run: `./sbtw "Test/compile"`
Expected: FAIL. Two kinds of error, both from the rename:

1. *Not found: DeltaPayload* in files that reached it through an
   `import ...{WalkerStepPayload, ...}` and the `WalkerStepPayload.` prefix.
2. *Unused import* (`-Wunused:imports`, fatal under `-Werror`) in files whose
   only use of `WalkerStepPayload` was that prefix.

- [ ] **Step 4: Fix the seven import lists**

These are every file affected; the compiler names the same seven.

In `src/main/scala/oathdigital/application/gamelog/NoteLines.scala:5-6`,
`WalkerStepPayload` becomes `DeltaPayload`:

```scala
import oathdigital.gameplay.walker.{ChoicePayload, DeltaPayload, LogNoted,
  WalkerCompleted, WalkerParked, WalkerStepRecorded}
```

In `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala:5-7`,
`WalkerStepPayload` stays — `encodeStepPayload` takes it as a parameter type
— and `DeltaPayload` is added:

```scala
import oathdigital.gameplay.walker.{ActionBoundaryRan, ChoicePayload,
  DeltaPayload, FollowUpSkipped, LogNoted, ProcedureDequeued, RollPayload,
  WalkerCompleted, WalkerParked, WalkerStepPayload, WalkerStepRecorded}
```

In `src/test/scala/oathdigital/serialization/WalkerWireEvents.scala:4`:

```scala
import oathdigital.gameplay.walker.{DeltaPayload, WalkerStepRecorded}
```

In `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala:6-7`,
`WalkerStepPayload` stays — the open-family test at the end of the file still
extends it, until Task 2 deletes that test:

```scala
import oathdigital.gameplay.walker.{DeltaPayload, RollPayload,
  WalkerStepPayload, WalkerStepRecorded}
```

In `src/test/scala/oathdigital/testkit/OutcomesSuite.scala:4-5`,
`src/test/scala/oathdigital/gameplay/RecoverProcedureSuite.scala:7-9`,
`src/test/scala/oathdigital/gameplay/WalkerReplaySettlementSuite.scala:3-4`,
`src/test/scala/oathdigital/application/gamelog/GameLogRerollSuite.scala:5-6`
and
`src/test/scala/oathdigital/application/gamelog/GameLogRareLinesSuite.scala:6-7`,
replace `WalkerStepPayload` with `DeltaPayload` in the braced import list,
keeping the list alphabetical.

`src/test/scala/oathdigital/gameplay/ForgeProcedureSuite.scala:9` and
`src/test/scala/oathdigital/application/UndoBarrierSuite.scala:3` keep
`WalkerStepPayload` — both use it as a type, at `ForgeProcedureSuite:450`
(an ascription) and `UndoBarrierSuite:18` (a parameter).

- [ ] **Step 5: Compile clean**

Run: `./sbtw "Test/compile"`
Expected: success, no warnings.

- [ ] **Step 6: Check nothing survives under the old name**

```bash
grep -rn 'DeltaRecorded\|object WalkerStepPayload' src --include='*.scala'
```

Expected: no output.

- [ ] **Step 7: Run the suites the rename touches**

Run: `./sbtw "testOnly oathdigital.serialization.* oathdigital.gameplay.walker.* oathdigital.application.gamelog.*"`
Expected: PASS, no test count change.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala src/main/scala/oathdigital/serialization/WalkerEventCodec.scala src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala src/main/scala/oathdigital/gameplay/walker/WalkerSteps.scala src/main/scala/oathdigital/gameplay/walker/AdviserArrivalPass.scala src/main/scala/oathdigital/application/gamelog/NoteLines.scala src/test
git commit -m "$(cat <<'MSG'
refactor(walker): the delta payload is named like its siblings

DeltaRecorded sat alone inside a companion object while ChoicePayload and
RollPayload sat beside it at the top level, so the three members of one
family were reached three different ways. Name and place it like them.

No behaviour changes; the case object still carries nothing.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

---

### Task 2: Seal the family and move the encode-failure test one layer out

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala:5-11`
- Modify: `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala:86-101`
- Modify: `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala:6-7,248-262`
- Test: `src/test/scala/oathdigital/serialization/GameEventWireEnvelopeSuite.scala`

**Interfaces:**
- Consumes: `DeltaPayload` from Task 1.
- Produces: `sealed trait WalkerStepPayload`. A match on it must now cover
  `DeltaPayload`, `ChoicePayload` and `RollPayload`, and nothing else.

**Why the test moves rather than goes.** `WalkerOperationWireSuite:248-262` is
the only test in the repository asserting that the *encode* path returns a
typed `WireError` instead of throwing. Every wire-error test in
`GameEventWireEnvelopeSuite` exercises decoding
(`UnsupportedFormatVersion`, `UnknownEventType`, `MalformedJson`, `WrongType`,
`InvalidSequence`), none of which reaches `encodePayloadSafe`'s catch, and
that method's own doc comment (`GameEventWire.scala:85-96`) states the
guarantee. Sealing makes the old test uncompilable — its stub payload is
declared in another file — so the guarantee is re-pinned one layer out, where
it stays reachable: `OathEvent` and `WalkerEvent` are open traits and the
encoder dispatch is a composed `PartialFunction`
(`GameEventWire.scala:250-262`), so an unregistered *event* still falls
through to the catch. `OathRulesEvolveSuite.scala:13` already declares an
unregistered walker event for the same reason, so the shape has a precedent.

- [ ] **Step 1: Write the replacement test first**

Append to `src/test/scala/oathdigital/serialization/GameEventWireEnvelopeSuite.scala`:

```scala
  test("an event the encoder has no arm for returns a typed WireError, " +
      "not a thrown exception"):
    // `encodePayloadSafe`'s doc states this guarantee for the append path,
    // and every other wire-error test in this suite exercises decoding.
    // `OathEvent` and `WalkerEvent` are open and the encoder dispatch is a
    // composed PartialFunction, so an event type no vocabulary registers
    // reaches that catch -- the same opening `OathRulesEvolveSuite` uses.

    // Stub of WalkerEvent: an event type no encoder arm matches.
    case object UnregisteredWalkerEvent extends WalkerEvent
    GameEventWire.encodeEvent("walker", 0L, UnregisteredWalkerEvent) match
      case Left(_) => ()
      case Right(value) => fail(s"expected a WireError, got $value")
```

`WalkerEvent` arrives with the file's existing `import oathdigital.model._`
at line 7; add no import.

- [ ] **Step 2: Run it against the unsealed family**

Run: `./sbtw "testOnly oathdigital.serialization.GameEventWireEnvelopeSuite"`
Expected: PASS. It passes before the seal as well as after — it is a
relocation of an existing guarantee, not a new one, and it must hold in both
states or it is not the same guarantee.

- [ ] **Step 3: Delete the old test**

Remove `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala:248-262`
in full — the test, its four comment lines and the `UnknownStepPayload` stub.
Then drop `WalkerStepPayload` from the import at line 6; the file's remaining
uses are `DeltaPayload`, `RollPayload` and `WalkerStepRecorded`:

```scala
import oathdigital.gameplay.walker.{DeltaPayload, RollPayload,
  WalkerStepRecorded}
```

- [ ] **Step 4: Seal the trait**

Replace `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala:5-11`:

```scala
/** What one recorded walker step recorded. Sealed over the three categories
  * of durable walk fact -- the step applied operations, it recorded an
  * answer, or it recorded dice -- so a fourth is a compile error at every
  * match rather than a shape each reader silently drops. The three cases
  * live in this file together, which sealing requires.
  */
sealed trait WalkerStepPayload extends Product with Serializable
```

- [ ] **Step 5: Compile and watch the throw become unreachable**

Run: `./sbtw "Test/compile"`
Expected: FAIL at `WalkerEventCodec.scala:100` —
`Unreachable case` for `case other => throw new IllegalArgumentException`,
fatal under `-Werror`.

- [ ] **Step 6: Delete the throw**

Remove these two lines from `encodeStepPayload`
(`src/main/scala/oathdigital/serialization/WalkerEventCodec.scala:100-101`):

```scala
      case other => throw new IllegalArgumentException(
        s"unsupported walker step payload $other")
```

- [ ] **Step 7: Compile clean and run the wire suites**

Run: `./sbtw "Test/compile" "testOnly oathdigital.serialization.*"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala src/main/scala/oathdigital/serialization/WalkerEventCodec.scala src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala src/test/scala/oathdigital/serialization/GameEventWireEnvelopeSuite.scala
git commit -m "$(cat <<'MSG'
refactor(walker): the step payload family is sealed

The comment saying the family stays open named three payloads to come --
DecisionStarted, DecisionResolved, RollRecorded. None was ever built. The
two that did arrive, ChoicePayload and RollPayload, landed the same day the
comment did, under different names, and both live in this file. The
rationale was spent on arrival.

Sealing makes the encoder's unsupported-payload throw unreachable, and
-Werror removes it. That throw's only test declared its own payload from
another file, which sealing forbids; the guarantee it pinned -- that the
append path returns a typed WireError rather than throwing -- is re-pinned
in GameEventWireEnvelopeSuite against an unregistered event, which still
reaches encodePayloadSafe's catch because OathEvent stays open.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

---

### Task 3: Move the operations into `DeltaPayload`

The atomic change. Scala offers no smaller compiling unit: the moment `ops`
leaves `WalkerStepRecorded`, every construction site, every four-element
pattern and every reader stops compiling at once. Work the steps in order —
production first, then the testkit, then the mechanical sweep, then the hand
edits — and compile only where a step says to.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala:13-62`
- Modify: `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala:26-31,86-99,102-114,269-279`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala:71-96`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSteps.scala:69-73,108-112,128-130`
- Modify: `src/main/scala/oathdigital/gameplay/walker/AdviserArrivalPass.scala:45-49`
- Modify: `src/main/scala/oathdigital/gameplay/walker/FollowUpOffer.scala:57-58`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala:305-308`
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala:7-10,353-356,421-427`
- Modify: `src/main/scala/oathdigital/application/UndoBarrier.scala:3-4,21-23`
- Modify: `src/main/scala/oathdigital/application/gamelog/LogJournal.scala:4-5,93,119-128`
- Modify: `src/main/scala/oathdigital/application/gamelog/NoteLines.scala:193,205`
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala:30,48`
- Modify: `src/main/scala/oathdigital/application/gamelog/StartLines.scala:100`
- Create: `src/test/scala/oathdigital/testkit/RecordedOps.scala`
- Modify: about 48 test files (enumerated in Steps 10-14)
- Test: `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala`, `src/test/scala/oathdigital/gameplay/walker/WalkerReplayGuardSuite.scala`

**Interfaces:**
- Consumes: `sealed trait WalkerStepPayload` and `case object DeltaPayload`
  from Tasks 1 and 2.
- Produces:
  - `final case class DeltaPayload(ops: Vector[CoreOperation]) extends WalkerStepPayload`
  - `final case class WalkerStepRecorded(nodeId: String, payload: WalkerStepPayload, contributions: Vector[PowerId]) extends WalkerEvent` — three fields, not four
  - `oathdigital.testkit.RecordedOps.of(step: WalkerStepRecorded): Vector[CoreOperation]`
  - `oathdigital.testkit.RecordedOps.all(events: Vector[OathEvent]): Vector[CoreOperation]`
  - Wire: a step is `{"nodeId": …, "step": {"kind": "delta", "ops": […]}, "contributions": […]}`. There is no top-level `"ops"` key.

- [ ] **Step 1: Write the two failing tests**

The first pins the new wire shape and that the old one stops decoding. The
second pins the guard that survives, which nothing covers today.

Add to `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala`,
after the contributions round-trip test:

```scala
  test("a delta's operations are written under its payload, and a step with " +
      "a top-level ops key no longer decodes"):
    val event: OathEvent = WalkerStepRecorded("0",
      DeltaPayload(Vector(ModifyDicePool(PoolKey("recover"), 2))),
      Vector.empty)
    val encoded = encodedStreamThatRoundTrips("walker", Vector(event))
    val written = ujson.read(encoded).arr.head("payload")
    assertEquals(written("step")("ops")(0)("kind").str, "modify-dice-pool")
    assert(!written.obj.contains("ops"), written)

    // The shape written before this change: operations beside the payload
    // rather than inside it. It fails on the key the payload now requires,
    // which is a journal that does not load rather than one that silently
    // replays without its batch.
    val old = ujson.read(encoded)
    old.arr.head("payload")("ops") = old.arr.head("payload")("step")("ops")
    old.arr.head("payload")("step").obj.remove("ops")
    // The message is ujson's own wording for a missing key, so assert the
    // path and the error type rather than the text.
    GameEventWire.decodeStream(ujson.write(old)) match
      case Left(WireError.InvalidValue(path, _)) =>
        assertEquals(path, "$[0].payload")
      case other => fail(s"expected an InvalidValue at the step, got $other")
```

Add to `src/test/scala/oathdigital/gameplay/walker/WalkerReplayGuardSuite.scala`,
whose subject is already what replay refuses:

```scala
  test("a recorded delta step carrying no operations is rejected on replay"):
    // The one guard of the three that survives the payload move. The type
    // cannot make an empty batch unrepresentable -- the wire is untrusted
    // input -- so the check stays where decoded facts are applied. Nothing
    // covered it before this plan.
    assertEquals(
      ProcedureWalker.applyRecorded(OathState.Ready(held),
        WalkerStepRecorded("0", DeltaPayload(Vector.empty), Vector.empty)),
      Left(OathViolation.InvalidEventOrder(
        "recorded delta step must contain operations")))
```

`validateStep` runs ahead of the guard and checks only the node id
(`WalkerReplay.scala:49-53`), and `"0"` passes it, so the guard is what
rejects this step rather than the node-id check.

- [ ] **Step 2: Run both to verify they fail**

Run: `./sbtw "testOnly oathdigital.serialization.WalkerOperationWireSuite oathdigital.gameplay.walker.WalkerReplayGuardSuite"`
Expected: FAIL — compilation error, `DeltaPayload` does not take arguments.

- [ ] **Step 3: Change the type**

In `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala`, replace
the `DeltaPayload` case object (Task 1's Step 1 block) with:

```scala
/** A step that executed a batch of operations, carrying the batch. `ops`
  * holds the applied leaves (`PrimitiveOperation`s, which are
  * `CoreOperation`s), so replay applies exactly the deltas that produced the
  * step and every reader of what it did works from them. A choice or a roll
  * records a state write instead and carries no batch -- which this family
  * makes a fact of the type rather than a comment. */
final case class DeltaPayload(ops: Vector[CoreOperation])
    extends WalkerStepPayload
```

Then fix the two sibling comments, whose "the step's `ops` stay empty" now
says something the type says. `ChoicePayload` (lines 20-29 at HEAD):

```scala
/** Records a resolved parked decision (Task 5 ruling 5.3). Appending the
  * answer to `pending.answered` is a state write -- the walker rebuilds
  * `answered` from these events at replay -- so a choice carries no
  * operation batch, which is why this payload has nowhere to put one.
  *
  * @param by who answered.
  */
```

`RollPayload` (lines 31-38 at HEAD):

```scala
/** Faces the acting player rolled for `pool`, recorded when a `Roll` park is
  * resumed through `ProcedureWalker.roll` (Task 4). The outcome is a state
  * write (a `RollOutcome` into `CurrentGameState.rollOutcomes`), not an
  * operation batch, so replay re-derives it from this payload -- and, this
  * payload carrying no batch, has nothing else it could apply.
  * `automatic` is true when the walker rolled the faces itself from its dice
  * source; replay then needs no durable park at the node.
  */
```

Then drop `ops` from the event and rewrite its doc (lines 42-62 at HEAD).
`contributions` is kept, but the sentence scoping it to operations goes: every
construction site passes it, choices and rolls included.

```scala
/** Container event: recorded once per delta, resolved choice, or submitted
  * roll the walker executes.
  *
  * `payload` is what the step recorded, and a [[DeltaPayload]] carries the
  * operation batch; `nodeId` is the leaf's child-index path joined with
  * `"."` (repeat passes of one body leaf repeat the same `nodeId` -- events
  * stay ordered in the journal). `contributions` is the deterministic order
  * of powers whose gather produced this step (spec decision 10f) -- an AUDIT
  * fact (who influenced this node), never a replay input: a delta's `ops`
  * alone is the replay authority (spec decision 5), so
  * `ProcedureWalker.applyRecorded` reads the payload and ignores this field
  * entirely. `Vector.empty` for a node with no window (no explicit default:
  * every construction site must state what it recorded).
  */
final case class WalkerStepRecorded(
    nodeId: String,
    payload: WalkerStepPayload,
    contributions: Vector[PowerId]
) extends WalkerEvent
```

- [ ] **Step 4: Move the wire**

In `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala`, the
encoder arm at lines 26-31 loses its `"ops"` key:

```scala
    case WalkerStepRecorded(nodeId, payload, contributions) =>
      ujson.Obj(
        "nodeId" -> nodeId,
        "step" -> encodeStepPayload(payload),
        "contributions" -> stringArray(contributions.map(_.value)))
```

`encodeStepPayload`'s delta arm (line 88) gains them:

```scala
      case DeltaPayload(ops) => ujson.Obj("kind" -> "delta",
        "ops" -> ujson.Arr.from(ops.map(encodeOperation)))
```

`decodeStepPayload`'s delta arm (line 105) reads them:

```scala
      case "delta" =>
        traverse(value("ops").arr.zipWithIndex.toVector) {
          case (operation, index) =>
            decodeOperation(operation, s"$path.ops[$index]")
        }.map(DeltaPayload.apply)
```

`decodeStepRecorded` (lines 269-279) drops its own operation read. The whole
body stays inside the `try`, so a missing `"ops"` key thrown by ujson from
inside `decodeStepPayload` still surfaces as `InvalidValue` at `$path` rather
than escaping — which is what Step 1's second assertion pins.

```scala
  private def decodeStepRecorded(value: ujson.Value,
      path: String): Either[WireError, OathEvent] = try for
    step <- decodeStepPayload(value("step"), s"$path.step")
    contributions = value("contributions").arr.toVector.map(id =>
      PowerId(id.str))
  yield WalkerStepRecorded(value("nodeId").str, step, contributions)
  catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid walker step"))) }
```

- [ ] **Step 5: Delete the two guards**

In `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala`, replace
the three step arms (lines 71-96). The comment above them about
`contributions` being deliberately unmatched stays as it is.

```scala
      case step @ WalkerStepRecorded(_,
          RollPayload(pool, faces, automatic), _) =>
        for
          _ <- if automatic then validateStep(step)
            else validateParkedStep(step).map(_ => ())
          outcome <- WalkerRolls.outcomeForRecorded(ready, pool, faces)
        yield WalkerRolls.write(ready, outcome)

      case step @ WalkerStepRecorded(_,
          ChoicePayload(decisionId, payload, by), _) =>
        for
          pending <- validateParkedStep(step)
          answered = pending.copy(answered = pending.answered :+
            Answered(decisionId, payload, by))
        yield ready.copy(game = ready.game.copy(current =
          ready.game.current.copy(walkerPending = Some(answered))))

      case step @ WalkerStepRecorded(_, DeltaPayload(ops), _) =>
        for
          _ <- validateStep(step)
          _ <- Either.cond(ops.nonEmpty, (), OathViolation.InvalidEventOrder(
            "recorded delta step must contain operations"))
          updated <- executeRecorded(ready, ops)
        yield updated
```

The `ops.nonEmpty` check stays. An empty `DeltaPayload` is still
representable and the wire is untrusted input, so the check belongs where
decoded facts are applied.

- [ ] **Step 6: The four walker construction sites**

`src/main/scala/oathdigital/gameplay/walker/WalkerSteps.scala:69-73`:

```scala
      val step = Option.when(updated.executed.nonEmpty)(WalkerStepRecorded(
        nodeId = nodeId,
        payload = DeltaPayload(updated.executed),
        contributions = contributions))
```

`WalkerSteps.scala:108-112`:

```scala
        events = ctx.events :+ WalkerStepRecorded(
          nodeId = nodeId,
          payload = ChoicePayload(answer.decisionId, answer.answer, answer.by),
          contributions = contributions))
```

`WalkerSteps.scala:128-130`:

```scala
        events = ctx.events :+ WalkerStepRecorded(
          nodeId = nodeId, payload = RollPayload(roll.pool, faces, automatic),
          contributions = contributions))
```

`src/main/scala/oathdigital/gameplay/walker/AdviserArrivalPass.scala:45-49`:

```scala
      val step = Option.when(updated.executed.nonEmpty)(WalkerStepRecorded(
        nodeId = nodeId,
        payload = DeltaPayload(updated.executed),
        contributions = Vector(power)))
```

`src/main/scala/oathdigital/gameplay/walker/FollowUpOffer.scala:57-58` loses
one `_`:

```scala
    case WalkerStepRecorded(_, ChoicePayload(`decisionId`,
        DecisionAnswer.ChooseOneAnswer(`skip`), _), _) => true
```

- [ ] **Step 7: The six production readers**

`src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala:305-308`
(same package, no import needed):

```scala
  private def recordedOperations(events: Vector[OathEvent])
      : Vector[CoreOperation] = events.collect {
    case WalkerStepRecorded(_, DeltaPayload(ops), _) => ops
  }.flatten
```

`src/main/scala/oathdigital/gameplay/OathRulesWalker.scala:353-356` keeps
`.nonEmpty` on the batch rather than becoming a bare type test — it asks
whether a procedure runs operations before its first decision, and an empty
delta must not answer yes:

```scala
        if events.exists {
          case WalkerStepRecorded(_, DeltaPayload(ops), _) => ops.nonEmpty
          case _ => false
        } then Left(InvalidEventOrder(s"${procedure.key} runs operations before " +
          "its first decision, so its start cannot be previewed"))
```

`OathRulesWalker.scala:421-427` is a collect, so matching the payload
preserves it exactly — choice and roll steps contribute nothing today and
cannot contribute anything after:

```scala
        val sources = steps.collect {
          case WalkerStepRecorded(_, DeltaPayload(ops), _) =>
            ops.collect:
              case Play(card: WorldCardId, _, Location.Site(site),
                  Orientation.FaceUp, _) =>
                RuleSourceRef.SiteCard(site, card)
              case Play(card: WorldCardId, _, Location.PlayArea(player),
                  Orientation.FaceUp, _) =>
                RuleSourceRef.Adviser(player, card)
        }.flatten.distinct
```

Add `DeltaPayload` to the `gameplay.walker` import at `OathRulesWalker.scala:7`.

`src/main/scala/oathdigital/application/UndoBarrier.scala:21-23` — the spec's
reader list omits this one. Matching the sealed family exhaustively is what
makes the fourth-payload compile error land here too:

```scala
  private def reveals(event: OathEvent): Boolean = event match
    case WalkerStepRecorded(_, payload, _) => payload match
      case _: RollPayload => true
      case DeltaPayload(ops) => ops.exists(hidden)
      case _: ChoicePayload => false
    case _ => false
```

and its import at lines 3-4:

```scala
import oathdigital.gameplay.walker.{ChoicePayload, DeltaPayload,
  HiddenOutcomes, RollPayload, WalkerStepRecorded}
```

`src/main/scala/oathdigital/application/gamelog/LogJournal.scala:119-128` —
the match that the log-operation-strictness candidate needs honest:

```scala
  private def opSteps(step: ReplayStep[OathState, OathEvent]): Vector[OpStep] =
    (step.before, step.event.event) match
      case (OathState.Ready(start),
          WalkerStepRecorded(_, DeltaPayload(ops), _)) =>
        ops.foldLeft((start, Vector.empty[OpStep])) {
          case ((state, done), operation) =>
            val next = ProcedureWalker.applyRecordedOperation(state, operation)
              .getOrElse(state)
            (next, done :+ OpStep(operation, state, next))
        }._2
      case _ => Vector.empty
```

`LogJournal.scala:93` loses one `_`:

```scala
      case WalkerStepRecorded(_, ChoicePayload(id, answer, by), _) =>
```

and `DeltaPayload` joins the import at lines 4-5.

`src/main/scala/oathdigital/application/gamelog/NoteLines.scala:193,205`:

```scala
      case WalkerStepRecorded(_, DeltaPayload(ops), _) =>
```
```scala
      case WalkerStepRecorded(_, _: ChoicePayload, _) => true
```

`src/main/scala/oathdigital/application/gamelog/DetailLines.scala:30,48` and
`src/main/scala/oathdigital/application/gamelog/StartLines.scala:100` each
lose one `_` from a three-underscore pattern; none of them reads operations.
`DetailLines.scala:32`'s `case _: WalkerStepRecorded` is a type test and does
not change.

- [ ] **Step 8: Compile production only**

Run: `./sbtw "compile"`
Expected: success. `src/test` is still broken; that is Steps 9-14.

- [ ] **Step 9: Add the one place a test reads a step's batch**

Create `src/test/scala/oathdigital/testkit/RecordedOps.scala`. Four suites
had each written this collect themselves; they all delegate to it in Step 10.

```scala
package oathdigital.testkit

import oathdigital.gameplay.walker.{ChoicePayload, DeltaPayload, RollPayload,
  WalkerStepRecorded}
import oathdigital.model.{CoreOperation, OathEvent}

/** The operations a recorded step applied, for the suites that assert what a
  * walk recorded.
  *
  * A choice or a roll records a state write and applies none, so it reads as
  * empty here. That makes "this step applied nothing" a fact about this
  * function rather than about the walker: a test that means it must assert
  * the step's payload instead.
  */
object RecordedOps:
  def of(step: WalkerStepRecorded): Vector[CoreOperation] =
    step.payload match
      case DeltaPayload(ops) => ops
      case _: ChoicePayload | _: RollPayload => Vector.empty

  /** Every operation the recorded steps among `events` applied, in order. */
  def all(events: Vector[OathEvent]): Vector[CoreOperation] =
    events.collect { case step: WalkerStepRecorded => of(step) }.flatten
```

- [ ] **Step 10: Collapse the four duplicated aggregators onto it**

Each of these is `events.collect { case step: WalkerStepRecorded => step.ops }.flatten`
written out again. Replace the body of each; its callers (about 95 sites) do
not change.

`src/test/scala/oathdigital/testkit/PhasePowerHarness.scala:96-98`:

```scala
  /** The operations the walk recorded, in order. */
  def ops(events: Vector[OathEvent]): Vector[CoreOperation] =
    RecordedOps.all(events)
```

`src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala:45-46`,
`src/test/scala/oathdigital/gameplay/CampaignPlanWindowSuite.scala:55-56` and
`src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala:102-103`
each become a call to `RecordedOps.all(events)`, keeping their own signature
and doc comment. Add `import oathdigital.testkit.RecordedOps` to each file
that does not already import from `oathdigital.testkit`.

- [ ] **Step 11: Sweep the 30 wire-path assertions**

Three serialization suites read a step's operations at `payload.ops`, as a
ujson path or as an expected `InvalidValue` path string. They all become
`payload.step.ops`. The spec's measurements did not count these; there are 24
in `WalkerOperationWireSuite`, 5 in `CampaignResultCodecSuite` and 1 in
`PayCostWireSuite`.

```bash
grep -rl 'payload")("ops")\|\.payload\.ops\[' src/test --include='*.scala' | xargs sed -i '' -e 's/("payload")("ops")/("payload")("step")("ops")/g' -e 's/\.payload\.ops\[/.payload.step.ops[/g'
```

Verify nothing was missed and nothing else matched:

```bash
grep -rn 'payload")("ops")\|\.payload\.ops\[' src/test --include='*.scala'
grep -rnc 'payload")("step")("ops")\|\.payload\.step\.ops\[' src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala src/test/scala/oathdigital/serialization/CampaignResultCodecSuite.scala src/test/scala/oathdigital/serialization/PayCostWireSuite.scala
```

Expected: the first prints nothing; the second prints `24`, `5` and `1`.

- [ ] **Step 12: Sweep the five four-element patterns**

None of these reads operations; each drops its trailing `, _`.

- `src/test/scala/oathdigital/gameplay/OathRulesWalkerPowerSuite.scala:81`
- `src/test/scala/oathdigital/gameplay/PassWhenEmptySuite.scala:69`
- `src/test/scala/oathdigital/gameplay/powers/campaign/ArcaneArmorSuite.scala:119`
- `src/test/scala/oathdigital/gameplay/oathkeeper/OathkeeperProcedureSuite.scala:86`
- `src/test/scala/oathdigital/application/GameApplicationServiceActionsSuite.scala:195`
- `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala:153`

For example, `OathRulesWalkerPowerSuite.scala:81`:

```scala
      case WalkerStepRecorded(_, ChoicePayload(_, _, by), _) => by == p1
```

```bash
grep -rl 'case WalkerStepRecorded(\|(WalkerStepRecorded(_, ChoicePayload' src/test --include='*.scala' | xargs sed -i '' -e 's/\(WalkerStepRecorded(_, [A-Za-z]*Payload([^)]*)\), _, _)/\1, _)/g' -e 's/\(WalkerStepRecorded(_, [a-z]*: ChoicePayload\), _, _)/\1, _)/g'
grep -rn 'WalkerStepRecorded(_, .*, _, _)' src/test --include='*.scala'
```

Expected: the check prints nothing. Fix by hand anything the patterns missed.

- [ ] **Step 13: Rewrite the 31 test construction sites**

Three shapes, by payload. Everything below is stated in post-Task-1 names.

**Delta steps** — the operations move inside the payload:

| File | Line | Becomes |
| --- | --- | --- |
| `serialization/WalkerWireEvents.scala` | 13 | `WalkerStepRecorded("0", DeltaPayload(Vector(operation)), Vector.empty)` |
| `serialization/WalkerOperationWireSuite.scala` | 37 | `WalkerStepRecorded("0", DeltaPayload(run.executed), Vector.empty)` |
| `serialization/WalkerOperationWireSuite.scala` | 99, 101, 103 | `DeltaPayload(<the vector already there>)`, one `Vector.empty` left |
| `serialization/WalkerOperationWireSuite.scala` | 132-133, 241-243 | same shape |
| `testkit/OutcomesSuite.scala` | 33 | `WalkerStepRecorded("0", DeltaPayload(Vector.empty), Vector.empty)` |
| `testkit/SituationDriver.scala` | 131 | `WalkerStepRecorded("0", DeltaPayload(ops), Vector.empty)` |
| `gameplay/WalkerReplaySettlementSuite.scala` | 30-32 | `WalkerStepRecorded("0", DeltaPayload(Vector(pay)), Vector.empty)` |
| `gameplay/walker/WalkerReplayGuardSuite.scala` | 23-24 | `WalkerStepRecorded("0", DeltaPayload(Vector(operation)), Vector.empty)` |
| `gameplay/walker/FollowUpReplaySuite.scala` | 28-29 | `WalkerStepRecorded("0", DeltaPayload(Vector(QueueProcedure(travel))), Vector.empty)` |
| `application/gamelog/GameLogRerollSuite.scala` | 39-41 | `WalkerStepRecorded("reroll", DeltaPayload(Vector(Reroll(pool, Vector(0, 1), Some(Vector(TwoShields, TwoShields))))), Vector.empty)` |
| `application/gamelog/GameLogRareLinesSuite.scala` | 31-32 | `WalkerStepRecorded("batch", DeltaPayload(ops.toVector), Vector.empty)` |

`OutcomesSuite:33` is the one delta step constructed with no operations. It is
testing `Outcomes.recorded`, which does not look at the payload, so an empty
batch is still the right fixture; keep it empty.

**Choice and roll steps** — the explicit `Vector.empty` for operations goes:

| File | Lines |
| --- | --- |
| `serialization/WalkerDecisionWireSuite.scala` | 22-24, 29-31, 47-48, 70-72, 73-75, 76-78, 188-190, 216-220 |
| `serialization/WalkerOperationWireSuite.scala` | 377-379, 380-382 |
| `application/gamelog/GameLogNoteLinesSuite.scala` | 239-241 |
| `application/gamelog/GameLogRerollSuite.scala` | 34-35, 36-37 |

For example, `WalkerDecisionWireSuite.scala:22-24`:

```scala
    val events = Vector[OathEvent](WalkerStepRecorded("1",
      ChoicePayload("challenge.ribbon-site", answer, player), Vector.empty))
```

**`UndoBarrierSuite`'s two-payload helper** splits, because a delta's
operations now live in a different place from a roll's absence of them.
Replace `src/test/scala/oathdigital/application/UndoBarrierSuite.scala:18-24`:

```scala
  /** A walker step recording a delta that applies `ops`. */
  private def deltaStep(ops: CoreOperation*): WalkerStepRecorded =
    WalkerStepRecorded("n", DeltaPayload(ops.toVector), Vector.empty)

  private def rollStep(payload: RollPayload): WalkerStepRecorded =
    WalkerStepRecorded("n", payload, Vector.empty)
```

and the one roll case at line 34:

```scala
    assert(blocks(rollStep(RollPayload(PoolKey("attack"), Vector.empty,
      automatic = true))))
```

Its import at lines 3-5 becomes one line, `WalkerStepPayload` no longer used:

```scala
import oathdigital.gameplay.walker.{DeltaPayload, RollPayload,
  WalkerStepRecorded}
```

- [ ] **Step 14: Rewrite the 64 remaining `.ops` reads**

Four of the 68 were the aggregators Step 10 already handled. The rest are in
34 files and take one of two forms.

**A collect over events** — match the payload, which is both shorter and
exact:

```scala
    events.collect { case WalkerStepRecorded(_, DeltaPayload(ops), _) => ops }
      .flatten
```

Sites: `application/GameApplicationServiceRecoverSuite.scala:130`,
`application/gamelog/GameLogNoteLinesSuite.scala:81,378`,
`gameplay/EndWakeProcedureSuite.scala:48`, `gameplay/EndWalkSuite.scala:27`,
`gameplay/FollowUpSupplyWaiverSuite.scala:36`,
`gameplay/OffTurnWalkSuite.scala:38`,
`gameplay/OptionRestrictionSuite.scala:77`,
`gameplay/ProcedureWalkerDeltaSuite.scala:78,127,186`,
`gameplay/ProcedureWalkerParkSuite.scala:185`,
`gameplay/ProcedureWalkerRerollSuite.scala:26`,
`gameplay/TakeWealthProcedureSuite.scala:194`,
`gameplay/oathkeeper/OathkeeperProcedureSuite.scala:52`,
`gameplay/powers/campaign/BookBurningSuite.scala:61-63`,
`gameplay/powers/campaign/ZealotsSuite.scala`,
`gameplay/powers/recover/RelicWorshipSuite.scala:75`,
`gameplay/powers/recover/SilverCharmSuite.scala:139`,
`gameplay/powers/rest/InsomniaSuite.scala:62`,
`gameplay/powers/rest/SilverTongueSuite.scala:65`,
`gameplay/powers/title/ChaosCultSuite.scala:68`,
`gameplay/powers/travel/BrassArmyMovesSuite.scala:49`,
`gameplay/walker/BurnTakesSuite.scala:41`,
`gameplay/walker/ModifierChoiceSuite.scala:148`.

Where the collect is the whole expression and flattens, prefer
`RecordedOps.all(events)` instead and drop the local lambda.

**A read off a step already in hand** — `RecordedOps.of(step)`:

```scala
    assertEquals(RecordedOps.of(steps(1)), Vector[CoreOperation](
      SpendSupply(actor, 1)))
```

Sites: `gameplay/ActAsIfSuite.scala:30`,
`gameplay/CatacombsContributionSuite.scala:53,56,130`,
`gameplay/DeferralSuite.scala:46,68,77`,
`gameplay/ForgeProcedureSuite.scala:253,321,452,489,562`,
`gameplay/ProcedureWalkerDeltaSuite.scala:56`,
`gameplay/ProcedureWalkerParkSuite.scala:33,153,221`,
`gameplay/ProcedureWalkerRollSuite.scala:153`,
`gameplay/ProcedureWalkerWindowSuite.scala:43,72,83,109,134`,
`gameplay/RecoverProcedureSuite.scala:157,161,170,205`,
`gameplay/WalkerRecordedOpsReducer.scala:55,57`,
`gameplay/powers/campaign/PlanDriver.scala:74`,
`serialization/WalkerOperationWireSuite.scala:27,54`,
`testkit/ProcedureWalkerFixture.scala:53`.

Add `import oathdigital.testkit.RecordedOps` where the file does not already
import it, and extend an existing `oathdigital.testkit.{…}` list where it
does.

`WalkerRecordedOpsReducer.scala:36-60` matches on `step.payload`, so it reads
the batch straight out of the pattern rather than through `RecordedOps`:

```scala
      step.payload match
        case RollPayload(pool, faces, _) =>
          …unchanged…
        case DeltaPayload(ops) if ops.isEmpty => current
        case DeltaPayload(ops) =>
          OperationPipeline.run(current, ops,
            OperationPolicy.Permissive, Vector.empty)(Right(_)) match
            case Right(updated) => updated.state
            case Left(violation) => …unchanged…
        case _: ChoicePayload => current
```

Its import at line 4 gains `ChoicePayload` and `DeltaPayload`.

- [ ] **Step 15: Delete the five assertions the type now makes**

Each of these says a choice or roll step applied no operations. A payload with
nowhere to put a batch cannot, so after this change the assertion tests
`RecordedOps.of`, not the walker. In four of the five the sibling line above
already asserts the payload, which is the fact the test means.

- `gameplay/RecoverProcedureSuite.scala:164` — delete. Line 163 asserts
  `steps(2).payload == RollPayload(pool, highRoll, automatic = true)`.
- `gameplay/RecoverProcedureSuite.scala:168` — delete. Lines 165-167 assert
  the `ChoicePayload`.
- `gameplay/RecoverProcedureSuite.scala:265` — delete. Lines 262-264 assert
  the `ChoicePayload`.
- `gameplay/ProcedureWalkerRollSuite.scala:93` — delete. Line 92 asserts
  `step.payload == RollPayload(recoverPool, faces)`.
- `gameplay/ForgeProcedureSuite.scala:451` — delete. Lines 448-450 assert the
  `ChoicePayload`.
- `gameplay/CatacombsContributionSuite.scala:163` — delete, and trim its
  comment at lines 157-160 to drop the clause "and its ops (answering a
  Decide records none)". The `nodeId` assertion at line 162 is the invariant
  the test names.

Also retire `WalkerStepPayload` from `gameplay/RecoverProcedureSuite.scala:8`
if lines 160 and 169 were the file's last use of it, and update the two
`steps(n).payload` assertions there to the new case class:
`assertEquals(steps(1).payload, DeltaPayload(Vector(SpendSupply(actor, 1))))`,
folding in what line 161 asserted separately. Do the same at line 169 with
the `Move` from line 170.

- [ ] **Step 16: Compile and resolve what the sweep missed**

Run: `./sbtw "Test/compile"`
Expected: either clean, or a short list of sites Steps 11-15 did not name.
Fix each with the matching shape above. Do not reintroduce an `ops` accessor
on `WalkerStepRecorded` to silence them.

- [ ] **Step 17: Run the whole suite**

Run: `./sbtw "test"`
Expected: PASS, including both new tests from Step 1.

Three existing suites carry the moved shape without new tests, which is what
the spec's **Testing** section promised the plan would name:

- `serialization/WalkerOperationWireSuite` round-trips every `CoreOperation`
  variant through a delta step, so the payload's new `"ops"` key is exercised
  once per operation in the algebra.
- `serialization/WalkerDecisionWireSuite` round-trips every generic decision
  answer on a choice step, so the two payloads that lost their explicit empty
  batch still encode and decode.
- `serialization/GameEventWireEnvelopeSuite`'s "serialized replay equals
  command state and preserves ordering" replays a real setup journal end to
  end. If the move is incomplete, this is the test that says so; read its
  failure before anything else.

- [ ] **Step 18: Retire the development database**

The journal in `var/` was written in the old shape and no longer loads. It is
not tracked by git; delete it so the next server start writes a fresh one.

```bash
rm -f var/oathdigital.lobs var/oathdigital.properties var/oathdigital.script
```

- [ ] **Step 19: Commit**

```bash
git add -A src
git commit -m "$(cat <<'MSG'
refactor(walker): a step's operations belong to the payload that carries them

WalkerStepRecorded carried ops beside a payload while only one of the three
payloads could have them, so "a roll step carries no operations" was a doc
comment that WalkerReplay enforced twice at runtime against input the type
permitted. The operations move into DeltaPayload and the two guards go: the
shapes they rejected are no longer representable.

The wire follows the type -- a delta's operations are written inside its
payload object, not beside it. The format version stays at 1, so a journal
written before this change breaks as a field error at its first walker step
rather than at its envelope; either way it does not load, and the version
field stays available for a change that needs it. The var/ development
database dies with it.

Tests read a step's batch through one testkit function instead of a field,
which also collapses four suites that had each written the same collect.
Five assertions that a choice or roll step applied no operations are deleted:
the type says it now, and their sibling payload assertions carry the fact.

LogJournal.opSteps can now match the one payload that has operations, which
is what the log operation strictness candidate was waiting for.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

---

### Task 4: The stale comments and the full gate

Seven comments describe the old shape. Five sit on lines Task 3 rewrote and
were fixed there; the two remaining sets are in files Task 3 did not touch.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala:23,103-104,132-133`
- Modify: `src/main/scala/oathdigital/model/Operation.scala:123-126`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSteps.scala:81-92,114-117`
- Modify: `src/test/scala/oathdigital/gameplay/WalkerReplayDriftSuite.scala:57`
- Test: no new tests. Comments are not behaviour; the gate is the whole suite plus coverage.

**Interfaces:**
- Consumes: everything Task 3 produced. Nothing new.

- [ ] **Step 1: Fix the three `ProcedureWalker` parentheticals**

Each says `(ops empty)` about a choice or a roll, which the type now says.

At `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala:23`,
replace `carrying a [[ChoicePayload]] (`ops` empty) is recorded` with:

```
    *    carrying a [[ChoicePayload]] is recorded, and the walk continues.
```

At lines 103-104, replace the parenthetical after `[[RollPayload]]`:

```
    * rolls of the same pool), append ONE [[WalkerStepRecorded]] carrying
    * [[RollPayload]] -- the outcome is a state write, and the payload holds
    * no operation batch -- then continue auto-walking the tree from the node
```

At lines 132-133, the same for `resolve`:

```
    * ONE [[WalkerStepRecorded]] carrying [[ChoicePayload]] -- the answer is
    * a state write into `pending.answered`, and the payload holds no
    * operation batch -- add
```

- [ ] **Step 2: Point `BuildOps`' doc at the payload**

At `src/main/scala/oathdigital/model/Operation.scala:125-126`, the comment
names a field that no longer exists:

```
  * `CoreOperation`s that come back, recording them in the node's step as a
  * `DeltaPayload`.
```

- [ ] **Step 3: Fix the two `WalkerSteps` doc comments**

At `src/main/scala/oathdigital/gameplay/walker/WalkerSteps.scala:85-86`,
`answerDecide`'s doc says `(ops empty -- the answer is a state write into
pending.answered)`. Replace with:

```
    * [[WalkerStepRecorded]] carrying a [[ChoicePayload]] is appended -- the
    * answer is a state write into `pending.answered`, so there is no
    * operation batch to carry.
```

At lines 115-117, `recordRoll`'s doc says `with no ops`. Replace:

```
    * a [[RollPayload]]; the outcome is a state write, so the step carries no
    * operation batch.
```

- [ ] **Step 4: Fix the drift suite's doc reference**

At `src/test/scala/oathdigital/gameplay/WalkerReplayDriftSuite.scala:57`,
`WalkerStepRecorded.ops` becomes `DeltaPayload.ops`.

- [ ] **Step 5: Check no comment still names the old shape**

```bash
grep -rn '`ops` empty\|ops empty\|WalkerStepRecorded\.ops\|DeltaRecorded' src --include='*.scala'
```

Expected: no output.

- [ ] **Step 6: Run the full gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: exit 0.

- [ ] **Step 7: Check the coverage ratchet**

Run: `./sbtw clean coverage test coverageReport`
Expected: exit 0 — statement coverage at or above 92.0 and branch coverage at
or above 84.4 (`build.sbt:88-90`).

Task 3 removed two guards, one `throw` and six assertions, and added two
tests plus one testkit function with three covered branches. If either number
has fallen,
read the report before touching `build.sbt`: a drop means a branch this plan
created is untested, not that the ratchet is wrong. Do not lower either
minimum.

- [ ] **Step 8: Check the file sizes**

```bash
wc -l src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala src/main/scala/oathdigital/serialization/WalkerEventCodec.scala src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala
```

Expected: all three below 800 and all three smaller than at `69402731`
(115, 305, 196).

- [ ] **Step 9: Commit**

```bash
git add src
git commit -m "$(cat <<'MSG'
docs(walker): the comments stop saying what the type says

Six doc comments described a step whose operations sat beside its payload,
three of them explaining that a choice or a roll leaves ops empty. The
family makes that a compile-time fact, so the comments say what the payload
holds instead of what callers must not put in it.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

---

## What this plan does not do

Recorded so a reviewer does not read these as oversights. Each is a candidate
of its own, and the first is the one this plan exists to unblock.

- **`LogJournal.opSteps` stays lenient.** It still falls back with
  `.getOrElse(state)` where the engine's fold short-circuits. Making it strict
  is the 2026-10-13 architecture review's candidate I, recorded in
  `docs/superpowers/specs/2026-10-19-log-operation-strictness-decision.md`,
  and it was sequenced after this plan because it needs the honest match this
  plan gives it. Step 7 of Task 3 changes only the pattern, not the fallback.
- **The log's per-marker re-application cost.** `LogJournal` re-applies the
  whole journal once per void marker plus once. Separate candidate.
- **`DetailLines.deltas` builds `OpStep`s whose states do not bracket their
  operation** (`DetailLines.scala:72-75`), a possible latent wrong-visibility
  bug. Separate candidate.
- **`LogScripts.formatAppended`'s unvalidated `stateBefore`/`lastStateAfter`
  hooks** synthesize `ReplayStep`s the engine never validated. Separate
  candidate.
- **Historical specs and plans under `docs/superpowers/` are not rewritten.**
  Each is dated and records what was decided then; the design this plan
  implements is the live statement. Only `WalkerReplayDriftSuite`'s comment
  changes, because it documents code rather than a decision.
