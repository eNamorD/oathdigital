# Procedure Walker: Operations as Data, Powers as Contributors

> Status: design (approved, not yet implemented). Supersedes the roadmap and phase plans under `docs/superpowers/plans/` for forward work.

## Problem

The engine will host 100+ powers, most simple, all interacting with shared game
state. Future expansions add more. Two constraints rule everything:

1. Adding a power must be one small file (≤50 lines) and never require an engine
   change.
2. Powers must be able to examine and modify game procedures at a granular
   level — not only add effects after the fact.

The current architecture fails both. Actions own bespoke Scala procedures
(Travel, Search, Recover, Campaign, Rest, Wake each hand-roll handle/evolve,
typed pending states, and per-action power integration seams). Power effects
are fragmented across several vocabularies: op-inserting powers
(Catacombs), typed cost facts (travel terrain), dice-pool modifiers
(skull-ignore flags), and battle-plan effect data. Each new interaction area
added a new vocabulary and a new seam. Rulebook precedence ("cannot" overrides
"must"; "ignore X" overrides named "must"/"cannot") has no faithful home.

## Goal

One uniform model: a game action is an **Operation tree**; a generic engine
**walker** executes it; powers are **contributors** that transform the tree,
restrict it, or add dice to its pools. All powers speak the same vocabulary.
Engine changes stop after the walker lands.

## Locked decisions (from design dialogue, 2026-09)

1. **Operation tree per action (A1/P2).** Every major action is declared once
   as an `Operation` tree spanning the whole action across commands. The engine
   walks the tree, parks at decision/roll nodes, resumes on the next command.
   Pending state = a `PendingTree` pointer in game state (see State).
2. **Operations unify procedures and deltas.** One `Operation` ADT with a
   single `children: Vector[Operation]` accessor replaces the two-layer
   split's `primitives` field. A `PrimitiveOperation` is a leaf
   (`children = Vector(this)`). A `CoreOperation` is a rulebook-concept bundle
   and may nest other composites. We do not require "Operation = state delta":
   `Decide` and `Roll` are leaves.
3. **Dice pools and roll outcomes are state.** Pool count lives in board state
   (`DicePoolState`), mutated by `ModifyDicePool` ops. A `Roll` leaf reads the
   pool from state and records faces; a `RollOutcome` (faces, skulls, score) is
   written to state and consumed by later resolution. Powers edit skulls/score
   with `ModifyRollOutcome` ops — no `ignoreSkulls` boolean flags in resolution
   math.
4. **Engine never calls random ports.** Randomness (dice) is pre-rolled by the
   application layer at the command boundary and rides the command in. Replay
   never re-rolls; it uses recorded faces.
5. **Journal = semantic events + recorded ops (J1/E1).** Each hookable node
   that runs emits one semantic event whose payload describes the game meaning
   and whose recorded payload includes the final operation batch (and faces /
   choices). One command may append several node events atomically, as today.
   Replay applies recorded ops only: powers, transforms, and the validator do
   not re-run. Replay therefore tolerates power/engine code changes; drift
   checks (recompute vs recorded) move to dev/test suites. Tampered ops are not
   caught by replay (accepted; journal trusted, append-only, sequence-checked).
6. **Scope (hybrid).** Procedures cover everything powers can touch: act
   actions, Rest, Wake, Negotiation. Purely automatic state-based evaluation
   (bandit refill, title checks) stays engine-internal; it needs no power
   hooks. A future power that hooks one of those areas adds a procedure then.
7. **Windows label hookable nodes (W1/H2).** The existing typed `PowerWindow`
   vocabulary remains (keep type safety + audit). Every hookable node carries
   `window: Option[PowerWindow]`. TODO comment left: rename PowerWindows if a
   cleaner naming emerges; not now.
8. **Power shape.** One object per power:
   `contributions: Map[PowerWindow, Vector[Contribution]]`, plus
   `applicable(ctx)`, `shouldIgnore(other)`, `source`, `priority`. No engine
   code in a power.
9. **Contribution kinds (three → two).**
   - `Transform(fn: (PowerCtx, Vector[Operation]) => Vector[Operation])` —
     local to the node whose window the power hooked (node's children vector).
     Covers must-effects (insert ops), cost changes (modify the pay ops), and
     reordering (roll order). Whole-action restructure is not available to
     `Transform`; a rare structural need would get its own hook later.
   - `Restriction(fn: (PowerCtx, Operation) => Option[OathViolation])` —
     cannot-effects. Validator checks the **whole action tree**, so
     Vow-of-Peace ("you cannot campaign") rejects the Campaign root.
   - Dice-pool-as-contribution removed: adding a die is a `ModifyDicePool` op
     via `Transform`.
10. **Gather protocol at a hooked node.** (a) discovery + `applicable`; (b)
    named-ignore, one pass, no transitivity: if any applicable power
    `shouldIgnore(other)`, drop `other`; (c) transforms chained, sorted by
    `(priority, source.stableKey, powerId)`; (d) restriction contributions
    collected, run on the whole tree; (e) execute surviving leaves; (f) record
    one event (semantic payload + ops + contribution order). Simultaneous
    effects: MVP picks the deterministic sort key; "active player chooses
    order" is recorded as a rulebook clause for later, implemented only when a
    real power needs it (most same-window effects are idempotent).
11. **Decision payloads are open (D2).** `DecisionPayload` is an open trait.
    A power may define its own payload case plus validation/preview, and park
    a `Decide` node at an existing window. Engine is generic over payloads.
12. **Tree is derived per command (S1).** The engine rebuilds the base action
    tree and re-applies power transforms deterministically on every command;
    pending stores only `at` (stable node-id chain, not child index),
    `answered` decisions, actor, and walker scratch. Power transforms re-run
    per command — they are pure. Replay uses recorded ops, not the tree.
13. **Command surface collapses.** Three generic commands: `Start(action)`,
    `Resolve(choice)`, `RollSubmitted(faces)`. Per-action command wrappers and
    the `WithModifiers` ordering flow die.
14. **Migration: vertical slice first, then batch.** Prove the walker on one
    action end-to-end (tree + powers + journal + frontend projection + replay),
    then migrate remaining actions in batches. Old journal compatibility is not
    a constraint (pre-release).

## Architecture

### Operation ADT

```scala
sealed trait Operation {
  def window: Option[PowerWindow]      // hook point; None = engine-internal
  def children: Vector[Operation]
}
sealed trait PrimitiveOperation extends Operation {
  final override def children: Vector[Operation] = Vector(this)
}
sealed trait CoreOperation extends Operation    // rulebook concept bundle; nests freely
```

Leaves (PrimitiveOperation):

- Existing deltas: `Move`, `Flip`, `FlipSecrets`, `Bury`, `Peek`,
  `AdjustSupply`, `PayCost`, and the other primitive deltas.
- New: `ModifyDicePool(pool, delta)`, `Roll(pool, dice)`,
  `ModifyRollOutcome(pool, skulls?, score?)`, `ClearDicePool(pool)`,
  `ClearRollOutcome(pool)`, `Decide(payload, owner)`.

Composites (CoreOperation): existing concept bundles plus sequence/branch
helpers (`Sequence`, `Branch`) and action roots (`Travel`, `Muster`,
`Campaign`, ...). A `Repeat(guard, body)` composite re-executes `body` until
`guard(state, pending)` is false (Recover's roll-until-success/stop loop;
Search draw loops). `CoreOperation.primitives` is removed; `children` is the
one accessor. The executor flattens `children` depth-first and applies leaves;
the walker unrolls `Repeat` iterations with each iteration's events recorded
separately (replay = recorded ops, so a loop whose guard changes over time
replays from ops, never by re-guarding).

### State

```scala
final case class CurrentGameState(
  ... existing fields ...,
  pending: Option[PendingTree],          // replaces PendingProcedure.*
  rollPools: Map[PoolKey, DicePoolState] // new
)
final case class PendingTree(
  at: NodePath,                // stable node-id chain
  answered: Vector[Answered],  // decisions recorded this action
  actor: PlayerId,
  action: Operation            // the derived (transformed) action tree
)
final case class DicePoolState(count: Int)
final case class RollOutcome(pool: PoolKey, count: Int, faces: Vector[DiceFace],
                             skulls: Int, score: Int)
```

`usedPowers` per-turn instance tracking stays state (decision 11 of the old
program: Take-Wealth tracking stays outside the power framework).

### Walker

Per command, the engine:

1. Derives the action tree: build base tree, apply power transforms
   deterministically.
2. Walks from `pendingTree.at`.
3. At a hooked node: gather powers, ignore pass, chain transforms, collect
   restrictions, run validator over the whole tree.
4. Executes delta leaves via the executor.
5. At `Roll`: pool count comes from state; the application layer has already
   rolled that many faces (they ride the command); engine validates count,
   writes `RollOutcome`, records the roll event.
6. At `Decide`: parks. Response carries owner, legal options, preview.
7. At tree end: clear pools, clear pending, return to action selection.

Application layer (game-application-service role) pre-rolls dice at the command
boundary and never lets the engine touch randomness.

### Powers

```scala
trait Power {
  def id: PowerId
  def source: RuleSourceRef
  def contributions: Map[PowerWindow, Vector[Contribution]]
  def applicable(ctx: PowerCtx): Boolean = true
  def shouldIgnore(other: PowerId): Boolean = false
  def priority: Int = 0
}
```

`shouldIgnore` takes the other power's `PowerId`, not the power itself
(corrected at Task 10; implemented that way from Task 1): decision 10(b)'s
named ignore is identity-based ("Vow of Peace ignores X"), and no ignore
decision in the MVP power set needs to inspect the ignored power's
contributions or state. A future ignore rule that does need the whole power
widens this one method.

`PowerCtx(state, actor, source, window, nodePath)` carries no mutable state
and no catalog of its own — a contribution reads game state through
`ctx.state` and identifies itself through its own `source`. A contribution
needing STATIC catalog data that neither `ReadyGame` nor `BuildOps.build`
exposes (e.g. a site's `relicSlots`) holds an `ExecutableCatalog` reference
on the contribution object itself, built once by a catalog-parameterized
factory (`CatacombsContribution.forCatalog`, assembled by
`WalkerPowerCatalog.default`) — the same precedent
`ReviewedPowerCatalog.resolver`/`registry` already set. This is the
established pattern for catalog-dependent powers, not a `PowerCtx` gap:
`PowerCtx` stays catalog-free by design.

Power authorship stays one file. Example shape:

```scala
object RelicWorship extends Power {
  def id = PowerId("denizen.relic-worship")
  def source = RuleSourceRef.Adviser(...)   // actual source depends on state
  def contributions = Map(
    PowerWindow.RecoverAfterRelic -> Vector(Transform((ctx, ops) =>
      ops :+ Delta(GainSupply(ctx.actor, 2))))
  )
}
```

### Journal / replay

Events keep semantic payloads (human-readable log lines render from them, e.g.
"Red: Traveled to the Plains (2 supply)", "Red: Gained 1 warband from
Dragonskin Drum"). Each node event also records its final op batch (and faces /
choices). Replay = fold events, apply recorded ops. No powers, no transforms,
no validator at replay.

Parked positions and pool state are durable facts, not walker outputs (P1,
2026-09-06): each `Decide`/`Roll` park appends a state-fact event
(`WalkerParked(at, answered, ...)`); replay applies it to restore `PendingTree`.
`RollOutcome`/answered are reconstructed from `RollPayload`/`ChoicePayload`
events and `ModifyDicePool` ops. The walker is never re-run at replay.

### Windows / catalog

`PowerWindow` stays typed. Catalog handler IDs remain the audited power
vocabulary (`ReviewedPowerCatalog` fingerprint). Source discovery
(`RuleSourceIndex`) stays. Old scaffolding windows (modifier-selection,
eligibility) are replaced by `Decide` nodes as actions migrate.

## Migration plan

1. Build Operation ADT + walker + PendingTree + new command surface.
   No powers yet. Port one action (vertical slice) — suggested Recover or
   Travel — to a declared tree with its decisions and rolls.
2. Wire power contributions (Transform + Restriction + ignore) against the
   slice; prove ≤50-line power authoring and engine-untouched.
3. Port remaining actions in batches: Search, Economy (Muster/Trade), Forge,
   Challenge, Campaign, Negotiation, CardPlay, Rest, Wake, Visions.
4. Delete retired machinery (per-action integration seams, typed-fact
   vocabularies, bespoke evolve/handle pairs, PendingProcedure ADT).
5. Author MVP power set on the new framework.

Existing phase plans (phases 1-4 under `docs/superpowers/plans/`) describe
steps already executed and are historical; forward phases are superseded by
this design and will be replanned.

## Verification

- Full gate stays green through migration (`./sbtw "test"
  "frontend/test" "frontend/fastLinkJS"`).
- Drift suites: recorded ops == recomputed tree ops, in dev/test only.
- Power-authoring bar: one file, ≤50 lines, engine untouched (asserted in a
  BackendArchitecture-style test per power family).
- Human-readable log lines rendered from event payloads.

## Slice status: Recover fully migrated, powers and UI (Task 10 checkpoint, 2026-09-08)

Migration plan steps 1 and 2 are complete for Recover and verified end to
end: the walker carries power contributions, and Recover's UI has been cut
over — there is no longer a legacy Recover path to fall back to. Status, for
whoever picks up the batch port (step 3):

**On the walker, powers and all:** `RecoverProcedure.build`/`rebuild`
(`src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala`)
declares the tree; `ProcedureWalker`
(`src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`) walks
it, folding `ContributingPower` contributions (`Transform`/`Restriction`) at
`PowerWindow`s through `WalkerPowerGather`/`ContributionCollector`, gathered
with one-pass named ignore and the deterministic
`(priority, source.stableKey, powerId)` sort (decision 10). Catacombs
(`src/main/scala/oathdigital/gameplay/powers/recover/CatacombsContribution.scala`)
is the first power ported onto this seam: one 50-line object that imports
`gameplay.operations` (its `Transform` returns `Vector[Operation]`, building
`Move`/`PayCost` to place the relic and charge the secret) but no
`gameplay.walker` import — the authoring bar `BackendArchitectureSuite`
("a walker power is one small file with no engine imports") actually
enforces is the walker import alone, not the operations vocabulary a power
needs to describe its own effect. Offered to a walk when the
player selects it as a `StartWalker` modifier. A player completes Recover,
with and without Catacombs, entirely through the wire's `StartWalker`/
`RollWalker`/`ResolveWalker` intents; the legacy `BeginRecover`/
`AddRecoverDice`/`StopRecover` surface no longer exists.

**Wire and preview:** the parked decision is projected owner-private as
`WalkerDecisionProjection` (`decisionId`, `kind`, `pool`/`count` for a roll
park, `relicCandidates` for the relic park; every field is `None` for a
non-actor viewer). `GameApplicationService.preview` and
`OathRules.validateModifiers` both resolve offerable modifiers through the
shared `OathRules.offerableWalkerPowers(ready, actor)`, so the
modifier-selection preview offers Catacombs from the same
`ContributionCollector.gather` call the walker itself folds through — there
is no second "which powers apply" computation to drift from the first.

**Deleted:** the legacy `Recover.scala` action module and
`RecoverPowerIntegration`; `PendingProcedure.Recover`/`RecoverPowerApplied`;
the `RecoverRolled`/`RecoverStopped`/`RelicRecovered`/`CatacombsResolved`
events and their codec branches; `BeginRecover`/`AddRecoverDice`/
`StopRecover` and their intents/codecs; the legacy `Catacombs` `Power`
object (`RelicWorship`/`E13Ruined`/`E17Intact`/`E17Ruined` stay as
reviewed-but-unimplemented catalog entries; Catacombs' real mechanics now
live solely in `CatacombsContribution`); and `RecoverProjection` end to end
(application layer, both DTO codec sides, the wire field). Nothing under
`gameplay/walker` or `gameplay/operations` names a specific power
(`BackendArchitectureSuite`, strengthened at Task 10 to scan every
`ContributingPower` under `gameplay/powers` rather than naming one file, so
the bar holds as the batch port adds more). Every other action is untouched
and its own suite stays green.

**What the batch port (migration plan step 3) inherits:**
- **Registry entry point:** `WalkerActionRegistry`
  (`src/main/scala/oathdigital/gameplay/walker/WalkerActionRegistry.scala`)
  is the one place an action registers its `build`/`rebuild` functions,
  keyed by `ActionRef`; `WalkerActionRegistrySuite` asserts the map covers
  `ActionRef.all`. A second action is one more `Entry`, not a new `match`
  arm at each of `OathRules.buildWalker` and
  `WalkerDecisionProjector.rebuild`.
- **Contribution vocabulary:** `ContributingPower`/`Contribution`
  (`Transform`/`Restriction`)/`PowerCtx`/`ContributionCollector`
  (`src/main/scala/oathdigital/gameplay/powerresolver/`) and `WalkerPowers`/
  `WalkerPowerGather` (`src/main/scala/oathdigital/gameplay/walker/`) are
  entirely action-agnostic; a second action's powers register in a
  `WalkerPowerCatalog`-shaped object and are selected the same way
  (`WalkerPowers.selected`, `PowerResolution.Automatic`/`PlayerSelected`).
- **Wire intents:** `StartWalker(action, modifiers)`/`RollWalker(pool)`/
  `ResolveWalker(decisionId, answer)`
  (`shared/src/main/scala/oathdigital/protocol/CommandIntents.scala`)
  already carry a generic `action`/`ActionRef` discriminator; a second
  action needs no new intent shape.
- **Preview seam:** `OathRules.offerableWalkerPowers` and
  `WalkerActionRegistry.isRegistered` already route offer/accept
  generically, but `offerableWalkerPowers` currently hardcodes
  `PowerWindow.RecoverModifierSelection` (deferred at Task 9a) — a second
  action needs that window parameterized, even though the routing itself
  needs no change.

**Migration plan steps remaining:**
- Step 3 (port Search, Economy, Forge, Challenge, Campaign, Negotiation,
  CardPlay, Rest, Wake, Visions in batches) has not started; every action
  but Recover still runs on `PendingProcedure`/evolve and the legacy
  `Power`/`PowerHandler`/`PowerResolver` machinery, untouched by this plan.
- Step 4 (delete retired machinery) is complete for Recover only; the
  per-action integration seams other actions still use are deliberately out
  of this plan's scope.
- Step 5 (author the MVP power set on the new framework) has not started.
  Catacombs is the framework's proof-of-life power — ported to exercise
  every part of the seam (Transform, applicability, player-selected
  resolution, catalog-backed data) — not the first entry of the MVP set.

**Drift check:** `WalkerReplayDriftSuite`
(`src/test/scala/oathdigital/gameplay/WalkerReplayDriftSuite.scala`, dev/test
only, never reachable from production replay) now covers four scripted
walks: the original three unpowered Recover walks (single-roll success,
multi-roll success via Continue, and Stop), plus, from Task 10, a
Catacombs-modified Recover — the first corpus entry where a power changed
the tree. Every case asserts that operations recorded in the journal equal
the operations the walker derives when a fresh tree is rebuilt and
re-walked, with the same contributions re-gathered, over state
reconstructed purely by replaying those same recorded events through
`ProcedureWalker.applyRecorded` — the identical function `OathRules.evolve`
dispatches to in production. The powered case is what would catch a
`Transform` (or the gather/fold machinery it runs through) folding
differently on a second walk; it is the property that makes "replay applies
recorded ops only, never re-derives or re-folds" (decision 5) safe to trust
for a powered action, not only an unpowered one.

**Two spec corrections from this plan's reviews** (both applied to the
"Powers" architecture section above):
- `shouldIgnore` takes a `PowerId`, not a `Power` — the spec's illustrative
  code had the latter; the implementation, deliberately, has always had the
  former (Ruling D, Task 1 review). Named ignore (decision 10b) is
  identity-based, so the collector never needs to hand a candidate the
  whole set of other candidates.
- `PowerCtx` carries no catalog, and still doesn't — but a *contribution*
  may hold one on itself. `CatacombsContribution` holds an
  `ExecutableCatalog` field because it needs static catalog data
  (`relicSlots`) that neither `ReadyGame` nor `BuildOps.build` exposes; it
  gets one via a `forCatalog`-style factory, the same precedent
  `ReviewedPowerCatalog.resolver`/`registry` set. Recorded here as the
  established pattern for catalog-dependent powers, not left as a
  contradiction between the doc comment and the code.

Minor deferred items (structure, coverage, one memoization opportunity, the
hardcoded preview window above) were tracked in the SDD ledger for this
slice; that ledger is git-ignored scratch, deleted once this branch
finishes, so the two items below that must actually survive for the batch
port are inlined here instead of left behind a dangling pointer:

- **`ContributingPower.resolution` is one flag per power, but `contributions`
  spans windows.** `resolution` (`Automatic`/`PlayerSelected`) is a single
  field on the whole power object, while a power's `contributions` can
  declare `Transform`/`Restriction` entries at several different
  `PowerWindow`s. A power that wants an automatic `Restriction` at one
  window (say, forbidding the action outright under some condition) AND a
  player-selected `Transform` at another (an optional effect the player
  opts into) cannot express that split with one `ContributingPower` — it
  needs two objects registered under two `PowerId`s, one per resolution
  kind. This is a real shape limit the batch port will hit the first time
  an MVP power wants exactly that combination; it is not a bug in
  Catacombs (which only ever needed one resolution kind), just a
  constraint the type doesn't yet express.
- **I4's four Recover-specific walker hardcodes are now all generalized**
  (final fix wave, commit `9aa29a9`, after this design doc's Task 10
  checkpoint above was written): `OathRules.startWalker`'s fallback-kind
  literal, `OathRules.parkedContinue`'s decision-id match, the roll
  decision id `WalkerDecisionProjector` projected, and
  `GameApplicationService`'s hardcoded roll-pool-size check all now read
  from `WalkerActionRegistry` (`fallbackKind`/`rollDecisionId`/
  `continuationFor`) or `DefenseDicePort.diceCount` instead of a
  Recover-only literal or `RecoverProcedure` reference. None of the four
  remain outstanding for the batch port; a second registered action
  supplies its own registry entry and dice-count expectation rather than
  editing these call sites.

## Out of scope / deferred

- Active-player ordering of simultaneous same-window effects (rulebook clause
  noted; MVP deterministic sort key).
- Whole-action restructure hook for powers (structural powers, rare).
- Renaming PowerWindow values (TODO comment only).
- Old-journal compatibility (pre-release; replay is forward-only).
