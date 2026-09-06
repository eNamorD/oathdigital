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
  def shouldIgnore(other: Power): Boolean = false
  def priority: Int = 0
}
```

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

## Out of scope / deferred

- Active-player ordering of simultaneous same-window effects (rulebook clause
  noted; MVP deterministic sort key).
- Whole-action restructure hook for powers (structural powers, rare).
- Renaming PowerWindow values (TODO comment only).
- Old-journal compatibility (pre-release; replay is forward-only).
