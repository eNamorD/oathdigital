# Operation Rewrite

**Status:** designed 2026-10-09. Slice 1 is done. Slice 2 waits until catalog
batch 6 slice 4 and consent slice 4 have merged, because their plans edit
code that slice 2 rewrites.

## Goal

A power that changes another procedure's steps finds those steps by what
they are, not by how the procedure happened to build them. Then:

- Wild Mounts replaces the discard of any Nomad battle plan, however that
  plan builds its discard. `PlanDiscard.Marked`, the marker every plan had
  to opt in to, is deleted. Warning Signals missed it once, and Mountain
  Giant, a Nomad plan still to be built, would have had to remember it.
- About twenty transforms that match their window's children stop
  depending on where an op sits in the tree, or on folding after the
  transform that hid it.
- A replacement may ask its question about an op that a closure built, by
  deferring the op and resolving it afterwards.

## Background

- **Opaque closures.** A `BuildOps` produces its `CoreOperation`s when the
  walk reaches it, and a `Branch` selects its children then too. A
  transform sees only the closures, so it cannot tell a discard from any
  other step. A closure's ops run as one batch (`WalkerSteps.runBuildOps`):
  no window sees them, and none of them can park.
- **`LossRewrite.rewrite`** already reaches inside closures. It wraps a
  `BuildOps`' `build`, a `Branch`'s `select` and a `Sequence`'s children,
  and maps each core op through a function. Hospital, Cursed Cauldron,
  `SparedForce` (Billowing Fog, Traveling Doctor), `PeaceEnvoy.spared` and
  `VictoriousNow.recorded` use it. It skips `Repeat` bodies and every custom
  composite, and it maps core ops to core ops only.
- **Hand-written matchers.** The other transforms that inspect or rewrite
  children each write their own descent, mostly top-level only:
  - one level of `LossRewrite`'s logic, copied: Wild Mounts (`Branch`),
    Disciples (`BuildOps`);
  - top-level only: `SupplyStrip` (8 users), `DrawExtension` (Augury,
    Truthful Harp), `TravelRoute.adjustSupply` (Royal Stables and the site
    powers), Initiation Rite, `SuitSupplyWaiver`, `ExtraWarbands`,
    `EconomySources`, `ConspiracyTargets`, Vow of Wandering,
    `PlacementTree.adjust` (6 users), Way Station, The Old Oak, Mounted
    Library.

  One can hide an op from the next: `SuitSupplyWaiver` wraps a
  `SpendSupply` in a `BuildOps`, which a later top-level `SpendSupply`
  matcher misses. Only power priority keeps today's trees safe.
- **Priority as fold order.** A power's `priority` orders it at every
  window it hooks (`ContributingPower.sortKey`). Thirteen powers set one;
  Wild Mounts' `Priority = 10` exists only so it folds after every plan that
  appends a discard at the Campaign's root.
- **Custom composites.** `PlacementTree`, `PlacementBody`,
  `CampaignPlanApplication`, `ModifierChoice` and `CampaignPlanChoice` build
  their children from constructor closures. Each carries its own window,
  except `PlacementBody`, which lives inside `PlacementTree`'s.
- **The catalog.** Of 406 printed powers, about 27 replace a step of a
  procedure:
  - within one window, without a question: Initiation Rite, Hospital,
    Billowing Fog, Traveling Doctor, Peace Envoy, Garrison Armory, and still
    to build Vow of Wisdom, Hallowed Spring, Arcane Armor, Drowned Library;
  - within one window, with a "may": Wild Mounts, Painted Trumpet, Wine of
    Welcome, Errand Boy, and still to build Land Warden, Search Party,
    Temple of Ancestors;
  - across procedures: Family Wagon (through the adviser arrival pass), and
    still to build Wild Pastures, Ring of Devotion, Vow of Kinship, Vow of
    Union, Oaths of Loyalty, Vow of Silence, Vow of Division;
  - at game end: Conspirator.

  Rival ("you may prevent any number of denizens from being discarded")
  replaces a batch of steps after one question, as Wild Mounts does.

## Rulings

1. **A utility called inside a transform.** Rewriting stays a transform's
   job. `OperationRewrite` is a helper a transform calls on its window's
   children; the walker learns nothing new about rewrites.
2. **A rewrite stops at a nested window.** It descends through `Sequence`,
   `Branch`, `Repeat` and `BuildOps`, and leaves untouched any nested node
   that carries its own window: that subtree belongs to its window's
   powers. The node being folded is entered even when windowed, since a
   windowed leaf is handed to its own transforms as its only child. It does
   not see what a nested window's transforms add later. A custom composite
   must carry a window, or live inside one that its own powers rewrite.
3. **Two entry points.** `ops` maps core ops to core ops, wherever they
   are, including what closures build. `nodes` maps any node to any nodes,
   in the tree only. A closure can produce only core ops, so the types keep
   that true.
4. **An op is matched when it becomes known.** An op directly in the
   children is matched at fold time, with the fold's state and answers. An
   op a `Branch` selects or a closure builds is matched when the walk
   reaches it, with that moment's state and answers. The tree's shape never
   changes.
5. **A semantic composite is matched whole.** `ops` matches a core
   composite such as `Discard.Denizen` as one op and does not descend into
   its children. Only the walker's own composites are descended.
6. **Deferral holds ops in walker state.** A deferred op is held under a
   key and run, replaced or dropped later in the same action. Pools would
   not do: they hold counts, and Rival, Wild Pastures and Ring of Devotion
   must hold ops.
7. **A deferred op runs as built.** It keeps what it captured, such as a
   discard's `from` location and tokens, and the pipeline validates it
   when it runs. It is resolved within the action that deferred it. A
   power that needs a fresh build defers its own intent instead.
8. **The take comes last.** A resolve asks its questions first and takes
   the deferred ops in its last step. A `Branch` selects again when a
   parked command resumes, and must find the ops still held.
9. **Two fold phases.** A transform is `Normal` or `Late`. Every `Late`
   transform at a window folds after every `Normal` one; priority orders
   within each phase. A battle plan's `rewrites` always fold `Late`.
10. **Wild Mounts matches the card, not the helper.** It replaces a
    `Discard.Denizen` whose suit is Nomad and whose card was chosen as a
    battle plan on Wild Mounts' side in this Campaign. It does not read the
    op's acting player, which is the attacker when a bandit defender's plan
    is discarded.
11. **The migration is a refactor.** Slice 2 changes no test. A test that
    has to change marks a behavior change, which stops the slice for a
    ruling.

## Design

### `OperationRewrite`

`oathdigital.gameplay.powerresolver.OperationRewrite`, beside the other
power-facing types.

```scala
object OperationRewrite:
  type Matcher[A, B] = (ReadyGame, PendingTree) => PartialFunction[A, Vector[B]]

  def ops(ctx: PowerCtx, children: Vector[Operation])(
      matcher: Matcher[CoreOperation, CoreOperation]): Vector[Operation]

  def nodes(ctx: PowerCtx, children: Vector[Operation])(
      matcher: Matcher[Operation, Operation]): Vector[Operation]
```

Each child is rewritten as follows:

| Node | `ops` | `nodes` |
|---|---|---|
| a nested node with its own window (not `ctx.operation`) | unchanged | unchanged |
| `BuildOps` | `build` wrapped: each op it builds is matched at walk time | unchanged as a node, not entered |
| `Branch` | `select` wrapped: what it selects is rewritten at walk time | the same |
| `Sequence` | children rewritten | children rewritten |
| `Repeat` | body rewritten, guard kept | body rewritten, guard kept |
| any other `CoreOperation` | matched at fold time | matched at fold time |
| any other node (`Note`, a custom composite) | unchanged | matched at fold time |

- Fold-time matches read `ctx.state` and `PendingTree(ctx.nodePath,
  ctx.answered)`. Walk-time matches read the `(ready, pending)` the closure
  or select is handed.
- What a match returns is not matched again. An unmatched op stays as it
  is.
- A `nodes` match on a `BuildOps` or a `Branch` replaces it whole, without
  entering it.
- A wrapped `BuildOps` keeps its `window` and `required`, and a
  `Sequence` keeps its `window`. `Branch` and `Repeat` carry no window.
- The node being folded is `ctx.operation`. When it is a leaf, a leaf
  below it with the same window counts as it too, since the walker does
  not fold that window again on the branch (`WalkerHooks.gathered`).
- Several replacements, or none, go in a `Sequence` in the op's place,
  in the tree and in what a `Branch` selects. What a `BuildOps` builds
  is a flat batch, so there they are spliced in.
- A single replacement for a single op keeps the tree's indices. Several
  replacements for one tree-level op go in a `Sequence` in its place, as
  `LossRewrite` does now.

`LossRewrite.apply` becomes a thin call to `ops`, and its private `rewrite`
is deleted. `ops` needs the fold's context, and every `LossRewrite` user
calls it from a `wrapping`, so `BattlePlan.wrapping` is handed the
`PowerCtx` too (`BattlePlan.Wrap`).

### Deferral

**State.** `CurrentGameState.walkerDeferred: Map[DeferralKey,
Vector[CoreOperation]]`, empty by default. The walker clears it when an
action ends, where it clears `rollPools` (`ProcedureWalker.finish`,
`WalkerReplay`). It is rebuilt by replay and never projected to clients.
`DeferralKey(value: String)` lives in `model`, named after the power that
owns it. `Defer` stores ops as the journal records them
(`OperationRun.canonical`), so a game reloaded from its events holds the same
ops: a held `Discard.Denizen` is not `required`.

**Ops.** Two `PrimitiveOperation`s in `CoreOperations.scala`:

- `Defer(key, ops)` appends `ops` under `key`;
- `TakeDeferred(key)` removes the key.

Both run through the pipeline like `ModifyDicePool`, get
`WalkerOperationCodec` entries, and write no Game Log line.

**Helper.** `oathdigital.gameplay.powerresolver.Deferred`:

```scala
object Deferred:
  def held(ready: ReadyGame, key: DeferralKey): Vector[CoreOperation]

  /** `f` returns the questions, then what runs; the take is appended last.
    * Nothing after the last question may park. */
  def resolve(key: DeferralKey)(
      f: (ReadyGame, PendingTree, Vector[CoreOperation]) => Vector[Operation])
      : Operation
```

`resolve` is a `Branch` that selects nothing while nothing is held, and
otherwise selects `f(ready, pending, held) :+ TakeDeferred(key)`.

A power uses it in one of two ways:

- **Hold:** an `ops` match returns `Vector(Defer(key, Vector(op)))`, so the
  op does not run. A `resolve` later runs it, a replacement, or nothing.
- **Record:** an `ops` match returns `Vector(op, Defer(key, Vector(op)))`,
  so the op runs and is remembered. A `resolve` later reads what ran.

### Fold phase

`Transform(fn, phase: FoldPhase = FoldPhase.Normal)`, with `enum FoldPhase {
case Normal, Late }`. `ContributionCollector.gather` orders transforms by
phase, then by `ContributingPower.sortKey`. The gather order recorded on a
step lists them in that fold order.
Each power's place in that order is set by the earliest phase of its
transforms at the window, then by its sort key.

`BattlePlan.contributions` builds each `rewrites` entry as a `Late`
transform. `BattlePlan`'s doc says so.

### Wild Mounts

- **Hook.** Its `rewrites` at `CampaignActionEligibility`, the root:
  `OperationRewrite.ops(ctx, children)(swappable) :+
  Deferred.resolve(WildMounts.deferred)(swap)`.
- **Match.** `swappable` matches when all of these hold:
  - Wild Mounts was used in this Campaign (`PlanUse.chosen`);
  - the op is a `Discard.Denizen` whose `suit` is Nomad;
  - its `card` is among the plans chosen on Wild Mounts' side, as the
    answered plan picks record them.

  A match is held: `Vector(Defer(WildMounts.deferred, Vector(discard)))`.
  The plan's own "Discarded after the Campaign." note stays silent, since
  its step moved nothing.
- **Resolve.** `swap` runs once over every held discard:
  - **A player user who rules a faceup Beast card** is asked once:
    "discard one of them or Skip Wild Mounts", as now. A Beast card chosen
    is discarded and writes the swap line. Skip runs the held discards.
  - **A bandit user** with two or more held discards discards the first
    Beast card the bandits rule in map order instead (FAQ). Otherwise it
    runs the held discards.
  - **Otherwise** it runs the held discards.

  Each held discard that runs writes "Discarded after the Campaign." with
  the plan's card as its source, under the plan's own power: the card's
  printed power, which declares `PlanDiscard.discarded`. Wild Mounts
  writes only its swap lines.
- **What goes:** `Priority = 10`, the stripping of root `Branch`es and the
  second selection.
- **What changes:**
  - `PlanDiscard.Marked` is deleted. `afterCampaign` stays as the standard
    discard and its line, unmarked.
  - The `PlanDiscard` doc and the `BattlePlan` doc stop mentioning the mark.
  - Warning Signals' doc stops saying it uses `afterCampaign` so that Wild
    Mounts can replace it.

### Migrations (slice 2)

Each rewrite helper below moves onto `ops` or `nodes`. Its behavior and its
tests stay the same.

- `ops`:
  - Disciples (`LossRewrite` moved in slice 1);
  - `SupplyStrip`, `DrawExtension`, `TravelRoute.adjustSupply`;
  - Initiation Rite, `SuitSupplyWaiver`, `ExtraWarbands`;
  - `EconomySources.intoOccupied`.
- `nodes`:
  - `EconomySources.exempted` and `withOptions`, `ConspiracyTargets.narrowed`;
  - Vow of Wandering's Decide-to-Note swap;
  - `PlacementTree.adjust`.
- **The Old Oak and Mounted Library** record the actor's `Gain.Secrets` at
  `TradeGain` (`ops`, record form). Their bonus step becomes a `resolve`
  that reads what was gained. The bonus stays a separate step after the
  gain, so the lines stay as they are.
- **Way Station** keeps its fold-time read of `SpendSupply`. The consent
  design asks every question of a cost before any of it is paid
  ([consent design](2026-10-08-consent-system-design.md)), and a deferral
  would learn the Supply only after the spending closures ran. It moves
  from priority 3 to the `Late` phase. Its doc names the limit: it cannot
  see Supply spent inside a closure.
- **Priority review.** Each remaining priority is checked: Bottom Draw,
  Cursed Cauldron, `EconomySources.ExemptionPriority`, Hospital, Painted
  Trumpet, Mounted Library, Royal Stables, `SparedForce` and Special Envoy.
  - A priority that exists only so the power folds after others' appends
    becomes `Late`.
  - A priority that orders one rewrite after another stays, with its reason
    in its doc. Hospital after `SparedForce` and Cursed Cauldron after
    Hospital are such cases.

### Extension points (not built)

- **Walker-level reach.** A rewrite the walker carries down a walk and
  applies after every nested fold and to every batch. Registered at a
  procedure's root, it reaches across procedures. Wild Pastures, Ring of
  Devotion, Vow of Kinship, Vow of Silence and Vow of Division need it.
- **Op causes.** A cause on `Discard.Denizen` and the gain ops, for "from a
  power" (Wild Pastures).
- **Ask per deferred op.** A `resolve` variant that asks once for each
  held op (Wild Pastures, Ring of Devotion).

## Slices

1. **The utility and Wild Mounts.** `OperationRewrite`, deferral (the state
   field, `Defer`, `TakeDeferred`, their codec entries, `Deferred`), the
   fold phase, and Wild Mounts on top of them. `PlanDiscard.Marked` is
   deleted and `LossRewrite` calls `ops`.
2. **Migrations.** Every helper listed under Migrations, The Old Oak and
   Mounted Library on deferral, Way Station to `Late`, and the priority
   review. No test changes.

## Testing

- **`OperationRewriteSuite`:**
  - each composite: `Sequence`, `Branch`, `Repeat`, `BuildOps`, a leaf;
  - a closure does not run at fold time;
  - two folds give the same shape;
  - it stops at a nested window and leaves custom composites alone;
  - a semantic composite such as `Discard.Denizen` is matched whole;
  - a fold-time match and a walk-time match each read their own state;
  - `nodes` replaces a `BuildOps` whole and never enters it.
- **Deferral:**
  - `Defer` and `TakeDeferred` through the pipeline, and their codec
    round trip;
  - the walker clears the state when the action ends;
  - a `resolve` that parks on its question and resumes finds the ops still
    held, and takes them last.
- **Fold phase:** a `Late` transform folds after a `Normal` one at a higher
  priority, and priority orders within a phase.
- **`WildMountsSuite`** keeps every test, and adds one: a Nomad plan whose
  discard is a raw `PlanDiscard.denizen`, with no helper and no mark, is
  still replaced.
- **Slice 2:** the full suite, unchanged.
