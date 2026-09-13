# Procedure Walker: Operations as Data, Powers as Contributors

> Status: approved; partly implemented. Recover, Forge, Travel, Take Wealth and End Wake run on the walker; every other action still runs on its legacy path. See **Migration status** for what the implementation settled. Supersedes the roadmap and phase plans under `docs/superpowers/plans/` for forward work.

## Problem

The engine will host 100+ powers, most simple, all interacting with shared game
state. Future expansions add more. Two constraints rule everything:

1. Adding a power must be one small class (≤50 lines) and never require an
   engine change. The unit is the class, not the file: related powers are
   expected to sit together in one file (a Recover-powers file, a Travel-powers
   file), each still its own ≤50-line contribution object.
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
   A phase transition a player commands is a procedure too, even though it is
   not an action: ending Wake is a one-leaf tree, because a phase change is a
   state write and the walker is where a procedure's state writes are
   declared, journalled and replayed. What follows from it not being an action
   (no Act action boundary) is read at completion, not flagged on its
   registration — see Walker.
   *Resolved by `2026-09-12-walker-ownership-and-phases-design.md` (not yet
   implemented):* state-based evaluation stays engine-internal and decides
   *whether* anything happens; when a change may park a player decision, the
   engine starts a triggered procedure that *performs* it. The Oathkeeper
   title is the first: every title change becomes that procedure's
   `SetOathkeeper` step. That design also replaces "read at completion" above:
   whether the action boundary runs is decided by the completed reference's
   family (`ActionRef`, `PhaseTransitionRef`, `TriggeredProcedureRef`), and it
   runs after every action in every phase.
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
     Restrictions run at command entry, before any `BuildOps` has produced its
     operations, so a restriction cannot see an operation a tree builds at
     walk time. One that matched such an operation would find nothing and
     return no violation, indistinguishable from a correct one in every green
     test. A restriction that needs a fact the tree only builds later reads it
     from `ctx.state` instead (Take Wealth's limit reads the pawn's site).
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
11. **Decisions are declarative queries (superseded D2, 2026-09-11).** A
    `Decide` carries a `DecisionQuery` (`ChooseOne` or `Partition` over
    `DecisionOption`s), and the answer is a `DecisionAnswer`. Both families
    are sealed. One generic validator in `DecisionQueries` checks every answer,
    and `WalkerDecisionProjector` projects the same query, so what is legal and
    what is offered cannot drift apart. No action or power writes a validation
    closure. A power that changes a decision's choices transforms the `Decide`
    node's query; it does not define a payload case of its own. The original
    D2 (an open `DecisionPayload` trait, with per-power payload cases plus
    their own validation) is retired. Full contract:
    `docs/superpowers/specs/2026-09-10-declarative-walker-decisions-design.md`.
12. **Tree is derived per command (S1).** The engine rebuilds the base action
    tree and re-applies power transforms deterministically on every command;
    `PendingTree` stores only `at` (stable node-id chain, not child index),
    `answered` decisions and actor. *(Per
    `2026-09-12-walker-ownership-and-phases-design.md`, not yet implemented:
    the actor is no longer stored, since it always equals
    `turn.activePlayer`; `Answered` gains `by`; `walkerAction` becomes
    `walkerProcedure: Option[ProcedureRef]`.)* Beside it, and durable for the same
    reason, state carries the action (`walkerAction`), the player-selected
    powers (`walkerModifiers`) and the start selections (`walkerStartArgs`):
    none of them is re-derivable from state, and a resume must rebuild the
    tree the start built. Power transforms re-run per command — they are pure.
    Replay uses recorded ops, not the tree.
13. **Command surface collapses.** Three generic commands: `StartWalker(action,
    modifiers, start selections)`, `ResolveWalker(decisionId, answer)`,
    `RollWalker(pool)`. An action's per-action commands are deleted when it
    migrates. `WithModifiers` survives only for the actions still on their
    legacy path, and dies with the last of them. A client-facing spelling may
    outlive its engine command as a one-line route to `StartWalker`:
    `GameIntent.EndWake` does this, so two spellings can start the same
    procedure. *(Per `2026-09-12-walker-ownership-and-phases-design.md`, not
    yet implemented: `StartWalker` takes a `StartableRef`, an action or a
    phase transition, so a triggered procedure cannot be started by a
    client; End Wake becomes a `PhaseTransitionRef`.)*
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
  `ClearRollOutcome(pool)`, `Decide(decisionId, owner, query, window)`,
  `BuildOps(build, window)` (a leaf whose operations are computed from state
  when the walker reaches it, so a transform hooked on it cannot see inside).
- Added by batch 1, each generic and naming no action or power:
  `RecordPowerUse(PowerUseRef)`, which writes a use limit to
  `TurnState.usedPowers` (adding a ref the turn already holds is a no-op);
  and `EnterPhase(Phase)`, which changes the phase. `EnterPhase` states no
  phase order, because that rule belongs to the procedures that perform
  transitions. It does reject entering the phase the turn is already in
  (`OperationError.PhaseAlreadyEntered`), because replay re-runs no gates and
  without this a doubled transition in the journal would replay clean.
- *Planned by `2026-09-12-walker-ownership-and-phases-design.md` (not yet implemented):* `SetOathkeeper(Option[PlayerId])`,
  the only writer of the title holder, which resets the side to Oathkeeper
  and rejects an unchanged holder.

Only four cases carry a `window`: `ModifyDicePool`, `Decide`, `BuildOps` and
`Sequence`. A delta that must be hookable is made hookable by the `Sequence`
it sits in, not by itself. A transform receives the hooked node's children,
and a leaf's children are the leaf itself, so a transform on a windowed leaf
can only replace it whole. Travel's cost node is therefore a windowed
`Sequence(AdjustSupply, Move)`: its terrain transforms see both the payment
and the route.

Composites (CoreOperation): existing concept bundles plus sequence/branch
helpers (`Sequence`, `Branch`). There are no per-action root case classes. An
action's root is an ordinary `Sequence` built by that action's procedure object
(`TravelProcedure`, `ForgeProcedure`, ...) and registered on
`WalkerActionRegistry` (renamed `WalkerProcedureRegistry`, keyed by
`ProcedureRef`, by `2026-09-12-walker-ownership-and-phases-design.md`; not yet implemented), so the ADT never learns an
action's name. A
`Repeat(guard, body)` composite re-executes `body` until
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
  walkerPending: Option[PendingTree],       // replaces PendingProcedure.*
  walkerAction: Option[ActionRef],          // which registered tree to rebuild
  walkerModifiers: Vector[PowerId],         // player-selected, action-scoped
  walkerStartArgs: Vector[DecisionOptionRef], // start selections, action-scoped
  rollPools: Map[PoolKey, DicePoolState]
)
final case class PendingTree(
  at: Vector[String],          // stable node-id chain
  answered: Vector[Answered],  // decisions recorded this action
  actor: PlayerId
)                              // the tree itself is never stored (decision 12)
// Per 2026-09-12-walker-ownership-and-phases-design.md (not yet implemented):
// walkerAction -> walkerProcedure: Option[ProcedureRef]; PendingTree drops
// actor (always turn.activePlayer); Answered gains `by: PlayerId`.
final case class DicePoolState(count: Int)
final case class RollOutcome(pool: PoolKey, count: Int, faces: Vector[DiceFace],
                             skulls: Int, score: Int)
```

**Power use limits stay turn state, and the walker never reads them.** This
rule is inherited from the program this design supersedes, where it was
decision 11 ("Take Wealth tracking stays outside the power framework"). It is
not locked decision 11 above. Batch 1's Take Wealth port tested it and it
held with no spec change:

- *Read side:* a once-per-turn limit is a `Restriction` that reads
  `TurnState.usedPowers` through `ctx.state`, the `ReadyGame` every
  contribution already sees. `PowerCtx` gained no field and the walker learned
  no "power was used" concept.
- *Write side:* the procedure's own tree records the use with the generic
  `RecordPowerUse` operation. The limit has to be a recorded operation because
  replay applies recorded operations and nothing else. A limit written in a
  state callback beside the walk would be missing from a reloaded game, and
  the same use could be made twice.
- The `PowerUseRef` spelling lives once, on the power (`TakeWealthLimit.useRef`),
  and the procedure's write imports it. A read side and a write side that
  spelled the ref differently would leave the limit permanently silent.

This settles use **limits** only. Turn-scoped **activation**, a power switched
on once that then lasts the turn, is still open: `walkerModifiers` lasts
exactly one action, and no batch-1 card needed more. See Migration status.

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
7. At tree end: clear pools and pending. The phase the procedure **finished**
   in names the continuation, meaning where the player now is (Act action
   selection, or awaiting a Wake action). The phase it **started** in decides
   whether the Act action boundary (`completeAction`) runs: only after a
   procedure that started in Act. The two reads differ only for a procedure
   that changes the phase mid-walk; End Wake is the one that does.
   A phase with no walker continuation is a typed rejection, not a default,
   so the first procedure registered in Rest fails loudly instead of sending
   its player back to Act. Neither phase is registry data: the walker and its
   registry state what a procedure does, and which phase the player is in is
   neither's business. The reads live in `OathRulesWalker`.
   *Superseded in part by `2026-09-12-walker-ownership-and-phases-design.md`
   (not yet implemented):* the continuation is still read off the finishing
   phase, but the started-in-Act rule is replaced. The boundary runs iff the
   completed reference is an `ActionRef`, in any phase; never after a
   `PhaseTransitionRef` (End Wake) or a `TriggeredProcedureRef`. Take Wealth
   therefore gains the boundary.

Application layer (game-application-service role) pre-rolls dice at the command
boundary and never lets the engine touch randomness.

**Phase gates.** Starting a walk checks no phase; each procedure's `build`
gates its own (Take Wealth and End Wake require Wake, the rest require Act).
Resuming a parked walk currently requires `Phase.Act`
(`OathRulesWalker.walkerResumeContext`). Every registered procedure outside
Act finishes inside the command that starts it, so nothing has reached that
gate yet. The first procedure that parks outside Act has to change it
deliberately; no test can reach the change before then. *(Per
`2026-09-12-walker-ownership-and-phases-design.md`, not yet implemented: the
resume gate is deleted without replacement, since only resume commands are
accepted while a walker is pending; `startWalker` checks the requester is the
active player, and a parked `Decide` is authorized for its owner.)*

**Decision ids are constants, not per-command tokens.** A `Decide`'s id is a
fixed string per decision (`"forge.assignment"`, `"recover.relic"`), and a
roll park's client-facing id is the entry's `rollDecisionId`. The legacy Forge
path minted `DecisionId(s"forge-$nextSequence")` per command and used it as a
stale-request token. On the walker, staleness is covered by
`expectedNextSequence` on every command plus the invariant that at most one
walker action is pending. A constant id is the walker's model, not a
regression, and a later batch should not "fix" it back.

### Powers

```scala
trait Power {
  def id: PowerId
  def source: RuleSourceRef
  def contributions: Map[PowerWindow, Vector[Contribution]]
  def applicable(ctx: PowerCtx): Boolean = true
  def shouldIgnore(other: ContributingPower): Boolean = false
  def priority: Int = 0
}
```

`shouldIgnore` receives the whole candidate, not its `PowerId`. It was
originally written to take an id, on the reading that decision 10(b)'s named
ignore is purely identity-based ("Vow of Peace ignores X"); that was too
narrow and was widened immediately after the Recover cutover, before any
further power was authored.

The reason: a `PowerId` is an identity string carrying no classification.
Whether a power is a Travel modifier lives on the *window* it hooks —
`PowerWindow.associatedMajorAction`, fixed per window family (`TravelWindow`
→ `Travel`) — reachable from the power object via `contributions.keys` but
not from its id. So a rule of the shape "ignore other modifiers of this
action" was inexpressible; only ignores naming specific ids could be
written. A power now classifies its target directly:

```scala
override def shouldIgnore(other: ContributingPower): Boolean =
  other.contributions.keys.flatMap(_.associatedMajorAction)
    .exists(_ == MajorActionType.Travel)
```

The timing mattered more than the size. Because the migration cuts each
action's UI over and deletes its legacy path in one step, the batch port
(step 3) authors each action's powers as it migrates it — so every power
written against the narrow signature would have become a call site to
revisit. Changed while exactly one `ContributingPower` existed, it was one
line on this trait and one at `ContributionCollector`'s vote step, which
already held both power objects.

`PowerCtx(state, actor, source, window, nodePath, operation)` (`actor`
becomes `activePlayer` per `2026-09-12-walker-ownership-and-phases-design.md`; not yet implemented) carries no
mutable state and no catalog of its own. `operation` is the generic hooked
operation, so a contribution may inspect its children when applicability needs
facts carried by the tree (for example Travel's sibling payment and Move).
A contribution reads game state through `ctx.state` and identifies itself
through its own `source`. A contribution
needing STATIC catalog data that neither `ReadyGame` nor `BuildOps.build`
exposes (e.g. a site's `relicSlots`) holds an `ExecutableCatalog` reference
on the contribution object itself, built once by a catalog-parameterized
factory (`CatacombsContribution.forCatalog`, assembled by
`WalkerPowerCatalog.default`) — the same precedent
`ReviewedPowerCatalog.resolver`/`registry` already set. This is the
established pattern for catalog-dependent powers, not a `PowerCtx` gap:
`PowerCtx` stays catalog-free by design.

Power authorship stays one small class, grouped with its neighbours in a
shared file. Example shape:

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
`WalkerParked` also carries the walk's modifiers and start selections, which
replay restores beside the `PendingTree`. *(Per `2026-09-12-walker-ownership-and-phases-design.md`, not yet implemented:
walker events carry no actor, `Answered.by` records who answered, a triggered
procedure's events replay like any other without re-running a gate, and the
Oathkeeper title events are replaced by that procedure's `SetOathkeeper`
step.)*

A procedure that cannot park (Travel, Take Wealth, End Wake: no `Decide`, no
`Roll`) journals only its step events and `WalkerCompleted`. Those events are
now the whole journal of the Wake phase: `WealthTaken` and `WakeEnded` were
deleted with their legacy paths.

### Windows / catalog

`PowerWindow` stays typed. Catalog handler IDs remain the audited power
vocabulary (`ReviewedPowerCatalog` fingerprint). Source discovery
(`RuleSourceIndex`) stays.

The scaffolding windows were expected to be replaced by `Decide` nodes as
actions migrated. They were not; both kept a job:

- **`*ActionEligibility`** is the window on an action's root `Sequence`. It is
  where root-level restrictions are gathered (Narrow Pass blocks a Travel
  there) and where they see the complete tree.
- **`*ModifierSelection`** is not a tree node at all. It is the window a
  registry entry names (`Entry.modifierWindow`) to say which player-selected
  powers `StartWalker` may offer and accept for that action. It is `Option`:
  Take Wealth and End Wake have none, and inventing a window just to fill the
  field would add one to the audited vocabulary that no rulebook clause
  backs.

## Migration plan

1. Build Operation ADT + walker + PendingTree + new command surface.
   No powers yet. Port one action (vertical slice) — suggested Recover or
   Travel — to a declared tree with its decisions and rolls.
2. Wire power contributions (Transform + Restriction + ignore) against the
   slice; prove ≤50-line power authoring and engine-untouched.
3. Port remaining actions in batches: Search, Economy (Muster/Trade), Forge,
   Challenge, Campaign, Negotiation, CardPlay, Rest, Wake, Visions.
   *In progress:* batch 1 ported Forge, Travel and Wake (Take Wealth and End
   Wake). Remaining: Search, Economy, Challenge, Campaign, Negotiation,
   CardPlay, Rest, Visions.
4. Delete retired machinery (per-action integration seams, typed-fact
   vocabularies, bespoke evolve/handle pairs, PendingProcedure ADT).
   *In progress, and done per action at its cutover:* Recover, Forge, Travel
   and Wake have no legacy path left, and the typed-cost vocabulary is gone.
   Nine `PendingProcedure` cases remain (see Migration status).
5. Author MVP power set on the new framework.

Existing phase plans (phases 1-4 under `docs/superpowers/plans/`) describe
steps already executed and are historical; forward phases are superseded by
this design and will be replanned.

## Verification

- Full gate stays green through migration (`./sbtw "test"
  "frontend/test" "frontend/fastLinkJS"`).
- Drift suites: recorded ops == recomputed tree ops, in dev/test only.
  `WalkerReplayDriftSuite` still covers Recover only: three unpowered walks
  and one Catacombs-modified walk. Batch 1 added no drift case for Forge,
  Travel or Wake. Each has an end-to-end replay-parity test instead, which
  checks that the reloaded state matches but not that the recorded operations
  equal a re-derived walk. Travel's terrain transforms are the obvious next
  entry, since they are the powered case this batch added.
- Power-authoring bar: one class, ≤50 lines, engine untouched. The size half
  is a **design guideline, not an asserted property** (settled 2026-09-09). A
  power needing 55 lines to state its rule honestly should be allowed them,
  and a reviewer judges that better than a line count. Enforcing it
  mechanically also proved expensive out of proportion to its value: measuring
  per class means splitting Scala by hand, and the splitter that did so
  silently missed a power declared inside a family object — taking the
  engine-name check down with it while the suite stayed green.
- What IS asserted, in `BackendArchitectureSuite` ("a walker power imports no
  engine, and the engine never learns its name"): a file declaring a
  `ContributingPower` never imports `gameplay.walker`, and no source under
  `gameplay/walker` or `gameplay/operations` names a specific power. These are
  the boundaries that keep the engine generic, and neither needs the file
  parsed: power names come from the nearest declaration identifier preceding
  each `extends ContributingPower`, which works at any nesting depth. The
  import check is file-scoped on purpose, so one power's illegal import taints
  every power grouped beside it.
- Human-readable log lines rendered from event payloads.

## Migration status (walker batch 1 close-out, 2026-09-12)

Written for whoever plans the next batch. It replaces the Recover slice
checkpoint of 2026-09-08, whose forward-looking claims batch 1 made out of
date. The plan that produced it is
`docs/superpowers/plans/2026-09-09-walker-batch-1-forge-travel-wake.md`, and its
per-task "What Task N settled" notes carry the full evidence.

**On the walker, with no legacy path left.** Five `WalkerActionRegistry`
entries, each building its tree in its own procedure object:
`RecoverProcedure`, `ForgeProcedure`, `TravelProcedure`, `TakeWealthProcedure`
and `EndWakeProcedure`. Walker powers: `CatacombsContribution` (player-selected),
the four terrain powers in `TravelSitePowers` (Mountain, Island, Coast, Narrow
Pass) and `TakeWealthLimit` (both automatic). The last two families were built
during migration; none of them is part of step 5's MVP set.

### The recipe generalised

Forge was chosen to test whether the Recover recipe works for other actions,
and it does. Porting four further procedures needed **no new window, no new
composite, and no engine code naming an action or a power**. What did change
is below, and it is the complete list.

- **`Entry` shape.**
  - `rollDecisionId: Option[String]`: an action with no `Roll` node declares
    `None` rather than a placeholder id no tree would ever park on.
  - `modifierWindow: Option[PowerWindow]` replaced a hardcoded
    `RecoverModifierSelection`, which would have filtered every action's
    offers through Recover's window (see Windows / catalog).
  - `build`/`rebuild` receive the player's start selections as
    `Vector[DecisionOptionRef]`. Travel's destination is a site reference;
    Take Wealth's resource is a `DecisionOptionRef.Button`. A sealed
    per-action start-argument family was tried first and rejected: it would put
    per-action knowledge in the model and the journal codec. An action that
    takes no selection rejects a non-empty vector, with its own test, because
    ignoring it passed the whole suite. The limit is that a selection that is
    neither a game object nor a button (a warband count) has no spelling yet.
    Widen the vocabulary; do not add a case per action.
  - An eligibility-window field was added at Forge's cutover and removed on
    the same branch when the relaxation concept was centralized. There is no
    such field.
- **Operations.** `RecordPowerUse` and `EnterPhase`, both generic (see
  Operation ADT). `Decide` changed shape under the declarative-decisions plan
  (decision 11), which ran on this branch between Tasks 4 and 5 because it
  changed the contract every later port writes against.
- **`PowerCtx`** gained `operation`, the generic hooked node, so Travel's
  terrain powers can read the route (the pay leaf and its sibling `Move`)
  without the collector or walker knowing Travel. Nothing else was missing:
  Take Wealth's limit reached everything through `ctx.state`.
- **Completion** reads the continuation and the Act boundary off the phase,
  no longer hardcoding Act (see Walker, step 7). *(`2026-09-12-walker-ownership-and-phases-design.md`, not yet
  implemented, keeps the continuation read and decides the boundary by
  reference family instead.)*

### Typed cost facts are retired; `Transform` won

Travel was chosen to force this. Terrain cost is a `Transform` over Travel's
windowed pay `Sequence`: Mountain and Island raise the `AdjustSupply` amount,
and Coast replaces it with 1 and uses `shouldIgnore` to drop a destination-side
increase. Narrow Pass is a `Restriction` at `TravelActionEligibility` returning
the typed `TravelPassBlocked` directly. Deleted with the legacy path:
`CostContribution`, `SuppressionRegistry`, `TravelCostWindow`,
`TravelCostLegality` and `TravelPassBlockedCodec`. No reference to any of them
remains.

The cost of this: Task 4's parity table compared each route against the legacy
fold, and that oracle was deleted in Task 5, so the expected costs are now
literals. A batch that retires a legacy oracle should expect the same loss of
evidence.

### Projection and preview run the tree

An action offering candidates (Travel's destinations, Take Wealth's resources)
now does it by dry-running its own tree per candidate through
`WalkerSimulation` and keeping only candidates that finish. Restrictions and
transforms therefore decide the offer exactly as they decide the command. This
matters most for once-per-turn limits, which are restrictions rather than build
gates. The procedure owns `candidates`, and the application-layer projector
only names the results as controls. The modifier preview re-costs with the
exact selected powers and returns them on `MajorActionPreviewAccepted.targets`.
**Not measured yet:** this is one tree build and walk per candidate on every
projection read.

### Turn-scoped state: limits settled, activation still open

Wake was chosen to force this, and it answered half of it. Once-per-turn
**limits** are settled (see State): read by a `Restriction` through
`ctx.state`, written by the tree's `RecordPowerUse`, with no spec change.

Turn-scoped **activation**, a power switched on once that then lasts the
turn, was never exercised, because Take Wealth is a limit and not an
activation. The gap stands as the Recover slice recorded it.
`CurrentGameState.walkerModifiers` holds one `StartWalker`'s player-selected
powers. It is written on park, read on every resume so the fold is identical,
and cleared by `WalkerCompleted`. That lifetime serves decision 12, since a
modifier present at start and absent on resume would change the fold and move
the park cursor. So a turn-long power would disappear at the first
completion. The scope is also fixed in the journal, because `applyRecorded`
checks recorded modifiers against state. A turn-scoped activation therefore
needs its own state cleared at the turn boundary, with the gather consulting
both. `usedPowers` is the nearest precedent. As before, the recommendation is
not to design this speculatively: let the first real card that needs it
decide the shape.

### Still true from the Recover slice

- **One registration per action.** A new action is one `Entry`, and
  `WalkerActionRegistrySuite` asserts the registry covers `ActionRef.all`.
  Registration does not imply "an action": End Wake is registered, and what
  differs is read at completion.
- **The contribution vocabulary is action-agnostic.** `ContributingPower`,
  `Transform`, `Restriction`, `PowerCtx` and `ContributionCollector` gained
  nothing action-specific across four ports. Wire intents are
  `StartWalker`/`RollWalker`/`ResolveWalker` for every action.
- **One `resolution` per power, while `contributions` spans windows.** A
  power that wants an automatic `Restriction` at one window and a
  player-selected `Transform` at another needs two objects under two
  `PowerId`s. Batch 1 did not hit this: every power it added is automatic
  throughout. The first MVP power with that combination will.

### Costs worth carrying forward

- Two extractions were needed to stay under the 800-line cap:
  `OathRulesWalker` out of `OathRules`, and `WalkerReplay` out of
  `ProcedureWalker`. `WalkerEventCodec.scala` is at 785 of 800, so it is the
  file to split before the next batch adds an operation.
  `actions/Campaign.scala` is at exactly 800.
- The drift suite did not grow (see Verification).
- `ActionRef.TakeWealth` keys itself `take-wealth`, the first action key that
  is not also a `MajorActionKind` key. A preview requested for the Wake kind
  keeps its existing path rather than being answered as this action.

### What remains

Step 3: Search, Economy (Muster/Trade), Challenge, Campaign, Negotiation,
CardPlay, Rest and Visions. Step 5 has not started.

Nine `PendingProcedure` cases remain, and their owners are the starting
inventory for the next plan (the file-level table is in the batch-1 plan,
Task 8):

- `Search`: Search.
- `Campaign` and `CampaignRaidRelocation`: Campaign. The `CampaignPlan*` types
  nested beside them are Campaign's supporting vocabulary, not cases.
- `Challenge`: Challenge.
- `Negotiation`: Negotiation.
- `Conspiracy`: CardPlay and Visions.
- `RestPowerDecision` and `RestPowerContinuation`: Rest's power integration,
  which is itself one of step 4's per-action seams.
- `OathkeeperRecipient`: state-based evaluation, not an action. It parks a
  player decision, which decision 6 does not cover. *(Designed as the
  triggered `Oathkeeper` procedure and the proving slice of `2026-09-12-walker-ownership-and-phases-design.md`, not yet
  implemented.)*

Economy and the minor actions own no pending case. They are single-command,
so they are the cheapest ports and the least informative ones.

## Out of scope / deferred

- Active-player ordering of simultaneous same-window effects (rulebook clause
  noted; MVP deterministic sort key).
- Whole-action restructure hook for powers (structural powers, rare).
- Renaming PowerWindow values (TODO comment only).
- Old-journal compatibility (pre-release; replay is forward-only).
- Turn-scoped power activation: waits for the first real card that needs it
  (see Migration status).
- Off-turn walker decisions: designed in `2026-09-12-walker-ownership-and-phases-design.md` (not yet implemented), which
  also records what it leaves open: concurrent multi-owner decisions
  (Negotiation), new answer kinds, power selection by an off-turn owner, and
  unanswered off-turn decisions.
- Start selections that are not an option reference (a warband count):
  waits for the first action that needs one.
