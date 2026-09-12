# Declarative Walker Decisions — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the transformed `Decide` operation the only source of both command legality and decision projection. Today the answers a player may submit live in per-action `validate` closures on the tree, while the choices the UI offers live in separate expressions inside `WalkerDecisionProjector` and `PendingProcedureProjector`. They agree only because both call the same helper by convention. Replace both with one declarative `DecisionQuery` the walker validates generically and the projector projects verbatim, so a power that transforms a decision changes what is legal and what is offered in the same edit.

**Architecture:** `Decide` carries `decisionId`, a concrete `PlayerId` owner, and a `DecisionQuery`. `ChooseOne` exposes independently selectable options; `Partition` declares sections with minimum counts and the options to spread across them. Every option has a stable `DecisionOptionRef`, and both answers are generic over refs: `ChooseOneAnswer(selected)` and `PartitionAnswer(placements)`. The walker rebuilds and power-transforms the tree, confirms the owner is the pending actor, validates the submitted answer against the query with one generic validator, and records it. `WalkerDecisionProjector` projects the same transformed query. After this change no engine, projector, wire or renderer type names a Recover or Forge answer shape.

**Tech Stack:** Scala 2.13, sbt multi-project (root engine + `shared` protocol + `frontend` Scala.js), munit, ujson. Full gate: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.

**Spec:** `docs/superpowers/specs/2026-09-10-declarative-walker-decisions-design.md` (approved, revised twice — generic answers over option references, then the removal of legacy answer-tag translation). This plan implements all of it.

**Relationship to walker batch 1:** `docs/superpowers/plans/2026-09-09-walker-batch-1-forge-travel-wake.md` is mid-flight — Tasks 1, 1b, 2, 3 and 4 have landed, Task 5 (Travel cutover) has not. That plan's Task 2 was amended after Forge shipped and now describes Forge's decision in this spec's vocabulary, so the committed `ForgeProcedure` no longer matches its own plan text. This plan is what closes that gap. It is written to land **before** batch 1's Task 5: Travel is the next action to declare a tree, and it should declare a declarative decision rather than a validation closure that would have to be rewritten a task later.

Two neighbouring batch-1 amendments are deliberately **not** in scope here, because they are rules changes rather than decision-contract changes: dropping the `relicDeck.nonEmpty` start gate so an exhausted relic deck is a legal Forge outcome (commit `829135b`), and the Travel powered-candidate simulation (commit `44fb0e0`). Leave both to batch 1.

---

## Rulings to confirm at review

The revised spec and the review settled the two layering questions the previous draft had to work around. Both are restated here as they now stand, because the *reasons* changed even where the answers did not.

**R1 — the whole vocabulary lives in one model file** *(settled at review)*. Queries, options, references, sections, placements and both answers go together in `oathdigital.model`, in one file. Splitting `DecisionOption` into a persisted `DecisionOptionRef` and a display-carrying `DecisionOption` removed the constraint that would have forced a split, so there is no longer a reason to scatter the contract across two packages: a reader opens one file and sees both what may be asked and what may be answered.

The tradeoff, stated plainly rather than argued: the model now holds a button's label and a section's label. That is prompt copy in a layer that otherwise holds none. It buys a single home for the contract, and it costs nothing structurally — `Decide` references these types exactly as it already references `PlayerId` and `PoolKey`, and gameplay is free to import the model in a way the reverse never is.

Two things are **not** in that file. The validator returns typed `OathViolation`s, which the model may not name, so it stays in gameplay (R3). And `DecisionOptionRef.Deck` needs `CardDeck` to be model-safe, which is R2.

On the apparent contradiction between the spec carrying labels on the query and its non-goal "moving presentation labels into gameplay/model code": those are different labels. Declarative prompt copy for a button or a section is authored by the action and travels on the query. Game-object names — a relic's name, a denizen's title — never enter gameplay and are still resolved by `GamePresentationProjector` at projection time from the option's ref. A `Denizen` option carries a ref and nothing else.

**R2 — `CardDeck` moves to the model, and all seven ref variants are declared.** `DecisionOptionRef.Deck(id: CardDeck)` is persisted, so `CardDeck` cannot stay in `gameplay/operations/CoreOperations.scala`. The move is nearly free and that is worth knowing before it is planned as a risk: `CardDeck` is a closed four-case enum, and of the ten production files that reference it, nine already wildcard-import `oathdigital.model._`, so they keep compiling untouched. Only `gameplay/actions/Search.scala` names it in an explicit `gameplay.operations` import. No case object, no JSON tag and no wire string changes — the type declaration moves packages and nothing else.

**R3 — the generic validator lives in gameplay, not in `ProcedureWalker`** *(tentatively approved)*. It returns typed `OathViolation`s, which the model may not name, so it cannot sit in the model beside the data it validates. And `ProcedureWalker.scala` is at 790 of the 800-line cap, so it cannot sit there either. It goes in a `DecisionQueries` object beside `DecisionQuery`, and the walker's `answerDecide` shrinks to an owner comparison plus one call. The thing to check at review is the boundary this draws: `DecisionQueries` never reads `ReadyGame`, so it cannot express state-dependent legality even by accident. Staleness is handled structurally instead, because the tree carrying the query is rebuilt against authoritative state on every command and an option that no longer exists is simply absent from the query. Adding a state parameter here would reintroduce the per-decision legality closure this change exists to delete.

---

## Global Constraints

- Do not break any other action. Full gate green at every commit (root + shared + frontend).
- **Three production files this change touches are within ten lines of the 800-line cap**: `ProcedureWalker.scala` (790), `serialization/WalkerEventCodec.scala` (795) and `frontend/ServerUiSupport.scala` (795). `ActionDecisionRenderer.scala` (774) is close behind. Every task that adds to one of these must budget an extraction in the same commit, not discover the cap at the gate. Tasks 3 and 4 name the extractions they expect.
- Engine code contains no action-specific or power-specific logic. After Task 4 no projector may name a `decisionId` or an `ActionRef` to decide what to offer; `BackendArchitectureSuite`'s "a walker power imports no engine, and the engine never learns its name" must stay green without being weakened.
- **No journal compatibility.** The alpha holds no recorded games worth preserving, so the `"recover-choice"`, `"recover-relic"` and `"forge-assignment"` answer tags are deleted from both sides of the event codec rather than kept as a decode-only translation, and journal fixtures asserting them are rewritten to the generic `"choose-one"` and `"partition"` tags in the same commit. This is what the spec now says and the constraint batch 1 already works under.
- Presentation copy never affects legality, per R1. A query carries prompt copy for buttons and sections; game-object names are resolved at projection time by `GamePresentationProjector` from an option's reference, and never enter a query, an answer or a validator.
- Owner-private projection is unchanged: `WalkerDecisionProjector.project` already gates on `context.viewer.contains(pending.actor)` and every new field inherits that.
- Per-task gate: `./sbtw "test"` green plus `python3 scripts/check-architecture.py`. Tasks touching `shared/` or `frontend/` additionally run `./sbtw "frontend/test" "frontend/fastLinkJS"`. Task 6 runs the full gate.
- Commit per task with the exact message shown. Work on branch `feat/walker-declarative-decisions` cut from `feat/engine-redesign`.

---

### Task 1: `DecisionPayload` becomes `DecisionAnswer`

**Files:**
- Rename: `src/main/scala/oathdigital/model/DecisionPayload.scala` → `Decisions.scala`
- Modify: `model/PendingTree.scala`, `gameplay/operations/CoreOperations.scala`, `gameplay/walker/WalkerEvents.scala`, `gameplay/actions/recover/RecoverProcedure.scala`, `gameplay/actions/forge/ForgeProcedure.scala`, `serialization/WalkerEventCodec.scala`, `application/GameCommands.scala`, `application/GameIntentMapper.scala`, `shared/.../protocol/CommandIntents.scala`, `shared/.../protocol/CommandNestedCodecs.scala`, `shared/.../protocol/CommandIntentDecoders.scala`, `frontend/.../ServerUiSupport.scala`, `frontend/.../ForgeAssignmentState.scala`
- Test: every suite naming the old family (`ProcedureWalkerSuite`, `WalkerStateSuite`, `RecoverProcedureSuite`, `ForgeProcedureSuite`, `GameEventWireSuite`, `GameHttpWireSuite`, `WalkerReplayDriftSuite`, `OathRulesWalkerPowerSuite`, `GameApplicationServiceSuite`, `WalkerDecisionProjectionSuite`, `HttpGameClientSuite`, `ServerModeUiSuite`, `ProtocolTestCommands`)

A pure rename, landed alone so that the substantive tasks read as design changes rather than as a diff dominated by churn. `DecisionPayload` → `DecisionAnswer`; `Answered.payload` → `Answered.answer`; `TreeDecision.payload` → `TreeDecision.answer`; `DecisionPayloadWire` → `DecisionAnswerWire`.

The file takes the neutral name `Decisions.scala` rather than `DecisionAnswer.scala`, because Task 2 adds the query half of the vocabulary to the same file (R1). Naming it for the answer family now would mean renaming it again one commit later.

Rename the three concrete cases along with the family — `RecoverChoiceAnswer`, `RecoverRelicAnswer`, `ForgeAssignmentAnswer` — rather than leaving a half-renamed family standing for two commits. All three are **deleted** in Task 3 when generic answers replace them, so spend no thought on their names beyond consistency.

Two things deliberately do **not** change. `WalkerStepPayload.ChoicePayload` keeps its name: it is a recorded *step* kind, not a member of the answer family, and `RollPayload` sits beside it under the same parent. Rename only its `payload` field to `answer`. And `RestPowerDecisionPayload` is a different family belonging to the legacy pending-procedure path; leave it entirely alone.

No wire or persisted string moves in this task. `encodeDecisionAnswer` still emits `"kind" -> "recover-choice"` and journal fixtures are untouched; the encoding changes in Task 3. If a recorded-journal assertion changes here, the rename went too far.

- [x] **Step 1: rename** the family, its three cases and the two field names across all four source roots. Compile-driven; there is no new behaviour to test.
- [x] **Step 2: verify nothing persisted moved.** `git diff` must show no change to any string literal inside `WalkerEventCodec`'s encode/decode bodies or to any journal fixture. `grep -rn "DecisionPayload" src shared frontend --include=*.scala` returns only `RestPowerDecisionPayload` hits.
- [x] **Step 3:** `./sbtw "test"`, `./sbtw "frontend/test" "frontend/fastLinkJS"`, `python3 scripts/check-architecture.py`, `git diff --check`.
- [x] **Step 4: commit** `refactor(walker): rename decision payloads to decision answers`.

---

### Task 2: the declarative decision vocabulary lands, unused

**Files:**
- Create: `src/main/scala/oathdigital/model/CardDeck.scala`, `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala`
- Modify: `src/main/scala/oathdigital/model/Decisions.scala` (the whole vocabulary lands here), `gameplay/operations/CoreOperations.scala` (remove `CardDeck`), `gameplay/actions/Search.scala` (its one explicit import)
- Test: `src/test/scala/oathdigital/gameplay/walker/DecisionQuerySuite.scala` (new); mechanical import fixes in any suite naming `CardDeck` through `gameplay.operations`

Additive only apart from the `CardDeck` package move, so this commit changes no behaviour and the gate stays green by construction. Landing the contract and its validator alone is what makes Task 3 reviewable — by then the only open question is whether Recover and Forge state their rules correctly, not whether the rules engine is right.

All of it in `model/Decisions.scala`, beside the `DecisionAnswer` family Task 1 renamed (R1) — the question half and the answer half in one place:

```scala
sealed trait DecisionOptionRef              // Button(key) / Player / Site / Denizen / Relic / Vision / Deck
sealed trait DecisionOption { def ref: DecisionOptionRef }   // Button carries a label; the rest carry only a ref
final case class DecisionSection(key: String, label: String, minRequired: Int)

sealed trait DecisionQuery
object DecisionQuery {
  final case class ChooseOne(options: Vector[DecisionOption]) extends DecisionQuery
  final case class Partition(sections: Vector[DecisionSection],
      options: Vector[DecisionOption]) extends DecisionQuery
}

final case class DecisionPlacement(option: DecisionOptionRef, sectionKey: String)
final case class ChooseOneAnswer(selected: DecisionOptionRef) extends DecisionAnswer
final case class PartitionAnswer(placements: Vector[DecisionPlacement]) extends DecisionAnswer
```

Keep the file's existing header comment accurate: it currently explains why answers must be model-safe, and it now also has to say that queries live here by choice rather than by constraint, so a later reader does not "restore" the split.

`CardDeck` moves from `CoreOperations.scala` into its own model file so that `DecisionOptionRef.Deck` can name it (R2). Move the declaration only — same four case objects, same names, no JSON or wire change.

`DecisionQueries`, in `gameplay/walker/` (R3), holds the whole generic contract as two functions (R3). Both take the `decisionId` as their first argument, because every violation they return names the decision. `wellFormed(decisionId, query)` is the structural check a malformed *tree* fails, and it enforces two properties that are different in kind.

The first is that the query can be answered at all: a query with no options is invalid on a parked decision; `ChooseOne` option refs must be unique; `Partition` section keys must be unique with non-negative minima, its option refs must be unique, and its minima must not together demand more placements than there are options — two sections each requiring two of three options rejects every possible answer.

The second, added at review, is that the query is worth asking. An action must not park and prompt for an answer that is already determined, so a `Partition` needs at least two sections and no single section may demand every option. Those two rules are exactly the forced shapes rather than a heuristic: given satisfiable minima and two or more sections, a partition has one legal answer if and only if some section's minimum equals the option count. A single-option `ChooseOne` is deliberately still well-formed — a lone button is a consent step rather than a choice, and the node is also where a power window hangs.

`accepts(decisionId, query, answer)` is the check a bad *submission* fails: `ChooseOne` requires a `ChooseOneAnswer` whose ref is one of the declared option refs; `Partition` requires a `PartitionAnswer` that places every declared option ref exactly once, names only declared sections, and meets every section's minimum. Both return `Either[OathViolation, Unit]` and never throw — a malformed query and a mismatched answer are both `InvalidEventOrder` naming the decision, and neither is an action-specific violation case.

Both functions compare refs and nothing else. A label never affects legality, which is what lets a power restate a prompt without touching what is submittable.

- [x] **Step 1: failing tests** in `DecisionQuerySuite`, against hand-built queries with fixture answers and no game state: (a) `ChooseOne` accepts each declared ref and rejects an undeclared one; (b) duplicate option refs and an empty `ChooseOne` fail `wellFormed`; (c) two options differing only in label are rejected as duplicates, proving legality is ref-keyed; (d) `Partition` accepts a complete legal placement; (e) it rejects an unplaced option, a twice-placed option, an undeclared option, an unknown section key, and a section left under its minimum, each with a distinguishable message; (f) duplicate section keys, a negative minimum, duplicate options, an empty `Partition`, a `Partition` with fewer than two sections, minima that together exceed the option count, and a section demanding every option all fail `wellFormed`, while a single-option `ChooseOne` and a partition whose minima merely fix each section's size both pass; (g) each query shape rejects the other's answer type rather than matching loosely. Expected FAIL: nothing exists.
- [x] **Step 2: implement** the vocabulary in `model/Decisions.scala`, the `CardDeck` move with `Search.scala`'s import, and `DecisionQueries` in `gameplay/walker/`. Leave `WalkerModel.scala` alone; it holds only `OwnerQuery` and `WalkerCtx` now, and Task 3 deletes the file.
- [x] **Step 3:** re-run the focused suite; expected PASS.
- [x] **Step 4:** `./sbtw "test"`, `python3 scripts/check-architecture.py`. The architecture check is what proves the vocabulary is genuinely model-safe: any field reaching a gameplay type fails here, not three tasks later. `CardDeck` is the one that would have.
- [x] **Step 5: commit** `feat(walker): declare the decision query vocabulary`.

---

### Task 3: `Decide` becomes declarative, and both actions declare queries

**Files:**
- Modify: `gameplay/operations/CoreOperations.scala`, `gameplay/walker/ProcedureWalker.scala`, `gameplay/actions/recover/RecoverProcedure.scala`, `gameplay/actions/forge/ForgeProcedure.scala`, `gameplay/actions/ForgeRules.scala`, `gameplay/model/GameViolation.scala`, `serialization/WalkerEventCodec.scala`, `application/GameIntentMapper.scala`, `shared/.../protocol/CommandIntents.scala`, `shared/.../protocol/CommandNestedCodecs.scala`, `frontend/.../ServerUiSupport.scala`, `frontend/.../ForgeAssignmentState.scala`
- Create: a decision-answer codec file extracted from `WalkerEventCodec.scala`
- Delete: `gameplay/walker/WalkerModel.scala` entirely; `Decide.payload` and `Decide.validate`; `RecoverProcedure.validateChoice`/`validateRelic`; `ForgeProcedure.validateAssignment`/`favorBySuit`/`suitOf`/`assignmentOperations`; `DecisionAnswer.RecoverChoiceAnswer`/`RecoverRelicAnswer`/`ForgeAssignmentAnswer` and the `RecoverChoice` enum; `OathViolation.ForgeOutcomeMismatch`; `DecisionAnswerWire`'s three legacy cases, and the three legacy answer tags from both the command codec and the event codec
- Test: `ProcedureWalkerSuite`, `WalkerStateSuite`, `RecoverProcedureSuite`, `ForgeProcedureSuite`, `ForgeRulesSuite`, `GameEventWireSuite`, `WalkerReplayDriftSuite`, `CommandProtocolSuite`, `GameApplicationServiceSuite`, `HttpGameClientSuite`, `ServerModeUiSuite`

This is the one commit that cannot be split further. Changing `Decide`'s constructor breaks both declared trees at once, and deleting `validate` means each tree's legality must already be in its query — so Recover and Forge migrate together or not at all. Because both actions' answers become generic in the same stroke, both wire cases and both frontend command constructors move with them.

```scala
final case class Decide(decisionId: String, owner: PlayerId,
    query: DecisionQuery,
    override val window: Option[PowerWindow] = None)
    extends PrimitiveOperation
```

`ProcedureWalker.answerDecide` becomes: confirm `decide.owner == ctx.actor` (a mismatch is `WrongPlayer`, exactly as today, but with no `None` case to handle because the owner is concrete), then `DecisionQueries.wellFormed` and `DecisionQueries.accepts`, then record the answer unchanged. `OwnerQuery` and `WalkerCtx` lose their last users and go — and since the vocabulary they used to sit beside now lives in the model, `WalkerModel.scala` has nothing left in it and the file goes too. Neither type has any other reference in production.

**Recover** declares two `ChooseOne` queries. The continue/stop decision declares `Button(Button("continue"), "Continue")` and `Button(Button("stop"), "Stop")`; the relic decision declares one `Relic(Relic(relicId))` option per live facedown site relic, built where the surrounding `Branch` already calls `actorFacedownRelics`. Its answers become `ChooseOneAnswer(Button("continue"))` and `ChooseOneAnswer(Relic(relicId))`, so two things inside the tree change with them: the `Repeat` guard's `stopped` predicate now matches the stop button's key rather than a `RecoverChoice.Stop` case, and the trailing `moveRelic` `BuildOps` reads the relic id out of the recorded ref. Both read the same fact through the generic vocabulary; neither is a semantic change.

Deleting `validateChoice` looks like a lost check and is not. Its whole body rejected a continue-or-stop answer when the recovery had already succeeded — and the `Branch` that carries the node already omits it in exactly that case, so on a rebuilt tree a stale choice answer finds no matching `Decide` and rejects before the query is consulted. Prove that with a test rather than asserting it. `validateRelic` likewise collapses into the option list, since both it and the projector already read the same `actorFacedownRelics`.

**Forge** becomes a `DecisionQuery.Partition`: sections `"pay-favor"` and `"pay-secret"` labelled “Pay Favor” and “Pay Secret”, minima taken from `printedCost`, and one `Denizen` option per live eligible target. Its trailing `BuildOps` maps each placement to `PayCost(actor, Location.OnCard(denizen), Cost(favor = 1))` or the `Cost(secret = 1)` equivalent, so `OperationPipeline` validates the payments generically and applies them atomically.

**Forge must not park at a single-resource site.** Task 2's forced-decision rule makes this a hard constraint rather than a nicety. Four of the seven forgeable sites print a cost of three of one resource — Ancient City and Golden Valley at three favor, Standing Stones and Steppe at three secrets — and at those sites one section demands all three denizens, so the query is forced and `wellFormed` rejects it. Forge therefore declares the `Decide` node only when both minima are non-zero, and otherwise applies the determined split directly in the trailing `BuildOps`. That is a behaviour change in this commit: today Forge parks and prompts at those four sites for an answer the player cannot get wrong.

That is a **rules change**, not just a restatement: Forge currently moves favor out of the target denizen's own suit bank and secrets out of the shared bank. Under the spec the actor funds the payment from their own play area and suit banks are never consulted. Two consequences follow and both belong in this commit. `suitOf`, `favorBySuit` and the suit-bank sufficiency check in `validateAssignment` are deleted along with their `ForgeOutcomeMismatch` uses — which removes that violation's last producer, so the case goes too (`RecoverOutcomeMismatch` keeps a producer in `PowerOperations` and stays). And `ForgeRules.validate` must gain an affordability gate on the actor's own favor and secrets, via `Costs.plan`, because a Forge that starts with the player unable to pay spends Supply, parks, and can never be answered — the same stranding failure `ForgeProcedure.rebuild`'s doc comment already exists to prevent. The existing `relicDeck.nonEmpty` gate stays; changing it is batch 1's business.

**Wire.** `DecisionAnswerWire` becomes two generic cases: `ChooseOneWire(optionKind, optionId)` and `PartitionWire(placements)`, each placement carrying an option kind, its id, and its section key. The three legacy cases and their codec branches are deleted — the command wire has no backward-compatibility obligation. `GameIntentMapper` maps kind-plus-id to a `DecisionOptionRef` through one total function over the seven declared variants; this is generic mapping and must name neither Recover nor Forge.

**Event codec.** Both sides carry the generic tags only: `"choose-one"` and `"partition"`. The three legacy tags are deleted from encode and decode together, and the journal fixtures in `GameEventWireSuite` that assert them are rewritten to the new shapes in this commit — no translation layer, no decode-only survivors. Both answers encode a `DecisionOptionRef` as kind plus id, so write that encoder once and use it from both.

**Extract the answer codec anyway.** `WalkerEventCodec.scala` sits at 795 of 800 lines. Deleting three legacy branches while adding a ref codec and two generic ones roughly breaks even, which leaves the file exactly as close to the cap as it is today and makes the next decision variant someone adds a cap failure. Move the decision-answer codec into its own file in this commit for the headroom, not for the volume. While there, settle the question batch-1 Task 3 flagged and deferred: `encodeDecisionAnswer` still *throws* on an unknown answer while its decode counterpart returns a typed `Left`. With both concrete answers now generic the family is far closer to closed than it was, so decide deliberately whether it should be sealed and the match exhaustive, and record the reasoning in the ledger.

**Frontend, minimally.** Recover's two command constructors build `ChooseOneWire` from the button key and the relic id — the frontend already speaks `"continue"`/`"stop"` strings, so this is a small change. `ForgeAssignmentState.command` emits `PartitionWire`, mapping its per-target favor/secret choice to a placement in the matching section. Both keep their current renderers and their current `relicCandidates`/`ForgeProjection` inputs here; Task 4 re-sources those from the projected query and Task 5 replaces Forge's interaction. Do **not** add a translation shim that keeps a legacy command-wire case alive for a task.

- [x] **Step 1: failing tests.** In `ProcedureWalkerSuite`/`WalkerStateSuite`: a generic `Decide` accepts exactly its declared options, rejects an undeclared ref, rejects a malformed and an empty query, and rejects an owner who is not the pending actor with `WrongPlayer`. In `RecoverProcedureSuite`: the choice decision declares exactly the two buttons; the relic decision declares one option per live facedown relic; a relic answer naming a relic that has since left the site rejects on the rebuilt tree; a continue answer submitted after the roll succeeded rejects because the node is gone; the `Repeat` guard still stops on the stop button; empty-site Recover still finishes without parking. In `ForgeProcedureSuite`: the query declares the three live denizens and the printed minima and consults no suit bank; a three-favor or three-secret site declares no `Decide` node at all and resolves its forced split without parking; a legal placement resolves to exactly three player-funded `PayCost`s; an incomplete, duplicated, or minimum-violating placement rejects; a Forge started with insufficient player favor or secrets is rejected by `ForgeRules.validate` before any Supply is spent. In `GameEventWireSuite`/`WalkerReplayDriftSuite`: generic answers round-trip and replay unchanged, the rewritten journal fixtures assert the generic tags, and an event carrying one of the three deleted tags is rejected with a typed decode failure rather than silently ignored. Expected FAIL: `Decide` has no `query`.
- [x] **Step 2: implement** the `Decide` shape change, the walker's generic resolution, and the `OwnerQuery`/`WalkerCtx` deletion.
- [x] **Step 3: implement** Recover's two `ChooseOne` queries, its guard and relic-move reads, and delete both closures with the `RecoverChoice` enum and its two answer cases.
- [x] **Step 4: implement** Forge's `Partition`, its single-resource no-park path, its `PayCost` translation, the `ForgeRules` affordability gate, and the suit-bank deletions with `ForgeOutcomeMismatch`.
- [x] **Step 5: implement** the wire and mapper changes, then the event codec: the extracted file, the generic encoders, the legacy-tag deletion with its fixture rewrites, and the throw-versus-typed-error decision.
- [x] **Step 6:** re-run the suites; expected PASS. Two sweeps, and both must return nothing. The deleted symbols, word-bounded so they do not match live camel-case identifiers that merely contain them:

  ```bash
  grep -rnE '\b(OwnerQuery|WalkerCtx|RecoverChoice|RecoverChoiceAnswer|RecoverRelicAnswer|ForgeAssignmentAnswer|RecoverChoiceWire|RecoverRelicWire|ForgeAssignmentWire|ForgeOutcomeMismatch|ForgeAssignment)\b' src shared frontend/src
  ```

  And the three deleted wire tags, as complete quoted literals, in **production sources only**:

  ```bash
  grep -rn '"recover-choice"\|"recover-relic"\|"forge-assignment"' src/main shared/src/main frontend/src/main
  ```

  Both narrowings are load-bearing rather than convenient, so do not widen them back. The tag sweep excludes tests because Step 1 **requires** negative tests that name all three deleted tags and assert each is now a typed decode failure — a sweep over tests would contradict the step that demands them. It excludes unquoted matches because `ServerUiSupport` styles its relic button with the CSS class `recover-relic-choice`, which is presentation naming that shares a prefix with a wire tag and is not one. The symbol sweep is word-bounded because the frontend keeps `resolveRecoverChoiceCommand`, a live method whose name contains `RecoverChoice` and which has nothing to do with the deleted enum. A hit in either sweep is a real survivor; expect zero and investigate anything else.
- [x] **Step 7:** `./sbtw "test"`, `./sbtw "frontend/test" "frontend/fastLinkJS"`, `python3 scripts/check-architecture.py`, `git diff --check`.
- [x] **Step 8: commit** `feat(walker): make decisions declarative and migrate Recover and Forge`.

**Report before Task 4.** This is the task that proves the contract carries two differently-shaped real decisions. If either action needed something the query could not state — a legality fact that is genuinely not an option set, a minimum that is not a count — say so plainly in the ledger before the projection work starts, because Tasks 4 and 5 assume the query is complete enough to project verbatim.

---

### Task 4: projection reads the transformed query

**Files:**
- Modify: `shared/.../protocol/projection/ActionProjectionDtos.scala`, `shared/.../protocol/projection/GameProjectionCodec.scala`, `shared/.../protocol/projection/GameProjectionDto.scala`, `application/WalkerDecisionProjector.scala`, `application/PendingProcedureProjector.scala`, `application/ScopedProjectionContext.scala`, `frontend/.../package.scala`, `frontend/.../ServerUiSupport.scala`, `frontend/.../ActionDecisionRenderer.scala`, `frontend/.../ForgeAssignmentState.scala`
- Delete: `WalkerDecisionProjection.relicCandidates` and `WalkerDecisionProjector.relicCandidates`; `PendingProcedureProjector.forgeProjection`; `ForgeProjection`; `ForgeAssignmentTargetProjection`; `GameProjectionDto.forge`
- Test: `WalkerDecisionProjectionSuite`, `ProjectionProtocolSuite`, `ServerModeUiSuite`, `GameApplicationServiceSuite`, and a new synthetic-power suite

The projector stops discovering candidates and starts describing the query it already has. `WalkerDecisionProjection` loses `relicCandidates` and gains an optional projected query:

```scala
final case class DecisionQueryProjection(form: String,          // "choose-one" | "partition"
    options: Vector[DecisionOptionProjection],
    sections: Vector[DecisionSectionProjection] = Vector.empty)
final case class DecisionOptionProjection(kind: String, id: String, label: String,
    card: Option[CardDetailsProjection] = None)
final case class DecisionSectionProjection(key: String, label: String, minRequired: Int)
```

An option projects its stable reference as `kind` plus `id`, its display text, and for card-shaped options the existing `GamePresentationProjector.cardDetails` output, so disclosure rules are unchanged and no naming logic enters gameplay. A `Button`'s label is the query's own declarative copy; a card or board object's label comes from presentation.

There is deliberately **no** wire answer on a projected option. The client already holds everything an answer needs — a generic `ChooseOneWire(kind, id)` or a `PartitionWire` of placements is built from the same kind-and-id pair the option carries — so embedding a prebuilt answer would duplicate the identity and couple the projection DTOs to the command protocol for nothing.

`WalkerDecisionProjector.parked` loses its `if (decide.decisionId == RecoverProcedure.relicDecisionId)` branch entirely and projects `decide.query`. When an option's identity cannot be presented — an id absent from authoritative state — omit the whole decision projection rather than emit a half-described option; that is the spec's failure rule and it must be a test, not a comment. `rollOutcome` and the roll-only `pool`/`count` fields are unchanged; they are roll feedback, not option discovery.

`forgeProjection` and its two DTOs go. Forge's frontend state is re-sourced from `walkerDecision.query` — the sections give it labels and minima, the options give it the three denizens — while keeping its current dropdown interaction for one more task. Recover's `recoverWalkerStep` keeps its per-step renderers but takes its relic list and its two button refs from the projected options. Its supply-aware Continue label and disabled state may stay: interpreting a known option for a richer interaction is explicitly permitted, independently computing who is eligible is not.

`ServerUiSupport.scala` is at 795 lines and this task edits it. Extract before you add.

The synthetic-power test is the whole point of the change and deserves its own suite: a fixture `ContributingPower` whose `Transform` adds one option to a parked decision, and another that removes one, asserting that the projected options and the answers the walker accepts move together in both directions. A mutation that transforms the tree for walking but not for projection must fail it.

- [ ] **Step 1: failing tests.** (a) Recover's relic decision projects one option per live facedown relic with card details, and no `relicCandidates` field exists; (b) Recover's choice decision projects two button options with their declared labels; (c) Forge projects two sections with printed minima and three denizen options; (d) an option whose id is absent from authoritative state suppresses the entire decision projection; (e) the projection round-trips through `GameProjectionCodec`; (f) the new synthetic-power suite's add and remove cases. Expected FAIL: the DTO has no query.
- [ ] **Step 2: implement** the DTOs, their codec, and the projector rewrite; delete `relicCandidates` and `forgeProjection` with their types.
- [ ] **Step 3: implement** the frontend re-sourcing for Recover and Forge, with the `ServerUiSupport` extraction.
- [ ] **Step 4:** re-run; expected PASS. `grep -rnE '\b(relicCandidates|ForgeProjection|ForgeAssignmentTargetProjection)\b' src shared frontend/src` returns nothing. (Scope `frontend/src`, never `frontend`, whose `target/` holds linked JS carrying every symbol you just deleted; and quote any `--include` glob, which zsh expands before grep sees it.)
- [ ] **Step 5:** `./sbtw "test"`, `./sbtw "frontend/test" "frontend/fastLinkJS"`, `python3 scripts/check-architecture.py`.
- [ ] **Step 6: commit** `feat(walker): project decisions from the transformed query`.

---

### Task 5: the generic two-section partition interaction

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/PartitionDecisionState.scala`
- Modify: `frontend/.../CardDecisionState.scala`, `frontend/.../ActionDecisionRenderer.scala`, `shared/.../protocol/CommandIntents.scala`
- Delete: `frontend/.../ForgeAssignmentState.scala`; `ForgeAssignment` wire row and its decoder; `model/PendingProcedures.scala`'s `ForgeResource` and `ForgeResourceAssignment`
- Test: `frontend/.../CardDecisionStateSuite.scala`, a new `PartitionDecisionStateSuite`, `ServerModeUiSuite`, `CommandProtocolSuite`

Forge's dropdown-per-denizen is the last place a Forge answer is assembled by Forge-specific code. Replace it by extracting the two-zone move/drag interaction that Keep/Discard already implements into a `PartitionDecisionState` both callers adapt into: a generic pair of named sections, items that may be moved between them by drag or by an accessible move button, and a confirmation predicate driven by declared minima.

Keep the extraction honest in both directions. Search and starting-adviser keep their exact current semantics — the two-stage `Arrange`/`Resolve` flow, their existing labels, and discard ordering — so `CardDecisionState` adapts its projected data into the shared state and keeps its own resolution stage on top. Forge supplies “Pay Favor” and “Pay Secret”, its projected minima, and no ordering requirement. Do not route Forge through `PendingCardDecisionProjection`, and do not let the shared state learn any Search or setup concept; the direction of reuse is Forge borrowing an interaction, never a walker query depending on the card-decision pipeline.

Confirmation is enabled only when every option is placed and every projected minimum is met, computed from the projection rather than from any local knowledge of Forge's cost. Submitting produces the generic `PartitionWire`.

With Forge's UI generic, the last consumers of the old Forge answer vocabulary go: the `ForgeAssignment` protocol row and the model's `ForgeResource`/`ForgeResourceAssignment`. `SiteDenizenTarget` stays — `ForgeRules` and `LeagueTreatyPower` still use it.

- [ ] **Step 1: failing tests** in `PartitionDecisionStateSuite`: moving an item between sections, a minimum not yet met blocking confirmation, every-option-placed enforced, and the submitted answer naming each option ref exactly once in its section. Plus `CardDecisionStateSuite` regressions proving Search and starting-adviser keep their arrangement rules, ordering, and stage behaviour through the shared state. Plus a `ServerModeUiSuite` case driving Forge end to end through the generic interaction. Expected FAIL: the shared state does not exist.
- [ ] **Step 2: implement** the extraction, adapt both callers, and delete `ForgeAssignmentState`.
- [ ] **Step 3: delete** the `ForgeAssignment` wire row with its decoder and the two model types, in this commit.
- [ ] **Step 4:** re-run; expected PASS. `grep -rnE '\b(ForgeAssignment|ForgeResource)\b' src shared frontend/src` returns nothing.
- [ ] **Step 5:** `./sbtw "test"`, `./sbtw "frontend/test" "frontend/fastLinkJS"`, `python3 scripts/check-architecture.py`.
- [ ] **Step 6: commit** `feat(ui): reuse one partition interaction for Forge and card decisions`.

---

### Task 6: close-out

**Files:** whatever the sweep finds; `docs/superpowers/plans/2026-09-09-walker-batch-1-forge-travel-wake.md`

- [ ] **Step 1: walk the spec's eleven testing obligations** one at a time against the suites that now exist, and name the test that discharges each. An obligation with no test is a gap to close here, not a line to tick.
- [ ] **Step 2: sweep for survivors.** `grep -rnE '\b(OwnerQuery|WalkerCtx|DecisionPayload|relicCandidates|ForgeProjection|ForgeAssignment|ForgeResource|RecoverChoice)\b' src shared frontend/src` returns nothing but `RestPowerDecisionPayload`, and `grep -rn '"recover-choice"\|"recover-relic"\|"forge-assignment"' src/main shared/src/main frontend/src/main` returns nothing. Both are word-bounded and production-scoped for the reasons Task 3's Step 6 records; the legacy tags survive on purpose in the negative decode tests. No projector names a `decisionId` or an `ActionRef` to decide what to offer.
- [ ] **Step 3: check the caps.** `python3 scripts/check-architecture.py` plus a line-count read of the four files this plan flagged, so the next plan inherits an accurate picture rather than four files silently at 799.
- [ ] **Step 4: update batch 1.** Its Task 2 text already describes the declarative contract; note in that plan that the contract now exists and that Travel's decision (Task 5) declares a query rather than a `validate` closure.
- [ ] **Step 5: full gate** `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` and `python3 scripts/check-architecture.py`.
- [ ] **Step 6: ledger.** Record how the open judgement calls were settled: whether `DecisionAnswer` ended up sealed and `encodeDecisionAnswer` exhaustive rather than throwing; and whether any migrated decision needed legality the query could not state. Then commit `docs(plan): close out declarative walker decisions`.

---

## Non-goals

- A universal form or workflow description language. Two query shapes, added to when a real decision needs a third.
- Persisting decision options or queries in game state or events. Only answers persist, and an answer carries refs, never labels.
- Moving game-object presentation into gameplay or model code. Prompt copy on a query is the one admitted exception, per R1.
- Off-turn walker decision ownership. `Decide.owner` is concrete and equals the pending actor; supporting anything else needs its own redesign of pending-state ownership, authorization, continuation and viewer scoping.
- Generalizing Recover's roll feedback, which is not decision-option discovery.
- Batch 1's neighbouring amendments: the exhausted-relic-deck Forge outcome and Travel's powered-candidate simulation.
